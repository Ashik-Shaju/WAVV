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

    @Test
    fun stackOrderStartsWithSelectedSectionAndClampsRestoredIndex() {
        assertEquals(listOf(2, 3, 0, 1), homeSectionOrder(sectionCount = 4, stackIndex = 2))
        assertEquals(listOf(2, 0, 1), homeSectionOrder(sectionCount = 3, stackIndex = Int.MAX_VALUE))
        assertEquals(emptyList<Int>(), homeSectionOrder(sectionCount = 0, stackIndex = 4))
    }

    @Test
    fun hiddenStackCardsOnlyComposeContentWhenVisibleOrTransitioning() {
        assertEquals(false, homeStackContentVisible(expanded = false, compactVisible = false, expansionProgress = 0f))
        assertEquals(true, homeStackContentVisible(expanded = false, compactVisible = true, expansionProgress = 0f))
        assertEquals(true, homeStackContentVisible(expanded = true, compactVisible = false, expansionProgress = 0f))
        assertEquals(true, homeStackContentVisible(expanded = false, compactVisible = false, expansionProgress = .5f))
        assertEquals(false, homeStackContentVisible(expanded = false, compactVisible = false, expansionProgress = 0f))
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
