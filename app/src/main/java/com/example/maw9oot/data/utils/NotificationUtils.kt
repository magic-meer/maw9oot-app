package com.example.maw9oot.data.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.maw9oot.data.local.DataStoreManager
import com.example.maw9oot.data.repository.PrayerTimesRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAILY_NOTIFICATION_REQUEST_CODE = 1000

// Coordinates for prayer times (Algiers, Algeria)
// TODO: These should be retrieved from user settings or GPS in a future update
private const val DEFAULT_LATITUDE = 36.402482
private const val DEFAULT_LONGITUDE = 3.323412

// Use a fixed formatter for database and internal logic to ensure locale independence
private val DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT)

fun scheduleDailyNotification(context: Context, time: LocalTime) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val now = LocalDateTime.now()
    var scheduledDateTime = LocalDateTime.of(now.toLocalDate(), time)

    if (scheduledDateTime.isBefore(now)) {
        scheduledDateTime = scheduledDateTime.plusDays(1)
    }

    val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
        putExtra("notification_type", "daily")
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        DAILY_NOTIFICATION_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerAtMillis = scheduledDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        Log.d("NotificationUtils", "Daily notification scheduled (Inexact) for $scheduledDateTime")
    } else {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        Log.d("NotificationUtils", "Daily notification scheduled (Exact) for $scheduledDateTime")
    }
}

fun schedulePrayerReminder(context: Context, date: LocalDate, prayerTime: LocalTime, delayMinutes: Int, prayerName: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val reminderDateTime = LocalDateTime.of(date, prayerTime).plusMinutes(delayMinutes.toLong())
    val now = LocalDateTime.now()

    // Ensure we are scheduling for the future
    if (reminderDateTime.isBefore(now)) {
        Log.d("NotificationUtils", "Skipping past prayer reminder for $prayerName at $reminderDateTime")
        return
    }

    val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
        putExtra("notification_type", "prayer_reminder")
        putExtra("prayer_name", prayerName)
    }

    val formattedDate = date.format(DB_DATE_FORMATTER)
    val requestCode = "${prayerName}_${formattedDate}".hashCode()

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerAtMillis = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        Log.d("NotificationUtils", "Prayer reminder scheduled (Inexact) for $prayerName at $reminderDateTime")
    } else {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        Log.d("NotificationUtils", "Prayer reminder scheduled (Exact) for $prayerName at $reminderDateTime")
    }
}

fun cancelDailyNotification(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
        putExtra("notification_type", "daily")
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        DAILY_NOTIFICATION_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d("NotificationUtils", "Daily notification cancelled")
    }
}

fun cancelPrayerReminders(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val prayers = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    val today = LocalDate.now()
    val datesToCancel = listOf(today, today.plusDays(1))

    for (date in datesToCancel) {
        val formattedDate = date.format(DB_DATE_FORMATTER)
        for (prayerName in prayers) {
            val requestCode = "${prayerName}_${formattedDate}".hashCode()
            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                putExtra("notification_type", "prayer_reminder")
                putExtra("prayer_name", prayerName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d("NotificationUtils", "Prayer reminder cancelled for $prayerName on $formattedDate")
            }
        }
    }
}

fun cancelAllNotifications(context: Context) {
    cancelDailyNotification(context)
    cancelPrayerReminders(context)
}

suspend fun rescheduleAllAlarms(
    context: Context,
    dataStoreManager: DataStoreManager,
    prayerTimesRepository: PrayerTimesRepository
) {
    Log.d("NotificationUtils", "Rescheduling all alarms...")

    // Schedule Daily Notification
    val isDailyEnabled = dataStoreManager.isDailyNotificationEnabled.first()
    if (isDailyEnabled) {
        val timeStr = dataStoreManager.notificationTime.first()
        try {
            val time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
            scheduleDailyNotification(context, time)
        } catch (e: Exception) {
            Log.e("NotificationUtils", "Error parsing daily notification time: $timeStr", e)
        }
    } else {
        cancelDailyNotification(context)
    }

    // Ensure prayer times are synced for today and tomorrow
    val today = LocalDate.now()
    val datesToSchedule = listOf(today, today.plusDays(1))

    for (date in datesToSchedule) {
        val formattedDate = date.format(DB_DATE_FORMATTER)
        if (prayerTimesRepository.getPrayerTimesForDate(formattedDate).isEmpty()) {
            val year = date.year
            try {
                prayerTimesRepository.fetchAndStorePrayerTimes(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, year)
                Log.d("NotificationUtils", "Prayer times synced automatically for $formattedDate")
            } catch (e: Exception) {
                Log.e("NotificationUtils", "Error syncing prayer times automatically: ${e.message}")
            }
        }
    }

    // Schedule Prayer Reminders
    val isPrayerEnabled = dataStoreManager.isPrayerReminderEnabled.first()
    if (isPrayerEnabled) {
        val delay = dataStoreManager.prayerReminderDelay.first().toIntOrNull() ?: 15

        for (date in datesToSchedule) {
            val formattedDate = date.format(DB_DATE_FORMATTER)
            val prayerTimes = prayerTimesRepository.getPrayerTimesForDate(formattedDate)

            for (prayerTime in prayerTimes) {
                try {
                    // Robustly parse time like "05:30 (CET)"
                    val timeStr = prayerTime.time.split(" ")[0]
                    val pTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
                    schedulePrayerReminder(context, date, pTime, delay, prayerTime.prayerName)
                } catch (e: Exception) {
                    Log.e("NotificationUtils", "Error parsing prayer time: ${prayerTime.time} for ${prayerTime.prayerName}", e)
                }
            }
        }
    } else {
        cancelPrayerReminders(context)
    }
}
