package com.wavv.app

import org.junit.Assert.fail
import org.junit.Test

class FolderScanBudgetTest {
    @Test
    fun acceptsDocumentsAndSongsAtTheirConfiguredLimits() {
        val budget = FolderScanBudget(maximumDepth = 2, maximumDocuments = 2, maximumSongs = 1)

        budget.recordDocument(depth = 2)
        budget.recordDocument(depth = 1)
        budget.recordSong()

    }

    @Test
    fun rejectsExcessiveDepthAndDocumentCount() {
        assertLimitExceeded {
            FolderScanBudget(maximumDepth = 1).recordDocument(depth = 2)
        }
        assertLimitExceeded {
            FolderScanBudget(maximumDocuments = 1).apply {
                recordDocument(depth = 1)
                recordDocument(depth = 1)
            }
        }
    }

    @Test
    fun rejectsExcessiveAudioSongCount() {
        assertLimitExceeded {
            FolderScanBudget(maximumSongs = 1).apply {
                recordSong()
                recordSong()
            }
        }
    }

    private fun assertLimitExceeded(block: () -> Unit) {
        try {
            block()
            fail("Expected folder scan limit to be exceeded")
        } catch (_: FolderScanLimitExceededException) {
            // Expected boundary behavior.
        }
    }
}
