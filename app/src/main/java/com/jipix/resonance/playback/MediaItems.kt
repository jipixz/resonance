package com.jipix.resonance.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jipix.resonance.data.db.SongEntity
import com.jipix.resonance.data.media.MediaStoreScanner

/**
 * The session hands this metadata straight to the notification, the lock screen
 * and Bluetooth head units, so it is worth filling in properly rather than
 * letting the extractor guess at playback time.
 */
fun SongEntity.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(MediaStoreScanner.songUri(id))
        .setMimeType(mimeType.takeIf { it.isNotBlank() })
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setTrackNumber(trackNumber.takeIf { it > 0 })
                .setDiscNumber(discNumber.takeIf { it > 0 })
                .setRecordingYear(year.takeIf { it > 0 })
                .setArtworkUri(MediaStoreScanner.albumArtUri(albumId))
                // MediaMetadata has no field for codec or bitrate, and the
                // controller has no access to the database, so the info line's
                // data rides along in extras.
                .setExtras(
                    Bundle().apply {
                        putString(EXTRA_MIME_TYPE, mimeType)
                        putInt(EXTRA_BITRATE, bitrate)
                        putLong(EXTRA_DURATION, durationMs)
                        putLong(EXTRA_ALBUM_ID, albumId)
                    }
                )
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

fun List<SongEntity>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

const val EXTRA_MIME_TYPE = "resonance.mimeType"
const val EXTRA_BITRATE = "resonance.bitrate"
const val EXTRA_DURATION = "resonance.duration"
const val EXTRA_ALBUM_ID = "resonance.albumId"

/** Recovers the MediaStore id the queue item was built from. */
fun MediaItem.songId(): Long? = mediaId.toLongOrNull()
