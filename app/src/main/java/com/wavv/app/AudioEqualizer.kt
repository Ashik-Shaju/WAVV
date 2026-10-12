package com.wavv.app

import android.content.Context
import java.util.Locale
import java.util.UUID
import kotlin.math.ln

internal const val AUDIO_EQUALIZER_PREFERENCE_PREFIX = "equalizer_"

internal data class EqualizerProfile(
    val name: String,
    val gainsDb: List<Float>,
)

internal data class SavedEqualizerProfile(
    val id: String,
    val profile: EqualizerProfile,
)

internal fun hasEqualizerProfileName(profiles: List<SavedEqualizerProfile>, candidate: String): Boolean {
    val normalized = candidate.trim().take(32)
    return normalized.isNotEmpty() && profiles.any { it.profile.name.equals(normalized, ignoreCase = true) }
}

internal object EqualizerProfiles {
    const val MIN_GAIN_DB = -6f
    const val MAX_GAIN_DB = 6f
    val frequenciesHz = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

    val presets = listOf(
        EqualizerProfile("Flat", List(frequenciesHz.size) { 0f }),
        EqualizerProfile("Bass", listOf(3f, 3f, 2.5f, 1.5f, .5f, 0f, 0f, 0f, 0f, 0f)),
        EqualizerProfile("Vocal", listOf(-1.5f, -1f, -.5f, 0f, 1.5f, 2.5f, 2f, 1f, 0f, -.5f)),
        EqualizerProfile("Treble", listOf(0f, 0f, 0f, 0f, 0f, .5f, 1.5f, 2.5f, 3f, 3f)),
    )

    fun gainAtFrequency(gainsDb: List<Float>, frequencyHz: Int): Float {
        require(gainsDb.size == frequenciesHz.size)
        if (frequencyHz <= frequenciesHz.first()) return gainsDb.first()
        if (frequencyHz >= frequenciesHz.last()) return gainsDb.last()
        val upper = frequenciesHz.indexOfFirst { frequencyHz <= it }
        val lowerFrequency = frequenciesHz[upper - 1].toDouble()
        val upperFrequency = frequenciesHz[upper].toDouble()
        val fraction = (ln(frequencyHz.toDouble()) - ln(lowerFrequency)) /
            (ln(upperFrequency) - ln(lowerFrequency))
        return (gainsDb[upper - 1] + (gainsDb[upper] - gainsDb[upper - 1]) * fraction.toFloat())
            .coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }

    fun encodeGains(gainsDb: List<Float>): String? {
        if (gainsDb.size != frequenciesHz.size || gainsDb.any { !it.isFinite() }) return null
        return gainsDb.joinToString(",") { it.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB).toString() }
    }

    fun decodeGains(encoded: String?): List<Float>? {
        val values = encoded?.split(',')?.map(String::toFloatOrNull) ?: return null
        if (values.size != frequenciesHz.size || values.any { it == null || !it.isFinite() }) return null
        return values.filterNotNull().map { it.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) }
    }

    fun formatFrequency(frequencyHz: Int): String =
        if (frequencyHz < 1_000) "${frequencyHz}Hz" else "${frequencyHz / 1_000}kHz"

    fun formatGain(gainDb: Float): String = String.format(Locale.ROOT, "%+.1f dB", gainDb)
}

internal object AudioEqualizerSettings {
    private const val ENABLED = "${AUDIO_EQUALIZER_PREFERENCE_PREFIX}enabled"
    private const val ACTIVE_NAME = "${AUDIO_EQUALIZER_PREFERENCE_PREFIX}active_name"
    private const val ACTIVE_GAINS = "${AUDIO_EQUALIZER_PREFERENCE_PREFIX}active_gains"
    private const val PROFILE_IDS = "${AUDIO_EQUALIZER_PREFERENCE_PREFIX}profile_ids"
    private const val PROFILE_NAME_PREFIX = "${AUDIO_EQUALIZER_PREFERENCE_PREFIX}profile_name_"
    private const val PROFILE_GAINS_PREFIX = "${AUDIO_EQUALIZER_PREFERENCE_PREFIX}profile_gains_"

    fun isEnabled(context: Context): Boolean =
        UHQPlaybackPreference.preferences(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        UHQPlaybackPreference.preferences(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun activeProfile(context: Context): EqualizerProfile {
        val preferences = UHQPlaybackPreference.preferences(context)
        val name = preferences.getString(ACTIVE_NAME, null)?.trim()?.takeIf(String::isNotEmpty) ?: "Flat"
        val gains = EqualizerProfiles.decodeGains(preferences.getString(ACTIVE_GAINS, null))
            ?: EqualizerProfiles.presets.first().gainsDb
        return EqualizerProfile(name.take(32), gains)
    }

    fun setActiveProfile(context: Context, profile: EqualizerProfile) {
        val encoded = EqualizerProfiles.encodeGains(profile.gainsDb) ?: return
        UHQPlaybackPreference.preferences(context).edit()
            .putString(ACTIVE_NAME, profile.name.trim().take(32).ifEmpty { "Custom" })
            .putString(ACTIVE_GAINS, encoded)
            .apply()
    }

    fun savedProfiles(context: Context): List<SavedEqualizerProfile> {
        val preferences = UHQPlaybackPreference.preferences(context)
        return preferences.getStringSet(PROFILE_IDS, emptySet()).orEmpty().mapNotNull { id ->
            val name = preferences.getString(PROFILE_NAME_PREFIX + id, null)?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val gains = EqualizerProfiles.decodeGains(preferences.getString(PROFILE_GAINS_PREFIX + id, null))
                ?: return@mapNotNull null
            SavedEqualizerProfile(id, EqualizerProfile(name.take(32), gains))
        }.sortedBy { it.profile.name.lowercase(Locale.ROOT) }
    }

    fun saveProfile(context: Context, profile: EqualizerProfile): List<SavedEqualizerProfile> {
        val name = profile.name.trim().take(32)
        val encoded = EqualizerProfiles.encodeGains(profile.gainsDb) ?: return savedProfiles(context)
        if (name.isEmpty()) return savedProfiles(context)
        val preferences = UHQPlaybackPreference.preferences(context)
        val existing = savedProfiles(context).firstOrNull { it.profile.name.equals(name, ignoreCase = true) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val ids = preferences.getStringSet(PROFILE_IDS, emptySet()).orEmpty().toMutableSet().apply { add(id) }
        preferences.edit()
            .putStringSet(PROFILE_IDS, ids)
            .putString(PROFILE_NAME_PREFIX + id, name)
            .putString(PROFILE_GAINS_PREFIX + id, encoded)
            .apply()
        return savedProfiles(context)
    }
}
