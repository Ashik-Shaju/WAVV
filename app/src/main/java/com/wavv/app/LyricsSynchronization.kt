package com.wavv.app

/** Show a timed wait only when the lyric-event gap is at least this long. */
internal const val WAITING_GAP_THRESHOLD_MS = 10_000L
private const val INTRO_LYRIC_DELAY_THRESHOLD_MS = 1_500L
private const val WAITING_LEAD_IN_MS = 5_000L

internal data class LyricsWaitingWindow(
    val startsAtMs: Long,
    val endsAtMs: Long,
) {
    fun contains(positionMs: Long): Boolean = positionMs >= startsAtMs && positionMs < endsAtMs

    /** Progress reaches 1 exactly at the next lyric timestamp. */
    fun progressAt(positionMs: Long): Float =
        ((positionMs - startsAtMs).toFloat() / (endsAtMs - startsAtMs).coerceAtLeast(1L))
            .coerceIn(0f, 1f)
}

/** Validate once when a lyric track changes; timestamp-poor tracks use the normal renderer. */
internal fun hasUsableLyricsTimeline(lines: List<LyricLine>): Boolean {
    if (lines.isEmpty()) return false
    var previousLineTimeMs = -1L
    for (line in lines) {
        val timeMs = line.timeMs ?: return false
        if (!line.timestampSequenceValid || timeMs < previousLineTimeMs) return false
        previousLineTimeMs = timeMs
        var previousWordTimeMs = timeMs
        for (word in line.words) {
            val wordTimeMs = word.timeMs ?: return false
            if (wordTimeMs < previousWordTimeMs) return false
            previousWordTimeMs = wordTimeMs
        }
    }
    return true
}

/** Returns the playback-time window in which the dots should fill toward the next lyric. */
internal fun lyricsWaitingWindow(
    lines: List<LyricLine>,
    activeLyricIndex: Int,
    durationMs: Long,
    hasUsableTimeline: Boolean,
): LyricsWaitingWindow? {
    if (!hasUsableTimeline || lines.isEmpty()) return null

    val firstTimeMs = lines.first().timeMs ?: return null
    if (activeLyricIndex < 0) {
        if (firstTimeMs <= INTRO_LYRIC_DELAY_THRESHOLD_MS) return null
        if (durationMs > 0L && firstTimeMs > durationMs) return null
        return LyricsWaitingWindow(startsAtMs = 0L, endsAtMs = firstTimeMs)
    }

    val current = lines.getOrNull(activeLyricIndex) ?: return null
    val next = lines.getOrNull(activeLyricIndex + 1) ?: return null
    val currentStartMs = current.timeMs ?: return null
    val nextStartMs = next.timeMs ?: return null
    var simultaneousStartIndex = activeLyricIndex
    while (simultaneousStartIndex > 0 && lines[simultaneousStartIndex - 1].timeMs == currentStartMs) {
        simultaneousStartIndex--
    }
    var lastLyricEventMs = currentStartMs
    for (index in simultaneousStartIndex..activeLyricIndex) {
        val lastWordTimeMs = lines[index].words.lastOrNull()?.timeMs ?: continue
        lastLyricEventMs = maxOf(lastLyricEventMs, lastWordTimeMs)
    }

    val gapMs = nextStartMs - lastLyricEventMs
    if (gapMs < WAITING_GAP_THRESHOLD_MS) return null
    if (durationMs > 0L && nextStartMs > durationMs) return null

    val startsAtMs = maxOf(currentStartMs, lastLyricEventMs + WAITING_LEAD_IN_MS)
    if (startsAtMs >= nextStartMs) return null
    return LyricsWaitingWindow(startsAtMs = startsAtMs, endsAtMs = nextStartMs)
}

internal fun isLyricsWaiting(
    lines: List<LyricLine>,
    activeLyricIndex: Int,
    positionMs: Long,
    durationMs: Long,
    hasUsableTimeline: Boolean,
): Boolean = positionMs >= 0L && lyricsWaitingWindow(
    lines = lines,
    activeLyricIndex = activeLyricIndex,
    durationMs = durationMs,
    hasUsableTimeline = hasUsableTimeline,
).let { it?.contains(positionMs) == true }
