package com.wavv.dclap.benchmark

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.abs
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Build
import android.os.Debug
import android.os.SystemClock

@RunWith(AndroidJUnit4::class)
class DclapModelBenchmarkTest {
    @Test
    fun runFourModelCombinations() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(InstrumentationRegistry.getArguments().getString("dclap.root") ?: error("dclap.root is required"))
        val fixture = Fixture(File(root, "audio_fixture.bin"), File(root, "text_fixture.bin"))
        val features = cpuFeatures()
        require(features.neon && features.dotprod) { "Required ARM NEON and dotprod CPU features are unavailable: $features" }

        val audio32 = runAudio(File(root, "models/audio_fp32/model_epoch_36.onnx"), fixture.audio)
        val audio16 = runAudio(File(root, "models/audio_fp16/model_epoch_36.fp16.onnx"), fixture.audio)
        val text32 = runText(File(root, "models/text_fp32/clap_text_model.onnx"), fixture.text)
        val text8 = runText(File(root, "models/text_int8/clap_text_model.int8.dynamic.per_channel.onnx"), fixture.text)

        val retrieval = linkedMapOf(
            "fp32_audio_fp32_text" to retrieval(text32.embeddings, fixture.text.queries, audio32.embeddings, fixture.audio.records.map { it.genre }),
            "fp32_audio_int8_text" to retrieval(text8.embeddings, fixture.text.queries, audio32.embeddings, fixture.audio.records.map { it.genre }),
            "fp16_audio_fp32_text" to retrieval(text32.embeddings, fixture.text.queries, audio16.embeddings, fixture.audio.records.map { it.genre }),
            "fp16_audio_int8_text" to retrieval(text8.embeddings, fixture.text.queries, audio16.embeddings, fixture.audio.records.map { it.genre }),
        )
        val baseline = retrieval.getValue("fp32_audio_fp32_text")
        val relative = retrieval.mapValues { (_, candidate) -> candidate.mapValues { (key, value) ->
            (value - baseline.getValue(key)) / abs(baseline.getValue(key)) * 100.0
        } }

