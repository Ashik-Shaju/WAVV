package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SongTest {
    @Test
    fun searchSongs_matchesTitleArtistAndAlbum_withoutChangingOrder() {
        val songs = listOf(
            song(1, "Blue Hour", "Mina", "Night Drive"),
            song(2, "Morning Light", "Arun", "Blue Skies"),
        )

        assertEquals(listOf(songs[1]), searchSongs(songs, "ARUN"))
        assertEquals(listOf(songs[0]), searchSongs(songs, "night"))
        assertEquals(songs, searchSongs(songs, ""))
    }

    @Test
    fun searchSongs_putsSemanticMatchesFirst_deduplicatesAndHonorsLimit() {
        val songs = listOf(
            song(1, "Blue Hour", "Mina", "Night Drive"),
            song(2, "Morning Light", "Arun", "Blue Skies"),
            song(3, "Blue Again", "Mina", "Daylight"),
        )

        assertEquals(
            listOf(songs[1], songs[0]),
            searchSongs(songs, "blue", semanticSongs = listOf(songs[1], songs[1]), limit = 2),
        )
    }

    @Test
    fun moveQueueItem_reordersOnlyTheRequestedEntry() {
        assertEquals(listOf("a", "c", "d", "b"), moveQueueItem(listOf("a", "b", "c", "d"), 1, 3))
        assertEquals(listOf("a", "b"), moveQueueItem(listOf("a", "b"), -1, 1))
    }

    @Test
    fun shuffleUpcoming_keepsPlayedPrefixAndEveryQueueItem() {
        val songs = listOf("played", "current", "one", "two", "three")

        val shuffled = shuffleUpcoming(songs, currentIndex = 1, seed = 42L)

        assertEquals(songs.take(2), shuffled.take(2))
        assertEquals(songs.toSet(), shuffled.toSet())
        assertEquals(songs.size, shuffled.size)
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
