package com.example.maw9oot.data.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.Calendar

private const val DAILY_NOTIFICATION_REQUEST_CODE = 1000

fun scheduleDailyNotification(context: Context, calendar: Calendar) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return
        }
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

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent
    )
}

fun schedulePrayerReminder(context: Context, prayerTime: Calendar, delayMinutes: Int, prayerName: String) {

    val reminderTime = prayerTime.clone() as Calendar

    reminderTime.add(Calendar.MINUTE, delayMinutes)

    // Ensure we are scheduling for the future
    if (reminderTime.before(Calendar.getInstance())) {
        return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
            // We don't want to spam the user with permission requests if scheduling multiple reminders
            // The daily notification schedule should handle the initial request
            return
        }
    }

    val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
        putExtra("notification_type", "prayer_reminder")
        putExtra("prayer_name", prayerName)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        prayerName.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        reminderTime.timeInMillis,
        pendingIntent
    )
}

fun cancelDailyNotification(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, PrayerNotificationReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        DAILY_NOTIFICATION_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}

fun cancelPrayerReminders(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val prayers = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    for (prayerName in prayers) {
        val intent = Intent(context, PrayerNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

fun cancelAllNotifications(context: Context) {
    cancelDailyNotification(context)
    cancelPrayerReminders(context)
}
