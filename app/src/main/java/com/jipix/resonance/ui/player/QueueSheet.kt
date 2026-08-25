package com.jipix.resonance.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.jipix.resonance.playback.QueueItem
import com.jipix.resonance.ui.library.asClock
import kotlin.math.roundToInt

private val RowHeight = 56.dp

/**
 * The queue, reached by the chevron at the bottom of the player.
 *
 * The list opens scrolled to whatever is playing — a queue is usually the whole
 * library, and landing at track one of two thousand would be useless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<QueueItem>,
    currentIndex: Int,
    positionMs: Long,
    palette: PlayerPalette,
    tapPlays: Boolean,
    closeOnTap: Boolean,
    onPlayItem: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onShuffle: () -> Unit,
    onRemoveDuplicates: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onSetTapPlays: (Boolean) -> Unit,
    onSetCloseOnTap: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Local copy so a drag can reorder immediately; the controller is told as the
    // item crosses each neighbour rather than only on release.
    val items = remember(queue) { queue.toMutableStateList() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    // Bumped by "go to current track"; the effect keys on it so the same request
    // can be made twice in a row.
    var recenter by remember { mutableIntStateOf(0) }
    val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }

    LaunchedEffect(currentIndex, queue.size, recenter) {
        if (currentIndex in items.indices) listState.animateScrollToItem(currentIndex)
    }

    // The sheet paints no container of its own and supplies no handle: both used
    // to sit above the gradient, which is exactly where the hard colour seam
    // appeared. Everything now lives inside one surface with one wash.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Material caps a bottom sheet at 640dp, which in landscape leaves the
        // queue floating in the middle of the screen. This is a full-bleed list.
        sheetMaxWidth = Dp.Unspecified,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(
                    if (palette.tinted) palette.mid else MaterialTheme.colorScheme.surface
                )
                .drawBehind {
                    if (palette.tinted) {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    palette.glow.copy(alpha = 0.5f),
                                    palette.mid,
                                    palette.edge,
                                )
                            )
                        )
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.content.copy(alpha = 0.4f)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "A continuación",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.content,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Opciones de la cola",
                            tint = palette.content,
                        )
                    }
                    QueueMenu(
                        expanded = menuOpen,
                        tapPlays = tapPlays,
                        closeOnTap = closeOnTap,
                        onDismiss = { menuOpen = false },
                        onGoToCurrent = {
                            menuOpen = false
                            recenter++
                        },
                        onShuffle = {
                            menuOpen = false
                            onShuffle()
                        },
                        onRemoveDuplicates = {
                            menuOpen = false
                            onRemoveDuplicates()
                        },
                        onSaveAsPlaylist = {
                            menuOpen = false
                            onSaveAsPlaylist()
                        },
                        onSetTapPlays = onSetTapPlays,
                        onSetCloseOnTap = onSetCloseOnTap,
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                itemsIndexed(items, key = { _, item -> item.index }) { position, item ->
                    val dragging = draggingIndex == position
                    QueueRow(
                        item = item,
                        playing = position == currentIndex,
                        palette = palette,
                        dragging = dragging,
                        dragOffset = if (dragging) dragOffset else 0f,
                        onClick = { onPlayItem(position) },
                        onDragStart = {
                            draggingIndex = position
                            dragOffset = 0f
                        },
                        onDrag = { delta ->
                            dragOffset += delta
                            val from = draggingIndex ?: return@QueueRow
                            val steps = (dragOffset / rowHeightPx).roundToInt()
                            if (steps != 0) {
                                val to = (from + steps).coerceIn(0, items.lastIndex)
                                if (to != from) {
                                    items.move(from, to)
                                    onMoveItem(from, to)
                                    draggingIndex = to
                                    dragOffset -= (to - from) * rowHeightPx
                                }
                            }
                        },
                        onDragEnd = {
                            draggingIndex = null
                            dragOffset = 0f
                        },
                    )
                }
            }

            HorizontalDivider(color = palette.content.copy(alpha = 0.15f))

            QueueTotals(
                items = items,
                currentIndex = currentIndex,
                positionMs = positionMs,
                palette = palette,
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    playing: Boolean,
    palette: PlayerPalette,
    dragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .background(
                if (dragging) palette.content.copy(alpha = 0.10f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            // Tighter to the edges than the library lists: this is a dense,
            // transient list, not a browsing surface.
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.artworkUri,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.Medium else FontWeight.Normal,
                color = if (playing) palette.active else palette.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.subdued,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (playing) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Sonando",
                tint = palette.active,
                modifier = Modifier.size(20.dp),
            )
        }

        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = "Reordenar",
            tint = palette.subdued,
            modifier = Modifier
                .padding(start = 4.dp, end = 8.dp)
                .size(24.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                    )
                },
        )
    }
}

@Composable
private fun QueueMenu(
    expanded: Boolean,
    tapPlays: Boolean,
    closeOnTap: Boolean,
    onDismiss: () -> Unit,
    onGoToCurrent: () -> Unit,
    onShuffle: () -> Unit,
    onRemoveDuplicates: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onSetTapPlays: (Boolean) -> Unit,
    onSetCloseOnTap: (Boolean) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Ir a la pista actual") },
            leadingIcon = { Icon(Icons.Rounded.MyLocation, contentDescription = null) },
            onClick = onGoToCurrent,
        )
        DropdownMenuItem(
            text = { Text("Aleatorizar lo que falta") },
            leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
            onClick = onShuffle,
        )
        DropdownMenuItem(
            text = { Text("Quitar duplicados") },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
            onClick = onRemoveDuplicates,
        )
        DropdownMenuItem(
            text = { Text("Guardar como lista") },
            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
            onClick = onSaveAsPlaylist,
        )

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("Reproducir al tocar") },
            trailingIcon = { Switch(checked = tapPlays, onCheckedChange = onSetTapPlays) },
            onClick = { onSetTapPlays(!tapPlays) },
        )
        DropdownMenuItem(
            text = { Text("Cerrar la cola al tocar") },
            trailingIcon = { Switch(checked = closeOnTap, onCheckedChange = onSetCloseOnTap) },
            onClick = { onSetCloseOnTap(!closeOnTap) },
        )
    }
}

/** BlackPlayer shows what is left and what the whole queue adds up to. */
@Composable
private fun QueueTotals(
    items: List<QueueItem>,
    currentIndex: Int,
    positionMs: Long,
    palette: PlayerPalette,
) {
    val total = items.sumOf { it.durationMs }
    val playedBefore = items.take(currentIndex.coerceAtLeast(0)).sumOf { it.durationMs }
    val remaining = (total - playedBefore - positionMs).coerceAtLeast(0L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${items.size} pistas",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.subdued,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Restan ${remaining.asClock()} · Total ${total.asClock()}",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.subdued,
        )
    }
}

private fun <T> SnapshotStateList<T>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}
