package com.wavv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassBackdropTest {
    @Test
    fun regularBackdropKeepsRefreshingAfterPixelsHaveBeenCaptured() {
        assertTrue(shouldCaptureBackdropFrame(refreshRequested = true, freezeAfterFirstCapture = false, hasBackdropPixels = true))
    }

    @Test
    fun frozenBackdropStopsRefreshingAfterItsFirstCapture() {
        assertTrue(shouldCaptureBackdropFrame(refreshRequested = true, freezeAfterFirstCapture = true, hasBackdropPixels = false))
        assertFalse(shouldCaptureBackdropFrame(refreshRequested = true, freezeAfterFirstCapture = true, hasBackdropPixels = true))
    }

    @Test
    fun disabledBackdropDoesNotCaptureEvenBeforeItsFirstFrame() {
        assertFalse(shouldCaptureBackdropFrame(refreshRequested = false, freezeAfterFirstCapture = true, hasBackdropPixels = false))
    }
}
