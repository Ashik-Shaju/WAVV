package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningHistoryRetentionTest {
    @Test
    fun cutoffRetainsAFullYearAndNeverBecomesNegative() {
        assertEquals(0L, listeningHistoryCutoff(LISTENING_HISTORY_RETENTION_MS))
        assertEquals(0L, listeningHistoryCutoff(LISTENING_HISTORY_RETENTION_MS - 1L))
        assertEquals(1L, listeningHistoryCutoff(LISTENING_HISTORY_RETENTION_MS + 1L))
    }
}
