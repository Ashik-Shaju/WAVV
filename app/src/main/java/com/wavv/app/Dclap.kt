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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

internal const val DCLAP_MODEL_ID = "audiomuse-dclap-v1-fp32-audio-int8-text"
private const val AUDIO_MODEL = "model_epoch_36.onnx"
private const val AUDIO_EXTERNAL_DATA = "model_epoch_36.onnx.data"
private const val TEXT_MODEL = "clap_text_model.int8.dynamic.per_channel.onnx"
private const val AUDIO_SHA256 = "17860403f8fc90aff8ac0632a0741eb5e58d8c0b0ad2fce5ced967274b0ea971"
private const val AUDIO_EXTERNAL_DATA_SHA256 = "2a735b23c2aad7b12d9ffc85334cebcc659c07696d2ff60e2e378da28b6df657"
private const val TEXT_SHA256 = "b8f1b1ded9d7484e7d8be29c1f04a06d8e357787083965cf96626c9e57aa7985"

private data class DclapModels(
    val audio: File,
    val text: File,
    val tokenizer: File,
)

private object DclapModelInstaller {
    private const val ASSET_ROOT = "dclap/v1"

    fun install(context: Context): DclapModels {
        val root = File(context.filesDir, "dclap/v1").apply { mkdirs() }
        val audio = copyVerified(context, "$ASSET_ROOT/$AUDIO_MODEL", File(root, AUDIO_MODEL), AUDIO_SHA256)
        copyVerified(context, "$ASSET_ROOT/$AUDIO_EXTERNAL_DATA", File(root, AUDIO_EXTERNAL_DATA), AUDIO_EXTERNAL_DATA_SHA256)
        val text = copyVerified(context, "$ASSET_ROOT/$TEXT_MODEL", File(root, TEXT_MODEL), TEXT_SHA256)
        val tokenizer = File(root, "tokenizer").apply { mkdirs() }
        copyAsset(context, "$ASSET_ROOT/tokenizer/vocab.json", File(tokenizer, "vocab.json"))
        copyAsset(context, "$ASSET_ROOT/tokenizer/merges.txt", File(tokenizer, "merges.txt"))
        return DclapModels(audio, text, tokenizer)
    }

    private fun copyVerified(context: Context, asset: String, destination: File, expectedSha256: String): File {
        if (destination.isFile && sha256(destination) == expectedSha256) return destination
        destination.delete()
        copyAsset(context, asset, destination)
        check(sha256(destination) == expectedSha256) { "DCLAP asset hash mismatch: $asset" }
        return destination
    }

