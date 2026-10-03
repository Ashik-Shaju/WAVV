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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal val WavvBackground = Color(0xFF0A0A0A)
internal val WavvSurface = Color(0xFF202026)
internal val WavvFrostedGlass = Color(0xD62A2A31)
internal val WavvPink = Color(0xFFFA2D48)
internal val WavvMuted = Color.White.copy(alpha = .45f)
internal val WavvMotionEasing = CubicBezierEasing(.32f, .72f, 0f, 1f)
internal val WavvEaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
internal val WavvScreenEasing = CubicBezierEasing(.22f, 1f, .36f, 1f)
private val albumArtCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount / 1024).coerceAtLeast(1)
}
private val albumArtDecodeLocks = ConcurrentHashMap<String, Mutex>()
private val albumArtDecodeSize = IntSize(768, 768)
@OptIn(ExperimentalTextApi::class)
internal val WavvFontFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_variable, FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito_variable, FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito_variable, FontWeight.W700, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito_variable, FontWeight.W800, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.nunito_variable, FontWeight.W900, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)
internal val WavvSurfaceGradient = Brush.verticalGradient(
    listOf(Color(0xFF282832), Color(0xFF17171D)),
)
internal val WavvAccentGradient = Brush.linearGradient(
    listOf(Color(0xFFB71C1C), Color(0xFFE91E63), Color(0xFFFF4081)),
)
internal enum class Tab { Home, Search, Library, You }

internal enum class LibraryPage { Main, Liked, Playlists, Artists, Albums, Songs, Playlist, Artist, Album }

internal data class LibraryDestination(
    val page: LibraryPage = LibraryPage.Main,
    val itemId: Long = 0L,
    val title: String = "",
    val subtitle: String = "",
) : java.io.Serializable {
    val isDetail get() = page == LibraryPage.Playlist || page == LibraryPage.Artist || page == LibraryPage.Album
}

internal enum class LibraryLayout { Grid, List }

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
                                MediaLibraryRepository(applicationContext),
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
                state = WavvContentState(libraryState, playbackState, source),
                actions = WavvContentActions(
                    onSearch = libraryViewModel::search,
                    onChooseAllDevice = chooseAllDevice,
                    onChooseFiles = { chooseFiles.launch(arrayOf("audio/*")) },
                    onChooseFolder = { chooseFolder.launch(null) },
                    onAddMusicFolder = { addMusicFolder.launch(null) },
                    onRemoveMusicFolder = libraryViewModel::removeFolder,
                    onRetry = libraryViewModel::loadSongs,
                    onChooseSource = libraryViewModel::clearSource,
                    onPlay = { song, queue -> playbackController.play(song, queue) },
                    onPlayWithRecommendations = playbackController::play,
                    onTogglePlayback = playbackController::toggle,
                    onNext = playbackController::next,
                    onPrevious = playbackController::previous,
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
                    onClearListeningHistory = libraryViewModel::clearListeningHistory,
                ),
            )
        }
    }
}

private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

internal data class WavvContentState(
    val libraryState: LibraryUiState,
    val playbackState: PlaybackState,
    val source: LibrarySource?,
)

internal data class WavvContentActions(
    val onSearch: (String) -> Unit,
    val onChooseAllDevice: () -> Unit,
    val onChooseFiles: () -> Unit,
    val onChooseFolder: () -> Unit,
    val onAddMusicFolder: () -> Unit,
    val onRemoveMusicFolder: (String) -> Unit,
    val onRetry: () -> Unit,
    val onChooseSource: () -> Unit,
    val onPlay: (Song, List<Song>) -> Unit,
    val onPlayWithRecommendations: (Song, List<Song>, Set<Long>, String, String?) -> Unit,
    val onTogglePlayback: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleFavorite: (Long) -> Unit,
    val onSetFavorites: (Set<Long>, Boolean) -> Unit,
    val onUnavailable: (String) -> Unit,
    val onRepeatMode: (Int) -> Unit,
    val onQueueMode: (QueueMode) -> Unit,
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    val onRemoveQueueItem: (Int) -> Unit,
    val onMoveQueueItem: (Int, Int) -> Unit,
    val onCreatePlaylist: (String, Long?) -> Unit,
    val onRenamePlaylist: (Long, String) -> Unit,
    val onDeletePlaylist: (Long) -> Unit,
    val onAddSongToPlaylist: (Long, Long) -> Unit,
    val onRemoveSongFromPlaylist: (Long, Long) -> Unit,
    val onClearListeningHistory: () -> Unit,
)

