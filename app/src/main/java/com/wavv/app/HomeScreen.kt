package com.wavv.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.Rect as AndroidRect
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import android.Manifest
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.compose.animation.animateColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouteSelector
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun Modifier.homeAcrylicBackground(hasArtwork: Boolean) = drawWithCache {
    val base = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF060A18),
            .16f to Color(0xFF040713),
            .34f to Color(0xFF010208),
            .72f to Color(0xFF000104),
            .88f to Color(0xFF02040B),
            1f to Color(0xFF050716),
        ),
        endY = size.height,
    )
    val artworkTint = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color(0xB8060A18),
            .16f to Color(0xD9040712),
            .34f to Color(0xF2010207),
            .72f to Color(0xF5000103),
            .88f to Color(0xD902040B),
            1f to Color(0xBD050716),
        ),
        endY = size.height,
    )
    val topBlueHaze = Brush.radialGradient(
        0f to Color(0xFF36548D).copy(alpha = .17f),
        .50f to Color(0xFF203665).copy(alpha = .08f),
        1f to Color.Transparent,
        center = Offset(size.width * .12f, size.height * .04f),
        radius = size.height * .38f,
    )
    val topVioletHaze = Brush.radialGradient(
        0f to Color(0xFF574273).copy(alpha = .12f),
        .55f to Color(0xFF302446).copy(alpha = .06f),
        1f to Color.Transparent,
        center = Offset(size.width * .94f, size.height * .13f),
        radius = size.height * .34f,
    )
    val lowerBlueHaze = Brush.radialGradient(
        0f to Color(0xFF1B315F).copy(alpha = .10f),
        .52f to Color(0xFF101D3D).copy(alpha = .05f),
        1f to Color.Transparent,
        center = Offset(size.width * .10f, size.height * .98f),
        radius = size.height * .34f,
    )
    val lowerVioletHaze = Brush.radialGradient(
        0f to Color(0xFF463467).copy(alpha = .11f),
        .55f to Color(0xFF2B2146).copy(alpha = .05f),
        1f to Color.Transparent,
        center = Offset(size.width * .96f, size.height * .92f),
        radius = size.height * .36f,
    )
    val acrylicSheen = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color(0xFFD8E4FF).copy(alpha = .018f),
            .24f to Color(0xFFAABEFF).copy(alpha = .008f),
            .52f to Color.Transparent,
            1f to Color(0xFFC8B7F4).copy(alpha = .008f),
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height),
    )
    val lowerShade = Brush.verticalGradient(
        0f to Color.Transparent,
        .72f to Color.Transparent,
        1f to Color.Black.copy(alpha = .28f),
        endY = size.height,
    )
    onDrawBehind {
        drawRect(if (hasArtwork) artworkTint else base)
        drawRect(topBlueHaze)
        drawRect(topVioletHaze)
        drawRect(lowerBlueHaze)
        drawRect(lowerVioletHaze)
        drawRect(acrylicSheen)
        drawRect(lowerShade)
    }
}

internal fun primaryHomeShelf(recentlyPlayed: List<Song>, recentlyAdded: List<Song>): Pair<String, List<Song>> =
    if (recentlyPlayed.isNotEmpty()) "Recently Played" to recentlyPlayed else "Recently Added" to recentlyAdded

internal fun homeCarouselSongs(
    songs: List<Song>,
    recommendations: List<Song>,
    recentlyPlayed: List<Song>,
    recentlyAdded: List<Song>,
    limit: Int = 8,
): List<Song> {
    if (limit <= 0) return emptyList()
    val catalog = songs.associateBy(Song::id)
    return sequence {
        yieldAll(recommendations)
        yieldAll(recentlyPlayed)
        yieldAll(recentlyAdded)
    }
        .mapNotNull { catalog[it.id] }
        .distinctBy(Song::id)
        .take(limit)
        .toList()
}

internal fun homeSectionOrder(sectionCount: Int, stackIndex: Int): List<Int> {
    if (sectionCount <= 0) return emptyList()
    val startIndex = stackIndex.coerceIn(0, sectionCount - 1)
    return (startIndex until sectionCount).toList() + (0 until startIndex).toList()
}

internal fun homeStackContentVisible(expanded: Boolean, compactVisible: Boolean, expansionProgress: Float): Boolean =
    expanded || compactVisible || expansionProgress > 0f

internal fun homeHeroCardArtworkGradient(
    startY: Float = 0f,
    endY: Float = Float.POSITIVE_INFINITY,
): Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    .28f to Color.Black.copy(.48f),
    .66f to Color.Black.copy(.84f),
    1f to Color.Black.copy(.96f),
    startY = startY,
    endY = endY,
)

@Composable
private fun HomeMixCardAcrylicFlightSurface(
    frame: PlayerArtworkFrame?,
    sourceFrame: PlayerArtworkFrame?,
    alpha: Float,
) {
    val bounds = frame?.homeCardBounds ?: return
    val sourceBounds = sourceFrame?.homeCardBounds ?: return
    if (alpha <= .001f || bounds.width <= 0f || bounds.height <= 0f) return
    Canvas(Modifier.fillMaxSize().zIndex(11f)) {
        val movingRadius = frame.homeCardCornerRadiusPx.coerceIn(0f, minOf(bounds.width, bounds.height) / 2f)
        val sourceRadius = sourceFrame.homeCardCornerRadiusPx.coerceIn(0f, minOf(sourceBounds.width, sourceBounds.height) / 2f)
        val movingPath = Path().apply {
            addRoundRect(RoundRect(bounds, CornerRadius(movingRadius)))
        }
        val sourcePath = Path().apply {
            addRoundRect(RoundRect(sourceBounds, CornerRadius(sourceRadius)))
        }
        val expandingSurface = Path.combine(PathOperation.Difference, movingPath, sourcePath)
        val surfaceBrush = Brush.verticalGradient(
            0f to Color(0xD8243048),
            .24f to Color(0xD310192B),
            .62f to Color(0xE0060C19),
            1f to Color(0xEB050711),
            startY = bounds.top,
            endY = bounds.bottom,
        )
        drawPath(expandingSurface, surfaceBrush, alpha = alpha)
        drawPath(
            expandingSurface,
            Brush.radialGradient(
                0f to Color(0x563D68A5),
                .58f to Color(0x182D4A79),
                1f to Color.Transparent,
                center = Offset(bounds.left + bounds.width * .16f, bounds.top + bounds.height * .12f),
                radius = bounds.height * .55f,
            ),
            alpha = alpha,
        )
        drawPath(
            expandingSurface,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = .055f),
                .35f to Color.White.copy(alpha = .012f),
                .68f to Color.Transparent,
                1f to Color(0xFFCCB8F4).copy(alpha = .018f),
                start = Offset(bounds.left, bounds.top),
                end = Offset(bounds.right, bounds.bottom),
            ),
            alpha = alpha,
        )
    }
}

