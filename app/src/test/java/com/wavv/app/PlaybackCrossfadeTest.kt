package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCrossfadeTest {
    @Test
    fun crossfadeRequiresTwoDifferentTracksAndNoUHQOrRepeatOne() {
        assertEquals(true, isCrossfadeEligible("1", "2", false, false, false))
        assertEquals(false, isCrossfadeEligible("1", "1", false, false, false))
        assertEquals(false, isCrossfadeEligible(null, "2", false, false, false))
        assertEquals(false, isCrossfadeEligible("1", "2", true, false, false))
        assertEquals(false, isCrossfadeEligible("1", "2", false, true, false))
        assertEquals(false, isCrossfadeEligible("1", "2", false, false, true))
    }

    @Test
    fun equalPowerGainsReachExpectedEndpointsAndMidpoint() {
        val start = equalPowerCrossfadeGains(0f)
        val middle = equalPowerCrossfadeGains(0.5f)
        val end = equalPowerCrossfadeGains(1f)

        assertEquals(1f, start.outgoing, 0.0001f)
        assertEquals(0f, start.incoming, 0.0001f)
        assertEquals(1f, end.incoming, 0.0001f)
        assertEquals(0f, end.outgoing, 0.0001f)
        assertEquals(middle.outgoing, middle.incoming, 0.0001f)
        assertEquals(1f, middle.outgoing * middle.outgoing + middle.incoming * middle.incoming, 0.0001f)
    }

    @Test
    fun equalPowerProgressIsClamped() {
        assertEquals(equalPowerCrossfadeGains(0f), equalPowerCrossfadeGains(-1f))
        assertEquals(equalPowerCrossfadeGains(1f), equalPowerCrossfadeGains(2f))
    }

    @Test
    fun effectiveDurationNeverExceedsRequestOrHalfOfEitherTrack() {
        assertEquals(4_000L, effectiveCrossfadeDurationMs(12_000L, 8_000L, 10_000L))
        assertEquals(1_500L, effectiveCrossfadeDurationMs(12_000L, 8_000L, 3_000L))
        assertEquals(0L, effectiveCrossfadeDurationMs(0L, 8_000L, 8_000L))
        assertEquals(0L, effectiveCrossfadeDurationMs(4_000L, -1L, 8_000L))
    }

    @Test
    fun maximumSettingIsTwelveSeconds() {
        assertEquals(12, CrossfadeSettings.MAX_SECONDS)
        assertEquals(0, CrossfadeSettings.normalizeSeconds(-1))
        assertEquals(12, CrossfadeSettings.normalizeSeconds(20))
    }
}
