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
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal data class AnalysisIndexResult(
    val failedOperations: Int = 0,
    val unavailableMessage: String? = null,
) {
    val hasFailures: Boolean get() = failedOperations > 0 || unavailableMessage != null

    operator fun plus(other: AnalysisIndexResult) = AnalysisIndexResult(
        failedOperations = failedOperations + other.failedOperations,
        unavailableMessage = unavailableMessage ?: other.unavailableMessage,
    )

    fun userMessage(): String = unavailableMessage
        ?: "One or more music-analysis tasks failed. Check access to the selected files and retry."
}

class DclapIndexWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val store = LibraryStore(WavvDatabase.get(applicationContext))
        return try {
            val dclapResult = indexDclapSongs(applicationContext, store) { completed, total ->
                setProgress(workDataOf(KEY_PHASE to PHASE_DCLAP, KEY_COMPLETED to completed, KEY_TOTAL to total))
            }
            val musicUnderstandingResult = indexMusicUnderstandingSongs(applicationContext, store) { completed, total ->
                setProgress(workDataOf(KEY_PHASE to PHASE_MUSIC_UNDERSTANDING, KEY_COMPLETED to completed, KEY_TOTAL to total))
            }
            val result = dclapResult + musicUnderstandingResult
            if (result.hasFailures) {
                Result.failure(
                    workDataOf(
                        KEY_ERROR to result.userMessage(),
                        KEY_FAILURE_COUNT to result.failedOperations,
                    ),
                )
            } else {
                Result.success()
            }
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
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_PHASE = "phase"
        const val PHASE_DCLAP = "dclap"
        const val PHASE_MUSIC_UNDERSTANDING = "music_understanding"
    }
}

class DclapIndexScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueue(): UUID {
        val workName = DclapIndexWorker.UNIQUE_WORK_NAME
        val existing = workManager.getWorkInfosForUniqueWork(workName).get()
        val previouslyActiveId = existing.firstOrNull { !it.state.isFinished }?.id
        val request = OneTimeWorkRequestBuilder<DclapIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request).result.get()
        val current = workManager.getWorkInfosForUniqueWork(workName).get()
        return current.firstOrNull { it.id == request.id }?.id
            ?: previouslyActiveId?.takeIf { activeId -> current.any { it.id == activeId } }
            ?: current.firstOrNull { !it.state.isFinished }?.id
            ?: request.id
    }

    fun observe(id: UUID): Flow<androidx.work.WorkInfo?> = workManager.getWorkInfoByIdFlow(id)

    fun cancel() = workManager.cancelUniqueWork(DclapIndexWorker.UNIQUE_WORK_NAME)
}
