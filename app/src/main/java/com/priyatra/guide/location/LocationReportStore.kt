package com.priyatra.guide.location

import android.content.Context
import com.priyatra.guide.auth.PhoneUtils
import com.priyatra.guide.data.db.LocationSnapshotEntity
import com.priyatra.guide.data.db.PriyaTraDatabase

/**
 * When customers share location during a trip, we persist last known coordinates in Room
 * so the admin map (same app, admin session) can read them.
 */
data class CustomerLocationSnapshot(
    val lat: Double,
    val lng: Double,
    val updatedAtMillis: Long,
    val phone: String,
)

class LocationReportStore(context: Context) {
    private val app = context.applicationContext
    private val locationDao get() = PriyaTraDatabase.getInstance(app).locationDao()

    fun put(tripId: String, phone: String, lat: Double, lng: Double) {
        val digits = PhoneUtils.normalize(phone)
        if (digits.isEmpty()) return
        locationDao.upsert(
            LocationSnapshotEntity(
                tripId = tripId,
                phoneDigits = digits,
                lat = lat,
                lng = lng,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun getAll(tripId: String): Map<String, CustomerLocationSnapshot> =
        locationDao.forTrip(tripId).associate { e ->
            e.phoneDigits to CustomerLocationSnapshot(
                lat = e.lat,
                lng = e.lng,
                updatedAtMillis = e.updatedAtMillis,
                phone = e.phoneDigits,
            )
        }

    fun clear(tripId: String) = locationDao.clearForTrip(tripId)
}