    private fun copyAsset(context: Context, asset: String, destination: File) {
        if (destination.isFile) return
        val temporary = File(destination.parentFile, "${destination.name}.part")
        context.assets.open(asset).use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        check(temporary.renameTo(destination)) { "Could not install DCLAP asset: $asset" }
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
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

private class AudioPcmDecoder(private val context: Context) {
    fun decode(uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        val codec: MediaCodec
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME type missing")
            val sourceRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            return try {
                decodePcm(extractor, codec, sourceRate, sourceChannels)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun decodePcm(
        extractor: MediaExtractor,
        codec: MediaCodec,
        sourceRate: Int,
        sourceChannels: Int,
    ): FloatArray {
        val output = FloatAccumulator()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var outputFormat = MediaFormat.createAudioFormat("audio/raw", sourceRate, sourceChannels)
        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex) ?: error("Missing decoder input buffer")
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                else -> if (outputIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        appendPcm(buffer.slice().order(ByteOrder.LITTLE_ENDIAN), outputFormat, sourceChannels, output)
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
        return resample(output.toArray(), sourceRate, TARGET_SAMPLE_RATE)
    }

    private fun appendPcm(
        buffer: ByteBuffer,
        format: MediaFormat,
        sourceChannels: Int,
        output: FloatAccumulator,
    ) {
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, sourceChannels)
        val encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
        require(buffer.remaining() % (channels * bytesPerSample) == 0) { "Invalid decoded PCM buffer" }
        while (buffer.remaining() >= channels * bytesPerSample) {
            var mono = 0f
            repeat(channels) {
                mono += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f
            }
            output.add(mono / channels)
        }
    }

    private fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (input.isEmpty() || sourceRate == targetRate) return input
        val outputSize = (input.size.toDouble() * targetRate / sourceRate).roundToIntSafe()
        return FloatArray(outputSize) { index ->
            val sourcePosition = index.toDouble() * sourceRate / targetRate
            val left = sourcePosition.toInt().coerceAtMost(input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (sourcePosition - left).toFloat()
            input[left] + (input[right] - input[left]) * fraction
        }
    }

    private class FloatAccumulator {
        private var values = FloatArray(16_384)
        private var size = 0

        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(values.size * 2)
            values[size++] = value
        }

        fun toArray(): FloatArray = values.copyOf(size)
    }

    private companion object {
        const val TARGET_SAMPLE_RATE = 48_000
        const val TIMEOUT_US = 10_000L
    }
}

private object DclapPreprocessor {
    private const val SAMPLE_RATE = 48_000
    private const val SEGMENT_SAMPLES = 480_000
    private const val SEGMENT_HOP = 240_000
    private const val N_FFT = 2_048
    private const val MEL_HOP = 480
    private const val N_MELS = 128
    private val melFilters = melFilters()
    private val hann = FloatArray(N_FFT) { index -> (0.5 - 0.5 * cos(2.0 * PI * index / N_FFT)).toFloat() }

    fun windows(audio: FloatArray): List<FloatArray> {
        val starts = ArrayList<Int>()
        if (audio.size <= SEGMENT_SAMPLES) {
            starts += 0
        } else {
            var start = 0
            while (start + SEGMENT_SAMPLES <= audio.size) {
                starts += start
                start += SEGMENT_HOP
            }
            if (starts.size * SEGMENT_HOP < audio.size) starts += audio.size - SEGMENT_SAMPLES
        }
        return starts.map { start ->
            val segment = FloatArray(SEGMENT_SAMPLES)
            audio.copyInto(segment, 0, start, min(audio.size, start + SEGMENT_SAMPLES))
            logMel(segment)
        }
    }

    private fun logMel(segment: FloatArray): FloatArray {
        val output = FloatArray(N_MELS * 1001)
        val padded = FloatArray(segment.size + N_FFT) { index -> reflect(segment, index - N_FFT / 2) }
        val real = FloatArray(N_FFT)
        val imaginary = FloatArray(N_FFT)
        val power = FloatArray(N_FFT / 2 + 1)
        var frame = 0
        var offset = 0
        while (offset + N_FFT <= padded.size && frame < 1001) {
            for (index in 0 until N_FFT) {
                real[index] = padded[offset + index] * hann[index]
                imaginary[index] = 0f
            }
            fft(real, imaginary)
            for (bin in power.indices) power[bin] = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            for (mel in 0 until N_MELS) {
                var value = 0.0
                val filter = melFilters[mel]
                for (bin in power.indices) value += power[bin] * filter[bin]
                output[mel * 1001 + frame] = (10.0 * log10(max(value, 1e-10))).toFloat()
            }
            frame++
            offset += MEL_HOP
        }
        return output
    }

    private fun fft(real: FloatArray, imaginary: FloatArray) {
        var j = 0
        for (i in real.indices) {
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
            var bit = real.size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
        }
        var length = 2
        while (length <= real.size) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle).toFloat()
            val stepImaginary = sin(angle).toFloat()
            for (start in real.indices step length) {
                var currentReal = 1f
                var currentImaginary = 0f
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * currentReal - imaginary[odd] * currentImaginary
                    val oddImaginary = real[odd] * currentImaginary + imaginary[odd] * currentReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = currentReal * stepReal - currentImaginary * stepImaginary
                    currentImaginary = currentReal * stepImaginary + currentImaginary * stepReal
                    currentReal = nextReal
                }
            }
            length = length shl 1
        }
    }

    private fun reflect(source: FloatArray, index: Int): Float {
        if (index in source.indices) return source[index]
        var reflected = index
        while (reflected < 0 || reflected >= source.size) {
            reflected = if (reflected < 0) -reflected else 2 * source.size - 2 - reflected
        }
        return source[reflected]
    }

    private fun melFilters(): Array<FloatArray> {
        val minMel = hzToMel(0.0)
        val maxMel = hzToMel(14_000.0)
        val points = DoubleArray(N_MELS + 2) { index -> melToHz(minMel + (maxMel - minMel) * index / (N_MELS + 1)) }
        val bins = points.map { floor((N_FFT + 1) * it / SAMPLE_RATE).toInt().coerceIn(0, N_FFT / 2) }
        return Array(N_MELS) { mel ->
            val filter = FloatArray(N_FFT / 2 + 1)
            val left = bins[mel]
            val center = bins[mel + 1]
            val right = bins[mel + 2]
            val area = max(1.0, points[mel + 2] - points[mel])
            for (bin in left until center) if (center > left) filter[bin] = ((bin - left).toDouble() / (center - left) * 2.0 / area).toFloat()
            for (bin in center until right) if (right > center) filter[bin] = ((right - bin).toDouble() / (right - center) * 2.0 / area).toFloat()
            filter
        }
    }

    private fun hzToMel(hz: Double): Double = if (hz < 1_000.0) 3.0 * hz / 200.0 else 15.0 + 27.0 * ln(hz / 1_000.0) / ln(6.4)
    private fun melToHz(mel: Double): Double = if (mel < 15.0) 200.0 * mel / 3.0 else 1_000.0 * kotlin.math.exp((mel - 15.0) * ln(6.4) / 27.0)
}

