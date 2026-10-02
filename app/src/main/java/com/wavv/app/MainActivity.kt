package com.wavv.app

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val WavvBackground = Color(0xFF0A0A0A)
private val WavvSurface = Color(0xFF202026)
private val WavvFrostedGlass = Color(0xD62A2A31)
private val WavvPink = Color(0xFFFA2D48)
private val WavvMuted = Color.White.copy(alpha = .45f)
private val WavvMotionEasing = CubicBezierEasing(.32f, .72f, 0f, 1f)
private val WavvEaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val WavvScreenEasing = CubicBezierEasing(.22f, 1f, .36f, 1f)
private val albumArtCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount / 1024).coerceAtLeast(1)
}
private val albumArtDecodeLocks = ConcurrentHashMap<String, Mutex>()
private val albumArtDecodeSize = IntSize(768, 768)
@OptIn(ExperimentalTextApi::class)
private val WavvFontFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_variable, FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito_variable, FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito_variable, FontWeight.W700, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito_variable, FontWeight.W800, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.nunito_variable, FontWeight.W900, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)
private val WavvSurfaceGradient = Brush.verticalGradient(
    listOf(Color(0xFF282832), Color(0xFF17171D)),
)
private val WavvAccentGradient = Brush.linearGradient(
    listOf(Color(0xFFB71C1C), Color(0xFFE91E63), Color(0xFFFF4081)),
)
private enum class Tab { Home, Search, Library, You }

private enum class LibraryPage { Main, Liked, Playlists, Artists, Albums, Songs, Playlist, Artist, Album }

private data class LibraryDestination(
    val page: LibraryPage = LibraryPage.Main,
    val itemId: Long = 0L,
    val title: String = "",
    val subtitle: String = "",
) : java.io.Serializable {
    val isDetail get() = page == LibraryPage.Playlist || page == LibraryPage.Artist || page == LibraryPage.Album
}

private enum class LibraryLayout { Grid, List }

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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        playbackController = PlaybackController(this)
        setContent {
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer {
                            LibraryViewModel(
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

    override fun onStart() {
        super.onStart()
        playbackController.connect()
    }

    override fun onStop() {
        playbackController.disconnect()
        super.onStop()
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
            val failedGrants = libraryViewModel.selectFiles(uris)
            if (failedGrants > 0) {
                Toast.makeText(context, "Couldn't keep access to $failedGrants selected file(s).", Toast.LENGTH_LONG).show()
            }
        }
    }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (!libraryViewModel.selectFolder(uri)) {
            Toast.makeText(context, "Couldn't keep access to this folder.", Toast.LENGTH_LONG).show()
        }
    }
    val addMusicFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (!libraryViewModel.addFolder(uri)) {
            Toast.makeText(context, "Couldn't keep access to this folder.", Toast.LENGTH_LONG).show()
        }
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
            is LibrarySource.Files, is LibrarySource.Folder, is LibrarySource.Folders -> libraryViewModel.loadSongs()
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
    LaunchedEffect((libraryState as? LibraryUiState.Ready)?.indexingError) {
        (libraryState as? LibraryUiState.Ready)?.indexingError?.let(showUnavailable)
    }

    MaterialTheme(colorScheme = darkColorScheme(background = WavvBackground, surface = WavvSurface)) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = WavvFontFamily)) {
            WavvContent(
            libraryState = libraryState,
            playbackState = playbackState,
            source = source,
            favoriteIds = (libraryState as? LibraryUiState.Ready)?.favoriteIds.orEmpty(),
            searchResults = (libraryState as? LibraryUiState.Ready)?.searchResults,
            semanticLoading = (libraryState as? LibraryUiState.Ready)?.semanticLoading == true,
            semanticSearchComplete = (libraryState as? LibraryUiState.Ready)?.semanticSearchComplete == true,
            onSearch = libraryViewModel::search,
            onChooseAllDevice = chooseAllDevice,
            onChooseFiles = { chooseFiles.launch(arrayOf("audio/*")) },
            onChooseFolder = { chooseFolder.launch(null) },
            onAddMusicFolder = { addMusicFolder.launch(null) },
            onRemoveMusicFolder = libraryViewModel::removeFolder,
            onRetry = libraryViewModel::loadSongs,
            onChooseSource = libraryViewModel::clearSource,
            onPlay = { song, queue ->
                libraryViewModel.recordPlaybackEvent("play", song.id, selectionSource = "manual_select")
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
            onSetFavorites = libraryViewModel::setFavorites,
            onUnavailable = showUnavailable,
            onRepeatMode = playbackController::setRepeatMode,
            onQueueMode = playbackController::setQueueMode,
            onPlayNext = playbackController::playNext,
            onAddToQueue = playbackController::addToQueue,
            onRemoveQueueItem = playbackController::removeFromQueue,
            onMoveQueueItem = playbackController::moveQueueItem,
            onCreatePlaylist = { name, songId ->
                libraryViewModel.createPlaylist(name) { playlistId ->
                    songId?.let { libraryViewModel.addSongToPlaylist(playlistId, it) }
                }
            },
            onRenamePlaylist = libraryViewModel::renamePlaylist,
            onDeletePlaylist = libraryViewModel::deletePlaylist,
            onAddSongToPlaylist = libraryViewModel::addSongToPlaylist,
            onRemoveSongFromPlaylist = libraryViewModel::removeSongFromPlaylist,
            )
        }
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
    source: LibrarySource?,
    favoriteIds: Set<Long>,
    searchResults: List<Song>?,
    semanticLoading: Boolean,
    semanticSearchComplete: Boolean,
    onSearch: (String) -> Unit,
    onChooseAllDevice: () -> Unit,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    onAddMusicFolder: () -> Unit,
    onRemoveMusicFolder: (String) -> Unit,
    onRetry: () -> Unit,
    onChooseSource: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onSetFavorites: (Set<Long>, Boolean) -> Unit,
    onUnavailable: (String) -> Unit,
    onRepeatMode: (Int) -> Unit,
    onQueueMode: (QueueMode) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onCreatePlaylist: (String, Long?) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddSongToPlaylist: (Long, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long, Long) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var sharedArtworkTransitionActive by remember { mutableStateOf(true) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var loopMode by rememberSaveable { mutableStateOf(LoopMode.Off) }
    var homeAtTop by rememberSaveable { mutableStateOf(true) }
    var libraryDestination by rememberSaveable { mutableStateOf(LibraryDestination()) }
    var previousLibraryDestination by rememberSaveable { mutableStateOf(LibraryDestination()) }
    var libraryLayout by rememberSaveable { mutableStateOf(LibraryLayout.Grid) }
    var pinnedPlaylistIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var createPlaylistName by rememberSaveable { mutableStateOf("") }
    var createPlaylistSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamePlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var renamePlaylistName by rememberSaveable { mutableStateOf("") }
    var deletePlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    val songs = (libraryState as? LibraryUiState.Ready)?.songs.orEmpty()
    val recentlyPlayed = (libraryState as? LibraryUiState.Ready)?.recentlyPlayed.orEmpty()
    val playlists = (libraryState as? LibraryUiState.Ready)?.playlists.orEmpty()
    val activeSong = playbackState.song
    LaunchedEffect(showPlayer) {
        sharedArtworkTransitionActive = true
        delay(700)
        sharedArtworkTransitionActive = false
    }
    val openPlayer: () -> Unit = {
        sharedArtworkTransitionActive = true
        showPlayer = true
    }
    val closePlayer: () -> Unit = {
        sharedArtworkTransitionActive = true
        showPlayer = false
        showQueue = false
    }
    val beginPlayerClose: () -> Unit = { sharedArtworkTransitionActive = true }
    val upNextSongs = activeSong?.let { song ->
        playbackState.queue.dropWhile { it.id != song.id }.drop(1).take(3)
    }.orEmpty()
    val startPlayback: (Song) -> Unit = { song -> onPlay(song, songs.ifEmpty { listOf(song) }) }
    val showLibrary = libraryState is LibraryUiState.Ready
    val navigateLibrary: (LibraryDestination) -> Unit = { destination ->
        previousLibraryDestination = if (destination.isDetail) libraryDestination else LibraryDestination()
        libraryDestination = destination
    }
    val backLibrary = {
        libraryDestination = if (libraryDestination.isDetail) previousLibraryDestination else LibraryDestination()
    }

    BackHandler(enabled = showPlayer || (showLibrary && tab != Tab.Home)) {
        when {
            showPlayer -> closePlayer()
            tab == Tab.Library && libraryDestination.page != LibraryPage.Main -> backLibrary()
            else -> tab = Tab.Home
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedTransitionScope = this
        val navBackdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().background(WavvBackground)) {
        Box(Modifier.fillMaxSize().layerBackdrop(navBackdrop)) {
        if (showLibrary) {
            val tabOffsetPx = (8 * LocalDensity.current.density).toInt()
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(300, easing = WavvScreenEasing)) +
                        slideInVertically(tween(300, easing = WavvScreenEasing)) { tabOffsetPx } +
                        scaleIn(initialScale = .995f, animationSpec = tween(300, easing = WavvScreenEasing))) togetherWith fadeOut(tween(1))
                },
                label = "tab content",
            ) { selectedTab ->
                when (selectedTab) {
                    Tab.Home -> HomeScreen(
                        songs = songs,
                        favoriteIds = favoriteIds,
                        isPlaying = playbackState.isPlaying,
                        onChooseSource = onChooseSource,
                        onPlay = startPlayback,
                        recentSongs = recentlyPlayed,
                        upNextSongs = upNextSongs,
                        onAtTopChange = { homeAtTop = it },
                    )
                    Tab.Search -> SearchScreen(
                        songs = songs,
                        searchResults = searchResults,
                        semanticLoading = semanticLoading,
                        semanticSearchComplete = semanticSearchComplete,
                        onQueryChange = onSearch,
                        onPlay = startPlayback,
                        hasNowPlaying = activeSong != null,
                    )
                    Tab.Library -> LibraryScreen(
                        destination = libraryDestination,
                        songs = songs,
                        hasNowPlaying = activeSong != null,
                        favoriteIds = favoriteIds,
                        playlists = playlists,
                        layout = libraryLayout,
                        pinnedPlaylistIds = pinnedPlaylistIds,
                        onNavigate = navigateLibrary,
                        onBack = backLibrary,
                        onToggleLayout = { libraryLayout = if (libraryLayout == LibraryLayout.Grid) LibraryLayout.List else LibraryLayout.Grid },
                        onPlay = onPlay,
                        onShuffle = { queue -> queue.shuffled().takeIf { it.isNotEmpty() }?.let { onPlay(it.first(), it) } },
                        onSetFavorites = onSetFavorites,
                        onTogglePin = { id -> pinnedPlaylistIds = if (id in pinnedPlaylistIds) pinnedPlaylistIds - id else pinnedPlaylistIds + id },
                        onRequestCreatePlaylist = { songId -> createPlaylistSongId = songId; createPlaylistName = ""; showCreatePlaylist = true },
                        onRequestRenamePlaylist = { playlist -> renamePlaylist = playlist; renamePlaylistName = playlist.name },
                        onRequestDeletePlaylist = { deletePlaylist = it },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddSongToPlaylist = onAddSongToPlaylist,
                        onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
                    )
                    Tab.You -> YouScreen(
                        songs = songs,
                        source = source,
                        onAddMusicFolder = onAddMusicFolder,
                        onRemoveMusicFolder = onRemoveMusicFolder,
                        onChooseAllDevice = onChooseAllDevice,
                    )
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
                onOpen = openPlayer,
                sharedTransitionScope = sharedTransitionScope,
                playerVisible = showPlayer,
                sharedArtworkTransitionActive = sharedArtworkTransitionActive,
            )
        }
        }
        if (showLibrary) BottomNav(tab, { tab = it }, navBackdrop, Modifier.align(Alignment.BottomCenter))
        AnimatedVisibility(
            visible = showPlayer,
            enter = fadeIn(tween(180)) + slideInVertically(tween(620, easing = WavvMotionEasing)) { it },
            exit = fadeOut(tween(1)),
        ) {
            val sharedPlayerVisible =
                transition.currentState == EnterExitState.Visible ||
                    transition.targetState == EnterExitState.Visible
            activeSong?.let {
                FullPlayer(
                    song = it,
                    state = playbackState,
                    liked = it.id in favoriteIds,
                    showQueue = showQueue,
                    onClose = closePlayer,
                    onBeginClose = beginPlayerClose,
                    onToggle = onTogglePlayback,
                    onLike = { onToggleFavorite(it.id) },
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    loopMode = loopMode,
                    onCycleLoop = { loopMode = loopMode.next(); onRepeatMode(loopMode.toPlayerRepeatMode()) },
                    onShowQueue = { showQueue = true },
                    onHideQueue = { showQueue = false },
                    onPlaySong = startPlayback,
                    onAddToQueue = onAddToQueue,
                    queueMode = playbackState.queueMode,
                    onQueueMode = onQueueMode,
                    onRemoveQueueItem = onRemoveQueueItem,
                    onMoveQueueItem = onMoveQueueItem,
                    onPlayNext = onPlayNext,
                    playlists = playlists,
                    sharedTransitionScope = sharedTransitionScope,
                    playerVisible = sharedPlayerVisible,
                    sharedArtworkTransitionActive = sharedArtworkTransitionActive,
                    onAddSongToPlaylist = onAddSongToPlaylist,
                    onCreatePlaylist = { songId -> createPlaylistSongId = songId; createPlaylistName = ""; showCreatePlaylist = true },
                    onNavigateToAlbum = { song -> navigateLibrary(LibraryDestination(LibraryPage.Album, title = song.album, subtitle = song.artist)); tab = Tab.Library; closePlayer() },
                    onNavigateToArtist = { song -> navigateLibrary(LibraryDestination(LibraryPage.Artist, title = song.artist)); tab = Tab.Library; closePlayer() },
                )
            }
        }
        if (showCreatePlaylist) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylist = false },
                title = { Text("Create playlist") },
                text = {
                    OutlinedTextField(
                        value = createPlaylistName,
                        onValueChange = { createPlaylistName = it },
                        singleLine = true,
                        label = { Text("Playlist name") },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = createPlaylistName.isNotBlank(),
                        onClick = {
                            onCreatePlaylist(createPlaylistName, createPlaylistSongId)
                            showCreatePlaylist = false
                        },
                    ) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") } },
            )
        }
        renamePlaylist?.let { playlist ->
            AlertDialog(
                onDismissRequest = { renamePlaylist = null },
                title = { Text("Edit playlist") },
                text = {
                    OutlinedTextField(
                        value = renamePlaylistName,
                        onValueChange = { renamePlaylistName = it },
                        singleLine = true,
                        label = { Text("Name") },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = renamePlaylistName.isNotBlank(),
                        onClick = { onRenamePlaylist(playlist.id, renamePlaylistName); renamePlaylist = null },
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { renamePlaylist = null }) { Text("Cancel") } },
            )
        }
        deletePlaylist?.let { playlist ->
            AlertDialog(
                onDismissRequest = { deletePlaylist = null },
                title = { Text("Delete playlist?") },
                text = { Text("${playlist.name} will be removed. Songs in your library won't be deleted.") },
                confirmButton = { TextButton(onClick = { onDeletePlaylist(playlist.id); deletePlaylist = null }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text("Cancel") } },
            )
        }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, waveform: Boolean = false, isPlaying: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().background(if (waveform) WavvBackground else Color(0xB20A0A0A)),
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
                                0f to Color.Transparent,
                                .34f to Color.Transparent,
                                .66f to Color.Black.copy(alpha = .35f),
                                1f to WavvBackground,
                            ),
                        )
                    }
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = if (waveform) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            if (waveform) Waveform(isPlaying)
        }
        if (!waveform) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color.White.copy(.06f)))
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
    favoriteIds: Set<Long> = emptySet(),
    isPlaying: Boolean,
    onChooseSource: () -> Unit,
    onPlay: (Song) -> Unit,
    onAtTopChange: (Boolean) -> Unit,
    recentSongs: List<Song> = emptyList(),
    upNextSongs: List<Song> = emptyList(),
) {
    val listState = rememberLazyListState()
    val likedSongs = remember(songs, favoriteIds) { songs.filter { it.id in favoriteIds } }
    val albumSongs = remember(songs) {
        songs.filter { it.album.isNotBlank() }
            .distinctBy { it.album.trim().lowercase(Locale.ROOT) }
            .sortedBy { it.album.lowercase(Locale.ROOT) }
    }
    val artistSongs = remember(songs) {
        songs.filter { it.artist.isNotBlank() }
            .distinctBy { it.artist.trim().lowercase(Locale.ROOT) }
            .sortedBy { it.artist.lowercase(Locale.ROOT) }
    }
    val recentlyAdded = remember(songs) { songs.sortedByDescending(Song::modifiedAt) }
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
                item { HeroCarousel(songs, upNextSongs, onPlay) }
                if (recentSongs.isNotEmpty()) {
                    item { Hairline() }
                    item { SongShelf("Recently Played", recentSongs, onPlay) }
                }
                if (likedSongs.isNotEmpty()) {
                    item { Hairline() }
                    item { SongShelf("Liked Songs", likedSongs, onPlay) }
                }
                if (albumSongs.isNotEmpty()) {
                    item { Hairline() }
                    item { AlbumShelf(albumSongs, onPlay) }
                }
                if (artistSongs.isNotEmpty()) {
                    item { Hairline() }
                    item { SongShelf("Artists", artistSongs, onPlay, Song::artist, Song::album) }
                }
                item { Hairline() }
                item { SongShelf("Recently Added", recentlyAdded, onPlay) }
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
private fun HeroCarousel(songs: List<Song>, upNextSongs: List<Song>, onPlay: (Song) -> Unit) {
    val pageCount = minOf(8, songs.size).coerceAtLeast(1)
    val pagerState = rememberPagerState { pageCount }
    LaunchedEffect(pageCount) {
        if (pageCount > 1) while (isActive) {
            delay(10_000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % pageCount)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                pageSize = androidx.compose.foundation.pager.PageSize.Fixed((maxWidth - 56.dp).coerceAtLeast(0.dp)),
                contentPadding = PaddingValues(start = 20.dp, end = 36.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val song = songs[page % songs.size]
                HeroCard(song, upNextSongs, onPlay)
            }
        }
        val indicatorCount = pageCount.coerceAtMost(9)
        val firstIndicatorPage = (pagerState.currentPage - indicatorCount / 2).coerceIn(0, pageCount - indicatorCount)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(indicatorCount) { offset ->
                val index = firstIndicatorPage + offset
                val selected = index == pagerState.currentPage
                Box(Modifier.padding(horizontal = 3.dp).size(if (selected) 20.dp else 4.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Color.White else Color.White.copy(.25f)))
            }
        }
    }
}

