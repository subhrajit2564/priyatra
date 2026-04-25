package com.priyatra.guide.data.db

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.priyatra.guide.data.TripJson
import com.priyatra.guide.data.TripsCatalogFile
import com.priyatra.guide.location.CustomerLocationSnapshot
import java.io.File

/**
 * One-time import from the previous JSON file and SharedPreferences into Room.
 */
object LegacyDataImporter {
    private const val MIGRATION_FLAG_KEY = "migrated_priyatra_v1"
    private const val LEGACY_TRIPS_FILE = "priyatra_trips.json"
    private const val LEGACY_TRIPS_BAK = "priyatra_trips.json.bak"
    private const val PREFS_FEEDBACK = "priyatra_feedback"
    private const val PREFS_LOCS = "priyatra_locs"

    fun importOnceIfNeeded(context: Context) {
        val db = PriyaTraDatabase.getInstance(context)
        if (db.settingsDao().getValue(MIGRATION_FLAG_KEY) != null) return

        if (db.tripDao().count() == 0) {
            val f = File(context.filesDir, LEGACY_TRIPS_FILE)
            if (f.exists() && f.length() > 0) {
                val catalog = runCatching {
                    val text = f.readText()
                    if (text.isBlank()) null else TripJson.gson.fromJson(text, TripsCatalogFile::class.java)
                }.getOrNull() ?: TripsCatalogFile()
                if (catalog.trips.isNotEmpty()) {
                    db.tripDao().replaceCatalog(catalog.trips)
                }
                f.renameTo(File(context.filesDir, LEGACY_TRIPS_BAK))
            }
        }

        val fb = context.getSharedPreferences(PREFS_FEEDBACK, Context.MODE_PRIVATE)
        if (fb.all.isNotEmpty()) {
            for ((k, v) in fb.all) {
                if (!k.endsWith("_stars")) continue
                val spotId = k.removeSuffix("_stars")
                if (db.feedbackDao().get(spotId) != null) continue
                val stars = (v as? Number)?.toInt() ?: continue
                if (stars !in 1..5) continue
                val note = fb.getString("${spotId}_note", "") ?: ""
                db.feedbackDao()
                    .upsert(SpotFeedbackEntity(spotId, stars, note))
            }
            fb.edit().clear().apply()
        }

        val locPrefs = context.getSharedPreferences(PREFS_LOCS, Context.MODE_PRIVATE)
        if (db.locationDao().countAll() == 0 && locPrefs.all.isNotEmpty()) {
            val mapType = object : TypeToken<Map<String, CustomerLocationSnapshot>>() {}.type
            for ((k, v) in locPrefs.all) {
                if (!k.startsWith("trip_locs_")) continue
                val tripId = k.removePrefix("trip_locs_")
                val raw = v as? String ?: continue
                val map: Map<String, CustomerLocationSnapshot> = runCatching {
                    TripJson.gson.fromJson<Map<String, CustomerLocationSnapshot>>(raw, mapType)
                }.getOrNull().orEmpty()
                for ((keyPhone, snap) in map) {
                    val digits = com.priyatra.guide.auth.PhoneUtils.normalize(
                        if (keyPhone.isNotBlank()) keyPhone else snap.phone,
                    )
                    if (digits.isEmpty()) continue
                    db.locationDao().upsert(
                        LocationSnapshotEntity(
                            tripId = tripId,
                            phoneDigits = digits,
                            lat = snap.lat,
                            lng = snap.lng,
                            updatedAtMillis = snap.updatedAtMillis,
                        ),
                    )
                }
            }
            locPrefs.edit().clear().apply()
        }

        db.settingsDao()
            .set(AppSettingEntity(MIGRATION_FLAG_KEY, "1"))
    }
}
