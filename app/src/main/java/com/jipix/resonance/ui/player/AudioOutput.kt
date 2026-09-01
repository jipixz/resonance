package com.jipix.resonance.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.jipix.resonance.playback.OutputKind

/**
 * Carries the *kind* of route rather than an icon. Resolving a drawable needs a
 * composition, and this is built from a plain function on a system callback —
 * holding the vector here is what forced that whole path to become @Composable.
 */
data class AudioOutput(val label: String, val kind: OutputKind)

/**
 * Which physical route media audio is currently going out on — Bluetooth
 * (covers a car's hands-free profile too, since Android reports both the same
 * way), wired headphones, or USB. Shows the connected device's own name (car,
 * headphone model, etc.) when it can — on API 31+ that needs
 * `BLUETOOTH_CONNECT`, which this asks for the first time the player screen
 * shows a Bluetooth route; falls back to the generic "Bluetooth" label
 * until/unless it is granted.
 *
 * Null means the built-in speaker — the default route needs no callout, so
 * the row this feeds just doesn't render rather than stating the obvious.
 */
@Composable
fun rememberAudioOutput(): AudioOutput? {
    val context = LocalContext.current

    var bluetoothNameGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> bluetoothNameGranted = granted }

    LaunchedEffect(bluetoothNameGranted) {
        if (!bluetoothNameGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    var output by remember(bluetoothNameGranted) {
        mutableStateOf(currentAudioOutput(context, bluetoothNameGranted))
    }

    // Only wired up while the player screen holding this is actually
    // composed — a route callback sitting registered for the app's whole
    // lifetime would be the kind of always-on cost this project avoids.
    DisposableEffect(context, bluetoothNameGranted) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            onDispose {}
        } else {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    output = currentAudioOutput(context, bluetoothNameGranted)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    output = currentAudioOutput(context, bluetoothNameGranted)
                }
            }
            audioManager.registerAudioDeviceCallback(callback, null)
            onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
        }
    }

    return output
}

private fun currentAudioOutput(context: Context, canReadBluetoothName: Boolean): AudioOutput? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return null
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

    // Priority mirrors what the platform actually routes audio to: an active
    // Bluetooth or wired connection always wins over the built-in speaker.
    //
    // Some OEM builds (confirmed on the ASUS ROG Phone this app is tested on)
    // list a phantom TYPE_BLUETOOTH_SCO entry whose productName is the phone's
    // *own* model — nothing is actually paired. Filtered out by comparing
    // against Build.MODEL/PRODUCT/DEVICE rather than trusted at face value.
    val bluetoothDevice = devices.firstOrNull { device ->
        val isBluetoothType = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_HEARING_AID
        isBluetoothType && !device.namesThisPhone()
    }
    if (bluetoothDevice != null) {
        // productName falls back to a generic placeholder without the
        // permission rather than throwing, but the guard is kept anyway —
        // this is a "nice to have" label, not worth a crash over.
        val name = if (canReadBluetoothName) {
            runCatching { bluetoothDevice.productName?.toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return AudioOutput(name ?: "Bluetooth", OutputKind.Bluetooth)
    }

    val wired = devices.any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
    }
    if (wired) return AudioOutput("Audífonos", OutputKind.Wired)

    val usb = devices.any {
        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    }
    if (usb) return AudioOutput("USB", OutputKind.Usb)

    return null
}

/** Whether this device's reported name is actually just the phone's own. */
private fun AudioDeviceInfo.namesThisPhone(): Boolean {
    val name = runCatching { productName?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: return false
    return listOf(Build.MODEL, Build.PRODUCT, Build.DEVICE).any { self ->
        self.isNotBlank() && (name.contains(self, ignoreCase = true) || self.contains(name, ignoreCase = true))
    }
}
