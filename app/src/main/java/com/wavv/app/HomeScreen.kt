package com.wavv.app

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs

private data class HomeStackEntry(val song: Song, val title: String)
private data class HomeStackSection(
    val title: String,
    val entries: List<HomeStackEntry>,
    val mix: SmartMix? = null,
)

internal fun homeSectionOrder(sectionCount: Int, stackIndex: Int): List<Int> {
    if (sectionCount <= 0) return emptyList()
    val start = stackIndex.coerceIn(0, sectionCount - 1)
    return (start until sectionCount).toList() + (0 until start).toList()
}

internal fun primaryHomeShelf(recentSongs: List<Song>, recentlyAdded: List<Song>): Pair<String, List<Song>> =
    if (recentSongs.isNotEmpty()) "Recently Played" to recentSongs else "Recently Added" to recentlyAdded

@Composable
internal fun HomeScreen(
    songs: List<Song>,
    favoriteIds: Set<Long> = emptySet(),
    isPlaying: Boolean,
    onChooseSource: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onExpandPlayer: () -> Unit,
    onAtTopChange: (Boolean) -> Unit,
    hasNowPlaying: Boolean,
    miniPlayerHeight: Dp,
    stackExpanded: Boolean,
    stackIndex: Int,
    stackExpansionProgress: State<Float>,
    onStackExpandedChange: (Boolean) -> Unit,
    onStackIndexChange: (Int) -> Unit,
    recentSongs: List<Song> = emptyList(),
    upNextSongs: List<Song> = emptyList(),
    smartMixes: List<SmartMix> = emptyList(),
    onPlayMix: (SmartMix) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val homeBackdrop = rememberLayerBackdrop()
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
    val allSongs = remember(songs) { songs.sortedBy { it.title.lowercase(Locale.ROOT) } }
    val (primaryShelfTitle, primaryShelfSongs) = remember(recentSongs, recentlyAdded) {
        primaryHomeShelf(recentSongs, recentlyAdded)
    }
    // shortcut: Home shelves show at most eight entries; use Library to browse the full catalog.
    val homeSections = remember(primaryShelfTitle, primaryShelfSongs, likedSongs, albumSongs, artistSongs, recentlyAdded, recentSongs, smartMixes, allSongs) {
        buildList {
            if (primaryShelfSongs.isNotEmpty()) {
                add(HomeStackSection(primaryShelfTitle, primaryShelfSongs.take(8).map { HomeStackEntry(it, it.title) }))
            }
            smartMixes.forEach { mix ->
                val entries = mix.songs.take(8).map { HomeStackEntry(it, it.title) }
                if (entries.isNotEmpty()) add(HomeStackSection(mix.title, entries, mix))
            }
            if (likedSongs.isNotEmpty()) {
                add(HomeStackSection("Liked Songs", likedSongs.take(8).map { HomeStackEntry(it, it.title) }))
            }
            if (albumSongs.isNotEmpty()) {
                add(HomeStackSection("Albums", albumSongs.take(8).map { HomeStackEntry(it, it.album) }))
            }
            if (artistSongs.isNotEmpty()) {
                add(HomeStackSection("Artists", artistSongs.take(8).map { HomeStackEntry(it, it.artist) }))
            }
            if (recentSongs.isNotEmpty() && recentlyAdded.isNotEmpty()) {
                add(HomeStackSection("Recently Added", recentlyAdded.take(8).map { HomeStackEntry(it, it.title) }))
            }
            if (allSongs.isNotEmpty()) {
                add(HomeStackSection("Songs", allSongs.take(8).map { HomeStackEntry(it, it.title) }))
            }
        }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        onAtTopChange(listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 60)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Wavv", waveform = true, isPlaying = isPlaying, showAmbient = false)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val navigationBarBottom = with(LocalDensity.current) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }
            val stackCardHeight = 144.dp
            val stackPeek = 40.dp
            val stackCardsVisible = homeSections.size.coerceAtMost(2)
            val playerClearance = if (hasNowPlaying) 108.dp + miniPlayerHeight else 96.dp
            val stackClearance = navigationBarBottom + playerClearance +
                stackCardHeight + stackPeek * (stackCardsVisible - 1).coerceAtLeast(0)
            val feedBottomClearance = if (homeSections.isEmpty()) navigationBarBottom + 104.dp else stackClearance

            Box(Modifier.fillMaxSize().layerBackdrop(homeBackdrop)) {
                HomeAccentGlow(Modifier.align(Alignment.BottomCenter).offset(y = (-190).dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(bottom = feedBottomClearance).graphicsLayer {
                        val progress = stackExpansionProgress.value.coerceIn(0f, 1f)
                        translationY = -size.height * progress
                        alpha = 1f - progress
                    },
                    userScrollEnabled = !stackExpanded,
                    contentPadding = PaddingValues(
                        top = 10.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (songs.isEmpty()) {
                        item { EmptyLibraryCard(onChooseSource) }
                    } else {
                        item {
                            HeroCarousel(
                                songs = songs,
                                upNextSongs = upNextSongs,
                                compactHero = hasNowPlaying && !stackExpanded,
                                favoriteIds = favoriteIds,
                                onPlay = onPlay,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                onToggleFavorite = onToggleFavorite,
                                onRequestAddToPlaylist = onRequestAddToPlaylist,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToArtist = onNavigateToArtist,
                            )
                        }
                    }
                }
            }
            if (homeSections.isNotEmpty()) {
                HomeSectionsStack(
                    sections = homeSections,
                    expanded = stackExpanded,
                    stackIndex = stackIndex.coerceIn(0, (homeSections.size - 1).coerceAtLeast(0)),
                    expansionProgress = stackExpansionProgress,
                    hasNowPlaying = hasNowPlaying,
                    miniPlayerHeight = miniPlayerHeight,
                    navigationBarBottom = navigationBarBottom,
                    backdrop = homeBackdrop,
                    onExpandedChange = onStackExpandedChange,
                    onStackIndexChange = { onStackIndexChange(it.coerceIn(0, homeSections.lastIndex.coerceAtLeast(0))) },
                    onExpandPlayer = onExpandPlayer,
                    onPlay = onPlay,
                    onPlayMix = onPlayMix,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun HomeSectionsStack(
    sections: List<HomeStackSection>,
    expanded: Boolean,
    stackIndex: Int,
    expansionProgress: State<Float>,
    hasNowPlaying: Boolean,
    miniPlayerHeight: Dp,
    navigationBarBottom: Dp,
    backdrop: Backdrop,
    onExpandedChange: (Boolean) -> Unit,
    onStackIndexChange: (Int) -> Unit,
    onExpandPlayer: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayMix: (SmartMix) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(expanded) {
        if (!expanded) scrollState.scrollTo(0)
    }
    val orderedSections = remember(sections, stackIndex) {
        homeSectionOrder(sections.size, stackIndex).map(sections::get)
    }
    val collapseAtTop = remember(scrollState, expanded, onExpandedChange) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (expanded && source == NestedScrollSource.UserInput && available.y > 0f && scrollState.value == 0) {
                    onExpandedChange(false)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val densityScale = density.density
        val cardHeight = 144.dp
        val peek = 40.dp
        val expandedTop = if (hasNowPlaying) 76.dp else 12.dp
        val collapsedBottomClearance = navigationBarBottom +
            if (hasNowPlaying) 108.dp + miniPlayerHeight else 80.dp
        val expandedHeight = (maxHeight - collapsedBottomClearance).coerceAtLeast(cardHeight + peek)
        val expandedHeightPx = with(density) { expandedHeight.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val rowHeight = cardHeight + 12.dp
        val rowHeightPx = with(density) { rowHeight.toPx() }
        val peekPx = with(density) { peek.toPx() }
        val expandedTopPx = with(density) { expandedTop.toPx() }
        val collapsedTop = expandedHeight - cardHeight - peek
        val collapsedTopPx = with(density) { collapsedTop.toPx() }
        val stackBoundaryPx = expandedHeightPx - cardHeightPx - peekPx - with(density) { 24.dp.toPx() }

        Box(Modifier.fillMaxWidth().height(expandedHeight).clipToBounds()) {
            Column(
                Modifier.fillMaxWidth()
                    .nestedScroll(collapseAtTop)
                    .then(if (expanded) Modifier.verticalScroll(scrollState, overscrollEffect = null) else Modifier)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(expandedTop))
                orderedSections.forEachIndexed { displayIndex, section ->
                    val active = displayIndex == 0
                    val next = displayIndex == 1
                    val leaving = stackIndex > 0 && displayIndex == orderedSections.lastIndex
                    val compactVisible = active || next || leaving
                    val compactY = when {
                        active -> 0.dp
                        next -> peek
                        leaving -> -cardHeight
                        else -> (displayIndex * 144).dp
                    }
                    val compactScale = if (active) 1f else .94f
                    val compactAlpha = if (compactVisible) 1f else 0f
                    val compactTopForCard = with(density) { compactY.toPx() }
                    val compactCardTop = collapsedTopPx + compactTopForCard

                    Box(
                        Modifier.fillMaxWidth().height(rowHeight)
                            .zIndex((orderedSections.size - displayIndex).toFloat()),
                    ) {
                        if (expanded || compactVisible) {
                            Box(
                                Modifier.fillMaxWidth().height(cardHeight).graphicsLayer {
                                    val layerProgress = expansionProgress.value.coerceIn(0f, 1f)
                                    val layerNaturalTop = expandedTopPx + displayIndex * rowHeightPx - scrollState.value
                                    val layerTuck = ((layerNaturalTop - stackBoundaryPx) / rowHeightPx).coerceIn(0f, 1f)
                                    val layerEasedTuck = layerTuck * layerTuck * (3f - 2f * layerTuck)
                                    val layerExpandedTop = if (layerNaturalTop > stackBoundaryPx) {
                                        layerNaturalTop + (stackBoundaryPx + peekPx - layerNaturalTop) * layerEasedTuck
                                    } else layerNaturalTop
                                    val targetScale = compactScale + (1f - .06f * layerEasedTuck - compactScale) * layerProgress
                                    alpha = compactAlpha + (1f - compactAlpha) * layerProgress
                                    scaleX = targetScale
                                    scaleY = targetScale
                                    translationY = compactCardTop * (1f - layerProgress) + layerExpandedTop * layerProgress - layerNaturalTop
                                }.pointerInput(
                                    expanded,
                                    compactVisible,
                                    stackIndex,
                                    orderedSections.size,
                                    hasNowPlaying,
                                ) {
                                    if (!expanded && compactVisible) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            val velocityTracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                                            var handled = false
                                            var expandingWithGesture = false
                                            var lastPosition = down.position
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                val pointerDelta = change.position - lastPosition
                                                lastPosition = change.position
                                                val delta = change.position - down.position
                                                val velocity = velocityTracker.calculateVelocity()
                                                val verticalIntent = abs(delta.y) > abs(delta.x) * 1.15f ||
                                                    abs(velocity.y) > abs(velocity.x) * 1.15f
                                                val crossedSlop = abs(delta.y) > viewConfiguration.touchSlop
                                                val fastFlick = abs(velocity.y) > 650f * densityScale && abs(delta.y) > 5f * densityScale
                                                if (!handled && verticalIntent && (crossedSlop || fastFlick)) {
                                                    handled = true
                                                    change.consume()
                                                    val swipeDirection = if (fastFlick && !crossedSlop) velocity.y else delta.y
                                                    if (swipeDirection < 0f) {
                                                        onExpandedChange(true)
                                                        expandingWithGesture = true
                                                        if (pointerDelta.y < 0f) scrollState.dispatchRawDelta(-pointerDelta.y)
                                                    } else if (hasNowPlaying && stackIndex == 0) {
                                                        onExpandPlayer()
                                                    } else {
                                                        onStackIndexChange((stackIndex - 1).coerceAtLeast(0))
                                                    }
                                                } else if (handled) {
                                                    if (expandingWithGesture && pointerDelta.y < 0f) {
                                                        scrollState.dispatchRawDelta(-pointerDelta.y)
                                                    }
                                                    change.consume()
                                                }
                                                if (!change.pressed) break
                                            }
                                        }
                                    }
                                }.zIndex((orderedSections.size - displayIndex).toFloat())
                                    .clip(RoundedCornerShape(28.dp)),
                            ) {
                                HomeStackCard(
                                    section = section,
                                    enabled = expanded || active,
                                    backdrop = backdrop,
                                    onToggleExpanded = { onExpandedChange(!expanded) },
                                    onPlay = onPlay,
                                    onPlayMix = onPlayMix,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(peek - 12.dp))
            }
        }
    }
}

@Composable
private fun HomeAccentGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "home accent glow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_200, easing = LinearEasing), RepeatMode.Reverse),
        label = "home accent phase",
    )
    val color = Color.hsv((318f + phase * 60f) % 360f, .56f, .58f, .20f)
    Box(
        modifier.size(170.dp).blur(24.dp).background(
            Brush.radialGradient(0f to color, .58f to color.copy(alpha = .08f), 1f to Color.Transparent),
            CircleShape,
        ),
    )
}

@Composable
private fun HomeStackCard(
    section: HomeStackSection,
    enabled: Boolean,
    backdrop: Backdrop,
    onToggleExpanded: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayMix: (SmartMix) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(
        modifier.clip(shape)
            .background(Color(0x761E2024))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) vibrancy()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(34.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(Color(0x761E2024))
                },
            )
            .drawWithCache {
                val gloss = Brush.linearGradient(listOf(Color.White.copy(.055f), Color.White.copy(.012f), Color.Transparent))
                onDrawBehind { drawRect(gloss) }
            }
            .border(1.dp, Color.White.copy(.12f), shape),
    ) {
        val tileWidth = ((maxWidth - 48.dp) / 3).coerceIn(64.dp, 94.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 3.dp).padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(section.title.uppercase(), color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, letterSpacing = .55.sp, maxLines = 1)
                Text(
                    if (section.mix == null) "Swipe to explore" else "Play mix",
                    Modifier.clickable(role = Role.Button) {
                        section.mix?.let(onPlayMix) ?: onToggleExpanded()
                    },
                    color = Color.White.copy(.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 3.dp)) {
                items(section.entries, key = { it.song.id }) { entry ->
                    Column(
                        Modifier.width(tileWidth).clickable(enabled = enabled, role = Role.Button) { onPlay(entry.song) },
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)
                            .shadow(5.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(.08f))) {
                            AlbumArt(entry.song, Modifier.fillMaxSize(), contentDescription = entry.title, radius = 14.dp)
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.9f)))),
                            )
                            Text(entry.title, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp), color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
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
    songs: List<Song>,
    upNextSongs: List<Song>,
    compactHero: Boolean,
    favoriteIds: Set<Long>,
    onPlay: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
) {
    val pageCount = minOf(8, songs.size).coerceAtLeast(1)
    val pagerState = rememberPagerState { pageCount }
    var songForActions by remember { mutableStateOf<Song?>(null) }
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
                HeroCard(
                    song = song,
                    queue = upNextSongs,
                    compactHero = compactHero,
                    onPlay = onPlay,
                    onShowActions = { songForActions = song },
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
    songForActions?.let { song ->
        HomeSongActionsSheet(
            song = song,
            favorite = song.id in favoriteIds,
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HeroCard(
    song: Song,
    queue: List<Song>,
    compactHero: Boolean,
    onPlay: (Song) -> Unit,
    onShowActions: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val cardBackdrop = rememberLayerBackdrop()
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (compactHero) 1.05f else .95f).clip(shape)
            .combinedClickable(
                onClick = {},
                onLongClickLabel = "Song actions",
                onLongClick = onShowActions,
            ),
    ) {
        Box(Modifier.fillMaxSize().layerBackdrop(cardBackdrop)) {
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
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(song.title.uppercase(), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = .25.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, Modifier.padding(top = 2.dp), color = Color.White.copy(.76f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassPill(cardBackdrop, onClick = { onPlay(song) }, onLongClick = onShowActions, label = "Play")
            }
            AnimatedContent(
                targetState = queue.firstOrNull(),
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                        fadeOut(tween(140))
                },
                label = "home next track",
            ) { next ->
                if (next != null) {
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
}

@Composable
private fun HomeSongActionsSheet(
    song: Song,
    favorite: Boolean,
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
        HomeSongActionRow(Icons.Default.SkipNext, "Play next") { close(); onPlayNext(song) }
        HomeSongActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Add to queue") { close(); onAddToQueue(song) }
        HomeSongActionRow(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (favorite) "Remove from favorites" else "Add to favorites") {
            close(); onToggleFavorite(song)
        }
        HomeSongActionRow(Icons.Default.Add, "Add to playlist") { close(); onRequestAddToPlaylist(song) }
        if (song.album.isNotBlank()) HomeSongActionRow(Icons.Default.Album, "Go to album") { close(); onNavigateToAlbum(song) }
        if (song.artist.isNotBlank()) HomeSongActionRow(Icons.Default.Mic, "Go to artist") { close(); onNavigateToArtist(song) }
    }
}

@Composable
private fun HomeSongActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = WavvPink, modifier = Modifier.size(21.dp))
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlassPill(backdrop: Backdrop, onClick: () -> Unit, onLongClick: () -> Unit, label: String) {
    val shape = CircleShape
    Box(
        Modifier.width(86.dp).height(42.dp).clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) vibrancy()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(22.dp.toPx())
                },
                onDrawSurface = { drawRect(Color(0x906A6A6A)) },
            )
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = "Song actions",
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
        }
    }
}

@Composable
internal fun Hairline() = Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Color.White.copy(.07f)))

@Composable
internal fun ShelfLabel(label: String) = Text(label.uppercase(), color = WavvMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
