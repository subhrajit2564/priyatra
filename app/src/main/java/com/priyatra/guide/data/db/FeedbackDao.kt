package com.priyatra.guide.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM spot_feedback WHERE spot_id = :spotId")
    fun get(spotId: String): SpotFeedbackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(e: SpotFeedbackEntity)
}
