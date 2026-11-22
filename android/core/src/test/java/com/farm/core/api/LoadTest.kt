package com.farm.core.api

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The typed load used by every screen: what is caught, and what the person is told. */
class LoadTest {
    @Test
    fun loadKeepsTypedFailuresAndMessages() {
        val offline = load { throw NetworkException(IOException("down")) }
        assertTrue(isOffline(offline.exceptionOrNull()))
        assertEquals(
            "Cannot reach the server at http://10.0.2.2/api/v1. Check your connection and try again.",
            loadErrorMessage(offline.exceptionOrNull(), "http://10.0.2.2/api/v1"),
        )
        val refused = load { throw ApiException("Listing not found", "LISTING_NOT_FOUND", 404) }
        assertFalse(isOffline(refused.exceptionOrNull()))
        assertEquals("Listing not found", loadErrorMessage(refused.exceptionOrNull(), "u"))
        val garbled = load { throw SerializationException("bad") }
        assertEquals("The server sent a reply the app could not read.", loadErrorMessage(garbled.exceptionOrNull(), "u"))
        assertEquals(3, load { 3 }.getOrNull())
    }

    @Test(expected = IllegalStateException::class)
    fun loadDoesNotHideProgrammingErrors() {
        load { error("bug") }
    }

    @Test
    fun apiErrorUsesTheServerEnvelope() {
        val e = apiError(HttpResult(422, """{"error":{"code":"SEASON_ENDED","message":"This season has ended","correlationId":"c"}}"""), "Could not reserve")
        assertEquals("This season has ended", e.message)
        assertEquals("SEASON_ENDED", e.code)
        assertEquals(422, e.status)
        val bare = apiError(HttpResult(502, "Bad gateway"), "Could not reserve")
        assertEquals("Could not reserve (502)", bare.message)
    }
}
