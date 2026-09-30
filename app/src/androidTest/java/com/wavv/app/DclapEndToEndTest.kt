package com.wavv.app

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DclapEndToEndTest {
    @Test
    fun indexesDeviceAudioPersistsNormalizedEmbeddingAndAnswersTextQuery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        assumeTrue(
            "Grant audio library access to run this smoke test",
            context.checkSelfPermission(audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
        val database = Room.inMemoryDatabaseBuilder(context, WavvDatabase::class.java).build()
        try {
            val store = LibraryStore(database)
            val audioUri = firstAudioUri(context)
            assumeTrue("Add a local audio file to run this smoke test", audioUri != null)
            requireNotNull(audioUri)
            val songs = MediaLibraryRepository(context).loadSongs(LibrarySource.Files(listOf(audioUri.toString())))
            assertFalse("The device library must contain audio for this test", songs.isEmpty())
            store.replaceSongs(songs)

            val progress = mutableListOf<Pair<Int, Int>>()
            indexDclapSongs(context, store) { completed, total -> progress += completed to total }
            assertEquals(songs.size to songs.size, progress.last())

            val embeddings = database.songEmbeddingDao().getAll()
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

            val cachedProgress = mutableListOf<Pair<Int, Int>>()
            indexDclapSongs(context, store) { completed, total -> cachedProgress += completed to total }
            assertEquals(songs.size to songs.size, cachedProgress.last())

            DclapTextSearch(context).use { textSearch ->
                val results = textSearch.search("music", songs, store, limit = 5)
                assertTrue("DCLAP text search returned no indexed songs", results.isNotEmpty())
            }
        } finally {
            database.close()
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
