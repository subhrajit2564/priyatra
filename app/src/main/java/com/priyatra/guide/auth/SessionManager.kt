package com.priyatra.guide.auth

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("priyatra_auth", Context.MODE_PRIVATE)

    fun login(phone: String, isAdmin: Boolean, customerTripId: String? = null) {
        val e = prefs.edit()
            .putString("phone", phone)
            .putBoolean("is_admin", isAdmin)
        if (customerTripId != null) {
            e.putString("customer_trip_id", customerTripId)
        } else {
            e.remove("customer_trip_id")
        }
        e.apply()
    }

    fun setPreviewTripId(tripId: String?) {
        if (tripId == null) prefs.edit().remove("preview_trip_id").apply()
        else prefs.edit().putString("preview_trip_id", tripId).apply()
    }

    fun getPreviewTripId(): String? = prefs.getString("preview_trip_id", null)

    fun getCustomerTripId(): String? = prefs.getString("customer_trip_id", null)

    /** When previewing the user app as admin, or a logged-in customer. */
    fun getActiveUserTripId(): String? = getCustomerTripId() ?: getPreviewTripId()

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = prefs.contains("phone")
    fun isAdmin(): Boolean = prefs.getBoolean("is_admin", false)
    fun getPhone(): String? = prefs.getString("phone", null)
}
