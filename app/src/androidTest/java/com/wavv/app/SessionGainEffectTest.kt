package com.wavv.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class SessionGainEffectTest {
    @Test
    fun gainMapsSignedDecibelsToLoudnessEnhancerMillibels() {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44_100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(8_192)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val effect = LoudnessEnhancer(track.audioSessionId)
        try {
            assumeTrue("LoudnessEnhancer has no control for this session", effect.hasControl())

            applySessionGain(effect, 1f)
            assertEquals(100f, effect.targetGain, 0.01f)

            applySessionGain(effect, -1f)
            assertEquals(-100f, effect.targetGain, 0.01f)
            assertTrue(effect.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS)
            assertTrue(effect.enabled)
        } finally {
            effect.release()
            track.release()
        }
    }
}
