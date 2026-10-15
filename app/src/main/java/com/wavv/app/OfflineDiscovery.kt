package com.wavv.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    preferenceOrder: List<Song> = emptyList(),
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
    val tokens = intent.semanticText.tokens()
    val lexical = candidates.associate { song -> song.id to lexicalScore(song, tokens, tagsBySong[song.id].orEmpty()) }
    val hasSemantic = semanticValues.isNotEmpty()
    val hasTextMatch = lexical.values.any { it > 0.0 }
    val preferencePositions = preferenceOrder.asSequence()
        .distinctBy(Song::id)
        .take(SEARCH_PREFERENCE_LIMIT)
        .mapIndexed { index, song -> song.id to index }
        .toMap()
    val scored = candidates.mapNotNull { song ->
        val lexicalScore = lexical.getValue(song.id)
        val semanticScore = semantic[song.id]?.let { score -> ((score + 1.0) / 2.0).coerceIn(0.0, 1.0) } ?: 0.0
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
        } + preferencePositions[song.id]?.let { index ->
            SEARCH_PREFERENCE_BOOST * (1.0 - index.toDouble() / SEARCH_PREFERENCE_LIMIT)
        }.orZero()
        song to score
    }
    return scored.sortedWith(compareByDescending<Pair<Song, Double>> { it.second }.thenBy { it.first.title.lowercase(Locale.ROOT) }.thenBy { it.first.id })
        .take(limit)
        .map { it.first }
}

internal fun coldStartSeedSongId(
    events: List<LocalListeningEvent>,
    catalogSongIds: Set<Long>,
    nowMs: Long,
): Long? {
    val seed = events.asSequence()
        .filter {
            it.songId in catalogSongIds && it.timestampMs <= nowMs && it.eventType in PLAY_EVENTS &&
                it.selectionSource?.startsWith("autoplay_") != true
        }
        .maxByOrNull(LocalListeningEvent::timestampMs) ?: return null
    val wasSkipped = events.any {
        it.songId == seed.songId && it.sessionId == seed.sessionId && it.eventType == "skip" &&
            it.timestampMs >= seed.timestampMs && skipPreferenceWeight(it) < 0.0
    }
    return seed.songId.takeUnless { wasSkipped }
}

