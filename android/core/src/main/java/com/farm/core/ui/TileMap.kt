package com.farm.core.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** One pin on a [TileMap]. */
data class MapPin(
    val key: String,
    val lat: Double,
    val lng: Double,
    val title: String,
    val snippet: String? = null,
    val color: Color = FarmColors.Primary,
)

/** Key of the "you are here" pin in [TileMap]; tapping it selects nothing. */
const val ME_PIN_KEY = "__me"

/** The person's own position as a blue pin (title "You are here", or the place they chose). */
fun mePin(lat: Double, lng: Double, title: String = "You are here") =
    MapPin(ME_PIN_KEY, lat, lng, title, color = Color(0xFF2F5FD0))

/** A blue "you are here" marker for osmdroid maps that draw their own overlays. */
fun meMarker(map: MapView, lat: Double, lng: Double, title: String = "You are here"): Marker = Marker(map).apply {
    position = GeoPoint(lat, lng)
    this.title = title
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    ContextCompat.getDrawable(map.context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.let { d ->
        d.setTint(Color(0xFF2F5FD0).toArgb())
        icon = d
    }
    setOnMarkerClickListener { m, _ -> m.showInfoWindow(); true }
}

/** osmdroid needs an identifying user agent (OSM tile policy) and a writable cache. */
fun configureOsm(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
}

/**
 * OpenStreetMap tiles (osmdroid, no API key) with [pins] as markers and, optionally, a [path]
 * drawn through points in order. On first show and whenever the set of pins changes, the map fits
 * them all. Tapping a pin calls [onSelect].
 */
@Composable
fun TileMap(
    pins: List<MapPin>,
    centerLat: Double,
    centerLng: Double,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    onSelect: (String) -> Unit = {},
    path: List<Pair<Double, Double>> = emptyList(),
    description: String = "Map",
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val select by rememberUpdatedState(onSelect)
    val mapView = remember {
        configureOsm(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(centerLat, centerLng))
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Fit the pins when the set of pins changes (not on every selection).
    val pinKeys = pins.map { it.key }
    LaunchedEffect(pinKeys, centerLat, centerLng) {
        val points = pins.map { GeoPoint(it.lat, it.lng) }
        when {
            points.size >= 2 -> mapView.post {
                runCatching { mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.3f), false) }
            }
            points.size == 1 -> mapView.controller.animateTo(points[0])
            else -> mapView.controller.animateTo(GeoPoint(centerLat, centerLng))
        }
    }

    Box(modifier.semantics { contentDescription = description }) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.removeAll { it is Marker || it is Polyline }
                if (path.size >= 2) {
                    map.overlays.add(
                        Polyline(map).apply {
                            setPoints(path.map { GeoPoint(it.first, it.second) })
                            outlinePaint.color = FarmColors.Info.toArgb()
                            outlinePaint.strokeWidth = 6f
                        },
                    )
                }
                pins.forEach { pin ->
                    val selected = pin.key == selectedKey
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(pin.lat, pin.lng)
                            title = pin.title
                            snippet = pin.snippet
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            ContextCompat.getDrawable(map.context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.let { d ->
                                d.setTint((if (selected) FarmColors.Info else pin.color).toArgb())
                                icon = d
                            }
                            setOnMarkerClickListener { m, _ ->
                                if (pin.key != ME_PIN_KEY) select(pin.key)
                                m.showInfoWindow()
                                true
                            }
                        },
                    )
                }
                map.invalidate()
            },
        )
        overlay()
        Text(
            "© OpenStreetMap contributors",
            fontSize = 10.sp,
            color = FarmColors.TextMuted,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xCCFFFFFF)).padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
