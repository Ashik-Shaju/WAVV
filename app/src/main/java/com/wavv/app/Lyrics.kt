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

internal data class LyricWord(val timeMs: Long?, val text: String)

internal data class LyricLine(
    val timeMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val backgroundText: String? = null,
    val vocalSide: Int? = null,
    val timestampSequenceValid: Boolean = true,
)

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

internal suspend fun loadLyricsDocument(context: Context, uri: Uri): List<LyricLine> = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
    val text = input.bufferedReader(Charsets.UTF_8).use { reader ->
        buildString {
            val buffer = CharArray(8_192)
            while (length <= MAX_LYRIC_CHARS) {
                val count = reader.read(buffer, 0, minOf(buffer.size, MAX_LYRIC_CHARS + 1 - length))
                if (count <= 0) break
                append(buffer, 0, count)
            }
        }
    }.trimStart('\uFEFF')
    parseLyrics(listOf(text))
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
    val boundedSources = sources.asSequence().take(MAX_LYRIC_SOURCES).map { it.take(MAX_LYRIC_CHARS) }.toList()
    // Embedded tags may contain both ordinary (untimed) lyrics and a synchronized
    // version. Combining them makes one plain-text field disable timed-gap animation.
    val timedSources = boundedSources.filter { LINE_TIMESTAMP.containsMatchIn(it) }
    val text = (timedSources.ifEmpty { boundedSources }).joinToString("\n")
    if (text.isBlank()) return emptyList()
    val offsetMs = OFFSET.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.coerceIn(-MAX_LYRIC_OFFSET_MS, MAX_LYRIC_OFFSET_MS) ?: 0L
    val result = mutableListOf<LyricLine>()
    var malformedTimestampFound = false

    text.lineSequence().take(MAX_LYRIC_LINES).forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || METADATA_TAG.matches(line)) return@forEach
        val timestamps = LINE_TIMESTAMP.findAll(line).toList()
        if (timestamps.isEmpty()) {
            result += lyricLine(timeMs = null, payload = line, offsetMs = offsetMs)
        } else {
            val payload = line.substring(timestamps.last().range.last + 1).trim()
            if (payload.isNotEmpty()) {
                timestamps.forEach timestamp@{ match ->
                    val timeMs = timestampMs(match) ?: run {
                        malformedTimestampFound = true
                        return@timestamp
                    }
                    result += lyricLine(timeMs, payload, offsetMs)
                }
            }
        }
    }
    // Validate each line's word order independently. The rendered timeline is sorted
    // by its timestamps below, so source ordering and overlapping singer lines must
    // not disable otherwise usable synchronized lyrics.
    val timingSequenceValid = !malformedTimestampFound &&
        result.all { lyric ->
            val lineTime = lyric.timeMs
            lyric.words.all { word -> word.timeMs != null && (lineTime == null || word.timeMs >= lineTime) } &&
                lyric.words.mapNotNull { it.timeMs }.zipWithNext().all { (previous, next) -> next >= previous }
        }
    val orderedLines = assignVocalSides(result.sortedWith(compareBy<LyricLine> { it.timeMs == null }.thenBy { it.timeMs }))
    return if (timingSequenceValid) orderedLines else orderedLines.map { it.copy(timestampSequenceValid = false) }
}

