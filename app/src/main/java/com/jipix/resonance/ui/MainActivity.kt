package com.jipix.resonance.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jipix.resonance.ResonanceApp
import com.jipix.resonance.core.Settings
import com.jipix.resonance.core.SettingsStore
import com.jipix.resonance.data.db.SongEntity
import com.jipix.resonance.playback.QueueItem
import com.jipix.resonance.ui.library.AddToPlaylistSheet
import com.jipix.resonance.ui.library.AlbumGrid
import com.jipix.resonance.ui.library.ArtistList
import com.jipix.resonance.ui.library.DetailScreen
import com.jipix.resonance.ui.library.DetailTarget
import com.jipix.resonance.ui.library.EmptyLibrary
import com.jipix.resonance.ui.library.FoldersScreen
import com.jipix.resonance.ui.library.LibraryViewModel
import com.jipix.resonance.ui.library.NamePlaylistDialog
import com.jipix.resonance.ui.library.PlaylistList
import com.jipix.resonance.ui.library.SearchScreen
import com.jipix.resonance.ui.library.SongList
import com.jipix.resonance.ui.player.MiniPlayer
import com.jipix.resonance.ui.player.PlayerScreen
import com.jipix.resonance.ui.player.QueueSheet
import com.jipix.resonance.ui.player.rememberPlayerPalette
import com.jipix.resonance.ui.theme.ResonanceTheme
import com.jipix.resonance.ui.theme.WordmarkFont
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as ResonanceApp).container
        container.playerConnection.connect()

        setContent {
            val settings by container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = Settings())

            // Cold start only. The flag lives on the Application, so coming back
            // to a process that is still resident skips the animation entirely;
            // a genuinely cold start is by definition a new process.
            val app = LocalContext.current.applicationContext as ResonanceApp
            var showSplash by remember { mutableStateOf(!app.splashPlayed) }

            ResonanceTheme(
                dynamicColor = settings.dynamicColor,
                amoled = settings.amoled,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ResonanceRoot(
                        viewModel = viewModel(
                            factory = LibraryViewModel.Factory(
                                container.musicRepository,
                                container.playerConnection,
                            )
                        ),
                        settings = settings,
                        settingsStore = container.settingsStore,
                    )

                    if (showSplash) {
                        SplashOverlay(
                            onFinished = {
                                app.splashPlayed = true
                                showSplash = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class LibraryTab(val label: String) {
    Songs("Canciones"),
    Albums("Álbumes"),
    Artists("Artistas"),
    Playlists("Listas"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResonanceRoot(
    viewModel: LibraryViewModel,
    settings: Settings,
    settingsStore: SettingsStore,
) {
    // READ_MEDIA_AUDIO only exists from API 33; below that the legacy read
    // permission is what actually gates MediaStore.
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    // A rescan is cheap when nothing changed, so it runs on every grant rather
    // than making the user hunt for a refresh button.
    LaunchedEffect(granted) {
        if (granted) viewModel.sync()
    }

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val pagerState = rememberPagerState { LibraryTab.entries.size }

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var creatingPlaylist by rememberSaveable { mutableStateOf(false) }
    var savingQueue by rememberSaveable { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<SongEntity?>(null) }
    // Snapshotted when the sheet opens rather than tracked continuously; see
    // PlayerConnection.snapshotQueue.
    var queue by remember { mutableStateOf(emptyList<QueueItem>()) }

    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    val palette = rememberPlayerPalette(playback.artworkUri, settings.artworkTint)
    val snackbarHostState = remember { SnackbarHostState() }

    fun confirm(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // Back at the library root sends the app to the background instead of
    // finishing it. Finishing throws away every screen the user was on, which is
    // why reopening always landed back at the songs tab.
    val activity = LocalContext.current as? Activity
    BackHandler(
        enabled = !showPlayer && !showSearch && !showFolders && detail == null
    ) {
        activity?.moveTaskToBack(true)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LibraryDrawer(
                settings = settings,
                songCount = songs.size,
                selectedTab = pagerState.currentPage,
                onSelectTab = { page ->
                    scope.launch {
                        pagerState.animateScrollToPage(page)
                        drawerState.close()
                    }
                },
                onOpenFolders = {
                    showFolders = true
                    scope.launch { drawerState.close() }
                },
                onSetDynamicColor = { scope.launch { settingsStore.setDynamicColor(it) } },
                onSetAmoled = { scope.launch { settingsStore.setAmoled(it) } },
                onSetArtworkTint = { scope.launch { settingsStore.setArtworkTint(it) } },
                onSetCrossfade = { scope.launch { settingsStore.setCrossfade(it) } },
                onSetCrossfadeSeconds = {
                    scope.launch { settingsStore.setCrossfadeSeconds(it) }
                },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        // Scaffold already lifts this clear of bottomBar's own
                        // height; a fixed offset on top of that (what used to be
                        // here) double-counts it and floats the snackbar too high.
                        modifier = Modifier.padding(bottom = 2.dp),
                    ) { data ->
                        // The M3 default snackbar deliberately inverts against the
                        // theme (light chip on a dark app, dark chip on a light
                        // one) for contrast. That reads as "wrong theme" here, so
                        // it follows the app's own surface colours instead.
                        val snackbarShape = RoundedCornerShape(16.dp)
                        // Not the Snackbar(snackbarData = ...) convenience
                        // overload: it adds `modifier.padding(12.dp)` internally
                        // *after* whatever modifier is passed in, so a border on
                        // that modifier ends up drawn 12dp outside the visible
                        // coloured surface instead of hugging it. This lower-
                        // level overload applies the modifier straight to the
                        // Surface with nothing in between.
                        Snackbar(
                            modifier = Modifier.border(
                                width = 1.dp,
                                // AMOLED collapses every surface tier toward true
                                // black, so a tonal container alone (what used to
                                // be here) all but disappears against it — a
                                // border in the app's own accent colour is what
                                // actually stays visible regardless of theme.
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = snackbarShape,
                            ),
                            action = data.visuals.actionLabel?.let { label ->
                                {
                                    TextButton(
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary,
                                        ),
                                        onClick = { data.performAction() },
                                    ) { Text(label) }
                                }
                            },
                            dismissAction = if (data.visuals.withDismissAction) {
                                {
                                    IconButton(onClick = { data.dismiss() }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                                    }
                                }
                            } else {
                                null
                            },
                            shape = snackbarShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text(data.visuals.message)
                        }
                    }
                },
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Resonance",
                                    fontFamily = WordmarkFont,
                                    fontSize = 24.sp,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Rounded.Menu, contentDescription = "Menú")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showSearch = true }) {
                                    Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                                }
                            },
                        )
                        // Scrollable so a fourth tab does not squeeze the labels.
                        PrimaryScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            edgePadding = 0.dp,
                        ) {
                            LibraryTab.entries.forEachIndexed { index, entry ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    text = { Text(entry.label) },
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    MiniPlayer(
                        state = playback,
                        artworkTint = settings.artworkTint,
                        onPlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                        onExpand = { showPlayer = true },
                    )
                },
            ) { scaffoldPadding ->
                if (!granted) {
                    PermissionGate(
                        onRequest = { permissionLauncher.launch(permission) },
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                    return@Scaffold
                }

                // The pager sits inside the bars; each page pads its own content so
                // lists still scroll edge-to-edge underneath the mini player.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = scaffoldPadding.calculateTopPadding()),
                ) { page ->
                    val listPadding = PaddingValues(
                        bottom = scaffoldPadding.calculateBottomPadding(),
                    )
                    when (LibraryTab.entries[page]) {
                        LibraryTab.Songs -> {
                            if (songs.isEmpty()) {
                                EmptyLibrary("No se encontró música en este dispositivo.")
                            } else {
                                SongList(
                                    songs = songs,
                                    onPlay = { index -> viewModel.playFrom(songs, index) },
                                    onSongMenu = { menuSong = it },
                                    contentPadding = listPadding,
                                )
                            }
                        }

                        LibraryTab.Albums -> {
                            val albums by viewModel.albums.collectAsStateWithLifecycle()
                            if (albums.isEmpty()) {
                                EmptyLibrary("Aún no hay álbumes.")
                            } else {
                                AlbumGrid(
                                    albums = albums,
                                    onOpen = { album ->
                                        viewModel.openDetail(
                                            DetailTarget.Album(
                                                albumId = album.albumId,
                                                title = album.album,
                                                subtitle = album.albumArtist,
                                                year = album.year,
                                            )
                                        )
                                    },
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = scaffoldPadding.calculateBottomPadding() + 8.dp,
                                    ),
                                )
                            }
                        }

                        LibraryTab.Artists -> {
                            val artists by viewModel.artists.collectAsStateWithLifecycle()
                            if (artists.isEmpty()) {
                                EmptyLibrary("Aún no hay artistas.")
                            } else {
                                ArtistList(
                                    artists = artists,
                                    onOpen = { viewModel.openDetail(DetailTarget.Artist(it)) },
                                    contentPadding = listPadding,
                                )
                            }
                        }

                        LibraryTab.Playlists -> PlaylistList(
                            playlists = playlists,
                            onOpen = {
                                viewModel.openDetail(DetailTarget.Playlist(it.id, it.name))
                            },
                            onDelete = { viewModel.deletePlaylist(it.id) },
                            onCreate = { creatingPlaylist = true },
                            contentPadding = listPadding,
                        )
                    }
                }
            }

            val detailTarget = detail
            AnimatedVisibility(
                visible = detailTarget != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                val detailSongs by viewModel.detailSongs.collectAsStateWithLifecycle()
                if (detailTarget != null) {
                    DetailScreen(
                        target = detailTarget,
                        songs = detailSongs,
                        onBack = { viewModel.closeDetail() },
                        onPlay = { index -> viewModel.playFrom(detailSongs, index) },
                        onShuffle = {
                            if (detailSongs.isNotEmpty()) {
                                viewModel.playFrom(detailSongs.shuffled(), 0)
                            }
                        },
                        onRemoveFromPlaylist = if (detailTarget is DetailTarget.Playlist) {
                            { songId -> viewModel.removeFromPlaylist(detailTarget.playlistId, songId) }
                        } else {
                            null
                        },
                        onSongMenu = { menuSong = it },
                        onPickCover = if (detailTarget is DetailTarget.Playlist) {
                            { albumId ->
                                viewModel.setPlaylistCover(detailTarget.playlistId, albumId)
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = showSearch,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                val query by viewModel.query.collectAsStateWithLifecycle()
                val results by viewModel.searchResults.collectAsStateWithLifecycle()
                SearchScreen(
                    query = query,
                    results = results,
                    onQueryChange = viewModel::setQuery,
                    onBack = { showSearch = false },
                    onPlay = { index -> viewModel.playFrom(results, index) },
                    onSongMenu = { menuSong = it },
                )
            }

            AnimatedVisibility(
                visible = showFolders,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                val folders by viewModel.folders.collectAsStateWithLifecycle()
                FoldersScreen(
                    folders = folders,
                    excluded = settings.excludedFolders,
                    onSetExcluded = { folder, excluded ->
                        scope.launch { settingsStore.setFolderExcluded(folder, excluded) }
                    },
                    onBack = { showFolders = false },
                )
            }

            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                PlayerScreen(
                    state = playback,
                    artworkTint = settings.artworkTint,
                    onOpenQueue = {
                        queue = viewModel.queueSnapshot()
                        showQueue = true
                    },
                    infoLine = settings.infoLine,
                    onCycleInfoLine = {
                        scope.launch { settingsStore.setInfoLine(settings.infoLine.next()) }
                    },
                    onCollapse = { showPlayer = false },
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeek = viewModel::seekTo,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeat,
                    onSetSleepTimer = viewModel::setSleepTimer,
                    onCancelSleepTimer = viewModel::cancelSleepTimer,
                    queueItemAt = viewModel::queueItemAt,
                    onSeekQueueIndex = { index -> viewModel.playQueueItem(index, autoPlay = true) },
                )
            }

            // Layered above the player screen it opens from, not as a separate
            // Popup/window the way ModalBottomSheet was — see QueueSheet's own
            // doc comment for why it moved off that component.
            AnimatedVisibility(
                visible = showQueue,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                QueueSheet(
                    queue = queue,
                    currentIndex = playback.queueIndex,
                    positionMs = playback.positionMs,
                    isPlaying = playback.isPlaying,
                    palette = palette,
                    tapPlays = settings.queueTapPlays,
                    closeOnTap = settings.queueClosesOnTap,
                    onPlayItem = { index ->
                        viewModel.playQueueItem(index, settings.queueTapPlays)
                        if (settings.queueClosesOnTap) showQueue = false
                    },
                    onMoveItem = viewModel::moveQueueItem,
                    onRemoveItem = viewModel::removeQueueItem,
                    onShuffle = {
                        viewModel.shuffleQueue()
                        queue = viewModel.queueSnapshot()
                    },
                    onRemoveDuplicates = {
                        viewModel.removeQueueDuplicates()
                        queue = viewModel.queueSnapshot()
                    },
                    onSaveAsPlaylist = { savingQueue = true },
                    onSetTapPlays = { scope.launch { settingsStore.setQueueTapPlays(it) } },
                    onSetCloseOnTap = { scope.launch { settingsStore.setQueueClosesOnTap(it) } },
                    onDismiss = { showQueue = false },
                )
            }
        }
    }

    menuSong?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = playlists,
            onAddTo = { playlistId ->
                viewModel.addToPlaylist(playlistId, listOf(song.id))
                val listName = playlists.firstOrNull { it.id == playlistId }?.name.orEmpty()
                confirm("Añadida a $listName")
                menuSong = null
            },
            onCreateWith = { name ->
                viewModel.createPlaylist(name, listOf(song.id))
                confirm("Lista \"$name\" creada con 1 pista")
                menuSong = null
            },
            onDismiss = { menuSong = null },
        )
    }

    if (savingQueue) {
        NamePlaylistDialog(
            onConfirm = { name ->
                viewModel.saveQueueAsPlaylist(name)
                confirm("Cola guardada como \"$name\"")
                savingQueue = false
            },
            onDismiss = { savingQueue = false },
        )
    }

    if (creatingPlaylist) {
        NamePlaylistDialog(
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                confirm("Lista \"$name\" creada")
                creatingPlaylist = false
            },
            onDismiss = { creatingPlaylist = false },
        )
    }

    // Back peels off one overlay at a time before it leaves the app. The queue
    // opens on top of the player, so it takes priority over it here too.
    BackHandler(enabled = showQueue) { showQueue = false }
    BackHandler(enabled = showPlayer && !showQueue) { showPlayer = false }
    BackHandler(enabled = showSearch && !showPlayer && !showQueue) { showSearch = false }
    BackHandler(
        enabled = showFolders && !showPlayer && !showSearch && !showQueue
    ) { showFolders = false }
    BackHandler(
        enabled = detail != null && !showPlayer && !showSearch && !showFolders && !showQueue
    ) { viewModel.closeDetail() }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Resonance necesita acceso a tu música para construir la biblioteca.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            TextButton(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
                Text("Conceder acceso")
            }
        }
    }
}
