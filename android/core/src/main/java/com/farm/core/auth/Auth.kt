package com.farm.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.farm.core.api.ApiConfig
import com.farm.core.api.ApiException
import com.farm.core.api.Http
import com.farm.core.api.HttpResult
import com.farm.core.api.NetworkException
import com.farm.core.api.OtpRequestResponse
import com.farm.core.api.TokenResponse
import com.farm.core.api.User
import com.farm.core.api.load
import com.farm.core.api.loadErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ─── Session storage ─────────────────────────────────────────────────────────

/** Where the session lives: [KeystoreTokenStore] in the app, [MemoryTokenStore] in tests. */
interface TokenStore {
    var accessToken: String?
    var refreshToken: String?
    var user: User?
    fun clear()
}

class MemoryTokenStore : TokenStore {
    override var accessToken: String? = null
    override var refreshToken: String? = null
    override var user: User? = null
    override fun clear() {
        accessToken = null
        refreshToken = null
        user = null
    }
}

/**
 * Tokens and the user, encrypted with AES-GCM under a key that never leaves the Android Keystore,
 * then kept in app-private preferences. A value that cannot be decrypted (for example after the
 * key was reset) reads as signed out.
 */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // Sessions of older builds were stored in plain text; drop them (one sign-in again).
        context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    } catch (_: GeneralSecurityException) {
        null // the session is then not kept across restarts, but nothing is written in clear
    }

    private fun decrypt(stored: String): String? = try {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        }
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun read(name: String): String? = prefs.getString(name, null)?.let(::decrypt)

    private fun write(name: String, value: String?) {
        val encrypted = value?.let(::encrypt)
        prefs.edit().apply { if (encrypted == null) remove(name) else putString(name, encrypted) }.apply()
    }

    override var accessToken: String?
        get() = read(ACCESS)
        set(value) = write(ACCESS, value)

    override var refreshToken: String?
        get() = read(REFRESH)
        set(value) = write(REFRESH, value)

    override var user: User?
        get() = read(USER)?.let { runCatching { Http.json.decodeFromString<User>(it) }.getOrNull() }
        set(value) = write(USER, value?.let { Http.encode(it) })

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "farm_session"
        const val LEGACY_PREFS = "farm_auth"
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val USER = "user"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "farm_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/** The app's session, backed by [store] (the Keystore once [init] ran, memory before and in tests). */
object AuthStorage {
    @Volatile
    var store: TokenStore = MemoryTokenStore()

    fun init(context: Context) {
        store = KeystoreTokenStore(context)
    }

    var accessToken: String?
        get() = store.accessToken
        set(value) { store.accessToken = value }

    var refreshToken: String?
        get() = store.refreshToken
        set(value) { store.refreshToken = value }

    var user: User?
        get() = store.user
        set(value) { store.user = value }

    fun save(data: TokenResponse) {
        accessToken = data.accessToken
        refreshToken = data.refreshToken
        user = data.user
    }

    fun clear() = store.clear()
}

// ─── Small rules (unit tested) ───────────────────────────────────────────────

/** "98765 43210", "09876543210", "919876543210" or "+91 98765-43210" -> "+919876543210". */
fun normalizePhone(value: String): String {
    val digits = value.replace(Regex("[\\s\\-()]"), "")
    val bare = digits.removePrefix("+")
    return when {
        Regex("^0?\\d{10}$").matches(digits) -> "+91" + digits.takeLast(10)
        Regex("^91\\d{10}$").matches(bare) -> "+$bare"
        else -> digits
    }
}

/** What the phone field keeps while typing or pasting: the 10 local digits ("+91 98765-43210" -> "9876543210"). */
fun localMobileDigits(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.length == 12 && digits.startsWith("91") -> digits.takeLast(10)
        digits.length == 11 && digits.startsWith("0") -> digits.takeLast(10)
        else -> digits.take(10)
    }
}

/** An Indian mobile number in any of the forms [normalizePhone] accepts. */
fun isValidMobile(value: String): Boolean = Regex("^\\+91[6-9]\\d{9}$").matches(normalizePhone(value))

/** Why this account cannot use this app, and which account it needs. */
fun wrongRoleMessage(requiredRole: String, roles: List<String>): String {
    val where = when {
        "delivery" in roles -> "the delivery app"
        "customer" in roles -> "the customer app"
        roles.any { it in setOf("farmer", "admin", "inspector") } -> "the website"
        else -> null
    }
    val needed = if (requiredRole == "delivery") "a delivery partner account" else "a $requiredRole account"
    return if (where != null) "This account is for $where. Sign in with $needed." else "This account cannot use this app. Sign in with $needed."
}