private class DclapAudioEncoder(model: File) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        options.setInterOpNumThreads(1)
        options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(8).coerceAtLeast(1))
        options.setMemoryPatternOptimization(true)
        options.setCPUArenaAllocator(true)
        options.addCPU(true)
        session = try {
            environment.createSession(model.absolutePath, options)
        } finally {
            options.close()
        }
    }

    suspend fun encode(context: Context, uri: Uri): FloatArray = withContext(Dispatchers.Default) {
        val audio = AudioPcmDecoder(context).decode(uri)
        val windows = DclapPreprocessor.windows(audio)
        val sum = FloatArray(512)
        windows.forEach { window ->
            coroutineContext.ensureActive()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(window), longArrayOf(1, 1, 128, 1001)).use { input ->
                session.run(mapOf("mel_spectrogram" to input)).use { result ->
                    val values = ((result[0].value as Array<*>).single() as FloatArray)
                    values.forEachIndexed { index, value -> sum[index] += value }
                }
            }
        }
        normalize(sum)
    }

    override fun close() = session.close()
}

private class DclapTokenizer(directory: File) {
    private val vocabulary = HashMap<String, Int>()
    private val ranks = HashMap<String, Int>()
    private val byteEncoder = byteEncoder()
    private val pattern = Regex("""'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""")

    init {
        val json = JSONObject(File(directory, "vocab.json").readText())
        json.keys().forEach { token -> vocabulary[token] = json.getInt(token) }
        File(directory, "merges.txt").useLines { lines ->
            lines.drop(1).forEachIndexed { index, line ->
                val parts = line.split(' ')
                if (parts.size == 2) ranks["${parts[0]} ${parts[1]}"] = index
            }
        }
    }

    fun encode(text: String): TextTokens {
        val ids = LongArray(77) { 1L }
        val mask = LongArray(77)
        var position = 0
        ids[position++] = 0
        pattern.findAll(text).forEach { match ->
            if (position >= 76) return@forEach
            val encoded = match.value.toByteArray(Charsets.UTF_8).joinToString("") { byte -> byteEncoder[byte.toInt() and 0xff].toString() }
            bpe(encoded).forEach { token ->
                if (position < 76) {
                    ids[position++] = (vocabulary[token] ?: 3).toLong()
                }
            }
        }
        ids[position] = 2
        for (index in 0..position) mask[index] = 1
        return TextTokens(ids, mask)
    }

    private fun bpe(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        val initial = value.map(Char::toString).toMutableList()
        initial[initial.lastIndex] += "</w>"
        var symbols: List<String> = initial
        while (symbols.size > 1) {
            val best = symbols.zipWithNext()
                .map { (first, second) -> first to second }
                .minByOrNull { pair -> ranks["${pair.first} ${pair.second}"] ?: Int.MAX_VALUE }
                ?: break
            if (ranks["${best.first} ${best.second}"] == null) break
            val merged = ArrayList<String>(symbols.size)
            var index = 0
            while (index < symbols.size) {
                if (index < symbols.lastIndex && symbols[index] == best.first && symbols[index + 1] == best.second) {
                    merged += best.first + best.second
                    index += 2
                } else {
                    merged += symbols[index++]
                }
            }
            symbols = merged
        }
        return symbols
    }

    private fun byteEncoder(): Map<Int, Char> {
        val bytes = ArrayList<Int>()
        (33..126).forEach(bytes::add)
        (161..172).forEach(bytes::add)
        (174..255).forEach(bytes::add)
        val codePoints = bytes.toMutableList()
        var extra = 0
        (0..255).forEach { byte ->
            if (byte !in bytes) {
                bytes += byte
                codePoints += 256 + extra++
            }
        }
        return bytes.indices.associate { index -> bytes[index] to codePoints[index].toChar() }
    }
}

