package com.jipix.resonance.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jipix.resonance.data.db.SongEntity

/**
 * One screen for albums, artists and playlists. They differ only in the header
 * art and whether rows can be removed, so three near-identical screens would be
 * three places to fix the same bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    target: DetailTarget,
    songs: List<SongEntity>,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRemoveFromPlaylist: ((Long) -> Unit)?,
    onSongMenu: (SongEntity) -> Unit,
    onPickCover: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var pickingCover by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(target.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                actions = {
                    if (onPickCover != null) {
                        IconButton(onClick = { pickingCover = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = "Elegir portada",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item(key = "header") {
                DetailHeader(target = target, songs = songs, onPlay = onPlay, onShuffle = onShuffle)
            }

            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onPlay(index) },
                    onLongClick = { onSongMenu(song) },
                    trailing = if (onRemoveFromPlaylist != null) {
                        {
                            IconButton(onClick = { onRemoveFromPlaylist(song.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Quitar de la lista",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (pickingCover && onPickCover != null) {
        CoverPickerDialog(
            albumIds = songs.map { it.albumId }.distinct(),
            onPick = {
                onPickCover(it)
                pickingCover = false
            },
            onDismiss = { pickingCover = false },
        )
    }
}

/**
 * Covers to choose from come from the playlist's own tracks. Picking an arbitrary
 * image would mean a file picker plus somewhere to store the result; borrowing an
 * album cover needs neither, and it is what the list already looks like.
 */
@Composable
private fun CoverPickerDialog(
    albumIds: List<Long>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Portada de la lista") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                item(key = "auto") {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onPick(0L) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Mosaico",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(albumIds) { albumId ->
                    Artwork(
                        albumId = albumId,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(albumId) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun DetailHeader(
    target: DetailTarget,
    songs: List<SongEntity>,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (target) {
                is DetailTarget.Album -> Artwork(
                    albumId = target.albumId,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                is DetailTarget.Artist -> Artwork(
                    albumId = songs.firstOrNull()?.albumId ?: -1L,
                    placeholder = Icons.Rounded.Person,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                )

                is DetailTarget.Playlist -> Artwork(
                    albumId = songs.firstOrNull()?.albumId ?: -1L,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when (target) {
                    is DetailTarget.Album -> if (target.year > 0) {
                        "${target.subtitle} · ${target.year}"
                    } else {
                        target.subtitle
                    }
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${songs.size} pistas · ${songs.sumOf { it.durationMs }.asDuration()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Button(
                onClick = { onPlay(0) },
                enabled = songs.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text("Reproducir", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onShuffle,
                enabled = songs.isNotEmpty(),
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Text("Aleatorio", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** "1 h 12 min" / "42 min" — a running time, not a clock reading. */
fun Long.asDuration(): String {
    val totalMinutes = this / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
