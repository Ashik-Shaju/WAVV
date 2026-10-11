package com.wavv.app

internal class FolderScanLimitExceededException(message: String) : Exception(message)

// shortcut: catalogs above these limits fail before replacement; raise them only after indexing persists incrementally.
internal class FolderScanBudget(
    private val maximumDepth: Int = MAX_DEPTH,
    private val maximumDocuments: Int = MAX_DOCUMENTS,
    private val maximumSongs: Int = MAX_SONGS,
) {
    private var scannedDocuments = 0
    private var collectedSongs = 0

    fun recordDocument(depth: Int) {
        if (depth > maximumDepth) {
            throw FolderScanLimitExceededException("Selected folder nesting exceeds $maximumDepth levels")
        }
        if (++scannedDocuments > maximumDocuments) {
            throw FolderScanLimitExceededException("Selected folders contain more than $maximumDocuments documents")
        }
    }

    fun recordSong() {
        if (++collectedSongs > maximumSongs) {
            throw FolderScanLimitExceededException("Selected folders contain more than $maximumSongs audio files")
        }
    }

    companion object {
        const val MAX_DEPTH = 64
        const val MAX_DOCUMENTS = 100_000
        const val MAX_SONGS = 25_000
    }
}
