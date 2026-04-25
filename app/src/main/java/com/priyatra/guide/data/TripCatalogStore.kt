package com.priyatra.guide.data

import android.content.Context
import java.io.File

class TripCatalogStore(context: Context) {
    private val file = File(context.filesDir, "priyatra_trips.json")

    fun load(): TripsCatalogFile = runCatching {
        if (!file.exists()) return TripsCatalogFile()
        val text = file.readText()
        if (text.isBlank()) return TripsCatalogFile()
        TripJson.gson.fromJson(text, TripsCatalogFile::class.java) ?: TripsCatalogFile()
    }.getOrElse { TripsCatalogFile() }

    fun save(catalog: TripsCatalogFile) {
        file.writeText(TripJson.gson.toJson(catalog))
    }
}
