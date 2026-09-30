package com.wavv.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class DclapIndexWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val store = LibraryStore(WavvDatabase.get(applicationContext))
        return try {
            indexDclapSongs(applicationContext, store) { completed, total ->
                setProgress(workDataOf(KEY_COMPLETED to completed, KEY_TOTAL to total))
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf(KEY_ERROR to error.message))
        } catch (error: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: error::class.simpleName)))
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "wavv-dclap-index"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
    }
}

class DclapIndexScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<DclapIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(DclapIndexWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() = workManager.cancelUniqueWork(DclapIndexWorker.UNIQUE_WORK_NAME)
}
