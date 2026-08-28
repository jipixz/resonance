package com.jipix.resonance.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import android.content.Context
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

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
 *
 * Deliberately does not try to keep the tail in sync with `primary`'s pause/resume
 * state: that was tried (twice), and both times produced an audible cut instead.
 * The trade this accepts: pausing exactly during a crossfade leaves the outgoing
 * track's tail playing for up to `fadeMs` after the UI shows paused. This is a
 * closed decision — do not reintroduce pause-mirroring here.
 *
 * ## The transition glitch, and why this is a rewrite rather than another patch
 *
 * Three earlier versions of this file all captured `primary.currentPosition`
 * once, then set the tail up cold — `setMediaItem`/`prepare`/`seekTo`/`play` —
 * *at* the moment the fade was supposed to start. Every one of those calls
 * takes real wall-clock time, during which `primary` kept playing forward
 * (its volume was not zeroed until after the tail's setup). By the time the
 * tail actually became audible, the position it had been told to seek to was
 * already stale — the listener had already heard `primary` play *past* that
 * point — so the tail taking over read as the track rewinding a beat before
 * resuming. Waiting for `Player.STATE_READY` (tried in the previous version)
 * narrowed this but could not close it: readiness is about the decoder, not
 * about the captured position already being behind by the time it is used.
 *
 * This version removes the stale-position problem instead of racing it: the
 * tail is started *playing* — muted, not just prepared — during the approach
 * window, seeked once to `primary`'s position at that moment and then simply
 * left running in real time alongside it. By the time [crossfade] actually
 * needs it, the tail has been keeping pace with `primary` on its own for up
 * to [APPROACH_MS] — no seek, no cold start, nothing captured-then-stale.
 * Making it audible is just raising its volume from 0.
 */
@OptIn(UnstableApi::class)
class CrossfadeEngine(
    private val context: Context,
    private val primary: ExoPlayer,
    private val scope: CoroutineScope,
    /** Told right before the queue is advanced for a fade, so the session's
     * stats listener can tell that transition apart from a real user skip. */
    private val onAdvance: () -> Unit = {},
) {

    var enabled: Boolean = false
        set(value) {
            field = value
            // Nothing left to fade with once it is switched off — free the extra
            // decoder pipeline rather than let it sit around idle.
            if (!value) releaseTail()
        }
    var fadeMs: Long = 6_000L

    private var watchJob: Job? = null
    private var tail: ExoPlayer? = null
    private var fading = false
    /** The item [tail] is currently muted and playing in real-time sync with
     * `primary` for, so [crossfade] only has to raise its volume. Cleared
     * (and the tail paused) the moment `primary` moves on to something else
     * without a fade happening — a manual skip during the approach window,
     * for instance — so it never sits there quietly wasting battery on a
     * track nobody is fading into any more. */
    private var syncedItem: MediaItem? = null

    fun start() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            while (isActive) {
                // The sync target went stale — primary moved on (a manual
                // skip, a seek past the fade window) without a fade ever
                // happening. Stop wasting cycles on it.
                if (syncedItem != null && syncedItem != primary.currentMediaItem) {
                    tail?.playWhenReady = false
                    syncedItem = null
                }

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

                    remaining <= fadeMs + APPROACH_MS -> {
                        startSyncedTail()
                        delay(APPROACH_POLL_MS)
                    }

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

    /**
     * Gets the tail actually playing — muted — alongside `primary`, well
     * before the fade needs it. One seek to align it, then it just keeps
     * running in real time; see the class doc for why that matters more than
     * it sounds like it should.
     */
    private fun startSyncedTail() {
        val current = primary.currentMediaItem ?: return
        if (syncedItem == current) return
        val player = obtainTail()
        player.setMediaItem(current)
        player.seekTo(primary.currentPosition)
        player.volume = 0f
        player.prepare()
        player.playWhenReady = true
        syncedItem = current
    }

    private suspend fun crossfade() {
        val outgoing = primary.currentMediaItem ?: return
        fading = true

        try {
            val secondary = obtainTail()
            if (syncedItem != outgoing || secondary.playbackState == Player.STATE_IDLE) {
                // Missed the sync window — a very short track, or crossfade
                // only just switched on mid-song. Falls back to the old
                // cold-start path rather than skipping the fade outright;
                // this is the rare case, not the one being optimised for.
                secondary.setMediaItem(outgoing)
                secondary.seekTo(primary.currentPosition)
                secondary.volume = 0f
                secondary.prepare()
                secondary.playWhenReady = true
                secondary.awaitReady()
            }

            // The common path: already playing in sync, so this is the only
            // step that actually has to happen right now.
            secondary.volume = 1f

            // The queue moves on immediately; only the audio lags behind. This
            // seek is a fade completing, not a skip, so the stats listener is
            // told before it fires — otherwise it reads as MEDIA_ITEM_TRANSITION_
            // REASON_SEEK and every crossfaded track gets counted as skipped.
            primary.volume = 0f
            onAdvance()
            primary.seekToNextMediaItem()

            // primary just handed the session to a media item it may not
            // have buffered yet. Same reasoning as the tail: better to wait
            // out a slow decoder swap than time the ramp against a guess.
            primary.awaitReady()

            val steps = (fadeMs / STEP_MS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val progress = step.toFloat() / steps
                primary.volume = progress
                secondary.volume = 1f - progress
                delay(STEP_MS)
            }
        } finally {
            primary.volume = 1f
            tail?.playWhenReady = false
            syncedItem = null
            fading = false
        }
    }

    /** Suspends until this player reaches [Player.STATE_READY], or [timeoutMs]
     * passes — whichever first, so a stuck player cannot hang the fade. */
    private suspend fun ExoPlayer.awaitReady(timeoutMs: Long = 1_200L) {
        if (playbackState == Player.STATE_READY) return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            removeListener(this)
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                }
                addListener(listener)
                continuation.invokeOnCancellation { removeListener(listener) }
            }
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
        syncedItem = null
    }

    private companion object {
        const val IDLE_POLL_MS = 1_000L
        /** How long before the fade the tail starts playing muted in sync. */
        const val APPROACH_MS = 2_000L
        const val APPROACH_POLL_MS = 120L
        const val STEP_MS = 50L
    }
}
