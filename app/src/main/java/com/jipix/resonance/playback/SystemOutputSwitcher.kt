package com.jipix.resonance.playback

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Opens the system's own "playing on" device chooser.
 *
 * ## Why this exists instead of routing the audio ourselves
 *
 * The app used to force a route with `ExoPlayer.setPreferredAudioDevice`, and it
 * never sounded right. Watching the sequence on device explained why: tapping
 * "speaker" paused the Bluetooth headset, *resumed on the headset*, and only
 * then jumped to the speaker. `setPreferredAudioDevice` does not take effect
 * synchronously — the track migrates some time later — so pausing around the
 * call pauses the wrong moment entirely, which is why a volume duck, dropping
 * offload, and a pause/resume all failed in turn. Each attempt was aimed at a
 * transition that had not happened yet.
 *
 * The system switcher has none of that problem: it owns the routing, so it can
 * fade and hand off at the actual moment of change. This is also what every
 * other player that gets this right is doing.
 *
 * ## Why raw intent strings
 *
 * The switcher has no public constant and is not where its name suggests: it is
 * a SystemUI broadcast receiver, not a Settings activity. Querying the device
 * is what settled it. Both candidates are checked against the package manager
 * before use, since a control that crashes is worse than one that lands
 * somewhere close.
 */
object SystemOutputSwitcher {

    /**
     * SystemUI's output dialog — the one ASUS's own volume-panel button opens,
     * and the one behind "Reproducir Resonance en" in the volume panel.
     *
     * It is a *broadcast receiver*, not an activity. Querying the device settled
     * that: nothing answers `com.android.settings.panel.action.MEDIA_OUTPUT` as
     * an activity, while `MediaOutputDialogReceiver` answers this as a
     * broadcast. Trying to `startActivity` it is why the first attempt fell all
     * the way through to the volume panel and cost an extra tap.
     */
    private const val ACTION_MEDIA_OUTPUT_DIALOG =
        "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"

    private const val SYSTEM_UI = "com.android.systemui"

    /** Tells the dialog whose session to show, so it opens with our track in it. */
    private const val EXTRA_PACKAGE_NAME = "package_name"

    /**
     * @return false when neither the dialog nor the volume panel could be
     *   reached, so the caller can say so rather than appearing to do nothing.
     */
    fun open(context: Context): Boolean {
        val dialog = Intent(ACTION_MEDIA_OUTPUT_DIALOG)
            .setPackage(SYSTEM_UI)
            .putExtra(EXTRA_PACKAGE_NAME, context.packageName)

        val hasDialog = context.packageManager
            .queryBroadcastReceivers(dialog, 0)
            .isNotEmpty()

        if (hasDialog && runCatching { context.sendBroadcast(dialog) }.isSuccess) {
            return true
        }

        // The volume panel reaches the same dialog one tap deeper, which is a
        // worse experience but a working one.
        val panel = Intent(Settings.Panel.ACTION_VOLUME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(panel, 0) == null) return false
        return runCatching { context.startActivity(panel) }.isSuccess
    }
}
