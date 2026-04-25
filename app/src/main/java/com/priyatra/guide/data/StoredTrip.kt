package com.priyatra.guide.data

import java.time.LocalDate
import java.util.UUID

data class TripsCatalogFile(
    val trips: List<StoredTrip> = emptyList(),
)

/**
 * One row in the admin catalog. [tripPackage] is the full customer-facing model;
 * the extra top-level fields power admin forms and login assignment.
 */
data class StoredTrip(
    val id: String,
    val name: String,
    val destination: String,
    val startDate: LocalDate,
    val durationDays: Int,
    val customerPhones: List<String> = emptyList(),
    val tripPackage: TripPackage,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
