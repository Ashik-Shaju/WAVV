package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMetadataTest {
    @Test
    fun parsesNumberFieldsIncludingTrackIndexAndTotal() {
        assertEquals(3, "3/12".toMetadataNumber())
        assertEquals(192_000, "192000".toMetadataNumber())
        assertNull("unknown".toMetadataNumber())
        assertNull("".toMetadataNumber())
    }

    @Test
    fun parsesCompilationOnlyFromKnownBooleanValues() {
        assertEquals(true, "1".toMetadataBoolean())
        assertEquals(false, "false".toMetadataBoolean())
        assertNull("maybe".toMetadataBoolean())
    }

    @Test
    fun extractsYearOnlyFromAStandalonePlausibleDateYear() {
        assertEquals(2024, "2024-03-16T12:00:00Z".recordingYear())
        assertEquals(1998, "release year 1998".recordingYear())
        assertNull("1730000000000".recordingYear())
        assertNull("1700".recordingYear())
    }
}
