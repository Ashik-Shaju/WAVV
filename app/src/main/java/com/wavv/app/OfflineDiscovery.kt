package com.wavv.app

import java.util.Locale
import java.util.UUID
import kotlin.math.exp
import kotlin.math.sqrt

internal data class SemanticMatch(val songId: Long, val cosineSimilarity: Float)

internal data class MusicQueryIntent(
    val semanticText: String,
    val languageCode: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val artist: String? = null,
    val album: String? = null,
    val composer: String? = null,
) {
    val hasMetadataFilters: Boolean
        get() = languageCode != null || genre != null || year != null || artist != null || album != null || composer != null
}

internal data class LocalListeningEvent(
    val songId: Long,
    val timestampMs: Long,
    val eventType: String,
    val sessionId: String,
    val listenSeconds: Long = 0L,
    val completionRatio: Float? = null,
    val skipPositionSeconds: Long? = null,
    val selectionSource: String? = null,
    val recommendationId: String? = null,
    val playlistId: Long? = null,
)

internal data class PersonalizedDiscovery(
    val recommendations: List<Song> = emptyList(),
    val mixes: List<SmartMix> = emptyList(),
)

data class SmartMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
)

internal data class TextMetadataField(val key: String, val value: String)

internal object WavvSession {
    val id: String = UUID.randomUUID().toString()
}

internal fun parseMusicQuery(query: String, songs: List<Song>): MusicQueryIntent {
    var remainder = query.trim()
    var languageCode: String? = null
    var genre: String? = null
    var year: Int? = null
    var artist: String? = null
    var album: String? = null
    var composer: String? = null

    val explicitLanguage = LANGUAGE_COMMAND.find(remainder)
    if (explicitLanguage != null && languageCode(explicitLanguage.groupValues[1]) != null) {
        val value = explicitLanguage.groupValues[1]
        languageCode = languageCode(value)
        remainder = remainder.replaceRange(explicitLanguage.range, " ")
    } else {
        val language = languageNames.firstOrNull { (name, _) -> remainder.containsWordPhrase(name) }
        if (language != null) {
            languageCode = language.second
            remainder = remainder.replacePhrase(language.first)
        }
    }

    val knownGenres = songs.flatMap(Song::genreNames).distinctBy(String::normalized)
        .sortedByDescending(String::length)
    genre = knownGenres.firstOrNull(remainder::containsWordPhrase)
    if (genre != null) remainder = remainder.replacePhrase(genre)

    year = YEAR.find(remainder)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?.takeIf { target -> songs.any { it.year == target } }
    if (year != null) {
        YEAR.find(remainder)?.range?.let { range -> remainder = remainder.replaceRange(range, " ") }
    }

    artist = takeKnownField(remainder, songs.flatMap { listOf(it.artist, it.albumArtist.orEmpty()) }, "by")
    if (artist != null) remainder = remainder.replacePhrase(artist)
    album = takeKnownField(remainder, songs.map(Song::album), "from")
    if (album != null) remainder = remainder.replacePhrase(album)
    composer = takeKnownField(remainder, songs.mapNotNull(Song::composer), "composed by")
    if (composer != null) remainder = remainder.replacePhrase(composer).replace(COMPOSED_BY, " ")

    remainder = remainder
        .replace(REQUEST_WORDS, " ")
        .replace(WHITESPACE, " ")
        .trim()
    return MusicQueryIntent(remainder, languageCode, genre, year, artist, album, composer)
}

