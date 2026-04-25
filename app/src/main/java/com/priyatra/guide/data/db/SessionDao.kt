package com.priyatra.guide.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {
    @Query("SELECT * FROM app_session WHERE id = 1")
    fun getRow(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(e: SessionEntity)
}
