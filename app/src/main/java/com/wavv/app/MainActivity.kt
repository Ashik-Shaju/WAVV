package com.wavv.app

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max

private val WavvBackground = Color(0xFF0A0A0A)
private val WavvSurface = Color(0xFF202026)
private val WavvPink = Color(0xFFFA2D48)
private val WavvMuted = Color.White.copy(alpha = .45f)
private val WavvMotionEasing = CubicBezierEasing(.32f, .72f, 0f, 1f)
private val WavvEaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val WavvSurfaceGradient = Brush.verticalGradient(
    listOf(Color(0xFF282832), Color(0xFF17171D)),
)
private val WavvAccentGradient = Brush.linearGradient(
    listOf(Color(0xFFB71C1C), Color(0xFFE91E63), Color(0xFFFF4081)),
)
private val WavvGlassGradient = Brush.verticalGradient(
    listOf(Color(0xFF352018).copy(alpha = .92f), Color(0xFF171419).copy(alpha = .97f)),
)
private val WavvNavGradient = Brush.verticalGradient(
    listOf(Color(0xE326262D), Color(0xF20D0D11)),
)

private enum class Tab { Home, Search, Library, You }

private enum class LoopMode {
    Off,
    All,
    One;

    fun next() = when (this) {
        Off -> All
        All -> One
        One -> Off
    }
}

private fun LoopMode.toPlayerRepeatMode() = when (this) {
    LoopMode.Off -> androidx.media3.common.Player.REPEAT_MODE_OFF
    LoopMode.All -> androidx.media3.common.Player.REPEAT_MODE_ALL
    LoopMode.One -> androidx.media3.common.Player.REPEAT_MODE_ONE
}

class MainActivity : ComponentActivity() {
    private lateinit var playbackController: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackController = PlaybackController(this)
        setContent {
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer {
                            LibraryViewModel(
                                MediaLibraryRepository(applicationContext),
                                LibraryStore(WavvDatabase.get(applicationContext)),
                                LibrarySourceStore(applicationContext),
                                LibraryIndexScheduler(applicationContext),
                                DclapIndexScheduler(applicationContext),
                                DclapTextSearch(applicationContext),
                            )
                        }
                    }
                },
            )
            WavvApp(libraryViewModel, playbackController)
        }
    }

    override fun onDestroy() {
        playbackController.release()
        super.onDestroy()
    }
}

@Composable
private fun WavvApp(libraryViewModel: LibraryViewModel, playbackController: PlaybackController) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED)
    }
    var pendingAllDevice by rememberSaveable { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted && pendingAllDevice) {
            pendingAllDevice = false
            libraryViewModel.selectAllDevice()
        }
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val chooseAllDevice = {
        if (ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED) {
            pendingAllDevice = false
            hasPermission = true
            libraryViewModel.selectAllDevice()
        } else {
            pendingAllDevice = true
            requestPermission.launch(audioPermission())
        }
    }
    val chooseFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            libraryViewModel.selectFiles(uris)
        }
    }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        libraryViewModel.selectFolder(uri)
    }
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val source by libraryViewModel.source.collectAsStateWithLifecycle()
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
    val currentSongs = (libraryState as? LibraryUiState.Ready)?.songs.orEmpty()
    val showUnavailable: (String) -> Unit = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }

    LaunchedEffect(currentSongs) {
        playbackController.updateCatalog(currentSongs)
    }

    LaunchedEffect(source, hasPermission) {
        when (source) {
            null -> libraryViewModel.showSourceSelection()
            LibrarySource.AllDevice -> if (hasPermission) libraryViewModel.loadSongs() else libraryViewModel.showPermissionRequired()
            is LibrarySource.Files, is LibrarySource.Folder -> libraryViewModel.loadSongs()
        }
    }
    LaunchedEffect(playbackState.isPlaying, playbackState.song?.id) {
        while (isActive && playbackState.isPlaying) {
            playbackController.refreshProgress()
            delay(250)
        }
    }
    LaunchedEffect(playbackState.errorMessage) {
        playbackState.errorMessage?.let(showUnavailable)
    }

    MaterialTheme(colorScheme = darkColorScheme(background = WavvBackground, surface = WavvSurface)) {
        WavvContent(
            libraryState = libraryState,
            playbackState = playbackState,
            favoriteIds = (libraryState as? LibraryUiState.Ready)?.favoriteIds.orEmpty(),
            semanticResults = (libraryState as? LibraryUiState.Ready)?.semanticResults,
            semanticLoading = (libraryState as? LibraryUiState.Ready)?.semanticLoading == true,
            onSearch = libraryViewModel::search,
            onChooseAllDevice = chooseAllDevice,
            onChooseFiles = { chooseFiles.launch(arrayOf("audio/*")) },
            onChooseFolder = { chooseFolder.launch(null) },
            onRetry = libraryViewModel::loadSongs,
            onChooseSource = libraryViewModel::clearSource,
            onPlay = { song, queue ->
                libraryViewModel.recordPlaybackEvent("play", song.id, selectionSource = "manual_select")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                playbackController.play(song, queue)
            },
            onTogglePlayback = {
                playbackState.song?.let { song ->
                    libraryViewModel.recordPlaybackEvent(if (playbackState.isPlaying) "pause" else "resume", song.id)
                }
                playbackController.toggle()
            },
            onNext = {
                playbackState.song?.let { song -> libraryViewModel.recordPlaybackEvent("skip", song.id, skipPositionMs = playbackState.positionMs) }
                playbackController.next()
            },
            onPrevious = {
                playbackState.song?.let { song -> libraryViewModel.recordPlaybackEvent("skip", song.id, skipPositionMs = playbackState.positionMs) }
                playbackController.previous()
            },
            onSeek = playbackController::seekTo,
            onToggleFavorite = libraryViewModel::toggleFavorite,
            onUnavailable = showUnavailable,
            onRepeatMode = playbackController::setRepeatMode,
        )
    }
}

