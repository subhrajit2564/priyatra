package com.priyatra.guide.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.priyatra.guide.data.TravelSpot
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.geo.GeoUtils
import com.priyatra.guide.ui.TripViewModel
import com.priyatra.guide.ui.location.rememberUserLocation
import android.location.Location
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    vm: TripViewModel,
    onOpenSpot: (String) -> Unit,
    onOpenMaps: (Double, Double, String) -> Unit,
) {
    val trip by TripRepository.tripState.collectAsState()
    val zone = TripRepository.zone()
    val today = LocalDate.now(zone)
    val dayIndex = TripRepository.tripDayIndexForDate(today)
    val locState = rememberUserLocation()
    val loc = locState.value
    val weather by vm.weather.collectAsState()

    val todaySpots = remember(dayIndex, trip) {
        if (dayIndex == null) emptyList() else TripRepository.spotsForDay(dayIndex)
    }
    val focus = remember(dayIndex, todaySpots) {
        todaySpots.minByOrNull { it.order } ?: todaySpots.firstOrNull()
    }

    LaunchedEffect(loc, focus?.id) {
        val anchor = focus ?: return@LaunchedEffect
        vm.refreshWeather(anchor.lat, anchor.lng)
    }

    var feedbackSpot by remember { mutableStateOf<TravelSpot?>(null) }
    var stars by remember { mutableIntStateOf(5) }
    var note by remember { mutableStateOf("") }
    val prompted = remember { mutableSetOf<String>() }

    LaunchedEffect(loc?.latitude, loc?.longitude, todaySpots.map { it.id }) {
        val l = loc ?: return@LaunchedEffect
        val nearest = todaySpots.minByOrNull {
            GeoUtils.haversineMeters(l.latitude, l.longitude, it.lat, it.lng)
        } ?: return@LaunchedEffect
        val d = GeoUtils.haversineMeters(l.latitude, l.longitude, nearest.lat, nearest.lng)
        if (d < 450 && vm.shouldAskFeedback(nearest.id) && !prompted.contains(nearest.id)) {
            prompted.add(nearest.id)
            feedbackSpot = nearest
            stars = 5
            note = ""
        }
    }

    if (feedbackSpot != null) {
        val spot = feedbackSpot!!
        AlertDialog(
            onDismissRequest = {
                prompted.remove(spot.id)
                feedbackSpot = null
            },
            title = { Text("How was ${spot.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("You are near this PriyaTra stop — a quick star helps us tune real tours.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { s ->
                            TextButton(onClick = { stars = s }) {
                                Text(
                                    if (s <= stars) "★" else "☆",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Optional note") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveFeedback(spot.id, stars, note)
                        feedbackSpot = null
                    },
                ) { Text("Submit") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        prompted.remove(spot.id)
                        feedbackSpot = null
                    },
                ) { Text("Later") }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (dayIndex == null) {
            Text(
                "This POC itinerary is authored for 2–5 May 2026 in Asia/Kolkata. " +
                    "You can still explore the full plan, tickets, and spot guides from the other tabs.",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                TripRepository.dayPlan(dayIndex)?.summary.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Cloud, contentDescription = null)
                        Text("Weather near ${focus?.name.orEmpty()}", fontWeight = FontWeight.SemiBold)
                    }
                    if (weather == null) {
                        Text("Fetching Open-Meteo…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "${weather!!.tempC.toInt()}°C · ${weather!!.summary} · wind ${weather!!.windKmh.toInt()} km/h",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            val deviation = remember(loc, todaySpots) { deviationCopy(loc, todaySpots) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("GPS check-in", fontWeight = FontWeight.SemiBold)
                    }
                    Text(deviation, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Today’s stops", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            todaySpots.forEach { spot ->
                val distKm = if (loc == null) {
                    null
                } else {
                    GeoUtils.haversineMeters(loc.latitude, loc.longitude, spot.lat, spot.lng) / 1000.0
                }
                Card(
                    onClick = { onOpenSpot(spot.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(spot.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        distKm?.let {
                            Text(
                                String.format("About %.1f km away (straight line)", it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } ?: Text("Enable location to see distance.", style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = { onOpenMaps(spot.lat, spot.lng, spot.name) }) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Map, contentDescription = null)
                                Text("Maps")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun deviationCopy(loc: Location?, todaySpots: List<TravelSpot>): String {
    if (loc == null) return "Location permission lets PriyaTra compare you with today’s corridor."
    if (todaySpots.isEmpty()) return "No mapped stops today."
    val distances = todaySpots.map {
        GeoUtils.haversineMeters(loc.latitude, loc.longitude, it.lat, it.lng)
    }
    val min = distances.minOrNull() ?: return "No distance."
    val far = distances.all { it > 2_500 }
    val nearest = todaySpots[distances.indexOf(min)]
    return if (far) {
        "You appear outside today’s planned cluster. If that is intentional, enjoy — otherwise use the Help button to reach PriyaTra."
    } else {
        "Nearest planned stop: ${nearest.name} (~${String.format("%.1f", min / 1000.0)} km crow-flies)."
    }
}
