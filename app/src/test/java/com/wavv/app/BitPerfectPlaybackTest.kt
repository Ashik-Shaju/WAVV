package com.wavv.app

import android.media.AudioFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitPerfectPlaybackTest {
    @Test
    fun bitPerfectRequiresAnExactPcmOutputFormatAndNoOffload() {
        assertTrue(
            matchesBitPerfectMixerFormat(
                outputEncoding = AudioFormat.ENCODING_PCM_16BIT,
                outputSampleRate = 44_100,
                outputChannelMask = AudioFormat.CHANNEL_OUT_STEREO,
                isOffload = false,
                mixerEncoding = AudioFormat.ENCODING_PCM_16BIT,
                mixerSampleRate = 44_100,
                mixerChannelMask = AudioFormat.CHANNEL_OUT_STEREO,
            ),
        )
        assertFalse(
            matchesBitPerfectMixerFormat(
                AudioFormat.ENCODING_PCM_16BIT,
                44_100,
                AudioFormat.CHANNEL_OUT_STEREO,
                false,
                AudioFormat.ENCODING_PCM_16BIT,
                48_000,
                AudioFormat.CHANNEL_OUT_STEREO,
            ),
        )
        assertFalse(
            matchesBitPerfectMixerFormat(
                AudioFormat.ENCODING_PCM_16BIT,
                44_100,
                AudioFormat.CHANNEL_OUT_STEREO,
                false,
                AudioFormat.ENCODING_PCM_16BIT,
                44_100,
                AudioFormat.CHANNEL_OUT_MONO,
            ),
        )
        assertFalse(
            matchesBitPerfectMixerFormat(
                AudioFormat.ENCODING_PCM_16BIT,
                44_100,
                AudioFormat.CHANNEL_OUT_STEREO,
                true,
                AudioFormat.ENCODING_PCM_16BIT,
                44_100,
                AudioFormat.CHANNEL_OUT_STEREO,
            ),
        )
        assertFalse(
            matchesBitPerfectMixerFormat(
                AudioFormat.ENCODING_MP3,
                44_100,
                AudioFormat.CHANNEL_OUT_STEREO,
                false,
                AudioFormat.ENCODING_MP3,
                44_100,
                AudioFormat.CHANNEL_OUT_STEREO,
            ),
        )
    }
}
