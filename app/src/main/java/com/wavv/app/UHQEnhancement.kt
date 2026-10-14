package com.wavv.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val UHQ_CORRECTION_SCALE = 1f
internal const val MAX_UHQ_AUDIO_BYTES = 1024L * 1024L * 1024L
internal const val UHQ_AAC_MIME_TYPE = "audio/mp4a-latm"

internal fun isSupportedUhqMimeType(mimeType: String): Boolean = mimeType == UHQ_AAC_MIME_TYPE

internal fun isUhqAudioWithinLimit(
    frames: Long,
    channels: Int,
    bytesPerSample: Int,
    maximumBytes: Long = MAX_UHQ_AUDIO_BYTES,
): Boolean {
    if (frames < 0L || channels !in 1..2 || bytesPerSample !in 1..4 || maximumBytes < 0L) return false
    return frames <= maximumBytes / (channels.toLong() * bytesPerSample)
}

internal object UHQPlaybackPreference {
    private const val PREFERENCES = "playback_preferences"
    private const val ENABLED = "uhq_aac_enabled"
    private const val TRACK_OVERRIDE = "uhq_track_override"

    data class TrackOverride(val songId: Long, val enabled: Boolean)

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun trackOverride(context: Context): TrackOverride? {
        val encoded = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(TRACK_OVERRIDE, null) ?: return null
        val parts = encoded.split(':', limit = 2)
        val songId = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val enabled = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: return null
        return TrackOverride(songId, enabled)
    }

    fun setTrackOverride(context: Context, songId: Long, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(TRACK_OVERRIDE, "$songId:$enabled")
            .apply()
    }

    fun clearTrackOverride(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(TRACK_OVERRIDE).apply()
    }

    fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    fun preferenceKey() = ENABLED
    fun trackOverrideKey() = TRACK_OVERRIDE
}

internal object UHQAacCache {
    const val MODEL_ID = "uhq-aac-v1"
    private const val CACHE_FOLDER = "uhq/aac-v1"
    private const val MAX_CACHE_BYTES = MAX_UHQ_AUDIO_BYTES

    fun key(context: Context, uri: Uri): String? {
        val metadata = if (uri.scheme == "file") {
            uri.path?.let(::File)?.takeIf(File::isFile)?.let { it.length() to it.lastModified() }
        } else {
            queryMetadata(context, uri, MediaStore.MediaColumns.DATE_MODIFIED, 1_000L)
                ?: queryMetadata(context, uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED, 1L)
        }
        val (size, modified) = metadata ?: return null
        if (size <= 0L || modified <= 0L) return null
        return sha256("$MODEL_ID\n$UHQ_CORRECTION_SCALE\n$uri\n$size\n$modified")
    }

    fun file(context: Context, key: String): File = File(File(context.cacheDir, CACHE_FOLDER), "$key.wav")

    fun workName(key: String): String = "$MODEL_ID-$key"

    fun trim(requiredBytes: Long, replacing: File): Boolean {
        if (requiredBytes > MAX_CACHE_BYTES) return false
        val directory = replacing.parentFile ?: return false
        val cached = directory.listFiles { file -> file.extension == "wav" && file != replacing }.orEmpty()
            .sortedBy(File::lastModified)
        var total = cached.sumOf(File::length) + if (replacing.isFile) replacing.length() else 0L
        for (file in cached) {
            if (total + requiredBytes <= MAX_CACHE_BYTES) break
            val size = file.length()
            if (file.delete()) total -= size
        }
        return total + requiredBytes <= MAX_CACHE_BYTES
    }

    fun cleanupStaleTemporaryFiles(context: Context) {
        val cutoff = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MS
        context.cacheDir.listFiles {
            (it.name.startsWith("uhq-decoded-") || it.name.startsWith("uhq-resampled-")) && it.name.endsWith(".pcm.part")
        }
            .orEmpty()
            .filter { it.lastModified() in 1 until cutoff }
            .forEach(File::delete)
        File(context.cacheDir, CACHE_FOLDER).listFiles { file -> file.name.endsWith(".wav.part") }
            .orEmpty()
            .filter { it.lastModified() in 1 until cutoff }
            .forEach(File::delete)
    }