private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

@Composable
private fun WavvContent(
    libraryState: LibraryUiState,
    playbackState: PlaybackState,
    favoriteIds: Set<Long>,
    semanticResults: List<Song>?,
    semanticLoading: Boolean,
    onSearch: (String) -> Unit,
    onChooseAllDevice: () -> Unit,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    onRetry: () -> Unit,
    onChooseSource: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onUnavailable: (String) -> Unit,
    onRepeatMode: (Int) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var loopMode by rememberSaveable { mutableStateOf(LoopMode.Off) }
    var homeAtTop by rememberSaveable { mutableStateOf(true) }
    val songs = (libraryState as? LibraryUiState.Ready)?.songs.orEmpty()
    val recentlyPlayed = (libraryState as? LibraryUiState.Ready)?.recentlyPlayed.orEmpty()
    val activeSong = playbackState.song
    val startPlayback: (Song) -> Unit = { song -> onPlay(song, songs.ifEmpty { listOf(song) }) }
    val showLibrary = libraryState is LibraryUiState.Ready

    Box(Modifier.fillMaxSize().background(WavvBackground)) {
        if (showLibrary) {
            AnimatedContent(targetState = tab, label = "tab content") { selectedTab ->
                when (selectedTab) {
                    Tab.Home -> HomeScreen(
                        songs = songs,
                        isPlaying = playbackState.isPlaying,
                        onChooseSource = onChooseSource,
                        onPlay = startPlayback,
                        recentSongs = recentlyPlayed,
                        onAtTopChange = { homeAtTop = it },
                    )
                    Tab.Search -> SearchScreen(
                        songs = songs,
                        semanticSongs = semanticResults,
                        semanticLoading = semanticLoading,
                        onQueryChange = onSearch,
                        onPlay = startPlayback,
                        hasNowPlaying = activeSong != null,
                    )
                    Tab.Library -> LibraryScreen(songs, startPlayback)
                    Tab.You -> YouScreen(songs.size, songs.map(Song::artist).distinct().size, songs.sumOf { it.durationMs } / 60_000, onUnavailable)
                }
            }
        } else {
            when (libraryState) {
                LibraryUiState.Loading -> LoadingScreen()
                LibraryUiState.SelectSource -> ImportSourceScreen(onChooseAllDevice, onChooseFiles, onChooseFolder)
                LibraryUiState.PermissionRequired -> PermissionScreen(onChooseAllDevice, onChooseSource)
                is LibraryUiState.Error -> LibraryErrorScreen(libraryState.message, onRetry, onChooseSource)
                is LibraryUiState.Ready -> Unit
            }
        }
        if (activeSong != null) {
            NowPlayingBar(
                song = activeSong,
                state = playbackState,
                expanded = tab == Tab.Home && homeAtTop,
                modifier = Modifier.align(Alignment.BottomCenter),
                liked = activeSong.id in favoriteIds,
                onToggle = onTogglePlayback,
                onLike = { onToggleFavorite(activeSong.id) },
                onPrevious = onPrevious,
                onNext = onNext,
                onOpen = { showPlayer = true },
            )
        }
        if (showLibrary) BottomNav(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
        AnimatedVisibility(
            visible = showPlayer,
            enter = fadeIn(tween(180)) + slideInVertically(tween(620, easing = WavvMotionEasing)) { it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(620, easing = WavvMotionEasing)) { it },
        ) {
            activeSong?.let {
                FullPlayer(
                    song = it,
                    state = playbackState,
                    liked = it.id in favoriteIds,
                    showQueue = showQueue,
                    onClose = { showPlayer = false; showQueue = false },
                    onToggle = onTogglePlayback,
                    onLike = { onToggleFavorite(it.id) },
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    loopMode = loopMode,
                    onCycleLoop = { loopMode = loopMode.next(); onRepeatMode(loopMode.toPlayerRepeatMode()) },
                    onShowQueue = { showQueue = true },
                    onHideQueue = { showQueue = false },
                    onUnavailable = onUnavailable,
                    onPlaySong = startPlayback,
                )
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, waveform: Boolean = false, isPlaying: Boolean = false) {
    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF5D0D1E), Color(0xFF210A16), WavvBackground),
                ),
            )
            .statusBarsPadding(),
    ) {
        if (waveform) {
            Box(
                Modifier.matchParentSize().drawWithCache {
                    val radius = size.width * .75f
                    val left = Brush.radialGradient(
                        0f to Color(0x88B71C1C),
                        .75f to Color.Transparent,
                        center = Offset(0f, 0f),
                        radius = radius,
                    )
                    val right = Brush.radialGradient(
                        0f to Color(0x77E91E63),
                        .75f to Color.Transparent,
                        center = Offset(size.width, 0f),
                        radius = radius,
                    )
                    val center = Brush.radialGradient(
                        0f to Color(0x55FF4081),
                        .70f to Color.Transparent,
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * .7f,
                    )
                    onDrawBehind {
                        drawRect(left)
                        drawRect(right)
                        drawRect(center)
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = .62f),
                                ),
                            ),
                        )
                    }
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            if (waveform) Waveform(isPlaying)
        }
    }
}

