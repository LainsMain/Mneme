package com.egoisticfoil.mneme.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
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
import com.egoisticfoil.mneme.data.DaySummary
import com.egoisticfoil.mneme.ui.photo.rememberFileBitmap
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapScreen(
    entries: List<DaySummary>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val places = remember(entries) { entries.filter { it.latitude != null && it.longitude != null } }
    val initialTarget = remember(places) {
        places.firstOrNull()?.let { LatLng(it.latitude!!, it.longitude!!) } ?: LatLng(50.8503, 4.3517)
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
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
            readyMap.setOnInfoWindowClickListener { marker ->
                marker.snippet?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.let(onOpenDay)
                true
            }
        }
    }

    LaunchedEffect(map, places, darkMap) {
        map?.setStyle(
            Style.Builder().fromUri(if (darkMap) OPEN_FREE_MAP_DARK else OPEN_FREE_MAP_LIGHT),
        ) {
            map?.clear()
            places.forEach { place ->
                map?.addMarker(
                    MarkerOptions()
                        .position(LatLng(place.latitude!!, place.longitude!!))
                        .title(place.locationName ?: "Journal location")
                        .snippet(place.date.toString()),
                )
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
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 5.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Map, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "${places.size} ${if (places.size == 1) "place" else "places"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (places.isEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                    Text("No journal locations yet", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "Choose a place from an entry and it will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(places, key = { it.date.toEpochDay() }) { place ->
                    MapPlaceCard(
                        place = place,
                        onCenter = {
                            map?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(place.latitude!!, place.longitude!!),
                                    14.0,
                                ),
                            )
                        },
                        onOpenDay = { onOpenDay(place.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MapPlaceCard(place: DaySummary, onCenter: () -> Unit, onOpenDay: () -> Unit) {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    val bitmap by rememberFileBitmap(place.thumbnailFileName, 500)
    Surface(
        modifier = Modifier.widthIn(min = 260.dp, max = 310.dp).clickable(onClick = onCenter),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                    )
                } ?: Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(
                    place.locationName ?: "Photo location",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    place.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(onClick = onOpenDay, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Open journal entry",
                    modifier = Modifier.padding(10.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private const val OPEN_FREE_MAP_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val OPEN_FREE_MAP_DARK = "https://tiles.openfreemap.org/styles/dark"
