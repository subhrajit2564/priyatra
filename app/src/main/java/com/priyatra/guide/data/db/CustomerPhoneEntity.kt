package com.priyatra.guide.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "customer_phones",
    primaryKeys = ["trip_id", "phone_digits"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trip_id"]),
        Index(value = ["phone_digits"]),
    ],
)
data class CustomerPhoneEntity(
    @ColumnInfo(name = "trip_id") val tripId: String,
    /** [com.priyatra.guide.auth.PhoneUtils.normalize] digits for matching. */
    @ColumnInfo(name = "phone_digits") val phoneDigits: String,
)
