package com.farm.core.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The API layer against a MockWebServer: success, the error envelope, 404s that mean "none", no network (#47). */
class ServicesTest {
    private lateinit var server: MockWebServer

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun envelope(code: String, message: String) =
        """{"error":{"code":"$code","message":"$message","correlationId":"c-1","details":[{"field":"radius_km","message":"must be at most 100"}]}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        Http.baseUrlProvider = { server.url("/api/v1").toString() }
        Http.authToken = "token-1"
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        Http.authToken = null
    }

    @Test
    fun catalogSearchSendsFiltersAndReadsGeoJson() = runBlocking {
        server.enqueue(
            json(
                200,
                """{"type":"FeatureCollection","nextCursor":"n2","features":[{"type":"Feature","id":"l1",
                "geometry":{"type":"Point","coordinates":[81.2,21.1]},"properties":{"id":"l1","cropName":"Tomato","farmRating":4.5,"farmRatingCount":2}}]}""",
            ),
        )
        val page = CatalogApi.searchListings(21.1, 81.2, radiusKm = 25, crop = "tomato", grade = "B", organic = true, sort = "price", limit = 20)
        assertEquals("n2", page.nextCursor)
        assertEquals(4.5, page.features[0].properties.farmRating!!, 0.0)
        val req = server.takeRequest()
        val url = req.requestUrl!!
        assertEquals("/api/v1/catalog/listings/search", url.encodedPath)
        assertEquals("25", url.queryParameter("radius_km"))
        assertEquals("tomato", url.queryParameter("crop"))
        assertEquals("B", url.queryParameter("grade"))
        assertEquals("true", url.queryParameter("organic"))
        assertNull(url.queryParameter("cursor"))
        assertEquals("Bearer token-1", req.getHeader("Authorization"))
    }

    @Test
    fun errorEnvelopeBecomesApiExceptionWithCodeAndMessage() = runBlocking {
        server.enqueue(json(422, envelope("VALIDATION_ERROR", "Invalid query")))
        try {
            CatalogApi.searchListings(21.1, 81.2, radiusKm = 500)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(422, e.status)
            assertEquals("VALIDATION_ERROR", e.code)
            assertEquals("Invalid query", e.message)
        }
    }

    @Test
    fun noServerIsANetworkException() = runBlocking {
        server.shutdown()
        val result = load { CatalogApi.searchListings(21.1, 81.2) }
        assertTrue(isOffline(result.exceptionOrNull()))
        assertTrue(loadErrorMessage(result.exceptionOrNull(), "http://x/api/v1").startsWith("Cannot reach the server at http://x/api/v1"))
    }

    @Test
    fun myRoute404MeansNoRouteToday() = runBlocking {
        server.enqueue(json(404, envelope("NOT_FOUND", "No pool")))
        assertNull(LogisticsApi.getMyRoute("2026-11-22"))
        assertEquals("2026-11-22", server.takeRequest().requestUrl!!.queryParameter("pool_date"))
    }

    @Test
    fun arriveAndSkipPostToTheStop() = runBlocking {
        server.enqueue(json(200, """{"id":"s1","status":"ARRIVED","stopType":"DROP"}"""))
        server.enqueue(json(200, """{"id":"s1","status":"SKIPPED","stopType":"DROP"}"""))
        assertEquals("ARRIVED", LogisticsApi.arrive("s1").status)
        assertEquals("SKIPPED", LogisticsApi.skip("s1", "Customer not available").status)
        val arrive = server.takeRequest()
        assertEquals("POST", arrive.method)
        assertEquals("/api/v1/logistics/stops/s1/arrive", arrive.requestUrl!!.encodedPath)
        val skip = server.takeRequest()
        assertEquals("/api/v1/logistics/stops/s1/skip", skip.requestUrl!!.encodedPath)
        val body = Json.parseToJsonElement(skip.body.readUtf8()).jsonObject
        assertEquals("Customer not available", body["reason"]!!.jsonPrimitive.content)
    }

    @Test
    fun wrongOtpShowsTheServersMessage() = runBlocking {
        server.enqueue(json(400, envelope("INVALID_OTP", "The delivery code is wrong")))
        val result = load { LogisticsApi.submitDelivery("s1", "0000", "m1", null) }
        val e = result.exceptionOrNull() as ApiException
        assertTrue(e.message!!.startsWith("The delivery code is wrong"))
    }

    @Test
    fun locationPingCarriesThePoolAndNeverThrows() = runBlocking {
        server.enqueue(json(202, "{}"))
        assertTrue(LogisticsApi.sendLocationPing(21.0, 81.0, null, null, 5.0, "2026-11-22T10:00:00Z", poolId = "p1"))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("p1", body["poolId"]!!.jsonPrimitive.content)
        server.shutdown()
        assertEquals(false, LogisticsApi.sendLocationPing(21.0, 81.0, null, null, null, "2026-11-22T10:00:30Z"))
    }

    @Test
    fun cropsList() = runBlocking {
        server.enqueue(json(200, """[{"id":"tomato","name":"Tomato","category":"vegetable","defaultUnit":"kg"}]"""))
        assertEquals("Tomato", CatalogApi.listCrops().single().name)
        assertEquals("/api/v1/catalog/crops", server.takeRequest().requestUrl!!.encodedPath)
    }
}
