package com.jipix.resonance.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val dynamicColor: Boolean = true,
    /** True-black surfaces in dark mode. Saves OLED power at night. */
    val amoled: Boolean = false,
    /**
     * Off by default on purpose: crossfading needs two simultaneous streams,
     * which forces decoding back onto the CPU and gives up the offload saving.
     */
    val crossfade: Boolean = false,
    val crossfadeSeconds: Int = 6,
    /**
     * Tint the mini player with the dominant colour of the current cover.
     * Off by default: an artwork-derived wash is the strongest visual tell of a
     * streaming client, so it stays something the user opts into.
     */
    val artworkTint: Boolean = false,
    /** What the line under the transport controls shows. */
    val infoLine: InfoLine = InfoLine.Format,
    /** Tapping a queue row starts playback instead of only moving the cursor. */
    val queueTapPlays: Boolean = true,
    /** Whether picking from the queue closes the sheet. */
    val queueClosesOnTap: Boolean = false,
    /**
     * Level tracks against each other on playback. Off by default: the first
     * play of any track is still unmeasured, and measuring costs a background
     * decode.
     */
    val normalizeVolume: Boolean = false,
    /**
     * Measure a track the first time it plays. Turning this off makes the batch
     * pass the only source of measurements, which is the point: analysis stops
     * being something that happens behind every new song and becomes something
     * the user runs when it suits them.
     */
    val analyseOnPlay: Boolean = true,
    /** Absolute folder paths the library ignores. */
    val excludedFolders: Set<String> = emptySet(),
)

enum class InfoLine(val label: String) {
    Format("Formato"),
    Album("Álbum"),
    NextTrack("Siguiente"),
    None("Nada");

    /** Next in the cycle, wrapping — the player's info line is tapped to advance. */
    fun next(): InfoLine = entries[(ordinal + 1) % entries.size]
}

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun setDynamicColor(enabled: Boolean) = put(Keys.DYNAMIC_COLOR, enabled)

    suspend fun setAmoled(enabled: Boolean) = put(Keys.AMOLED, enabled)

    suspend fun setCrossfade(enabled: Boolean) = put(Keys.CROSSFADE, enabled)

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CROSSFADE_SECONDS] = seconds }
    }

    suspend fun setArtworkTint(enabled: Boolean) = put(Keys.ARTWORK_TINT, enabled)

    suspend fun setNormalizeVolume(enabled: Boolean) = put(Keys.NORMALIZE, enabled)

    suspend fun setAnalyseOnPlay(enabled: Boolean) = put(Keys.ANALYSE_ON_PLAY, enabled)

    suspend fun setQueueTapPlays(enabled: Boolean) = put(Keys.QUEUE_TAP_PLAYS, enabled)

    suspend fun setQueueClosesOnTap(enabled: Boolean) = put(Keys.QUEUE_CLOSES, enabled)

    suspend fun setInfoLine(value: InfoLine) {
        context.dataStore.edit { it[Keys.INFO_LINE] = value.name }
    }

    suspend fun setFolderExcluded(folder: String, excluded: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_FOLDERS].orEmpty()
            prefs[Keys.EXCLUDED_FOLDERS] =
                if (excluded) current + folder else current - folder
        }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private fun Preferences.toSettings() = Settings(
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
        amoled = this[Keys.AMOLED] ?: false,
        crossfade = this[Keys.CROSSFADE] ?: false,
        crossfadeSeconds = this[Keys.CROSSFADE_SECONDS] ?: 6,
        artworkTint = this[Keys.ARTWORK_TINT] ?: false,
        normalizeVolume = this[Keys.NORMALIZE] ?: false,
        analyseOnPlay = this[Keys.ANALYSE_ON_PLAY] ?: true,
        queueTapPlays = this[Keys.QUEUE_TAP_PLAYS] ?: true,
        queueClosesOnTap = this[Keys.QUEUE_CLOSES] ?: false,
        infoLine = this[Keys.INFO_LINE]
            ?.let { name -> InfoLine.entries.firstOrNull { it.name == name } }
            ?: InfoLine.Format,
        excludedFolders = this[Keys.EXCLUDED_FOLDERS].orEmpty(),
    )

    private object Keys {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val CROSSFADE = booleanPreferencesKey("crossfade")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val ARTWORK_TINT = booleanPreferencesKey("artwork_tint")
        val NORMALIZE = booleanPreferencesKey("normalize_volume")
        val ANALYSE_ON_PLAY = booleanPreferencesKey("analyse_on_play")
        val QUEUE_TAP_PLAYS = booleanPreferencesKey("queue_tap_plays")
        val QUEUE_CLOSES = booleanPreferencesKey("queue_closes_on_tap")
        val INFO_LINE = stringPreferencesKey("info_line")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
    }
}
