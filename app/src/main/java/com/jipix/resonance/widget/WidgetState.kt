package com.jipix.resonance.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * What the widget knows about playback.
 *
 * Pushed by [com.jipix.resonance.playback.PlaybackService] when something
 * actually changes, rather than pulled by the widget on a timer. A widget that
 * polls is a widget that wakes the device on a schedule it does not control —
 * which in this project is the one thing worth refusing outright.
 *
 * Note what is *not* here: playback position. Showing a live progress bar means
 * redrawing every second forever, on the home screen, whether or not anyone is
 * looking. The seconds are in the notification and in the app; the widget's job
 * is "what is playing, and let me change it", and it can answer that without
 * ever waking up on its own.
 */
object WidgetState {

    val TITLE = stringPreferencesKey("title")
    val ARTIST = stringPreferencesKey("artist")
    val ARTWORK_URI = stringPreferencesKey("artworkUri")
    val IS_PLAYING = booleanPreferencesKey("isPlaying")
    val HAS_QUEUE = booleanPreferencesKey("hasQueue")

    /**
     * Reads the session once and publishes what it finds.
     *
     * Binding here does start the service if it is not running, which sounds
     * like exactly the sort of thing this project avoids — but the bind is
     * released immediately, and a MediaSessionService with nothing queued does
     * not promote itself to the foreground or hold anything open. The
     * alternative is a widget that stays blank until the next track change.
     */
    suspend fun seedFromSession(context: Context) {
        val snapshot = readSessionSnapshot(context) ?: return
        publish(
            context = context,
            title = snapshot.title,
            artist = snapshot.artist,
            artworkUri = snapshot.artworkUri,
            isPlaying = snapshot.isPlaying,
            hasQueue = snapshot.hasQueue,
        )
    }

    suspend fun publish(
        context: Context,
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        hasQueue: Boolean,
    ) {
        ResonanceWidget().updateIf(context) { prefs ->
            prefs[TITLE] = title
            prefs[ARTIST] = artist
            prefs[ARTWORK_URI] = artworkUri.orEmpty()
            prefs[IS_PLAYING] = isPlaying
            prefs[HAS_QUEUE] = hasQueue
        }
    }
}

/**
 * Writes state into every placed instance and redraws them.
 *
 * Silently does nothing when no widget is placed — the common case, and one
 * where the alternative is an exception on a background thread for a feature
 * the user never asked for.
 */
private suspend fun ResonanceWidget.updateIf(
    context: Context,
    edit: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
) {
    val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
    val ids = runCatching { manager.getGlanceIds(ResonanceWidget::class.java) }.getOrNull()
    if (ids.isNullOrEmpty()) return

    ids.forEach { id ->
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply(edit)
        }
        update(context, id)
    }
}
