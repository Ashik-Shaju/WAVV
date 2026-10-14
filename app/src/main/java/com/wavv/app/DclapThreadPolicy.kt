package com.wavv.app

internal object DclapThreadPolicy {
    private const val MAX_INTRA_OP_THREADS = 2
    private const val MAX_XNNPACK_THREADS = 4

    fun intraOpThreadCount(processorCount: Int): Int =
        processorCount.coerceAtLeast(1).coerceAtMost(MAX_INTRA_OP_THREADS)

    fun xnnpackThreadCount(processorCount: Int): Int =
        processorCount.coerceAtLeast(1).coerceAtMost(MAX_XNNPACK_THREADS)
}