internal fun rankLocalSearch(
    songs: List<Song>,
    intent: MusicQueryIntent,
    semanticMatches: List<SemanticMatch>,
    tagsBySong: Map<Long, List<MusicTag>> = emptyMap(),
    limit: Int = 50,
): List<Song> {
    if (limit <= 0) return emptyList()
    val candidates = songs.distinctBy(Song::id).filter { song ->
        (intent.languageCode == null || song.languageTags.any { languageCode(it) == intent.languageCode }) &&
            (intent.genre == null || song.genreNames().any { it.equals(intent.genre, ignoreCase = true) }) &&
            (intent.year == null || song.year == intent.year) &&
            (intent.artist == null || song.artist.equals(intent.artist, true) || song.albumArtist.equals(intent.artist, true)) &&
            (intent.album == null || song.album.equals(intent.album, true)) &&
            (intent.composer == null || song.composer.equals(intent.composer, true))
    }
    if (candidates.isEmpty()) return emptyList()

    val semantic = semanticMatches.asSequence()
        .filter { it.cosineSimilarity.isFinite() }
        .associate { it.songId to it.cosineSimilarity.toDouble() }
    val semanticValues = candidates.mapNotNull { semantic[it.id] }
    val minimum = semanticValues.minOrNull() ?: 0.0
    val range = (semanticValues.maxOrNull() ?: minimum) - minimum
    val tokens = intent.semanticText.tokens()
    val lexical = candidates.associate { song -> song.id to lexicalScore(song, tokens, tagsBySong[song.id].orEmpty()) }
    val hasSemantic = semanticValues.isNotEmpty()
    val hasTextMatch = lexical.values.any { it > 0.0 }
    val scored = candidates.mapNotNull { song ->
        val lexicalScore = lexical.getValue(song.id)
        val semanticScore = semantic[song.id]?.let { score -> if (range > 1e-9) (score - minimum) / range else 1.0 } ?: 0.0
        if (
            !intent.hasMetadataFilters && intent.semanticText.isNotBlank() && hasSemantic &&
            song.id !in semantic && lexicalScore == 0.0
        ) return@mapNotNull null
        if (!intent.hasMetadataFilters && intent.semanticText.isNotBlank() && !hasSemantic && lexicalScore == 0.0) return@mapNotNull null
        if (!intent.hasMetadataFilters && intent.semanticText.isNotBlank() && !hasTextMatch && !hasSemantic) return@mapNotNull null
        val score = when {
            intent.semanticText.isBlank() -> lexicalScore
            hasSemantic -> semanticScore * 0.72 + lexicalScore * 0.28
            else -> lexicalScore
        }
        song to score
    }
    return scored.sortedWith(compareByDescending<Pair<Song, Double>> { it.second }.thenBy { it.first.title.lowercase(Locale.ROOT) }.thenBy { it.first.id })
        .take(limit)
        .map { it.first }
}

