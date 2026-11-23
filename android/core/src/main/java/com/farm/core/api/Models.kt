package com.farm.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Statuses and enums are kept as plain strings so a new value from the backend never crashes the app.

// ─── Identity ────────────────────────────────────────────────────────────────

object Roles {
    const val CUSTOMER = "customer"
    const val FARMER = "farmer"
    const val DELIVERY = "delivery"
    const val ADMIN = "admin"
}

@Serializable
data class User(
    val id: String,
    val name: String = "",
    val phone: String = "",
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val phoneVerified: Boolean = false,
    val farmerVerified: Boolean = false,
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val createdAt: String = "",
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "bearer",
    val expiresIn: Long = 0,
    val user: User,
)

@Serializable
data class OtpRequestResponse(
    val expiresIn: Int = 0,
    val resendAfter: Int = 0,
    val devCode: String? = null,
)

// ─── Shared geometry ─────────────────────────────────────────────────────────

/** GeoJSON point: coordinates are [longitude, latitude]. */
@Serializable
data class GeoPoint(val type: String = "Point", val coordinates: List<Double> = emptyList()) {
    val lng: Double get() = coordinates.getOrElse(0) { 0.0 }
    val lat: Double get() = coordinates.getOrElse(1) { 0.0 }

    companion object {
        fun of(lat: Double, lng: Double) = GeoPoint("Point", listOf(lng, lat))
    }
}

@Serializable
data class PolygonGeometry(val type: String = "Polygon", val coordinates: List<List<List<Double>>> = emptyList())

// ─── Catalog & media ─────────────────────────────────────────────────────────

