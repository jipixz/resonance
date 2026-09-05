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
 * ## Why an intent string and not a constant
 *
 * The output switcher is not in `Settings.Panel`. It is a Settings panel action
 * with no public constant, so the action name is written out and every launch
 * is checked against the package manager first — an unresolvable intent throws,
 * and a control that crashes is worse than one that lands somewhere close.
 *
 * The fall-back chain ends at the volume panel, which on Android 12+ carries the
 * same switcher one tap deeper.
 */
object SystemOutputSwitcher {

    /** Settings' media output dialog — "Se reproducirá en" / "Play on". */
    private const val ACTION_MEDIA_OUTPUT = "com.android.settings.panel.action.MEDIA_OUTPUT"

    /** Tells the dialog whose session to switch, rather than guessing. */
    private const val EXTRA_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"

    /**
     * @return false when nothing on this device could handle any of the
     *   candidates, so the caller can say so rather than appearing to do nothing.
     */
    fun open(context: Context): Boolean {
        val candidates = listOf(
            Intent(ACTION_MEDIA_OUTPUT)
                .putExtra(EXTRA_PACKAGE_NAME, context.packageName),
            // Some OEM builds expose the dialog under the framework-side name.
            Intent("android.settings.MEDIA_OUTPUT")
                .putExtra(EXTRA_PACKAGE_NAME, context.packageName),
            // Last resort: the volume panel, which reaches the same switcher on
            // Android 12+ through its own output button.
            Intent(Settings.Panel.ACTION_VOLUME),
        )

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolvable = context.packageManager
                .resolveActivity(intent, 0) != null
            if (!resolvable) continue
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) return true
        }
        return false
    }
}
