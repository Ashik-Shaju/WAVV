package com.wavv.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class UhqPlaybackStatus { Disabled, Waiting, Preparing, Enhanced, Unsupported, Failed, Bypassed }

internal enum class EqualizerPlaybackStatus { Disabled, Waiting, Applied, Unavailable, Bypassed }

internal data class PlaybackEffectState(
    val uhqTrackId: Long? = null,
    val uhq: UhqPlaybackStatus = UhqPlaybackStatus.Disabled,
    val uhqProgressPercent: Int? = null,
    val uhqProgressStage: String? = null,
    val equalizer: EqualizerPlaybackStatus = EqualizerPlaybackStatus.Disabled,
    val gain: EqualizerPlaybackStatus = EqualizerPlaybackStatus.Disabled,
    val bitPerfect: BitPerfectPlaybackStatus = BitPerfectPlaybackStatus.Off,
)

internal object PlaybackEffectStatus {
    private val mutableState = MutableStateFlow(PlaybackEffectState())
    val state: StateFlow<PlaybackEffectState> = mutableState.asStateFlow()

    fun updateUHQ(
        trackId: Long?,
        status: UhqPlaybackStatus,
        progressPercent: Int? = null,
        progressStage: String? = null,
    ) {
        mutableState.update {
            it.copy(
                uhqTrackId = trackId,
                uhq = status,
                uhqProgressPercent = progressPercent?.coerceIn(0, 99),
                uhqProgressStage = progressStage,
            )
        }
    }

    fun updateEqualizer(status: EqualizerPlaybackStatus) {
        mutableState.update { it.copy(equalizer = status) }
    }

    fun updateGain(status: EqualizerPlaybackStatus) {
        mutableState.update { it.copy(gain = status) }
    }

    fun updateBitPerfect(status: BitPerfectPlaybackStatus) {
        mutableState.update { it.copy(bitPerfect = status) }
    }

    fun reset() {
        mutableState.value = PlaybackEffectState()
    }
}

internal fun uhqSettingLabel(enabled: Boolean, activeTrackId: Long?, state: PlaybackEffectState): String = when {
    !enabled -> "Off"
    state.uhq == UhqPlaybackStatus.Bypassed -> "Paused by bit-perfect mode"
    activeTrackId == null || state.uhqTrackId != activeTrackId -> "On"
    else -> when (state.uhq) {
        UhqPlaybackStatus.Disabled -> "On"
        UhqPlaybackStatus.Waiting -> "On"
        UhqPlaybackStatus.Preparing -> state.uhqProgressPercent?.let { percent ->
            "${state.uhqProgressStage ?: "Enhancing"} · $percent%"
        } ?: "Preparing"
        UhqPlaybackStatus.Enhanced -> "Enhanced"
        UhqPlaybackStatus.Unsupported -> "Original"
        UhqPlaybackStatus.Failed -> "Failed · original"
        UhqPlaybackStatus.Bypassed -> "Paused by bit-perfect mode"
    }
}

internal fun equalizerSettingLabel(
    enabled: Boolean,
    profileName: String,
    status: EqualizerPlaybackStatus,
    gainStatus: EqualizerPlaybackStatus = EqualizerPlaybackStatus.Disabled,
    gainDb: Float = 0f,
): String = when {
    !enabled -> "Off"
    status == EqualizerPlaybackStatus.Bypassed -> "$profileName · Paused by bit-perfect mode"
    status == EqualizerPlaybackStatus.Unavailable -> "Unavailable"
    status == EqualizerPlaybackStatus.Waiting || status == EqualizerPlaybackStatus.Disabled -> "Waiting for playback"
    gainDb != 0f && gainStatus == EqualizerPlaybackStatus.Unavailable -> "$profileName · Gain unavailable"
    gainDb != 0f && gainStatus != EqualizerPlaybackStatus.Applied -> "Waiting for playback"
    else -> profileName
}

internal fun bitPerfectSettingLabel(
    enabled: Boolean,
    status: BitPerfectPlaybackStatus,
): String = if (!enabled) {
    if (status == BitPerfectPlaybackStatus.UnsupportedApi) "Unavailable" else "Off"
} else {
    when (status) {
        BitPerfectPlaybackStatus.Off -> "Waiting for playback"
        BitPerfectPlaybackStatus.Available -> "Ready for USB audio"
        BitPerfectPlaybackStatus.Active -> "Active"
        BitPerfectPlaybackStatus.UnsupportedApi -> "Android 14+ required"
        BitPerfectPlaybackStatus.NoUsbOutput -> "Waiting for USB DAC"
        BitPerfectPlaybackStatus.UnsupportedOutput -> "Output unsupported"
        BitPerfectPlaybackStatus.UnsupportedFormat -> "Format unsupported"
        BitPerfectPlaybackStatus.Failed -> "Could not enable"
    }
}
