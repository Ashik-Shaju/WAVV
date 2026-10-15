package com.wavv.app

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioAttributes as PlatformAudioAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider

private const val BIT_PERFECT_ENABLED = "bit_perfect_enabled"

internal enum class BitPerfectPlaybackStatus {
    Off,
    Available,
    Active,
    UnsupportedApi,
    NoUsbOutput,
    UnsupportedOutput,
    UnsupportedFormat,
    Failed,
}

internal object BitPerfectSettings {
    fun preferences(context: Context): SharedPreferences = CrossfadeSettings.preferences(context)

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(BIT_PERFECT_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(BIT_PERFECT_ENABLED, enabled).apply()
    }

    fun preferenceKey(): String = BIT_PERFECT_ENABLED
}

internal fun matchesBitPerfectMixerFormat(
    outputEncoding: Int,
    outputSampleRate: Int,
    outputChannelMask: Int,
    isOffload: Boolean,
    mixerEncoding: Int,
    mixerSampleRate: Int,
    mixerChannelMask: Int,
): Boolean = !isOffload && outputEncoding in BIT_PERFECT_PCM_ENCODINGS &&
    outputSampleRate > 0 && outputChannelMask != AudioFormat.CHANNEL_INVALID &&
    outputEncoding == mixerEncoding &&
    outputSampleRate == mixerSampleRate &&
    outputChannelMask == mixerChannelMask

private val BIT_PERFECT_PCM_ENCODINGS = setOf(
    AudioFormat.ENCODING_PCM_8BIT,
    AudioFormat.ENCODING_PCM_16BIT,
    AudioFormat.ENCODING_PCM_24BIT_PACKED,
    AudioFormat.ENCODING_PCM_32BIT,
    AudioFormat.ENCODING_PCM_FLOAT,
)

internal class BitPerfectAudioOutputProvider(
    context: Context,
    onStatusChanged: (BitPerfectPlaybackStatus) -> Unit,
) : ForwardingAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(context).build()) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mediaAttributes = PlatformAudioAttributes.Builder()
        .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
        .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val onStatusChanged = onStatusChanged
    private var currentOutputConfig: AudioOutputProvider.OutputConfig? = null
    private var preferredDevice: AudioDeviceInfo? = null
    private var preferredMixerAttributes: Any? = null

    @Synchronized
    fun refresh() {
        applyPreferredMixerAttributes(currentOutputConfig)
    }

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        val requestedBitPerfect = synchronized(this) {
            currentOutputConfig = config
            applyPreferredMixerAttributes(config)
        }
        return try {
            super.getAudioOutput(config)
        } catch (error: AudioOutputProvider.InitializationException) {
            if (!requestedBitPerfect) throw error
            synchronized(this) {
                clearPreferredMixerAttributes()
                onStatusChanged(BitPerfectPlaybackStatus.Failed)
            }
            try {
                super.getAudioOutput(config)
            } catch (fallbackError: AudioOutputProvider.InitializationException) {
                fallbackError.addSuppressed(error)
                throw fallbackError
            }
        }
    }

    @Synchronized
    override fun release() {
        clearPreferredMixerAttributes()
        onStatusChanged(BitPerfectPlaybackStatus.Off)
        super.release()
    }

    /** Returns true only when Android accepted the exact output format for the active USB route. */
    @Synchronized
    private fun applyPreferredMixerAttributes(config: AudioOutputProvider.OutputConfig?): Boolean {
        if (!BitPerfectSettings.isEnabled(appContext)) {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.Off)
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.UnsupportedApi)
            return false
        }
        val manager = audioManager
        if (manager == null) {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.Failed)
            return false
        }
        return applyPreferredMixerAttributesApi34(config, manager)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyPreferredMixerAttributesApi34(
        config: AudioOutputProvider.OutputConfig?,
        manager: AudioManager,
    ): Boolean {
        val usbDevices = runCatching {
            manager.getAudioDevicesForAttributes(mediaAttributes).filter { it.isUsbAudioOutput() }
        }.getOrElse {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.Failed)
            return false
        }
        if (usbDevices.isEmpty()) {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.NoUsbOutput)
            return false
        }
        val supported = usbDevices.mapNotNull { device ->
            runCatching {
                device to manager.getSupportedMixerAttributes(device)
                    .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            }.getOrNull()
        }
        if (supported.none { (_, attributes) -> attributes.isNotEmpty() }) {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.UnsupportedOutput)
            return false
        }
        if (config == null) {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.Available)
            return false
        }

        val match = supported.asSequence().flatMap { (device, attributes) ->
            attributes.asSequence().map { device to it }
        }.firstOrNull { (_, attributes) ->
            matchesBitPerfectMixerFormat(
                outputEncoding = config.encoding,
                outputSampleRate = config.sampleRate,
                outputChannelMask = config.channelMask,
                isOffload = config.isOffload,
                mixerEncoding = attributes.format.encoding,
                mixerSampleRate = attributes.format.sampleRate,
                mixerChannelMask = attributes.format.channelMask,
            )
        }
        if (match == null) {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.UnsupportedFormat)
            return false
        }

        val (device, mixerAttributes) = match
        if (preferredDevice != device || preferredMixerAttributes != mixerAttributes) {
            clearPreferredMixerAttributes()
        }
        val accepted = runCatching {
            manager.setPreferredMixerAttributes(mediaAttributes, device, mixerAttributes)
        }.getOrDefault(false)
        if (!accepted) {
            clearPreferredMixerAttributes()
            onStatusChanged(BitPerfectPlaybackStatus.Failed)
            return false
        }

        preferredDevice = device
        preferredMixerAttributes = mixerAttributes
        onStatusChanged(BitPerfectPlaybackStatus.Active)
        return true
    }

    private fun clearPreferredMixerAttributes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            clearPreferredMixerAttributesApi34()
        } else {
            preferredDevice = null
            preferredMixerAttributes = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearPreferredMixerAttributesApi34() {
        val manager = audioManager
        val device = preferredDevice
        val attributes = preferredMixerAttributes as? AudioMixerAttributes
        if (manager != null && device != null && attributes != null) {
            runCatching { manager.clearPreferredMixerAttributes(mediaAttributes, device) }
        }
        preferredDevice = null
        preferredMixerAttributes = null
    }

    private fun AudioDeviceInfo.isUsbAudioOutput(): Boolean = isSink && type in USB_AUDIO_TYPES

    private companion object {
        val USB_AUDIO_TYPES = setOf(
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
