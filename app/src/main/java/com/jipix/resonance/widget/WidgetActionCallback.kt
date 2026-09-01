package com.jipix.resonance.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.jipix.resonance.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The transport commands a widget button can send. */
object WidgetAction {
    const val PLAY_PAUSE = "play_pause"
    const val NEXT = "next"
    const val PREVIOUS = "previous"

    val KEY = ActionParameters.Key<String>("action")

    fun params(action: String): ActionParameters = actionParametersOf(KEY to action)
}

/**
 * Runs a widget button's command against the session.
 *
 * Connects a short-lived [MediaController] per tap rather than holding one open.
 * A widget is not a running screen — there is nowhere to keep a connection alive
 * without pinning the service, and pinning it so a button can answer a few
 * milliseconds sooner is the exact trade this project refuses. Connecting also
 * starts the service, which is what makes play work after the app was swiped
 * away.
 */
class WidgetActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action = parameters[WidgetAction.KEY] ?: return
        withMediaController(context) { controller ->
            when (action) {
                WidgetAction.PLAY_PAUSE -> if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }

                WidgetAction.NEXT -> controller.seekToNextMediaItem()
                WidgetAction.PREVIOUS -> controller.seekToPreviousMediaItem()
            }
        }
    }
}

/** Registers the widget with the system. */
class ResonanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResonanceWidget()
}

/**
 * Connects, runs [block], and releases — every step of it on the main thread.
 *
 * That is the entire point of this helper, and it took three passes to get
 * right. A `MediaController` is not merely *built* on the application thread: it
 * verifies the calling thread on **every** method and throws otherwise. The
 * previous version hopped to the main dispatcher for the builder alone and then
 * returned to a background coroutine to issue commands, so connecting succeeded
 * and the very next property read threw — which from the outside looked exactly
 * like a button that did nothing.
 *
 * The context is the application's rather than the one Glance hands in: widget
 * actions arrive through a BroadcastReceiver, whose ReceiverRestrictedContext
 * refuses bindService, and building a controller binds.
 */
private suspend fun withMediaController(
    context: Context,
    block: (MediaController) -> Unit,
) = withContext(Dispatchers.Main) {
    val appContext = context.applicationContext
    val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))

    val controller = runCatching { awaitController(appContext, token) }
        .onFailure { android.util.Log.w("ResonanceWidget", "connect failed", it) }
        .getOrNull() ?: return@withContext

    try {
        block(controller)
    } catch (error: Exception) {
        android.util.Log.w("ResonanceWidget", "command failed", error)
    } finally {
        controller.release()
    }
}

/** Awaits the connection without pulling in kotlinx-coroutines-guava for one call. */
private suspend fun awaitController(
    context: Context,
    token: SessionToken,
): MediaController = suspendCancellableCoroutine { continuation ->
    val future = MediaController.Builder(context, token).buildAsync()
    future.addListener(
        {
            runCatching { future.get() }
                .onSuccess { continuation.resume(it) { _, _, _ -> it.release() } }
                .onFailure { continuation.cancel(it) }
        },
        com.google.common.util.concurrent.MoreExecutors.directExecutor(),
    )
    continuation.invokeOnCancellation { future.cancel(true) }
}

/** What the session currently holds, read in one main-thread pass. */
internal data class SessionSnapshot(
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val isPlaying: Boolean,
    val hasQueue: Boolean,
)

/**
 * Reads the session once so a freshly placed widget is not born blank. Shares
 * the main-thread and application-context handling above, for the same reasons.
 */
internal suspend fun readSessionSnapshot(context: Context): SessionSnapshot? {
    var snapshot: SessionSnapshot? = null
    withMediaController(context) { controller ->
        val metadata = controller.mediaMetadata
        snapshot = SessionSnapshot(
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUri = metadata.artworkUri?.toString(),
            isPlaying = controller.isPlaying,
            hasQueue = controller.mediaItemCount > 0,
        )
    }
    return snapshot
}
