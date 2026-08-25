package com.jipix.resonance.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.jipix.resonance.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's audio library out of MediaStore. Nothing here touches the
 * files themselves — MediaStore has already indexed tags, so a full scan is one
 * cursor walk rather than thousands of file opens. That difference is the whole
 * reason startup is fast and cheap on battery.
 */
class MediaStoreScanner(private val context: Context) {

    suspend fun scan(minDurationMs: Long = 15_000L): List<SongEntity> =
        withContext(Dispatchers.IO) {
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.BITRATE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
            )

            // IS_MUSIC already drops most ringtones and notification sounds; the
            // duration floor drops app sound effects. Messaging-app media survives
            // both, so it is filtered per-row in isExcluded() below.
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} >= ?"
            val args = arrayOf(minDurationMs.toString())

            val out = ArrayList<SongEntity>(512)

            context.contentResolver.query(collection, projection, selection, args, null)
                ?.use { cursor -> readAll(cursor, out) }

            out
        }

    private fun readAll(cursor: Cursor, out: MutableList<SongEntity>) {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val relPathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val bitrateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
        val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

        while (cursor.moveToNext()) {
            val path = cursor.getString(dataCol).orEmpty()
            val relativePath = cursor.getString(relPathCol).orEmpty()
            val displayName = cursor.getString(displayNameCol).orEmpty()

            if (isExcluded(path, relativePath, displayName)) continue

            val rawTrack = cursor.getInt(trackCol)
            val artist = cursor.getString(artistCol).orUnknown(UNKNOWN_ARTIST)

            out += SongEntity(
                id = cursor.getLong(idCol),
                title = cursor.getString(titleCol).orUnknown(UNKNOWN_TITLE),
                artist = artist,
                album = cursor.getString(albumCol).orUnknown(UNKNOWN_ALBUM),
                albumId = cursor.getLong(albumIdCol),
                // Falling back to the track artist keeps compilations from
                // splitting into one "album" per performer.
                albumArtist = cursor.getString(albumArtistCol) ?: artist,
                durationMs = cursor.getLong(durationCol),
                trackNumber = rawTrack.trackWithinDisc(),
                discNumber = rawTrack.discNumber(),
                year = cursor.getInt(yearCol),
                path = path,
                folder = path.substringBeforeLast('/', missingDelimiterValue = ""),
                bitrate = cursor.getInt(bitrateCol) / 1000,
                mimeType = cursor.getString(mimeCol).orEmpty(),
                sizeBytes = cursor.getLong(sizeCol),
                dateAddedSec = cursor.getLong(addedCol),
                dateModifiedSec = cursor.getLong(modifiedCol),
            )
        }
    }

    companion object {
        const val UNKNOWN_TITLE = "Sin título"
        const val UNKNOWN_ARTIST = "Artista desconocido"
        const val UNKNOWN_ALBUM = "Álbum desconocido"

        /** The stable content URI for a song, safe to hand to ExoPlayer. */
        fun songUri(songId: Long): Uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            songId,
        )

        /**
         * Album art lives behind this legacy path, which Coil can load directly.
         * Songs whose art is only embedded resolve through the song URI instead.
         */
        fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )
    }
}

/**
 * MediaStore packs multi-disc releases into one integer as `disc * 1000 + track`,
 * so a track on disc 2 arrives as e.g. 2003. Single-disc albums are left alone.
 */
private fun Int.discNumber(): Int = if (this >= 1000) this / 1000 else 1

private fun Int.trackWithinDisc(): Int = if (this >= 1000) this % 1000 else this

private fun String?.orUnknown(fallback: String): String =
    if (this.isNullOrBlank() || this == "<unknown>") fallback else this

/**
 * Messaging apps drop their audio into shared storage where MediaStore happily
 * indexes it as music. A two-minute voice note clears the duration floor, so the
 * only reliable signals are where the file lives and what it is called.
 */
private fun isExcluded(path: String, relativePath: String, displayName: String): Boolean {
    val haystack = "$relativePath/$path".lowercase()
    if (EXCLUDED_FOLDERS.any { it in haystack }) return true
    return WHATSAPP_MEDIA.containsMatchIn(displayName)
}

/**
 * WhatsApp stamps every file it saves with a `-WA####` suffix — `PTT-20260101-WA0001.opus`
 * for voice notes, `AUD-20260101-WA0002.mp3` for shared audio. Matching the suffix is
 * far more precise than matching the prefixes, which are not unique to WhatsApp.
 */
private val WHATSAPP_MEDIA = Regex("""-WA\d{4}\.""", RegexOption.IGNORE_CASE)

/**
 * Only system sound directories stay hardcoded — nothing in them is ever music.
 * Everything else the user excludes through the folder blacklist, which needs the
 * songs in the database to be able to list the folder at all.
 */
private val EXCLUDED_FOLDERS = listOf(
    "/notifications/",
    "/ringtones/",
    "/alarms/",
)
