package com.wavv.app

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.WorkInfo
import androidx.work.workDataOf
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class LibrarySourceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): LibrarySource? = when (preferences.getString(SOURCE_KEY, null)) {
        ALL_DEVICE -> LibrarySource.AllDevice
        FILES -> preferences.getStringSet(FILE_URIS_KEY, emptySet())
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?.let(LibrarySource::Files)
        FOLDER -> preferences.getString(FOLDER_URI_KEY, null)?.let(LibrarySource::Folder)
        else -> null
    }

    fun saveAllDevice() {
        preferences.edit()
            .putString(SOURCE_KEY, ALL_DEVICE)
            .remove(FILE_URIS_KEY)
            .remove(FOLDER_URI_KEY)
            .remove(INDEXED_SOURCE_KEY)
            .apply()
    }

    fun saveFiles(uris: List<Uri>) {
        preferences.edit()
            .putString(SOURCE_KEY, FILES)
            .putStringSet(FILE_URIS_KEY, uris.map(Uri::toString).distinct().toSet())
            .remove(FOLDER_URI_KEY)
            .remove(INDEXED_SOURCE_KEY)
            .apply()
    }

    fun saveFolder(uri: Uri) {
        preferences.edit()
            .putString(SOURCE_KEY, FOLDER)
            .putString(FOLDER_URI_KEY, uri.toString())
            .remove(FILE_URIS_KEY)
            .remove(INDEXED_SOURCE_KEY)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(SOURCE_KEY)
            .remove(FILE_URIS_KEY)
            .remove(FOLDER_URI_KEY)
            .remove(INDEXED_SOURCE_KEY)
            .apply()
    }

    fun isIndexed(source: LibrarySource): Boolean =
        preferences.getString(INDEXED_SOURCE_KEY, null) == source.fingerprint()

    fun markIndexed(source: LibrarySource) {
        preferences.edit().putString(INDEXED_SOURCE_KEY, source.fingerprint()).apply()
    }

    private companion object {
        const val PREFERENCES = "wavv.library"
        const val SOURCE_KEY = "source"
        const val FILE_URIS_KEY = "file_uris"
        const val FOLDER_URI_KEY = "folder_uri"
        const val INDEXED_SOURCE_KEY = "indexed_source"
        const val ALL_DEVICE = "all_device"
        const val FILES = "files"
        const val FOLDER = "folder"
    }
}

class LibraryIndexWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val sourceStore = LibrarySourceStore(applicationContext)
        val source = sourceStore.read() ?: return Result.failure()
        return try {
            setProgress(workDataOf(KEY_COMPLETED to 0, KEY_TOTAL to -1))
            val songs = MediaLibraryRepository(applicationContext).loadSongs(source) { completed, total ->
                if (completed == 1 || completed % PROGRESS_INTERVAL == 0 || total == completed) {
                    setProgress(workDataOf(KEY_COMPLETED to completed, KEY_TOTAL to (total ?: -1)))
                }
            }
            if (sourceStore.read()?.fingerprint() != source.fingerprint()) return Result.success()
            LibraryStore(WavvDatabase.get(applicationContext)).replaceSongs(songs)
            sourceStore.markIndexed(source)
            setProgress(workDataOf(KEY_COMPLETED to songs.size, KEY_TOTAL to songs.size))
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            failure(error)
        } catch (error: IOException) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else failure(error)
        } catch (error: Exception) {
            failure(error)
        }
    }

    private fun failure(error: Exception): Result = Result.failure(
        workDataOf(KEY_ERROR to (error.message ?: error::class.simpleName ?: "Indexing failed").take(MAX_ERROR_LENGTH)),
    )

    companion object {
        const val UNIQUE_WORK_NAME = "wavv-library-index"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val MAX_ERROR_LENGTH = 512
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val PROGRESS_INTERVAL = 25
    }
}

class LibraryIndexScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueue(): UUID {
        val request = OneTimeWorkRequestBuilder<LibraryIndexWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(LibraryIndexWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    fun cancel() {
        workManager.cancelUniqueWork(LibraryIndexWorker.UNIQUE_WORK_NAME)
    }

    fun observe(id: UUID): Flow<WorkInfo?> = workManager.getWorkInfoByIdFlow(id)
}

internal fun LibrarySource.fingerprint(): String = when (this) {
    LibrarySource.AllDevice -> "all_device"
    is LibrarySource.Files -> "files:${uris.distinct().sorted().joinToString("\u0000")}"
    is LibrarySource.Folder -> "folder:$uri"
}
