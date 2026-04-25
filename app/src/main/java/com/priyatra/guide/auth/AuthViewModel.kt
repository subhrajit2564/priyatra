package com.priyatra.guide.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.data.db.PriyaTraDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager(application)

    private val _isLoggedIn = MutableStateFlow(sessionManager.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isAdmin = MutableStateFlow(sessionManager.isAdmin())
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _previewingAsUser = MutableStateFlow(false)
    val previewingAsUser: StateFlow<Boolean> = _previewingAsUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    /** Comma-separated admin digits (for display/debug). */
    val registeredAdminLines: String
        get() = AdminPhoneConfig.loadDigitsList(PriyaTraDatabase.getInstance(getApplication()).settingsDao())
            .joinToString(", ")

    fun clearLoginError() {
        _loginError.value = null
    }

    fun loginWithPhone(phone: String) {
        val normalized = PhoneUtils.normalize(phone)
        if (normalized.isEmpty()) {
            _loginError.value = "Enter a valid phone number."
            return
        }
        val settings = PriyaTraDatabase.getInstance(getApplication()).settingsDao()
        if (AdminPhoneConfig.isAdminLine(phone, settings)) {
            _loginError.value = null
            sessionManager.login(phone, isAdmin = true)
            _isAdmin.value = true
            _isLoggedIn.value = true
            _previewingAsUser.value = false
            sessionManager.setPreviewTripId(null)
            TripRepository.refreshFromSession(getApplication())
            return
        }
        val match = PriyaTraDatabase.getInstance(getApplication())
            .customerPhoneDao()
            .findFirstByNormalizedDigits(PhoneUtils.normalize(phone))
        if (match == null) {
            _loginError.value = "This number is not registered on any active trip. Contact PriyaTra."
            return
        }
        _loginError.value = null
        sessionManager.login(phone, isAdmin = false, customerTripId = match.tripId)
        _isAdmin.value = false
        _isLoggedIn.value = true
        _previewingAsUser.value = false
        TripRepository.refreshFromSession(getApplication())
    }

    /**
     * Admin preview of the user app. [tripId] must be set to load the right itinerary
     * (uses [SessionManager] preview id).
     */
    fun setPreviewAsUser(enabled: Boolean, tripId: String? = null) {
        if (enabled) {
            if (tripId != null) sessionManager.setPreviewTripId(tripId)
            _previewingAsUser.value = true
        } else {
            sessionManager.setPreviewTripId(null)
            _previewingAsUser.value = false
        }
        TripRepository.refreshFromSession(getApplication())
    }

    fun logout() {
        sessionManager.logout()
        _isLoggedIn.value = false
        _isAdmin.value = false
        _previewingAsUser.value = false
        _loginError.value = null
    }
}
