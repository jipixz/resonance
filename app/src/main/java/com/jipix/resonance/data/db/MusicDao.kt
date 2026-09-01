package com.jipix.resonance.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // ---- songs ----

    @Query(
        """
        SELECT * FROM songs
        WHERE folder NOT IN (:excludedFolders)
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun observeAllSongs(excludedFolders: List<String>): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSong(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongs(ids: List<Long>): List<SongEntity>

    @Query(
        """
        SELECT * FROM songs
        WHERE albumId = :albumId
        ORDER BY discNumber ASC, trackNumber ASC, title COLLATE NOCASE ASC
        """
    )
    fun observeAlbumSongs(albumId: Long): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs
        WHERE artist = :artist OR albumArtist = :artist
        ORDER BY album COLLATE NOCASE ASC, discNumber ASC, trackNumber ASC
        """
    )
    fun observeArtistSongs(artist: String): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
        LIMIT 100
        """
    )
    fun search(query: String): Flow<List<SongEntity>>

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<Long>)

    @Query("SELECT id, dateModifiedSec FROM songs")
    suspend fun getFingerprints(): List<SongFingerprint>

    // ---- derived collections ----

    @Query(
        """
        SELECT albumId,
               album,
               albumArtist,
               COUNT(*) AS songCount,
               MAX(year) AS year
        FROM songs
        WHERE folder NOT IN (:excludedFolders)
        GROUP BY albumId
        ORDER BY album COLLATE NOCASE ASC
        """
    )
    fun observeAlbums(excludedFolders: List<String>): Flow<List<AlbumSummary>>

    @Query(
        """
        SELECT artist,
               COUNT(*) AS songCount,
               COUNT(DISTINCT albumId) AS albumCount,
               MIN(albumId) AS artworkAlbumId
        FROM songs
        WHERE folder NOT IN (:excludedFolders)
        GROUP BY artist
        ORDER BY artist COLLATE NOCASE ASC
        """
    )
    fun observeArtists(excludedFolders: List<String>): Flow<List<ArtistSummary>>

    @Query(
        """
        SELECT folder, COUNT(*) AS songCount
        FROM songs
        GROUP BY folder
        ORDER BY folder COLLATE NOCASE ASC
        """
    )
    fun observeFolders(): Flow<List<FolderSummary>>

    // ---- stats ----

    @Query(
        """
        SELECT s.* FROM songs s
        JOIN playback_stats p ON p.songId = s.id
        ORDER BY p.playCount DESC, p.lastPlayedAtMs DESC
        LIMIT :limit
        """
    )
    fun observeMostPlayed(limit: Int = 100): Flow<List<SongEntity>>

    @Query(
        """
        SELECT s.* FROM songs s
        JOIN playback_stats p ON p.songId = s.id
        WHERE p.lastPlayedAtMs > 0
        ORDER BY p.lastPlayedAtMs DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyPlayed(limit: Int = 100): Flow<List<SongEntity>>

    @Query("SELECT * FROM playback_stats WHERE songId = :songId")
    suspend fun getStat(songId: Long): PlaybackStatEntity?

    @Upsert
    suspend fun upsertStat(stat: PlaybackStatEntity)

    @Transaction
    suspend fun recordPlay(songId: Long, atMs: Long) {
        val current = getStat(songId) ?: PlaybackStatEntity(songId)
        upsertStat(
            current.copy(playCount = current.playCount + 1, lastPlayedAtMs = atMs)
        )
    }

    @Transaction
    suspend fun recordSkip(songId: Long, atMs: Long) {
        val current = getStat(songId) ?: PlaybackStatEntity(songId)
        upsertStat(
            current.copy(skipCount = current.skipCount + 1, lastPlayedAtMs = atMs)
        )
    }

    // ---- loudness ----

    @Query("SELECT lufs FROM track_loudness WHERE songId = :songId")
    suspend fun loudnessOf(songId: Long): Double?

    @Upsert
    suspend fun upsertLoudness(entry: TrackLoudnessEntity)

    @Query(
        """
        SELECT id FROM songs
        WHERE id NOT IN (SELECT songId FROM track_loudness)
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    suspend fun songIdsWithoutLoudness(): List<Long>

    @Query("SELECT COUNT(*) FROM track_loudness")
    fun observeAnalysedCount(): Flow<Int>

    @Query("DELETE FROM track_loudness")
    suspend fun clearLoudness()

    // ---- playlists ----

    @Query("SELECT * FROM playlists ORDER BY updatedAtMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET coverAlbumId = :albumId WHERE id = :playlistId")
    suspend fun setPlaylistCover(playlistId: Long, albumId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long)

    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.coverAlbumId AS coverAlbumId,
               COUNT(ps.songId) AS songCount,
               GROUP_CONCAT(DISTINCT s.albumId) AS albumIdsCsv
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        LEFT JOIN songs s ON s.id = ps.songId
        GROUP BY p.id
        ORDER BY p.updatedAtMs DESC
        """
    )
    fun observePlaylistSummaries(): Flow<List<PlaylistSummary>>

    @Query(
        """
        SELECT s.* FROM songs s
        JOIN playlist_songs ps ON ps.songId = s.id
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """
    )
    fun observePlaylistSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun lastPositionIn(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongs(rows: List<PlaylistSongEntity>)

    @Transaction
    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        var next = lastPositionIn(playlistId) + 1
        insertPlaylistSongs(songIds.map { PlaylistSongEntity(playlistId, it, next++) })
    }
}

/** A playlist and its size, for the list screen. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    /** 0 when no cover was chosen, in which case [albumIds] drives a mosaic. */
    val coverAlbumId: Long,
    val songCount: Int,
    /**
     * Comma-joined album ids, straight from GROUP_CONCAT. Collected in the same
     * pass as the counts so the list does not fire one query per row.
     */
    val albumIdsCsv: String?,
) {
    val albumIds: List<Long>
        get() = albumIdsCsv?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()
}

/** A directory containing music, and how much of it. */
data class FolderSummary(
    val folder: String,
    val songCount: Int,
)

/** Minimal projection used to decide what a rescan actually has to touch. */
data class SongFingerprint(
    val id: Long,
    val dateModifiedSec: Long,
)
