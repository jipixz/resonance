package com.jipix.resonance.ui.player

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.jipix.resonance.core.InfoLine
import com.jipix.resonance.playback.PlaybackUiState
import com.jipix.resonance.playback.QueueItem
import com.jipix.resonance.ui.library.asClock
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.jipix.resonance.ui.ResonanceIcons

/** The artwork and metadata as one unit, so a track change animates them together. */
private data class TrackVisual(
    val index: Int,
    val title: String,
    val artist: String,
)

/** Everything the screen's colours are derived from, computed once per frame. */
data class PlayerPalette(
    val glow: Color,
    val mid: Color,
    val edge: Color,
    val content: Color,
    val subdued: Color,
    val active: Color,
    val tinted: Boolean,
)

/**
 * The full-screen player.
 *
 * When the artwork tint is on, the cover's colour is not just a background: it is
 * the whole screen's palette — the glow behind the art, the slider, the play
 * button, the text. Everything derives from one extracted colour so the screen
 * reads as belonging to the record that is playing.
 */
@Composable
fun PlayerScreen(
    state: PlaybackUiState,
    infoLine: InfoLine,
    onCycleInfoLine: () -> Unit,
    artworkTint: Boolean,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onSetSleepTimer: (minutes: Int, finishTrack: Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
    queueItemAt: (Int) -> QueueItem?,
    onSeekQueueIndex: (Int) -> Unit,
    onOpenOutputPicker: () -> Unit,
    onOpenAlbum: () -> Unit,
    /**
     * False while the queue is open over this screen. Both surfaces carry their
     * own vertical drag-to-dismiss, and with the player's still live underneath
     * one downward drag could dismiss the queue *and* the player behind it,
     * skipping a layer. Layers close one at a time or not at all.
     */
    dismissEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Drag-to-dismiss.
    //
    // The offset is an Animatable rather than a plain float so that releasing can
    // *continue* the movement. Snapping it back to zero on release — and letting
    // the exit transition start over from the top — is what read as choppy: the
    // screen jumped back up before it fell.
    val dismissThreshold = with(LocalDensity.current) { 140.dp.toPx() }
    val offsetY = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    var heightPx by remember { mutableFloatStateOf(0f) }
    var showSleepTimer by remember { mutableStateOf(false) }

    val palette = rememberPlayerPalette(state.artworkUri, artworkTint)
    val output = rememberAudioOutput()
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .pointerInput(dismissEnabled) {
                if (!dismissEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        dragScope.launch {
                            if (offsetY.value > dismissThreshold) {
                                // Carry it the rest of the way down at the speed
                                // it was already moving, then unmount. One
                                // continuous fall instead of a bounce and a drop.
                                offsetY.animateTo(
                                    targetValue = heightPx.takeIf { it > 0f } ?: 2400f,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutLinearInEasing,
                                    ),
                                    initialVelocity = offsetY.velocity,
                                )
                                onCollapse()
                            } else {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                    initialVelocity = offsetY.velocity,
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        dragScope.launch { offsetY.animateTo(0f, spring()) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    // Downward only; dragging up should do nothing.
                    dragScope.launch {
                        offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                    }
                }
            }
            // The glow is centred where the cover sits rather than on the screen,
            // so the colour reads as coming off the artwork.
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(palette.glow, palette.mid, palette.edge),
                        center = Offset(
                            x = if (landscape) size.width * 0.28f else size.width / 2f,
                            y = size.height * if (landscape) 0.5f else 0.42f,
                        ),
                        radius = size.maxDimension * 0.78f,
                    )
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            PlayingFromHeader(
                album = state.album,
                onOpenAlbum = onOpenAlbum.takeIf { state.albumId > 0 },
                palette = palette,
                sleepTimerActive = state.sleepTimerEndAtMs != null,
                onCollapse = onCollapse,
                onOpenSleepTimer = { showSleepTimer = true },
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            if (landscape) {
                LandscapeBody(
                    state = state,
                    palette = palette,
                    output = output,
                    queueItemAt = queueItemAt,
                    onSeekQueueIndex = onSeekQueueIndex,
                    infoLine = infoLine,
                    onCycleInfoLine = onCycleInfoLine,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onOpenOutputPicker = onOpenOutputPicker,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PortraitBody(
                    state = state,
                    palette = palette,
                    output = output,
                    queueItemAt = queueItemAt,
                    onSeekQueueIndex = onSeekQueueIndex,
                    infoLine = infoLine,
                    onCycleInfoLine = onCycleInfoLine,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onOpenOutputPicker = onOpenOutputPicker,
                    modifier = Modifier.weight(1f),
                )
            }

            // BlackPlayer's tell: a small chevron at the bottom centre that pulls
            // the queue up.
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp),
            ) {
                Icon(
                    imageVector = ResonanceIcons.KeyboardArrowUp,
                    contentDescription = "Cola",
                    tint = palette.subdued,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            endAtMs = state.sleepTimerEndAtMs,
            onSet = onSetSleepTimer,
            onCancel = onCancelSleepTimer,
            onDismiss = { showSleepTimer = false },
        )
    }
}

@Composable
private fun PortraitBody(
    state: PlaybackUiState,
    palette: PlayerPalette,
    output: AudioOutput?,
    queueItemAt: (Int) -> QueueItem?,
    onSeekQueueIndex: (Int) -> Unit,
    infoLine: InfoLine,
    onCycleInfoLine: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenOutputPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // A fixed floor of breathing room under the header, on top of the
        // weighted spacer below — the weight alone still let the cover ride
        // right up against the header text on shorter screens.
        Spacer(Modifier.height(24.dp))

        // Weighted 1.4 above against 1 below so the cover sits below the optical
        // centre rather than riding high against the header.
        Spacer(Modifier.weight(1.4f))

        // Deliberately not inside the 24dp-padded column below: the current
        // cover fills exactly the same width it always did (the pager's own
        // contentPadding reproduces that inset), but the *carousel* itself
        // runs edge-to-edge, so the neighbouring covers peek into what used
        // to be dead margin at the true screen edge instead of eating into
        // the current cover's own size.
        CoverCarousel(
            state = state,
            palette = palette,
            queueItemAt = queueItemAt,
            onSeekQueueIndex = onSeekQueueIndex,
            edgePeek = 24.dp,
            sizeFromWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(32.dp))

            TrackBlock(state = state, palette = palette)

            Spacer(Modifier.height(24.dp))

            Scrubber(state = state, onSeek = onSeek, palette = palette)
            Spacer(Modifier.height(8.dp))
            TransportControls(
                state = state,
                palette = palette,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )
            InfoLineText(
                state = state,
                infoLine = infoLine,
                onCycle = onCycleInfoLine,
                color = palette.subdued,
            )
        }

        // Centred in the space between the controls and the queue chevron
        // below this whole block, rather than crowding either one.
        Spacer(Modifier.weight(1f))
        PlayingOnRow(output = output, palette = palette, onClick = onOpenOutputPicker)
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Landscape puts the controls on the left and the record on the right. A portrait
 * layout rotated is just a giant cover with everything crushed underneath it.
 */
@Composable
private fun LandscapeBody(
    state: PlaybackUiState,
    palette: PlayerPalette,
    output: AudioOutput?,
    queueItemAt: (Int) -> QueueItem?,
    onSeekQueueIndex: (Int) -> Unit,
    infoLine: InfoLine,
    onCycleInfoLine: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenOutputPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // Landscape lost the screen's blanket 24dp inset when the cover
        // carousel needed to bleed past it in portrait — this is what
        // restores it here, where there is no edge to bleed into anyway.
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CoverCarousel(
                state = state,
                palette = palette,
                queueItemAt = queueItemAt,
                onSeekQueueIndex = onSeekQueueIndex,
                edgePeek = 12.dp,
                sizeFromWidth = false,
                // Sized off the available height, not the width, or the
                // square would overflow the short axis.
                modifier = Modifier.fillMaxHeight(0.62f),
            )
            Spacer(Modifier.height(20.dp))
            TrackBlock(state = state, palette = palette, centred = true)
        }

        Spacer(Modifier.width(32.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Scrubber(state = state, onSeek = onSeek, palette = palette)
            Spacer(Modifier.height(8.dp))
            TransportControls(
                state = state,
                palette = palette,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )
            InfoLineText(
                state = state,
                infoLine = infoLine,
                onCycle = onCycleInfoLine,
                color = palette.subdued,
            )
            Spacer(Modifier.height(12.dp))
            PlayingOnRow(output = output, palette = palette, onClick = onOpenOutputPicker)
        }
    }
}

/**
 * Title and artist, animated as their own slide when the track changes. Used
 * to also carry the cover in the same `AnimatedContent`, but that fought with
 * [CoverCarousel]'s own drag-driven paging once the cover became swipeable —
 * two systems both trying to own the same horizontal motion. The cover now
 * animates entirely on its own; this only ever handles the text.
 */
@Composable
private fun TrackBlock(
    state: PlaybackUiState,
    palette: PlayerPalette,
    centred: Boolean = false,
) {
    AnimatedContent(
        targetState = TrackVisual(
            index = state.queueIndex,
            title = state.title,
            artist = state.artist,
        ),
        transitionSpec = {
            // Advancing pushes the outgoing track off to the left and brings the
            // new one in from the right; going back reverses it.
            val forward = targetState.index >= initialState.index
            val direction = if (forward) 1 else -1
            (slideInHorizontally { width -> direction * width } + fadeIn()) togetherWith
                (slideOutHorizontally { width -> -direction * width } + fadeOut())
        },
        label = "track",
    ) { visual ->
        Column(
            horizontalAlignment = if (centred) {
                Alignment.CenterHorizontally
            } else {
                Alignment.Start
            },
        ) {
            MarqueeText(
                text = visual.title,
                style = MaterialTheme.typography.headlineSmall,
                color = palette.content,
                centred = centred,
            )
            Text(
                text = visual.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.subdued,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

/**
 * The cover as a real drag-along carousel — Deezer's, not a detect-then-jump
 * gesture (tried first; without the finger actually moving anything until
 * release, it read as unresponsive rather than swipeable). A finished drag
 * that settles on a neighbouring page is what actually changes the track —
 * [onSeekQueueIndex] fires from watching [PagerState.settledPage], which only
 * updates once a scroll/fling genuinely finishes, so a mid-drag position
 * never fires it. External track changes (buttons, auto-advance) are pushed
 * back the other way with `animateScrollToPage`, guarded by
 * `!isScrollInProgress` so they never fight a gesture already in flight.
 */
@Composable
private fun CoverCarousel(
    state: PlaybackUiState,
    palette: PlayerPalette,
    queueItemAt: (Int) -> QueueItem?,
    onSeekQueueIndex: (Int) -> Unit,
    edgePeek: Dp,
    // Portrait gives this a fixed *width* (the screen, edge to edge) and lets
    // the square's height follow; landscape gives it a fixed *height* (a
    // fraction of the available height) and lets the square's width follow.
    // contentPadding only ever eats into the horizontal axis, so whichever
    // dimension the caller *didn't* fix has to be derived by hand below, or
    // the peek carves into one axis without the other matching — a stretched
    // cover, not a square one.
    sizeFromWidth: Boolean,
    modifier: Modifier = Modifier,
) {
    if (state.queueCount <= 0) {
        // No pager here to derive the square from the caller's fixed
        // dimension (see the sizeFromWidth doc above), so this establishes it
        // directly instead.
        CoverBox(uri = state.artworkUri, palette = palette, modifier = modifier.aspectRatio(1f))
        return
    }

    val pagerState = rememberPagerState(initialPage = state.queueIndex) { state.queueCount }

    LaunchedEffect(state.queueIndex, state.queueCount) {
        val target = state.queueIndex.coerceIn(0, state.queueCount - 1)
        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
            // The pager's own default spring covers one page almost instantly —
            // fine for a manual fling, but a button tap has no finger motion to
            // read the speed off of, so the snap-cut read as jarring. A fixed
            // tween gives every button-triggered page change the same, slower,
            // deliberate motion regardless of distance.
            pagerState.animateScrollToPage(
                page = target,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            )
        }
    }
    // Both of these are read through rememberUpdatedState rather than captured
    // directly, and that is not defensive style — it is the fix for a real bug.
    //
    // This effect is keyed on `pagerState`, which never changes, so it launches
    // exactly once and its lambda closes over whatever `state` was at that
    // moment. Reading `state.queueIndex` inside meant comparing the settled page
    // against the index the queue had when the player was *opened*, frozen
    // forever after. Swiping onto that one index compared equal, the seek never
    // fired, and the track silently refused to change — while every other index
    // worked, which is exactly why it looked like one cursed album rather than a
    // logic error.
    val currentIndex by rememberUpdatedState(state.queueIndex)
    val seekToIndex by rememberUpdatedState(onSeekQueueIndex)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (settled != currentIndex) seekToIndex(settled)
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val pageSize = if (sizeFromWidth) maxWidth - edgePeek * 2 else maxHeight
        val pagerWidth = if (sizeFromWidth) maxWidth else pageSize + edgePeek * 2
        val pagerHeight = if (sizeFromWidth) pageSize else maxHeight

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.width(pagerWidth).height(pagerHeight),
            // This is the whole trick: each page's own width is the pager's
            // width minus this padding, so making it equal to whatever margin
            // the caller would otherwise have reserved is what keeps the
            // current cover exactly the size it always was — the neighbours
            // peek into that margin rather than shrinking the current cover
            // to make room for themselves.
            contentPadding = PaddingValues(horizontal = edgePeek),
            pageSpacing = 8.dp,
        ) { page ->
            // Deliberately never reads state.artworkUri here, even for the page
            // that is currently "current" — that field arrives from the real
            // player's own callback, one step behind state.queueIndex advancing,
            // and mixing it in produced a one-frame flash of the *previous*
            // track's art on the page that had already become current.
            // queueItemAt is plain queue data with no such lag, so every page —
            // neighbours and current alike — reads from the same single source.
            val uri = remember(page, state.queueCount) { queueItemAt(page)?.artworkUri }
            CoverBox(uri = uri, palette = palette, modifier = Modifier.fillMaxSize())
        }
    }
}

/** No `aspectRatio` here — the caller's [modifier] already establishes the
 * square (see [CoverCarousel]'s own callers), and applying it a second time
 * to a page already constrained by the pager's own size does not behave the
 * same way as applying it once to the pager itself. */
@Composable
private fun CoverBox(uri: String?, palette: PlayerPalette, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.content.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        CoverImage(uri = uri, palette = palette)
    }
}

@Composable
private fun CoverImage(uri: String?, palette: PlayerPalette) {
    Icon(
        imageVector = ResonanceIcons.Album,
        contentDescription = null,
        tint = palette.subdued,
        modifier = Modifier.size(64.dp),
    )
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * A long title scrolls sideways instead of wrapping. Wrapping would push every
 * control below it down by a line, so the layout would shift depending on which
 * track happened to be playing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    centred: Boolean,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = if (centred) TextAlign.Center else TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(iterations = Int.MAX_VALUE),
    )
}

