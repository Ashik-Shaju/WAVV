package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScreenContentTest {
    @Test
    fun playlistArtworkFollowsPlaylistOrderAndPrefersActualArtwork() {
        val firstWithoutArt = song(1, null)
        val nextWithArt = song(2, "content://art/2")
        val laterWithArt = song(3, "content://art/3")
        val songsById = listOf(firstWithoutArt, nextWithArt, laterWithArt).associateBy(Song::id)

        assertEquals(nextWithArt, playlistArtworkSong(listOf(1L, 2L, 3L), songsById))
    }

    @Test
    fun playlistWithoutArtworkUsesFirstAvailablePlaylistTrack() {
        val first = song(1, null)
        val second = song(2, null)

        assertEquals(first, playlistArtworkSong(listOf(1L, 2L), listOf(first, second).associateBy(Song::id)))
        assertEquals(null, playlistArtworkSong(listOf(99L), emptyMap()))
    }

    private fun song(id: Long, albumArtUri: String?) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        durationMs = 60_000,
        uri = "content://media/$id",
        albumArtUri = albumArtUri,
    )
}
