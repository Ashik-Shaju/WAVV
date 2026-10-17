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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private fun expressiveFontFor(page: LibraryPage): FontFamily = when (page) {
    LibraryPage.Artist, LibraryPage.Artists -> FontFamily.SansSerif
    LibraryPage.Album, LibraryPage.Albums -> FontFamily.Serif
    LibraryPage.Playlist, LibraryPage.Playlists -> WavvFontFamily
    else -> WavvFontFamily
}

private const val MADE_FOR_YOU_PLAYLIST_ID = Long.MIN_VALUE
private const val LOSSLESS_PLAYLIST_ID = Long.MIN_VALUE + 1L
private const val HI_RES_LOSSLESS_PLAYLIST_ID = Long.MIN_VALUE + 2L

private val PERMANENT_PLAYLIST_IDS = setOf(
    MADE_FOR_YOU_PLAYLIST_ID,
    LOSSLESS_PLAYLIST_ID,
    HI_RES_LOSSLESS_PLAYLIST_ID,
)

private fun permanentPlaylistCard(
    id: Long,
    title: String,
    songs: List<Song>,
    sortPriority: Int,
    onNavigate: (LibraryDestination) -> Unit,
) = LibraryCard(
    id = id.toString(),
    title = title,
    subtitle = "${songs.size} songs",
    artSong = songs.firstOrNull(),
    priority = Int.MAX_VALUE - sortPriority,
    permanent = true,
    playlistSongs = songs,
) {
    onNavigate(LibraryDestination(LibraryPage.Playlist, id, title))
}

