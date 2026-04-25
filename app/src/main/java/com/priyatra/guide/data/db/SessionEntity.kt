package com.priyatra.guide.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_session")
data class SessionEntity(
    @PrimaryKey val id: Int = 1,
    val phone: String?,
    @ColumnInfo(name = "is_admin") val isAdmin: Boolean,
    @ColumnInfo(name = "customer_trip_id") val customerTripId: String?,
    @ColumnInfo(name = "preview_trip_id") val previewTripId: String?,
)
