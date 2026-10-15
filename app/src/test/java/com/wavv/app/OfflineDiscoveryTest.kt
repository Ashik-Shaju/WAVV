package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class OfflineDiscoveryTest {
    private val tracks = listOf(
        song(1, title = "Midnight Bass", genre = "Electronic", languageTag = "en-US", composer = "A. Composer"),
        song(2, title = "Quiet Rain", genre = "Ambient", languageTag = null),
        song(3, title = "Tamil Dawn", genre = "Electronic", languageTag = "ta"),
    )

    @Test
    fun parsesMoodAsSemanticIntentAndEnglishAsExplicitMetadataFilter() {
        val intent = parseMusicQuery("happy songs in English", tracks)

        assertEquals("happy", intent.semanticText)
        assertEquals("en", intent.languageCode)
        assertEquals(null, intent.genre)
    }

    @Test
    fun explicitLanguageFilterNeverTreatsMissingTagsAsEnglish() {
        val result = rankLocalSearch(
            songs = tracks,
            intent = parseMusicQuery("bass in English", tracks),
            semanticMatches = emptyList(),
        )

        assertEquals(listOf(1L), result.map(Song::id))
    }

    @Test
    fun languageMetadataReadsOnlyExplicitId3OrVorbisFields() {
        assertEquals(
            listOf("en", "ta"),
            extractExplicitLanguageTags(
                listOf(
                    TextMetadataField("TLAN", "eng"),
                    TextMetadataField("LANGUAGE", "Tamil"),
                    TextMetadataField("TITLE", "English song"),
                ),
            ),
        )
    }

    @Test
    fun mediaTrackLanguageIsIncludedAlongsideEmbeddedLanguageFrames() {
        val fields = trackLanguageFields(
            formatLanguage = "en-US",
            metadataFields = listOf(TextMetadataField("TLAN", "eng")),
        )

        assertEquals(listOf("en"), extractExplicitLanguageTags(fields))
    }

    @Test
    fun unknownMediaTrackLanguageDoesNotBecomeEnglish() {
        val fields = trackLanguageFields(formatLanguage = "und", metadataFields = emptyList())

        assertTrue(extractExplicitLanguageTags(fields).isEmpty())
    }

    @Test
    fun recognizedGenreIsAnExactFilterAndRemainingWordsStaySemantic() {
        val intent = parseMusicQuery("bass songs in Electronic", tracks)

        assertEquals("bass", intent.semanticText)
        assertEquals("Electronic", intent.genre)
        assertEquals(listOf(1L, 3L), rankLocalSearch(tracks, intent, emptyList()).map(Song::id))
    }

    @Test
    fun recognizedModelGenreIsAnExactFilterAndRemainingWordsStaySemantic() {
        val analyzed = tracks.map { song ->
            if (song.id == 2L) {
                song.copy(genre = null, inferredGenres = listOf("Jazz"))
            } else {
                song
            }
        }
        val intent = parseMusicQuery("bass songs in Jazz", analyzed)

        assertEquals("bass", intent.semanticText)
        assertEquals("Jazz", intent.genre)
        assertEquals(listOf(2L), rankLocalSearch(analyzed, intent, emptyList()).map(Song::id))
    }

    @Test
    fun semanticRankingDoesNotChangeWhenAnUnrelatedLowSimilarityTrackIsIndexed() {
        val unrelated = tracks[0].copy(id = 11L, title = "Unrelated", genre = null, genres = emptyList())
        val lexical = tracks[0].copy(id = 12L, title = "Happy", genre = null, genres = emptyList())
        val newlyIndexed = tracks[0].copy(id = 13L, title = "Another track", genre = null, genres = emptyList())
        val intent = MusicQueryIntent("happy")
        val originalMatches = listOf(
            SemanticMatch(unrelated.id, .30f),
            SemanticMatch(lexical.id, .29f),
        )
        val originalOrder = rankLocalSearch(listOf(unrelated, lexical), intent, originalMatches).map(Song::id)
        val expandedOrder = rankLocalSearch(
            listOf(unrelated, lexical, newlyIndexed),
            intent,
            originalMatches + SemanticMatch(newlyIndexed.id, 0f),
        ).take(2).map(Song::id)

        assertEquals(originalOrder, expandedOrder)
    }

    @Test
    fun searchPreferenceOnlyBreaksCloseMatchesAndDoesNotChangeCandidates() {
        val sameQueryMatch = song(4, tracks[0].title, tracks[0].genre.orEmpty(), null)
        val candidates = listOf(tracks[0], tracks[1], sameQueryMatch)
        val intent = MusicQueryIntent("bass")
        val semantic = listOf(
            SemanticMatch(1L, .8f),
            SemanticMatch(2L, .95f),
            SemanticMatch(4L, .8f),
        )

        val result = rankLocalSearch(
            songs = candidates,
            intent = intent,
            semanticMatches = semantic,
            preferenceOrder = listOf(sameQueryMatch),
        )

        assertEquals(listOf(4L, 1L, 2L), result.map(Song::id))
        assertEquals(candidates.map(Song::id).toSet(), result.map(Song::id).toSet())
    }

    @Test
    fun dclapOrderingIsCombinedWithMetadataAndFilteredBeforeResultLimit() {
        val semantic = listOf(
            SemanticMatch(songId = 3L, cosineSimilarity = .98f),
            SemanticMatch(songId = 1L, cosineSimilarity = .72f),
        )

        val result = rankLocalSearch(
            songs = tracks,
            intent = parseMusicQuery("bass in English", tracks),
            semanticMatches = semantic,
            limit = 1,
        )

        assertEquals(listOf(1L), result.map(Song::id))
    }

    @Test
    fun composerAndTagMetadataCanMatchWithoutDclap() {
        val result = rankLocalSearch(
            songs = tracks,
            intent = parseMusicQuery("A. Composer", tracks),
            semanticMatches = emptyList(),
            tagsBySong = mapOf(2L to listOf(MusicTag("mood", "mood", "calm", "model", .9f, .5f))),
        )

        assertEquals(listOf(1L), result.map(Song::id))
        assertFalse(rankLocalSearch(tracks, parseMusicQuery("calm", tracks), emptyList()).any { it.id == 2L })
        assertTrue(
            rankLocalSearch(
                songs = tracks,
                intent = parseMusicQuery("calm", tracks),
                semanticMatches = emptyList(),
                tagsBySong = mapOf(2L to listOf(MusicTag("mood", "mood", "calm", "model", .9f, .5f))),
            ).first().id == 2L,
        )
    }

    @Test
    fun confidentMusicUnderstandingTagsAcrossAllSixteenHeadsAreSearchable() {
        val tags = listOf(
            MusicTag("danceability", "attribute", "Danceable", "model", .8f, .5f),
            MusicTag("mood_acoustic", "mood", "Acoustic", "model", .8f, .5f),
            MusicTag("mood_aggressive", "mood", "Aggressive", "model", .8f, .5f),
            MusicTag("mood_electronic", "mood", "Electronic", "model", .8f, .5f),
            MusicTag("mood_happy", "mood", "Happy", "model", .8f, .5f),
            MusicTag("mood_party", "mood", "Party", "model", .8f, .5f),
            MusicTag("mood_relaxed", "mood", "Relaxed", "model", .8f, .5f),
            MusicTag("mood_sad", "mood", "Sad", "model", .8f, .5f),
            MusicTag("genre:electronic", "genre", "Electronic", "model", .8f, .5f),
            MusicTag("genre:pop", "genre", "Pop", "model", .8f, .5f),
            MusicTag("genre:rock", "genre", "Rock", "model", .8f, .5f),
            MusicTag("genre:ambient", "genre", "Ambient", "model", .8f, .5f),
            MusicTag("genre:easylistening", "genre", "Easy listening", "model", .8f, .5f),
            MusicTag("instrument:piano", "instrument", "Piano", "model", .8f, .5f),
            MusicTag("instrument:synthesizer", "instrument", "Synthesizer", "model", .8f, .5f),
            MusicTag("instrument:bass", "instrument", "Bass", "model", .8f, .5f),
        )
        val candidates = tags.mapIndexed { index, _ ->
            tracks.first().copy(
                id = 100L + index,
                title = "Track $index",
                artist = "Artist $index",
                album = "Album $index",
                genre = null,
                genres = emptyList(),
                inferredGenres = emptyList(),
            )
        }
        val tagsBySong = candidates.mapIndexed { index, song -> song.id to listOf(tags[index]) }.toMap()

        tags.forEachIndexed { index, tag ->
            val results = rankLocalSearch(
                songs = candidates,
                intent = MusicQueryIntent(tag.label.lowercase()),
                semanticMatches = emptyList(),
                tagsBySong = tagsBySong,
            )

            assertTrue("${tag.task} should be searchable", results.any { it.id == candidates[index].id })
        }
        val belowThreshold = candidates.first().copy(id = 999L)
        assertTrue(
            rankLocalSearch(
                songs = listOf(belowThreshold),
                intent = MusicQueryIntent("oboe"),
                semanticMatches = emptyList(),
                tagsBySong = mapOf(
                    belowThreshold.id to listOf(MusicTag("instrument:oboe", "instrument", "Oboe", "model", .49f, .5f)),
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun recommendationsRequireRealTasteEvidenceAndProduceNoColdStartMixes() {
        val discovery = buildPersonalizedDiscovery(
            songs = tracks,
            events = emptyList(),
            favoriteIds = emptySet(),
            audioVectors = emptyMap(),
            tagsBySong = emptyMap(),
            currentSessionId = "session",
            nowMs = 1_000_000L,
        )

        assertTrue(discovery.recommendations.isEmpty())
        assertTrue(discovery.mixes.isEmpty())
    }

    @Test
    fun repeatedEventsForOneSongDoNotPretendThereIsCatalogTasteEvidence() {
        val discovery = buildPersonalizedDiscovery(
            songs = tracks,
            events = listOf(
                LocalListeningEvent(1L, 900_000L, "play", sessionId = "old"),
                LocalListeningEvent(1L, 950_000L, "completion", sessionId = "old", completionRatio = 1f),
            ),
            favoriteIds = emptySet(),
            audioVectors = emptyMap(),
            tagsBySong = emptyMap(),
            currentSessionId = "new",
            nowMs = 1_000_000L,
        )

        assertTrue(discovery.recommendations.isEmpty())
        assertTrue(discovery.mixes.isEmpty())
    }

    @Test
    fun coldStartSeedUsesLatestDirectPlayButRejectsAutoplayAndNegativeSkip() {
        val directPlay = LocalListeningEvent(1L, 900L, "play", "session", selectionSource = "home")
        val autoplay = LocalListeningEvent(2L, 950L, "play", "session", selectionSource = "autoplay_auto")

        assertEquals(1L, coldStartSeedSongId(listOf(directPlay), setOf(1L, 2L), nowMs = 1_000L))
        assertEquals(null, coldStartSeedSongId(listOf(autoplay), setOf(1L, 2L), nowMs = 1_000L))
        assertEquals(
            null,
            coldStartSeedSongId(
                listOf(directPlay, LocalListeningEvent(1L, 950L, "skip", "session", skipPositionSeconds = 2L)),
                setOf(1L, 2L),
                nowMs = 1_000L,
            ),
        )
        assertEquals(null, coldStartSeedSongId(listOf(directPlay), setOf(2L), nowMs = 1_000L))
    }

    @Test
    fun seededRecommendationsUseStrongAudioOrSharedMetadataAndRejectWeakVectorOnlyMatches() {
        val seed = song(10, "Seed", "Rock", null)
        val genreMatch = song(11, "Genre match", "Rock", null)
        val vectorMatch = song(12, "Audio match", "Ambient", null)
        val weakVectorOnly = song(13, "Weak audio", "Jazz", null)
        val calmMatch = song(14, "Tag match", "Pop", null)
        val catalog = listOf(seed, genreMatch, vectorMatch, weakVectorOnly, calmMatch)
        val vectors = mapOf(
            seed.id to floatArrayOf(1f, 0f),
            vectorMatch.id to floatArrayOf(.8f, .6f),
            weakVectorOnly.id to floatArrayOf(0f, 1f),
        )
        val tags = mapOf(
            seed.id to listOf(MusicTag("mood", "mood", "calm", "model", .9f, .5f)),
            calmMatch.id to listOf(MusicTag("mood", "mood", "calm", "model", .8f, .5f)),
        )

        val recommendations = buildSeededRecommendations(catalog, seed.id, vectors, tags)

        assertTrue(recommendations.any { it.id == genreMatch.id })
        assertTrue(recommendations.any { it.id == vectorMatch.id })
        assertTrue(recommendations.any { it.id == calmMatch.id })
        assertFalse(recommendations.any { it.id == seed.id || it.id == weakVectorOnly.id })
    }

    @Test
    fun tagRecommendationsMatchTheUsersPositiveTagsInsteadOfAnyConfidentTag() {
        val discovery = buildPersonalizedDiscovery(
            songs = tracks.map { if (it.id == 3L) it.copy(genre = "Jazz") else it },
            events = emptyList(),
            favoriteIds = setOf(1L),
            audioVectors = emptyMap(),
            tagsBySong = mapOf(
                1L to listOf(MusicTag("mood", "mood", "calm", "model", .9f, .5f)),
                2L to listOf(MusicTag("mood", "mood", "calm", "model", .8f, .5f)),
                3L to listOf(MusicTag("mood", "mood", "energetic", "model", .95f, .5f)),
            ),
            currentSessionId = "session",
            nowMs = 1_000_000L,
        )

        assertTrue(discovery.recommendations.any { it.id == 2L })
        assertFalse(discovery.recommendations.any { it.id == 3L })
    }

    @Test
    fun recommendationsUseConfidentMoodAttributeInstrumentAndGenrePredictions() {
        val seed = song(100L, "Seed", "", null)
        val moodMatch = song(101L, "Mood", "", null)
        val attributeMatch = song(102L, "Attribute", "", null)
        val instrumentMatch = song(103L, "Instrument", "", null)
        val genreMatch = song(104L, "Genre", "", null)
        val tagsBySong = mapOf(
            seed.id to listOf(
                MusicTag("mood_happy", "mood", "Happy", "model", .9f, .5f),
                MusicTag("danceability", "attribute", "Danceable", "model", .9f, .5f),
                MusicTag("instrument:piano", "instrument", "Piano", "model", .9f, .5f),
                MusicTag("genre:rock", "genre", "Rock", "model", .9f, .5f),
            ),
            moodMatch.id to listOf(MusicTag("mood_happy", "mood", "Happy", "model", .8f, .5f)),
            attributeMatch.id to listOf(MusicTag("danceability", "attribute", "Danceable", "model", .8f, .5f)),
            instrumentMatch.id to listOf(MusicTag("instrument:piano", "instrument", "Piano", "model", .8f, .5f)),
            genreMatch.id to listOf(MusicTag("genre:rock", "genre", "Rock", "model", .8f, .5f)),
        )
        val catalog = listOf(seed, moodMatch, attributeMatch, instrumentMatch, genreMatch)
            .map { it.withMusicUnderstandingGenres(tagsBySong[it.id].orEmpty()) }

        val discovery = buildPersonalizedDiscovery(
            songs = catalog,
            events = emptyList(),
            favoriteIds = setOf(seed.id),
            audioVectors = emptyMap(),
            tagsBySong = tagsBySong,
            currentSessionId = "session",
            nowMs = 1_000_000L,
        )

        assertTrue(discovery.recommendations.any { it.id == moodMatch.id })
        assertTrue(discovery.recommendations.any { it.id == attributeMatch.id })
        assertTrue(discovery.recommendations.any { it.id == instrumentMatch.id })
        assertTrue(discovery.recommendations.any { it.id == genreMatch.id })
    }

    @Test
    fun inferredGenreAddsToExistingFileGenreAndUsesModelScoreAsSoftRecommendationEvidence() {
        val liked = song(10, "Liked", "Rock", null)
        val lowerScore = song(11, "Lower score", "Jazz", null)
        val higherScore = song(12, "Higher score", "Jazz", null)
        val tags = mapOf(
            10L to listOf(MusicTag("genre", "genre", "Electronic", "model", .9f, .5f)),
            11L to listOf(MusicTag("genre", "genre", "Electronic", "model", .6f, .5f)),
            12L to listOf(MusicTag("genre", "genre", "Electronic", "model", .95f, .5f)),
        )
        val catalog = listOf(
            liked.withMusicUnderstandingGenres(tags.getValue(10L)),
            lowerScore.withMusicUnderstandingGenres(tags.getValue(11L)),
            higherScore.withMusicUnderstandingGenres(tags.getValue(12L)),
        )

        assertEquals(listOf("Rock", "Electronic"), catalog.first().genreNames())
        assertEquals(listOf("Rock"), catalog.first().fileGenreNames())
        val discovery = buildPersonalizedDiscovery(
            songs = catalog,
            events = emptyList(),
            favoriteIds = setOf(10L),
            audioVectors = emptyMap(),
            tagsBySong = tags,
            currentSessionId = "session",
            nowMs = 1_000_000L,
        )

        assertTrue(
            discovery.recommendations.indexOfFirst { it.id == higherScore.id } <
                discovery.recommendations.indexOfFirst { it.id == lowerScore.id },
        )
        assertTrue(discovery.recommendations.any { it.id == lowerScore.id })
    }

    @Test
    fun unfavoritingReducesRecommendationsNearThatTrack() {
        val songs = listOf(
            tracks[0],
            tracks[1],
            song(4, "Quiet Rain Again", "Ambient", null),
            song(5, "Midnight Bass Again", "Electronic", null),
        )
        val nowMs = 2_000_000_000_000L
        val discovery = buildPersonalizedDiscovery(
            songs = songs,
            events = listOf(
                LocalListeningEvent(1L, nowMs - 2_000L, "play", "older"),
                LocalListeningEvent(1L, nowMs - 1_000L, "unfavorite", "older"),
                LocalListeningEvent(2L, nowMs - 1_000L, "completion", "older", completionRatio = .95f),
            ),
            favoriteIds = emptySet(),
            audioVectors = mapOf(
                1L to floatArrayOf(1f, 0f),
                2L to floatArrayOf(0f, 1f),
                4L to floatArrayOf(.01f, .99f),
                5L to floatArrayOf(.99f, .01f),
            ),
            tagsBySong = emptyMap(),
            currentSessionId = "new",
            nowMs = nowMs,
        )

        assertTrue(discovery.recommendations.indexOfFirst { it.id == 4L } < discovery.recommendations.indexOfFirst { it.id == 5L })
    }

    @Test
    fun sessionTasteCanShiftRankingAndCompletionDoesNotCountAsARepeatPlay() {
        val nowMs = 2_000_000_000_000L
        val events = listOf(
            LocalListeningEvent(1L, nowMs - 300L * 86_400_000L, "play", sessionId = "older"),
            LocalListeningEvent(2L, nowMs - 10_000L, "play", sessionId = "session"),
            LocalListeningEvent(1L, nowMs - 300L * 86_400_000L + 10_000L, "completion", sessionId = "older", completionRatio = 1f),
        )
        val vectors = mapOf(
            1L to floatArrayOf(1f, 0f),
            2L to floatArrayOf(0f, 1f),
            3L to floatArrayOf(.01f, .99f),
        )

        val discovery = buildPersonalizedDiscovery(
            songs = tracks.map { if (it.id == 3L) it.copy(genre = "Ambient") else it },
            events = events,
            favoriteIds = emptySet(),
            audioVectors = vectors,
            tagsBySong = emptyMap(),
            currentSessionId = "session",
            nowMs = nowMs,
        )

        assertEquals(3L, discovery.recommendations.first().id)
        assertFalse(discovery.mixes.any { it.id == "on-repeat" })
    }

    @Test
    fun favoriteAndCurrentSessionSignalsCreateRealDiverseRecommendations() {
        val discovery = buildPersonalizedDiscovery(
            songs = tracks,
            events = listOf(
                LocalListeningEvent(1L, 900_000L, "favorite", sessionId = "old"),
                LocalListeningEvent(2L, 990_000L, "play", sessionId = "session"),
            ),
            favoriteIds = setOf(1L),
            audioVectors = mapOf(
                1L to floatArrayOf(1f, 0f),
                2L to floatArrayOf(.9f, .1f),
                3L to floatArrayOf(0f, 1f),
            ),
            tagsBySong = emptyMap(),
            currentSessionId = "session",
            nowMs = 1_000_000L,
        )

        assertTrue(discovery.recommendations.isNotEmpty())
        assertTrue(discovery.mixes.isNotEmpty())
        assertEquals(discovery.recommendations.map(Song::id).distinct(), discovery.recommendations.map(Song::id))
        assertTrue(discovery.recommendations.all { recommendation -> tracks.any { it.id == recommendation.id } })
    }

    @Test
    fun earlySkipOffsetsTheWeakPlayStartSignal() {
        val songs = listOf(
            tracks[0],
            tracks[1],
            song(4, "Quiet Rain Again", "Ambient", null),
        )
        val nowMs = 2_000_000_000_000L
        val discovery = buildPersonalizedDiscovery(
            songs = songs,
            events = listOf(
                LocalListeningEvent(1L, nowMs - 2_000L, "play", "session"),
                LocalListeningEvent(1L, nowMs - 1_000L, "skip", "session", listenSeconds = 8L, completionRatio = .04f, skipPositionSeconds = 8L),
                LocalListeningEvent(2L, nowMs - 10_000L, "play", "older"),
                LocalListeningEvent(2L, nowMs - 1_000L, "completion", "older", listenSeconds = 170L, completionRatio = .95f),
            ),
            favoriteIds = emptySet(),
            audioVectors = mapOf(
                1L to floatArrayOf(1f, 0f),
                2L to floatArrayOf(0f, 1f),
                4L to floatArrayOf(.01f, .99f),
            ),
            tagsBySong = emptyMap(),
            currentSessionId = "session",
            nowMs = nowMs,
        )

        assertEquals(4L, discovery.recommendations.first().id)
    }

    @Test
    fun longEngagedSkipContributesPositiveSessionContext() {
        val songs = listOf(
            tracks[0],
            tracks[1],
            song(4, "Midnight Bass Again", "Electronic", null),
            song(5, "Quiet Rain Again", "Ambient", null),
        )
        val nowMs = 2_000_000_000_000L
        val discovery = buildPersonalizedDiscovery(
            songs = songs,
            events = listOf(
                LocalListeningEvent(1L, nowMs - 2_000L, "play", "session"),
                LocalListeningEvent(1L, nowMs - 1_000L, "skip", "session", listenSeconds = 145L, skipPositionSeconds = 145L),
                LocalListeningEvent(2L, nowMs - 1_000L, "play", "older"),
            ),
            favoriteIds = emptySet(),
            audioVectors = mapOf(
                1L to floatArrayOf(1f, 0f),
                2L to floatArrayOf(0f, 1f),
                4L to floatArrayOf(.99f, .01f),
                5L to floatArrayOf(.01f, .99f),
            ),
            tagsBySong = emptyMap(),
            currentSessionId = "session",
            nowMs = nowMs,
        )

        assertEquals(4L, discovery.recommendations.first().id)
    }

    @Test
    fun aUserSelectedTrackShapesTasteMoreThanOneAutomaticallyAdvancedTrack() {
        val songs = listOf(
            song(1, "Picked", "Electronic", null),
            song(2, "Advanced", "Ambient", null),
            song(3, "Picked neighbor", "Electronic", null),
            song(4, "Advanced neighbor", "Ambient", null),
        )
        val nowMs = 2_000_000_000_000L
        val discovery = buildPersonalizedDiscovery(
            songs = songs,
            events = listOf(
                LocalListeningEvent(1L, nowMs - 1_000L, "play", "session", selectionSource = "search"),
                LocalListeningEvent(2L, nowMs - 1_000L, "play", "session", selectionSource = "autoplay_auto"),
            ),
            favoriteIds = emptySet(),
            audioVectors = mapOf(
                1L to floatArrayOf(1f, 0f),
                2L to floatArrayOf(0f, 1f),
                3L to floatArrayOf(.99f, .01f),
                4L to floatArrayOf(.01f, .99f),
            ),
            tagsBySong = emptyMap(),
            currentSessionId = "session",
            nowMs = nowMs,
        )

        assertEquals(3L, discovery.recommendations.first().id)
    }

    @Test
    fun addingATrackToTheQueueIsARecommendationSignal() {
        val songs = listOf(
            song(1, "Queued", "Electronic", null),
            song(2, "Advanced", "Ambient", null),
            song(3, "Queued neighbor", "Electronic", null),
            song(4, "Advanced neighbor", "Ambient", null),
        )
        val nowMs = 2_000_000_000_000L
        val discovery = buildPersonalizedDiscovery(
            songs = songs,
            events = listOf(
                LocalListeningEvent(1L, nowMs - 1_000L, "queue_add", "session", selectionSource = "play_next"),
                LocalListeningEvent(2L, nowMs - 1_000L, "play", "session", selectionSource = "autoplay_auto"),
            ),
            favoriteIds = emptySet(),
            audioVectors = mapOf(
                1L to floatArrayOf(1f, 0f),
                2L to floatArrayOf(0f, 1f),
                3L to floatArrayOf(.99f, .01f),
                4L to floatArrayOf(.01f, .99f),
            ),
            tagsBySong = emptyMap(),
            currentSessionId = "session",
            nowMs = nowMs,
        )

        assertEquals(1L, discovery.recommendations.first().id)
    }

    @Test
    fun timeOfDayDoesNotInfluenceRankingBeforeThreeDistinctListeningDays() {
        val ranked = daypartRankedIds(dayCount = 2)
        assertTrue(ranked.indexOf(5L) < ranked.indexOf(6L))
    }

    @Test
    fun timeOfDayInfluencesRankingAfterRepeatedEvidenceAcrossDays() {
        val ranked = daypartRankedIds(dayCount = 3)
        assertTrue(ranked.indexOf(6L) < ranked.indexOf(5L))
    }

    private fun daypartRankedIds(dayCount: Int): List<Long> {
        val now = Instant.parse("2026-10-13T09:00:00Z").toEpochMilli()
        val songs = listOf(
            song(1, "Morning One", "Electronic", null),
            song(2, "Morning Two", "Electronic", null),
            song(3, "Evening One", "Ambient", null),
            song(4, "Evening Two", "Ambient", null),
            song(5, "Evening Candidate", "Ambient", null),
            song(6, "Morning Candidate", "Electronic", null),
        )
        val vectors = mapOf(
            1L to floatArrayOf(1f, 0f),
            2L to floatArrayOf(.9f, .1f),
            3L to floatArrayOf(0f, 1f),
            4L to floatArrayOf(.1f, .9f),
            5L to floatArrayOf(0f, 1f),
            6L to floatArrayOf(1f, 0f),
        )
        fun event(songId: Long, day: Int, hour: Int) = LocalListeningEvent(
            songId = songId,
            timestampMs = Instant.parse("2026-10-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00Z").toEpochMilli(),
            eventType = "completion",
            sessionId = "older",
            completionRatio = .95f,
        )
        val events = (8 until 8 + dayCount).flatMap { day ->
            listOf(event(1, day, 8), event(2, day, 8), event(3, day, 20), event(4, day, 20))
        }

        return buildPersonalizedDiscovery(
            songs = songs,
            events = events,
            favoriteIds = emptySet(),
            audioVectors = vectors,
            tagsBySong = emptyMap(),
            currentSessionId = "current",
            nowMs = now,
            timeZone = ZoneId.of("UTC"),
        ).recommendations.map(Song::id)
    }

    @Test
    fun autoplayRefreshUsesLatestOrderUnlessTheListenerReorderedItAndNeverRestoresDismissedItems() {
        val newlyRecommended = song(4, "New", "Electronic", null)
        val ranked = listOf(tracks[2], newlyRecommended, tracks[0], tracks[1])
        val candidates = reconcileAutoplayRecommendations(
            current = listOf(tracks[1], tracks[0]),
            ranked = ranked,
            queuedIds = setOf(3L),
            dismissedIds = setOf(1L),
            preserveManualOrder = true,
        )
        val refreshed = reconcileAutoplayRecommendations(
            current = listOf(tracks[1], tracks[0]),
            ranked = ranked,
            queuedIds = setOf(3L),
            dismissedIds = setOf(1L),
            preserveManualOrder = false,
        )

        assertEquals(listOf(2L, 4L), candidates.map(Song::id))
        assertEquals(listOf(4L, 2L), refreshed.map(Song::id))
    }

    @Test
    fun libraryForYouSortMovesRankedTracksFirstAndLeavesUnknownTracksInTheirCurrentOrder() {
        assertEquals(
            listOf(3L, 1L, 2L),
            recommendationOrderedSongs(tracks, listOf(tracks[2], tracks[0])).map(Song::id),
        )
    }

    private fun song(
        id: Long,
        title: String,
        genre: String,
        languageTag: String?,
        composer: String? = null,
    ) = Song(
        id = id,
        title = title,
        artist = "Artist $id",
        album = "Album $id",
        durationMs = 180_000L,
        uri = "content://music/$id",
        albumArtUri = null,
        genre = genre,
        composer = composer,
        languageTags = listOfNotNull(languageTag),
    )
}
