package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryNavigationStateTest {
    @Test
    fun backPopsNestedDetailsToTheirOriginatingCollection() {
        val songs = LibraryDestination(page = LibraryPage.Songs, title = "Songs")
        val album = LibraryDestination(page = LibraryPage.Album, title = "25", subtitle = "Adele")
        val artist = LibraryDestination(page = LibraryPage.Artist, title = "Adele")
        val state = LibraryNavigationState()
            .navigateTo(songs)
            .navigateTo(album)
            .navigateTo(artist)

        assertEquals(album, state.back().destination)
        assertEquals(songs, state.back().back().destination)
        assertEquals(LibraryDestination(), state.back().back().back().destination)
    }

    @Test
    fun navigatingToAnotherTopLevelCollectionResetsDetailHistory() {
        val album = LibraryDestination(page = LibraryPage.Album, title = "25", subtitle = "Adele")
        val artist = LibraryDestination(page = LibraryPage.Artist, title = "Adele")
        val songs = LibraryDestination(page = LibraryPage.Songs, title = "Songs")
        val state = LibraryNavigationState()
            .navigateTo(album)
            .navigateTo(artist)
            .navigateTo(songs)

        assertEquals(LibraryDestination(), state.back().destination)
    }
}
