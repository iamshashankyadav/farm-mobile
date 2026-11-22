package com.farm.delivery.route

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.farm.core.api.LogisticsApi
import com.farm.delivery.MainActivity
import com.farm.delivery.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val PING_INTERVAL_MS = 30_000L
private const val CHANNEL_ID = "route_tracking"
private const val NOTIFICATION_ID = 41
private const val EXTRA_POOL_ID = "poolId"

/** What the route screen shows about location sharing. */
data class TrackingState(
    val isTracking: Boolean = false,
    val pingCount: Int = 0,
    val lastPingTime: String? = null,
    val error: String? = null,
)

/** Shared between the foreground service (writes) and the route screen (reads). */
object RouteTracker {
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    internal fun update(f: (TrackingState) -> TrackingState) = _state.update(f)

    fun start(context: Context, poolId: String?) {
        val intent = Intent(context, LocationPingService::class.java).putExtra(EXTRA_POOL_ID, poolId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, LocationPingService::class.java))
    }
}

fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Pings the partner's position to logistics every 30 s (POST /logistics/location) from a
 * foreground service with a visible notification, so pings continue with the screen off or the
 * app in the background while the route has stops left.
 */
class LocationPingService : Service() {
    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val poolId = intent?.getStringExtra(EXTRA_POOL_ID)
        if (!hasLocationPermission(this)) {
            RouteTracker.update { it.copy(isTracking = false, error = "Location permission is off. Allow it to share your position.") }
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)

        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { s ->
            s.launch {
                RouteTracker.update { it.copy(isTracking = true, error = null) }
                while (isActive) {
                    ping(poolId)
                    delay(PING_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        RouteTracker.update { it.copy(isTracking = false) }
        super.onDestroy()
    }

    @SuppressLint("MissingPermission") // checked in onStartCommand
    private suspend fun ping(poolId: String?) {
        val client = LocationServices.getFusedLocationProviderClient(this)
        val loc = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                ?: client.lastLocation.await()
        }.getOrNull()
        if (loc == null) {
            RouteTracker.update { it.copy(error = "Location unavailable. Is GPS turned on?") }
            return
        }
        val sent = LogisticsApi.sendLocationPing(
            lat = loc.latitude,
            lng = loc.longitude,
            speed = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            heading = if (loc.hasBearing()) loc.bearing.toDouble() else null,
            accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
            recordedAt = Instant.ofEpochMilli(loc.time).toString(),
            poolId = poolId,
        )
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
        RouteTracker.update {
            if (sent) it.copy(pingCount = it.pingCount + 1, lastPingTime = now, error = null)
            else it.copy(error = "The last position could not be sent (no connection or refused). Retrying in 30 s.")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Route tracking", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while your position is shared with dispatch and customers"
                },
            )
        }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_route)
        .setContentTitle("Sharing your location")
        .setContentText("Customers can follow their delivery. Stops when the route is done.")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()
}

/**
 * Asks for location (and, on Android 13+, notification) permission when [active] turns true, then
 * starts the ping service; stops it when [active] turns false. The service keeps running if the
 * partner leaves this screen with stops left, and is stopped when the route is finished.
 */
@Composable
fun LocationSharing(active: Boolean, poolId: String?) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) RouteTracker.update { it.copy(isTracking = false, error = "Location permission denied. Allow location so customers can follow the delivery.") }
    }

    LaunchedEffect(active, granted, poolId) {
        when {
            !active -> RouteTracker.stop(context)
            !granted && !asked -> {
                asked = true
                val perms = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                launcher.launch(perms.toTypedArray())
            }
            granted -> RouteTracker.start(context, poolId)
        }
    }
}
