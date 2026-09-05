package com.jipix.resonance.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jipix.resonance.playback.PlaybackUiState
import com.jipix.resonance.ui.ResonanceIcons

/**
 * The persistent bar above the gesture area. Deliberately shallow — it reads one
 * state object and emits no work of its own, so it stays cheap to recompose while
 * a track plays.
 */
@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    artworkTint: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.hasQueue,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        val accent = rememberArtworkAccent(state.artworkUri, artworkTint)
        val surface = MaterialTheme.colorScheme.surfaceContainerHigh

        // Animated so a track change eases into the new cover's colour instead of
        // snapping, which would read as a flicker.
        val tint by animateColorAsState(
            targetValue = accent ?: surface,
            label = "miniPlayerTint",
        )

        val background = if (accent != null) {
            // The wash starts at the cover and falls away across the bar: the
            // artwork sits on the left, so that is where the colour comes from.
            Brush.horizontalGradient(
                0f to tint.darken(0.30f),
                0.45f to tint.darken(0.58f),
                1f to tint.darken(0.80f),
            )
        } else {
            Brush.horizontalGradient(listOf(surface, surface))
        }

        val content = if (accent != null) {
            // Judged against the middle of the wash, where the text actually
            // sits, rather than against its bright left edge.
            tint.darken(0.58f).readableOn()
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val subdued = content.copy(alpha = 0.7f)

        Box(modifier = Modifier.fillMaxWidth().background(background)) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // Sits on top of the bar, not under it: at the bottom edge it
                // collided with the system gesture handle.
                LinearProgressIndicator(
                    progress = { state.progressFraction() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = if (accent != null) content else MaterialTheme.colorScheme.primary,
                    trackColor = if (accent != null) {
                        content.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    drawStopIndicator = {},
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExpand)
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = state.artworkUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp, end = 10.dp),
                    ) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = subdued,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (state.isPlaying) {
                                ResonanceIcons.Pause
                            } else {
                                ResonanceIcons.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                            tint = content,
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = ResonanceIcons.SkipNext,
                            contentDescription = "Siguiente",
                            tint = content,
                        )
                    }
                }
            }
        }
    }
}

private fun PlaybackUiState.progressFraction(): Float =
    if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