internal fun buildPersonalizedDiscovery(
    songs: List<Song>,
    events: List<LocalListeningEvent>,
    favoriteIds: Set<Long>,
    audioVectors: Map<Long, FloatArray>,
    tagsBySong: Map<Long, List<MusicTag>>,
    currentSessionId: String,
    nowMs: Long,
    limit: Int = 30,
): PersonalizedDiscovery {
    if (songs.isEmpty() || limit <= 0) return PersonalizedDiscovery()
    val catalog = songs.distinctBy(Song::id)
    val songById = catalog.associateBy(Song::id)
    val usableEvents = events.filter { it.songId in songById && it.timestampMs <= nowMs }
    val meaningfulSignalCount = usableEvents.asSequence()
        .filter { it.eventType in TASTE_SIGNAL_EVENTS }
        .map(LocalListeningEvent::songId)
        .distinct()
        .count()
    if (favoriteIds.isEmpty() && meaningfulSignalCount < 2) return PersonalizedDiscovery()

    val longTermWeights = mutableMapOf<Long, Double>()
    val sessionWeights = mutableMapOf<Long, Double>()
    val longTermGenres = mutableMapOf<String, Double>()
    val sessionGenres = mutableMapOf<String, Double>()
    val longTermArtists = mutableMapOf<String, Double>()
    val sessionArtists = mutableMapOf<String, Double>()
    val longTermTags = mutableMapOf<String, Double>()
    val sessionTags = mutableMapOf<String, Double>()
    val recentPlays = mutableMapOf<Long, Long>()
    val positiveByGenre = mutableMapOf<String, Int>()
    val playCounts = usableEvents.asSequence()
        .filter { it.eventType == "play" || it.eventType == "replay" }
        .groupingBy(LocalListeningEvent::songId)
        .eachCount()

    usableEvents.forEach { event ->
        val ageMs = (nowMs - event.timestampMs).coerceAtLeast(0L)
        val decay = exp(-ageMs.toDouble() / LONG_TERM_HALF_LIFE_MS)
        val inSession = event.sessionId == currentSessionId
        val baseWeight = when (event.eventType) {
            // Current favorites are the source of truth below; historical toggles must not resurrect an unfavorite.
            "favorite", "unfavorite" -> 0.0
            "completion", "complete" -> 1.4 * (event.completionRatio ?: 1f).coerceIn(0f, 1f)
            "replay" -> 0.45
            "playlist_add" -> 0.3
            // Starting a song is useful immediate context, but weak long-term evidence.
            "play" -> if (inSession) 0.55 else 0.12
            // An early skip is only a small contextual signal, not a dislike.
            "skip" -> if (event.skipPositionSeconds != null && event.skipPositionSeconds < EARLY_SKIP_SECONDS) -0.18 else -0.04
            "unfavorite", "playlist_remove" -> -0.12
            else -> 0.0
        }
        if (baseWeight == 0.0) return@forEach
        val song = songById.getValue(event.songId)
        val longWeight = baseWeight * decay
        longTermWeights[event.songId] = (longTermWeights[event.songId] ?: 0.0) + longWeight
        if (inSession) sessionWeights[event.songId] = (sessionWeights[event.songId] ?: 0.0) + baseWeight
        if (event.eventType in PLAY_EVENTS) recentPlays[event.songId] = maxOf(recentPlays[event.songId] ?: 0L, event.timestampMs)
        if (baseWeight > 0.0) {
            tagsBySong[event.songId].orEmpty().asSequence()
                .filter { it.probability >= it.threshold }
                .forEach { tag ->
                    val key = tag.discoveryKey()
                    longTermTags[key] = (longTermTags[key] ?: 0.0) + longWeight * tag.probability
                    if (inSession) sessionTags[key] = (sessionTags[key] ?: 0.0) + baseWeight * tag.probability
                }
            song.genreNames().forEach { genre ->
                val key = genre.normalized()
                longTermGenres[key] = (longTermGenres[key] ?: 0.0) + longWeight
                if (inSession) sessionGenres[key] = (sessionGenres[key] ?: 0.0) + baseWeight
                positiveByGenre[key] = (positiveByGenre[key] ?: 0) + 1
            }
            val artist = song.artist.takeUnless { it.equals("Unknown artist", true) }?.normalized()
            if (artist != null) {
                longTermArtists[artist] = (longTermArtists[artist] ?: 0.0) + longWeight
                if (inSession) sessionArtists[artist] = (sessionArtists[artist] ?: 0.0) + baseWeight
            }
        }
    }
    favoriteIds.forEach { id ->
        if (id !in songById) return@forEach
        val song = songById.getValue(id)
        longTermWeights[id] = (longTermWeights[id] ?: 0.0) + 2.8
        song.genreNames().forEach { genre ->
            val key = genre.normalized()
            longTermGenres[key] = (longTermGenres[key] ?: 0.0) + 2.8
            positiveByGenre[key] = (positiveByGenre[key] ?: 0) + 1
        }
        song.artist.takeUnless { it.equals("Unknown artist", true) }?.normalized()?.let { key ->
            longTermArtists[key] = (longTermArtists[key] ?: 0.0) + 2.8
        }
        tagsBySong[id].orEmpty().asSequence()
            .filter { it.probability >= it.threshold }
            .forEach { tag ->
                val key = tag.discoveryKey()
                longTermTags[key] = (longTermTags[key] ?: 0.0) + 2.8 * tag.probability
            }
    }

    val longVector = weightedVector(longTermWeights, audioVectors)
    val sessionVector = weightedVector(sessionWeights, audioVectors)
    val longGenreMax = longTermGenres.values.maxOrNull() ?: 0.0
    val sessionGenreMax = sessionGenres.values.maxOrNull() ?: 0.0
    val longArtistMax = longTermArtists.values.maxOrNull() ?: 0.0
    val sessionArtistMax = sessionArtists.values.maxOrNull() ?: 0.0
    val longTagMax = longTermTags.values.maxOrNull() ?: 0.0
    val sessionTagMax = sessionTags.values.maxOrNull() ?: 0.0
    val ranked = catalog.mapNotNull { song ->
        val vector = audioVectors[song.id]?.takeIf { it.isValidVector() }
        val longContent = vector?.let { cosine(it, longVector) } ?: 0.0
        val sessionContent = vector?.let { cosine(it, sessionVector) } ?: 0.0
        val genreScore = song.genreNames().maxOfOrNull { longTermGenres[it.normalized()].orZero().normalizedBy(longGenreMax) } ?: 0.0
        val sessionGenreScore = song.genreNames().maxOfOrNull { sessionGenres[it.normalized()].orZero().normalizedBy(sessionGenreMax) } ?: 0.0
        val artistKey = song.artist.normalized()
        val artistScore = longArtistsScore(artistKey, longTermArtists, longArtistMax)
        val sessionArtistScore = longArtistsScore(artistKey, sessionArtists, sessionArtistMax)
        val tagScore = tagsBySong[song.id].orEmpty().asSequence()
            .filter { it.probability >= it.threshold }
            .maxOfOrNull { tag -> longTermTags[tag.discoveryKey()].orZero().normalizedBy(longTagMax) * tag.probability }
            ?: 0.0
        val sessionTagScore = tagsBySong[song.id].orEmpty().asSequence()
            .filter { it.probability >= it.threshold }
            .maxOfOrNull { tag -> sessionTags[tag.discoveryKey()].orZero().normalizedBy(sessionTagMax) * tag.probability }
            ?: 0.0
        val lastPlay = recentPlays[song.id]
        val daysSincePlay = lastPlay?.let { (nowMs - it).coerceAtLeast(0L) / DAY_MS }
        val repeatPenalty = if (daysSincePlay == null) 0.0 else (0.26 * exp(-daysSincePlay.toDouble() / 5.0))
        val score = longContent * .34 + genreScore * .17 + artistScore * .13 +
            sessionContent * .18 + sessionGenreScore * .08 + sessionArtistScore * .04 +
            tagScore * .04 + sessionTagScore * .03 + (if (song.id in favoriteIds) .12 else 0.0) - repeatPenalty
        val hasContentEvidence = vector != null && (longVector.isNotEmpty() || sessionVector.isNotEmpty())
        val hasPreferenceEvidence = song.id in favoriteIds || hasContentEvidence || genreScore > 0.0 ||
            sessionGenreScore > 0.0 || artistScore > 0.0 || sessionArtistScore > 0.0 || tagScore > 0.0 || sessionTagScore > 0.0
        song.takeIf { hasPreferenceEvidence }?.let { it to score }
    }.sortedWith(compareByDescending<Pair<Song, Double>> { it.second }.thenBy { it.first.id })

    val recommendations = diverseOrder(ranked, limit)
    val mixes = buildList {
        if (recommendations.isNotEmpty()) {
            add(SmartMix("made-for-you", "Made for you", "Shaped by your listening", recommendations.take(MIX_SIZE)))
        }
        val rediscover = diverseOrder(
            ranked.filter { (song, _) -> recentPlays[song.id]?.let { nowMs - it >= REDISCOVER_AFTER_MS } == true },
            MIX_SIZE,
        )
        if (rediscover.size >= MIN_MIX_SIZE) add(SmartMix("rediscover", "Rediscover", "A fresh return to your library", rediscover))

        val repeatSongs = diverseOrder(
            ranked.filter { (song, _) -> (playCounts[song.id] ?: 0) >= 2 },
            MIX_SIZE,
        )
        if (repeatSongs.size >= MIN_MIX_SIZE) add(SmartMix("on-repeat", "On repeat", "Tracks you keep coming back to", repeatSongs))

        val favoriteGenre = positiveByGenre.entries.maxByOrNull(Map.Entry<String, Int>::value)
            ?.takeIf { it.value >= 2 }
        favoriteGenre?.let { (genreKey, _) ->
            val genreName = catalog.flatMap(Song::genreNames).firstOrNull { it.normalized() == genreKey } ?: return@let
            val genreSongs = diverseOrder(ranked.filter { (song, _) -> song.genreNames().any { it.normalized() == genreKey } }, MIX_SIZE)
            if (genreSongs.size >= MIN_MIX_SIZE) {
                add(SmartMix("genre:$genreKey", "$genreName mix", "Built from your tagged $genreName tracks", genreSongs))
            }
        }
    }
    return PersonalizedDiscovery(recommendations, mixes)
}

