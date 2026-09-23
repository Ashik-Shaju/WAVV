package com.wavv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBackendTest {
    @Test
    fun audioDocument_acceptsAudioMimeRegardlessOfExtension() {
        assertTrue(isAudioDocument("recording.bin", "audio/x-wav"))
    }

    @Test
    fun audioDocument_acceptsSupportedExtensionWhenMimeIsMissing() {
        assertTrue(isAudioDocument("track.FLAC", ""))
    }

    @Test
    fun audioDocument_rejectsNonAudioFile() {
        assertFalse(isAudioDocument("cover.jpg", "image/jpeg"))
    }

    @Test
    fun stableSongId_isDeterministicAndChangesWithUri() {
        assertEquals(stableSongId("content://media/1"), stableSongId("content://media/1"))
        assertNotEquals(stableSongId("content://media/1"), stableSongId("content://media/2"))
    }

    @Test
    fun sourceFingerprint_isOrderIndependentForFiles() {
        assertEquals(
            LibrarySource.Files(listOf("content://b", "content://a")).fingerprint(),
            LibrarySource.Files(listOf("content://a", "content://b")).fingerprint(),
        )
    }

    @Test
    fun sourceFingerprint_changesForDifferentFolder() {
        assertNotEquals(
            LibrarySource.Folder("content://folder/a").fingerprint(),
            LibrarySource.Folder("content://folder/b").fingerprint(),
        )
    }
}
