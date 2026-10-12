package com.wavv.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendWarmStartTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun currentDclapCacheSkipsModelInstallation() = runBlocking {
        val database = inMemoryDatabase()
        val filesDirectory = isolatedFilesDirectory("dclap")
        try {
            val store = LibraryStore(database)
            val song = song()
            store.replaceSongs(listOf(song))
            store.saveEmbedding(
                SongEmbeddingEntity(
                    songId = song.id,
                    modelId = DCLAP_MODEL_ID,
                    sourceSizeBytes = song.fileSizeBytes,
                    sourceModifiedAt = song.modifiedAt,
                    dimension = 512,
                    dtype = "float32",
                    normalized = true,
                    vector = ByteArray(512 * Float.SIZE_BYTES),
                    updatedAt = song.modifiedAt,
                ),
            )

            val progress = mutableListOf<Pair<Int, Int>>()
            val result = indexDclapSongs(isolatedContext(filesDirectory), store) { done, total ->
                progress += done to total
            }

            assertFalse(result.hasFailures)
            assertEquals(0, result.updatedResults)
            assertEquals(listOf(0 to 1, 1 to 1), progress)
            assertFalse(File(filesDirectory, "dclap/v1").exists())
        } finally {
            database.close()
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun currentMusicUnderstandingCacheSkipsModelInstallation() = runBlocking {
        val database = inMemoryDatabase()
        val filesDirectory = isolatedFilesDirectory("music-understanding")
        try {
            val store = LibraryStore(database)
            val song = song()
            store.replaceSongs(listOf(song))
            val modelId = context.assets.open("music-understanding/v1/manifest.json").bufferedReader().use {
                JSONObject(it.readText()).getString("modelId")
            }
            store.saveMusicUnderstanding(
                MusicUnderstandingResultEntity(
                    songId = song.id,
                    modelId = modelId,
                    sourceSizeBytes = song.fileSizeBytes,
                    sourceModifiedAt = song.modifiedAt,
                    tagsJson = "[]",
                    updatedAt = song.modifiedAt,
                ),
            )

            val progress = mutableListOf<Pair<Int, Int>>()
            val result = indexMusicUnderstandingSongs(isolatedContext(filesDirectory), store) { done, total ->
                progress += done to total
            }

            assertFalse(result.hasFailures)
            assertEquals(0, result.updatedResults)
            assertEquals(listOf(0 to 1, 1 to 1), progress)
            assertFalse(File(filesDirectory, "music-understanding/v1").exists())
        } finally {
            database.close()
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun catalogReplacementReportsOnlyRealChangesAndPreservesLanguageMetadata() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val store = LibraryStore(database)
            val song = song()

            assertTrue(store.replaceSongs(listOf(song)))
            store.setLanguageTags(song.id, listOf("en"))

            assertFalse(store.replaceSongs(listOf(song)))
            assertEquals(listOf("en"), store.getSongs().single().languageTags)
            assertTrue(store.replaceSongs(listOf(song.copy(title = "Updated title"))))
            assertEquals("Updated title", store.getSongs().single().title)
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase() = Room.inMemoryDatabaseBuilder(context, WavvDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun isolatedFilesDirectory(prefix: String) = File(context.cacheDir, "$prefix-${UUID.randomUUID()}")

    private fun isolatedContext(filesDirectory: File): Context = object : ContextWrapper(context) {
        override fun getFilesDir(): File = filesDirectory
        override fun getApplicationContext(): Context = this
    }

    private fun song() = Song(
        id = 1L,
        title = "Cached track",
        artist = "Wavv",
        album = "Cache tests",
        durationMs = 10_000L,
        uri = "content://com.wavv.test/cached-track",
        albumArtUri = null,
        fileSizeBytes = 1_024L,
        modifiedAt = 1_234L,
    )
}
