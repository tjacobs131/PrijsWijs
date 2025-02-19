package com.example.enerfy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.util.Calendar
import android.provider.Settings as AndroidSettings

object AlarmScheduler {
    fun scheduleHourlyAlarm(context: Context, bedtime: Int, wakeUpTime: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        // Check if exact alarms are allowed (Android 12+)
        if (!alarmManager.canScheduleExactAlarms()) {
            // Launch permission request activity
            val intent = Intent().apply {
                action = AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
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

        // Check if we are past bedtime
        val currentTime = LocalDateTime.now()
        if (currentTime.hour >= bedtime) {
            // Calculate time for next day
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, wakeUpTime)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }

            Log.println(Log.INFO, "Enerfy", "Time to next alarm: ${calendar.time} / ${calendar.timeInMillis}")

            alarmManager.canScheduleExactAlarms()
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            return
        }

        // Calculate exact time for next hour transition
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.HOUR, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }

        Log.println(Log.INFO, "Enerfy", "Time to next alarm: ${calendar.time} / ${calendar.timeInMillis}")

        alarmManager.canScheduleExactAlarms()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    private const val HOURLY_REQUEST_CODE = 1337
}