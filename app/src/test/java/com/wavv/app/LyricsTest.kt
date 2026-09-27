package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTest {
    @Test
    fun parseLyrics_expandsMultipleLrcTimestampsAndOffset() {
        val lines = parseLyrics(
            listOf("[offset:+500]\n[ar:Artist]\n[00:01.25][00:03.500]First\n[00:05.0]Second"),
        )

        assertEquals(
            listOf(
                LyricLine(1_750L, "First"),
                LyricLine(4_000L, "First"),
                LyricLine(5_500L, "Second"),
            ),
            lines,
        )
    }

    @Test
    fun parseLyrics_keepsPlainEmbeddedLyricsAsUntimedLines() {
        assertEquals(
            listOf(LyricLine(null, "First line"), LyricLine(null, "Second line")),
            parseLyrics(listOf("First line\nSecond line")),
        )
    }

    @Test
    fun decodeUsltFrame_readsUtf8LyricsAfterEmptyDescription() {
        val data = byteArrayOf(3, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0) + "Words".toByteArray()

        assertEquals("Words", decodeUsltFrame(data))
    }

    @Test
    fun decodeUsltFrame_usesUtf16ByteOrderFromDescriptionBom() {
        val data = byteArrayOf(1, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), -1, -2, 0, 0) +
            "Words".toByteArray(Charsets.UTF_16LE)

        assertEquals("Words", decodeUsltFrame(data))
    }
}
