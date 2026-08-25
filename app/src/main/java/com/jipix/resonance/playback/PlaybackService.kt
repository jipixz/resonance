package com.jipix.resonance.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jipix.resonance.ResonanceApp
import com.jipix.resonance.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The only component that owns a player. Everything else — the UI, the
 * notification, Bluetooth — talks to it through a MediaController, so there is
 * exactly one source of truth for what is playing.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var crossfade: CrossfadeEngine
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(
                // Software decoders would run on the CPU even when the DSP could
                // do the work; extension renderers stay off for the same reason.
                DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Lets ExoPlayer hold the wake lock only while it is actually
            // rendering, instead of us holding one for the whole session.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .setSkipSilenceEnabled(false)
            .build()

        crossfade = CrossfadeEngine(this, player, scope)
        crossfade.start()

        player.addListener(StatsListener())
        observeSettings()

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
    }

    /**
     * Offload and crossfade are mutually exclusive: the DSP renders one stream, and
     * a crossfade needs two. Whenever the setting flips, the renderer preference is
     * rebuilt to match.
     */
    private fun observeSettings() {
        scope.launch {
            (application as ResonanceApp).container.settingsStore.settings.collect { settings ->
                crossfade.enabled = settings.crossfade
                crossfade.fadeMs = settings.crossfadeSeconds * 1000L
                if (settings.crossfade) disableAudioOffload() else enableAudioOffload()
            }
        }
    }

    private fun disableAudioOffload() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                    .build()
            )
            .build()
    }

    /**
     * Hands decoding to the device's audio DSP so the CPU can idle between
     * buffer refills. This is the single biggest battery win over a player that
     * decodes on the main audio thread — with the screen off it is the difference
     * between the CPU waking hundreds of times a second and waking once every
     * few seconds.
     *
     * Requiring gapless support means the platform will refuse offload rather
     * than silently insert a gap between tracks; correctness wins over the
     * saving if a device cannot do both.
     */
    private fun enableAudioOffload() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .setIsGaplessSupportRequired(true)
                    .setIsSpeedChangeSupportRequired(false)
                    .build()
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Swiping the app away should not kill music that is still playing, but
     * leaving an idle service (and its notification) behind is exactly the kind
     * of thing that drains a battery overnight.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        crossfade.stop()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Feeds the "most played / skipped" stats. A track counts as played once it
     * reaches the end; anything the user jumps away from early counts as a skip.
     */
    private inner class StatsListener : Player.Listener {
        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) {
            val previous = lastItemId ?: return
            val repo = (application as ResonanceApp).container.musicRepository

            scope.launch {
                when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> repo.recordPlay(previous)
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> repo.recordSkip(previous)
                    else -> Unit
                }
            }
            lastItemId = mediaItem?.songId()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && lastItemId == null) {
                lastItemId = player.currentMediaItem?.songId()
            }
        }
    }

    private var lastItemId: Long? = null
}
