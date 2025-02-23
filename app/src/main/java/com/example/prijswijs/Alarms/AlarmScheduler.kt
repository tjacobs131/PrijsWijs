package com.example.prijswijs.Alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.prijswijs.Persistence.Persistence
import java.time.LocalDateTime
import java.util.Calendar
import android.provider.Settings as AndroidSettings

object AlarmScheduler {
    fun scheduleHourlyAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val settings = Persistence(context).loadSettings(context)

        // Check exact alarm permission (Android 12+)
        if (!alarmManager.canScheduleExactAlarms()) {
            val intent = Intent().apply {
                action = AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            Handler(Looper.getMainLooper()).postDelayed({
                scheduleHourlyAlarm(context)
            }, 5000)
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

        val currentTime = LocalDateTime.now()
        val bedTimeHour = settings.bedTime
        val wakeUpHour = settings.wakeUpTime
        val currentHour = currentTime.hour

        // Determine if current time is within bedtime window
        val isPastBedtime = if (bedTimeHour < wakeUpHour) {
            currentHour in bedTimeHour..<wakeUpHour
        } else {
            currentHour >= bedTimeHour || currentHour < wakeUpHour
        }

        Log.println(Log.INFO, "PrijsWijs", "Current time: $currentTime, Bedtime: $bedTimeHour, Wakeup: $wakeUpHour")

        if (isPastBedtime) {
            // Schedule next alarm at wake-up time
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                // Add day if bedtime is today but wake-up is tomorrow
                if (bedTimeHour in wakeUpHour..currentHour) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                set(Calendar.HOUR_OF_DAY, wakeUpHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 10)
                set(Calendar.MILLISECOND, 0)
            }
            // Ensure wake-up is not in the past (if called right at wake-up hour)
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            Log.println(Log.INFO, "PrijsWijs", "Scheduling at wake-up: ${calendar.time}")

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            // Schedule at next hour
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                add(Calendar.HOUR, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }

            Log.println(Log.INFO, "PrijsWijs", "Scheduling next hour: ${calendar.time}")

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private const val HOURLY_REQUEST_CODE = 1337420
}