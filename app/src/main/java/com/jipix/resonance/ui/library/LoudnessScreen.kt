package com.jipix.resonance.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Batch loudness analysis, and the switch that decides whether it also happens
 * quietly behind playback.
 *
 * Two ways to get measurements exist because they suit different people: one
 * pass now and done with it, or let it accumulate as you listen. Neither is
 * right for everyone, so both are here and neither is forced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoudnessScreen(
    totalSongs: Int,
    analysedCount: Int,
    progress: AnalysisProgress,
    normalizeVolume: Boolean,
    analyseOnPlay: Boolean,
    onSetNormalize: (Boolean) -> Unit,
    onSetAnalyseOnPlay: (Boolean) -> Unit,
    onAnalyse: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Volumen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding.plusBottom(bottomInset))
                .padding(horizontal = 24.dp),
        ) {
            SettingRow(
                label = "Igualar volumen",
                detail = "Nivela las pistas medidas contra una referencia común. " +
                    "Solo baja las más fuertes; no puede subir las más bajas.",
                checked = normalizeVolume,
                onCheckedChange = onSetNormalize,
            )

            SettingRow(
                label = "Medir al reproducir",
                detail = "Analiza una pista nueva la primera vez que suena. " +
                    "Apágalo si prefieres decidir tú cuándo se analiza.",
                checked = analyseOnPlay,
                onCheckedChange = onSetAnalyseOnPlay,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Biblioteca analizada",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$analysedCount de $totalSongs pistas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (totalSongs > 0) {
                LinearProgressIndicator(
                    progress = { analysedCount.toFloat() / totalSongs },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    drawStopIndicator = {},
                )
            }

            if (progress.running) {
                Text(
                    text = "Analizando ${progress.done} de ${progress.total}…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = "Puedes salir de esta pantalla; el análisis continúa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else if (progress.total > 0) {
                val analysed = progress.done - progress.failed
                Text(
                    text = if (progress.failed > 0) {
                        "Listo: $analysed medidas, ${progress.failed} sin poder decodificar"
                    } else {
                        "Listo: $analysed medidas"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (progress.running) {
                    OutlinedButton(onClick = onCancel) { Text("Detener") }
                } else {
                    Button(
                        onClick = onAnalyse,
                        enabled = analysedCount < totalSongs,
                    ) {
                        Text(
                            if (analysedCount == 0) "Analizar todo" else "Analizar pendientes"
                        )
                    }
                }
                TextButton(onClick = onClear, enabled = analysedCount > 0) {
                    Text("Borrar análisis")
                }
            }

            Text(
                text = "El análisis decodifica cada archivo una vez y guarda el " +
                    "resultado. Es trabajo del procesador, así que conviene hacerlo " +
                    "con el teléfono cargando. Las pistas nuevas quedan pendientes " +
                    "hasta que vuelvas a analizar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, end = 16.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
