package com.jipix.resonance.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import android.os.Bundle
import com.jipix.resonance.data.db.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One row of the queue sheet. */
data class QueueItem(
    val index: Int,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val durationMs: Long,
)

/** What the UI needs to draw the player. Nothing more, so recomposition stays cheap. */
data class PlaybackUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val album: String = "",
    /** Title of the upcoming queue item, empty when this is the last one. */
    val nextTitle: String = "",
    /** Codec and bitrate of the current track, e.g. "FLAC · 1006 kbps". */
    val format: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val hasQueue: Boolean = false,
    /** Position in the queue. Drives the direction of the track-change animation. */
    val queueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/**
 * Bridges the UI to [PlaybackService]. Position is polled rather than observed
 * because the player has no per-frame position callback — but the poll only runs
 * while something is actually playing *and* the UI is collecting, which is why
 * this does not burn cycles with the screen off.
 */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private var controller: MediaController? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun connect() {
        if (controller != null) return

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()

        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(ControllerListener())
            pushState()
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        controller?.release()
        controller = null
        _state.value = PlaybackUiState()
    }

    // ---- transport ----

    fun playQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        val c = controller ?: return
        c.setMediaItems(songs.toMediaItems(), startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() = controller?.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---- state plumbing ----

    private inner class ControllerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()
    }

    private fun pushState() {
        val c = controller
        if (c == null) {
            _state.value = PlaybackUiState()
            return
        }

        val metadata = c.mediaMetadata
        _state.value = _state.value.copy(
            isConnected = true,
            isPlaying = c.isPlaying,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUri = metadata.artworkUri?.toString(),
            album = metadata.albumTitle?.toString().orEmpty(),
            nextTitle = c.peekNextTitle(),
            format = metadata.extras.describeFormat(),
            durationMs = c.duration.coerceAtLeast(0L),
            positionMs = c.currentPosition.coerceAtLeast(0L),
            hasQueue = c.mediaItemCount > 0,
            queueIndex = c.currentMediaItemIndex,
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )

        if (c.isPlaying) startProgressTicker() else stopProgressTicker()
    }

    private fun startProgressTicker() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (true) {
                val c = controller ?: break
                _state.value = _state.value.copy(
                    positionMs = c.currentPosition.coerceAtLeast(0L),
                )
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * The whole queue, read on demand rather than carried in [PlaybackUiState].
     * A queue is often the entire library, and rebuilding that list on every
     * player event — which is what putting it in the state would mean — would be
     * a lot of allocation for something the user sees only when they ask for it.
     */
    fun snapshotQueue(): List<QueueItem> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).map { index ->
            val metadata = c.getMediaItemAt(index).mediaMetadata
            QueueItem(
                index = index,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                artworkUri = metadata.artworkUri?.toString(),
                durationMs = metadata.extras?.getLong(EXTRA_DURATION) ?: 0L,
            )
        }
    }

    fun playQueueItem(index: Int, autoPlay: Boolean) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        // seekTo alone preserves the paused state, which is why tapping a queue
        // row used to just move the cursor and sit there.
        if (autoPlay) c.play()
    }

    /**
     * Shuffles only what has not been played yet. Replacing the whole list would
     * restart the current track, and reshuffling history is meaningless anyway.
     */
    fun shuffleUpcoming() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from >= c.mediaItemCount) return
        val upcoming = (from until c.mediaItemCount).map { c.getMediaItemAt(it) }.shuffled()
        c.replaceMediaItems(from, c.mediaItemCount, upcoming)
    }

    /** Drops repeats of the same track, keeping the earliest copy and the current one. */
    fun removeDuplicates(): Int {
        val c = controller ?: return 0
        val seen = HashSet<String>()
        val doomed = ArrayList<Int>()
        for (index in 0 until c.mediaItemCount) {
            val id = c.getMediaItemAt(index).mediaId
            if (!seen.add(id) && index != c.currentMediaItemIndex) doomed += index
        }
        // Descending, or each removal would shift the indices still to come.
        doomed.asReversed().forEach { c.removeMediaItem(it) }
        return doomed.size
    }

    fun queueSongIds(): List<Long> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).songId() }
    }

    fun moveQueueItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
    }

    /** The queue item after the current one, if the queue has not run out. */
    private fun MediaController.peekNextTitle(): String {
        val next = nextMediaItemIndex
        if (next == C.INDEX_UNSET || next >= mediaItemCount) return ""
        return getMediaItemAt(next).mediaMetadata.title?.toString().orEmpty()
    }

    private companion object {
        /**
         * 500 ms is under the threshold where a seek bar looks like it stutters,
         * and a fifth of the wakeups a 100 ms tick would cost.
         */
        const val PROGRESS_INTERVAL_MS = 500L
    }
}

/**
 * Renders the codec and bitrate stashed in [MediaItems] extras. Falls back to an
 * empty string rather than showing a half-filled line.
 */
private fun Bundle?.describeFormat(): String {
    val bundle = this ?: return ""
    val codec = bundle.getString(EXTRA_MIME_TYPE).orEmpty()
        .substringAfterLast('/')
        .removePrefix("x-")
        .uppercase()
    val bitrate = bundle.getInt(EXTRA_BITRATE)
    return when {
        codec.isBlank() -> ""
        bitrate <= 0 -> codec
        else -> "$codec · $bitrate kbps"
    }
}