internal fun homeMixDetailOverlayAlpha(progress: Float): Float =
    ((progress - HOME_MIX_ARTWORK_MIX_FADE_IN_START_MS.toFloat() / HOME_MIX_ARTWORK_TRANSITION_MS) /
        ((HOME_MIX_ARTWORK_MIX_FADE_IN_END_MS - HOME_MIX_ARTWORK_MIX_FADE_IN_START_MS).toFloat() /
            HOME_MIX_ARTWORK_TRANSITION_MS)).coerceIn(0f, 1f)

internal fun homeMixCardOverlayAlpha(progress: Float, closing: Boolean): Float =
    if (closing) {
        ((HOME_MIX_ARTWORK_MIX_FADE_IN_START_MS.toFloat() / HOME_MIX_ARTWORK_TRANSITION_MS - progress) /
            ((HOME_MIX_ARTWORK_MIX_FADE_IN_END_MS - HOME_MIX_ARTWORK_MIX_FADE_IN_START_MS).toFloat() /
                HOME_MIX_ARTWORK_TRANSITION_MS)).coerceIn(0f, 1f)
    } else {
        (1f - progress / (HOME_MIX_ARTWORK_CARD_FADE_OUT_MS.toFloat() / HOME_MIX_ARTWORK_TRANSITION_MS)).coerceIn(0f, 1f)
    }

@Composable
internal fun HomeMixCardOverlayFlight(
    song: Song,
    sourceFrame: PlayerArtworkFrame?,
    currentFrame: PlayerArtworkFrame?,
    progress: Float,
    closing: Boolean,
) {
    val sourceBounds = sourceFrame?.homeCardBounds ?: return
    val bounds = currentFrame?.homeCardBounds ?: sourceBounds
    if (sourceBounds.width <= 0f || sourceBounds.height <= 0f) return
    val density = LocalDensity.current
    val overlayAlpha = homeMixCardOverlayAlpha(progress, closing)
    if (overlayAlpha <= .001f) return
    val buttonAccent = sourceFrame.homeCardButtonAccentColor ?: homeFallbackAccent(song.id)
    val textAccent = sourceFrame.homeCardTextAccentColor ?: Color.White
    Box(
        Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(with(density) { sourceBounds.width.toDp() }, with(density) { sourceBounds.height.toDp() })
            .graphicsLayer {
                scaleX = bounds.width / sourceBounds.width
                scaleY = bounds.height / sourceBounds.height
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = overlayAlpha
            },
    ) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, Modifier.songMetadataMarquee(), color = textAccent, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = .25.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    Text(song.artist, Modifier.padding(top = 2.dp).songMetadataMarquee(), color = Color.White.copy(.76f), fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
                GlassPill(
                    onClick = {},
                    onLongClick = {},
                    label = "Play",
                    accentColor = buttonAccent,
                    iconAccentColor = textAccent,
                )
            }
        }
    }
}

