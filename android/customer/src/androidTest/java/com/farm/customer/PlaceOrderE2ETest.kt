package com.farm.customer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.farm.core.api.DeliveryAddress
import com.farm.core.api.GeoPoint
import com.farm.core.api.Http
import com.farm.core.auth.AuthManager
import com.farm.core.auth.FarmCore
import com.farm.customer.browse.BrowseRepository
import com.farm.customer.browse.FilterState
import com.farm.customer.checkout.ApiResult
import com.farm.customer.checkout.Cart
import com.farm.customer.checkout.add
import com.farm.customer.checkout.CheckoutSession
import com.farm.customer.checkout.DELIVERY_FEE_PER_ORDER
import com.farm.customer.checkout.RealCheckoutApi
import com.farm.customer.ui.toCartItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * End-to-end against a seeded demo backend. Skipped unless a base URL is passed:
 *
 *   ./gradlew :customer:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.apiBaseUrl=http://10.0.2.2/api/v1
 *
 * Reseed first (farm-backend: scripts/reset-demo.sh); accounts are in farm-backend/docs/DEMO.md.
 */
@RunWith(AndroidJUnit4::class)
class PlaceOrderE2ETest {
    @Test fun customerFindsProduceAndPlacesAnOrder() = runBlocking {
        val base = InstrumentationRegistry.getArguments().getString("apiBaseUrl")
        assumeTrue("pass apiBaseUrl to run against a demo backend", !base.isNullOrBlank())
        FarmCore.init(InstrumentationRegistry.getInstrumentation().targetContext)
        Http.baseUrlProvider = { base!! }

        AuthManager.loginWithPassword("+919200000001", "Farm@12345", "customer")
        // Bhilai, where the demo farms are.
        val page = BrowseRepository.search(21.2092, 81.4285, FilterState(radiusKm = 50))
        val listing = page.listings.firstOrNull { it.qtyAvailable >= 1 }
        assumeTrue("the demo backend has no produce with stock near Bhilai", listing != null)

        val cart = Cart().add(listing!!.toCartItem(1.0))
        val address = DeliveryAddress("Home", "Sector 6", null, "Bhilai", "490006", GeoPoint.of(21.2092, 81.4285), "+919200000001")
        val result = CheckoutSession(RealCheckoutApi, DELIVERY_FEE_PER_ORDER).submit(cart, address, "e2e test")
        assertTrue("checkout failed: $result", result is ApiResult.Ok)
        val checkout = (result as ApiResult.Ok).data
        assertEquals(1, checkout.orders.size)
        assertEquals(listing.id, checkout.orders[0].items[0].listingId)
    }
}
