package com.wavv.app

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
        album.contains(query, ignoreCase = true)
