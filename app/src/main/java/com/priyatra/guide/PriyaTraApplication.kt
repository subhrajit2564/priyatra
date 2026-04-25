package com.priyatra.guide

import android.app.Application
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.notifications.NotificationHelper
import com.priyatra.guide.notifications.TripNotificationScheduler

class PriyaTraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        TripRepository.init(this)
        TripNotificationScheduler.scheduleTripNotifications(this)
    }
}
