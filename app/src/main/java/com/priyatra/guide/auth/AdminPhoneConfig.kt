package com.priyatra.guide.auth

import com.priyatra.guide.data.db.SettingsDao

/**
 * Admin logins: comma-separated normalized digit strings in `app_settings` and in Supabase
 * `priyatra_state.admin_phone_digits` (same CSV format for compatibility).
 */
object AdminPhoneConfig {
    const val KEY_ADMIN_PHONES_CSV = "admin_phones_csv"
    /** Legacy single admin; only used if [KEY_ADMIN_PHONES_CSV] is missing. */
    const val LEGACY_ADMIN_PHONE = "admin_phone_digits"

    /** Default three admin lines (digits only, no +91). */
    const val DEFAULT_ADMIN_PHONES_CSV = "9432748575,8334809645,7003438191"

    fun loadDigitsList(settings: SettingsDao): List<String> {
        val primary = settings.getValue(KEY_ADMIN_PHONES_CSV)?.trim()
        if (!primary.isNullOrEmpty()) {
            return parseCsvToDigits(primary)
        }
        val leg = settings.getValue(LEGACY_ADMIN_PHONE)?.trim()
        if (!leg.isNullOrEmpty()) {
            val d = leg.filter { it.isDigit() }
            return if (d.isNotEmpty()) listOf(d) else parseCsvToDigits(DEFAULT_ADMIN_PHONES_CSV)
        }
        return parseCsvToDigits(DEFAULT_ADMIN_PHONES_CSV)
    }

    fun isAdminLine(phone: String, settings: SettingsDao): Boolean {
        return loadDigitsList(settings).any { stored -> PhoneUtils.sameNumber(phone, stored) }
    }

    private fun parseCsvToDigits(csv: String): List<String> =
        csv.split(',')
            .map { it.filter { c -> c.isDigit() } }
            .filter { it.isNotEmpty() }
            .distinct()
}