@Composable
private fun ImportSourceScreen(
    onChooseAllDevice: () -> Unit,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Wavv", waveform = true)
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A122A), Color(0xFF17131F))))
                    .border(1.dp, Color.White.copy(.1f), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(58.dp).clip(CircleShape).background(WavvAccentGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("Bring your music to Wavv", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Choose one place for Wavv to read your audio from. You can change this later.", color = Color.White.copy(.62f), fontSize = 15.sp, lineHeight = 21.sp)
            }
            Text("Choose a source", color = WavvMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
            SourceOptionCard(Icons.Default.LibraryMusic, "All music on this device", "Use music indexed by Android.", onChooseAllDevice)
            SourceOptionCard(Icons.Default.MusicNote, "Individual audio files", "Pick one or more files from storage.", onChooseFiles)
            SourceOptionCard(Icons.Default.Folder, "A specific folder", "Include audio in this folder and its subfolders.", onChooseFolder)
        }
    }
}

@Composable
private fun SourceOptionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WavvSurfaceGradient)
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(WavvPink.copy(.32f), Color(0xFF7B1FA2).copy(.24f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = WavvPink, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = WavvMuted, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = .3f))
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onChooseSource: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Wavv", waveform = true)
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = WavvPink, modifier = Modifier.size(42.dp))
            Text("Music access is needed", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Allow Wavv to read music indexed on this device, or choose files and folders directly.", color = WavvMuted, fontSize = 16.sp)
            Button(onClick = onAllow, colors = ButtonDefaults.buttonColors(containerColor = WavvPink)) {
                Text("Allow music access")
            }
            Button(onClick = onChooseSource, colors = ButtonDefaults.buttonColors(containerColor = WavvSurface)) {
                Text("Choose files or folder")
            }
        }
    }
}

@Composable
private fun LibraryErrorScreen(message: String, onRetry: () -> Unit, onChooseSource: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Wavv", waveform = true)
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("We could not load your music", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = WavvMuted, fontSize = 16.sp)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = WavvPink)) {
                Text("Try again")
            }
            Button(onClick = onChooseSource, colors = ButtonDefaults.buttonColors(containerColor = WavvSurface)) {
                Text("Choose another source")
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = WavvPink)
        Text("Loading your music…", Modifier.padding(top = 14.dp), color = WavvMuted)
    }
}

@Composable
private fun HomeScreen(
    songs: List<Song>,
    isPlaying: Boolean,
    onChooseSource: () -> Unit,
    onPlay: (Song) -> Unit,
    onAtTopChange: (Boolean) -> Unit,
    recentSongs: List<Song> = emptyList(),
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        onAtTopChange(listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 60)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Wavv", waveform = true, isPlaying = isPlaying)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (songs.isEmpty()) {
                item { EmptyLibraryCard(onChooseSource) }
            } else {
                item { HeroCarousel(songs, onPlay) }
                item { Hairline() }
                item { SongShelf("Recently Played", recentSongs.ifEmpty { songs.take(8) }, onPlay) }
                item { Hairline() }
                item { SongShelf("Forgotten Favourites", songs.drop(1).ifEmpty { songs }.take(8), onPlay) }
                item { Hairline() }
                item { MoodShelf(songs, onPlay) }
                item { Hairline() }
                item { SongShelf("Hip Hop", songs.reversed().take(8), onPlay) }
                item { Hairline() }
                item { SongShelf("Melody", songs.takeLast(8).ifEmpty { songs }, onPlay) }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(onChooseSource: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(20.dp)).background(WavvSurfaceGradient).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = WavvPink, modifier = Modifier.size(32.dp))
        Text("No audio files found", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Choose a different source or add audio files to this location.", color = WavvMuted)
        Button(onClick = onChooseSource) { Text("Choose another source") }
    }
}

@Composable
private fun HeroCarousel(songs: List<Song>, onPlay: (Song) -> Unit) {
    val pageCount = max(1, songs.size)
    val pagerState = rememberPagerState { pageCount }
    LaunchedEffect(pageCount) {
        if (pageCount > 1) while (isActive) {
            delay(10_000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % pageCount)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth()) { page ->
            val song = songs[page % songs.size]
            HeroCard(song, songs.drop(page + 1).ifEmpty { songs }.take(3), onPlay)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pageCount) { index ->
                val selected = index == pagerState.currentPage
                Box(Modifier.padding(horizontal = 3.dp).size(if (selected) 20.dp else 4.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Color.White else Color.White.copy(.25f)))
            }
        }
    }
}

