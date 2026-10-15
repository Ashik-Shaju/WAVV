package com.wavv.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SearchScreen(
    songs: List<Song>,
    onPlay: (Song, String) -> Unit,
    hasNowPlaying: Boolean,
    searchResults: List<Song>?,
    personalizedRecommendations: List<Song> = emptyList(),
    semanticLoading: Boolean = false,
    semanticSearchComplete: Boolean = false,
    searchError: String? = null,
    searchNotice: String? = null,
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
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > 0
    val navigationBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val normalBarBottom = if (hasNowPlaying) {
        navigationBarBottom + 138.dp
    } else {
        navigationBarBottom + 62.dp
    }
    val targetBarBottom = if (imeVisible) 16.dp else normalBarBottom
    val barBottom by animateDpAsState(
        targetValue = targetBarBottom,
        animationSpec = spring(dampingRatio = .9f, stiffness = 560f),
        label = "search bar keyboard position",
    )
    val genreSongs = remember(songs, selectedGenre, personalizedRecommendations) {
        selectedGenre?.let { genre ->
            recommendationOrderedSongs(songsForGenre(songs, genre), personalizedRecommendations)
        }.orEmpty()
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Search")
        Box(Modifier.fillMaxSize()) {
            val resultsBottomPadding = when {
                hasNowPlaying && imeVisible -> 100.dp
                hasNowPlaying -> 168.dp
                imeVisible -> 92.dp
                else -> 92.dp
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = resultsBottomPadding),
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
                        SearchResultRow(song) { onPlay(it, "genre") }
                        if (index != genreSongs.lastIndex) Hairline()
                    }
                } else if (query.isNotBlank()) {
                    item {
                        AnimatedContent(
                            targetState = semanticSearchComplete,
                            transitionSpec = {
                                (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 4 }) togetherWith
                                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
                            },
                            label = "search result priority label",
                        ) { semantic ->
                            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                                ShelfLabel(if (semantic) "Semantic results" else "Results")
                            }
                        }
                    }
                    searchNotice?.let { notice ->
                        item {
                            Text(
                                notice,
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                color = Color.White.copy(.58f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    if (searchError != null) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(searchError, color = Color.White.copy(.66f), fontSize = 14.sp)
                                TextButton(onClick = { onQueryChange(query) }) { Text("Try again", color = WavvPink) }
                            }
                        }
                    }
                    if (semanticLoading) {
                        item {
                            AnimatedContent(
                                targetState = searchResults.isNullOrEmpty(),
                                transitionSpec = {
                                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 5 }) togetherWith
                                        (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 5 })
                                },
                                label = "search loading to quick results",
                            ) { waitingForQuickResults ->
                                if (waitingForQuickResults) {
                                    SearchLoadingSkeleton()
                                } else {
                                    SearchRefiningHint()
                                }
                            }
                        }
                    }
                    if (!semanticLoading && searchError == null && searchResults?.isEmpty() == true) {
                        item {
                            Text(
                                "No matching tracks found.",
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                                color = WavvMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    itemsIndexed(searchResults.orEmpty(), key = { _, song -> song.id }) { index, song ->
                        SearchResultRow(song, Modifier.animateItem()) { onPlay(it, "search") }
                        if (index != searchResults.orEmpty().lastIndex) Hairline()
                    }
                } else if (!focused) {
                    item {
                        BrowseCategories(songs, personalizedRecommendations) { category ->
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
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = barBottom),
                showClear = selectedGenre != null,
            )
        }
    }
}

