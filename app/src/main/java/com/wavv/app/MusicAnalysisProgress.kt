package com.wavv.app

import androidx.work.WorkInfo

enum class MusicAnalysisPhase(val label: String) {
    DCLAP("Audio search indexing"),
    MUSIC_UNDERSTANDING("Music-tag analysis"),
}

enum class MusicAnalysisStatus {
    QUEUED,
    RUNNING,
    COMPLETE,
    FAILED,
    CANCELLED,
}

data class MusicAnalysisProgress(
    val status: MusicAnalysisStatus,
    val phase: MusicAnalysisPhase? = null,
    val completed: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float?
        get() = total.takeIf { it > 0 }?.let {
            completed.coerceIn(0, it).toFloat() / it
        }

    val canRetry: Boolean
        get() = status == MusicAnalysisStatus.FAILED || status == MusicAnalysisStatus.CANCELLED

    val detail: String
        get() = when (status) {
            MusicAnalysisStatus.QUEUED -> "Queued for on-device analysis"
            MusicAnalysisStatus.RUNNING -> buildString {
                append(phase?.label ?: "Analyzing your music")
                if (total > 0) append(" · ${completed.coerceIn(0, total)} of $total tracks")
            }
            MusicAnalysisStatus.COMPLETE -> "Music insights are up to date"
            MusicAnalysisStatus.FAILED -> "Some analysis tasks need attention"
            MusicAnalysisStatus.CANCELLED -> "Analysis was canceled"
        }
}

internal fun WorkInfo.State.toMusicAnalysisStatus(): MusicAnalysisStatus = when (this) {
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> MusicAnalysisStatus.QUEUED
    WorkInfo.State.RUNNING -> MusicAnalysisStatus.RUNNING
    WorkInfo.State.SUCCEEDED -> MusicAnalysisStatus.COMPLETE
    WorkInfo.State.FAILED -> MusicAnalysisStatus.FAILED
    WorkInfo.State.CANCELLED -> MusicAnalysisStatus.CANCELLED
}