@Composable
internal fun HomeScreen(
    songs: List<Song>,
    favoriteIds: Set<Long> = emptySet(),
    isPlaying: Boolean,
    onChooseSource: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayFromContext: (Song, List<Song>, String) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    playerExpanded: Boolean,
    stackIndex: Int,
    stackExpansionProgress: State<Float>,
    onStackIndexChange: (Int) -> Unit,
    stackListExpanded: Boolean,
    onStackListExpandedChange: (Boolean) -> Unit,
    hasNowPlaying: Boolean,
    isNowPlayingOpen: Boolean,
    onExpandPlayer: () -> Unit,
    recentSongs: List<Song> = emptyList(),
    personalizedRecommendations: List<Song> = emptyList(),
    smartMixes: List<SmartMix> = emptyList(),
    onPlayMix: (SmartMix) -> Unit = {},
    homeMixArtworkFlightActive: Boolean,
    homeMixArtworkProgress: Float,
    homeMixArtworkClosing: Boolean,
    homeMixArtworkSurfaceFrame: PlayerArtworkFrame?,
    homeMixArtworkSurfaceSourceFrame: PlayerArtworkFrame?,
    onOpenHomeMixArtwork: (Song, PlayerArtworkFrame?) -> Unit,
    onHomeMixArtworkFrameChange: (PlayerArtworkFrame) -> Unit,
    onCloseHomeMixArtwork: (Song, PlayerArtworkFrame?) -> Unit,
    onPlayHomeMix: (Song, List<Song>, String) -> Unit,
    onMixDetailVisibilityChange: (Boolean) -> Unit,
    onCarouselBottomChange: (Float) -> Unit,
    homeReselectSignal: Int,
    statusChips: List<HomeStatusChip> = emptyList(),
) {
    val orderedSongs = remember(songs) { songs.sortedByDescending(Song::modifiedAt) }
    val carouselSongs = remember(songs, personalizedRecommendations, recentSongs, orderedSongs) {
        homeCarouselSongs(songs, personalizedRecommendations, recentSongs, orderedSongs)
    }
    var carouselContainerTopPx by remember { mutableFloatStateOf(0f) }
    var carouselContentHeightPx by remember { mutableIntStateOf(0) }
    val latestOnCarouselBottomChange = rememberUpdatedState(onCarouselBottomChange)
    LaunchedEffect(carouselContainerTopPx, carouselContentHeightPx) {
        latestOnCarouselBottomChange.value(carouselContainerTopPx + carouselContentHeightPx)
    }
    val sections = remember(songs, favoriteIds, recentSongs, personalizedRecommendations, smartMixes) {
        val recentlyAdded = orderedSongs
        val (primaryTitle, primarySongs) = primaryHomeShelf(recentSongs, recentlyAdded)
        val likedSongs = recommendationOrderedSongs(songs.filter { it.id in favoriteIds }, personalizedRecommendations)
        val songsForYou = recommendationOrderedSongs(songs.sortedBy { it.title.lowercase(Locale.ROOT) }, personalizedRecommendations)
        val albums = recommendationOrderedSongs(songs.filter { it.album.isNotBlank() }.sortedBy { it.album.lowercase(Locale.ROOT) }, personalizedRecommendations)
            .distinctBy { it.album.trim().lowercase(Locale.ROOT) }
        val artists = recommendationOrderedSongs(songs.filter { it.artist.isNotBlank() }.sortedBy { it.artist.lowercase(Locale.ROOT) }, personalizedRecommendations)
            .distinctBy { it.artist.trim().lowercase(Locale.ROOT) }
        buildList {
            if (primarySongs.isNotEmpty()) {
                add(HomeStackSection(primaryTitle, primarySongs.take(8).map { HomeStackEntry(it.title, it.artist, it) }))
            }
            smartMixes.forEach { mix ->
                val entries = mix.songs.take(8).map { HomeStackEntry(it.title, it.artist, it) }
                if (entries.isNotEmpty()) add(HomeStackSection(mix.title, entries, mix))
            }
            if (likedSongs.isNotEmpty()) {
                add(HomeStackSection("Liked Songs", likedSongs.take(8).map { HomeStackEntry(it.title, it.artist, it) }))
            }
            if (albums.isNotEmpty()) {
                add(HomeStackSection("Albums", albums.take(8).map { HomeStackEntry(it.album, it.artist, it) }))
            }
            if (artists.isNotEmpty()) {
                add(HomeStackSection("Artists", artists.take(8).map { HomeStackEntry(it.artist, it.album, it) }))
            }
            if (recentSongs.isNotEmpty() && recentlyAdded.isNotEmpty()) {
                add(HomeStackSection("Recently Added", recentlyAdded.take(8).map { HomeStackEntry(it.title, it.artist, it) }))
            }
            if (songs.isNotEmpty()) {
                add(HomeStackSection("Songs", songsForYou.take(8).map { HomeStackEntry(it.title, it.artist, it) }))
            }
        }
    }
    val heroMixes = remember(songs, carouselSongs, personalizedRecommendations, smartMixes) {
        val availableIds = songs.mapTo(mutableSetOf(), Song::id)
        val engineMixes = buildList {
            val depth = smartMixes.maxOfOrNull { it.songs.size } ?: 0
            for (mixIndex in 0 until depth) {
                smartMixes.forEach { engineMix ->
                    val rankedSongs = engineMix.songs.filter { it.id in availableIds }
                    val seed = rankedSongs.getOrNull(mixIndex) ?: return@forEach
                    val queue = (listOf(seed) + rankedSongs.filterNot { it.id == seed.id })
                        .distinctBy(Song::id)
                        .take(21)
                    val genreName = engineMix.title.removeSuffix(" mix").removeSuffix(" Mix").trim()
                    val title = if (engineMix.id.startsWith("genre:") && genreName.isNotBlank()) {
                        "$genreName Mix"
                    } else "${seed.title} Mix"
                    add(HomeSongMix(seed, title, queue))
                }
            }
        }.distinctBy { it.seed.id }.associateBy { it.seed.id }
        carouselSongs.map { seed ->
            engineMixes[seed.id] ?: run {
                val queue = (listOf(seed) + recommendationOrderedSongs(songs.filterNot { it.id == seed.id }, personalizedRecommendations))
                    .distinctBy(Song::id)
                    .take(21)
                HomeSongMix(seed, "${seed.title} Mix", queue)
            }
        }
    }
    var selectedCategory by remember { mutableStateOf<HomeStackEntry?>(null) }
    var selectedHomeMix by remember { mutableStateOf<HomeSongMix?>(null) }
    var homeMixArtworkFrame by remember { mutableStateOf<PlayerArtworkFrame?>(null) }
    var mixDetailClosing by remember { mutableStateOf(false) }
    val latestOnMixDetailVisibilityChange = rememberUpdatedState(onMixDetailVisibilityChange)
    LaunchedEffect(homeMixArtworkFlightActive, mixDetailClosing) {
        if (mixDetailClosing && !homeMixArtworkFlightActive) {
            selectedHomeMix = null
            mixDetailClosing = false
            latestOnMixDetailVisibilityChange.value(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose { latestOnMixDetailVisibilityChange.value(false) }
    }
    val mixDetailVisible = selectedHomeMix != null
    fun closeHomeMixDetail() {
        val mix = selectedHomeMix ?: return
        if (mixDetailClosing) return
        mixDetailClosing = true
        onCloseHomeMixArtwork(mix.seed, homeMixArtworkFrame)
    }
    LaunchedEffect(homeReselectSignal) {
        if (homeReselectSignal > 0) closeHomeMixDetail()
    }
    // The full player is layered above this screen and owns the first Back press.
    // Leave the mix detail open until the full player has returned to the mini-player;
    // the next Back press then closes the mix with its carousel artwork flight.
    BackHandler(enabled = mixDetailVisible && !isNowPlayingOpen) { closeHomeMixDetail() }
    var backdropSong by remember(carouselSongs) { mutableStateOf(carouselSongs.firstOrNull()) }
    var acrylicChipColor by remember { mutableStateOf(Color(0xFF26385D)) }
    val context = LocalContext.current
    LaunchedEffect(backdropSong) {
        acrylicChipColor = backdropSong?.let { song ->
            withContext(Dispatchers.IO) { homeAcrylicChipColor(context, song) }
        } ?: Color(0xFF26385D)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = backdropSong,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(240)) },
            modifier = Modifier.matchParentSize(),
            label = "home artwork acrylic backdrop",
        ) { imageSong ->
            if (imageSong != null) {
                AlbumArt(
                    imageSong,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = 1.18f; scaleY = 1.18f }.blur(54.dp),
                    contentDescription = null,
                    radius = 0.dp,
                )
            }
        }
        Box(Modifier.matchParentSize().homeAcrylicBackground(hasArtwork = backdropSong != null))
        Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Wavv",
            waveform = true,
            isPlaying = isPlaying,
            showAmbient = false,
            backgroundColor = Color.Transparent,
            statusChips = statusChips,
            statusChipColor = acrylicChipColor,
            titleFontFamily = WavvAppNameFontFamily,
        )
        if (songs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyLibraryCard(onChooseSource)
            }
        } else {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { carouselContainerTopPx = it.boundsInRoot().top },
            ) {
                val density = LocalDensity.current
                val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
                val carouselChromeHeight = 28.dp // outer spacer, pager-to-dots spacing, and indicator
                val playerAndStackGap = 84.dp // player lane, followed by the stack breathing gap
                val stackCardHeight = 144.dp
                val nextCardPeek = 24.dp
                val footerClearance = 76.dp // keep the stack above the 60dp bottom bar and its padding
                val maxHeroCardHeight = (
                    maxHeight - navigationBottom - carouselChromeHeight - playerAndStackGap -
                        stackCardHeight - nextCardPeek - footerClearance
                    ).coerceAtLeast(0.dp)
                Column(
                    Modifier.fillMaxWidth().onSizeChanged { carouselContentHeightPx = it.height }.graphicsLayer {
                        val progress = stackExpansionProgress.value
                        translationY = -size.height * progress
                        alpha = 1f - progress
                    },
                ) {
                    Spacer(Modifier.height(10.dp))
                    HeroCarousel(
                        mixes = heroMixes,
                        maxCardHeight = maxHeroCardHeight,
                        onCurrentSongChanged = { backdropSong = it },
                        onPlay = onPlay,
                        mixDetailVisible = mixDetailVisible,
                        hiddenArtworkSongId = selectedHomeMix?.seed?.id.takeIf { homeMixArtworkFlightActive },
                        cardOverlayAlpha = homeMixCardOverlayAlpha(homeMixArtworkProgress, homeMixArtworkClosing),
                        onOpenMix = { mix, sourceFrame ->
                            mixDetailClosing = false
                            selectedHomeMix = mix
                            homeMixArtworkFrame = null
                            onMixDetailVisibilityChange(true)
                            onOpenHomeMixArtwork(mix.seed, sourceFrame)
                        },
                        onPlayNext = onPlayNext,
                        favoriteIds = favoriteIds,
                        onAddToQueue = onAddToQueue,
                        onToggleFavorite = onToggleFavorite,
                        onRequestAddToPlaylist = onRequestAddToPlaylist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                    )
                }
                HomeStackPanel(
                    sections = sections,
                    stackIndex = stackIndex,
                    playerExpanded = playerExpanded,
                    stackListExpanded = stackListExpanded,
                    expansionProgress = stackExpansionProgress,
                    hasNowPlaying = hasNowPlaying,
                    onIndexChange = { onStackIndexChange(it.coerceIn(0, sections.lastIndex.coerceAtLeast(0))) },
                    onStackListExpandedChange = onStackListExpandedChange,
                    onExpandPlayer = onExpandPlayer,
                    onOpenSong = onPlay,
                    onOpenCategory = { selectedCategory = it },
                    onPlayMix = onPlayMix,
                    favoriteIds = favoriteIds,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onToggleFavorite = onToggleFavorite,
                    onRequestAddToPlaylist = onRequestAddToPlaylist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToArtist = onNavigateToArtist,
                    carouselContentHeightPx = carouselContentHeightPx,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        }
        val detailOverlayAlpha = homeMixDetailOverlayAlpha(homeMixArtworkProgress)
        HomeMixCardAcrylicFlightSurface(
            frame = homeMixArtworkSurfaceFrame,
            sourceFrame = homeMixArtworkSurfaceSourceFrame,
            alpha = if (homeMixArtworkFlightActive) 1f - detailOverlayAlpha else 0f,
        )
        selectedHomeMix?.let { mix ->
            HomeMixDetailScreen(
                mix = mix,
                modifier = Modifier.fillMaxSize().zIndex(12f),
                overlayAlpha = detailOverlayAlpha,
                isInteractive = detailOverlayAlpha >= .999f && !homeMixArtworkFlightActive,
                hideSharpArtwork = homeMixArtworkFlightActive || mixDetailClosing,
                onArtworkFrameChange = { frame ->
                    homeMixArtworkFrame = frame
                    onHomeMixArtworkFrameChange(frame)
                },
                onBack = ::closeHomeMixDetail,
                onPlay = { song ->
                    onPlayHomeMix(song, mix.songs, mix.title)
                },
            )
        }
        selectedCategory?.let { category ->
            val categorySongs = songs.filter { song ->
                val text = "${song.title} ${song.artist} ${song.album}".lowercase()
                text.contains(category.title.lowercase())
            }.ifEmpty { songs }
            Dialog(
                onDismissRequest = { selectedCategory = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                HomeCategoryDetailScreen(
                    title = category.title,
                    color = category.color ?: WavvPink,
                    artworkSong = category.song,
                    songs = categorySongs,
                    onClose = { selectedCategory = null },
                    onPlay = { song -> selectedCategory = null; onPlayFromContext(song, categorySongs, category.title) },
                )
            }
        }
    }
}

