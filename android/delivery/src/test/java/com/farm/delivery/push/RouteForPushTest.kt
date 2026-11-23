package com.farm.delivery.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteForPushTest {
    @Test fun poolDateOpensThatDaysRoute() =
        assertEquals("route?date=2026-11-24", routeForPush(mapOf("type" to "ORDER_POOLED", "poolDate" to "2026-11-24")))

    @Test fun routePushWithoutDateOpensToday() {
        assertEquals("route", routeForPush(mapOf("type" to "ORDER_POOLED")))
        assertEquals("route", routeForPush(mapOf("poolId" to "p1", "poolDate" to "not-a-date")))
    }

    @Test fun otherPushesOpenNothing() = assertNull(routeForPush(mapOf("type" to "SYSTEM")))
}