@Composable
private fun PlayingFromHeader(
    album: String,
    onOpenAlbum: (() -> Unit)?,
    palette: PlayerPalette,
    sleepTimerActive: Boolean,
    onCollapse: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Pushed down off the status bar so it does not read as a title bar.
            // Symmetric so the text truly sits centred between the icon
            // buttons flanking it, rather than skewed toward the top.
            .padding(vertical = 12.dp),
    ) {
        IconButton(
            onClick = onCollapse,
            // Pulled back toward the true screen edge — the screen's own 24dp
            // horizontal padding otherwise leaves it stranded well past where a
            // collapse/back affordance normally sits.
            modifier = Modifier.align(Alignment.CenterStart).offset(x = (-16).dp),
        ) {
            Icon(
                imageVector = ResonanceIcons.KeyboardArrowDown,
                contentDescription = "Contraer",
                tint = palette.content,
            )
        }

        IconButton(
            onClick = onOpenSleepTimer,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 16.dp),
        ) {
            Icon(
                imageVector = ResonanceIcons.Bedtime,
                contentDescription = "Temporizador de apagado",
                // Same on/dim-off language as the skip buttons at the ends of
                // the queue — one icon, colour is the only thing that toggles.
                // Swapping to a crossed-out glyph for "off" read as broken
                // rather than "not currently set".
                tint = if (sleepTimerActive) palette.active else palette.content.copy(alpha = 0.28f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                // Only clickable when there is an album to open. A dead tap
                // target that looks alive is worse than one that is plainly not
                // interactive.
                .then(
                    if (onOpenAlbum != null) Modifier.clickable(onClick = onOpenAlbum)
                    else Modifier
                )
                .padding(horizontal = 48.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "REPRODUCIENDO DE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp,
                ),
                color = palette.subdued,
            )
            Text(
                text = album,
                style = MaterialTheme.typography.labelLarge,
                color = palette.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Spotify's "playing on" line. Tapping it now opens this app's own output
 * picker rather than handing straight off to the system panel — the picker
 * still offers that panel as its last row, for pairing and anything else
 * choosing among existing routes cannot do.
 */
@Composable
private fun PlayingOnRow(
    output: AudioOutput?,
    palette: PlayerPalette,
    onClick: () -> Unit,
) {
    // Rendered even with nothing plugged in. This row is the only way into the
    // output picker, and hiding it on the built-in speaker made the picker
    // unreachable in exactly the situation where someone might want to send the
    // audio somewhere else.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = output?.kind?.icon() ?: ResonanceIcons.Speaker,
            contentDescription = null,
            tint = palette.subdued,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = output?.label ?: "Altavoz del teléfono",
            style = MaterialTheme.typography.labelSmall,
            color = palette.subdued,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * The configurable line under the controls, in the spirit of BlackPlayer's.
 * Tapping it advances to the next mode, and the swap crossfades so the change
 * does not read as a glitch.
 */
@Composable
private fun InfoLineText(
    state: PlaybackUiState,
    infoLine: InfoLine,
    onCycle: () -> Unit,
    color: Color,
) {
    val text = when (infoLine) {
        InfoLine.Format -> state.format
        InfoLine.Album -> state.album
        InfoLine.NextTrack -> state.nextTitle.takeIf { it.isNotBlank() }
            ?.let { "Siguiente: $it" }
            .orEmpty()
        InfoLine.None -> ""
    }

    // The slot keeps its height even when the mode has nothing to say: the row
    // is the tap target, so an empty one still has to be there to tap.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onCycle)
            .heightIn(min = 34.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = text, label = "infoLine") { shown ->
            Text(
                text = shown,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.6.sp,
                ),
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * While a drag is in flight the slider follows the finger, not the player. Without
 * that local override the 500 ms position tick would yank the thumb back mid-drag.
 */
@Composable
private fun Scrubber(state: PlaybackUiState, onSeek: (Long) -> Unit, palette: PlayerPalette) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val shown = scrubbing ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column {
        Slider(
            value = shown,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { onSeek((it * duration).toLong()) }
                scrubbing = null
            },
            colors = SliderDefaults.colors(
                thumbColor = palette.active,
                activeTrackColor = palette.active,
                inactiveTrackColor = palette.content.copy(alpha = 0.22f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = (shown * duration).toLong().asClock(),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.subdued,
            )
            Text(
                text = state.durationMs.asClock(),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.subdued,
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: PlaybackUiState,
    palette: PlayerPalette,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    // Related to the play button's colour rather than the plain body text
    // colour, but quieter — full palette.active on a small icon would compete
    // with the filled play button for attention.
    val skipTint = palette.active.desaturate(0.55f)
    // Distinctly greyer than palette.subdued (used for merely secondary text)
    // so the start/end of the queue with repeat off reads as genuinely inert.
    val disabledSkipTint = palette.content.copy(alpha = 0.28f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = ResonanceIcons.Shuffle,
                contentDescription = "Aleatorio",
                tint = if (state.shuffleEnabled) palette.active else palette.subdued,
            )
        }

        IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
            Icon(
                imageVector = ResonanceIcons.SkipPrevious,
                // Greyed out at the start of the queue with repeat off, so
                // reaching the end reads without having to open it.
                contentDescription = "Anterior",
                tint = if (state.hasPrevious) skipTint else disabledSkipTint,
                modifier = Modifier.size(36.dp),
            )
        }

        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = palette.active,
                contentColor = palette.active.readableOn(),
            ),
        ) {
            Icon(
                imageVector = if (state.isPlaying) ResonanceIcons.Pause else ResonanceIcons.PlayArrow,
                contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                modifier = Modifier.size(36.dp),
            )
        }

        IconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(
                imageVector = ResonanceIcons.SkipNext,
                contentDescription = "Siguiente",
                tint = if (state.hasNext) skipTint else disabledSkipTint,
                modifier = Modifier.size(36.dp),
            )
        }

        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    ResonanceIcons.RepeatOne
                } else {
                    ResonanceIcons.Repeat
                },
                contentDescription = "Repetir",
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                    palette.subdued
                } else {
                    palette.active
                },
            )
        }
    }
}

