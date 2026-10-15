package com.wavv.app

import java.util.Locale

internal data class GenreCategory(val name: String, val artwork: Song?)

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
        val hasArtwork = !song.albumArtUri.isNullOrBlank()
        song.genreNames().forEach { name ->
            val modelInferred = song.inferredGenres.any { it.equals(name, ignoreCase = true) }
            if (!hasArtwork && !modelInferred) return@forEach
            val key = name.lowercase(Locale.ROOT)
            val existing = categories[key]
            if (existing == null) {
                categories[key] = GenreCategory(name, song.takeIf { hasArtwork })
            } else if (existing.artwork == null && hasArtwork) {
                categories[key] = existing.copy(artwork = song)
            }
        }
    }
    return categories.values.sortedBy { it.name.lowercase(Locale.ROOT) }
}

internal fun recommendationOrderedGenreCategories(
    songs: List<Song>,
    recommendations: List<Song>,
): List<GenreCategory> {
    val catalog = songs.distinctBy(Song::id)
    val songById = catalog.associateBy(Song::id)
    val rankedSongs = recommendations.mapNotNull { songById[it.id] }.distinctBy(Song::id)
    val genrePositions = mutableMapOf<String, Int>()
    val songPositions = mutableMapOf<Long, Int>()
    rankedSongs.forEachIndexed { index, song ->
        songPositions.putIfAbsent(song.id, index)
        song.genreNames().forEach { genre -> genrePositions.putIfAbsent(genre.lowercase(Locale.ROOT), index) }
    }
    val genreCounts = catalog.asSequence()
        .flatMap { song -> song.genreNames().asSequence().map { it.lowercase(Locale.ROOT) } }
        .groupingBy { it }
        .eachCount()
    return genreCategories(catalog)
        .map { category ->
            val genre = category.name.lowercase(Locale.ROOT)
            val artwork = catalog.asSequence()
                .filter { song ->
                    !song.albumArtUri.isNullOrBlank() && song.genreNames().any { it.equals(category.name, ignoreCase = true) }
                }
                .sortedWith(
                    compareBy<Song> { songPositions[it.id] ?: Int.MAX_VALUE }
                        .thenByDescending(Song::modifiedAt)
                        .thenBy(Song::id),
                )
                .firstOrNull() ?: category.artwork
            category.copy(artwork = artwork)
        }
        .sortedWith(
            compareBy<GenreCategory> { genrePositions[it.name.lowercase(Locale.ROOT)] ?: Int.MAX_VALUE }
                .thenByDescending { genreCounts[it.name.lowercase(Locale.ROOT)] ?: 0 }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
}
