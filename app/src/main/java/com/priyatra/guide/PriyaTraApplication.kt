package com.priyatra.guide

import android.app.Application
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.notifications.NotificationHelper
import com.priyatra.guide.notifications.TripNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PriyaTraApplication : Application() {
    /** For cloud sync; kept off the main thread (except [TripRepository] UI updates). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        TripRepository.init(this)
        TripNotificationScheduler.scheduleTripNotifications(this)
    }
}
