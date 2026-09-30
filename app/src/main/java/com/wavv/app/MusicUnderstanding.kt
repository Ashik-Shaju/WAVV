package com.wavv.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.sin

internal const val MUSIC_UNDERSTANDING_SAMPLE_RATE = 32_000
internal const val MUSIC_UNDERSTANDING_WINDOW_SAMPLES = 10 * MUSIC_UNDERSTANDING_SAMPLE_RATE
internal const val MUSIC_UNDERSTANDING_CONTRACT_VERSION = 1

internal data class MusicWindow(val startSample: Int, val validSamples: Int)

internal data class MusicTag(
    val task: String,
    val taxonomy: String,
    val label: String,
    val labelSource: String,
    val probability: Float,
    val threshold: Float,
)

internal data class MusicHead(
    val task: String,
    val taxonomy: String,
    val label: String,
    val labelSource: String,
    val weights: FloatArray,
    val intercept: Float,
    val threshold: Float,
)

internal class MusicTagger(
    private val featureMean: FloatArray,
    private val featureScale: FloatArray,
    private val heads: List<MusicHead>,
) {
    init {
        require(featureMean.isNotEmpty() && featureMean.size == featureScale.size)
        require(heads.isNotEmpty())
        require(featureMean.all(Float::isFinite) && featureScale.all { it.isFinite() && it > 0f })
        require(heads.all { head ->
            head.weights.size == featureMean.size && head.weights.all(Float::isFinite) &&
                head.intercept.isFinite() && head.threshold.isFinite() && head.threshold in 0f..1f
        })
    }

    fun probabilities(features: FloatArray): FloatArray {
        require(features.size == featureMean.size && features.all(Float::isFinite))
        return FloatArray(heads.size) { index ->
            val head = heads[index]
            var logit = head.intercept.toDouble()
            for (feature in features.indices) {
                logit += ((features[feature] - featureMean[feature]) / featureScale[feature] * head.weights[feature]).toDouble()
            }
            if (logit >= 0.0) {
                (1.0 / (1.0 + exp(-logit))).toFloat()
            } else {
                (exp(logit) / (1.0 + exp(logit))).toFloat()
            }
        }
    }

    fun tags(probabilities: FloatArray): List<MusicTag> {
        require(probabilities.size == heads.size && probabilities.all { it.isFinite() && it in 0f..1f })
        return heads.indices.mapNotNull { index ->
            val head = heads[index]
            val probability = probabilities[index]
            if (probability < head.threshold) return@mapNotNull null
            MusicTag(head.task, head.taxonomy, head.label, head.labelSource, probability, head.threshold)
        }
    }
}

internal fun musicUnderstandingWindows(sampleCount: Int): List<MusicWindow> {
    require(sampleCount > 0) { "Audio must contain at least one sample" }
    return buildList {
        var start = 0
        while (start < sampleCount) {
            val validSamples = minOf(MUSIC_UNDERSTANDING_WINDOW_SAMPLES, sampleCount - start)
            add(MusicWindow(start, validSamples))
            if (validSamples < MUSIC_UNDERSTANDING_WINDOW_SAMPLES) break
            start += MUSIC_UNDERSTANDING_WINDOW_SAMPLES
        }
    }
}

internal class MeanProbabilityPool(private val headCount: Int) {
    private val weightedSums = DoubleArray(headCount)
    private var totalWeight = 0L

    init {
        require(headCount > 0)
    }

    fun add(probabilities: FloatArray, validSamples: Int) {
        require(probabilities.size == headCount)
        require(validSamples in 1..MUSIC_UNDERSTANDING_WINDOW_SAMPLES)
        probabilities.forEachIndexed { index, probability ->
            require(probability.isFinite() && probability in 0f..1f)
            weightedSums[index] += probability * validSamples
        }
        totalWeight += validSamples
    }

    fun finish(): FloatArray {
        check(totalWeight > 0) { "No audio windows were pooled" }
        return FloatArray(headCount) { index -> (weightedSums[index] / totalWeight).toFloat() }
    }
}

internal class MusicUnderstandingPreprocessor(private val melFilterBank: FloatArray) {
    private val window = FloatArray(WINDOW_SAMPLES)
    private val preEmphasized = FloatArray(WINDOW_SAMPLES - 1)
    private val frameReal = FloatArray(FFT_SIZE)
    private val frameImaginary = FloatArray(FFT_SIZE)
    private val power = FloatArray(FFT_SIZE / 2 + 1)
    private val hann = FloatArray(WINDOW_LENGTH) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / (WINDOW_LENGTH - 1))).toFloat()
    }
    private val mel = FloatArray(MEL_BINS * FRAME_COUNT)

    init {
        require(melFilterBank.size == MEL_BINS * (FFT_SIZE / 2 + 1))
    }

    fun logMel(samples: FloatArray, validSamples: Int = samples.size): FloatArray {
        require(samples.size == WINDOW_SAMPLES)
        require(validSamples in 1..WINDOW_SAMPLES)
        for (index in preEmphasized.indices) {
            preEmphasized[index] = samples[index + 1] - PRE_EMPHASIS * samples[index]
        }

        for (frame in 0 until FRAME_COUNT) {
            frameReal.fill(0f)
            frameImaginary.fill(0f)
            val frameStart = frame * HOP_LENGTH - FFT_SIZE / 2 + (FFT_SIZE - WINDOW_LENGTH) / 2
            for (index in 0 until WINDOW_LENGTH) {
                val sourceIndex = reflect(frameStart + index, preEmphasized.size)
                frameReal[(FFT_SIZE - WINDOW_LENGTH) / 2 + index] = preEmphasized[sourceIndex] * hann[index]
            }
            radix2Fft(frameReal, frameImaginary)
            for (bin in power.indices) {
                power[bin] = frameReal[bin] * frameReal[bin] + frameImaginary[bin] * frameImaginary[bin]
            }
            for (band in 0 until MEL_BINS) {
                val filterStart = band * power.size
                var energy = 0f
                for (bin in power.indices) energy += power[bin] * melFilterBank[filterStart + bin]
                mel[band * FRAME_COUNT + frame] = ((ln(energy + LOG_EPSILON) + NORMALIZATION_OFFSET) / NORMALIZATION_SCALE)
            }
        }
        return mel
    }

    private fun reflect(index: Int, sampleCount: Int): Int {
        var result = index
        while (result < 0 || result >= sampleCount) {
            result = if (result < 0) -result else 2 * sampleCount - 2 - result
        }
        return result
    }

    private companion object {
        const val WINDOW_SAMPLES = MUSIC_UNDERSTANDING_WINDOW_SAMPLES
        const val FFT_SIZE = 1_024
        const val WINDOW_LENGTH = 800
        const val HOP_LENGTH = 320
        const val FRAME_COUNT = 1_000
        const val MEL_BINS = 128
        const val PRE_EMPHASIS = 0.97f
        const val LOG_EPSILON = 1e-5f
        const val NORMALIZATION_OFFSET = 4.5f
        const val NORMALIZATION_SCALE = 5f
    }
}
