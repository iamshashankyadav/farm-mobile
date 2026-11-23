package com.farm.customer.orders

import com.farm.core.api.OrderEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Status rail and cancel rules of the orders screens (#40). */
class OrdersTest {
    @Test
    fun railHasTheSixStagesOfTheWebsite() {
        assertEquals(
            listOf("Placed", "Confirmed", "Packed", "On a route", "In transit", "Delivered"),
            RAIL_STAGES.map { it.label },
        )
    }

    @Test
    fun railStageCountsCompletedSegments() {
        assertEquals(1, railStage("PLACED"))
        assertEquals(3, railStage("READY_FOR_DISPATCH")) // shares Packed
        assertEquals(4, railStage("POOLED"))
        assertEquals(6, railStage("DELIVERED"))
        assertEquals(6, railStage("COMPLETED"))
        assertEquals(0, railStage("QC_PASSED")) // no longer a status
        assertEquals(0, railStage("CANCELLED"))
        assertEquals(0, railStage("SOMETHING_NEW"))
    }

    @Test
    fun stageTextSaysWhatHappensNow() {
        assertEquals("Packed, waiting for a delivery partner", stageText("PACKED"))
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
        assertEquals("2026-11-21T09:00:00Z", times[5])
    }

    @Test
    fun cancelIsOfferedOnlyUntilPacked() {
        listOf("PLACED", "CONFIRMED", "PACKED").forEach { assertTrue(it, canCancel(it)) }
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
    fun harvestChoiceCosts() {
        val c = harvestCosts(total = 1000.0, balanceDue = 800.0, deliveryFee = 0.0)
        assertEquals(820.0, c.deliveryPayOnArrival, 0.0) // balance + flat 20 delivery
        assertEquals(800.0, c.buybackPayNow, 0.0)
        assertEquals(900.0, c.buybackCredit, 0.0)
        assertEquals(830.0, harvestCosts(1000.0, 800.0, 30.0).deliveryPayOnArrival, 0.0)
        assertEquals("Your balance payment was declined. Nothing was taken; try again.", parseChoiceError(409, "BALANCE_PAYMENT_FAILED").message)
    }
}
