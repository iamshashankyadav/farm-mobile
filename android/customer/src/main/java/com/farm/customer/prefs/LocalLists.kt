package com.farm.customer.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A farm the customer marked as a favourite (kept on this phone only). */
@Serializable
data class FavouriteFarm(val farmId: String, val farmName: String)

// ─── Pure list rules (unit tested) ──────────────────────────────────────────

const val MAX_RECENT_SEARCHES = 8

/** Puts [query] first, drops a case-insensitive duplicate and keeps at most [max]. Blank queries are ignored. */
fun addRecentSearch(list: List<String>, query: String, max: Int = MAX_RECENT_SEARCHES): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return list
    return (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(max)
}

/** Adds the farm when it is not a favourite yet, removes it when it is. */
fun toggleFavourite(list: List<FavouriteFarm>, farm: FavouriteFarm): List<FavouriteFarm> =
    if (list.any { it.farmId == farm.farmId }) list.filterNot { it.farmId == farm.farmId } else list + farm

// ─── Stored lists ───────────────────────────────────────────────────────────

/**
 * Favourite farms and recent searches, kept in app-private SharedPreferences. They are not sent
 * to the server and are cleared when the customer signs out.
 */
object LocalLists {
    private const val KEY_FAVOURITES = "favourite_farms"
    private const val KEY_RECENT = "recent_searches"
    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null

    private val _favourites = MutableStateFlow<List<FavouriteFarm>>(emptyList())
    val favourites: StateFlow<List<FavouriteFarm>> = _favourites.asStateFlow()

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recent.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences("farm_local_lists", Context.MODE_PRIVATE)
        prefs = p
        _favourites.value = runCatching {
            json.decodeFromString(ListSerializer(FavouriteFarm.serializer()), p.getString(KEY_FAVOURITES, null) ?: "[]")
        }.getOrDefault(emptyList())
        _recent.value = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), p.getString(KEY_RECENT, null) ?: "[]")
        }.getOrDefault(emptyList())
    }

    fun isFavourite(farmId: String): Boolean = _favourites.value.any { it.farmId == farmId }

    fun toggleFavourite(farmId: String, farmName: String) {
        _favourites.value = toggleFavourite(_favourites.value, FavouriteFarm(farmId, farmName))
        prefs?.edit()?.putString(KEY_FAVOURITES, json.encodeToString(ListSerializer(FavouriteFarm.serializer()), _favourites.value))?.apply()
    }

    fun addSearch(query: String) {
        val next = addRecentSearch(_recent.value, query)
        if (next == _recent.value) return
        _recent.value = next
        prefs?.edit()?.putString(KEY_RECENT, json.encodeToString(ListSerializer(String.serializer()), next))?.apply()
    }

    fun clearSearches() {
        _recent.value = emptyList()
        prefs?.edit()?.remove(KEY_RECENT)?.apply()
    }

    fun clear() {
        _favourites.value = emptyList()
        _recent.value = emptyList()
        prefs?.edit()?.clear()?.apply()
    }
}
