package com.example.enerfy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class HourlyReceiver : BroadcastReceiver() {

    var persistence: Persistence? = null
    var settings: Settings? = null

    override fun onReceive(context: Context, intent: Intent) {
        // Start service to show notification
        context.startService(Intent(context, EnergyNotificationService::class.java))

        Log.println(Log.INFO, "Enerfy", "HourlyReceiver triggered")

        // Load settings
        persistence = Persistence.getInstance(context)
        settings = persistence!!.loadSettings(context)

        // Reschedule for next hour
        AlarmScheduler.scheduleHourlyAlarm(context, settings!!.bedTime, settings!!.wakeUpTime)
    }
}