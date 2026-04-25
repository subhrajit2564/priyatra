package com.priyatra.guide.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberUserLocation(intervalMs: Long = 22_000L): MutableState<Location?> {
    val context = LocalContext.current
    val client = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationState = remember { mutableStateOf<Location?>(null) }
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )

    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) {
            permissions.launchMultiplePermissionRequest()
        }
    }

    DisposableEffect(permissions.allPermissionsGranted, client) {
        var callback: LocationCallback? = null
        if (!permissions.allPermissionsGranted) {
            return@DisposableEffect onDispose { }
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs,
        ).setMinUpdateIntervalMillis(intervalMs / 2).build()
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                locationState.value = result.lastLocation
            }
        }
        try {
            @SuppressLint("MissingPermission")
            client.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
        } catch (_: SecurityException) {
        }
        onDispose {
            callback?.let { client.removeLocationUpdates(it) }
        }
    }
    return locationState
}