@Composable
internal fun SearchResultRow(song: Song, modifier: Modifier = Modifier, onPlay: (Song) -> Unit) {
    Row(modifier.fillMaxWidth().clickable { onPlay(song) }.padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AlbumArt(song, Modifier.size(46.dp), song.title, 8.dp)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(.4f), fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(.2f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SearchLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "search results shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_050, easing = LinearEasing), RepeatMode.Restart),
        label = "search skeleton shimmer progress",
    )
    val shimmer = Brush.linearGradient(
        colors = listOf(Color.White.copy(.045f), Color.White.copy(.13f), Color.White.copy(.045f)),
        start = Offset(shimmerProgress * 900f - 220f, 0f),
        end = Offset(shimmerProgress * 900f + 180f, 180f),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(4) { index ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.fillMaxWidth(if (index % 2 == 0) .68f else .54f)
                            .height(12.dp).clip(CircleShape).background(shimmer),
                    )
                    Box(
                        Modifier.fillMaxWidth(if (index % 2 == 0) .42f else .34f)
                            .height(9.dp).clip(CircleShape).background(shimmer),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchRefiningHint() {
    val transition = rememberInfiniteTransition(label = "semantic search refining hint")
    val alpha by transition.animateFloat(
        initialValue = .4f,
        targetValue = .9f,
        animationSpec = infiniteRepeatable(tween(720, easing = LinearEasing), RepeatMode.Reverse),
        label = "semantic search refining alpha",
    )
    Text(
        "Refining your matches",
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        color = WavvMuted.copy(alpha = alpha),
        fontSize = 12.sp,
    )
}

@Composable
private fun BrowseCategories(songs: List<Song>, recommendations: List<Song>, onChooseCategory: (String) -> Unit) {
    val categories = remember(songs, recommendations) { recommendationOrderedGenreCategories(songs, recommendations) }
    Column(
        Modifier.padding(horizontal = 16.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "BROWSE CATEGORIES",
            Modifier.padding(start = 4.dp, bottom = 2.dp),
            color = WavvMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .4.sp,
        )
        if (categories.isEmpty()) {
            Text("No genre tags were found in your library.", Modifier.padding(4.dp), color = Color.White.copy(.45f), fontSize = 13.sp)
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
    val hue = (category.name.hashCode().ushr(1) % 360).toFloat()
    val tint = Color.hsv(hue, .68f, .62f)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(100.dp).clip(shape).background(tint)
            .border(1.dp, Color.White.copy(.14f), shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        category.artwork?.let { artwork ->
            AlbumArt(
                artwork,
                Modifier.align(Alignment.CenterEnd).fillMaxWidth(.64f).fillMaxSize(),
                contentDescription = null,
                radius = 12.dp,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to tint,
                        .38f to tint.copy(alpha = .96f),
                        .7f to tint.copy(alpha = .2f),
                        1f to Color.Transparent,
                    ),
                ),
            ),
        )
        Text(
            category.name,
            Modifier.align(Alignment.BottomStart).fillMaxWidth(.64f).padding(start = 12.dp, bottom = 12.dp),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-.3).sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, focused: Boolean, onFocusChange: (Boolean) -> Unit, focusManager: androidx.compose.ui.focus.FocusManager, modifier: Modifier = Modifier, showClear: Boolean = false) {
    val shape = CircleShape
    Box(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(58.dp)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xF125262B), Color(0xF12D2E34), Color(0xF1232429)),
                ),
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(Color.White.copy(.14f), Color(0xFFB6D5FF).copy(.18f), Color.White.copy(.10f)),
                ),
                shape,
            ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(androidx.compose.ui.res.painterResource(R.drawable.nav_search), null, Modifier.size(18.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (focused) Color.White.copy(.82f) else Color.White.copy(.68f)))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) },
                textStyle = TextStyle(color = Color.White.copy(.94f), fontSize = 16.sp, fontFamily = WavvFontFamily, fontWeight = FontWeight.Medium),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { field -> if (query.isEmpty()) Text("Songs, artists, albums...", color = Color.White.copy(.52f), fontSize = 16.sp); field() },
            )
            if (query.isNotEmpty() || focused || showClear) Box(
                Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(.12f)).clickable {
                    onQueryChange("")
                    focusManager.clearFocus()
                    onFocusChange(false)
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.White.copy(.78f), modifier = Modifier.size(15.dp))
            }
        }
    }
}
