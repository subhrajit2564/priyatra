package com.priyatra.guide.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.priyatra.guide.auth.PhoneUtils
import com.priyatra.guide.auth.SessionManager
import com.priyatra.guide.data.TravelSpot
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.geo.GeoUtils
import com.priyatra.guide.location.LocationReportStore
import com.priyatra.guide.ui.location.rememberUserLocation

@Composable
fun LiveMapScreen(onOpenSpot: (String) -> Unit) {
    val locState = rememberUserLocation(intervalMs = 10_000L)
    val userLoc = locState.value
    val context = LocalContext.current
    val trip by TripRepository.tripState.collectAsState()
    val spots = trip.spots
    val hotel = trip.hotel
    
    val initialPos = remember<LatLng> {
        val first = spots.firstOrNull() ?: TravelSpot("", "", 0, 0, 27.0423, 88.2631, "", emptyList(), null, null, emptyList(), emptyList(), emptyList())
        LatLng(first.lat, first.lng)
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 12f)
    }

    // Auto-center on user location once when it becomes available
    LaunchedEffect(userLoc == null) {
        if (userLoc != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(userLoc.latitude, userLoc.longitude),
                14f
            )
        }
    }

    // Share last position with the desk map (same device / POC store).
    LaunchedEffect(userLoc) {
        val loc = userLoc ?: return@LaunchedEffect
        val sm = SessionManager(context)
        if (sm.isAdmin() && sm.getPreviewTripId() == null) return@LaunchedEffect
        val tid = sm.getActiveUserTripId() ?: return@LaunchedEffect
        val ph = sm.getPhone()?.let { PhoneUtils.normalize(it) } ?: return@LaunchedEffect
        if (ph.isNotEmpty()) {
            LocationReportStore(context).put(tid, ph, loc.latitude, loc.longitude)
        }
    }

    val nearestSpot = remember(userLoc) {
        if (userLoc == null) null
        else {
            spots.minByOrNull {
                GeoUtils.haversineMeters(userLoc.latitude, userLoc.longitude, it.lat, it.lng)
            }?.let {
                val dist = GeoUtils.haversineMeters(userLoc.latitude, userLoc.longitude, it.lat, it.lng)
                if (dist < 1000) it else null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = userLoc != null),
            uiSettings = MapUiSettings(myLocationButtonEnabled = true)
        ) {
            Marker(
                state = rememberMarkerState(position = LatLng(hotel.lat, hotel.lng)),
                title = "Hotel: ${hotel.name}",
                snippet = hotel.address,
            )

            spots.forEach { spot ->
                Marker(
                    state = rememberMarkerState(position = LatLng(spot.lat, spot.lng)),
                    title = spot.name,
                    snippet = spot.highlights.firstOrNull(),
                    onInfoWindowClick = { onOpenSpot(spot.id) }
                )
            }
        }

        AnimatedVisibility(
            visible = nearestSpot != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            nearestSpot?.let { spot ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "You are at: ${spot.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            spot.highlights.firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                        TextButton(
                            onClick = { onOpenSpot(spot.id) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("VIEW GUIDE")
                        }
                    }
                }
            }
        }
        
        if (userLoc == null) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    "Enable location to see your position on the map.",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
