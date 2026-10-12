package com.wavv.app

internal fun decodePlaylistPinIds(values: Set<String>): Set<Long> =
    values.mapNotNullTo(mutableSetOf()) { value -> value.toLongOrNull()?.takeIf { it > 0L } }

internal fun encodePlaylistPinIds(ids: Set<Long>): Set<String> =
    ids.asSequence().filter { it > 0L }.map(Long::toString).toCollection(sortedSetOf())
