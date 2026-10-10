package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenContentTest {
    @Test
    fun primaryShelfUsesListeningHistoryWhenAvailable() {
        val recent = listOf(song(1))

        assertEquals("Recently Played" to recent, primaryHomeShelf(recent, listOf(song(2))))
    }

    @Test
    fun primaryShelfUsesRecentlyAddedTracksWithoutInventingPlayHistory() {
        val added = listOf(song(2), song(3))

        assertEquals("Recently Added" to added, primaryHomeShelf(emptyList(), added))
    }

    private fun song(id: Long) = Song(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        durationMs = 120_000,
        uri = "content://media/$id",
        albumArtUri = null,
    )
}
