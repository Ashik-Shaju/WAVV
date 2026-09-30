package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DclapTest {
    @Test
    fun windowStarts_doNotDuplicateCompleteTailSegments() {
        assertEquals(listOf(0, 240_000), dclapWindowStarts(15 * 48_000))
        assertEquals(listOf(0, 240_000, 480_000), dclapWindowStarts(20 * 48_000))
        assertEquals(listOf(0, 240_000, 480_000, 720_000), dclapWindowStarts(25 * 48_000))
    }

    @Test
    fun windowStarts_addsEndAlignedWindowOnlyForUncoveredTail() {
        assertEquals(listOf(0), dclapWindowStarts(10 * 48_000))
        assertEquals(listOf(0, 1), dclapWindowStarts(10 * 48_000 + 1))
    }
}
