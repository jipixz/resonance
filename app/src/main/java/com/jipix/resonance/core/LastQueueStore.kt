package com.jipix.resonance.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.lastQueueStore by preferencesDataStore(name = "last_queue")

/**
 * The queue as it stood when the process last went away.
 *
 * Without this, pressing play on the widget or the notification after the app
 * has been killed does nothing at all: the service starts, finds an empty
 * player, and sits there. Media3 has a hook for exactly this
 * (`onPlaybackResumption`), but it asks the app for the items to restore — so
 * something has to have remembered them.
 *
 * Kept apart from [SettingsStore] on purpose. This is not a preference; it is
 * ephemeral session state that happens to outlive the process, and mixing the
 * two would mean a user's settings file churning on every track change.
 */
class LastQueueStore(private val context: Context) {

    suspend fun save(songIds: List<Long>, index: Int, positionMs: Long) {
        context.lastQueueStore.edit { prefs ->
            prefs[SONG_IDS] = songIds.joinToString(",")
            prefs[INDEX] = index
            prefs[POSITION] = positionMs
        }
    }

    suspend fun load(): LastQueue? {
        val prefs = context.lastQueueStore.data.first()
        val ids = prefs[SONG_IDS]
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
        if (ids.isEmpty()) return null

        return LastQueue(
            songIds = ids,
            // A stored index can outlive the library it pointed into — a rescan
            // may have removed tracks — so it is clamped rather than trusted.
            index = (prefs[INDEX] ?: 0).coerceIn(0, ids.lastIndex),
            positionMs = prefs[POSITION] ?: 0L,
        )
    }

    private companion object {
        val SONG_IDS = stringPreferencesKey("songIds")
        val INDEX = intPreferencesKey("index")
        val POSITION = longPreferencesKey("positionMs")
    }
}

data class LastQueue(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
)