@Composable
private fun HeroCard(song: Song, queue: List<Song>, onPlay: (Song) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(.95f).clip(RoundedCornerShape(20.dp)).clickable { onPlay(song) }) {
        AlbumArt(song, Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1f), song.title, 0.dp)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.68f).background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .28f to Color.Black.copy(.48f),
                    .66f to Color.Black.copy(.84f),
                    1f to Color.Black.copy(.96f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, Modifier.padding(top = 2.dp), color = Color.White.copy(.76f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassPill(onClick = { onPlay(song) }, label = "Play")
            }
            queue.firstOrNull()?.let { next ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("NEXT", color = Color.White.copy(.66f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${next.title} · ${next.artist}",
                        color = Color.White.copy(.82f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
private fun SongShelf(
    label: String,
    songs: List<Song>,
    onPlay: (Song) -> Unit,
    titleFor: (Song) -> String = Song::title,
    subtitleFor: (Song) -> String = Song::artist,
) {
    Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfLabel(label)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
            items(songs, key = Song::id) { song ->
                Column(Modifier.width(110.dp).clickable { onPlay(song) }) {
                    AlbumArt(song, Modifier.size(110.dp), song.title, 12.dp)
                    Text(titleFor(song), Modifier.padding(top = 7.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitleFor(song), color = Color.White.copy(.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AlbumShelf(albums: List<Song>, onPlay: (Song) -> Unit) {
    val washes = listOf(Color(0xFF1A4A6E), Color(0xFF1A3A2E), Color(0xFF6E1A2E), Color(0xFF2A1A4E), Color(0xFF6E4A1A), Color(0xFF1A1A4E))
    Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfLabel("Albums")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 20.dp)) {
            itemsIndexed(albums, key = { _, song -> song.id }) { index, song ->
                val color = washes[index % washes.size]
                Box(Modifier.size(140.dp, 80.dp).clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(color, color.copy(.72f), WavvBackground))).clickable { onPlay(song) }) {
                    AlbumArt(song, Modifier.fillMaxSize().alpha(.45f), song.title, 12.dp)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(color, Color.Transparent))))
                    Text(song.album, Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    searchResults: List<Song>?,
    semanticLoading: Boolean = false,
    semanticSearchComplete: Boolean = false,
    onQueryChange: (String) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(songs) {
        if (query.isNotBlank()) onQueryChange(query)
    }
    LaunchedEffect(songs, selectedGenre) {
        val genre = selectedGenre
        if (genre != null && songsForGenre(songs, genre).isEmpty()) {
            selectedGenre = null
        }
    }
    val focusManager = LocalFocusManager.current
    val genreSongs = remember(songs, selectedGenre) {
        selectedGenre?.let { genre -> songsForGenre(songs, genre) }.orEmpty()
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Search")
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (hasNowPlaying) 256.dp else 160.dp),
            ) {
                if (selectedGenre != null) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(selectedGenre.orEmpty(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "All genres",
                                Modifier.clickable(role = Role.Button) {
                                    selectedGenre = null
                                    focused = false
                                }.padding(8.dp),
                                color = WavvPink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (genreSongs.isEmpty()) {
                        item {
                            Text(
                                "No tracks are tagged with this genre.",
                                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                color = WavvMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    itemsIndexed(genreSongs, key = { _, song -> song.id }) { index, song ->
                        SearchResultRow(song, onPlay)
                        if (index != genreSongs.lastIndex) Hairline()
                    }
                } else if (query.isNotBlank()) {
                    item {
                        ShelfLabel(if (semanticSearchComplete) "Semantic results" else "Results")
                        Spacer(Modifier.height(4.dp))
                    }
                    if (semanticLoading) item { CircularProgressIndicator(color = WavvPink, modifier = Modifier.padding(horizontal = 20.dp).size(18.dp)) }
                    itemsIndexed(searchResults.orEmpty(), key = { _, song -> song.id }) { index, song ->
                        SearchResultRow(song, onPlay)
                        if (index != searchResults.orEmpty().lastIndex) Hairline()
                    }
                } else if (!focused) {
                    item {
                        BrowseCategories(songs) { category ->
                            selectedGenre = category
                            query = ""
                            focused = false
                            focusManager.clearFocus()
                            onQueryChange("")
                        }
                    }
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
                { query = it; selectedGenre = null; onQueryChange(it) },
                focused,
                { focused = it; if (it) selectedGenre = null },
                focusManager,
                Modifier.align(Alignment.BottomCenter).padding(bottom = if (hasNowPlaying) 184.dp else 88.dp),
                showClear = selectedGenre != null,
            )
        }
    }
}

@Composable
private fun SearchResultRow(song: Song, onPlay: (Song) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPlay(song) }.padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AlbumArt(song, Modifier.size(46.dp), song.title, 8.dp)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(.4f), fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.2f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun BrowseCategories(songs: List<Song>, onChooseCategory: (String) -> Unit) {
    val categories = remember(songs) { genreCategories(songs) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "BROWSE CATEGORIES",
            Modifier.padding(start = 4.dp, bottom = 2.dp),
            color = WavvMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .4.sp,
        )
        if (categories.isEmpty()) {
            Text("No genre tags with artwork were found in your library.", Modifier.padding(4.dp), color = Color.White.copy(.45f), fontSize = 13.sp)
        } else {
            categories.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { category ->
                        CategoryCard(category, onClick = { onChooseCategory(category.name) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: GenreCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        AlbumArt(category.artwork, Modifier.fillMaxSize(), contentDescription = null, radius = 12.dp)
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .35f to Color.Black.copy(.14f), 1f to Color.Black.copy(.84f))),
        )
        Text(category.name, Modifier.align(Alignment.BottomStart).fillMaxWidth(.9f).padding(start = 12.dp, bottom = 12.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.3).sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, focused: Boolean, onFocusChange: (Boolean) -> Unit, focusManager: androidx.compose.ui.focus.FocusManager, modifier: Modifier = Modifier, showClear: Boolean = false) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(20.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color(0xEB44142A), Color(0xF01C1423), Color(0xF50D0D12))))
            .drawWithCache {
                val pinkWash = Brush.radialGradient(
                    0f to Color(0x42FF4081),
                    1f to Color.Transparent,
                    center = Offset(size.width * .16f, size.height * .2f),
                    radius = size.width * .52f,
                )
                val redWash = Brush.horizontalGradient(0f to Color(0x29B71C1C), .72f to Color.Transparent)
                onDrawBehind {
                    drawRect(pinkWash)
                    drawRect(redWash)
                }
            }
            .border(1.dp, Color(0x2EFF7691), CircleShape)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(androidx.compose.ui.res.painterResource(R.drawable.nav_search), null, Modifier.size(17.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (focused) Color.White.copy(.6f) else Color.White.copy(.3f)))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = WavvFontFamily),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { field -> if (query.isEmpty()) Text("Songs, artists, albums...", color = Color.White.copy(.35f), fontSize = 16.sp); field() },
        )
        if (query.isNotEmpty() || focused || showClear) Box(
            Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(.15f)).clickable {
                onQueryChange("")
                focusManager.clearFocus()
                onFocusChange(false)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryScreen(
    destination: LibraryDestination,
    songs: List<Song>,
    hasNowPlaying: Boolean,
    favoriteIds: Set<Long>,
    playlists: List<UserPlaylist>,
    layout: LibraryLayout,
    pinnedPlaylistIds: Set<Long>,
    onNavigate: (LibraryDestination) -> Unit,
    onBack: () -> Unit,
    onToggleLayout: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSetFavorites: (Set<Long>, Boolean) -> Unit,
    onTogglePin: (Long) -> Unit,
    onRequestCreatePlaylist: (Long?) -> Unit,
    onRequestRenamePlaylist: (UserPlaylist) -> Unit,
    onRequestDeletePlaylist: (UserPlaylist) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddSongToPlaylist: (Long, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long, Long) -> Unit,
) {
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var openPlaylistMenu by remember { mutableStateOf<Long?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var sortMode by rememberSaveable(destination.page) { mutableStateOf("Recently added") }
    val recent = remember(songs) { songs.sortedByDescending(Song::modifiedAt).take(6) }
    val featuredSongs = songs.take(6)
    val playlist = playlists.firstOrNull { it.id == destination.itemId }
    val collectionSongs = remember(destination, songs, favoriteIds, playlists) {
        when (destination.page) {
            LibraryPage.Liked -> songs.filter { it.id in favoriteIds }
            LibraryPage.Songs -> songs
            LibraryPage.Playlist -> playlist?.songIds.orEmpty().mapNotNull { id -> songs.firstOrNull { it.id == id } }
            LibraryPage.Artist -> songs.filter { it.artist == destination.title }
            LibraryPage.Album -> songs.filter { it.album == destination.title && it.artist == destination.subtitle }
            else -> emptyList()
        }
    }
    val collectionTitle = when (destination.page) {
        LibraryPage.Liked -> "Liked Songs"
        LibraryPage.Songs -> "Songs"
        LibraryPage.Playlist -> playlist?.name ?: destination.title
        else -> destination.title
    }
    val collectionSubtitle = when (destination.page) {
        LibraryPage.Liked, LibraryPage.Songs -> "${collectionSongs.size} songs"
        LibraryPage.Playlist -> "${collectionSongs.size} songs"
        LibraryPage.Artist -> "${collectionSongs.size} songs"
        LibraryPage.Album -> destination.subtitle
        else -> ""
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(420, easing = WavvMotionEasing)) { it / 10 }) togetherWith
                (fadeOut(tween(160)) + slideOutHorizontally(tween(340, easing = WavvMotionEasing)) { -it / 10 })
        },
        label = "library route",
    ) { route ->
        when (route.page) {
            LibraryPage.Main -> Column(Modifier.fillMaxSize()) {
                ScreenHeader("Library")
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = if (hasNowPlaying) 260.dp else 176.dp),
                ) {
                    item {
                        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            featuredSongs.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { song ->
                                        Column(
                                            Modifier.weight(1f).clickable { onPlay(song, songs) },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AlbumArt(song, Modifier.fillMaxWidth().aspectRatio(1f).shadow(14.dp, RoundedCornerShape(10.dp)), song.title, 10.dp)
                                            Text(song.title, Modifier.padding(top = 6.dp), color = Color.White.copy(.82f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 14.sp)
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(18.dp)); Hairline() }
                    item {
                        val categories = listOf(
                            Triple(R.drawable.lib_liked, "Liked Songs", LibraryPage.Liked),
                            Triple(R.drawable.lib_playlists, "Playlists", LibraryPage.Playlists),
                            Triple(R.drawable.lib_artists, "Artists", LibraryPage.Artists),
                            Triple(R.drawable.lib_albums, "Albums", LibraryPage.Albums),
                            Triple(R.drawable.lib_songs, "Songs", LibraryPage.Songs),
                        )
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                            categories.forEachIndexed { index, (icon, label, page) ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        onNavigate(LibraryDestination(page = page, title = label))
                                    }.padding(vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Image(androidx.compose.ui.res.painterResource(icon), null, Modifier.size(22.dp))
                                    Text(label, Modifier.weight(1f), color = Color.White, fontSize = 17.sp)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.22f), modifier = Modifier.size(14.dp))
                                }
                                if (index != categories.lastIndex) Hairline()
                            }
                        }
                    }
                    item { Hairline(); Text("Recently Added", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    items(recent, key = Song::id) { item -> SearchResultRow(item) { onPlay(it, songs) } }
                    if (songs.isEmpty()) item { LibraryEmpty("No music in your library", "Choose a music source to add tracks.") }
                }
            }

            LibraryPage.Playlists, LibraryPage.Artists, LibraryPage.Albums -> {
                val cards = when (route.page) {
                    LibraryPage.Playlists -> playlists.sortedWith(
                        compareByDescending<UserPlaylist> { it.id in pinnedPlaylistIds }.thenByDescending(UserPlaylist::updatedAt),
                    ).map { item ->
                        LibraryCard(
                            item.id.toString(), item.name, "${item.songIds.size} songs",
                            songs.firstOrNull { song -> song.id in item.songIds },
                        ) { onNavigate(LibraryDestination(LibraryPage.Playlist, item.id, item.name)) }
                    }
                    LibraryPage.Artists -> songs.groupBy(Song::artist).entries
                        .sortedBy { it.key.lowercase() }
                        .map { (artist, tracks) -> LibraryCard(artist, artist, "${tracks.size} songs", tracks.firstOrNull()) {
                            onNavigate(LibraryDestination(LibraryPage.Artist, title = artist))
                        } }
                    else -> songs.groupBy { it.album to it.artist }.entries
                        .sortedBy { it.key.first.lowercase() }
                        .map { (key, tracks) -> LibraryCard("${key.first}\u0000${key.second}", key.first, key.second, tracks.firstOrNull()) {
                            onNavigate(LibraryDestination(LibraryPage.Album, title = key.first, subtitle = key.second))
                        } }
                }
                Column(Modifier.fillMaxSize()) {
                    LibraryBrowseHeader(
                        title = route.title.ifBlank { route.page.name.lowercase().replaceFirstChar(Char::uppercase) },
                        showLayout = true,
                        layout = layout,
                        onBack = onBack,
                        onToggleLayout = onToggleLayout,
                        showAdd = route.page == LibraryPage.Playlists,
                        showSort = route.page == LibraryPage.Playlists,
                        onAdd = { onRequestCreatePlaylist(null) },
                        onSort = { sortMenu = true },
                    )
                    val orderedCards = remember(cards, sortMode) {
                        when (sortMode) {
                            "Title A–Z" -> cards.sortedBy { it.title.lowercase() }
                            "Artist or creator" -> cards.sortedBy { it.subtitle.lowercase() }
                            else -> cards
                        }
                    }
                    val cardRows = remember(orderedCards) { orderedCards.chunked(2) }
                    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = if (hasNowPlaying) 260.dp else 176.dp)) {
                        if (layout == LibraryLayout.Grid) {
                            items(cardRows.size) { rowIndex ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    cardRows[rowIndex].forEach { card ->
                                        Column(Modifier.weight(1f).clickable(onClick = card.onClick)) {
                                            AlbumArt(card.artSong?.albumArtUri, Modifier.fillMaxWidth().aspectRatio(1f), card.title, 13.dp, card.artSong?.uri)
                                            Text(card.title, Modifier.padding(top = 8.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(card.subtitle, color = Color.White.copy(.44f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (route.page == LibraryPage.Playlists) {
                                                val playlistId = card.id.toLongOrNull()
                                                Box {
                                                    IconButton(onClick = { openPlaylistMenu = playlistId }, modifier = Modifier.size(34.dp)) {
                                                        FigmaMoreIcon(30.dp)
                                                    }
                                                    DropdownMenu(expanded = openPlaylistMenu == playlistId, onDismissRequest = { openPlaylistMenu = null }) {
                                                        DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { openPlaylistMenu = null; playlists.firstOrNull { it.id == playlistId }?.let(onRequestRenamePlaylist) })
                                                        DropdownMenuItem(text = { Text(if (playlistId?.let(pinnedPlaylistIds::contains) == true) "Unpin" else "Pin") }, onClick = { openPlaylistMenu = null; playlistId?.let(onTogglePin) })
                                                        DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { openPlaylistMenu = null; playlists.firstOrNull { it.id == playlistId }?.let(onRequestDeletePlaylist) })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (orderedCards.size % 2 == 1 && rowIndex == cardRows.lastIndex) Spacer(Modifier.weight(1f))
                                }
                            }
                        } else {
                            items(orderedCards, key = LibraryCard::id) { card ->
                                Row(Modifier.fillMaxWidth().clickable(onClick = card.onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    AlbumArt(card.artSong?.albumArtUri, Modifier.size(58.dp), card.title, 10.dp, card.artSong?.uri)
                                    Column(Modifier.weight(1f)) {
                                        Text(card.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(card.subtitle, color = Color.White.copy(.44f), fontSize = 12.sp)
                                    }
                                    if (route.page == LibraryPage.Playlists) {
                                        val playlistId = card.id.toLongOrNull()
                                        Box {
                                            Box(Modifier.size(36.dp).clickable { openPlaylistMenu = playlistId }, contentAlignment = Alignment.Center) { FigmaMoreIcon(30.dp) }
                                            DropdownMenu(expanded = openPlaylistMenu == playlistId, onDismissRequest = { openPlaylistMenu = null }) {
                                                DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { openPlaylistMenu = null; playlists.firstOrNull { it.id == playlistId }?.let(onRequestRenamePlaylist) })
                                                DropdownMenuItem(text = { Text(if (playlistId?.let(pinnedPlaylistIds::contains) == true) "Unpin" else "Pin") }, onClick = { openPlaylistMenu = null; playlistId?.let(onTogglePin) })
                                                DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { openPlaylistMenu = null; playlists.firstOrNull { it.id == playlistId }?.let(onRequestDeletePlaylist) })
                                            }
                                        }
                                    }
                                }
                                Hairline()
                            }
                        }
                        if (orderedCards.isEmpty()) item { LibraryEmpty("Nothing here yet", if (route.page == LibraryPage.Playlists) "Create a playlist to organize your music." else "Add music to your library to see it here.") }
                    }
                }
            }

            LibraryPage.Liked, LibraryPage.Songs, LibraryPage.Playlist, LibraryPage.Artist, LibraryPage.Album -> {
                LibraryCollectionScreen(
                    title = if (route == destination) collectionTitle else route.title,
                    subtitle = if (route == destination) collectionSubtitle else route.subtitle,
                    songs = if (route == destination) collectionSongs else when (route.page) {
                        LibraryPage.Liked -> songs.filter { it.id in favoriteIds }
                        LibraryPage.Songs -> songs
                        LibraryPage.Playlist -> playlists.firstOrNull { it.id == route.itemId }?.songIds.orEmpty().mapNotNull { id -> songs.firstOrNull { it.id == id } }
                        LibraryPage.Artist -> songs.filter { it.artist == route.title }
                        LibraryPage.Album -> songs.filter { it.album == route.title && it.artist == route.subtitle }
                    },
                    playlists = playlists,
                    favoriteIds = favoriteIds,
                    sortMode = sortMode,
                    playAsText = route.page == LibraryPage.Liked || route.page == LibraryPage.Songs || route.page == LibraryPage.Playlist,
                    hasNowPlaying = hasNowPlaying,
                    isPlaylist = route.page == LibraryPage.Playlist,
                    isDetail = route.isDetail,
                    onBack = onBack,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    onSetFavorites = onSetFavorites,
                    onRequestRenamePlaylist = { playlist?.let(onRequestRenamePlaylist) },
                    onRequestDeletePlaylist = { playlist?.let(onRequestDeletePlaylist) },
                    onTogglePin = { playlist?.let { onTogglePin(it.id) } },
                    pinned = playlist?.id?.let(pinnedPlaylistIds::contains) == true,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onRequestAddToPlaylist = { songForPlaylist = it },
                    onAddSongToPlaylist = onAddSongToPlaylist,
                    onRemoveSongFromPlaylist = { id -> playlist?.let { onRemoveSongFromPlaylist(it.id, id) } },
                    onNavigateToAlbum = { item -> onNavigate(LibraryDestination(LibraryPage.Album, title = item.album, subtitle = item.artist)) },
                    onNavigateToArtist = { item -> onNavigate(LibraryDestination(LibraryPage.Artist, title = item.artist)) },
                    onSort = { sortMenu = true },
                )
            }
        }
    }

    if (sortMenu) {
        WavvBottomSheet(onDismissRequest = { sortMenu = false }, maxHeight = 320.dp) { close ->
            Text("Sort by", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            listOf("Recently added", "Title A–Z", "Artist or creator").forEach { mode ->
                val selected = sortMode == mode
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape)
                        .background(if (selected) WavvPink.copy(.16f) else Color.White.copy(.06f))
                        .clickable { sortMode = mode; close() }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(mode, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = WavvPink, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    songForPlaylist?.let { selected ->
        AlertDialog(
            onDismissRequest = { songForPlaylist = null },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) Text("Create a playlist to save this song.")
                else Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    playlists.forEach { target ->
                        TextButton(onClick = { onAddSongToPlaylist(target.id, selected.id); songForPlaylist = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(target.name, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songForPlaylist = null; onRequestCreatePlaylist(selected.id) }) { Text("Create playlist") } },
            dismissButton = { TextButton(onClick = { songForPlaylist = null }) { Text("Cancel") } },
        )
    }
}

private data class LibraryCard(val id: String, val title: String, val subtitle: String, val artSong: Song?, val onClick: () -> Unit)

@Composable
private fun LibraryBrowseHeader(
    title: String,
    showLayout: Boolean,
    layout: LibraryLayout,
    onBack: () -> Unit,
    onToggleLayout: () -> Unit,
    showAdd: Boolean,
    showSort: Boolean,
    onAdd: () -> Unit,
    onSort: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(.08f))) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library", tint = Color.White)
        }
        Text(title, Modifier.weight(1f).padding(start = 8.dp), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showSort) IconButton(onClick = onSort, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Tune, contentDescription = "Sort", tint = Color.White.copy(.8f), modifier = Modifier.size(19.dp)) }
        if (showLayout) IconButton(onClick = onToggleLayout, modifier = Modifier.size(38.dp)) {
            Icon(if (layout == LibraryLayout.Grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, contentDescription = if (layout == LibraryLayout.Grid) "Switch to list" else "Switch to grid", tint = Color.White.copy(.8f), modifier = Modifier.size(19.dp))
        }
        if (showAdd) IconButton(onClick = onAdd, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Add, contentDescription = "Create playlist", tint = WavvPink) }
    }
}

@Composable
private fun LibraryEmpty(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(.28f), modifier = Modifier.size(34.dp))
        Text(title, color = Color.White.copy(.8f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(message, color = Color.White.copy(.44f), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun WavvBottomSheet(
    onDismissRequest: () -> Unit,
    maxHeight: Dp = 520.dp,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(dismissing) {
        if (dismissing) {
            delay(260)
            dismiss()
        }
    }

    Dialog(
        onDismissRequest = { dismissing = true },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(160)),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(.5f))
                        .clickable { if (!dismissing) dismissing = true },
                )
            }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                enter = fadeIn(tween(180)) + slideInVertically(tween(280, easing = WavvMotionEasing)) { it },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(240, easing = WavvMotionEasing)) { it },
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = maxHeight)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xF41C1419), Color(0xF20A0A0E))))
                        .border(1.dp, Color.White.copy(.13f), RoundedCornerShape(32.dp))
                        .clickable { }
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { if (!dismissing) dismissing = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(.28f)))
                    }
                    content { if (!dismissing) dismissing = true }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCollectionScreen(
    title: String,
    subtitle: String,
    songs: List<Song>,
    playlists: List<UserPlaylist>,
    favoriteIds: Set<Long>,
    sortMode: String,
    playAsText: Boolean,
    hasNowPlaying: Boolean,
    isPlaylist: Boolean,
    isDetail: Boolean,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSetFavorites: (Set<Long>, Boolean) -> Unit,
    onRequestRenamePlaylist: () -> Unit,
    onRequestDeletePlaylist: () -> Unit,
    onTogglePin: () -> Unit,
    pinned: Boolean,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onAddSongToPlaylist: (Long, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onSort: () -> Unit,
) {
    var featureOffset by remember(title, songs) { mutableStateOf(0) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var addToPlaylist by remember { mutableStateOf(false) }
    val displaySongs = remember(songs, sortMode) {
        when (sortMode) {
            "Title A–Z" -> songs.sortedBy { it.title.lowercase() }
            "Artist or creator" -> songs.sortedBy { it.artist.lowercase() }
            else -> songs
        }
    }
    val featured = displaySongs.getOrNull(featureOffset % displaySongs.size.coerceAtLeast(1))
    LaunchedEffect(title, songs.map(Song::id)) {
        featureOffset = 0
        if (displaySongs.size > 1) while (true) {
            delay(10_000)
            featureOffset = (featureOffset + 1) % displaySongs.size
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(WavvBackground)) {
        val height = minOf(720.dp, maxWidth + 270.dp)
        val artworkHeight = minOf(maxWidth, 480.dp)
        if (featured != null) {
            AnimatedContent(
                targetState = featured,
                transitionSpec = {
                    fadeIn(
                        initialAlpha = .35f,
                        animationSpec = tween(900, easing = CubicBezierEasing(.25f, .1f, .25f, 1f)),
                    ) togetherWith fadeOut(tween(1))
                },
                contentKey = { it.id },
                label = "collection color wash",
            ) { washSong ->
                AlbumArt(washSong, Modifier.fillMaxSize().scale(1.16f).blur(62.dp).alpha(.55f), null, 0.dp)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.12f), Color.Transparent, Color(0xD908070A)))))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (hasNowPlaying) 260.dp else 176.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().height(height)) {
                    if (featured != null) {
                        AnimatedContent(
                            targetState = featured,
                            transitionSpec = {
                                (fadeIn(tween(820, easing = WavvScreenEasing)) +
                                    scaleIn(initialScale = 1.08f, animationSpec = tween(820, easing = WavvScreenEasing))) togetherWith fadeOut(tween(1))
                            },
                            contentKey = { it.id },
                            label = "collection artwork",
                        ) { artSong ->
                            AlbumArt(artSong,
                                Modifier.padding(top = 54.dp).fillMaxWidth().height(artworkHeight),
                                artSong.title,
                                0.dp,
                            )
                        }
                        Box(
                            Modifier.padding(top = 54.dp).fillMaxWidth().height(artworkHeight)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, WavvBackground.copy(.88f)))),
                        )
                    }
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, Modifier.padding(top = 3.dp), color = Color.White.copy(.58f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        featured?.let { Text("${it.title} · ${it.artist}", Modifier.padding(top = 4.dp), color = Color.White.copy(.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconButton(onClick = { onShuffle(displaySongs); featureOffset = displaySongs.indices.filterNot { it == featureOffset }.randomOrNull() ?: 0 }, enabled = displaySongs.isNotEmpty(), modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.12f)).border(1.dp, Color.White.copy(.16f), CircleShape)) {
                                Image(androidx.compose.ui.res.painterResource(R.drawable.queue_mode_shuffle), "Shuffle $title", Modifier.size(23.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
                            }
                            Button(
                                onClick = { displaySongs.firstOrNull()?.let { onPlay(it, displaySongs) } },
                                enabled = displaySongs.isNotEmpty(),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111111)),
                            ) {
                                Image(androidx.compose.ui.res.painterResource(R.drawable.play_solid), null, Modifier.size(13.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF111111)))
                                if (playAsText) Text("Play", Modifier.padding(start = 3.dp), fontWeight = FontWeight.ExtraBold)
                            }
                            IconButton(
                                onClick = { onSetFavorites(displaySongs.map(Song::id).toSet(), !displaySongs.all { it.id in favoriteIds }) },
                                enabled = displaySongs.isNotEmpty(),
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.12f)).border(1.dp, Color.White.copy(.16f), CircleShape),
                            ) {
                            FigmaFavouriteIcon(displaySongs.isNotEmpty() && displaySongs.all { it.id in favoriteIds }, 36.dp)
                            }
                        }
                    }
                }
            }
            item {
                Text("SONGS", Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp), color = Color.White.copy(.45f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
            }
            items(displaySongs, key = Song::id) { item ->
                Row(
                    Modifier.fillMaxWidth().combinedClickable(role = Role.Button, onClick = { onPlay(item, displaySongs) }, onLongClick = { menuSong = item })
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AlbumArt(item, Modifier.size(50.dp), item.title, 9.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.artist, color = Color.White.copy(.47f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box {
                        Box(Modifier.size(36.dp).clickable { menuSong = item }, contentAlignment = Alignment.Center) { FigmaMoreIcon(30.dp) }
                        DropdownMenu(expanded = menuSong?.id == item.id, onDismissRequest = { menuSong = null }) {
                            DropdownMenuItem(text = { Text("Play next") }, onClick = { menuSong = null; onPlayNext(item) })
                            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menuSong = null; onAddToQueue(item) })
                            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menuSong = null; onRequestAddToPlaylist(item) })
                            DropdownMenuItem(text = { Text("Go to album") }, onClick = { menuSong = null; onNavigateToAlbum(item) })
                            DropdownMenuItem(text = { Text("Go to artist") }, onClick = { menuSong = null; onNavigateToArtist(item) })
                            if (isPlaylist) DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menuSong = null; onRemoveSongFromPlaylist(item.id) })
                        }
                    }
                }
                Hairline()
            }
            if (songs.isEmpty()) item { LibraryEmpty("No songs yet", "Add music to your library, then come back here.") }
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(.2f))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (isDetail) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(.2f))) {
                        FigmaMoreIcon(30.dp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (isPlaylist) {
                            DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { expanded = false; onRequestRenamePlaylist() })
                            DropdownMenuItem(text = { Text(if (pinned) "Unpin" else "Pin") }, onClick = { expanded = false; onTogglePin() })
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { expanded = false; onRequestDeletePlaylist() })
                        }
                        displaySongs.firstOrNull()?.let { first ->
                            DropdownMenuItem(text = { Text("Play next") }, onClick = { expanded = false; onPlayNext(first) })
                            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { expanded = false; onAddToQueue(first) })
                        }
                    }
                }
            } else {
                IconButton(onClick = onSort, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(.2f))) {
                    Icon(Icons.Default.Tune, contentDescription = "Sort songs", tint = Color.White)
                }
            }
        }
    }
    if (addToPlaylist) {
        AlertDialog(
            onDismissRequest = { addToPlaylist = false },
            title = { Text("Add to another playlist") },
            text = {
                val targets = playlists.filterNot { it.name == title }
                if (targets.isEmpty()) Text("There are no other playlists yet.")
                else Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    targets.forEach { target ->
                        TextButton(
                            onClick = {
                                displaySongs.forEach { onAddSongToPlaylist(target.id, it.id) }
                                addToPlaylist = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(target.name, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addToPlaylist = false }) { Text("Close") } },
        )
    }
}

private enum class ProfileSettingContent { Info, Theme, Folders, Storage, Notifications }

private data class ProfileSetting(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val value: String,
    val detail: String,
    val content: ProfileSettingContent = ProfileSettingContent.Info,
)

private fun LibrarySource?.folderUris(): List<String> = when (this) {
    is LibrarySource.Folder -> listOf(uri)
    is LibrarySource.Folders -> uris.distinct().sorted()
    else -> emptyList()
}

private fun LibrarySource?.sourceSummary(): String = when (this) {
    LibrarySource.AllDevice -> "All device music"
    is LibrarySource.Files -> "${uris.size} selected files"
    is LibrarySource.Folder -> "1 folder"
    is LibrarySource.Folders -> if (uris.isEmpty()) "No folders selected" else "${uris.size} folders"
    null -> "Not selected"
}

private fun folderDisplayName(uriString: String): String = runCatching {
    Uri.decode(Uri.parse(uriString).lastPathSegment.orEmpty())
        .substringAfter(':')
        .substringAfterLast('/')
        .ifBlank { "Storage folder" }
}.getOrDefault("Selected folder")

@Composable
private fun YouScreen(
    songs: List<Song>,
    source: LibrarySource?,
    onAddMusicFolder: () -> Unit,
    onRemoveMusicFolder: (String) -> Unit,
    onChooseAllDevice: () -> Unit,
) {
    val context = LocalContext.current
    val trackCount = songs.size
    val artistCount = songs.asSequence()
        .map { it.artist.trim() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .count()
    val albumCount = songs.asSequence()
        .map { it.album.trim() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .count()
    val storageBytes = songs.sumOf(Song::fileSizeBytes)
    val storageUsed = remember(songs, context) {
        storageBytes.takeIf { it > 0L }?.let { android.text.format.Formatter.formatShortFileSize(context, it) } ?: "—"
    }
    val notificationsEnabled = context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
    val settings = listOf(
        ProfileSetting(Icons.Outlined.Palette, "Theme", "Dark", "Dark appearance is currently the only theme available.", ProfileSettingContent.Theme),
        ProfileSetting(Icons.Outlined.Notifications, "Notifications", if (notificationsEnabled) "Allowed" else "Blocked", "Notification access is controlled by Android for this app.", ProfileSettingContent.Notifications),
        ProfileSetting(Icons.Default.Folder, "Music Folders", source.sourceSummary(), "Choose one or more folders that Wavv scans for audio and adds to your playback library.", ProfileSettingContent.Folders),
        ProfileSetting(Icons.Default.LibraryMusic, "Storage Used", storageUsed, "Storage reported for the audio files currently indexed in your library.", ProfileSettingContent.Storage),
        ProfileSetting(Icons.Outlined.PrivacyTip, "Privacy", "", "Your selected library and playback history are stored locally on this device."),
        ProfileSetting(Icons.AutoMirrored.Outlined.HelpOutline, "Help & Support", "", "Wavv is a local music player. For app permissions, open Android app settings."),
    )
    var activeSetting by remember { mutableStateOf<ProfileSetting?>(null) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("You")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Box(Modifier.size(96.dp).shadow(24.dp, CircleShape).clip(CircleShape).background(Color(0xFF6250D6)).border(3.dp, Color(0x667B3FF5), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = "You", tint = Color.White, modifier = Modifier.size(46.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("You", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.6).sp, lineHeight = 29.sp)
                            Text("Your library, on this device", color = Color.White.copy(.62f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Surface(
                                Modifier.padding(top = 8.dp),
                                shape = CircleShape,
                                color = Color.White.copy(.12f),
                                border = BorderStroke(1.dp, Color.White.copy(.18f)),
                            ) {
                                Text("NO ACCOUNT REQUIRED", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShelfLabel("On this device")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(trackCount.toString(), "Tracks", Modifier.weight(1f))
                            StatCard(artistCount.toString(), "Artists", Modifier.weight(1f))
                            StatCard(albumCount.toString(), "Albums", Modifier.weight(1f))
                        }
                    }
                }
                item { SettingsBlock(settings) { activeSetting = it } }
                item { Text("Wavv · Local Music Player", Modifier.fillMaxWidth().padding(24.dp), color = Color.White.copy(.2f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
        activeSetting?.let { setting ->
            WavvBottomSheet(onDismissRequest = { activeSetting = null }, maxHeight = 460.dp) { _ ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(.09f)), contentAlignment = Alignment.Center) {
                        Icon(setting.icon, contentDescription = null, tint = Color.White.copy(.85f), modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(setting.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(setting.value, color = Color.White.copy(.52f), fontSize = 12.sp)
                    }
                }
                Text(setting.detail, Modifier.padding(top = 16.dp, bottom = 12.dp), color = Color.White.copy(.62f), fontSize = 14.sp, lineHeight = 20.sp)
                when (setting.content) {
                    ProfileSettingContent.Theme -> SettingInfoRow(setting.value, "Current app appearance", "Selected")
                    ProfileSettingContent.Folders -> {
                        val folders = source.folderUris()
                        if (folders.isEmpty()) {
                            SettingInfoRow("Current source", source.sourceSummary(), null)
                        } else {
                            LazyColumn(
                                Modifier.heightIn(max = 210.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                items(folders, key = { it }) { folderUri ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                            .background(Color.White.copy(.065f))
                                            .border(1.dp, Color.White.copy(.07f), RoundedCornerShape(16.dp))
                                            .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White.copy(.68f), modifier = Modifier.size(18.dp))
                                        Text(folderDisplayName(folderUri), Modifier.weight(1f), color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        TextButton(onClick = { onRemoveMusicFolder(folderUri) }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = onAddMusicFolder, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add folder")
                        }
                        TextButton(onClick = onChooseAllDevice, modifier = Modifier.fillMaxWidth()) {
                            Text("Scan all device music")
                        }
                    }
                    ProfileSettingContent.Storage -> {
                        SettingInfoRow("Indexed audio", storageUsed, null)
                        Spacer(Modifier.height(8.dp))
                        SettingInfoRow("Tracks in library", trackCount.toString(), null)
                    }
                    ProfileSettingContent.Notifications -> {
                        SettingInfoRow("Android notifications", if (notificationsEnabled) "Allowed" else "Blocked", null)
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open notification settings") }
                    }
                    ProfileSettingContent.Info -> SettingInfoRow(setting.title, setting.value, null)
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.06f)).border(1.dp, Color.White.copy(.07f), RoundedCornerShape(12.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White.copy(.4f), fontSize = 11.sp)
    }
}

@Composable
private fun SettingInfoRow(title: String, value: String, trailing: String?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.065f))
            .border(1.dp, Color.White.copy(.07f), RoundedCornerShape(22.dp))
            .padding(horizontal = 17.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (value.isNotBlank()) Text(value, color = Color.White.copy(.48f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        trailing?.let { Text(it, color = WavvPink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(CircleShape)
            .background(Color.White.copy(.07f))
            .border(1.dp, Color.White.copy(.08f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(.82f), modifier = Modifier.size(21.dp))
        Text(title, Modifier.weight(1f), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.3f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsBlock(settings: List<ProfileSetting>, onSelect: (ProfileSetting) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfLabel("Settings")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.05f)).border(1.dp, Color.White.copy(.07f), RoundedCornerShape(14.dp))) {
            settings.forEachIndexed { index, setting ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onSelect(setting) }).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.06f)), contentAlignment = Alignment.Center) {
                        Icon(setting.icon, contentDescription = null, tint = Color.White.copy(.72f), modifier = Modifier.size(18.dp))
                    }
                    Text(setting.title, Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
                    Text(setting.value, color = Color.White.copy(.35f), fontSize = 13.sp)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.2f), modifier = Modifier.size(14.dp))
                }
                if (index != settings.lastIndex) Hairline()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BottomNav(active: Tab, onChange: (Tab) -> Unit, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val tabs = listOf(Tab.Home to "Home", Tab.Search to "Search", Tab.Library to "Library", Tab.You to "You")
    val activeIndex = tabs.indexOfFirst { it.first == active }
    val dragState = remember { AnchoredDraggableState(active) }
    val currentActive by rememberUpdatedState(active)
    val currentOnChange by rememberUpdatedState(onChange)
    val tabSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = dragState,
        positionalThreshold = { distance -> distance * .32f },
        animationSpec = tabSpring,
    )
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.settledValue }.drop(1).collect { settledTab ->
            if (settledTab != currentActive) currentOnChange(settledTab)
        }
    }
    LaunchedEffect(active) {
        if (dragState.targetValue != active) dragState.animateTo(active, tabSpring)
    }
    Box(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp)
            .shadow(20.dp, CircleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        vibrancy()
                        blur(8.dp.toPx())
                    }
                },
                onDrawSurface = { drawRect(Color(0x881A1920)) },
            )
            .clip(CircleShape)
            .border(
                1.dp,
                Brush.verticalGradient(0f to Color.White.copy(.28f), 1f to Color.White.copy(.06f)),
                CircleShape,
            ),
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.White.copy(.12f),
                    .42f to Color.Transparent,
                    1f to Color.Black.copy(.2f),
                ),
            ),
        )
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth(.7f).height(1.dp).background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    .5f to Color.White.copy(.48f),
                    1f to Color.Transparent,
                ),
            ),
        )
        BoxWithConstraints(Modifier.fillMaxWidth().padding(5.dp)) {
            val itemWidth = maxWidth / tabs.size
            val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
            Box(
                Modifier.matchParentSize()
                    .onSizeChanged { size ->
                        if (size.width > 0) {
                            dragState.updateAnchors(
                                DraggableAnchors {
                                    tabs.forEachIndexed { index, (tab, _) ->
                                        tab at (size.width.toFloat() * index / tabs.size)
                                    }
                                },
                                active,
                            )
                        }
                    }
                    .anchoredDraggable(dragState, Orientation.Horizontal, flingBehavior = flingBehavior),
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset {
                            val offset = dragState.offset
                            IntOffset(
                                x = if (offset.isFinite()) offset.roundToInt() else (itemWidthPx * activeIndex).roundToInt(),
                                y = 0,
                            )
                        }
                        .fillMaxWidth(1f / tabs.size)
                        .fillMaxHeight()
                        .shadow(8.dp, CircleShape)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    vibrancy()
                                    blur(2.dp.toPx())
                                    lens(
                                        refractionHeight = 14.dp.toPx(),
                                        refractionAmount = 22.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = true,
                                    )
                                }
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(.10f))
                                drawRect(WavvPink.copy(.10f))
                            },
                        )
                        .clip(CircleShape)
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                0f to Color.White.copy(.38f),
                                1f to Color.White.copy(.1f),
                            ),
                            CircleShape,
                        ),
                ) {
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth(.58f).height(1.dp).background(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                .5f to Color.White.copy(.62f),
                                1f to Color.Transparent,
                            ),
                        ),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { (tab, label) ->
                    val selected = tab == active
                    val itemTransition = updateTransition(selected, label = "$label selection")
                    val iconColor by itemTransition.animateColor(label = "$label icon color") { isSelected -> if (isSelected) WavvPink else Color.White.copy(alpha = .88f) }
                    val labelColor by itemTransition.animateColor(label = "$label label color") { isSelected -> if (isSelected) WavvPink else Color.White.copy(alpha = .55f) }
                    val iconScale by itemTransition.animateFloat(label = "$label icon scale") { isSelected -> if (isSelected) 1.06f else 1f }
                    val iconOffsetY by itemTransition.animateFloat(label = "$label icon offset") { isSelected -> if (isSelected) -1f else 0f }
                    Column(
                        Modifier.weight(1f).clip(CircleShape).clickable(role = Role.Tab) { onChange(tab) }.padding(top = 10.dp, bottom = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(
                                when (tab) {
                                    Tab.Home -> R.drawable.nav_home
                                    Tab.Search -> R.drawable.nav_search
                                    Tab.Library -> if (selected) R.drawable.nav_library_active else R.drawable.nav_library_inactive
                                    Tab.You -> R.drawable.nav_you
                                },
                            ),
                            contentDescription = label,
                            colorFilter = if (tab == Tab.Library) null else androidx.compose.ui.graphics.ColorFilter.tint(iconColor),
                            modifier = Modifier.size(if (tab == Tab.Library) 22.dp else 24.dp, if (tab == Tab.Library) 24.dp else 23.dp).graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                                translationY = iconOffsetY.dp.toPx()
                            },
                        )
                        Text(label, color = labelColor, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(song: Song, state: PlaybackState, expanded: Boolean, modifier: Modifier = Modifier, liked: Boolean, onToggle: () -> Unit, onLike: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit, sharedTransitionScope: SharedTransitionScope, playerVisible: Boolean, sharedArtworkTransitionActive: Boolean) {
    val height by animateDpAsState(if (expanded) 144.dp else 70.dp, tween(520, easing = WavvMotionEasing), label = "player bar height")
    val cornerRadius by animateDpAsState(if (expanded) 30.dp else 999.dp, tween(320, easing = WavvMotionEasing), label = "player bar corners")
    val shape = RoundedCornerShape(cornerRadius)
    val durationMs = max(state.durationMs, song.durationMs).coerceAtLeast(1L)
    val positionMs = state.positionMs.coerceIn(0L, durationMs)
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 96.dp).height(height).shadow(18.dp, shape).clip(shape).background(WavvFrostedGlass).border(1.dp, Color.White.copy(.16f), shape).clickable(onClick = onOpen)) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.White.copy(.06f), 1f to Color.Transparent),
            ),
        )
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(350, delayMillis = 120, easing = WavvMotionEasing)) +
                        scaleIn(initialScale = .96f, animationSpec = tween(400, delayMillis = 100, easing = WavvMotionEasing))) togetherWith
                        fadeOut(tween(180, easing = WavvMotionEasing))
                } else {
                    fadeIn(tween(180, easing = WavvScreenEasing)) togetherWith
                        (fadeOut(tween(350, delayMillis = 120, easing = WavvMotionEasing)) +
                            scaleOut(targetScale = .96f, animationSpec = tween(400, delayMillis = 100, easing = WavvMotionEasing)))
                }
            },
            label = "player bar content",
        ) { big ->
            if (big) {
                Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(50.dp).sharedPlayerArtwork(sharedTransitionScope, sharedArtworkTransitionActive && !playerVisible && big == expanded)) {
                            AlbumArt(song, Modifier.fillMaxSize().shadow(12.dp, RoundedCornerShape(11.dp)), song.title, 11.dp)
                        }
                        TrackText(song, Modifier.weight(1f), 15.sp, subtitleSize = 12.sp, titleLetterSpacing = (-.3).sp)
                        Box(Modifier.size(38.dp).clickable(onClick = onLike), contentAlignment = Alignment.Center) { FigmaFavouriteIcon(liked, 30.dp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column {
                        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(.15f))) {
                            Box(Modifier.fillMaxWidth(positionMs.toFloat() / durationMs).fillMaxHeight().background(Color.White.copy(.8f)))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(positionMs), color = Color.White.copy(.32f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text("-${formatTime(durationMs - positionMs)}", color = Color.White.copy(.32f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    PlaybackButtons(state.isPlaying, onToggle, onPrevious, onNext, iconSize = 40.dp, centerIconSize = 38.dp, modifier = Modifier.weight(1f))
                }
            } else {
                Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).sharedPlayerArtwork(sharedTransitionScope, sharedArtworkTransitionActive && !playerVisible && big == expanded)) {
                        AlbumArt(song, Modifier.fillMaxSize(), song.title, 10.dp)
                    }
                    TrackText(song, Modifier.weight(1f), 14.sp)
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else "Play", Modifier.size(22.dp).clickable(role = Role.Button, onClick = onToggle), tint = Color.White)
                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(20.dp).alpha(.6f).clickable(role = Role.Button, onClick = onNext), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TrackText(song: Song, modifier: Modifier = Modifier, titleSize: TextUnit = 14.sp, subtitleSize: TextUnit = 12.sp, titleLetterSpacing: TextUnit = TextUnit.Unspecified) {
    Column(modifier) {
        Text(song.title, color = Color.White, fontSize = titleSize, fontWeight = FontWeight.Bold, letterSpacing = titleLetterSpacing, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = Color.White.copy(.5f), fontSize = subtitleSize, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Modifier.sharedPlayerArtwork(scope: SharedTransitionScope, visible: Boolean): Modifier =
    with(scope) {
        sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(key = "player-art"),
            visible = visible,
            boundsTransform = { initialBounds, targetBounds ->
                tween(
                    durationMillis = if (initialBounds.width > targetBounds.width) 460 else 620,
                    easing = WavvMotionEasing,
                )
            },
        )
    }

@Composable
private fun PlaybackButtons(isPlaying: Boolean, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, iconSize: Dp, centerIconSize: Dp = iconSize, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(38.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(iconSize).clickable(role = Role.Button, onClick = onPrevious), tint = Color.White)
        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", Modifier.size(centerIconSize).clickable(role = Role.Button, onClick = onToggle), tint = Color.White)
        Icon(Icons.Default.SkipNext, "Next", Modifier.size(iconSize).clickable(role = Role.Button, onClick = onNext), tint = Color.White)
    }
}

@Composable
private fun FigmaFavouriteIcon(filled: Boolean, iconSize: Dp) {
    val fillProgress by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = tween(360, easing = WavvMotionEasing),
        label = "favorite icon transition",
    )
    Canvas(Modifier.size(iconSize)) {
        val unit = size.minDimension / 24f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.White.copy(.15f), radius = 12f * unit)
        val star = Path().apply {
            moveTo(12f * unit, 4.2f * unit)
            cubicTo(12.55f * unit, 8.65f * unit, 15.35f * unit, 11.45f * unit, 19.8f * unit, 12f * unit)
            cubicTo(15.35f * unit, 12.55f * unit, 12.55f * unit, 15.35f * unit, 12f * unit, 19.8f * unit)
            cubicTo(11.45f * unit, 15.35f * unit, 8.65f * unit, 12.55f * unit, 4.2f * unit, 12f * unit)
            cubicTo(8.65f * unit, 11.45f * unit, 11.45f * unit, 8.65f * unit, 12f * unit, 4.2f * unit)
            close()
        }
        val starScale = .84f * (1f - fillProgress) + .46f * fillProgress
        withTransform({
            rotate(degrees = -24f * fillProgress, pivot = center)
            scale(scaleX = starScale, scaleY = starScale, pivot = center)
        }) {
            drawPath(star, Color.White.copy(alpha = .82f * (1f - fillProgress)), style = Stroke(width = .9f * unit, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        val badgeScale = .55f * (1f - fillProgress) + fillProgress
        drawCircle(WavvPink.copy(alpha = fillProgress), radius = 9f * unit * badgeScale)
        val check = Path().apply {
            moveTo(7.8f * unit, 12.1f * unit)
            lineTo(10.45f * unit, 14.8f * unit)
            lineTo(16.3f * unit, 8.8f * unit)
        }
        drawPath(check, Color.White.copy(alpha = fillProgress), style = Stroke(width = 1.9f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun FigmaMoreIcon(iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val unit = size.minDimension / 24f
        drawCircle(Color.White.copy(.15f), radius = 12f * unit)
        listOf(7.5f, 12f, 16.5f).forEach { x -> drawCircle(Color.White, radius = 1.5f * unit, center = Offset(x * unit, 12f * unit)) }
    }
}

@Composable
private fun FullPlayer(
    song: Song,
    state: PlaybackState,
    liked: Boolean,
    showQueue: Boolean,
    queueMode: QueueMode,
    playlists: List<UserPlaylist>,
    onClose: () -> Unit,
    onBeginClose: () -> Unit,
    onToggle: () -> Unit,
    onLike: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    loopMode: LoopMode,
    onCycleLoop: () -> Unit,
    onQueueMode: (QueueMode) -> Unit,
    onShowQueue: () -> Unit,
    onHideQueue: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onAddSongToPlaylist: (Long, Long) -> Unit,
    onCreatePlaylist: (Long) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    playerVisible: Boolean,
    sharedArtworkTransitionActive: Boolean,
) {
    var showLyrics by remember(song.id) { mutableStateOf(false) }
    var showOutputs by remember(song.id) { mutableStateOf(false) }
    var showTrackOptions by remember(song.id) { mutableStateOf(false) }
    var playlistSong by remember(song.id) { mutableStateOf<Song?>(null) }
    var showNextSong by remember { mutableStateOf(false) }
    var nudgeNextUp by remember { mutableStateOf(false) }
    var playerDragOffset by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val gestureScope = rememberCoroutineScope()
    BackHandler(enabled = showLyrics || showQueue) {
        if (showLyrics) showLyrics = false else onHideQueue()
    }
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
        val playerMaxWidth = maxWidth
        val playerHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val dismissalDistancePx = playerHeightPx + with(LocalDensity.current) { 96.dp.toPx() }
        val dismissPlayer: () -> Unit = {
            if (!isDismissing) {
                isDismissing = true
                onBeginClose()
                gestureScope.launch {
                    animate(
                        initialValue = playerDragOffset,
                        targetValue = dismissalDistancePx,
                        animationSpec = tween(460, easing = WavvMotionEasing),
                    ) { value, _ -> playerDragOffset = value }
                    onClose()
                }
            }
        }
        val settlePlayer: () -> Unit = {
            val startingOffset = playerDragOffset
            if (startingOffset > 0f) {
                gestureScope.launch {
                    animate(
                        initialValue = startingOffset,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) { value, _ -> playerDragOffset = value }
                }
            }
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { translationY = playerDragOffset }
                .background(Color.Black)
                .pointerInput(showQueue, isDismissing, playerHeightPx) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onVerticalDrag = { _, amount ->
                    total += amount
                    if (!showQueue && !isDismissing) {
                        playerDragOffset = (playerDragOffset + amount).coerceAtLeast(0f)
                    }
                },
                onDragEnd = {
                    if (showQueue && total > 80) {
                        onHideQueue()
                    } else if (!showQueue && playerDragOffset > 100f) {
                        dismissPlayer()
                    } else if (total < -80 && !showQueue) {
                        onShowQueue()
                    } else {
                        settlePlayer()
                    }
                },
                onDragCancel = { settlePlayer() },
            )
        },
        ) {
        NowPlayingAmbientBackdrop(song, state.isPlaying)
        val targetArtworkSize = if (state.isPlaying) playerMaxWidth else minOf(playerMaxWidth * .76f, 320.dp)
        val artworkSize by animateDpAsState(targetArtworkSize, tween(620, easing = WavvMotionEasing), label = "player artwork size")
        val artworkTop by animateDpAsState(if (state.isPlaying) 64.dp else 84.dp, tween(620, easing = WavvMotionEasing), label = "player artwork position")
        val artworkRadius by animateDpAsState(if (state.isPlaying) 0.dp else 16.dp, tween(620, easing = WavvMotionEasing), label = "player artwork corners")
        val artworkShadow by animateDpAsState(if (state.isPlaying) 0.dp else 20.dp, tween(780, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)), label = "player artwork shadow")
        val regularArtAlpha by animateFloatAsState(if (state.isPlaying) 0f else 1f, tween(780, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)), label = "regular artwork fade")
        val maskedArtAlpha by animateFloatAsState(if (state.isPlaying) 1f else 0f, tween(780, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)), label = "masked artwork fade")
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = artworkTop)
                .size(artworkSize)
                .shadow(artworkShadow, RoundedCornerShape(artworkRadius))
                .clip(RoundedCornerShape(artworkRadius))
                .sharedPlayerArtwork(sharedTransitionScope, playerVisible && sharedArtworkTransitionActive),
        ) {
            AlbumArt(song, Modifier.fillMaxSize().alpha(regularArtAlpha), song.title, 0.dp)
            AlbumArt(song, Modifier.fillMaxSize().alpha(maskedArtAlpha).maskedArtworkFade(), song.title, 0.dp)
        }
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
                .size(width = 36.dp, height = 16.dp).clip(CircleShape).clickable(onClick = dismissPlayer),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(.3f)))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().widthIn(max = 480.dp)
                .navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 18.dp)
                .alpha(if (showQueue) 0f else 1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TrackText(song, Modifier.weight(1f).padding(end = 12.dp), 28.sp, 20.sp, (-.65).sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clickable(onClick = onLike), contentAlignment = Alignment.Center) { FigmaFavouriteIcon(liked, 30.dp) }
                        Box(Modifier.size(38.dp).clickable { showTrackOptions = true }, contentAlignment = Alignment.Center) { FigmaMoreIcon(30.dp) }
                    }
                }
                val duration = max(state.durationMs, song.durationMs).coerceAtLeast(1L)
                val position = state.positionMs.coerceIn(0L, duration)
                Box(
                    Modifier.fillMaxWidth().padding(top = 16.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(.2f))
                        .pointerInput(duration) {
                            detectTapGestures { point -> onSeek((point.x / size.width * duration).toLong().coerceIn(0L, duration)) }
                        }
                        .pointerInput(duration) {
                            detectDragGestures { change, _ ->
                                onSeek((change.position.x / size.width * duration).toLong().coerceIn(0L, duration))
                                change.consume()
                            }
                        },
                ) {
                    Box(Modifier.fillMaxWidth(position.toFloat() / duration).fillMaxHeight().background(Color.White.copy(.8f)))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .2.sp)
                    Row(Modifier.clip(CircleShape).background(Color.White.copy(.15f)).border(1.dp, Color.White.copy(.3f), CircleShape).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Image(androidx.compose.ui.res.painterResource(R.drawable.uhq_mark), null, Modifier.size(13.dp))
                        Text("UHQ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                    }
                    Text("-${formatTime(duration - position)}", color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .2.sp)
                }
            }
            PlaybackButtons(state.isPlaying, onToggle, onPrevious, onNext, iconSize = 48.dp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showLyrics = true }) { Icon(Icons.AutoMirrored.Filled.Subject, "Lyrics", Modifier.size(24.dp).alpha(.7f), tint = Color.White) }
                IconButton(onClick = { showOutputs = true }) { Image(androidx.compose.ui.res.painterResource(R.drawable.output_device), "Audio output", Modifier.size(24.dp).alpha(.7f)) }
                IconButton(onClick = onCycleLoop) {
                    Icon(if (loopMode == LoopMode.One) Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = "Loop " + loopMode.name, tint = if (loopMode == LoopMode.Off) Color.White.copy(.4f) else Color.White.copy(.8f))
                }
            }
            AnimatedContent(targetState = showNextSong, label = "next up label") { showSong ->
                Text(
                    if (showSong) nextTitle else "Next Up",
                    Modifier.fillMaxWidth().clickable(onClick = onShowQueue).graphicsLayer { translationY = if (nudgeNextUp) -5f else 0f }.padding(bottom = 4.dp),
                    color = Color.White.copy(.45f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        AnimatedVisibility(visible = showQueue, enter = slideInVertically(tween(440, easing = WavvMotionEasing)) { it }, exit = slideOutVertically(tween(440, easing = WavvMotionEasing)) { it }) {
            QueuePanel(
                song = song,
                queue = state.queue,
                liked = liked,
                queueMode = queueMode,
                loopMode = loopMode,
                playlists = playlists,
                onLike = onLike,
                onClose = onHideQueue,
                onCycleLoop = onCycleLoop,
                onQueueMode = onQueueMode,
                onPlayNext = onPlayNext,
                onRemoveQueueItem = onRemoveQueueItem,
                onMoveQueueItem = onMoveQueueItem,
                onAddSongToPlaylist = onAddSongToPlaylist,
                onCreatePlaylist = onCreatePlaylist,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onPlaySong = onPlaySong,
            )
        }
        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn(tween(180)) + slideInVertically(tween(460, easing = WavvMotionEasing)) { it },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(420, easing = WavvMotionEasing)) { it },
        ) {
            LyricsPanel(song, state.positionMs, state.durationMs, onSeek, { showLyrics = false })
        }
        if (showOutputs) AudioOutputPanel(song, state.isPlaying, onClose = { showOutputs = false })
        if (showTrackOptions) {
            WavvBottomSheet(onDismissRequest = { showTrackOptions = false }) { close ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    AlbumArt(song, Modifier.size(58.dp), song.title, 12.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Track options", color = Color.White.copy(.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(song.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(14.dp))
                SheetActionRow(Icons.Default.SkipNext, "Play next") { close(); onPlayNext(song) }
                SheetActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Add to queue") { close(); onAddToQueue(song) }
                SheetActionRow(Icons.Default.Add, "Add to playlist") { playlistSong = song; close() }
                SheetActionRow(Icons.Default.Album, "Go to album") { close(); onNavigateToAlbum(song) }
                SheetActionRow(Icons.Default.Mic, "Go to artist") { close(); onNavigateToArtist(song) }
            }
        }
        playlistSong?.let { selectedSong ->
            AlertDialog(
                onDismissRequest = { playlistSong = null },
                title = { Text("Add to playlist") },
                text = {
                    if (playlists.isEmpty()) Text("Create a playlist to save this song.")
                    else Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = { onAddSongToPlaylist(playlist.id, selectedSong.id); playlistSong = null },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(playlist.name, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { playlistSong = null; onCreatePlaylist(selectedSong.id) }) { Text("Create playlist") } },
                dismissButton = { TextButton(onClick = { playlistSong = null }) { Text("Cancel") } },
            )
        }
        }
    }
}

