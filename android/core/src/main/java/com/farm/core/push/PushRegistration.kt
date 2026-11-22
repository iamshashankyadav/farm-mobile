package com.farm.core.push

import com.farm.core.api.Http
import com.farm.core.api.load
import com.farm.core.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Keeps this device's push token registered with the notification service while someone is
 * signed in: POST /notification/notifications/devices on sign-in (or when the token changes) and
 * DELETE /notification/notifications/devices/{token} on sign-out. The token comes from FCM in the
 * app module; without Firebase configured nothing is registered.
 */
object PushRegistration {
    @Serializable
    private data class DeviceTokenIn(val token: String, val platform: String = "android")

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    @Volatile private var deviceToken: String? = null
    @Volatile private var registeredWith: String? = null // the access token the device was registered under
    private var started = false

    /** Call once, from Application.onCreate, after the auth manager is set up. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            AuthManager.state.map { it.accessToken.takeIf { _ -> it.isAuthenticated } }.distinctUntilChanged().collect { access ->
                val token = deviceToken
                if (access != null) {
                    if (token != null) register(token, access)
                } else {
                    val previous = registeredWith
                    if (token != null && previous != null) unregister(token, previous)
                }
            }
        }
    }

    /** A new (or the current) FCM token. Registers it straight away when someone is signed in. */
    fun onToken(token: String) {
        deviceToken = token
        val access = AuthManager.state.value.takeIf { it.isAuthenticated }?.accessToken ?: return
        scope.launch { register(token, access) }
    }

    private suspend fun register(token: String, access: String) {
        load {
            Http.call("POST", "/notification/notifications/devices", Http.encode(DeviceTokenIn(token)), token = access)
        }.onSuccess { if (it.ok) registeredWith = access }
    }

    private suspend fun unregister(token: String, access: String) {
        load { Http.call("DELETE", "/notification/notifications/devices/${Http.enc(token)}", token = access) }
        registeredWith = null
    }
}
