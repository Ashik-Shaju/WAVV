package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerProfilesTest {
    @Test
    fun presetsUseTenStandardOctaveBandCentersAndStayWithinSafeUiRange() {
        assertEquals(listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000), EqualizerProfiles.frequenciesHz)
        assertTrue(EqualizerProfiles.presets.all { profile ->
            profile.gainsDb.size == EqualizerProfiles.frequenciesHz.size &&
                profile.gainsDb.all { it in EqualizerProfiles.MIN_GAIN_DB..EqualizerProfiles.MAX_GAIN_DB }
        })
    }

    @Test
    fun mapsCurvesLogarithmicallyAndClampsOutsideTheSupportedRange() {
        val gains = listOf(-6f, -4f, -2f, 0f, 2f, 4f, 6f, 4f, 2f, 0f)

        assertEquals(-6f, EqualizerProfiles.gainAtFrequency(gains, 20), 0f)
        assertEquals(2f, EqualizerProfiles.gainAtFrequency(gains, 500), 0f)
        assertEquals(5f, EqualizerProfiles.gainAtFrequency(gains, 1_414), .02f)
        assertEquals(0f, EqualizerProfiles.gainAtFrequency(gains, 20_000), 0f)
    }

    @Test
    fun gainPersistenceCodecRejectsMalformedCurvesAndClampsFiniteValues() {
        val gains = EqualizerProfiles.presets[2].gainsDb

        assertEquals(gains, EqualizerProfiles.decodeGains(EqualizerProfiles.encodeGains(gains)))
        assertNull(EqualizerProfiles.decodeGains("1,2,3"))
        assertNull(EqualizerProfiles.decodeGains(List(10) { "NaN" }.joinToString(",")))
        assertEquals(6f, EqualizerProfiles.decodeGains(List(10) { "99" }.joinToString(","))?.first())
    }
}
