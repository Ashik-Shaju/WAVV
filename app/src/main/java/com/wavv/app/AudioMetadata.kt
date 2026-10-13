package com.wavv.app

internal fun String?.toMetadataNumber(): Int? = this?.substringBefore('/')?.trim()?.toIntOrNull()

internal fun String?.toMetadataBoolean(): Boolean? = when (this?.trim()?.lowercase()) {
    "1", "true", "yes" -> true
    "0", "false", "no" -> false
    else -> null
}

internal fun String?.recordingYear(): Int? = this
    ?.let { value -> RECORDING_YEAR.find(value) }
    ?.groupValues
    ?.getOrNull(1)
    ?.toIntOrNull()
    ?.takeIf { it in 1800..2100 }

private val RECORDING_YEAR = Regex("(?<!\\d)((?:18|19|20|21)\\d{2})(?!\\d)")
