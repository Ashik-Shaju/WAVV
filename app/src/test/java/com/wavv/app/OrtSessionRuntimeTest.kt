package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OrtSessionRuntimeTest {
    @Test
    fun cpuOnlyPolicyDoesNotAttemptXnnpack() {
        val result = withXnnpackCpuFallback(
            preferXnnpack = false,
            xnnpack = { error("XNNPACK must not be attempted for this model") },
            cpu = { "cpu" },
        )

        assertEquals("cpu", result)
    }

    @Test
    fun fallsBackToCpuWhenXnnpackSessionCreationFails() {
        val result = withXnnpackCpuFallback(
            preferXnnpack = true,
            xnnpack = { throw IllegalStateException("XNNPACK unavailable") },
            cpu = { "cpu" },
        )

        assertEquals("cpu", result)
    }

    @Test
    fun preservesBothProviderFailuresWhenCpuFallbackAlsoFails() {
        val xnnpackFailure = IllegalStateException("XNNPACK unavailable")
        val cpuFailure = IllegalArgumentException("CPU session failed")
        val thrown = try {
            withXnnpackCpuFallback(
                preferXnnpack = true,
                xnnpack = { throw xnnpackFailure },
                cpu = { throw cpuFailure },
            )
            error("Expected fallback failure")
        } catch (failure: IllegalArgumentException) {
            failure
        }

        assertSame(cpuFailure, thrown)
        assertSame(xnnpackFailure, thrown.suppressed.single())
    }
}
