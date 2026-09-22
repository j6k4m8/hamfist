package dev.hamfist.core

import org.junit.Assert.*
import org.junit.Test

class DeferredDeliveryTest {
    @Test fun delayedLookupCannotMakeAnOldMessageLookFresh() {
        val request = DeferredDelivery(100000, 2000, 30000) { true }
        assertTrue(request.canDeliver(31999))
        assertFalse(request.canDeliver(32001))
        assertEquals(100000L, request.sentAtMillis)
    }

    @Test fun revokingPermissionWhileLookupIsPendingCancelsDelivery() {
        var allowed = true
        val request = DeferredDelivery(100000, 2000, 30000) { allowed }
        assertTrue(request.canDeliver(2000))
        allowed = false
        assertFalse(request.canDeliver(2001))
    }

    @Test fun expiryIsBasedOnMonotonicTimeAndRejectsClockRollback() {
        val request = DeferredDelivery(999999, 2000, 1500) { true }
        assertFalse(request.canDeliver(1999))
        assertTrue(request.canDeliver(3500))
        assertFalse(request.canDeliver(3501))
    }
}
