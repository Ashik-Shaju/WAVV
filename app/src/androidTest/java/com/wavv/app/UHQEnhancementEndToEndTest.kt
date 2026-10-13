package com.wavv.app

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UHQEnhancementEndToEndTest {
    @Test
    fun bundledAacEnhancerDecodesInfersAndWritesPlayableWaveWithProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "uhq-test-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "source.m4a")
        val output = File(directory, "enhanced.wav")
        try {
            encodeAac(source)
            val progress = mutableListOf<Pair<String, Int>>()

            UHQAacEnhancer(context).enhance(Uri.fromFile(source), output) { stage, percent ->
                progress += stage to percent
            }

            assertTrue(output.isFile && output.length() > 44L)
            assertEquals("RIFF", ByteArray(4).also { bytes ->
                output.inputStream().use { it.read(bytes) }
            }.toString(Charsets.US_ASCII))
            assertTrue(progress.any { (stage, percent) -> stage == "Enhancing audio" && percent > 25 })
            assertEquals("Finalizing" to 98, progress.last())
            assertTrue(progress.zipWithNext().all { (before, after) -> after.second >= before.second })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun availableAacInMusicFolderCanBeEnhancedWithoutChangingTheSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assumeTrue(context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val source = firstShortAacInMusicFolder(context)
        assumeTrue("No supported AAC file in Music/", source != null)
        val directory = File(context.cacheDir, "uhq-music-test-${UUID.randomUUID()}").apply { mkdirs() }
        val output = File(directory, "enhanced.wav")
        try {
            assertTrue("AAC source has no stable cache identity", UHQAacCache.key(context, requireNotNull(source)) != null)
            UHQAacEnhancer(context).enhance(requireNotNull(source), output) { _, _ -> }
            assertTrue(output.isFile && output.length() > 44L)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun firstShortAacInMusicFolder(context: android.content.Context): Uri? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("Music/%")
        return context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.SIZE} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (cursor.moveToNext()) {
                if (cursor.getLong(durationIndex) !in 1L..180_000L || cursor.getLong(sizeIndex) <= 0L) continue
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIndex))
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                    val aacTrack = (0 until extractor.trackCount).firstOrNull { index ->
                        val format = extractor.getTrackFormat(index)
                        isSupportedUhqMimeType(format.getString(MediaFormat.KEY_MIME).orEmpty()) &&
                            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) in 8_000..48_000 &&
                            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) in 1..2
                    }
                    if (aacTrack != null) return uri
                } finally {
                    extractor.release()
                }
            }
            null
        }
    }

    private fun encodeAac(destination: File) {
        val sampleRate = 44_100
        val channels = 2
        val totalFrames = sampleRate * 5
        val format = MediaFormat.createAudioFormat(UHQ_AAC_MIME_TYPE, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType(UHQ_AAC_MIME_TYPE)
        val muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = MediaCodec.BufferInfo()
            var frameCount = 0
            var inputEnded = false
            var outputEnded = false
            var trackIndex = -1
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = encoder.getInputBuffer(inputIndex) ?: error("Missing AAC encoder input buffer")
                        input.clear()
                        val remainingFrames = totalFrames - frameCount
                        if (remainingFrames <= 0) {
                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                frameCount * 1_000_000L / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            val frames = minOf(remainingFrames, input.remaining() / (channels * Short.SIZE_BYTES))
                            repeat(frames) { offset ->
                                val time = (frameCount + offset).toDouble() / sampleRate
                                val sample = (sin(2.0 * PI * 440.0 * time) * Short.MAX_VALUE * .2).toInt().toShort()
                                repeat(channels) { input.putShort(sample) }
                            }
                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                frames * channels * Short.SIZE_BYTES,
                                frameCount * 1_000_000L / sampleRate,
                                0,
                            )
                            frameCount += frames
                        }
                    }
                }

                when (val outputIndex = encoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (outputIndex >= 0) {
                        val buffer = encoder.getOutputBuffer(outputIndex)
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted && trackIndex >= 0) { "AAC encoder did not publish its output format" }
                            requireNotNull(buffer)
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buffer, info)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}