internal enum class TrackTransition { INITIAL, AUTOMATIC, REPEAT, FORWARD_SKIP, PREVIOUS, QUEUE_CHANGE }

internal class PlaybackFeedbackReducer(private val sessionId: String = "local") {
    private data class ActiveTrack(
        val songId: Long,
        var durationMs: Long,
        val selectionSource: String?,
        val recommendationId: String?,
        val queueIndex: Int,
        var positionMs: Long,
        var listenedMs: Long = 0L,
        var lastElapsedMs: Long? = null,
        var isPlaying: Boolean = false,
        var playConfirmed: Boolean = false,
        var endRecorded: Boolean = false,
    )

    private var active: ActiveTrack? = null

    fun onTrackChanged(
        songId: Long,
        durationMs: Long,
        positionMs: Long,
        transition: TrackTransition,
        index: Int,
        selectionSource: String?,
        nowElapsedMs: Long,
        isPlaying: Boolean = false,
        recommendationId: String? = null,
        timestampMs: Long = nowElapsedMs,
    ): List<LocalListeningEvent> {
        val events = mutableListOf<LocalListeningEvent>()
        active?.let { previous ->
            accountTime(previous, nowElapsedMs)
            if (previous.playConfirmed) {
                when (transition) {
                    TrackTransition.AUTOMATIC, TrackTransition.REPEAT -> {
                        previous.completionEvent(timestampMs)?.let(events::add)
                        if (transition == TrackTransition.REPEAT) events += event(
                            songId = songId,
                            type = "replay",
                            timestampMs = timestampMs,
                            selectionSource = selectionSource,
                            recommendationId = recommendationId,
                        )
                    }
                    TrackTransition.FORWARD_SKIP -> if (index > previous.queueIndex) {
                        events += previous.toEvent("skip", timestampMs).copy(
                            skipPositionSeconds = previous.positionMs / 1_000L,
                        )
                    }
                    TrackTransition.PREVIOUS -> events += event(
                        songId = songId,
                        type = "replay",
                        timestampMs = timestampMs,
                        selectionSource = selectionSource,
                        recommendationId = recommendationId,
                    )
                    else -> Unit
                }
            }
        }
        active = ActiveTrack(
            songId = songId,
            durationMs = durationMs.coerceAtLeast(0L),
            selectionSource = selectionSource,
            recommendationId = recommendationId,
            queueIndex = index,
            positionMs = positionMs.coerceAtLeast(0L),
        )
        if (isPlaying) events += setPlaying(true, nowElapsedMs, timestampMs)
        return events
    }

