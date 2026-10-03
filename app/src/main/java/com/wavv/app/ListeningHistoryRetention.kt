package com.wavv.app

internal const val LISTENING_HISTORY_RETENTION_MS = 365L * 24L * 60L * 60L * 1_000L

internal fun listeningHistoryCutoff(nowMs: Long): Long =
    if (nowMs <= LISTENING_HISTORY_RETENTION_MS) 0L else nowMs - LISTENING_HISTORY_RETENTION_MS
