package com.farm.customer.notifications

import com.farm.core.api.AppNotification
import com.farm.customer.orders.parseInstant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/** Where tapping a notification leads (the payload carries the id). */
sealed interface NotificationTarget {
    data class Order(val orderId: String) : NotificationTarget
    data class Reservation(val reservationId: String) : NotificationTarget
}

private fun JsonObject?.id(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }

/**
 * An order notification (ORDER_*, PAYMENT_SUCCEEDED for a checkout) opens the order; a
 * reservation one (RESERVATION_ACCEPTED, HARVEST_READY) opens the reservation. A deposit payment
 * has no order id and leads nowhere.
 */
fun notificationTarget(n: AppNotification): NotificationTarget? {
    n.payload.id("orderId")?.let { return NotificationTarget.Order(it) }
    n.payload.id("reservationId")?.let { return NotificationTarget.Reservation(it) }
    return null
}

/** Newest first, whatever order the pages arrived in; a notification shown twice appears once. */
fun newestFirst(items: List<AppNotification>): List<AppNotification> =
    items.distinctBy { it.id }.sortedByDescending { parseInstant(it.createdAt) ?: Instant.EPOCH }

/** Mark one notification (or all, with [id] null) as read on the screen before the server answers. */
fun markReadLocally(items: List<AppNotification>, id: String?, now: String): List<AppNotification> =
    items.map { if (it.readAt == null && (id == null || it.id == id)) it.copy(readAt = now) else it }

/** The badge text: nothing at 0, the number up to 99, then "99+". */
fun badgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}
