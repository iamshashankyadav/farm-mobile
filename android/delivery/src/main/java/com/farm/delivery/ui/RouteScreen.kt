package com.farm.delivery.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.Pool
import com.farm.core.auth.AuthManager
import com.farm.core.ui.Banner
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.ErrorState
import com.farm.core.ui.FarmColors
import com.farm.core.ui.LoadingSkeleton
import com.farm.core.ui.Pill
import com.farm.core.ui.ScreenHeader
import com.farm.core.ui.TextVariant
import com.farm.delivery.route.RouteStop
import com.farm.delivery.route.StopStatus
import com.farm.delivery.route.StopType
import com.farm.delivery.route.localIsoDate
import com.farm.delivery.route.mapsUrl
import com.farm.delivery.route.markStopDone
import com.farm.delivery.route.LocationSharing
import com.farm.delivery.route.RouteTracker
import com.farm.delivery.route.dialUri
import com.farm.delivery.route.open
import com.farm.delivery.route.routeLengthM
import com.farm.delivery.route.withStop
import com.farm.delivery.route.routeStops
import com.farm.delivery.route.routeSummary
import com.farm.core.api.LogisticsApi
import com.farm.core.api.load
import com.farm.core.api.loadErrorMessage
import com.farm.core.ui.MapPin
import com.farm.core.ui.TileMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun formatDay(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale("en", "IN")))
}.getOrDefault(iso)

