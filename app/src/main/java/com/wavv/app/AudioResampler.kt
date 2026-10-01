package com.wavv.app

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun bandLimitedAudioResample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
    require(sourceRate > 0 && targetRate > 0)
    if (input.isEmpty() || sourceRate == targetRate) return input

    val outputCount = (input.size.toDouble() * targetRate / sourceRate).roundToInt()
    if (outputCount <= 0) return FloatArray(0)
    val filter = BandLimitedFilter(sourceRate, targetRate)

    val output = FloatArray(outputCount)
    for (outputIndex in output.indices) {
        if (outputIndex % RESAMPLE_CANCELLATION_INTERVAL == 0) currentCoroutineContext().ensureActive()
        val numerator = outputIndex.toLong() * sourceRate
        val sourceIndex = numerator / targetRate
        val phase = ((numerator % targetRate) / filter.gcd).toInt()
        val taps = filter.coefficients[phase]
        var sum = 0.0
        var weight = 0.0
        for (index in filter.offsets.indices) {
            val sampleIndex = sourceIndex + filter.offsets[index]
            if (sampleIndex >= 0L && sampleIndex < input.size) {
                val tap = taps[index]
                sum += input[sampleIndex.toInt()] * tap
                weight += tap
            }
        }
        require(weight.isFinite() && kotlin.math.abs(weight) > 1e-12)
        output[outputIndex] = (sum / weight).toFloat()
    }
    return output
}

