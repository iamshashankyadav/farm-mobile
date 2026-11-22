package com.farm.customer.checkout

import com.farm.core.api.Checkout
import com.farm.core.api.CheckoutRequest
import com.farm.core.api.DeliveryAddress
import com.farm.core.api.ErrorEnvelope
import com.farm.core.api.GeoPoint
import com.farm.core.api.Http
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error mapping of POST /order/orders and the price breakdown shown before paying (#39). */
class CheckoutErrorTest {
    private fun body(json: String) = Http.json.decodeFromString<ErrorEnvelope>(json).error

    @Test
    fun paymentDeclinedIs402() {
        val e = parseCheckoutError(402, body("""{"error":{"code":"PAYMENT_FAILED","message":"Payment failed"}}"""))
        assertTrue(e is CheckoutError.PaymentFailed)
    }

    @Test
    fun stockErrorListsEachShortItem() {
        val e = parseCheckoutError(
            409,
            body(
                """{"error":{"code":"STOCK_UNAVAILABLE","message":"Not enough stock","correlationId":"c1",
                "details":[{"listingId":"l1","requested":3,"available":1},{"listingId":"l2","requested":2,"available":0}]}}""",
            ),
        )
        assertTrue(e is CheckoutError.Stock)
        assertEquals(
            listOf(Shortage("l1", 3.0, 1.0), Shortage("l2", 2.0, 0.0)),
            (e as CheckoutError.Stock).shortages,
        )
    }

    @Test
    fun listingNotAvailableNamesTheListing() {
        val e = parseCheckoutError(
            409,
            body("""{"error":{"code":"LISTING_NOT_AVAILABLE","message":"Listing l2 is paused","details":[{"listingId":"l2","status":"PAUSED"}]}}"""),
        )
        assertTrue(e is CheckoutError.ListingUnavailable)
        assertEquals(listOf("l2"), (e as CheckoutError.ListingUnavailable).listingIds)
    }

    @Test
    fun idempotencyCodesAreToldApart() {
        assertTrue(parseCheckoutError(409, body("""{"error":{"code":"IDEMPOTENCY_IN_PROGRESS"}}""")) is CheckoutError.InProgress)
        assertTrue(parseCheckoutError(409, body("""{"error":{"code":"IDEMPOTENCY_KEY_REUSED"}}""")) is CheckoutError.AttemptInvalid)
    }

    @Test
    fun validationErrorKeepsFieldMessages() {
        val e = parseCheckoutError(
            422,
            body("""{"error":{"code":"VALIDATION_ERROR","message":"Invalid","details":[{"field":"deliveryAddress.pincode","message":"6 digits"}]}}"""),
        )
        assertTrue(e is CheckoutError.Validation)
        assertEquals(listOf("deliveryAddress.pincode" to "6 digits"), (e as CheckoutError.Validation).fields)
    }

    @Test
    fun unknownErrorShowsTheServerMessage() {
        val e = parseCheckoutError(500, body("""{"error":{"code":"INTERNAL","message":"Boom"}}"""))
        assertTrue(e is CheckoutError.Server)
        assertEquals("Boom", e.message)
    }

    @Test
    fun breakdownChargesOneDeliveryFeePerFarm() {
        val cart = Cart()
            .add(CartItem("l1", "f1", "Farm A", "Tomato", "kg", 32.0, 2.0))
            .add(CartItem("l2", "f1", "Farm A", "Chilli", "kg", 10.5, 1.0))
            .add(CartItem("l3", "f2", "Farm B", "Onion", "kg", 20.0, 3.0))
        val b = cart.breakdown(20.0)
        assertEquals(listOf("f1", "f2"), b.farms.map { it.farmId })
        assertEquals(74.5, b.farms[0].items, 0.001)
        assertEquals(94.5, b.farms[0].total, 0.001)
        assertEquals(80.0, b.farms[1].total, 0.001)
        assertEquals(134.5, b.items, 0.001)
        assertEquals(40.0, b.delivery, 0.001)
        assertEquals(174.5, b.total, 0.001)
        assertEquals(cart.estimateTotal(20.0), b.total, 0.001)
    }

    private class Api(private val results: MutableList<ApiResult<Checkout>>) : CheckoutApi {
        val keys = mutableListOf<String>()
        var intents = 0
        override suspend fun createPaymentIntent(amount: Double): ApiResult<String> = ApiResult.Ok("pi-${++intents}")
        override suspend fun placeOrder(key: String, body: CheckoutRequest): ApiResult<Checkout> {
            keys += key
            return results.removeAt(0)
        }
    }

    @Test
    fun inProgressKeepsTheKeyAndAPaymentFailureGetsANewOne() = runBlocking {
        val api = Api(
            mutableListOf(
                ApiResult.Err(CheckoutError.InProgress("busy")),
                ApiResult.Err(CheckoutError.PaymentFailed("declined")),
                ApiResult.Ok(Checkout("c1")),
            ),
        )
        var n = 0
        val session = CheckoutSession(api, 20.0) { "key-${++n}" }
        val cart = Cart().add(CartItem("l1", "f1", "Farm A", "Tomato", "kg", 32.0, 1.0))
        val address = DeliveryAddress(line1 = "1 Road", city = "Durg", pincode = "491001", location = GeoPoint.of(21.19, 81.28))
        repeat(3) { session.submit(cart, address, null) }
        assertEquals(listOf("key-1", "key-1", "key-2"), api.keys)
        assertEquals(2, api.intents)
    }
}
