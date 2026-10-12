package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistPinIdsTest {
    @Test
    fun persistedIdsIgnoreMalformedValuesAndRoundTrip() {
        val ids = setOf(12L, 3L, -5L)

        assertEquals(setOf(12L, 3L), decodePlaylistPinIds(encodePlaylistPinIds(ids)))
        assertEquals(setOf(3L, 12L), decodePlaylistPinIds(setOf("12", "broken", "3", "0")))
    }
}
