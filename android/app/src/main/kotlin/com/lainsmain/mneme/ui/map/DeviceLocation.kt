package com.lainsmain.mneme.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat

@Composable
internal fun rememberDeviceLocationRequest(
    onLocation: (Location) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnLocation = rememberUpdatedState(onLocation)
    val currentOnUnavailable = rememberUpdatedState(onUnavailable)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            requestDeviceLocation(context, currentOnLocation.value, currentOnUnavailable.value)
        } else {
            currentOnUnavailable.value()
        }
    }
    return remember(context, permissionLauncher) {
        {
            if (context.hasLocationPermission()) {
                requestDeviceLocation(context, currentOnLocation.value, currentOnUnavailable.value)
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun requestDeviceLocation(
    context: Context,
    onLocation: (Location) -> Unit,
    onUnavailable: () -> Unit,
) {
    val manager = context.getSystemService(LocationManager::class.java)
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val provider = listOfNotNull(
        LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(it) },
        LocationManager.GPS_PROVIDER.takeIf { hasFineLocation && manager.isProviderEnabled(it) },
    ).firstOrNull()
    if (provider == null) {
        onUnavailable()
        return
    }

    runCatching {
        manager.getLastKnownLocation(provider)
            ?.takeIf { System.currentTimeMillis() - it.time < RECENT_LOCATION_MAX_AGE_MILLIS }
            ?.let {
                onLocation(it)
                return
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(provider, CancellationSignal(), context.mainExecutor) { location ->
                if (location != null) {
                    onLocation(location)
                } else {
                    manager.getLastKnownLocation(provider)?.let(onLocation) ?: onUnavailable()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(
                provider,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) = onLocation(location)
                    override fun onProviderDisabled(provider: String) = onUnavailable()
                    override fun onProviderEnabled(provider: String) = Unit
                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                },
                Looper.getMainLooper(),
            )
        }
    }.onFailure { onUnavailable() }
}

private const val RECENT_LOCATION_MAX_AGE_MILLIS = 5 * 60 * 1_000L
