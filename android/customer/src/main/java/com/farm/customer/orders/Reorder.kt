package com.farm.customer.orders

import com.farm.core.api.CatalogApi
import com.farm.core.api.ListingOut
import com.farm.core.api.OrderItem
import com.farm.core.api.load
import com.farm.customer.checkout.CartItem
import com.farm.customer.checkout.formatQty
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** What "Order again" put in the cart and what it could not. */
data class ReorderResult(val added: List<CartItem>, val reduced: List<String>, val unavailable: List<String>) {
    /** One line for the cart screen; null when everything went back in as it was. */
    val message: String?
        get() = buildList {
            if (unavailable.isNotEmpty()) add("No longer available: ${unavailable.joinToString()}.")
            if (reduced.isNotEmpty()) add("Less in stock now: ${reduced.joinToString()}.")
            if (added.isEmpty() && unavailable.isNotEmpty()) add("Nothing from this order could be added.")
        }.joinToString(" ").ifEmpty { null }
}

/**
 * Turns a past order's lines into cart items at today's price and stock. [current] is the listing
 * as the catalog returns it now (null when it is gone or could not be loaded). A listing that is
 * not ACTIVE or has no stock is unavailable; a smaller stock lowers the quantity.
 */
fun planReorder(items: List<OrderItem>, current: Map<String, ListingOut?>): ReorderResult {
    val added = mutableListOf<CartItem>()
    val reduced = mutableListOf<String>()
    val unavailable = mutableListOf<String>()
    for (item in items) {
        val id = item.listingId
        val listing = id?.let { current[it] }
        if (listing == null || listing.status != "ACTIVE" || listing.qtyAvailable <= 0) {
            unavailable += item.name.ifBlank { "an item" }
            continue
        }
        val qty = minOf(item.qty, listing.qtyAvailable)
        if (qty < item.qty) reduced += "${listing.cropName.ifBlank { item.name }} (${formatQty(qty)} ${listing.unit})"
        added += CartItem(
            listingId = listing.id,
            farmId = listing.farmId,
            farmName = listing.farmName,
            name = listing.cropName + (listing.variety?.let { " ($it)" } ?: ""),
            unit = listing.unit,
            unitPrice = listing.pricePerUnit,
            qty = qty,
            maxQty = listing.qtyAvailable,
        )
    }
    return ReorderResult(added, reduced, unavailable)
}

/** Loads today's listing for each line (in parallel) and plans the reorder. A listing that fails to load counts as unavailable. */
suspend fun reorder(items: List<OrderItem>): ReorderResult = coroutineScope {
    val ids = items.mapNotNull { it.listingId }.distinct()
    val current = ids.map { id -> async { id to load { CatalogApi.getListing(id) }.getOrNull() } }.awaitAll().toMap()
    planReorder(items, current)
}

/** Harvest orders come from a reservation, not from listings, so they cannot be ordered again. */
fun canReorder(orderType: String?, items: List<OrderItem>): Boolean =
    orderType != "HARVEST" && items.any { it.listingId != null }

/** "What happens next" under an order's rail, in the customer's words. */
fun orderNextStep(status: String): String? = when (status) {
    "PLACED" -> "Next: your payment is confirmed (a few seconds)."
    "CONFIRMED" -> "Next: the farmer packs your order."
    "PACKED" -> "Next: a delivery partner collects it."
    "READY_FOR_DISPATCH" -> "Next: it is added to a delivery route."
    "POOLED" -> "Next: the delivery partner picks it up from the farm."
    "IN_TRANSIT" -> "Next: keep your delivery code ready for the partner."
    "DELIVERED" -> "Next: the order completes and you can rate it."
    "COMPLETED", "CANCELLED" -> null
    else -> null
}

/** After checkout, poll each new order every [PLACED_POLL_MS] for at most [PLACED_POLL_LIMIT_MS] until payment is confirmed. */
const val PLACED_POLL_MS = 3_000L
const val PLACED_POLL_LIMIT_MS = 60_000L