private data class HomeStackEntry(
    val title: String,
    val subtitle: String? = null,
    val song: Song? = null,
    val color: Color? = null,
)

private data class HomeSongMix(
    val seed: Song,
    val title: String,
    val songs: List<Song>,
)

private data class HomeStackSection(val label: String, val entries: List<HomeStackEntry>, val mix: SmartMix? = null)

@Composable
private fun HomeStackPanel(
    sections: List<HomeStackSection>,
    stackIndex: Int,
    playerExpanded: Boolean,
    stackListExpanded: Boolean,
    expansionProgress: State<Float>,
    hasNowPlaying: Boolean,
    carouselContentHeightPx: Int,
    onIndexChange: (Int) -> Unit,
    onStackListExpandedChange: (Boolean) -> Unit,
    onExpandPlayer: () -> Unit,
    onOpenSong: (Song) -> Unit,
    onOpenCategory: (HomeStackEntry) -> Unit,
    onPlayMix: (SmartMix) -> Unit,
    favoriteIds: Set<Long>,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val densityScale = density.density
    val scrollState = rememberScrollState()
    LaunchedEffect(stackListExpanded) {
        if (!stackListExpanded && scrollState.value != 0) {
            scrollState.scrollTo(0)
        }
    }
    val currentStackListExpanded = rememberUpdatedState(stackListExpanded)
    val currentOnStackListExpandedChange = rememberUpdatedState(onStackListExpandedChange)
    val currentOnIndexChange = rememberUpdatedState(onIndexChange)
    val currentOnExpandPlayer = rememberUpdatedState(onExpandPlayer)
    val collapseAtTop = remember(scrollState, stackListExpanded) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (
                    stackListExpanded && source == NestedScrollSource.UserInput && available.y > 0f &&
                    scrollState.value == 0
                ) {
                    onStackListExpandedChange(false)
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    val orderedSections = remember(sections, stackIndex) {
        homeSectionOrder(sections.size, stackIndex).map(sections::get)
    }
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val topInset = if (hasNowPlaying) 76.dp else 12.dp
    val cardHeight = 144.dp
    val cardGap = 12.dp
    val rowHeight = cardHeight + cardGap
    val stackPeek = 40.dp
    val miniPlayerSlotHeight = 60.dp
    val carouselGap = 12.dp
    val cardHeightPx = with(density) { cardHeight.toPx() }
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val stackPeekPx = with(density) { stackPeek.toPx() }
    val topInsetPx = with(density) { topInset.toPx() }
    val carouselGapPx = with(density) { carouselGap.toPx() }
    val miniPlayerSlotHeightPx = with(density) { miniPlayerSlotHeight.toPx() }
    val preferredStackBottomClearance = 80.dp
    val minimumStackBottomClearance = 76.dp
    val minimumNextCardReveal = 24.dp
    val minimumNextCardRevealPx = with(density) { minimumNextCardReveal.toPx() }
    val preferredStackBottomClearancePx = with(density) { preferredStackBottomClearance.toPx() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val parentHeight = maxHeight
        val parentHeightPx = with(density) { parentHeight.toPx() }
        val navigationBottomPx = with(density) { navigationBottom.toPx() }
        // Reserve the mini-player lane under the carousel, then place the first stack card
        // where the expanded player belongs. This keeps the stack attached to the carousel
        // instead of anchoring it to the bottom of a variable-height screen.
        val compactTopPx = carouselContentHeightPx + carouselGapPx * 2f + miniPlayerSlotHeightPx
        // On short screens, reclaim bottom padding before sacrificing the space between the
        // carousel and the player/card lanes. This keeps a real, nonzero peek of card two in
        // the viewport, so the connected stack still reads as scrollable.
        val stackSpaceBeforeClearancePx = parentHeightPx - navigationBottomPx - compactTopPx
        val clearanceThatStillAllowsPeekPx = (
            stackSpaceBeforeClearancePx - cardHeightPx - minimumNextCardRevealPx
            ).coerceAtLeast(0f)
        val minimumStackBottomClearancePx = with(density) { minimumStackBottomClearance.toPx() }
        val stackBottomClearancePx = minOf(preferredStackBottomClearancePx, clearanceThatStillAllowsPeekPx)
            .coerceAtLeast(minimumStackBottomClearancePx)
        val expandedHeightPx = (parentHeightPx - navigationBottomPx - stackBottomClearancePx).coerceAtLeast(0f)
        val expandedHeight = with(density) { expandedHeightPx.toDp() }.coerceAtMost(parentHeight)
        val compactAvailablePx = (expandedHeightPx - compactTopPx).coerceAtLeast(0f)
        val playerStackPushPx by animateFloatAsState(
            targetValue = if (playerExpanded && !stackListExpanded) with(density) { 40.dp.toPx() } else 0f,
            animationSpec = tween(540, easing = WavvMotionEasing),
            label = "home stack yielding to expanded player",
        )
        val compactVisibleCardCount = when {
            orderedSections.isEmpty() -> 0
            orderedSections.size == 1 -> 1
            else -> (
                2 + ((compactAvailablePx - cardHeightPx - minimumNextCardRevealPx) / stackPeekPx)
                    .toInt().coerceAtLeast(0)
                ).coerceAtMost(orderedSections.size)
        }
        // Keep the tucked card's lower edge clear of the opaque footer/navbar extension.
        // Moving the handoff boundary up makes the card finish its curve before that layer.
        val stackPathClearancePx = with(density) { 24.dp.toPx() }
        val stackBoundaryPx = expandedHeightPx - cardHeightPx - stackPeekPx - stackPathClearancePx
        Box(
            Modifier.fillMaxWidth().height(expandedHeight)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val featherStartY = (size.height - with(density) { 72.dp.toPx() }).coerceAtLeast(0f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White,
                            1f to Color.Transparent,
                            startY = featherStartY,
                            endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .clipToBounds(),
        ) {
            val listGestureModifier = if (stackListExpanded) {
                Modifier.nestedScroll(collapseAtTop)
                    .verticalScroll(scrollState, enabled = true, overscrollEffect = null)
            } else Modifier
            Column(
                Modifier.fillMaxWidth().then(listGestureModifier).padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(topInset))
                orderedSections.forEachIndexed { displayIndex, section ->
                    val actualIndex = if (sections.isEmpty()) 0 else (displayIndex + stackIndex).mod(sections.size)
                    val active = actualIndex == stackIndex
                    val compactVisible = displayIndex < compactVisibleCardCount
                    val compactAlpha by animateFloatAsState(
                        targetValue = if (compactVisible) 1f else 0f,
                        animationSpec = tween(220, easing = WavvMotionEasing),
                        label = "home compact stack card visibility",
                    )
                    val contentVisible by remember(expansionProgress, stackListExpanded, compactVisible) {
                        derivedStateOf {
                            homeStackContentVisible(stackListExpanded, compactVisible, expansionProgress.value)
                        }
                    }
                    val compactTopForCard = compactTopPx + playerStackPushPx + displayIndex * stackPeekPx
                    val compactScaleX = (1f - displayIndex.coerceAtMost(5) * .035f).coerceAtLeast(.82f)
                    val shape = RoundedCornerShape(28.dp)
                    Box(
                        Modifier.fillMaxWidth().height(rowHeight)
                            .zIndex((orderedSections.size - displayIndex).toFloat()),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(cardHeight).graphicsLayer {
                                val progress = expansionProgress.value.coerceIn(0f, 1f)
                                val listScrollOffset = if (stackListExpanded) scrollState.value else 0
                                val naturalTop = topInsetPx + displayIndex * rowHeightPx - listScrollOffset
                                // Keep the same card moving through the list/stack boundary.
                                // The overflow card eases into the single tucked layer over
                                // one row instead of jumping there as soon as it crosses the
                                // viewport boundary. Deeper cards share that tucked position
                                // and are revealed in order as scrolling brings them forward.
                                val tuck = ((naturalTop - stackBoundaryPx) / rowHeightPx)
                                    .coerceIn(0f, 1f)
                                val easedTuck = tuck * tuck * (3f - 2f * tuck)
                                val tuckedTop = stackBoundaryPx + stackPeekPx
                                val expandedTop = if (naturalTop > stackBoundaryPx) {
                                    naturalTop + (tuckedTop - naturalTop) * easedTuck
                                } else naturalTop
                                val desiredTop = compactTopForCard * (1f - progress) + expandedTop * progress
                                val expandedScale = 1f - .06f * easedTuck
                                alpha = compactAlpha + (1f - compactAlpha) * progress
                                scaleX = compactScaleX + (expandedScale - compactScaleX) * progress
                                scaleY = 1f + (expandedScale - 1f) * progress
                                translationY = desiredTop - naturalTop
                            }.pointerInput(
                                compactVisible,
                                stackIndex,
                                sections.size,
                                hasNowPlaying,
                                playerExpanded,
                            ) {
                                // Attach this detector to the moving card, rather than
                                // the full-screen panel. That preserves the carousel's
                                // independent hit target and keeps swipe input aligned
                                // with the card while it transitions between layouts.
                                if (!currentStackListExpanded.value && compactVisible) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            val velocityTracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                                            var handled = false
                                            var expandingWithThisGesture = false
                                            var lastPosition = down.position
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                val pointerDelta = change.position - lastPosition
                                                lastPosition = change.position
                                                val delta = change.position - down.position
                                                val velocity = velocityTracker.calculateVelocity()
                                                val verticalIntent =
                                                    kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x) * 1.15f ||
                                                        kotlin.math.abs(velocity.y) > kotlin.math.abs(velocity.x) * 1.15f
                                                val crossedSlop = kotlin.math.abs(delta.y) > viewConfiguration.touchSlop
                                                val fastFlick = kotlin.math.abs(velocity.y) > 650f * densityScale && kotlin.math.abs(delta.y) > 5f * densityScale
                                                if (!handled && verticalIntent && (crossedSlop || fastFlick)) {
                                                    handled = true
                                                    change.consume()
                                                    val swipeDirection = if (fastFlick && !crossedSlop) velocity.y else delta.y
                                                    if (swipeDirection < 0f) {
                                                        currentOnStackListExpandedChange.value(true)
                                                        expandingWithThisGesture = true
                                                        if (pointerDelta.y < 0f) scrollState.dispatchRawDelta(-pointerDelta.y)
                                                    } else if (hasNowPlaying && stackIndex == 0) {
                                                        currentOnExpandPlayer.value()
                                                    } else {
                                                        currentOnIndexChange.value((stackIndex - 1).coerceIn(0, sections.lastIndex.coerceAtLeast(0)))
                                                    }
                                                } else if (handled) {
                                                    if (expandingWithThisGesture && pointerDelta.y < 0f) {
                                                        scrollState.dispatchRawDelta(-pointerDelta.y)
                                                    }
                                                    change.consume()
                                                }
                                                if (!change.pressed) break
                                            }
                                    }
                                }
                            }
                                .shadow(if (active) 16.dp else 10.dp, shape).clip(shape),
                        ) {
                            // Preserve one keyed card instance through both layouts. Keeping
                            // its content and frosted backdrop alive avoids a blank/recreated
                            // frame when it changes from list position to the tucked stack.
                            key(section.label) {
                                HomeStackCard(
                                    section = section,
                                    enabled = stackListExpanded || active,
                                    stackScrollInProgress = scrollState.isScrollInProgress,
                                    contentVisible = contentVisible,
                                    modifier = Modifier.fillMaxSize(),
                                    onPlayMix = onPlayMix,
                                    favoriteIds = favoriteIds,
                                    onPlayNext = onPlayNext,
                                    onAddToQueue = onAddToQueue,
                                    onToggleFavorite = onToggleFavorite,
                                    onRequestAddToPlaylist = onRequestAddToPlaylist,
                                    onNavigateToAlbum = onNavigateToAlbum,
                                    onNavigateToArtist = onNavigateToArtist,
                                    onEntryClick = { entry ->
                                        if (!stackListExpanded && !active) return@HomeStackCard
                                        if (entry.color != null) onOpenCategory(entry)
                                        else entry.song?.let(onOpenSong)
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(stackPeek - cardGap))
            }
        }
    }
}

@Composable
private fun HomeStackCard(
    section: HomeStackSection,
    enabled: Boolean,
    stackScrollInProgress: Boolean,
    contentVisible: Boolean,
    modifier: Modifier = Modifier,
    onPlayMix: (SmartMix) -> Unit,
    favoriteIds: Set<Long>,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onEntryClick: (HomeStackEntry) -> Unit,
) {
    var songForActions by remember { mutableStateOf<Song?>(null) }
    val entryListState = rememberLazyListState()
    val rowScrollInProgress = rememberUpdatedState(entryListState.isScrollInProgress)
    val stackScrollActive = rememberUpdatedState(stackScrollInProgress)
    val tileWidth = ((LocalConfiguration.current.screenWidthDp.dp - 96.dp) / 3).coerceIn(64.dp, 88.dp)
    val cardShape = RoundedCornerShape(28.dp)
    Box(modifier.clip(cardShape).homeStackCardFill()) {
        if (contentVisible) Column(
            Modifier.fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 3.dp).padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(section.label.uppercase(), color = Color.White, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, letterSpacing = .45.sp, fontFamily = FontFamily.SansSerif)
            Text(
                if (section.mix != null) "PLAY MIX" else "Swipe to explore",
                Modifier.then(
                    if (section.mix != null) {
                        Modifier.scrollSafeTap(
                            enabled = enabled,
                            canTap = { !stackScrollActive.value && !rowScrollInProgress.value },
                        ) { onPlayMix(section.mix) }
                    } else Modifier,
                ),
                color = Color.White.copy(.48f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        if (contentVisible) LazyRow(
            state = entryListState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 3.dp),
        ) {
            itemsIndexed(
                items = section.entries,
                key = { index, entry -> entry.song?.id ?: "${section.label}:${entry.title}:$index" },
            ) { _, entry ->
                Column(
                Modifier.width(tileWidth).scrollSafeTap(
                    enabled = enabled,
                    canTap = { !stackScrollActive.value && !rowScrollInProgress.value },
                    onLongClick = { entry.song?.let { songForActions = it } },
                ) { onEntryClick(entry) },
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                            .background(entry.color ?: Color.White.copy(.08f))
                            .shadow(5.dp, RoundedCornerShape(14.dp)),
                    ) {
                        entry.song?.let { AlbumArt(it, Modifier.fillMaxSize().alpha(if (entry.color != null) .62f else 1f), entry.title, 14.dp) }
                        if (entry.color != null) {
                            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(entry.color.copy(alpha = .67f), Color.Transparent))))
                            Text(entry.title, Modifier.align(Alignment.BottomStart).padding(start = 9.dp, end = 7.dp, bottom = 8.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f)))),
                            )
                            Text(
                                entry.title,
                                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        }
    }
    songForActions?.let { song ->
        SongActionsSheet(
            song = song,
            isFavorite = song.id in favoriteIds,
            onDismiss = { songForActions = null },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onToggleFavorite = onToggleFavorite,
            onRequestAddToPlaylist = onRequestAddToPlaylist,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist,
        )
    }
}

