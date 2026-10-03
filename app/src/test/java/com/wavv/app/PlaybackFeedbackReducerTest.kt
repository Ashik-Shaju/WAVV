package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackFeedbackReducerTest {
    @Test
    fun playIsRecordedOnceOnlyAfterPlayerConfirmsAudiblePlayback() {
        val reducer = PlaybackFeedbackReducer()
        reducer.onTrackChanged(10L, 100_000L, 0L, TrackTransition.INITIAL, 0, "manual", 0L)

        assertEquals("play", reducer.setPlaying(true, 0L).single().eventType)
        assertEquals(emptyList<LocalListeningEvent>(), reducer.setPlaying(true, 500L))
        assertEquals("pause", reducer.setPlaying(false, 1_000L).single().eventType)
        assertEquals("resume", reducer.setPlaying(true, 1_000L).single().eventType)
    }

    @Test
    fun pausedTimeDoesNotCountTowardListeningDuration() {
        val reducer = PlaybackFeedbackReducer()
        reducer.onTrackChanged(10L, 100_000L, 0L, TrackTransition.INITIAL, 0, "manual", 0L)
        reducer.setPlaying(true, 0L)
        reducer.sample(positionMs = 10_000L, nowElapsedMs = 10_000L)
        reducer.setPlaying(false, 10_000L)
        reducer.sample(positionMs = 10_000L, nowElapsedMs = 60_000L)
        reducer.setPlaying(true, 60_000L)
        reducer.sample(positionMs = 15_000L, nowElapsedMs = 65_000L)

        val skip = reducer.onTrackChanged(11L, 100_000L, 0L, TrackTransition.FORWARD_SKIP, 1, "manual", 66_000L).single()

        assertEquals("skip", skip.eventType)
        assertEquals(16L, skip.listenSeconds)
    }

    @Test
    fun forwardSkipIsWeakFeedbackAndPreviousIsNotAReject() {
        val reducer = PlaybackFeedbackReducer()
        reducer.onTrackChanged(10L, 100_000L, 0L, TrackTransition.INITIAL, 0, "manual", 0L)
        reducer.setPlaying(true, 0L)
        reducer.sample(5_000L, 5_000L)
        val skip = reducer.onTrackChanged(11L, 100_000L, 0L, TrackTransition.FORWARD_SKIP, 1, "manual", 6_000L).single()
        reducer.setPlaying(true, 6_000L)
        val previous = reducer.onTrackChanged(10L, 100_000L, 5_000L, TrackTransition.PREVIOUS, 0, "manual", 7_000L).single()

        assertEquals("skip", skip.eventType)
        assertEquals("replay", previous.eventType)
        assertEquals(null, previous.skipPositionSeconds)
    }

    @Test
    fun naturalEndRecordsCompletionOnlyFromObservedListening() {
        val reducer = PlaybackFeedbackReducer()
        reducer.onTrackChanged(10L, 10_000L, 0L, TrackTransition.INITIAL, 0, "manual", 0L)
        reducer.setPlaying(true, 0L)
        reducer.sample(9_000L, 9_000L)

        val completion = reducer.onEnded(10_000L).single()

        assertEquals("completion", completion.eventType)
        assertEquals(10L, completion.listenSeconds)
        assertEquals(1f, completion.completionRatio ?: 0f, .001f)
    }

    @Test
    fun completionUsesDurationLearnedAfterMediaItemTransition() {
        val reducer = PlaybackFeedbackReducer()
        reducer.onTrackChanged(10L, 0L, 0L, TrackTransition.INITIAL, 0, "manual", 0L)
        reducer.setPlaying(true, 0L)
        reducer.sample(positionMs = 5_000L, nowElapsedMs = 5_000L, durationMs = 10_000L)
        reducer.sample(positionMs = 10_000L, nowElapsedMs = 10_000L, durationMs = 10_000L)

        val completion = reducer.onEnded(10_000L).single()

        assertEquals("completion", completion.eventType)
        assertEquals(1f, completion.completionRatio ?: 0f, .001f)
    }

    @Test
    fun automaticQueueAdvanceRecordsPriorTrackCompletionWithoutGlobalEndedState() {
        val reducer = PlaybackFeedbackReducer()
        reducer.onTrackChanged(10L, 10_000L, 0L, TrackTransition.INITIAL, 0, "manual", 0L)
        reducer.setPlaying(true, 0L)
        reducer.sample(9_000L, 9_000L, durationMs = 10_000L)

        val events = reducer.onTrackChanged(
            11L,
            20_000L,
            0L,
            TrackTransition.AUTOMATIC,
            1,
            "personalized",
            10_000L,
            isPlaying = true,
        )

        assertEquals(listOf("completion", "play"), events.map(LocalListeningEvent::eventType))
        assertEquals(1f, events.first().completionRatio ?: 0f, .001f)
        assertEquals(emptyList<LocalListeningEvent>(), reducer.onEnded(10_000L))
    }

    @Test
    fun shortOrUnobservedPlaybackDoesNotBecomeCompletion() {
        val reducer = PlaybackFeedbackReducer()
        reducer.onTrackChanged(10L, 10_000L, 0L, TrackTransition.INITIAL, 0, "manual", 0L)

        assertNull(reducer.onEnded(10_000L).singleOrNull())
    }
}
