package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SongTest {
    @Test
    fun searchSongs_matchesTitleArtistAndAlbum_withoutChangingOrder() {
        val songs = listOf(
            song(1, "Track A", "Artist A", "Album A"),
            song(2, "Track B", "Artist B", "Album B"),
        )

        assertEquals(listOf(songs[1]), searchSongs(songs, "ARTIST B"))
        assertEquals(listOf(songs[0]), searchSongs(songs, "album a"))
        assertEquals(songs, searchSongs(songs, ""))
    }

    @Test
    fun songsByGenre_groupsOnlyTaggedTracks_caseInsensitively() {
        val songs = listOf(
            song(1, "Track A", "Artist A", "Album A").copy(genre = "Electronic"),
            song(2, "Track B", "Artist B", "Album B").copy(genre = "Rock"),
            song(3, "Track C", "Artist A", "Album C").copy(genre = " electronic "),
            song(4, "Track D", "Artist B", "Album D"),
        )

        assertEquals(
            listOf("Electronic" to listOf(songs[0], songs[2]), "Rock" to listOf(songs[1])),
            songsByGenre(songs),
        )
        assertEquals(listOf(songs[0], songs[2]), songsForGenre(songs, "Electronic"))
    }

    @Test
    fun searchSongs_putsSemanticMatchesFirst_deduplicatesAndHonorsLimit() {
        val songs = listOf(
            song(1, "Track A", "Artist A", "Album A"),
            song(2, "Track B", "Artist B", "Album B"),
            song(3, "Track C", "Artist A", "Album C"),
        )

        assertEquals(
            listOf(songs[1], songs[0]),
            searchSongs(songs, "track", semanticSongs = listOf(songs[1], songs[1]), limit = 2),
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
