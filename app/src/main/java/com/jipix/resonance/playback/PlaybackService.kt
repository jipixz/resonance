package com.jipix.resonance.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jipix.resonance.ResonanceApp
import com.jipix.resonance.data.media.MediaStoreScanner
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

        crossfade = CrossfadeEngine(this, player, scope, onAdvance = { crossfadeAdvancing = true })
        crossfade.start()

        player.addListener(StatsListener())
        observeSettings()

        session = MediaSession.Builder(this, player)
            .setCallback(OutputRoutingCallback())
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
                normalizeVolume = settings.normalizeVolume
                analyseOnPlay = settings.analyseOnPlay
                applyGainForCurrentItem()
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

    /**
     * Routing lives here because `setPreferredAudioDevice` is an ExoPlayer API and
     * this service holds the only ExoPlayer. Controllers ask by device id and the
     * service resolves it against the live list, so a device that disappeared
     * between the tap and the handling simply resolves to nothing.
     */
    private inner class OutputRoutingCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            // The single-argument builder is deprecated; this one takes the
            // controller too, which is what lets the session tailor what it
            // grants per caller rather than handing every connection the same set.
            MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(OutputRouting.command)
                        .build()
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != OutputRouting.ACTION_SET_OUTPUT) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }

            val requested = args.getInt(
                OutputRouting.EXTRA_DEVICE_ID,
                OutputRouting.DEVICE_AUTOMATIC,
            )
            val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
            val device = if (requested == OutputRouting.DEVICE_AUTOMATIC) {
                null
            } else {
                audioManager
                    ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    ?.firstOrNull { it.id == requested }
            }

            // null clears the preference and hands routing back to the system,
            // which is exactly what the "Automatic" entry means.
            player.setPreferredAudioDevice(device)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
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

            // A crossfade advances the queue with the same seek reason a manual
            // skip uses. The flag lets this transition be told apart from one the
            // user actually triggered, so a faded-out track still counts as played.
            val fromCrossfade = crossfadeAdvancing
            crossfadeAdvancing = false

            scope.launch {
                when {
                    fromCrossfade -> repo.recordPlay(previous)
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> repo.recordPlay(previous)
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> repo.recordSkip(previous)
                    else -> Unit
                }
            }
            lastItemId = mediaItem?.songId()
            applyGainForCurrentItem()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && lastItemId == null) {
                lastItemId = player.currentMediaItem?.songId()
            }
        }
    }

    private var lastItemId: Long? = null
    private var normalizeVolume: Boolean = false
    private var analyseOnPlay: Boolean = true
    /** Guards against queueing a second analysis for a track already in flight. */
    private val analysing = java.util.Collections.synchronizedSet(HashSet<Long>())

    /**
     * Levels the current track against [LoudnessAnalyzer.TARGET_LUFS].
     *
     * Applied through `Player.volume`, which the renderer scales at the
     * AudioTrack — not an AudioProcessor, so offload survives. That is the whole
     * reason this is a per-track gain rather than a compressor: a compressor
     * would need to see the samples.
     *
     * A track with no measurement yet plays at full scale and is queued for
     * analysis, so it is levelled from its second play onward. Measuring during
     * the first play is not possible without putting a processor in the audio
     * path, which is the trade this refuses.
     */
    private fun applyGainForCurrentItem() {
        val songId = player.currentMediaItem?.songId()
        if (!normalizeVolume || songId == null) {
            player.volume = 1f
            return
        }

        scope.launch {
            val repo = (application as ResonanceApp).container.musicRepository
            val known = repo.loudnessOf(songId)
            if (known != null) {
                player.volume = LoudnessAnalyzer.gainFor(known)
                return@launch
            }

            player.volume = 1f
            if (!analyseOnPlay) return@launch
            if (!analysing.add(songId)) return@launch
            try {
                val measured = LoudnessAnalyzer.analyse(
                    this@PlaybackService,
                    MediaStoreScanner.songUri(songId),
                )
                if (measured != null && measured.isFinite()) {
                    repo.storeLoudness(songId, measured)
                    // Only if it is still the track playing: the analysis
                    // outlives short tracks and skips.
                    if (player.currentMediaItem?.songId() == songId) {
                        player.volume = LoudnessAnalyzer.gainFor(measured)
                    }
                }
            } finally {
                analysing.remove(songId)
            }
        }
    }
    private var crossfadeAdvancing = false
}