    fun setPlaying(isPlaying: Boolean, nowElapsedMs: Long, timestampMs: Long = nowElapsedMs): List<LocalListeningEvent> {
        val track = active ?: return emptyList()
        accountTime(track, nowElapsedMs)
        if (track.isPlaying == isPlaying) return emptyList()
        track.isPlaying = isPlaying
        track.lastElapsedMs = nowElapsedMs
        if (isPlaying && !track.playConfirmed) {
            track.playConfirmed = true
            return listOf(track.toEvent("play", timestampMs))
        }
        if (track.playConfirmed) return listOf(track.toEvent(if (isPlaying) "resume" else "pause", timestampMs))
        return emptyList()
    }

    fun sample(positionMs: Long, nowElapsedMs: Long, durationMs: Long? = null) {
        val track = active ?: return
        accountTime(track, nowElapsedMs)
        track.positionMs = positionMs.coerceAtLeast(0L)
        durationMs?.takeIf { it > 0L }?.let { track.durationMs = it }
    }

    fun onEnded(nowElapsedMs: Long, timestampMs: Long = nowElapsedMs): List<LocalListeningEvent> {
        val track = active ?: return emptyList()
        accountTime(track, nowElapsedMs)
        return listOfNotNull(track.completionEvent(timestampMs))
    }

