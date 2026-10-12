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

    @Test
    fun nextUsesQueueBeforeAutoplayAndAutoplayOnlyWhenQueueIsExhausted() {
        assertEquals(NextPlaybackAction.Queue, nextPlaybackAction(true, true, true))
        assertEquals(NextPlaybackAction.Autoplay, nextPlaybackAction(false, true, true))
        assertEquals(NextPlaybackAction.None, nextPlaybackAction(false, false, true))
        assertEquals(NextPlaybackAction.None, nextPlaybackAction(false, true, false))
    }

    @Test
    fun rejectedQueueActionsHaveSpecificUserFeedback() {
        assertEquals("This track is already playing.", queueActionMessage(QueueActionResult.AlreadyPlaying))
        assertEquals("This track is already next.", queueActionMessage(QueueActionResult.AlreadyNext))
        assertEquals("This track is already in the queue.", queueActionMessage(QueueActionResult.AlreadyQueued))
        assertEquals("Playback isn't ready yet. Try again.", queueActionMessage(QueueActionResult.Unavailable))
        assertEquals(null, queueActionMessage(QueueActionResult.Added))
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
