package com.farm.customer.notifications

import com.farm.core.api.AppNotification
import com.farm.core.api.Http
import com.farm.core.api.NotificationPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Inbox helpers (#44): where a notification leads, ordering, optimistic read, the badge. */
class NotificationsTest {
    private fun n(id: String, createdAt: String, payload: String = "{}", readAt: String? = null): AppNotification =
        Http.json.decodeFromString(
            """{"id":"$id","userId":"u","type":"ORDER_PACKED","title":"t","body":"b","payload":$payload,
            "readAt":${readAt?.let { "\"$it\"" } ?: "null"},"createdAt":"$createdAt"}""",
        )

    @Test
    fun orderAndReservationNotificationsLeadToTheirScreen() {
        assertEquals(NotificationTarget.Order("o1"), notificationTarget(n("1", "2026-11-20T10:00:00Z", """{"orderId":"o1","total":120.0}""")))
        assertEquals(
            NotificationTarget.Reservation("r1"),
            notificationTarget(n("2", "2026-11-20T10:00:00Z", """{"reservationId":"r1","deposit":400.0}""")),
        )
        // a deposit payment has no order id: the server sends "" and the notification leads nowhere
        assertNull(notificationTarget(n("3", "2026-11-20T10:00:00Z", """{"orderId":"","total":400.0}""")))
        assertNull(notificationTarget(n("4", "2026-11-20T10:00:00Z")))
    }

    @Test
    fun newestFirstAndNoDuplicatesAcrossPages() {
        val list = newestFirst(
            listOf(
                n("a", "2026-11-20T10:00:00Z"),
                n("b", "2026-11-21T10:00:00+05:30"),
                n("a", "2026-11-20T10:00:00Z"),
                n("c", "2026-11-22T08:00:00Z"),
            ),
        )
        assertEquals(listOf("c", "b", "a"), list.map { it.id })
    }

    @Test
    fun markReadLocallyChangesOnlyUnreadOnes() {
        val items = listOf(n("a", "2026-11-20T10:00:00Z"), n("b", "2026-11-20T11:00:00Z", readAt = "2026-11-20T12:00:00Z"))
        val one = markReadLocally(items, "a", "NOW")
        assertEquals(listOf("NOW", "2026-11-20T12:00:00Z"), one.map { it.readAt })
        val all = markReadLocally(listOf(n("a", "x"), n("c", "y")), null, "NOW")
        assertEquals(listOf("NOW", "NOW"), all.map { it.readAt })
    }

    @Test
    fun badgeHidesZeroAndCapsAt99() {
        assertNull(badgeText(0))
        assertEquals("7", badgeText(7))
        assertEquals("99+", badgeText(140))
    }

    @Test
    fun pageDecodesTheServerShape() {
        val page = Http.json.decodeFromString<NotificationPage>(
            """{"items":[{"id":"1","userId":"u","type":"PAYMENT_SUCCEEDED","title":"Paid","body":"b","payload":{"orderId":"o9"},"readAt":null,"createdAt":"2026-11-20T10:00:00Z"}],"nextCursor":"abc","unreadCount":1}""",
        )
        assertEquals(1, page.unreadCount)
        assertEquals("abc", page.nextCursor)
        assertEquals(NotificationTarget.Order("o9"), notificationTarget(page.items[0]))
    }
}
