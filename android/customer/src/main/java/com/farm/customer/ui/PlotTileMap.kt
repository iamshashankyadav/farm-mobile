package com.farm.customer.ui

import com.farm.core.ui.meMarker

import com.farm.core.ui.FarmColors

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FarmButton
import com.farm.customer.land.PlotItem
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

/**
 * OpenStreetMap tiles (osmdroid, no API key) with each plot drawn as its boundary polygon (a pin
 * when a plot has no usable polygon). Tapping a plot selects it; "Search this area" asks the land
 * service for plots inside the visible box (west, south, east, north).
 */
@Composable
fun PlotTileMap(
    plots: List<PlotItem>,
    centerLat: Double,
    centerLng: Double,
    selectedId: String?,
    onSelect: (PlotItem) -> Unit,
    onSearchArea: (List<Double>) -> Unit,
    modifier: Modifier = Modifier,
    /** Title of the blue pin at the search centre (the person's position, or the chosen place). */
    meTitle: String = "You are here",
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val select by rememberUpdatedState(onSelect)
    val mapView = remember {
        Configuration.getInstance().apply {
            // OSM's tile policy asks for an identifying user agent; tiles are cached in app storage.
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(11.0)
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

    LaunchedEffect(centerLat, centerLng) { mapView.controller.animateTo(GeoPoint(centerLat, centerLng)) }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.removeAll { it is Polygon || it is Marker }
                plots.forEach { plot -> map.overlays.add(plotOverlay(map, plot, plot.id == selectedId) { select(plot) }) }
                map.overlays.add(meMarker(map, centerLat, centerLng, meTitle))
                map.invalidate()
            },
        )
        FarmButton(
            "Search this area",
            {
                val box = mapView.boundingBox
                if (box.lonWest < box.lonEast && box.latSouth < box.latNorth) {
                    onSearchArea(listOf(box.lonWest, box.latSouth, box.lonEast, box.latNorth))
                }
            },
            Modifier.align(Alignment.TopCenter).padding(8.dp),
            variant = ButtonVariant.Secondary,
        )
        Text(
            "© OpenStreetMap contributors",
            fontSize = 10.sp,
            color = FarmColors.TextMuted,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xCCFFFFFF)).padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

private const val GREEN = 0xFF1D7A4D.toInt()
private const val BLUE = 0xFF2F5FD0.toInt()

/** The plot's boundary ([lng, lat] ring) as a polygon, or a pin at its centre without one. */
private fun plotOverlay(map: MapView, plot: PlotItem, selected: Boolean, onTap: () -> Unit) =
    plot.boundary.firstOrNull()?.filter { it.size >= 2 }?.takeIf { it.size >= 3 }?.let { ring ->
        Polygon(map).apply {
            points = ring.map { GeoPoint(it[1], it[0]) }
            title = plot.title
            fillPaint.color = AndroidColor.argb(if (selected) 110 else 60, AndroidColor.red(GREEN), AndroidColor.green(GREEN), AndroidColor.blue(GREEN))
            outlinePaint.color = if (selected) BLUE else GREEN
            outlinePaint.strokeWidth = if (selected) 6f else 3f
            setOnClickListener { _, _, _ ->
                onTap()
                true
            }
        }
    } ?: Marker(map).apply {
        position = GeoPoint(plot.centroid.getOrElse(1) { 0.0 }, plot.centroid.getOrElse(0) { 0.0 })
        title = plot.title
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        setOnMarkerClickListener { _, _ ->
            onTap()
            true
        }
    }
