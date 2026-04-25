package com.priyatra.guide.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.priyatra.guide.admin.AdminFormMerge
import com.priyatra.guide.BuildConfig
import com.priyatra.guide.admin.AdminViewModel
import com.priyatra.guide.admin.DayFormData
import com.priyatra.guide.admin.HotelFormRow
import com.priyatra.guide.admin.dayFormsFromTrip
import com.priyatra.guide.admin.hotelRowsFromTrip
import com.priyatra.guide.admin.joinDestinationList
import com.priyatra.guide.admin.parseDestinationList
import com.priyatra.guide.data.HotelBooking
import com.priyatra.guide.data.StoredTrip
import com.priyatra.guide.location.LocationReportStore
import com.priyatra.guide.notifications.NotificationHelper
import com.priyatra.guide.notifications.TripNotificationScheduler
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onLogout: () -> Unit,
    onPreviewWithTrip: (String) -> Unit,
) {
    val nav = rememberNavController()
    val vm: AdminViewModel = viewModel()
    val catalog by vm.catalog.collectAsState()
    val busy by vm.busy.collectAsState()
    val err by vm.error.collectAsState()
    val llmInfo by vm.llmInfo.collectAsState()
    var previewOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshList() }

    if (err != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("Something went wrong") },
            text = { Text(err!!) },
            confirmButton = { TextButton({ vm.clearError() }) { Text("OK") } },
        )
    }
    if (llmInfo != null) {
        AlertDialog(
            onDismissRequest = { vm.clearLlmInfo() },
            title = { Text("AI itinerary") },
            text = { Text(llmInfo!!) },
            confirmButton = { TextButton({ vm.clearLlmInfo() }) { Text("OK") } },
        )
    }

    if (previewOpen) {
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            title = { Text("Preview as traveller") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose which trip to load in the user app (same build).", style = MaterialTheme.typography.bodySmall)
                    catalog.forEach { t ->
                        TextButton(
                            onClick = {
                                onPreviewWithTrip(t.id)
                                previewOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(t.name, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ previewOpen = false }) { Text("Cancel") } },
        )
    }

    val navEntry by nav.currentBackStackEntryAsState()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Priyatra · Desk") },
                actions = {
                    TextButton({ nav.navigate("admin_nudges") }) { Text("Nudges") }
                    TextButton({ nav.navigate("admin_map") }) { Text("Map") }
                },
            )
        },
        floatingActionButton = {
            if (navEntry?.destination?.route == "admin_list") {
                FloatingActionButton(
                    onClick = {
                        val id = vm.addDraftTrip()
                        nav.navigate("admin_edit/$id")
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "New trip")
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "admin_list",
            modifier = Modifier.padding(padding),
        ) {
            composable("admin_list") {
                AdminTripList(
                    catalog = catalog,
                    busy = busy,
                    onOpen = { id -> nav.navigate("admin_edit/$id") },
                    onDelete = { id -> vm.deleteTrip(id) },
                    onRefresh = { vm.refreshFromServer() },
                    onLogout = onLogout,
                    onPreview = { previewOpen = true },
                )
            }
            composable("admin_nudges") { AdminNudgesPane(onBack = { nav.popBackStack() }) }
            composable("admin_map") { AdminMapPane(catalog, onBack = { nav.popBackStack() }) }
            composable("admin_edit/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id").orEmpty()
                AdminTripEdit(
                    tripId = id,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun AdminTripList(
    catalog: List<StoredTrip>,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onPreview: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Create trips, add travellers’ numbers, and send nudges. Use AI (Groq, free key) to draft stops after you add driver, hotel, and day notes. Set GROQ_API_KEY in local.properties.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy) {
            Text("Working…", style = MaterialTheme.typography.labelLarge)
        }
        catalog.forEach { t ->
            Card(
                onClick = { onOpen(t.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(8.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${t.destination} · ${t.startDate} · ${t.durationDays} day(s) · ${t.customerPhones.size} guest(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    }
                    IconButton(onClick = { onDelete(t.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete trip")
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                Text("Preview user app (pick a trip)")
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (BuildConfig.SUPABASE_URL.isNotEmpty()) "Sync from shared cloud" else "Reload catalog",
                )
            }
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Logout")
            }
        }
    }
}

@Composable
private fun AdminTripEdit(
    tripId: String,
    vm: AdminViewModel,
    onBack: () -> Unit,
) {
    val list by vm.catalog.collectAsState()
    val trip = list.find { it.id == tripId }
    if (trip == null) {
        LaunchedEffect(tripId) { onBack() }
        return
    }
    var name by remember { mutableStateOf(trip.name) }
    var destList by remember { mutableStateOf(parseDestinationList(trip.destination)) }
    var startText by remember { mutableStateOf(trip.startDate.toString()) }
    var support by remember { mutableStateOf(trip.tripPackage.supportPhone) }
    var hotelRows by remember { mutableStateOf(hotelRowsFromTrip(trip)) }
    var phones by remember { mutableStateOf(trip.customerPhones.joinToString(", ")) }
    var dayRows by remember { mutableStateOf(dayFormsFromTrip(trip)) }

    fun parseStart(): LocalDate? = runCatching { LocalDate.parse(startText) }.getOrNull()

    fun renumberDays(rows: List<DayFormData>): List<DayFormData> =
        rows.sortedBy { it.dayIndex }.mapIndexed { idx, d -> d.copy(dayIndex = idx + 1) }

    fun addDay() {
        val n = (dayRows.maxOfOrNull { it.dayIndex } ?: 0) + 1
        dayRows = dayRows + DayFormData(
            dayIndex = n,
            title = "Day $n",
            summary = "",
        )
    }

    fun removeDay(dayIndex: Int) {
        if (dayRows.size <= 1) return
        val next = renumberDays(dayRows.filter { it.dayIndex != dayIndex })
        dayRows = next
    }

    fun setHotelRow(i: Int, row: HotelFormRow) {
        hotelRows = hotelRows.mapIndexed { j, h -> if (j == i) row else h }
    }

    fun addHotelRow() {
        val s = parseStart() ?: LocalDate.now()
        hotelRows = hotelRows + HotelFormRow(
            name = "",
            address = "",
            lat = "0",
            lng = "0",
            checkIn = s.toString(),
            checkOut = s.plusDays(1).toString(),
            confirmation = "",
            phone = "",
        )
    }

    fun removeHotelRow(index: Int) {
        if (hotelRows.size <= 1) return
        if (index == 0) return
        hotelRows = hotelRows.filterIndexed { j, _ -> j != index }
    }

    LaunchedEffect(trip.tripPackage, trip.startDate, trip.durationDays, trip.customerPhones, trip.name, trip.destination) {
        val t = list.find { it.id == tripId } ?: return@LaunchedEffect
        name = t.name
        destList = parseDestinationList(t.destination)
        startText = t.startDate.toString()
        support = t.tripPackage.supportPhone
        hotelRows = hotelRowsFromTrip(t)
        phones = t.customerPhones.joinToString(", ")
        dayRows = renumberDays(dayFormsFromTrip(t))
    }

    val destinationJoined: () -> String = {
        val j = joinDestinationList(destList)
        if (j.isBlank()) "—" else j
    }
    val dayCount = dayRows.size

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton({ onBack() }) { Text("← Back") }
        Text("Edit trip", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Trip name") }, modifier = Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Destinations", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { destList = destList + "" },
            ) { Icon(Icons.Outlined.Add, contentDescription = "Add destination") }
        }
        destList.forEachIndexed { i, line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = line,
                    onValueChange = { v -> destList = destList.mapIndexed { j, s -> if (j == i) v else s } },
                    label = { Text("Area / stop ${i + 1}") },
                    modifier = Modifier.weight(1f),
                )
                if (destList.size > 1) {
                    IconButton(
                        onClick = { destList = destList.filterIndexed { j, _ -> j != i } },
                    ) { Icon(Icons.Outlined.Remove, contentDescription = "Remove destination") }
                }
            }
        }

        OutlinedTextField(startText, { startText = it }, label = { Text("Start date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
        Text("Trip length: $dayCount day(s) — add or remove day cards below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(support, { support = it }, label = { Text("Support / desk phone (shown in app)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phones, { phones = it }, label = { Text("Guest phones (comma separated, digits)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hotels (first = primary for map & meals)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { addHotelRow() },
            ) { Icon(Icons.Outlined.Add, contentDescription = "Add hotel") }
        }
        hotelRows.forEachIndexed { hi, hrow ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hi == 0) "Primary hotel" else "Extra hotel $hi",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (hotelRows.size > 1 && hi > 0) {
                            IconButton(onClick = { removeHotelRow(hi) }) {
                                Icon(Icons.Outlined.Remove, contentDescription = "Remove hotel")
                            }
                        }
                    }
                    OutlinedTextField(
                        hrow.name,
                        { setHotelRow(hi, hrow.copy(name = it)) },
                        label = { Text("Hotel name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        hrow.address,
                        { setHotelRow(hi, hrow.copy(address = it)) },
                        label = { Text("Address") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            hrow.lat,
                            { setHotelRow(hi, hrow.copy(lat = it)) },
                            label = { Text("Lat") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            hrow.lng,
                            { setHotelRow(hi, hrow.copy(lng = it)) },
                            label = { Text("Lng") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            hrow.checkIn,
                            { setHotelRow(hi, hrow.copy(checkIn = it)) },
                            label = { Text("Check-in (yyyy-MM-dd)") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            hrow.checkOut,
                            { setHotelRow(hi, hrow.copy(checkOut = it)) },
                            label = { Text("Check-out") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        hrow.phone,
                        { setHotelRow(hi, hrow.copy(phone = it)) },
                        label = { Text("Hotel POC phone") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        hrow.confirmation,
                        { setHotelRow(hi, hrow.copy(confirmation = it)) },
                        label = { Text("Confirmation #") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Itinerary days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { addDay() },
            ) { Icon(Icons.Outlined.Add, contentDescription = "Add day") }
        }
        val sortedDays = dayRows.sortedBy { it.dayIndex }
        sortedDays.forEach { d ->
            val di = d.dayIndex
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Day $di", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (dayRows.size > 1) {
                            IconButton(onClick = { removeDay(di) }) {
                                Icon(Icons.Outlined.Remove, contentDescription = "Remove day")
                            }
                        }
                    }
                    OutlinedTextField(
                        d.title,
                        { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(title = v) else it } },
                        label = { Text("Day title") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        d.summary,
                        { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(summary = v) else it } },
                        label = { Text("Stops, food, notes (AI + guests)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        d.driverName,
                        { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(driverName = v) else it } },
                        label = { Text("Driver name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        d.driverPhone,
                        { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(driverPhone = v) else it } },
                        label = { Text("Driver POC phone (required for ops)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        d.hotelName,
                        { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(hotelName = v) else it } },
                        label = { Text("Hotel (this day, optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        d.hotelAddress,
                        { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(hotelAddress = v) else it } },
                        label = { Text("Hotel address") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            d.hotelLat,
                            { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(hotelLat = v) else it } },
                            label = { Text("H lat") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            d.hotelLng,
                            { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(hotelLng = v) else it } },
                            label = { Text("H lng") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        d.hotelPoc,
                        { v -> dayRows = dayRows.map { if (it.dayIndex == di) it.copy(hotelPoc = v) else it } },
                        label = { Text("Hotel POC phone") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val parseStartFn: () -> LocalDate? = { parseStart() }
        val dura = renumberDays(dayRows).size

        fun buildMergedTrip(s: LocalDate, primary: HotelBooking, extra: List<HotelBooking>) = AdminFormMerge.apply(
            base = trip,
            name = name,
            destination = destinationJoined(),
            start = s,
            duration = dura,
            supportPhone = support,
            days = renumberDays(dayRows),
            primaryHotel = primary,
            extraHotels = extra,
        )

        Button(
            onClick = {
                val s = parseStartFn() ?: return@Button
                val bookings = hotelRows.mapNotNull { it.toBookingOrNull() }
                if (bookings.isEmpty()) {
                    vm.postError("Add at least one hotel with check-in, check-out (yyyy-MM-dd), and other required fields.")
                    return@Button
                }
                val primary = bookings.first()
                val extra = bookings.drop(1)
                val updated = buildMergedTrip(s, primary, extra)
                vm.upsertTrip(updated)
                val cleanPhones = phones.split(',', ';', '\n')
                    .map { it.filter { c -> c.isDigit() } }
                    .filter { it.isNotEmpty() }
                vm.setCustomerPhones(tripId, cleanPhones)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save all")
        }
        OutlinedButton(
            onClick = {
                val s = parseStartFn() ?: return@OutlinedButton
                val bookings = hotelRows.mapNotNull { it.toBookingOrNull() }
                if (bookings.isEmpty()) {
                    vm.postError("Add at least one valid hotel (dates as yyyy-MM-dd) before running AI.")
                    return@OutlinedButton
                }
                val primary = bookings.first()
                val extra = bookings.drop(1)
                val base = buildMergedTrip(s, primary, extra)
                vm.upsertTrip(base)
                vm.runItineraryLlm(base, renumberDays(dayRows).map { it.toLlmInput() }, dura)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Generate / refresh with AI (Groq)")
        }
    }
}

@Composable
private fun AdminNudgesPane(onBack: () -> Unit) {
    var title by remember { mutableStateOf("Priyatra Getaways") }
    var body by remember { mutableStateOf("") }
    var delayMin by remember { mutableStateOf("5") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton({ onBack() }) { Text("← Back") }
        Text("Notification nudges (this device)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (body.isNotBlank()) NotificationHelper.showNotification(context, title, body)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send now")
        }
        OutlinedTextField(delayMin, { delayMin = it }, label = { Text("Schedule in (minutes from now)") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = {
                val m = delayMin.toLongOrNull() ?: 0L
                if (m > 0L && body.isNotBlank()) {
                    val t = System.currentTimeMillis() + m * 60_000L
                    TripNotificationScheduler.scheduleAtMillis(context, t, title, body)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Schedule nudge (alarm)")
        }
    }
}

@Composable
private fun AdminMapPane(
    catalog: List<StoredTrip>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var tripId by remember(catalog) { mutableStateOf(catalog.firstOrNull()?.id.orEmpty()) }
    val locStore = remember { LocationReportStore(context) }
    val locs = if (tripId.isNotEmpty()) locStore.getAll(tripId) else emptyMap()
    val t = catalog.find { it.id == tripId }
    val p = t?.tripPackage
    val camera = rememberCameraPositionState {
        val lat = p?.hotel?.lat ?: 27.04
        val lng = p?.hotel?.lng ?: 88.26
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 10f)
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ onBack() }) { Text("← Back") }
        Text("Live guest pins (last update from customer map)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Select trip", style = MaterialTheme.typography.labelMedium)
        catalog.forEach { st ->
            val sel = st.id == tripId
            TextButton(onClick = { tripId = st.id }) {
                Text(if (sel) "· ${st.name}" else st.name, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            cameraPositionState = camera,
        ) {
            p?.let { pkg ->
                Marker(
                    state = rememberMarkerState(position = LatLng(pkg.hotel.lat, pkg.hotel.lng)),
                    title = "Primary hotel",
                )
                pkg.extraHotels.orEmpty().forEachIndexed { i, h ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(h.lat, h.lng)),
                        title = "Hotel ${i + 2}",
                    )
                }
            }
            locs.values.forEach { c ->
                Marker(
                    state = rememberMarkerState(position = LatLng(c.lat, c.lng)),
                    title = c.phone,
                    snippet = "Updated ${formatMillis(c.updatedAtMillis)}",
                )
            }
        }
    }
}

private fun formatMillis(m: Long): String =
    runCatching {
        val z = java.time.ZoneId.systemDefault()
        java.time.Instant.ofEpochMilli(m).atZone(z).toLocalTime().toString()
    }.getOrDefault("?")