private fun lyricLine(timeMs: Long?, payload: String, offsetMs: Long): LyricLine {
    val vocalMatch = VOCAL_MARKER.find(payload)
    val vocalSide = vocalMatch?.groupValues?.getOrNull(1)?.let { marker ->
        when (marker.lowercase()) {
            "v1", "left" -> -1
            "v2", "right" -> 1
            else -> null
        }
    }
    val withoutVocalMarker = if (vocalMatch != null) payload.removeRange(vocalMatch.range).trimStart() else payload
    val backgroundParts = BACKGROUND_VOCALS.findAll(withoutVocalMarker).map { it.groupValues[1].trim() }.filter(String::isNotEmpty).toList()
    val mainText = BACKGROUND_VOCALS.replace(withoutVocalMarker, "").trim().ifBlank { withoutVocalMarker.trim() }
    val words = parseWordTimings(mainText, offsetMs)
    return LyricLine(
        timeMs = timeMs?.let { (it + offsetMs).coerceAtLeast(0L) },
        text = if (words.isEmpty()) mainText else words.joinToString(separator = "") { it.text },
        words = words,
        backgroundText = backgroundParts.joinToString(" ").ifBlank { null },
        vocalSide = vocalSide,
    )
}

private fun parseWordTimings(text: String, offsetMs: Long): List<LyricWord> {
    val markers = WORD_TIMESTAMP.findAll(text).toList()
    if (markers.isEmpty()) return emptyList()
    return buildList {
        markers.forEachIndexed { index, marker ->
            val nextMarker = markers.getOrNull(index + 1)
            val start = marker.range.last + 1
            val end = nextMarker?.range?.first ?: text.length
            val wordText = text.substring(start, end)
            val wordTime = timestampMs(marker)?.let { (it + offsetMs).coerceAtLeast(0L) }
            if (wordText.isNotEmpty()) add(LyricWord(wordTime, wordText))
        }
    }
}

private fun timestampMs(match: MatchResult): Long? {
    val minutes = match.groupValues[1].toLongOrNull() ?: return null
    val seconds = match.groupValues[2].toLongOrNull() ?: return null
    if (seconds !in 0L..59L) return null
    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    return (minutes * 60L + seconds) * 1_000L + fraction
}

private fun assignVocalSides(lines: List<LyricLine>): List<LyricLine> {
    val (timed, untimed) = lines.partition { it.timeMs != null }
    val assigned = timed.groupBy { it.timeMs }.values.flatMap { simultaneous ->
        val unassigned = simultaneous.filter { it.vocalSide == null }
        if (unassigned.size < 2 || unassigned.map { it.text }.distinct().size < 2) {
            simultaneous
        } else {
            var laneIndex = 0
            simultaneous.map { line ->
                if (line.vocalSide != null) line else line.copy(vocalSide = if (laneIndex++ % 2 == 0) -1 else 1)
            }
        }
    }
    return (assigned + untimed).sortedWith(compareBy<LyricLine> { it.timeMs == null }.thenBy { it.timeMs })
}

internal fun decodeUsltFrame(data: ByteArray): String? {
    if (data.size <= 4) return null
    val encoding = data[0].toInt() and 0xff
    val charset = charsetFor(encoding, data, 4)
    val textStart = findTextTerminator(data, 4, encoding)?.let { it + terminatorSize(encoding) } ?: return null
    return decodeText(data, textStart, data.size, charset).trim('\u0000', '\uFEFF', '\r', '\n', ' ').ifBlank { null }
}

private data class SynchronizedLyricEntry(val timeMs: Long, val text: String)

private fun decodeSyltFrame(data: ByteArray): List<String> {
    if (data.size <= 6) return emptyList()
    val encoding = data[0].toInt() and 0xff
    val charset = charsetFor(encoding, data, 6)
    val timestampFormat = data[4].toInt() and 0xff
    if (timestampFormat !in 1..2) return emptyList()
    var offset = findTextTerminator(data, 6, encoding)?.plus(terminatorSize(encoding)) ?: return emptyList()
    val entries = buildList {
        while (offset < data.size && size < MAX_LYRIC_LINES * 4) {
            val textEnd = findTextTerminator(data, offset, encoding) ?: break
            val timestampStart = textEnd + terminatorSize(encoding)
            if (timestampStart + 4 > data.size) break
            val text = decodeText(data, offset, textEnd, charset).trim('\uFEFF', '\u0000')
            val rawTimestamp = ((data[timestampStart].toLong() and 0xff) shl 24) or
                ((data[timestampStart + 1].toLong() and 0xff) shl 16) or
                ((data[timestampStart + 2].toLong() and 0xff) shl 8) or
                (data[timestampStart + 3].toLong() and 0xff)
            val timeMs = if (timestampFormat == 1) rawTimestamp * 1_000L / 75L else rawTimestamp
            if (text.isNotEmpty()) add(SynchronizedLyricEntry(timeMs, text))
            offset = timestampStart + 4
        }
    }
    return synchronizedEntriesAsLyrics(entries)
}

