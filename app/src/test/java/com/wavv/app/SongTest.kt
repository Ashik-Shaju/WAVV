package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SongTest {
    @Test
    fun filterSongs_matchesTitleArtistAndAlbum_withoutChangingOrder() {
        val songs = listOf(
            song(1, "Blue Hour", "Mina", "Night Drive"),
            song(2, "Morning Light", "Arun", "Blue Skies"),
        )

        assertEquals(listOf(songs[1]), filterSongs(songs, "ARUN"))
        assertEquals(listOf(songs[0]), filterSongs(songs, "night"))
        assertEquals(songs, filterSongs(songs, ""))
    }

    private fun song(id: Long, title: String, artist: String, album: String) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = 120_000,
        uri = "content://media/$id",
        albumArtUri = null,
    )
}
