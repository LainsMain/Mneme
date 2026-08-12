package com.lainsmain.mneme.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lainsmain.mneme.data.DaySummary
import com.lainsmain.mneme.ui.photo.rememberFileBitmap
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    entries: List<DaySummary>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val places = remember(entries) {
        entries.filter { it.latitude != null && it.longitude != null }
    }
    val initialTarget = remember(places) {
        places.firstOrNull()?.let { LatLng(it.latitude!!, it.longitude!!) }
            ?: LatLng(50.8503, 4.3517)
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var results by remember { mutableStateOf<List<DaySummary>?>(null) }
    var resultsTitle by remember { mutableStateOf("Entries in this area") }
    var hasCenteredOnEntries by remember { mutableStateOf(false) }
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.35f

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
                isTiltGesturesEnabled = true
                isLogoEnabled = true
                isAttributionEnabled = true
            }
            readyMap.cameraPosition = CameraPosition.Builder()
                .target(initialTarget)
                .zoom(if (places.isEmpty()) 6.0 else 11.0)
                .build()
        }
    }

    LaunchedEffect(map, places, darkMap) {
        map?.setStyle(
            Style.Builder().fromUri(if (darkMap) OPEN_FREE_MAP_DARK else OPEN_FREE_MAP_LIGHT),
        ) {
            val activeMap = map ?: return@setStyle
            activeMap.clear()
            places.forEach { place ->
                activeMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(place.latitude!!, place.longitude!!))
                        .title(place.locationName ?: "Journal location")
                        .snippet(place.date.toString()),
                )
            }
            if (!hasCenteredOnEntries && places.isNotEmpty()) {
                hasCenteredOnEntries = true
                mapView.post {
                    if (places.size == 1) {
                        activeMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(places.first().latitude!!, places.first().longitude!!),
                                13.0,
                            ),
                        )
                    } else {
                        val bounds = LatLngBounds.Builder()
                            .includes(places.map { LatLng(it.latitude!!, it.longitude!!) })
                            .build()
                        activeMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72))
                    }
                }
            }
            activeMap.setOnMarkerClickListener { marker ->
                val radius = markerSearchRadiusMeters(activeMap.cameraPosition.zoom)
                results = places
                    .filter { place ->
                        distanceMeters(
                            marker.position.latitude,
                            marker.position.longitude,
                            place.latitude!!,
                            place.longitude!!,
                        ) <= radius
                    }
                    .sortedByDescending { it.date }
                resultsTitle = marker.title?.takeIf(String::isNotBlank) ?: "Entries near this pin"
                true
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 5.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Map, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "${places.size} located ${if (places.size == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (places.isEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
            ) {
                Column(
                    Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "No journal locations yet",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Choose a place from an entry and it will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    val activeMap = map ?: return@Button
                    val bounds = activeMap.projection.visibleRegion.latLngBounds
                    results = places
                        .filter { place -> bounds.contains(LatLng(place.latitude!!, place.longitude!!)) }
                        .sortedByDescending { it.date }
                    resultsTitle = "Entries in this area"
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Rounded.TravelExplore, contentDescription = null)
                Text("Search this area", Modifier.padding(start = 8.dp))
            }
        }
    }

    results?.let { matchingEntries ->
        ModalBottomSheet(onDismissRequest = { results = null }) {
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        resultsTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (matchingEntries.isEmpty()) {
                            "No located entries are visible here. Zoom out or move the map and try again."
                        } else {
                            "${matchingEntries.size} ${if (matchingEntries.size == 1) "entry" else "entries"} found"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
                    )
                }
                if (matchingEntries.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        items(matchingEntries, key = { it.date.toEpochDay() }) { entry ->
                            MapEntryResult(entry = entry, onOpenDay = { onOpenDay(entry.date) })
                            HorizontalDivider(Modifier.padding(start = 100.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapEntryResult(entry: DaySummary, onOpenDay: () -> Unit) {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    val bitmap by rememberFileBitmap(entry.thumbnailFileName, 500)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDay)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(66.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                )
            } ?: Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                entry.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.locationName ?: "Photo location",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.plainText.isNotBlank()) {
                Text(
                    entry.plainText.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onOpenDay) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Open journal entry")
        }
    }
}

private fun markerSearchRadiusMeters(zoom: Double): Double = when {
    zoom >= 15.0 -> 500.0
    zoom >= 13.0 -> 2_000.0
    zoom >= 10.0 -> 10_000.0
    else -> 50_000.0
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val latitudeDelta = Math.toRadians(lat2 - lat1)
    val longitudeDelta = Math.toRadians(lon2 - lon1)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private const val OPEN_FREE_MAP_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val OPEN_FREE_MAP_DARK = "https://tiles.openfreemap.org/styles/dark"
