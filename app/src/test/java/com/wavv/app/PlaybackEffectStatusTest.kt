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
    }

    @Test
    fun equalizerLabelDoesNotClaimAnUnsupportedEffectWasApplied() {
        assertEquals("Off", equalizerSettingLabel(false, "Bass", EqualizerPlaybackStatus.Applied))
        assertEquals("Waiting for playback", equalizerSettingLabel(true, "Bass", EqualizerPlaybackStatus.Waiting))
        assertEquals("Unavailable", equalizerSettingLabel(true, "Bass", EqualizerPlaybackStatus.Unavailable))
        assertEquals("Bass", equalizerSettingLabel(true, "Bass", EqualizerPlaybackStatus.Applied))
    }
}