/**
 * Today's route: the signed-in partner's pool from GET /logistics/my-route, stops in driving order.
 * "Navigate" opens Google Maps; pickup and delivery go through their sheets to the logistics API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(dateParam: String?, onBack: (() -> Unit)?, onHome: () -> Unit) {
    val auth by AuthManager.state.collectAsState()
    val isPartner = auth.isAuthenticated && auth.hasRole("delivery")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = localIsoDate()
    val poolDate = if (dateParam != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateParam)) dateParam else today

    var pool by remember { mutableStateOf<Pool?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var deliveryStop by remember { mutableStateOf<RouteStop?>(null) }
    var pickupStop by remember { mutableStateOf<RouteStop?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var skipStop by remember { mutableStateOf<RouteStop?>(null) }
    var busyStopId by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var selectedStopId by remember { mutableStateOf<String?>(null) }

    suspend fun loadRoute() {
        load { LogisticsApi.getMyRoute(poolDate) }
            .onSuccess { pool = it; error = null }
            .onFailure { error = it }
        loaded = true
        refreshing = false
    }

    LaunchedEffect(isPartner, reloadKey) { if (isPartner) loadRoute() }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3500)
            toast = null
        }
    }

    val stops = routeStops(pool)
    val summary = routeSummary(stops)
    var available by remember { mutableStateOf(Availability.get(context)) }
    // Share the position while working a route, and also while waiting for one when the partner
    // is available, so dispatch can offer routes near them.
    LocationSharing(active = isPartner && (summary.next != null || available), poolId = pool?.id)
    val pings by RouteTracker.state.collectAsState()

    if (auth.isLoading) return CenteredLoading()
    if (!isPartner) {
        Column(
            Modifier.fillMaxSize().background(FarmColors.Background).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FText("Access denied. Please log in as a delivery partner.", textAlign = TextAlign.Center)
            FarmButton("Go Back", onHome, Modifier.padding(top = 16.dp), variant = ButtonVariant.Secondary)
        }
        return
    }

    val dayLabel = if (poolDate == today) "today" else formatDay(poolDate)
    // With no pool on the asked day, logistics answers with the partner's active pool from another day.
    val shownDate = pool?.poolDate?.ifEmpty { null } ?: poolDate

    fun onStopDone(stopId: String) {
        pool = markStopDone(pool, stopId)
        toast = "Stop completed."
        scope.launch { loadRoute() }
    }

    /** POST arrive / skip for one stop; the server's answer replaces the stop, a refusal is shown. */
    fun stopAction(stop: RouteStop, done: String, call: suspend () -> com.farm.core.api.Stop) {
        busyStopId = stop.id
        actionError = null
        scope.launch {
            load { call() }
                .onSuccess { pool = withStop(pool, it); toast = done; loadRoute() }
                .onFailure { actionError = loadErrorMessage(it) }
            busyStopId = null
        }
    }

    fun callCustomer(stop: RouteStop) {
        val uri = dialUri(stop.phone) ?: return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No phone app on this device. Number: ${stop.phone}", Toast.LENGTH_LONG).show()
        }
    }

    fun navigate(stop: RouteStop) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl(stop.lat, stop.lng, stop.label))))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Could not open Google Maps on this device.", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader(if (shownDate == today) "Today's Route" else "Route · ${formatDay(shownDate)}", onBack)
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; reloadKey++ },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (loaded && summary.next == null) {
                    item {
                        AvailabilityCard(available) { on ->
                            available = on
                            Availability.set(context, on)
                        }
                    }
                }
                if (available) {
                    item {
                        RouteOffers(reloadKey) { accepted ->
                            pool = accepted
                            toast = "Route accepted. Your stops are below."
                            reloadKey++
                        }
                    }
                }
                when {
                    !loaded -> item { LoadingSkeleton() }
                    error != null && pool == null -> item {
                        ErrorState(error, { reloadKey++ }, title = "Could not load your route")
                    }
                    stops.isEmpty() -> item {
                        FarmCard(Modifier.fillMaxWidth(), background = FarmColors.PrimaryLight) {
                            FText("No route assigned for $dayLabel.", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary)
                            FText(
                                "Pull down to check again once dispatch has assigned you a pool.",
                                variant = TextVariant.Caption, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    else -> {
                        item {
                            RouteMap(stops, selectedStopId ?: summary.next?.id) { selectedStopId = it }
                        }
                        item { RouteProgress(stops.size, summary.done, routeLengthM(stops)) }
                        item { TrackingCard(pings.isTracking, pings.pingCount, pings.lastPingTime, pings.error) }
                        toast?.let { item { Banner("✓ $it", FarmColors.PrimaryLight, FarmColors.PrimaryDark, borderColor = FarmColors.Border) } }
                        actionError?.let { item { Banner("⚠️ $it", FarmColors.ErrorBg, FarmColors.Error) } }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SummaryChip(summary.pickups, "Pickups", Modifier.weight(1f))
                                SummaryChip(summary.drops, "Drops", Modifier.weight(1f))
                                SummaryChip(summary.done, "Done", Modifier.weight(1f), FarmColors.Primary)
                            }
                        }
                        if (summary.finished) item {
                            FarmCard(Modifier.fillMaxWidth(), background = FarmColors.PrimaryLight) {
                                FText("🎉 Route complete! Great work today.", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary)
                            }
                        }
                        items(stops, key = { it.id }) { stop ->
                            StopCard(
                                stop = stop,
                                isNext = summary.next?.id == stop.id,
                                busy = busyStopId == stop.id,
                                onNavigate = ::navigate,
                                onArrive = { stopAction(it, "Arrival recorded.") { LogisticsApi.arrive(it.id) } },
                                onAction = { if (it.type == StopType.PICKUP) pickupStop = it else deliveryStop = it },
                                onSkip = { skipStop = it },
                                onCall = ::callCustomer,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    deliveryStop?.let { stop ->
        ProofOfDeliverySheet(stop = stop, onClose = { deliveryStop = null }, onSuccess = ::onStopDone)
    }
    pickupStop?.let { stop ->
        PickupSheet(stop = stop, onClose = { pickupStop = null }, onSuccess = ::onStopDone)
    }
    skipStop?.let { stop ->
        SkipDialog(stop, onDismiss = { skipStop = null }) { reason ->
            skipStop = null
            stopAction(stop, "Stop skipped.") { LogisticsApi.skip(stop.id, reason) }
        }
    }
}

private val SKIP_REASONS = listOf("Customer not available", "Address not found", "Farm closed", "Vehicle problem")

@Composable
private fun SkipDialog(stop: RouteStop, onDismiss: () -> Unit, onSkip: (String) -> Unit) {
    var reason by remember { mutableStateOf(SKIP_REASONS[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip stop ${stop.seq}?") },
        text = {
            Column {
                FText("Dispatch is told the reason. A skipped stop cannot be completed later today.", variant = TextVariant.Caption)
                SKIP_REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { reason = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSkip(reason) }) { Text("Skip stop", color = FarmColors.Error) } },
        dismissButton = { TextButton(onDismiss) { Text("Keep") } },
    )
}

/** The stops on OpenStreetMap tiles in driving order, pickups green and drops blue. */
@Composable
private fun RouteMap(stops: List<RouteStop>, selectedId: String?, onSelect: (String) -> Unit) {
    val first = stops.first()
    TileMap(
        pins = stops.map {
            MapPin(
                it.id, it.lat, it.lng, "${it.seq}. ${it.label}", it.address,
                color = when {
                    !it.status.open -> FarmColors.TextMuted
                    it.type == StopType.PICKUP -> FarmColors.Primary
                    else -> FarmColors.Info
                },
            )
        },
        centerLat = first.lat,
        centerLng = first.lng,
        selectedKey = selectedId,
        onSelect = onSelect,
        path = stops.map { it.lat to it.lng },
        description = "Route map with ${stops.size} stops",
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun SummaryChip(value: Int, label: String, modifier: Modifier, color: Color = FarmColors.TextDark) {
    FarmCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            FText(label, variant = TextVariant.Caption)
        }
    }
}

@Composable
private fun RouteProgress(total: Int, done: Int, lengthM: Double) {
    val pct = if (total > 0) Math.round(done * 100f / total) else 0
    Column {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(FarmColors.Border)) {
            Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).background(FarmColors.Primary))
        }
        FText(
            "$done / $total stops completed ($pct%) · about ${formatKm(lengthM)} in total",
            variant = TextVariant.Caption, color = FarmColors.TextLabel,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

private fun formatKm(m: Double): String = if (m < 1000) "${m.toInt()} m" else String.format(Locale.US, "%.1f km", m / 1000)

@Composable
private fun TrackingCard(isTracking: Boolean, pingCount: Int, lastPing: String?, error: String?) {
    FarmCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp), border = BorderStroke(1.dp, FarmColors.Border)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (isTracking) FarmColors.Primary else FarmColors.TextMuted))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isTracking) "GPS Tracking Active" else "GPS Tracking Paused",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark, modifier = Modifier.weight(1f),
            )
            Pill("30s interval", FarmColors.PrimaryLight, FarmColors.Primary)
        }
        FText(
            if (isTracking) "Reporting coordinates to logistics dispatch · $pingCount pings sent${lastPing?.let { " (last: $it)" } ?: ""}"
            else "Tracking resumes automatically when pending stops exist.",
            variant = TextVariant.Caption, color = FarmColors.TextMuted, modifier = Modifier.padding(top = 4.dp),
        )
        if (error != null) {
            FText("⚠️ $error", variant = TextVariant.Caption, color = FarmColors.Error, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StopCard(
    stop: RouteStop,
    isNext: Boolean,
    busy: Boolean,
    onNavigate: (RouteStop) -> Unit,
    onArrive: (RouteStop) -> Unit,
    onAction: (RouteStop) -> Unit,
    onSkip: (RouteStop) -> Unit,
    onCall: (RouteStop) -> Unit,
) {
    val isDone = !stop.status.open
    val color = if (stop.type == StopType.PICKUP) FarmColors.PrimaryDark else FarmColors.Info
    val bg = if (stop.type == StopType.PICKUP) FarmColors.PrimaryLight else FarmColors.InfoBg

    FarmCard(Modifier.fillMaxWidth().alpha(if (isDone) 0.6f else 1f)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 2.dp).size(30.dp).clip(CircleShape).background(if (isDone) FarmColors.InputBorder else color),
                contentAlignment = Alignment.Center,
            ) { Text("${stop.seq}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (stop.type == StopType.PICKUP) "📦 PICKUP" else "🚪 DROP", bg, color, fontSize = 10, border = color)
                    if (isNext) Pill("NEXT", FarmColors.Warning, Color.White, fontSize = 10)
                    Spacer(Modifier.weight(1f))
                    if (stop.status == StopStatus.ARRIVED) Pill("ARRIVED", FarmColors.InfoBg, FarmColors.Info, fontSize = 10)
                    Text(
                        when (stop.status) { StopStatus.DONE -> "Done"; StopStatus.SKIPPED -> "Skipped"; StopStatus.ARRIVED -> "At stop"; else -> "Pending" },
                        fontSize = 12.sp, color = FarmColors.TextLabel,
                    )
                }
                Text(
                    stop.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isDone) FarmColors.TextMuted else FarmColors.TextDark,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        FText("📍 ${stop.address}", variant = TextVariant.Caption, color = FarmColors.TextLabel, modifier = Modifier.padding(vertical = 8.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(FarmColors.Background)
                .border(1.dp, FarmColors.Border, RoundedCornerShape(8.dp)).padding(10.dp),
        ) {
            FText("Items", variant = TextVariant.Label)
            stop.items.forEach { FText("• $it", variant = TextVariant.Caption, color = FarmColors.TextBody, modifier = Modifier.padding(top = 2.dp)) }
        }

        stop.notes?.let {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(FarmColors.WarningBg)
                    .border(1.dp, FarmColors.Border, RoundedCornerShape(8.dp)).padding(10.dp),
            ) {
                FText("Notes", variant = TextVariant.Label, color = FarmColors.Warning)
                FText(it, variant = TextVariant.Caption, color = FarmColors.TextDark, modifier = Modifier.padding(top = 2.dp))
            }
        }

        if (!isDone) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmButton("Navigate", { onNavigate(stop) }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                if (stop.status == StopStatus.PENDING) {
                    FarmButton(if (busy) "Saving…" else "I've arrived", { onArrive(stop) }, Modifier.weight(1f), loading = busy, enabled = !busy)
                } else {
                    FarmButton(
                        if (stop.type == StopType.PICKUP) "Confirm pickup" else "Deliver",
                        { onAction(stop) }, Modifier.weight(1f), enabled = !busy,
                    )
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stop.phone != null) {
                    FarmButton("Call ${if (stop.type == StopType.PICKUP) "farm" else "customer"}", { onCall(stop) }, Modifier.weight(1f), variant = ButtonVariant.Ghost)
                }
                FarmButton("Skip stop", { onSkip(stop) }, Modifier.weight(1f), variant = ButtonVariant.Ghost, enabled = !busy)
            }
        }
    }
}
