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

    /**
     * Writes catalog to Room only. Use when the shared Supabase row is the source of truth
     * (e.g. removing the local Darjeeling demo) — [save] also pushes, which can race with startup
     * pull and **overwrite the cloud** with an empty or stale catalog.
     */
    fun replaceLocal(catalog: TripsCatalogFile) {
        db.tripDao().replaceCatalog(catalog.trips)
    }

    fun save(catalog: TripsCatalogFile) {
        db.tripDao().replaceCatalog(catalog.trips)
        CatalogCloudSync.requestPushAfterLocalSave(app)
    }
}
