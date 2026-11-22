package com.farm.core.api

import kotlinx.serialization.SerializationException

/**
 * Runs one API call and keeps the failures a screen can show: no connection, an HTTP error, or a
 * reply the app cannot read. Anything else (and coroutine cancellation, so leaving a screen stops
 * its polling) is not caught here.
 */
inline fun <T> load(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: NetworkException) {
    Result.failure(e)
} catch (e: ApiException) {
    Result.failure(e)
} catch (e: SerializationException) {
    Result.failure(e)
}

/** True when the request never got an HTTP answer (server down, wrong address, no network). */
fun isOffline(e: Throwable?): Boolean = e is NetworkException

/** What to tell the person when [load] failed: the server's own message when there is one. */
fun loadErrorMessage(e: Throwable?, baseUrl: String = ApiConfig.baseUrl): String = when (e) {
    is NetworkException -> "Cannot reach the server at $baseUrl. Check your connection and try again."
    is SerializationException -> "The server sent a reply the app could not read."
    else -> e?.message ?: "Something went wrong. Try again."
}

/** An [ApiException] carrying the platform error envelope's message and code, or [fallback]. */
fun apiError(res: HttpResult, fallback: String): ApiException {
    val error = res.error()
    return ApiException(
        error?.message ?: error?.detail ?: "$fallback (${res.status})", error?.code ?: "REQUEST_FAILED", res.status, error?.details,
    )
}
