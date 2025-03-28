package com.example.prijswijs.Notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.example.prijswijs.R
import com.example.prijswijs.Model.Settings
import kotlin.properties.Delegates

class NotificationBuilder(private val context: Context, private val settings: Settings) {
  private val notificationManager: NotificationManager =
    context.getSystemService(NotificationManager::class.java)

  companion object {
    var vibrate by Delegates.notNull<Boolean>()
  }

  fun createNotificationChannels() {
    // Create channel for background updates
    val serviceChannel = NotificationChannel(
      "service_channel",
      "Background Updates",
      NotificationManager.IMPORTANCE_LOW
    )

    // Create channel for energy price alerts
    val priceChannel = NotificationChannel(
      "energy_prices",
      "Price Alerts",
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      enableLights(true)
      lightColor = Color.GREEN
      enableVibration(true)
      vibrationPattern = longArrayOf(500, 200)
    }

    notificationManager.createNotificationChannel(serviceChannel)
    notificationManager.createNotificationChannel(priceChannel)
  }

//  fun buildProcessingNotification(): Notification {
//    // Ensure channels exist before creating notifications
//    createNotificationChannels()
//
//    return NotificationCompat.Builder(context, "service_channel")
//      .setContentTitle("Updating Energy Prices")
//      .setContentText("Fetching latest data...")
//      .setSmallIcon(R.drawable.notification_icon)
//      .setSilent(true)
//      .build()
//  }

  fun buildFinalNotification(message: String, isError: Boolean = false): Notification {
    val builder = NotificationCompat.Builder(context, "energy_prices")
      .setSmallIcon(R.drawable.notification_icon)
      .setContentTitle(
        if (isError)
          "⚠️ Price Update Failed ⚠️"
        else
          "⚡ Today's Energy Prices ⚡"
      )
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))

    if (isError) {
      builder.priority = NotificationCompat.PRIORITY_LOW
      builder.setSilent(true)
    } else if (vibrate) {
      builder.priority = NotificationCompat.PRIORITY_MAX
      builder.setSilent(false)
    } else {
      builder.priority = NotificationCompat.PRIORITY_HIGH
      builder.setSilent(true)
    }

    return builder.build()
  }

  fun doVibration(doVibration: Boolean) {
    vibrate = doVibration
  }

}
