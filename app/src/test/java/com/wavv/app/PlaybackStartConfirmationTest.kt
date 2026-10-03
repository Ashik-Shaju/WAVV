package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStartConfirmationTest {
    @Test
    fun requestedPlayIsNotConfirmedUntilMedia3ReportsTheSameSongPlaying() {
        val confirmation = PlaybackStartConfirmation()
        confirmation.request(42L)

        assertNull(confirmation.confirm(isPlaying = false, currentSongId = 42L))
        assertNull(confirmation.confirm(isPlaying = true, currentSongId = 7L))
        assertEquals(42L, confirmation.confirm(isPlaying = true, currentSongId = 42L))
        assertNull(confirmation.confirm(isPlaying = true, currentSongId = 42L))
    }

    @Test
    fun failedPlaybackClearsPendingStartWithoutRecordingAPlay() {
        val confirmation = PlaybackStartConfirmation()
        confirmation.request(42L)

        confirmation.cancel()

        assertNull(confirmation.confirm(isPlaying = true, currentSongId = 42L))
    }
}