/** Streams resampled mono PCM in bounded chunks. Consumers must finish using each chunk before returning. */
internal class StreamingAudioResampler(
    private val sourceRate: Int,
    private val targetRate: Int,
    bandLimited: Boolean,
    private val onSamples: suspend (FloatArray, Int) -> Unit,
) {
    private val filter = if (bandLimited && sourceRate != targetRate) BandLimitedFilter(sourceRate, targetRate) else null
    private val sourceBufferCapacity = filter?.let {
        (2L * it.radius + ceil(sourceRate.toDouble() / targetRate).toLong() + 4L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } ?: 0
    private val sourceBuffer = filter?.let { FloatArray(sourceBufferCapacity) }
    private val outputBuffer = FloatArray(RESAMPLE_OUTPUT_CHUNK_SAMPLES)
    private var outputBufferSize = 0
    private var sourceBase = 0L
    private var sourceCount = 0L
    private var nextOutputIndex = 0L
    private var previousSample = 0f
    private var lastSample = 0f

    init {
        require(sourceRate > 0 && targetRate > 0)
    }

    internal val sourceBufferCapacitySamples: Int get() = sourceBufferCapacity

    suspend fun write(samples: FloatArray, offset: Int = 0, count: Int = samples.size) {
        require(offset >= 0 && count >= 0 && offset <= samples.size - count)
        if (count == 0) return
        if (sourceRate == targetRate) {
            var copied = 0
            while (copied < count) {
                val chunkSize = minOf(outputBuffer.size, count - copied)
                samples.copyInto(outputBuffer, 0, offset + copied, offset + copied + chunkSize)
                onSamples(outputBuffer, chunkSize)
                copied += chunkSize
            }
            sourceCount += count
            lastSample = samples[offset + count - 1]
            return
        }

        if (filter == null) {
            for (index in offset until offset + count) {
                if (sourceCount % RESAMPLE_CANCELLATION_INTERVAL == 0L) currentCoroutineContext().ensureActive()
                val currentIndex = sourceCount
                val sample = samples[index]
                if (currentIndex > 0) {
                    while (linearPosition(nextOutputIndex) < currentIndex) {
                        val position = linearPosition(nextOutputIndex)
                        val leftIndex = position.toLong()
                        check(leftIndex == currentIndex - 1) { "Linear resampler lost its input history" }
                        val fraction = (position - leftIndex).toFloat()
                        outputBuffer[outputBufferSize++] = previousSample + (sample - previousSample) * fraction
                        nextOutputIndex++
                        if (outputBufferSize == outputBuffer.size) flushOutput()
                    }
                }
                previousSample = sample
                lastSample = sample
                sourceCount++
            }
            return
        }

        val ring = checkNotNull(sourceBuffer)
        for (index in offset until offset + count) {
            if (sourceCount % RESAMPLE_CANCELLATION_INTERVAL == 0L) currentCoroutineContext().ensureActive()
            check(sourceCount - sourceBase < ring.size) { "Band-limited resampler input window exceeded its bound" }
            ring[(sourceCount % ring.size).toInt()] = samples[index]
            sourceCount++
            lastSample = samples[index]
            while (nextSourceIndex(nextOutputIndex) + filter.radius < sourceCount) {
                outputBuffer[outputBufferSize++] = filteredSample(nextOutputIndex, filter, ring)
                nextOutputIndex++
                trimSourceBuffer(filter)
                if (outputBufferSize == outputBuffer.size) flushOutput()
            }
        }
    }

    suspend fun finish() {
        if (sourceCount == 0L) {
            flushOutput()
            return
        }
        if (sourceRate != targetRate) {
            val outputCount = if (filter == null) {
                maxOf(1L, (sourceCount.toDouble() * targetRate / sourceRate).toLong())
            } else {
                (sourceCount.toDouble() * targetRate / sourceRate).roundToInt().toLong()
            }
            while (nextOutputIndex < outputCount) {
                if (nextOutputIndex % RESAMPLE_CANCELLATION_INTERVAL == 0L) currentCoroutineContext().ensureActive()
                outputBuffer[outputBufferSize++] = if (filter == null) {
                    lastSample
                } else {
                    filteredSample(nextOutputIndex, filter, checkNotNull(sourceBuffer))
                }
                nextOutputIndex++
                if (filter != null) trimSourceBuffer(filter)
                if (outputBufferSize == outputBuffer.size) flushOutput()
            }
        }
        flushOutput()
    }

    private fun linearPosition(outputIndex: Long): Double = outputIndex.toDouble() * sourceRate / targetRate

    private fun nextSourceIndex(outputIndex: Long): Long = outputIndex * sourceRate / targetRate

    private fun filteredSample(outputIndex: Long, filter: BandLimitedFilter, ring: FloatArray): Float {
        val numerator = outputIndex * sourceRate
        val sourceIndex = numerator / targetRate
        val phase = ((numerator % targetRate) / filter.gcd).toInt()
        val taps = filter.coefficients[phase]
        var sum = 0.0
        var weight = 0.0
        for (index in filter.offsets.indices) {
            val sampleIndex = sourceIndex + filter.offsets[index]
            if (sampleIndex >= 0L && sampleIndex < sourceCount) {
                check(sampleIndex >= sourceBase) { "Band-limited resampler discarded required input history" }
                val tap = taps[index]
                sum += ring[(sampleIndex % ring.size).toInt()] * tap
                weight += tap
            }
        }
        check(weight.isFinite() && kotlin.math.abs(weight) > 1e-12)
        return (sum / weight).toFloat()
    }

    private fun trimSourceBuffer(filter: BandLimitedFilter) {
        sourceBase = maxOf(sourceBase, nextSourceIndex(nextOutputIndex) - filter.radius + 1).coerceAtMost(sourceCount)
    }

    private suspend fun flushOutput() {
        if (outputBufferSize == 0) return
        onSamples(outputBuffer, outputBufferSize)
        outputBufferSize = 0
    }
}

/** Retains at most one analysis window plus its overlap, independent of track duration. */
internal class PcmWindowCollector(
    private val windowSizeSamples: Int,
    private val hopSizeSamples: Int,
    private val alignLastWindow: Boolean,
    private val emitEmptyWindow: Boolean,
    private val onWindow: suspend (FloatArray, Int) -> Unit,
) {
    internal val bufferCapacitySamples = Math.addExact(windowSizeSamples, hopSizeSamples)
    private val ring = FloatArray(bufferCapacitySamples)
    private val window = FloatArray(windowSizeSamples)
    private var retainedFrom = 0L
    private var totalSamples = 0L
    private var nextWindowStart = 0L
    private var emittedWindows = 0
    private var finished = false

    init {
        require(windowSizeSamples > 0 && hopSizeSamples in 1..windowSizeSamples)
    }

    suspend fun append(samples: FloatArray, offset: Int = 0, count: Int = samples.size) {
        check(!finished) { "Cannot append samples after finishing the collector" }
        require(offset >= 0 && count >= 0 && offset <= samples.size - count)
        for (index in offset until offset + count) {
            check(totalSamples - retainedFrom < ring.size) { "PCM window collector exceeded its bounded buffer" }
            ring[(totalSamples % ring.size).toInt()] = samples[index]
            totalSamples++
            while (nextWindowStart + windowSizeSamples <= totalSamples) {
                emitWindow(nextWindowStart, windowSizeSamples)
                emittedWindows++
                nextWindowStart += hopSizeSamples
                retainedFrom = if (alignLastWindow) {
                    maxOf(0L, nextWindowStart - hopSizeSamples)
                } else {
                    nextWindowStart
                }
            }
        }
    }

    suspend fun finish() {
        check(!finished) { "PCM window collector is already finished" }
        finished = true
        if (totalSamples == 0L) {
            if (emitEmptyWindow) emitWindow(0L, 0)
            return
        }
        if (alignLastWindow) {
            if (emittedWindows == 0) {
                emitWindow(0L, totalSamples.toInt())
            } else {
                val lastCovered = nextWindowStart - hopSizeSamples + windowSizeSamples
                if (lastCovered < totalSamples) emitWindow(totalSamples - windowSizeSamples, windowSizeSamples)
            }
        } else if (nextWindowStart < totalSamples) {
            emitWindow(nextWindowStart, (totalSamples - nextWindowStart).toInt())
        }
    }

    private suspend fun emitWindow(start: Long, validSamples: Int) {
        window.fill(0f)
        for (index in 0 until validSamples) {
            val sampleIndex = start + index
            check(sampleIndex in retainedFrom until totalSamples) { "PCM window fell outside the retained input" }
            window[index] = ring[(sampleIndex % ring.size).toInt()]
        }
        onWindow(window, validSamples)
    }
}

private const val RESAMPLE_CANCELLATION_INTERVAL = 1_024
private const val RESAMPLE_OUTPUT_CHUNK_SAMPLES = 8_192

private class BandLimitedFilter(sourceRate: Int, targetRate: Int) {
    val gcd = greatestCommonDivisor(sourceRate, targetRate)
    val radius: Int
    val offsets: IntArray
    val coefficients: Array<DoubleArray>

    init {
        val phaseCount = targetRate / gcd
        val cutoff = minOf(1.0, targetRate.toDouble() / sourceRate) * 0.99
        val filterWidth = 9.0
        radius = ceil(filterWidth / cutoff).toInt()
        offsets = IntArray(radius * 2) { index -> index - radius + 1 }
        coefficients = Array(phaseCount) { phase ->
            val fraction = phase.toDouble() / phaseCount
            val values = DoubleArray(offsets.size) { index ->
                val scaledDistance = (fraction - offsets[index]) * cutoff
                if (kotlin.math.abs(scaledDistance) >= filterWidth) {
                    0.0
                } else {
                    val sinc = if (scaledDistance == 0.0) 1.0 else sin(PI * scaledDistance) / (PI * scaledDistance)
                    cutoff * sinc * 0.5 * (1.0 + cos(PI * scaledDistance / filterWidth))
                }
            }
            val total = values.sum()
            require(total.isFinite() && total != 0.0)
            DoubleArray(values.size) { values[it] / total }
        }
    }
}

private fun greatestCommonDivisor(left: Int, right: Int): Int {
    var a = left
    var b = right
    while (b != 0) {
        val remainder = a % b
        a = b
        b = remainder
    }
    return a
}
