package com.priyatra.guide.llm

import com.priyatra.guide.data.DayPlan
import com.priyatra.guide.data.PhotoTip
import com.priyatra.guide.data.TravelSpot

internal data class LlmPlanRoot(
    val title: String? = null,
    val destination: String? = null,
    val spots: List<LlmSpotDto> = emptyList(),
    val days: List<LlmDayDto> = emptyList(),
)

internal data class LlmDayDto(
    val dayIndex: Int = 0,
    val title: String = "",
    val summary: String = "",
    val spotIds: List<String> = emptyList(),
)

internal data class LlmPhotoTipDto(
    val viewpoint: String = "",
    val description: String = "",
    val exampleImageUrl: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

internal data class LlmSpotDto(
    val id: String = "",
    val name: String = "",
    val dayIndex: Int = 0,
    val order: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val history: String = "",
    val highlights: List<String> = emptyList(),
    val reachabilityNote: String? = null,
    val trekOrLocalNote: String? = null,
    val foods: List<String> = emptyList(),
    val souvenirs: List<String> = emptyList(),
    val photoTips: List<LlmPhotoTipDto> = emptyList(),
)

data class DayAdminInput(
    val dayIndex: Int,
    val driverPocName: String,
    val driverPocPhone: String,
    val hotelName: String,
    val hotelAddress: String,
    val hotelLat: String,
    val hotelLng: String,
    val hotelPocPhone: String,
    val stopNotes: String,
)

internal fun LlmSpotDto.toTravelSpot() = TravelSpot(
    id = id.ifBlank { "spot_${(name + dayIndex).hashCode()}" },
    name = name,
    dayIndex = dayIndex,
    order = order,
    lat = lat,
    lng = lng,
    history = history,
    highlights = highlights,
    reachabilityNote = reachabilityNote,
    trekOrLocalNote = trekOrLocalNote,
    foods = foods,
    souvenirs = souvenirs,
    photoTips = photoTips.map { p ->
        PhotoTip(
            viewpoint = p.viewpoint,
            description = p.description,
            exampleImageUrl = p.exampleImageUrl?.trim().orEmpty(),
            lat = p.lat,
            lng = p.lng,
        )
    },
)

internal fun LlmDayDto.toDayPlan(merge: DayAdminInput) = DayPlan(
    dayIndex = dayIndex,
    title = title,
    summary = summary,
    spotIds = spotIds,
    driverPocName = merge.driverPocName,
    driverPocPhone = merge.driverPocPhone,
    hotelName = merge.hotelName,
    hotelAddress = merge.hotelAddress,
    hotelLat = merge.hotelLat.toDoubleOrNull(),
    hotelLng = merge.hotelLng.toDoubleOrNull(),
    hotelPocPhone = merge.hotelPocPhone,
)
