package com.wavv.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): LibrarySource? = when (preferences.getString(SOURCE_KEY, null)) {
        ALL_DEVICE -> LibrarySource.AllDevice
        FILES -> preferences.getStringSet(FILE_URIS_KEY, emptySet())
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?.let(LibrarySource::Files)
        FOLDER -> preferences.getString(FOLDER_URI_KEY, null)?.let(LibrarySource::Folder)
        FOLDERS -> LibrarySource.Folders(preferences.getStringSet(FOLDER_URIS_KEY, emptySet()).orEmpty().toList())
        else -> null
    }

    fun saveAllDevice() {
        updateSource(emptySet()) {
            putString(SOURCE_KEY, ALL_DEVICE)
            remove(FILE_URIS_KEY)
            remove(FOLDER_URI_KEY)
            remove(FOLDER_URIS_KEY)
            remove(INDEXED_SOURCE_KEY)
        }
    }

    fun saveFiles(uris: List<Uri>): List<Uri> {
        val readableUris = uris.distinct().filter { uri ->
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
        }
        if (readableUris.isEmpty()) return emptyList()
        val retainedUris = readableUris.map(Uri::toString).toSet()
        updateSource(retainedUris) {
            putString(SOURCE_KEY, FILES)
            putStringSet(FILE_URIS_KEY, retainedUris)
            remove(FOLDER_URI_KEY)
            remove(FOLDER_URIS_KEY)
            remove(INDEXED_SOURCE_KEY)
        }
        return readableUris
    }

    fun saveFolder(uri: Uri): Boolean {
        val permissionSaved = runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!permissionSaved) return false
        updateSource(setOf(uri.toString())) {
            putString(SOURCE_KEY, FOLDER)
            putString(FOLDER_URI_KEY, uri.toString())
            remove(FILE_URIS_KEY)
            remove(FOLDER_URIS_KEY)
            remove(INDEXED_SOURCE_KEY)
        }
        return true
    }

    fun addFolder(uri: Uri): Boolean {
        val permissionSaved = runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!permissionSaved) return false
        val folders = readFolderUris().toMutableSet().apply { add(uri.toString()) }
        saveFolders(folders)
        return true
    }

    fun removeFolder(uri: String) {
        val remaining = readFolderUris().filterNot { it == uri }.toSet()
        saveFolders(remaining)
    }

    private fun readFolderUris(): Set<String> = when (preferences.getString(SOURCE_KEY, null)) {
        FOLDERS -> preferences.getStringSet(FOLDER_URIS_KEY, emptySet()).orEmpty()
        FOLDER -> preferences.getString(FOLDER_URI_KEY, null)?.let(::setOf).orEmpty()
        else -> emptySet()
    }

    private fun saveFolders(uris: Set<String>) {
        updateSource(uris) {
            putString(SOURCE_KEY, FOLDERS)
            putStringSet(FOLDER_URIS_KEY, uris)
            remove(FOLDER_URI_KEY)
            remove(FILE_URIS_KEY)
            remove(INDEXED_SOURCE_KEY)
        }
    }

    fun clear() {
        updateSource(emptySet()) {
            remove(SOURCE_KEY)
            remove(FILE_URIS_KEY)
            remove(FOLDER_URI_KEY)
            remove(FOLDER_URIS_KEY)
            remove(INDEXED_SOURCE_KEY)
        }
    }

    private fun updateSource(retainedUris: Set<String>, update: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(update).apply()
        appContext.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.uri.toString() !in retainedUris }
            .forEach { permission ->
                runCatching {
                    appContext.contentResolver.releasePersistableUriPermission(
                        permission.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
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
        const val FOLDER_URIS_KEY = "folder_uris"
        const val INDEXED_SOURCE_KEY = "indexed_source"
        const val ALL_DEVICE = "all_device"
        const val FILES = "files"
        const val FOLDER = "folder"
        const val FOLDERS = "folders"
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
    is LibrarySource.Folders -> "folders:${uris.distinct().sorted().joinToString("\u0000")}"
}