@Composable
private fun HeroCard(song: Song, queue: List<Song>, onPlay: (Song) -> Unit) {
    Box(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(20.dp)).clickable { onPlay(song) }) {
        AlbumArt(song.albumArtUri, Modifier.fillMaxSize(), song.title, 20.dp)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(.04f),
                    .35f to Color.Black.copy(.15f),
                    .62f to Color.Black.copy(.70f),
                    1f to Color.Black.copy(.92f),
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("UP NEXT", color = Color.White.copy(.42f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                Spacer(Modifier.width(6.dp))
                Text(queue.joinToString(" · ") { it.title }, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(song.album, color = Color.White.copy(.55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Color.White.copy(.65f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassPill(onClick = { onPlay(song) }, label = "Play")
            }
        }
    }
}

@Composable
private fun GlassPill(onClick: () -> Unit, label: String) {
    Row(Modifier.height(42.dp).clip(CircleShape).background(Color.White.copy(.18f)).border(1.dp, Color.White.copy(.28f), CircleShape).clickable(onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(Icons.Default.PlayArrow, contentDescription = label, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Color.White.copy(.07f)))

@Composable
private fun SongShelf(label: String, songs: List<Song>, onPlay: (Song) -> Unit) {
    Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfLabel(label)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
            items(songs, key = Song::id) { song ->
                Column(Modifier.width(110.dp).clickable { onPlay(song) }) {
                    AlbumArt(song.albumArtUri, Modifier.size(110.dp), song.title, 12.dp)
                    Text(song.title, Modifier.padding(top = 7.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Color.White.copy(.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MoodShelf(songs: List<Song>, onPlay: (Song) -> Unit) {
    val moods = listOf("Chill" to Color(0xFF1A4A6E), "Focus" to Color(0xFF1A3A2E), "Hype" to Color(0xFF6E1A2E), "Sad" to Color(0xFF2A1A4E), "Happy" to Color(0xFF6E4A1A), "Late Night" to Color(0xFF1A1A4E))
    Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfLabel("Moods")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 20.dp)) {
            itemsIndexed(moods) { index, (name, color) ->
                val song = songs[index % songs.size]
                Box(Modifier.size(140.dp, 80.dp).clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(color, color.copy(.72f), WavvBackground))).clickable { onPlay(song) }) {
                    AlbumArt(song.albumArtUri, Modifier.fillMaxSize().alpha(.45f), song.title, 12.dp)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(color, Color.Transparent))))
                    Text(name, Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ShelfLabel(label: String) = Text(label.uppercase(), color = WavvMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)

@Composable
private fun SearchScreen(
    songs: List<Song>,
    onPlay: (Song) -> Unit,
    hasNowPlaying: Boolean,
    semanticSongs: List<Song>? = null,
    semanticLoading: Boolean = false,
    onQueryChange: (String) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var focused by rememberSaveable { mutableStateOf(false) }
    val visible = remember(songs, query, semanticSongs) {
        val semantic = semanticSongs.orEmpty()
        (semantic + filterSongs(songs, query).filterNot { candidate -> semantic.any { it.id == candidate.id } }).take(12)
    }
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Search")
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (hasNowPlaying) 256.dp else 160.dp),
            ) {
                if (query.isNotBlank()) {
                    item {
                        ShelfLabel(if (semanticSongs != null) "Semantic results" else "Results")
                        Spacer(Modifier.height(4.dp))
                    }
                    if (semanticLoading) item { CircularProgressIndicator(color = WavvPink, modifier = Modifier.padding(horizontal = 20.dp).size(18.dp)) }
                    items(visible, key = Song::id) { song -> SearchResultRow(song, onPlay) }
                } else if (!focused) {
                    item { BrowseCategories(songs, onPlay) }
                } else {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(.18f), modifier = Modifier.size(44.dp))
                            Text("Start typing to search", color = Color.White.copy(.25f), fontSize = 14.sp)
                        }
                    }
                }
            }
            SearchBar(
                query,
                { query = it; onQueryChange(it) },
                focused,
                { focused = it },
                focusManager,
                Modifier.align(Alignment.BottomCenter).padding(bottom = if (hasNowPlaying) 174.dp else 88.dp),
            )
        }
    }
}

@Composable
private fun SearchResultRow(song: Song, onPlay: (Song) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPlay(song) }.padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AlbumArt(song.albumArtUri, Modifier.size(46.dp), song.title, 8.dp)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(.4f), fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.2f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun BrowseCategories(songs: List<Song>, onPlay: (Song) -> Unit) {
    val categories = listOf("Hip-Hop" to Color(0xFF5B2D8E), "Pop" to Color(0xFFD81B60), "Electronic" to Color(0xFF1565C0), "R&B" to Color(0xFFAD1457), "Indie" to Color(0xFFC62828), "Classical" to Color(0xFF2E7D32), "Jazz" to Color(0xFFE65100), "Tamil Indie" to Color(0xFF283593), "Rock" to Color(0xFFBF360C), "Chill" to Color(0xFF00695C))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfLabel("Browse Categories")
        categories.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { cellIndex, (label, color) ->
                    val song = if (songs.isEmpty()) null else songs[(rowIndex * 2 + cellIndex) % songs.size]
                    CategoryCard(label, color, song, onPlay, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryCard(label: String, color: Color, song: Song?, onPlay: (Song) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = .78f), color.copy(alpha = .45f))))
            .clickable(enabled = song != null) { song?.let(onPlay) },
    ) {
        song?.let {
            AlbumArt(
                it.albumArtUri,
                Modifier.align(Alignment.CenterEnd)
                    .offset(x = 6.dp, y = 4.dp)
                    .width(90.dp)
                    .height(110.dp)
                    .graphicsLayer { rotationZ = 8f }
                    .alpha(.60f),
                it.title,
                6.dp,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = .94f), color.copy(alpha = .58f), Color.Transparent),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .38f)))))
        Text(label, Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, focused: Boolean, onFocusChange: (Boolean) -> Unit, focusManager: androidx.compose.ui.focus.FocusManager, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "search hue")
    val washProgress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "search wash")
    val wash = Color(red = .25f + .16f * washProgress, green = .12f, blue = .36f + .16f * washProgress)
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(wash.copy(alpha = .62f), Color(0xFF191923), Color(0xFF11151E)))).border(1.dp, wash.copy(alpha = if (focused) .72f else .42f), CircleShape).padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Search, contentDescription = null, tint = if (focused) Color.White.copy(.6f) else Color.White.copy(.3f), modifier = Modifier.size(17.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { field -> if (query.isEmpty()) Text("Songs, artists, albums...", color = Color.White.copy(.35f), fontSize = 16.sp); field() },
        )
        if (query.isNotEmpty() || focused) IconButton(onClick = { onQueryChange(""); focusManager.clearFocus(); onFocusChange(false) }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LibraryScreen(songs: List<Song>, onPlay: (Song) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Library")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 168.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                val art = songs.take(6)
                art.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { song -> AlbumArt(song.albumArtUri, Modifier.weight(1f).aspectRatio(1f).clickable { onPlay(song) }, song.title, 10.dp) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            item { Hairline(); Spacer(Modifier.height(4.dp)) }
            item { LibraryCategories() }
            item { Hairline() }
            item { Text("Recently Added", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            items(songs.take(8), key = Song::id) { song -> SearchResultRow(song, onPlay) }
        }
    }
}

@Composable
private fun LibraryCategories() {
    val categories = listOf(Icons.AutoMirrored.Filled.PlaylistPlay to "Playlists", Icons.Default.Mic to "Artists", Icons.Default.Album to "Albums", Icons.Default.LibraryMusic to "Songs")
    Column(Modifier.padding(horizontal = 20.dp)) {
        categories.forEachIndexed { index, (icon, label) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(icon, contentDescription = null, tint = WavvPink, modifier = Modifier.size(22.dp))
                Text(label, Modifier.weight(1f), color = Color.White, fontSize = 17.sp)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.22f), modifier = Modifier.size(14.dp))
            }
            if (index != categories.lastIndex) Hairline()
        }
    }
}

