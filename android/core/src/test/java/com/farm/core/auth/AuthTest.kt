package com.farm.core.auth

import com.farm.core.api.ApiException
import com.farm.core.api.Http
import com.farm.core.api.User
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Sign-in and the silent token refresh against a MockWebServer (#37). */
class AuthTest {
    private lateinit var server: MockWebServer
    private val store = MemoryTokenStore()

    private fun tokens(access: String, refresh: String, roles: String = "\"customer\"") =
        """{"accessToken":"$access","refreshToken":"$refresh","tokenType":"bearer","expiresIn":900,
        "user":{"id":"u1","name":"Anita","phone":"+919200000001","roles":[$roles]}}"""

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        Http.baseUrlProvider = { server.url("/api/v1").toString() }
        Http.authToken = null
        AuthStorage.store = store
        store.clear()
        AuthManager.install()
        AuthManager.signOutLocally()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Http.onUnauthorized = null
    }

    @Test
    fun passwordLoginStoresTheSessionAndNormalisesThePhone() = runBlocking {
        server.enqueue(json(200, tokens("a1", "r1")))
        val user = AuthManager.loginWithPassword("92000 00001", "Farm@12345", "customer")
        assertEquals("u1", user.id)
        assertEquals("a1", store.accessToken)
        assertEquals("r1", store.refreshToken)
        assertEquals("a1", Http.authToken)
        assertTrue(AuthManager.state.value.isAuthenticated)
        val sent = server.takeRequest()
        assertEquals("/api/v1/identity/login", sent.path)
        assertTrue(sent.body.readUtf8().contains("\"phone\":\"+919200000001\""))
    }

    @Test
    fun anExpiredTokenIsRefreshedOnceAndTheCallRetried() = runBlocking {
        store.accessToken = "old"; store.refreshToken = "r1"; Http.authToken = "old"
        server.enqueue(json(401, """{"error":{"code":"UNAUTHORIZED","message":"Token expired"}}"""))
        server.enqueue(json(200, tokens("new", "r2")))
        server.enqueue(json(200, """{"items":[],"nextCursor":null}"""))

        val res = Http.get("/order/orders")

        assertEquals(200, res.status)
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
        val refresh = server.takeRequest()
        assertEquals("/api/v1/identity/refresh", refresh.path)
        assertTrue(refresh.body.readUtf8().contains("\"refreshToken\":\"r1\""))
        assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
        assertEquals("new", store.accessToken)
        assertEquals("r2", store.refreshToken)
    }

    @Test
    fun aRefusedRefreshSignsOutAndClearsAppData() = runBlocking {
        var cleared = false
        AuthManager.onSignOut { cleared = true }
        store.accessToken = "old"; store.refreshToken = "revoked"; Http.authToken = "old"
        server.enqueue(json(401, """{"error":{"code":"UNAUTHORIZED","message":"Token expired"}}"""))
        server.enqueue(json(401, """{"error":{"code":"INVALID_REFRESH_TOKEN","message":"Refresh token revoked"}}"""))

        val res = Http.get("/order/orders")

        assertEquals(401, res.status)
        assertEquals(2, server.requestCount) // no retry after the refused refresh
        assertNull(store.accessToken)
        assertNull(Http.authToken)
        assertFalse(AuthManager.state.value.isAuthenticated)
        assertTrue(cleared)
    }

    @Test
    fun noConnectionDuringRefreshKeepsTheSession() = runBlocking {
        store.accessToken = "old"; store.refreshToken = "r1"; Http.authToken = "old"
        AuthStorage.user = User("u1", "Anita", roles = listOf("customer"))
        server.enqueue(json(401, """{"error":{"code":"UNAUTHORIZED","message":"Token expired"}}"""))
        val port = server.port
        server.shutdown() // the refresh will not reach the server
        Http.baseUrlProvider = { "http://127.0.0.1:$port/api/v1" }
        val fresh = AuthManager.refreshAfterUnauthorized("old")
        assertNull(fresh)
        assertEquals("old", store.accessToken)
        assertEquals("r1", store.refreshToken)
    }

    @Test
    fun validationErrorsAreShownPerField() = runBlocking {
        server.enqueue(
            json(
                422,
                """{"error":{"code":"VALIDATION_ERROR","message":"Invalid request","correlationId":"c1",
                "details":[{"field":"body.password","message":"String should have at least 8 characters"},
                {"field":"body.phone","message":"Value error, Enter a valid mobile number, e.g. +919876543210"}]}}""",
            ),
        )
        try {
            AuthManager.register("Anita", "9200000001", "short", null, "customer")
            fail("register should fail")
        } catch (e: ApiException) {
            assertEquals("VALIDATION_ERROR", e.code)
        }
        val state = AuthManager.state.value
        assertEquals("String should have at least 8 characters", state.fieldErrors["password"])
        assertTrue(state.fieldErrors["phone"]!!.contains("valid mobile number"))
        assertEquals("Please correct the highlighted fields.", state.error)
        assertEquals(1, server.requestCount) // no sign-in attempted after a refused sign-up
    }

    @Test
    fun theWrongRoleGetsAClearMessageAndNoSession() = runBlocking {
        server.enqueue(json(200, tokens("a1", "r1", roles = "\"delivery\"")))
        server.enqueue(MockResponse().setResponseCode(204)) // logout
        try {
            AuthManager.loginWithPassword("9400000001", "Farm@12345", "customer")
            fail("a delivery account must not enter the customer app")
        } catch (e: ApiException) {
            assertEquals("This account is for the delivery app. Sign in with a customer account.", e.message)
        }
        assertNull(store.accessToken)
        assertFalse(AuthManager.state.value.isAuthenticated)
        assertEquals("This account is for the delivery app. Sign in with a customer account.", AuthManager.state.value.error)
    }

    @Test
    fun phonesAreNormalisedToPlus91() {
        assertEquals("+919876543210", normalizePhone("98765 43210"))
        assertEquals("+919876543210", normalizePhone("09876543210"))
        assertEquals("+919876543210", normalizePhone("+91 98765-43210"))
        assertEquals("+919876543210", normalizePhone("919876543210"))
        assertTrue(isValidMobile("9876543210"))
        assertFalse(isValidMobile("1234567890"))
        assertEquals("9876543210", localMobileDigits("+91 98765-43210"))
        assertEquals("9876543210", localMobileDigits("09876543210"))
    }

    @Test
    fun wrongRoleMessages() {
        assertEquals("This account is for the website. Sign in with a delivery partner account.", wrongRoleMessage("delivery", listOf("farmer")))
        assertEquals("This account is for the customer app. Sign in with a delivery partner account.", wrongRoleMessage("delivery", listOf("customer")))
        assertEquals("This account cannot use this app. Sign in with a customer account.", wrongRoleMessage("customer", emptyList()))
    }
}
