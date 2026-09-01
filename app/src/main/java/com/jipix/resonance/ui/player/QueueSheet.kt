package com.jipix.resonance.ui.player

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.jipix.resonance.playback.QueueItem
import com.jipix.resonance.ui.library.EdgeGlow
import com.jipix.resonance.ui.library.asClock
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val RowHeight = 56.dp

/**
 * Animates to [index], but only across the final [nearbyRows]. A long-distance
 * animated scroll composes and loads artwork for every row it flies past on
 * the way there — visibly janky once the queue is long and the jump is far,
 * even on fast hardware, since it is composition/decoding cost rather than
 * raw scroll performance. Warping most of the distance instantly and
 * animating only the last short stretch keeps the "this was a scroll" feel
 * without paying to realize every row in between.
 */
private suspend fun LazyListState.animateToItemWarping(index: Int, nearbyRows: Int = 10) {
    val distance = index - firstVisibleItemIndex
    if (kotlin.math.abs(distance) > nearbyRows) {
        val warpTarget = if (distance > 0) index - nearbyRows else index + nearbyRows
        scrollToItem(warpTarget.coerceAtLeast(0))
    }
    animateScrollToItem(index)
}

/**
 * The queue, reached by the chevron at the bottom of the player.
 *
 * The list opens scrolled to whatever is playing — a queue is usually the whole
 * library, and landing at track one of two thousand would be useless.
 *
 * A plain full-screen overlay (`AnimatedVisibility` in `MainActivity`, same as
 * the player/search/folders screens) rather than `ModalBottomSheet` — that was
 * tried, but `ModalBottomSheet`'s swipe-to-dismiss plumbing forwards leftover
 * *fling* velocity from the list into the sheet's own drag state whenever a
 * fling reaches the top of the content (`ConsumeSwipeWithinBottomSheetBounds
 * NestedScrollConnection` in M3), which — with only one resting anchor here —
 * reads as the whole sheet, footer included, visibly nudging and springing
 * back. Nothing in `ModalBottomSheetProperties` can turn that off without also
 * giving up the swipe gesture, so this drops the component instead.
 *
 * Swipe-to-dismiss itself is still wanted, so it is rebuilt by hand via the
 * [NestedScrollConnection] below — deliberately narrower than M3's: it only
 * ever reacts to a genuine drag (`NestedScrollSource.UserInput`) pulling down
 * once the list has nothing left above it, and — critically — never forwards
 * *fling* velocity into the dismiss offset the way M3's did. `onPreFling` is
 * only used as the "the drag ended" signal to decide spring-back vs. dismiss,
 * not as a channel for residual velocity, which is exactly the distinction that
 * avoids reintroducing the bug this dropped `ModalBottomSheet` to fix.
 */
