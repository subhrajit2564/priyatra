package com.priyatra.guide.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.priyatra.guide.R

class TripAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.ensureChannels(context)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.app_name)
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: NotificationHelper.CHANNEL_TRIP
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, title.hashCode())

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