/** Keep word-timed SYLT entries together as a lyric line so the view can highlight each segment. */
private fun synchronizedEntriesAsLyrics(entries: List<SynchronizedLyricEntry>): List<String> {
    val lines = mutableListOf<String>()
    val lineContent = StringBuilder()
    var lineStartMs: Long? = null
    var previousEntry: SynchronizedLyricEntry? = null

    fun flushLine() {
        val start = lineStartMs ?: return
        if (lineContent.isNotBlank()) {
            lines += "${formatLyricTimestamp(start, '[', ']')}$lineContent"
        }
        lineContent.clear()
        lineStartMs = null
    }

    fun appendSegment(entry: SynchronizedLyricEntry, segment: String) {
        if (segment.isBlank()) return
        val previous = previousEntry
        val gapMs = previous?.let { entry.timeMs - it.timeMs }
        val segmentIsPhrase = segment.trim().split(WHITESPACE).size > 1
        val previousEndsSentence = lineContent.trimEnd().lastOrNull() in setOf('.', '!', '?')
        if (lineContent.isNotEmpty() && (segmentIsPhrase || previousEndsSentence || gapMs != null && gapMs > SYLT_LINE_GAP_MS)) {
            flushLine()
        }
        if (lineStartMs == null) lineStartMs = entry.timeMs
        lineContent.append(formatLyricTimestamp(entry.timeMs, '<', '>')).append(segment)
        previousEntry = entry
        if (segmentIsPhrase) flushLine()
    }

    entries.forEach { entry ->
        val normalized = entry.text.replace("\r\n", "\n").replace('\r', '\n')
        val segments = normalized.split('\n')
        segments.forEachIndexed { index, segment ->
            if (segment.isNotBlank()) appendSegment(entry, segment)
            if (index < segments.lastIndex) flushLine()
        }
        if (normalized.endsWith('\n')) flushLine()
    }
    flushLine()
    return lines.take(MAX_LYRIC_LINES)
}

private fun formatLyricTimestamp(timeMs: Long, open: Char, close: Char): String {
    val safeTimeMs = timeMs.coerceAtLeast(0L)
    val minutes = safeTimeMs / 60_000L
    val seconds = ((safeTimeMs / 1_000L) % 60L).toString().padStart(2, '0')
    val millis = (safeTimeMs % 1_000L).toString().padStart(3, '0')
    return "$open$minutes:$seconds.$millis$close"
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

private val LINE_TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val WORD_TIMESTAMP = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
private val OFFSET = Regex("""(?im)^\[offset:\s*([+-]?\d+)\s*]""")
private val METADATA_TAG = Regex("""(?i)^\[(?:ar|al|ti|au|by|re|ve|length|offset):.*]$""")
private val VOCAL_MARKER = Regex("""(?i)^\{(v1|v2|left|right)\}\s*""")
private val BACKGROUND_VOCALS = Regex("""\(([^()]+)\)""")
private val WHITESPACE = Regex("\\s+")
private val LYRICS_TAGS = setOf("LYRIC", "LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")
private const val MAX_LYRIC_SOURCES = 32
private const val MAX_LYRIC_CHARS = 128_000
private const val MAX_LYRIC_LINES = 5_000
private const val MAX_LYRIC_OFFSET_MS = 86_400_000L
private const val SYLT_LINE_GAP_MS = 2_200L
