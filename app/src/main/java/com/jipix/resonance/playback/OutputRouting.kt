package com.jipix.resonance.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Choosing which output the player renders to.
 *
 * `setPreferredAudioDevice` lives on `ExoPlayer`, which only [PlaybackService]
 * holds — a `MediaController` has no access to it. So the choice crosses the
 * session boundary as a custom command carrying nothing but a device id, and the
 * service resolves that id against the live device list on its own side.
 *
 * Passing the id rather than the `AudioDeviceInfo` is not just about
 * serialisation: a device can disappear between the user picking it and the
 * service acting, and re-resolving means the service simply finds nothing rather
 * than acting on a stale handle.
 */
object OutputRouting {

    const val ACTION_SET_OUTPUT = "com.jipix.resonance.SET_OUTPUT"
    const val EXTRA_DEVICE_ID = "deviceId"

    /** Sentinel for "stop pinning, follow the system route again". */
    const val DEVICE_AUTOMATIC = -1

    val command = SessionCommand(ACTION_SET_OUTPUT, Bundle.EMPTY)

    fun args(deviceId: Int): Bundle = Bundle().apply { putInt(EXTRA_DEVICE_ID, deviceId) }

    /**
     * The outputs worth offering. Telephony and the earpiece are filtered out —
     * they are routes for calls, not for music, and Android lists them anyway.
     */
    fun availableOutputs(context: Context): List<OutputOption> {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()

        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in MUSIC_OUTPUT_TYPES }
            // One entry per physical thing: a Bluetooth headset shows up twice,
            // once as A2DP and once as SCO, and offering both means offering the
            // user a choice between two names for the same object.
            .distinctBy { device -> device.productName.toString() + device.type.deviceFamily() }
            .map { device ->
                OutputOption(
                    id = device.id,
                    label = device.displayLabel(),
                    kind = device.type.toKind(),
                )
            }
    }
}

data class OutputOption(
    val id: Int,
    val label: String,
    val kind: OutputKind,
)

enum class OutputKind { Speaker, Wired, Bluetooth, Usb }

private val MUSIC_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_HEARING_AID,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
)

/** Groups the two Bluetooth profiles so they collapse into one entry. */
private fun Int.deviceFamily(): String = when (this) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_HEARING_AID -> "bt"

    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb"

    else -> this.toString()
}

private fun Int.toKind(): OutputKind = when (this) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.Speaker
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.Wired
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.Usb

    else -> OutputKind.Bluetooth
}

/**
 * A readable name. `productName` is the phone's own model for built-in routes and
 * for Bluetooth devices whose name needs a permission we may not hold, so those
 * fall back to naming the route instead of naming the phone.
 */
private fun AudioDeviceInfo.displayLabel(): String {
    val product = runCatching { productName?.toString() }.getOrNull().orEmpty().trim()
    val generic = when (type.toKind()) {
        OutputKind.Speaker -> "Altavoz del teléfono"
        OutputKind.Wired -> "Auriculares con cable"
        OutputKind.Usb -> "USB"
        OutputKind.Bluetooth -> "Bluetooth"
    }
    if (product.isBlank()) return generic
    // Built-in routes report the handset's model, which tells the user nothing
    // about which output they are picking.
    if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return generic
    return product
}
