package com.priyatra.guide.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.priyatra.guide.data.StoredTrip
import com.priyatra.guide.data.TripCatalogStore
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.data.TripsCatalogFile
import com.priyatra.guide.data.HotelBooking
import com.priyatra.guide.auth.PhoneUtils
import com.priyatra.guide.llm.DayAdminInput
import com.priyatra.guide.llm.GroqItineraryClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import com.priyatra.guide.data.TripPackage
import com.priyatra.guide.data.DriverAssignment
import java.time.LocalDate

class AdminViewModel(
    private val app: Application,
) : AndroidViewModel(app) {

    private val itineraryLlm = GroqItineraryClient()
    private val _catalog = MutableStateFlow<List<StoredTrip>>(emptyList())
    val catalog: StateFlow<List<StoredTrip>> = _catalog.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _llmInfo = MutableStateFlow<String?>(null)
    val llmInfo: StateFlow<String?> = _llmInfo.asStateFlow()

    fun clearLlmInfo() {
        _llmInfo.value = null
    }

    init {
        refreshList()
    }

    fun clearError() {
        _error.value = null
    }

    fun postError(message: String) {
        _error.value = message
    }

    fun refreshList() {
        _catalog.value = TripCatalogStore(app).load().trips
    }

    fun upsertTrip(trip: StoredTrip) {
        val store = TripCatalogStore(app)
        val c = store.load()
        val list = c.trips.filter { it.id != trip.id } + trip
        store.save(TripsCatalogFile(trips = list))
        refreshList()
        TripRepository.reloadCatalog(app)
    }

    fun deleteTrip(id: String) {
        val store = TripCatalogStore(app)
        val c = store.load()
        val list = c.trips.filter { it.id != id }
        store.save(TripsCatalogFile(trips = list))
        refreshList()
        TripRepository.reloadCatalog(app)
    }

    fun setCustomerPhones(tripId: String, rawPhones: List<String>) {
        val clean = rawPhones
            .map { PhoneUtils.normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        val store = TripCatalogStore(app)
        val c = store.load()
        val t = c.trips.find { it.id == tripId } ?: return
        val next = t.copy(
            customerPhones = clean,
            tripPackage = t.tripPackage,
        )
        upsertTrip(next)
    }

    fun runItineraryLlm(
        trip: StoredTrip,
        dayInputs: List<DayAdminInput>,
        durationDaysOverride: Int? = null,
    ) {
        if (dayInputs.isEmpty()) {
            _error.value = "Add at least one day before generating."
            return
        }
        val duration = durationDaysOverride ?: trip.durationDays
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            _llmInfo.value = "Calling AI (Groq)…"
            val hotel = trip.tripPackage.hotel
            val xHotels = trip.tripPackage.extraHotels.orEmpty()
            val result = itineraryLlm.generatePackage(
                tripName = trip.name,
                destination = trip.destination,
                startDate = trip.startDate,
                durationDays = duration,
                hotel = hotel,
                days = dayInputs,
            )
            _busy.value = false
            _llmInfo.value = null
            result.fold(
                onSuccess = { pkg ->
                    val merged = pkg.copy(
                        title = trip.name,
                        destination = trip.destination,
                        hotel = recomputeHotel(hotel, trip.startDate, duration),
                        extraHotels = xHotels.takeIf { it.isNotEmpty() },
                    )
                    val withDrivers = merged.copy(
                        drivers = buildDriversFromDays(merged, trip.startDate, duration),
                    )
                    val updated = trip.copy(
                        durationDays = duration,
                        tripPackage = withDrivers,
                    )
                    upsertTrip(updated)
                    _llmInfo.value = "Itinerary updated. Review the day list and press Save to persist any manual tweaks."
                },
                onFailure = { e ->
                    _error.value = e.message ?: "AI request failed"
                },
            )
        }
    }

    private fun recomputeHotel(h: HotelBooking, start: LocalDate, durationDays: Int): HotelBooking {
        val end = start.plusDays((durationDays - 1).toLong().coerceAtLeast(0))
        return h.copy(checkIn = start, checkOut = end)
    }

    private fun buildDriversFromDays(pkg: TripPackage, start: LocalDate, durationDays: Int): List<DriverAssignment> {
        return pkg.days.map { d ->
            val dayDate = start.plusDays((d.dayIndex - 1).toLong())
            DriverAssignment(
                segmentLabel = "Day ${d.dayIndex}: ${d.title}",
                name = d.driverPocName.ifBlank { "Driver" },
                phone = d.driverPocPhone.ifBlank { "+910000000000" },
                activeFrom = dayDate,
                activeUntil = dayDate,
            )
        }
    }

    /** Create an editable skeleton trip and persist it, then open the editor. */
    fun addDraftTrip(): String {
        val start = LocalDate.now()
        val h = HotelBooking(
            name = "Hotel (edit in form)",
            address = "Address TBD",
            lat = 27.0410,
            lng = 88.2633,
            checkIn = start,
            checkOut = start.plusDays(2),
            confirmation = "CONF-PENDING",
            phone = "+910000000000",
        )
        val t = buildManualPlaceholderTrip(
            name = "New trip",
            destination = "Destination TBD",
            start = start,
            duration = 3,
            supportPhone = "+919876543210",
            hotel = h,
        )
        upsertTrip(t)
        return t.id
    }

    fun buildManualPlaceholderTrip(
        name: String,
        destination: String,
        start: LocalDate,
        duration: Int,
        supportPhone: String,
        hotel: HotelBooking,
    ): StoredTrip {
        val zone = ZoneId.of("Asia/Kolkata")
        val days = (1..duration).map { i ->
            com.priyatra.guide.data.DayPlan(
                dayIndex = i,
                title = "Day $i",
                summary = "Add stops or run AI to fill details.",
                spotIds = listOf("placeholder_${i}_a"),
            )
        }
        val spots = (1..duration).map { i ->
            com.priyatra.guide.data.TravelSpot(
                id = "placeholder_${i}_a",
                name = "Day $i — set stops",
                dayIndex = i,
                order = 1,
                lat = if (i == 1) hotel.lat else hotel.lat,
                lng = if (i == 1) hotel.lng else hotel.lng,
                history = "Generated placeholder — use Edit or AI to replace.",
                highlights = listOf("Update this stop"),
                reachabilityNote = null,
                trekOrLocalNote = null,
                foods = listOf("Ask operator for local picks"),
                souvenirs = emptyList(),
                photoTips = emptyList(),
            )
        }
        val pkg = TripPackage(
            title = name,
            zone = zone,
            destination = destination,
            supportPhone = supportPhone,
            transports = emptyList(),
            drivers = emptyList(),
            hotel = recomputeHotel(hotel, start, duration),
            extraHotels = null,
            spots = spots,
            days = days,
        )
        return StoredTrip(
            id = UUID.randomUUID().toString(),
            name = name,
            destination = destination,
            startDate = start,
            durationDays = duration,
            customerPhones = emptyList(),
            tripPackage = pkg,
        )
    }
}
