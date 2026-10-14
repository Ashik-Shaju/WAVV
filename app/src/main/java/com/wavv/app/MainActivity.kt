package com.wavv.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDp
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
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.mediarouter.media.MediaRouter
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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
private class AlbumArtDecodeLock(val mutex: Mutex = Mutex(), var references: Int = 0)
private val albumArtDecodeLocks = mutableMapOf<String, AlbumArtDecodeLock>()
private const val MIN_ALBUM_ART_DECODE_EDGE = 128
private const val MAX_ALBUM_ART_DECODE_EDGE = 1024

internal fun albumArtDecodeBucketEdge(size: IntSize): Int {
    val edge = maxOf(size.width, size.height).coerceIn(MIN_ALBUM_ART_DECODE_EDGE, MAX_ALBUM_ART_DECODE_EDGE)
    return Integer.highestOneBit(edge - 1) * 2
}
@OptIn(ExperimentalTextApi::class)
internal val WavvFontFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_variable, FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito_variable, FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito_variable, FontWeight.W700, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito_variable, FontWeight.W800, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.nunito_variable, FontWeight.W900, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)
internal val WavvBrandFontFamily = FontFamily(Font(R.font.galaxie_copernicus_book, FontWeight.W400))
internal val WavvSurfaceGradient = Brush.verticalGradient(
    listOf(Color(0xFF282832), Color(0xFF17171D)),
)
internal val WavvAccentGradient = Brush.linearGradient(
    listOf(Color(0xFFB71C1C), Color(0xFFE91E63), Color(0xFFFF4081)),
)
internal enum class Tab { Home, Search, Library, You }

private enum class NowPlayingBarMode { Expanded, Stacked, Docked }

private enum class PlayerArtworkFlightDirection { Opening, Closing }

private enum class ArtworkFlightDestination { MiniPlayer, FullPlayer, Lyrics }

private data class PlayerArtworkFrame(
    val bounds: Rect,
    val cornerRadiusPx: Float,
    val alpha: Float = 1f,
    val regularArtworkAlpha: Float = 1f,
    val maskedArtworkAlpha: Float = 0f,
    val shadowElevationPx: Float = 0f,
)

private data class PlayerArtworkFlight(
    val id: Long,
    val song: Song,
    val direction: PlayerArtworkFlightDirection,
    val from: PlayerArtworkFrame,
    val to: PlayerArtworkFrame,
    val progressOrigin: Float = 0f,
)

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun normalizedArtworkFlightProgress(flight: PlayerArtworkFlight, rawProgress: Float): Float {
    val remaining = 1f - flight.progressOrigin
    return if (remaining <= .0001f) 1f else ((rawProgress - flight.progressOrigin) / remaining).coerceIn(0f, 1f)
}

private fun interpolateArtworkFrame(
    start: PlayerArtworkFrame,
    end: PlayerArtworkFrame,
    progress: Float,
): PlayerArtworkFrame = PlayerArtworkFrame(
    bounds = Rect(
        lerpFloat(start.bounds.left, end.bounds.left, progress),
        lerpFloat(start.bounds.top, end.bounds.top, progress),
        lerpFloat(start.bounds.right, end.bounds.right, progress),
        lerpFloat(start.bounds.bottom, end.bounds.bottom, progress),
    ),
    cornerRadiusPx = lerpFloat(start.cornerRadiusPx, end.cornerRadiusPx, progress),
    alpha = lerpFloat(start.alpha, end.alpha, progress),
    regularArtworkAlpha = lerpFloat(start.regularArtworkAlpha, end.regularArtworkAlpha, progress),
    maskedArtworkAlpha = lerpFloat(start.maskedArtworkAlpha, end.maskedArtworkAlpha, progress),
    shadowElevationPx = lerpFloat(start.shadowElevationPx, end.shadowElevationPx, progress),
)

@Composable
private fun PlayerArtworkFlightCanvas(
    flight: PlayerArtworkFlight,
    progress: Float,
) {
    val context = LocalContext.current
    val sourceKey = "${flight.song.albumArtUri.orEmpty()}|${flight.song.uri.orEmpty()}"
    val screenWidthPx = context.resources.displayMetrics.widthPixels
    val targetEdge = albumArtDecodeBucketEdge(IntSize(screenWidthPx, screenWidthPx))
    val cacheKey = "$sourceKey|$targetEdge"
    var bitmap by remember(cacheKey) { mutableStateOf(albumArtCache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                decodeAlbumArt(context, flight.song.albumArtUri, flight.song.uri, cacheKey, targetEdge)
            }
        }
    }
    val image = bitmap?.asImageBitmap()
    val frame = interpolateArtworkFrame(flight.from, flight.to, progress)
    Canvas(Modifier.fillMaxSize()) {
        val left = frame.bounds.left
        val top = frame.bounds.top
        val width = frame.bounds.width.coerceAtLeast(1f)
        val height = frame.bounds.height.coerceAtLeast(1f)
        val radius = frame.cornerRadiusPx.coerceIn(0f, minOf(width, height) / 2f)
        val clip = Path().apply {
            addRoundRect(RoundRect(frame.bounds, androidx.compose.ui.geometry.CornerRadius(radius)))
        }

        // Draw directly into the moving destination rectangle. The previous implementation
        // scaled a mini-player-sized graphics layer, which softened the cover during the flight.
        drawContext.canvas.save()
        drawContext.canvas.clipPath(clip)
        image?.let { artwork ->
            val sourceWidth = artwork.width
            val sourceHeight = artwork.height
            val targetAspect = width / height
            val sourceAspect = sourceWidth.toFloat() / sourceHeight
            val sourceX: Int
            val sourceY: Int
            val sourceCropWidth: Int
            val sourceCropHeight: Int
            if (sourceAspect > targetAspect) {
                val cropWidth = (sourceHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
                sourceX = (sourceWidth - cropWidth) / 2
                sourceY = 0
                sourceCropWidth = cropWidth
                sourceCropHeight = sourceHeight
            } else {
                val cropHeight = (sourceWidth / targetAspect).roundToInt().coerceIn(1, sourceHeight)
                sourceX = 0
                sourceY = (sourceHeight - cropHeight) / 2
                sourceCropWidth = sourceWidth
                sourceCropHeight = cropHeight
            }
            val sourceOffset = IntOffset(sourceX, sourceY)
            val sourceSize = IntSize(sourceCropWidth, sourceCropHeight)
            val destinationOffset = IntOffset(left.roundToInt(), top.roundToInt())
            val destinationSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))

            if (frame.regularArtworkAlpha > 0f) {
                drawImage(
                    image = artwork,
                    srcOffset = sourceOffset,
                    srcSize = sourceSize,
                    dstOffset = destinationOffset,
                    dstSize = destinationSize,
                    alpha = frame.regularArtworkAlpha * frame.alpha,
                    filterQuality = FilterQuality.High,
                )
            }
            if (frame.maskedArtworkAlpha > 0f) {
                drawContext.canvas.saveLayer(frame.bounds, Paint())
                drawImage(
                    image = artwork,
                    srcOffset = sourceOffset,
                    srcSize = sourceSize,
                    dstOffset = destinationOffset,
                    dstSize = destinationSize,
                    alpha = frame.maskedArtworkAlpha * frame.alpha,
                    filterQuality = FilterQuality.High,
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        .35f to Color.Black,
                        .60f to Color.Black,
                        1f to Color.Transparent,
                        startY = top,
                        endY = top + height,
                    ),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    blendMode = BlendMode.DstIn,
                )
                drawContext.canvas.restore()
            }
        } ?: drawRoundRect(
            color = WavvSurface.copy(alpha = frame.alpha),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        drawContext.canvas.restore()
    }
}

internal enum class LibraryPage { Main, Liked, Playlists, Artists, Albums, Songs, Playlist, Artist, Album }

internal data class LibraryDestination(
    val page: LibraryPage = LibraryPage.Main,
    val itemId: Long = 0L,
    val title: String = "",
    val subtitle: String = "",
) : java.io.Serializable {
    val isDetail get() = page == LibraryPage.Playlist || page == LibraryPage.Artist || page == LibraryPage.Album
}