private fun Modifier.scrollSafeTap(
    enabled: Boolean,
    canTap: () -> Boolean,
    onLongClick: (() -> Unit)? = null,
    onTap: () -> Unit,
): Modifier = composed {
    var gestureMoved by remember { mutableStateOf(false) }
    val latestEnabled = rememberUpdatedState(enabled)
    val latestCanTap = rememberUpdatedState(canTap)
    val latestOnTap = rememberUpdatedState(onTap)
    val latestOnLongClick = rememberUpdatedState(onLongClick)
    val density = LocalDensity.current.density
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val velocityTracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            gestureMoved = down.isConsumed || !latestCanTap.value()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.position - down.position
                val fastFlick = kotlin.math.abs(velocityTracker.calculateVelocity().y) > 650f * density &&
                    kotlin.math.abs(delta.y) > 4f * density
                if (
                    change.isConsumed ||
                    kotlin.math.abs(delta.x) > viewConfiguration.touchSlop ||
                    kotlin.math.abs(delta.y) > viewConfiguration.touchSlop ||
                    fastFlick
                ) {
                    gestureMoved = true
                }
                if (!change.pressed) break
            }
        }
    }.then(
        if (onLongClick == null) {
            Modifier.clickable(enabled = enabled, role = Role.Button) {
                if (latestEnabled.value && !gestureMoved && latestCanTap.value()) {
                    latestOnTap.value()
                }
            }
        } else {
            Modifier.combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    if (latestEnabled.value && !gestureMoved && latestCanTap.value()) {
                        latestOnTap.value()
                    }
                },
                onLongClickLabel = "Song actions",
                onLongClick = {
                    if (latestEnabled.value && !gestureMoved && latestCanTap.value()) {
                        latestOnLongClick.value?.invoke()
                    }
                },
            )
        },
    )
}

