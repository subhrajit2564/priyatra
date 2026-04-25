package com.priyatra.guide.data.db

import android.content.Context
import androidx.room.Database
import com.priyatra.guide.auth.AdminPhoneConfig
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        CustomerPhoneEntity::class,
        SessionEntity::class,
        LocationSnapshotEntity::class,
        SpotFeedbackEntity::class,
        AppSettingEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PriyaTraDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun customerPhoneDao(): CustomerPhoneDao
    abstract fun sessionDao(): SessionDao
    abstract fun locationDao(): LocationDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val DB_NAME = "priyatra.db"
        @Deprecated("Use AdminPhoneConfig.KEY_ADMIN_PHONES_CSV", ReplaceWith("AdminPhoneConfig.LEGACY_ADMIN_PHONE"))
        const val SETTING_ADMIN_PHONE = "admin_phone_digits"

        @Volatile
        private var instance: PriyaTraDatabase? = null

        fun getInstance(context: Context): PriyaTraDatabase = synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val db = Room.databaseBuilder(app, PriyaTraDatabase::class.java, DB_NAME)
                    .allowMainThreadQueries()
                    .build()
                ensureDefaults(db)
                instance = db
                db
            }
        }

        private fun ensureDefaults(db: PriyaTraDatabase) {
            if (db.settingsDao().getValue(AdminPhoneConfig.KEY_ADMIN_PHONES_CSV) == null) {
                db.settingsDao().set(
                    AppSettingEntity(
                        AdminPhoneConfig.KEY_ADMIN_PHONES_CSV,
                        AdminPhoneConfig.DEFAULT_ADMIN_PHONES_CSV,
                    ),
                )
            }
            if (db.sessionDao().getRow() == null) {
                db.sessionDao()
                    .save(SessionEntity(1, null, false, null, null))
            }
        }
    }
}
