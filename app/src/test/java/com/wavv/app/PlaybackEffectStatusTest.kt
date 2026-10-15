package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackEffectStatusTest {
    @Test
    fun uhqLabelShowsTheCurrentTrackProcessingOutcome() {
        val state = PlaybackEffectState(uhqTrackId = 7L, uhq = UhqPlaybackStatus.Preparing)

        assertEquals("Off", uhqSettingLabel(false, 7L, state))
        assertEquals("On", uhqSettingLabel(true, null, state))
        assertEquals("On", uhqSettingLabel(true, 8L, state))
        assertEquals("Preparing", uhqSettingLabel(true, 7L, state))
        assertEquals("Enhanced", uhqSettingLabel(true, 7L, state.copy(uhq = UhqPlaybackStatus.Enhanced)))
        assertEquals("Original", uhqSettingLabel(true, 7L, state.copy(uhq = UhqPlaybackStatus.Unsupported)))
        assertEquals("Failed · original", uhqSettingLabel(true, 7L, state.copy(uhq = UhqPlaybackStatus.Failed)))
        assertEquals("Paused by bit-perfect mode", uhqSettingLabel(true, 7L, state.copy(uhq = UhqPlaybackStatus.Bypassed)))
    }

    @Test
    fun uhqLabelShowsWorkerStageAndRealCompletedProgress() {
        val state = PlaybackEffectState(
            uhqTrackId = 7L,
            uhq = UhqPlaybackStatus.Preparing,
            uhqProgressPercent = 42,
            uhqProgressStage = "Enhancing audio",
        )

        assertEquals("Enhancing audio · 42%", uhqSettingLabel(true, 7L, state))
        assertEquals("Preparing", uhqSettingLabel(true, 7L, state.copy(uhqProgressPercent = null)))
    }

    @Test
    fun equalizerLabelDoesNotClaimAnUnsupportedEffectWasApplied() {
        assertEquals("Off", equalizerSettingLabel(false, "Bass", EqualizerPlaybackStatus.Applied))
        assertEquals("Waiting for playback", equalizerSettingLabel(true, "Bass", EqualizerPlaybackStatus.Waiting))
        assertEquals("Unavailable", equalizerSettingLabel(true, "Bass", EqualizerPlaybackStatus.Unavailable))
        assertEquals(
            "Bass · Paused by bit-perfect mode",
            equalizerSettingLabel(true, "Bass", EqualizerPlaybackStatus.Bypassed),
        )
        assertEquals("Bass", equalizerSettingLabel(true, "Bass", EqualizerPlaybackStatus.Applied))
        assertEquals(
            "Bass · Gain unavailable",
            equalizerSettingLabel(
                true,
                "Bass",
                EqualizerPlaybackStatus.Applied,
                EqualizerPlaybackStatus.Unavailable,
                gainDb = 3f,
            ),
        )
    }

    @Test
    fun bitPerfectLabelDistinguishesRequestedFromActuallyApplied() {
        assertEquals("Off", bitPerfectSettingLabel(false, BitPerfectPlaybackStatus.Active))
        assertEquals("Ready for USB audio", bitPerfectSettingLabel(true, BitPerfectPlaybackStatus.Available))
        assertEquals("Active", bitPerfectSettingLabel(true, BitPerfectPlaybackStatus.Active))
        assertEquals("Android 14+ required", bitPerfectSettingLabel(true, BitPerfectPlaybackStatus.UnsupportedApi))
        assertEquals("Format unsupported", bitPerfectSettingLabel(true, BitPerfectPlaybackStatus.UnsupportedFormat))
    }
}
