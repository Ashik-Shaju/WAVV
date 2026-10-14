package com.wavv.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import android.util.Xml
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import java.io.StringReader
import java.io.File
import java.text.Normalizer
import java.nio.charset.Charset
import java.util.ArrayDeque
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal data class LyricWord(val timeMs: Long?, val text: String)

internal data class LyricVocalCue(
    val timeMs: Long?,
    val endTimeMs: Long? = null,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

internal data class LyricLine(
    val timeMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val backgroundText: String? = null,
    val backgroundWords: List<LyricWord> = emptyList(),
    val backgroundTimeMs: Long? = null,
    val vocalSide: Int? = null,
    val vocalGroup: String? = null,
    val isBackgroundCue: Boolean = false,
    val timestampSequenceValid: Boolean = true,
    val endTimeMs: Long? = null,
    val backgroundEndTimeMs: Long? = null,
    val backgroundCues: List<LyricVocalCue> = emptyList(),
    val vocalParentTimeMs: Long? = null,
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

internal object EmbeddedLyricsCache {
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>?): Boolean = size > 16
    }
    private val mutex = Mutex()

    suspend fun load(context: Context, uri: String): List<LyricLine> = mutex.withLock {
        synchronized(cache) { cache[uri] } ?: loadEmbeddedLyrics(context, uri).also { lines ->
            synchronized(cache) { cache[uri] = lines }
        }
    }
}

internal suspend fun loadLyricsDocument(context: Context, uri: Uri): List<LyricLine> = withContext(Dispatchers.IO) {
    val input = if (uri.scheme == "file") {
        uri.path?.let(::File)?.inputStream()
    } else {
        context.contentResolver.openInputStream(uri)
    } ?: return@withContext emptyList()
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
    parseTtmlLyrics(text) ?: parseLyrics(listOf(text))
}

internal object LyricsSourcePreferences {
    private const val PREFERENCES = "lyrics_source_preferences"

    fun useEmbeddedLyrics(context: Context, songId: Long): Boolean =
        preferences(context).getBoolean("embedded_$songId", true)

    fun setUseEmbeddedLyrics(context: Context, songId: Long, enabled: Boolean) {
        preferences(context).edit().putBoolean("embedded_$songId", enabled).apply()
    }

    fun importedDocumentUri(context: Context, songId: Long): Uri? =
        preferences(context).getString("document_$songId", null)
            ?.let { stored -> runCatching { Uri.parse(stored) }.getOrNull() }

    fun rememberImportedDocument(context: Context, songId: Long, uri: Uri) {
        preferences(context).edit().putString("document_$songId", uri.toString()).apply()
    }

    fun clearImportedDocument(context: Context, songId: Long) {
        preferences(context).edit().remove("document_$songId").apply()
    }

    fun automaticDocumentUri(context: Context, songId: Long): Uri? =
        preferences(context).getString("automatic_document_$songId", null)
            ?.let { stored -> runCatching { Uri.parse(stored) }.getOrNull() }

    fun rememberAutomaticDocument(context: Context, songId: Long, uri: Uri) {
        preferences(context).edit().putString("automatic_document_$songId", uri.toString()).apply()
    }

    fun clearAutomaticDocument(context: Context, songId: Long) {
        preferences(context).edit().remove("automatic_document_$songId").apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

internal object AutomaticLyricsSearchPreference {
    private const val PREFERENCES = "lyrics_source_preferences"
    private const val ENABLED = "automatically_check_for_lyrics"
    private val enabledState = MutableStateFlow(false)
    @Volatile private var initialized = false

    fun state(context: Context): kotlinx.coroutines.flow.StateFlow<Boolean> {
        if (!initialized) synchronized(this) {
            if (!initialized) {
                enabledState.value = context.applicationContext
                    .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getBoolean(ENABLED, false)
                initialized = true
            }
        }
        return enabledState
    }

    fun isEnabled(context: Context): Boolean = state(context).value

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).apply()
        state(context)
        enabledState.value = enabled
    }
}

/** Searches only an explicitly accessible lyrics scope, and only once per track per day. */
internal object AutomaticLyricsFinder {
    private const val CHECK_RETRY_INTERVAL_MS = 24 * 60 * 60 * 1_000L
    private const val MAX_DOCUMENTS = 12_000
    private const val MAX_DEPTH = 24
    private const val MAX_DOWNLOADS_FILES = 12_000
    private const val MAX_DOWNLOADS_DEPTH = 16
    private val scanMutex = Mutex()
    private val automaticUris = MutableStateFlow<Map<Long, String?>>(emptyMap())

    fun uriFlow(songId: Long): Flow<String?> = automaticUris
        .map { it[songId] }
        .distinctUntilChanged()

