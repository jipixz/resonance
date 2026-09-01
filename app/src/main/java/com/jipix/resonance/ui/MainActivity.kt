package com.jipix.resonance.ui

import android.Manifest
import android.app.Activity
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jipix.resonance.R
import com.jipix.resonance.ResonanceApp
import com.jipix.resonance.core.Settings
import com.jipix.resonance.data.M3uPlaylists
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
import com.jipix.resonance.ui.library.LoudnessScreen
import com.jipix.resonance.ui.library.NamePlaylistDialog
import com.jipix.resonance.ui.library.PlaylistList
import com.jipix.resonance.ui.library.SearchScreen
import com.jipix.resonance.ui.library.SongList
import com.jipix.resonance.ui.player.MiniPlayer
import com.jipix.resonance.ui.player.OutputPickerSheet
import com.jipix.resonance.ui.player.PlayerScreen
import com.jipix.resonance.ui.player.QueueSheet
import com.jipix.resonance.ui.player.rememberPlayerPalette
import com.jipix.resonance.ui.theme.DisplayFont
import com.jipix.resonance.ui.theme.ResonanceTheme
import com.jipix.resonance.ui.theme.WordmarkFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jipix.resonance.ui.ResonanceIcons

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

/**
 * Where the collapsed player sits when there is one. Used to park the snackbar in
 * the same place whether or not a queue exists, so a confirmation does not jump
 * up the screen the first time something starts playing.
 */
