package com.wavv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCacheTest {
    @Test
    fun unknownDocumentMetadataNeverValidatesAnOldCacheEntry() {
        assertFalse(sourceFingerprintMatches(0L, 0L, 0L, 0L))
        assertFalse(sourceFingerprintMatches(1_024L, 0L, 1_024L, 0L))
        assertFalse(sourceFingerprintMatches(0L, 1_234L, 0L, 1_234L))
    }

    @Test
    fun knownProviderMetadataValidatesOnlyAnExactFingerprint() {
        assertTrue(sourceFingerprintMatches(1_024L, 1_234L, 1_024L, 1_234L))
        assertFalse(sourceFingerprintMatches(1_024L, 1_234L, 2_048L, 1_234L))
        assertFalse(sourceFingerprintMatches(1_024L, 1_234L, 1_024L, 1_235L))
    }
}
