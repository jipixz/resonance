package com.jipix.resonance.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val DurationsMinutes = listOf(15, 30, 45, 60, 90)

/**
 * Picks a duration and whether to cut immediately or let the current track
 * finish; while a timer is already running it shows the time left and offers
 * to cancel it instead. Runs entirely against [PlayerConnection]'s own timer
 * (see its doc comment) — this dialog is just the face on it.
 */
@Composable
fun SleepTimerDialog(
    endAtMs: Long?,
    onSet: (minutes: Int, finishTrack: Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMinutes by remember { mutableIntStateOf(30) }
    var finishTrack by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador de apagado") },
        text = {
            Column {
                if (endAtMs != null) {
                    Text(
                        text = "Se pausará en ${rememberRemainingLabel(endAtMs)}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }

                Text(
                    text = "Duración",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DurationsMinutes.forEach { minutes ->
                        FilterChip(
                            selected = selectedMinutes == minutes,
                            onClick = { selectedMinutes = minutes },
                            label = { Text("${minutes}m") },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Dejar terminar la canción",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Si no, corta de inmediato al cumplirse el tiempo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = finishTrack, onCheckedChange = { finishTrack = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSet(selectedMinutes, finishTrack)
                    onDismiss()
                },
            ) { Text(if (endAtMs != null) "Reiniciar" else "Iniciar") }
        },
        dismissButton = {
            if (endAtMs != null) {
                TextButton(
                    onClick = {
                        onCancel()
                        onDismiss()
                    },
                ) { Text("Cancelar temporizador") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}

/** Ticks once a second purely for display — the timer itself is driven by a
 * fixed target timestamp, not by this. */
@Composable
private fun rememberRemainingLabel(endAtMs: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val remainingMinutes = ((endAtMs - now).coerceAtLeast(0L) / 60_000L + 1).toInt()
    return if (remainingMinutes == 1) "1 min" else "$remainingMinutes min"
}
