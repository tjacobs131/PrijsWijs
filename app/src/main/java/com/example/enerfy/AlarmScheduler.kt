package com.example.enerfy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Calendar

object AlarmScheduler {
    fun scheduleHourlyAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        // Check if exact alarms are allowed (Android 12+)
        if (!alarmManager.canScheduleExactAlarms()) {
            // Launch permission request activity
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }

        val intent = Intent(context, HourlyReceiver::class.java).apply {
            action = "HOURLY_UPDATE_ACTION"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            HOURLY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate exact time for next hour transition
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.HOUR, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }

        println("Time to next alarm: ${calendar.time} / ${calendar.timeInMillis}")

        alarmManager.canScheduleExactAlarms()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    private const val HOURLY_REQUEST_CODE = 1337
}