/**
 * Field messages of a 422 VALIDATION_ERROR (`details: [{field: "body.password", message}]`),
 * keyed by the request field ("password").
 */
fun fieldErrors(details: JsonElement?): Map<String, String> =
    (details as? JsonArray)?.mapNotNull { d ->
        val o = d as? JsonObject ?: return@mapNotNull null
        val field = runCatching { o["field"]?.jsonPrimitive?.content }.getOrNull() ?: return@mapNotNull null
        val message = runCatching { o["message"]?.jsonPrimitive?.content }.getOrNull() ?: return@mapNotNull null
        field.removePrefix("body.") to message
    }?.toMap() ?: emptyMap()

// ─── Identity service ────────────────────────────────────────────────────────

/** The identity service calls (POST /identity/...). */
object AuthService {
    @Serializable private data class PhoneIn(val phone: String)
    @Serializable private data class VerifyIn(val phone: String, val code: String)
    @Serializable private data class LoginIn(val phone: String, val password: String)
    @Serializable private data class RefreshIn(val refreshToken: String?)
    @Serializable
    private data class RegisterIn(
        val name: String, val phone: String, val password: String, val roles: List<String>, val email: String? = null,
    )

    private inline fun <reified T> handle(res: HttpResult): T {
        if (!res.ok) {
            val error = res.error()
            val message = error?.message ?: error?.detail ?: "Request failed with status ${res.status}"
            throw ApiException(message, error?.code ?: "REQUEST_FAILED", res.status, error?.details)
        }
        return res.decode()
    }

    private fun store(data: TokenResponse): TokenResponse {
        AuthStorage.save(data)
        Http.authToken = data.accessToken
        return data
    }

    suspend fun requestOtp(phone: String): OtpRequestResponse =
        handle(Http.call("POST", "/identity/otp/request", Http.encode(PhoneIn(normalizePhone(phone))), token = null))

    suspend fun verifyOtp(phone: String, code: String): TokenResponse =
        store(handle(Http.call("POST", "/identity/otp/verify", Http.encode(VerifyIn(normalizePhone(phone), code)), token = null)))

    suspend fun loginWithPassword(phone: String, password: String): TokenResponse =
        store(handle(Http.call("POST", "/identity/login", Http.encode(LoginIn(normalizePhone(phone), password)), token = null)))

    /** Creates the account; it returns the user, not tokens, so sign in afterwards. */
    suspend fun register(name: String, phone: String, password: String, roles: List<String>, email: String?): User =
        handle(
            Http.call(
                "POST", "/identity/register",
                Http.encode(RegisterIn(name, normalizePhone(phone), password, roles, email)), token = null,
            ),
        )

    suspend fun getMe(): User {
        val token = AuthStorage.accessToken ?: throw ApiException("No access token found", "UNAUTHORIZED", 401)
        return handle(Http.call("GET", "/identity/me", token = token))
    }

    suspend fun refresh(): TokenResponse {
        val refresh = AuthStorage.refreshToken ?: throw ApiException("No refresh token found", "UNAUTHORIZED", 401)
        return store(handle(Http.call("POST", "/identity/refresh", Http.encode(RefreshIn(refresh)), token = null)))
    }

    /** Ends the session on the server when it can be reached; the caller always clears it locally. */
    suspend fun logout() {
        val token = AuthStorage.accessToken
        val refresh = AuthStorage.refreshToken
        if (token != null || refresh != null) {
            load { Http.call("POST", "/identity/logout", Http.encode(RefreshIn(refresh)), token = token) }
        }
    }
}

// ─── App-wide auth state ─────────────────────────────────────────────────────

data class AuthState(
    val user: User? = null,
    val accessToken: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Per-field messages of the last refused sign-in or sign-up (e.g. "password"). */
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isAuthenticated: Boolean get() = user != null && accessToken != null
    fun hasRole(role: String): Boolean = user?.roles?.contains(role) == true
}

/** App-wide auth state (the AuthProvider / useAuth of the React app). */
object AuthManager {
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private var started = false
    private val refreshLock = Mutex()
    private val signOutListeners = CopyOnWriteArrayList<() -> Unit>()

    /** Lets every API call refresh an expired access token once (see [Http.onUnauthorized]). */
    fun install() {
        Http.onUnauthorized = ::refreshAfterUnauthorized
    }

