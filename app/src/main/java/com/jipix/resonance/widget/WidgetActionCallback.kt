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
 * without pinning the service, and pinning the service so a button can respond a
 * few milliseconds sooner is the exact trade this project refuses.
 *
 * Connecting also starts the service if it is not running, which is what makes
 * play work after the app has been swiped away.
 */
class WidgetActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action = parameters[WidgetAction.KEY] ?: return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = runCatching { awaitController(context, token) }.getOrNull() ?: return

        try {
            when (action) {
                WidgetAction.PLAY_PAUSE -> if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }

                WidgetAction.NEXT -> controller.seekToNextMediaItem()
                WidgetAction.PREVIOUS -> controller.seekToPreviousMediaItem()
            }
        } finally {
            controller.release()
        }
    }
}

/** Registers the widget with the system. */
class ResonanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResonanceWidget()
}

/**
 * Awaits a controller connection without pulling in kotlinx-coroutines-guava
 * for one call. The future's listener resumes the coroutine; cancelling the
 * coroutine cancels the connection attempt rather than leaking it.
 */
internal suspend fun awaitController(
    context: Context,
    token: SessionToken,
): MediaController = withContext(Dispatchers.Main) {
    // MediaController.Builder must be called from the main looper. Glance runs
    // action callbacks and provideGlance on background coroutines, so without
    // this hop the connection throws and every widget button silently does
    // nothing — which is exactly how it failed.
    suspendCancellableCoroutine { continuation ->
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
}