@Composable
private fun YouScreen(trackCount: Int, artistCount: Int, minutes: Long, onUnavailable: (String) -> Unit = {}) {
    val settings = listOf(
        Icons.Default.MusicNote to ("Audio Quality" to "High"),
        Icons.Outlined.Wifi to ("Download on Wi-Fi" to "On"),
        Icons.Outlined.Palette to ("Theme" to "Dark"),
        Icons.Outlined.Notifications to ("Notifications" to "All"),
        Icons.Default.LibraryMusic to ("Storage Used" to "On device"),
        Icons.Default.SmartDisplay to ("Share Profile" to ""),
        Icons.Outlined.PrivacyTip to ("Privacy" to ""),
        Icons.AutoMirrored.Outlined.HelpOutline to ("Help & Support" to ""),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("You")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x407B3FF5), Color.Transparent))).padding(horizontal = 20.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7B3FF5), Color(0xFFE8553E)))).border(3.dp, Color(0x667B3FF5), CircleShape), contentAlignment = Alignment.Center) {
                        Text("W", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Local library", Modifier.padding(top = 14.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Music stored on this device", color = Color.White.copy(.45f), fontSize = 13.sp)
                    Surface(Modifier.padding(top = 14.dp).clickable(role = Role.Button, onClick = { onUnavailable("Profile editing is not available yet") }), shape = CircleShape, color = Color.White.copy(.10f), border = BorderStroke(1.dp, Color.White.copy(.15f))) {
                        Text("Edit Profile", Modifier.padding(horizontal = 20.dp, vertical = 7.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfLabel("This Month")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(minutes.toString(), "Minutes", Modifier.weight(1f))
                        StatCard(artistCount.toString(), "Artists", Modifier.weight(1f))
                        StatCard(trackCount.toString(), "Tracks", Modifier.weight(1f))
                    }
                }
            }
            item { SettingsBlock(settings, onUnavailable) }
            item { Text("Wavv · Local Music Player", Modifier.fillMaxWidth().padding(24.dp), color = Color.White.copy(.2f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.06f)).border(1.dp, Color.White.copy(.07f), RoundedCornerShape(12.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(.4f), fontSize = 11.sp)
    }
}

@Composable
private fun SettingsBlock(settings: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, Pair<String, String>>>, onUnavailable: (String) -> Unit = {}) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfLabel("Settings")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WavvSurfaceGradient).border(1.dp, Color.White.copy(.07f), RoundedCornerShape(14.dp))) {
            settings.forEachIndexed { index, (icon, value) ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onUnavailable(value.first) }).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, contentDescription = null, tint = WavvPink.copy(.9f), modifier = Modifier.size(20.dp))
                    Text(value.first, Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
                    if (value.second.isNotEmpty()) Text(value.second, color = Color.White.copy(.35f), fontSize = 13.sp)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.2f), modifier = Modifier.size(14.dp))
                }
                if (index != settings.lastIndex) Hairline()
            }
        }
    }
}