    /** Runs on every sign-out (button, refresh refused, wrong role): apps clear their cached data. */
    fun onSignOut(listener: () -> Unit) {
        signOutListeners += listener
    }

    /** Restore the stored session, then verify it against /me (refreshing the token if needed). */
    fun start() {
        if (started) return
        started = true
        install()
        scope.launch {
            try {
                val token = AuthStorage.accessToken
                val user = AuthStorage.user
                if (token != null && user != null) {
                    Http.authToken = token
                    _state.update { it.copy(user = user, accessToken = token) }
                    verifyStoredSession(token)
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun verifyStoredSession(token: String) {
        load { AuthService.getMe() }
            .onSuccess { fresh ->
                AuthStorage.user = fresh
                _state.update { it.copy(user = fresh) }
            }
            .onFailure { e ->
                // An expired token refreshes; a refused refresh signs out; offline keeps the session.
                if (e is ApiException && e.status == 401) refreshAfterUnauthorized(token)
            }
    }

    /**
     * A call made with [failedToken] got 401. Refresh once (one refresh at a time; a request that
     * failed with an older token gets the newer one) and return the new token. When the refresh
     * is refused the session ends; when the server cannot be reached the session is kept.
     */
    suspend fun refreshAfterUnauthorized(failedToken: String): String? = refreshLock.withLock {
        val current = AuthStorage.accessToken
        if (current != null && current != failedToken) return@withLock current
        if (current == null) return@withLock null
        load { AuthService.refresh() }.fold(
            onSuccess = { data ->
                _state.update { it.copy(user = data.user, accessToken = data.accessToken) }
                data.accessToken
            },
            onFailure = { e ->
                if (e !is NetworkException) signOutLocally()
                null
            },
        )
    }

    /** Clears the session and every app's cached data (no server call). */
    fun signOutLocally() {
        AuthStorage.clear()
        Http.authToken = null
        _state.update { it.copy(user = null, accessToken = null, error = null, fieldErrors = emptyMap()) }
        signOutListeners.forEach { it() }
    }

    fun clearError() = _state.update { it.copy(error = null, fieldErrors = emptyMap()) }

    private fun report(e: Throwable) {
        val fields = (e as? ApiException)?.let { fieldErrors(it.details) }.orEmpty()
        val message = when {
            fields.isNotEmpty() -> "Please correct the highlighted fields."
            e is NetworkException -> loadErrorMessage(e, ApiConfig.baseUrl)
            else -> loadErrorMessage(e)
        }
        _state.update { it.copy(error = message, fieldErrors = fields) }
    }

    /** Runs a sign-in step; a failure is reported in [state] and rethrown to the caller. */
    private suspend inline fun <T> guarded(block: () -> T): T {
        clearError()
        try {
            return block()
        } catch (e: ApiException) {
            report(e); throw e
        } catch (e: NetworkException) {
            report(e); throw e
        } catch (e: SerializationException) {
            report(e); throw e
        }
    }

    private suspend fun checkRole(user: User, requiredRole: String?) {
        if (requiredRole == null || user.roles.contains(requiredRole)) return
        AuthService.logout()
        signOutLocally()
        throw ApiException(wrongRoleMessage(requiredRole, user.roles), "FORBIDDEN_ROLE", 403)
    }

    suspend fun requestOtp(phone: String): OtpRequestResponse = guarded { AuthService.requestOtp(phone) }

    suspend fun verifyOtp(phone: String, code: String, requiredRole: String?): User = guarded {
        val data = AuthService.verifyOtp(phone, code)
        checkRole(data.user, requiredRole)
        _state.update { it.copy(user = data.user, accessToken = data.accessToken) }
        data.user
    }

    suspend fun loginWithPassword(phone: String, password: String, requiredRole: String?): User = guarded {
        val data = AuthService.loginWithPassword(phone, password)
        checkRole(data.user, requiredRole)
        _state.update { it.copy(user = data.user, accessToken = data.accessToken) }
        data.user
    }

    /** Registers with [requiredRole], then signs in with the same phone and password. */
    suspend fun register(name: String, phone: String, password: String, email: String?, requiredRole: String): User {
        guarded { AuthService.register(name, phone, password, listOf(requiredRole), email) }
        return loginWithPassword(phone, password, requiredRole)
    }

    fun logout() {
        scope.launch {
            AuthService.logout()
            signOutLocally()
        }
    }
}

/** Call once from the app's onCreate. */
object FarmCore {
    fun init(context: Context) {
        ApiConfig.init(context)
        AuthStorage.init(context)
        AuthManager.start()
    }
}
