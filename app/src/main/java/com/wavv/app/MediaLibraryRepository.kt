package com.wavv.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface LibrarySource {
    data object AllDevice : LibrarySource
    data class Files(val uris: List<String>) : LibrarySource
    data class Folder(val uri: String) : LibrarySource
}

class MediaLibraryRepository(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver
    private val metadataContext = context.applicationContext

    suspend fun loadSongs(
        source: LibrarySource,
        onProgress: suspend (completed: Int, total: Int?) -> Unit = { _, _ -> },
    ): List<Song> = withContext(Dispatchers.IO) {
        when (source) {
            LibrarySource.AllDevice -> loadDeviceSongs(onProgress)
            is LibrarySource.Files -> buildList {
                source.uris.forEachIndexed { index, rawUri ->
                    coroutineContext.ensureActive()
                    val uri = Uri.parse(rawUri)
                    readDocumentSong(uri, uri.lastPathSegment ?: "Audio")?.let(::add)
                    onProgress(index + 1, source.uris.size)
                }
            }.sortedBy(Song::title)
            is LibrarySource.Folder -> buildList {
                val treeUri = Uri.parse(source.uri)
                var completed = 0
                collectFolder(
                    treeUri = treeUri,
                    documentId = DocumentsContract.getTreeDocumentId(treeUri),
                    songs = this,
                    visited = mutableSetOf(),
                    onAudioProcessed = {
                        completed += 1
                        onProgress(completed, null)
                    },
                )
            }.distinctBy(Song::uri).sortedBy(Song::title)
        }
    }

    private suspend fun loadDeviceSongs(
        onProgress: suspend (completed: Int, total: Int?) -> Unit,
    ): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND ${MediaStore.MediaColumns.IS_PENDING} = 0")
            }
        }

        return buildList {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val modifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val mimeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                var completed = 0

                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val id = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    add(
                        Song(
                            id = id,
                            title = cursor.getString(titleColumn).orFallback("Unknown title"),
                            artist = cursor.getString(artistColumn).orFallback("Unknown artist"),
                            album = cursor.getString(albumColumn).orFallback("Unknown album"),
                            durationMs = cursor.getLong(durationColumn),
                            uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                                .appendPath(id.toString())
                                .build()
                                .toString(),
                            albumArtUri = albumArtUri(albumId).takeIf { albumId > 0L }?.toString(),
                            fileSizeBytes = cursor.getLongOrZero(sizeColumn),
                            modifiedAt = cursor.getLongOrZero(modifiedColumn) * 1_000L,
                            codec = cursor.getStringOrNull(mimeColumn)?.substringAfterLast('/')?.ifBlank { null },
                            container = cursor.getStringOrNull(mimeColumn),
                            metadataSource = "embedded",
                        ),
                    )
                    completed += 1
                    onProgress(completed, cursor.count.takeIf { it >= 0 })
                }
            }
        }
    }

    private fun albumArtUri(albumId: Long) =
        Uri.parse("content://media/external/audio/albumart/$albumId")

    private suspend fun collectFolder(
        treeUri: Uri,
        documentId: String,
        songs: MutableList<Song>,
        visited: MutableSet<String>,
        onAudioProcessed: suspend () -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val documentId = cursor.getString(idColumn)
                if (!visited.add(documentId)) continue
                val name = cursor.getString(nameColumn).orFallback("Audio")
                val mimeType = cursor.getString(mimeColumn).orEmpty()
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectFolder(treeUri, documentId, songs, visited, onAudioProcessed)
                } else if (isAudioDocument(name, mimeType)) {
                    readDocumentSong(documentUri, name)?.let(songs::add)
                    onAudioProcessed()
                }
            }
        }
    }

    private suspend fun readDocumentSong(uri: Uri, displayName: String): Song? {
        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Unknown title" }
        var title = fallbackTitle
        var artist = "Unknown artist"
        var album = "Unknown album"
        var durationMs = 0L
        var codec: String? = null
        var container: String? = null
        var sampleRate: Int? = null
        var metadataSource = "filename"
        val documentStats = try {
            documentStats(uri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DocumentStats()
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(metadataContext, uri)
            container = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            codec = container?.substringAfterLast('/')?.ifBlank { null }
            sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orFallback(fallbackTitle)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orFallback(artist)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orFallback(album)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            metadataSource = "embedded"
            Song(
                id = stableSongId(uri.toString()),
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                uri = uri.toString(),
                albumArtUri = null,
                fileSizeBytes = documentStats.size,
                modifiedAt = documentStats.modifiedAt,
                codec = codec,
                container = container,
                sampleRate = sampleRate,
                metadataSource = metadataSource,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Song(
                id = stableSongId(uri.toString()),
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                uri = uri.toString(),
                albumArtUri = null,
                fileSizeBytes = documentStats.size,
                modifiedAt = documentStats.modifiedAt,
                codec = codec,
                container = container,
                sampleRate = sampleRate,
                metadataSource = metadataSource,
            )
        } finally {
            retriever.release()
        }
    }

    private fun documentStats(uri: Uri): DocumentStats = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
        null,
        null,
        null,
    )?.use { cursor ->
        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
        val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        if (cursor.moveToFirst()) {
            DocumentStats(cursor.getLongOrZero(sizeColumn), cursor.getLongOrZero(modifiedColumn))
        } else {
            DocumentStats()
        }
    } ?: DocumentStats()

    private data class DocumentStats(val size: Long = 0L, val modifiedAt: Long = 0L)

    private fun android.database.Cursor.getLongOrZero(column: Int): Long = if (column >= 0 && !isNull(column)) getLong(column) else 0L

    private fun android.database.Cursor.getStringOrNull(column: Int): String? = if (column >= 0 && !isNull(column)) getString(column) else null

    private fun String?.orFallback(fallback: String) = takeUnless { it.isNullOrBlank() } ?: fallback
}

internal fun isAudioDocument(name: String, mimeType: String): Boolean =
    mimeType.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExtensions

internal fun stableSongId(value: String): Long = value.fold(17L) { hash, character -> hash * 31 + character.code }

private val audioExtensions = setOf(
    "3gp",
    "aac",
    "ac3",
    "aif",
    "aiff",
    "alac",
    "ape",
    "amr",
    "caf",
    "dff",
    "dsf",
    "flac",
    "m4a",
    "m4b",
    "mka",
    "mkv",
    "mid",
    "midi",
    "mp3",
    "mp4",
    "oga",
    "ogg",
    "opus",
    "wav",
    "webm",
    "wma",
    "wv",
)
