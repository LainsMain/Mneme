package com.lainsmain.mneme.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.Locale
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun LocationPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val initialTarget = remember(initialLatitude, initialLongitude) {
        if (initialLatitude != null && initialLongitude != null) {
            LatLng(initialLatitude, initialLongitude)
        } else {
            DEFAULT_MAP_TARGET
        }
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedTarget by remember(initialTarget) { mutableStateOf(initialTarget) }
    var deviceTarget by remember { mutableStateOf<LatLng?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val requestDeviceLocation = rememberDeviceLocationRequest(
        onLocation = { location ->
            locationMessage = null
            deviceTarget = LatLng(location.latitude, location.longitude)
        },
        onUnavailable = {
            locationMessage = "Location is unavailable. Check the app permission and device location setting."
        },
    )

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.apply {
                isCompassEnabled = true
                isZoomGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
                isLogoEnabled = true
                isAttributionEnabled = true
            }
            readyMap.cameraPosition = CameraPosition.Builder()
                .target(initialTarget)
                .zoom(if (initialLatitude == null) 7.0 else 15.0)
                .build()
            readyMap.addOnCameraIdleListener {
                readyMap.cameraPosition.target?.let { selectedTarget = it }
            }
            readyMap.addOnMapLongClickListener { target ->
                readyMap.animateCamera(CameraUpdateFactory.newLatLng(target))
                true
            }
        }
    }

    LaunchedEffect(map, darkMap) {
        map?.setStyle(
            Style.Builder().fromUri(if (darkMap) OPEN_FREE_MAP_DARK else OPEN_FREE_MAP_LIGHT),
        )
    }

    LaunchedEffect(map, deviceTarget) {
        val target = deviceTarget ?: return@LaunchedEffect
        val activeMap = map ?: return@LaunchedEffect
        selectedTarget = target
        activeMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16.0))
    }

    LaunchedEffect(initialLatitude, initialLongitude) {
        if (initialLatitude == null || initialLongitude == null) requestDeviceLocation()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close map picker")
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Choose a location", fontWeight = FontWeight.SemiBold)
                            Text(
                                locationMessage ?: "Move the map, long-press, or use your location",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (locationMessage == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.Center).offset(y = (-18).dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp,
                ) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = "Selected map point",
                        modifier = Modifier.padding(10.dp).size(26.dp),
                    )
                }

                Surface(
                    onClick = requestDeviceLocation,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .systemBarsPadding()
                        .padding(end = 22.dp, bottom = 112.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 8.dp,
                ) {
                    Icon(
                        Icons.Rounded.MyLocation,
                        contentDescription = "Use my current location",
                        modifier = Modifier.padding(14.dp).size(22.dp),
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Pinned location", fontWeight = FontWeight.SemiBold)
                            Text(
                                formatCoordinates(selectedTarget),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { onConfirm(selectedTarget.latitude, selectedTarget.longitude) }) {
                            Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp))
                            Text("Use point", modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatCoordinates(target: LatLng): String =
    "${"%.5f".format(Locale.ROOT, target.latitude)}, ${"%.5f".format(Locale.ROOT, target.longitude)}"

private val DEFAULT_MAP_TARGET = LatLng(50.8503, 4.3517)
private const val OPEN_FREE_MAP_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val OPEN_FREE_MAP_DARK = "https://tiles.openfreemap.org/styles/dark"
