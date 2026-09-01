package com.jipix.resonance.data



import com.jipix.resonance.data.db.AlbumSummary

import com.jipix.resonance.data.db.ArtistSummary

import com.jipix.resonance.data.db.MusicDao

import com.jipix.resonance.data.db.PlaylistEntity

import com.jipix.resonance.data.db.SongEntity
import com.jipix.resonance.data.db.TrackLoudnessEntity

import com.jipix.resonance.core.SettingsStore

import com.jipix.resonance.data.db.FolderSummary
import com.jipix.resonance.data.db.PlaylistSummary

import com.jipix.resonance.data.media.MediaStoreScanner

import kotlinx.coroutines.ExperimentalCoroutinesApi

import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.flow.flatMapLatest

import kotlinx.coroutines.flow.map



@OptIn(ExperimentalCoroutinesApi::class)

class MusicRepository(

    private val dao: MusicDao,

    private val scanner: MediaStoreScanner,

    settingsStore: SettingsStore,

) {



    /**

     * Room cannot bind an empty `NOT IN ()` list, so a sentinel that no real path

     * can equal always rides along.

     */

    private val excluded: Flow<List<String>> = settingsStore.settings

        .map { it.excludedFolders }

        .distinctUntilChanged()

        .map { it.toList() + NO_FOLDER }



    // Exclusions are applied at query time, not at scan time. That keeps every

    // folder in the database so the blacklist screen can list — and re-enable —

    // folders the user has already switched off.

    val songs: Flow<List<SongEntity>> = excluded.flatMapLatest(dao::observeAllSongs)

    val albums: Flow<List<AlbumSummary>> = excluded.flatMapLatest(dao::observeAlbums)

    val artists: Flow<List<ArtistSummary>> = excluded.flatMapLatest(dao::observeArtists)

    val folders: Flow<List<FolderSummary>> = dao.observeFolders()
    val playlistSummaries: Flow<List<PlaylistSummary>> = dao.observePlaylistSummaries()

    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()

    val mostPlayed: Flow<List<SongEntity>> = dao.observeMostPlayed()

    val recentlyPlayed: Flow<List<SongEntity>> = dao.observeRecentlyPlayed()



    fun albumSongs(albumId: Long): Flow<List<SongEntity>> = dao.observeAlbumSongs(albumId)

    fun artistSongs(artist: String): Flow<List<SongEntity>> = dao.observeArtistSongs(artist)

    fun playlistSongs(playlistId: Long): Flow<List<SongEntity>> = dao.observePlaylistSongs(playlistId)

    fun search(query: String): Flow<List<SongEntity>> = dao.search(query)



    suspend fun getSongs(ids: List<Long>): List<SongEntity> = dao.getSongs(ids)

    /**
     * Every cached song, exclusions ignored. Used for resolving imported
     * playlists, where a hidden folder should still match.
     */
    suspend fun allSongsOnce(): List<SongEntity> = dao.getAllSongsOnce()

    // ---- loudness ----

    suspend fun loudnessOf(songId: Long): Double? = dao.loudnessOf(songId)

    suspend fun storeLoudness(songId: Long, lufs: Double) =
        dao.upsertLoudness(TrackLoudnessEntity(songId, lufs, System.currentTimeMillis()))

    val analysedCount: Flow<Int> = dao.observeAnalysedCount()

    suspend fun clearLoudness() = dao.clearLoudness()



    /**

     * Reconciles the local cache against MediaStore. Only rows that are new or

     * whose file actually changed get written, so the common case — nothing moved

     * since last launch — costs one query and zero writes.

     *

     * @return how many rows were added/updated and how many were dropped.

     */

    suspend fun sync(): SyncResult {

        val fresh = scanner.scan()

        val known = dao.getFingerprints().associate { it.id to it.dateModifiedSec }



        val changed = fresh.filter { known[it.id] != it.dateModifiedSec }

        val removed = known.keys - fresh.mapTo(HashSet()) { it.id }



        if (changed.isNotEmpty()) dao.upsertSongs(changed)

        if (removed.isNotEmpty()) dao.deleteSongs(removed.toList())



        return SyncResult(

            total = fresh.size,

            upserted = changed.size,

            removed = removed.size,

        )

    }



    suspend fun recordPlay(songId: Long) = dao.recordPlay(songId, System.currentTimeMillis())



    suspend fun recordSkip(songId: Long) = dao.recordSkip(songId, System.currentTimeMillis())



    suspend fun createPlaylist(name: String): Long {

        val now = System.currentTimeMillis()

        return dao.createPlaylist(PlaylistEntity(name = name, createdAtMs = now, updatedAtMs = now))

    }



    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>) =

        dao.addToPlaylist(playlistId, songIds)



    suspend fun deletePlaylist(playlistId: Long) {
        // playlist_songs has no foreign key, so its rows have to go explicitly.
        dao.clearPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) =
        dao.removeFromPlaylist(playlistId, songId)

    suspend fun setPlaylistCover(playlistId: Long, albumId: Long) =
        dao.setPlaylistCover(playlistId, albumId)

}



private const val NO_FOLDER = "<none>"



data class SyncResult(

    val total: Int,

    val upserted: Int,

    val removed: Int,

) {

    val changedAnything: Boolean get() = upserted > 0 || removed > 0

}

