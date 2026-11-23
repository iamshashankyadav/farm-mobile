package com.farm.customer.orders

import com.farm.core.api.ListingOut
import com.farm.core.api.OrderItem
import com.farm.customer.prefs.FavouriteFarm
import com.farm.customer.prefs.addRecentSearch
import com.farm.customer.prefs.toggleFavourite
import com.farm.customer.browse.FilterState
import com.farm.customer.browse.applyLocalFilters
import com.farm.customer.browse.needsRefetch
import com.farm.customer.browse.ProduceListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderTest {
    private fun listing(id: String, qty: Double, price: Double = 30.0, status: String = "ACTIVE") = ListingOut(
        id = id, farmId = "f1", farmName = "Green Acres", cropName = "Tomato", unit = "kg",
        pricePerUnit = price, qtyAvailable = qty, status = status,
    )

    @Test fun reorderUsesTodaysPriceAndStock() {
        val items = listOf(
            OrderItem(listingId = "l1", name = "Tomato", unit = "kg", qty = 5.0, unitPrice = 25.0),
            OrderItem(listingId = "l2", name = "Onion", unit = "kg", qty = 2.0),
            OrderItem(listingId = "l3", name = "Okra", unit = "kg", qty = 1.0),
            OrderItem(listingId = "l4", name = "Mango", unit = "kg", qty = 1.0),
        )
        val r = planReorder(items, mapOf("l1" to listing("l1", 3.0, price = 32.0), "l2" to null, "l3" to listing("l3", 9.0, status = "SOLD_OUT"), "l4" to listing("l4", 0.0)))
        assertEquals(1, r.added.size)
        assertEquals(3.0, r.added[0].qty, 0.0)
        assertEquals(32.0, r.added[0].unitPrice, 0.0)
        assertEquals(listOf("Onion", "Okra", "Mango"), r.unavailable)
        assertEquals(listOf("Tomato (3 kg)"), r.reduced)
        assertTrue(r.message!!.contains("No longer available: Onion, Okra, Mango."))
    }

    @Test fun everythingBackInHasNoMessage() {
        val r = planReorder(listOf(OrderItem(listingId = "l1", name = "Tomato", qty = 2.0)), mapOf("l1" to listing("l1", 10.0)))
        assertNull(r.message)
    }

    @Test fun harvestOrdersCannotBeReordered() {
        val items = listOf(OrderItem(listingId = "l1", qty = 1.0))
        assertFalse(canReorder("HARVEST", items))
        assertTrue(canReorder("PRODUCE", items))
        assertFalse(canReorder("PRODUCE", listOf(OrderItem(listingId = null, qty = 1.0))))
    }

    @Test fun nextStepLines() {
        assertEquals("Next: the farmer packs your order.", orderNextStep("CONFIRMED"))
        assertNull(orderNextStep("COMPLETED"))
        assertNull(orderNextStep("CANCELLED"))
    }

    @Test fun aSharedDeliveryOrderSaysWhenItsBatchRuns() {
        val now = java.time.Instant.parse("2026-11-24T02:00:00Z") // 07:30 in India
        assertEquals("today 10:00 am", batchWhen("2026-11-24T10:00:00+05:30", now))
        assertEquals("tomorrow 10:00 am", batchWhen("2026-11-25T10:00:00+05:30", now))
        assertEquals("Sat, 28 Nov, 10:00 am", batchWhen("2026-11-28T10:00:00+05:30", now))
        assertNull(batchWhen("not a time", now))
    }

    @Test fun onlyAnOrderThatHasNotLeftWaitsForTheBatch() {
        assertTrue(waitsForBatch("READY_FOR_DISPATCH", "FLEXIBLE"))
        assertTrue(waitsForBatch("CONFIRMED", "FLEXIBLE"))
        assertFalse(waitsForBatch("POOLED", "FLEXIBLE"))
        assertFalse(waitsForBatch("READY_FOR_DISPATCH", "IMMEDIATE"))
    }

    @Test fun nextStepOfASharedDeliveryOrderMentionsTheBatch() {
        val line = orderNextStep("READY_FOR_DISPATCH", "FLEXIBLE", "2999-01-01T10:00:00+05:30")
        assertTrue(line!!.startsWith("Shared delivery: it leaves in our batch"))
        assertEquals("Next: it is added to a delivery route.", orderNextStep("READY_FOR_DISPATCH"))
        assertEquals("Next: the delivery partner picks it up from the farm.", orderNextStep("POOLED", "FLEXIBLE", null))
        assertTrue(orderNextStep("PACKED", "FLEXIBLE", null)!!.contains("next daily batch"))
    }

    @Test fun recentSearchesDedupeAndCap() {
        var list = emptyList<String>()
        list = addRecentSearch(list, "tomato")
        list = addRecentSearch(list, "Onion")
        list = addRecentSearch(list, "  TOMATO ")
        list = addRecentSearch(list, "   ")
        assertEquals(listOf("TOMATO", "Onion"), list)
        assertEquals(3, (1..10).fold(emptyList<String>()) { acc, i -> addRecentSearch(acc, "q$i", max = 3) }.size)
    }

    @Test fun favouritesToggleAndFilter() {
        val f = FavouriteFarm("f1", "Green Acres")
        val on = toggleFavourite(emptyList(), f)
        assertEquals(listOf(f), on)
        assertEquals(emptyList<FavouriteFarm>(), toggleFavourite(on, f))

        val a = ProduceListing(id = "a", farmId = "f1", farmName = "Green Acres", cropId = "tomato", cropName = "Tomato", unit = "kg", pricePerUnit = 1.0, qtyAvailable = 1.0, harvestDate = "")
        val b = a.copy(id = "b", farmId = "f2", farmName = "Other")
        assertEquals(listOf("a"), applyLocalFilters(listOf(a, b), FilterState(favouritesOnly = true), setOf("f1")).map { it.id })
        assertEquals(2, applyLocalFilters(listOf(a, b), FilterState(), setOf("f1")).size)
        assertFalse(needsRefetch(FilterState(), FilterState(favouritesOnly = true)))
    }
}
