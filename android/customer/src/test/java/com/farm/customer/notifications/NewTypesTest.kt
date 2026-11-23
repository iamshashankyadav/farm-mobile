package com.farm.customer.notifications

import com.farm.core.api.AppNotification
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The notification types added by the backend audit (#64) open the reservation. */
class NewTypesTest {
    private fun n(type: String, payload: Map<String, String>) =
        AppNotification(id = type, type = type, title = "t", body = "b", payload = JsonObject(payload.mapValues { JsonPrimitive(it.value) }))

    @Test fun reservationTypesOpenTheReservation() {
        listOf("RESERVATION_REQUESTED", "RESERVATION_REJECTED", "RESERVATION_CANCELLED", "RESERVATION_EXPIRED", "RESERVATION_ACTIVATED", "CROP_UPDATE")
            .forEach { assertEquals(it, NotificationTarget.Reservation("r1"), notificationTarget(n(it, mapOf("reservationId" to "r1")))) }
    }

    @Test fun farmerVerifiedOpensNothing() = assertNull(notificationTarget(n("FARMER_VERIFIED", emptyMap())))
}
