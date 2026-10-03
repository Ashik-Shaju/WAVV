package com.wavv.app

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

@OptIn(markerClass = [UnstableApi::class])
internal suspend fun readEmbeddedLanguageTags(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
    val retriever = MetadataRetriever.Builder(context.applicationContext, MediaItem.fromUri(uri)).build()
    try {
        val groups = runInterruptible { retriever.retrieveTrackGroups().get() }
        val fields = buildList {
            repeat(groups.length) { groupIndex ->
                val group = groups[groupIndex]
                repeat(group.length) { trackIndex ->
                    val format = group.getFormat(trackIndex)
                    addAll(
                        trackLanguageFields(
                            formatLanguage = format.language,
                            metadataFields = format.metadata?.let(::languageFields).orEmpty(),
                        ),
                    )
                }
            }
        }
        extractExplicitLanguageTags(fields)
    } finally {
        retriever.close()
    }
}

internal fun trackLanguageFields(
    formatLanguage: String?,
    metadataFields: List<TextMetadataField>,
): List<TextMetadataField> = buildList {
    formatLanguage
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
        ?.let { add(TextMetadataField("LANGUAGE", it)) }
    addAll(metadataFields)
}

@OptIn(markerClass = [UnstableApi::class])
private fun languageFields(metadata: Metadata): List<TextMetadataField> = buildList {
    repeat(metadata.length()) { index ->
        when (val entry = metadata[index]) {
            is TextInformationFrame -> when {
                entry.id == "TLAN" -> entry.values.forEach { add(TextMetadataField("TLAN", it)) }
                entry.id == "TXXX" && isLanguageKey(entry.description) -> entry.values.forEach {
                    add(TextMetadataField("TXXX:${entry.description}", it))
                }
            }
            is VorbisComment -> if (isLanguageKey(entry.key)) add(TextMetadataField(entry.key, entry.value))
            is CommentFrame -> if (isLanguageKey(entry.description)) add(TextMetadataField(entry.description, entry.text))
        }
    }
}

private fun isLanguageKey(value: String?): Boolean = value?.let {
    it.equals("language", true) || it.equals("lang", true) || it.equals("language_code", true)
} == true
