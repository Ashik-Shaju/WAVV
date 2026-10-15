package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioOutputRoutingTest {
    @Test
    fun selectedBluetoothRouteUsesItsActiveStreamVolumeInsteadOfStaleSnapshot() {
        assertEquals(
            11,
            activeRouteVolume(
                routeVolume = 2,
                routeVolumeMax = 15,
                isSelected = true,
                isLocalPlayback = true,
                streamVolume = 11,
                streamVolumeMax = 15,
            ),
        )
    }

    @Test
    fun returningToPhoneDoesNotKeepTheDisconnectedBluetoothVolume() {
        assertEquals(
            3,
            activeRouteVolume(
                routeVolume = 11,
                routeVolumeMax = 15,
                isSelected = true,
                isLocalPlayback = true,
                streamVolume = 3,
                streamVolumeMax = 15,
            ),
        )
    }

    @Test
    fun onlySelectedLocalRoutesUseTheActiveStreamVolume() {
        assertEquals(
            4,
            activeRouteVolume(
                routeVolume = 4,
                routeVolumeMax = 15,
                isSelected = false,
                isLocalPlayback = true,
                streamVolume = 12,
                streamVolumeMax = 15,
            ),
        )
        assertEquals(
            4,
            activeRouteVolume(
                routeVolume = 4,
                routeVolumeMax = 15,
                isSelected = true,
                isLocalPlayback = false,
                streamVolume = 12,
                streamVolumeMax = 15,
            ),
        )
    }

    @Test
    fun scalesDifferentVolumeRangesAndFallsBackWhenStreamRangeIsUnavailable() {
        assertEquals(
            8,
            activeRouteVolume(
                routeVolume = 1,
                routeVolumeMax = 15,
                isSelected = true,
                isLocalPlayback = true,
                streamVolume = 4,
                streamVolumeMax = 8,
            ),
        )
        assertEquals(
            5,
            activeRouteVolume(
                routeVolume = 5,
                routeVolumeMax = 15,
                isSelected = true,
                isLocalPlayback = true,
                streamVolume = 9,
                streamVolumeMax = 0,
            ),
        )
    }
}
