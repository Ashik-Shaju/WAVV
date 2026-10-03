package com.wavv.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class MusicAnalysis(
    val modelId: String,
    val probabilities: FloatArray,
    val tags: List<MusicTag>,
)

internal class MusicUnderstandingModel(context: Context) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val bundle = MusicUnderstandingBundle.install(context.applicationContext)
    val modelId: String get() = bundle.modelId
    private val melFilterBank = bundle.melFilterBank()
    private val preprocessor = MusicUnderstandingPreprocessor(melFilterBank)
    private val tagger = MusicTagger(bundle.featureMean, bundle.featureScale, bundle.heads)
    private val runtime = createSession(bundle.backbone)

    suspend fun analyze(uri: Uri): MusicAnalysis = withContext(Dispatchers.Default) {
        val probabilities = MeanProbabilityPool(bundle.heads.size)
        val analysisContext = currentCoroutineContext()
        AudioPcmDecoder(bundle.context).decodeWindows(
            uri = uri,
            targetSampleRate = MUSIC_UNDERSTANDING_SAMPLE_RATE,
            bandLimitedResampling = true,
            windowSizeSamples = MUSIC_UNDERSTANDING_WINDOW_SAMPLES,
            hopSizeSamples = MUSIC_UNDERSTANDING_WINDOW_SAMPLES,
            alignLastWindow = false,
        ) { samples, validSamples ->
            analysisContext.ensureActive()
            val features = encodeWindow(samples, validSamples)
            probabilities.add(tagger.probabilities(features), validSamples)
        }
        analysisContext.ensureActive()
        val pooled = probabilities.finish()
        MusicAnalysis(bundle.modelId, pooled, tagger.tags(pooled))
    }

    suspend fun analyzePcm(samples: FloatArray): MusicAnalysis {
        currentCoroutineContext().ensureActive()
        val windows = musicUnderstandingWindows(samples.size)
        val probabilities = MeanProbabilityPool(bundle.heads.size)
        val context = currentCoroutineContext()
        windows.forEach { window ->
            context.ensureActive()
            val segment = FloatArray(MUSIC_UNDERSTANDING_WINDOW_SAMPLES)
            samples.copyInto(segment, 0, window.startSample, window.startSample + window.validSamples)
            val features = encodeWindow(segment, window.validSamples)
            probabilities.add(tagger.probabilities(features), window.validSamples)
        }
        context.ensureActive()
        val pooled = probabilities.finish()
        return MusicAnalysis(bundle.modelId, pooled, tagger.tags(pooled))
    }

    internal suspend fun encodeWindow(samples: FloatArray, validSamples: Int = samples.size): FloatArray {
        require(samples.size == MUSIC_UNDERSTANDING_WINDOW_SAMPLES)
        return infer(preprocessor.logMel(samples, validSamples))
    }

    private suspend fun infer(logMel: FloatArray): FloatArray {
        currentCoroutineContext().ensureActive()
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(logMel),
            longArrayOf(1, 1, MEL_BINS.toLong(), FRAME_COUNT.toLong()),
        ).use { input ->
            runtime.session.run(mapOf(INPUT_NAME to input)).use { outputs ->
                val value = outputs.get(OUTPUT_NAME).get().value
                val feature = (value as? Array<*>)?.singleOrNull() as? FloatArray
                    ?: error("DyMN output has an unexpected tensor shape")
                require(feature.size == FEATURE_DIMENSION && feature.all(Float::isFinite)) {
                    "DyMN produced an invalid pooled feature"
                }
                return feature
            }
        }
    }

    override fun close() = runtime.close()

    private fun createSession(model: File): MusicUnderstandingSession {
        fun configure(options: OrtSession.SessionOptions, useXnnpack: Boolean) {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setInterOpNumThreads(1)
            options.setIntraOpNumThreads(1)
            options.setMemoryPatternOptimization(true)
            options.setCPUArenaAllocator(true)
            options.addConfigEntry("session.intra_op.allow_spinning", "0")
            if (useXnnpack) {
                options.addXnnpack(mapOf("intra_op_num_threads" to Runtime.getRuntime().availableProcessors().coerceIn(1, 4).toString()))
            }
        }

        val xnnpackOptions = OrtSession.SessionOptions()
        try {
            configure(xnnpackOptions, useXnnpack = true)
            return MusicUnderstandingSession(environment.createSession(model.absolutePath, xnnpackOptions), xnnpackOptions)
        } catch (xnnpackError: Exception) {
            runCatching { xnnpackOptions.close() }
            val cpuOptions = OrtSession.SessionOptions()
            try {
                configure(cpuOptions, useXnnpack = false)
                return MusicUnderstandingSession(environment.createSession(model.absolutePath, cpuOptions), cpuOptions)
            } catch (cpuError: Exception) {
                runCatching { cpuOptions.close() }
                cpuError.addSuppressed(xnnpackError)
                throw cpuError
            }
        }
    }

    private companion object {
        const val INPUT_NAME = "log_mel"
        const val OUTPUT_NAME = "pooled_feature"
        const val FEATURE_DIMENSION = 384
        const val MEL_BINS = 128
        const val FRAME_COUNT = 1_000
    }
}

