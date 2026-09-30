package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreCategoriesTest {
    @Test
    fun categoriesUseActualGenresAndOnlySongsWithArtwork() {
        val artSong = song(1, "First", listOf("Rock"), hasArtwork = true)
        val duplicate = song(2, "Second", listOf("rock", "Jazz"), hasArtwork = true)
        val noArtwork = song(3, "Voice note", listOf("Spoken word"), hasArtwork = false)

        assertEquals(
            listOf(GenreCategory("Jazz", duplicate), GenreCategory("Rock", artSong)),
            genreCategories(listOf(artSong, duplicate, noArtwork)),
        )
    }

    @Test
    fun genreSelectionMatchesExactTagsCaseInsensitively() {
        val rock = song(1, "Rock track", listOf("Rock"), hasArtwork = true)
        val rockPop = song(2, "Rock and Pop", listOf("Rock/Pop"), hasArtwork = true)
        val pop = song(3, "Pop track", listOf("Pop"), hasArtwork = true)

        assertEquals(listOf(rock), songsForGenre(listOf(rock, rockPop, pop), " rock "))
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
