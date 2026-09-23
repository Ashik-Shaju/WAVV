package com.wavv.app

import android.content.Context
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DclapEndToEndTest {
    @Test
    fun indexesDeviceAudioPersistsNormalizedEmbeddingAndAnswersTextQuery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LibraryStore(WavvDatabase.get(context))
        val audioUri = firstAudioUri(context) ?: error("The device must expose at least one audio row")
        val songs = MediaLibraryRepository(context).loadSongs(LibrarySource.Files(listOf(audioUri.toString())))
        assertFalse("The device library must contain audio for this test", songs.isEmpty())
        store.replaceSongs(songs)

        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<DclapIndexWorker>().build()
        val uniqueName = "dclap-integration-${UUID.randomUUID()}"
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        val work = workManager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true }
        assertEquals("DCLAP worker failed: ${work?.outputData}", androidx.work.WorkInfo.State.SUCCEEDED, work?.state)

        val embeddings = WavvDatabase.get(context).songEmbeddingDao().getAll()
        assertTrue("DCLAP did not persist an embedding", embeddings.isNotEmpty())
        embeddings.forEach { embedding ->
            assertEquals(DCLAP_MODEL_ID, embedding.modelId)
            assertEquals(512, embedding.dimension)
            assertEquals("float32", embedding.dtype)
            assertTrue(embedding.normalized)
            val vector = embedding.vector.toFloatArrayForTest()
            assertEquals(512, vector.size)
            val norm = sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
            assertTrue("Embedding is not normalized: $norm", abs(norm - 1.0) < 0.01)
        }

        DclapTextSearch(context).use { textSearch ->
            val results = textSearch.search("music", songs, store, limit = 5)
            assertTrue("DCLAP text search returned no indexed songs", results.isNotEmpty())
        }
    }
}

private fun firstAudioUri(context: Context): android.net.Uri? =
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Media._ID),
        null,
        null,
        "${MediaStore.Audio.Media._ID} ASC",
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
            .appendPath(cursor.getLong(0).toString())
            .build()
    }

private fun ByteArray.toFloatArrayForTest(): FloatArray =
    ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { buffer ->
        FloatArray(buffer.remaining()).also(buffer::get)
    }
