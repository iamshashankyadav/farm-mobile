package com.farm.delivery.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.farm.core.api.ApiException
import com.farm.core.api.LogisticsApi
import com.farm.core.api.Pool
import com.farm.core.api.RouteOffer
import com.farm.core.api.load
import com.farm.core.api.loadErrorMessage
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.StatusChip
import com.farm.core.ui.TextVariant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** How often open offers are re-read while the route screen is shown (pushes also refresh it). */
const val OFFER_POLL_MS = 10_000L

/** "4 stops (2 pickups, 2 drops) · 12.5 km route". */
fun offerSummary(o: RouteOffer): String {
    val km = o.totalKm?.let { String.format(Locale.US, " · %.1f km route", it) } ?: ""
    return "${o.stopCount} stops (${o.pickupCount} ${if (o.pickupCount == 1) "pickup" else "pickups"}, " +
        "${o.dropCount} ${if (o.dropCount == 1) "drop" else "drops"})$km"
}

/** "First pickup 1.2 km from you: Patan farm". */
fun offerWhere(o: RouteOffer): String {
    val away = o.distanceKm?.let { String.format(Locale.US, "%.1f km from you", it) }
    val place = o.firstPickup?.address?.takeIf { it.isNotBlank() }
    return listOfNotNull("First pickup" + (away?.let { " $it" } ?: ""), place).joinToString(": ")
}

/** True when the accept lost the race (another partner was first). */
fun isTaken(e: Throwable?): Boolean = (e as? ApiException)?.let { it.code == "POOL_TAKEN" || it.status == 409 } == true

/** Whether the partner wants new route offers (kept on this phone). */
object Availability {
    private const val PREFS = "delivery_prefs"
    private const val KEY = "available_for_routes"
    fun get(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun set(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
}

/**
 * "Available for new routes": while on, the phone shares its position (so dispatch can offer
 * nearby routes) even before a route is assigned.
 */
@Composable
fun AvailabilityCard(available: Boolean, onChange: (Boolean) -> Unit) {
    FarmCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText("Available for new routes", variant = TextVariant.H2)
                FText(
                    if (available) "Dispatch can offer you routes near you. Your position is shared while this is on."
                    else "You will not be offered new routes.",
                    variant = TextVariant.Caption,
                )
            }
            Switch(
                checked = available,
                onCheckedChange = onChange,
                modifier = Modifier.semantics { contentDescription = "Available for new routes" },
                colors = SwitchDefaults.colors(checkedTrackColor = FarmColors.Primary),
            )
        }
    }
}

/**
 * Open route offers, re-read every [OFFER_POLL_MS] and whenever [refreshKey] changes. Accept takes
 * the route (first partner wins); if someone was faster the card disappears with a note.
 */
@Composable
fun RouteOffers(refreshKey: Int, onAccepted: (Pool) -> Unit) {
    val scope = rememberCoroutineScope()
    val offers = remember { mutableStateListOf<RouteOffer>() }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val accepted by rememberUpdatedState(onAccepted)

    suspend fun reload() {
        load { LogisticsApi.myOffers() }
            .onSuccess { offers.clear(); offers.addAll(it); error = null }
            .onFailure { error = loadErrorMessage(it) }
    }
    LaunchedEffect(refreshKey) {
        while (true) {
            reload()
            delay(OFFER_POLL_MS)
        }
    }

    if (offers.isEmpty() && note == null && error == null) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        note?.let { FText(it, variant = TextVariant.Caption, color = FarmColors.Warning) }
        error?.let { FText(it, variant = TextVariant.Caption, color = FarmColors.Error) }
        offers.forEach { o ->
            FarmCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText("New route offer", variant = TextVariant.H2, modifier = Modifier.weight(1f))
                    StatusChip(o.poolDate.ifBlank { "Today" }, FarmColors.Warning)
                }
                FText(offerSummary(o), modifier = Modifier.padding(top = 4.dp))
                FText(offerWhere(o), variant = TextVariant.Caption)
                FText("The first partner to accept gets it.", variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FarmButton(
                        "Decline",
                        {
                            busy = o.poolId
                            scope.launch {
                                load { LogisticsApi.declineOffer(o.poolId) }
                                offers.remove(o)
                                busy = null
                            }
                        },
                        Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = busy == null,
                    )
                    FarmButton(
                        if (busy == o.poolId) "Accepting…" else "Accept route",
                        {
                            busy = o.poolId
                            note = null
                            scope.launch {
                                load { LogisticsApi.acceptOffer(o.poolId) }
                                    .onSuccess { pool -> offers.clear(); accepted(pool) }
                                    .onFailure { e ->
                                        if (isTaken(e)) {
                                            offers.remove(o)
                                            note = "Another delivery partner accepted that route first."
                                        } else {
                                            note = loadErrorMessage(e)
                                        }
                                    }
                                busy = null
                            }
                        },
                        Modifier.weight(1f), loading = busy == o.poolId, enabled = busy == null,
                    )
                }
            }
        }
    }
}
