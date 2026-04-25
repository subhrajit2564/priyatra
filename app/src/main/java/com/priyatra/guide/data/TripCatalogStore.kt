package com.priyatra.guide.data

import android.content.Context
import com.priyatra.guide.data.db.PriyaTraDatabase
import com.priyatra.guide.data.db.TripEntityMappers
import com.priyatra.guide.data.remote.CatalogCloudSync

/**
 * App catalog: trips, packages, and **customer phone numbers** in Room, with optional
 * Optional `CatalogCloudSync` push to Supabase when `SUPABASE_*` is set in `local.properties`. Legacy `priyatra_trips.json`
 * is imported once on upgrade.
 */
class TripCatalogStore(context: Context) {
    private val app = context.applicationContext
    private val db get() = PriyaTraDatabase.getInstance(app)

    fun load(): TripsCatalogFile = TripsCatalogFile(
        trips = db.tripDao().getAllWithPhones().map { TripEntityMappers.toStoredTrip(it) },
    )

    fun save(catalog: TripsCatalogFile) {
        db.tripDao().replaceCatalog(catalog.trips)
        CatalogCloudSync.requestPushAfterLocalSave(app)
    }
}
