package com.wavv.app

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumArtDecodeSizeTest {
    @Test
    fun decodeSizeBucketsAreBoundedAndRoundUpForDisplayedArtwork() {
        assertEquals(128, albumArtDecodeBucketEdge(IntSize(64, 96)))
        assertEquals(256, albumArtDecodeBucketEdge(IntSize(180, 190)))
        assertEquals(1024, albumArtDecodeBucketEdge(IntSize(900, 1024)))
        assertEquals(1024, albumArtDecodeBucketEdge(IntSize(2048, 2048)))
    }
}
