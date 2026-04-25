package com.priyatra.guide.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "location_snapshots",
    primaryKeys = ["trip_id", "phone_digits"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trip_id"])],
)
data class LocationSnapshotEntity(
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "phone_digits") val phoneDigits: String,
    val lat: Double,
    val lng: Double,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)
