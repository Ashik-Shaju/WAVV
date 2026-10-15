package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GenreCategoriesTest {
    @Test
    fun categoriesUseRealArtworkAndKeepConfidentInferredGenresVisibleWithoutIt() {
        val artSong = song(1, "First", listOf("Rock"), hasArtwork = true)
        val duplicate = song(2, "Second", listOf("rock", "Jazz"), hasArtwork = true)
        val noArtwork = song(3, "Voice note", listOf("Spoken word"), hasArtwork = false)
        val inferredWithoutArtwork = song(4, "Uncovered model genre", emptyList(), hasArtwork = false)
            .copy(inferredGenres = listOf("Ambient"))

        assertEquals(
            listOf(
                GenreCategory("Ambient", null),
                GenreCategory("Jazz", duplicate),
                GenreCategory("Rock", artSong),
            ),
            genreCategories(listOf(artSong, duplicate, noArtwork, inferredWithoutArtwork)),
        )
    }

    @Test
    fun genreSelectionMatchesExactTagsCaseInsensitively() {
        val rock = song(1, "Rock track", listOf("Rock"), hasArtwork = true)
        val rockPop = song(2, "Rock and Pop", listOf("Rock/Pop"), hasArtwork = true)
        val pop = song(3, "Pop track", listOf("Pop"), hasArtwork = true)

        assertEquals(listOf(rock), songsForGenre(listOf(rock, rockPop, pop), " rock "))
    }

    @Test
    fun confidentModelGenresJoinFileGenresWithoutPromotingOtherOrLowConfidenceTags() {
        val rock = song(1, "Rock track", listOf("Rock"), hasArtwork = true)
        val analyzed = rock.withMusicUnderstandingGenres(
            listOf(
                MusicTag("genre", "genre", "Electronic", "weak_uploader_tag", .8f, .5f),
                MusicTag("genre", "genre", "rock", "weak_uploader_tag", .9f, .5f),
                MusicTag("genre", "genre", "Jazz", "weak_uploader_tag", .4f, .5f),
                MusicTag("instrument", "instrument", "Piano", "weak_uploader_tag", .99f, .5f),
            ),
        )

        assertEquals(listOf("Rock", "Electronic"), analyzed.genreNames())
        assertEquals(listOf("Rock"), analyzed.fileGenreNames())
        assertEquals(.8, analyzed.genreConfidence("Electronic", listOf(
            MusicTag("genre", "genre", "Electronic", "model", .8f, .5f),
        )), .0001)
        assertEquals(1.0, analyzed.genreConfidence("Rock", emptyList()), .0)
        assertEquals(0.0, analyzed.genreConfidence("Piano", listOf(
            MusicTag("instrument", "instrument", "Piano", "model", .99f, .5f),
        )), .0)
        assertEquals(
            listOf(GenreCategory("Electronic", analyzed), GenreCategory("Rock", analyzed)),
            genreCategories(listOf(analyzed)),
        )
    }

    @Test
    fun belowThresholdAndInvalidGenreScoresAreNotPromoted() {
        val song = song(1, "Track", listOf("Rock"), hasArtwork = true).withMusicUnderstandingGenres(
            listOf(
                MusicTag("genre", "genre", "Jazz", "model", .49f, .5f),
                MusicTag("genre", "genre", "Ambient", "model", Float.NaN, .5f),
                MusicTag("genre", "genre", "Pop", "model", .9f, Float.NaN),
            ),
        )

        assertEquals(listOf("Rock"), song.genreNames())
        assertFalse(song.genreNames().any { it == "Jazz" || it == "Ambient" || it == "Pop" })
    }

    @Test
    fun categoriesReflectCurrentTasteWithoutRemovingOtherGenres() {
        val jazz = song(1, "Jazz", listOf("Jazz"), hasArtwork = true)
        val pop = song(2, "Pop", listOf("Pop"), hasArtwork = true)
        val rock = song(3, "Rock", listOf("Rock"), hasArtwork = true)

        assertEquals(
            listOf("Rock", "Jazz", "Pop"),
            recommendationOrderedGenreCategories(
                listOf(jazz, pop, rock),
                listOf(rock, jazz),
            ).map(GenreCategory::name),
        )
    }

    @Test
    fun coldStartCategoriesPrioritizeLocalCoverageAndUseNewestArtwork() {
        val jazz = song(1, "Jazz", listOf("Jazz"), hasArtwork = true)
        val olderRock = song(2, "Older rock", listOf("Rock"), hasArtwork = true).copy(modifiedAt = 10)
        val newerRock = song(3, "Newer rock", listOf("Rock"), hasArtwork = true).copy(modifiedAt = 20)

        val categories = recommendationOrderedGenreCategories(listOf(jazz, olderRock, newerRock), emptyList())

        assertEquals(listOf("Rock", "Jazz"), categories.map(GenreCategory::name))
        assertEquals(newerRock.id, categories.first().artwork?.id)
    }

    @Test
    fun categoryArtworkFollowsTheHighestRankedAvailableTrack() {
        val first = song(1, "First alphabetically", listOf("Rock"), hasArtwork = true)
        val recommended = song(2, "Recommended", listOf("Rock"), hasArtwork = true)

        assertEquals(
            recommended.id,
            recommendationOrderedGenreCategories(listOf(first, recommended), listOf(recommended))
                .single().artwork?.id,
        )
    }

    private fun song(id: Long, title: String, genres: List<String>, hasArtwork: Boolean) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 60_000,
        uri = "content://media/$id",
        albumArtUri = if (hasArtwork) "content://art/$id" else null,
        genres = genres,
    )
}