private class MusicUnderstandingSession(
    val session: OrtSession,
    private val options: OrtSession.SessionOptions,
) : Closeable {
    override fun close() {
        try {
            session.close()
        } finally {
            options.close()
        }
    }
}

internal suspend fun indexMusicUnderstandingSongs(
    context: Context,
    store: LibraryStore,
    onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
): AnalysisIndexResult {
    currentCoroutineContext().ensureActive()
    val songs = store.getSongs()
    onProgress(0, songs.size)
    currentCoroutineContext().ensureActive()
    if (songs.isEmpty()) return AnalysisIndexResult()
    val bundleAvailable = try {
        context.assets.open("music-understanding/v1/manifest.json").use { true }
    } catch (_: IOException) {
        false
    }
    if (!bundleAvailable) {
        return AnalysisIndexResult(unavailableMessage = "The bundled music-understanding model is missing.")
    }
    var failedOperations = 0
    MusicUnderstandingModel(context).use { model ->
        songs.forEachIndexed { index, song ->
            currentCoroutineContext().ensureActive()
            val cached = store.getMusicUnderstanding(song.id)
            if (cached?.isCurrent(song, model.modelId) == true) {
                onProgress(index + 1, songs.size)
                return@forEachIndexed
            }
            val failure = store.runAnalysisJob(song.id, MUSIC_UNDERSTANDING_JOB) {
                val analysis = model.analyze(Uri.parse(song.uri))
                store.saveMusicUnderstanding(
                    MusicUnderstandingResultEntity(
                        songId = song.id,
                        modelId = analysis.modelId,
                        sourceSizeBytes = song.fileSizeBytes,
                        sourceModifiedAt = song.modifiedAt,
                        tagsJson = analysis.tags.toJson(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            if (failure is IOException) throw failure
            if (failure != null) failedOperations += 1
            onProgress(index + 1, songs.size)
        }
    }
    return AnalysisIndexResult(failedOperations = failedOperations)
}

private fun MusicUnderstandingResultEntity.isCurrent(song: Song, modelId: String): Boolean =
    this.modelId == modelId &&
        sourceFingerprintMatches(sourceSizeBytes, sourceModifiedAt, song.fileSizeBytes, song.modifiedAt)

internal fun List<MusicTag>.toJson(): String = JSONArray().apply {
    forEach { tag ->
        put(
            JSONObject()
                .put("task", tag.task)
                .put("taxonomy", tag.taxonomy)
                .put("label", tag.label)
                .put("labelSource", tag.labelSource)
                .put("probability", tag.probability.toDouble())
                .put("threshold", tag.threshold.toDouble()),
        )
    }
}.toString()

private const val MUSIC_UNDERSTANDING_JOB = "music_understanding"

private class MusicUnderstandingBundle(
    val context: Context,
    val modelId: String,
    val backbone: File,
    private val melPath: File,
    val featureMean: FloatArray,
    val featureScale: FloatArray,
    val heads: List<MusicHead>,
) {
    fun melFilterBank(): FloatArray {
        val bytes = melPath.readBytes()
        require(bytes.size == MUSIC_MEL_FLOATS * Float.SIZE_BYTES) { "Invalid DyMN mel filterbank" }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { buffer ->
            FloatArray(buffer.remaining()).also(buffer::get)
        }.also { values -> require(values.all(Float::isFinite)) { "Invalid DyMN mel filterbank values" } }
    }

    companion object {
        private const val ASSET_ROOT = "music-understanding/v1"
        private const val MUSIC_MEL_FLOATS = 128 * 513

        @Synchronized
        fun install(context: Context): MusicUnderstandingBundle {
            val manifest = JSONObject(context.assets.open("$ASSET_ROOT/manifest.json").bufferedReader().use { it.readText() })
            require(manifest.getInt("formatVersion") == 1)
            require(manifest.getInt("contractVersion") == MUSIC_UNDERSTANDING_CONTRACT_VERSION)
            val modelId = manifest.getString("modelId")
            require(modelId.startsWith("wavv-dymn04as-"))
            val root = File(context.filesDir, "music-understanding/v1").apply { mkdirs() }
            val files = manifest.getJSONObject("files")
            val backbone = installVerified(context, root, "backbone.onnx", files)
            val mel = installVerified(context, root, "mel_filterbank.f32", files)
            val headsFile = installVerified(context, root, "heads.json", files)
            val headsBundle = JSONObject(headsFile.readText())
            val featureMean = headsBundle.getJSONArray("featureMean").toFloatArray()
            val featureScale = headsBundle.getJSONArray("featureScale").toFloatArray()
            require(featureMean.size == 384 && featureScale.size == 384)
            val headsJson = headsBundle.getJSONArray("heads")
            val heads = List(headsJson.length()) { index ->
                val head = headsJson.getJSONObject(index)
                val task = head.getString("task")
                val taxonomy = head.getString("taxonomy")
                val label = head.getString("label")
                val source = head.getString("source")
                require(source == "human_consensus" || source == "weak_uploader_tag")
                MusicHead(
                    task = task,
                    taxonomy = taxonomy,
                    label = label,
                    labelSource = source,
                    weights = head.getJSONArray("weights").toFloatArray(),
                    intercept = head.getDouble("intercept").toFloat(),
                    threshold = head.getDouble("threshold").toFloat(),
                )
            }
            require(heads.size == 16 && heads.none { it.task == "instrument:electricguitar" })
            return MusicUnderstandingBundle(context, modelId, backbone, mel, featureMean, featureScale, heads)
        }

        private fun installVerified(context: Context, root: File, name: String, files: JSONObject): File {
            val entry = files.getJSONObject(name)
            val expectedHash = entry.getString("sha256")
            val expectedBytes = entry.getLong("bytes")
            val destination = File(root, name)
            if (destination.isFile && destination.length() == expectedBytes && sha256(destination) == expectedHash) {
                return destination
            }
            val temporary = File(root, "$name.part")
            temporary.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("$ASSET_ROOT/$name").use { asset ->
                DigestInputStream(asset, digest).use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            }
            val actualHash = digest.digest().toHex()
            if (temporary.length() != expectedBytes || actualHash != expectedHash) {
                temporary.delete()
                throw IOException("Music Understanding asset failed integrity validation: $name")
            }
            if (destination.exists() && !destination.delete()) {
                temporary.delete()
                throw IOException("Could not replace cached Music Understanding asset: $name")
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                throw IOException("Could not install Music Understanding asset: $name")
            }
            return destination
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().toHex()
        }

        private fun JSONArray.toFloatArray(): FloatArray = FloatArray(length()) { getDouble(it).toFloat() }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