internal data class LibraryNavigationState(
    val destination: LibraryDestination = LibraryDestination(),
    val backStack: List<LibraryDestination> = emptyList(),
) : java.io.Serializable {
    fun navigateTo(destination: LibraryDestination): LibraryNavigationState {
        if (destination == this.destination) return this
        return if (destination.isDetail) {
            copy(destination = destination, backStack = backStack + this.destination)
        } else {
            copy(destination = destination, backStack = emptyList())
        }
    }

    fun back(): LibraryNavigationState =
        copy(
            destination = backStack.lastOrNull() ?: LibraryDestination(),
            backStack = backStack.dropLast(1),
        )
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

    override fun onResume() {
        super.onResume()
        FrostedBackdropFrames.refreshAfterHostResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) FrostedBackdropFrames.refreshAfterHostResume()
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
    val currentRecommendations = (libraryState as? LibraryUiState.Ready)?.personalizedRecommendations
    val showUnavailable: (String) -> Unit = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.actionFeedback.collect { message -> showUnavailable(message) }
    }

    LaunchedEffect(currentSongs) {
        playbackController.updateCatalog(currentSongs)
    }

    LaunchedEffect(currentRecommendations, playbackState.queue.map(Song::id)) {
        currentRecommendations?.let(playbackController::updateAutoplayRecommendations)
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
                state = WavvContentState(
                    libraryState,
                    playbackState,
                    playbackController.progress,
                    source,
                    playbackController.audioOutputState,
                ),
                actions = WavvContentActions(
                    onSearch = libraryViewModel::search,
                    onChooseAllDevice = chooseAllDevice,
                    onChooseFiles = { chooseFiles.launch(arrayOf("audio/*")) },
                    onChooseFolder = { chooseFolder.launch(null) },
                    onAddMusicFolder = { addMusicFolder.launch(null) },
                    onRemoveMusicFolder = libraryViewModel::removeFolder,
                    onRetry = libraryViewModel::loadSongs,
                    onChooseSource = libraryViewModel::clearSource,
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
                    onPlayNext = { song ->
                        val result = playbackController.playNext(song)
                        if (result == QueueActionResult.Added) libraryViewModel.recordQueueAction(song.id, "play_next")
                        reportQueueAction(result, showUnavailable)
                    },
                    onAddToQueue = { song ->
                        val result = playbackController.addToQueue(song)
                        if (result == QueueActionResult.Added) libraryViewModel.recordQueueAction(song.id, "queue")
                        reportQueueAction(result, showUnavailable)
                    },
                    onRemoveQueueItem = playbackController::removeFromQueue,
                    onMoveQueueItem = playbackController::moveQueueItem,
                    onPlayQueuedSong = playbackController::playQueueSong,
                    onPlayAutoplaySong = playbackController::playAutoplaySong,
                    onPlayNextFromAutoplay = {
                        val result = playbackController.playNext(it, fromAutoplay = true)
                        if (result == QueueActionResult.Added) libraryViewModel.recordQueueAction(it.id, "autoplay_play_next")
                        reportQueueAction(result, showUnavailable)
                    },
                    onAddAutoplayToQueue = {
                        val result = playbackController.addToQueue(it, fromAutoplay = true)
                        if (result == QueueActionResult.Added) libraryViewModel.recordQueueAction(it.id, "autoplay_queue")
                        reportQueueAction(result, showUnavailable)
                    },
                    onRemoveAutoplaySong = playbackController::removeAutoplaySong,
                    onMoveAutoplayItem = playbackController::moveAutoplayItem,
                    onSetAutoplayEnabled = playbackController::setAutoplayEnabled,
                    onSetTrackUHQ = playbackController::setCurrentTrackUHQ,
                    onSelectAudioRoute = playbackController::selectAudioRoute,
                    onSetAudioRouteVolume = playbackController::setAudioRouteVolume,
                    onRefreshAudioRoutes = playbackController::refreshAudioRoutes,
                    onCreatePlaylist = { name, songId, onComplete ->
                        libraryViewModel.createPlaylist(name, songId, onComplete)
                    },
                    onRenamePlaylist = { playlistId, name, onComplete ->
                        libraryViewModel.renamePlaylist(playlistId, name, onComplete)
                    },
                    onDeletePlaylist = { playlistId, onComplete ->
                        libraryViewModel.deletePlaylist(playlistId, onComplete)
                    },
                    onAddSongToPlaylist = { playlistId, songIds, onComplete ->
                        libraryViewModel.addSongsToPlaylist(playlistId, songIds, onComplete)
                    },
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

private const val PLAYLIST_PIN_PREFERENCES = "wavv.playlists"
private const val PLAYLIST_PIN_IDS = "pinned_ids"

private fun reportQueueAction(result: QueueActionResult, showMessage: (String) -> Unit) {
    queueActionMessage(result)?.let(showMessage)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity.setStandbySystemBarsHidden(hidden: Boolean) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
    else controller.show(WindowInsetsCompat.Type.systemBars())
}

internal data class WavvContentState(
    val libraryState: LibraryUiState,
    val playbackState: PlaybackState,
    val progressFlow: StateFlow<PlaybackProgress>,
    val source: LibrarySource?,
    val audioOutputState: StateFlow<AudioOutputRoutingState>,
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
    val onPlayWithRecommendations: (Song, List<Song>, List<Song>, String, String?, String?) -> Unit,
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
    val onPlayQueuedSong: (Song) -> Unit,
    val onPlayAutoplaySong: (Song) -> Unit,
    val onPlayNextFromAutoplay: (Song) -> Unit,
    val onAddAutoplayToQueue: (Song) -> Unit,
    val onRemoveAutoplaySong: (Song) -> Unit,
    val onMoveAutoplayItem: (Int, Int) -> Unit,
    val onSetAutoplayEnabled: (Boolean) -> Unit,
    val onSetTrackUHQ: (Long, Boolean) -> Unit,
    val onSelectAudioRoute: (String) -> Unit,
    val onSetAudioRouteVolume: (String, Int) -> Unit,
    val onRefreshAudioRoutes: () -> Unit,
    val onCreatePlaylist: (String, Long?, (Boolean) -> Unit) -> Unit,
    val onRenamePlaylist: (Long, String, (Boolean) -> Unit) -> Unit,
    val onDeletePlaylist: (Long, (Boolean) -> Unit) -> Unit,
    val onAddSongToPlaylist: (Long, List<Long>, (Boolean) -> Unit) -> Unit,
    val onRemoveSongFromPlaylist: (Long, Long) -> Unit,
    val onClearListeningHistory: () -> Unit,
)

@Composable
internal fun WavvContent(state: WavvContentState, actions: WavvContentActions) {
    val libraryState = state.libraryState
    val playbackState = state.playbackState
    val playbackProgress by state.progressFlow.collectAsStateWithLifecycle()
    val audioOutputState by state.audioOutputState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val statusPlaybackState = playbackState.copy(
        positionMs = playbackProgress.positionMs,
        durationMs = playbackProgress.durationMs.takeIf { it > 0L } ?: playbackState.durationMs,
    )
    val source = state.source
    val favoriteIds = (libraryState as? LibraryUiState.Ready)?.favoriteIds.orEmpty()
    val searchResults = (libraryState as? LibraryUiState.Ready)?.searchResults
    val semanticLoading = (libraryState as? LibraryUiState.Ready)?.semanticLoading == true
    val semanticSearchComplete = (libraryState as? LibraryUiState.Ready)?.semanticSearchComplete == true
    val searchError = (libraryState as? LibraryUiState.Ready)?.searchError
    val searchNotice = (libraryState as? LibraryUiState.Ready)?.searchNotice
    val analysisProgress = (libraryState as? LibraryUiState.Ready)?.analysisProgress
    val onSearch = actions.onSearch
    val onChooseAllDevice = actions.onChooseAllDevice
    val onChooseFiles = actions.onChooseFiles
    val onChooseFolder = actions.onChooseFolder
    val onAddMusicFolder = actions.onAddMusicFolder
    val onRemoveMusicFolder = actions.onRemoveMusicFolder
    val onRetry = actions.onRetry
    val onChooseSource = actions.onChooseSource
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
    val onPlayQueuedSong = actions.onPlayQueuedSong
    val onPlayAutoplaySong = actions.onPlayAutoplaySong
    val onPlayNextFromAutoplay = actions.onPlayNextFromAutoplay
    val onAddAutoplayToQueue = actions.onAddAutoplayToQueue
    val onRemoveAutoplaySong = actions.onRemoveAutoplaySong
    val onMoveAutoplayItem = actions.onMoveAutoplayItem
    val onSetAutoplayEnabled = actions.onSetAutoplayEnabled
    val onSelectAudioRoute = actions.onSelectAudioRoute
    val onSetAudioRouteVolume = actions.onSetAudioRouteVolume
    val onRefreshAudioRoutes = actions.onRefreshAudioRoutes
    DisposableEffect(lifecycleOwner, onRefreshAudioRoutes) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) onRefreshAudioRoutes()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val onCreatePlaylist = actions.onCreatePlaylist
    val onRenamePlaylist = actions.onRenamePlaylist
    val onDeletePlaylist = actions.onDeletePlaylist
    val onAddSongToPlaylist = actions.onAddSongToPlaylist
    val onRemoveSongFromPlaylist = actions.onRemoveSongFromPlaylist
    val onClearListeningHistory = actions.onClearListeningHistory
    val context = LocalContext.current
    val bluetoothOutput = rememberBluetoothOutputStatus(context)
    val connectedSpeakerName = bluetoothOutput.name
    var bluetoothPermissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }
    val bluetoothCodec = rememberBluetoothCodec(context, bluetoothPermissionGranted)
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        bluetoothPermissionGranted = granted
    }
    var bluetoothPermissionRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(connectedSpeakerName, bluetoothPermissionRequested) {
        if (connectedSpeakerName != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !bluetoothPermissionRequested && !bluetoothPermissionGranted
        ) {
            bluetoothPermissionRequested = true
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    LaunchedEffect(bluetoothOutput.notice) {
        if (bluetoothOutput.notice != null) {
            delay(2_200)
            bluetoothOutput.clearNotice()
        }
    }
    val nextSongChipEligible = statusPlaybackState.isPlaying &&
        (statusPlaybackState.durationMs - statusPlaybackState.positionMs) in 1..20_000
    val homeStatusChips = remember(
        statusPlaybackState.song?.id,
        statusPlaybackState.song?.codec,
        statusPlaybackState.song?.container,
        statusPlaybackState.isPlaying,
        statusPlaybackState.isUHQEnhanced,
        statusPlaybackState.positionMs,
        statusPlaybackState.durationMs,
        nextSongChipEligible,
        statusPlaybackState.queue,
        statusPlaybackState.currentQueueIndex,
        statusPlaybackState.autoplaySongs,
        statusPlaybackState.autoplayEnabled,
        statusPlaybackState.playingFrom,
        connectedSpeakerName,
        bluetoothCodec,
        bluetoothOutput.notice,
    ) {
        homeStatusChipLabels(statusPlaybackState, connectedSpeakerName, bluetoothCodec, bluetoothOutput.notice)
    }
    val hostActivity = remember(context) { context.findActivity() }
    val playlistPinPreferences = remember(context) {
        context.getSharedPreferences(PLAYLIST_PIN_PREFERENCES, Context.MODE_PRIVATE)
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showStandby by rememberSaveable { mutableStateOf(false) }
    var standbyKeepScreenOn by rememberSaveable { mutableStateOf(false) }
    var standbyPlayerWasExpanded by rememberSaveable { mutableStateOf(true) }
    var standbyPriorOrientation by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var standbyOrientationActive by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var loopMode by rememberSaveable { mutableStateOf(LoopMode.Off) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var playerPositionInitialized by rememberSaveable { mutableStateOf(false) }
    var homeStackListExpanded by rememberSaveable { mutableStateOf(false) }
    var homeStackIndex by rememberSaveable { mutableIntStateOf(0) }
    var libraryNavigation by rememberSaveable { mutableStateOf(LibraryNavigationState()) }
    val libraryDestination = libraryNavigation.destination
    var libraryHistoryVisible by rememberSaveable { mutableStateOf(false) }
    var libraryLayout by rememberSaveable { mutableStateOf(LibraryLayout.Grid) }
    var pinnedPlaylistIds by remember(playlistPinPreferences) {
        mutableStateOf(decodePlaylistPinIds(playlistPinPreferences.getStringSet(PLAYLIST_PIN_IDS, emptySet()).orEmpty()))
    }
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
    val playbackHistory = (libraryState as? LibraryUiState.Ready)?.playbackHistory.orEmpty()
    val playlists = (libraryState as? LibraryUiState.Ready)?.playlists.orEmpty()
    val activeSong = playbackState.song
    var songChangeDirection by remember { mutableIntStateOf(1) }
    var playbackAccentTarget by remember {
        mutableStateOf(homeFallbackAccent(activeSong?.id ?: 0L))
    }
    val playbackAccentColor by animateColorAsState(
        targetValue = playbackAccentTarget,
        animationSpec = tween(650, easing = WavvMotionEasing),
        label = "now playing artwork accent",
    )
    LaunchedEffect(activeSong?.id, activeSong?.albumArtUri, activeSong?.uri) {
        activeSong?.let { song ->
            playbackAccentTarget = withContext(Dispatchers.IO) { homeAcrylicChipColor(context, song) }
        }
    }
    var miniPlayerArtworkFrame by remember { mutableStateOf<PlayerArtworkFrame?>(null) }
    var fullPlayerArtworkFrame by remember { mutableStateOf<PlayerArtworkFrame?>(null) }
    var artworkFlight by remember { mutableStateOf<PlayerArtworkFlight?>(null) }
    var artworkFlightTracksFinger by remember { mutableStateOf(false) }
    var artworkFlightAwaitingTarget by remember { mutableStateOf(false) }
    var artworkFlightDestination by remember { mutableStateOf(ArtworkFlightDestination.FullPlayer) }
    var artworkFlightOwnedByLyrics by remember { mutableStateOf(false) }
    var artworkFlightSequence by remember { mutableStateOf(0L) }
    val gestureArtworkProgress = remember { mutableFloatStateOf(0f) }
    val artworkFlightProgress = remember { Animatable(0f) }
    val artworkFlightScope = rememberCoroutineScope()
    val minimumArtworkTravelPx = with(LocalDensity.current) { 280.dp.toPx() }
    val artworkRetargetThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val homePlayerExpanded = activeSong != null && playerExpanded
    val enterStandby: () -> Unit = {
        if (!showStandby && StandbyPreference.isEnabled(context) && tab == Tab.Home && homePlayerExpanded) {
            standbyPriorOrientation = hostActivity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            standbyPlayerWasExpanded = playerExpanded
            homeStackListExpanded = false
            homeStackIndex = 0
            showQueue = false
            playerExpanded = false
            standbyKeepScreenOn = true
            showStandby = true
            standbyOrientationActive = true
        }
    }
    val exitStandby: () -> Unit = {
        if (showStandby) {
            showStandby = false
            standbyKeepScreenOn = false
            tab = Tab.Home
            playerExpanded = standbyPlayerWasExpanded && activeSong != null
            playerPositionInitialized = activeSong != null
        }
    }
    SideEffect {
        hostActivity?.window?.let { window ->
            if (showStandby && standbyKeepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    DisposableEffect(hostActivity) {
        onDispose { hostActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(showStandby, standbyOrientationActive, hostActivity) {
        if (showStandby) {
            hostActivity?.setStandbySystemBarsHidden(true)
            // Let Standby's opaque layer cover Home before the activity is allowed to rotate.
            delay(460)
            if (showStandby) hostActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else if (standbyOrientationActive) {
            delay(420)
            if (!showStandby) {
                hostActivity?.requestedOrientation = standbyPriorOrientation
                hostActivity?.setStandbySystemBarsHidden(false)
                standbyOrientationActive = false
            }
        }
    }
    val homeStackExpansionProgress = animateFloatAsState(
        // Keep the mini-player docked at its current stack/list position behind the full player.
        // Resetting this while opening moves the thumbnail target toward the screen middle.
        targetValue = if (tab == Tab.Home && homeStackListExpanded && songs.isNotEmpty()) 1f else 0f,
        animationSpec = tween(560, easing = WavvMotionEasing),
        label = "home stack expansion",
    )
    val searchImeVisible = tab == Tab.Search && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(activeSong?.id) {
        if (!playerPositionInitialized && activeSong != null) {
            if (playbackState.isPlaying) playerExpanded = true
            playerPositionInitialized = true
        }
    }
    fun newArtworkFlight(
        song: Song,
        direction: PlayerArtworkFlightDirection,
        from: PlayerArtworkFrame,
        to: PlayerArtworkFrame,
    ): PlayerArtworkFlight {
        artworkFlightSequence += 1L
        return PlayerArtworkFlight(artworkFlightSequence, song, direction, from, to)
    }
    val openPlayer: () -> Unit = {
        val song = activeSong
        val source = miniPlayerArtworkFrame
        artworkFlightDestination = ArtworkFlightDestination.FullPlayer
        artworkFlightOwnedByLyrics = false
        if (song != null && source != null && source.bounds.width > 0f && source.bounds.height > 0f) {
            // Freeze the artwork at the live mini-player bounds until the full-player target is measured.
            val openingFlight = newArtworkFlight(song, PlayerArtworkFlightDirection.Opening, source, source)
            artworkFlight = openingFlight
            artworkFlightTracksFinger = false
            artworkFlightAwaitingTarget = true
            gestureArtworkProgress.floatValue = 0f
        } else {
            artworkFlight = null
            artworkFlightAwaitingTarget = false
        }
        showPlayer = true
    }
    fun beginArtworkDrag(source: PlayerArtworkFrame) {
        val song = activeSong ?: return
        val destination = miniPlayerArtworkFrame ?: return
        artworkFlightDestination = ArtworkFlightDestination.MiniPlayer
        artworkFlightOwnedByLyrics = false
        val openingFlight = artworkFlight?.takeIf { it.direction == PlayerArtworkFlightDirection.Opening }
        val visibleSource = openingFlight?.let { flight ->
            val rawProgress = if (artworkFlightAwaitingTarget) 0f else artworkFlightProgress.value.coerceIn(0f, 1f)
            interpolateArtworkFrame(
                flight.from,
                flight.to,
                normalizedArtworkFlightProgress(flight, rawProgress),
            )
        } ?: source
        artworkFlight = newArtworkFlight(song, PlayerArtworkFlightDirection.Closing, visibleSource, destination)
        gestureArtworkProgress.floatValue = 0f
        artworkFlightTracksFinger = true
        artworkFlightAwaitingTarget = false
    }
    fun updateArtworkDrag(dragDistancePx: Float) {
        if (!artworkFlightTracksFinger) return
        val flight = artworkFlight ?: return
        val centerTravel = flight.to.bounds.center.y - flight.from.bounds.center.y
        val travelDistance = max(kotlin.math.abs(centerTravel), minimumArtworkTravelPx)
        gestureArtworkProgress.floatValue = (dragDistancePx / travelDistance).coerceIn(0f, 1f)
    }
    fun cancelArtworkDrag() {
        if (artworkFlightTracksFinger) {
            artworkFlightTracksFinger = false
            artworkFlightAwaitingTarget = false
            gestureArtworkProgress.floatValue = 0f
            artworkFlight = null
        }
    }
    fun animateArtworkFlight(flight: PlayerArtworkFlight) {
        artworkFlightTracksFinger = true
        artworkFlightAwaitingTarget = false
        gestureArtworkProgress.floatValue = 0f
        artworkFlightScope.launch {
            artworkFlightProgress.snapTo(0f)
            artworkFlightTracksFinger = false
            artworkFlightProgress.animateTo(1f, tween(480, easing = WavvMotionEasing))
            if (artworkFlight?.id == flight.id) {
                artworkFlight = null
                artworkFlightOwnedByLyrics = false
                gestureArtworkProgress.floatValue = 0f
            }
        }
    }
    fun retargetArtworkFlight(flight: PlayerArtworkFlight, frame: PlayerArtworkFrame): PlayerArtworkFlight {
        val rawProgress = artworkFlightProgress.value.coerceIn(0f, 1f)
        val currentProgress = normalizedArtworkFlightProgress(flight, rawProgress)
        val currentFrame = interpolateArtworkFrame(flight.from, flight.to, currentProgress)
        val targetDisplacement = maxOf(
            kotlin.math.abs(frame.bounds.left - flight.to.bounds.left),
            kotlin.math.abs(frame.bounds.top - flight.to.bounds.top),
            kotlin.math.abs(frame.bounds.right - flight.to.bounds.right),
            kotlin.math.abs(frame.bounds.bottom - flight.to.bounds.bottom),
        )
        return if (targetDisplacement > artworkRetargetThresholdPx && rawProgress < .98f) {
            // Rebase from the visible frame when a layout change moves the target substantially.
            flight.copy(from = currentFrame, to = frame, progressOrigin = rawProgress)
        } else {
            flight.copy(to = frame)
        }
    }
    fun openLyricsArtworkFlight(sourceFrame: PlayerArtworkFrame?) {
        val song = activeSong ?: return
        val source = sourceFrame ?: fullPlayerArtworkFrame
        artworkFlightDestination = ArtworkFlightDestination.Lyrics
        artworkFlightOwnedByLyrics = true
        if (source != null && source.bounds.width > 0f && source.bounds.height > 0f) {
            artworkFlight = newArtworkFlight(song, PlayerArtworkFlightDirection.Opening, source, source)
            artworkFlightTracksFinger = false
            artworkFlightAwaitingTarget = true
            gestureArtworkProgress.floatValue = 0f
        } else {
            artworkFlight = null
            artworkFlightAwaitingTarget = false
            artworkFlightOwnedByLyrics = false
        }
    }
    fun closeLyricsArtworkFlight(sourceFrame: PlayerArtworkFrame?) {
        val song = activeSong ?: return
        val source = sourceFrame
        val destination = fullPlayerArtworkFrame
        artworkFlightDestination = ArtworkFlightDestination.FullPlayer
        artworkFlightOwnedByLyrics = true
        if (source != null && destination != null &&
            source.bounds.width > 0f && source.bounds.height > 0f &&
            destination.bounds.width > 0f && destination.bounds.height > 0f
        ) {
            val flight = newArtworkFlight(song, PlayerArtworkFlightDirection.Closing, source, destination)
            artworkFlight = flight
            artworkFlightTracksFinger = false
            artworkFlightAwaitingTarget = false
            animateArtworkFlight(flight)
        } else {
            artworkFlight = null
            artworkFlightAwaitingTarget = false
            artworkFlightOwnedByLyrics = false
        }
    }
    fun closePlayer(sourceFrame: PlayerArtworkFrame? = null) {
        if (showPlayer && activeSong != null) {
            artworkFlightDestination = ArtworkFlightDestination.MiniPlayer
            artworkFlightOwnedByLyrics = false
            val destination = miniPlayerArtworkFrame
            val currentFlight = artworkFlight
            if (artworkFlightTracksFinger && currentFlight != null) {
                // Rebase at the visible finger-driven frame, then finish at the live mini-player target.
                val fingerProgress = normalizedArtworkFlightProgress(currentFlight, gestureArtworkProgress.floatValue)
                val currentFrame = interpolateArtworkFrame(currentFlight.from, currentFlight.to, fingerProgress)
                val closingFlight = currentFlight.copy(
                    direction = PlayerArtworkFlightDirection.Closing,
                    // The flight can outlive the song that opened Now Playing. Rebind it to
                    // the currently active track before animating toward the mini player.
                    song = activeSong,
                    from = currentFrame,
                    to = destination ?: currentFlight.to,
                    progressOrigin = 0f,
                )
                artworkFlight = closingFlight
                animateArtworkFlight(closingFlight)
            } else {
                val openingFlightFrame = currentFlight
                    ?.takeIf { it.direction == PlayerArtworkFlightDirection.Opening }
                    ?.let { flight ->
                        val rawProgress = if (artworkFlightAwaitingTarget) 0f else artworkFlightProgress.value.coerceIn(0f, 1f)
                        interpolateArtworkFrame(
                            flight.from,
                            flight.to,
                            normalizedArtworkFlightProgress(flight, rawProgress),
                        )
                    }
                val source = openingFlightFrame ?: sourceFrame ?: fullPlayerArtworkFrame
                if (source != null && destination != null &&
                    source.bounds.width > 0f && source.bounds.height > 0f &&
                    destination.bounds.width > 0f && destination.bounds.height > 0f
                ) {
                    // Measure the live mini-player target at dismissal; never reuse a prior close point.
                    val closingFlight = newArtworkFlight(activeSong, PlayerArtworkFlightDirection.Closing, source, destination)
                    artworkFlight = closingFlight
                    animateArtworkFlight(closingFlight)
                } else {
                    artworkFlight = null
                    artworkFlightTracksFinger = false
                    artworkFlightAwaitingTarget = false
                }
            }
        }
        showPlayer = false
        showQueue = false
    }
    val startPlayback: (Song, String) -> Unit = { song, selectionSource ->
        playerExpanded = true
        playerPositionInitialized = true
        onPlayWithRecommendations(
            song,
            listOf(song),
            personalizedRecommendations,
            selectionSource,
            null,
            null,
        )
    }
    val startMix: (SmartMix) -> Unit = { mix ->
        mix.songs.firstOrNull()?.let { first ->
            onPlayWithRecommendations(first, mix.songs, personalizedRecommendations, "smart_mix", mix.id, mix.title)
        }
    }
    val showLibrary = libraryState is LibraryUiState.Ready
    val navigateLibrary: (LibraryDestination) -> Unit = { destination ->
        libraryNavigation = libraryNavigation.navigateTo(destination)
    }
    val backLibrary = { libraryNavigation = libraryNavigation.back() }

    BackHandler(enabled = showStandby || showPlayer || (tab == Tab.Home && homeStackListExpanded) || (showLibrary && tab != Tab.Home)) {
        when {
            showStandby -> Unit
            showPlayer -> closePlayer()
            tab == Tab.Home && homeStackListExpanded -> {
                homeStackListExpanded = false
                homeStackIndex = 0
            }
            tab == Tab.Library && libraryHistoryVisible -> libraryHistoryVisible = false
            tab == Tab.Library && libraryDestination.page != LibraryPage.Main -> backLibrary()
            else -> tab = Tab.Home
        }
    }

    SharedTransitionLayout(
        Modifier.fillMaxSize().then(if (tab == Tab.Search) Modifier.imePadding() else Modifier),
    ) {
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
                        statusChips = homeStatusChips,
                        onChooseSource = onChooseSource,
                        onPlay = { song -> startPlayback(song, "home") },
                        onPlayFromContext = { song, contextQueue, sourceLabel ->
                            playerExpanded = true
                            playerPositionInitialized = true
                            onPlayWithRecommendations(song, contextQueue, personalizedRecommendations, "home_shelf", null, sourceLabel)
                        },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onToggleFavorite = { song -> onToggleFavorite(song.id) },
                        onRequestAddToPlaylist = { song ->
                            createPlaylistSongId = song.id
                            createPlaylistName = ""
                            showCreatePlaylist = true
                        },
                        onNavigateToAlbum = { song ->
                            navigateLibrary(LibraryDestination(LibraryPage.Album, title = song.album, subtitle = song.albumArtist ?: song.artist))
                            tab = Tab.Library
                        },
                        onNavigateToArtist = { song ->
                            navigateLibrary(LibraryDestination(LibraryPage.Artist, title = song.artist))
                            tab = Tab.Library
                        },
                        recentSongs = recentlyPlayed,
                        personalizedRecommendations = personalizedRecommendations,
                        playerExpanded = homePlayerExpanded,
                        stackIndex = homeStackIndex,
                        stackExpansionProgress = homeStackExpansionProgress,
                        onStackIndexChange = { homeStackIndex = it },
                        stackListExpanded = homeStackListExpanded,
                        onStackListExpandedChange = { expanded ->
                            homeStackListExpanded = expanded
                            if (!expanded) homeStackIndex = 0
                        },
                        hasNowPlaying = activeSong != null,
                        smartMixes = smartMixes,
                        onPlayMix = startMix,
                        onExpandPlayer = { if (homeStackIndex == 0) playerExpanded = true },
                    )
                    Tab.Search -> SearchScreen(
                        songs = songs,
                        searchResults = searchResults,
                        semanticLoading = semanticLoading,
                        semanticSearchComplete = semanticSearchComplete,
                        searchError = searchError,
                        searchNotice = searchNotice,
                        onQueryChange = onSearch,
                        onPlay = { song, sourceLabel -> startPlayback(song, sourceLabel) },
                        personalizedRecommendations = personalizedRecommendations,
                        hasNowPlaying = activeSong != null,
                    )
                    Tab.Library -> LibraryScreen(
                        destination = libraryDestination,
                        songs = songs,
                        personalizedRecommendations = personalizedRecommendations,
                        playbackHistory = playbackHistory,
                        historyVisible = libraryHistoryVisible,
                        onToggleHistory = { libraryHistoryVisible = !libraryHistoryVisible },
                        hasNowPlaying = activeSong != null,
                        favoriteIds = favoriteIds,
                        playlists = playlists,
                        layout = libraryLayout,
                        pinnedPlaylistIds = pinnedPlaylistIds,
                        onNavigate = navigateLibrary,
                        onBack = backLibrary,
                        onToggleLayout = { libraryLayout = if (libraryLayout == LibraryLayout.Grid) LibraryLayout.List else LibraryLayout.Grid },
                        onPlay = { song, queue, playingFrom ->
                            onPlayWithRecommendations(song, queue, personalizedRecommendations, "collection", null, playingFrom)
                        },
                        onShuffle = { queue, playingFrom ->
                            queue.shuffled().takeIf { it.isNotEmpty() }?.let {
                                onPlayWithRecommendations(it.first(), it, personalizedRecommendations, "collection_shuffle", null, playingFrom)
                            }
                        },
                        onSetFavorites = onSetFavorites,
                        onTogglePin = { id ->
                            pinnedPlaylistIds = if (id in pinnedPlaylistIds) pinnedPlaylistIds - id else pinnedPlaylistIds + id
                            playlistPinPreferences.edit()
                                .putStringSet(PLAYLIST_PIN_IDS, encodePlaylistPinIds(pinnedPlaylistIds))
                                .apply()
                        },
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
                        standbyEnabled = StandbyPreference.isEnabled(context),
                        activeTrackId = playbackState.song?.id,
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
                expanded = tab == Tab.Home && homePlayerExpanded && !homeStackListExpanded,
                stacked = tab == Tab.Home && (!playerExpanded || homeStackListExpanded),
                stackDockEnabled = tab == Tab.Home,
                stackDockProgress = homeStackExpansionProgress,
                modifier = Modifier.fillMaxSize(),
                bottomClearance = if (tab == Tab.Search) 80.dp else 96.dp,
                liked = activeSong.id in favoriteIds,
                accentColor = playbackAccentColor,
                songChangeDirection = songChangeDirection,
                progressFlow = state.progressFlow,
                onToggle = onTogglePlayback,
                onLike = { onToggleFavorite(activeSong.id) },
                onPrevious = { songChangeDirection = -1; onPrevious() },
                onNext = { songChangeDirection = 1; onNext() },
                onOpen = openPlayer,
                onCollapse = { playerExpanded = false },
                onExpand = {
                    if (homeStackListExpanded) {
                        homeStackListExpanded = false
                        homeStackIndex = 0
                    } else if (homeStackIndex == 0) {
                        playerExpanded = true
                    }
                },
                onEnterStandby = enterStandby,
                artworkFlightActive = artworkFlight != null,
                onArtworkFrameChange = { frame ->
                    miniPlayerArtworkFrame = frame
                    val flight = artworkFlight
                    if (flight?.direction == PlayerArtworkFlightDirection.Closing &&
                        artworkFlightDestination == ArtworkFlightDestination.MiniPlayer &&
                        !artworkFlightTracksFinger && !artworkFlightAwaitingTarget && flight.to != frame
                    ) {
                        artworkFlight = retargetArtworkFlight(flight, frame)
                    }
                },
            )
        }
        AnimatedVisibility(
            visible = showLibrary && !showStandby && !searchImeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(300, easing = WavvScreenEasing)),
            exit = fadeOut(tween(220, easing = WavvScreenEasing)),
        ) {
            BottomNav(
                active = tab,
                onChange = { tab = it },
                onReselect = { selected ->
                    when (selected) {
                        Tab.Home -> {
                            homeStackListExpanded = false
                            homeStackIndex = 0
                            playerExpanded = activeSong != null
                            playerPositionInitialized = activeSong != null
                            if (showPlayer) closePlayer() else showQueue = false
                        }
                        Tab.Library -> {
                            libraryHistoryVisible = false
                            libraryNavigation = LibraryNavigationState()
                        }
                        else -> Unit
                    }
                },
                modifier = Modifier,
            )
        }
        AnimatedVisibility(
            visible = showPlayer,
            enter = fadeIn(tween(480, easing = WavvMotionEasing)),
            exit = fadeOut(tween(160, easing = CubicBezierEasing(.4f, 0f, .2f, 1f))) +
                slideOutVertically(tween(160, easing = CubicBezierEasing(.4f, 0f, .2f, 1f))) { (it * .38f).roundToInt() },
        ) {
            activeSong?.let {
                FullPlayer(
                    song = it,
                    state = playbackState,
                    progressFlow = state.progressFlow,
                    audioOutputState = audioOutputState,
                    onSelectAudioRoute = onSelectAudioRoute,
                    onSetAudioRouteVolume = onSetAudioRouteVolume,
                    onRefreshAudioRoutes = onRefreshAudioRoutes,
                    liked = it.id in favoriteIds,
                    showQueue = showQueue,
                    onClose = { frame -> closePlayer(frame) },
                    onToggle = onTogglePlayback,
                    onLike = { onToggleFavorite(it.id) },
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    loopMode = loopMode,
                    onCycleLoop = { loopMode = loopMode.next(); onRepeatMode(loopMode.toPlayerRepeatMode()) },
                    onShowQueue = { showQueue = true },
                    onHideQueue = { showQueue = false },
                    onPlaySong = onPlayQueuedSong,
                    onAddToQueue = onAddToQueue,
                    queueMode = playbackState.queueMode,
                    onQueueMode = onQueueMode,
                    onRemoveQueueItem = onRemoveQueueItem,
                    onMoveQueueItem = onMoveQueueItem,
                    currentQueueIndex = playbackState.currentQueueIndex,
                    onPlayNext = onPlayNext,
                    onPlayAutoplaySong = onPlayAutoplaySong,
                    onPlayNextFromAutoplay = onPlayNextFromAutoplay,
                    onAddAutoplayToQueue = onAddAutoplayToQueue,
                    onRemoveAutoplaySong = onRemoveAutoplaySong,
                    onMoveAutoplayItem = onMoveAutoplayItem,
                    onSetAutoplayEnabled = onSetAutoplayEnabled,
                    onSetTrackUHQ = actions.onSetTrackUHQ,
                    playlists = playlists,
                    artworkFlightActive = artworkFlight != null,
                    lyricsArtworkFlightActive = artworkFlightOwnedByLyrics,
                    onOpenLyricsArtwork = { frame -> openLyricsArtworkFlight(frame) },
                    onCloseLyricsArtwork = { frame -> closeLyricsArtworkFlight(frame) },
                    onArtworkFrameChange = { frame ->
                        fullPlayerArtworkFrame = frame
                        val flight = artworkFlight
                        if (flight?.direction == PlayerArtworkFlightDirection.Opening &&
                            artworkFlightDestination == ArtworkFlightDestination.FullPlayer
                        ) {
                            if (artworkFlightAwaitingTarget) {
                                // Use the measured, settled full-player artwork bounds as the destination.
                                val openingFlight = flight.copy(to = frame)
                                artworkFlight = openingFlight
                                animateArtworkFlight(openingFlight)
                            } else if (!artworkFlightTracksFinger && flight.to != frame) {
                                artworkFlight = retargetArtworkFlight(flight, frame)
                            }
                        } else if (flight?.direction == PlayerArtworkFlightDirection.Closing &&
                            artworkFlightDestination == ArtworkFlightDestination.FullPlayer &&
                            !artworkFlightTracksFinger && !artworkFlightAwaitingTarget && flight.to != frame
                        ) {
                            artworkFlight = retargetArtworkFlight(flight, frame)
                        }
                    },
                    onLyricsArtworkFrameChange = { frame ->
                        val flight = artworkFlight
                        if (artworkFlightOwnedByLyrics &&
                            artworkFlightDestination == ArtworkFlightDestination.Lyrics &&
                            flight?.direction == PlayerArtworkFlightDirection.Opening
                        ) {
                            if (artworkFlightAwaitingTarget) {
                                val openingFlight = flight.copy(to = frame)
                                artworkFlight = openingFlight
                                animateArtworkFlight(openingFlight)
                            } else if (!artworkFlightTracksFinger && flight.to != frame) {
                                artworkFlight = flight.copy(to = frame)
                            }
                        }
                    },
                    onArtworkDragStart = { frame -> beginArtworkDrag(frame) },
                    onArtworkDragProgress = { distance -> updateArtworkDrag(distance) },
                    onArtworkDragCancel = { cancelArtworkDrag() },
                    onAddSongToPlaylist = onAddSongToPlaylist,
                    onCreatePlaylist = { songId -> createPlaylistSongId = songId; createPlaylistName = ""; showCreatePlaylist = true },
                    onNavigateToAlbum = { song -> navigateLibrary(LibraryDestination(LibraryPage.Album, title = song.album, subtitle = song.albumArtist ?: song.artist)); tab = Tab.Library; closePlayer() },
                    onNavigateToArtist = { song -> navigateLibrary(LibraryDestination(LibraryPage.Artist, title = song.artist)); tab = Tab.Library; closePlayer() },
                )
            }
        }
        artworkFlight?.let { flight ->
            val rawProgress = when {
                artworkFlightAwaitingTarget -> 0f
                artworkFlightTracksFinger -> gestureArtworkProgress.floatValue
                else -> artworkFlightProgress.value
            }.coerceIn(0f, 1f)
            PlayerArtworkFlightCanvas(
                flight = flight,
                progress = normalizedArtworkFlightProgress(flight, rawProgress),
            )
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
                            onCreatePlaylist(createPlaylistName, createPlaylistSongId) { success ->
                                if (success) showCreatePlaylist = false
                            }
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
                        onClick = {
                            onRenamePlaylist(playlist.id, renamePlaylistName) { success ->
                                if (success) renamePlaylist = null
                            }
                        },
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
                confirmButton = {
                    TextButton(onClick = {
                        onDeletePlaylist(playlist.id) { success -> if (success) deletePlaylist = null }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text("Cancel") } },
            )
        }
        AnimatedVisibility(
            visible = showStandby && activeSong != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(420, easing = WavvScreenEasing)),
        ) {
            activeSong?.let { song ->
                StandbyScreen(
                    song = song,
                    isPlaying = playbackState.isPlaying,
                    isActive = showStandby,
                    onTogglePlayback = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onExit = exitStandby,
                    onKeepScreenOnChange = { standbyKeepScreenOn = it },
                )
            }
        }
        }
    }
}

@Composable
internal fun ScreenHeader(
    title: String,
    waveform: Boolean = false,
    isPlaying: Boolean = false,
    showAmbient: Boolean = true,
    backgroundColor: Color? = null,
    statusChips: List<HomeStatusChip> = emptyList(),
    statusChipColor: Color = Color(0xFF26385D),
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val isBrandTitle = title.equals("Wavv", ignoreCase = true)
    val brandHome = isBrandTitle && !showAmbient
    Box(
        Modifier.fillMaxWidth().background(
            backgroundColor ?: if (waveform && !showAmbient) Color.Black else if (waveform) WavvBackground else Color(0xB20A0A0A),
        ),
    ) {
        if (waveform && showAmbient) {
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
        ) {
            Text(
                title,
                color = if (brandHome) Color(0xFFD6D6D6) else Color.White,
                fontSize = if (brandHome) 33.sp else 32.sp,
                fontWeight = if (isBrandTitle) FontWeight.Normal else if (brandHome) FontWeight.Black else FontWeight.ExtraBold,
                letterSpacing = if (brandHome) (-1.15).sp else (-1).sp,
                fontFamily = if (isBrandTitle) WavvBrandFontFamily else if (brandHome) FontFamily.SansSerif else FontFamily.Default,
            )
            if (waveform) {
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    HomeStatusChips(statusChips, statusChipColor, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(12.dp))
                Waveform(isPlaying)
            }
            if (!waveform) Spacer(Modifier.weight(1f))
            trailingContent()
        }
        if (!waveform) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color.White.copy(.06f)))
        }
    }
}

internal fun homeStatusChipLabels(
    playback: PlaybackState,
    connectedSpeakerName: String?,
    bluetoothCodec: String? = null,
    bluetoothConnectionNotice: String? = null,
): List<HomeStatusChip> {
    return buildList {
        bluetoothConnectionNotice?.takeIf(String::isNotBlank)?.let { add(HomeStatusChip(it)) }
        bluetoothCodec?.takeIf {
            it.isNotBlank() && !it.equals("AAC", ignoreCase = true) && !it.equals("SBC", ignoreCase = true)
        }?.let { add(HomeStatusChip(it)) }
        connectedSpeakerName?.trim()?.takeIf(String::isNotEmpty)?.let { add(HomeStatusChip(it)) }
        val song = playback.song
        if (song != null) {
            val fileName = Uri.decode(Uri.parse(song.uri).lastPathSegment.orEmpty())
            val metadata = "${song.codec.orEmpty()} ${song.container.orEmpty()} $fileName".lowercase(Locale.ROOT)
            when {
                playback.isUHQEnhanced -> add(HomeStatusChip("UHQ"))
                metadata.contains("flac") -> add(HomeStatusChip("FLAC"))
                metadata.contains("alac") || fileName.endsWith(".m4a", ignoreCase = true) -> add(HomeStatusChip("ALAC"))
            }
            val nextSong = playback.queue.getOrNull(playback.currentQueueIndex + 1)
                ?: playback.autoplaySongs.firstOrNull().takeIf { playback.autoplayEnabled }
            val remainingMs = playback.durationMs - playback.positionMs
            nextSong?.takeIf { it.id != song.id && playback.isPlaying && remainingMs in 1..20_000 }
                ?.let { add(HomeStatusChip("Next song: ${it.title}", truncateToFit = true)) }
            playback.playingFrom?.trim()?.takeIf(String::isNotEmpty)?.let { add(HomeStatusChip("Playing from · $it")) }
        }
    }
}

@Composable
private fun rememberUHQPlaybackEnabled(context: Context): Boolean {
    var enabled by remember(context) { mutableStateOf(UHQPlaybackPreference.isEnabled(context)) }
    DisposableEffect(context) {
        val preferences = UHQPlaybackPreference.preferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == UHQPlaybackPreference.preferenceKey()) {
                enabled = UHQPlaybackPreference.isEnabled(context)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        enabled = UHQPlaybackPreference.isEnabled(context)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

private data class BluetoothOutputStatus(
    val name: String? = null,
    val notice: String? = null,
    val clearNotice: () -> Unit = {},
)

@Composable
private fun rememberBluetoothOutputStatus(context: Context): BluetoothOutputStatus {
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var name by remember(audioManager) { mutableStateOf(findConnectedBluetoothOutputName(audioManager)) }
    var notice by remember(audioManager) { mutableStateOf<String?>(null) }
    DisposableEffect(audioManager) {
        fun refreshConnection() {
            val updatedName = findConnectedBluetoothOutputName(audioManager)
            if (updatedName != name) {
                notice = if (updatedName != null) "Device connected"
                else if (name != null) "Device disconnected" else null
                name = updatedName
            }
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refreshConnection()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refreshConnection()
            }
        }
        audioManager.registerAudioDeviceCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        refreshConnection()
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return BluetoothOutputStatus(name, notice) { notice = null }
}

private fun findConnectedBluetoothOutputName(audioManager: AudioManager): String? =
    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        ?.productName?.toString()?.trim()?.takeIf(String::isNotEmpty)

@Composable
private fun rememberBluetoothCodec(context: Context, hasBluetoothOutput: Boolean): String? {
    val tracker = remember(context) { BluetoothCodecTracker(context.applicationContext) }
    val codec by tracker.codec.collectAsStateWithLifecycle()
    DisposableEffect(tracker, hasBluetoothOutput) {
        if (hasBluetoothOutput) tracker.start()
        onDispose { tracker.stop() }
    }
    return codec
}

internal data class HomeStatusChip(
    val label: String,
    val truncateToFit: Boolean = false,
    val maxWidthPx: Int? = null,
)

@Composable
private fun HomeStatusChips(labels: List<HomeStatusChip>, color: Color, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val textStyle = LocalTextStyle.current.merge(
            TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        )
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val gapPx = with(density) { 7.dp.roundToPx() }
        val horizontalPaddingPx = with(density) { 24.dp.roundToPx() }
        val pages = remember(labels, maxWidthPx, textMeasurer, textStyle) {
            val pages = mutableListOf<List<HomeStatusChip>>()
            var page = mutableListOf<HomeStatusChip>()
            var usedWidth = 0
            labels.forEach { chip ->
                val textWidth = textMeasurer.measure(
                    text = AnnotatedString(chip.label),
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width
                val naturalChipWidth = textWidth + horizontalPaddingPx
                val chipWidth = if (chip.truncateToFit) naturalChipWidth.coerceAtMost(maxWidthPx) else naturalChipWidth
                if (!chip.truncateToFit && chipWidth > maxWidthPx) return@forEach
                val packedChip = if (chip.truncateToFit) chip.copy(maxWidthPx = chipWidth) else chip
                val proposedWidth = if (page.isEmpty()) chipWidth else usedWidth + gapPx + chipWidth
                if (page.isNotEmpty() && proposedWidth > maxWidthPx) {
                    pages += page
                    page = mutableListOf()
                    usedWidth = 0
                }
                if (page.isEmpty()) usedWidth = chipWidth else usedWidth += gapPx + chipWidth
                page += packedChip
            }
            if (page.isNotEmpty()) pages += page
            pages
        }

        var pageIndex by remember { mutableIntStateOf(0) }
        LaunchedEffect(pages) {
            pageIndex = 0
            if (pages.size > 1) {
                while (isActive) {
                    delay(4_500)
                    pageIndex = (pageIndex + 1) % pages.size
                }
            }
        }

        AnimatedContent(
            targetState = pages.getOrNull(pageIndex).orEmpty(),
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "home status chip pages",
        ) { page ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                page.forEach { chip ->
                    val maxWidth = chip.maxWidthPx?.let { with(density) { it.toDp() } }
                    HomeStatusPill(chip.label, color, textStyle, maxWidth)
                }
            }
        }
    }
}

@Composable
private fun HomeStatusPill(label: String, acrylicColor: Color, textStyle: TextStyle, maxWidth: Dp? = null) {
    val shape = RoundedCornerShape(13.dp)
    Text(
        text = label,
        color = Color.White.copy(.94f),
        style = textStyle,
        maxLines = 1,
        overflow = if (maxWidth != null) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = Modifier.clip(shape)
            .background(acrylicColor.copy(alpha = .72f))
            .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun ImportSourceScreen(
    onChooseAllDevice: () -> Unit,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("wavv", waveform = true)
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
                Text(
                    buildAnnotatedString {
                        append("Bring your music to ")
                        withStyle(SpanStyle(fontFamily = WavvBrandFontFamily, fontWeight = FontWeight.Normal)) {
                            append("wavv")
                        }
                    },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
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




@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomNav(
    active: Tab,
    onChange: (Tab) -> Unit,
    onReselect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(Tab.Home to "Home", Tab.Search to "Search", Tab.Library to "Library", Tab.You to "You")
    val activeIndex = tabs.indexOfFirst { it.first == active }
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnReselect by rememberUpdatedState(onReselect)
    var pillOffsetPx by remember { mutableFloatStateOf(0f) }
    var pillTargetTab by remember { mutableStateOf(active) }
    var pillIsDragging by remember { mutableStateOf(false) }
    var measuredItemWidthPx by remember { mutableFloatStateOf(0f) }
    var measuredMaxOffsetPx by remember { mutableFloatStateOf(0f) }
    val itemWidthForInput = rememberUpdatedState(measuredItemWidthPx)
    val maxOffsetForInput = rememberUpdatedState(measuredMaxOffsetPx)
    val navDragState = rememberDraggableState { delta ->
        pillOffsetPx = (pillOffsetPx + delta).coerceIn(0f, maxOffsetForInput.value)
    }
    val dragGlowProgress by animateFloatAsState(
        targetValue = if (pillIsDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = .86f, stiffness = 560f),
        label = "navigation pill liquid state",
    )
    val tabSpring = spring<Float>(
        dampingRatio = .88f,
        stiffness = 520f,
    )
    Box(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 4.dp).height(60.dp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape),
    ) {
        Box(Modifier.matchParentSize().homeNavBarFill())
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(4.dp)
                .draggable(
                    state = navDragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { pillIsDragging = true },
                    onDragStopped = {
                        val itemWidthPx = itemWidthForInput.value
                        if (itemWidthPx > 0f) {
                            // Settle on the tab under the pill when the finger lifts.
                            // Ignoring fling velocity prevents quick swipes overshooting.
                            val targetIndex = ((pillOffsetPx / itemWidthPx) + .5f)
                                .toInt().coerceIn(tabs.indices)
                            val destination = tabs[targetIndex].first
                            pillTargetTab = destination
                            currentOnChange(destination)
                        }
                        pillIsDragging = false
                    },
                ),
        ) {
            val itemWidth = maxWidth / tabs.size
            val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
            SideEffect {
                measuredItemWidthPx = itemWidthPx
                measuredMaxOffsetPx = itemWidthPx * tabs.lastIndex
            }
            LaunchedEffect(active) {
                pillTargetTab = active
            }
            LaunchedEffect(pillTargetTab, itemWidthPx, pillIsDragging) {
                if (!pillIsDragging && itemWidthPx > 0f) {
                    val targetIndex = tabs.indexOfFirst { it.first == pillTargetTab }.coerceAtLeast(0)
                    animate(
                        initialValue = pillOffsetPx,
                        targetValue = itemWidthPx * targetIndex,
                        animationSpec = tabSpring,
                    ) { value, _ ->
                        pillOffsetPx = value
                    }
                }
            }
            Box(Modifier.matchParentSize()) {
                val pillBaseModifier = Modifier.align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                x = pillOffsetPx.roundToInt(),
                                y = 0,
                            )
                        }
                        .fillMaxWidth(1f / tabs.size)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val position = if (itemWidthPx > 0f) {
                                (pillOffsetPx / itemWidthPx).coerceIn(0f, tabs.lastIndex.toFloat())
                            } else {
                                activeIndex.toFloat()
                            }
                            val progress = position - floor(position)
                            val stretch = sin(progress * PI).toFloat().coerceIn(0f, 1f)
                            scaleX = 1f + stretch * .14f * dragGlowProgress
                            scaleY = 1f - stretch * .06f * dragGlowProgress
                        }
                Box(
                    pillBaseModifier
                        .clip(CircleShape),
                ) {
                    // Keep the dark highlight static while its pill shape deforms under drag.
                    Box(Modifier.matchParentSize().homeNavBubbleFill())
                }
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { (tab, label) ->
                    val selected = tab == active
                    val itemTransition = updateTransition(selected, label = "$label selection")
                    val iconColor by itemTransition.animateColor(label = "$label icon color") { isSelected -> if (isSelected) WavvPink else Color.White.copy(alpha = .88f) }
                    val labelColor by itemTransition.animateColor(label = "$label label color") { isSelected -> if (isSelected) WavvPink else Color.White.copy(alpha = .55f) }
                    val iconScale by itemTransition.animateFloat(label = "$label icon scale") { isSelected -> if (isSelected) 1.06f else 1f }
                    val iconOffsetY by itemTransition.animateFloat(label = "$label icon offset") { isSelected -> if (isSelected) -1f else 0f }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                this.selected = selected
                                contentDescription = label
                            }
                            .clickable(role = Role.Tab) {
                            pillTargetTab = tab
                            if (tab == active) currentOnReselect(tab) else currentOnChange(tab)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
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
                            contentDescription = null,
                            colorFilter = if (tab == Tab.Library) null else androidx.compose.ui.graphics.ColorFilter.tint(iconColor),
                            modifier = Modifier.size(if (tab == Tab.Library) 18.dp else 20.dp, 20.dp).graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                                translationY = iconOffsetY.dp.toPx()
                            },
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(label, color = labelColor, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(song: Song, state: PlaybackState, progressFlow: StateFlow<PlaybackProgress>, accentColor: Color, songChangeDirection: Int, expanded: Boolean, stacked: Boolean, stackDockEnabled: Boolean, stackDockProgress: State<Float>, modifier: Modifier = Modifier, bottomClearance: Dp = 96.dp, liked: Boolean, onToggle: () -> Unit, onLike: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit, onCollapse: () -> Unit, onExpand: () -> Unit, onEnterStandby: () -> Unit, artworkFlightActive: Boolean, onArtworkFrameChange: (PlayerArtworkFrame) -> Unit) {
    val barMode = when { expanded -> NowPlayingBarMode.Expanded; stacked -> NowPlayingBarMode.Stacked; else -> NowPlayingBarMode.Docked }
    val barTransition = updateTransition(barMode, label = "now playing bar mode")
    val height by barTransition.animateDp(transitionSpec = { tween(540, easing = WavvMotionEasing) }, label = "player bar height") {
        if (it == NowPlayingBarMode.Expanded) 144.dp else 60.dp
    }
    val cornerRadius by barTransition.animateDp(transitionSpec = { tween(540, easing = WavvMotionEasing) }, label = "player bar corners") {
        if (it == NowPlayingBarMode.Expanded) 28.dp else 30.dp
    }
    val barOffset by barTransition.animateDp(transitionSpec = { tween(540, easing = WavvMotionEasing) }, label = "player bar home position") {
        when (it) { NowPlayingBarMode.Stacked -> (-178).dp; NowPlayingBarMode.Expanded -> (-24).dp; NowPlayingBarMode.Docked -> 0.dp }
    }
    val artworkSize by barTransition.animateDp(transitionSpec = { tween(540, easing = WavvMotionEasing) }, label = "player artwork size") {
        if (it == NowPlayingBarMode.Expanded) 50.dp else 40.dp
    }
    val artworkX by barTransition.animateDp(transitionSpec = { tween(540, easing = WavvMotionEasing) }, label = "player artwork horizontal position") {
        16.dp
    }
    val artworkY by barTransition.animateDp(transitionSpec = { tween(540, easing = WavvMotionEasing) }, label = "player artwork vertical position") {
        10.dp
    }
    val artworkCorner by barTransition.animateDp(transitionSpec = { tween(540, easing = WavvMotionEasing) }, label = "player artwork corners") {
        if (it == NowPlayingBarMode.Expanded) 11.dp else 10.dp
    }
    val shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    BoxWithConstraints(modifier) {
    val dockProgress = if (stackDockEnabled) stackDockProgress.value.coerceIn(0f, 1f) else 0f
    val bottomPosition = maxHeight - navigationBarBottom - bottomClearance - height + barOffset
    val topPosition = statusBarTop + 70.dp
    val barPosition = bottomPosition * (1f - dockProgress) + topPosition * dockProgress
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = barPosition).height(height)
        .pointerInput(expanded, stacked, onCollapse, onExpand, onEnterStandby) {
            if (!expanded && !stacked) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var swiped = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val delta = change.position - down.position
                    if (!swiped && kotlin.math.abs(delta.y) > viewConfiguration.touchSlop && kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x)) {
                        if (expanded && delta.y < 0f) {
                            swiped = true
                            change.consume()
                            onCollapse()
                        } else if (expanded && delta.y > 0f) {
                            swiped = true
                            change.consume()
                            onEnterStandby()
                        } else if (stacked && delta.y > 0f) {
                            swiped = true
                            change.consume()
                            onExpand()
                        }
                    } else if (swiped) {
                        change.consume()
                    }
                }
            }
        }
        .shadow(18.dp, shape).clip(shape)
        .clickable(onClick = onOpen)) {
        Box(Modifier.matchParentSize().homeNowPlayingFill(accentColor))
        Box(
            Modifier.align(Alignment.TopStart).offset(x = artworkX, y = artworkY).size(artworkSize)
                .onGloballyPositioned { coordinates ->
                    onArtworkFrameChange(
                        PlayerArtworkFrame(
                            bounds = coordinates.boundsInRoot(),
                            cornerRadiusPx = with(density) { artworkCorner.toPx() },
                            shadowElevationPx = with(density) { 12.dp.toPx() },
                        ),
                    )
                }
                .alpha(if (artworkFlightActive) 0f else 1f)
        ) {
            AnimatedContent(
                targetState = song,
                contentKey = { it.id },
                transitionSpec = {
                    fadeIn(tween(300, easing = WavvMotionEasing)) togetherWith
                        fadeOut(tween(260, easing = WavvMotionEasing))
                },
                label = "player artwork track change",
            ) { displayedSong ->
                AlbumArt(
                    displayedSong,
                    Modifier.fillMaxSize().shadow(12.dp, RoundedCornerShape(artworkCorner)),
                    displayedSong.title,
                    artworkCorner,
                )
            }
        }
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(tween(200, easing = WavvMotionEasing)) togetherWith fadeOut(tween(160, easing = WavvMotionEasing))
            },
            label = "player bar content",
        ) { big ->
            if (big) {
                Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.width(50.dp))
                        AnimatedContent(
                            targetState = song,
                            contentKey = { it.id },
                            transitionSpec = {
                                (slideInHorizontally(tween(320, easing = WavvMotionEasing)) { it * songChangeDirection } +
                                    fadeIn(tween(260, easing = WavvMotionEasing))) togetherWith
                                    (slideOutHorizontally(tween(260, easing = WavvMotionEasing)) { -it * songChangeDirection } +
                                        fadeOut(tween(200, easing = WavvMotionEasing)))
                            },
                            label = "expanded player track change",
                            modifier = Modifier.weight(1f),
                        ) { displayedSong ->
                            TrackText(
                                displayedSong,
                                Modifier.fillMaxWidth(),
                                15.sp,
                                subtitleSize = 12.sp,
                                titleLetterSpacing = (-.3).sp,
                                marquee = true,
                                uhqEnhanced = state.isUHQEnhanced,
                            )
                        }
                        FavouriteButton(liked, onLike)
                    }
                    Spacer(Modifier.height(12.dp))
                    MiniPlayerTimeline(song, progressFlow)
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(38.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(androidx.compose.ui.res.painterResource(R.drawable.skip_prev), "Previous", Modifier.size(40.dp).clickable(role = Role.Button, onClick = onPrevious))
                        Image(
                            androidx.compose.ui.res.painterResource(if (state.isPlaying) R.drawable.image_16 else R.drawable.play_button),
                            if (state.isPlaying) "Pause" else "Play",
                            Modifier.size(38.dp).clickable(role = Role.Button, onClick = onToggle),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                        )
                        Image(androidx.compose.ui.res.painterResource(R.drawable.skip_next), "Next", Modifier.size(40.dp).clickable(role = Role.Button, onClick = onNext))
                    }
                }
            } else {
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.width(40.dp))
                    AnimatedContent(
                        targetState = song,
                        contentKey = { it.id },
                        transitionSpec = {
                            (slideInHorizontally(tween(320, easing = WavvMotionEasing)) { it * songChangeDirection } +
                                fadeIn(tween(260, easing = WavvMotionEasing))) togetherWith
                                (slideOutHorizontally(tween(260, easing = WavvMotionEasing)) { -it * songChangeDirection } +
                                    fadeOut(tween(200, easing = WavvMotionEasing)))
                        },
                        label = "compact player track change",
                        modifier = Modifier.weight(1f),
                    ) { displayedSong ->
                        TrackText(displayedSong, Modifier.fillMaxWidth(), 14.sp, marquee = true, uhqEnhanced = state.isUHQEnhanced)
                    }
                    Image(
                        androidx.compose.ui.res.painterResource(R.drawable.skip_prev),
                        "Previous",
                        Modifier.size(20.dp).clickable(role = Role.Button, onClick = onPrevious),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                    )
                    Image(
                        androidx.compose.ui.res.painterResource(if (state.isPlaying) R.drawable.image_16 else R.drawable.play_button),
                        if (state.isPlaying) "Pause" else "Play",
                        Modifier.size(22.dp).clickable(role = Role.Button, onClick = onToggle),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                    )
                    Image(
                        androidx.compose.ui.res.painterResource(R.drawable.skip_next),
                        "Next",
                        Modifier.size(20.dp).clickable(role = Role.Button, onClick = onNext),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                    )
                }
            }
        }
    }
}

}