    suspend fun findIfNeeded(context: Context, song: Song, source: LibrarySource?) = scanMutex.withLock {
        val appContext = context.applicationContext
        if (!AutomaticLyricsSearchPreference.isEnabled(appContext)) return@withLock
        val manuallyAttachedUri = LyricsSourcePreferences.importedDocumentUri(appContext, song.id)
        if (manuallyAttachedUri != null) {
            val manualLyrics = try {
                loadLyricsDocument(appContext, manuallyAttachedUri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (manualLyrics.isNotEmpty()) return@withLock
            LyricsSourcePreferences.clearImportedDocument(appContext, song.id)
        }

        val cachedUri = LyricsSourcePreferences.automaticDocumentUri(appContext, song.id)
        if (cachedUri != null) {
            val cachedLyrics = try {
                loadLyricsDocument(appContext, cachedUri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (cachedLyrics.isNotEmpty()) {
                publish(song.id, cachedUri)
                return@withLock
            }
            LyricsSourcePreferences.clearAutomaticDocument(appContext, song.id)
            publish(song.id, null)
            appContext.getSharedPreferences("lyrics_source_preferences", Context.MODE_PRIVATE)
                .edit().remove("automatic_checked_${song.id}").apply()
        }

        val preferences = appContext.getSharedPreferences("lyrics_source_preferences", Context.MODE_PRIVATE)
        val roots = accessibleLyricsRoots(appContext, source)
        val scopeKey = roots.joinToString("|") { it.cacheKey }
        val checkedAt = preferences.getLong("automatic_checked_${song.id}", 0L)
        val previousScope = preferences.getString("automatic_scope_${song.id}", null)
        if (previousScope == scopeKey && System.currentTimeMillis() - checkedAt < CHECK_RETRY_INTERVAL_MS) return@withLock

        val embedded = try {
            EmbeddedLyricsCache.load(appContext, song.uri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        if (embedded.isNotEmpty()) {
            preferences.edit()
                .putLong("automatic_checked_${song.id}", System.currentTimeMillis())
                .putString("automatic_scope_${song.id}", scopeKey)
                .apply()
            return@withLock
        }

        val match = withContext(Dispatchers.IO) { findMatchingDocument(appContext, roots, song) }
        preferences.edit()
            .putLong("automatic_checked_${song.id}", System.currentTimeMillis())
            .putString("automatic_scope_${song.id}", scopeKey)
            .apply()
        if (match != null) {
            LyricsSourcePreferences.rememberAutomaticDocument(appContext, song.id, match)
            publish(song.id, match)
        }
    }

    private fun publish(songId: Long, uri: Uri?) {
        automaticUris.value = automaticUris.value + (songId to uri?.toString())
    }

    private fun accessibleLyricsRoots(context: Context, source: LibrarySource?): List<AutoLyricsRoot> = when (source) {
        is LibrarySource.Folder -> listOf(AutoLyricsRoot.Tree(source.uri))
        is LibrarySource.Folders -> source.uris.distinct().map(AutoLyricsRoot::Tree)
        LibrarySource.AllDevice -> if (hasWholeStorageAccess(context)) {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            listOf(AutoLyricsRoot.Filesystem(downloads))
        } else emptyList()
        is LibrarySource.Files, null -> emptyList()
    }

    private fun hasWholeStorageAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Environment.isExternalStorageLegacy() &&
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    private suspend fun findMatchingDocument(context: Context, roots: List<AutoLyricsRoot>, song: Song): Uri? {
        if (roots.isEmpty()) return null
        val title = normalizedLyricsName(song.title)
        if (title.isBlank()) return null
        val artistTokens = song.artist.split(Regex("(?i)\\b(?:feat(?:uring)?|ft|with)\\b|[,;&]"))
            .map(::normalizedLyricsName)
            .filter { it.length >= 3 }
        val matches = mutableListOf<MatchedLyrics>()
        var visitedDocuments = 0

        fun consider(name: String, uri: Uri) {
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension != "ttml" && extension != "lrc") return
            val base = normalizedLyricsName(name.substringBeforeLast('.', name))
            if (base.isBlank() || !base.contains(title)) return
            val includesArtist = artistTokens.any(base::contains)
            if (base != title && !includesArtist) return
            matches += MatchedLyrics(
                uri = uri,
                isTtml = extension == "ttml",
                matchQuality = if (includesArtist) 2 else 1,
            )
        }

        roots.forEach { root ->
            coroutineContext.ensureActive()
            when (root) {
                is AutoLyricsRoot.Tree -> {
                    val treeUri = runCatching { Uri.parse(root.uri) }.getOrNull() ?: return@forEach
                    val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@forEach
                    val pending = ArrayDeque<Pair<String, Int>>()
                    pending.add(rootDocumentId to 0)
                    val visitedIds = mutableSetOf<String>()
                    while (pending.isNotEmpty() && visitedDocuments < MAX_DOCUMENTS) {
                        coroutineContext.ensureActive()
                        val (parentId, depth) = pending.removeFirst()
                        if (!visitedIds.add(parentId)) continue
                        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                        val cursor = try {
                            context.contentResolver.query(
                                childrenUri,
                                arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
                                null,
                                null,
                                null,
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        } ?: continue
                        cursor.use {
                            val idColumn = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
                            val nameColumn = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
                            val mimeColumn = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
                            while (cursor.moveToNext() && visitedDocuments < MAX_DOCUMENTS) {
                                coroutineContext.ensureActive()
                                visitedDocuments += 1
                                val id = cursor.getString(idColumn) ?: continue
                                val name = cursor.getString(nameColumn).orEmpty()
                                val mime = cursor.getString(mimeColumn).orEmpty()
                                if (mime == Document.MIME_TYPE_DIR) {
                                    if (depth < MAX_DEPTH) pending.add(id to depth + 1)
                                } else if (name.endsWith(".ttml", true) || name.endsWith(".lrc", true)) {
                                    consider(name, DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                                }
                            }
                        }
                    }
                }
                is AutoLyricsRoot.Filesystem -> {
                    val downloads = root.file
                    val canonicalRoot = runCatching { downloads.canonicalFile }.getOrNull() ?: return@forEach
                    val pending = ArrayDeque<Pair<File, Int>>()
                    pending.add(downloads to 0)
                    while (pending.isNotEmpty() && visitedDocuments < MAX_DOWNLOADS_FILES) {
                        coroutineContext.ensureActive()
                        val (directory, depth) = pending.removeFirst()
                        val files = runCatching { directory.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) } }.getOrNull().orEmpty()
                        for (file in files) {
                            coroutineContext.ensureActive()
                            visitedDocuments += 1
                            if (visitedDocuments >= MAX_DOWNLOADS_FILES) break
                            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: continue
                            if (!canonical.toPath().startsWith(canonicalRoot.toPath())) continue
                            if (canonical.isDirectory) {
                                if (depth < MAX_DOWNLOADS_DEPTH && !canonical.name.startsWith('.')) pending.add(canonical to depth + 1)
                            } else if (canonical.name.endsWith(".ttml", true) || canonical.name.endsWith(".lrc", true)) {
                                consider(canonical.name, Uri.fromFile(canonical))
                            }
                        }
                    }
                }
            }
        }
        return matches.maxWithOrNull(
            compareBy<MatchedLyrics> { if (it.isTtml) 1 else 0 }
                .thenBy(MatchedLyrics::matchQuality),
        )?.uri
    }

    private fun normalizedLyricsName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    private sealed interface AutoLyricsRoot {
        val cacheKey: String
        data class Tree(val uri: String) : AutoLyricsRoot { override val cacheKey = "tree:$uri" }
        data class Filesystem(val file: File) : AutoLyricsRoot { override val cacheKey = "downloads:${file.absolutePath}" }
    }

    private data class MatchedLyrics(val uri: Uri, val isTtml: Boolean, val matchQuality: Int)
}

private data class TtmlTimingFrame(
    val name: String,
    val beginMs: Long,
    val hasTiming: Boolean,
    val timeContainer: String,
    var sequentialCursorMs: Long? = null,
    var hasSequentialChildren: Boolean = false,
    var endMs: Long? = null,
    var contentEndMs: Long? = null,
)

private data class TtmlSpanBuilder(
    val timeMs: Long?,
    val textStartIndex: Int,
    val isBackground: Boolean,
    val text: StringBuilder = StringBuilder(),
)

private data class TtmlParagraphBuilder(
    val beginMs: Long?,
    val vocalSide: Int?,
    val vocalGroup: String?,
    val isBackgroundCue: Boolean,
    val text: StringBuilder = StringBuilder(),
    val backgroundText: StringBuilder = StringBuilder(),
    val words: MutableList<LyricWord> = mutableListOf(),
    val backgroundWords: MutableList<LyricWord> = mutableListOf(),
    val spans: MutableList<TtmlSpanBuilder> = mutableListOf(),
) {
    fun appendText(value: String, isBackground: Boolean) {
        if (isBackground) {
            backgroundText.append(value)
            val span = spans.lastOrNull { it.isBackground && it.timeMs != null }
            if (span != null) span.text.append(value)
            else if (backgroundWords.isNotEmpty()) {
                val last = backgroundWords.last()
                backgroundWords[backgroundWords.lastIndex] = last.copy(text = last.text + value)
            }
        } else {
            text.append(value)
            val span = spans.lastOrNull { !it.isBackground && it.timeMs != null }
            if (span != null) span.text.append(value)
            else if (words.isNotEmpty()) {
                val last = words.last()
                words[words.lastIndex] = last.copy(text = last.text + value)
            }
        }
    }

    fun finishTimedSpan(span: TtmlSpanBuilder) {
        val timeMs = span.timeMs ?: return
        val text = span.text.toString()
        if (text.isEmpty()) return
        val target = if (span.isBackground) backgroundWords else words
        val sourceText = if (span.isBackground) backgroundText else this.text
        if (target.isEmpty()) {
            val precedingText = sourceText.substring(0, span.textStartIndex.coerceIn(0, sourceText.length))
            target += LyricWord(timeMs, precedingText + text)
        } else {
            target += LyricWord(timeMs, text)
        }
    }
}

/** Reads TTML timing hierarchy, sequential containers, timed words, and vocal metadata. */
private fun parseTtmlLyrics(source: String): List<LyricLine>? {
    var isTtml = false
    return try {
        val parser = Xml.newPullParser().apply {
            runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
            setInput(StringReader(source))
        }
        var frameRate = 30.0
        var tickRate = 1.0
        var paragraph: TtmlParagraphBuilder? = null
        val lines = mutableListOf<LyricLine>()
        val timingStack = mutableListOf<TtmlTimingFrame>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when {
                        parser.localElementName().equals("tt", ignoreCase = true) -> {
                            isTtml = true
                            frameRate = ttmlFrameRate(parser)
                            tickRate = parser.attributeValue("tickRate")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
                        }
                        isTtml && parser.localElementName() in TTML_TIMING_ELEMENTS -> {
                            val parent = timingStack.lastOrNull()
                            val isFirstSequentialChild = parent?.timeContainer == "seq" && !parent.hasSequentialChildren
                            val parentBase = when {
                                parent == null -> 0L
                                parent.timeContainer == "seq" -> parent.sequentialCursorMs ?: parent.beginMs
                                else -> parent.beginMs
                            }
                            val beginOffset = parseTtmlTime(parser.attributeValue("begin"), frameRate, tickRate)
                            // Apple/BitChord TTML stores absolute media timestamps on
                            // nested <div>, <p>, and <span> elements. Sequence
                            // containers are the exception: their child offsets are
                            // relative to the sequence cursor.
                            val beginMs = when {
                                beginOffset == null -> parentBase
                                parent?.timeContainer == "seq" -> (parentBase + beginOffset).coerceAtLeast(0L)
                                else -> beginOffset.coerceAtLeast(0L)
                            }
                            val inheritedTiming = parent?.hasTiming == true ||
                                (parent?.timeContainer == "seq" && parent.sequentialCursorMs != null)
                            val hasTiming = beginOffset != null || inheritedTiming || isFirstSequentialChild
                            val duration = parseTtmlTime(parser.attributeValue("dur"), frameRate, tickRate)
                            val explicitEnd = parseTtmlTime(parser.attributeValue("end"), frameRate, tickRate)
                                ?.let { end ->
                                    if (parent?.timeContainer == "seq") (parentBase + end).coerceAtLeast(0L)
                                    else end.coerceAtLeast(0L)
                                }
                            val frame = TtmlTimingFrame(
                                name = parser.localElementName(),
                                beginMs = beginMs,
                                hasTiming = hasTiming,
                                timeContainer = parser.attributeValue("timeContainer")?.lowercase()?.takeIf { it == "seq" } ?: "par",
                                endMs = explicitEnd ?: duration?.let { (beginMs + it).coerceAtLeast(beginMs) },
                            )
                            timingStack += frame
                            if (parent?.timeContainer == "seq") parent.hasSequentialChildren = true
                            if (frame.name == "p") {
                                val speaker = normalizeTtmlAgent(parser.attributeValue("agent") ?: parser.attributeValue("who"))
                                val role = parser.attributeValue("role")?.substringAfterLast(':')?.lowercase().orEmpty()
                                val isBackgroundCue = isTtmlBackgroundRole(role)
                                paragraph = TtmlParagraphBuilder(
                                    beginMs = frame.beginMs.takeIf { frame.hasTiming },
                                    vocalSide = vocalSideFromTtml(speaker),
                                    vocalGroup = speaker,
                                    isBackgroundCue = isBackgroundCue,
                                )
                            } else if (frame.name == "span" && paragraph != null) {
                                val role = parser.attributeValue("role")?.substringAfterLast(':')?.lowercase().orEmpty()
                                // TTML providers wrap timed backing-vocal spans in an untimed
                                // role-marked span (for example <span role="x-bg"><span
                                // begin="...">(ad-lib)</span></span>). Inherit that role
                                // so the timed words become a separate, synchronized vocal row.
                                val background = isTtmlBackgroundRole(role) ||
                                    paragraph.spans.lastOrNull()?.isBackground == true
                                val isSequentialWordCue = parent?.timeContainer == "seq" &&
                                    (parent.sequentialCursorMs != null || isFirstSequentialChild)
                                val wordTime = frame.beginMs.takeIf {
                                    frame.hasTiming && (beginOffset != null || isSequentialWordCue)
                                }
                                val sourceText = if (background) paragraph.backgroundText else paragraph.text
                                paragraph.spans.add(
                                    TtmlSpanBuilder(
                                        timeMs = wordTime,
                                        textStartIndex = sourceText.length,
                                        isBackground = background,
                                    ),
                                )
                            } else if (frame.name == "br" && paragraph != null) {
                                val background = paragraph.spans.lastOrNull()?.isBackground == true
                                paragraph.appendText("\n", background)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> paragraph?.let { current ->
                    current.appendText(parser.text, current.spans.lastOrNull()?.isBackground == true)
                }
                XmlPullParser.END_TAG -> {
                    if (isTtml && parser.localElementName() in TTML_TIMING_ELEMENTS) {
                        val elementName = parser.localElementName()
                        val frameIndex = timingStack.indexOfLast { it.name == elementName }
                        if (frameIndex >= 0) {
                            val frame = timingStack.removeAt(frameIndex)
                            if (frame.name == "span" && paragraph != null && paragraph.spans.isNotEmpty()) {
                                paragraph.finishTimedSpan(paragraph.spans.removeAt(paragraph.spans.lastIndex))
                            }
                            if (frame.name == "p") {
                                paragraph?.let { current ->
                                    val rawText = current.text.toString().trim()
                                    val rawBackgroundText = current.backgroundText.toString().trim()
                                    val backgroundOnly = rawText.isEmpty() && rawBackgroundText.isNotEmpty()
                                    val lyricText = if (backgroundOnly) rawBackgroundText else rawText
                                    if (lyricText.isNotEmpty()) {
                                        val timedWords = trimTimedWords(if (backgroundOnly) current.backgroundWords else current.words)
                                        val lineTime = current.beginMs ?: timedWords.firstNotNullOfOrNull { it.timeMs }
                                        val parsed = lyricLine(lineTime, lyricText, 0L)
                                        val line = if (timedWords.isEmpty()) {
                                            parsed.copy(
                                                vocalSide = parsed.vocalSide ?: current.vocalSide,
                                                endTimeMs = frame.endMs ?: frame.contentEndMs,
                                                vocalGroup = current.vocalGroup,
                                                backgroundText = rawBackgroundText.takeUnless { backgroundOnly }.orEmpty().ifBlank { null },
                                                backgroundWords = if (backgroundOnly) emptyList() else trimTimedWords(current.backgroundWords),
                                                backgroundTimeMs = if (backgroundOnly) null else current.backgroundWords.mapNotNull { it.timeMs }.minOrNull()
                                                    ?: rawBackgroundText.takeIf(String::isNotBlank)?.let { current.beginMs },
                                                backgroundEndTimeMs = if (backgroundOnly) null else frame.endMs ?: frame.contentEndMs,
                                                isBackgroundCue = current.isBackgroundCue || backgroundOnly,
                                            )
                                        } else {
                                            parsed.copy(
                                                timeMs = lineTime,
                                                endTimeMs = frame.endMs ?: frame.contentEndMs,
                                                text = timedWords.joinToString(separator = "") { it.text },
                                                words = timedWords,
                                                vocalSide = parsed.vocalSide ?: current.vocalSide,
                                                vocalGroup = current.vocalGroup,
                                                backgroundText = rawBackgroundText.takeUnless { backgroundOnly }.orEmpty().ifBlank { null },
                                                backgroundWords = if (backgroundOnly) emptyList() else trimTimedWords(current.backgroundWords),
                                                backgroundTimeMs = if (backgroundOnly) null else current.backgroundWords.mapNotNull { it.timeMs }.minOrNull()
                                                    ?: rawBackgroundText.takeIf(String::isNotBlank)?.let { current.beginMs },
                                                backgroundEndTimeMs = if (backgroundOnly) null else frame.endMs ?: frame.contentEndMs,
                                                isBackgroundCue = current.isBackgroundCue || backgroundOnly,
                                            )
                                        }
                                        if (lines.size < MAX_LYRIC_LINES) lines += line
                                    }
                                }
                                paragraph = null
                            }
                            val parent = timingStack.lastOrNull()
                            if (frame.endMs != null) {
                                parent?.let { parentFrame ->
                                    parentFrame.contentEndMs = maxOf(parentFrame.contentEndMs ?: Long.MIN_VALUE, frame.endMs!!)
                                    if (parentFrame.timeContainer == "seq") parentFrame.sequentialCursorMs = frame.endMs
                                }
                            } else if (frame.contentEndMs != null) {
                                parent?.let { parentFrame ->
                                    parentFrame.contentEndMs = maxOf(parentFrame.contentEndMs ?: Long.MIN_VALUE, frame.contentEndMs!!)
                                    if (parentFrame.timeContainer == "seq") parentFrame.sequentialCursorMs = frame.contentEndMs
                                }
                            }
                            if (frame.contentEndMs != null && frame.endMs == null) frame.endMs = frame.contentEndMs
                        }
                    }
                }
            }
            event = parser.next()
        }
        if (!isTtml) null else {
            val withBackgroundCues = attachStandaloneBackgroundCues(lines)
                .map(::removeTtmlBackgroundBrackets)
            val ordered = if (withBackgroundCues.all { it.timeMs != null }) {
                withBackgroundCues.sortedBy { it.timeMs }
            } else {
                // Keep untimed TTML paragraphs where they appeared in the document. Moving
                // them to the end made an unparsed opening verse look like a post-song tail.
                withBackgroundCues
            }
            prepareLyricTimeline(ordered)
        }
    } catch (error: Exception) {
        if (isTtml) Log.w("WavvLyrics", "Failed to parse TTML lyrics", error)
        if (isTtml) emptyList() else null
    }
}

private fun normalizeTtmlAgent(value: String?): String? = value
    ?.trim()
    ?.removePrefix("#")
    ?.substringAfterLast(':')
    ?.takeIf(String::isNotBlank)

private fun isTtmlBackgroundRole(role: String): Boolean =
    role in TTML_BACKGROUND_ROLES || role.contains("background") || role.contains("backing") || role == "bg"

private fun vocalSideFromTtml(value: String?): Int? = when (value?.lowercase()) {
    "v1", "left", "1" -> -1
    "v2", "right", "2" -> 1
    else -> null
}

private fun assignTtmlVocalSides(lines: List<LyricLine>): List<LyricLine> {
    val groups = lines.mapNotNull { it.vocalGroup }.distinct()
    val groupSides = groups.mapIndexed { index, group -> group to if (index % 2 == 0) -1 else 1 }.toMap()
    val assigned = lines.map { line ->
        line.copy(vocalSide = line.vocalSide ?: line.vocalGroup?.let(groupSides::get))
    }
    return assignVocalSides(assigned)
}

private fun attachStandaloneBackgroundCues(lines: List<LyricLine>): List<LyricLine> {
    val main = lines.filterNot { it.isBackgroundCue() }.toMutableList()
    val background = lines.filter { it.isBackgroundCue() }
    val sortedMainTimes = main.mapNotNull { it.timeMs }.distinct().sorted()

    fun nextMainTime(timeMs: Long): Long? {
        val found = sortedMainTimes.binarySearch(timeMs)
        val nextIndex = if (found >= 0) found + 1 else -found - 1
        return sortedMainTimes.getOrNull(nextIndex)
    }

    background.forEach { cue ->
        val cueStart = cue.timeMs
        val cueEnd = cue.endTimeMs ?: cueStart
        val overlappingIndices = if (cueStart == null || cueEnd == null) emptyList() else main.indices.filter { index ->
            val mainLine = main[index]
            val mainStart = mainLine.timeMs ?: return@filter false
            val mainEnd = mainLine.endTimeMs ?: nextMainTime(mainStart) ?: mainStart
            mainStart <= cueEnd && mainEnd >= cueStart
        }
        val activeAtCueStart = if (cueStart == null) emptyList() else overlappingIndices.filter { index ->
            val mainLine = main[index]
            val mainStart = mainLine.timeMs ?: return@filter false
            val mainEnd = mainLine.endTimeMs ?: nextMainTime(mainStart) ?: mainStart
            mainStart <= cueStart && mainEnd >= cueStart
        }
        val upcomingDuringCue = if (cueStart == null || cueEnd == null) emptyList() else overlappingIndices
            .filter { index -> main[index].timeMs?.let { it > cueStart && it <= cueEnd } == true }
        val nearestIndex = when {
            activeAtCueStart.isNotEmpty() -> activeAtCueStart.maxByOrNull { main[it].timeMs ?: Long.MIN_VALUE }
            upcomingDuringCue.isNotEmpty() -> upcomingDuringCue.minByOrNull { main[it].timeMs ?: Long.MAX_VALUE }
            overlappingIndices.isNotEmpty() -> overlappingIndices.maxByOrNull { index ->
                val mainLine = main[index]
                val mainStart = mainLine.timeMs ?: Long.MAX_VALUE
                val mainEnd = mainLine.endTimeMs ?: nextMainTime(mainStart) ?: mainStart
                val overlapStart = maxOf(mainStart, cueStart ?: mainStart)
                val overlapEnd = minOf(mainEnd, cueEnd ?: mainEnd)
                (overlapEnd - overlapStart).coerceAtLeast(0L)
            }
            cue.endTimeMs == null -> main.indices
                .filter { main[it].timeMs != null && cueStart != null }
                .minByOrNull { index -> kotlin.math.abs(main[index].timeMs!! - cueStart!!) }
                ?.takeIf { index -> kotlin.math.abs(main[index].timeMs!! - cueStart!!) <= TTML_BACKGROUND_PAIR_WINDOW_MS }
            else -> null
        }

        if (nearestIndex != null) {
            val target = main[nearestIndex]
            main[nearestIndex] = target.copy(
                backgroundCues = target.backgroundCues + LyricVocalCue(
                    timeMs = cueStart,
                    endTimeMs = cue.endTimeMs,
                    text = cue.text,
                    words = cue.words,
                ),
                backgroundTimeMs = listOfNotNull(target.backgroundTimeMs, cueStart).minOrNull(),
                backgroundEndTimeMs = listOfNotNull(target.backgroundEndTimeMs, cue.endTimeMs).maxOrNull(),
            )
        } else {
            main += cue
        }
    }
    return main
}

/**
 * Turns inline and standalone backing vocals into their own timed lyric rows.
 * Keeping them as independent rows lets them enter only at their cue time, animate
 * into the list, and participate in the active-vocal calculation while the main
 * line remains active. A cue inherits its lead's side, so backing vocals stay
 * directly under the main line instead of being mistaken for another lead.
 */
private fun prepareLyricTimeline(lines: List<LyricLine>): List<LyricLine> {
    data class LyricEvent(val sourceIndex: Int, val vocalOrder: Int, val line: LyricLine)

    val primarySourceIndices = lines.indices.filterNot { lines[it].isBackgroundCue() }
    val primaryLines = primarySourceIndices.map { sourceIndex ->
        lines[sourceIndex].copy(
            backgroundText = null,
            backgroundWords = emptyList(),
            backgroundTimeMs = null,
            backgroundEndTimeMs = null,
            backgroundCues = emptyList(),
        )
    }
    val assignedPrimary = assignTtmlVocalSides(primaryLines).toMutableList()

    val additionalVocalEvents = mutableListOf<LyricEvent>()
    lines.forEachIndexed { sourceIndex, line ->
        if (line.isBackgroundCue()) {
            additionalVocalEvents += LyricEvent(sourceIndex, 1, line)
        } else {
            line.backgroundCues.forEachIndexed { cueIndex, cue ->
                val cueTime = cue.timeMs ?: cue.words.mapNotNull { it.timeMs }.minOrNull() ?: line.timeMs
                additionalVocalEvents += LyricEvent(
                    sourceIndex,
                    cueIndex + 1,
                    LyricLine(
                        timeMs = cueTime,
                        text = cue.text,
                        words = cue.words,
                        isBackgroundCue = true,
                        endTimeMs = cue.endTimeMs,
                    ),
                )
            }
            line.backgroundText?.takeIf(String::isNotBlank)?.let { backingText ->
                val cueTime = line.backgroundTimeMs
                    ?: line.backgroundWords.mapNotNull { it.timeMs }.minOrNull()
                    ?: line.timeMs
                additionalVocalEvents += LyricEvent(
                    sourceIndex,
                    line.backgroundCues.size + 1,
                    LyricLine(
                        timeMs = cueTime,
                        text = backingText,
                        words = line.backgroundWords,
                        isBackgroundCue = true,
                        endTimeMs = line.backgroundEndTimeMs,
                    ),
                )
            }
        }
    }

    fun mainLineEnd(index: Int): Long? {
        val start = assignedPrimary[index].timeMs ?: return null
        val explicitEnd = assignedPrimary[index].endTimeMs?.takeIf { it > start }
        if (explicitEnd != null) return explicitEnd
        return assignedPrimary.asSequence()
            .mapNotNull { it.timeMs }
            .filter { it > start }
            .minOrNull()
            ?: start
    }

    fun overlappingMainIndex(cue: LyricLine): Int? {
        val cueStart = cue.timeMs ?: return null
        val cueEnd = cue.endTimeMs?.coerceAtLeast(cueStart) ?: cueStart
        return assignedPrimary.indices
            .mapNotNull { index ->
                val mainStart = assignedPrimary[index].timeMs ?: return@mapNotNull null
                val mainEnd = mainLineEnd(index) ?: return@mapNotNull null
                if (mainStart > cueEnd || mainEnd < cueStart) return@mapNotNull null
                val overlapStart = maxOf(mainStart, cueStart)
                val overlapEnd = minOf(mainEnd, cueEnd)
                val activeAtCueStart = mainStart <= cueStart && mainEnd >= cueStart
                index to ((overlapEnd - overlapStart).coerceAtLeast(0L) + if (activeAtCueStart) 10_000L else 0L)
            }
            .maxByOrNull { it.second }
            ?.first
    }

    val assignedAdditionalVocals = additionalVocalEvents.map { event ->
        val partnerIndex = overlappingMainIndex(event.line)
        val cue = if (partnerIndex == null) {
            event.line.copy(vocalSide = event.line.vocalSide ?: -1)
        } else {
            val partner = assignedPrimary[partnerIndex]
            val primarySide = partner.vocalSide ?: -1
            if (partner.vocalSide == null) {
                assignedPrimary[partnerIndex] = partner.copy(vocalSide = primarySide)
            }
            event.line.copy(
                vocalSide = primarySide,
                vocalParentTimeMs = event.line.vocalParentTimeMs ?: partner.timeMs,
            )
        }
        event.copy(line = cue)
    }

    val events = buildList {
        assignedPrimary.forEachIndexed { index, line ->
            add(LyricEvent(primarySourceIndices[index], 0, line))
        }
        addAll(assignedAdditionalVocals)
    }
    val orderedEvents = if (events.all { it.line.timeMs != null }) {
        events.sortedWith(
            compareBy<LyricEvent>({ it.line.timeMs }, { if (it.line.isBackgroundCue()) 1 else 0 }, { it.sourceIndex }, { it.vocalOrder }),
        )
    } else {
        events.sortedWith(compareBy<LyricEvent>({ it.sourceIndex }, { it.vocalOrder }))
    }
    return orderedEvents.take(MAX_LYRIC_LINES).map { it.line }
}

private fun LyricLine.isBackgroundCue(): Boolean = isBackgroundCue

private fun trimTimedWords(words: List<LyricWord>): List<LyricWord> {
    if (words.isEmpty()) return emptyList()
    val result = words.toMutableList()
    val firstText = result.first().text.trimStart()
    if (firstText.isEmpty()) result.removeAt(0) else result[0] = result[0].copy(text = firstText)
    if (result.isNotEmpty()) {
        val lastIndex = result.lastIndex
        val lastText = result[lastIndex].text.trimEnd()
        if (lastText.isEmpty()) result.removeAt(lastIndex) else result[lastIndex] = result[lastIndex].copy(text = lastText)
    }
    return result
}

/** Removes only the enclosing punctuation used to mark TTML backing-vocal cues. */
private fun removeTtmlBackgroundBrackets(line: LyricLine): LyricLine {
    fun strip(text: String, words: List<LyricWord>): Pair<String, List<LyricWord>> {
        val trimmed = text.trim()
        val opening = trimmed.firstOrNull()
        val closing = when (opening) {
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            else -> null
        }
        if (closing == null || trimmed.lastOrNull() != closing || trimmed.length < 2) {
            return text to words
        }

        val cleanedWords = words.toMutableList()
        val firstIndex = cleanedWords.indexOfFirst { it.text.isNotBlank() }
        val lastIndex = cleanedWords.indexOfLast { it.text.isNotBlank() }
        if (firstIndex >= 0) {
            val firstText = cleanedWords[firstIndex].text
            val firstCharacter = firstText.indexOfFirst { !it.isWhitespace() }
            if (firstCharacter >= 0 && firstText[firstCharacter] == opening) {
                cleanedWords[firstIndex] = cleanedWords[firstIndex].copy(
                    text = firstText.removeRange(firstCharacter, firstCharacter + 1),
                )
            }
        }
        if (lastIndex >= 0) {
            val lastText = cleanedWords[lastIndex].text
            val lastCharacter = lastText.indexOfLast { !it.isWhitespace() }
            if (lastCharacter >= 0 && lastText[lastCharacter] == closing) {
                cleanedWords[lastIndex] = cleanedWords[lastIndex].copy(
                    text = lastText.removeRange(lastCharacter, lastCharacter + 1),
                )
            }
        }
        return trimmed.substring(1, trimmed.lastIndex).trim() to cleanedWords
    }

    if (line.isBackgroundCue) {
        val (text, words) = strip(line.text, line.words)
        return line.copy(text = text, words = words)
    }

    val (backgroundText, backgroundWords) = line.backgroundText?.let { text ->
        strip(text, line.backgroundWords)
    } ?: (null to line.backgroundWords)
    val backgroundCues = line.backgroundCues.map { cue ->
        val (text, words) = strip(cue.text, cue.words)
        cue.copy(text = text, words = words)
    }
    return line.copy(
        backgroundText = backgroundText,
        backgroundWords = backgroundWords,
        backgroundCues = backgroundCues,
    )
}

private fun ttmlFrameRate(parser: XmlPullParser): Double {
    val frameRate = parser.attributeValue("frameRate")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 30.0
    val multiplier = parser.attributeValue("frameRateMultiplier")?.trim()?.split(Regex("\\s+"))
    if (multiplier?.size == 2) {
        val numerator = multiplier[0].toDoubleOrNull()
        val denominator = multiplier[1].toDoubleOrNull()
        if (numerator != null && denominator != null && numerator > 0.0 && denominator > 0.0) {
            return frameRate * numerator / denominator
        }
    }
    return frameRate
}

private fun XmlPullParser.attributeValue(localName: String): String? =
    (0 until attributeCount).firstNotNullOfOrNull { index ->
        getAttributeName(index).substringAfterLast(':')
            .takeIf { it.equals(localName, ignoreCase = true) }
            ?.let { getAttributeValue(index) }
    }

private fun XmlPullParser.localElementName(): String = name.substringAfterLast(':').lowercase()

private fun parseTtmlTime(value: String?, frameRate: Double, tickRate: Double = 1.0): Long? {
    val time = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val frameClock = TTML_FRAME_CLOCK.matchEntire(time)
    if (frameClock != null) {
        val hours = frameClock.groupValues[1].toLongOrNull() ?: 0L
        val minutes = frameClock.groupValues[2].toLongOrNull() ?: return null
        val seconds = frameClock.groupValues[3].toLongOrNull() ?: return null
        val frames = frameClock.groupValues[4].toLongOrNull() ?: return null
        val subFrame = frameClock.groupValues[5].toLongOrNull() ?: 0L
        if (minutes > 59 || seconds > 59 || frameRate <= 0.0) return null
        return (((hours * 60L + minutes) * 60L + seconds) * 1_000L + frames * 1_000.0 / frameRate + subFrame * 1_000.0 / (frameRate * 100.0)).toLong()
    }
    val clock = TTML_CLOCK.matchEntire(time)
    if (clock != null) {
        val hours = clock.groupValues[1].toLongOrNull() ?: 0L
        val minutes = clock.groupValues[2].toLongOrNull() ?: return null
        val seconds = clock.groupValues[3].toLongOrNull() ?: return null
        val fraction = clock.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        if (minutes > 59 || seconds > 59) return null
        return ((hours * 60L + minutes) * 60L + seconds) * 1_000L + fraction
    }
    // Apple/BitChord TTML commonly writes seconds as a bare decimal, e.g.
    // begin="27.395". TTML's clock parser handles that form as seconds too.
    time.toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { return (it * 1_000.0).toLong() }
    val offset = TTML_OFFSET.matchEntire(time) ?: return null
    val amount = offset.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (offset.groupValues[2].lowercase()) {
        "h" -> 3_600_000.0
        "m" -> 60_000.0
        "s" -> 1_000.0
        "ms" -> 1.0
        "f" -> 1_000.0 / frameRate
        "t" -> 1_000.0 / tickRate
        else -> return null
    }
    return (amount * multiplier).toLong().takeIf { it >= 0L }
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
    val ordered = if (result.all { it.timeMs != null }) result.sortedBy { it.timeMs } else result
    val orderedLines = prepareLyricTimeline(ordered)
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
    // BitChord writes a leading <R> into its LRC to preserve right alignment.
    // Strip it as layout metadata rather than displaying it as lyric text.
    val alignmentMatch = LRC_RIGHT_ALIGNMENT_MARKER.find(withoutVocalMarker)
    val withoutAlignmentMarker = if (alignmentMatch != null) {
        withoutVocalMarker.removeRange(alignmentMatch.range).trimStart()
    } else {
        withoutVocalMarker
    }
    val resolvedVocalSide = when {
        vocalSide != null -> vocalSide
        alignmentMatch != null -> 1
        else -> null
    }
    val textWithoutWordTimestamps = WORD_TIMESTAMP.replace(withoutAlignmentMarker, "").trim()
    val standaloneVocalText = BACKGROUND_VOCALS.findAll(textWithoutWordTimestamps)
        .map { it.groupValues[1].trim() }
        .filter(String::isNotEmpty)
        .toList()
    val hasOnlyStandaloneVocalText = standaloneVocalText.isNotEmpty() &&
        BACKGROUND_VOCALS.replace(textWithoutWordTimestamps, "").isBlank()
    if (hasOnlyStandaloneVocalText) {
        val parsedWords = parseWordTimings(withoutAlignmentMarker, offsetMs, timeMs)
            .map { word -> word.copy(text = word.text.replace("(", "").replace(")", "")) }
            .filter { it.text.isNotEmpty() }
        val cueTimeMs = timeMs?.let { (it + offsetMs).coerceAtLeast(0L) }
            ?: parsedWords.firstNotNullOfOrNull { it.timeMs }
        val cueText = parsedWords.joinToString(separator = "") { it.text }
            .ifBlank { standaloneVocalText.joinToString(" ") }
        val cueWords = parsedWords.ifEmpty {
            listOfNotNull(cueTimeMs?.let { LyricWord(it, cueText) })
        }
        return LyricLine(
            timeMs = cueTimeMs,
            text = cueText,
            words = cueWords,
            vocalSide = resolvedVocalSide,
            isBackgroundCue = true,
            endTimeMs = trailingWordTimestamp(withoutAlignmentMarker, offsetMs),
        )
    }
    val backgroundParts = BACKGROUND_VOCALS.findAll(withoutAlignmentMarker).map { it.groupValues[1].trim() }.filter(String::isNotEmpty).toList()
    val mainTextWithoutBackground = BACKGROUND_VOCALS.replace(withoutAlignmentMarker, "").trim()
    val mainText = when {
        mainTextWithoutBackground.isNotEmpty() -> mainTextWithoutBackground
        backgroundParts.isEmpty() -> withoutAlignmentMarker.trim()
        else -> ""
    }
    val words = parseWordTimings(mainText, offsetMs, timeMs)
    val parsedBackgrounds = backgroundParts.map { part ->
        val timedWords = parseWordTimings(part, offsetMs, timeMs)
        (if (timedWords.isEmpty()) part else timedWords.joinToString(separator = "") { it.text }) to timedWords
    }
    val backgroundWords = parsedBackgrounds.flatMap { it.second }
    val backgroundHasFullTiming = parsedBackgrounds.isNotEmpty() && parsedBackgrounds.all { it.second.isNotEmpty() }
    val lineTimeMs = timeMs?.let { (it + offsetMs).coerceAtLeast(0L) }
        ?: words.firstNotNullOfOrNull { it.timeMs }
        ?: backgroundWords.firstNotNullOfOrNull { it.timeMs }
    return LyricLine(
        timeMs = lineTimeMs,
        text = if (words.isEmpty()) mainText else words.joinToString(separator = "") { it.text },
        words = words,
        backgroundText = parsedBackgrounds.joinToString(" ") { it.first }.ifBlank { null },
        backgroundWords = backgroundWords.takeIf { backgroundHasFullTiming }.orEmpty(),
        backgroundTimeMs = backgroundWords.firstNotNullOfOrNull { it.timeMs }
            ?: lineTimeMs.takeIf { backgroundParts.isNotEmpty() },
        vocalSide = resolvedVocalSide,
        endTimeMs = trailingWordTimestamp(withoutAlignmentMarker, offsetMs),
    )
}

private fun trailingWordTimestamp(text: String, offsetMs: Long): Long? {
    val marker = WORD_TIMESTAMP.findAll(text).lastOrNull() ?: return null
    if (text.substring(marker.range.last + 1).isNotBlank()) return null
    return timestampMs(marker)?.let { (it + offsetMs).coerceAtLeast(0L) }
}

private fun parseWordTimings(text: String, offsetMs: Long, lineTimeMs: Long?): List<LyricWord> {
    val markers = WORD_TIMESTAMP.findAll(text).toList()
    if (markers.isEmpty()) return emptyList()
    val firstWordTime = timestampMs(markers.first())?.let { (it + offsetMs).coerceAtLeast(0L) }
    return buildList {
        val leadingText = text.substring(0, markers.first().range.first)
        if (leadingText.isNotEmpty()) {
            val leadingTime = lineTimeMs?.let { (it + offsetMs).coerceAtLeast(0L) } ?: firstWordTime
            add(LyricWord(leadingTime, leadingText))
        }
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
    // Timing overlap is not singer identity: a single vocalist's long LRC line
    // often overlaps the next timestamp. Use explicit LRC markers or TTML
    // agent groups when available; otherwise present the track as one singer,
    // left aligned.
    return lines.map { line ->
        if (line.isBackgroundCue || line.vocalSide != null) line
        else line.copy(vocalSide = -1)
    }
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
private val LRC_RIGHT_ALIGNMENT_MARKER = Regex("""(?i)^<R>\s*""")
private val BACKGROUND_VOCALS = Regex("""\(([^()]+)\)""")
private val WHITESPACE = Regex("\\s+")
private val TTML_CLOCK = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})(?:[.,](\d+))?$""")
private val TTML_FRAME_CLOCK = Regex("""^(\d+):(\d{2}):(\d{2}):(\d+)(?:\.(\d+))?$""")
private val TTML_OFFSET = Regex("""^([+-]?(?:\d+(?:\.\d+)?|\.\d+))(h|m|s|ms|f|t)$""", RegexOption.IGNORE_CASE)
private val TTML_TIMING_ELEMENTS = setOf("tt", "body", "div", "p", "span", "br")
private val TTML_BACKGROUND_ROLES = setOf("x-bg", "bg", "background", "backing", "secondary", "background-vocals", "backing-vocals")
private val LYRICS_TAGS = setOf("LYRIC", "LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")
private const val MAX_LYRIC_SOURCES = 32
private const val MAX_LYRIC_CHARS = 128_000
private const val MAX_LYRIC_LINES = 5_000
private const val MAX_LYRIC_OFFSET_MS = 86_400_000L
private const val SYLT_LINE_GAP_MS = 2_200L
private const val TTML_BACKGROUND_PAIR_WINDOW_MS = 2_000L
