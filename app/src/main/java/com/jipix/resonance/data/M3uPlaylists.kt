package com.jipix.resonance.data

import com.jipix.resonance.data.db.SongEntity

/**
 * Reading and writing M3U/M3U8 playlists — the one interchange format every
 * other player on the planet already understands.
 *
 * Only the extended form is written (`#EXTM3U` + `#EXTINF`), because the extra
 * lines cost nothing and let a player that never sees the files still show
 * sensible titles.
 */
object M3uPlaylists {

    private const val HEADER = "#EXTM3U"
    private const val INFO_PREFIX = "#EXTINF:"

    /**
     * Serialises to extended M3U with absolute paths.
     *
     * Absolute, not relative: a playlist exported from here is far more likely
     * to be read back by another app on the same device than to be carried to a
     * different directory layout, and a relative path only helps in the second
     * case while breaking the first.
     */
    fun write(songs: List<SongEntity>): String = buildString {
        appendLine(HEADER)
        songs.forEach { song ->
            val seconds = (song.durationMs / 1000).coerceAtLeast(0)
            appendLine("$INFO_PREFIX$seconds,${song.artist} - ${song.title}")
            appendLine(song.path)
        }
    }

    /**
     * Pulls the track paths out of an M3U. Comments, directives and blank lines
     * are skipped; so is anything with a URI scheme, since a remote entry has no
     * counterpart in a local library.
     */
    fun readPaths(content: String): List<String> =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filterNot { it.contains("://") }
            .map { it.removePrefix("file://") }
            .toList()

    /**
     * Resolves paths from a playlist file against the library.
     *
     * Matching falls back through three levels because an M3U rarely comes from
     * the device that will read it: exact path first, then the same path with
     * separators and case normalised, then bare filename. The filename fallback
     * is what makes a playlist exported on a PC actually resolve here, where the
     * storage root has a completely different name.
     *
     * @return the matched songs in playlist order, plus how many lines resolved
     *   to nothing.
     */
    fun resolve(paths: List<String>, library: List<SongEntity>): ResolveResult {
        val byExact = library.associateBy { it.path }
        val byNormalised = library.associateBy { it.path.normalisePath() }
        // Several files can share a name across folders; first one wins, which
        // is the best guess available without more information.
        val byFilename = library
            .groupBy { it.path.substringAfterLast('/').lowercase() }
            .mapValues { (_, matches) -> matches.first() }

        var missing = 0
        val resolved = paths.mapNotNull { raw ->
            val candidate = raw.replace('\\', '/')
            val song = byExact[candidate]
                ?: byNormalised[candidate.normalisePath()]
                ?: byFilename[candidate.substringAfterLast('/').lowercase()]
            if (song == null) missing++
            song
        }

        return ResolveResult(songs = resolved, missing = missing)
    }

    /** A filename suggestion for the export dialog. */
    fun fileNameFor(playlistName: String): String {
        val safe = playlistName
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "playlist" }
        return "$safe.m3u8"
    }
}

data class ResolveResult(
    val songs: List<SongEntity>,
    val missing: Int,
)

private fun String.normalisePath(): String = replace('\\', '/').lowercase()
