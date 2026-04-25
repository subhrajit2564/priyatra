package com.priyatra.guide.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.priyatra.guide.data.TripRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        TripRepository.init(app)
        TripNotificationScheduler.scheduleTripNotifications(app)
    }
}
