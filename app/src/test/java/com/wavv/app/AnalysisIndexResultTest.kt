package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisIndexResultTest {
    @Test
    fun batchCombinesPartialTrackFailureCounts() {
        val result = AnalysisIndexResult(failedOperations = 2) + AnalysisIndexResult(failedOperations = 1)

        assertEquals(3, result.failedOperations)
        assertTrue(result.hasFailures)
        assertTrue(result.userMessage().contains("analysis tasks"))
    }

    @Test
    fun missingBundledModelIsAnExplicitFailureRatherThanSuccessfulNoOp() {
        val result = AnalysisIndexResult(unavailableMessage = "The bundled model is missing.")

        assertTrue(result.hasFailures)
        assertEquals("The bundled model is missing.", result.userMessage())
        assertFalse(AnalysisIndexResult().hasFailures)
    }
}
