package com.jipix.resonance.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per audio file. [id] is the MediaStore id, which keeps the local cache
 * cheap to reconcile: a rescan only has to diff ids and dateModified.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("albumId"),
        Index("artist"),
        Index("albumArtist"),
        Index("title"),
        Index("folder"),
    ],
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val path: String,
    /** Parent directory of [path]. Drives the folder blacklist. */
    val folder: String,
    /** Kilobits per second, straight from MediaStore. 0 when unknown. */
    val bitrate: Int,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    /** Album whose art represents the list. 0 means "build a mosaic instead". */
    val coverAlbumId: Long = 0,
)

/**
 * Explicit [position] rather than relying on insertion order, so reordering a
 * queue is a single UPDATE pass instead of a rewrite.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("playlistId"), Index("songId")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

/** Drives "most played", "recently played" and skip-aware ordering. */
@Entity(tableName = "playback_stats")
data class PlaybackStatEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayedAtMs: Long = 0,
)

/** Projection for the album grid; derived from [SongEntity], not stored. */
data class AlbumSummary(
    val albumId: Long,
    val album: String,
    val albumArtist: String,
    val songCount: Int,
    val year: Int,
)

/** Projection for the artist list; derived from [SongEntity], not stored. */
data class ArtistSummary(
    val artist: String,
    val songCount: Int,
    val albumCount: Int,
    /**
     * MediaStore has no artist imagery, so the list borrows the cover of one of
     * the artist's albums. Picked deterministically so it does not shuffle
     * between scans.
     */
    val artworkAlbumId: Long,
)
