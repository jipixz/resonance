package com.jipix.resonance.ui.player

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.jipix.resonance.core.InfoLine
import com.jipix.resonance.playback.PlaybackUiState
import com.jipix.resonance.ui.library.asClock
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The artwork and metadata as one unit, so a track change animates them together. */
private data class TrackVisual(
    val index: Int,
    val artworkUri: String?,
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

    val palette = rememberPlayerPalette(state.artworkUri, artworkTint)
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
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
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            PlayingFromHeader(
                album = state.album,
                palette = palette,
                onCollapse = onCollapse,
            )

            if (landscape) {
                LandscapeBody(
                    state = state,
                    palette = palette,
                    infoLine = infoLine,
                    onCycleInfoLine = onCycleInfoLine,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PortraitBody(
                    state = state,
                    palette = palette,
                    infoLine = infoLine,
                    onCycleInfoLine = onCycleInfoLine,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
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
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Cola",
                    tint = palette.subdued,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PortraitBody(
    state: PlaybackUiState,
    palette: PlayerPalette,
    infoLine: InfoLine,
    onCycleInfoLine: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Weighted 1.4 above against 1 below so the cover sits below the optical
        // centre rather than riding high against the header.
        Spacer(Modifier.weight(1.4f))

        TrackBlock(state = state, palette = palette) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.content.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                CoverImage(uri = it, palette = palette)
            }
        }

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
    infoLine: InfoLine,
    onCycleInfoLine: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TrackBlock(state = state, palette = palette, centred = true) {
                Box(
                    modifier = Modifier
                        // Sized off the available height, not the width, or the
                        // square would overflow the short axis.
                        .fillMaxHeight(0.62f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.content.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverImage(uri = it, palette = palette)
                }
            }
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
        }
    }
}

/** Cover plus title and artist, animated as one when the track changes. */
@Composable
private fun TrackBlock(
    state: PlaybackUiState,
    palette: PlayerPalette,
    centred: Boolean = false,
    cover: @Composable (String?) -> Unit,
) {
    AnimatedContent(
        targetState = TrackVisual(
            index = state.queueIndex,
            artworkUri = state.artworkUri,
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
            cover(visual.artworkUri)

            Spacer(Modifier.height(if (centred) 20.dp else 32.dp))

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

@Composable
private fun CoverImage(uri: String?, palette: PlayerPalette) {
    Icon(
        imageVector = Icons.Rounded.Album,
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
    palette: PlayerPalette,
    onCollapse: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Pushed down off the status bar so it does not read as a title bar.
            .padding(top = 12.dp, bottom = 4.dp),
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Contraer",
                tint = palette.content,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp),
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Aleatorio",
                tint = if (state.shuffleEnabled) palette.active else palette.subdued,
            )
        }

        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Anterior",
                tint = palette.content,
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
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                modifier = Modifier.size(36.dp),
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Siguiente",
                tint = palette.content,
                modifier = Modifier.size(36.dp),
            )
        }

        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
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
