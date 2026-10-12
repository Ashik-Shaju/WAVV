package com.wavv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCacheTest {
    @Test
    fun unknownDocumentMetadataNeverValidatesAnOldCacheEntry() {
        assertFalse(sourceFingerprintMatches(0L, 0L, 0L, 0L))
        assertFalse(sourceFingerprintMatches(1_024L, 0L, 1_024L, 0L))
        assertFalse(sourceFingerprintMatches(0L, 1_234L, 0L, 1_234L))
    }

    @Test
    fun knownProviderMetadataValidatesOnlyAnExactFingerprint() {
        assertTrue(sourceFingerprintMatches(1_024L, 1_234L, 1_024L, 1_234L))
        assertFalse(sourceFingerprintMatches(1_024L, 1_234L, 2_048L, 1_234L))
        assertFalse(sourceFingerprintMatches(1_024L, 1_234L, 1_024L, 1_235L))
    }

    @Test
    fun dclapIndexStateMatchesOnlyTheSameModelAndValidFingerprint() {
        val song = song()
        val current = SongEmbeddingIndexState(
            songId = song.id,
            modelId = DCLAP_MODEL_ID,
            sourceSizeBytes = song.fileSizeBytes,
            sourceModifiedAt = song.modifiedAt,
            dimension = 512,
            dtype = "float32",
            normalized = true,
        )

        assertTrue(current.isCurrent(song))
        assertFalse(current.copy(modelId = "older-model").isCurrent(song))
        assertFalse(current.copy(dimension = 256).isCurrent(song))
        assertFalse(current.copy(normalized = false).isCurrent(song))
        assertFalse(current.isCurrent(song.copy(fileSizeBytes = song.fileSizeBytes + 1)))
    }

    @Test
    fun musicUnderstandingIndexStateMatchesOnlyTheSameModelAndFingerprint() {
        val song = song()
        val current = MusicUnderstandingIndexState(
            songId = song.id,
            modelId = "current-model",
            sourceSizeBytes = song.fileSizeBytes,
            sourceModifiedAt = song.modifiedAt,
        )

        assertTrue(current.isCurrent(song, "current-model"))
        assertFalse(current.isCurrent(song, "older-model"))
        assertFalse(current.isCurrent(song.copy(modifiedAt = song.modifiedAt + 1), "current-model"))
    }

    private fun song() = Song(
        id = 7L,
        title = "Cached track",
        artist = "Wavv",
        album = "Cache tests",
        durationMs = 10_000L,
        uri = "content://com.wavv.test/cached-track",
        albumArtUri = null,
        fileSizeBytes = 1_024L,
        modifiedAt = 1_234L,
    )
}