/**
 * Shared by the player and the queue sheet so both sides of the same session are
 * lit by the same colour.
 */
@Composable
fun rememberPlayerPalette(artworkUri: String?, artworkTint: Boolean): PlayerPalette {
    val accent = rememberArtworkAccent(artworkUri, artworkTint)
    val fallbackSurface = MaterialTheme.colorScheme.surface
    val fallbackContent = MaterialTheme.colorScheme.onSurface
    val fallbackAccent = MaterialTheme.colorScheme.primary

    // Animated so moving between tracks eases from one record's colour to the
    // next instead of cutting.
    val tint by animateColorAsState(
        targetValue = accent ?: fallbackSurface,
        animationSpec = tween(durationMillis = 520),
        label = "playerTint",
    )

    val tinted = accent != null
    val mid = if (tinted) tint.darken(0.74f) else fallbackSurface
    val content = if (tinted) mid.readableOn() else fallbackContent

    return PlayerPalette(
        glow = if (tinted) tint.darken(0.42f) else fallbackSurface,
        mid = mid,
        edge = if (tinted) tint.darken(0.93f) else fallbackSurface,
        content = content,
        subdued = content.copy(alpha = 0.62f),
        active = if (tinted) tint.lighten(0.22f) else fallbackAccent,
        tinted = tinted,
    )
}
