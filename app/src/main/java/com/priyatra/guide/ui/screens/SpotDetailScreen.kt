package com.priyatra.guide.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.priyatra.guide.data.TripRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailScreen(spotId: String, onBack: () -> Unit) {
    val trip by TripRepository.tripState.collectAsState()
    val spot = trip.spots.find { it.id == spotId }
    val ctx = LocalContext.current
    if (spot == null) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Spot guide") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Text(
                "This stop is not in the POC dataset.",
                modifier = Modifier.padding(padding).padding(20.dp),
            )
        }
        return
    }
    val openGeo = { lat: Double, lng: Double, q: String ->
        val uri = Uri.parse("geo:$lat,$lng?q=${Uri.encode(q)}")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
    val openGoogleImages = { query: String ->
        val q = Uri.encode(query)
        val url = "https://www.google.com/search?tbm=isch&q=$q"
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Spot guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(spot.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            FilledTonalButton(
                onClick = { openGeo(spot.lat, spot.lng, spot.name) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Open main stop in Maps (GPS ${String.format("%.5f, %.5f", spot.lat, spot.lng)})")
            }

            Text("1 · History of the place", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (spot.history.isNotBlank()) spot.history else "—",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text("2 · Famous points of the place", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (spot.highlights.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                spot.highlights.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }

            val reach = spot.reachabilityNote?.trim().orEmpty()
            val trek = spot.trekOrLocalNote?.trim().orEmpty()
            Text("3 · Reach by car, trek & local options (time & map)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "If a car can reach parking or a drop, that is called out below. If a trek, shared jeeps, or local transport are needed, times and how to get them are in the same section — use Maps for the main pin, or a viewpoint pin on each photo card when the GPS differs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reach.isNotEmpty()) {
                Text("By road / vehicle", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(reach, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trek.isNotEmpty()) {
                Text("Trek, last mile & local transport (times)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(trek, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(
                    onClick = { openGeo(spot.lat, spot.lng, "${spot.name} access") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Open in Maps (use text for trailhead if it differs from this pin)")
                }
            }

            Text("4 · Best food to try", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (spot.foods.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                spot.foods.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }

            Text("5 · Souvenirs & keepsakes to buy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (spot.souvenirs.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                spot.souvenirs.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }

            Text("6 · Best photos: examples, Google Images, viewpoint & GPS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "When the guide includes an image link, it appears below. Otherwise use Google Images; each card has a map pin for that viewpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            spot.photoTips.forEach { tip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tip.viewpoint, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        Text(tip.description, style = MaterialTheme.typography.bodyMedium)

                        if (tip.exampleImageUrl.isNotBlank() && (tip.exampleImageUrl.startsWith("http://", true) || tip.exampleImageUrl.startsWith("https://", true))) {
                            AsyncImage(
                                model = tip.exampleImageUrl,
                                contentDescription = tip.viewpoint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        FilledTonalButton(
                            onClick = { openGoogleImages("${spot.name} ${tip.viewpoint}") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Find more photos (Google Images)")
                        }

                        OutlinedButton(
                            onClick = { openGeo(tip.lat, tip.lng, tip.viewpoint) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Navigate to viewpoint (GPS ${String.format("%.5f, %.5f", tip.lat, tip.lng)})")
                        }
                    }
                }
            }
            if (spot.photoTips.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