    private fun queryMetadata(context: Context, uri: Uri, modifiedColumn: String, multiplier: Long): Pair<Long, Long>? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE, modifiedColumn), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndex(modifiedColumn)
                if (sizeIndex < 0 || modifiedIndex < 0 || cursor.isNull(sizeIndex) || cursor.isNull(modifiedIndex)) return@use null
                cursor.getLong(sizeIndex) to (cursor.getLong(modifiedIndex) * multiplier)
            }
        }.getOrNull()

    private const val TEMP_FILE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private object UHQAacModel {
    private const val ASSET = "uhq/aac-v1/uhq_aac.onnx"
    private const val SHA256 = "47f275077a89bf91bf02aff0d64bde56246845d88ce718865cdaab625f725098"
    private const val MODEL_FILE = "uhq_aac.onnx"

    @Synchronized
    fun install(context: Context): File {
        val directory = File(context.filesDir, "uhq/${UHQAacCache.MODEL_ID}").apply { mkdirs() }
        val model = File(directory, MODEL_FILE)
        if (model.isFile && sha256(model) == SHA256) return model
        model.delete()
        val part = File(directory, "$MODEL_FILE.part")
        part.delete()
        context.assets.open(ASSET).use { input ->
            FileOutputStream(part).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        check(sha256(part) == SHA256) { "UHQ AAC model asset hash mismatch" }
        check(part.renameTo(model)) { "Couldn't install UHQ AAC model" }
        return model
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal class UHQEnhancementWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        UHQAacCache.cleanupStaleTemporaryFiles(applicationContext)
        val source = inputData.getString(INPUT_URI)?.let(Uri::parse) ?: return Result.failure()
        val key = inputData.getString(INPUT_KEY) ?: return Result.failure()
        val output = UHQAacCache.file(applicationContext, key)
        if (output.isFile && output.length() > WAV_HEADER_BYTES) {
            if (output.length() <= MAX_UHQ_AUDIO_BYTES) return Result.success()
            output.delete()
        }
        output.parentFile?.mkdirs()
        val temporary = File(output.parentFile, "${output.name}.part")
        temporary.delete()
        return try {
            setProgress(workDataOf(KEY_STAGE to "Starting", KEY_PROGRESS to 1))
            UHQAacEnhancer(applicationContext).enhance(source, temporary) { stage, percent ->
                setProgress(workDataOf(KEY_STAGE to stage, KEY_PROGRESS to percent))
            }
            check(temporary.isFile && temporary.length() > WAV_HEADER_BYTES) { "UHQ produced no audio" }
            if (!UHQAacCache.trim(temporary.length(), output)) {
                temporary.delete()
                return Result.failure()
            }
            output.delete()
            check(temporary.renameTo(output)) { "Couldn't publish UHQ cache file" }
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (error: Exception) {
            temporary.delete()
            Log.w(TAG, "UHQ AAC enhancement failed; playback will use the source file", error)
            Result.failure()
        }
    }

    companion object {
        const val INPUT_URI = "source_uri"
        const val INPUT_KEY = "cache_key"
        const val KEY_PROGRESS = "uhq_progress"
        const val KEY_STAGE = "uhq_stage"
        private const val TAG = "WavvUHQ"
    }
}

private const val MIN_UHQ_SAMPLE_RATE = 8_000
private const val MAX_UHQ_SAMPLE_RATE = 48_000

private class StreamingStereoResampler(
    inputRate: Int,
    outputRate: Int,
    private val emit: (Float, Float) -> Unit,
) {
    private val direct = inputRate == outputRate
    private val step = inputRate.toDouble() / outputRate
    private val samples = FloatArray(RING_FRAMES * 2)
    private val kernels = if (direct) emptyArray() else buildKernels(inputRate, outputRate)
    private var inputFrames = 0L
    private var outputFrames = 0L
    private var nextPosition = 0.0

    init {
        require(inputRate in MIN_UHQ_SAMPLE_RATE..MAX_UHQ_SAMPLE_RATE) {
            "Unsupported AAC sample rate: $inputRate Hz"
        }
    }

    fun push(left: Float, right: Float) {
        val offset = (inputFrames % RING_FRAMES).toInt() * 2
        samples[offset] = left
        samples[offset + 1] = right
        inputFrames++
        if (direct) {
            emit(left, right)
            outputFrames++
        } else {
            while (nextPosition + RADIUS < inputFrames) emitNext(final = false)
        }
    }

    fun finish(): Long {
        if (!direct) while (nextPosition < inputFrames) emitNext(final = true)
        return outputFrames
    }

    private fun emitNext(final: Boolean) {
        val center = nextPosition.toLong()
        if (!final && center + RADIUS >= inputFrames) return
        val phase = ((nextPosition - center) * PHASES).toInt().coerceIn(0, PHASES - 1)
        val kernel = kernels[phase]
        var left = 0.0
        var right = 0.0
        for (tap in 0 until TAPS) {
            val absoluteIndex = (center - RADIUS + 1 + tap).coerceIn(0L, inputFrames - 1)
            val offset = (absoluteIndex % RING_FRAMES).toInt() * 2
            val coefficient = kernel[tap]
            left += samples[offset] * coefficient
            right += samples[offset + 1] * coefficient
        }
        emit(left.toFloat(), right.toFloat())
        outputFrames++
        nextPosition += step
    }

    private fun buildKernels(inputRate: Int, outputRate: Int): Array<FloatArray> {
        require(inputRate in MIN_UHQ_SAMPLE_RATE..MAX_UHQ_SAMPLE_RATE) {
            "Unsupported AAC sample rate: $inputRate Hz"
        }
        val cutoff = 0.5 * min(1.0, outputRate.toDouble() / inputRate) * 0.94
        return Array(PHASES) { phase ->
            val fraction = phase.toDouble() / PHASES
            val weights = FloatArray(TAPS)
            var sum = 0.0
            for (tap in 0 until TAPS) {
                val distance = tap - RADIUS + 1 - fraction
                val normalized = distance / RADIUS
                val window = if (normalized <= -1.0 || normalized >= 1.0) 0.0 else
                    0.42 + 0.5 * cos(PI * normalized) + 0.08 * cos(2.0 * PI * normalized)
                val argument = 2.0 * cutoff * distance
                val sinc = if (argument == 0.0) 1.0 else sin(PI * argument) / (PI * argument)
                val weight = 2.0 * cutoff * sinc * window
                weights[tap] = weight.toFloat()
                sum += weight
            }
            for (tap in weights.indices) weights[tap] = (weights[tap] / sum).toFloat()
            weights
        }
    }

    private companion object {
        const val RADIUS = 16
        const val TAPS = RADIUS * 2
        const val PHASES = 1024
        const val RING_FRAMES = 1024
    }
}

internal class UHQAacEnhancer(private val context: Context) {
    suspend fun enhance(source: Uri, output: File, onProgress: suspend (stage: String, percent: Int) -> Unit) {
        val raw = File(context.cacheDir, "uhq-decoded-${System.nanoTime()}.pcm.part")
        val modelInput = File(context.cacheDir, "uhq-resampled-${System.nanoTime()}.pcm.part")
        try {
            onProgress("Decoding AAC", 3)
            val decoded = decodeToFloatFile(source, raw)
            onProgress("Preparing audio", 15)
            val inputFile = if (decoded.sampleRate == SAMPLE_RATE && decoded.channels == CHANNELS) raw else modelInput
            val sampleCount = if (inputFile === raw) decoded.frames else resampleToModelPcm(raw, modelInput, decoded)
            check(sampleCount >= FFT_SIZE) { "AAC is too short for UHQ" }
            onProgress("Loading UHQ model", 20)
            val model = UHQAacModel.install(context)
            OrtSessionRuntime.create(model, preferXnnpack = true).use { runtime ->
                onProgress("Enhancing audio", 25)
                enhanceTiles(inputFile, sampleCount, output, runtime.session) { completed, total ->
                    onProgress("Enhancing audio", 25 + completed * 70 / total)
                }
            }
            onProgress("Finalizing", 98)
        } finally {
            raw.delete()
            modelInput.delete()
        }
    }

    private suspend fun decodeToFloatFile(source: Uri, destination: File): DecodedAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME type missing")
            require(isSupportedUhqMimeType(mime)) { "UHQ AAC model only accepts AAC audio" }
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            extractor.selectTrack(track)
            val codec = MediaCodec.createDecoderByType(mime)
            var started = false
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                started = true
                decode(codec, extractor, destination, sampleRate, channels)
            } finally {
                if (started) runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private suspend fun decode(
        codec: MediaCodec,
        extractor: MediaExtractor,
        destination: File,
        sampleRate: Int,
        channels: Int,
    ): DecodedAudio = destination.outputStream().buffered(256 * 1024).use { stream ->
        val encodedFloats = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        var inputDone = false
        var outputDone = false
        var sampleCount = 0L
        var outputRate = sampleRate
        var outputChannels = channels
        val info = MediaCodec.BufferInfo()
        while (!outputDone) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index) ?: error("Missing decoder input buffer")
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val changed = codec.outputFormat
                    val changedRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val changedChannels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    require(changedRate in MIN_UHQ_SAMPLE_RATE..MAX_UHQ_SAMPLE_RATE && changedChannels in 1..2) {
                        "Unsupported AAC decoder output format: ${changedRate}Hz, $changedChannels channels"
                    }
                    if (sampleCount > 0L) {
                        require(changedRate == outputRate && changedChannels == outputChannels) {
                            "AAC decoder changed sample rate or channel count mid-stream"
                        }
                    }
                    outputRate = changedRate
                    outputChannels = changedChannels
                }
                else -> if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val pcm = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                        val encoding = if (codec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                        val bytesPerSample = when (encoding) {
                            AudioFormat.ENCODING_PCM_8BIT -> 1
                            AudioFormat.ENCODING_PCM_16BIT -> 2
                            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
                            else -> error("Unsupported decoder PCM format: $encoding")
                        }
                        require(outputRate in MIN_UHQ_SAMPLE_RATE..MAX_UHQ_SAMPLE_RATE && outputChannels in 1..2) {
                            "Unsupported AAC decoder output format: ${outputRate}Hz, $outputChannels channels"
                        }
                        require(pcm.remaining() % (outputChannels * bytesPerSample) == 0) { "Malformed decoded PCM" }
                        val decodedFrames = pcm.remaining() / (outputChannels * bytesPerSample)
                        require(
                            isUhqAudioWithinLimit(sampleCount + decodedFrames, outputChannels, Float.SIZE_BYTES),
                        ) { "Decoded AAC exceeds the UHQ temporary-audio limit" }
                        while (pcm.remaining() >= outputChannels * bytesPerSample) {
                            repeat(outputChannels) {
                                val value = when (encoding) {
                                    AudioFormat.ENCODING_PCM_8BIT -> ((pcm.get().toInt() and 0xff) - 128) / 128f
                                    AudioFormat.ENCODING_PCM_16BIT -> pcm.short / 32768f
                                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                                        val raw = (pcm.get().toInt() and 0xff) or
                                            ((pcm.get().toInt() and 0xff) shl 8) or
                                            ((pcm.get().toInt() and 0xff) shl 16)
                                        val signed = if (raw and 0x800000 != 0) raw or -0x1000000 else raw
                                        signed / 8_388_608f
                                    }
                                    AudioFormat.ENCODING_PCM_32BIT -> pcm.int / 2_147_483_648f
                                    AudioFormat.ENCODING_PCM_FLOAT -> pcm.float
                                    else -> error("Unsupported decoded PCM format: $encoding")
                                }
                                if (encodedFloats.remaining() < 4) flushFloats(stream, encodedFloats)
                                encodedFloats.putFloat(value)
                            }
                            sampleCount++
                        }
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
        flushFloats(stream, encodedFloats)
        check(sampleCount > 0L) { "AAC decoded to empty audio" }
        require(outputRate in MIN_UHQ_SAMPLE_RATE..MAX_UHQ_SAMPLE_RATE && outputChannels in 1..2) {
            "Unsupported AAC decoder output format: ${outputRate}Hz, $outputChannels channels"
        }
        DecodedAudio(sampleCount, outputRate, outputChannels)
    }

    private fun flushFloats(stream: java.io.OutputStream, buffer: ByteBuffer) {
        if (buffer.position() == 0) return
        stream.write(buffer.array(), 0, buffer.position())
        buffer.clear()
    }

    private suspend fun resampleToModelPcm(source: File, destination: File, decoded: DecodedAudio): Long =
        withContext(Dispatchers.IO) {
            var count = 0L
            RandomAccessFile(source, "r").use { input ->
                destination.outputStream().buffered(256 * 1024).use { stream ->
                    val encodedFloats = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
                    fun writeStereo(left: Float, right: Float) {
                        require(isUhqAudioWithinLimit(count + 1, CHANNELS, Float.SIZE_BYTES)) {
                            "Resampled AAC exceeds the UHQ temporary-audio limit"
                        }
                        if (encodedFloats.remaining() < 2 * Float.SIZE_BYTES) flushFloats(stream, encodedFloats)
                        encodedFloats.putFloat(left).putFloat(right)
                        count++
                    }
                    val resampler = StreamingStereoResampler(decoded.sampleRate, SAMPLE_RATE, ::writeStereo)
                    val framesPerRead = 4096
                    val bytesPerFrame = decoded.channels * Float.SIZE_BYTES
                    val bytes = ByteArray(framesPerRead * bytesPerFrame)
                    var remainingFrames = decoded.frames
                    while (remainingFrames > 0L) {
                        currentCoroutineContext().ensureActive()
                        val frames = min(framesPerRead.toLong(), remainingFrames).toInt()
                        val byteCount = frames * bytesPerFrame
                        input.readFully(bytes, 0, byteCount)
                        val pcm = ByteBuffer.wrap(bytes, 0, byteCount).order(ByteOrder.LITTLE_ENDIAN)
                        repeat(frames) {
                            val left = pcm.float
                            val right = if (decoded.channels == 1) left else pcm.float
                            resampler.push(left, right)
                        }
                        remainingFrames -= frames
                    }
                    count = resampler.finish()
                    flushFloats(stream, encodedFloats)
                }
            }
            count
        }

    private data class DecodedAudio(val frames: Long, val sampleRate: Int, val channels: Int)

    private suspend fun enhanceTiles(
        raw: File,
        sampleCount: Long,
        destination: File,
        session: OrtSession,
        onTileComplete: suspend (completed: Int, total: Int) -> Unit,
    ) =
        withContext(Dispatchers.Default) {
            require(sampleCount <= Int.MAX_VALUE / CHANNELS) { "Audio is too long for this experimental enhancer" }
            val totalSamples = sampleCount.toInt()
            val overlap = TILE_OVERLAP_SAMPLES
            val step = TILE_SAMPLES - overlap
            val starts = generateSequence(0) { previous ->
                val next = previous + step
                if (next < totalSamples) next else null
            }.toList()
            val pendingTail = FloatArray(overlap * CHANNELS)
            var pendingFrames = 0
            RandomAccessFile(raw, "r").use { input ->
                PcmWavWriter(destination).use { writer ->
                    for ((tileIndex, start) in starts.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        val length = min(TILE_SAMPLES, totalSamples - start)
                        val tile = FloatArray(length * CHANNELS)
                        input.seek(start.toLong() * CHANNELS * Float.SIZE_BYTES)
                        val bytes = ByteArray(tile.size * Float.SIZE_BYTES)
                        input.readFully(bytes)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(tile)
                        val enhanced = enhanceTile(tile, session)
                        val hasNext = tileIndex < starts.lastIndex
                        if (tileIndex == 0 && hasNext) {
                            val tailFrames = min(overlap, length)
                            writer.write(enhanced, 0, length - tailFrames)
                            enhanced.copyInto(pendingTail, 0, (length - tailFrames) * CHANNELS, length * CHANNELS)
                            pendingFrames = tailFrames
                        } else if (hasNext) {
                            val crossfadeFrames = min(pendingFrames, length)
                            crossfade(pendingTail, enhanced, crossfadeFrames)
                            writer.write(pendingTail, 0, crossfadeFrames)
                            val tailFrames = min(overlap, length - crossfadeFrames)
                            writer.write(enhanced, crossfadeFrames, length - tailFrames)
                            enhanced.copyInto(pendingTail, 0, (length - tailFrames) * CHANNELS, length * CHANNELS)
                            pendingFrames = tailFrames
                        } else {
                            val crossfadeFrames = min(pendingFrames, length)
                            crossfade(pendingTail, enhanced, crossfadeFrames)
                            writer.write(pendingTail, 0, crossfadeFrames)
                            writer.write(enhanced, crossfadeFrames, length)
                        }
                        onTileComplete(tileIndex + 1, starts.size)
                    }
                    writer.finish()
                }
            }
        }

    private fun crossfade(previous: FloatArray, current: FloatArray, frames: Int) {
        if (frames <= 0) return
        for (frame in 0 until frames) {
            val alpha = (frame + 1f) / (frames + 1f)
            for (channel in 0 until CHANNELS) {
                val index = frame * CHANNELS + channel
                previous[index] = previous[index] * (1f - alpha) + current[index] * alpha
            }
        }
    }

    private suspend fun enhanceTile(interleaved: FloatArray, session: OrtSession): FloatArray {
        val samples = interleaved.size / CHANNELS
        val frames = samples / HOP_LENGTH + 1
        val plane = FREQUENCY_BINS * frames
        val features = FloatArray(CHANNELS * plane)
        val phaseReal = FloatArray(CHANNELS * plane)
        val phaseImaginary = FloatArray(CHANNELS * plane)
        val window = HANN
        val real = FloatArray(FFT_SIZE)
        val imaginary = FloatArray(FFT_SIZE)
        for (channel in 0 until CHANNELS) {
            val channelBase = channel * plane
            for (frame in 0 until frames) {
                if (frame % 32 == 0) currentCoroutineContext().ensureActive()
                val frameStart = frame * HOP_LENGTH - FFT_SIZE / 2
                for (index in 0 until FFT_SIZE) {
                    real[index] = sampleReflect(interleaved, channel, samples, frameStart + index) * window[index]
                    imaginary[index] = 0f
                }
                radix2Fft(real, imaginary)
                for (bin in 0 until FREQUENCY_BINS) {
                    val index = channelBase + bin * frames + frame
                    val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                    features[index] = ln(magnitude + EPSILON)
                    if (magnitude > PHASE_EPSILON) {
                        phaseReal[index] = real[bin] / magnitude
                        phaseImaginary[index] = imaginary[bin] / magnitude
                    }
                }
            }
        }
        val environment = OrtEnvironment.getEnvironment()
        val correction = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(features),
            longArrayOf(1, CHANNELS.toLong(), FREQUENCY_BINS.toLong(), frames.toLong()),
        ).use { input ->
            session.run(mapOf("log_magnitude" to input)).use { result ->
                val tensor = result[0] as OnnxTensor
                FloatArray(features.size).also { output -> tensor.floatBuffer.get(output) }
            }
        }
        val output = FloatArray(interleaved.size)
        val denominator = FloatArray(samples)
        val realFull = FloatArray(FFT_SIZE)
        val imaginaryFull = FloatArray(FFT_SIZE)
        for (channel in 0 until CHANNELS) {
            val channelBase = channel * plane
            for (frame in 0 until frames) {
                if (frame % 32 == 0) currentCoroutineContext().ensureActive()
                realFull.fill(0f)
                imaginaryFull.fill(0f)
                for (bin in 0 until FREQUENCY_BINS) {
                    val index = channelBase + bin * frames + frame
                    val magnitude = exp(features[index] + UHQ_CORRECTION_SCALE * correction[index])
                    val binReal = magnitude * phaseReal[index]
                    val binImaginary = magnitude * phaseImaginary[index]
                    realFull[bin] = binReal
                    imaginaryFull[bin] = if (bin == 0 || bin == FFT_SIZE / 2) 0f else binImaginary
                    if (bin in 1 until FFT_SIZE / 2) {
                        realFull[FFT_SIZE - bin] = binReal
                        imaginaryFull[FFT_SIZE - bin] = -binImaginary
                    }
                }
                for (index in imaginaryFull.indices) imaginaryFull[index] = -imaginaryFull[index]
                radix2Fft(realFull, imaginaryFull)
                val frameStart = frame * HOP_LENGTH - FFT_SIZE / 2
                for (index in 0 until FFT_SIZE) {
                    val sample = frameStart + index
                    if (sample !in 0 until samples) continue
                    val windowed = realFull[index] / FFT_SIZE * window[index]
                    output[sample * CHANNELS + channel] += windowed
                    if (channel == 0) denominator[sample] += window[index] * window[index]
                }
            }
        }
        for (sample in 0 until samples) {
            val norm = denominator[sample]
            if (norm > NORMALIZATION_EPSILON) {
                output[sample * CHANNELS] /= norm
                output[sample * CHANNELS + 1] /= norm
            }
        }
        return output
    }

    private fun sampleReflect(interleaved: FloatArray, channel: Int, samples: Int, requested: Int): Float {
        var index = requested
        while (index < 0 || index >= samples) {
            index = if (index < 0) -index else 2 * samples - 2 - index
        }
        return interleaved[index * CHANNELS + channel]
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val FFT_SIZE = 2_048
        const val HOP_LENGTH = 512
        const val FREQUENCY_BINS = FFT_SIZE / 2 + 1
        const val EPSILON = 1e-7f
        const val PHASE_EPSILON = 1e-12f
        const val NORMALIZATION_EPSILON = 1e-8f
        const val TILE_SAMPLES = SAMPLE_RATE * 5
        const val TILE_OVERLAP_SAMPLES = SAMPLE_RATE / 2
        const val TIMEOUT_US = 10_000L
        val HANN = FloatArray(FFT_SIZE) { index -> (0.5 - 0.5 * cos(2.0 * PI * index / FFT_SIZE)).toFloat() }
    }
}