@Composable
internal fun WavvContent(state: WavvContentState, actions: WavvContentActions) {
    val libraryState = state.libraryState
    val playbackState = state.playbackState
    val source = state.source
    val favoriteIds = (libraryState as? LibraryUiState.Ready)?.favoriteIds.orEmpty()
    val searchResults = (libraryState as? LibraryUiState.Ready)?.searchResults
    val semanticLoading = (libraryState as? LibraryUiState.Ready)?.semanticLoading == true
    val semanticSearchComplete = (libraryState as? LibraryUiState.Ready)?.semanticSearchComplete == true
    val analysisProgress = (libraryState as? LibraryUiState.Ready)?.analysisProgress
    val onSearch = actions.onSearch
    val onChooseAllDevice = actions.onChooseAllDevice
    val onChooseFiles = actions.onChooseFiles
    val onChooseFolder = actions.onChooseFolder
    val onAddMusicFolder = actions.onAddMusicFolder
    val onRemoveMusicFolder = actions.onRemoveMusicFolder
    val onRetry = actions.onRetry
    val onChooseSource = actions.onChooseSource
    val onPlay = actions.onPlay
    val onPlayWithRecommendations = actions.onPlayWithRecommendations
    val onTogglePlayback = actions.onTogglePlayback
    val onNext = actions.onNext
    val onPrevious = actions.onPrevious
    val onSeek = actions.onSeek
    val onToggleFavorite = actions.onToggleFavorite
    val onSetFavorites = actions.onSetFavorites
    val onUnavailable = actions.onUnavailable
    val onRepeatMode = actions.onRepeatMode
    val onQueueMode = actions.onQueueMode
    val onPlayNext = actions.onPlayNext
    val onAddToQueue = actions.onAddToQueue
    val onRemoveQueueItem = actions.onRemoveQueueItem
    val onMoveQueueItem = actions.onMoveQueueItem
    val onCreatePlaylist = actions.onCreatePlaylist
    val onRenamePlaylist = actions.onRenamePlaylist
    val onDeletePlaylist = actions.onDeletePlaylist
    val onAddSongToPlaylist = actions.onAddSongToPlaylist
    val onRemoveSongFromPlaylist = actions.onRemoveSongFromPlaylist
    val onClearListeningHistory = actions.onClearListeningHistory
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
    val personalizedRecommendations = (libraryState as? LibraryUiState.Ready)?.personalizedRecommendations.orEmpty()
    val smartMixes = (libraryState as? LibraryUiState.Ready)?.smartMixes.orEmpty()
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
        nextQueuedSongs(playbackState.queue, song.id)
    }.orEmpty()
    val startPlayback: (Song) -> Unit = { song ->
        val recommendations = personalizedRecommendations.filterNot { it.id == song.id }
        val queue = personalizedQueue(song, songs.ifEmpty { listOf(song) }, recommendations)
        val recommendedIds = recommendations.mapTo(mutableSetOf(), Song::id)
        onPlayWithRecommendations(
            song,
            queue,
            recommendedIds,
            "manual",
            null,
        )
    }
    val startMix: (SmartMix) -> Unit = { mix ->
        mix.songs.firstOrNull()?.let { first ->
            onPlayWithRecommendations(first, mix.songs, mix.songs.mapTo(mutableSetOf(), Song::id), "smart_mix", mix.id)
        }
    }
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
                        smartMixes = smartMixes,
                        onPlayMix = startMix,
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
                        analysisProgress = analysisProgress,
                        onRetryAnalysis = onRetry,
                        onAddMusicFolder = onAddMusicFolder,
                        onRemoveMusicFolder = onRemoveMusicFolder,
                        onChooseAllDevice = onChooseAllDevice,
                        onClearListeningHistory = onClearListeningHistory,
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
        }
        // Backdrop readers must be siblings of the captured layer, not content inside it.
        // Otherwise the mini-player samples a layer that is drawing the mini-player itself.
        if (activeSong != null) {
            NowPlayingBar(
                song = activeSong,
                state = playbackState,
                backdrop = navBackdrop,
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
internal fun ScreenHeader(title: String, waveform: Boolean = false, isPlaying: Boolean = false) {
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
@OptIn(ExperimentalFoundationApi::class)
internal fun BottomNav(active: Tab, onChange: (Tab) -> Unit, backdrop: Backdrop, modifier: Modifier = Modifier) {
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
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(5.dp)
                .anchoredDraggable(dragState, Orientation.Horizontal, flingBehavior = flingBehavior),
        ) {
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
                    },
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
                        .graphicsLayer {
                            val offset = dragState.offset
                            val position = if (offset.isFinite() && itemWidthPx > 0f) {
                                offset / itemWidthPx
                            } else {
                                activeIndex.toFloat()
                            }
                            val progress = position - floor(position)
                            val stretch = sin(progress * PI).toFloat().coerceIn(0f, 1f)
                            scaleX = 1f + stretch * .14f
                            scaleY = 1f - stretch * .06f
                        }
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
            Row(Modifier.fillMaxWidth().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { (tab, label) ->
                    val selected = tab == active
                    val itemTransition = updateTransition(selected, label = "$label selection")
                    val iconColor by itemTransition.animateColor(label = "$label icon color") { isSelected -> if (isSelected) WavvPink else Color.White.copy(alpha = .88f) }
                    val labelColor by itemTransition.animateColor(label = "$label label color") { isSelected -> if (isSelected) WavvPink else Color.White.copy(alpha = .55f) }
                    val iconScale by itemTransition.animateFloat(label = "$label icon scale") { isSelected -> if (isSelected) 1.06f else 1f }
                    val iconOffsetY by itemTransition.animateFloat(label = "$label icon offset") { isSelected -> if (isSelected) -1f else 0f }
                    Column(
                        Modifier.weight(1f)
                            .clip(CircleShape)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onChange(tab) })
                            .padding(top = 10.dp, bottom = 9.dp),
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
private fun NowPlayingBar(song: Song, state: PlaybackState, backdrop: Backdrop, expanded: Boolean, modifier: Modifier = Modifier, liked: Boolean, onToggle: () -> Unit, onLike: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit, sharedTransitionScope: SharedTransitionScope, playerVisible: Boolean, sharedArtworkTransitionActive: Boolean) {
    val height by animateDpAsState(if (expanded) 144.dp else 70.dp, tween(520, easing = WavvMotionEasing), label = "player bar height")
    val cornerRadius by animateDpAsState(if (expanded) 30.dp else 999.dp, tween(320, easing = WavvMotionEasing), label = "player bar corners")
    val shape = RoundedCornerShape(cornerRadius)
    val durationMs = max(state.durationMs, song.durationMs).coerceAtLeast(1L)
    val positionMs = state.positionMs.coerceIn(0L, durationMs)
    Box(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 96.dp)
            .height(height)
            .shadow(18.dp, shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(
                            refractionHeight = 12.dp.toPx(),
                            refractionAmount = 18.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    }
                },
                onDrawSurface = {
                    drawRect(Color(0x881A1920))
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color(0x33F8B65E), WavvPink.copy(.13f), Color(0x24345DA8)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                },
            )
            .clip(shape)
            .border(
                1.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(.34f),
                    .42f to WavvPink.copy(.22f),
                    1f to Color.White.copy(.08f),
                ),
                shape,
            )
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.White.copy(.10f), .42f to Color.Transparent, 1f to Color.Black.copy(.14f)),
            ),
        )
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth(.62f).height(1.dp).background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    .24f to WavvPink.copy(.25f),
                    .5f to Color.White.copy(.62f),
                    .76f to Color(0xFFFFC76C).copy(.3f),
                    1f to Color.Transparent,
                ),
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
                        IconButton(
                            onClick = onLike,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = if (liked) "Remove from liked songs" else "Add to liked songs"
                                stateDescription = if (liked) "Liked" else "Not liked"
                            },
                        ) { FigmaFavouriteIcon(liked, 30.dp) }
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
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(48.dp).sharedPlayerArtwork(sharedTransitionScope, sharedArtworkTransitionActive && !playerVisible && big == expanded)) {
                        AlbumArt(song, Modifier.fillMaxSize(), song.title, 10.dp)
                    }
                    TrackText(song, Modifier.weight(1f), 14.sp)
                    IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(20.dp), tint = Color.White.copy(.78f))
                    }
                    IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                        Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else "Play", Modifier.size(22.dp), tint = Color.White)
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.SkipNext, "Next", Modifier.size(20.dp).alpha(.78f), tint = Color.White)
                    }
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
internal fun FigmaFavouriteIcon(filled: Boolean, iconSize: Dp) {
    val fillProgress by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = spring(dampingRatio = .55f, stiffness = Spring.StiffnessMedium),
        label = "favorite icon transition",
    )
    Canvas(Modifier.size(iconSize)) {
        val unit = size.minDimension / 24f
        val center = Offset(size.width / 2f, size.height / 2f)
        val fillAlpha = fillProgress.coerceIn(0f, 1f)
        val shellRadius = 11.5f * unit
        drawCircle(Color.White.copy(.11f * (1f - fillAlpha)), radius = shellRadius)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(.32f * fillAlpha),
                    WavvPink.copy(.28f * fillAlpha),
                    Color(0xFF7951C8).copy(.16f * fillAlpha),
                ),
                center = Offset(size.width * .34f, size.height * .26f),
                radius = shellRadius * 1.8f,
            ),
            radius = shellRadius,
        )
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(.38f), Color.White.copy(.13f), Color.White.copy(.06f)),
            ),
            radius = shellRadius,
            style = Stroke(width = .7f * unit),
        )
        val star = Path().apply {
            moveTo(12f * unit, 4.2f * unit)
            cubicTo(12.55f * unit, 8.65f * unit, 15.35f * unit, 11.45f * unit, 19.8f * unit, 12f * unit)
            cubicTo(15.35f * unit, 12.55f * unit, 12.55f * unit, 15.35f * unit, 12f * unit, 19.8f * unit)
            cubicTo(11.45f * unit, 15.35f * unit, 8.65f * unit, 12.55f * unit, 4.2f * unit, 12f * unit)
            cubicTo(8.65f * unit, 11.45f * unit, 11.45f * unit, 8.65f * unit, 12f * unit, 4.2f * unit)
            close()
        }
        val starScale = .84f + fillProgress * .08f
        withTransform({
            rotate(degrees = -24f * fillProgress, pivot = center)
            scale(scaleX = starScale, scaleY = starScale, pivot = center)
        }) {
            drawPath(
                star,
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF0BD), Color(0xFFFFC76C), WavvPink, Color(0xFF9B72FF)),
                    start = Offset(size.width * .24f, size.height * .18f),
                    end = Offset(size.width * .82f, size.height * .84f),
                ),
                alpha = fillAlpha,
            )
            drawPath(
                star,
                Brush.linearGradient(listOf(Color.White, WavvPink, Color(0xFFFFC76C))),
                alpha = 1f - fillAlpha,
                style = Stroke(width = .95f * unit, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
        }
        val reflection = Path().apply {
            moveTo(8.1f * unit, 9.3f * unit)
            quadraticTo(9.1f * unit, 7.45f * unit, 12f * unit, 7.1f * unit)
            quadraticTo(13.6f * unit, 7.2f * unit, 14.8f * unit, 8.05f * unit)
        }
        drawPath(
            reflection,
            Color.White.copy(alpha = fillAlpha * .82f),
            style = Stroke(width = .7f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        val sparkleCenter = Offset(19.1f * unit, 5.5f * unit)
        drawLine(Color.White.copy(alpha = fillAlpha * .9f), sparkleCenter.copy(y = sparkleCenter.y - 1.45f * unit), sparkleCenter.copy(y = sparkleCenter.y + 1.45f * unit), .7f * unit)
        drawLine(Color.White.copy(alpha = fillAlpha * .9f), sparkleCenter.copy(x = sparkleCenter.x - 1.45f * unit), sparkleCenter.copy(x = sparkleCenter.x + 1.45f * unit), .7f * unit)
    }
}

@Composable
internal fun FigmaMoreIcon(iconSize: Dp) {
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
    val context = LocalContext.current
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
    val nextTitle = nextQueuedSongs(state.queue, song.id, limit = 1).firstOrNull()?.title ?: "Next Up"
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
                        IconButton(
                            onClick = onLike,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = if (liked) "Remove from liked songs" else "Add to liked songs"
                                stateDescription = if (liked) "Liked" else "Not liked"
                            },
                        ) { FigmaFavouriteIcon(liked, 30.dp) }
                        IconButton(
                            onClick = { showTrackOptions = true },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "More track options" },
                        ) { FigmaMoreIcon(30.dp) }
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
                SheetActionRow(Icons.Default.SkipNext, "Play next") {
                    close()
                    if (song.id == state.song?.id) {
                        Toast.makeText(context, "This track is already playing", Toast.LENGTH_SHORT).show()
                    } else {
                        onPlayNext(song)
                        Toast.makeText(context, "Will play next: ${song.title}", Toast.LENGTH_SHORT).show()
                    }
                }
                SheetActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Add to queue") {
                    close()
                    if (state.queue.any { it.id == song.id }) {
                        Toast.makeText(context, "This track is already in the queue", Toast.LENGTH_SHORT).show()
                    } else {
                        onAddToQueue(song)
                        Toast.makeText(context, "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
                    }
                }
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
internal fun AlbumArt(song: Song, modifier: Modifier, contentDescription: String? = song.title, radius: Dp) {
    AlbumArt(song.albumArtUri, modifier, contentDescription, radius, audioUri = song.uri)
}

@Composable
internal fun AlbumArt(
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
