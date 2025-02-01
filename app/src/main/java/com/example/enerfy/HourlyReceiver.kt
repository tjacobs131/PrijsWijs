package com.example.enerfy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HourlyReceiver : BroadcastReceiver() {

    var persistence: Persistence? = null
    var settings: Settings? = null

    override fun onReceive(context: Context, intent: Intent) {
        // Start service to show notification
        context.startService(Intent(context, EnergyNotificationService::class.java))

        // Load settings
        persistence = Persistence(context)
        settings = persistence!!.loadSettings()

        // Reschedule for next hour
        AlarmScheduler.scheduleHourlyAlarm(context, settings!!.bedTime, settings!!.wakeUpTime)
    }
}