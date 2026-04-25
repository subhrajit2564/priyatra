package com.priyatra.guide.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class TripWithPhonesRow(
    @Embedded val trip: TripEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "trip_id",
        entity = CustomerPhoneEntity::class,
    )
    val phones: List<CustomerPhoneEntity>,
)
