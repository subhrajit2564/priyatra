package com.priyatra.guide.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.priyatra.guide.data.MealKind
import com.priyatra.guide.data.TripCatalogStore
import com.priyatra.guide.data.TripRepository
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object TripNotificationScheduler {
    private const val TAG = "PriyaTraNotify"

    fun scheduleTripNotifications(context: Context) {
        NotificationHelper.ensureChannels(context)
        val catalog = TripCatalogStore(context).load()
        if (catalog.trips.isEmpty()) {
            // Fallback: single in-memory / catalog seed path
            scheduleOnePackage(context, TripRepository.trip, "default")
        } else {
            for (t in catalog.trips) {
                scheduleOnePackage(context, t.tripPackage, t.id)
            }
        }
    }

    private fun scheduleOnePackage(context: Context, trip: com.priyatra.guide.data.TripPackage, keyPrefix: String) {
        val zone = trip.zone
        trip.transports.forEach { t ->
            val dep = t.departure.atZone(zone).toInstant().toEpochMilli()
            val transportId = "$keyPrefix-${t.id}"
            scheduleOffsets(context, dep, "Transport: ${t.title}", transportId) { offset ->
                when (offset) {
                    H_24 -> "24 hours to departure from ${t.from}."
                    H_6 -> "6 hours to departure — keep PNR ${t.pnr} handy."
                    H_3 -> "3 hours to departure — head to the station with time to spare."
                    H_1 -> "1 hour to departure — proceed to your platform."
                    M_30 -> "30 minutes — final boarding window."
                    else -> ""
                }
            }
        }

        val startD = trip.hotel.checkIn
        val endD = trip.hotel.checkOut
        var day = startD
        while (!day.isAfter(endD)) {
            TripRepository.mealReminders.forEach { meal ->
                val start = ZonedDateTime.of(day, meal.windowStart, zone)
                val label = when (meal.kind) {
                    MealKind.BREAKFAST -> "Breakfast"
                    MealKind.LUNCH -> "Lunch"
                    MealKind.SNACKS -> "Snacks"
                    MealKind.DINNER -> "Dinner"
                }
                val title = "${trip.title} · $label"
                val text =
                    "${meal.label}: ${meal.windowStart}–${meal.windowEnd} (${zone.id})"
                val id = ("$keyPrefix-meal-${meal.kind}-$day").hashCode()
                scheduleExact(
                    context,
                    start.toInstant().toEpochMilli(),
                    title,
                    text,
                    NotificationHelper.CHANNEL_MEALS,
                    id,
                )
            }
            day = day.plusDays(1)
        }
    }

    fun scheduleDemoNotification(context: Context, delaySeconds: Long = 8) {
        NotificationHelper.ensureChannels(context)
        val trigger = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delaySeconds)
        scheduleExact(
            context,
            trigger,
            "Priyatra Getaways",
            "Demo alert — your scheduler is working.",
            NotificationHelper.CHANNEL_TRIP,
            "demo".hashCode(),
        )
    }

    /** Admin / desk: custom scheduled push on device (same as transport channel). */
    fun scheduleAtMillis(context: Context, whenUtcMillis: Long, title: String, message: String) {
        if (whenUtcMillis <= System.currentTimeMillis()) return
        NotificationHelper.ensureChannels(context)
        val id = ("desk-${title}-${whenUtcMillis}").hashCode()
        scheduleExact(
            context,
            whenUtcMillis,
            title,
            message,
            NotificationHelper.CHANNEL_TRIP,
            id,
        )
    }

    private fun scheduleOffsets(
        context: Context,
        departureMillis: Long,
        titlePrefix: String,
        transportKey: String,
        body: (Long) -> String,
    ) {
        listOf(H_24, H_6, H_3, H_1, M_30).forEach { offset ->
            val at = departureMillis - offset
            val bodyText = body(offset)
            if (bodyText.isBlank()) return@forEach
            val id = ("$transportKey-$offset").hashCode()
            scheduleExact(
                context,
                at,
                titlePrefix,
                bodyText,
                NotificationHelper.CHANNEL_TRIP,
                id,
            )
        }
    }

    private fun scheduleExact(
        context: Context,
        triggerAtMillis: Long,
        title: String,
        text: String,
        channel: String,
        notificationId: Int,
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Skip past alarm $title @ $triggerAtMillis")
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingShowNotification(context, title, text, channel, notificationId)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }.onFailure { Log.e(TAG, "schedule failed: $title", it) }
    }

    private fun pendingShowNotification(
        context: Context,
        title: String,
        text: String,
        channel: String,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(context, TripAlarmReceiver::class.java).apply {
            putExtra(TripAlarmReceiver.EXTRA_TITLE, title)
            putExtra(TripAlarmReceiver.EXTRA_TEXT, text)
            putExtra(TripAlarmReceiver.EXTRA_CHANNEL, channel)
            putExtra(TripAlarmReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, notificationId, intent, flags)
    }

    private val H_24 = TimeUnit.HOURS.toMillis(24)
    private val H_6 = TimeUnit.HOURS.toMillis(6)
    private val H_3 = TimeUnit.HOURS.toMillis(3)
    private val H_1 = TimeUnit.HOURS.toMillis(1)
    private val M_30 = TimeUnit.MINUTES.toMillis(30)
}
