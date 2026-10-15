package com.wavv.app

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object CrossfadeSettings {
    const val MAX_SECONDS = 12
    private const val DEFAULT_SECONDS = 0
    private const val PREFERENCES_NAME = "playback_preferences"
    private const val DURATION_SECONDS_KEY = "crossfade_duration_seconds"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun durationSeconds(context: Context): Int =
        preferences(context)
            .getInt(DURATION_SECONDS_KEY, DEFAULT_SECONDS)
            .let(::normalizeSeconds)

    fun setDurationSeconds(context: Context, seconds: Int) {
        preferences(context)
            .edit()
            .putInt(DURATION_SECONDS_KEY, normalizeSeconds(seconds))
            .apply()
    }

    fun preferenceKey(): String = DURATION_SECONDS_KEY

    fun normalizeSeconds(seconds: Int): Int = seconds.coerceIn(0, MAX_SECONDS)
}

internal data class CrossfadeGains(val outgoing: Float, val incoming: Float)

internal fun isCrossfadeEligible(
    currentTrackId: String?,
    nextTrackId: String?,
    repeatCurrentTrack: Boolean,
    currentNeedsUHQ: Boolean,
    nextNeedsUHQ: Boolean,
    bitPerfectMode: Boolean = false,
): Boolean = currentTrackId != null && nextTrackId != null &&
    currentTrackId != nextTrackId && !repeatCurrentTrack && !currentNeedsUHQ && !nextNeedsUHQ && !bitPerfectMode

internal fun shouldCancelCrossfadeOnTimelineChange(playerHandedOff: Boolean): Boolean = !playerHandedOff

internal fun equalPowerCrossfadeGains(progress: Float): CrossfadeGains {
    val angle = progress.coerceIn(0f, 1f) * (PI.toFloat() / 2f)
    return CrossfadeGains(cos(angle), sin(angle))
}

internal fun effectiveCrossfadeDurationMs(
    requestedMs: Long,
    outgoingDurationMs: Long,
    incomingDurationMs: Long,
): Long = if (requestedMs <= 0L || outgoingDurationMs <= 0L || incomingDurationMs <= 0L) {
    0L
} else {
    minOf(requestedMs, outgoingDurationMs / 2L, incomingDurationMs / 2L)
}
