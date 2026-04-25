package com.priyatra.guide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ensures [TripsCatalogFile] JSON from Supabase deserializes with [TripJson.gson]
 * (same path as [com.priyatra.guide.data.remote.CatalogCloudSync.applyToRoom]).
 */
class CatalogGsonParseTest {

    @Test
    fun parseCloudCatalogFixture() {
        val json = javaClass.getResourceAsStream("/cloud_catalog_fixture.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Missing /cloud_catalog_fixture.json — add from Supabase priyatra_state.catalog_json")
        val catalog = TripJson.gson.fromJson(json, TripsCatalogFile::class.java)
            ?: error("Gson returned null")
        assertTrue("fixture should have at least one trip", catalog.trips.isNotEmpty())
        val t = catalog.trips.first()
        assertTrue(t.id.isNotBlank())
        assertTrue(t.name.isNotBlank())
        assertTrue(t.tripPackage.title.isNotBlank())
        assertTrue(t.tripPackage.days.isNotEmpty())
        assertTrue(t.tripPackage.spots.isNotEmpty())
    }
}
