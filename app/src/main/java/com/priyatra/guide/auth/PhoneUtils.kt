package com.priyatra.guide.auth

object PhoneUtils {
    /** Digits only, for matching against stored customer lists. */
    fun normalize(raw: String): String = raw.filter { it.isDigit() }

    /** True if the same phone (handles optional country code, last 10 digits in IN-style numbers). */
    fun sameNumber(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return true
        if (na.length >= 10 && nb.length >= 10) return na.takeLast(10) == nb.takeLast(10)
        return false
    }
}