@Composable
private fun LibraryPlaylistActionsSheet(
    playlist: UserPlaylist,
    playlistSongs: List<Song>,
    artworkSong: Song?,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    WavvBottomSheet(
        onDismissRequest = onDismiss,
        maxHeight = 380.dp,
        artworkSong = artworkSong,
    ) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            PlaylistArtwork(playlistSongs, artworkSong, Modifier.size(54.dp), playlist.name, 11.dp)
            Column(Modifier.weight(1f)) {
                Text("Playlist options", color = Color.White.copy(.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(playlist.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.songIds.size} songs", color = Color.White.copy(.55f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetActionRow(Icons.Default.Edit, "Edit playlist") { close(); onRename() }
        SheetActionRow(Icons.Default.PushPin, if (pinned) "Unpin playlist" else "Pin playlist") { close(); onTogglePin() }
        SheetActionRow(Icons.Default.Delete, "Delete playlist") { close(); onDelete() }
    }
}

@Composable
private fun LibraryAlbumActionsSheet(
    albumTitle: String,
    albumArtist: String,
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
) {
    WavvBottomSheet(onDismissRequest = onDismiss, artworkSong = song) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            AlbumArt(song, Modifier.size(58.dp), albumTitle, 12.dp)
            Column(Modifier.weight(1f)) {
                Text("Album options", color = Color.White.copy(.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(albumTitle, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(albumArtist, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetActionRow(R.drawable.action_play_next, "Play next") { close(); onPlayNext(song) }
        SheetActionRow(R.drawable.action_add_to_queue, "Add to queue") { close(); onAddToQueue(song) }
    }
}

@Composable
private fun LibraryArtistActionsSheet(
    artistName: String,
    songCount: String,
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
) {
    WavvBottomSheet(onDismissRequest = onDismiss, artworkSong = song) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            AlbumArt(song, Modifier.size(58.dp).clip(CircleShape), artistName, 29.dp)
            Column(Modifier.weight(1f)) {
                Text("Artist options", color = Color.White.copy(.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(artistName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(songCount, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetActionRow(R.drawable.action_play_next, "Play next") { close(); onPlayNext(song) }
        SheetActionRow(R.drawable.action_add_to_queue, "Add to queue") { close(); onAddToQueue(song) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun LibraryScreen(
    destination: LibraryDestination,
    songs: List<Song>,
    playbackHistory: List<PlaybackHistoryEntry>,
    historyVisible: Boolean,
    onToggleHistory: () -> Unit,
    hasNowPlaying: Boolean,
    activeSong: Song?,
    favoriteIds: Set<Long>,
    personalizedRecommendations: List<Song>,
    smartMixes: List<SmartMix>,
    playlists: List<UserPlaylist>,
    layout: LibraryLayout,
    pinnedPlaylistIds: Set<Long>,
    onNavigate: (LibraryDestination) -> Unit,
    onBack: () -> Unit,
    onToggleLayout: () -> Unit,
    onPlay: (Song, List<Song>, String?) -> Unit,
    onShuffle: (List<Song>, String?) -> Unit,
    onSetFavorites: (Set<Long>, Boolean) -> Unit,
    onTogglePin: (Long) -> Unit,
    onRequestCreatePlaylist: (Long?) -> Unit,
    onRequestRenamePlaylist: (UserPlaylist) -> Unit,
    onRequestDeletePlaylist: (UserPlaylist) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddSongToPlaylist: (Long, List<Long>, (Boolean) -> Unit) -> Unit,
    onRemoveSongFromPlaylist: (Long, Long) -> Unit,
) {
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var mainMenuSong by remember { mutableStateOf<Song?>(null) }
    var openPlaylistMenu by remember { mutableStateOf<Long?>(null) }
    var openAlbumMenu by remember { mutableStateOf<String?>(null) }
    var openArtistMenu by remember { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var sortMode by rememberSaveable(destination.page) { mutableStateOf("Recently added") }
    var artistsLayout by rememberSaveable { mutableStateOf(LibraryLayout.Grid) }
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val losslessCandidates = remember(songs) {
        songs.filter(::isLosslessAudioSource).sortedByDescending { it.sampleRate ?: 0 }
    }
    val immediatelyKnownLosslessSongs = remember(losslessCandidates) {
        losslessCandidates.filter { song ->
            song.sampleRate?.let { it in 1 until 48_000 } == true
        }
    }
    var audioQualitySongs by remember(songs) {
        mutableStateOf(
            immediatelyKnownLosslessSongs.takeIf { it.isNotEmpty() }
                ?.let { mapOf("Lossless" to it) }
                .orEmpty(),
        )
    }
    val songsById = remember(songs) { songs.associateBy(Song::id) }
    val recent = remember(songs) { songs.sortedByDescending(Song::modifiedAt).take(6) }
    val featuredSongs = remember(songs, personalizedRecommendations) {
        recommendationOrderedSongs(songs, personalizedRecommendations).take(6)
    }
    val madeForYouSongs = remember(smartMixes) {
        smartMixes.firstOrNull { it.id == "made-for-you" }?.songs?.take(HOME_STACK_MIX_SONG_LIMIT).orEmpty()
    }
    LaunchedEffect(songs, appContext) {
        val groupedSongs = mutableMapOf<String, MutableList<Song>>()
        immediatelyKnownLosslessSongs.forEach { song ->
            groupedSongs.getOrPut("Lossless") { mutableListOf() }.add(song)
        }
        audioQualitySongs = groupedSongs.mapValues { (_, tracks) -> tracks.toList() }

        losslessCandidates.forEach { song ->
            currentCoroutineContext().ensureActive()
            val details = try {
                withContext(Dispatchers.IO) { readAudioQualityDetails(appContext, song) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return@forEach
            if (details.qualityName == "Lossless" || details.qualityName == "Hi-Res Lossless") {
                groupedSongs.values.forEach { tracks -> tracks.removeAll { it.id == song.id } }
                groupedSongs.getOrPut(details.qualityName) { mutableListOf() }.add(song)
                audioQualitySongs = groupedSongs.mapValues { (_, tracks) -> tracks.toList() }
            }
        }
    }
    val permanentPlaylistSongs = mapOf(
        MADE_FOR_YOU_PLAYLIST_ID to madeForYouSongs,
        LOSSLESS_PLAYLIST_ID to audioQualitySongs["Lossless"].orEmpty(),
        HI_RES_LOSSLESS_PLAYLIST_ID to audioQualitySongs["Hi-Res Lossless"].orEmpty(),
    )
    val permanentPlaylistTitles = mapOf(
        MADE_FOR_YOU_PLAYLIST_ID to "Made for you",
        LOSSLESS_PLAYLIST_ID to "Lossless",
        HI_RES_LOSSLESS_PLAYLIST_ID to "Hi-Res Lossless",
    )
    val playlist = playlists.firstOrNull { it.id == destination.itemId }
    val collectionSongs = remember(destination, songs, favoriteIds, playlists, permanentPlaylistSongs) {
        when (destination.page) {
            LibraryPage.Liked -> songs.filter { it.id in favoriteIds }
            LibraryPage.Songs -> songs
            LibraryPage.Playlist -> permanentPlaylistSongs[destination.itemId]
                ?: playlist?.songIds.orEmpty().mapNotNull { id -> songs.firstOrNull { it.id == id } }
            LibraryPage.Artist -> songs.filter { item -> item.artistNames().any { it.equals(destination.title, ignoreCase = true) } }
            LibraryPage.Album -> songs.filter { it.album == destination.title && (it.albumArtist ?: it.artist) == destination.subtitle }
            else -> emptyList()
        }
    }
    val collectionTitle = when (destination.page) {
        LibraryPage.Liked -> "Liked Songs"
        LibraryPage.Songs -> "Songs"
        LibraryPage.Playlist -> permanentPlaylistTitles[destination.itemId] ?: playlist?.name ?: destination.title
        else -> destination.title
    }
    val collectionSubtitle = when (destination.page) {
        LibraryPage.Liked, LibraryPage.Songs -> "${collectionSongs.size} songs"
        LibraryPage.Playlist -> "${collectionSongs.size} songs"
        LibraryPage.Artist -> "${collectionSongs.size} songs"
        LibraryPage.Album -> destination.subtitle
        else -> ""
    }

    Box(Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = historyVisible,
        transitionSpec = { fadeIn(tween(230, easing = WavvMotionEasing)) togetherWith fadeOut(tween(180, easing = WavvMotionEasing)) },
        label = "library history toggle",
    ) { route ->
        if (route) {
            LibraryHistoryScreen(
                history = playbackHistory,
                allSongs = songs,
                hasNowPlaying = hasNowPlaying,
                onToggleHistory = onToggleHistory,
                onPlay = { song, _ -> onPlay(song, listOf(song), null) },
            )
        } else AnimatedContent(
        targetState = destination,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(420, easing = WavvMotionEasing)) { it / 10 }) togetherWith
                (fadeOut(tween(160)) + slideOutHorizontally(tween(340, easing = WavvMotionEasing)) { -it / 10 })
        },
        label = "library route",
    ) { route ->
        when (route.page) {
            LibraryPage.Main -> Column(Modifier.fillMaxSize()) {
                ScreenHeader("Library", trailingContent = {
                    LibraryHistoryAction(historyVisible, onToggleHistory)
                })
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
                                            Modifier.weight(1f).combinedClickable(
                                                role = Role.Button,
                                                onClick = { onPlay(song, listOf(song), null) },
                                                onLongClickLabel = "Song actions",
                                                onLongClick = { mainMenuSong = song },
                                            ),
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
                    items(recent, key = Song::id) { item ->
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                role = Role.Button,
                                onClick = { onPlay(item, listOf(item), null) },
                                onLongClickLabel = "Song actions",
                                onLongClick = { mainMenuSong = item },
                            ).padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AlbumArt(item, Modifier.size(46.dp), item.title, 8.dp)
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.artist, color = Color.White.copy(.4f), fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.2f), modifier = Modifier.size(16.dp))
                        }
                    }
                    if (songs.isEmpty()) item { LibraryEmpty("No music in your library", "Choose a music source to add tracks.") }
                }
            }

            LibraryPage.Playlists, LibraryPage.Artists, LibraryPage.Albums -> {
                val libraryBrowseSongs = remember(songs, route.page, sortMode, personalizedRecommendations) {
                    val alphabetic = when (route.page) {
                        LibraryPage.Artists -> songs.sortedBy { it.mainArtistName().lowercase() }
                        LibraryPage.Albums -> songs.sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { (it.albumArtist ?: it.artist).lowercase() })
                        else -> songs
                    }
                    if (sortMode == "For You" && route.page != LibraryPage.Playlists) {
                        recommendationOrderedSongs(alphabetic, personalizedRecommendations)
                    } else alphabetic
                }
                val cards = when (route.page) {
                    LibraryPage.Playlists -> buildList {
                        add(permanentPlaylistCard(MADE_FOR_YOU_PLAYLIST_ID, "Made for you", madeForYouSongs, 0, onNavigate))
                        audioQualitySongs["Lossless"]?.takeIf { it.isNotEmpty() }?.let { tracks ->
                            add(permanentPlaylistCard(LOSSLESS_PLAYLIST_ID, "Lossless", tracks, 1, onNavigate))
                        }
                        audioQualitySongs["Hi-Res Lossless"]?.takeIf { it.isNotEmpty() }?.let { tracks ->
                            add(permanentPlaylistCard(HI_RES_LOSSLESS_PLAYLIST_ID, "Hi-Res Lossless", tracks, 2, onNavigate))
                        }
                        addAll(playlists.sortedWith(
                            compareByDescending<UserPlaylist> { it.id in pinnedPlaylistIds }.thenByDescending(UserPlaylist::updatedAt),
                        ).map { item ->
                            val playlistTracks = item.songIds.mapNotNull(songsById::get)
                            val priority = when {
                                item.id in pinnedPlaylistIds -> 2
                                playlistTracks.isNotEmpty() && playlistTracks.all { it.id in favoriteIds } -> 1
                                else -> 0
                            }
                            LibraryCard(
                                item.id.toString(), item.name, "${item.songIds.size} songs",
                                playlistArtworkSong(item.songIds, songsById),
                                priority = priority,
                                playlistSongs = playlistTracks,
                            ) { onNavigate(LibraryDestination(LibraryPage.Playlist, item.id, item.name)) }
                        })
                    }
                    LibraryPage.Artists -> libraryBrowseSongs
                        .flatMap { item -> item.artistNames().map { artist -> artist to item } }
                        .groupBy({ it.first }, { it.second }).entries
                        .map { (artist, tracks) -> LibraryCard(
                            artist,
                            artist,
                            "${tracks.size} songs",
                            tracks.firstOrNull(),
                            priority = if (tracks.isNotEmpty() && tracks.all { it.id in favoriteIds }) 1 else 0,
                        ) {
                            onNavigate(LibraryDestination(LibraryPage.Artist, title = artist))
                        } }
                    else -> libraryBrowseSongs.groupBy { it.album to (it.albumArtist ?: it.artist) }.entries
                        .map { (key, tracks) -> LibraryCard(
                            "${key.first}\u0000${key.second}",
                            key.first,
                            key.second,
                            tracks.firstOrNull(),
                            priority = if (tracks.isNotEmpty() && tracks.all { it.id in favoriteIds }) 1 else 0,
                        ) {
                            onNavigate(LibraryDestination(LibraryPage.Album, title = key.first, subtitle = key.second))
                        } }
                }
                Column(Modifier.fillMaxSize()) {
                    val browseLayout = if (route.page == LibraryPage.Artists) artistsLayout else layout
                    val toggleBrowseLayout: () -> Unit = {
                        if (route.page == LibraryPage.Artists) {
                            artistsLayout = if (artistsLayout == LibraryLayout.Grid) LibraryLayout.List else LibraryLayout.Grid
                        } else {
                            onToggleLayout()
                        }
                    }
                    LibraryBrowseHeader(
                        title = route.title.ifBlank { route.page.name.lowercase().replaceFirstChar(Char::uppercase) },
                        fontFamily = expressiveFontFor(route.page),
                        showLayout = true,
                        layout = browseLayout,
                        onBack = onBack,
                        onToggleLayout = toggleBrowseLayout,
                        showAdd = route.page == LibraryPage.Playlists,
                        showSort = route.page in setOf(LibraryPage.Playlists, LibraryPage.Artists, LibraryPage.Albums),
                        groupActionsInPill = route.page in setOf(LibraryPage.Playlists, LibraryPage.Artists, LibraryPage.Albums),
                        addContentDescription = if (activeSong == null) "Create playlist" else "Add current song to playlist",
                        onAdd = {
                            if (activeSong != null) songForPlaylist = activeSong
                            else onRequestCreatePlaylist(null)
                        },
                        onSort = { sortMenu = true },
                    )
                    val orderedCards = remember(cards, sortMode, personalizedRecommendations) {
                        when (sortMode) {
                            "Title A–Z" -> cards.sortedWith(compareByDescending<LibraryCard> { it.priority }.thenBy { it.title.lowercase() })
                            "Artist or creator" -> cards.sortedWith(compareByDescending<LibraryCard> { it.priority }.thenBy { it.subtitle.lowercase() })
                            else -> cards.sortedByDescending { it.priority }
                        }
                    }
                    val cardsPerRow = if (route.page == LibraryPage.Artists) 3 else 2
                    val cardRows = remember(orderedCards, cardsPerRow) { orderedCards.chunked(cardsPerRow) }
                    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = if (hasNowPlaying) 260.dp else 176.dp)) {
                        if (browseLayout == LibraryLayout.Grid) {
                            items(cardRows.size) { rowIndex ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    cardRows[rowIndex].forEach { card ->
                                        val artistCard = route.page == LibraryPage.Artists
                                        val playlist = if (route.page == LibraryPage.Playlists && !card.permanent) {
                                            card.id.toLongOrNull()?.let { id -> playlists.firstOrNull { it.id == id } }
                                        } else null
                                        val albumSong = if (route.page == LibraryPage.Albums) card.artSong else null
                                        val artistSong = if (route.page == LibraryPage.Artists) card.artSong else null
                                        val cardClickModifier = when {
                                            playlist != null -> Modifier.combinedClickable(
                                                role = Role.Button,
                                                onClick = card.onClick,
                                                onLongClickLabel = "Playlist options",
                                                onLongClick = { openPlaylistMenu = playlist.id },
                                            )
                                            albumSong != null -> Modifier.combinedClickable(
                                                role = Role.Button,
                                                onClick = card.onClick,
                                                onLongClickLabel = "Album options",
                                                onLongClick = { openAlbumMenu = card.id },
                                            )
                                            artistSong != null -> Modifier.combinedClickable(
                                                role = Role.Button,
                                                onClick = card.onClick,
                                                onLongClickLabel = "Artist options",
                                                onLongClick = { openArtistMenu = card.id },
                                            )
                                            else -> Modifier.clickable(onClick = card.onClick)
                                        }
                                        Column(
                                            Modifier.weight(1f).then(cardClickModifier),
                                            horizontalAlignment = if (artistCard) Alignment.CenterHorizontally else Alignment.Start,
                                        ) {
                                            val artworkModifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                                .then(if (artistCard) Modifier.clip(CircleShape) else Modifier)
                                            if (route.page == LibraryPage.Playlists) {
                                                PlaylistArtwork(card.playlistSongs, card.artSong, artworkModifier, card.title, 13.dp)
                                            } else {
                                                AlbumArt(card.artSong?.albumArtUri, artworkModifier, card.title, 13.dp, card.artSong?.uri)
                                            }
                                            if (playlist != null || albumSong != null) {
                                                Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        card.title,
                                                        Modifier.weight(1f),
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = expressiveFontFor(route.page),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (playlist != null) {
                                                        Box {
                                                            Box(Modifier.size(34.dp).clickable(role = Role.Button) { openPlaylistMenu = playlist.id }, contentAlignment = Alignment.Center) {
                                                                FigmaMoreIcon(30.dp)
                                                            }
                                                            if (openPlaylistMenu == playlist.id) {
                                                                LibraryPlaylistActionsSheet(
                                                                    playlist = playlist,
                                                                    playlistSongs = card.playlistSongs,
                                                                    artworkSong = card.artSong ?: songs.firstOrNull(),
                                                                    pinned = playlist.id in pinnedPlaylistIds,
                                                                    onDismiss = { openPlaylistMenu = null },
                                                                    onRename = { openPlaylistMenu = null; onRequestRenamePlaylist(playlist) },
                                                                    onTogglePin = { openPlaylistMenu = null; onTogglePin(playlist.id) },
                                                                    onDelete = { openPlaylistMenu = null; onRequestDeletePlaylist(playlist) },
                                                                )
                                                            }
                                                        }
                                                    } else if (albumSong != null) {
                                                        Box {
                                                            Box(Modifier.size(34.dp).clickable(role = Role.Button) { openAlbumMenu = card.id }, contentAlignment = Alignment.Center) {
                                                                FigmaMoreIcon(30.dp)
                                                            }
                                                            if (openAlbumMenu == card.id) {
                                                                LibraryAlbumActionsSheet(
                                                                    albumTitle = card.title,
                                                                    albumArtist = card.subtitle,
                                                                    song = albumSong,
                                                                    onDismiss = { openAlbumMenu = null },
                                                                    onPlayNext = onPlayNext,
                                                                    onAddToQueue = onAddToQueue,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Text(card.subtitle, color = Color.White.copy(.48f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            } else {
                                                Text(
                                                    card.title,
                                                    Modifier.fillMaxWidth().padding(top = 7.dp),
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = expressiveFontFor(route.page),
                                                    maxLines = if (artistCard) 2 else 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = if (artistCard) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                                                )
                                                if (!artistCard) Text(card.subtitle, color = Color.White.copy(.48f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            if (artistSong != null && openArtistMenu == card.id) {
                                                LibraryArtistActionsSheet(
                                                    artistName = card.title,
                                                    songCount = card.subtitle,
                                                    song = artistSong,
                                                    onDismiss = { openArtistMenu = null },
                                                    onPlayNext = onPlayNext,
                                                    onAddToQueue = onAddToQueue,
                                                )
                                            }
                                        }
                                    }
                                    if (rowIndex == cardRows.lastIndex && cardRows[rowIndex].size < cardsPerRow) {
                                        repeat(cardsPerRow - cardRows[rowIndex].size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        } else {
                            items(orderedCards, key = LibraryCard::id) { card ->
                                val artistCard = route.page == LibraryPage.Artists
                                val playlist = if (route.page == LibraryPage.Playlists && !card.permanent) {
                                    card.id.toLongOrNull()?.let { id -> playlists.firstOrNull { it.id == id } }
                                } else null
                                val albumSong = if (route.page == LibraryPage.Albums) card.artSong else null
                                val artistSong = if (route.page == LibraryPage.Artists) card.artSong else null
                                val cardClickModifier = when {
                                    playlist != null -> Modifier.combinedClickable(
                                        role = Role.Button,
                                        onClick = card.onClick,
                                        onLongClickLabel = "Playlist options",
                                        onLongClick = { openPlaylistMenu = playlist.id },
                                    )
                                    albumSong != null -> Modifier.combinedClickable(
                                        role = Role.Button,
                                        onClick = card.onClick,
                                        onLongClickLabel = "Album options",
                                        onLongClick = { openAlbumMenu = card.id },
                                    )
                                    artistSong != null -> Modifier.combinedClickable(
                                        role = Role.Button,
                                        onClick = card.onClick,
                                        onLongClickLabel = "Artist options",
                                        onLongClick = { openArtistMenu = card.id },
                                    )
                                    else -> Modifier.clickable(onClick = card.onClick)
                                }
                                Row(Modifier.fillMaxWidth().then(cardClickModifier).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    val artworkModifier = Modifier.size(58.dp)
                                        .then(if (artistCard) Modifier.clip(CircleShape) else Modifier)
                                    if (route.page == LibraryPage.Playlists) {
                                        PlaylistArtwork(card.playlistSongs, card.artSong, artworkModifier, card.title, 10.dp)
                                    } else {
                                        AlbumArt(card.artSong?.albumArtUri, artworkModifier, card.title, 10.dp, card.artSong?.uri)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(card.title, Modifier.weight(1f), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = expressiveFontFor(route.page), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (playlist != null || albumSong != null || artistSong != null) {
                                                Box {
                                                    Box(Modifier.size(36.dp).clickable(role = Role.Button) {
                                                        when {
                                                            playlist != null -> openPlaylistMenu = playlist.id
                                                            albumSong != null -> openAlbumMenu = card.id
                                                            artistSong != null -> openArtistMenu = card.id
                                                        }
                                                    }, contentAlignment = Alignment.Center) { FigmaMoreIcon(30.dp) }
                                                    if (playlist != null && openPlaylistMenu == playlist.id) {
                                                        LibraryPlaylistActionsSheet(
                                                            playlist = playlist,
                                                            playlistSongs = card.playlistSongs,
                                                            artworkSong = card.artSong ?: songs.firstOrNull(),
                                                            pinned = playlist.id in pinnedPlaylistIds,
                                                            onDismiss = { openPlaylistMenu = null },
                                                            onRename = { openPlaylistMenu = null; onRequestRenamePlaylist(playlist) },
                                                            onTogglePin = { openPlaylistMenu = null; onTogglePin(playlist.id) },
                                                            onDelete = { openPlaylistMenu = null; onRequestDeletePlaylist(playlist) },
                                                        )
                                                    } else if (albumSong != null && openAlbumMenu == card.id) {
                                                        LibraryAlbumActionsSheet(
                                                            albumTitle = card.title,
                                                            albumArtist = card.subtitle,
                                                            song = albumSong,
                                                            onDismiss = { openAlbumMenu = null },
                                                            onPlayNext = onPlayNext,
                                                            onAddToQueue = onAddToQueue,
                                                        )
                                                    } else if (artistSong != null && openArtistMenu == card.id) {
                                                        LibraryArtistActionsSheet(
                                                            artistName = card.title,
                                                            songCount = card.subtitle,
                                                            song = artistSong,
                                                            onDismiss = { openArtistMenu = null },
                                                            onPlayNext = onPlayNext,
                                                            onAddToQueue = onAddToQueue,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (!artistCard) Text(card.subtitle, color = Color.White.copy(.44f), fontSize = 12.sp)
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
                    page = route.page,
                    title = if (route == destination) collectionTitle else route.title,
                    subtitle = if (route == destination) collectionSubtitle else route.subtitle,
                    songs = if (route == destination) collectionSongs else when (route.page) {
                        LibraryPage.Liked -> songs.filter { it.id in favoriteIds }
                        LibraryPage.Songs -> songs
                        LibraryPage.Playlist -> permanentPlaylistSongs[route.itemId]
                            ?: playlists.firstOrNull { it.id == route.itemId }?.songIds.orEmpty()
                                .mapNotNull { id -> songs.firstOrNull { it.id == id } }
                        LibraryPage.Artist -> songs.filter { item -> item.artistNames().any { it.equals(route.title, ignoreCase = true) } }
                        LibraryPage.Album -> songs.filter { it.album == route.title && (it.albumArtist ?: it.artist) == route.subtitle }
                    },
                    playlists = playlists,
                    favoriteIds = favoriteIds,
                    personalizedRecommendations = personalizedRecommendations,
                    sortMode = sortMode,
                    hasNowPlaying = hasNowPlaying,
                    isPlaylist = route.page == LibraryPage.Playlist && route.itemId !in PERMANENT_PLAYLIST_IDS,
                    isDetail = route.isDetail,
                    showCollectionFavoriteAction = route.page != LibraryPage.Liked &&
                        !(route.page == LibraryPage.Playlist && route.itemId in PERMANENT_PLAYLIST_IDS),
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
                    onNavigateToAlbum = { item -> onNavigate(LibraryDestination(LibraryPage.Album, title = item.album, subtitle = item.albumArtist ?: item.artist)) },
                    onNavigateToArtist = { item -> onNavigate(LibraryDestination(LibraryPage.Artist, title = item.mainArtistName())) },
                    onSort = { sortMenu = true },
                )
            }
        }
    }
    }
    }

    if (sortMenu) {
        val sortArtworkSong = if (destination.page == LibraryPage.Playlists) {
            playlists.asSequence().flatMap { it.songIds.asSequence() }.mapNotNull(songsById::get).firstOrNull() ?: songs.firstOrNull()
        } else {
            collectionSongs.firstOrNull() ?: songs.firstOrNull()
        }
        WavvBottomSheet(
            onDismissRequest = { sortMenu = false },
            maxHeight = 320.dp,
            artworkSong = sortArtworkSong,
        ) { close ->
            Text("Sort by", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            val sortModes = listOf("Recently added", "Title A–Z", "Artist or creator") +
                (if (destination.page == LibraryPage.Album) listOf("Album order") else emptyList()) +
                if (destination.page in setOf(LibraryPage.Liked, LibraryPage.Songs, LibraryPage.Artists, LibraryPage.Albums) && personalizedRecommendations.isNotEmpty()) {
                    listOf("For You")
                } else {
                    emptyList()
                }
            sortModes.forEachIndexed { index, mode ->
                val selected = sortMode == mode
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { sortMode = mode; close() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(mode, color = if (selected) WavvPink else Color.White.copy(.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = WavvPink, modifier = Modifier.size(17.dp))
                }
                if (index != sortModes.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(Color.White.copy(.07f)))
                }
            }
        }
    }
    mainMenuSong?.let { selected ->
        LibrarySongActionsSheet(
            song = selected,
            isFavorite = selected.id in favoriteIds,
            onDismiss = { mainMenuSong = null },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onToggleFavorite = { item -> onSetFavorites(setOf(item.id), item.id !in favoriteIds) },
            onRequestAddToPlaylist = { item -> songForPlaylist = item },
            onNavigateToAlbum = { item ->
                onNavigate(LibraryDestination(LibraryPage.Album, title = item.album, subtitle = item.albumArtist ?: item.artist))
            },
            onNavigateToArtist = { item ->
                onNavigate(LibraryDestination(LibraryPage.Artist, title = item.mainArtistName()))
            },
        )
    }
    songForPlaylist?.let { selected ->
        AddToPlaylistSheet(
            selectedSongs = listOf(selected),
            allSongs = songs,
            playlists = playlists,
            onAddSongToPlaylist = onAddSongToPlaylist,
            onCreatePlaylist = onRequestCreatePlaylist,
            onDismiss = { songForPlaylist = null },
        )
    }
}

@Composable
private fun LibrarySongActionsSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    showFavoriteAction: Boolean = true,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    WavvBottomSheet(onDismissRequest = onDismiss, artworkSong = song) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            AlbumArt(song, Modifier.size(58.dp), song.title, 12.dp)
            Column(Modifier.weight(1f)) {
                Text("Song actions", color = Color.White.copy(.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(song.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetActionRow(R.drawable.action_play_next, "Play next") { close(); onPlayNext(song) }
        SheetActionRow(R.drawable.action_add_to_queue, "Add to queue") { close(); onAddToQueue(song) }
        if (showFavoriteAction) {
            SheetActionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Remove from favorites" else "Add to favorites") {
                close(); onToggleFavorite(song)
            }
        }
        SheetActionRow(Icons.Default.Add, "Add to playlist") { close(); onRequestAddToPlaylist(song) }
        SheetActionRow(Icons.Default.Album, "Go to album") { close(); onNavigateToAlbum(song) }
        SheetActionRow(Icons.Default.Mic, "Go to artist") { close(); onNavigateToArtist(song) }
        onRemoveFromPlaylist?.let { remove ->
            SheetActionRow(Icons.Default.Delete, "Remove from playlist") { close(); remove() }
        }
    }
}

@Composable
private fun LibraryCollectionActionsSheet(
    title: String,
    subtitle: String,
    artworkSong: Song?,
    isPlaylist: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onRenamePlaylist: () -> Unit,
    onTogglePin: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
) {
    WavvBottomSheet(onDismissRequest = onDismiss, artworkSong = artworkSong) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            artworkSong?.let { AlbumArt(it, Modifier.size(58.dp), title, 12.dp) }
            Column(Modifier.weight(1f)) {
                Text("Collection options", color = Color.White.copy(.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (isPlaylist) {
            SheetActionRow(Icons.Default.Edit, "Edit playlist") { close(); onRenamePlaylist() }
            SheetActionRow(Icons.Default.PushPin, if (pinned) "Unpin playlist" else "Pin playlist") { close(); onTogglePin() }
            SheetActionRow(Icons.Default.Delete, "Delete playlist") { close(); onDeletePlaylist() }
        }
        artworkSong?.let { song ->
            SheetActionRow(R.drawable.action_play_next, "Play next") { close(); onPlayNext(song) }
            SheetActionRow(R.drawable.action_add_to_queue, "Add to queue") { close(); onAddToQueue(song) }
        }
    }
}

@Composable
private fun LibraryHistoryScreen(
    history: List<PlaybackHistoryEntry>,
    allSongs: List<Song>,
    hasNowPlaying: Boolean,
    onToggleHistory: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
) {
    val groupedHistory = remember(history) {
        history.groupBy { localDayStart(it.playedAt) }
            .toSortedMap(compareByDescending { it })
    }
    val surfaceShape = RoundedCornerShape(26.dp)
    val density = LocalDensity.current
    val navigationBarInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val surfaceBottomInset by animateDpAsState(
        targetValue = navigationBarInset + if (hasNowPlaying) 164.dp else 72.dp,
        animationSpec = tween(360, easing = WavvMotionEasing),
        label = "history surface available height",
    )
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ScreenHeader("History", showAmbient = false, trailingContent = {
            LibraryHistoryAction(historyVisible = true, onToggleHistory = onToggleHistory)
        })
        if (history.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = surfaceBottomInset)
                    .clip(surfaceShape).background(Color(0xFF242428)),
                contentAlignment = Alignment.Center,
            ) {
                LibraryEmpty("No listening history", "Songs you play will appear here.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = surfaceBottomInset)
                    .clip(surfaceShape).background(Color(0xFF242428)),
                contentPadding = PaddingValues(top = 10.dp, bottom = 14.dp),
            ) {
                groupedHistory.forEach { (day, entries) ->
                    item(key = "history-day-$day") {
                        Text(
                            historyDayLabel(day),
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            color = Color.White.copy(.66f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = .2.sp,
                        )
                    }
                    items(entries, key = PlaybackHistoryEntry::id) { entry ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPlay(entry.song, allSongs) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AlbumArt(
                                entry.song,
                                Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
                                entry.song.title,
                                9.dp,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.song.artist, color = Color.White.copy(.48f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(historyTimeLabel(entry.playedAt), color = Color.White.copy(.4f), fontSize = 11.sp, maxLines = 1)
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 76.dp, end = 16.dp).height(1.dp).background(Color.White.copy(.085f)))
                    }
                }
            }
        }
    }
}

private fun localDayStart(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun historyDayLabel(dayStart: Long): String {
    val today = localDayStart(System.currentTimeMillis())
    val yesterday = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
    val date = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(dayStart))
    return when (dayStart) {
        today -> "Today · $date"
        yesterday -> "Yesterday · $date"
        else -> SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(dayStart))
    }
}

private fun historyTimeLabel(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

private data class LibraryCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val artSong: Song?,
    val priority: Int = 0,
    val permanent: Boolean = false,
    val playlistSongs: List<Song> = emptyList(),
    val onClick: () -> Unit,
)

internal fun playlistArtworkSong(songIds: List<Long>, songsById: Map<Long, Song>): Song? =
    songIds.asSequence().mapNotNull(songsById::get).firstOrNull { !it.albumArtUri.isNullOrBlank() }
        ?: songIds.firstNotNullOfOrNull(songsById::get)

@Composable
private fun PlaylistArtwork(
    songs: List<Song>,
    fallbackSong: Song?,
    modifier: Modifier,
    contentDescription: String,
    radius: Dp,
) {
    val quadrants = remember(songs) { songs.take(4) }
    if (quadrants.size >= 4) {
        Box(modifier.clip(RoundedCornerShape(radius))) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    AlbumArt(quadrants[0], Modifier.weight(1f).fillMaxHeight(), contentDescription, 0.dp)
                    AlbumArt(quadrants[1], Modifier.weight(1f).fillMaxHeight(), contentDescription, 0.dp)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    AlbumArt(quadrants[2], Modifier.weight(1f).fillMaxHeight(), contentDescription, 0.dp)
                    AlbumArt(quadrants[3], Modifier.weight(1f).fillMaxHeight(), contentDescription, 0.dp)
                }
            }
        }
    } else if (fallbackSong != null) {
        AlbumArt(fallbackSong, modifier, contentDescription, radius)
    } else {
        Box(modifier.clip(RoundedCornerShape(radius)).background(WavvPink.copy(.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = contentDescription, tint = WavvPink, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun LibraryBrowseHeader(
    title: String,
    fontFamily: FontFamily,
    showLayout: Boolean,
    layout: LibraryLayout,
    onBack: () -> Unit,
    onToggleLayout: () -> Unit,
    showAdd: Boolean,
    showSort: Boolean,
    groupActionsInPill: Boolean,
    addContentDescription: String,
    onAdd: () -> Unit,
    onSort: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibrarySoftIconButton(
            onClick = onBack,
            contentDescription = "Back to Library",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            modifier = Modifier.size(42.dp),
            iconSize = 23.dp,
            frosted = true,
        )
        Text(title, Modifier.weight(1f).padding(start = 8.dp), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = fontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LibraryBrowseHeaderActions(
            showSort = showSort,
            showLayout = showLayout,
            layout = layout,
            showAdd = showAdd,
            groupedInPill = groupActionsInPill,
            addContentDescription = addContentDescription,
            onSort = onSort,
            onToggleLayout = onToggleLayout,
            onAdd = onAdd,
        )
    }
}

@Composable
private fun LibraryBrowseHeaderActions(
    showSort: Boolean,
    showLayout: Boolean,
    layout: LibraryLayout,
    showAdd: Boolean,
    groupedInPill: Boolean,
    addContentDescription: String,
    onSort: () -> Unit,
    onToggleLayout: () -> Unit,
    onAdd: () -> Unit,
) {
    val buttonSize = if (groupedInPill) 36.dp else 38.dp
    val optionCount = listOf(showSort, showLayout, showAdd).count { it }
    val pillInset = if (optionCount > 2) 5.dp else 8.dp
    val pillGap = if (optionCount > 2) 4.dp else 8.dp
    val content: @Composable RowScope.() -> Unit = {
        if (showSort) LibrarySoftIconButton(
            onClick = onSort,
            contentDescription = "Sort",
            icon = null,
            sortIcon = true,
            modifier = Modifier.size(buttonSize),
            iconSize = 19.dp,
            surfaceAlpha = if (groupedInPill) 0f else .085f,
        )
        if (showLayout) LibrarySoftIconButton(
            onClick = onToggleLayout,
            contentDescription = if (layout == LibraryLayout.Grid) "Switch to list" else "Switch to grid",
            icon = if (layout == LibraryLayout.Grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
            modifier = Modifier.size(buttonSize),
            iconSize = 19.dp,
            surfaceAlpha = if (groupedInPill) 0f else .085f,
        )
        if (showAdd) LibrarySoftIconButton(
            onClick = onAdd,
            contentDescription = addContentDescription,
            icon = Icons.Default.Add,
            modifier = Modifier.size(buttonSize),
            iconSize = 21.dp,
            tint = WavvPink,
            surfaceAlpha = if (groupedInPill) 0f else .085f,
        )
    }
    if (groupedInPill) {
        Box(Modifier.clip(CircleShape)) {
            FrostedBackdrop(
                captureState = remember { AtomicBoolean(false) },
                blurRadiusDp = 22f,
                tint = Color(0x9923262D),
                modifier = Modifier.matchParentSize().clip(CircleShape),
                refreshEnabled = true,
            )
            Row(
                Modifier.clip(CircleShape)
                    .background(Color.White.copy(.045f))
                    .padding(horizontal = pillInset, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(pillGap),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun LibraryHistoryAction(historyVisible: Boolean, onToggleHistory: () -> Unit) {
    LibrarySoftIconButton(
        onClick = onToggleHistory,
        contentDescription = if (historyVisible) "Return to library" else "Listening history",
        icon = if (historyVisible) Icons.Default.LibraryMusic else null,
        iconResource = if (historyVisible) null else R.drawable.ic_library_history,
        modifier = Modifier.size(40.dp),
        iconSize = 28.dp,
    )
}

@Composable
internal fun LibrarySoftIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier.size(40.dp),
    iconSize: Dp = 21.dp,
    tint: Color = Color.White.copy(.9f),
    surfaceAlpha: Float = .085f,
    frosted: Boolean = false,
    enabled: Boolean = true,
    sortIcon: Boolean = false,
    iconResource: Int? = null,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val backdropCaptureState = remember { AtomicBoolean(false) }
    Box(
        modifier.clip(CircleShape)
            .then(if (frosted) Modifier else Modifier.background(Color.White.copy(surfaceAlpha)))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (frosted) {
            FrostedBackdrop(
                captureState = backdropCaptureState,
                blurRadiusDp = 30f,
                tint = Color(0x2223262D),
                modifier = Modifier.matchParentSize().clip(CircleShape),
                refreshEnabled = true,
            )
            Box(Modifier.matchParentSize().background(Color.White.copy(surfaceAlpha)))
        }
        val customIconResource = iconResource ?: if (sortIcon) R.drawable.ic_library_sort_arrows else null
        if (customIconResource != null) {
            Image(
                painter = androidx.compose.ui.res.painterResource(customIconResource),
                contentDescription = contentDescription,
                modifier = Modifier.align(Alignment.Center).size(iconSize),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
            )
        } else {
            icon?.let { vector ->
                Icon(vector, contentDescription = contentDescription, tint = tint, modifier = Modifier.align(Alignment.Center).size(iconSize))
            }
        }
    }
}

@Composable
private fun LibrarySoftMoreButton(onClick: () -> Unit, contentDescription: String) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val backdropCaptureState = remember { AtomicBoolean(false) }
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FrostedBackdrop(
            captureState = backdropCaptureState,
            blurRadiusDp = 30f,
            tint = Color(0x2223262D),
            modifier = Modifier.matchParentSize().clip(CircleShape),
            refreshEnabled = true,
        )
        Box(Modifier.matchParentSize().background(Color.White.copy(.15f)))
        FigmaMoreIcon(30.dp, showSurface = false, dotRadiusScale = 1.6f, dotSpacingScale = 1.4f)
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
internal fun WavvBottomSheet(
    onDismissRequest: () -> Unit,
    maxHeight: Dp = 520.dp,
    artworkSong: Song? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val requestClose = {
        if (!dismissing) {
            dismissing = true
            visible = false
        }
    }
    LaunchedEffect(Unit) { visible = true }
    val density = LocalDensity.current
    val view = LocalView.current
    val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    SideEffect {
        if (artworkSong != null) {
            dialogWindow?.setDimAmount(.46f)
            if (Build.VERSION.SDK_INT >= 31) {
                dialogWindow?.setBackgroundBlurRadius(with(density) { 20.dp.roundToPx() })
            }
        }
    }
    LaunchedEffect(dismissing) {
        if (dismissing) {
            delay(240)
            dismiss()
        }
    }

    Dialog(
        onDismissRequest = requestClose,
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
                        .background(if (artworkSong == null) Color.Black.copy(.5f) else Color.Transparent)
                        .clickable(onClick = requestClose),
                )
            }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                enter = fadeIn(tween(180)) + slideInVertically(tween(280, easing = WavvMotionEasing)) { it },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(240, easing = WavvMotionEasing)) { it },
            ) {
                Box(
                    Modifier.fillMaxWidth().heightIn(max = maxHeight)
                        .clip(RoundedCornerShape(32.dp))
                ) {
                    if (artworkSong == null) {
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(listOf(Color(0xF41C1419), Color(0xF20A0A0E))),
                            ),
                        )
                    } else {
                        AlbumArt(
                            artworkSong,
                            Modifier.matchParentSize().scale(1.18f).blur(34.dp),
                            null,
                            0.dp,
                        )
                        Box(Modifier.matchParentSize().background(Color(0xE8171717)))
                    }
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp)
                            .clickable { },
                    ) {
                        Box(
                            Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable(onClick = requestClose),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(.28f)))
                        }
                        content(requestClose)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddToPlaylistSheet(
    selectedSongs: List<Song>,
    allSongs: List<Song> = selectedSongs,
    playlists: List<UserPlaylist>,
    onAddSongToPlaylist: (Long, List<Long>, (Boolean) -> Unit) -> Unit,
    onCreatePlaylist: (Long?) -> Unit,
    onDismiss: () -> Unit,
    embedded: Boolean = false,
) {
    val songIds = remember(selectedSongs) { selectedSongs.map(Song::id).distinct() }
    val allSongsById = remember(allSongs) { allSongs.associateBy(Song::id) }
    val headerSong = selectedSongs.firstOrNull()
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * .86f).dp.coerceAtLeast(440.dp)
    var searchQuery by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    var createPlaylistAfterClose by remember { mutableStateOf(false) }
    val alreadySavedPlaylistIds = remember(playlists, songIds) {
        playlists.filter { playlist -> songIds.any(playlist.songIds::contains) }.map(UserPlaylist::id).toSet()
    }
    var selectedPlaylistIds by remember(songIds, alreadySavedPlaylistIds) {
        mutableStateOf(
            alreadySavedPlaylistIds,
        )
    }
    val savedPlaylists = remember(playlists, alreadySavedPlaylistIds) {
        playlists.filter { it.id in alreadySavedPlaylistIds }
    }
    val availablePlaylists = remember(playlists, alreadySavedPlaylistIds, searchQuery) {
        playlists.filter { playlist ->
            playlist.id !in alreadySavedPlaylistIds && playlist.name.contains(searchQuery.trim(), ignoreCase = true)
        }.sortedByDescending(UserPlaylist::updatedAt)
    }

    val onSheetDismiss: () -> Unit = {
        if (!isSaving) {
            onDismiss()
            if (createPlaylistAfterClose) onCreatePlaylist(songIds.firstOrNull())
        }
    }
    val sheetContent: @Composable ColumnScope.(close: () -> Unit) -> Unit = { close ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            AddToPlaylistPillAction("Cancel", enabled = !isSaving, onClick = close)
            AddToPlaylistPillAction(
                label = "Done",
                emphasized = true,
                enabled = !isSaving,
                onClick = {
                    val additions = playlists.filter { it.id in selectedPlaylistIds }.mapNotNull { playlist ->
                        val missingIds = songIds.filterNot(playlist.songIds::contains)
                        playlist.id to missingIds
                    }.filter { it.second.isNotEmpty() }
                    if (additions.isEmpty()) {
                        close()
                    } else {
                        isSaving = true
                        saveError = false
                        var remaining = additions.size
                        var failed = false
                        additions.forEach { (playlistId, missingIds) ->
                            onAddSongToPlaylist(playlistId, missingIds) { success ->
                                if (!success) failed = true
                                remaining -= 1
                                if (remaining == 0) {
                                    isSaving = false
                                    saveError = failed
                                    if (!failed) close()
                                }
                            }
                        }
                    }
                },
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(15.dp), color = WavvPink, strokeWidth = 2.dp)
                else Text("Done", color = WavvPink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (savedPlaylists.isEmpty()) "Save in" else "Saved in",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            AddToPlaylistPillAction(
                "New playlist",
                emphasized = true,
                enabled = !isSaving,
                onClick = {
                    if (embedded) {
                        close()
                        onCreatePlaylist(songIds.firstOrNull())
                    } else {
                        createPlaylistAfterClose = true
                        close()
                    }
                },
            )
        }

        if (savedPlaylists.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 168.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                savedPlaylists.forEach { playlist ->
                    PlaylistPickerRow(
                        playlist = playlist,
                        allSongsById = allSongsById,
                        fallbackSong = playlistArtworkSong(playlist.songIds, allSongsById) ?: headerSong,
                        selected = true,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp).clip(RoundedCornerShape(16.dp))) {
            val searchBackdrop = remember { AtomicBoolean(false) }
            FrostedBackdrop(
                captureState = searchBackdrop,
                blurRadiusDp = 24f,
                tint = Color(0x9923262D),
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)),
            )
            Box(Modifier.matchParentSize().background(Color.White.copy(.075f)))
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search playlists", tint = Color.White.copy(.74f), modifier = Modifier.size(21.dp))
                Box(Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) Text("Find playlist", color = Color.White.copy(.48f), fontSize = 14.sp)
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        enabled = !isSaving,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(WavvPink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, enabled = !isSaving, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.White.copy(.62f), modifier = Modifier.size(17.dp))
                    }
                }
            }
        }

        if (saveError) {
            Text("Some playlists could not be updated. Try again.", color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (availablePlaylists.isEmpty()) {
                item(key = "playlist-empty") {
                    Text(
                        when {
                            playlists.isEmpty() -> "Create a playlist to save your music."
                            searchQuery.isNotBlank() -> "No matching playlists"
                            savedPlaylists.isNotEmpty() -> "Saved in all playlists"
                            else -> "No matching playlists"
                        },
                        color = Color.White.copy(.5f),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                items(availablePlaylists, key = { "available-${it.id}" }) { playlist ->
                    PlaylistPickerRow(
                        playlist = playlist,
                        allSongsById = allSongsById,
                        fallbackSong = playlistArtworkSong(playlist.songIds, allSongsById) ?: headerSong,
                        selected = playlist.id in selectedPlaylistIds,
                        enabled = !isSaving,
                        onClick = {
                            selectedPlaylistIds = if (playlist.id in selectedPlaylistIds) {
                                selectedPlaylistIds - playlist.id
                            } else {
                                selectedPlaylistIds + playlist.id
                            }
                        },
                    )
                }
            }
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)) {
            sheetContent(onDismiss)
        }
    } else {
        WavvBottomSheet(
            onDismissRequest = onSheetDismiss,
            maxHeight = maxSheetHeight,
            artworkSong = headerSong,
        ) { close -> sheetContent(close) }
    }
}

@Composable
private fun AddToPlaylistPillAction(
    label: String,
    enabled: Boolean,
    emphasized: Boolean = false,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val backdropCaptureState = remember { AtomicBoolean(false) }
    Box(
        Modifier.height(38.dp).clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FrostedBackdrop(
            captureState = backdropCaptureState,
            blurRadiusDp = 24f,
            tint = Color(0x5523262D),
            modifier = Modifier.matchParentSize().clip(CircleShape),
            refreshEnabled = true,
        )
        Box(Modifier.matchParentSize().background(Color.White.copy(if (emphasized) .11f else .075f)))
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (content != null) content() else Text(
                label,
                color = if (emphasized) WavvPink else Color.White.copy(.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlaylistPickerRow(
    playlist: UserPlaylist,
    allSongsById: Map<Long, Song>,
    fallbackSong: Song?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val playlistSongs = remember(playlist.songIds, allSongsById) { playlist.songIds.mapNotNull(allSongsById::get) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        PlaylistArtwork(playlistSongs, fallbackSong, Modifier.size(58.dp), playlist.name, 11.dp)
        Column(Modifier.weight(1f)) {
            Text(playlist.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.songIds.size} ${if (playlist.songIds.size == 1) "song" else "songs"}",
                color = Color.White.copy(.5f),
                fontSize = 13.sp,
            )
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (selected) WavvPink else Color.White.copy(.09f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (selected) "Saved in ${playlist.name}" else "Add to ${playlist.name}",
                    tint = if (selected) Color(0xFF151015) else Color.White.copy(.88f),
                    modifier = Modifier.size(if (selected) 19.dp else 22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCollectionScreen(
    page: LibraryPage,
    title: String,
    subtitle: String,
    songs: List<Song>,
    playlists: List<UserPlaylist>,
    favoriteIds: Set<Long>,
    personalizedRecommendations: List<Song>,
    sortMode: String,
    hasNowPlaying: Boolean,
    isPlaylist: Boolean,
    isDetail: Boolean,
    showCollectionFavoriteAction: Boolean,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>, String?) -> Unit,
    onShuffle: (List<Song>, String?) -> Unit,
    onSetFavorites: (Set<Long>, Boolean) -> Unit,
    onRequestRenamePlaylist: () -> Unit,
    onRequestDeletePlaylist: () -> Unit,
    onTogglePin: () -> Unit,
    pinned: Boolean,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onAddSongToPlaylist: (Long, List<Long>, (Boolean) -> Unit) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onSort: () -> Unit,
) {
    var featureOffset by remember(title, songs) { mutableStateOf(0) }
    var favoriteSparkleTrigger by remember(title, songs) { mutableIntStateOf(0) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var collectionOptionsVisible by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }
    val displaySongs = remember(songs, sortMode, personalizedRecommendations) {
        when (sortMode) {
            "Title A–Z" -> songs.sortedBy { it.title.lowercase() }
            "Artist or creator" -> songs.sortedBy { it.artist.lowercase() }
            "Album order" -> albumTrackOrder(songs)
            "For You" -> recommendationOrderedSongs(songs, personalizedRecommendations)
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
        val height = minOf(620.dp, maxWidth + 170.dp)
        val artworkHeight = minOf(maxWidth, 480.dp)
        SongListArtworkBackdrop(featured, Modifier.matchParentSize())
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
                            Box(Modifier.fillMaxWidth().height(artworkHeight).statusBarsPadding().edgeBlendedArtworkMask()) {
                                AlbumArt(artSong, Modifier.fillMaxSize(), artSong.title, 0.dp)
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
                    }
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = expressiveFontFor(page), letterSpacing = if (page == LibraryPage.Album) .15.sp else (-.55).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, Modifier.padding(top = 3.dp), color = Color.White.copy(.58f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        featured?.let { Text("${it.title} · ${it.artist}", Modifier.padding(top = 4.dp), color = Color.White.copy(.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconButton(onClick = { onShuffle(displaySongs, title); featureOffset = displaySongs.indices.filterNot { it == featureOffset }.randomOrNull() ?: 0 }, enabled = displaySongs.isNotEmpty(), modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.12f))) {
                                Image(androidx.compose.ui.res.painterResource(R.drawable.queue_mode_shuffle), "Shuffle $title", Modifier.size(23.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
                            }
                            Button(
                                onClick = { displaySongs.firstOrNull()?.let { onPlay(it, displaySongs, title) } },
                                enabled = displaySongs.isNotEmpty(),
                                modifier = Modifier.size(48.dp).semantics { contentDescription = "Play $title" },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111111)),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Image(androidx.compose.ui.res.painterResource(R.drawable.play_solid), null, Modifier.size(19.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF111111)))
                            }
                            if (showCollectionFavoriteAction && page != LibraryPage.Songs) {
                                val allFavorites = displaySongs.isNotEmpty() && displaySongs.all { it.id in favoriteIds }
                                IconButton(
                                    onClick = {
                                        if (!allFavorites) favoriteSparkleTrigger++
                                        onSetFavorites(displaySongs.map(Song::id).toSet(), !allFavorites)
                                    },
                                    enabled = displaySongs.isNotEmpty(),
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.12f))
                                        .semantics {
                                            contentDescription = if (allFavorites) "Remove $title from favorites" else "Add $title to favorites"
                                            stateDescription = if (allFavorites) "All tracks liked" else "Not all tracks liked"
                                        },
                                ) {
                                    FigmaFavouriteIcon(
                                        allFavorites,
                                        24.dp,
                                        showIconSurface = false,
                                        sparkleTrigger = favoriteSparkleTrigger,
                                    )
                                }
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
                    Modifier.fillMaxWidth().combinedClickable(role = Role.Button, onClick = { onPlay(item, displaySongs, title) }, onLongClick = { menuSong = item })
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
                    }
                }
                Hairline()
            }
            if (songs.isEmpty()) item { LibraryEmpty("No songs yet", "Add music to your library, then come back here.") }
        }
        menuSong?.let { item ->
            LibrarySongActionsSheet(
                song = item,
                isFavorite = item.id in favoriteIds,
                onDismiss = { menuSong = null },
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onToggleFavorite = { song -> onSetFavorites(setOf(song.id), song.id !in favoriteIds) },
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                showFavoriteAction = false,
                onRemoveFromPlaylist = if (isPlaylist) ({ onRemoveSongFromPlaylist(item.id) }) else null,
            )
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibrarySoftIconButton(
                onClick = onBack,
                contentDescription = "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                modifier = Modifier.size(42.dp),
                iconSize = 23.dp,
                surfaceAlpha = .15f,
                frosted = true,
            )
            Spacer(Modifier.weight(1f))
            if (isDetail) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isPlaylist) {
                        LibrarySoftIconButton(
                            onClick = onSort,
                            contentDescription = "Sort playlist songs",
                            icon = null,
                            modifier = Modifier.size(42.dp),
                            iconSize = 22.dp,
                            frosted = true,
                            sortIcon = true,
                        )
                    }
                    LibrarySoftMoreButton(onClick = { collectionOptionsVisible = true }, contentDescription = "Collection options")
                }
            } else {
                LibrarySoftIconButton(
                    onClick = onSort,
                    contentDescription = "Sort songs",
                    icon = null,
                    modifier = Modifier.size(40.dp),
                    iconSize = 22.dp,
                    frosted = true,
                    sortIcon = true,
                )
            }
        }
        if (collectionOptionsVisible) {
            LibraryCollectionActionsSheet(
                title = title,
                subtitle = subtitle,
                artworkSong = displaySongs.firstOrNull(),
                isPlaylist = isPlaylist,
                pinned = pinned,
                onDismiss = { collectionOptionsVisible = false },
                onRenamePlaylist = onRequestRenamePlaylist,
                onTogglePin = onTogglePin,
                onDeletePlaylist = onRequestDeletePlaylist,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
            )
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
                                onAddSongToPlaylist(target.id, displaySongs.map(Song::id)) { success ->
                                    if (success) addToPlaylist = false
                                }
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
