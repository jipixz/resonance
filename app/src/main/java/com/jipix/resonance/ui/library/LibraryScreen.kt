package com.jipix.resonance.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jipix.resonance.data.db.AlbumSummary
import com.jipix.resonance.data.db.ArtistSummary
import com.jipix.resonance.data.db.PlaylistSummary
import com.jipix.resonance.data.db.SongEntity
import com.jipix.resonance.data.media.MediaStoreScanner
import java.util.Locale
import com.jipix.resonance.ui.ResonanceIcons

/**
 * The pre-Android-12 EdgeEffect glow — a wide, shallow tinted arc that flares
 * in while a scroll gesture is pulling against a boundary and eases back out
 * once it ends, rather than sitting there. The platform's own stretch
 * overscroll is switched off app-wide (see `ResonanceTheme`, and `QueueSheet`'s
 * doc comment for why plain removal wasn't enough inside a bottom-sheet-shaped
 * container) because its bounce read as too strong; this replaces it with
 * something closer to what the user actually asked for — a transient glow, not
 * a boundary state that just sits there.
 *
 * This is an approximation, not the real `EdgeEffect`: the public
 * `OverscrollEffect` API does not expose per-edge state or pull distance, only
 * a single `isInProgress` flag with no directionality — so this drives off
 * [LazyListState.isScrollInProgress]/[LazyGridState.isScrollInProgress]
 * instead, gated to whichever edge is actually the current boundary.
 */
@Composable
fun BoxScope.EdgeGlow(
    atTop: Boolean,
    atBottom: Boolean,
    active: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    GlowArc(
        visible = atTop && active,
        atTop = true,
        color = color,
        modifier = Modifier.align(Alignment.TopCenter),
    )
    GlowArc(
        visible = atBottom && active,
        atTop = false,
        color = color,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}

@Composable
private fun GlowArc(visible: Boolean, atTop: Boolean, color: Color, modifier: Modifier) {
    // Quick to flare in, slower to retract — an instant appearance reads as
    // "caught you pulling", while the retreat needs to be visible to land as
    // "retracting" rather than just popping out.
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.85f else 0f,
        animationSpec = tween(if (visible) 100 else 380),
        label = "glowAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.55f,
        animationSpec = tween(if (visible) 100 else 380),
        label = "glowScale",
    )
    if (alpha <= 0.001f) return

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                // Scales toward the edge it hugs, not the arc's own centre —
                // it should shrink back *into* the boundary, not inward on
                // itself.
                transformOrigin = TransformOrigin(0.5f, if (atTop) 0f else 1f)
            }
            .drawWithCache {
                // Centred well outside the box so only its cap is visible —
                // the classic flattened "semicircle" shape, not a full circle.
                val centerY = if (atTop) -size.height * 0.7f else size.height * 1.7f
                val brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0f)),
                    center = Offset(size.width / 2f, centerY),
                    radius = size.width * 0.8f,
                )
                onDrawBehind { drawRect(brush) }
            },
    )
}

@Composable
fun SongList(
    songs: List<SongEntity>,
    onPlay: (Int) -> Unit,
    onSongMenu: (SongEntity) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            // Keyed so a rescan animates rows instead of rebuilding the whole list.
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onPlay(index) },
                    onLongClick = { onSongMenu(song) },
                )
            }
        }
        EdgeGlow(
            atTop = !listState.canScrollBackward,
            atBottom = !listState.canScrollForward,
            active = listState.isScrollInProgress,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            albumId = song.albumId,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = song.durationMs.asClock(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
fun AlbumGrid(
    albums: List<AlbumSummary>,
    onOpen: (AlbumSummary) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            // 160dp comfortably fit two columns at default display size, but
            // Android's own "display size" accessibility setting shrinks the
            // screen's *effective* dp width — at the larger settings, two
            // 160dp tiles plus spacing no longer fit and Adaptive silently
            // drops to one column. 130dp still reads as a real album grid,
            // not a list, while leaving enough margin for two columns to
            // survive the display-size range Android actually offers.
            columns = GridCells.Adaptive(minSize = 130.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(albums, key = { it.albumId }) { album ->
                AlbumCell(album = album, onClick = { onOpen(album) })
            }
        }
        EdgeGlow(
            atTop = !gridState.canScrollBackward,
            atBottom = !gridState.canScrollForward,
            active = gridState.isScrollInProgress,
        )
    }
}

@Composable
private fun AlbumCell(album: AlbumSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Artwork(
            albumId = album.albumId,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        )
        Text(
            text = album.album,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = album.albumArtist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArtistList(
    artists: List<ArtistSummary>,
    onOpen: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(artists, key = { it.artist }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(artist.artist) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    albumId = artist.artworkAlbumId,
                    placeholder = ResonanceIcons.Person,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = artist.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${artist.songCount} pistas · ${artist.albumCount} álbumes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
        EdgeGlow(
            atTop = !listState.canScrollBackward,
            atBottom = !listState.canScrollForward,
            active = listState.isScrollInProgress,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistList(
    playlists: List<PlaylistSummary>,
    onOpen: (PlaylistSummary) -> Unit,
    onDelete: (PlaylistSummary) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "new") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ResonanceIcons.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = "Nueva lista",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }

        item(key = "import") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onImport)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ResonanceIcons.FileOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(
                    text = "Importar .m3u",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist) }
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistCover(
                    coverAlbumId = playlist.coverAlbumId,
                    albumIds = playlist.albumIds,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${playlist.songCount} pistas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Deleting used to be a long press — the same gesture as "I am
                // not sure what this is, let me hold it and find out" — and it
                // destroyed the list without asking. An explicit button that
                // confirms first is the only honest way to offer this.
                IconButton(onClick = { onDelete(playlist) }) {
                    Icon(
                        imageVector = ResonanceIcons.DeleteOutline,
                        contentDescription = "Borrar lista",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
        EdgeGlow(
            atTop = !listState.canScrollBackward,
            atBottom = !listState.canScrollForward,
            active = listState.isScrollInProgress,
        )
    }
}

/**
 * A playlist's face: the chosen cover when there is one, otherwise a 2x2 mosaic
 * of the first albums in it — which is what makes a fresh list recognisable
 * before anyone has bothered to pick artwork for it.
 */
@Composable
fun PlaylistCover(
    coverAlbumId: Long,
    albumIds: List<Long>,
    modifier: Modifier = Modifier,
) {
    val tiles = albumIds.take(4)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when {
            coverAlbumId > 0L -> Artwork(albumId = coverAlbumId, modifier = Modifier.fillMaxSize())

            tiles.size >= 4 -> Column(modifier = Modifier.fillMaxSize()) {
                repeat(2) { row ->
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(2) { column ->
                            Artwork(
                                albumId = tiles[row * 2 + column],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                            )
                        }
                    }
                }
            }

            tiles.isNotEmpty() -> Artwork(
                albumId = tiles.first(),
                modifier = Modifier.fillMaxSize(),
            )

            else -> Icon(
                imageVector = ResonanceIcons.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Album art over a themed placeholder. The placeholder is a composable rather
 * than a drawable so it picks up the dynamic colour scheme, and it stays
 * underneath the image so there is never a blank frame while art decodes.
 */
@Composable
fun Artwork(
    albumId: Long,
    modifier: Modifier = Modifier,
    placeholder: ImageVector = ResonanceIcons.Album,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = placeholder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        AsyncImage(
            model = MediaStoreScanner.albumArtUri(albumId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun EmptyLibrary(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = ResonanceIcons.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

fun Long.asClock(): String {
    val totalSeconds = this / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
