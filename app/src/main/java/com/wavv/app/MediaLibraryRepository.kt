package com.wavv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface LibrarySource {
    data object AllDevice : LibrarySource
    data class Files(val uris: List<String>) : LibrarySource
    data class Folder(val uri: String) : LibrarySource
    data class Folders(val uris: List<String>) : LibrarySource
}

class MediaLibraryRepository(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver
    private val metadataContext = context.applicationContext

    internal suspend fun readLanguageTags(song: Song): List<String> =
        readEmbeddedLanguageTags(metadataContext, Uri.parse(song.uri))

    fun pruneEmbeddedArtworkCache(songs: List<Song>) {
        val artworkUris = songs.mapNotNull(Song::albumArtUri)
        pruneEmbeddedArtworkFiles(File(metadataContext.filesDir, "embedded-artwork"), artworkUris)
    }

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
            is LibrarySource.Folder -> loadFolders(listOf(source.uri), onProgress)
            is LibrarySource.Folders -> loadFolders(source.uris, onProgress)
        }
    }

    private suspend fun loadFolders(
        rawUris: List<String>,
        onProgress: suspend (completed: Int, total: Int?) -> Unit,
    ): List<Song> = buildList {
        var completed = 0
        rawUris.distinct().forEach { rawUri ->
            coroutineContext.ensureActive()
            val treeUri = Uri.parse(rawUri)
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
        }
    }.distinctBy(Song::uri).sortedBy(Song::title)

    private suspend fun loadDeviceSongs(
        onProgress: suspend (completed: Int, total: Int?) -> Unit,
    ): List<Song> {
        val genres = try {
            readDeviceGenres()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }
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
            MediaStore.Audio.AudioColumns.COMPOSER,
            MediaStore.Audio.AudioColumns.ALBUM_ARTIST,
            MediaStore.Audio.AudioColumns.YEAR,
            MediaStore.Audio.AudioColumns.TRACK,
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND ${MediaStore.MediaColumns.IS_PENDING} = 0")
            }
        }

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: throw IOException("Could not read device audio library")

        return buildList {
            cursor.use {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val modifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val mimeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val composerColumn = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.COMPOSER)
                val albumArtistColumn = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ARTIST)
                val yearColumn = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.YEAR)
                val trackColumn = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.TRACK)
                val albumArtwork = mutableMapOf<Long, String?>()
                var completed = 0

                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val id = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val mediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString())
                        .build()
                    val artworkKey = albumId.takeIf { it > 0L } ?: -id
                    val embedded = extractEmbeddedAudioMetadata(mediaUri)
                    val artUri = if (albumArtwork.containsKey(artworkKey)) albumArtwork[artworkKey] else {
                        (embedded.artworkUri ?: albumArtUri(albumId).takeIf { albumId > 0L }?.toString())
                            .also { albumArtwork[artworkKey] = it }
                    }
                    add(
                        Song(
                            id = id,
                            title = cursor.getString(titleColumn).orFallback("Unknown title"),
                            artist = cursor.getString(artistColumn).orFallback("Unknown artist"),
                            album = cursor.getString(albumColumn).orFallback("Unknown album"),
                            durationMs = cursor.getLong(durationColumn),
                            uri = mediaUri.toString(),
                            albumArtUri = artUri,
                            fileSizeBytes = cursor.getLongOrZero(sizeColumn),
                            modifiedAt = cursor.getLongOrZero(modifiedColumn) * 1_000L,
                            codec = cursor.getStringOrNull(mimeColumn)?.substringAfterLast('/')?.ifBlank { null },
                            container = cursor.getStringOrNull(mimeColumn),
                            metadataSource = "embedded",
                            genre = genres[id]?.firstOrNull(),
                            genres = genres[id].orEmpty(),
                            composer = cursor.getStringOrNull(composerColumn)?.trim()?.ifBlank { null } ?: embedded.composer,
                            albumArtist = cursor.getStringOrNull(albumArtistColumn)?.trim()?.ifBlank { null } ?: embedded.albumArtist,
                            year = cursor.getStringOrNull(yearColumn).toMetadataNumber() ?: embedded.year,
                            trackNumber = cursor.getStringOrNull(trackColumn).toMetadataNumber() ?: embedded.trackNumber,
                            discNumber = embedded.discNumber,
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

    private suspend fun readDeviceGenres(): Map<Long, List<String>> {
        val genresByAudioId = mutableMapOf<Long, MutableList<String>>()
        val cursor = contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null,
        ) ?: return emptyMap()
        cursor.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val genreId = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)?.trim().orEmpty()
                if (name.isBlank()) continue
                contentResolver.query(
                    MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null,
                    null,
                    null,
                )?.use { members ->
                    val audioIdColumn = members.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    while (members.moveToNext()) {
                        coroutineContext.ensureActive()
                        genresByAudioId.getOrPut(members.getLong(audioIdColumn)) { mutableListOf() }.add(name)
                    }
                }
            }
        }
        return genresByAudioId.mapValues { (_, names) ->
            names.distinctBy { it.lowercase(java.util.Locale.ROOT) }
        }
    }

    private data class EmbeddedAudioMetadata(
        val artworkUri: String? = null,
        val albumArtist: String? = null,
        val composer: String? = null,
        val year: Int? = null,
        val trackNumber: Int? = null,
        val discNumber: Int? = null,
    )

    private fun extractEmbeddedAudioMetadata(mediaUri: Uri): EmbeddedAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(metadataContext, mediaUri)
            EmbeddedAudioMetadata(
                artworkUri = cacheEmbeddedArtwork(retriever.embeddedPicture),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.ifBlank { null },
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.trim()?.ifBlank { null },
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR).toMetadataNumber(),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).toMetadataNumber(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).toMetadataNumber(),
            )
        } catch (_: Exception) {
            EmbeddedAudioMetadata()
        } finally {
            retriever.release()
        }
    }

    private suspend fun collectFolder(
        treeUri: Uri,
        documentId: String,
        songs: MutableList<Song>,
        visited: MutableSet<String>,
        onAudioProcessed: suspend () -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        ) ?: throw IOException("Could not read selected music folder")
        cursor.use {
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
        var genre: String? = null
        var albumArtUri: String? = null
        var metadataSource = "filename"
        var albumArtist: String? = null
        var composer: String? = null
        var year: Int? = null
        var trackNumber: Int? = null
        var discNumber: Int? = null
        val documentStats = try {
            documentStats(uri)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
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
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()?.ifBlank { null }
            albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.ifBlank { null }
            composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.trim()?.ifBlank { null }
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR).toMetadataNumber()
            trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).toMetadataNumber()
            discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).toMetadataNumber()
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            albumArtUri = runCatching { cacheEmbeddedArtwork(retriever.embeddedPicture) }.getOrNull()
            metadataSource = "embedded"
            Song(
                id = stableSongId(uri.toString()),
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                uri = uri.toString(),
                albumArtUri = albumArtUri,
                fileSizeBytes = documentStats.size,
                modifiedAt = documentStats.modifiedAt,
                codec = codec,
                container = container,
                sampleRate = sampleRate,
                metadataSource = metadataSource,
                genre = genre,
                genres = parseGenreMetadata(genre),
                albumArtist = albumArtist,
                composer = composer,
                year = year,
                trackNumber = trackNumber,
                discNumber = discNumber,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            throw error
        } catch (_: Exception) {
            Song(
                id = stableSongId(uri.toString()),
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                uri = uri.toString(),
                albumArtUri = albumArtUri,
                fileSizeBytes = documentStats.size,
                modifiedAt = documentStats.modifiedAt,
                codec = codec,
                container = container,
                sampleRate = sampleRate,
                metadataSource = metadataSource,
                genre = genre,
                genres = parseGenreMetadata(genre),
                albumArtist = albumArtist,
                composer = composer,
                year = year,
                trackNumber = trackNumber,
                discNumber = discNumber,
            )
        } finally {
            retriever.release()
        }
    }

    private fun cacheEmbeddedArtwork(data: ByteArray?): String? {
        if (data == null || data.size !in 1..MAX_ARTWORK_BYTES) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        // ponytail: deduplicated 640px artwork stays in filesDir so persisted song URIs survive cache eviction; prune if source churn accumulates storage.
        val directory = File(metadataContext.filesDir, "embedded-artwork")
        if (!directory.isDirectory && !directory.mkdirs()) return null
        val destination = File(directory, "$digest.img")
        if (destination.isFile) return Uri.fromFile(destination).toString()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longestEdge / sampleSize > MAX_EMBEDDED_ART_EDGE) sampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null

        return try {
            val temporary = File.createTempFile("$digest.", ".tmp", directory)
            try {
                val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val quality = if (format == Bitmap.CompressFormat.JPEG) 88 else 100
                val saved = temporary.outputStream().use { bitmap.compress(format, quality, it) }
                if (!saved) return null
                if (!temporary.renameTo(destination) && !destination.isFile) return null
                Uri.fromFile(destination).toString()
            } finally {
                temporary.delete()
            }
        } finally {
            bitmap.recycle()
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

private fun String?.toMetadataNumber(): Int? = this?.substringBefore('/')?.trim()?.toIntOrNull()

internal fun pruneEmbeddedArtworkFiles(directory: File, referencedArtworkUris: Collection<String>): Int {
    if (!directory.isDirectory) return 0
    val referencedPaths = mutableSetOf<String>()
    for (uri in referencedArtworkUris) {
        val fileUri = runCatching { URI(uri) }.getOrNull() ?: return 0
        if (fileUri.scheme != "file") continue
        val path = runCatching { File(fileUri).canonicalPath }.getOrNull() ?: return 0
        referencedPaths += path
    }
    return directory.listFiles().orEmpty().count { file ->
        val shouldDelete = when (file.extension) {
            "tmp" -> true
            "img" -> runCatching { file.canonicalPath !in referencedPaths }.getOrDefault(false)
            else -> false
        }
        shouldDelete && file.delete()
    }
}

internal fun isAudioDocument(name: String, mimeType: String): Boolean =
    mimeType.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExtensions

internal fun stableSongId(value: String): Long {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return (ByteBuffer.wrap(digest).long and Long.MAX_VALUE).coerceAtLeast(1L)
}

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

internal const val MAX_ARTWORK_BYTES = 12 * 1024 * 1024
private const val MAX_EMBEDDED_ART_EDGE = 640
