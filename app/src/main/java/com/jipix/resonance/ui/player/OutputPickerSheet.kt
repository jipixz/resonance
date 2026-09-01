package com.jipix.resonance.ui.player

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jipix.resonance.playback.OutputKind
import com.jipix.resonance.playback.OutputOption
import com.jipix.resonance.playback.OutputRouting
import com.jipix.resonance.ui.ResonanceIcons

/**
 * Picks which output the player renders to.
 *
 * "Automático" is the first entry and the default, because pinning an output is
 * the exception: normally you want the phone's own routing, which already moves
 * audio to a headset the moment one is connected. Pinning is for the case where
 * that guess is wrong.
 *
 * The shortcut to the system volume panel stays at the bottom. This screen can
 * choose among outputs the player can already render to; it cannot pair a device
 * or manage one, and pretending otherwise would be worse than a visible handoff.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputPickerSheet(
    palette: PlayerPalette,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    // Read once per opening rather than watched: the list is short-lived, and a
    // device vanishing while the sheet is up resolves to nothing on the service
    // side anyway.
    val outputs = remember { OutputRouting.availableOutputs(context) }
    var selected by remember { mutableStateOf(OutputRouting.DEVICE_AUTOMATIC) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (palette.tinted) palette.mid else MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = "Reproducir en",
                style = MaterialTheme.typography.titleMedium,
                color = palette.content,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )

            OutputRow(
                icon = ResonanceIcons.PhoneAndroid,
                label = "Automático",
                detail = "Sigue la salida del sistema",
                selected = selected == OutputRouting.DEVICE_AUTOMATIC,
                palette = palette,
                onClick = {
                    selected = OutputRouting.DEVICE_AUTOMATIC
                    onPick(OutputRouting.DEVICE_AUTOMATIC)
                },
            )

            outputs.forEach { option ->
                OutputRow(
                    icon = option.kind.icon(),
                    label = option.label,
                    detail = null,
                    selected = selected == option.id,
                    palette = palette,
                    onClick = {
                        selected = option.id
                        onPick(option.id)
                    },
                )
            }

            HorizontalDivider(
                color = palette.content.copy(alpha = 0.15f),
                modifier = Modifier.padding(vertical = 8.dp),
            )

            OutputRow(
                icon = ResonanceIcons.VolumeUp,
                label = "Panel de volumen del sistema",
                detail = "Para emparejar o cambiar de dispositivo",
                selected = false,
                palette = palette,
                onClick = {
                    // Settings.Panel, not ACTION_SOUND_SETTINGS. The panel floats
                    // over whatever is in front and closes back into it; the
                    // settings action launches the whole Settings app and leaves
                    // the user somewhere else entirely. For a control that exists
                    // to be a quick detour, that difference is the whole point.
                    runCatching {
                        context.startActivity(Intent(Settings.Panel.ACTION_VOLUME))
                    }
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun OutputRow(
    icon: ImageVector,
    label: String,
    detail: String?,
    selected: Boolean,
    palette: PlayerPalette,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (selected) palette.active.copy(alpha = 0.20f) else Color.Transparent
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) palette.active else palette.subdued,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) palette.active else palette.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.subdued,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selected) {
            Icon(
                imageVector = ResonanceIcons.Check,
                contentDescription = "Seleccionado",
                tint = palette.active,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Shared with the player's "playing on" row. */
@Composable
fun OutputKind.icon(): ImageVector = when (this) {
    OutputKind.Speaker -> ResonanceIcons.Speaker
    OutputKind.Wired -> ResonanceIcons.Headphones
    OutputKind.Bluetooth -> ResonanceIcons.Bluetooth
    OutputKind.Usb -> ResonanceIcons.Usb
}
