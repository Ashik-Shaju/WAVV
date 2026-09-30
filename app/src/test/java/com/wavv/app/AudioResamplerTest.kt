package com.wavv.app

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioResamplerTest {
    @Test
    fun downsamplingRejectsFrequenciesAboveTheNewNyquistLimit() = runBlocking {
        val sourceRate = 48_000
        val targetRate = 32_000
        val input = FloatArray(sourceRate * 2) { index ->
            sin(2.0 * PI * 18_000 * index / sourceRate).toFloat()
        }

        val output = bandLimitedAudioResample(input, sourceRate, targetRate)
        val rms = sqrt(output.drop(128).dropLast(128).sumOf { it.toDouble() * it } / (output.size - 256))

        assertEquals(64_000, output.size)
        assertTrue("Aliased energy remains too high after downsampling: $rms", rms < 0.05)
    }
}
