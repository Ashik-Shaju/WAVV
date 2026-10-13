package com.wavv.app

import java.util.Locale

internal data class GenreCategory(val name: String, val artwork: Song)

internal fun parseGenreMetadata(value: String?): List<String> =
    value?.trim()?.takeIf(String::isNotEmpty)?.let(::listOf).orEmpty()

internal fun Song.fileGenreNames(): List<String> =
    (genres + listOfNotNull(genre))
        .map { it.trim() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }

internal fun Song.genreNames(): List<String> =
    (fileGenreNames() + inferredGenres)
        .map { it.trim() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }

internal fun Song.genreConfidence(name: String, tags: List<MusicTag>): Double {
    if (fileGenreNames().any { it.equals(name, ignoreCase = true) }) return 1.0
    return tags.asSequence()
        .filter {
            it.taxonomy.equals("genre", ignoreCase = true) &&
                it.label.equals(name, ignoreCase = true) &&
                it.probability.isFinite() && it.probability in 0f..1f &&
                it.threshold.isFinite() && it.threshold in 0f..1f &&
                it.probability >= it.threshold
        }
        .maxOfOrNull { it.probability.toDouble() }
        ?: 0.0
}

internal fun Song.withMusicUnderstandingGenres(tags: List<MusicTag>): Song = copy(
    inferredGenres = tags.asSequence()
        .filter {
            it.taxonomy.equals("genre", ignoreCase = true) &&
                it.probability.isFinite() && it.probability in 0f..1f &&
                it.threshold.isFinite() && it.threshold in 0f..1f &&
                it.probability >= it.threshold
        }
        .map { it.label.trim() }
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .toList(),
)

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

internal fun recommendationOrderedGenreCategories(
    songs: List<Song>,
    recommendations: List<Song>,
): List<GenreCategory> {
    val genrePositions = mutableMapOf<String, Int>()
    recommendations.forEachIndexed { index, song ->
        song.genreNames().forEach { genre -> genrePositions.putIfAbsent(genre.lowercase(Locale.ROOT), index) }
    }
    return genreCategories(songs).withIndex()
        .sortedWith(compareBy<IndexedValue<GenreCategory>> { genrePositions[it.value.name.lowercase(Locale.ROOT)] ?: Int.MAX_VALUE }
            .thenBy(IndexedValue<GenreCategory>::index))
        .map(IndexedValue<GenreCategory>::value)
}
