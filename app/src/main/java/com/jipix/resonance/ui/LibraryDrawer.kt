package com.jipix.resonance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jipix.resonance.R
import com.jipix.resonance.core.Settings
import com.jipix.resonance.ui.theme.WordmarkFont
import com.jipix.resonance.ui.ResonanceIcons

/**
 * The navigation drawer, in the shape Gmail and Calendar use: a titled header,
 * icon-led destinations, then grouped settings. The destinations drive the same
 * pager the top tabs do, so the two stay in sync rather than competing.
 */
@Composable
fun LibraryDrawer(
    settings: Settings,
    songCount: Int,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenLoudness: () -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAmoled: (Boolean) -> Unit,
    onSetArtworkTint: (Boolean) -> Unit,
    onSetCrossfade: (Boolean) -> Unit,
    onSetCrossfadeSeconds: (Int) -> Unit,
) {
    ModalDrawerSheet {
        val accent = MaterialTheme.colorScheme.primary
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                // The wash is painted here rather than on the header row, which
                // is what the last two attempts got wrong: a background only
                // covers the composable that draws it, so the falloff was being
                // clipped flat at the row's bottom edge — that clip *was* the
                // visible cut. Given the whole sheet to fade across, the radial
                // finally has somewhere to go.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.16f),
                                accent.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.18f, 0f),
                            radius = size.height * 0.42f,
                        )
                    )
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 28.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_resonance_mark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = "Resonance",
                        // The one place the script font appears.
                        fontFamily = WordmarkFont,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (songCount == 1) "1 canción" else "$songCount canciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Destination(ResonanceIcons.MusicNote, "Canciones", selectedTab == 0) { onSelectTab(0) }
            Destination(ResonanceIcons.Album, "Álbumes", selectedTab == 1) { onSelectTab(1) }
            Destination(ResonanceIcons.Person, "Artistas", selectedTab == 2) { onSelectTab(2) }

            SectionDivider()

            Destination(ResonanceIcons.Folder, "Carpetas", selected = false, onClick = onOpenFolders)
            Destination(
                ResonanceIcons.VolumeUp,
                "Volumen",
                selected = false,
                onClick = onOpenLoudness,
            )

            SectionDivider()
            SectionLabel("Apariencia")

            DrawerSwitch(
                label = "Color del fondo de pantalla",
                checked = settings.dynamicColor,
                onCheckedChange = onSetDynamicColor,
            )
            DrawerSwitch(
                label = "Negro real (AMOLED)",
                checked = settings.amoled,
                onCheckedChange = onSetAmoled,
            )
            DrawerSwitch(
                label = "Teñir con la carátula",
                checked = settings.artworkTint,
                onCheckedChange = onSetArtworkTint,
            )

            SectionDivider()
            SectionLabel("Reproducción")

            DrawerSwitch(
                label = "Crossfade",
                checked = settings.crossfade,
                onCheckedChange = onSetCrossfade,
            )

            if (settings.crossfade) {
                Row(
                    modifier = Modifier.padding(start = 28.dp, end = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(3, 6, 10).forEach { seconds ->
                        FilterChip(
                            selected = settings.crossfadeSeconds == seconds,
                            onClick = { onSetCrossfadeSeconds(seconds) },
                            label = { Text("$seconds s") },
                        )
                    }
                }
                Text(
                    text = "Mezcla el final de una pista con el inicio de la siguiente. " +
                        "Apaga la descarga de audio al DSP, así que gasta más batería.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, end = 20.dp, bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Destination(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
}

@Composable
private fun DrawerSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