internal fun buildSeededRecommendations(
    songs: List<Song>,
    seedSongId: Long,
    audioVectors: Map<Long, FloatArray>,
    tagsBySong: Map<Long, List<MusicTag>>,
    limit: Int = 30,
): List<Song> {
    if (limit <= 0) return emptyList()
    val catalog = songs.distinctBy(Song::id)
    val seed = catalog.firstOrNull { it.id == seedSongId } ?: return emptyList()
    val seedVector = audioVectors[seedSongId]?.takeIf(FloatArray::isValidVector)
    val seedTags = tagsBySong[seedSongId].orEmpty()
    val seedGenres = seed.genreNames().associate { genre ->
        genre.normalized() to seed.genreConfidence(genre, seedTags)
    }
    val seedTagsByKey = seedTags.asSequence()
        .filter { it.isAboveThreshold() && !it.isGenreTag() }
        .associate { it.discoveryKey() to it.probability.toDouble() }
    val seedCreators = seed.creatorKeys().toSet()

    val ranked = catalog.mapNotNull { song ->
        if (song.id == seedSongId) return@mapNotNull null
        val tags = tagsBySong[song.id].orEmpty()
        val genreScore = song.genreNames().mapNotNull { genre ->
            seedGenres[genre.normalized()]?.let { seedScore ->
                minOf(seedScore, song.genreConfidence(genre, tags))
            }
        }.maxOrNull() ?: 0.0
        val tagScore = tags.asSequence()
            .filter { it.isAboveThreshold() && !it.isGenreTag() }
            .mapNotNull { tag -> seedTagsByKey[tag.discoveryKey()]?.let { minOf(it, tag.probability.toDouble()) } }
            .maxOrNull() ?: 0.0
        val audioScore = if (seedVector == null) 0.0 else {
            audioVectors[song.id]?.takeIf(FloatArray::isValidVector)?.let { cosine(seedVector, it) } ?: 0.0
        }
        val audioMatch = audioScore >= MIN_COLD_START_AUDIO_AFFINITY
        val creatorMatch = seedCreators.any { it in song.creatorKeys() }
        if (!audioMatch && genreScore == 0.0 && tagScore == 0.0 && !creatorMatch) return@mapNotNull null
        val creatorScore = if (creatorMatch) COLD_START_CREATOR_MATCH_SCORE else 0.0
        song to maxOf(if (audioMatch) audioScore else 0.0, genreScore, tagScore, creatorScore)
    }
    return diverseOrder(ranked, limit)
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
    timeZone: ZoneId = ZoneId.systemDefault(),
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
    val positiveByGenre = mutableMapOf<String, Double>()
    val timeWeights = timeOfDayPreferenceWeights(usableEvents, nowMs, timeZone)
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
            "favorite" -> 0.0
            "unfavorite" -> -0.5
            "completion", "complete" -> 1.4 * (event.completionRatio ?: 1f).coerceIn(0f, 1f)
            "replay" -> 0.45
            "playlist_add" -> 0.3
            // Starting a song is useful immediate context, but weak long-term evidence.
            "play" -> playPreferenceWeight(event.selectionSource, inSession)
            "queue_add" -> if (event.selectionSource?.startsWith("autoplay_") == true) 0.18 else 0.32
            "skip" -> skipPreferenceWeight(event)
            else -> 0.0
        }
        if (baseWeight == 0.0) return@forEach
        val song = songById.getValue(event.songId)
        val longWeight = baseWeight * decay
        longTermWeights[event.songId] = (longTermWeights[event.songId] ?: 0.0) + longWeight
        if (inSession) sessionWeights[event.songId] = (sessionWeights[event.songId] ?: 0.0) + baseWeight
        if (event.eventType in PLAY_EVENTS) recentPlays[event.songId] = maxOf(recentPlays[event.songId] ?: 0L, event.timestampMs)
        if (baseWeight > 0.0) {
            val tags = tagsBySong[event.songId].orEmpty()
            tags.asSequence()
                .filter { it.isAboveThreshold() && !it.isGenreTag() }
                .forEach { tag ->
                    val key = tag.discoveryKey()
                    longTermTags[key] = (longTermTags[key] ?: 0.0) + longWeight * tag.probability
                    if (inSession) sessionTags[key] = (sessionTags[key] ?: 0.0) + baseWeight * tag.probability
                }
            song.genreNames().forEach { genre ->
                val confidence = song.genreConfidence(genre, tags)
                if (confidence <= 0.0) return@forEach
                val key = genre.normalized()
                longTermGenres[key] = (longTermGenres[key] ?: 0.0) + longWeight * confidence
                if (inSession) sessionGenres[key] = (sessionGenres[key] ?: 0.0) + baseWeight * confidence
                positiveByGenre[key] = (positiveByGenre[key] ?: 0.0) + confidence
            }
            song.creatorKeys().forEach { creator ->
                longTermArtists[creator] = (longTermArtists[creator] ?: 0.0) + longWeight
                if (inSession) sessionArtists[creator] = (sessionArtists[creator] ?: 0.0) + baseWeight
            }
        }
    }
    favoriteIds.forEach { id ->
        if (id !in songById) return@forEach
        val song = songById.getValue(id)
        val tags = tagsBySong[id].orEmpty()
        longTermWeights[id] = (longTermWeights[id] ?: 0.0) + 2.8
        song.genreNames().forEach { genre ->
            val confidence = song.genreConfidence(genre, tags)
            if (confidence <= 0.0) return@forEach
            val key = genre.normalized()
            longTermGenres[key] = (longTermGenres[key] ?: 0.0) + 2.8 * confidence
            positiveByGenre[key] = (positiveByGenre[key] ?: 0.0) + confidence
        }
        song.creatorKeys().forEach { creator ->
            longTermArtists[creator] = (longTermArtists[creator] ?: 0.0) + 2.8
        }
        tags.asSequence()
            .filter { it.isAboveThreshold() && !it.isGenreTag() }
            .forEach { tag ->
                val key = tag.discoveryKey()
                longTermTags[key] = (longTermTags[key] ?: 0.0) + 2.8 * tag.probability
            }
    }

    val timeGenres = mutableMapOf<String, Double>()
    val timeArtists = mutableMapOf<String, Double>()
    val timeTags = mutableMapOf<String, Double>()
    timeWeights.forEach { (id, weight) ->
        val song = songById[id] ?: return@forEach
        val tags = tagsBySong[id].orEmpty()
        song.genreNames().forEach { genre ->
            val confidence = song.genreConfidence(genre, tags)
            if (confidence <= 0.0) return@forEach
            val key = genre.normalized()
            timeGenres[key] = (timeGenres[key] ?: 0.0) + weight * confidence
        }
        song.creatorKeys().forEach { creator ->
            timeArtists[creator] = (timeArtists[creator] ?: 0.0) + weight
        }
        tags.asSequence()
            .filter { it.isAboveThreshold() && !it.isGenreTag() }
            .forEach { tag ->
                val key = tag.discoveryKey()
                timeTags[key] = (timeTags[key] ?: 0.0) + weight * tag.probability
            }
    }

    val longVector = weightedVector(longTermWeights, audioVectors)
    val sessionVector = weightedVector(sessionWeights, audioVectors)
    val timeVector = weightedVector(timeWeights, audioVectors)
    val longGenreMax = longTermGenres.values.maxOrNull() ?: 0.0
    val sessionGenreMax = sessionGenres.values.maxOrNull() ?: 0.0
    val longArtistMax = longTermArtists.values.maxOrNull() ?: 0.0
    val sessionArtistMax = sessionArtists.values.maxOrNull() ?: 0.0
    val longTagMax = longTermTags.values.maxOrNull() ?: 0.0
    val sessionTagMax = sessionTags.values.maxOrNull() ?: 0.0
    val timeGenreMax = timeGenres.values.maxOrNull() ?: 0.0
    val timeArtistMax = timeArtists.values.maxOrNull() ?: 0.0
    val timeTagMax = timeTags.values.maxOrNull() ?: 0.0
    val ranked = catalog.mapNotNull { song ->
        val vector = audioVectors[song.id]?.takeIf { it.isValidVector() }
        val longContent = vector?.let { cosine(it, longVector) } ?: 0.0
        val sessionContent = vector?.let { cosine(it, sessionVector) } ?: 0.0
        val timeContent = vector?.let { cosine(it, timeVector) } ?: 0.0
        val tags = tagsBySong[song.id].orEmpty()
        val genreScore = song.genreNames().maxOfOrNull {
            longTermGenres[it.normalized()].orZero().normalizedBy(longGenreMax) * song.genreConfidence(it, tags)
        } ?: 0.0
        val sessionGenreScore = song.genreNames().maxOfOrNull {
            sessionGenres[it.normalized()].orZero().normalizedBy(sessionGenreMax) * song.genreConfidence(it, tags)
        } ?: 0.0
        val creators = song.creatorKeys()
        val artistScore = creators.maxOfOrNull { longArtistsScore(it, longTermArtists, longArtistMax) } ?: 0.0
        val sessionArtistScore = creators.maxOfOrNull { longArtistsScore(it, sessionArtists, sessionArtistMax) } ?: 0.0
        val tagScore = tags.asSequence()
            .filter { it.isAboveThreshold() && !it.isGenreTag() }
            .maxOfOrNull { tag -> longTermTags[tag.discoveryKey()].orZero().normalizedBy(longTagMax) * tag.probability }
            ?: 0.0
        val sessionTagScore = tags.asSequence()
            .filter { it.isAboveThreshold() && !it.isGenreTag() }
            .maxOfOrNull { tag -> sessionTags[tag.discoveryKey()].orZero().normalizedBy(sessionTagMax) * tag.probability }
            ?: 0.0
        val timeGenreScore = song.genreNames().maxOfOrNull {
            timeGenres[it.normalized()].orZero().normalizedBy(timeGenreMax) * song.genreConfidence(it, tags)
        } ?: 0.0
        val timeArtistScore = creators.maxOfOrNull { longArtistsScore(it, timeArtists, timeArtistMax) } ?: 0.0
        val timeTagScore = tags.asSequence()
            .filter { it.isAboveThreshold() && !it.isGenreTag() }
            .maxOfOrNull { tag -> timeTags[tag.discoveryKey()].orZero().normalizedBy(timeTagMax) * tag.probability }
            ?: 0.0
        val lastPlay = recentPlays[song.id]
        val daysSincePlay = lastPlay?.let { (nowMs - it).coerceAtLeast(0L) / DAY_MS }
        val repeatPenalty = if (daysSincePlay == null) 0.0 else (0.26 * exp(-daysSincePlay.toDouble() / 5.0))
        val score = longContent * .34 + genreScore * .17 + artistScore * .13 +
            sessionContent * .18 + sessionGenreScore * .08 + sessionArtistScore * .04 +
            tagScore * .04 + sessionTagScore * .03 + timeContent * .08 + timeGenreScore * .03 +
            timeArtistScore * .01 + timeTagScore * .01 + (if (song.id in favoriteIds) .12 else 0.0) - repeatPenalty
        val hasContentEvidence = vector != null && (longVector.isNotEmpty() || sessionVector.isNotEmpty())
        val hasPreferenceEvidence = song.id in favoriteIds || hasContentEvidence || genreScore > 0.0 ||
            sessionGenreScore > 0.0 || artistScore > 0.0 || sessionArtistScore > 0.0 || tagScore > 0.0 ||
            sessionTagScore > 0.0 || timeContent > 0.0 || timeGenreScore > 0.0 || timeArtistScore > 0.0 || timeTagScore > 0.0
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

        val favoriteGenre = positiveByGenre.entries.maxByOrNull(Map.Entry<String, Double>::value)
            ?.takeIf { it.value >= 2.0 }
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

internal fun timeOfDayPreferenceWeights(
    events: List<LocalListeningEvent>,
    nowMs: Long,
    timeZone: ZoneId,
): Map<Long, Double> {
    val targetPeriod = dayPeriod(nowMs, timeZone)
    val signals = events.mapNotNull { event ->
        if (event.timestampMs > nowMs) return@mapNotNull null
        if (dayPeriod(event.timestampMs, timeZone) != targetPeriod) return@mapNotNull null
        val engagement = when (event.eventType) {
            "completion", "complete" -> event.completionRatio ?: 0.0f
            "replay" -> 0.65f
            "skip" -> event.completionRatio?.takeIf { it >= .65f }
                ?: if (event.completionRatio == null && event.listenSeconds >= LONG_LISTEN_SKIP_SECONDS) .65f else 0.0f
            else -> 0.0f
        }.coerceIn(0.0f, 1.0f)
        if (engagement < .5f) return@mapNotNull null
        val localDate = Instant.ofEpochMilli(event.timestampMs).atZone(timeZone).toLocalDate()
        val decay = exp(-(nowMs - event.timestampMs).coerceAtLeast(0L).toDouble() / LONG_TERM_HALF_LIFE_MS)
        Triple(event.songId to localDate, engagement.toDouble() * decay, event.songId)
    }
    if (signals.map { it.first.second }.distinct().size < MIN_TIME_PREFERENCE_DAYS ||
        signals.map(Triple<Pair<Long, LocalDate>, Double, Long>::third).distinct().size < 2
    ) return emptyMap()

    return signals.groupBy { it.first }.mapValues { (_, sameSongDay) -> sameSongDay.maxOf { it.second } }
        .entries.groupBy { it.key.first }
        .mapValues { (_, sameSongSignals) -> sameSongSignals.sumOf { it.value } }
}

internal fun reconcileAutoplayRecommendations(
    current: List<Song>,
    ranked: List<Song>,
    queuedIds: Set<Long>,
    dismissedIds: Set<Long>,
    preserveManualOrder: Boolean,
    limit: Int = 30,
): List<Song> {
    if (limit <= 0) return emptyList()
    val available = ranked.asSequence()
        .filterNot { it.id in queuedIds || it.id in dismissedIds }
        .distinctBy(Song::id)
        .toList()
    if (!preserveManualOrder) return available.take(limit)

    val availableById = available.associateBy(Song::id)
    val kept = current.asSequence()
        .filterNot { it.id in queuedIds || it.id in dismissedIds }
        .distinctBy(Song::id)
        .mapNotNull { availableById[it.id] }
        .toList()
    val keptIds = kept.mapTo(mutableSetOf(), Song::id)
    return (kept + available.filterNot { it.id in keptIds }).take(limit)
}

internal fun recommendationOrderedSongs(songs: List<Song>, recommendations: List<Song>): List<Song> {
    val positions = recommendations.map(Song::id).distinct().withIndex().associate { it.value to it.index }
    return songs.withIndex()
        .sortedWith(compareBy<IndexedValue<Song>> { positions[it.value.id] ?: Int.MAX_VALUE }.thenBy { it.index })
        .map(IndexedValue<Song>::value)
}

internal fun playPreferenceWeight(selectionSource: String?, inSession: Boolean): Double = when (selectionSource) {
    "autoplay_auto" -> if (inSession) 0.08 else 0.025
    "smart_mix", "home_mix", "autoplay_recommendation" -> if (inSession) 0.38 else 0.16
    "manual", "home", "home_shelf", "search", "genre", "collection", "collection_shuffle" ->
        if (inSession) 0.46 else 0.18
    "queue" -> if (inSession) 0.32 else 0.12
    else -> if (inSession) 0.28 else 0.08
}

private fun skipPreferenceWeight(event: LocalListeningEvent): Double {
    val ratio = event.completionRatio ?: when {
        event.listenSeconds >= LONG_LISTEN_SKIP_SECONDS -> .65f
        event.skipPositionSeconds == null -> return -0.08
        event.skipPositionSeconds < EARLY_SKIP_SECONDS -> 0.05f
        else -> 0.3f
    }
    return when {
        ratio >= .65f -> .12
        ratio >= .2f -> -.04
        else -> -.22
    }
}

private fun dayPeriod(timestampMs: Long, timeZone: ZoneId): Int = when (Instant.ofEpochMilli(timestampMs).atZone(timeZone).hour) {
    in 5..10 -> 0
    in 11..16 -> 1
    in 17..21 -> 2
    else -> 3
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
        song.author to .68,
        song.writer to .68,
        song.recordingDate to .45,
        song.codec to .35,
        song.container to .3,
        song.bitrate?.toString() to .2,
        song.sampleRate?.toString() to .2,
        song.channels?.toString() to .2,
        song.trackCount?.toString() to .1,
    ) + song.fileGenreNames().map { it to .8 } + song.languageTags.map { it to .6 } +
        tags.filter(MusicTag::isAboveThreshold).map { "${it.label} ${it.taxonomy} ${it.task}" to it.probability.toDouble() }
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
private fun Song.creatorKeys(): List<String> = listOfNotNull(
    artist.takeUnless { it.equals("Unknown artist", ignoreCase = true) },
    albumArtist,
    composer,
    author,
    writer,
).map(String::normalized).filter(String::isNotBlank).distinct()
private fun MusicTag.isAboveThreshold(): Boolean =
    probability.isFinite() && probability in 0f..1f && threshold.isFinite() && threshold in 0f..1f && probability >= threshold
private fun MusicTag.isGenreTag(): Boolean = taxonomy.equals("genre", ignoreCase = true)
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
private const val MIN_TIME_PREFERENCE_DAYS = 3
private const val LONG_LISTEN_SKIP_SECONDS = 60L
private const val REDISCOVER_AFTER_MS = 30L * DAY_MS
private const val MIX_SIZE = 12
private const val MIN_MIX_SIZE = 3
private const val SEARCH_PREFERENCE_LIMIT = 20
private const val SEARCH_PREFERENCE_BOOST = 0.035
// shortcut: this is an uncalibrated DCLAP closeness gate; retune only with held-out listening judgments.
private const val MIN_COLD_START_AUDIO_AFFINITY = 0.75
private const val COLD_START_CREATOR_MATCH_SCORE = 0.6
private val PLAY_EVENTS = setOf("play", "completion", "complete", "replay")
private val TASTE_SIGNAL_EVENTS = setOf("play", "completion", "complete", "replay", "playlist_add", "queue_add")
private val LANGUAGE_METADATA_KEYS = setOf("tlan", "lang", "language", "languagecode", "txxxlanguage")
private val LANGUAGE_COMMAND = Regex("(?i)\\b(?:in|language\\s*:)\\s*([\\p{L}-]+)\\b")
private val YEAR = Regex("\\b((?:19|20)\\d{2})\\b")
private val REQUEST_WORDS = Regex("(?i)\\b(?:songs?|tracks?|music|please|play|find|show|some|listen|to|me|for|and|the|in|by|from)\\b")
private val COMPOSED_BY = Regex("(?i)\\bcomposed\\s+by\\b")
private val WHITESPACE = Regex("\\s+")
private val WORD_BREAKS = Regex("[^\\p{L}\\p{N}]+")
