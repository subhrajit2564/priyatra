package com.priyatra.guide.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class TransportMode { TRAIN, FLIGHT, BUS }

data class TripTransport(
    val id: String,
    val mode: TransportMode,
    val title: String,
    val from: String,
    val to: String,
    val departure: LocalDateTime,
    val arrival: LocalDateTime,
    val pnr: String,
    val coach: String?,
    val seat: String?,
    val trainNumber: String?,
    val pdfAssetPath: String,
)

data class DriverAssignment(
    val segmentLabel: String,
    val name: String,
    val phone: String,
    val activeFrom: LocalDate,
    val activeUntil: LocalDate?,
)

data class HotelBooking(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val confirmation: String,
    val phone: String,
)

data class PhotoTip(
    val viewpoint: String,
    val description: String,
    val exampleImageUrl: String,
    val lat: Double,
    val lng: Double,
)

data class TravelSpot(
    val id: String,
    val name: String,
    val dayIndex: Int,
    val order: Int,
    val lat: Double,
    val lng: Double,
    val history: String,
    val highlights: List<String>,
    val reachabilityNote: String?,
    val trekOrLocalNote: String?,
    val foods: List<String>,
    val souvenirs: List<String>,
    val photoTips: List<PhotoTip>,
)

data class DayPlan(
    val dayIndex: Int,
    val title: String,
    val summary: String,
    val spotIds: List<String>,
    /** Admin-editable: driver point of contact */
    val driverPocName: String = "",
    val driverPocPhone: String = "",
    /** Admin-editable: primary hotel for this day (LLM fills other copy) */
    val hotelName: String = "",
    val hotelAddress: String = "",
    val hotelLat: Double? = null,
    val hotelLng: Double? = null,
    val hotelPocPhone: String = "",
)

data class TripPackage(
    val title: String,
    val zone: ZoneId,
    /** Shown in customer home; used by admin + LLM (separate from title). */
    val destination: String = "",
    val supportPhone: String,
    val transports: List<TripTransport>,
    val drivers: List<DriverAssignment>,
    /** Primary property for map, nudges, and legacy code paths. */
    val hotel: HotelBooking,
    /** Additional stay blocks (e.g. multi-city). Map / services may use [hotel] first. */
    val extraHotels: List<HotelBooking>? = null,
    val spots: List<TravelSpot>,
    val days: List<DayPlan>,
)

enum class MealKind { BREAKFAST, LUNCH, SNACKS, DINNER }

data class MealReminder(
    val kind: MealKind,
    val label: String,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
)
