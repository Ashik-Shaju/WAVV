package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueTest {
    @Test
    fun upcomingTracksStartAfterCurrentTrackRatherThanAtQueueBeginning() {
        val queue = listOf(song(1L), song(2L), song(3L), song(4L))

        assertEquals(listOf(song(3L), song(4L)), nextQueuedSongs(queue, currentSongId = 2L))
    }

    @Test
    fun upcomingTracksAreEmptyAtQueueEndOrWhenCurrentTrackIsMissing() {
        val queue = listOf(song(1L), song(2L))

        assertEquals(emptyList<Song>(), nextQueuedSongs(queue, currentSongId = 2L))
        assertEquals(emptyList<Song>(), nextQueuedSongs(queue, currentSongId = 3L))
    }

    @Test
    fun upcomingTrackLimitIsAppliedAfterCurrentTrack() {
        val queue = listOf(song(1L), song(2L), song(3L), song(4L), song(5L))

        assertEquals(listOf(song(4L), song(5L)), nextQueuedSongs(queue, currentSongId = 3L, limit = 2))
    }

    private fun song(id: Long) = Song(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        durationMs = 1_000L,
        uri = "content://music/$id",
        albumArtUri = null,
    )
}
