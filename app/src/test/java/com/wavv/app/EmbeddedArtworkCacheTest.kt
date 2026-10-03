package com.wavv.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedArtworkCacheTest {
    @Test
    fun pruningRetainsCurrentArtworkAndDeletesOrphansAndTemporaryFiles() {
        val directory = Files.createTempDirectory("wavv-artwork-test-").toFile()
        try {
            val retained = File(directory, "retained.img").apply { writeBytes(byteArrayOf(1)) }
            val orphan = File(directory, "orphan.img").apply { writeBytes(byteArrayOf(2)) }
            val temporary = File(directory, "in-progress.tmp").apply { writeBytes(byteArrayOf(3)) }
            File(directory, "unrelated.txt").writeText("keep")

            val deleted = pruneEmbeddedArtworkFiles(directory, listOf(retained.toURI().toString()))

            assertEquals(2, deleted)
            assertTrue(retained.isFile)
            assertFalse(orphan.exists())
            assertFalse(temporary.exists())
            assertTrue(File(directory, "unrelated.txt").isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun anUnparseableReferenceDisablesPruningToAvoidDeletingLiveArtwork() {
        val directory = Files.createTempDirectory("wavv-artwork-test-").toFile()
        try {
            val image = File(directory, "art.img").apply { writeBytes(byteArrayOf(1)) }

            assertEquals(0, pruneEmbeddedArtworkFiles(directory, listOf("not a URI")))
            assertTrue(image.isFile)
        } finally {
            directory.deleteRecursively()
        }
    }
}