        val report = JSONObject()
            .put("device", JSONObject(mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "device" to Build.DEVICE,
                "sdk" to Build.VERSION.SDK_INT,
                "abis" to JSONArray(Build.SUPPORTED_ABIS.toList()),
            )))
            .put("cpu", JSONObject(mapOf(
                "features" to features.raw,
                "neon" to features.neon,
                "dotprod" to features.dotprod,
                "execution_provider" to OrtProvider.CPU.getName(),
                "available_providers" to JSONArray(OrtEnvironment.getAvailableProviders().map { it.getName() }),
                "intra_op_threads" to Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                "acceleration" to "ONNX Runtime ARM CPU kernels with NEON and dotprod dispatch",
            )))
            .put("fixture", JSONObject(mapOf(
                "audio_records" to fixture.audio.records.size,
                "text_queries" to fixture.text.queries.size,
                "tracks_per_genre" to 3,
                "application_independent" to true,
            )))
            .put("representation", JSONObject(mapOf(
                "audio_fp32_to_fp16" to JSONObject(distortion(audio32.embeddings, audio16.embeddings)),
                "text_fp32_to_int8" to JSONObject(distortion(text32.embeddings, text8.embeddings)),
            )))
            .put("retrieval_by_variant", jsonMap(retrieval))
            .put("relative_degradation_percent", jsonMap(relative))
            .put("performance", JSONObject(mapOf(
                "audio_fp32" to JSONObject(audio32.performance),
                "audio_fp16" to JSONObject(audio16.performance),
                "text_fp32" to JSONObject(text32.performance),
                "text_int8" to JSONObject(text8.performance),
            )))

        assertEquals(30, fixture.audio.records.size)
        assertEquals(30, fixture.text.queries.size)
        assertTrue(report.toString().contains("fp16_audio_int8_text"))
        val output = File(context.filesDir, "dclap-benchmark/report.json")
        output.parentFile?.mkdirs()
        output.writeText(report.toString(2))
    }

    @Test
    fun runInt8AudioInt8Text() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(InstrumentationRegistry.getArguments().getString("dclap.root") ?: error("dclap.root is required"))
        val fixture = Fixture(File(root, "audio_fixture.bin"), File(root, "text_fixture.bin"))
        val features = cpuFeatures()
        require(features.neon && features.dotprod) { "Required ARM NEON and dotprod CPU features are unavailable: $features" }

        // FP32 audio is a device-local representation reference; only the new INT8/INT8 variant is retrieved.
        val audio32 = runAudio(File(root, "models/audio_fp32/model_epoch_36.onnx"), fixture.audio)
        val audio8 = runAudio(File(root, "models/audio_int8/model_epoch_36.int8.dynamic.per_channel.onnx"), fixture.audio)
        val text8 = runText(File(root, "models/text_int8/clap_text_model.int8.dynamic.per_channel.onnx"), fixture.text)
        val retrieval = retrieval(
            text8.embeddings,
            fixture.text.queries,
            audio8.embeddings,
            fixture.audio.records.map { it.genre },
        )
        val report = JSONObject()
            .put("scope", JSONObject(mapOf(
                "new_variant" to "int8_audio_int8_text",
                "other_combinations_rerun" to false,
                "audio_reference" to "fp32_audio_only_for_device_representation",
            )))
            .put("device", JSONObject(mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "device" to Build.DEVICE,
                "sdk" to Build.VERSION.SDK_INT,
                "abis" to JSONArray(Build.SUPPORTED_ABIS.toList()),
            )))
            .put("cpu", JSONObject(mapOf(
                "features" to features.raw,
                "neon" to features.neon,
                "dotprod" to features.dotprod,
                "execution_provider" to OrtProvider.CPU.getName(),
                "available_providers" to JSONArray(OrtEnvironment.getAvailableProviders().map { it.getName() }),
                "intra_op_threads" to Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                "acceleration" to "ONNX Runtime ARM CPU kernels with NEON and dotprod dispatch",
            )))
            .put("fixture", JSONObject(mapOf(
                "audio_records" to fixture.audio.records.size,
                "text_queries" to fixture.text.queries.size,
                "tracks_per_genre" to 3,
                "application_independent" to true,
            )))
            .put("representation", JSONObject(mapOf(
                "audio_fp32_to_int8" to JSONObject(distortion(audio32.embeddings, audio8.embeddings)),
            )))
            .put("retrieval_by_variant", jsonMap(mapOf("int8_audio_int8_text" to retrieval)))
            .put("performance", JSONObject(mapOf(
                "audio_fp32_reference" to JSONObject(audio32.performance),
                "audio_int8" to JSONObject(audio8.performance),
                "text_int8" to JSONObject(text8.performance),
            )))

        assertEquals(30, fixture.audio.records.size)
        assertEquals(30, fixture.text.queries.size)
        assertTrue(report.toString().contains("int8_audio_int8_text"))
        val output = File(context.filesDir, "dclap-benchmark/report-int8-audio-int8-text.json")
        output.parentFile?.mkdirs()
        output.writeText(report.toString(2))
    }

    private fun runAudio(model: File, fixture: AudioFixture): RunResult {
        val handle = open(model)
        val warm = fixture.records.first().windows.first()
        repeat(2) { encodeAudio(handle.session, warm) }
        val start = SystemClock.elapsedRealtimeNanos()
        repeat(5) { encodeAudio(handle.session, warm) }
        val warmMs = millis(SystemClock.elapsedRealtimeNanos() - start) / 5.0
        val embeddings = try {
            fixture.records.map { record ->
                normalize(record.windows.map { encodeAudio(handle.session, it) })
            }
        } finally {
            handle.session.close()
        }
        return RunResult(embeddings, handle.performance + ("warm_single_window_ms" to warmMs))
    }

    private fun runText(model: File, fixture: TextFixture): RunResult {
        val handle = open(model)
        val warm = fixture.queries.first()
        repeat(2) { encodeText(handle.session, warm) }
        val start = SystemClock.elapsedRealtimeNanos()
        repeat(5) { encodeText(handle.session, warm) }
        val warmMs = millis(SystemClock.elapsedRealtimeNanos() - start) / 5.0
        val embeddings = try {
            fixture.queries.map { normalize(encodeText(handle.session, it)) }
        } finally {
            handle.session.close()
        }
        return RunResult(embeddings, handle.performance + ("warm_single_query_ms" to warmMs))
    }

    private fun open(model: File): SessionHandle {
        val environment = OrtEnvironment.getEnvironment()
        val before = Debug.getPss()
        val start = SystemClock.elapsedRealtimeNanos()
        val options = OrtSession.SessionOptions()
        val session = try {
            val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setInterOpNumThreads(1)
            options.setIntraOpNumThreads(threads)
            options.setMemoryPatternOptimization(true)
            options.setCPUArenaAllocator(true)
            options.addCPU(true)
            environment.createSession(model.absolutePath, options)
        } finally {
            options.close()
        }
        return SessionHandle(
            session,
            mapOf(
                "model" to model.name,
                "session_creation_ms" to millis(SystemClock.elapsedRealtimeNanos() - start),
                "rss_delta_mb" to (Debug.getPss() - before) / 1024.0,
            ),
        )
    }

    private fun encodeAudio(session: OrtSession, window: FloatArray): FloatArray =
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(window), longArrayOf(1, 1, 128, 1001)).use { input ->
            session.run(mapOf("mel_spectrogram" to input)).use { result ->
                ((result[0].value as Array<*>).single() as FloatArray).copyOf()
            }
        }

    private fun encodeText(session: OrtSession, query: TextQuery): FloatArray =
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), LongBuffer.wrap(query.ids), longArrayOf(1, query.ids.size.toLong())).use { ids ->
            OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), LongBuffer.wrap(query.mask), longArrayOf(1, query.mask.size.toLong())).use { mask ->
                session.run(mapOf("input_ids" to ids, "attention_mask" to mask)).use { result ->
                    ((result[0].value as Array<*>).single() as FloatArray).copyOf()
                }
            }
        }

    private fun retrieval(
        text: List<FloatArray>,
        queries: List<TextQuery>,
        audio: List<FloatArray>,
        audioGenres: List<String>,
    ): Map<String, Double> {
        val rows = text.indices.map { queryIndex ->
            val genre = queries[queryIndex].genre
            val ranking = audio.indices.sortedWith(compareByDescending<Int> { dot(text[queryIndex], audio[it]) }.thenBy { it })
            val relevant = audioGenres.count { it == genre }
            val flags = ranking.map { audioGenres[it] == genre }
            mapOf(
                "mAP" to averagePrecision(flags, relevant),
                "mAP_at_10" to averagePrecision(flags.take(10), relevant),
                "recall_at_1" to flags.take(1).count { it }.toDouble() / relevant,
                "recall_at_5" to flags.take(5).count { it }.toDouble() / relevant,
                "recall_at_10" to flags.take(10).count { it }.toDouble() / relevant,
            )
        }
        return rows.first().keys.associateWith { key -> rows.map { it.getValue(key) }.average() }
    }

    private fun averagePrecision(flags: List<Boolean>, relevant: Int): Double {
        var hits = 0
        var total = 0.0
        flags.forEachIndexed { index, flag -> if (flag) { hits++; total += hits.toDouble() / (index + 1) } }
        return total / relevant
    }

    private fun distortion(reference: List<FloatArray>, candidate: List<FloatArray>): Map<String, Double> {
        val cosine = reference.indices.map { dot(reference[it], candidate[it]) }.sorted()
        val mse = reference.indices.map { index -> reference[index].indices.map { i ->
            val delta = reference[index][i].toDouble() - candidate[index][i]
            delta * delta
        }.average() }.sorted()
        val l2 = reference.indices.map { index -> sqrt(reference[index].indices.sumOf { i ->
            val delta = reference[index][i].toDouble() - candidate[index][i]
            delta * delta
        }) }.sorted()
        return mapOf("cosine_mean" to cosine.average(), "cosine_median" to cosine[cosine.size / 2], "mse_mean" to mse.average(), "l2_mean" to l2.average())
    }

    private fun normalize(values: List<FloatArray>): FloatArray {
        val mean = FloatArray(values.first().size) { index -> values.sumOf { it[index].toDouble() }.toFloat() / values.size }
        val norm = sqrt(mean.sumOf { it.toDouble() * it })
        return FloatArray(mean.size) { index -> (mean[index] / norm).toFloat() }
    }

    private fun normalize(value: FloatArray): FloatArray {
        val norm = sqrt(value.sumOf { it.toDouble() * it })
        return FloatArray(value.size) { index -> (value[index] / norm).toFloat() }
    }

    private fun dot(left: FloatArray, right: FloatArray): Double = left.indices.sumOf { left[it].toDouble() * right[it] }
    private fun millis(nanos: Long): Double = nanos / 1_000_000.0
    private fun jsonMap(value: Map<String, Map<String, Double>>) = JSONObject().apply { value.forEach { (key, child) -> put(key, JSONObject(child)) } }

    private fun cpuFeatures(): CpuFeatures {
        val lines = File("/proc/cpuinfo").readLines()
        val raw = lines.firstOrNull { it.startsWith("Features") }?.substringAfter(":")?.trim().orEmpty()
        val features = raw.split(Regex("\\s+")).toSet()
        return CpuFeatures(raw, "asimd" in features || "neon" in features, "asimddp" in features || "dotprod" in features)
    }

    private data class CpuFeatures(val raw: String, val neon: Boolean, val dotprod: Boolean)
    private data class SessionHandle(val session: OrtSession, val performance: Map<String, Any>)
    private data class RunResult(val embeddings: List<FloatArray>, val performance: Map<String, Any>)
    private data class AudioRecord(val genre: String, val windows: List<FloatArray>)
    private data class TextQuery(val genre: String, val text: String, val ids: LongArray, val mask: LongArray)
    private data class AudioFixture(val records: List<AudioRecord>)
    private data class TextFixture(val queries: List<TextQuery>)
    private class Fixture(audioFile: File, textFile: File) {
        val audio = readAudio(audioFile)
        val text = readText(textFile)

        private fun readAudio(file: File): AudioFixture = DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            require(input.readInt() == 0x44434131) { "Invalid audio fixture" }
            val records = List(input.readInt()) {
                readUtf(input)
                val genre = readUtf(input)
                val windows = List(input.readInt()) {
                    FloatArray(input.readInt()) { input.readFloat() }
                }
                AudioRecord(genre, windows)
            }
            AudioFixture(records)
        }

        private fun readText(file: File): TextFixture = DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            require(input.readInt() == 0x44435431) { "Invalid text fixture" }
            val queries = List(input.readInt()) {
                val genre = readUtf(input)
                val text = readUtf(input)
                val ids = LongArray(77) { input.readLong() }
                val mask = LongArray(77) { input.readLong() }
                TextQuery(genre, text, ids, mask)
            }
            TextFixture(queries)
        }

        private fun readUtf(input: DataInputStream): String {
            val size = input.readUnsignedShort()
            val bytes = ByteArray(size)
            input.readFully(bytes)
            return bytes.toString(Charsets.UTF_8)
        }
    }
}
