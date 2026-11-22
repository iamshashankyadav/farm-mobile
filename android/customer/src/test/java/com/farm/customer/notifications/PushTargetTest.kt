package com.farm.customer.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushTargetTest {
    @Test fun orderPushOpensTheOrder() {
        val t = pushTarget(mapOf("orderId" to "o-1", "type" to "ORDER_PACKED"))
        assertEquals("order/o-1", routeFor(t!!))
    }

    @Test fun reservationPushOpensTheReservation() {
        assertEquals("reservation/r-9", routeFor(pushTarget(mapOf("reservationId" to " r-9 "))!!))
    }

    @Test fun pushWithoutAnIdOpensNothing() {
        assertNull(pushTarget(mapOf("orderId" to " ")))
        assertNull(pushTarget(emptyMap()))
    }
}
