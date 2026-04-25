package com.priyatra.guide.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spot_feedback")
data class SpotFeedbackEntity(
    @PrimaryKey @ColumnInfo(name = "spot_id") val spotId: String,
    val stars: Int,
    val note: String,
)