@Composable
private fun NowPlayingAmbientBackdrop(song: Song, isPlaying: Boolean) {
    val pulseTransition = rememberInfiniteTransition(label = "album ambient glow")
    val pulse by pulseTransition.animateFloat(
        initialValue = .35f,
        targetValue = .65f,
        animationSpec = infiniteRepeatable(tween(900, easing = WavvEaseInOut), RepeatMode.Reverse),
        label = "album ambient pulse",
    )
    val backdropAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else .5f,
        animationSpec = tween(550, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)),
        label = "album backdrop opacity",
    )
    val glowVisibility by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(780, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)),
        label = "album glow opacity",
    )
    Box(Modifier.fillMaxSize()) {
        AlbumArt(
            song,
            Modifier.fillMaxSize().scale(1.12f).blur(60.dp).alpha(backdropAlpha),
            null,
            0.dp,
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.45f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(.85f))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.45f)
                .graphicsLayer {
                    scaleX = 1.6f
                    translationY = size.height * .05f
                    alpha = glowVisibility * pulse
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(2.5f) })
                    blendMode = BlendMode.Screen
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .radialArtworkGlowMask(),
        ) {
            AlbumArt(song, Modifier.fillMaxSize().blur(50.dp), null, 0.dp)
        }
    }
}

private fun Modifier.maskedArtworkFade(): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                .35f to Color.Black,
                .60f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private fun Modifier.radialArtworkGlowMask(): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.radialGradient(
                0f to Color.Black,
                .70f to Color.Transparent,
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * .70f,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun PlayerProgress(state: PlaybackState, song: Song, onSeek: (Long) -> Unit = {}) {
    val duration = max(state.durationMs, song.durationMs).coerceAtLeast(1L)
    val position = state.positionMs.coerceIn(0L, duration)
    Column(Modifier.fillMaxWidth()) {
        Slider(value = position.toFloat(), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..duration.toFloat(), modifier = Modifier.fillMaxWidth().height(20.dp), colors = SliderDefaults.colors(thumbColor = Color(0xFFFFAE46), activeTrackColor = Color(0xFFFFAE46), inactiveTrackColor = Color.White.copy(.22f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(position), color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text("-" + formatTime((duration - position).coerceAtLeast(0L)), color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AudioOutputPanel(song: Song, isPlaying: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context.applicationContext) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .build()
    }
    var outputs by remember(router, selector) { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }
    DisposableEffect(router, selector) {
        fun refreshRoutes() {
            outputs = router.routes.filter { it.isEnabled && it.matchesSelector(selector) }
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refreshRoutes()
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refreshRoutes()
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refreshRoutes()
            }

            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                refreshRoutes()
            }

            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                refreshRoutes()
            }

            override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refreshRoutes()
            }
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refreshRoutes()
        onDispose { router.removeCallback(callback) }
    }
    var visible by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val view = LocalView.current
    val density = LocalDensity.current
    val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setDimAmount(.28f)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            dialogWindow?.setBackgroundBlurRadius(with(density) { 8.dp.roundToPx() })
        }
    }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(visible) {
        if (!visible) {
            delay(240)
            close()
        }
    }
    Dialog(
        onDismissRequest = { visible = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .clickable { if (visible) visible = false },
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .statusBarsPadding().navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 72.dp, bottom = 20.dp),
                enter = fadeIn(tween(220)) + slideInVertically(tween(360, easing = WavvMotionEasing)) { it / 5 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(240, easing = WavvMotionEasing)) { it / 5 },
            ) {
                Box(
                    Modifier.fillMaxWidth().widthIn(max = 440.dp).height(480.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF180C08))
                        .border(1.dp, Color(0x47FF8238), RoundedCornerShape(32.dp))
                        .shadow(26.dp, RoundedCornerShape(32.dp)),
                ) {
                    AlbumArt(
                        song,
                        Modifier.fillMaxSize().blur(42.dp).graphicsLayer {
                            scaleX = 1.25f
                            scaleY = 1.25f
                        },
                        null,
                        0.dp,
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color(0x80260E05),
                                .52f to Color(0xC70A0706),
                                1f to Color(0xEB050507),
                            ),
                        ),
                    )
                    Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 18.dp)) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 15.dp)
                                .width(42.dp).height(5.dp).clip(CircleShape)
                                .background(Color.White.copy(.28f))
                                .clickable { if (visible) visible = false },
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AlbumArt(song, Modifier.size(58.dp), null, 12.dp)
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, Modifier.padding(top = 3.dp), color = Color.White.copy(.62f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                OutputWaveform(isPlaying, Modifier.padding(top = 7.dp))
                            }
                        }
                        Column(
                            Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            outputs.forEach { route ->
                                OutputRouteRow(route)
                            }
                            if (outputs.isEmpty()) {
                                Text("No compatible audio outputs are currently available.", Modifier.padding(16.dp), color = Color.White.copy(.55f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputWaveform(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val heights = listOf(4, 7, 10, 6, 9, 5, 8, 4, 7, 5)
    Row(modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEachIndexed { index, height ->
            val transition = rememberInfiniteTransition(label = "output waveform $index")
            val scale by transition.animateFloat(
                initialValue = .18f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(620 + (index % 4) * 80, delayMillis = index * 60, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "output wave bar $index",
            )
            Box(
                Modifier.width(1.dp).height(height.dp).graphicsLayer {
                    scaleY = if (isPlaying) scale else .18f
                    alpha = if (isPlaying) .5f else .28f
                }.clip(CircleShape).background(Color.White),
            )
        }
    }
}

@Composable
private fun OutputRouteRow(route: MediaRouter.RouteInfo) {
    val selected = route.isSelected
    val volumeMax = route.volumeMax
    val volume = if (volumeMax > 0) (route.volume.toFloat() / volumeMax).coerceIn(0f, 1f) else 0f
    val variableVolume = selected && route.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE && volumeMax > 0
    val iconColor = if (selected && volume > .18f) Color(0xFF2D170F) else Color.White
    val titleColor = if (selected && volume > .42f) Color(0xFF2D170F) else Color.White
    val detailColor = if (selected && volume > .58f) Color(0xA62D170F) else Color.White.copy(.54f)
    val checkColor = if (selected && volume > .86f) Color(0xFF2D170F) else Color.White
    val kind = remember(route.id, route.name, route.description, route.isBluetooth) {
        val label = "${route.name} ${route.description.orEmpty()}".lowercase()
        when {
            route.isBluetooth || "bluetooth" in label || "buds" in label -> "bluetooth"
            "cast" in label || "tv" in label || "display" in label -> "cast"
            "headphone" in label || "headset" in label || "wired" in label || "usb" in label -> "wired"
            else -> "phone"
        }
    }
    val isBuiltInPhone = route.name.equals("Phone", ignoreCase = true) || route.name.equals("This phone", ignoreCase = true)
    val outputName = if (isBuiltInPhone) "This phone" else route.name
    val outputDescription = if (isBuiltInPhone && route.description.isNullOrBlank()) {
        "Built-in speaker"
    } else {
        route.description?.takeIf(String::isNotBlank)
            ?: if (route.isBluetooth) "Bluetooth audio" else "Audio output"
    }
    val selectRoute = { if (route.isEnabled && !selected) route.select() }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).clip(CircleShape)
            .background(if (selected) Color(0xA6A98450) else Color.White.copy(.09f))
            .border(1.dp, if (selected) Color(0x4DFFE2B6) else Color.White.copy(.08f), CircleShape)
            .drawBehind {
                if (variableVolume && volume > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFFEDCC), Color(0xE6FFDaa5)),
                            startX = 0f,
                            endX = size.width,
                        ),
                        size = androidx.compose.ui.geometry.Size(size.width * volume, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                    )
                }
            }
            .then(if (selected) Modifier.pointerInput(route.id, variableVolume, volumeMax) {
                if (variableVolume) {
                    detectDragGestures(
                        onDragStart = { point ->
                            val fraction = (point.x / size.width).coerceIn(0f, 1f)
                            route.requestSetVolume((fraction * volumeMax).roundToInt())
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            route.requestSetVolume((fraction * volumeMax).roundToInt())
                        },
                    )
                }
            } else Modifier)
            .clickable(enabled = route.isEnabled, role = Role.Button, onClick = selectRoute),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 17.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(Modifier.width(30.dp).height(38.dp), contentAlignment = Alignment.Center) {
                when (kind) {
                    "wired" -> OutputGlyph("wired", iconColor, Modifier.size(22.dp))
                    "bluetooth" -> OutputGlyph("bluetooth", iconColor, Modifier.size(22.dp))
                    "cast" -> OutputGlyph("cast", iconColor, Modifier.size(22.dp))
                    else -> Image(
                        androidx.compose.ui.res.painterResource(R.drawable.output_device),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(iconColor),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(outputName, color = titleColor, fontSize = 16.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    outputDescription,
                    color = detailColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Currently selected", tint = checkColor, modifier = Modifier.size(20.dp))
            } else {
                Box(Modifier.size(9.dp).border(1.5.dp, Color.White.copy(.3f), CircleShape))
            }
        }
    }
}

@Composable
private fun OutputGlyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val path = Path()
        when (kind) {
            "wired" -> {
                path.moveTo(5f * sx, 13f * sy)
                path.lineTo(5f * sx, 9f * sy)
                path.cubicTo(5f * sx, 5.134f * sy, 8.134f * sx, 2f * sy, 12f * sx, 2f * sy)
                path.cubicTo(15.866f * sx, 2f * sy, 19f * sx, 5.134f * sy, 19f * sx, 9f * sy)
                path.lineTo(19f * sx, 13f * sy)
                path.addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(3f * sx, 12f * sy, 7f * sx, 19f * sy), 2f * sx, 2f * sy))
                path.addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(17f * sx, 12f * sy, 21f * sx, 19f * sy), 2f * sx, 2f * sy))
            }
            "bluetooth" -> {
                path.moveTo(7f * sx, 7f * sy)
                path.lineTo(17f * sx, 17f * sy)
                path.lineTo(12f * sx, 21f * sy)
                path.lineTo(12f * sx, 3f * sy)
                path.lineTo(17f * sx, 7f * sy)
                path.lineTo(7f * sx, 17f * sy)
            }
            "cast" -> {
                path.moveTo(4f * sx, 18.5f * sy)
                path.lineTo(4.01f * sx, 18.5f * sy)
                path.moveTo(4f * sx, 14f * sy)
                path.cubicTo(6.761f * sx, 14f * sy, 9f * sx, 16.239f * sy, 9f * sx, 19f * sy)
                path.moveTo(4f * sx, 9.5f * sy)
                path.cubicTo(9.247f * sx, 9.5f * sy, 13.5f * sx, 13.753f * sy, 13.5f * sx, 19f * sy)
                path.moveTo(5f * sx, 5f * sy)
                path.lineTo(18f * sx, 5f * sy)
                path.cubicTo(19.105f * sx, 5f * sy, 20f * sx, 5.895f * sy, 20f * sx, 7f * sy)
                path.lineTo(20f * sx, 17f * sy)
            }
            else -> {
                path.moveTo(4f * sx, 14.5f * sy)
                path.lineTo(4f * sx, 9.8f * sy)
                path.cubicTo(4f * sx, 8.253f * sy, 5.253f * sx, 7f * sy, 6.8f * sx, 7f * sy)
                path.lineTo(8.6f * sx, 7f * sy)
                path.lineTo(11.8f * sx, 4.3f * sy)
                path.cubicTo(12.4f * sx, 3.8f * sy, 13.3f * sx, 4.2f * sy, 13.3f * sx, 5f * sy)
                path.lineTo(13.3f * sx, 19f * sy)
                path.cubicTo(13.3f * sx, 19.8f * sy, 12.4f * sx, 20.2f * sy, 11.8f * sx, 19.7f * sy)
                path.lineTo(8.6f * sx, 17f * sy)
                path.lineTo(6.5f * sx, 17f * sy)
                path.cubicTo(5.119f * sx, 17f * sy, 4f * sx, 15.881f * sy, 4f * sx, 14.5f * sy)
                path.moveTo(16.5f * sx, 9.2f * sy)
                path.cubicTo(18.5f * sx, 10.8f * sy, 18.5f * sx, 13.2f * sy, 16.5f * sx, 14.8f * sy)
                path.moveTo(19f * sx, 6.8f * sy)
                path.cubicTo(22.2f * sx, 9.6f * sy, 22.2f * sx, 14.4f * sy, 19f * sx, 17.2f * sy)
            }
        }
        drawPath(path, color, style = Stroke(width = 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePanel(
    song: Song,
    queue: List<Song>,
    liked: Boolean,
    queueMode: QueueMode,
    loopMode: LoopMode,
    playlists: List<UserPlaylist>,
    onLike: () -> Unit,
    onClose: () -> Unit,
    onCycleLoop: () -> Unit,
    onQueueMode: (QueueMode) -> Unit,
    onPlayNext: (Song) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onAddSongToPlaylist: (Long, Long) -> Unit,
    onCreatePlaylist: (Long) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onPlaySong: (Song) -> Unit,
) {
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    val rowStepPx = with(LocalDensity.current) { 62.dp.toPx() }
    Column(Modifier.fillMaxSize().background(Color(0xB8000008)).statusBarsPadding().navigationBarsPadding().padding(top = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).pointerInput(onClose) {
                var dragDistance = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = { if (dragDistance > 80f) onClose() },
                )
            },
        ) {
            AlbumArt(song, Modifier.padding(start = 24.dp).size(64.dp), song.title, 10.dp)
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp).size(48.dp).clip(CircleShape).background(Color.White.copy(.12f)),
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Back to player", tint = Color.White.copy(.85f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 102.dp, end = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackText(song, Modifier.weight(1f), 16.sp)
            Box(Modifier.size(38.dp).clickable(onClick = onLike), contentAlignment = Alignment.Center) { FigmaFavouriteIcon(liked, 30.dp) }
        }
        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp)) { ShelfLabel("Up Next") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            itemsIndexed(queue, key = { index, item -> "${item.id}:$index" }) { index, item ->
                val isCurrent = item.id == song.id
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { onPlaySong(item) },
                            onLongClick = { menuIndex = index },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AlbumArt(item, Modifier.size(46.dp), item.title, 9.dp)
                    TrackText(item, Modifier.weight(1f), 14.sp)
                    if (isCurrent) Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                    Box {
                        Box(Modifier.size(34.dp).clickable { menuIndex = index }, contentAlignment = Alignment.Center) { FigmaMoreIcon(30.dp) }
                        DropdownMenu(expanded = menuIndex == index, onDismissRequest = { menuIndex = null }) {
                            DropdownMenuItem(text = { Text("Play next") }, onClick = { menuIndex = null; onPlayNext(item) })
                            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menuIndex = null; playlistSong = item })
                            DropdownMenuItem(text = { Text("Go to album") }, onClick = { menuIndex = null; onNavigateToAlbum(item) })
                            DropdownMenuItem(text = { Text("Go to artist") }, onClick = { menuIndex = null; onNavigateToArtist(item) })
                            if (!isCurrent) DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { menuIndex = null; onRemoveQueueItem(index) })
                        }
                    }
                    IconButton(
                        onClick = { if (index < queue.lastIndex) onMoveQueueItem(index, index + 1) },
                        modifier = Modifier.size(36.dp).pointerInput(index, queue.size) {
                            var dragDistance = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragDistance = 0f },
                                onDragEnd = {
                                    val steps = (dragDistance / rowStepPx).toInt()
                                    if (steps != 0) onMoveQueueItem(index, (index + steps).coerceIn(0, queue.lastIndex))
                                },
                                onDragCancel = { dragDistance = 0f },
                                onDrag = { change, amount -> change.consume(); dragDistance += amount.y },
                            )
                        },
                    ) {
                        Image(androidx.compose.ui.res.painterResource(R.drawable.queue_drag_handle), "Hold and drag to reorder ${item.title}", Modifier.size(18.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(.42f)))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onQueueMode(queueMode.next()) }, modifier = Modifier.size(42.dp).clip(CircleShape).background(if (queueMode == QueueMode.Off) Color.White.copy(.07f) else Color.White.copy(.13f)).border(1.dp, Color.White.copy(.13f), CircleShape)) {
                Image(
                    androidx.compose.ui.res.painterResource(if (queueMode == QueueMode.Shuffle) R.drawable.queue_mode_shuffle else R.drawable.queue_mode_off),
                    contentDescription = if (queueMode == QueueMode.Shuffle) "Shuffle is on. Tap to turn it off" else "Shuffle is off. Tap to turn it on",
                    Modifier.size(23.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (queueMode == QueueMode.Off) Color.White.copy(.42f) else Color.White),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(.1f)).border(1.dp, Color.White.copy(.15f), CircleShape)) {
                Image(androidx.compose.ui.res.painterResource(R.drawable.chevron_down), "Back to Now Playing", Modifier.size(18.dp))
            }
            IconButton(onClick = onCycleLoop, modifier = Modifier.size(42.dp).clip(CircleShape).background(if (loopMode == LoopMode.Off) Color.White.copy(.07f) else Color.White.copy(.13f)).border(1.dp, Color.White.copy(.13f), CircleShape)) {
                Icon(
                    if (loopMode == LoopMode.One) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Loop ${loopMode.name.lowercase()}",
                    tint = if (loopMode == LoopMode.Off) Color.White.copy(.5f) else Color.White,
                )
            }
        }
    }
    playlistSong?.let { selectedSong ->
        AlertDialog(
            onDismissRequest = { playlistSong = null },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Create a playlist to save this song.")
                } else {
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = { onAddSongToPlaylist(playlist.id, selectedSong.id); playlistSong = null },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(playlist.name, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistSong = null; onCreatePlaylist(selectedSong.id) }) { Text("Create playlist") }
            },
            dismissButton = { TextButton(onClick = { playlistSong = null }) { Text("Cancel") } },
        )
    }
}

