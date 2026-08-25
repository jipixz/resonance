package com.jipix.resonance.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real crossfade: the tail of the outgoing track and the head of the incoming one
 * genuinely overlap.
 *
 * The obvious design — two players alternating as "the" player — means the media
 * session has to follow whichever one is live, and Media3 binds a session to one
 * `Player` for its lifetime. So this inverts it: the session's player always owns
 * the queue and always holds the *incoming* track. A throwaway second player is
 * handed the *outgoing* track's tail and fades it out underneath.
 *
 * The consequences of that choice, stated plainly:
 * - Session, notification and queue state are correct from the first frame of the
 *   fade. Nothing has to be handed over mid-flight.
 * - The seam lands on the outgoing track (a seek into the tail), not the incoming
 *   one. That is the right place for it: the tail is already fading to silence.
 *
 * Crossfade is fundamentally incompatible with audio offload — the DSP renders one
 * stream, not two — so [PlaybackService] drops back to CPU decoding whenever this
 * is enabled. That is the battery cost, and it is why the feature is opt-in.
 */
@OptIn(UnstableApi::class)
class CrossfadeEngine(
    private val context: Context,
    private val primary: ExoPlayer,
    private val scope: CoroutineScope,
) {

    var enabled: Boolean = false
    var fadeMs: Long = 6_000L

    private var watchJob: Job? = null
    private var tail: ExoPlayer? = null
    private var fading = false

    fun start() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            while (isActive) {
                // Idle polling is the whole cost of having this armed, so it stays
                // coarse until a fade is actually within reach.
                if (!enabled || fading || !primary.isPlaying) {
                    delay(IDLE_POLL_MS)
                    continue
                }

                val duration = primary.duration
                val remaining = duration - primary.currentPosition
                when {
                    duration <= 0 || duration == C.TIME_UNSET -> delay(IDLE_POLL_MS)

                    // Repeat-one would fade a track into itself, and the last item
                    // has nothing to fade into.
                    !primary.hasNextMediaItem() -> delay(IDLE_POLL_MS)

                    remaining <= fadeMs -> crossfade()

                    remaining <= fadeMs + APPROACH_MS -> delay(APPROACH_POLL_MS)

                    else -> delay(IDLE_POLL_MS)
                }
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        releaseTail()
        primary.volume = 1f
        fading = false
    }

    private suspend fun crossfade() {
        val outgoing = primary.currentMediaItem ?: return
        val outgoingPosition = primary.currentPosition
        fading = true

        try {
            val secondary = obtainTail()
            secondary.setMediaItem(outgoing)
            secondary.prepare()
            secondary.seekTo(outgoingPosition)
            secondary.volume = 1f
            secondary.play()

            // The queue moves on immediately; only the audio lags behind.
            primary.volume = 0f
            primary.seekToNextMediaItem()

            val steps = (fadeMs / STEP_MS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val progress = step.toFloat() / steps
                primary.volume = progress
                secondary.volume = 1f - progress
                delay(STEP_MS)
            }
        } finally {
            primary.volume = 1f
            releaseTail()
            fading = false
        }
    }

    private fun obtainTail(): ExoPlayer {
        tail?.let { return it }

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // The primary player already holds focus. Asking for it again
                // would duck or stop the very stream we are fading against.
                /* handleAudioFocus = */ false,
            )
            .build()

        // Two offloaded streams are not a thing; ask for software rendering so the
        // request cannot be silently refused mid-fade.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                    .build()
            )
            .build()

        tail = player
        return player
    }

    private fun releaseTail() {
        tail?.release()
        tail = null
    }

    private companion object {
        const val IDLE_POLL_MS = 1_000L
        /** How early to tighten the poll so the fade starts on time. */
        const val APPROACH_MS = 2_000L
        const val APPROACH_POLL_MS = 120L
        const val STEP_MS = 50L
    }
}
