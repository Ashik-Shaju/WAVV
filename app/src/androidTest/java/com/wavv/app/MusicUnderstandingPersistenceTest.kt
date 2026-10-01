package com.wavv.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicUnderstandingPersistenceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WavvDatabase::class.java,
    )

    @Test
    fun versionFourDatabaseMigratesToVersionSixWithoutLosingSchema() {
        val databaseName = "wavv-migration-${UUID.randomUUID()}"
        migrationHelper.createDatabase(databaseName, 4).close()

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            WavvDatabase.MIGRATION_4_5,
            WavvDatabase.MIGRATION_5_6,
        ).close()
    }

    @Test
    fun cancellationStopsBeforeLoadingTheModel() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val store = LibraryStore(database)
            store.replaceSongs(listOf(song("content://local/one")))
            val job = launch {
                indexMusicUnderstandingSongs(context, store) { _, _ ->
                    currentCoroutineContext().cancel(CancellationException("test cancellation"))
                }
            }

            job.join()

            assertTrue(job.isCancelled)
            assertNull(database.analysisJobDao().get(1L, "music_understanding"))
        } finally {
            database.close()
        }
    }

    @Test
    fun unreadableSongIsRecordedAsFailedWithoutPersistingTags() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val store = LibraryStore(database)
            val song = song("content://com.wavv.test/unreadable")
            store.replaceSongs(listOf(song))

            indexMusicUnderstandingSongs(context, store)

            val status = database.analysisJobDao().get(song.id, "music_understanding")
            assertNotNull(status)
            assertEquals("failed", status?.status)
            assertNotNull(status?.lastError)
            assertNull(database.musicUnderstandingDao().get(song.id))
        } finally {
            database.close()
        }
    }

    @Test
    fun fatalAnalysisFailureIsRecordedBeforeItEscapes() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val store = LibraryStore(database)
            val song = song("content://com.wavv.test/fatal")
            store.replaceSongs(listOf(song))
            val failure = OutOfMemoryError("simulated allocation failure")

            try {
                store.runAnalysisJob(song.id, "dclap") { throw failure }
                throw AssertionError("Fatal analysis error was swallowed")
            } catch (actual: OutOfMemoryError) {
                assertTrue(actual === failure)
            }

            val status = database.analysisJobDao().get(song.id, "dclap")
            assertNotNull(status)
            assertEquals("failed", status?.status)
            assertNotNull(status?.lastError)
            assertTrue(status?.lastError?.contains("fatal", ignoreCase = true) == true)
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase() = Room.inMemoryDatabaseBuilder(context, WavvDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun song(uri: String) = Song(
        id = 1L,
        title = "local file",
        artist = "local artist",
        album = "local album",
        durationMs = 10_000L,
        uri = uri,
        albumArtUri = null,
        fileSizeBytes = 123L,
        modifiedAt = 456L,
        codec = null,
        container = null,
        sampleRate = null,
        channels = null,
        metadataSource = "local",
    )
}