@Composable
private fun HomeCategoryDetailScreen(
    title: String,
    color: Color,
    artworkSong: Song?,
    songs: List<Song>,
    onClose: () -> Unit,
    onPlay: (Song) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        SongListArtworkBackdrop(artworkSong ?: songs.firstOrNull(), Modifier.matchParentSize())
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(204.dp).background(color.copy(alpha = .12f))) {
            artworkSong?.let { AlbumArt(it, Modifier.fillMaxSize().statusBarsPadding().alpha(.4f).edgeBlendedArtworkMask(), title, 0.dp) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xFF090B11).copy(alpha = .72f))))
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 8.dp)
                    .size(36.dp).clip(CircleShape).background(Color.Black.copy(.3f)).border(1.dp, Color.White.copy(.2f), CircleShape),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp)) }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp)) {
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-.4).sp, fontFamily = FontFamily.SansSerif)
                Text("${songs.size} songs", Modifier.padding(top = 3.dp), color = Color.White.copy(.58f), fontSize = 13.sp)
            }
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 160.dp)) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPlay(song) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AlbumArt(song, Modifier.size(50.dp), song.title, 9.dp)
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, Modifier.padding(top = 3.dp), color = Color.White.copy(.44f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (index != songs.lastIndex) Hairline()
            }
        }
        }
    }
}