@Composable
private fun BottomNav(active: Tab, onChange: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf(Tab.Home to ("Home" to Icons.Default.Home), Tab.Search to ("Search" to Icons.Default.Search), Tab.Library to ("Library" to Icons.Default.LibraryMusic), Tab.You to ("You" to Icons.Default.Person))
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp).clip(CircleShape).background(WavvNavGradient).border(1.dp, Color.White.copy(.08f), CircleShape).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { (tab, item) ->
            val selected = tab == active
            val itemTransition = updateTransition(selected, label = "${item.first} selection")
            val iconColor by itemTransition.animateColor(label = "${item.first} icon color") { isSelected -> if (isSelected) WavvPink else Color.White.copy(alpha = .88f) }
            val labelColor by itemTransition.animateColor(label = "${item.first} label color") { isSelected -> if (isSelected) WavvPink else Color.White.copy(alpha = .55f) }
            val pillStart by itemTransition.animateColor(label = "${item.first} pill start") { isSelected -> if (isSelected) WavvPink.copy(alpha = .34f) else Color.Transparent }
            val pillEnd by itemTransition.animateColor(label = "${item.first} pill end") { isSelected -> if (isSelected) Color(0xFF7B1FA2).copy(alpha = .18f) else Color.Transparent }
            val iconScale by itemTransition.animateFloat(label = "${item.first} icon scale") { isSelected -> if (isSelected) 1f else .92f }
            Column(Modifier.weight(1f).clip(CircleShape).background(Brush.linearGradient(listOf(pillStart, pillEnd))).clickable { onChange(tab) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(item.second, contentDescription = item.first, tint = iconColor, modifier = Modifier.size(23.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                Text(item.first, color = labelColor, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun NowPlayingBar(song: Song, state: PlaybackState, expanded: Boolean, modifier: Modifier = Modifier, liked: Boolean, onToggle: () -> Unit, onLike: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    val height by animateDpAsState(if (expanded) 144.dp else 70.dp, tween(520, easing = WavvMotionEasing), label = "player bar height")
    val shape = if (expanded) RoundedCornerShape(30.dp) else CircleShape
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 88.dp).height(height).shadow(18.dp, shape).clip(shape).background(WavvGlassGradient).border(1.dp, Color.White.copy(.14f), shape).clickable(onClick = onOpen)) {
        AlbumArt(song.albumArtUri, Modifier.fillMaxSize().blur(28.dp).scale(1.4f).alpha(.25f), song.title, 30.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF6A2F19).copy(alpha = .22f), Color.Transparent, Color.Black.copy(alpha = .28f)))))
        AnimatedContent(targetState = expanded, label = "player bar content") { big ->
            if (big) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AlbumArt(song.albumArtUri, Modifier.size(50.dp), song.title, 11.dp)
                        TrackText(song, Modifier.weight(1f), 15.sp)
                        IconButton(onClick = onLike) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Like", tint = if (liked) WavvPink else Color.White.copy(.55f), modifier = Modifier.size(20.dp)) }
                    }
                    PlayerProgress(state, song, showUhq = false)
                    PlaybackButtons(state.isPlaying, onToggle, onPrevious, onNext, iconSize = 38.dp)
                }
            } else {
                Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AlbumArt(song.albumArtUri, Modifier.size(48.dp), song.title, 10.dp)
                    TrackText(song, Modifier.weight(1f), 14.sp)
                    IconButton(onClick = onToggle) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (state.isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(22.dp)) }
                        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White.copy(.6f), modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TrackText(song: Song, modifier: Modifier = Modifier, titleSize: TextUnit = 14.sp) {
    Column(modifier) {
        Text(song.title, color = Color.White, fontSize = titleSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = Color.White.copy(.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaybackButtons(isPlaying: Boolean, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, iconSize: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(iconSize)) }
        IconButton(onClick = onToggle) {
            if (isPlaying) {
                Image(painterResource(R.drawable.pause), "Pause", Modifier.size(iconSize), colorFilter = ColorFilter.tint(Color.White))
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(iconSize))
            }
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(iconSize)) }
    }
}