@Serializable
data class ListingSearchProperties(
    val id: String,
    val farmId: String = "",
    val farmName: String = "",
    val farmerId: String = "",
    val cropId: String = "",
    val cropName: String = "",
    val variety: String? = null,
    val unit: String = "kg",
    val pricePerUnit: Double = 0.0,
    val qtyAvailable: Double = 0.0,
    val harvestDate: String = "",
    val isOrganic: Boolean = false,
    val description: String? = null,
    val distanceM: Double = 0.0,
    val district: String = "",
    val village: String? = null,
    val status: String = "ACTIVE",
    val mediaIds: List<String> = emptyList(),
    /** The farm's average score, 1 to 5; null until it has been rated. */
    val farmRating: Double? = null,
    val farmRatingCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class ListingFeature(val geometry: GeoPoint? = null, val properties: ListingSearchProperties)

@Serializable
data class ListingFeatureCollection(val features: List<ListingFeature> = emptyList(), val nextCursor: String? = null)

@Serializable
data class ListingOut(
    val id: String,
    val farmId: String = "",
    val farmName: String = "",
    val farmerId: String = "",
    val cropId: String = "",
    val cropName: String = "",
    val variety: String? = null,
    val unit: String = "kg",
    val pricePerUnit: Double = 0.0,
    val qtyAvailable: Double = 0.0,
    val harvestDate: String = "",
    val isOrganic: Boolean = false,
    val description: String? = null,
    val district: String = "",
    val village: String? = null,
    val pickupLocation: GeoPoint? = null,
    val status: String = "ACTIVE",
    val mediaIds: List<String> = emptyList(),
    val farmRating: Double? = null,
    val farmRatingCount: Int = 0,
)

@Serializable
data class CropOut(val id: String, val name: String = "", val category: String = "", val defaultUnit: String = "kg")

@Serializable
data class MediaOut(
    val id: String,
    val status: String = "",
    val contentType: String = "",
    val url: String? = null,
    val thumbUrl: String? = null,
    val isGeoVerified: Boolean = false,
    val geoStatus: String = "PENDING",
    val distanceM: Double? = null,
    val rejectReason: String? = null,
)

// ─── Land ────────────────────────────────────────────────────────────────────

@Serializable
data class SeasonOut(
    val id: String,
    val plotId: String = "",
    val season: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val price: Double = 0.0,
    val allowedCrops: List<String> = emptyList(),
)

@Serializable
data class PlotOut(
    val id: String,
    val farmerId: String = "",
    val title: String = "",
    val description: String? = null,
    val areaAcres: Double = 0.0,
    val soilType: String? = null,
    val waterSource: String? = null,
    val status: String? = null,
    val boundary: PolygonGeometry? = null,
    val centroid: GeoPoint? = null,
    val seasons: List<SeasonOut> = emptyList(),
)

/** One plot-season of GET /land/plots/search (the server sends one feature per open season). */
@Serializable
data class PlotSeasonProperties(
    val title: String = "",
    val areaAcres: Double = 0.0,
    val farmerId: String = "",
    val plotSeasonId: String,
    val season: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val price: Double = 0.0,
    val allowedCrops: List<String> = emptyList(),
)

/** A search hit: [id] is the plot id, [geometry] its boundary polygon ([lng, lat] rings). */
@Serializable
data class PlotSeasonFeature(val id: String, val geometry: PolygonGeometry? = null, val properties: PlotSeasonProperties)

@Serializable
data class PlotSearchResult(val features: List<PlotSeasonFeature> = emptyList())

@Serializable
data class ReservationCreate(val plotSeasonId: String, val cropId: String, val expectedQtyKg: Double)

@Serializable
data class ReservationOut(
    val id: String,
    val plotSeasonId: String = "",
    val farmerId: String = "",
    val customerId: String = "",
    val cropId: String = "",
    val expectedQtyKg: Double = 0.0,
    val status: String = "PENDING",
    val deposit: Double? = null,
    val total: Double? = null,
    val createdAt: String? = null,
    val plotTitle: String? = null,
    val seasonName: String? = null,
    /** When a PENDING request lapses if the farmer does not answer (48 hours). */
    val expiresAt: String? = null,
)

// ─── Logistics ───────────────────────────────────────────────────────────────

@Serializable
data class Stop(
    val id: String,
    val poolId: String = "",
    val orderId: String = "",
    val seq: Int = 0,
    val status: String = "PENDING", // PENDING, ARRIVED, DONE, SKIPPED
    val stopType: String = "DROP", // PICKUP, DROP
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val doneAt: String? = null,
    val eta: String? = null,
    val notes: String? = null,
    val otp: String? = null,
    val otpVerified: Boolean = false,
    val photoMediaId: String? = null,
    val weightKg: Double? = null,
)

@Serializable
data class OfferPlace(val lat: Double = 0.0, val lng: Double = 0.0, val address: String? = null)

/** One stop of an offered route, in driving order (no address until the route is accepted). */
@Serializable
data class OfferStop(val seq: Int = 0, val stopType: String = "DROP", val lat: Double = 0.0, val lng: Double = 0.0)

/** A route offered to this partner that nobody has accepted yet (GET /logistics/offers/mine). */
@Serializable
data class RouteOffer(
    val poolId: String,
    val poolDate: String = "",
    val stopCount: Int = 0,
    val pickupCount: Int = 0,
    val dropCount: Int = 0,
    val totalKm: Double? = null,
    /** From the partner to the first pickup, when the server knows where the partner is. */
    val distanceKm: Double? = null,
    val firstPickup: OfferPlace? = null,
    /** Every stop in driving order, to draw the route before accepting. Empty from older servers. */
    val stops: List<OfferStop> = emptyList(),
    val offeredAt: String = "",
    val expiresAt: String? = null,
)

@Serializable
data class Pool(
    val id: String,
    val poolDate: String = "",
    val status: String = "PENDING",
    val partnerId: String? = null,
    val assignedAt: String? = null,
    val createdAt: String = "",
    val kmSaved: Double? = null,
    val totalKm: Double? = null,
    val stops: List<Stop> = emptyList(),
    /** The road line through the stops, [lat, lng] pairs; null when the routing engine had none. */
    val routePath: List<List<Double>>? = null,
)

@Serializable
data class TrackStop(
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    /** The code the customer gives the partner at the door; logistics sends it with the drop stop. */
    val otp: String? = null,
    val otpVerified: Boolean = false,
)

/** GET /logistics/orders/{id}/track. Everything but orderId can be missing. */
@Serializable
data class Track(
    val orderId: String = "",
    val poolId: String? = null,
    val status: String? = null,
    val stop: TrackStop? = null,
    val partnerName: String? = null,
    val partnerPhone: String? = null,
    val partnerLat: Double? = null,
    val partnerLng: Double? = null,
    val partnerUpdatedAt: String? = null,
    val eta: String? = null,
)

/** GET /land/reservations/{id}/updates: one crop update the farmer posted. */
@Serializable
data class CropUpdateOut(
    val id: String,
    val reservationId: String = "",
    val stage: String = "",
    val note: String? = null,
    val mediaIds: List<String> = emptyList(),
    val location: GeoPoint? = null,
    val createdAt: String = "",
)

/** GET /land/reservations/{id}/agreement: the terms made when the farmer accepted. */
@Serializable
data class AgreementOut(
    val id: String,
    val reservationId: String = "",
    val plotId: String = "",
    val plotTitle: String = "",
    val season: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val cropId: String = "",
    val expectedQtyKg: Double = 0.0,
    val pricePerKg: Double = 0.0,
    val total: Double = 0.0,
    val deposit: Double = 0.0,
    val balance: Double = 0.0,
    val terms: List<String> = emptyList(),
    val createdAt: String = "",
)

// ─── Order ───────────────────────────────────────────────────────────────────

@Serializable
data class DeliveryAddress(
    val label: String? = null,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val pincode: String,
    val location: GeoPoint,
    val contactPhone: String? = null,
)

@Serializable
data class OrderItemIn(val listingId: String, val qty: Double)

@Serializable
data class CheckoutRequest(
    val items: List<OrderItemIn>,
    val deliveryAddress: DeliveryAddress,
    val deliveryDate: String? = null,
    val paymentIntentId: String,
    val notes: String? = null,
    /** IMMEDIATE (route as soon as it is packed) or FLEXIBLE (waits for the daily shared-delivery batch, cheaper). */
    val deliveryMode: String = "IMMEDIATE",
)

@Serializable
data class OrderItem(
    val listingId: String? = null,
    val farmId: String? = null,
    val name: String = "",
    val unit: String = "",
    val qty: Double = 0.0,
    val unitPrice: Double = 0.0,
    val lineTotal: Double = 0.0,
    val pickupLocation: GeoPoint? = null,
)

@Serializable
data class Order(
    val id: String,
    val checkoutId: String = "",
    val orderType: String = "PRODUCE",
    val farmId: String? = null,
    val reservationId: String? = null,
    val harvestOption: String? = null,
    val status: String = "PLACED",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    val currency: String = "INR",
    val deliveryAddress: DeliveryAddress? = null,
    val deliveryDate: String? = null,
    /** IMMEDIATE or FLEXIBLE: a FLEXIBLE order waits for the daily shared-delivery batch. */
    val deliveryMode: String = "IMMEDIATE",
    /** FLEXIBLE orders not on a route yet: when the next batch that can take them runs (an estimate). */
    val batchAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class Checkout(
    val id: String,
    val customerId: String = "",
    val paymentIntentId: String = "",
    val paymentStatus: String = "",
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    val currency: String = "INR",
    val orders: List<Order> = emptyList(),
    val createdAt: String = "",
)

@Serializable
data class OrderEvent(val status: String, val occurredAt: String, val note: String? = null)

@Serializable
data class OrderRating(val orderId: String = "", val score: Int, val comment: String? = null, val createdAt: String = "")

@Serializable
data class PaymentRecord(
    val id: String = "",
    val kind: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val status: String = "",
    val createdAt: String = "",
)

@Serializable
data class OrderDetail(
    val id: String,
    val checkoutId: String = "",
    val orderType: String = "PRODUCE",
    val farmId: String? = null,
    val reservationId: String? = null,
    val harvestOption: String? = null,
    val status: String = "PLACED",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    val currency: String = "INR",
    val deliveryAddress: DeliveryAddress? = null,
    val deliveryDate: String? = null,
    /** IMMEDIATE or FLEXIBLE: a FLEXIBLE order waits for the daily shared-delivery batch. */
    val deliveryMode: String = "IMMEDIATE",
    /** FLEXIBLE orders not on a route yet: when the next batch that can take them runs (an estimate). */
    val batchAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val events: List<OrderEvent> = emptyList(),
    val rating: OrderRating? = null,
    val payments: List<PaymentRecord> = emptyList(),
    val balanceDue: Double = 0.0,
)

@Serializable
data class OrderSummary(
    val id: String,
    val checkoutId: String = "",
    val orderType: String = "PRODUCE",
    val farmId: String? = null,
    val reservationId: String? = null,
    val harvestOption: String? = null,
    val status: String = "PLACED",
    val itemCount: Int = 0,
    val total: Double = 0.0,
    val currency: String = "INR",
    val deliveryMode: String = "IMMEDIATE",
    val batchAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class OrderList(val items: List<OrderSummary> = emptyList(), val nextCursor: String? = null)

@Serializable
data class PaymentIntentCreate(val amount: Double, val kind: String = "FULL", val referenceId: String? = null)

@Serializable
data class PaymentIntent(
    val id: String,
    val amount: Double = 0.0,
    val currency: String = "INR",
    val kind: String = "FULL",
    val referenceId: String? = null,
    val status: String = "",
    val createdAt: String = "",
)

@Serializable
data class HarvestOrderChoice(
    val reservationId: String,
    val option: String, // DELIVERY or BUYBACK
    val deliveryAddress: DeliveryAddress? = null,
    val deliveryDate: String? = null,
)

/** One choice of GET /order/orders/delivery-options. */
@Serializable
data class DeliveryOption(val mode: String, val feePerOrder: Double = 0.0)

/** What the customer can choose at checkout: the fee of each delivery mode and when the shared batch runs. */
@Serializable
data class DeliveryOptions(
    val options: List<DeliveryOption> = emptyList(),
    /** Local time of the daily shared-delivery batch, "HH:MM". */
    val batchTime: String = "10:00",
    val nextBatchAt: String? = null,
)

@Serializable
data class OrderRatingCreate(val score: Int, val comment: String? = null)

// ─── Notification ────────────────────────────────────────────────────────────

@Serializable
data class AppNotification(
    val id: String,
    val userId: String = "",
    val type: String = "SYSTEM",
    val title: String = "",
    val body: String = "",
    val payload: JsonObject? = null,
    val readAt: String? = null,
    val createdAt: String = "",
)

@Serializable
data class NotificationPage(
    val items: List<AppNotification> = emptyList(),
    val nextCursor: String? = null,
    val unreadCount: Int = 0,
)
