package com.wavv.app

import kotlin.math.roundToInt

internal fun activeRouteVolume(
    routeVolume: Int,
    routeVolumeMax: Int,
    isSelected: Boolean,
    isLocalPlayback: Boolean,
    streamVolume: Int?,
    streamVolumeMax: Int?,
): Int {
    val routeMax = routeVolumeMax.coerceAtLeast(0)
    val snapshotVolume = routeVolume.coerceIn(0, routeMax)
    if (
        !isSelected || !isLocalPlayback || streamVolume == null ||
        streamVolumeMax == null || streamVolumeMax <= 0
    ) {
        return snapshotVolume
    }
    return (streamVolume.coerceIn(0, streamVolumeMax).toFloat() / streamVolumeMax * routeMax)
        .roundToInt()
        .coerceIn(0, routeMax)
}
