package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