    private fun accountTime(track: ActiveTrack, nowElapsedMs: Long) {
        val previous = track.lastElapsedMs
        if (track.isPlaying && previous != null && nowElapsedMs >= previous) {
            track.listenedMs += (nowElapsedMs - previous).coerceAtMost(MAX_SAMPLE_GAP_MS)
        }
        track.lastElapsedMs = nowElapsedMs
    }

    private fun ActiveTrack.toEvent(type: String, timestampMs: Long) = LocalListeningEvent(
        songId = songId,
        timestampMs = timestampMs,
        eventType = type,
        sessionId = sessionId,
        listenSeconds = listenedMs / 1_000L,
        completionRatio = durationMs.takeIf { it > 0L }?.let { (listenedMs.toFloat() / it).coerceIn(0f, 1f) },
        selectionSource = selectionSource,
        recommendationId = recommendationId,
    )

    private fun ActiveTrack.completionEvent(timestampMs: Long): LocalListeningEvent? {
        if (!playConfirmed || endRecorded) return null
        val ratio = durationMs.takeIf { it > 0L }?.let { (listenedMs.toFloat() / it).coerceIn(0f, 1f) }
            ?: return null
        if (ratio < COMPLETION_THRESHOLD) return null
        endRecorded = true
        return toEvent("completion", timestampMs).copy(completionRatio = ratio)
    }

    private fun event(
        songId: Long,
        type: String,
        timestampMs: Long,
        selectionSource: String?,
        recommendationId: String?,
    ) = LocalListeningEvent(songId, timestampMs, type, sessionId, selectionSource = selectionSource, recommendationId = recommendationId)

    private companion object {
        const val COMPLETION_THRESHOLD = .8f
        const val MAX_SAMPLE_GAP_MS = 10_000L
    }
}

internal fun personalizedQueue(
    selected: Song,
    contextQueue: List<Song>,
    recommendations: List<Song>,
    recommendationLimit: Int = 12,
): List<Song> {
    if (recommendationLimit <= 0) return (listOf(selected) + contextQueue).distinctBy(Song::id)
    val selectedId = selected.id
    val recommendationTracks = recommendations.asSequence().filter { it.id != selectedId }
        .distinctBy(Song::id).take(recommendationLimit).toList()
    val recommendationIds = recommendationTracks.mapTo(mutableSetOf(), Song::id)
    return buildList {
        add(selected)
        addAll(recommendationTracks)
        contextQueue.asSequence().filter { it.id != selectedId && it.id !in recommendationIds }
            .distinctBy(Song::id).forEach(::add)
    }
}

internal fun extractExplicitLanguageTags(fields: List<TextMetadataField>): List<String> = fields.asSequence()
    .filter { field -> field.key.normalizedMetadataKey() in LANGUAGE_METADATA_KEYS }
    .flatMap { field -> field.value.split(',', ';', '/', '|').asSequence() }
    .mapNotNull(::canonicalLanguageTag)
    .distinct()
    .toList()

