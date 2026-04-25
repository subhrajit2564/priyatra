package com.priyatra.guide.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(e: AppSettingEntity)
}
