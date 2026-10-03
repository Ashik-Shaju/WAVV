package com.wavv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerReconnectPolicyTest {
    @Test
    fun retriesAreBoundedAndBackOffThenResetAfterAConnection() {
        val policy = ControllerReconnectPolicy(listOf(10L, 20L, 40L))

        assertEquals(10L, policy.nextDelayMs())
        assertEquals(20L, policy.nextDelayMs())
        assertEquals(40L, policy.nextDelayMs())
        assertNull(policy.nextDelayMs())

        policy.reset()
        assertEquals(10L, policy.nextDelayMs())
    }
}
