package com.example.enerfy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build


class BootReceiver : BroadcastReceiver() {

  var persistence: Persistence? = null
  var settings: Settings? = null

  override fun onReceive(context: Context, intent: Intent?) {

    if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {

      // Start foreground service immediately
      val serviceIntent = Intent(context, EnergyNotificationService::class.java)
      context.startForegroundService(serviceIntent)

      persistence = Persistence(context)

      // Load settings
      settings = persistence!!.loadSettings()

      // Schedule hourly updates
      AlarmScheduler.scheduleHourlyAlarm(context, settings!!.bedTime, settings!!.wakeUpTime)
    }
  }
}