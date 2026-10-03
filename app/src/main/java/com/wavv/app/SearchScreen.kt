package com.wavv.app

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
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
internal fun SearchResultRow(song: Song, onPlay: (Song) -> Unit) {
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