private fun QueueMode.next() = if (this == QueueMode.Off) QueueMode.Shuffle else QueueMode.Off

@Composable
private fun LyricsPanel(song: Song, positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lyricLines by produceState<List<LyricLine>?>(initialValue = null, song.uri) {
        value = try {
            loadEmbeddedLyrics(context.applicationContext, song.uri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }
    val lyricsState = rememberLazyListState()
    val activeLyricIndex = lyricLines?.indexOfLast { it.timeMs?.let { time -> time <= positionMs } == true } ?: -1
    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex >= 0) lyricsState.animateScrollToItem(activeLyricIndex)
    }
    Box(Modifier.fillMaxSize().background(WavvBackground)) {
        AlbumArt(song, Modifier.fillMaxSize().scale(1.2f).blur(56.dp).alpha(.36f), null, 0.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xE65D0D1E), Color(0xF0100812), Color(0xFA050508)))))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("LYRICS", color = Color.White.copy(.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text(song.title, Modifier.padding(top = 5.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, Modifier.padding(top = 2.dp), color = Color.White.copy(.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(Color.White.copy(.1f))) {
                    Icon(Icons.Default.Close, contentDescription = "Close lyrics", tint = Color.White)
                }
            }
            when {
                lyricLines == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WavvPink, strokeWidth = 2.dp)
                }
                lyricLines.isNullOrEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(.25f), modifier = Modifier.size(42.dp))
                    Text("No readable embedded lyrics", Modifier.fillMaxWidth().padding(top = 16.dp), color = Color.White.copy(.78f), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("This track has no supported lyrics tag.", Modifier.fillMaxWidth().padding(top = 8.dp), color = Color.White.copy(.48f), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = lyricsState,
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(lyricLines.orEmpty()) { index, line ->
                        Text(
                            text = line.text,
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = line.timeMs != null) { line.timeMs?.let(onSeek) }
                                .padding(horizontal = 4.dp),
                            color = if (index == activeLyricIndex) Color.White else Color.White.copy(.52f),
                            fontSize = if (index == activeLyricIndex) 24.sp else 19.sp,
                            lineHeight = if (index == activeLyricIndex) 32.sp else 27.sp,
                            fontWeight = if (index == activeLyricIndex) FontWeight.Bold else FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            PlayerProgress(
                state = PlaybackState(positionMs = positionMs, durationMs = durationMs),
                song = song,
                onSeek = onSeek,
            )
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
private fun AlbumArt(song: Song, modifier: Modifier, contentDescription: String? = song.title, radius: Dp) {
    AlbumArt(song.albumArtUri, modifier, contentDescription, radius, audioUri = song.uri)
}

@Composable
private fun AlbumArt(
    uriString: String?,
    modifier: Modifier,
    contentDescription: String?,
    radius: Dp,
    audioUri: String? = null,
) {
    val context = LocalContext.current
    val sourceKey = "${uriString.orEmpty()}|${audioUri.orEmpty()}"
    val cachedBitmap = remember(sourceKey) { albumArtCache.get(sourceKey) }
    var retainedBitmap by remember { mutableStateOf(cachedBitmap) }
    var displayedSource by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sourceKey) {
        if (uriString == null && audioUri == null) {
            retainedBitmap = null
            displayedSource = sourceKey
        } else {
            val decoded = cachedBitmap ?: withContext(Dispatchers.IO) {
                decodeAlbumArt(context, uriString, audioUri, albumArtDecodeSize)
            }
            if (decoded != null) {
                retainedBitmap = decoded
                displayedSource = sourceKey
            } else if (displayedSource != sourceKey) {
                retainedBitmap = null
                displayedSource = sourceKey
            }
        }
    }
    val bitmap = cachedBitmap ?: retainedBitmap
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription, modifier.clip(RoundedCornerShape(radius)), contentScale = ContentScale.Crop)
    } else {
        NoArtwork(modifier, contentDescription, radius)
    }
}

private suspend fun decodeAlbumArt(context: Context, uriString: String?, audioUri: String?, targetSize: IntSize): Bitmap? {
    val cacheKey = "${uriString.orEmpty()}|${audioUri.orEmpty()}"
    val decodeLock = albumArtDecodeLocks.computeIfAbsent(cacheKey) { Mutex() }
    return decodeLock.withLock {
        try {
        val targetWidth = targetSize.width.coerceAtLeast(1)
        val targetHeight = targetSize.height.coerceAtLeast(1)
        albumArtCache.get(cacheKey)?.let { return@withLock it }

        fun sampleSize(width: Int, height: Int): Int {
            val longestEdge = maxOf(width, height)
            val targetEdge = maxOf(targetWidth, targetHeight)
            var sample = 1
            while (sample <= longestEdge / 2 && longestEdge / (sample * 2) >= targetEdge) {
                sample *= 2
            }
            return sample
        }

        fun decodeBytes(data: ByteArray): Bitmap? {
            if (data.isEmpty() || data.size > MAX_ARTWORK_BYTES) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            })
        }

        fun decodeUri(rawUri: String): Bitmap? {
            val uri = Uri.parse(rawUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }

        val artworkFromUri = uriString?.let { rawUri ->
            runCatching { decodeUri(rawUri) }.getOrNull()
        }
        val bitmap = artworkFromUri ?: audioUri?.let { rawAudioUri ->
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(rawAudioUri))
                retriever.embeddedPicture?.let(::decodeBytes)
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
        bitmap?.also { albumArtCache.put(cacheKey, it) }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
private fun NoArtwork(modifier: Modifier, contentDescription: String?, radius: Dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Color(0xFF202026)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = contentDescription, tint = Color.White.copy(.35f), modifier = Modifier.size(32.dp))
    }
}

private fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1_000
    return (seconds / 60).toString() + ":" + (seconds % 60).toString().padStart(2, '0')
}
