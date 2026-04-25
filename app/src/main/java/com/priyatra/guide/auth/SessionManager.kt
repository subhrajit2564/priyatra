package com.priyatra.guide.auth

import android.content.Context
import com.priyatra.guide.data.db.PriyaTraDatabase
import com.priyatra.guide.data.db.SessionEntity

class SessionManager(context: Context) {
    private val app = context.applicationContext
    private val db get() = PriyaTraDatabase.getInstance(app)
    private val sessionDao get() = db.sessionDao()
    private val legacy = app.getSharedPreferences("priyatra_auth", Context.MODE_PRIVATE)

    init {
        migrateFromLegacySharedPrefs()
    }

    private fun row(): SessionEntity =
        sessionDao.getRow() ?: SessionEntity(1, null, false, null, null)

    private fun migrateFromLegacySharedPrefs() {
        if (!legacy.contains("phone")) return
        if (row().phone != null) {
            legacy.edit().clear().apply()
            return
        }
        val phone = legacy.getString("phone", null) ?: return
        val isAdmin = legacy.getBoolean("is_admin", false)
        val customerTripId = legacy.getString("customer_trip_id", null)
        val previewTripId = legacy.getString("preview_trip_id", null)
        sessionDao.save(
            SessionEntity(1, phone, isAdmin, customerTripId, previewTripId),
        )
        legacy.edit().clear().apply()
    }

    fun login(phone: String, isAdmin: Boolean, customerTripId: String? = null) {
        val r = row()
        sessionDao.save(
            SessionEntity(
                id = 1,
                phone = phone,
                isAdmin = isAdmin,
                customerTripId = customerTripId,
                previewTripId = r.previewTripId,
            ),
        )
    }

    fun setPreviewTripId(tripId: String?) {
        sessionDao.save(
            row().copy(previewTripId = tripId),
        )
    }

    fun getPreviewTripId(): String? = row().previewTripId

    fun getCustomerTripId(): String? = row().customerTripId

    /** When previewing the user app as admin, or a logged-in customer. */
    fun getActiveUserTripId(): String? = getCustomerTripId() ?: getPreviewTripId()

    fun logout() {
        sessionDao.save(SessionEntity(1, null, false, null, null))
    }

    fun isLoggedIn(): Boolean = row().phone != null
    fun isAdmin(): Boolean = row().isAdmin
    fun getPhone(): String? = row().phone
}