@Composable
private fun HomeMixDetailScreen(
    mix: HomeSongMix,
    modifier: Modifier,
    overlayAlpha: Float,
    isInteractive: Boolean,
    hideSharpArtwork: Boolean,
    onArtworkFrameChange: (PlayerArtworkFrame) -> Unit,
    onBack: () -> Unit,
    onPlay: (Song) -> Unit,
) {
    BoxWithConstraints(modifier.graphicsLayer { alpha = overlayAlpha }.background(WavvBackground)) {
        val heroHeight = minOf(620.dp, maxWidth + 170.dp)
        val artworkSize = minOf(maxWidth, 480.dp)
        val density = LocalDensity.current
        val mixListState = rememberLazyListState()
        val mixListIsScrolling = rememberUpdatedState(mixListState.isScrollInProgress)
        SongListArtworkBackdrop(
            mix.seed,
            Modifier.matchParentSize(),
            strongerMixBlur = true,
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            state = mixListState,
            userScrollEnabled = isInteractive,
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    Box(
                        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 32.dp).size(artworkSize)
                            .onGloballyPositioned { coordinates ->
                                onArtworkFrameChange(
                                    PlayerArtworkFrame(
                                        bounds = coordinates.boundsInRoot(),
                                        cornerRadiusPx = with(density) { 0.dp.toPx() },
                                        regularArtworkAlpha = 0f,
                                        maskedArtworkAlpha = 1f,
                                        edgeBlend = 1f,
                                        homeCardGradientBlend = 0f,
                                    ),
                                )
                            },
                    ) {
                        Box(
                            Modifier.matchParentSize().alpha(if (hideSharpArtwork) 0f else 1f).maskedArtworkFade(),
                        ) {
                            AlbumArt(mix.seed, Modifier.fillMaxSize(), mix.seed.title, 0.dp)
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        .54f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = .24f),
                                    ),
                                ),
                            )
                        }
                    }
                    IconButton(
                        enabled = isInteractive,
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 8.dp)
                            .size(42.dp).clip(CircleShape).background(Color.Black.copy(.28f)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home", tint = Color.White)
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("PERSONALIZED MIX", color = Color.White.copy(.65f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                        Text(mix.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-.6).sp, fontFamily = WavvFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(mix.seed.artist.takeIf(String::isNotBlank), mix.seed.genre?.takeIf(String::isNotBlank))
                                .joinToString(" · ").ifBlank { "Recommended around ${mix.seed.title}" },
                            color = Color.White.copy(.6f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).padding(top = 2.dp)
                                .clip(CircleShape).background(Color.White)
                                .scrollSafeTap(
                                    enabled = isInteractive,
                                    canTap = { isInteractive && !mixListIsScrolling.value },
                                ) { onPlay(mix.seed) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF111111), modifier = Modifier.size(23.dp))
                            Text("Play Mix", Modifier.padding(start = 6.dp), color = Color(0xFF111111), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            item {
                Text(
                    "UP NEXT",
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 3.dp),
                    color = Color.White.copy(.48f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = .9.sp,
                )
            }
            itemsIndexed(mix.songs.drop(1), key = { _, song -> song.id }) { index, song ->
                Row(
                    Modifier.fillMaxWidth()
                        .scrollSafeTap(
                            enabled = isInteractive,
                            canTap = { isInteractive && !mixListIsScrolling.value },
                        ) { onPlay(song) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("${index + 1}", Modifier.width(22.dp), color = Color.White.copy(.34f), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    AlbumArt(song, Modifier.size(52.dp), song.title, 9.dp)
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, Modifier.padding(top = 3.dp), color = Color.White.copy(.48f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play ${song.title}", tint = Color.White.copy(.45f), modifier = Modifier.size(19.dp))
                }
                if (index != mix.songs.lastIndex - 1) Hairline()
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
private fun HeroCarousel(
    mixes: List<HomeSongMix>,
    maxCardHeight: Dp,
    onCurrentSongChanged: (Song) -> Unit,
    onPlay: (Song) -> Unit,
    onOpenMix: (HomeSongMix, PlayerArtworkFrame?) -> Unit,
    hiddenArtworkSongId: Long?,
    cardOverlayAlpha: Float,
    mixDetailVisible: Boolean,
    onPlayNext: (Song) -> Unit,
    favoriteIds: Set<Long>,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
) {
    val pageCount = minOf(8, mixes.size).coerceAtLeast(1)
    val pagerState = rememberPagerState { pageCount }
    LaunchedEffect(pagerState.currentPage, mixes) {
        if (mixes.isNotEmpty()) {
            mixes.getOrNull(pagerState.currentPage % mixes.size)?.seed?.let(onCurrentSongChanged)
        }
    }
    LaunchedEffect(pageCount, mixDetailVisible) {
        if (pageCount > 1 && !mixDetailVisible) while (isActive) {
            delay(10_000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % pageCount)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val naturalCardWidth = (maxWidth - 56.dp).coerceAtLeast(0.dp)
            val responsiveCardWidth = minOf(naturalCardWidth, maxCardHeight * .95f)
            val responsiveCardHeight = responsiveCardWidth / .95f
            HorizontalPager(
                state = pagerState,
                pageSize = androidx.compose.foundation.pager.PageSize.Fixed(responsiveCardWidth),
                contentPadding = PaddingValues(start = 20.dp, end = 36.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val mix = mixes[page % mixes.size]
                val song = mix.seed
                HeroCard(
                    song = song,
                    cardHeight = responsiveCardHeight,
                    onPlay = onPlay,
                    onOpenMix = { sourceFrame -> onOpenMix(mix, sourceFrame) },
                    hideArtwork = song.id == hiddenArtworkSongId,
                    overlayAlpha = cardOverlayAlpha,
                    interactive = !mixDetailVisible,
                    onPlayNext = onPlayNext,
                    isFavorite = song.id in favoriteIds,
                    onAddToQueue = onAddToQueue,
                    onToggleFavorite = onToggleFavorite,
                    onRequestAddToPlaylist = onRequestAddToPlaylist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToArtist = onNavigateToArtist,
                )
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
private fun HeroCard(
    song: Song,
    cardHeight: Dp,
    onPlay: (Song) -> Unit,
    onOpenMix: (PlayerArtworkFrame?) -> Unit,
    hideArtwork: Boolean,
    overlayAlpha: Float,
    interactive: Boolean,
    onPlayNext: (Song) -> Unit,
    isFavorite: Boolean,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
) {
    var showActions by remember(song.id) { mutableStateOf(false) }
    var artworkFrame by remember(song.id) { mutableStateOf<PlayerArtworkFrame?>(null) }
    var cardBounds by remember(song.id) { mutableStateOf<Rect?>(null) }
    var thumbnailAccent by remember(song.id) { mutableStateOf(homeFallbackAccent(song.id)) }
    val context = LocalContext.current
    val density = LocalDensity.current
    LaunchedEffect(song.id, song.albumArtUri, song.uri) {
        thumbnailAccent = withContext(Dispatchers.IO) { homeThumbnailAccentColor(context, song) }
    }
    val playAccent = Color(
        red = (thumbnailAccent.red * 1.45f).coerceAtMost(.95f),
        green = (thumbnailAccent.green * 1.45f).coerceAtMost(.95f),
        blue = (thumbnailAccent.blue * 1.45f).coerceAtMost(.95f),
    )
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth().height(cardHeight)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .combinedClickable(
                enabled = interactive,
                role = Role.Button,
                onClick = {
                    onOpenMix(
                        artworkFrame?.copy(
                            homeCardBounds = cardBounds,
                            homeCardCornerRadiusPx = with(density) { 20.dp.toPx() },
                            homeCardTextAccentColor = playAccent,
                            homeCardButtonAccentColor = thumbnailAccent,
                        ),
                    )
                },
                onLongClickLabel = "Song actions",
                onLongClick = { showActions = true },
            ),
    ) {
        Box(
            Modifier.matchParentSize().clip(cardShape)
                .background(if (hideArtwork) Color.Transparent else Color.Black),
        ) {
            AlbumArt(
                song,
                Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1f)
                    .onGloballyPositioned { coordinates ->
                        artworkFrame = PlayerArtworkFrame(
                            bounds = coordinates.boundsInRoot(),
                            cornerRadiusPx = with(density) { 20.dp.toPx() },
                            homeCardGradientBlend = 1f,
                        )
                    }
                    .alpha(if (hideArtwork) 0f else 1f),
                song.title,
                0.dp,
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.68f)
                    .graphicsLayer { alpha = if (hideArtwork) 0f else overlayAlpha }
                    .background(homeHeroCardArtworkGradient()),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .graphicsLayer { alpha = if (hideArtwork) 0f else overlayAlpha }
                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, Modifier.songMetadataMarquee(), color = playAccent, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = .25.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                        Text(song.artist, Modifier.padding(top = 2.dp).songMetadataMarquee(), color = Color.White.copy(.76f), fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                    GlassPill(
                        onClick = { onPlay(song) },
                        onLongClick = { showActions = true },
                        label = "Play",
                        accentColor = thumbnailAccent,
                        iconAccentColor = playAccent,
                    )
                }
            }
        }
    }
    if (showActions) {
        SongActionsSheet(
            song = song,
            isFavorite = isFavorite,
            onDismiss = { showActions = false },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onToggleFavorite = onToggleFavorite,
            onRequestAddToPlaylist = onRequestAddToPlaylist,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist,
        )
    }
}

@Composable
private fun SongActionsSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
) {
    WavvBottomSheet(onDismissRequest = onDismiss) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            AlbumArt(song, Modifier.size(58.dp), song.title, 12.dp)
            Column(Modifier.weight(1f)) {
                Text("Song actions", color = Color.White.copy(.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(song.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetActionRow(Icons.Default.SkipNext, "Play next") { close(); onPlayNext(song) }
        SheetActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Add to queue") { close(); onAddToQueue(song) }
        SheetActionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Remove from favorites" else "Add to favorites") {
            close(); onToggleFavorite(song)
        }
        SheetActionRow(Icons.Default.Add, "Add to playlist") { close(); onRequestAddToPlaylist(song) }
        SheetActionRow(Icons.Default.Album, "Go to album") { close(); onNavigateToAlbum(song) }
        SheetActionRow(Icons.Default.Mic, "Go to artist") { close(); onNavigateToArtist(song) }
    }
}

@Composable
private fun GlassPill(onClick: () -> Unit, onLongClick: () -> Unit, label: String, accentColor: Color, iconAccentColor: Color) {
    val shape = CircleShape
    Box(
        Modifier.width(86.dp).height(42.dp).clip(shape)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = "Song actions",
                onLongClick = onLongClick,
            ),
    ) {
        Box(Modifier.matchParentSize().homeThumbnailAccentFill(accentColor))
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = iconAccentColor.copy(alpha = .78f), modifier = Modifier.size(20.dp).blur(4.dp))
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = iconAccentColor, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White.copy(alpha = .72f), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
        }
    }
}

@Composable
internal fun Hairline() = Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Color.White.copy(.07f)))

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
internal fun ShelfLabel(label: String) = Text(label.uppercase(), color = WavvMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
