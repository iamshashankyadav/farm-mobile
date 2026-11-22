package com.farm.customer.orders

import com.farm.core.api.ApiException
import com.farm.core.api.NetworkException
import com.farm.core.api.OrderEvent
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Status rail, cancel rules and load errors of the orders screens (#40). */
class OrdersTest {
    @Test
    fun railHasTheEightStagesOfTheWebsite() {
        assertEquals(
            listOf("Placed", "Confirmed", "Packed", "Graded", "Ready for dispatch", "Pooled", "In transit", "Delivered"),
            RAIL_STAGES.map { it.label },
        )
    }

    @Test
    fun railStageCountsCompletedSegments() {
        assertEquals(1, railStage("PLACED"))
        assertEquals(4, railStage("QC_PASSED"))
        assertEquals(6, railStage("POOLED"))
        assertEquals(8, railStage("DELIVERED"))
        assertEquals(8, railStage("COMPLETED"))
        assertEquals(0, railStage("CANCELLED"))
        assertEquals(0, railStage("SOMETHING_NEW"))
    }

    @Test
    fun stageTextSaysWhatHappensNow() {
        assertEquals("Packed, waiting for the quality check", stageText("PACKED"))
        assertEquals("Cancelled", stageText("CANCELLED"))
        assertEquals("Something new", stageText("SOMETHING_NEW"))
    }

    @Test
    fun stageTimesTakeTheFirstEventOfEachStatus() {
        val events = listOf(
            OrderEvent("CONFIRMED", "2026-11-20T10:01:00Z"),
            OrderEvent("PLACED", "2026-11-20T10:00:00Z"),
            OrderEvent("PACKED", "2026-11-20T12:00:00Z"),
            OrderEvent("PACKED", "2026-11-20T13:00:00Z"),
            OrderEvent("COMPLETED", "2026-11-21T09:00:00Z"),
        )
        val times = stageTimes(events)
        assertEquals(RAIL_STAGES.size, times.size)
        assertEquals("2026-11-20T10:00:00Z", times[0])
        assertEquals("2026-11-20T10:01:00Z", times[1])
        assertEquals("2026-11-20T12:00:00Z", times[2])
        assertEquals(null, times[3])
        // no DELIVERED event: the COMPLETED time fills the last stage
        assertEquals("2026-11-21T09:00:00Z", times[7])
    }

    @Test
    fun cancelIsOfferedOnlyUntilTheQualityCheck() {
        listOf("PLACED", "CONFIRMED", "PACKED", "QC_PASSED").forEach { assertTrue(it, canCancel(it)) }
        listOf("READY_FOR_DISPATCH", "POOLED", "IN_TRANSIT", "DELIVERED", "COMPLETED", "CANCELLED")
            .forEach { assertFalse(it, canCancel(it)) }
    }

    @Test
    fun cancelRefusedBecauseTheOrderMovedOn() {
        assertTrue(cancelErrorMessage(409, "INVALID_STATUS_TRANSITION", "x").startsWith("This order can no longer be cancelled"))
        assertEquals("Order not found.", cancelErrorMessage(404, "ORDER_NOT_FOUND", null))
        assertEquals("Server says no", cancelErrorMessage(500, "X", "Server says no"))
    }

    @Test
    fun loadKeepsTypedFailuresAndMessages() {
        val offline = load { throw NetworkException(IOException("down")) }
        assertTrue(offline.exceptionOrNull() is NetworkException)
        assertEquals(
            "Cannot reach the server at http://10.0.2.2/api/v1. Check your connection and try again.",
            loadErrorMessage(offline.exceptionOrNull(), "http://10.0.2.2/api/v1"),
        )
        val refused = load { throw ApiException("Could not load your orders (500)", status = 500) }
        assertEquals("Could not load your orders (500)", loadErrorMessage(refused.exceptionOrNull(), "u"))
        val garbled = load { throw SerializationException("bad") }
        assertEquals("The server sent a reply the app could not read.", loadErrorMessage(garbled.exceptionOrNull(), "u"))
        assertEquals(3, load { 3 }.getOrNull())
    }

    @Test(expected = IllegalStateException::class)
    fun loadDoesNotHideProgrammingErrors() {
        load { error("bug") }
    }
}
