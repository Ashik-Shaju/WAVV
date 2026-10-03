package com.wavv.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.effects.blur
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
internal fun LibraryScreen(
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
internal fun WavvBottomSheet(
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
