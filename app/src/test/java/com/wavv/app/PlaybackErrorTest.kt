package com.wavv.app

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorTest {
    @Test
    fun unsupportedContainerOrCodec_explainsDeviceCapabilityLimit() {
        assertEquals(
            "This device can't play this audio format.",
            playbackErrorMessage(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, null),
        )
    }

    @Test
    fun otherPlaybackFailuresKeepTheirSpecificMessage() {
        assertEquals(
            "Audio file is unavailable.",
            playbackErrorMessage(PlaybackException.ERROR_CODE_IO_NO_PERMISSION, "Audio file is unavailable."),
        )
    }
}
