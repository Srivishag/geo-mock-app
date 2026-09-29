package com.example.locationspoofer.ui

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.locationspoofer.R
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/**
 * High-performance OpenStreetMap (osmdroid) location picker composable.
 * Supports tap-to-pin, real device location overlay, and quick locate-me controls.
 */
@Composable
fun MapLocationPicker(
    selectedLatitude: Double,
    selectedLongitude: Double,
    onLocationSelected: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
    currentDeviceLocation: Pair<Double, Double>? = null,
    isLocating: Boolean = false,
    onLocateMeClicked: () -> Unit = {},
    cameraTarget: Pair<Double, Double>? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // High-speed OpenStreetMap Humanitarian / France multi-server CDN (No blocks, zero API keys)
    val tileSource = remember {
        XYTileSource(
            "OSM_HOT_FAST",
            0,
            19,
            256,
            ".png",
            arrayOf(
                "https://a.tile.openstreetmap.fr/hot/",
                "https://b.tile.openstreetmap.fr/hot/",
                "https://c.tile.openstreetmap.fr/hot/",
                "https://a.tile.openstreetmap.fr/osmfr/"
            ),
            "© OpenStreetMap contributors"
        )
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(tileSource)

            // GPU Hardware Acceleration & Smooth Rendering
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isTilesScaledToDpi = true
            isFlingEnabled = true
            setMultiTouchControls(true)

            // Constraints & Performance optimizations
            minZoomLevel = 3.0
            maxZoomLevel = 19.5
            isHorizontalMapRepetitionEnabled = true
            isVerticalMapRepetitionEnabled = false

            // Initial view
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(selectedLatitude, selectedLongitude))
        }
    }

    val targetMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Target Mock Location"
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_target_pin)
            if (drawable != null) {
                icon = drawable
            }
        }
    }

    val currentLocMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Current Device Location"
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_current_location_marker)
            if (drawable != null) {
                icon = drawable
            }
        }
    }

    // Gesture Overlay for tap/long-press
    LaunchedEffect(mapView) {
        mapView.overlays.clear()

        val gestureOverlay = object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
                val p = map.projection.fromPixels(e.x.toInt(), e.y.toInt())
                if (p != null) {
                    onLocationSelected(p.latitude, p.longitude)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent, map: MapView): Boolean {
                val p = map.projection.fromPixels(e.x.toInt(), e.y.toInt())
                if (p != null) {
                    onLocationSelected(p.latitude, p.longitude)
                }
                return true
            }
        }

        mapView.overlays.add(gestureOverlay)
        mapView.overlays.add(targetMarker)
    }

    // Update target marker position
    LaunchedEffect(selectedLatitude, selectedLongitude) {
        val geoPoint = GeoPoint(selectedLatitude, selectedLongitude)
        targetMarker.position = geoPoint
        targetMarker.snippet = String.format("%.4f, %.4f", selectedLatitude, selectedLongitude)
        mapView.invalidate()
    }

    // Update real device current location marker
    LaunchedEffect(currentDeviceLocation) {
        if (currentDeviceLocation != null) {
            val (cLat, cLon) = currentDeviceLocation
            currentLocMarker.position = GeoPoint(cLat, cLon)
            currentLocMarker.snippet = String.format("Current GPS: %.4f, %.4f", cLat, cLon)
            if (!mapView.overlays.contains(currentLocMarker)) {
                // Add device marker below target pin
                mapView.overlays.add(mapView.overlays.size - 1, currentLocMarker)
            }
        } else {
            mapView.overlays.remove(currentLocMarker)
        }
        mapView.invalidate()
    }

    // Animate camera when target changes externally
    LaunchedEffect(cameraTarget) {
        cameraTarget?.let { (lat, lon) ->
            val geoPoint = GeoPoint(lat, lon)
            mapView.controller.animateTo(geoPoint)
        }
    }

    // Lifecycle Management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                RoundedCornerShape(18.dp)
            )
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Target Coordinates Badge (Top-Left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7))
                )
                Text(
                    text = String.format("Target: %.4f, %.4f", selectedLatitude, selectedLongitude),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // "Locate Me / My Location" Action Button (Top-Right)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(42.dp)
                .shadow(6.dp, CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            IconButton(
                onClick = onLocateMeClicked,
                modifier = Modifier.fillMaxSize(),
                enabled = !isLocating
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_my_location),
                        contentDescription = "My Current Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Live Current Location Indicator Banner (Bottom-Left if available)
        if (currentDeviceLocation != null) {
            val (cLat, cLon) = currentDeviceLocation
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clickable {
                        onLocationSelected(cLat, cLon)
                    },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                    )
                    Text(
                        text = String.format("GPS: %.4f, %.4f (Tap to Set)", cLat, cLon),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Tap Hint (Bottom-End)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.65f)
        ) {
            Text(
                text = "Tap / Drag to reposition",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}
