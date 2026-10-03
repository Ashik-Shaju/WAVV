package com.wavv.app

internal fun sourceFingerprintMatches(
    cachedSizeBytes: Long,
    cachedModifiedAt: Long,
    sourceSizeBytes: Long,
    sourceModifiedAt: Long,
): Boolean =
    sourceSizeBytes > 0L &&
        sourceModifiedAt > 0L &&
        cachedSizeBytes == sourceSizeBytes &&
        cachedModifiedAt == sourceModifiedAt
