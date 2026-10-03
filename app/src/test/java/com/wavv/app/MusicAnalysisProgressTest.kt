package com.wavv.app

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAnalysisProgressTest {
    @Test
    fun runningProgressIsBoundedAndReportsRealPhaseCounts() {
        val progress = MusicAnalysisProgress(
            status = MusicAnalysisStatus.RUNNING,
            phase = MusicAnalysisPhase.MUSIC_UNDERSTANDING,
            completed = 3,
            total = 8,
        )

        assertEquals(0.375f, progress.fraction!!, 0f)
        assertEquals("Music-tag analysis · 3 of 8 tracks", progress.detail)
        assertFalse(progress.canRetry)
    }

    @Test
    fun missingOrInvalidTotalsDoNotCreateFalseDeterminateProgress() {
        assertNull(MusicAnalysisProgress(MusicAnalysisStatus.RUNNING, total = 0).fraction)
        assertNull(MusicAnalysisProgress(MusicAnalysisStatus.RUNNING, completed = 4, total = -1).fraction)
        assertEquals(1f, MusicAnalysisProgress(MusicAnalysisStatus.RUNNING, completed = 12, total = 8).fraction!!, 0f)
        assertEquals(0f, MusicAnalysisProgress(MusicAnalysisStatus.RUNNING, completed = -2, total = 8).fraction!!, 0f)
    }

    @Test
    fun workManagerTerminalAndActiveStatesMapToUserVisibleStatus() {
        assertEquals(MusicAnalysisStatus.QUEUED, WorkInfo.State.ENQUEUED.toMusicAnalysisStatus())
        assertEquals(MusicAnalysisStatus.QUEUED, WorkInfo.State.BLOCKED.toMusicAnalysisStatus())
        assertEquals(MusicAnalysisStatus.RUNNING, WorkInfo.State.RUNNING.toMusicAnalysisStatus())
        assertEquals(MusicAnalysisStatus.COMPLETE, WorkInfo.State.SUCCEEDED.toMusicAnalysisStatus())
        assertEquals(MusicAnalysisStatus.FAILED, WorkInfo.State.FAILED.toMusicAnalysisStatus())
        assertEquals(MusicAnalysisStatus.CANCELLED, WorkInfo.State.CANCELLED.toMusicAnalysisStatus())
    }

    @Test
    fun onlyFailedOrCancelledAnalysisOffersRetry() {
        assertTrue(MusicAnalysisProgress(MusicAnalysisStatus.FAILED).canRetry)
        assertTrue(MusicAnalysisProgress(MusicAnalysisStatus.CANCELLED).canRetry)
        assertFalse(MusicAnalysisProgress(MusicAnalysisStatus.COMPLETE).canRetry)
    }
}
