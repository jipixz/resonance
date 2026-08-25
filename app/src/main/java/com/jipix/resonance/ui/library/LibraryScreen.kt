package com.jipix.resonance.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun SongList(
    songs: List<SongEntity>,
    onPlay: (Int) -> Unit,
    onSongMenu: (SongEntity) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.albumId }) { album ->
            AlbumCell(album = album, onClick = { onOpen(album) })
        }
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                    placeholder = Icons.Rounded.Person,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistList(
    playlists: List<PlaylistSummary>,
    onOpen: (PlaylistSummary) -> Unit,
    onDelete: (PlaylistSummary) -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                        imageVector = Icons.Rounded.Add,
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

        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpen(playlist) },
                        onLongClick = { onDelete(playlist) },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistCover(
                    coverAlbumId = playlist.coverAlbumId,
                    albumIds = playlist.albumIds,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
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
            }
        }
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
                imageVector = Icons.Rounded.QueueMusic,
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
    placeholder: ImageVector = Icons.Rounded.Album,
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
                imageVector = Icons.Rounded.MusicNote,
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
