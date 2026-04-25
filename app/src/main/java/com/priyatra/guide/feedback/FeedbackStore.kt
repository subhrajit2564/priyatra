package com.priyatra.guide.feedback

import android.content.Context
import com.priyatra.guide.data.db.PriyaTraDatabase
import com.priyatra.guide.data.db.SpotFeedbackEntity

class FeedbackStore(context: Context) {
    private val dao = PriyaTraDatabase.getInstance(context.applicationContext).feedbackDao()

    fun hasFeedback(spotId: String): Boolean = dao.get(spotId) != null

    fun save(spotId: String, stars: Int, note: String) {
        dao.upsert(
            SpotFeedbackEntity(
                spotId = spotId,
                stars = stars.coerceIn(1, 5),
                note = note,
            ),
        )
    }

    fun stars(spotId: String): Int = dao.get(spotId)?.stars ?: 0
}
