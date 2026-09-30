package com.wavv.app

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

internal data class LyricLine(val timeMs: Long?, val text: String)

@OptIn(markerClass = [UnstableApi::class])
internal suspend fun loadEmbeddedLyrics(context: Context, uri: String): List<LyricLine> = withContext(Dispatchers.IO) {
    val retriever = MetadataRetriever.Builder(
        context.applicationContext,
        MediaItem.fromUri(Uri.parse(uri)),
    ).build()
    try {
        val trackGroups = runInterruptible { retriever.retrieveTrackGroups().get() }
        buildList {
            repeat(trackGroups.length) { groupIndex ->
                val group = trackGroups[groupIndex]
                repeat(group.length) { trackIndex ->
                    group.getFormat(trackIndex).metadata?.let { metadata ->
                        addAll(extractLyrics(metadata))
                    }
                }
            }
        }.let(::parseLyrics)
    } finally {
        retriever.close()
    }
}

@OptIn(markerClass = [UnstableApi::class])
private fun extractLyrics(metadata: Metadata): List<String> = buildList {
    repeat(metadata.length()) { index ->
        when (val entry = metadata[index]) {
            is BinaryFrame -> when (entry.id) {
                "USLT" -> decodeUsltFrame(entry.data)?.let(::add)
                "SYLT" -> addAll(decodeSyltFrame(entry.data))
            }
            is VorbisComment -> if (isLyricsTag(entry.key)) add(entry.value)
            is TextInformationFrame -> if (entry.id == "TXXX" && isLyricsTag(entry.description)) {
                addAll(entry.values)
            }
            is CommentFrame -> if (isLyricsTag(entry.description)) add(entry.text)
        }
    }
}

internal fun parseLyrics(sources: List<String>): List<LyricLine> {
    val text = sources.asSequence().take(MAX_LYRIC_SOURCES).joinToString("\n") { it.take(MAX_LYRIC_CHARS) }
    if (text.isBlank()) return emptyList()
    val offsetMs = OFFSET.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.coerceIn(-MAX_LYRIC_OFFSET_MS, MAX_LYRIC_OFFSET_MS) ?: 0L
    val result = mutableListOf<LyricLine>()

    text.lineSequence().take(MAX_LYRIC_LINES).forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || METADATA_TAG.matches(line)) return@forEach
        val timestamps = TIMESTAMP.findAll(line).toList()
        if (timestamps.isEmpty()) {
            result += LyricLine(null, line)
        } else {
            val lyricText = line.substring(timestamps.last().range.last + 1).trim()
            if (lyricText.isNotEmpty()) {
                timestamps.forEach timestamp@{ match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: return@timestamp
                    val seconds = match.groupValues[2].toLongOrNull() ?: return@timestamp
                    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    val timeMs = ((minutes * 60L + seconds) * 1_000L + fraction + offsetMs).coerceAtLeast(0L)
                    result += LyricLine(timeMs, lyricText)
                }
            }
        }
    }
    return result.sortedWith(compareBy<LyricLine> { it.timeMs == null }.thenBy { it.timeMs })
}

internal fun decodeUsltFrame(data: ByteArray): String? {
    if (data.size <= 4) return null
    val encoding = data[0].toInt() and 0xff
    val charset = charsetFor(encoding, data, 4)
    val textStart = findTextTerminator(data, 4, encoding)?.let { it + terminatorSize(encoding) } ?: return null
    return decodeText(data, textStart, data.size, charset).trim('\u0000', '\uFEFF', '\r', '\n', ' ').ifBlank { null }
}

private fun decodeSyltFrame(data: ByteArray): List<String> {
    if (data.size <= 6) return emptyList()
    val encoding = data[0].toInt() and 0xff
    val charset = charsetFor(encoding, data, 6)
    val timestampFormat = data[4].toInt() and 0xff
    if (timestampFormat !in 1..2) return emptyList()
    var offset = findTextTerminator(data, 6, encoding)?.plus(terminatorSize(encoding)) ?: return emptyList()
    return buildList {
        while (offset < data.size && size < MAX_LYRIC_LINES) {
            val textEnd = findTextTerminator(data, offset, encoding) ?: break
            val timestampStart = textEnd + terminatorSize(encoding)
            if (timestampStart + 4 > data.size) break
            val text = decodeText(data, offset, textEnd, charset).trim('\uFEFF')
            val rawTimestamp = ((data[timestampStart].toLong() and 0xff) shl 24) or
                ((data[timestampStart + 1].toLong() and 0xff) shl 16) or
                ((data[timestampStart + 2].toLong() and 0xff) shl 8) or
                (data[timestampStart + 3].toLong() and 0xff)
            val timeMs = if (timestampFormat == 1) rawTimestamp * 1_000L / 75L else rawTimestamp
            if (text.isNotEmpty()) add("[${timeMs / 60_000}:${((timeMs / 1_000) % 60).toString().padStart(2, '0')}.${(timeMs % 1_000).toString().padStart(3, '0')}]$text")
            offset = timestampStart + 4
        }
    }
}

private fun findTextTerminator(data: ByteArray, start: Int, encoding: Int): Int? {
    if (encoding == 1 || encoding == 2) {
        var index = start
        while (index + 1 < data.size) {
            if (data[index] == 0.toByte() && data[index + 1] == 0.toByte()) return index
            index += 2
        }
    } else {
        for (index in start until data.size) if (data[index] == 0.toByte()) return index
    }
    return null
}

private fun decodeText(data: ByteArray, start: Int, end: Int, charset: Charset): String =
    String(data, start, (end - start).coerceAtLeast(0), charset)

private fun charsetFor(encoding: Int, data: ByteArray, start: Int): Charset = when (encoding) {
    0 -> Charsets.ISO_8859_1
    1 -> when {
        start + 1 < data.size && data[start] == 0xff.toByte() && data[start + 1] == 0xfe.toByte() -> Charset.forName("UTF-16LE")
        else -> Charset.forName("UTF-16BE")
    }
    2 -> Charset.forName("UTF-16BE")
    3 -> Charsets.UTF_8
    else -> Charsets.UTF_8
}

private fun terminatorSize(encoding: Int) = if (encoding == 1 || encoding == 2) 2 else 1

private fun isLyricsTag(value: String?): Boolean = value?.uppercase()?.filter(Char::isLetterOrDigit)?.let(LYRICS_TAGS::contains) == true

private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val OFFSET = Regex("""(?im)^\[offset:\s*([+-]?\d+)\s*]""")
private val METADATA_TAG = Regex("""(?i)^\[(?:ar|al|ti|au|by|re|ve|length|offset):.*]$""")
private val LYRICS_TAGS = setOf("LYRIC", "LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")
private const val MAX_LYRIC_SOURCES = 32
private const val MAX_LYRIC_CHARS = 128_000
private const val MAX_LYRIC_LINES = 5_000
private const val MAX_LYRIC_OFFSET_MS = 86_400_000L