@Composable
private fun FullPlayer(song: Song, state: PlaybackState, liked: Boolean, showQueue: Boolean, onClose: () -> Unit, onToggle: () -> Unit, onLike: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit, loopMode: LoopMode, onCycleLoop: () -> Unit, onShowQueue: () -> Unit, onHideQueue: () -> Unit, onUnavailable: (String) -> Unit, onPlaySong: (Song) -> Unit) {
    var showNextSong by remember { mutableStateOf(false) }
    var nudgeNextUp by remember { mutableStateOf(false) }
    val nextTitle = state.queue.firstOrNull { it.id != song.id }?.title ?: "Next Up"
    LaunchedEffect(showQueue, nextTitle) {
        while (isActive && !showQueue) {
            delay(4_000)
            showNextSong = !showNextSong
        }
    }
    LaunchedEffect(showQueue) {
        while (isActive && !showQueue) {
            delay(2_600)
            nudgeNextUp = true
            delay(550)
            nudgeNextUp = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expandedArt by animateDpAsState(if (state.isPlaying) minOf(maxWidth - 32.dp, 300.dp) else minOf(maxWidth * .76f, 300.dp), tween(620, easing = WavvMotionEasing), label = "player art")
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onVerticalDrag = { _, amount -> total += amount },
                onDragEnd = { if (total > 80) onClose() else if (total < -80) onShowQueue() },
            )
        }) {
        if (state.isPlaying) {
            AlbumArt(
                song.albumArtUri,
                Modifier.fillMaxSize().scale(1.2f).blur(60.dp).alpha(.45f),
                song.title,
                0.dp,
            )
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x660E0504), .48f to Color.Transparent, 1f to Color(0xEB080607))),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0f to Color(0x663B160D),
                        .85f to Color.Transparent,
                        center = Offset(0f, 0f),
                    ),
                ),
            )
            Box(
                Modifier.align(Alignment.TopCenter).size(expandedArt).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.08f), Color.Black.copy(.86f))),
                ),
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowDownward, contentDescription = "Close player", tint = Color.White.copy(.75f)) }
                Text("NOW PLAYING", color = Color.White.copy(.35f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                IconButton(onClick = onShowQueue) { Image(painterResource(R.drawable.menu), "Queue", Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White.copy(.75f))) }
            }
            Spacer(Modifier.height(32.dp))
            AlbumArt(song.albumArtUri, Modifier.size(expandedArt), song.title, if (state.isPlaying) 20.dp else 16.dp)
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().alpha(if (showQueue) 0f else 1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TrackText(song, Modifier.weight(1f), 28.sp)
                    IconButton(onClick = onLike) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Like", tint = if (liked) WavvPink else Color.White, modifier = Modifier.size(26.dp)) }
                    IconButton(onClick = { onUnavailable("More actions are not available yet") }) { Image(painterResource(R.drawable.menu), "More", Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White.copy(.7f))) }
                }
                PlayerProgress(state, song, showUhq = true, onSeek = onSeek, onUhqClick = { onUnavailable("UHQ enhancement is not available yet") })
                PlaybackButtons(state.isPlaying, onToggle, onPrevious, onNext, iconSize = 40.dp)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { onUnavailable("Lyrics are not available yet") }) { Image(painterResource(R.drawable.song_lyrics), "Lyrics", Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White.copy(.8f))) }
                    IconButton(onClick = { onUnavailable("Casting is not available yet") }) { Image(painterResource(R.drawable.casting), "Airplay", Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White.copy(.8f))) }
                    IconButton(onClick = onCycleLoop) {
                        Icon(
                            if (loopMode == LoopMode.One) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Loop " + loopMode.name,
                            tint = if (loopMode == LoopMode.Off) Color.White.copy(.4f) else Color.White.copy(.8f),
                        )
                    }
                }
                AnimatedContent(targetState = showNextSong, label = "next up label") { showSong ->
                    Text(
                        if (showSong) nextTitle else "Next Up",
                        Modifier.fillMaxWidth().clickable(onClick = onShowQueue).graphicsLayer { translationY = if (nudgeNextUp) -5f else 0f }.padding(bottom = 4.dp),
                        color = Color.White.copy(.45f),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        AnimatedVisibility(visible = showQueue, enter = slideInVertically(tween(440, easing = WavvMotionEasing)) { it }, exit = slideOutVertically(tween(440, easing = WavvMotionEasing)) { it }) {
            QueuePanel(song, state.queue, liked, onLike, onHideQueue, onUnavailable, onPlaySong)
        }
        }
    }
}