@Composable
fun QueueSheet(
    queue: List<QueueItem>,
    currentIndex: Int,
    positionMs: Long,
    isPlaying: Boolean,
    palette: PlayerPalette,
    tapPlays: Boolean,
    closeOnTap: Boolean,
    onPlayItem: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRemoveDuplicates: () -> Unit,
    onClearQueue: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onSetTapPlays: (Boolean) -> Unit,
    onSetCloseOnTap: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
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

    // Opening the sheet deep into a long queue used to animate-scroll through
    // every row between the top and the current track — visibly janky with
    // each row loading its own artwork along the way. Only the *first* landing
    // needs to be instant; a later recentre (track change, "go to current")
    // is a small hop and reads better animated — but even that hop still
    // janked on a long jump ("ir a la pista actual" from far away), so it
    // warps most of the distance and only animates the final short stretch.
    var hasCenteredOnOpen by remember { mutableStateOf(false) }
    // Tapping a row to play it also changes currentIndex, which used to
    // trigger the same recentre — visually indistinguishable from the row
    // jumping to the top of the list, which was never the intent. Set right
    // before the tap's own onPlayItem call, consumed by the very next effect
    // run so it only ever suppresses that one trigger.
    var suppressNextRecenter by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex, queue.size, recenter) {
        if (currentIndex !in items.indices) return@LaunchedEffect
        when {
            !hasCenteredOnOpen -> {
                listState.scrollToItem(currentIndex)
                hasCenteredOnOpen = true
            }
            suppressNextRecenter -> suppressNextRecenter = false
            else -> listState.animateToItemWarping(currentIndex)
        }
    }

    // Rebuilt by hand rather than restored via ModalBottomSheet — see the doc
    // comment above for why. Only a genuine drag past the list's top boundary
    // moves this; fling velocity is deliberately never funnelled into it.
    var dismissOffsetPx by remember { mutableFloatStateOf(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    val dismissScope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }

    // Shared by the nested-scroll connection below (a drag reaching the top of
    // the list) and the pill's own direct drag gesture — same threshold, same
    // "carry the fall instead of handing off to the exit transition" fix as
    // the player screen's drag-to-dismiss, whichever gesture triggered it.
    suspend fun settleDismissDrag(velocityY: Float) {
        if (dismissOffsetPx <= 0f) return
        if (dismissOffsetPx > dismissThresholdPx) {
            animate(
                initialValue = dismissOffsetPx,
                targetValue = sheetHeightPx.takeIf { it > 0f } ?: 2400f,
                initialVelocity = velocityY,
                animationSpec = tween(300, easing = FastOutLinearInEasing),
            ) { value, _ -> dismissOffsetPx = value }
            onDismiss()
        } else {
            animate(
                initialValue = dismissOffsetPx,
                targetValue = 0f,
                initialVelocity = velocityY,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ -> dismissOffsetPx = value }
        }
    }

    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            private fun pull(available: Offset): Offset {
                if (available.y <= 0f) return Offset.Zero
                dismissOffsetPx = (dismissOffsetPx + available.y).coerceAtLeast(0f)
                return Offset(0f, available.y)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || listState.canScrollBackward) {
                    return Offset.Zero
                }
                return pull(available)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                return pull(available)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dismissOffsetPx <= 0f) return Velocity.Zero
                settleDismissDrag(available.y)
                return available
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                .offset { IntOffset(0, dismissOffsetPx.roundToInt()) }
                .nestedScroll(nestedScrollConnection)
                // ModalBottomSheet capped its own height short of the status
                // bar; a plain full-screen Box does not, so this does that
                // job now — otherwise the rounded top and header sit under it.
                .statusBarsPadding()
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
            // The entire header is the drag handle — pill, title and the
            // overflow button alike. Scoping it to the pill alone meant closing
            // the sheet from halfway down the queue required scrolling back to
            // the top first, just to reach a gesture that was always available
            // a few pixels higher.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { dismissScope.launch { settleDismissDrag(0f) } },
                            onDragCancel = { dismissScope.launch { settleDismissDrag(0f) } },
                        ) { change, dragAmount ->
                            change.consume()
                            dismissOffsetPx = (dismissOffsetPx + dragAmount).coerceAtLeast(0f)
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
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = palette.content,
                    )
                }
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
                        palette = palette,
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
                        onClearQueue = {
                            menuOpen = false
                            onClearQueue()
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
            }

            Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth(),
                // Just enough clearance to keep the end-of-list fade off the
                // final row's text — more than this and the row visibly floats
                // away from the divider/totals footer below it.
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.index }) { position, item ->
                    val dragging = draggingIndex == position
                    QueueRow(
                        // Removal used to be a hard cut: the row vanished and
                        // everything under it jumped up a slot. Placement is
                        // animated instead — but only while nothing is being
                        // dragged, since a reorder is already tracking the finger
                        // 1:1 and a second animation on top of that fights it.
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 250),
                            fadeOutSpec = tween(durationMillis = 190),
                            placementSpec = if (draggingIndex != null) {
                                null
                            } else {
                                // Watchable, but no longer languid: the swipe
                                // itself now crosses the whole row, so the gap
                                // closing after it has less patience to spend.
                                spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    visibilityThreshold = IntOffset(1, 1),
                                )
                            },
                        ),
                        item = item,
                        playing = position == currentIndex,
                        isPlaying = isPlaying,
                        palette = palette,
                        dragging = dragging,
                        dragOffset = if (dragging) dragOffset else 0f,
                        onClick = {
                            suppressNextRecenter = true
                            onPlayItem(position)
                        },
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
                        onRemove = {
                            items.removeAt(position)
                            onRemoveItem(position)
                        },
                    )
                }
            }
                EdgeGlow(
                    atTop = !listState.canScrollBackward,
                    atBottom = !listState.canScrollForward,
                    active = listState.isScrollInProgress,
                    color = palette.active,
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueRow(
    modifier: Modifier = Modifier,
    item: QueueItem,
    playing: Boolean,
    isPlaying: Boolean,
    palette: PlayerPalette,
    dragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
) {
    // confirmValueChange (tried first) fires as the anchored-draggable state
    // evaluates candidate targets while the drag is still in progress, not
    // only once the user has actually let go on one — calling onRemove() from
    // inside it fired too early or too often, which is exactly why a fast
    // swipe deleted the row before its own animation had a chance to play,
    // and a gentle one could leave it visually dismissed (red, trash icon)
    // without ever actually being removed. Watching the settled value instead
    // only reacts once a drag has genuinely finished animating into place.
    val dismissState = rememberSwipeToDismissBoxState()
    // settledValue, not currentValue: currentValue flips as soon as an anchor is
    // *committed*, which is roughly halfway across — removing there is what cut
    // the swipe short, the row vanishing while it was still travelling.
    // settledValue only changes once the row has finished animating clear of the
    // edge, so the gesture reads all the way out before the list closes the gap.
    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue != SwipeToDismissBoxValue.Settled) {
            onRemove()
        }
    }
    SwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Quitar de la cola",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .background(
                if (dragging) palette.content.copy(alpha = 0.10f)
                // Swipe-to-dismiss needs an opaque row underneath it, or the
                // sheet's own tinted background shows through the gap the
                // background content is meant to fill during the swipe.
                else if (palette.tinted) palette.mid else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            // Tighter to the edges than the library lists: this is a dense,
            // transient list, not a browsing surface.
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The 40dp row thumbnail doesn't need the source's full embedded
        // resolution — MediaStore's legacy albumart URI serves that as-is, so
        // an explicit small decode target keeps a fast scroll through many
        // distinct albums from paying full-size IO/decode for each one.
        // Precision.INEXACT lets a bitmap already cached at a nearby size
        // (the same album's art shown elsewhere) be reused instead of
        // decoding again just because the target size differs by a pixel.
        val context = LocalContext.current
        AsyncImage(
            model = remember(item.artworkUri) {
                ImageRequest.Builder(context)
                    .data(item.artworkUri)
                    .size(120, 120)
                    .precision(Precision.INEXACT)
                    .build()
            },
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
            PlayingIndicator(
                animate = isPlaying,
                color = palette.active,
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
}

/**
 * The "now playing" marker — five bars echoing the launcher mark's own
 * waveform (short, tall, tallest, tall, short), bouncing at staggered rates
 * while the track is actually playing. Holds at the mark's resting proportions
 * when paused, so a paused-but-current row still reads as "this one" without
 * implying audio is moving.
 */
@Composable
private fun PlayingIndicator(animate: Boolean, color: Color, modifier: Modifier = Modifier) {
    val peaks = remember { floatArrayOf(0.42f, 0.72f, 1f, 0.72f, 0.42f) }
    val durationsMs = remember { intArrayOf(560, 720, 480, 640, 600) }
    val transition = rememberInfiniteTransition(label = "queuePlayingBars")

    val heights = peaks.indices.map { index ->
        if (animate) {
            transition.animateFloat(
                initialValue = peaks[index] * 0.28f,
                targetValue = peaks[index],
                animationSpec = infiniteRepeatable(
                    animation = tween(durationsMs[index], easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            ).value
        } else {
            peaks[index]
        }
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / 9f
        // Centre-to-centre spacing between bars; the whole 5-bar row is
        // centred by placing the first bar's centre this far in from the edge.
        val stride = barWidth * 2f
        val startX = (size.width - stride * 4f) / 2f
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            val x = startX + index * stride
            drawLine(
                color = color,
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun QueueMenu(
    palette: PlayerPalette,
    expanded: Boolean,
    tapPlays: Boolean,
    closeOnTap: Boolean,
    onDismiss: () -> Unit,
    onGoToCurrent: () -> Unit,
    onShuffle: () -> Unit,
    onRemoveDuplicates: () -> Unit,
    onClearQueue: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onSetTapPlays: (Boolean) -> Unit,
    onSetCloseOnTap: (Boolean) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        // The sheet behind it is entirely the cover's colour; a menu in the base
        // theme reads as belonging to a different screen.
        containerColor = if (palette.tinted) palette.mid else MaterialTheme.colorScheme.surface,
    ) {
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
            text = { Text("Vaciar la cola") },
            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            onClick = onClearQueue,
        )
        DropdownMenuItem(
            text = { Text("Guardar como lista") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
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
