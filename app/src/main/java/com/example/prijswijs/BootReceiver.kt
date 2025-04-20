package com.example.prijswijs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.prijswijs.Alarms.AlarmScheduler
import com.example.prijswijs.Notifications.EnergyNotificationService


class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {

    val scheduler = AlarmScheduler()

    if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {

      // Start foreground service immediately
      val serviceIntent = Intent(context, EnergyNotificationService::class.java)
      context.startForegroundService(serviceIntent)

      Log.println(Log.INFO, "PrijsWijs", "BootReceiver triggered")

      // Schedule hourly updates
      scheduler.scheduleHourlyAlarm(context)
    }
  }
}