package com.priyatra.guide.data

import android.content.Context
import com.priyatra.guide.auth.SessionManager
import com.priyatra.guide.notifications.TripNotificationScheduler
import com.priyatra.guide.PriyaTraApplication
import com.priyatra.guide.data.db.LegacyDataImporter
import com.priyatra.guide.data.remote.CatalogCloudSync
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TripRepository {
    private var catalogTrips: List<StoredTrip> = emptyList()

    /** Bumps each time the trip catalog in memory (and Room) is reloaded. */
    private val _catalogRevision = MutableStateFlow(0L)
    val catalogRevision: StateFlow<Long> = _catalogRevision

    /** Shown when cloud is configured but trips are not loaded yet (avoids “Darjeeling” flash). */
    private fun createSyncPendingPackage(): TripPackage {
        val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        val day = LocalDate.now(zone)
        return TripPackage(
            title = "Loading your trips",
            zone = zone,
            destination = "",
            supportPhone = "",
            transports = emptyList(),
            drivers = emptyList(),
            hotel = HotelBooking(
                name = "—",
                address = "Open Desk (admin) and tap “Sync from shared cloud” if trips stay empty.",
                lat = 27.0410,
                lng = 88.2633,
                checkIn = day,
                checkOut = day,
                confirmation = "—",
                phone = "—",
            ),
            extraHotels = null,
            spots = emptyList(),
            days = listOf(
                DayPlan(
                    dayIndex = 1,
                    title = "Sync",
                    summary = "Your operator catalog loads from the cloud on first start.",
                    spotIds = emptyList(),
                ),
            ),
        )
    }

    private val _tripState: MutableStateFlow<TripPackage> = MutableStateFlow(createSyncPendingPackage())
    val tripState: StateFlow<TripPackage> = _tripState.asStateFlow()
    val trip: TripPackage get() = _tripState.value

    fun zone(): ZoneId = trip.zone

    val mealReminders: List<MealReminder> = listOf(
        MealReminder(MealKind.BREAKFAST, "Breakfast window", LocalTime.of(8, 0), LocalTime.of(9, 0)),
        MealReminder(MealKind.LUNCH, "Lunch window", LocalTime.of(13, 0), LocalTime.of(14, 0)),
        MealReminder(MealKind.SNACKS, "Evening snacks", LocalTime.of(18, 0), LocalTime.of(19, 0)),
        MealReminder(MealKind.DINNER, "Dinner window", LocalTime.of(21, 0), LocalTime.of(22, 0)),
    )

    /**
     * Old installs may still have the local demo trip [defaultSeedStored] in Room; once cloud is
     * enabled we remove it so the UI does not keep showing "Darjeeling Escape" over the real catalog.
     */
    private fun stripLocalDemoIfCloudEnabled(context: Context) {
        if (!CatalogCloudSync.isConfigured()) return
        val store = TripCatalogStore(context)
        val c = store.load()
        if (c.trips.none { it.id == "seed-darjeeling" }) return
        val next = c.trips.filter { it.id != "seed-darjeeling" }
        store.replaceLocal(TripsCatalogFile(trips = next))
    }

    fun init(context: Context) {
        val app = context.applicationContext
        LegacyDataImporter.importOnceIfNeeded(app)
        stripLocalDemoIfCloudEnabled(app)
        val store = TripCatalogStore(app)
        var file = store.load()
        // Do not write the Darjeeling seed before cloud when Supabase is enabled — the CI APK
        // and devices then pull the real catalog (e.g. East Sikkim) on first run.
        if (file.trips.isEmpty() && !CatalogCloudSync.isConfigured()) {
            file = TripsCatalogFile(trips = listOf(defaultSeedStored()))
            store.save(file)
        }
        catalogTrips = TripCatalogStore(app).load().trips
        refreshFromSession(app)
        (app as? PriyaTraApplication)?.applicationScope?.launch {
            CatalogCloudSync.runStartupSync(app)
        }
    }

    /**
     * Offline / no row on server: add the in-app sample trip (Darjeeling) only if still empty.
     * Never when Supabase is on — the operator catalog is the source of truth.
     */
    fun applyDefaultSeedIfEmpty(context: Context) {
        val app = context.applicationContext
        if (CatalogCloudSync.isConfigured()) return
        if (TripCatalogStore(app).load().trips.isNotEmpty()) return
        TripCatalogStore(app).save(TripsCatalogFile(trips = listOf(defaultSeedStored())))
        reloadCatalog(context)
    }

    fun reloadCatalog(context: Context) {
        val app = context.applicationContext
        catalogTrips = TripCatalogStore(app).load().trips
        refreshFromSession(app)
        TripNotificationScheduler.scheduleTripNotifications(context)
        _catalogRevision.value = _catalogRevision.value + 1L
    }

    fun listStoredTrips(context: Context): List<StoredTrip> {
        if (catalogTrips.isNotEmpty()) return catalogTrips
        return TripCatalogStore(context.applicationContext).load().trips
    }

    fun refreshFromSession(context: Context) {
        val sm = SessionManager(context)
        val pickId: String? = when {
            !sm.isLoggedIn() -> catalogTrips.firstOrNull()?.id
            sm.isAdmin() && sm.getPreviewTripId() != null -> sm.getPreviewTripId()
            sm.isAdmin() -> catalogTrips.firstOrNull()?.id
            else -> sm.getCustomerTripId() ?: catalogTrips.firstOrNull()?.id
        }
        val match = catalogTrips.find { it.id == pickId } ?: catalogTrips.firstOrNull()
        if (match != null) {
            _tripState.value = match.tripPackage
        } else {
            _tripState.value = if (CatalogCloudSync.isConfigured()) {
                createSyncPendingPackage()
            } else {
                createDefaultPocPackage()
            }
        }
    }

    fun defaultSeedStored(): StoredTrip {
        val pkg = createDefaultPocPackage()
        return StoredTrip(
            id = "seed-darjeeling",
            name = pkg.title,
            destination = pkg.destination,
            startDate = pkg.hotel.checkIn,
            durationDays = pkg.days.size,
            customerPhones = emptyList(),
            tripPackage = pkg,
        )
    }

    fun spotById(id: String): TravelSpot? = trip.spots.find { it.id == id }

    fun activeDriver(on: LocalDate = LocalDate.now(trip.zone)): DriverAssignment? =
        trip.drivers.firstOrNull { d ->
            !on.isBefore(d.activeFrom) && (d.activeUntil == null || !on.isAfter(d.activeUntil))
        }

    fun dayPlan(dayIndex: Int): DayPlan? = trip.days.find { it.dayIndex == dayIndex }

    fun spotsForDay(dayIndex: Int): List<TravelSpot> {
        val ids = dayPlan(dayIndex)?.spotIds ?: return emptyList()
        val order = ids.withIndex().associate { it.value to it.index }
        return trip.spots.filter { it.dayIndex == dayIndex }.sortedBy { order[it.id] ?: it.order }
    }

    /**
     * 1-based index into [TripPackage.days] for calendar dates that fall on the trip stay
     * (inclusive of check-in and check-out days in the stored hotel booking).
     */
    fun tripDayIndexForDate(date: LocalDate): Int? {
        val t = trip
        val start = t.hotel.checkIn
        val end = t.hotel.checkOut
        if (date.isBefore(start) || date.isAfter(end)) return null
        return ChronoUnit.DAYS.between(start, date).toInt() + 1
    }

    fun createDefaultPocPackage(): TripPackage {
        val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        val photoTiger = PhotoTip(
            viewpoint = "Tiger Hill sunrise deck",
            description = "Wide lens, horizon line low; capture Kanchenjunga first light.",
            exampleImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4f/Kanchenjunga_from_Tiger_Hill.jpg/960px-Kanchenjunga_from_Tiger_Hill.jpg",
            lat = 27.0258,
            lng = 88.2629,
        )
        val photoBatasia = PhotoTip(
            viewpoint = "Batasia Loop garden path",
            description = "Use the loop and toy train curve as a leading line.",
            exampleImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/Darjeeling_Himalayan_Railway_at_Batasia_Loop.jpg/960px-Darjeeling_Himalayan_Railway_at_Batasia_Loop.jpg",
            lat = 27.0414,
            lng = 88.2479,
        )
        val photoMall = PhotoTip(
            viewpoint = "Mall Road clock tower",
            description = "Blue hour after rain for reflections on the wet stone.",
            exampleImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/Darjeeling_town.jpg/960px-Darjeeling_town.jpg",
            lat = 27.0410,
            lng = 88.2633,
        )

        val spots = listOf(
            TravelSpot(
                id = "njp_pickup",
                name = "New Jalpaiguri Station pickup",
                dayIndex = 1,
                order = 1,
                lat = 26.6833,
                lng = 88.4456,
                history = "NJP is the main rail gateway to the Darjeeling hills; most hill journeys start here after the overnight train from Kolkata.",
                highlights = listOf(
                    "Meet driver at platform exit holding PriyaTra placard",
                    "Collect bottled water from the cab before the climb",
                ),
                reachabilityNote = "Car accessible; your chauffeured cab starts here.",
                trekOrLocalNote = null,
                foods = listOf("Hot tea from the station stall", "Light momos if you need a bite before the drive"),
                souvenirs = listOf("Not much at NJP — save shopping for Darjeeling Mall"),
                photoTips = listOf(
                    PhotoTip(
                        viewpoint = "Station forecourt",
                        description = "Document the start of the hill road with the cab number visible.",
                        exampleImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/New_Jalpaiguri_Junction_Railway_Station.jpg/960px-New_Jalpaiguri_Junction_Railway_Station.jpg",
                        lat = 26.6833,
                        lng = 88.4456,
                    ),
                ),
            ),
            TravelSpot(
                id = "hotel_checkin",
                name = "Summit Hermitage Hotel",
                dayIndex = 1,
                order = 2,
                lat = 27.0350,
                lng = 88.2580,
                history = "Colonial-era hill station hospitality; many properties blend British tea-planter aesthetics with Himalayan views.",
                highlights = listOf(
                    "Check-in with confirmation code",
                    "Short rest before Mall walk",
                ),
                reachabilityNote = "Hotel driveway accessible by car.",
                trekOrLocalNote = null,
                foods = listOf("Thukpa", "Darjeeling tea with ginger cookies"),
                souvenirs = listOf("Tea sampler packs from the hotel boutique"),
                photoTips = listOf(photoMall),
            ),
            TravelSpot(
                id = "mall_road",
                name = "Darjeeling Mall (Chowrasta)",
                dayIndex = 1,
                order = 3,
                lat = 27.0410,
                lng = 88.2633,
                history = "Chowrasta is a pedestrian-only hub from the Raj era; it is the social heart of Darjeeling with views of the eastern Himalaya on clear days.",
                highlights = listOf(
                    "Stroll the ridge",
                    "Visit Oxford Bookstore",
                ),
                reachabilityNote = "Vehicle-free core; short walk from drop-off points.",
                trekOrLocalNote = "Your driver will drop you at the nearest vehicular point (~3–6 minutes walk).",
                foods = listOf("Glenary’s pastries", "Local aloo dum"),
                souvenirs = listOf("Hand-knit woollens", "Tea from Nathmulls"),
                photoTips = listOf(photoMall),
            ),
            TravelSpot(
                id = "tiger_hill",
                name = "Tiger Hill sunrise",
                dayIndex = 2,
                order = 1,
                lat = 27.0258,
                lng = 88.2629,
                history = "Tiger Hill is among the most famous sunrise viewpoints in India; on clear mornings you see Kanchenjunga and, rarely, Everest.",
                highlights = listOf(
                    "Arrive before dawn; dress in layers",
                    "Carry a thermos — temperatures dip sharply",
                ),
                reachabilityNote = "Steep, narrow roads; 4WD vehicles recommended (pre-booked).",
                trekOrLocalNote = "Final approach is by jeep; some visitors walk short segments with a local guide — not required for this package.",
                foods = listOf("Hot black tea at the stalls", "Maggi at sunrise kiosks (POC menu)"),
                souvenirs = listOf("Hand-woven caps", "Postcards with Kanchenjunga"),
                photoTips = listOf(photoTiger),
            ),
            TravelSpot(
                id = "ghoom_monastery",
                name = "Ghoom Monastery (Yiga Choeling)",
                dayIndex = 2,
                order = 2,
                lat = 27.0295,
                lng = 88.2510,
                history = "Built in the 19th century, this monastery houses a revered Maitreya Buddha image and belongs to the Gelug school of Tibetan Buddhism.",
                highlights = listOf(
                    "Spin the prayer wheels clockwise",
                    "Observe monastery etiquette: quiet, no flash",
                ),
                reachabilityNote = "Road accessible; short stepped entry.",
                trekOrLocalNote = null,
                foods = listOf("Butter tea nearby stalls", "Vegetable momos"),
                souvenirs = listOf("Prayer flags", "Incense bundles"),
                photoTips = listOf(
                    PhotoTip(
                        viewpoint = "Monastery courtyard",
                        description = "Frame the stupa with prayer flags using a medium telephoto.",
                        exampleImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/Ghoom_Monastery%2C_Darjeeling.jpg/960px-Ghoom_Monastery%2C_Darjeeling.jpg",
                        lat = 27.0295,
                        lng = 88.2510,
                    ),
                ),
            ),
            TravelSpot(
                id = "batasia",
                name = "Batasia Loop",
                dayIndex = 2,
                order = 3,
                lat = 27.0414,
                lng = 88.2479,
                history = "This engineered spiral lowers the gradient of the Darjeeling Himalayan Railway; UNESCO-listed DHR trains loop dramatically here.",
                highlights = listOf(
                    "Gorkha War Memorial at the centre",
                    "Wait for a toy train pass for classic photos",
                ),
                reachabilityNote = "Car park nearby; gentle walks.",
                trekOrLocalNote = null,
                foods = listOf("Corn on the cob", "Chhurpi cheese snacks"),
                souvenirs = listOf("Mini toy train models", "Woollen gloves"),
                photoTips = listOf(photoBatasia),
            ),
            TravelSpot(
                id = "tea_estate",
                name = "Happy Valley Tea Estate",
                dayIndex = 3,
                order = 1,
                lat = 27.0467,
                lng = 88.2536,
                history = "One of the closest working tea gardens to town; Darjeeling tea is a protected GI and prized for its muscatel notes.",
                highlights = listOf(
                    "Factory tour slot (pre-arranged)",
                    "Learn orthodox vs CTC processing",
                ),
                reachabilityNote = "Estate roads are narrow; guided walking segments on slopes.",
                trekOrLocalNote = "Some terraces are footpaths only — follow the estate guide; ~400m gentle trek inside the garden.",
                foods = listOf("Fresh leaf tasting", "Light cucumber sandwiches at the tasting room"),
                souvenirs = listOf("First-flush loose leaf tins", "Tea infusers"),
                photoTips = listOf(
                    PhotoTip(
                        viewpoint = "Terraced rows mid-slope",
                        description = "Shoot along the contour lines in side-light after 3pm.",
                        exampleImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7e/Darjeeling_tea_garden.jpg/960px-Darjeeling_tea_garden.jpg",
                        lat = 27.0467,
                        lng = 88.2536,
                    ),
                ),
            ),
            TravelSpot(
                id = "rock_garden",
                name = "Rock Garden & Chunnu Falls",
                dayIndex = 3,
                order = 2,
                lat = 27.0530,
                lng = 88.2930,
                history = "Terraced rock pools and waterfalls developed as a picnic spot along the Lebong cart road; busy on weekends.",
                highlights = listOf(
                    "Wear grip-soled shoes — wet steps",
                    "Carry a light poncho",
                ),
                reachabilityNote = "Final stretch is steep with stairs; not ideal for low-mobility guests.",
                trekOrLocalNote = "Shared Sumos often ply the last leg; your package includes a local hop if the sedan cannot descend the final ramp (≈15 minutes).",
                foods = listOf("Roasted peanuts", "Seasonal plum candy"),
                souvenirs = listOf("Polished stone keepsakes", "Local honey jars"),
                photoTips = listOf(
                    PhotoTip(
                        viewpoint = "Lower cascade pool",
                        description = "Slow shutter 1/8–1/15s on a mini-tripod for silky water.",
                        exampleImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9d/Rock_Garden_Darjeeling_Waterfall.jpg/960px-Rock_Garden_Darjeeling_Waterfall.jpg",
                        lat = 27.0530,
                        lng = 88.2930,
                    ),
                ),
            ),
            TravelSpot(
                id = "zoo_himalayan",
                name = "Padmaja Naidu Himalayan Zoological Park",
                dayIndex = 4,
                order = 1,
                lat = 27.0570,
                lng = 88.2535,
                history = "Specialises in high-altitude species including snow leopard and red panda; a key conservation breeding centre.",
                highlights = listOf(
                    "Morning animals are more active",
                    "Stay on marked paths",
                ),
                reachabilityNote = "Sloped walkways; benches frequent.",
                trekOrLocalNote = null,
                foods = listOf("Cafeteria veg thali", "Packed picnic if you prefer"),
                souvenirs = listOf("Red panda plush toys", "Wildlife picture books"),
                photoTips = listOf(
                    PhotoTip(
                        viewpoint = "Red panda enclosure viewing deck",
                        description = "Continuous autofocus, higher ISO — subjects move quickly in dappled light.",
                        exampleImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/Red_Panda.JPG/960px-Red_Panda.JPG",
                        lat = 27.0570,
                        lng = 88.2535,
                    ),
                ),
            ),
            TravelSpot(
                id = "njp_return",
                name = "NJP departure for Kolkata train",
                dayIndex = 4,
                order = 2,
                lat = 26.6833,
                lng = 88.4456,
                history = "End of the hills sector; board your return service to Howrah.",
                highlights = listOf(
                    "Reach station 45 minutes early",
                    "Keep PNR handy for onboard checks",
                ),
                reachabilityNote = "Drop at station forecourt.",
                trekOrLocalNote = null,
                foods = listOf("Station canteen rice plate", "Packed snacks for overnight"),
                souvenirs = listOf("Last-minute tea from NJP kiosks"),
                photoTips = emptyList(),
            ),
        )

        val days = listOf(
            DayPlan(
                dayIndex = 1,
                title = "Arrival & ridge evening",
                summary = "NJP pickup, scenic drive to Darjeeling, hotel check-in, Mall stroll.",
                spotIds = listOf("njp_pickup", "hotel_checkin", "mall_road"),
            ),
            DayPlan(
                dayIndex = 2,
                title = "Sunrise & heritage loop",
                summary = "Tiger Hill dawn, Ghoom Monastery, Batasia Loop, back to hotel.",
                spotIds = listOf("tiger_hill", "ghoom_monastery", "batasia"),
            ),
            DayPlan(
                dayIndex = 3,
                title = "Tea & waterfalls",
                summary = "Tea estate experience, Rock Garden falls, leisure evening.",
                spotIds = listOf("tea_estate", "rock_garden"),
            ),
            DayPlan(
                dayIndex = 4,
                title = "Zoo & homeward",
                summary = "Himalayan Zoo morning, checkout, descend to NJP, evening train.",
                spotIds = listOf("zoo_himalayan", "njp_return"),
            ),
        )

        val outbound = TripTransport(
            id = "train_out",
            mode = TransportMode.TRAIN,
            title = "Howrah → New Jalpaiguri (sleeper)",
            from = "Howrah Jn (HWH)",
            to = "New Jalpaiguri Jn (NJP)",
            departure = LocalDateTime.of(2026, Month.MAY, 1, 20, 25),
            arrival = LocalDateTime.of(2026, Month.MAY, 2, 8, 15),
            pnr = "Priyatra-POC-7X9K2",
            coach = "B4",
            seat = "42, 43",
            trainNumber = "12343 (POC schedule)",
            pdfAssetPath = "sample_ticket.pdf",
        )
        val `return` = TripTransport(
            id = "train_return",
            mode = TransportMode.TRAIN,
            title = "New Jalpaiguri → Howrah (sleeper)",
            from = "New Jalpaiguri Jn (NJP)",
            to = "Howrah Jn (HWH)",
            departure = LocalDateTime.of(2026, Month.MAY, 5, 18, 5),
            arrival = LocalDateTime.of(2026, Month.MAY, 6, 5, 40),
            pnr = "Priyatra-POC-4L8Q1",
            coach = "A2",
            seat = "15, 16",
            trainNumber = "12344 (POC schedule)",
            pdfAssetPath = "sample_ticket.pdf",
        )

        val hotel = HotelBooking(
            name = "Summit Hermitage Hotel & Spa (POC)",
            address = "27/A, Hermitage Road, Darjeeling, WB 734101",
            lat = 27.0350,
            lng = 88.2580,
            checkIn = LocalDate.of(2026, Month.MAY, 2),
            checkOut = LocalDate.of(2026, Month.MAY, 5),
            confirmation = "PTR-HERM-2026-051",
            phone = "+913542250001",
        )

        val drivers = listOf(
            DriverAssignment(
                segmentLabel = "NJP pickup → Darjeeling hotel (May 2)",
                name = "Rajen Subba",
                phone = "+919876543210",
                activeFrom = LocalDate.of(2026, Month.MAY, 2),
                activeUntil = LocalDate.of(2026, Month.MAY, 2),
            ),
            DriverAssignment(
                segmentLabel = "Local Darjeeling tours (May 3–4)",
                name = "Pemba Sherpa",
                phone = "+919811122334",
                activeFrom = LocalDate.of(2026, Month.MAY, 3),
                activeUntil = LocalDate.of(2026, Month.MAY, 4),
            ),
            DriverAssignment(
                segmentLabel = "Hotel checkout → NJP drop (May 5)",
                name = "Rajen Subba",
                phone = "+919876543210",
                activeFrom = LocalDate.of(2026, Month.MAY, 5),
                activeUntil = LocalDate.of(2026, Month.MAY, 5),
            ),
        )

        return TripPackage(
            title = "Darjeeling Escape · 3 nights / 4 days",
            zone = zone,
            destination = "Darjeeling, West Bengal",
            supportPhone = "+918000123456",
            transports = listOf(outbound, `return`),
            drivers = drivers,
            hotel = hotel,
            extraHotels = null,
            spots = spots,
            days = days,
        )
    }
}
