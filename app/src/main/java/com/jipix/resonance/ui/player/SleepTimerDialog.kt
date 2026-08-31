package com.jipix.resonance.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val DurationsMinutes = listOf(15, 30, 45, 60, 90)

// The slider's fraction axis is not spent evenly across the full 5min–12h
// range: each (fraction, minutes) pair below is a control point, and the
// mapping is linear *between* them — so the segment from 0 to 2h gets 35% of
// the drag distance while 8h–12h, four times the real duration, gets only
// 18%. That is what gives short durations a fine dial and long ones a coarse,
// fast-moving one, without needing an actual exponential function to also
// land exactly on round checkpoint hours.
private val CustomCheckpoints = listOf(
    0.00f to 5,
    0.35f to 120, // 2h
    0.60f to 300, // 5h
    0.82f to 480, // 8h
    1.00f to 720, // 12h
)

private fun minutesFromFraction(fraction: Float): Int {
    val t = fraction.coerceIn(0f, 1f)
    for (i in 0 until CustomCheckpoints.lastIndex) {
        val (t0, m0) = CustomCheckpoints[i]
        val (t1, m1) = CustomCheckpoints[i + 1]
        if (t <= t1 || i == CustomCheckpoints.lastIndex - 1) {
            val local = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
            val raw = (m0 + local * (m1 - m0)).roundToInt()
            // Snapping onto a checkpoint itself is deliberate — it is much
            // easier to land a finger within ~10 minutes of "2h" than exactly
            // on the pixel that maps to it.
            return when {
                abs(raw - m0) <= 10 -> m0
                abs(raw - m1) <= 10 -> m1
                else -> ((raw + 2) / 5) * 5
            }
        }
    }
    return CustomCheckpoints.last().second
}

private fun fractionFromMinutes(minutes: Int): Float {
    val clamped = minutes.coerceIn(CustomCheckpoints.first().second, CustomCheckpoints.last().second)
    for (i in 0 until CustomCheckpoints.lastIndex) {
        val (t0, m0) = CustomCheckpoints[i]
        val (t1, m1) = CustomCheckpoints[i + 1]
        if (clamped <= m1) {
            val local = (clamped - m0).toFloat() / (m1 - m0)
            return t0 + local * (t1 - t0)
        }
    }
    return 1f
}

private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "$mins min"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

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
    var customMode by remember { mutableStateOf(false) }
    var customFraction by remember { mutableFloatStateOf(fractionFromMinutes(30)) }

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
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DurationsMinutes.forEach { minutes ->
                        FilterChip(
                            selected = !customMode && selectedMinutes == minutes,
                            onClick = {
                                customMode = false
                                selectedMinutes = minutes
                            },
                            label = { Text("${minutes}m") },
                        )
                    }
                    FilterChip(
                        selected = customMode,
                        onClick = {
                            customFraction = fractionFromMinutes(selectedMinutes)
                            customMode = true
                        },
                        label = { Text("Personalizado") },
                    )
                }

                if (customMode) {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = formatMinutes(selectedMinutes),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Fine at the low end, coarse at the high end — see
                        // CustomCheckpoints. Deliberately not a plain linear
                        // 5min–12h slider: at that scale a whole hour of drag
                        // distance would separate 5 and 10 minutes.
                        Slider(
                            value = customFraction.coerceIn(0f, 1f),
                            onValueChange = {
                                customFraction = it
                                selectedMinutes = minutesFromFraction(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "5 min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "12h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