@Composable
private fun PlayerProgress(state: PlaybackState, song: Song, showUhq: Boolean, onSeek: (Long) -> Unit = {}, onUhqClick: () -> Unit = {}) {
    val duration = max(state.durationMs, song.durationMs).coerceAtLeast(1L)
    val position = state.positionMs.coerceIn(0L, duration)
    Column(Modifier.fillMaxWidth()) {
        Slider(value = position.toFloat(), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..duration.toFloat(), modifier = Modifier.fillMaxWidth().height(20.dp), colors = SliderDefaults.colors(thumbColor = Color(0xFFFFAE46), activeTrackColor = Color(0xFFFFAE46), inactiveTrackColor = Color.White.copy(.22f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(position), color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (showUhq) Surface(modifier = Modifier.clickable(role = Role.Button, onClick = onUhqClick), shape = RoundedCornerShape(5.dp), color = Color.White.copy(.1f), border = BorderStroke(1.dp, Color.White.copy(.18f))) { Text("UHQ", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = Color.White.copy(.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            Text("-" + formatTime((duration - position).coerceAtLeast(0L)), color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QueuePanel(song: Song, queue: List<Song>, liked: Boolean, onLike: () -> Unit, onClose: () -> Unit, onUnavailable: (String) -> Unit, onPlaySong: (Song) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xB8000008)).statusBarsPadding().navigationBarsPadding().padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(72.dp)) {
            AlbumArt(song.albumArtUri, Modifier.padding(start = 24.dp).size(64.dp), song.title, 10.dp)
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp).size(48.dp).clip(CircleShape).background(Color.White.copy(.12f)),
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Back to player", tint = Color.White.copy(.85f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 102.dp, end = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackText(song, Modifier.weight(1f), 16.sp)
            IconButton(onClick = onLike) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Like", tint = if (liked) WavvPink else Color.White.copy(.7f)) }
            IconButton(onClick = { onUnavailable("More actions are not available yet") }) { Image(painterResource(R.drawable.menu), "More", Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White.copy(.7f))) }
        }
        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp)) {
            ShelfLabel("Up Next")
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(queue, key = Song::id) { item ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onPlaySong(item) }).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AlbumArt(item.albumArtUri, Modifier.size(46.dp), item.title, 9.dp)
                    TrackText(item, Modifier.weight(1f), 14.sp)
                    if (item.id == song.id) Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        Surface(
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp).clickable(role = Role.Button, onClick = onClose),
            shape = CircleShape,
            color = Color.White.copy(.1f),
            border = BorderStroke(1.dp, Color.White.copy(.12f)),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White)
                Text("Back to player", color = Color.White.copy(.82f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Waveform(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val bases = listOf(.45f, .65f, .85f, 1f, 1f, .85f, .65f, .45f)
    Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        bases.forEachIndexed { index, base ->
            val scale by transition.animateFloat(.18f, 1f, infiniteRepeatable(tween(580 + index * 35, easing = WavvEaseInOut, delayMillis = index * 40), RepeatMode.Reverse), label = "wave $index")
            Box(Modifier.width(3.dp).height((18 * base).dp).graphicsLayer { scaleY = if (isPlaying) scale else .18f }.clip(RoundedCornerShape(2.dp)).background(listOf(Color(0xFF7B1FA2), WavvPink, Color(0xFFFF4081), Color(0xFFAD1457))[index / 2]))
        }
    }
}

@Composable
private fun AlbumArt(uriString: String?, modifier: Modifier, contentDescription: String?, radius: Dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString) {
        value = uriString?.let { value -> withContext(Dispatchers.IO) { context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream) } }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription, modifier.clip(RoundedCornerShape(radius)), contentScale = ContentScale.Crop)
    } else {
        NoArtwork(modifier, contentDescription, radius)
    }
}

@Composable
private fun NoArtwork(modifier: Modifier, contentDescription: String?, radius: Dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(
            Brush.linearGradient(listOf(Color(0xFF343443), Color(0xFF14141A))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0x557B3FF5), Color.Transparent)),
            ),
        )
        Icon(Icons.Default.MusicNote, contentDescription = contentDescription, tint = Color.White.copy(.68f), modifier = Modifier.size(32.dp))
    }
}

private fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1_000
    return (seconds / 60).toString() + ":" + (seconds % 60).toString().padStart(2, '0')
}

private val PreviewSongs = listOf(
    Song(1, "Midnight Drive", "Synthwave Dreams", "Neon City", 185_000, "content://preview/1", "android.resource://com.wavv.app/drawable/image_18"),
    Song(2, "Neon Nights", "Synthwave Dreams", "Neon City", 192_000, "content://preview/2", "android.resource://com.wavv.app/drawable/image_19"),
    Song(3, "Good Luck, Babe!", "Chappell Roan", "The Rise and Fall", 201_000, "content://preview/3", "android.resource://com.wavv.app/drawable/image_20"),
    Song(4, "Aasa Kooda", "Sai Abhyankkar", "Think Indie Tamil 2024", 224_000, "content://preview/4", "android.resource://com.wavv.app/drawable/image_21"),
    Song(5, "Not Like Us", "Kendrick Lamar", "GNX", 274_000, "content://preview/5", "android.resource://com.wavv.app/drawable/image_22"),
    Song(6, "Beautiful Things", "Benson Boone", "Fireworks & Rollerblades", 180_000, "content://preview/6", "android.resource://com.wavv.app/drawable/image_23"),
)

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = WavvBackground, surface = WavvSurface)) {
        Box(Modifier.fillMaxSize().background(WavvBackground)) { content() }
    }
}

@Preview(name = "Home", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePreview() = PreviewSurface {
    HomeScreen(PreviewSongs, isPlaying = false, onChooseSource = {}, onPlay = {}, onAtTopChange = {})
}

@Preview(name = "Search", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchPreview() = PreviewSurface {
    SearchScreen(PreviewSongs, onPlay = {}, hasNowPlaying = true)
}

@Preview(name = "Library", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryPreview() = PreviewSurface {
    LibraryScreen(PreviewSongs, onPlay = {})
}

@Preview(name = "You", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun YouPreview() = PreviewSurface {
    YouScreen(PreviewSongs.size, PreviewSongs.map(Song::artist).distinct().size, 128)
}

@Preview(name = "Now Playing", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NowPlayingPreview() = PreviewSurface {
    val song = PreviewSongs[1]
    FullPlayer(
        song = song,
        state = PlaybackState(song = song, isPlaying = true, positionMs = 33_000, durationMs = song.durationMs, queue = PreviewSongs),
        liked = false,
        showQueue = false,
        onClose = {},
        onToggle = {},
        onLike = {},
        onPrevious = {},
        onNext = {},
        onSeek = {},
        loopMode = LoopMode.Off,
        onCycleLoop = {},
        onShowQueue = {},
        onHideQueue = {},
        onUnavailable = {},
        onPlaySong = {},
    )
}
