package com.priyatra.guide.feedback

import android.content.Context

class FeedbackStore(context: Context) {
    private val prefs = context.getSharedPreferences("priyatra_feedback", Context.MODE_PRIVATE)

    fun hasFeedback(spotId: String): Boolean = prefs.contains(keyStars(spotId))

    fun save(spotId: String, stars: Int, note: String) {
        prefs.edit()
            .putInt(keyStars(spotId), stars.coerceIn(1, 5))
            .putString(keyNote(spotId), note)
            .apply()
    }

    fun stars(spotId: String): Int = prefs.getInt(keyStars(spotId), 0)

    private fun keyStars(id: String) = "${id}_stars"
    private fun keyNote(id: String) = "${id}_note"
}
