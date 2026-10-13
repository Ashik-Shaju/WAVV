package com.wavv.app

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.util.Locale

internal data class AudioQualityDetails(
    val mimeType: String?,
    val format: String,
    val bitDepth: Int?,
    val sampleRateHz: Int?,
    val bitrateBps: Long?,
    val bitrateIsEstimated: Boolean,
    val channels: Int?,
    val isLossless: Boolean,
    val uhqEligible: Boolean,
    val container: String?,
    val fileSizeBytes: Long,
    val durationMs: Long,
) {
    val qualityName: String
        get() {
            // Only assign Hi-Res Lossless when both the lossless codec and
            // high-resolution source parameters are verified. Lossy formats
            // can qualify only for High Quality using their bitrate and rate.
            // A standalone Hi-Res label needs source-quality provenance, which
            // this file metadata does not provide.
            val verifiedHiResLossless = isLossless &&
                (bitDepth ?: 0) >= 24 &&
                (sampleRateHz ?: 0) >= 48_000
            val higherQualityLossy = !isLossless &&
                isKnownLossyFormat(mimeType, format) &&
                !bitrateIsEstimated &&
                (bitrateBps ?: 0L) >= 320_000L &&
                (sampleRateHz ?: 0) >= 44_100 &&
                (bitDepth == null || bitDepth >= 16)
            return when {
                verifiedHiResLossless -> "Hi-Res Lossless"
                isLossless -> "Lossless"
                higherQualityLossy -> "High Quality"
                else -> "Standard"
            }
        }

    fun formattedSourceSpec(): String = buildList {
        bitDepth?.let { add("${it}-bit") }
        sampleRateHz?.let { add(formatSampleRate(it)) }
        add(format)
    }.joinToString("/")

    fun formattedBitrate(): String = bitrateBps?.let {
        String.format(Locale.ROOT, "%.1f kbps", it / 1_000.0)
    } ?: "Not reported"
}

internal suspend fun readAudioQualityDetails(context: Context, song: Song): AudioQualityDetails {
    val uri = Uri.parse(song.uri)
    var mime: String? = null
    var sampleRate: Int? = song.sampleRate?.takeIf { it > 0 }
    var channels: Int? = song.channels?.takeIf { it > 0 }
    var bitDepth: Int? = null
    var bitrate: Long? = null
    var retriever: MediaMetadataRetriever? = null
    try {
        retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bitDepth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
        }
    } catch (_: Exception) {
        // Some document providers do not expose retriever metadata; MediaExtractor is tried below.
    } finally {
        runCatching { retriever?.release() }
    }

    var container: String? = song.container
    var extractor: MediaExtractor? = null
    try {
        extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!trackMime.startsWith("audio/")) continue
            mime = trackMime
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
            val sourceBitDepth = sequenceOf("bits-per-sample", "bits-per-raw-sample")
                .firstNotNullOfOrNull { key ->
                    if (format.containsKey(key)) format.getInteger(key).takeIf { it in 1..64 } else null
                }
            if (sourceBitDepth != null) {
                // Prefer the encoded track's declared depth over decoder/output PCM format.
                bitDepth = sourceBitDepth
            } else if (
                trackMime.lowercase(Locale.ROOT) in setOf("audio/raw", "audio/wav", "audio/x-wav", "audio/pcm") &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                format.containsKey(MediaFormat.KEY_PCM_ENCODING)
            ) {
                // PCM encoding is source depth only for raw PCM tracks. On compressed tracks
                // it can describe the decoder's 16-bit output, not the file's original depth.
                bitDepth = pcmEncodingBitDepth(format.getInteger(MediaFormat.KEY_PCM_ENCODING)) ?: bitDepth
            }
            break
        }
    } catch (_: Exception) {
        // Keep the metadata that MediaMetadataRetriever or the library index provided.
    } finally {
        runCatching { extractor?.release() }
    }

    val codecName = audioFormatName(mime, song.codec, song.uri)
    val lossless = isLosslessFormat(mime, codecName)
    val estimatedBitrate = bitrate ?: if (song.durationMs > 0 && song.fileSizeBytes > 0) {
        song.fileSizeBytes * 8_000L / song.durationMs
    } else null
    val verifiedBitrate = bitrate != null
    return AudioQualityDetails(
        mimeType = mime,
        format = codecName,
        bitDepth = bitDepth,
        sampleRateHz = sampleRate,
        bitrateBps = estimatedBitrate,
        bitrateIsEstimated = !verifiedBitrate && estimatedBitrate != null,
        channels = channels,
        isLossless = lossless,
        uhqEligible = mime == UHQ_AAC_MIME_TYPE && sampleRate?.let { it in 8_000..48_000 } == true && channels?.let { it in 1..2 } == true,
        container = container?.substringAfterLast('/'),
        fileSizeBytes = song.fileSizeBytes,
        durationMs = song.durationMs,
    )
}

