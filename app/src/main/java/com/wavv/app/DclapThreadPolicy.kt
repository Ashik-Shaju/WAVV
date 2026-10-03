package com.wavv.app

internal object DclapThreadPolicy {
    private const val MAX_INTRA_OP_THREADS = 2

    fun intraOpThreadCount(processorCount: Int): Int =
        processorCount.coerceAtLeast(1).coerceAtMost(MAX_INTRA_OP_THREADS)
}
