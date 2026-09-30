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
    val gcd = greatestCommonDivisor(sourceRate, targetRate)
    val phaseCount = targetRate / gcd
    val cutoff = minOf(1.0, targetRate.toDouble() / sourceRate) * 0.99
    val filterWidth = 9.0
    val radius = ceil(filterWidth / cutoff).toInt()
    val offsets = (-radius + 1..radius).toList()
    val coefficients = Array(phaseCount) { phase ->
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

    val output = FloatArray(outputCount)
    for (outputIndex in output.indices) {
        if (outputIndex % RESAMPLE_CANCELLATION_INTERVAL == 0) currentCoroutineContext().ensureActive()
        val numerator = outputIndex.toLong() * sourceRate
        val sourceIndex = numerator / targetRate
        val phase = ((numerator % targetRate) / gcd).toInt()
        val taps = coefficients[phase]
        var sum = 0.0
        var weight = 0.0
        offsets.forEachIndexed { index, offset ->
            val sampleIndex = sourceIndex + offset
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

private const val RESAMPLE_CANCELLATION_INTERVAL = 1_024

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
