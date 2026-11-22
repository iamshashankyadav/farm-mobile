package com.farm.delivery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.farm.core.api.Http
import com.farm.core.api.LogisticsApi
import com.farm.core.auth.AuthManager
import com.farm.core.auth.FarmCore
import com.farm.delivery.route.StopStatus
import com.farm.delivery.route.StopType
import com.farm.delivery.route.localIsoDate
import com.farm.delivery.route.routeStops
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * End-to-end against a seeded demo backend. Skipped unless a base URL is passed:
 *
 *   ./gradlew :delivery:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.apiBaseUrl=http://10.0.2.2/api/v1
 *
 * Reseed first (farm-backend: scripts/reset-demo.sh); accounts are in farm-backend/docs/DEMO.md.
 */
@RunWith(AndroidJUnit4::class)
class CompleteStopE2ETest {
    @Test fun partnerArrivesAndCompletesAPickup() = runBlocking {
        val base = InstrumentationRegistry.getArguments().getString("apiBaseUrl")
        assumeTrue("pass apiBaseUrl to run against a demo backend", !base.isNullOrBlank())
        FarmCore.init(InstrumentationRegistry.getInstrumentation().targetContext)
        Http.baseUrlProvider = { base!! }

        AuthManager.loginWithPassword("+919400000001", "Farm@12345", "delivery")
        val stops = routeStops(LogisticsApi.getMyRoute(localIsoDate()))
        val pickup = stops.firstOrNull { it.type == StopType.PICKUP && it.status == StopStatus.PENDING }
        assumeTrue("no pending pickup on today's route; reseed the demo backend", pickup != null)

        assertEquals("ARRIVED", LogisticsApi.arrive(pickup!!.id).status)
        assertEquals("DONE", LogisticsApi.submitPickup(pickup.id, "e2e test").status)
    }
}
