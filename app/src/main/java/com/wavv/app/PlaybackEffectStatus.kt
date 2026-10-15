package com.wavv.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class UhqPlaybackStatus { Disabled, Waiting, Preparing, Enhanced, Unsupported, Failed }

internal enum class EqualizerPlaybackStatus { Disabled, Waiting, Applied, Unavailable }

internal data class PlaybackEffectState(
    val uhqTrackId: Long? = null,
    val uhq: UhqPlaybackStatus = UhqPlaybackStatus.Disabled,
    val uhqProgressPercent: Int? = null,
    val uhqProgressStage: String? = null,
    val equalizer: EqualizerPlaybackStatus = EqualizerPlaybackStatus.Disabled,
    val gain: EqualizerPlaybackStatus = EqualizerPlaybackStatus.Disabled,
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

    fun reset() {
        mutableState.value = PlaybackEffectState()
    }
}

internal fun uhqSettingLabel(enabled: Boolean, activeTrackId: Long?, state: PlaybackEffectState): String = when {
    !enabled -> "Off"
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
    status == EqualizerPlaybackStatus.Unavailable -> "Unavailable"
    status == EqualizerPlaybackStatus.Waiting || status == EqualizerPlaybackStatus.Disabled -> "Waiting for playback"
    gainDb != 0f && gainStatus == EqualizerPlaybackStatus.Unavailable -> "$profileName · Gain unavailable"
    gainDb != 0f && gainStatus != EqualizerPlaybackStatus.Applied -> "Waiting for playback"
    else -> profileName
}
