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
    fun overlappingWindowCollectorMatchesEndAlignedTailWithoutGrowingWithTrackLength() = runBlocking {
        val input = FloatArray(21) { it.toFloat() }
        val windows = mutableListOf<Pair<Int, FloatArray>>()
        val collector = PcmWindowCollector(8, 4, alignLastWindow = true, emitEmptyWindow = true) { samples, valid ->
            windows.add(valid to samples.copyOf())
        }

        var offset = 0
        while (offset < input.size) {
            val count = minOf(3, input.size - offset)
            collector.append(input, offset, count)
            offset += count
        }
        collector.finish()

        assertEquals(12, collector.bufferCapacitySamples)
        assertEquals(listOf(8, 8, 8, 8, 8), windows.map { it.first })
        assertEquals(
            listOf(
                listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f),
                listOf(4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f),
                listOf(8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f),
                listOf(12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f),
                listOf(13f, 14f, 15f, 16f, 17f, 18f, 19f, 20f),
            ),
            windows.map { it.second.toList() },
        )
    }

    @Test
    fun nonOverlappingWindowCollectorZeroPadsOnlyTheFinalPartialWindow() = runBlocking {
        val input = FloatArray(11) { it.toFloat() }
        val windows = mutableListOf<Pair<Int, List<Float>>>()
        val collector = PcmWindowCollector(8, 8, alignLastWindow = false, emitEmptyWindow = false) { samples, valid ->
            windows.add(valid to samples.toList())
        }

        collector.append(input, 0, input.size)
        collector.finish()

        assertEquals(16, collector.bufferCapacitySamples)
        assertEquals(8, windows[0].first)
        assertEquals((0..7).map(Int::toFloat), windows[0].second)
        assertEquals(3, windows[1].first)
        assertEquals(listOf(8f, 9f, 10f, 0f, 0f, 0f, 0f, 0f), windows[1].second)
    }

    @Test
    fun linearStreamingResamplerMatchesPinnedInterpolationAcrossChunks() = runBlocking {
        val input = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f)
        val output = mutableListOf<Float>()
        val resampler = StreamingAudioResampler(4, 6, bandLimited = false) { samples, size ->
            repeat(size) { output.add(samples[it]) }
        }

        resampler.write(input, 0, 2)
        resampler.write(input, 2, 1)
        resampler.write(input, 3, 3)
        resampler.finish()

        assertEquals(9, output.size)
        assertEquals(listOf(0f, 2f / 3f, 4f / 3f, 2f, 8f / 3f, 10f / 3f, 4f, 14f / 3f, 5f), output)
    }

    @Test
    fun bandLimitedStreamingResamplerMatchesBatchOutputAcrossChunks() = runBlocking {
        val sourceRate = 48_000
        val targetRate = 32_000
        val input = FloatArray(8_193) { index ->
            sin(2.0 * PI * 997 * index / sourceRate).toFloat()
        }
        val expected = bandLimitedAudioResample(input, sourceRate, targetRate)
        val output = mutableListOf<Float>()
        val resampler = StreamingAudioResampler(sourceRate, targetRate, bandLimited = true) { samples, size ->
            repeat(size) { output.add(samples[it]) }
        }

        var offset = 0
        while (offset < input.size) {
            val count = minOf(137, input.size - offset)
            resampler.write(input, offset, count)
            offset += count
        }
        resampler.finish()

        assertEquals(expected.size, output.size)
        expected.indices.forEach { index -> assertEquals(expected[index], output[index], 0.000001f) }
        assertTrue(resampler.sourceBufferCapacitySamples < 1_000)
    }

    @Test
    fun bandLimitedStreamingResamplerHandlesCommonNonIntegerRateRatio() = runBlocking {
        val sourceRate = 44_100
        val targetRate = 32_000
        val input = FloatArray(2_003) { index ->
            sin(2.0 * PI * 997 * index / sourceRate).toFloat()
        }
        val expected = bandLimitedAudioResample(input, sourceRate, targetRate)
        val output = mutableListOf<Float>()
        val resampler = StreamingAudioResampler(sourceRate, targetRate, bandLimited = true) { samples, size ->
            repeat(size) { output.add(samples[it]) }
        }

        var offset = 0
        while (offset < input.size) {
            val count = minOf(113, input.size - offset)
            resampler.write(input, offset, count)
            offset += count
        }
        resampler.finish()

        assertEquals(expected.size, output.size)
        expected.indices.forEach { index -> assertEquals(expected[index], output[index], 0.000001f) }
    }

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
