package com.priyatra.guide.admin

import com.priyatra.guide.data.DayPlan
import com.priyatra.guide.data.HotelBooking
import com.priyatra.guide.data.StoredTrip
import com.priyatra.guide.data.TripPackage
import com.priyatra.guide.data.TravelSpot
import com.priyatra.guide.llm.DayAdminInput
import java.time.LocalDate

data class DayFormData(
    val dayIndex: Int,
    val title: String = "",
    /** Stops, food ideas, and operator notes (fed to the LLM as context). */
    val summary: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val hotelName: String = "",
    val hotelAddress: String = "",
    val hotelLat: String = "",
    val hotelLng: String = "",
    val hotelPoc: String = "",
) {
    fun toLlmInput() = DayAdminInput(
        dayIndex = dayIndex,
        driverPocName = driverName,
        driverPocPhone = driverPhone,
        hotelName = hotelName,
        hotelAddress = hotelAddress,
        hotelLat = hotelLat,
        hotelLng = hotelLng,
        hotelPocPhone = hotelPoc,
        stopNotes = summary,
    )
}

data class HotelFormRow(
    val name: String = "",
    val address: String = "",
    val lat: String = "",
    val lng: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val confirmation: String = "",
    val phone: String = "",
) {
    fun toBookingOrNull(): HotelBooking? {
        val ci = runCatching { LocalDate.parse(checkIn) }.getOrNull() ?: return null
        val co = runCatching { LocalDate.parse(checkOut) }.getOrNull() ?: return null
        return HotelBooking(
            name = name,
            address = address,
            lat = lat.toDoubleOrNull() ?: 0.0,
            lng = lng.toDoubleOrNull() ?: 0.0,
            checkIn = ci,
            checkOut = co,
            confirmation = confirmation,
            phone = phone,
        )
    }
}

fun HotelBooking.toFormRow(): HotelFormRow = HotelFormRow(
    name = name,
    address = address,
    lat = lat.toString(),
    lng = lng.toString(),
    checkIn = checkIn.toString(),
    checkOut = checkOut.toString(),
    confirmation = confirmation,
    phone = phone,
)

/** One line per destination segment, or " · " joined (from older trips). */
fun parseDestinationList(raw: String): List<String> {
    if (raw.isBlank()) return listOf("")
    val parts = raw.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    return if (parts.isNotEmpty()) parts else listOf(raw.trim())
}

fun joinDestinationList(parts: List<String>): String =
    parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")

fun hotelRowsFromTrip(t: StoredTrip): List<HotelFormRow> {
    val extra = t.tripPackage.extraHotels.orEmpty()
    return listOf(t.tripPackage.hotel.toFormRow()) + extra.map { it.toFormRow() }
}

fun dayFormsFromTrip(t: StoredTrip): List<DayFormData> {
    val list = t.tripPackage.days
        .sortedBy { it.dayIndex }
        .map { d ->
            DayFormData(
                dayIndex = d.dayIndex,
                title = d.title,
                summary = d.summary,
                driverName = d.driverPocName,
                driverPhone = d.driverPocPhone,
                hotelName = d.hotelName,
                hotelAddress = d.hotelAddress,
                hotelLat = d.hotelLat?.toString().orEmpty(),
                hotelLng = d.hotelLng?.toString().orEmpty(),
                hotelPoc = d.hotelPocPhone,
            )
        }
    if (list.isEmpty()) {
        return listOf(
            DayFormData(
                dayIndex = 1,
                title = "Day 1",
                summary = "Add stops, food, and notes, or use AI to draft.",
            ),
        )
    }
    return list
}

object AdminFormMerge {
    fun apply(
        base: StoredTrip,
        name: String,
        destination: String,
        start: LocalDate,
        duration: Int,
        supportPhone: String,
        days: List<DayFormData>,
        primaryHotel: HotelBooking,
        extraHotels: List<HotelBooking>,
    ): StoredTrip {
        val old = base.tripPackage
        val oldByOrder = old.days.sortedBy { it.dayIndex }
        val newOrder = days.sortedBy { it.dayIndex }
        val mergedDays: List<DayPlan> = newOrder.mapIndexed { i, f ->
            val oldDay = oldByOrder.getOrNull(i)
            DayPlan(
                dayIndex = f.dayIndex,
                title = f.title,
                summary = f.summary,
                spotIds = oldDay?.spotIds.orEmpty(),
                driverPocName = f.driverName,
                driverPocPhone = f.driverPhone,
                hotelName = f.hotelName,
                hotelAddress = f.hotelAddress,
                hotelLat = f.hotelLat.toDoubleOrNull(),
                hotelLng = f.hotelLng.toDoubleOrNull(),
                hotelPocPhone = f.hotelPoc,
            )
        }
        val newSpots = reassignSpotsToDays(old.spots, mergedDays)
        val newPkg = old.copy(
            title = name,
            destination = destination,
            supportPhone = supportPhone,
            hotel = primaryHotel,
            extraHotels = extraHotels.takeIf { it.isNotEmpty() },
            days = mergedDays,
            spots = newSpots,
        )
        return base.copy(
            name = name,
            destination = destination,
            startDate = start,
            durationDays = duration,
            tripPackage = newPkg,
        )
    }

    private fun reassignSpotsToDays(
        allSpots: List<TravelSpot>,
        mergedDays: List<DayPlan>,
    ): List<TravelSpot> {
        val referenced = mergedDays.flatMap { it.spotIds }.toSet()
        if (referenced.isEmpty()) return allSpots
        return allSpots.mapNotNull { s ->
            if (s.id !in referenced) return@mapNotNull null
            val day = mergedDays.find { d -> s.id in d.spotIds } ?: return@mapNotNull null
            s.copy(dayIndex = day.dayIndex)
        }
    }
}