private fun lexicalScore(song: Song, tokens: List<String>, tags: List<MusicTag>): Double {
    if (tokens.isEmpty()) return 0.0
    val fields = listOfNotNull(
        song.title to 1.0,
        song.artist to .85,
        song.albumArtist to .8,
        song.album to .75,
        song.composer to .72,
        song.genre to .8,
        song.year?.toString() to .7,
        song.trackNumber?.toString() to .5,
        song.discNumber?.toString() to .4,
    ) + song.genreNames().map { it to .8 } + song.languageTags.map { it to .6 } +
        tags.filter { it.probability >= it.threshold }.map { "${it.label} ${it.taxonomy} ${it.task}" to it.probability.toDouble() }
    val values = fields.mapNotNull { (value, weight) -> value?.lowercase(Locale.ROOT)?.let { it to weight } }
    val perToken = tokens.map { token ->
        values.maxOfOrNull { (value, weight) ->
            when {
                value == token -> weight
                value.startsWith(token) -> weight * .85
                value.contains(token) -> weight * .65
                else -> 0.0
            }
        } ?: 0.0
    }
    return perToken.average()
}

private fun takeKnownField(query: String, values: List<String>, marker: String): String? {
    val markerPattern = Regex("(?i)\\b${Regex.escape(marker)}\\s+([\\p{L}\\p{N}][\\p{L}\\p{N} .&'_-]*)")
    val phrase = markerPattern.find(query)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (phrase.isBlank()) return null
    return values.filter(String::isNotBlank).distinctBy(String::normalized)
        .filter { known -> phrase.equals(known, true) || phrase.startsWith("$known ", true) }
        .maxByOrNull(String::length)
}

private fun weightedVector(weights: Map<Long, Double>, vectors: Map<Long, FloatArray>): FloatArray {
    val valid = weights.mapNotNull { (id, weight) -> vectors[id]?.takeIf(FloatArray::isValidVector)?.let { it to weight } }
    val dimension = valid.firstOrNull()?.first?.size ?: return FloatArray(0)
    val sum = DoubleArray(dimension)
    valid.forEach { (vector, weight) ->
        if (vector.size != dimension) return@forEach
        vector.forEachIndexed { index, value -> sum[index] += value * weight }
    }
    return normalizeVector(sum.map(Double::toFloat).toFloatArray())
}

private fun cosine(left: FloatArray, right: FloatArray): Double {
    if (!left.isValidVector() || !right.isValidVector() || left.size != right.size) return 0.0
    var dot = 0.0
    for (index in left.indices) dot += left[index] * right[index]
    return ((dot + 1.0) / 2.0).coerceIn(0.0, 1.0)
}

private fun normalizeVector(vector: FloatArray): FloatArray {
    val norm = sqrt(vector.sumOf { it.toDouble() * it })
    if (norm <= 1e-12 || !norm.isFinite()) return FloatArray(0)
    return FloatArray(vector.size) { (vector[it] / norm).toFloat() }
}

private fun FloatArray.isValidVector(): Boolean = isNotEmpty() && all(Float::isFinite)

private fun diverseOrder(ranked: List<Pair<Song, Double>>, limit: Int): List<Song> {
    val remaining = ranked.toMutableList()
    val selected = mutableListOf<Song>()
    while (remaining.isNotEmpty() && selected.size < limit) {
        val last = selected.lastOrNull()
        val next = remaining.maxWithOrNull(compareBy<Pair<Song, Double>> { (song, score) ->
            score - if (last == null) 0.0 else {
                (if (song.artist.equals(last.artist, true)) .15 else 0.0) +
                    (if (song.album.equals(last.album, true)) .08 else 0.0)
            }
        }.thenByDescending { -it.first.id }) ?: break
        selected += next.first
        remaining.remove(next)
    }
    return selected
}

