package com.example.prijswijs.Alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.prijswijs.Notifications.EnergyNotificationService

class HourlyReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    // Start service to show notification
    context.startForegroundService(Intent(context, EnergyNotificationService::class.java))

    Log.println(Log.INFO, "PrijsWijs", "HourlyReceiver triggered")

    // Reschedule for next hour
    AlarmScheduler.scheduleHourlyAlarm(context)
  }
}