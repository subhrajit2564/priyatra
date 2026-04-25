package com.priyatra.guide.data.db

import com.priyatra.guide.data.StoredTrip
import com.priyatra.guide.data.TripJson
import com.priyatra.guide.data.TripPackage
import java.time.LocalDate

object TripEntityMappers {
    fun toEntity(t: StoredTrip): TripEntity = TripEntity(
        id = t.id,
        name = t.name,
        destination = t.destination,
        startDateIso = t.startDate.toString(),
        durationDays = t.durationDays,
        packageJson = TripJson.gson.toJson(t.tripPackage),
    )

    fun toPhoneRows(t: StoredTrip): List<CustomerPhoneEntity> =
        t.customerPhones.map { CustomerPhoneEntity(t.id, it) }

    fun toStoredTrip(trip: TripEntity, phoneRows: List<CustomerPhoneEntity>): StoredTrip {
        val pkg = TripJson.gson.fromJson(trip.packageJson, TripPackage::class.java)
        return StoredTrip(
            id = trip.id,
            name = trip.name,
            destination = trip.destination,
            startDate = LocalDate.parse(trip.startDateIso),
            durationDays = trip.durationDays,
            customerPhones = phoneRows.map { it.phoneDigits },
            tripPackage = pkg,
        )
    }

    fun toStoredTrip(row: TripWithPhonesRow): StoredTrip = toStoredTrip(row.trip, row.phones)
}
