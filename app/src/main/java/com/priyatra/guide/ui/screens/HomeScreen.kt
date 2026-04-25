package com.priyatra.guide.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.priyatra.guide.R
import com.priyatra.guide.data.TripRepository

@Composable
fun HomeScreen(
    onOpenToday: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenServices: () -> Unit,
) {
    val trip by TripRepository.tripState.collectAsState()
    val sub = if (trip.destination.isNotBlank()) {
        trip.destination
    } else {
        "Trip details, tickets, and day-by-day help"
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_priyatra_logo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            contentScale = ContentScale.Fit,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                )
                .padding(22.dp),
        ) {
            Text("Your journey", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            Text(
                trip.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
        }

        Text(
            "Priyatra Getaways keeps tickets, drivers, hotels, meals, and day plans in one calm timeline — with gentle nudges before each leg.",
            style = MaterialTheme.typography.bodyLarge,
        )

        HomeShortcut(
            title = "Today",
            subtitle = "Live context, weather, and where you should be",
            icon = Icons.Outlined.CalendarMonth,
            onClick = onOpenToday,
        )
        HomeShortcut(
            title = "${trip.days.size}-day plan",
            subtitle = "Curated stops with stories, food, and photo notes",
            icon = Icons.Outlined.Landscape,
            onClick = onOpenPlan,
        )
        HomeShortcut(
            title = "Tickets & hotel",
            subtitle = "PNR, PDF ticket, confirmations, drivers",
            icon = Icons.Outlined.Train,
            onClick = onOpenServices,
        )
    }
}

@Composable
private fun HomeShortcut(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
