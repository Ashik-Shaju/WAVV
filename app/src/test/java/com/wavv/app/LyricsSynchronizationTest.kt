package com.wavv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSynchronizationTest {
    @Test
    fun noGapAndShortGapDoNotShowWaiting() {
        assertFalse(isWaiting(10_500L, 10_000L, 10_800L))
        assertFalse(isWaiting(10_000L, 10_000L, 10_000L))
    }

    @Test
    fun mediumGapShowsOnlyInItsWaitingPortion() {
        assertFalse(isWaiting(10_499L, 10_000L, 12_000L))
        assertTrue(isWaiting(10_500L, 10_000L, 12_000L))
        assertTrue(isWaiting(11_999L, 10_000L, 12_000L))
        assertFalse(isWaiting(12_000L, 10_000L, 12_000L))
    }

    @Test
    fun longGapAndSeekIntoItShowWaitingImmediately() {
        assertFalse(isWaiting(21_499L, 20_000L, 35_000L))
        assertTrue(isWaiting(21_500L, 20_000L, 35_000L))
        assertTrue(isWaiting(28_000L, 20_000L, 35_000L))
        assertFalse(isWaiting(35_000L, 20_000L, 35_000L))
    }

    @Test
    fun delayedFirstLyricShowsWaitingFromSongStart() {
        val lines = listOf(LyricLine(18_000L, "First"))
        assertTrue(isLyricsWaiting(lines, activeLyricIndex = -1, positionMs = 0L, durationMs = 60_000L, hasUsableTimeline = true))
        assertTrue(isLyricsWaiting(lines, activeLyricIndex = -1, positionMs = 17_999L, durationMs = 60_000L, hasUsableTimeline = true))
        assertFalse(isLyricsWaiting(lines, activeLyricIndex = 0, positionMs = 18_000L, durationMs = 60_000L, hasUsableTimeline = true))
        assertFalse(isLyricsWaiting(lines, activeLyricIndex = -1, positionMs = 0L, durationMs = 10_000L, hasUsableTimeline = true))
    }

    @Test
    fun untimedMalformedAndOutOfOrderLyricsNeverShowWaiting() {
        val untimed = listOf(LyricLine(null, "Plain line"), LyricLine(20_000L, "Timed"))
        assertFalse(isWaiting(19_000L, untimed))

        val malformed = parseLyrics(listOf("[00:99.0]Bad\n[00:20.0]Next"))
        assertFalse(isWaiting(19_000L, malformed))

        val outOfOrder = parseLyrics(listOf("[00:20.0]Later\n[00:10.0]Earlier"))
        assertFalse(isWaiting(19_000L, outOfOrder))
    }

    @Test
    fun duplicateTimestampsAndMultipleGapsStayWellDefined() {
        val duplicates = listOf(
            LyricLine(10_000L, "First voice"),
            LyricLine(10_000L, "Second voice"),
            LyricLine(20_000L, "Next"),
        )
        assertFalse(isWaiting(11_499L, duplicates))
        assertTrue(isWaiting(11_500L, duplicates))

        val multipleGaps = listOf(
            LyricLine(10_000L, "First"),
            LyricLine(12_000L, "Second"),
            LyricLine(20_000L, "Third"),
        )
        assertTrue(isWaiting(10_500L, multipleGaps))
        assertFalse(isWaiting(12_000L, multipleGaps))
        assertTrue(isWaiting(18_500L, multipleGaps))
    }

    @Test
    fun finalWordTimestampDelaysWaitingUntilItsEventHasPassed() {
        val lines = listOf(
            LyricLine(20_000L, "Last word", words = listOf(LyricWord(30_000L, "word"))),
            LyricLine(35_000L, "Next verse"),
        )
        assertFalse(isWaiting(31_499L, lines))
        assertTrue(isWaiting(31_500L, lines))
    }

    private fun isWaiting(positionMs: Long, vararg timestamps: Long): Boolean =
        isWaiting(positionMs, timestamps.map { LyricLine(it, "line") })

    private fun isWaiting(positionMs: Long, lines: List<LyricLine>): Boolean {
        val activeIndex = lines.indexOfLast { it.timeMs?.let { time -> time <= positionMs } == true }
        return isLyricsWaiting(
            lines = lines,
            activeLyricIndex = activeIndex,
            positionMs = positionMs,
            durationMs = 60_000L,
            hasUsableTimeline = hasUsableLyricsTimeline(lines),
        )
    }
}
