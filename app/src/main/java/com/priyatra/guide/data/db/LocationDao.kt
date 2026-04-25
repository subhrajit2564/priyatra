package com.priyatra.guide.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(e: LocationSnapshotEntity)

    @Query("SELECT * FROM location_snapshots WHERE trip_id = :tid")
    fun forTrip(tid: String): List<LocationSnapshotEntity>

    @Query("DELETE FROM location_snapshots WHERE trip_id = :tid")
    fun clearForTrip(tid: String)

    @Query("SELECT COUNT(*) FROM location_snapshots")
    fun countAll(): Int
}
