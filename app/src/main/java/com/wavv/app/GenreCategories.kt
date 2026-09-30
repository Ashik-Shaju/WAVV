package com.wavv.app

import java.util.Locale

internal data class GenreCategory(val name: String, val artwork: Song)

internal fun parseGenreMetadata(value: String?): List<String> =
    value?.trim()?.takeIf(String::isNotEmpty)?.let(::listOf).orEmpty()

internal fun Song.genreNames(): List<String> =
    (genres + listOfNotNull(genre))
        .map { it.trim() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }

internal fun genreCategories(songs: List<Song>): List<GenreCategory> {
    val categories = mutableMapOf<String, GenreCategory>()
    songs.forEach { song ->
        if (song.albumArtUri.isNullOrBlank()) return@forEach
        song.genreNames().forEach { name ->
            val key = name.lowercase(Locale.ROOT)
            if (key !in categories) {
                categories[key] = GenreCategory(name, song)
            }
        }
    }
    return categories.values.sortedBy { it.name.lowercase(Locale.ROOT) }
}