private data class TextTokens(val ids: LongArray, val mask: LongArray)

private class DclapTextEncoder(model: File, tokenizerDirectory: File) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val tokenizer = DclapTokenizer(tokenizerDirectory)
    private val session: OrtSession

    init {
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        options.setInterOpNumThreads(1)
        options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(8).coerceAtLeast(1))
        options.setMemoryPatternOptimization(true)
        options.setCPUArenaAllocator(true)
        options.addCPU(true)
        session = try {
            environment.createSession(model.absolutePath, options)
        } finally {
            options.close()
        }
    }

    fun encode(query: String): FloatArray {
        val tokens = tokenizer.encode(query)
        OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.ids), longArrayOf(1, 77)).use { ids ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.mask), longArrayOf(1, 77)).use { mask ->
                session.run(mapOf("input_ids" to ids, "attention_mask" to mask)).use { result ->
                    return normalize(((result[0].value as Array<*>).single() as FloatArray).copyOf())
                }
            }
        }
    }

    override fun close() = session.close()
}

class DclapTextSearch(private val context: Context) : Closeable {
    private var encoder: DclapTextEncoder? = null

    suspend fun search(query: String, songs: List<Song>, store: LibraryStore, limit: Int = 50): List<Song> {
        val models = DclapModelInstaller.install(context)
        val activeEncoder = encoder ?: DclapTextEncoder(models.text, models.tokenizer).also { encoder = it }
        return store.searchEmbeddings(activeEncoder.encode(query), songs, limit)
    }

    override fun close() {
        encoder?.close()
        encoder = null
    }
}

class DclapIndexWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val store = LibraryStore(WavvDatabase.get(applicationContext))
        return try {
            val models = DclapModelInstaller.install(applicationContext)
            val songs = store.getSongs()
            DclapAudioEncoder(models.audio).use { encoder ->
                songs.forEachIndexed { index, song ->
                    coroutineContext.ensureActive()
                    val cached = store.getEmbedding(song.id)
                    if (cached?.isCurrent(song) == true) return@forEachIndexed
                    store.saveAnalysis(song.id, JOB_TYPE, RUNNING)
                    try {
                        val vector = encoder.encode(applicationContext, Uri.parse(song.uri))
                        store.saveEmbedding(
                            SongEmbeddingEntity(
                                songId = song.id,
                                modelId = DCLAP_MODEL_ID,
                                sourceSizeBytes = song.fileSizeBytes,
                                sourceModifiedAt = song.modifiedAt,
                                dimension = vector.size,
                                dtype = "float32",
                                normalized = true,
                                vector = vector.toByteArray(),
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        store.saveAnalysis(song.id, JOB_TYPE, COMPLETED)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        store.saveAnalysis(song.id, JOB_TYPE, FAILED, error.message?.take(512) ?: error::class.simpleName)
                    }
                    setProgress(workDataOf(KEY_COMPLETED to index + 1, KEY_TOTAL to songs.size))
                }
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf(KEY_ERROR to error.message))
        } catch (error: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: error::class.simpleName)))
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "wavv-dclap-index"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val JOB_TYPE = "dclap"
        private const val RUNNING = "running"
        private const val COMPLETED = "completed"
        private const val FAILED = "failed"
    }
}

class DclapIndexScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<DclapIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(DclapIndexWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() = workManager.cancelUniqueWork(DclapIndexWorker.UNIQUE_WORK_NAME)

    fun observe(id: java.util.UUID): Flow<androidx.work.WorkInfo?> = workManager.getWorkInfoByIdFlow(id)
}

private fun SongEmbeddingEntity.isCurrent(song: Song): Boolean =
    modelId == DCLAP_MODEL_ID && sourceSizeBytes == song.fileSizeBytes && sourceModifiedAt == song.modifiedAt && dimension == 512 && dtype == "float32" && normalized

private fun normalize(values: FloatArray): FloatArray {
    val norm = sqrt(values.sumOf { it.toDouble() * it.toDouble() })
    require(norm.isFinite() && norm > 0.0) { "DCLAP produced an invalid embedding" }
    return FloatArray(values.size) { index -> (values[index] / norm).toFloat() }
}

private fun FloatArray.toByteArray(): ByteArray {
    val bytes = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    bytes.asFloatBuffer().put(this)
    return bytes.array()
}

private fun Double.roundToIntSafe(): Int = toLong().coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
