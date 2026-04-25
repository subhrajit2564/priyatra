package com.priyatra.guide.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_TRIP = "priyatra_trip"
    const val CHANNEL_MEALS = "priyatra_meals"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRIP,
                "Travel & transport",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Departures, pickups, and itinerary alerts" },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEALS,
                "Meals",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Breakfast, lunch, snacks, and dinner reminders" },
        )
    }

    fun showNotification(context: Context, title: String, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_TRIP)
            .setSmallIcon(com.priyatra.guide.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
