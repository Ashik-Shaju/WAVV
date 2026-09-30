package com.wavv.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicUnderstandingTest {
    @Test
    fun songWindowsCoverAudioOnceAndWeightThePartialTail() {
        assertEquals(
            listOf(MusicWindow(0, 320_000), MusicWindow(320_000, 1_234)),
            musicUnderstandingWindows(321_234),
        )
    }

    @Test
    fun poolingWeightsEachWindowByItsRealAudioDuration() {
        val pool = MeanProbabilityPool(headCount = 1)
        pool.add(floatArrayOf(0.2f), validSamples = 320_000)
        pool.add(floatArrayOf(0.8f), validSamples = 80_000)

        assertArrayEquals(floatArrayOf(0.32f), pool.finish(), 0.000_001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun windowsRejectEmptyAudio() {
        musicUnderstandingWindows(0)
    }

    @Test
    fun independentHeadsKeepOnlyThresholdedTagsInHeadOrder() {
        val tagger = MusicTagger(
            featureMean = floatArrayOf(1f, -1f),
            featureScale = floatArrayOf(2f, 0.5f),
            heads = listOf(
                MusicHead("genre:ambient", "genre", "Ambient", "weak_uploader_tag", floatArrayOf(2f, 0f), 0f, 0.6f),
                MusicHead("mood_happy", "mood", "Happy", "human_consensus", floatArrayOf(0f, -1f), 0f, 0.6f),
            ),
        )

        val probabilities = tagger.probabilities(floatArrayOf(2f, 0f))

        assertArrayEquals(floatArrayOf(0.7310586f, 0.1192029f), probabilities, 0.00001f)
        assertEquals(listOf("genre:ambient"), tagger.tags(probabilities).map(MusicTag::task))
        assertEquals("weak_uploader_tag", tagger.tags(probabilities).single().labelSource)
    }
}
