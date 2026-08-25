package com.jipix.resonance.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jipix.resonance.data.MusicRepository
import com.jipix.resonance.data.db.AlbumSummary
import com.jipix.resonance.data.db.ArtistSummary
import com.jipix.resonance.data.db.FolderSummary
import com.jipix.resonance.data.db.PlaylistSummary
import com.jipix.resonance.data.db.SongEntity
import com.jipix.resonance.playback.PlaybackUiState
import com.jipix.resonance.playback.PlayerConnection
import com.jipix.resonance.playback.QueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a detail screen is currently showing. */
sealed interface DetailTarget {
    val title: String

    data class Album(val albumId: Long, override val title: String, val subtitle: String) :
        DetailTarget

    data class Artist(override val title: String) : DetailTarget

    data class Playlist(val playlistId: Long, override val title: String) : DetailTarget
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    private val repository: MusicRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    private val whileVisible = SharingStarted.WhileSubscribed(5_000)

    val songs: StateFlow<List<SongEntity>> = repository.songs
        .stateIn(viewModelScope, whileVisible, emptyList())

    val albums: StateFlow<List<AlbumSummary>> = repository.albums
        .stateIn(viewModelScope, whileVisible, emptyList())

    val artists: StateFlow<List<ArtistSummary>> = repository.artists
        .stateIn(viewModelScope, whileVisible, emptyList())

    val folders: StateFlow<List<FolderSummary>> = repository.folders
        .stateIn(viewModelScope, whileVisible, emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = repository.playlistSummaries
        .stateIn(viewModelScope, whileVisible, emptyList())

    val playbackState: StateFlow<PlaybackUiState> = player.state

    // ---- detail ----

    private val _detail = MutableStateFlow<DetailTarget?>(null)
    val detail: StateFlow<DetailTarget?> = _detail.asStateFlow()

    val detailSongs: StateFlow<List<SongEntity>> = _detail
        .flatMapLatest { target ->
            when (target) {
                is DetailTarget.Album -> repository.albumSongs(target.albumId)
                is DetailTarget.Artist -> repository.artistSongs(target.title)
                is DetailTarget.Playlist -> repository.playlistSongs(target.playlistId)
                null -> flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, whileVisible, emptyList())

    fun openDetail(target: DetailTarget) {
        _detail.value = target
    }

    fun closeDetail() {
        _detail.value = null
    }

    // ---- search ----

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Debounced so a fast typist runs one query instead of one per keystroke;
     * the DAO's LIKE scan is cheap but not free on a large library.
     */
    val searchResults: StateFlow<List<SongEntity>> = _query
        .debounce(180)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.search(q.trim())
        }
        .stateIn(viewModelScope, whileVisible, emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    // ---- scanning ----

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Safe to call on every launch: [MusicRepository.sync] no-ops when nothing changed. */
    fun sync() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.sync()
            } finally {
                _isScanning.value = false
            }
        }
    }

    // ---- playback ----

    fun playFrom(list: List<SongEntity>, index: Int) = player.playQueue(list, index)

    fun playAlbum(albumId: Long) {
        viewModelScope.launch {
            // Album order matters here, so take the sorted query's first emission
            // rather than filtering the flat song list.
            val tracks = repository.albumSongs(albumId).first()
            if (tracks.isNotEmpty()) player.playQueue(tracks, 0)
        }
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun next() {
        player.next()
    }

    fun previous() {
        player.previous()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun toggleShuffle() = player.toggleShuffle()

    fun cycleRepeat() = player.cycleRepeat()

    fun queueSnapshot(): List<QueueItem> = player.snapshotQueue()

    fun playQueueItem(index: Int, autoPlay: Boolean = true) =
        player.playQueueItem(index, autoPlay)

    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)

    fun shuffleQueue() = player.shuffleUpcoming()

    fun removeQueueDuplicates() = player.removeDuplicates()

    fun saveQueueAsPlaylist(name: String) {
        val ids = player.queueSongIds()
        if (ids.isNotEmpty()) createPlaylist(name, ids)
    }

    // ---- playlists ----

    fun createPlaylist(name: String, withSongs: List<Long> = emptyList()) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            if (withSongs.isNotEmpty()) repository.addToPlaylist(id, withSongs)
        }
    }

    fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch { repository.addToPlaylist(playlistId, songIds) }
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.removeFromPlaylist(playlistId, songId) }
    }

    fun setPlaylistCover(playlistId: Long, albumId: Long) {
        viewModelScope.launch { repository.setPlaylistCover(playlistId, albumId) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    class Factory(
        private val repository: MusicRepository,
        private val player: PlayerConnection,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(repository, player) as T
    }
}
