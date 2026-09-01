package com.jipix.resonance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jipix.resonance.R
import com.jipix.resonance.core.Settings
import com.jipix.resonance.ui.theme.WordmarkFont

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
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAmoled: (Boolean) -> Unit,
    onSetArtworkTint: (Boolean) -> Unit,
    onSetCrossfade: (Boolean) -> Unit,
    onSetCrossfadeSeconds: (Int) -> Unit,
) {
    // The logo is a five-bar waveform, symmetrical about a tall centre. The sheet
    // borrows that shape rather than merely displaying it: the trailing edge is
    // cut to the same silhouette, so the drawer reads as a piece of the mark
    // sliding in from the side.
    ModalDrawerSheet(
        drawerShape = WaveformEdgeShape,
        modifier = Modifier.fillMaxWidth(0.82f),
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // A faint wash of the accent behind the header, brightest at
                    // the mark and falling away — the same gesture the player
                    // makes with the cover's colour.
                    .background(
                        Brush.horizontalGradient(
                            0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            1f to Color.Transparent,
                        )
                    )
                    .padding(start = 28.dp, top = 28.dp, bottom = 20.dp),
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

            Destination(Icons.Rounded.MusicNote, "Canciones", selectedTab == 0) { onSelectTab(0) }
            Destination(Icons.Rounded.Album, "Álbumes", selectedTab == 1) { onSelectTab(1) }
            Destination(Icons.Rounded.Person, "Artistas", selectedTab == 2) { onSelectTab(2) }

            SectionDivider()

            Destination(Icons.Rounded.Folder, "Carpetas", selected = false, onClick = onOpenFolders)

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

/**
 * The drawer's trailing edge, cut to the logo's waveform: five rounded bars
 * mirrored about a tall centre. Drawn as a path rather than an image so it scales
 * with the sheet's height and stays crisp at any size.
 *
 * Kept shallow on purpose — a deep cut would eat into the row labels. This is a
 * silhouette you notice, not one you have to navigate around.
 */
private val WaveformEdgeShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val depth = with(density) { 10.dp.toPx() }
        val corner = with(density) { 16.dp.toPx() }
        val edge = size.width - depth
        // Five bars, so four peaks and troughs across the height; the relative
        // heights match the logo's 3-5-7-5-3 rhythm.
        val bars = listOf(0.32f, 0.62f, 1f, 0.62f, 0.32f)

        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(edge, 0f)

            val step = size.height / bars.size
            bars.forEachIndexed { index, amount ->
                val top = step * index
                val bottom = top + step
                val out = edge + depth * amount
                // A rounded bump per bar rather than a hard tooth: the logo's
                // strokes are round-capped, and a sharp sawtooth would read as a
                // different mark entirely.
                cubicTo(out, top + step * 0.18f, out, bottom - step * 0.18f, edge, bottom)
            }

            lineTo(edge, size.height)
            lineTo(corner, size.height)
            quadraticTo(0f, size.height, 0f, size.height - corner)
            lineTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}
