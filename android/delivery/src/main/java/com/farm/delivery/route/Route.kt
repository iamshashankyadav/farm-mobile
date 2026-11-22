package com.farm.delivery.route

import com.farm.core.api.Pool
import com.farm.core.api.Stop
import java.time.LocalDate
import java.util.Locale

enum class StopType { PICKUP, DROP }
enum class StopStatus { PENDING, ARRIVED, DONE, SKIPPED }

/** True while a stop still has to be worked (not yet arrived, or arrived but not finished). */
val StopStatus.open: Boolean get() = this == StopStatus.PENDING || this == StopStatus.ARRIVED

/** One stop as the route screen and the pickup / delivery sheets show it. */
data class RouteStop(
    val id: String,
    val orderId: String,
    val seq: Int,
    val type: StopType,
    val status: StopStatus,
    val label: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val customerName: String,
    val items: List<String>,
    /** Dialable phone of the customer (drop) or farm contact; null when the API has none. */
    val phone: String? = null,
    /** Dispatcher or customer notes for this stop. */
    val notes: String? = null,
)

data class RouteSummary(
    val pickups: Int,
    val drops: Int,
    val done: Int,
    val finished: Boolean,
    val next: RouteStop?,
)

private fun shortOrder(orderId: String) = "#${orderId.take(8)}"

private fun statusOf(s: String): StopStatus = when (s) {
    "DONE" -> StopStatus.DONE
    "SKIPPED" -> StopStatus.SKIPPED
    "ARRIVED" -> StopStatus.ARRIVED
    else -> StopStatus.PENDING
}

private fun trimNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

fun toRouteStop(stop: Stop): RouteStop {
    val type = if (stop.stopType == "PICKUP") StopType.PICKUP else StopType.DROP
    val name = stop.customerName?.trim()?.ifEmpty { null }
    val weight = stop.weightKg?.takeIf { it != 0.0 }?.let { " · ${trimNumber(it)} kg" } ?: ""
    return RouteStop(
        id = stop.id,
        orderId = stop.orderId,
        seq = stop.seq,
        type = type,
        status = statusOf(stop.status),
        label = if (type == StopType.PICKUP) "Farm pickup for order ${shortOrder(stop.orderId)}"
        else "Customer: ${name ?: "order ${shortOrder(stop.orderId)}"}",
        address = stop.address?.trim()?.ifEmpty { null }
            ?: String.format(Locale.US, "%.5f, %.5f", stop.lat, stop.lng),
        lat = stop.lat,
        lng = stop.lng,
        customerName = name ?: if (type == StopType.PICKUP) "Farm" else "Customer",
        items = listOf("Order ${shortOrder(stop.orderId)}$weight"),
        phone = stop.customerPhone?.trim()?.ifEmpty { null },
        notes = stop.notes?.trim()?.ifEmpty { null },
    )
}

/** The pool's stops in driving order (the pooling job always puts a pickup before its drop). */
fun routeStops(pool: Pool?): List<RouteStop> =
    pool?.stops.orEmpty().sortedBy { it.seq }.map(::toRouteStop)

fun routeSummary(stops: List<RouteStop>): RouteSummary {
    val worked = stops.count { !it.status.open }
    return RouteSummary(
        pickups = stops.count { it.type == StopType.PICKUP },
        drops = stops.count { it.type == StopType.DROP },
        done = stops.count { it.status == StopStatus.DONE },
        finished = stops.isNotEmpty() && worked == stops.size,
        next = stops.firstOrNull { it.status.open },
    )
}

/** Replaces one stop with the server's answer in a loaded pool, so the screen updates before the route is re-read. */
fun withStop(pool: Pool?, updated: Stop): Pool? =
    pool?.copy(stops = pool.stops.map { if (it.id == updated.id) updated else it })

/** Marks one stop done in a loaded pool, so the screen updates before the route is re-read. */
fun markStopDone(pool: Pool?, stopId: String): Pool? =
    pool?.copy(stops = pool.stops.map { if (it.id == stopId) it.copy(status = "DONE") else it })

/** A phone number as a tel: URI ("+91 94000 00001" -> "tel:+919400000001"); null without digits. */
fun dialUri(phone: String?): String? {
    val cleaned = phone?.filter { it.isDigit() || it == '+' }?.takeIf { it.any(Char::isDigit) } ?: return null
    return "tel:$cleaned"
}

/** Total straight-line distance of the route through its stops in order, in metres. */
fun routeLengthM(stops: List<RouteStop>): Double =
    stops.zipWithNext().sumOf { (a, b) -> haversine(a.lat, a.lng, b.lat, b.lng) }

private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dp / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dl / 2).let { it * it }
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/** Local calendar date as YYYY-MM-DD (the pool date the partner means by "today"). */
fun localIsoDate(d: LocalDate = LocalDate.now()): String = d.toString()

fun mapsUrl(lat: Double, lng: Double, label: String): String =
    "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng" +
        "&destination_place_id=${java.net.URLEncoder.encode(label, "UTF-8")}&travelmode=driving"