@Composable
private fun MiniPlayerTimeline(song: Song, progressFlow: StateFlow<PlaybackProgress>) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    val duration = max(progress.durationMs, song.durationMs).coerceAtLeast(1L)
    val position = progress.positionMs.coerceIn(0L, duration)
    Column {
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(.15f))) {
            Box(Modifier.fillMaxWidth(position.toFloat() / duration).fillMaxHeight().background(Color.White.copy(.8f)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Color.White.copy(.32f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text("-${formatTime(duration - position)}", color = Color.White.copy(.32f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TrackText(
    song: Song,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 14.sp,
    subtitleSize: TextUnit = 12.sp,
    titleLetterSpacing: TextUnit = TextUnit.Unspecified,
    titleColor: Color = Color.White,
    subtitleColor: Color = Color.White.copy(.5f),
    marquee: Boolean = false,
    uhqEnhanced: Boolean = false,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                song.title,
                modifier = Modifier.weight(1f).then(if (marquee) Modifier.songMetadataMarquee() else Modifier),
                color = titleColor,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = titleLetterSpacing,
                maxLines = 1,
                softWrap = false,
                overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
            if (uhqEnhanced) UHQPlaybackBadge()
        }
        Text(
            song.artist,
            modifier = Modifier.padding(top = 2.dp).then(if (marquee) Modifier.songMetadataMarquee() else Modifier),
            color = subtitleColor,
            fontSize = subtitleSize,
            maxLines = 1,
            softWrap = false,
            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UHQPlaybackBadge() {
    val shape = RoundedCornerShape(5.dp)
    Text(
        "UHQ",
        Modifier.clip(shape)
            .background(Color.White.copy(.12f))
            .border(1.dp, Color.White.copy(.28f), shape)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        color = Color.White.copy(.88f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = .35.sp,
        lineHeight = 11.sp,
    )
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.songMetadataMarquee(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 1_000,
    repeatDelayMillis = 1_600,
    spacing = MarqueeSpacing(24.dp),
    velocity = 22.dp,
)

@Composable
private fun PlaybackButtons(isPlaying: Boolean, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, iconSize: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(38.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Image(androidx.compose.ui.res.painterResource(R.drawable.skip_prev), "Previous", Modifier.size(iconSize).clickable(role = Role.Button, onClick = onPrevious))
        Image(
            androidx.compose.ui.res.painterResource(if (isPlaying) R.drawable.image_16 else R.drawable.play_button),
            if (isPlaying) "Pause" else "Play",
            Modifier.size(iconSize).clickable(role = Role.Button, onClick = onToggle),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
        )
        Image(androidx.compose.ui.res.painterResource(R.drawable.skip_next), "Next", Modifier.size(iconSize).clickable(role = Role.Button, onClick = onNext))
    }
}

@Composable
internal fun FigmaFavouriteIcon(filled: Boolean, iconSize: Dp) {
    val fillProgress by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "favorite icon transition",
    )
    Canvas(Modifier.size(iconSize)) {
        val unit = size.minDimension / 24f
        val fillAlpha = fillProgress.coerceIn(0f, 1f)
        val buttonRadius = 11.8f * unit
        drawCircle(Color.White.copy(alpha = .15f), radius = buttonRadius)

        val iconScale = .72f
        fun inset(value: Float) = 12f + (value - 12f) * iconScale
        val sparkle = Path().apply {
            moveTo(inset(12f) * unit, inset(.8f) * unit)
            cubicTo(inset(13.5f) * unit, inset(8f) * unit, inset(16f) * unit, inset(10.5f) * unit, inset(23.2f) * unit, inset(12f) * unit)
            cubicTo(inset(16f) * unit, inset(13.5f) * unit, inset(13.5f) * unit, inset(16f) * unit, inset(12f) * unit, inset(23.2f) * unit)
            cubicTo(inset(10.5f) * unit, inset(16f) * unit, inset(8f) * unit, inset(13.5f) * unit, inset(.8f) * unit, inset(12f) * unit)
            cubicTo(inset(8f) * unit, inset(10.5f) * unit, inset(10.5f) * unit, inset(8f) * unit, inset(12f) * unit, inset(.8f) * unit)
            close()
        }
        val fillTop = size.height * (1f - fillAlpha)
        if (fillAlpha > 0f) {
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, fillTop, size.width, size.height)
            drawPath(sparkle, color = Color.White)
            drawContext.canvas.restore()
        }
        drawPath(
            sparkle,
            color = Color.White,
            style = Stroke(width = 1.25f * unit, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

@Composable
private fun FavouriteButton(liked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(38.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (liked) "Remove from favorites" else "Add to favorites"
                stateDescription = if (liked) "Liked" else "Not liked"
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FigmaFavouriteIcon(liked, 30.dp)
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
    progressFlow: StateFlow<PlaybackProgress>,
    audioOutputState: AudioOutputRoutingState,
    onSelectAudioRoute: (String) -> Unit,
    onSetAudioRouteVolume: (String, Int) -> Unit,
    onRefreshAudioRoutes: () -> Unit,
    liked: Boolean,
    showQueue: Boolean,
    queueMode: QueueMode,
    currentQueueIndex: Int,
    playlists: List<UserPlaylist>,
    onClose: (PlayerArtworkFrame?) -> Unit,
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
    onPlayAutoplaySong: (Song) -> Unit,
    onPlayNextFromAutoplay: (Song) -> Unit,
    onAddAutoplayToQueue: (Song) -> Unit,
    onRemoveAutoplaySong: (Song) -> Unit,
    onMoveAutoplayItem: (Int, Int) -> Unit,
    onSetAutoplayEnabled: (Boolean) -> Unit,
    onSetTrackUHQ: (Long, Boolean) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onAddSongToPlaylist: (Long, List<Long>, (Boolean) -> Unit) -> Unit,
    onCreatePlaylist: (Long) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    artworkFlightActive: Boolean,
    lyricsArtworkFlightActive: Boolean,
    onOpenLyricsArtwork: (PlayerArtworkFrame?) -> Unit,
    onCloseLyricsArtwork: (PlayerArtworkFrame?) -> Unit,
    onArtworkFrameChange: (PlayerArtworkFrame) -> Unit,
    onLyricsArtworkFrameChange: (PlayerArtworkFrame) -> Unit,
    onArtworkDragStart: (PlayerArtworkFrame) -> Unit,
    onArtworkDragProgress: (Float) -> Unit,
    onArtworkDragCancel: () -> Unit,
) {
    val latestOnClose by rememberUpdatedState(onClose)
    val latestOnArtworkDragStart by rememberUpdatedState(onArtworkDragStart)
    val latestOnArtworkDragProgress by rememberUpdatedState(onArtworkDragProgress)
    val latestOnArtworkDragCancel by rememberUpdatedState(onArtworkDragCancel)
    val latestOnCloseLyricsArtwork by rememberUpdatedState(onCloseLyricsArtwork)
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var audioDetails by remember(song.id, song.uri) { mutableStateOf<AudioQualityDetails?>(null) }
    var showQualityDetails by remember(song.id) { mutableStateOf(false) }
    var showQualitySettings by remember(song.id) { mutableStateOf(false) }
    var uhqEnabledForSettings by remember(context) { mutableStateOf(UHQPlaybackPreference.isEnabled(context)) }
    // Keep the lyrics page open as the player advances to the next queued track.
    var showLyrics by remember { mutableStateOf(false) }
    var importedLyrics by remember(song.id) { mutableStateOf<List<LyricLine>?>(null) }
    var lyricsOffsetMs by remember(song.id) { mutableStateOf(0L) }
    var currentLyricsArtworkFrame by remember(song.id) { mutableStateOf<PlayerArtworkFrame?>(null) }
    var showOutputs by remember(song.id) { mutableStateOf(false) }
    var showTrackOptions by remember(song.id) { mutableStateOf(false) }
    var playlistSong by remember(song.id) { mutableStateOf<Song?>(null) }
    var showNextTitle by remember(song.id) { mutableStateOf(false) }
    val nextUpOffset = remember(song.id) { Animatable(0f) }
    val nextUpLiftPx = with(LocalDensity.current) { 6.dp.toPx() }
    var hasNudgedNextUp by remember(song.id) { mutableStateOf(false) }
    var playerDragOffset by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var artworkGestureStarted by remember(song.id) { mutableStateOf(false) }
    var currentArtworkFrame by remember(song.id) { mutableStateOf<PlayerArtworkFrame?>(null) }
    var artworkCoordinates by remember(song.id) { mutableStateOf<LayoutCoordinates?>(null) }
    val closeLyricsPage: () -> Unit = {
        if (showLyrics) {
            showLyrics = false
            latestOnCloseLyricsArtwork(currentLyricsArtworkFrame)
        }
    }
    LaunchedEffect(song.id, song.uri) {
        audioDetails = withContext(Dispatchers.IO) {
            runCatching { readAudioQualityDetails(appContext, song) }.getOrNull()
        }
    }
    val gestureScope = rememberCoroutineScope()
    BackHandler(enabled = showLyrics, onBack = closeLyricsPage)
    val nextSong = state.queue.getOrNull(currentQueueIndex + 1)
        ?.takeIf { it.id != song.id }
        ?: state.autoplaySongs.firstOrNull()?.takeIf { state.autoplayEnabled && it.id != song.id }
    val nextTitle = nextSong?.title
    LaunchedEffect(showQueue, nextTitle) {
        showNextTitle = false
        if (!showQueue && nextTitle != null) {
            while (isActive) {
                delay(3_500)
                showNextTitle = !showNextTitle
            }
        }
    }
    LaunchedEffect(showQueue, song.id) {
        if (!showQueue && !hasNudgedNextUp) {
            delay(1_500)
            nextUpOffset.animateTo(-nextUpLiftPx, tween(1_100, easing = WavvEaseInOut))
            nextUpOffset.animateTo(0f, tween(1_100, easing = WavvEaseInOut))
            hasNudgedNextUp = true
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val playerMaxWidth = maxWidth
        val density = LocalDensity.current
        val playerHeightPx = with(density) { maxHeight.toPx() }
        val lyricsTopSwipeZonePx = with(density) { 220.dp.toPx() }
        val targetArtworkSize = if (state.isPlaying) playerMaxWidth else minOf(playerMaxWidth * .76f, 320.dp)
        val artworkSize by animateDpAsState(targetArtworkSize, tween(620, easing = WavvMotionEasing), label = "player artwork size")
        val artworkTop by animateDpAsState(if (state.isPlaying) 56.dp else 76.dp, tween(620, easing = WavvMotionEasing), label = "player artwork position")
        val artworkRadius by animateDpAsState(if (state.isPlaying) 0.dp else 16.dp, tween(620, easing = WavvMotionEasing), label = "player artwork corners")
        val artworkShadow by animateDpAsState(if (state.isPlaying) 0.dp else 20.dp, tween(780, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)), label = "player artwork shadow")
        val regularArtAlpha by animateFloatAsState(if (state.isPlaying) 0f else 1f, tween(780, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)), label = "regular artwork fade")
        val maskedArtAlpha by animateFloatAsState(if (state.isPlaying) 1f else 0f, tween(780, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)), label = "masked artwork fade")
        val dismissPlayer: () -> Unit = {
            if (!isDismissing) {
                val measuredFrame = currentArtworkFrame
                val latestFrame = measuredFrame?.copy(
                    bounds = artworkCoordinates?.boundsInRoot() ?: measuredFrame.bounds,
                    alpha = 1f - (playerDragOffset / (playerHeightPx * .62f)).coerceIn(0f, .55f),
                    regularArtworkAlpha = regularArtAlpha,
                    maskedArtworkAlpha = maskedArtAlpha,
                    shadowElevationPx = with(density) { artworkShadow.toPx() },
                )
                latestOnClose(latestFrame)
                isDismissing = true
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
                    ) { value, _ ->
                        playerDragOffset = value
                        if (artworkGestureStarted) latestOnArtworkDragProgress(value)
                    }
                    if (artworkGestureStarted) {
                        latestOnArtworkDragCancel()
                        artworkGestureStarted = false
                    }
                }
            } else if (artworkGestureStarted) {
                latestOnArtworkDragCancel()
                artworkGestureStarted = false
            }
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = playerDragOffset
                    // Fade the player chrome quickly with the finger; the shared artwork flight
                    // is rendered in a sibling layer and keeps its own path and timing.
                    alpha = 1f - (playerDragOffset / (playerHeightPx * .18f)).coerceIn(0f, 1f)
                }
                .background(Color.Black)
                .pointerInput(song.id, showQueue, showLyrics, isDismissing, playerHeightPx) {
            var total = 0f
            var dragStartY = 0f
            var downwardDragStarted = false
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    total = 0f
                    dragStartY = offset.y
                    downwardDragStarted = false
                },
                onVerticalDrag = { _, amount ->
                    total += amount
                    if (amount > 0f) downwardDragStarted = true
                    if (!showQueue && !showLyrics && !isDismissing) {
                        val nextOffset = (playerDragOffset + amount).coerceAtLeast(0f)
                        if (nextOffset > 0f && !artworkGestureStarted) {
                            val measuredFrame = currentArtworkFrame
                            val frame = measuredFrame?.copy(
                                bounds = artworkCoordinates?.boundsInRoot() ?: measuredFrame.bounds,
                                alpha = 1f,
                                regularArtworkAlpha = regularArtAlpha,
                                maskedArtworkAlpha = maskedArtAlpha,
                                shadowElevationPx = with(density) { artworkShadow.toPx() },
                            )
                            if (frame != null) {
                                latestOnArtworkDragStart(frame)
                                artworkGestureStarted = true
                            }
                        }
                        playerDragOffset = nextOffset
                        if (artworkGestureStarted) latestOnArtworkDragProgress(nextOffset)
                    }
                },
                onDragEnd = {
                    if (showLyrics) {
                        if (total > 84 && downwardDragStarted && dragStartY <= lyricsTopSwipeZonePx) closeLyricsPage()
                    } else if (showQueue && total > 80) {
                        onHideQueue()
                    } else if (!showQueue && playerDragOffset > 100f) {
                        dismissPlayer()
                    } else if (total < -80 && !downwardDragStarted && playerDragOffset <= 0f && !showQueue) {
                        onShowQueue()
                    } else {
                        settlePlayer()
                    }
                },
                onDragCancel = { if (!showLyrics && !isDismissing) settlePlayer() },
            )
        },
        ) {
        NowPlayingAmbientBackdrop(song, state.isPlaying)
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = artworkTop)
                .size(artworkSize)
                .shadow(artworkShadow, RoundedCornerShape(artworkRadius))
                .clip(RoundedCornerShape(artworkRadius))
                .onGloballyPositioned { coordinates ->
                    artworkCoordinates = coordinates
                    val frame = PlayerArtworkFrame(
                        bounds = coordinates.boundsInRoot(),
                        cornerRadiusPx = with(density) { artworkRadius.toPx() },
                        regularArtworkAlpha = regularArtAlpha,
                        maskedArtworkAlpha = maskedArtAlpha,
                        shadowElevationPx = with(density) { artworkShadow.toPx() },
                    )
                    currentArtworkFrame = frame
                    onArtworkFrameChange(frame)
                }
                .alpha(if (artworkFlightActive) 0f else 1f),
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
                    TrackText(
                        song,
                        Modifier.weight(1f).padding(end = 12.dp),
                        28.sp,
                        20.sp,
                        (-.65).sp,
                        marquee = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        FavouriteButton(liked, onLike)
                        Box(Modifier.size(38.dp).clickable { showTrackOptions = true }, contentAlignment = Alignment.Center) { FigmaMoreIcon(30.dp) }
                    }
                }
                FullPlayerTimeline(song, progressFlow, onSeek)
            }
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                val qualityLabel = when {
                    state.isUHQEnhanced -> "UHQ"
                    else -> audioDetails?.qualityName
                }
                if (qualityLabel != null) QualityStatusChip(qualityLabel) { showQualityDetails = true }
            }
            PlaybackButtons(state.isPlaying, onToggle, onPrevious, onNext, iconSize = 48.dp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    onOpenLyricsArtwork(currentArtworkFrame)
                    showLyrics = true
                }) { Image(androidx.compose.ui.res.painterResource(R.drawable.song_lyrics), "Lyrics", Modifier.size(24.dp).alpha(.7f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)) }
                AudioOutputButton(audioOutputState, onClick = { showOutputs = true })
                IconButton(onClick = onCycleLoop) {
                    Icon(if (loopMode == LoopMode.One) Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = "Loop " + loopMode.name, tint = if (loopMode == LoopMode.Off) Color.White.copy(.4f) else Color.White.copy(.8f))
                }
            }
            Column(
                Modifier.fillMaxWidth().clickable(onClick = onShowQueue),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = showNextTitle && nextTitle != null,
                    transitionSpec = {
                        fadeIn(tween(500, easing = WavvEaseInOut)) togetherWith
                            fadeOut(tween(500, easing = WavvEaseInOut))
                    },
                    label = "next up and song title",
                ) { showSongTitle ->
                    Text(
                        if (showSongTitle) nextTitle.orEmpty() else "Next Up",
                        Modifier.fillMaxWidth()
                            .graphicsLayer { translationY = nextUpOffset.value }
                            .padding(top = 2.dp, bottom = 8.dp),
                        color = Color.White.copy(.82f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showQueue,
            enter = fadeIn(tween(150, easing = WavvEaseInOut)) + slideInVertically(tween(280, easing = WavvMotionEasing)) { it / 2 },
            exit = fadeOut(tween(130, easing = WavvEaseInOut)) + slideOutVertically(tween(240, easing = WavvMotionEasing)) { it / 2 },
        ) {
            QueuePanel(
                song = song,
                queue = state.queue,
                autoplaySongs = state.autoplaySongs,
                autoplayEnabled = state.autoplayEnabled,
                playingFrom = state.playingFrom,
                liked = liked,
                queueMode = queueMode,
                loopMode = loopMode,
                playlists = playlists,
                onLike = onLike,
                onClose = onHideQueue,
                onCycleLoop = onCycleLoop,
                onQueueMode = onQueueMode,
                onPlayNext = onPlayNext,
                onPlayAutoplaySong = onPlayAutoplaySong,
                onPlayNextFromAutoplay = onPlayNextFromAutoplay,
                onAddAutoplayToQueue = onAddAutoplayToQueue,
                onRemoveAutoplaySong = onRemoveAutoplaySong,
                onMoveAutoplayItem = onMoveAutoplayItem,
                onSetAutoplayEnabled = onSetAutoplayEnabled,
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
            enter = fadeIn(tween(280, easing = WavvMotionEasing)) +
                slideInVertically(tween(420, easing = WavvMotionEasing)) { it / 9 } +
                scaleIn(tween(420, easing = WavvMotionEasing), initialScale = .975f),
            exit = fadeOut(tween(180, easing = CubicBezierEasing(.4f, 0f, .2f, 1f))) +
                slideOutVertically(tween(360, easing = WavvMotionEasing)) { it / 7 } +
                scaleOut(tween(360, easing = WavvMotionEasing), targetScale = .985f),
        ) {
            LyricsPanel(
                song = song,
                progressFlow = progressFlow,
                isPlaying = state.isPlaying,
                onSeek = onSeek,
                onToggle = onToggle,
                lyricOffsetMs = lyricsOffsetMs,
                onOffsetChange = { lyricsOffsetMs = it },
                importedLyrics = importedLyrics,
                onImportedLyrics = { importedLyrics = it },
                lyricsArtworkFlightActive = lyricsArtworkFlightActive,
                onArtworkFrameChange = { frame ->
                    currentLyricsArtworkFrame = frame
                    onLyricsArtworkFrameChange(frame)
                },
                onClose = closeLyricsPage,
            )
        }
        if (showOutputs) AudioOutputPanel(
            song = song,
            isPlaying = state.isPlaying,
            state = audioOutputState,
            onSelectAudioRoute = onSelectAudioRoute,
            onSetAudioRouteVolume = onSetAudioRouteVolume,
            onRefreshAudioRoutes = onRefreshAudioRoutes,
            onClose = { showOutputs = false },
        )
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
                if (state.isUHQEnhanced || audioDetails?.uhqEligible == true) {
                    SheetActionRow(
                        Icons.Default.GraphicEq,
                        if (state.isUHQEnhanced) "Play original" else "Upgrade quality · UHQ",
                    ) {
                        close()
                        onSetTrackUHQ(song.id, !state.isUHQEnhanced)
                    }
                }
            }
        }
        if (showQualityDetails) {
            AudioQualityInfoDialog(
                details = audioDetails,
                uhqActive = state.isUHQEnhanced,
                onDismiss = { showQualityDetails = false },
                onOpenSettings = {
                    uhqEnabledForSettings = UHQPlaybackPreference.isEnabled(context)
                    showQualityDetails = false
                    showQualitySettings = true
                },
            )
        }
        if (showQualitySettings) {
            AlertDialog(
                onDismissRequest = { showQualitySettings = false },
                title = { Text("Audio quality settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("UHQ AAC enhancement", fontWeight = FontWeight.SemiBold)
                        Text("When enabled, eligible AAC tracks use their cached UHQ version automatically.", color = Color.White.copy(.68f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (uhqEnabledForSettings) "On" else "Off")
                            Switch(
                                checked = uhqEnabledForSettings,
                                onCheckedChange = { enabled ->
                                    uhqEnabledForSettings = enabled
                                    UHQPlaybackPreference.setEnabled(context, enabled)
                                },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showQualitySettings = false }) { Text("Done") } },
            )
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
                                onClick = {
                                    onAddSongToPlaylist(playlist.id, listOf(selectedSong.id)) { success ->
                                        if (success) playlistSong = null
                                    }
                                },
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
private fun QualityStatusChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.offset(y = (-17).dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(.105f), Color(0xFFB6D5FF).copy(.14f), Color.White.copy(.08f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        QualityWaveLogo(18.dp)
        Text(label, color = Color.White.copy(.96f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun QualityWaveLogo(logoSize: Dp) {
    Canvas(Modifier.size(logoSize)) {
        val canvasSize = this.size
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFE4F0FF).copy(.42f), Color(0xFFB6D5FF).copy(.14f), Color.Transparent),
                center = center,
                radius = canvasSize.minDimension * .72f,
            ),
        )
        val strokeWidth = canvasSize.minDimension * .075f
        for (wave in 0..2) {
            val path = Path()
            val yCenter = canvasSize.height * (.38f + wave * .12f)
            for (point in 0..24) {
                val x = canvasSize.width * (.11f + point / 24f * .78f)
                val angle = point / 24f * Math.PI * 2.3f + wave * .82f
                val y = yCenter + kotlin.math.sin(angle).toFloat() * canvasSize.height * .11f
                if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color.White.copy(.95f - wave * .12f), style = Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

@Composable
private fun AudioQualityInfoDialog(
    details: AudioQualityDetails?,
    uhqActive: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showStats by remember(details) { mutableStateOf(false) }
    val view = LocalView.current
    val density = LocalDensity.current
    val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setDimAmount(.32f)
        if (Build.VERSION.SDK_INT >= 31) {
            dialogWindow?.setBackgroundBlurRadius(with(density) { 22.dp.roundToPx() })
        }
    }
    val title = if (uhqActive) "UHQ" else details?.qualityName ?: "Audio quality"
    val subtitle = when {
        uhqActive -> "16-bit/44.1 kHz PCM output"
        details == null -> "Reading audio details…"
        else -> details.formattedSourceSpec()
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xF2171717), shape = RoundedCornerShape(26.dp), tonalElevation = 0.dp) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 380.dp).heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QualityWaveLogo(58.dp)
                Spacer(Modifier.height(14.dp))
                Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color.White.copy(.76f), fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (uhqActive) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "UHQ is Wavv’s AAC enhancement. It processes the source for this player; it does not add detail or make the original file higher resolution.",
                        color = Color.White.copy(.62f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QualityDetailRow("Codec", if (uhqActive) "AAC → PCM" else details?.format ?: "Not reported")
                    QualityDetailRow("Bit depth", if (uhqActive) "16-bit output" else details?.bitDepth?.let { "$it-bit" } ?: "Not reported")
                    QualityDetailRow("Sample rate", if (uhqActive) "44.1 kHz output" else details?.sampleRateHz?.let(::formatSampleRate) ?: "Not reported")
                    QualityDetailRow(
                        "Bitrate",
                        details?.let { it.formattedBitrate() + if (it.bitrateIsEstimated) " (estimated)" else "" } ?: "Not reported",
                    )
                    QualityDetailRow("Channels", if (uhqActive) "Stereo output" else details?.channels?.let { if (it == 2) "Stereo" else if (it == 1) "Mono" else "$it channels" } ?: "Not reported")
                }
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = { showStats = !showStats }) {
                    Text(if (showStats) "Hide stats for nerds" else "Stats for nerds", color = Color.White.copy(.8f))
                }
                AnimatedVisibility(visible = showStats, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QualityDetailRow("Source MIME", details?.mimeType ?: "Not reported")
                        QualityDetailRow("Container", details?.container ?: "Not reported")
                        QualityDetailRow("Source file", details?.fileSizeBytes?.takeIf { it > 0 }?.let {
                            android.text.format.Formatter.formatShortFileSize(LocalContext.current, it)
                        } ?: "Not reported")
                        QualityDetailRow("Duration", details?.durationMs?.takeIf { it > 0 }?.let(::formatTime) ?: "Not reported")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(.12f)))
                TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Audio Quality Settings", color = Color.White.copy(.92f), fontSize = 15.sp)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Done", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun QualityDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(.58f), fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = Color.White.copy(.91f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun NowPlayingAmbientBackdrop(song: Song, isPlaying: Boolean) {
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
private fun FullPlayerTimeline(
    song: Song,
    progressFlow: StateFlow<PlaybackProgress>,
    onSeek: (Long) -> Unit,
    onScrubPosition: (Long?) -> Unit = {},
) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    val duration = max(progress.durationMs, song.durationMs).coerceAtLeast(1L)
    var dragPositionMs by remember(song.id) { mutableStateOf<Long?>(null) }
    val position = (dragPositionMs ?: progress.positionMs).coerceIn(0L, duration)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnScrubPosition by rememberUpdatedState(onScrubPosition)
    Column {
        Box(
            Modifier.fillMaxWidth().padding(top = 16.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(.2f))
                .pointerInput(duration) {
                    detectTapGestures { point ->
                        latestOnSeek((point.x / size.width * duration).toLong().coerceIn(0L, duration))
                    }
                }
                .pointerInput(duration) {
                    var lastPreviewDispatchMs = 0L
                    var pendingPositionMs: Long? = null
                    fun positionAt(x: Float): Long =
                        (x / size.width * duration).toLong().coerceIn(0L, duration)

                    detectDragGestures(
                        onDragStart = { point ->
                            pendingPositionMs = positionAt(point.x)
                            dragPositionMs = pendingPositionMs
                            latestOnScrubPosition(pendingPositionMs)
                            lastPreviewDispatchMs = System.nanoTime() / 1_000_000L
                        },
                        onDrag = { change, _ ->
                            val nextPositionMs = positionAt(change.position.x)
                            pendingPositionMs = nextPositionMs
                            dragPositionMs = nextPositionMs
                            val nowMs = System.nanoTime() / 1_000_000L
                            // Keep the slider and lyric preview responsive without repeatedly
                            // seeking the decoder while the user is still dragging.
                            if (nowMs - lastPreviewDispatchMs >= 50L) {
                                latestOnScrubPosition(nextPositionMs)
                                lastPreviewDispatchMs = nowMs
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            pendingPositionMs?.let(latestOnSeek)
                            dragPositionMs = null
                            latestOnScrubPosition(null)
                        },
                        onDragCancel = {
                            pendingPositionMs?.let(latestOnSeek)
                            dragPositionMs = null
                            latestOnScrubPosition(null)
                        },
                    )
                },
        ) {
            Box(Modifier.fillMaxWidth(position.toFloat() / duration).fillMaxHeight().background(Color.White.copy(.8f)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .2.sp)
            Text("-${formatTime(duration - position)}", color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .2.sp)
        }
    }
}

@Composable
private fun PlayerProgress(
    progressFlow: StateFlow<PlaybackProgress>,
    song: Song,
    onSeek: (Long) -> Unit = {},
    accentColor: Color = Color(0xFFFFAE46),
    sliderHeight: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    val duration = max(progress.durationMs, song.durationMs).coerceAtLeast(1L)
    val position = progress.positionMs.coerceIn(0L, duration)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = position.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth().height(sliderHeight),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(.2f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(position), color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text("-" + formatTime((duration - position).coerceAtLeast(0L)), color = Color.White.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AudioOutputButton(state: AudioOutputRoutingState, onClick: () -> Unit) {
    val selectedRoute = state.selectedRoute
    IconButton(onClick = onClick) {
        OutputGlyph(
            kind = selectedRoute?.kind ?: "speaker",
            color = Color.White.copy(.78f),
            modifier = Modifier.size(23.dp).semantics {
                contentDescription = selectedRoute?.let { "Audio output, ${it.name}" }
                    ?: "Audio output, phone speaker"
            },
        )
    }
}

@Composable
private fun AudioOutputPanel(
    song: Song,
    isPlaying: Boolean,
    state: AudioOutputRoutingState,
    onSelectAudioRoute: (String) -> Unit,
    onSetAudioRouteVolume: (String, Int) -> Unit,
    onRefreshAudioRoutes: () -> Unit,
    onClose: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var isVolumeAdjusting by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val view = LocalView.current
    val density = LocalDensity.current
    val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setDimAmount(.46f)
        if (Build.VERSION.SDK_INT >= 31) dialogWindow?.setBackgroundBlurRadius(with(density) { 20.dp.roundToPx() })
    }
    LaunchedEffect(Unit) {
        onRefreshAudioRoutes()
        visible = true
    }
    LaunchedEffect(visible) {
        if (!visible) {
            delay(200)
            close()
        }
    }
    Dialog(onDismissRequest = { visible = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable { if (visible) visible = false })
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, bottom = 16.dp).widthIn(max = 360.dp),
                enter = fadeIn(tween(180)) + scaleIn(tween(240, easing = WavvEaseInOut), initialScale = .94f),
                exit = fadeOut(tween(140)) + scaleOut(tween(180, easing = WavvEaseInOut), targetScale = .97f),
            ) {
                val shape = RoundedCornerShape(27.dp)
                Box(Modifier.fillMaxWidth().height(318.dp).shadow(26.dp, shape).clip(shape)) {
                    AlbumArt(
                        song,
                        Modifier.fillMaxSize().blur(34.dp).graphicsLayer { scaleX = 1.18f; scaleY = 1.18f },
                        null,
                        0.dp,
                    )
                    Box(Modifier.fillMaxSize().background(Color(0xE8171717)))
                    Column(Modifier.fillMaxSize().padding(horizontal = 17.dp, vertical = 19.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AlbumArt(song, Modifier.size(56.dp), null, 12.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    Modifier.fillMaxWidth().songMetadataMarquee(),
                                    color = Color(0xFFF5F4F2),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                                Text(
                                    song.artist,
                                    Modifier.fillMaxWidth().padding(top = 4.dp).songMetadataMarquee(),
                                    color = Color.White.copy(.68f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                            OutputWaveform(isPlaying, Modifier.padding(top = 3.dp).align(Alignment.Top))
                        }
                        Spacer(Modifier.height(18.dp))
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            state.routes.forEach { route ->
                                OutputRouteRow(
                                    route = route,
                                    selected = route.id == state.selectedRoute?.id,
                                    pending = route.id == state.pendingRouteId,
                                    routeRevision = state.revision,
                                    isVolumeAdjusting = isVolumeAdjusting,
                                    onVolumeAdjustingChanged = { isVolumeAdjusting = it },
                                    onSelect = {
                                        if (!isVolumeAdjusting && state.pendingRouteId == null) onSelectAudioRoute(route.id)
                                    },
                                    onSetVolume = { volume -> onSetAudioRouteVolume(route.id, volume) },
                                )
                            }
                            if (state.routes.isEmpty()) {
                                if (state.isReady) {
                                    Text(
                                        "No supported audio output routes are available.",
                                        Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                        color = Color.White.copy(.62f),
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                } else {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Text("Finding output routes…", Modifier.padding(start = 9.dp), color = Color.White.copy(.68f), fontSize = 13.sp)
                                    }
                                }
                            }
                            state.selectedRoute?.takeIf { it.isRemote && state.routes.none { route -> route.id == it.id } }?.let { route ->
                                Text(
                                    "Current remote destination: ${route.name}",
                                    Modifier.fillMaxWidth().padding(top = 5.dp),
                                    color = Color.White.copy(.64f),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                            if (state.pendingRouteId != null) {
                                Text(
                                    "Switching output…",
                                    Modifier.fillMaxWidth().padding(top = 3.dp),
                                    color = Color.White.copy(.68f),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                            state.errorMessage?.let { message ->
                                Text(
                                    message,
                                    Modifier.fillMaxWidth().padding(top = 3.dp),
                                    color = Color(0xFFFFB4AB),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
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
private fun OutputRouteRow(
    route: AudioOutputRoute,
    selected: Boolean,
    pending: Boolean,
    routeRevision: Long,
    isVolumeAdjusting: Boolean,
    onVolumeAdjustingChanged: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onSetVolume: (Int) -> Unit,
) {
    val volumeMax = route.volumeMax.coerceAtLeast(0)
    val routeVolume = route.volume.coerceIn(0, volumeMax)
    val routeFraction = if (volumeMax > 0) (routeVolume.toFloat() / volumeMax).coerceIn(0f, 1f) else 0f
    val canAdjustVolume = selected &&
        route.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE && volumeMax > 0
    var isAdjusting by remember(route.id) { mutableStateOf(false) }
    var dragVolumeFraction by remember(route.id) { mutableFloatStateOf(routeFraction) }
    var lastRequestedVolume by remember(route.id) { mutableIntStateOf(routeVolume) }
    LaunchedEffect(route.id, routeRevision, routeVolume, isAdjusting) {
        if (!isAdjusting) {
            lastRequestedVolume = routeVolume
            dragVolumeFraction = routeFraction
        }
    }
    val volumeFraction by animateFloatAsState(
        targetValue = if (isAdjusting) dragVolumeFraction else routeFraction,
        animationSpec = tween(durationMillis = if (isAdjusting) 0 else 90, easing = LinearEasing),
        label = "${route.id} output volume fill",
    )
    val kind = route.kind
    val label = route.name
    val setVolume: (Float) -> Unit = { fraction ->
        dragVolumeFraction = fraction.coerceIn(0f, 1f)
        val targetStep = (fraction.coerceIn(0f, 1f) * volumeMax).roundToInt().coerceIn(0, volumeMax)
        if (canAdjustVolume && targetStep != lastRequestedVolume) {
            onSetVolume(targetStep)
            lastRequestedVolume = targetStep
        }
    }
    val interactionModifier = if (canAdjustVolume) {
        Modifier
            .pointerInput(route.id, volumeMax) {
                detectTapGestures {
                    setVolume(it.x / size.width)
                    isAdjusting = false
                    onVolumeAdjustingChanged(false)
                }
            }
            .pointerInput(route.id, volumeMax) {
                detectHorizontalDragGestures(
                    onDragStart = { point ->
                        isAdjusting = true
                        onVolumeAdjustingChanged(true)
                        setVolume(point.x / size.width)
                    },
                    onDragEnd = {
                        isAdjusting = false
                        onVolumeAdjustingChanged(false)
                    },
                    onDragCancel = {
                        isAdjusting = false
                        onVolumeAdjustingChanged(false)
                    },
                ) { change, _ ->
                    setVolume(change.position.x / size.width)
                    change.consume()
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = "Volume ${(volumeFraction * 100).roundToInt()} percent"
            }
    } else if (!selected && !pending) {
        Modifier.clickable(enabled = !isVolumeAdjusting, role = Role.Button, onClick = onSelect)
    } else Modifier
    val background = if (selected) Color.White.copy(.25f) else Color.White.copy(.09f)
    val fill = if (selected) Color(0xFFECEBE8) else Color.White.copy(.26f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(50.dp).clip(CircleShape)
            .background(background).then(interactionModifier),
    ) {
        val fillWidth = maxWidth * volumeFraction
        val iconSize = 21.dp
        val iconFillWidth = (fillWidth - 15.dp).coerceIn(0.dp, iconSize)
        val iconFillPx = with(LocalDensity.current) { iconFillWidth.toPx() }
        val textWidth = (maxWidth - 15.dp - iconSize - 10.dp - 15.dp).coerceAtLeast(0.dp)
        val labelFillWidth = (fillWidth - 15.dp - iconSize - 10.dp).coerceIn(0.dp, textWidth)
        val labelFillPx = with(LocalDensity.current) { labelFillWidth.toPx() }
        Box(
            Modifier.fillMaxHeight().width(fillWidth)
                .background(fill),
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selected) {
                Box(Modifier.size(iconSize)) {
                    OutputGlyph(
                        kind,
                        Color(0xFF292929),
                        Modifier.fillMaxSize().drawWithContent {
                            drawContext.canvas.save()
                            drawContext.canvas.clipRect(0f, 0f, iconFillPx, size.height)
                            drawContent()
                            drawContext.canvas.restore()
                        },
                    )
                    OutputGlyph(
                        kind,
                        Color(0xFFF3F2F0),
                        Modifier.fillMaxSize().drawWithContent {
                            drawContext.canvas.save()
                            drawContext.canvas.clipRect(iconFillPx, 0f, size.width, size.height)
                            drawContent()
                            drawContext.canvas.restore()
                        },
                    )
                }
            } else {
                OutputGlyph(kind, Color(0xFFF3F2F0), Modifier.size(iconSize))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(
                    label,
                    Modifier.fillMaxWidth().songMetadataMarquee().drawWithContent {
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(0f, 0f, labelFillPx, size.height)
                        drawContent()
                        drawContext.canvas.restore()
                    },
                    color = Color(0xFF292929),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    label,
                    Modifier.fillMaxWidth().songMetadataMarquee().drawWithContent {
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(labelFillPx, 0f, size.width, size.height)
                        drawContent()
                        drawContext.canvas.restore()
                    },
                    color = Color(0xFFF3F2F0),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            if (pending) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
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
            "headphones" -> {
                path.moveTo(4f * sx, 13f * sy)
                path.lineTo(4f * sx, 10f * sy)
                path.cubicTo(4f * sx, 5.582f * sy, 7.582f * sx, 2f * sy, 12f * sx, 2f * sy)
                path.cubicTo(16.418f * sx, 2f * sy, 20f * sx, 5.582f * sy, 20f * sx, 10f * sy)
                drawPath(path, color, style = Stroke(width = 2.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawRoundRect(color, Offset(2.5f * sx, 11f * sy), Size(5f * sx, 9f * sy), androidx.compose.ui.geometry.CornerRadius(2.4f * sx, 2.4f * sx))
                drawRoundRect(color, Offset(16.5f * sx, 11f * sy), Size(5f * sx, 9f * sy), androidx.compose.ui.geometry.CornerRadius(2.4f * sx, 2.4f * sx))
                return@Canvas
            }
            "speaker" -> {
                val speakerBody = Color.White.copy(alpha = .15f)
                drawRoundRect(
                    speakerBody,
                    Offset(3f * sx, .8f * sy),
                    Size(18f * sx, 22.4f * sy),
                    androidx.compose.ui.geometry.CornerRadius(4.2f * sx, 4.2f * sx),
                )
                drawCircle(color, 3.3f * sx, Offset(12f * sx, 7.2f * sy), style = Stroke(width = 2.2.dp.toPx()))
                drawCircle(speakerBody, 2.1f * sx, Offset(12f * sx, 7.2f * sy))
                drawCircle(color, 5.2f * sx, Offset(12f * sx, 16.2f * sy), style = Stroke(width = 2.6.dp.toPx()))
                drawCircle(speakerBody, 3.2f * sx, Offset(12f * sx, 16.2f * sy))
                return@Canvas
            }
            "tv" -> {
                path.addRoundRect(RoundRect(Rect(2.5f * sx, 3.5f * sy, 21.5f * sx, 17.5f * sy), 2.5f * sx, 2.5f * sy))
                path.moveTo(8f * sx, 21f * sy)
                path.lineTo(16f * sx, 21f * sy)
                path.moveTo(12f * sx, 17.5f * sy)
                path.lineTo(12f * sx, 21f * sy)
            }
            "phone" -> {
                path.addRoundRect(RoundRect(Rect(5f * sx, 1.5f * sy, 19f * sx, 22.5f * sy), 2.8f * sx, 2.8f * sy))
                path.moveTo(9f * sx, 4.3f * sy)
                path.lineTo(15f * sx, 4.3f * sy)
                path.moveTo(11f * sx, 19.5f * sy)
                path.lineTo(13f * sx, 19.5f * sy)
            }
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

private data class QueueDragEntry(
    val key: String,
    val song: Song,
    val section: QueueListSection,
)

private enum class QueueListSection { Queue, AutoPlay }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueSongRow(
    modifier: Modifier,
    song: Song,
    rowKey: String,
    isCurrent: Boolean,
    isAutoplay: Boolean,
    enabled: Boolean,
    canRemove: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onDragHandlePositioned: (Rect) -> Unit,
    onDragHandleDisposed: () -> Unit,
) {
    var rowWidthPx by remember(rowKey) { mutableFloatStateOf(0f) }
    var swipeOffsetPx by remember(rowKey) { mutableFloatStateOf(0f) }
    var swipeAnimationJob by remember(rowKey) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val revealWidthPx = with(density) { 76.dp.toPx() }
    val latestSwipeEnabled = rememberUpdatedState(enabled && canRemove)
    val dragState = rememberDraggableState { delta ->
        swipeAnimationJob?.cancel()
        swipeOffsetPx = (swipeOffsetPx + delta).coerceIn(-rowWidthPx, rowWidthPx)
    }

    fun settleSwipe(target: Float) {
        swipeAnimationJob?.cancel()
        val start = swipeOffsetPx
        swipeAnimationJob = scope.launch {
            animate(start, target, animationSpec = tween(170, easing = WavvEaseInOut)) { value, _ ->
                swipeOffsetPx = value
            }
        }
    }

    LaunchedEffect(canRemove, enabled) {
        if (!canRemove || !enabled) settleSwipe(0f)
    }

    Box(
        modifier
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .clip(RoundedCornerShape(14.dp)),
    ) {
        if (canRemove && kotlin.math.abs(swipeOffsetPx) > 0.5f) {
            if (swipeOffsetPx > 0f) {
                QueueSwipeDeleteAction(Modifier.align(Alignment.CenterStart).padding(start = 10.dp), enabled, onRemove)
            } else {
                QueueSwipeDeleteAction(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), enabled, onRemove)
            }
        }
        Row(
            Modifier.fillMaxSize()
                .graphicsLayer { translationX = swipeOffsetPx }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled && canRemove,
                    onDragStopped = { _ ->
                        val removeThreshold = max(revealWidthPx * 2f, rowWidthPx * .38f)
                        val draggedFarEnough = kotlin.math.abs(swipeOffsetPx) >= removeThreshold
                        if (!latestSwipeEnabled.value) {
                            settleSwipe(0f)
                        } else if (draggedFarEnough) {
                            onRemove()
                        } else if (kotlin.math.abs(swipeOffsetPx) >= revealWidthPx * .72f) {
                            settleSwipe(if (swipeOffsetPx > 0f) revealWidthPx else -revealWidthPx)
                        } else {
                            settleSwipe(0f)
                        }
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongPress,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AlbumArt(song, Modifier.size(56.dp), song.title, 10.dp)
                TrackText(
                    song,
                    Modifier.weight(1f),
                    titleSize = 16.sp,
                    subtitleSize = 13.sp,
                    titleColor = if (isCurrent) WavvPink else Color.White,
                    subtitleColor = if (isCurrent) WavvPink.copy(alpha = .72f) else Color.White.copy(.58f),
                )
            }
            if (isCurrent) Box(Modifier.size(6.dp).clip(CircleShape).background(WavvPink))
            Box(
                Modifier.size(40.dp).onGloballyPositioned { onDragHandlePositioned(it.boundsInRoot()) },
                contentAlignment = Alignment.Center,
            ) {
                DisposableEffect(rowKey) { onDispose(onDragHandleDisposed) }
                Image(
                    androidx.compose.ui.res.painterResource(R.drawable.queue_drag_handle),
                    if (isAutoplay) "Drag to reorder ${song.title} in AutoPlay" else "Drag to reorder ${song.title}",
                    Modifier.size(36.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(.48f)),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
            if (isAutoplay) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { onDismissMenu(); onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { onDismissMenu(); onAddToQueue() })
            } else {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { onDismissMenu(); onPlayNext() })
                if (canRemove) {
                    DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { onDismissMenu(); onRemove() })
                }
            }
            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { onDismissMenu(); onAddToPlaylist() })
            DropdownMenuItem(text = { Text("Go to album") }, onClick = { onDismissMenu(); onNavigateToAlbum() })
            DropdownMenuItem(text = { Text("Go to artist") }, onClick = { onDismissMenu(); onNavigateToArtist() })
        }
    }
}

@Composable
private fun QueueSwipeDeleteAction(modifier: Modifier, enabled: Boolean, onRemove: () -> Unit) {
    Box(
        modifier.size(48.dp)
            .clip(CircleShape)
            .background(WavvPink)
            .clickable(enabled = enabled, onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Close, contentDescription = "Delete song", tint = Color.White, modifier = Modifier.size(23.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePanel(
    song: Song,
    queue: List<Song>,
    autoplaySongs: List<Song>,
    autoplayEnabled: Boolean,
    playingFrom: String?,
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
    onMoveAutoplayItem: (Int, Int) -> Unit,
    onSetAutoplayEnabled: (Boolean) -> Unit,
    onPlayAutoplaySong: (Song) -> Unit,
    onPlayNextFromAutoplay: (Song) -> Unit,
    onAddAutoplayToQueue: (Song) -> Unit,
    onRemoveAutoplaySong: (Song) -> Unit,
    onAddSongToPlaylist: (Long, List<Long>, (Boolean) -> Unit) -> Unit,
    onCreatePlaylist: (Long) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onPlaySong: (Song) -> Unit,
) {
    var menuKey by remember { mutableStateOf<String?>(null) }
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    var currentSongMenuExpanded by remember { mutableStateOf(false) }
    var draggingSongKey by remember { mutableStateOf<String?>(null) }
    var draggingSongIndex by remember { mutableIntStateOf(-1) }
    var draggingSection by remember { mutableStateOf(QueueListSection.Queue) }
    val draggingPointerYState = remember { mutableFloatStateOf(Float.NaN) }
    var draggingPointerY by draggingPointerYState
    var draggingTouchOffsetY by remember { mutableFloatStateOf(0f) }
    var queueListTopInRootPx by remember { mutableFloatStateOf(0f) }
    var localQueueOrder by remember { mutableStateOf<List<QueueDragEntry>?>(null) }
    var localAutoplayOrder by remember { mutableStateOf<List<QueueDragEntry>?>(null) }
    var pendingQueueIds by remember { mutableStateOf<List<Long>?>(null) }
    var pendingAutoplayIds by remember { mutableStateOf<List<Long>?>(null) }
    var settlingSongKey by remember { mutableStateOf<String?>(null) }
    var settlingOverlayStartTopPx by remember { mutableFloatStateOf(0f) }
    var settlingOverlayTargetTopPx by remember { mutableFloatStateOf(0f) }
    var settlingTargetReady by remember { mutableStateOf(false) }
    val queueListState = rememberLazyListState()
    val density = LocalDensity.current
    val rowStepPx = with(density) { 84.dp.toPx() }
    val edgeZonePx = with(density) { 84.dp.toPx() }
    val maxAutoScrollPxPerSecond = with(density) { 1_100.dp.toPx() }
    val dragHandleBounds = remember { mutableMapOf<String, Rect>() }
    val queueRootCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val latestQueue = rememberUpdatedState(queue)
    val latestAutoplaySongs = rememberUpdatedState(autoplaySongs)
    val latestMoveQueueItem = rememberUpdatedState(onMoveQueueItem)
    val externalQueueEntries = remember(queue) {
        val occurrences = mutableMapOf<Long, Int>()
        queue.map { queuedSong ->
            val occurrence = occurrences.getOrDefault(queuedSong.id, 0)
            occurrences[queuedSong.id] = occurrence + 1
            QueueDragEntry("queue:${queuedSong.id}:$occurrence", queuedSong, QueueListSection.Queue)
        }
    }
    val externalAutoplayEntries = remember(autoplaySongs) {
        val occurrences = mutableMapOf<Long, Int>()
        autoplaySongs.map { suggestedSong ->
            val occurrence = occurrences.getOrDefault(suggestedSong.id, 0)
            occurrences[suggestedSong.id] = occurrence + 1
            QueueDragEntry("autoplay:${suggestedSong.id}:$occurrence", suggestedSong, QueueListSection.AutoPlay)
        }
    }
    val displayedQueueEntries = localQueueOrder ?: externalQueueEntries
    val displayedAutoplayEntries = localAutoplayOrder ?: externalAutoplayEntries
    val queueControlsEnabled = draggingSongKey == null && settlingSongKey == null &&
        pendingQueueIds == null && pendingAutoplayIds == null
    val rowActionsEnabled = queueControlsEnabled && !queueListState.isScrollInProgress
    val settleProgress by animateFloatAsState(
        targetValue = if (settlingSongKey != null && settlingTargetReady) 1f else 0f,
        animationSpec = tween(230, easing = WavvScreenEasing),
        label = "queue row drop settle",
    )
    val captureState = remember { AtomicBoolean(false) }
    LaunchedEffect(queue, pendingQueueIds, draggingSongKey) {
        val expectedIds = pendingQueueIds
        if (expectedIds != null && queue.map { it.id } == expectedIds) {
            // Keep the locally animated order until Media3 reports the committed move.
            localQueueOrder = null
            pendingQueueIds = null
        } else if (expectedIds == null && draggingSongKey == null) {
            localQueueOrder = null
        }
    }
    LaunchedEffect(autoplaySongs, pendingAutoplayIds, draggingSongKey) {
        val expectedIds = pendingAutoplayIds
        if (expectedIds != null && autoplaySongs.map { it.id } == expectedIds) {
            localAutoplayOrder = null
            pendingAutoplayIds = null
        } else if (expectedIds == null && draggingSongKey == null) {
            localAutoplayOrder = null
        }
    }
    fun moveDraggedRowLocally() {
        val key = draggingSongKey ?: return
        val currentRows = when (draggingSection) {
            QueueListSection.Queue -> localQueueOrder ?: displayedQueueEntries
            QueueListSection.AutoPlay -> localAutoplayOrder ?: displayedAutoplayEntries
        }
        val currentIndex = currentRows.indexOfFirst { it.key == key }
        if (currentIndex !in currentRows.indices) return
        val pointerY = draggingPointerY
        if (pointerY.isNaN()) return
        val overlayCenterY = pointerY - draggingTouchOffsetY + rowStepPx / 2f
        val visibleItems = queueListState.layoutInfo.visibleItemsInfo
        val nextEntry = currentRows.getOrNull(currentIndex + 1)
        val nextCenterY = nextEntry?.let { next ->
            visibleItems.firstOrNull { it.key == next.key }?.let { it.offset + it.size / 2f }
        }
        val previousEntry = currentRows.getOrNull(currentIndex - 1)
        val previousCenterY = previousEntry?.let { previous ->
            visibleItems.firstOrNull { it.key == previous.key }?.let { it.offset + it.size / 2f }
        }
        val targetIndex = when {
            nextCenterY != null && overlayCenterY > nextCenterY -> currentIndex + 1
            previousCenterY != null && overlayCenterY < previousCenterY -> currentIndex - 1
            else -> currentIndex
        }
        if (targetIndex != currentIndex) {
            val reorderedRows = currentRows.toMutableList()
            val draggedRow = reorderedRows.removeAt(currentIndex)
            reorderedRows.add(targetIndex, draggedRow)
            when (draggingSection) {
                QueueListSection.Queue -> localQueueOrder = reorderedRows
                QueueListSection.AutoPlay -> localAutoplayOrder = reorderedRows
            }
        }
    }
    fun finishQueueDrag() {
        val key = draggingSongKey ?: return
        val finalRows = when (draggingSection) {
            QueueListSection.Queue -> localQueueOrder ?: displayedQueueEntries
            QueueListSection.AutoPlay -> localAutoplayOrder ?: displayedAutoplayEntries
        }
        val destinationIndex = finalRows.indexOfFirst { it.key == key }
        val sourceIndex = draggingSongIndex
        settlingOverlayStartTopPx = draggingPointerY - draggingTouchOffsetY
        settlingOverlayTargetTopPx = settlingOverlayStartTopPx
        settlingTargetReady = false
        settlingSongKey = key
        draggingSongKey = null
        draggingSongIndex = -1
        draggingPointerY = Float.NaN
        if (sourceIndex >= 0 && destinationIndex >= 0 && sourceIndex != destinationIndex) {
            when (draggingSection) {
                QueueListSection.Queue -> {
                    // One Media3 queue update at drop; frame-by-frame reorder stays Compose-local.
                    pendingQueueIds = finalRows.map { it.song.id }
                    latestMoveQueueItem.value(sourceIndex, destinationIndex)
                }
                QueueListSection.AutoPlay -> {
                    pendingAutoplayIds = finalRows.map { it.song.id }
                    onMoveAutoplayItem(sourceIndex, destinationIndex)
                }
            }
        } else {
            when (draggingSection) {
                QueueListSection.Queue -> localQueueOrder = null
                QueueListSection.AutoPlay -> localAutoplayOrder = null
            }
        }
    }
    val latestBeginQueueDrag = rememberUpdatedState<(String, Float) -> Boolean> { rowKey, pointerRootY ->
        val section = if (rowKey.startsWith("autoplay:")) QueueListSection.AutoPlay else QueueListSection.Queue
        val currentRows = when (section) {
            QueueListSection.Queue -> latestQueue.value
            QueueListSection.AutoPlay -> latestAutoplaySongs.value
        }
        var sourceIndex = -1
        val occurrences = mutableMapOf<Long, Int>()
        currentRows.forEachIndexed { queueIndex, queuedSong ->
            val occurrence = occurrences.getOrDefault(queuedSong.id, 0)
            occurrences[queuedSong.id] = occurrence + 1
            val prefix = if (section == QueueListSection.AutoPlay) "autoplay:" else "queue:"
            if ("$prefix${queuedSong.id}:$occurrence" == rowKey) sourceIndex = queueIndex
        }
        val itemInfo = queueListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rowKey }
        if (sourceIndex >= 0 && itemInfo != null && !queueListState.isScrollInProgress &&
            pendingQueueIds == null && pendingAutoplayIds == null && draggingSongKey == null && settlingSongKey == null
        ) {
            settlingSongKey = null
            settlingTargetReady = false
            menuKey = null
            pendingAutoplayIds = null
            draggingSection = section
            when (section) {
                QueueListSection.Queue -> localQueueOrder = displayedQueueEntries
                QueueListSection.AutoPlay -> localAutoplayOrder = displayedAutoplayEntries
            }
            draggingSongKey = rowKey
            draggingSongIndex = sourceIndex
            draggingTouchOffsetY = (pointerRootY - queueListTopInRootPx - itemInfo.offset)
                .coerceIn(0f, rowStepPx)
            draggingPointerY = pointerRootY - queueListTopInRootPx
            true
        } else {
            false
        }
    }
    val latestUpdateQueueDragPointer = rememberUpdatedState<(Float) -> Unit> { pointerRootY ->
        if (draggingSongKey != null) {
            draggingPointerY = pointerRootY - queueListTopInRootPx
            moveDraggedRowLocally()
        }
    }
    val latestFinishQueueDrag = rememberUpdatedState { finishQueueDrag() }
    LaunchedEffect(settlingSongKey) {
        val key = settlingSongKey ?: return@LaunchedEffect
        settlingTargetReady = false
        withFrameNanos { }
        settlingOverlayTargetTopPx = queueListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key }
            ?.offset
            ?.toFloat()
            ?: settlingOverlayStartTopPx
        settlingTargetReady = true
        delay(240)
        if (settlingSongKey == key) {
            settlingSongKey = null
            settlingTargetReady = false
        }
    }
    LaunchedEffect(draggingSongKey) {
        var previousFrameNanos = 0L
        while (draggingSongKey != null) {
            val frameNanos = withFrameNanos { it }
            val frameSeconds = if (previousFrameNanos == 0L) 1f / 60f
                else ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, .05f)
            previousFrameNanos = frameNanos
            val layout = queueListState.layoutInfo
            val pointerY = draggingPointerY
            val viewportStart = layout.viewportStartOffset.toFloat()
            val viewportEnd = layout.viewportEndOffset.toFloat()
            val edgeDirection: Float
            val edgeProgress: Float
            when {
                pointerY.isNaN() -> {
                    edgeDirection = 0f
                    edgeProgress = 0f
                }
                pointerY < viewportStart + edgeZonePx -> {
                    edgeDirection = -1f
                    edgeProgress = ((viewportStart + edgeZonePx - pointerY) / edgeZonePx).coerceIn(0f, 1f)
                }
                pointerY > viewportEnd - edgeZonePx -> {
                    edgeDirection = 1f
                    edgeProgress = ((pointerY - (viewportEnd - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                }
                else -> {
                    edgeDirection = 0f
                    edgeProgress = 0f
                }
            }
            if (edgeDirection != 0f) {
                val easedEdgeProgress = edgeProgress * edgeProgress
                val scrollDelta = edgeDirection * maxAutoScrollPxPerSecond * easedEdgeProgress * frameSeconds
                queueListState.scrollBy(scrollDelta)
            }
            // Re-evaluate against the currently laid-out neighbors each frame. The
            // dragged artwork stays in a separate overlay while the list slot moves.
            moveDraggedRowLocally()
        }
    }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { queueRootCoordinates[0] = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val rootCoordinates = queueRootCoordinates[0] ?: return@awaitEachGesture
                    val downInRoot = rootCoordinates.localToRoot(down.position)
                    val rowKey = dragHandleBounds.entries.firstOrNull { (_, bounds) ->
                        bounds.contains(downInRoot)
                    }?.key ?: return@awaitEachGesture
                    var dragStarted = false
                    var dragFinished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (dragStarted) {
                                    latestFinishQueueDrag.value()
                                    dragFinished = true
                                    dragStarted = false
                                }
                                break
                            }
                            val pointerInRoot = rootCoordinates.localToRoot(change.position)
                            if (!dragStarted) {
                                if (change.isConsumed) break
                                val delta = pointerInRoot - downInRoot
                                if (kotlin.math.abs(delta.y) > viewConfiguration.touchSlop &&
                                    kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x)
                                ) {
                                    // Claim only handle-origin vertical drags, before LazyColumn
                                    // can turn the same gesture into a scroll and cancel it.
                                    dragStarted = latestBeginQueueDrag.value(rowKey, downInRoot.y)
                                    if (dragStarted) {
                                        change.consume()
                                        latestUpdateQueueDragPointer.value(pointerInRoot.y)
                                    } else {
                                        break
                                    }
                                }
                            } else {
                                change.consume()
                                latestUpdateQueueDragPointer.value(pointerInRoot.y)
                            }
                        }
                    } finally {
                        if (dragStarted && !dragFinished) latestFinishQueueDrag.value()
                    }
                }
            },
    ) {
        androidx.compose.runtime.key(song.id) {
            FrostedBackdrop(
                captureState = captureState,
                blurRadiusDp = 38f,
                tint = Color(0xA9101117),
                modifier = Modifier.matchParentSize(),
                refreshEnabled = true,
                freezeAfterFirstCapture = true,
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlbumArt(song, Modifier.size(52.dp), song.title, 10.dp)
            TrackText(song, Modifier.weight(1f), 15.sp, titleColor = WavvPink, subtitleColor = WavvPink.copy(alpha = .68f))
            Box(Modifier.size(36.dp).clickable(onClick = onLike), contentAlignment = Alignment.Center) {
                FigmaFavouriteIcon(liked, 29.dp)
            }
            Box {
                Box(Modifier.size(36.dp).clickable { currentSongMenuExpanded = true }, contentAlignment = Alignment.Center) {
                    FigmaMoreIcon(28.dp)
                }
                DropdownMenu(expanded = currentSongMenuExpanded, onDismissRequest = { currentSongMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Play next") }, onClick = { currentSongMenuExpanded = false; onPlayNext(song) })
                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { currentSongMenuExpanded = false; playlistSong = song })
                    DropdownMenuItem(text = { Text("Go to album") }, onClick = { currentSongMenuExpanded = false; onNavigateToAlbum(song) })
                    DropdownMenuItem(text = { Text("Go to artist") }, onClick = { currentSongMenuExpanded = false; onNavigateToArtist(song) })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Queue", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            if (!playingFrom.isNullOrBlank()) {
                Text(
                    "Playing From $playingFrom",
                    Modifier.padding(start = 10.dp).weight(1f),
                    color = Color.White.copy(.42f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else Spacer(Modifier.weight(1f))
        }
        LazyColumn(
            Modifier.weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .onGloballyPositioned { coordinates ->
                    queueListTopInRootPx = coordinates.boundsInRoot().top
                },
            state = queueListState,
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(displayedQueueEntries, key = { _, entry -> entry.key }) { index, entry ->
                val item = entry.song
                val rowKey = entry.key
                val isCurrent = item.id == song.id
                val isBeingDragged = rowKey == draggingSongKey
                val isSettling = rowKey == settlingSongKey
                val rowAlpha = when {
                    isBeingDragged -> 0f
                    isSettling -> settleProgress
                    else -> 1f
                }
                val rowScale = if (isSettling) .97f + .03f * settleProgress else 1f
                QueueSongRow(
                    Modifier.animateItem(
                        placementSpec = if (isBeingDragged) androidx.compose.animation.core.snap()
                            else androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessHigh,
                            ),
                    )
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSettling) WavvPink.copy(alpha = .08f * (1f - settleProgress))
                            else Color.Transparent,
                        )
                        .graphicsLayer {
                            alpha = rowAlpha
                            scaleX = rowScale
                            scaleY = rowScale
                        },
                    song = item,
                    rowKey = rowKey,
                    isCurrent = isCurrent,
                    isAutoplay = false,
                    enabled = rowActionsEnabled,
                    canRemove = !isCurrent,
                    menuExpanded = menuKey == rowKey,
                    onClick = { if (rowActionsEnabled) onPlaySong(item) },
                    onLongPress = { if (rowActionsEnabled) menuKey = rowKey },
                    onDismissMenu = { menuKey = null },
                    onRemove = { onRemoveQueueItem(index) },
                    onPlayNext = { onPlayNext(item) },
                    onAddToQueue = {},
                    onAddToPlaylist = { playlistSong = item },
                    onNavigateToAlbum = { onNavigateToAlbum(item) },
                    onNavigateToArtist = { onNavigateToArtist(item) },
                    onDragHandlePositioned = { dragHandleBounds[rowKey] = it },
                    onDragHandleDisposed = { dragHandleBounds.remove(rowKey) },
                )
            }
            item(key = "autoplay-section-header") {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(.13f)))
                    Row(
                        Modifier.fillMaxWidth().padding(top = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AutoPlay", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Wavv recommends music based on your listening",
                                Modifier.padding(top = 2.dp),
                                color = Color.White.copy(.46f),
                                fontSize = 11.sp,
                            )
                        }
                        Switch(
                            checked = autoplayEnabled,
                            onCheckedChange = onSetAutoplayEnabled,
                            enabled = queueControlsEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = WavvPink.copy(alpha = .78f),
                                uncheckedThumbColor = Color.White.copy(.72f),
                                uncheckedTrackColor = Color.White.copy(.15f),
                                uncheckedBorderColor = Color.White.copy(.16f),
                            ),
                        )
                    }
                }
            }
            if (!autoplayEnabled) {
                item(key = "autoplay-off") {
                    Text(
                        "AutoPlay is off",
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = Color.White.copy(.42f),
                        fontSize = 12.sp,
                    )
                }
            } else if (displayedAutoplayEntries.isEmpty()) {
                item(key = "autoplay-empty") {
                    Text(
                        "No recommendations available yet",
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = Color.White.copy(.42f),
                        fontSize = 12.sp,
                    )
                }
            } else {
                itemsIndexed(displayedAutoplayEntries, key = { _, entry -> entry.key }) { index, entry ->
                    val item = entry.song
                    val rowKey = entry.key
                    val isBeingDragged = rowKey == draggingSongKey
                    val isSettling = rowKey == settlingSongKey
                    val rowAlpha = when {
                        isBeingDragged -> 0f
                        isSettling -> settleProgress
                        else -> 1f
                    }
                    val rowScale = if (isSettling) .97f + .03f * settleProgress else 1f
                    QueueSongRow(
                        Modifier.animateItem(
                            placementSpec = if (isBeingDragged) androidx.compose.animation.core.snap()
                            else androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessHigh,
                            ),
                        )
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .height(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSettling) WavvPink.copy(alpha = .08f * (1f - settleProgress)) else Color.Transparent)
                            .graphicsLayer { alpha = rowAlpha; scaleX = rowScale; scaleY = rowScale },
                        song = item,
                        rowKey = rowKey,
                        isCurrent = false,
                        isAutoplay = true,
                        enabled = autoplayEnabled && rowActionsEnabled,
                        canRemove = true,
                        menuExpanded = menuKey == rowKey,
                        onClick = { if (autoplayEnabled && rowActionsEnabled) onPlayAutoplaySong(item) },
                        onLongPress = { if (autoplayEnabled && rowActionsEnabled) menuKey = rowKey },
                        onDismissMenu = { menuKey = null },
                        onRemove = { onRemoveAutoplaySong(item) },
                        onPlayNext = { onPlayNextFromAutoplay(item) },
                        onAddToQueue = { onAddAutoplayToQueue(item) },
                        onAddToPlaylist = { playlistSong = item },
                        onNavigateToAlbum = { onNavigateToAlbum(item) },
                        onNavigateToArtist = { onNavigateToArtist(item) },
                        onDragHandlePositioned = { dragHandleBounds[rowKey] = it },
                        onDragHandleDisposed = { dragHandleBounds.remove(rowKey) },
                    )
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
        val overlayKey = draggingSongKey ?: settlingSongKey
        val overlayRows = if (draggingSection == QueueListSection.AutoPlay) displayedAutoplayEntries else displayedQueueEntries
        val overlayEntry = overlayKey?.let { key -> overlayRows.firstOrNull { it.key == key } }
        if (overlayEntry != null) {
            val overlayAlpha = if (draggingSongKey != null) 1f else 1f - settleProgress
            val overlayDropBlend = if (draggingSongKey != null) 0f
                else 1f - kotlin.math.abs(2f * settleProgress - 1f)
            val overlayScale = 1f + .012f * overlayDropBlend
            Box(
                Modifier.align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset {
                        val pointerY = draggingPointerYState.floatValue
                        val overlayTopPx = if (draggingSongKey != null && !pointerY.isNaN()) {
                            pointerY - draggingTouchOffsetY
                        } else {
                            settlingOverlayStartTopPx +
                                (settlingOverlayTargetTopPx - settlingOverlayStartTopPx) * settleProgress
                        }
                        IntOffset(0, (queueListTopInRootPx + overlayTopPx).roundToInt())
                    }
                    .zIndex(2f)
                    .graphicsLayer {
                        alpha = overlayAlpha
                        shadowElevation = 8.dp.toPx() * overlayAlpha
                        scaleX = overlayScale
                        scaleY = overlayScale
                    }
                    .height(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = .07f + .11f * overlayDropBlend),
                        RoundedCornerShape(16.dp),
                    ),
            ) {
                FrostedBackdrop(
                    captureState = captureState,
                    blurRadiusDp = 24f,
                    tint = Color(0x99151920),
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)),
                    // Reuse the queue backdrop snapshot; never recapture the full root per drag frame.
                    refreshEnabled = false,
                )
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AlbumArt(overlayEntry.song, Modifier.size(56.dp), overlayEntry.song.title, 10.dp)
                    val overlayIsCurrent = overlayEntry.song.id == song.id
                    TrackText(
                        overlayEntry.song,
                        Modifier.weight(1f),
                        titleSize = 16.sp,
                        subtitleSize = 13.sp,
                        titleColor = if (overlayIsCurrent) WavvPink else Color.White,
                        subtitleColor = if (overlayIsCurrent) WavvPink.copy(alpha = .72f) else Color.White.copy(.58f),
                    )
                    if (overlayIsCurrent) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(WavvPink))
                    }
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Image(
                            androidx.compose.ui.res.painterResource(R.drawable.queue_drag_handle),
                            null,
                            Modifier.size(36.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(.42f)),
                        )
                    }
                }
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
                                onClick = {
                                    onAddSongToPlaylist(playlist.id, listOf(selectedSong.id)) { success ->
                                        if (success) playlistSong = null
                                    }
                                },
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
private fun LyricsPanel(
    song: Song,
    progressFlow: StateFlow<PlaybackProgress>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    lyricOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    importedLyrics: List<LyricLine>?,
    onImportedLyrics: (List<LyricLine>) -> Unit,
    lyricsArtworkFlightActive: Boolean,
    onArtworkFrameChange: (PlayerArtworkFrame) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val importScope = rememberCoroutineScope()
    val embeddedLyrics by produceState<List<LyricLine>?>(initialValue = null, song.uri) {
        value = null
        value = try {
            loadEmbeddedLyrics(context.applicationContext, song.uri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }
    val sourceLyrics = importedLyrics ?: embeddedLyrics
    val lyricLines = remember(sourceLyrics, lyricOffsetMs) {
        sourceLyrics?.map { line ->
            line.copy(
                timeMs = line.timeMs?.let { (it + lyricOffsetMs).coerceAtLeast(0L) },
                words = line.words.map { word ->
                    word.copy(timeMs = word.timeMs?.let { (it + lyricOffsetMs).coerceAtLeast(0L) })
                },
            )
        }
    }
    val scrubPositionMs = remember(song.id) { mutableStateOf<Long?>(null) }
    var showOptions by remember(song.id) { mutableStateOf(false) }
    var showOffsetDialog by remember(song.id) { mutableStateOf(false) }
    var importError by remember(song.id) { mutableStateOf(false) }
    var isImporting by remember(song.id) { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importScope.launch {
                isImporting = true
                val loaded = runCatching { loadLyricsDocument(context.applicationContext, uri) }.getOrDefault(emptyList())
                if (loaded.isNotEmpty()) {
                    onImportedLyrics(loaded)
                    onOffsetChange(0L)
                    importError = false
                } else {
                    importError = true
                }
                isImporting = false
            }
        }
    }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            // The lyrics page is a full-screen surface, including transparent space around
            // its controls; blank-area taps must not reach Now Playing underneath.
            .clickable(interactionSource = null, indication = null, onClick = {}),
    ) {
        // Match the output picker: blurred cover art under the same deep graphite veil.
        AlbumArt(song, Modifier.fillMaxSize().scale(1.18f).blur(34.dp), null, 0.dp)
        Box(Modifier.fillMaxSize().background(Color(0xE8171717)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(.06f),
                    .58f to Color.Black.copy(.20f),
                    1f to Color.Black.copy(.58f),
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val thumbnailShape = RoundedCornerShape(12.dp)
                AlbumArt(
                    song,
                    Modifier.size(56.dp).shadow(8.dp, thumbnailShape).clip(thumbnailShape)
                        .onGloballyPositioned { coordinates ->
                            onArtworkFrameChange(
                                PlayerArtworkFrame(
                                    bounds = coordinates.boundsInRoot(),
                                    cornerRadiusPx = with(density) { 12.dp.toPx() },
                                    shadowElevationPx = with(density) { 8.dp.toPx() },
                                ),
                            )
                        }
                        .alpha(if (lyricsArtworkFlightActive) 0f else 1f),
                    song.title,
                    12.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("LYRICS", color = Color.White.copy(.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(
                        song.title,
                        Modifier.fillMaxWidth().songMetadataMarquee(),
                        color = Color(0xFFF5F4F2),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        song.artist,
                        Modifier.fillMaxWidth().songMetadataMarquee(),
                        color = Color.White.copy(.65f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(.08f))) {
                    Icon(Icons.Default.Close, contentDescription = "Return to Now Playing", tint = Color.White.copy(.9f), modifier = Modifier.size(20.dp))
                }
            }

            LyricsScrollArea(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                songId = song.id,
                songDurationMs = song.durationMs,
                lyricLines = lyricLines,
                progressFlow = progressFlow,
                isPlaying = isPlaying,
                scrubPositionMs = scrubPositionMs,
                onSeek = onSeek,
                onImport = { filePicker.launch(arrayOf("text/*", "application/octet-stream", "application/x-subrip")) },
                importError = importError,
            )

            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showOptions = !showOptions },
                            modifier = Modifier.size(46.dp).clip(CircleShape)
                                .background(Color.White.copy(if (showOptions) .16f else .10f)),
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Lyrics options", tint = Color.White.copy(.87f), modifier = Modifier.size(24.dp))
                        }
                        if (isImporting) {
                            Text("Reading lyrics…", Modifier.padding(start = 8.dp), color = Color.White.copy(.55f), fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = onToggle,
                            modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(.12f)),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFFF5F4F2),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    LyricsOptionsMenu(
                        song = song,
                        visible = showOptions,
                        onImport = {
                            showOptions = false
                            filePicker.launch(arrayOf("text/*", "application/octet-stream", "application/x-subrip"))
                        },
                        onAdjustOffset = {
                            showOptions = false
                            showOffsetDialog = true
                        },
                        onDismiss = { showOptions = false },
                    )
                }
                Spacer(Modifier.height(2.dp))
                FullPlayerTimeline(song, progressFlow, onSeek) { position -> scrubPositionMs.value = position }
            }
        }
    }
    if (showOffsetDialog) {
        LyricsOffsetDialog(
            offsetMs = lyricOffsetMs,
            onOffsetChange = onOffsetChange,
            onDismiss = { showOffsetDialog = false },
        )
    }
}

@Composable
private fun LyricsScrollArea(
    modifier: Modifier,
    songId: Long,
    songDurationMs: Long,
    lyricLines: List<LyricLine>?,
    progressFlow: StateFlow<PlaybackProgress>,
    isPlaying: Boolean,
    scrubPositionMs: State<Long?>,
    onSeek: (Long) -> Unit,
    onImport: () -> Unit,
    importError: Boolean,
) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    val durationMs = max(progress.durationMs, songDurationMs).coerceAtLeast(0L)
    val scrubPosition by scrubPositionMs
    val currentPositionMs = scrubPosition ?: progress.positionMs
    val lyricsState = rememberLazyListState()
    val lyricsTopInsetPx = with(LocalDensity.current) { 78.dp.roundToPx() }
    val lyricItemPlacementSpec = remember {
        tween<IntOffset>(durationMillis = 820, easing = WavvMotionEasing)
    }
    var lastObservedPositionMs by remember(songId) { mutableStateOf(currentPositionMs) }
    var playbackReachedEnd by remember(songId) { mutableStateOf(false) }
    val activeLyricIndex = remember(lyricLines, currentPositionMs) {
        findActiveLyricIndex(lyricLines.orEmpty(), currentPositionMs)
    }
    val activeLyricListIndex = if (activeLyricIndex >= 0) activeLyricIndex * 2 + 1 else -1
    val activeLyricTime = lyricLines?.getOrNull(activeLyricIndex)?.timeMs
    val indicesByTime = remember(lyricLines) {
        lyricLines.orEmpty().mapIndexedNotNull { index, line -> line.timeMs?.let { it to index } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, indices) -> indices.toSet() }
    }
    val activeLyricIndices = indicesByTime[activeLyricTime].orEmpty()
    val isScrubbing = scrubPosition != null
    val hasSynchronizedLyrics = remember(lyricLines) {
        hasUsableLyricsTimeline(lyricLines.orEmpty())
    }
    val waitingWindow = lyricsWaitingWindow(
        lines = lyricLines.orEmpty(),
        activeLyricIndex = activeLyricIndex,
        durationMs = durationMs,
        hasUsableTimeline = hasSynchronizedLyrics,
    )
    val waitingProgress = waitingWindow?.progressAt(currentPositionMs) ?: 0f
    val isLyricsWaiting = waitingWindow?.contains(currentPositionMs) == true

    LaunchedEffect(songId, currentPositionMs) {
        val previousPositionMs = lastObservedPositionMs
        val returnedToStart = currentPositionMs <= 1_500L &&
            (currentPositionMs == 0L && previousPositionMs > 0L || previousPositionMs - currentPositionMs >= 1_500L)
        lastObservedPositionMs = currentPositionMs
        if (returnedToStart && !lyricLines.isNullOrEmpty()) {
            // Item zero is the top of the lyric timeline, including tracks whose first
            // timed line starts after 0:00 and therefore has no active line at restart.
            lyricsState.scrollToItem(0)
        }
    }

    LaunchedEffect(isPlaying, progress.positionMs, durationMs, isScrubbing) {
        val endThresholdMs = (durationMs - 1_000L).coerceAtLeast(0L)
        when {
            !isPlaying && durationMs > 0L && progress.positionMs >= endThresholdMs -> playbackReachedEnd = true
            !isPlaying && (durationMs <= 0L || progress.positionMs < endThresholdMs) -> playbackReachedEnd = false
            isPlaying && playbackReachedEnd && !isScrubbing && progress.positionMs <= 1_500L -> {
                // Return to the top of the timeline, even when the first lyric is later.
                if (!lyricLines.isNullOrEmpty()) lyricsState.scrollToItem(0)
                playbackReachedEnd = false
            }
        }
    }

    LaunchedEffect(activeLyricTime, activeLyricIndex, isScrubbing) {
        if (activeLyricIndex >= 0) {
            // During scrubbing, jump directly so a stream of seek updates does not keep
            // cancelling and restarting list animations. Resume smooth following on release.
            if (isScrubbing) {
                lyricsState.scrollToItem(activeLyricListIndex)
            } else {
                val target = lyricsState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeLyricListIndex }
                if (target == null) {
                    lyricsState.animateScrollToItem(activeLyricListIndex)
                } else {
                    // For the usual next-line transition, animate the measured distance
                    // with a long ease-out so the lyric drifts into place instead of snapping.
                    val distance = target.offset - lyricsTopInsetPx
                    if (abs(distance) > 1) {
                        lyricsState.animateScrollBy(
                            value = distance.toFloat(),
                            animationSpec = tween(durationMillis = 820, easing = WavvMotionEasing),
                        )
                    }
                }
            }
        }
    }

    Box(modifier) {
        when {
            lyricLines == null -> LyricsLoadingState()
            lyricLines.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(.35f), modifier = Modifier.size(40.dp))
                Text(
                    "No lyrics loaded yet",
                    Modifier.padding(top = 14.dp),
                    color = Color.White.copy(.92f),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    "Import a timed .lrc file or a plain text lyric sheet.",
                    Modifier.padding(top = 7.dp),
                    color = Color.White.copy(.58f),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = onImport, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Import lyrics", color = Color(0xFFE7E5E2), fontWeight = FontWeight.SemiBold)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                .16f to Color.Black,
                                .84f to Color.Black,
                                1f to Color.Transparent,
                                startY = 0f,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                state = lyricsState,
                contentPadding = PaddingValues(top = 78.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                val displayLyrics = lyricLines.orEmpty()
                items(
                    count = 1 + displayLyrics.size * 2,
                    key = { listIndex ->
                        when {
                            listIndex == 0 -> "lyrics-intro-wait"
                            listIndex % 2 == 1 -> {
                                val lineIndex = (listIndex - 1) / 2
                                "${displayLyrics[lineIndex].timeMs ?: "plain"}-$lineIndex"
                            }
                            else -> "lyrics-wait-${listIndex / 2 - 1}"
                        }
                    },
                ) { index ->
                    when {
                        index == 0 -> LyricsWaitingSlot(
                            modifier = Modifier.animateItem(placementSpec = lyricItemPlacementSpec),
                            visible = isLyricsWaiting && activeLyricIndex < 0,
                            progress = waitingProgress,
                        )
                        index % 2 == 1 -> {
                            val lineIndex = (index - 1) / 2
                            val line = displayLyrics[lineIndex]
                            LyricsLineContent(
                                modifier = Modifier.animateItem(placementSpec = lyricItemPlacementSpec)
                                    .fillMaxWidth().padding(bottom = 25.dp),
                                line = line,
                                active = !isLyricsWaiting && lineIndex in activeLyricIndices,
                                positionMs = if (lineIndex in activeLyricIndices) currentPositionMs else 0L,
                                onClick = { line.timeMs?.let(onSeek) },
                            )
                        }
                        else -> {
                            val afterLineIndex = index / 2 - 1
                            LyricsWaitingSlot(
                                modifier = Modifier.animateItem(placementSpec = lyricItemPlacementSpec),
                                visible = isLyricsWaiting && afterLineIndex == activeLyricIndex,
                                progress = waitingProgress,
                            )
                        }
                    }
                }
            }
        }
        if (importError) {
            Text(
                "Couldn’t read that lyrics file. Try an LRC or text file.",
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 12.dp),
                color = Color.White.copy(.72f),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun findActiveLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size
    while (low < high) {
        val middle = (low + high) ushr 1
        val lineTime = lines[middle].timeMs
        if (lineTime != null && lineTime <= positionMs) low = middle + 1 else high = middle
    }
    return low - 1
}

@Composable
private fun LyricsLineContent(
    modifier: Modifier,
    line: LyricLine,
    active: Boolean,
    positionMs: Long,
    onClick: () -> Unit,
) {
    val textAlign = when (line.vocalSide) {
        -1 -> androidx.compose.ui.text.style.TextAlign.Start
        1 -> androidx.compose.ui.text.style.TextAlign.End
        else -> androidx.compose.ui.text.style.TextAlign.Center
    }
    val boxAlignment = when (line.vocalSide) {
        -1 -> Alignment.CenterStart
        1 -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    val textColor by animateColorAsState(
        targetValue = if (active) Color(0xFFF7F5F2) else Color.White.copy(.48f),
        animationSpec = tween(340, easing = WavvMotionEasing),
        label = "lyrics line color",
    )
    val lineScale by animateFloatAsState(
        targetValue = if (active) 1f else .985f,
        animationSpec = tween(360, easing = WavvMotionEasing),
        label = "lyrics line scale",
    )
    val lineAlpha by animateFloatAsState(
        targetValue = if (active) 1f else .62f,
        animationSpec = tween(340, easing = WavvMotionEasing),
        label = "lyrics line alpha",
    )
    val density = LocalDensity.current
    val lineStyle = LocalTextStyle.current.copy(
        shadow = Shadow(
            color = Color(0xFFFFF7E8).copy(alpha = if (active) .10f else 0f),
            blurRadius = with(density) { if (active) 3.dp.toPx() else 0f },
        ),
    )
    val currentWordIndex = if (active) {
        line.words.indexOfLast { it.timeMs?.let { time -> time <= positionMs } == true }
    } else -1
    val annotatedText = remember(line, active, currentWordIndex) {
        if (line.words.isEmpty()) {
            AnnotatedString(line.text)
        } else {
            buildAnnotatedString {
                line.words.forEachIndexed { index, word ->
                    val color = when {
                        !active -> Color.White.copy(.46f)
                        index == currentWordIndex -> Color(0xFFFFFFFF)
                        index < currentWordIndex -> Color.White.copy(.78f)
                        else -> Color.White.copy(.52f)
                    }
                    withStyle(SpanStyle(color = color, fontWeight = if (index == currentWordIndex && active) FontWeight.Bold else FontWeight.SemiBold)) {
                        append(word.text)
                    }
                }
            }
        }
    }
    Box(modifier) {
        Column(
            Modifier.align(boxAlignment).fillMaxWidth(if (line.vocalSide == null) .94f else .88f)
                .animateContentSize(tween(360, easing = WavvMotionEasing))
                .graphicsLayer { scaleX = lineScale; scaleY = lineScale; alpha = lineAlpha }
                .clickable(enabled = line.timeMs != null, onClick = onClick),
            horizontalAlignment = when (line.vocalSide) {
                -1 -> Alignment.Start
                1 -> Alignment.End
                else -> Alignment.CenterHorizontally
            },
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                annotatedText,
                Modifier.fillMaxWidth(),
                color = textColor,
                fontSize = if (active) 28.sp else 22.sp,
                lineHeight = if (active) 36.sp else 30.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                textAlign = textAlign,
                style = lineStyle,
            )
            line.backgroundText?.takeIf(String::isNotBlank)?.let { backing ->
                Text(
                    backing,
                    Modifier.fillMaxWidth(),
                    color = Color.White.copy(if (active) .65f else .36f),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = textAlign,
                )
            }
        }
    }
}

@Composable
private fun LyricsWaitingSlot(visible: Boolean, progress: Float, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(tween(200, easing = WavvMotionEasing)) +
            expandVertically(expandFrom = Alignment.Top, animationSpec = tween(300, easing = WavvMotionEasing)),
        exit = fadeOut(tween(120, easing = WavvMotionEasing)) +
            shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(220, easing = WavvMotionEasing)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            LyricsWaitingIndicator(progress = progress)
        }
    }
}

@Composable
private fun LyricsWaitingIndicator(progress: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val dotProgress by animateFloatAsState(
                targetValue = (progress * 3f - index).coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 240, easing = LinearEasing),
                label = "lyrics wait dot progress $index",
            )
            Box(
                Modifier.size(12.dp)
                    .graphicsLayer { alpha = .34f + .66f * dotProgress }
                    .background(Color(0xFFF5F4F2), CircleShape),
            )
        }
    }
}

@Composable
private fun LyricsLoadingState() {
    val transition = rememberInfiniteTransition(label = "lyrics loading shimmer")
    val shimmer by transition.animateFloat(
        initialValue = .22f,
        targetValue = .62f,
        animationSpec = infiniteRepeatable(tween(900, easing = WavvEaseInOut), RepeatMode.Reverse),
        label = "lyrics loading shimmer alpha",
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) { index ->
            Box(
                Modifier.padding(vertical = 9.dp).fillMaxWidth(if (index == 1) .78f else .56f).height(if (index == 1) 17.dp else 13.dp)
                    .clip(CircleShape).background(Color.White.copy(if (index == 1) shimmer else shimmer * .6f)),
            )
        }
    }
}

@Composable
private fun LyricsOptionsMenu(
    song: Song,
    visible: Boolean,
    onImport: () -> Unit,
    onAdjustOffset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var popupMounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            popupMounted = true
        } else if (popupMounted) {
            delay(220)
            popupMounted = false
        }
    }

    if (popupMounted) {
        // Keep the menu fully above the 3-dot control so the popup never covers its anchor.
        val popupOffset = with(LocalDensity.current) { IntOffset(0, (-58).dp.roundToPx()) }
        Popup(
            alignment = Alignment.BottomStart,
            offset = popupOffset,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(150)) + scaleIn(tween(190, easing = WavvMotionEasing), initialScale = .94f) +
                    slideInVertically(tween(190, easing = WavvMotionEasing)) { it / 7 },
                exit = fadeOut(tween(120)) + scaleOut(tween(150, easing = WavvMotionEasing), targetScale = .96f) +
                    slideOutVertically(tween(150, easing = WavvMotionEasing)) { it / 9 },
            ) {
                val shape = RoundedCornerShape(18.dp)
                Box(Modifier.widthIn(min = 220.dp, max = 260.dp).shadow(20.dp, shape).clip(shape)) {
                    AlbumArt(song, Modifier.matchParentSize().scale(1.18f).blur(26.dp), null, 0.dp)
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(listOf(Color(0xEB121316), Color(0xF2090A0C))),
                        ),
                    )
                    Column(Modifier.padding(vertical = 5.dp)) {
                        LyricsMenuItem(Icons.Default.Folder, "Import lyrics", onClick = onImport)
                        LyricsMenuItem(Icons.Default.Tune, "Lyrics offset", onClick = onAdjustOffset)
                        LyricsMenuItem(Icons.Default.MusicNote, "Lyrics provider", enabled = false, onClick = {})
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(if (enabled) .78f else .32f), modifier = Modifier.size(18.dp))
        Text(label, color = Color.White.copy(if (enabled) .91f else .42f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LyricsOffsetDialog(offsetMs: Long, onOffsetChange: (Long) -> Unit, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val dialogWindow = (LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setDimAmount(.42f)
        if (Build.VERSION.SDK_INT >= 31) dialogWindow?.setBackgroundBlurRadius(with(density) { 24.dp.roundToPx() })
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable { onDismiss() })
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                    .background(Color(0xFF121212))
                    .clickable(interactionSource = null, indication = null, onClick = {})
                    .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 20.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(88.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(.28f)))
                }
                Text(
                    "Lyrics offset",
                    Modifier.padding(top = 34.dp),
                    color = Color(0xFFF5F4F2),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Positive values show lyrics later",
                    Modifier.padding(top = 4.dp),
                    color = Color.White.copy(.56f),
                    fontSize = 17.sp,
                )
                Column(
                    Modifier.fillMaxWidth().padding(top = 28.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White.copy(.075f))
                        .padding(horizontal = 20.dp, vertical = 26.dp),
                ) {
                    Text(
                        String.format(Locale.US, "%+.1f s", offsetMs / 1_000f),
                        Modifier.fillMaxWidth().padding(bottom = 26.dp),
                        color = Color(0xFFF5F4F2),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onOffsetChange((offsetMs - 500L).coerceAtLeast(-30_000L)) },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Text("−", color = Color.White.copy(.88f), fontSize = 32.sp, fontWeight = FontWeight.Medium)
                        }
                        Slider(
                            modifier = Modifier.weight(1f),
                            value = offsetMs.toFloat(),
                            onValueChange = { onOffsetChange(it.roundToInt().toLong()) },
                            valueRange = -30_000f..30_000f,
                            steps = 119,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFF5F4F2),
                                activeTrackColor = Color(0xFFF5F4F2),
                                inactiveTrackColor = Color.White.copy(.24f),
                            ),
                        )
                        IconButton(
                            onClick = { onOffsetChange((offsetMs + 500L).coerceAtMost(30_000L)) },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Text("+", color = Color.White.copy(.88f), fontSize = 30.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                TextButton(
                    onClick = { onOffsetChange(0L) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 46.dp),
                ) {
                    Text("Reset", color = Color.White.copy(.54f), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
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
    var measuredSize by remember(sourceKey) { mutableStateOf(IntSize.Zero) }
    val targetEdge = if (measuredSize.width > 0 && measuredSize.height > 0) albumArtDecodeBucketEdge(measuredSize) else null
    val cacheKey = targetEdge?.let { "$sourceKey|$it" }
    val cachedBitmap = remember(cacheKey) { cacheKey?.let(albumArtCache::get) }
    var retainedBitmap by remember { mutableStateOf(cachedBitmap) }
    var displayedSource by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sourceKey, cacheKey) {
        if (uriString == null && audioUri == null) {
            retainedBitmap = null
            displayedSource = sourceKey
        } else {
            if (targetEdge == null || cacheKey == null) return@LaunchedEffect
            val decoded = cachedBitmap ?: withContext(Dispatchers.IO) {
                decodeAlbumArt(context, uriString, audioUri, cacheKey, targetEdge)
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
    val measuredModifier = modifier.onSizeChanged { measuredSize = it }
    val bitmap = cachedBitmap ?: retainedBitmap
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription, measuredModifier.clip(RoundedCornerShape(radius)), contentScale = ContentScale.Crop)
    } else {
        NoArtwork(measuredModifier, contentDescription, radius)
    }
}

private suspend fun decodeAlbumArt(context: Context, uriString: String?, audioUri: String?, cacheKey: String, targetEdge: Int): Bitmap? {
    val decodeLock = synchronized(albumArtDecodeLocks) {
        albumArtDecodeLocks.getOrPut(cacheKey) { AlbumArtDecodeLock() }.also { it.references++ }
    }
    return try {
        decodeLock.mutex.withLock {
            try {
                albumArtCache.get(cacheKey)?.let { return@withLock it }

                fun sampleSize(width: Int, height: Int): Int {
                    val longestEdge = maxOf(width, height)
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
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    } finally {
        synchronized(albumArtDecodeLocks) {
            if (--decodeLock.references == 0 && albumArtDecodeLocks[cacheKey] === decodeLock) {
                albumArtDecodeLocks.remove(cacheKey)
            }
        }
    }
}

internal suspend fun homeAcrylicChipColor(context: Context, song: Song): Color {
    val targetEdge = 96
    val sourceKey = "${song.albumArtUri.orEmpty()}|${song.uri}"
    val bitmap = decodeAlbumArt(
        context,
        song.albumArtUri,
        song.uri,
        "$sourceKey|acrylic-chip-$targetEdge",
        targetEdge,
    ) ?: return Color(0xFF26385D)

    var red = 0L
    var green = 0L
    var blue = 0L
    var samples = 0
    val stepX = (bitmap.width / 12).coerceAtLeast(1)
    val stepY = (bitmap.height / 12).coerceAtLeast(1)
    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 48) continue
            red += android.graphics.Color.red(pixel)
            green += android.graphics.Color.green(pixel)
            blue += android.graphics.Color.blue(pixel)
            samples++
        }
    }
    if (samples == 0) return Color(0xFF26385D)

    // The home acrylic veil keeps most of the cover darkened; tint the chips with
    // a restrained sample of the same artwork, without capturing or refracting it.
    val imageRed = red.toFloat() / samples / 255f
    val imageGreen = green.toFloat() / samples / 255f
    val imageBlue = blue.toFloat() / samples / 255f
    return Color(
        red = .035f + (imageRed * .42f),
        green = .045f + (imageGreen * .42f),
        blue = .085f + (imageBlue * .48f),
    )
}

internal suspend fun homeThumbnailAccentColor(context: Context, song: Song): Color {
    val targetEdge = 96
    val sourceKey = "${song.albumArtUri.orEmpty()}|${song.uri}"
    val bitmap = decodeAlbumArt(
        context,
        song.albumArtUri,
        song.uri,
        "$sourceKey|carousel-accent-$targetEdge",
        targetEdge,
    ) ?: return homeFallbackAccent(song.id)

    val buckets = Array(24) { FloatArray(4) }
    val hsv = FloatArray(3)
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 48) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val brightness = hsv[2]
            if (saturation < .18f || brightness < .12f) continue
            val hueBucket = (hsv[0] / 15f).toInt().coerceIn(0, 23)
            val brightnessWeight = (1f - kotlin.math.abs(brightness - .58f) / .58f).coerceIn(.15f, 1f)
            val weight = saturation * saturation * brightnessWeight
            val bucket = buckets[hueBucket]
            bucket[0] += android.graphics.Color.red(pixel) * weight
            bucket[1] += android.graphics.Color.green(pixel) * weight
            bucket[2] += android.graphics.Color.blue(pixel) * weight
            bucket[3] += weight
        }
    }
    val dominantAccent = buckets.maxByOrNull { it[3] }?.takeIf { it[3] > 0f }
        ?: return homeFallbackAccent(song.id)
    return Color(
        red = (dominantAccent[0] / dominantAccent[3] / 255f).coerceIn(0f, 1f),
        green = (dominantAccent[1] / dominantAccent[3] / 255f).coerceIn(0f, 1f),
        blue = (dominantAccent[2] / dominantAccent[3] / 255f).coerceIn(0f, 1f),
    )
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
