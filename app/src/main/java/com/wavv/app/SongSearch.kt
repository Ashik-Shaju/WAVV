package com.wavv.app

import java.util.Locale

internal fun searchSongs(
    songs: List<Song>,
    query: String,
    semanticSongs: List<Song>? = null,
    limit: Int = 12,
): List<Song> {
    if (limit <= 0) return emptyList()
    val normalizedQuery = query.trim()
    return buildList(limit) {
        val seenIds = HashSet<Long>()
        for (song in semanticSongs.orEmpty()) {
            if (seenIds.add(song.id)) add(song)
            if (size == limit) return@buildList
        }
        for (song in songs) {
            if ((normalizedQuery.isEmpty() || song.matches(normalizedQuery)) && seenIds.add(song.id)) {
                add(song)
                if (size == limit) return@buildList
            }
        }
    }
}

private fun Song.matches(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        album.contains(query, ignoreCase = true) ||
        genreNames().any { it.contains(query, ignoreCase = true) }

internal fun songsByGenre(songs: List<Song>): List<Pair<String, List<Song>>> =
    songs.flatMap { song ->
        song.genreNames().map { name -> name.lowercase(Locale.ROOT) to (name to song) }
    }.groupBy { it.first }
        .values
        .map { taggedSongs -> taggedSongs.first().second.first to taggedSongs.map { it.second.second } }
        .sortedBy { it.first.lowercase(Locale.ROOT) }

internal fun songsForGenre(songs: List<Song>, genre: String): List<Song> {
    val normalizedGenre = genre.trim()
    if (normalizedGenre.isEmpty()) return emptyList()
    return songs.filter { song -> song.genreNames().any { it.equals(normalizedGenre, ignoreCase = true) } }
}
