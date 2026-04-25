package com.priyatra.guide.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val destination: String,
    @ColumnInfo(name = "start_date_iso") val startDateIso: String,
    @ColumnInfo(name = "duration_days") val durationDays: Int,
    /** Full [com.priyatra.guide.data.TripPackage] as JSON. */
    @ColumnInfo(name = "package_json") val packageJson: String,
)
