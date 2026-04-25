package com.priyatra.guide.location

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.priyatra.guide.data.TripJson

/**
 * When customers share location during a trip, we persist last known coordinates so the
 * admin map (same app, admin session) can read them. This is on-device only (POC);
 * production would use a server.
 */
data class CustomerLocationSnapshot(
    val lat: Double,
    val lng: Double,
    val updatedAtMillis: Long,
    val phone: String,
)

class LocationReportStore(context: Context) {
    private val prefs = context.getSharedPreferences("priyatra_locs", Context.MODE_PRIVATE)
    private val mapType = object : TypeToken<Map<String, CustomerLocationSnapshot>>() {}.type

    private fun key(tripId: String) = "trip_locs_$tripId"

    fun put(tripId: String, phone: String, lat: Double, lng: Double) {
        val all = getAll(tripId).toMutableMap()
        all[phone] = CustomerLocationSnapshot(lat, lng, System.currentTimeMillis(), phone)
        prefs.edit().putString(key(tripId), TripJson.gson.toJson(all, mapType)).apply()
    }

    fun getAll(tripId: String): Map<String, CustomerLocationSnapshot> {
        val raw = prefs.getString(key(tripId), null) ?: return emptyMap()
        return runCatching {
            TripJson.gson.fromJson<Map<String, CustomerLocationSnapshot>>(raw, mapType)
        }.getOrNull().orEmpty()
    }

    fun clear(tripId: String) {
        prefs.edit().remove(key(tripId)).apply()
    }
}
