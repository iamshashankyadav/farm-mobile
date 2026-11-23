package com.farm.delivery.ui

import com.farm.core.api.ApiException
import com.farm.core.api.OfferPlace
import com.farm.core.api.RouteOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffersTest {
    private val offer = RouteOffer(
        poolId = "p1", poolDate = "2026-11-24", stopCount = 4, pickupCount = 2, dropCount = 2, totalKm = 12.46,
        distanceKm = 1.24, firstPickup = OfferPlace(21.0, 81.0, "Patan farm"),
    )

    @Test fun summaryAndWhere() {
        assertEquals("4 stops (2 pickups, 2 drops) · 12.5 km route", offerSummary(offer))
        assertEquals("First pickup 1.2 km from you: Patan farm", offerWhere(offer))
        assertEquals("1 stops (1 pickup, 0 drops)", offerSummary(RouteOffer("p", stopCount = 1, pickupCount = 1)))
        assertEquals("First pickup", offerWhere(RouteOffer("p")))
    }

    @Test fun losingTheRaceIsRecognised() {
        assertTrue(isTaken(ApiException("taken", "POOL_TAKEN", 409)))
        assertFalse(isTaken(ApiException("nope", "OFFER_NOT_FOUND", 404)))
        assertFalse(isTaken(null))
    }
}