private fun audioFormatName(mime: String?, indexedCodec: String?, uri: String): String {
    val fromMime = when (mime?.lowercase(Locale.ROOT)) {
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/alac" -> "ALAC"
        "audio/mp4a-latm", "audio/aac", "audio/aac-adts" -> "AAC"
        "audio/mpeg" -> "MP3"
        "audio/opus", "audio/ogg" -> "Opus"
        "audio/raw", "audio/wav", "audio/x-wav", "audio/pcm" -> "PCM"
        "audio/ape" -> "APE"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        else -> null
    }
    if (fromMime != null) return fromMime
    val ext = uri.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (ext) {
        "flac" -> "FLAC"
        "alac" -> "ALAC"
        "wav", "wave", "aiff", "aif" -> "PCM"
        "mp3" -> "MP3"
        "aac", "m4a" -> "AAC"
        "opus", "ogg" -> "Opus"
        else -> indexedCodec?.uppercase(Locale.ROOT)?.takeIf { it.length <= 12 } ?: "Unknown codec"
    }
}

private fun isLosslessFormat(mime: String?, format: String): Boolean {
    if (mime in setOf(UHQ_AAC_MIME_TYPE, "audio/aac", "audio/aac-adts", "audio/mpeg", "audio/opus", "audio/ogg")) return false
    return format in setOf("FLAC", "ALAC", "PCM", "APE", "WAVPACK", "WAV", "AIFF") ||
        mime in setOf("audio/flac", "audio/x-flac", "audio/alac", "audio/raw", "audio/wav", "audio/x-wav", "audio/pcm", "audio/ape")
}

private fun isKnownLossyFormat(mime: String?, format: String): Boolean {
    val lossyMimeTypes = setOf(
        "audio/mpeg",
        "audio/mp4a-latm",
        "audio/aac",
        "audio/aac-adts",
        "audio/aacp",
        "audio/opus",
        "audio/ogg",
        "audio/ac3",
        "audio/eac3",
        "audio/amr",
        "audio/amr-wb",
    )
    if (mime?.lowercase(Locale.ROOT) in lossyMimeTypes) return true
    return format.uppercase(Locale.ROOT) in setOf(
        "MP3",
        "AAC",
        "OPUS",
        "VORBIS",
        "AC-3",
        "E-AC-3",
        "AMR",
        "AMR-WB",
        "WMA",
    )
}

private fun pcmEncodingBitDepth(encoding: Int): Int? = when (encoding) {
    android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
    android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
    android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
    android.media.AudioFormat.ENCODING_PCM_32BIT,
    android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
    else -> null
}

internal fun formatSampleRate(sampleRateHz: Int): String =
    if (sampleRateHz % 1_000 == 0) "${sampleRateHz / 1_000} kHz"
    else String.format(Locale.ROOT, "%.1f kHz", sampleRateHz / 1_000f)
