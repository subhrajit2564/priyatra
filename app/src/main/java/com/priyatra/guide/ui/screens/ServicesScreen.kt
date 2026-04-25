package com.priyatra.guide.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.priyatra.guide.assets.TicketShare
import com.priyatra.guide.data.HotelBooking
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.notifications.TripNotificationScheduler
import java.time.format.DateTimeFormatter

@Composable
fun ServicesScreen(isAdmin: Boolean = false, onBackToAdmin: () -> Unit = {}, onLogout: () -> Unit = {}) {
    val trip by TripRepository.tripState.collectAsState()
    val ctx = LocalContext.current
    val zone = TripRepository.zone()
    val fmt = DateTimeFormatter.ofPattern("dd MMM, HH:mm")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isAdmin) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Admin Mode Active", fontWeight = FontWeight.Bold)
                    Button(onClick = onBackToAdmin, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Return to Admin Dashboard")
                    }
                }
            }
        }

        Text("Tickets & confirmations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        trip.transports.forEach { t ->
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${t.from} → ${t.to}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Departs ${t.departure.atZone(zone).format(fmt)} · Arrives ${t.arrival.atZone(zone).format(fmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("PNR ${t.pnr} · Coach ${t.coach.orEmpty()} · Seats ${t.seat.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(
                        onClick = { TicketShare.openPdfFromAssets(ctx, t.pdfAssetPath) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null)
                        Text("Open PDF ticket (sample)")
                    }
                }
            }
        }

        Text("Hotels", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        HotelServiceCard(name = "Primary stay", hotel = trip.hotel)
        trip.extraHotels.orEmpty().forEachIndexed { i, h ->
            HotelServiceCard(name = "Additional stay ${i + 1}", hotel = h)
        }

        Text("Drivers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Numbers swap automatically by date — mid-trip changes stay accurate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val active = TripRepository.activeDriver()
        if (active != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("On duty today", fontWeight = FontWeight.SemiBold)
                    Text(active.name, style = MaterialTheme.typography.titleMedium)
                    Text(active.segmentLabel, style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = { dial(ctx, active.phone) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Phone, contentDescription = null)
                        Text(active.phone)
                    }
                }
            }
        }
        trip.drivers.forEach { d ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.name, fontWeight = FontWeight.SemiBold)
                    Text(d.segmentLabel, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${d.activeFrom} → ${d.activeUntil ?: "open"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { dial(ctx, d.phone) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Call ${d.phone}")
                    }
                }
            }
        }

        Text("Notifications POC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Departures fire at 24h / 6h / 3h / 1h / 30m. Meals fire daily 2–5 May at the scheduled windows.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = { TripNotificationScheduler.scheduleDemoNotification(ctx, 8L) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
            Text("Send test notification (8s)")
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
    }
}

@Composable
private fun HotelServiceCard(name: String, hotel: HotelBooking) {
    val ctx = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            RowIconTitle(Icons.Outlined.Hotel, hotel.name)
            Text(hotel.address, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Check-in ${hotel.checkIn} · Check-out ${hotel.checkOut}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Confirmation ${hotel.confirmation}", style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(
                onClick = { dial(ctx, hotel.phone) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Call, contentDescription = null)
                Text("Call hotel · ${hotel.phone}")
            }
            OutlinedButton(
                onClick = {
                    val uri = Uri.parse("geo:${hotel.lat},${hotel.lng}?q=${Uri.encode(hotel.name)}")
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open hotel in Maps") }
        }
    }
}

@Composable
private fun RowIconTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun dial(ctx: android.content.Context, raw: String) {
    val normalized = raw.replace(" ", "")
    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized")))
}