private class PcmWavWriter(private val file: File) : Closeable {
    private val output = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
    private val buffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
    private var frames = 0L
    private var finished = false

    init {
        output.write(ByteArray(WAV_HEADER_BYTES.toInt()))
    }

    fun write(samples: FloatArray, fromFrame: Int, untilFrame: Int) {
        require(
            isUhqAudioWithinLimit(
                frames + (untilFrame - fromFrame),
                channels = 2,
                bytesPerSample = Short.SIZE_BYTES,
                maximumBytes = MAX_UHQ_AUDIO_BYTES - WAV_HEADER_BYTES,
            ),
        ) { "Enhanced WAV exceeds the UHQ cache limit" }
        for (frame in fromFrame until untilFrame) {
            for (channel in 0 until 2) {
                if (buffer.remaining() < Short.SIZE_BYTES) flushBuffer()
                val value = samples[frame * 2 + channel].coerceIn(-1f, 1f)
                buffer.putShort((value * 32767f).roundToInt().toShort())
            }
            frames++
        }
    }

    fun finish() {
        flushBuffer()
        output.flush()
        output.close()
        val dataBytes = frames * 4L
        require(dataBytes <= 0xffff_ffffL - 36L) { "Enhanced WAV exceeds the RIFF size limit" }
        RandomAccessFile(file, "rw").use { wav ->
            wav.seek(0L)
            wav.writeBytes("RIFF")
            wav.writeIntLe((dataBytes + 36L).toInt())
            wav.writeBytes("WAVEfmt ")
            wav.writeIntLe(16)
            wav.writeShortLe(1)
            wav.writeShortLe(2)
            wav.writeIntLe(44_100)
            wav.writeIntLe(44_100 * 4)
            wav.writeShortLe(4)
            wav.writeShortLe(16)
            wav.writeBytes("data")
            wav.writeIntLe(dataBytes.toInt())
            wav.fd.sync()
        }
        finished = true
    }

    private fun flushBuffer() {
        if (buffer.position() == 0) return
        output.write(buffer.array(), 0, buffer.position())
        buffer.clear()
    }

    override fun close() {
        if (!finished) runCatching { output.close() }
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
        write(value ushr 16 and 0xff)
        write(value ushr 24 and 0xff)
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }
}

private const val WAV_HEADER_BYTES = 44L
