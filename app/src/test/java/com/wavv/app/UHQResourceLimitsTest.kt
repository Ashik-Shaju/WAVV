package com.wavv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UHQResourceLimitsTest {
    @Test
    fun uhqAcceptsOnlyAndroidAacDecoderMimeType() {
        assertTrue(isSupportedUhqMimeType(UHQ_AAC_MIME_TYPE))
        assertFalse(isSupportedUhqMimeType("audio/mpeg"))
        assertFalse(isSupportedUhqMimeType("audio/flac"))
    }

    @Test
    fun pcmLimitAcceptsExactBoundaryAndRejectsOneFrameOver() {
        val maxStereoFloatFrames = MAX_UHQ_AUDIO_BYTES / (2 * Float.SIZE_BYTES)

        assertTrue(isUhqAudioWithinLimit(maxStereoFloatFrames, channels = 2, bytesPerSample = Float.SIZE_BYTES))
        assertFalse(isUhqAudioWithinLimit(maxStereoFloatFrames + 1, channels = 2, bytesPerSample = Float.SIZE_BYTES))
        assertFalse(isUhqAudioWithinLimit(-1, channels = 2, bytesPerSample = Float.SIZE_BYTES))
        assertFalse(isUhqAudioWithinLimit(1, channels = 3, bytesPerSample = Float.SIZE_BYTES))
    }

    @Test
    fun outputLimitReservesTheWavHeader() {
        val maxStereoPcm16Frames = (MAX_UHQ_AUDIO_BYTES - 44) / (2 * Short.SIZE_BYTES)

        assertTrue(
            isUhqAudioWithinLimit(
                maxStereoPcm16Frames,
                channels = 2,
                bytesPerSample = Short.SIZE_BYTES,
                maximumBytes = MAX_UHQ_AUDIO_BYTES - 44,
            ),
        )
        assertFalse(
            isUhqAudioWithinLimit(
                maxStereoPcm16Frames + 1,
                channels = 2,
                bytesPerSample = Short.SIZE_BYTES,
                maximumBytes = MAX_UHQ_AUDIO_BYTES - 44,
            ),
        )
    }
}
