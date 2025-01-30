package com.example.enerfy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HourlyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Start service to show notification
        context.startService(Intent(context, EnergyNotificationService::class.java))

        // Reschedule for next hour
        AlarmScheduler.scheduleHourlyAlarm(context)
    }
}