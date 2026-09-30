package com.wavv.app

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicUnderstandingEndToEndTest {
    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun androidFrontendMatchesThePinnedReferenceForFixedPcm() {
        val melBank = targetContext.assets.open("music-understanding/v1/mel_filterbank.f32").use { input ->
            ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { buffer ->
                FloatArray(buffer.remaining()).also(buffer::get)
            }
        }
        val expected = readFloats("music-understanding/v1/mel_golden.f32")
        val actual = MusicUnderstandingPreprocessor(melBank).logMel(threeTonePcm())

        assertEquals(128_000, expected.size)
        assertArrayEquals(expected, actual, 0.001f)
    }

    @Test
    fun bundledOnnxProducesGoldenTaskProbabilitiesAndTags() = runBlocking {
        val expected = JSONObject(
            testContext.assets.open("music-understanding/v1/inference_golden.json")
                .bufferedReader().use { it.readText() },
        ).getJSONArray("probabilities")
        val expectedProbabilities = FloatArray(expected.length()) { expected.getDouble(it).toFloat() }
        val expectedFeatures = readFloats("music-understanding/v1/feature_golden.f32")
        val database = Room.inMemoryDatabaseBuilder(targetContext, WavvDatabase::class.java).build()
        try {
            val actual = MusicUnderstandingModel(targetContext).use { model ->
                assertArrayEquals(expectedFeatures, model.encodeWindow(threeTonePcm()), 0.005f)
                model.analyzePcm(threeTonePcm())
            }

            assertEquals("wavv-dymn04as-81d9eb2350a738c3", actual.modelId)
            assertArrayEquals(expectedProbabilities, actual.probabilities, 0.01f)
            assertEquals(
                listOf("mood_happy", "mood_relaxed", "genre:ambient"),
                actual.tags.map(MusicTag::task),
            )
            assertEquals("weak_uploader_tag", actual.tags.last().labelSource)
            assertTrue(actual.tags.none { it.task == "instrument:electricguitar" })
            assertTrue(actual.probabilities.all { it.isFinite() && it in 0f..1f })

            val stored = MusicUnderstandingResultEntity(
                songId = 44L,
                modelId = actual.modelId,
                sourceSizeBytes = 1_234L,
                sourceModifiedAt = 9_876L,
                tagsJson = actual.tags.toJson(),
                updatedAt = 10_000L,
            )
            database.musicUnderstandingDao().upsert(stored)
            assertEquals(stored, database.musicUnderstandingDao().get(stored.songId))
        } finally {
            database.close()
        }
    }

    private fun readFloats(path: String): FloatArray =
        testContext.assets.open(path).use { input ->
            ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { buffer ->
                FloatArray(buffer.remaining()).also(buffer::get)
            }
        }

    private fun threeTonePcm() = FloatArray(MUSIC_UNDERSTANDING_WINDOW_SAMPLES) { index ->
        val time = index.toFloat() / MUSIC_UNDERSTANDING_SAMPLE_RATE
        (
            0.25f * sin((2.0 * PI * 220.0).toFloat() * time) +
                0.15f * sin((2.0 * PI * 440.0).toFloat() * time) +
                0.08f * sin((2.0 * PI * 880.0).toFloat() * time)
            )
    }
}