private val CollapsedPlayerSlot = 64.dp

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
    var showLoudness by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showOutputPicker by rememberSaveable { mutableStateOf(false) }
    var creatingPlaylist by rememberSaveable { mutableStateOf(false) }
    var savingQueue by rememberSaveable { mutableStateOf(false) }
    var exportingPlaylist by remember { mutableStateOf<DetailTarget.Playlist?>(null) }
    var menuSong by remember { mutableStateOf<SongEntity?>(null) }
    // Snapshotted when the sheet opens rather than tracked continuously; see
    // PlayerConnection.snapshotQueue.
    var queue by remember { mutableStateOf(emptyList<QueueItem>()) }

    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    val palette = rememberPlayerPalette(playback.artworkUri, settings.artworkTint)

    // SAF rather than a storage permission: the user points at one file and
    // grants access to exactly that, which is both the modern API and less to
    // ask for than blanket file access.
    val resolver = context.contentResolver
    val snackbarHostState = remember { SnackbarHostState() }
    // Measured rather than assumed: the bar's height depends on whether it is
    // showing at all, and it carries its own navigation-bar padding inside.
    val density = LocalDensity.current
    var miniPlayerHeight by remember { mutableStateOf(0.dp) }

    fun confirm(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // The work runs straight off the launcher callback rather than through a
    // LaunchedEffect keyed on a "pending uri" state. That earlier shape cleared
    // its own key on its second line, which cancelled the effect mid-read: the
    // file was never actually parsed and the import silently did nothing.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (content == null) {
                confirm("No se pudo leer el archivo")
                return@launch
            }
            val name = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Lista importada"
            viewModel.importPlaylist(name, content) { added, missing ->
                confirm(
                    when {
                        added == 0 -> "Ninguna pista del archivo está en tu biblioteca"
                        missing > 0 -> "Importadas $added · $missing sin encontrar"
                        else -> "Importadas $added pistas"
                    }
                )
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val playlist = exportingPlaylist
        exportingPlaylist = null
        if (uri == null || playlist == null) return@rememberLauncherForActivityResult
        viewModel.exportPlaylist(playlist.playlistId) { text ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(text.toByteArray())
                        }
                    }.isSuccess
                }
                confirm(if (ok) "Lista exportada" else "No se pudo escribir el archivo")
            }
        }
    }

    // Back at the library root sends the app to the background instead of
    // finishing it. Finishing throws away every screen the user was on, which is
    // why reopening always landed back at the songs tab.
    val activity = LocalContext.current as? Activity
    BackHandler(
        enabled = !showPlayer && !showSearch && !showFolders && !showLoudness &&
            detail == null
    ) {
        activity?.moveTaskToBack(true)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Opening is the button's job only. An edge swipe competes with the
        // system back gesture on one side and with the pager's own horizontal
        // swipe on the other, and lost to both often enough to be unreliable.
        // Swiping the sheet closed stays on: that direction competes with
        // nothing.
        gesturesEnabled = drawerState.isOpen,
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
                onOpenLoudness = {
                    showLoudness = true
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
                        // The mini player is no longer a bottomBar, so Scaffold
                        // cannot lift this clear of it any more. With a bar
                        // present the snackbar sits just above it; with none it
                        // takes that empty slot instead of hugging the very
                        // bottom edge, so it lands in the same place either way.
                        modifier = Modifier.padding(
                            bottom = if (miniPlayerHeight > 0.dp) {
                                miniPlayerHeight + 2.dp
                            } else {
                                CollapsedPlayerSlot
                            },
                        ),
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
                                        Icon(ResonanceIcons.Close, contentDescription = "Cerrar")
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
                                // Deliberately larger than the 48dp default. This
                                // is the only way into the drawer now that the
                                // edge swipe is gone, and it gets pressed with wet
                                // hands and while driving — the cases where a
                                // minimum-size target is exactly the wrong call.
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.size(60.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_menu_waveform),
                                        contentDescription = "Menú",
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { showSearch = true }) {
                                    Icon(ResonanceIcons.Search, contentDescription = "Buscar")
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
                                    text = {
                                        Text(
                                            text = entry.label,
                                            fontFamily = DisplayFont,
                                            fontSize = 15.sp,
                                        )
                                    },
                                )
                            }
                        }
                    }
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
                        bottom = maxOf(
                            miniPlayerHeight,
                            scaffoldPadding.calculateBottomPadding(),
                        ),
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
                                        bottom = maxOf(
                                            miniPlayerHeight,
                                            scaffoldPadding.calculateBottomPadding(),
                                        ) + 8.dp,
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
                            onImport = {
                                importLauncher.launch(
                                    arrayOf("audio/x-mpegurl", "audio/mpegurl", "*/*")
                                )
                            },
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

            // The screen keeps rendering the target it is closing.
            //
            // Reading `detail` directly meant the content vanished the instant it
            // became null, leaving AnimatedVisibility to slide an empty box off
            // the screen — the exit animation was running the whole time, over
            // nothing. Holding the last non-null target gives it something to
            // animate, so closing looks like the reverse of opening.
            val detailTarget = detail
            val lastDetail = remember { mutableStateOf<DetailTarget?>(null) }
            if (detailTarget != null) lastDetail.value = detailTarget
            AnimatedVisibility(
                visible = detailTarget != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                val detailSongs by viewModel.detailSongs.collectAsStateWithLifecycle()
                val shown = detailTarget ?: lastDetail.value
                if (shown != null) {
                    DetailScreen(
                        target = shown,
                        songs = detailSongs,
                        onBack = { viewModel.closeDetail() },
                        onPlay = { index -> viewModel.playFrom(detailSongs, index) },
                        onShuffle = {
                            if (detailSongs.isNotEmpty()) {
                                viewModel.playFrom(detailSongs.shuffled(), 0)
                            }
                        },
                        onRemoveFromPlaylist = if (shown is DetailTarget.Playlist) {
                            { songId -> viewModel.removeFromPlaylist(shown.playlistId, songId) }
                        } else {
                            null
                        },
                        onSongMenu = { menuSong = it },
                        bottomInset = miniPlayerHeight,
                        onExport = if (shown is DetailTarget.Playlist) {
                            {
                                exportingPlaylist = shown
                                exportLauncher.launch(
                                    M3uPlaylists.fileNameFor(shown.title)
                                )
                            }
                        } else {
                            null
                        },
                        onPickCover = if (shown is DetailTarget.Playlist) {
                            { albumId ->
                                viewModel.setPlaylistCover(shown.playlistId, albumId)
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
                    bottomInset = miniPlayerHeight,
                )
            }

            AnimatedVisibility(
                visible = showLoudness,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                val analysedCount by viewModel.analysedCount.collectAsStateWithLifecycle()
                val analysis by viewModel.analysis.collectAsStateWithLifecycle()
                LoudnessScreen(
                    totalSongs = songs.size,
                    analysedCount = analysedCount,
                    progress = analysis,
                    normalizeVolume = settings.normalizeVolume,
                    analyseOnPlay = settings.analyseOnPlay,
                    onSetNormalize = {
                        scope.launch { settingsStore.setNormalizeVolume(it) }
                    },
                    onSetAnalyseOnPlay = {
                        scope.launch { settingsStore.setAnalyseOnPlay(it) }
                    },
                    onAnalyse = { viewModel.analyseLibrary(context) },
                    onCancel = viewModel::cancelAnalysis,
                    onClear = {
                        viewModel.clearAnalysis()
                        confirm("Análisis borrado")
                    },
                    onBack = { showLoudness = false },
                    bottomInset = miniPlayerHeight,
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
                    bottomInset = miniPlayerHeight,
                )
            }

            // Sits in the root Box rather than in the Scaffold, so it survives
            // navigation into Search, Folders and Detail. Declared after them and
            // before the full-screen player/queue, which is exactly its z-order:
            // over every browsing surface, under anything that takes the screen.
            MiniPlayer(
                state = playback,
                artworkTint = settings.artworkTint,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onExpand = { showPlayer = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        val measured = with(density) { size.height.toDp() }
                        if (measured != miniPlayerHeight) miniPlayerHeight = measured
                    },
            )

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
                    dismissEnabled = !showQueue && !showOutputPicker,
                    onOpenOutputPicker = { showOutputPicker = true },
                    onOpenAlbum = {
                        viewModel.openDetail(
                            DetailTarget.Album(
                                albumId = playback.albumId,
                                title = playback.album,
                                subtitle = playback.artist,
                                // The session carries no release year, and the
                                // detail screen reads it from the tracks it
                                // loads anyway; 0 means "not stated here".
                                year = 0,
                            )
                        )
                        // The player would otherwise stay on top of the very
                        // screen it just asked for.
                        showPlayer = false
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
                        if (index == playback.queueIndex) {
                            // Seeking to where you already are does nothing
                            // visible, so the tap read as dead. On the current
                            // row the useful meaning of a tap is play/pause.
                            viewModel.togglePlayPause()
                        } else {
                            viewModel.playQueueItem(index, settings.queueTapPlays)
                            if (settings.queueClosesOnTap) showQueue = false
                        }
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
                    onClearQueue = {
                        viewModel.clearQueue()
                        queue = emptyList()
                        showQueue = false
                        // Nothing is playing and nothing is queued, so the player
                        // would be sitting there showing a track it no longer has.
                        showPlayer = false
                        confirm("Cola vaciada")
                    },
                    onSaveAsPlaylist = { savingQueue = true },
                    onSetTapPlays = { scope.launch { settingsStore.setQueueTapPlays(it) } },
                    onSetCloseOnTap = { scope.launch { settingsStore.setQueueClosesOnTap(it) } },
                    onDismiss = { showQueue = false },
                )
            }
        }
    }

    if (showOutputPicker) {
        OutputPickerSheet(
            palette = palette,
            onPick = viewModel::setPreferredOutput,
            onDismiss = { showOutputPicker = false },
        )
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
        enabled = showLoudness && !showPlayer && !showSearch && !showFolders && !showQueue
    ) { showLoudness = false }
    BackHandler(
        enabled = detail != null && !showPlayer && !showSearch && !showFolders &&
            !showQueue && !showLoudness
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
