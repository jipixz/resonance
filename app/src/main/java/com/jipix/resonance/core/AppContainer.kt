package com.jipix.resonance.core

import android.content.Context
import com.jipix.resonance.data.MusicRepository
import com.jipix.resonance.data.db.ResonanceDatabase
import com.jipix.resonance.data.media.MediaStoreScanner
import com.jipix.resonance.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope

/**
 * Hand-rolled dependency graph. Small enough that a DI framework would add more
 * build complexity than it removes; swap in Hilt if this grows past a dozen
 * entries.
 */
class AppContainer(context: Context, appScope: CoroutineScope) {

    private val database = ResonanceDatabase.build(context)

    val settingsStore = SettingsStore(context)

    val musicRepository = MusicRepository(
        dao = database.musicDao(),
        scanner = MediaStoreScanner(context),
        settingsStore = settingsStore,
    )

    val playerConnection = PlayerConnection(context, appScope)
}
