package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DclapThreadPolicyTest {
    @Test
    fun inferenceLeavesCpuHeadroomOnHighCoreCountDevices() {
        assertEquals(1, DclapThreadPolicy.intraOpThreadCount(0))
        assertEquals(1, DclapThreadPolicy.intraOpThreadCount(1))
        assertEquals(2, DclapThreadPolicy.intraOpThreadCount(2))
        assertEquals(2, DclapThreadPolicy.intraOpThreadCount(8))
        assertEquals(2, DclapThreadPolicy.intraOpThreadCount(16))
    }
}