private fun longArtistsScore(key: String, weights: Map<String, Double>, maximum: Double) = weights[key].orZero().normalizedBy(maximum)
private fun Double?.orZero() = this ?: 0.0
private fun Double.normalizedBy(maximum: Double) = if (maximum <= 0.0) 0.0 else (this / maximum).coerceIn(0.0, 1.0)
private fun String.normalized() = trim().lowercase(Locale.ROOT)
private fun String.tokens() = lowercase(Locale.ROOT).split(WORD_BREAKS)
    .filter { it.length > 1 }
    .distinct()
private fun String.containsWordPhrase(phrase: String): Boolean = Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])").containsMatchIn(this)
private fun String.replacePhrase(phrase: String) = replace(Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])"), " ")
private fun String.normalizedMetadataKey() = lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
private fun MusicTag.discoveryKey() = listOf(task, taxonomy, label)
    .joinToString("|") { it.trim().lowercase(Locale.ROOT) }

private fun languageCode(value: String): String? {
    val normalized = value.trim().lowercase(Locale.ROOT).replace('_', '-')
    if (normalized.isEmpty()) return null
    val code = normalized.substringBefore('-')
    val direct = languageNames.firstOrNull { (name, alias) -> code == alias || normalized == name }
    if (direct != null) return direct.second
    if (code.length == 2) return code.takeIf { it in Locale.getISOLanguages() }
    if (code.length == 3) return iso3ToIso2[code]
    return null
}

private fun canonicalLanguageTag(value: String): String? {
    val trimmed = value.trim().trim('\u0000', ' ', '\'', '"')
    if (trimmed.isEmpty()) return null
    val normalized = trimmed.lowercase(Locale.ROOT).replace('_', '-')
    val primary = normalized.substringBefore('-')
    val code = languageCode(normalized)
    return code ?: primary.takeIf { it.matches(Regex("[a-z]{2,3}")) }
}

private val languageNames: List<Pair<String, String>> = Locale.getISOLanguages().asSequence()
    .mapNotNull { code ->
        val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
        name.takeIf { it.isNotBlank() && it != code }?.let { it to code }
    }
    .distinctBy { it.first }
    .sortedByDescending { it.first.length }
    .toList()

private val iso3ToIso2: Map<String, String> = Locale.getAvailableLocales().asSequence()
    .mapNotNull { locale -> runCatching { locale.isO3Language.lowercase(Locale.ROOT) to locale.language }.getOrNull() }
    .filter { (iso3, iso2) -> iso3.length == 3 && iso2.length == 2 }
    .toMap()

private const val DAY_MS = 86_400_000L
private const val LONG_TERM_HALF_LIFE_MS = 180.0 * DAY_MS
private const val EARLY_SKIP_SECONDS = 20L
private const val REDISCOVER_AFTER_MS = 30L * DAY_MS
private const val MIX_SIZE = 12
private const val MIN_MIX_SIZE = 3
private val PLAY_EVENTS = setOf("play", "completion", "complete", "replay")
private val TASTE_SIGNAL_EVENTS = setOf("play", "completion", "complete", "replay", "playlist_add")
private val LANGUAGE_METADATA_KEYS = setOf("tlan", "lang", "language", "languagecode", "txxxlanguage")
private val LANGUAGE_COMMAND = Regex("(?i)\\b(?:in|language\\s*:)\\s*([\\p{L}-]+)\\b")
private val YEAR = Regex("\\b((?:19|20)\\d{2})\\b")
private val REQUEST_WORDS = Regex("(?i)\\b(?:songs?|tracks?|music|please|play|find|show|some|listen|to|me|for|and|the|in|by|from)\\b")
private val COMPOSED_BY = Regex("(?i)\\bcomposed\\s+by\\b")
private val WHITESPACE = Regex("\\s+")
private val WORD_BREAKS = Regex("[^\\p{L}\\p{N}]+")
