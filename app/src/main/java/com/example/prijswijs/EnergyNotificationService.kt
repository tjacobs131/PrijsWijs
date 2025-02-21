package com.example.prijswijs

import EnergyPriceAPI
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.base.Converter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EnergyNotificationService : Service() {
    private val NOTIFICATION_ID = 1337
    private val FINAL_NOTIFICATION_ID = 1338 // Separate ID

    private val persistence: Persistence by lazy { Persistence(this) }
    private val settings: Settings by lazy { persistence.loadSettings(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannels() // Creates both channels

        startForeground(NOTIFICATION_ID, createProcessingNotification())
        showNotification()
        return START_NOT_STICKY
    }

    private fun createNotificationChannels() {
        // Create both channels
        val serviceChannel = NotificationChannel(
            "service_channel",
            "Background Updates",
            NotificationManager.IMPORTANCE_LOW
        )

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

        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(serviceChannel)
            createNotificationChannel(priceChannel)
        }
    }

    private fun showNotification() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prices = EnergyPriceAPI().getTodaysEnergyPrices()

                Log.println(Log.INFO, "PrijsWijs", "Prices fetched, generating message")

                val message = generateHourlyNotificationMessage(prices)

                Log.println(Log.INFO, "PrijsWijs", "Showing message: $message")

                withContext(Dispatchers.Main) {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(FINAL_NOTIFICATION_ID, buildFinalNotification(message))

                    Log.println(Log.INFO, "PrijsWijs", "Notification shown")

                    // Check after 15 seconds if the notification is shown
                    Handler(Looper.getMainLooper()).postDelayed({
                        if ((getSystemService(NOTIFICATION_SERVICE) as NotificationManager).activeNotifications.none { it.id == FINAL_NOTIFICATION_ID }) {
                            Log.println(Log.INFO, "PrijsWijs", "Notification not shown, retrying")
                            showNotification()
                        } else {
                            Log.println(Log.INFO, "PrijsWijs", "Notification shown")
                        }
                    }, 15000)
                }

                // Stop service after showing notification
                stopSelf()
            } catch (e: Exception) {
                android.util.Log.e("SERVICE_ERROR", "Notification failed", e)

                stopSelf()
            }
        }
    }
    private fun createProcessingNotification(): Notification {
        createNotificationChannels()

        return NotificationCompat.Builder(this, "service_channel")
            .setContentTitle("Updating Energy Prices")
            .setContentText("Fetching latest data...")
            .setSmallIcon(R.drawable.notification_icon)
            .setSilent(true)
            .build()
    }

    private fun buildFinalNotification(message: String): Notification {
        return NotificationCompat.Builder(this, "energy_prices")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("⚡ Today's Energy Prices ⚡")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSilent(!settings.vibrate)
            //.setOngoing(true)
            .build()
    }

    private fun generateHourlyNotificationMessage(priceData: Triple<Map<Date, Double>, Double, Double>): String {
        var returnString = ""
        val dateFormat = SimpleDateFormat("HH:mm", Locale.US)

        val peakPrice = priceData.second
        val troughPrice = priceData.third
        val range = peakPrice - troughPrice

        val dateTimeEmojiTable = mapOf(
            4 to "\uD83C\uDF11",
            6 to "\uD83C\uDF05",
            8 to "🌄",
            16 to "☀️",
            19 to "🌆",
            23 to "\uD83C\uDF19",
        )

        val keysList = priceData.first.keys.toList() // Keep keysList for day transition check

        priceData.first.entries.forEachIndexed { index, entry ->
            val date = entry.key
            val price = entry.value
            var suffix = ""

            if (range > 0) {
                // Calculate dynamic thresholds based on 10% and 20% of the range
                val peakThreshold1 = peakPrice - 0.1 * range  // Top 10% near peak
                val peakThreshold2 = peakPrice - 0.4 * range  // Next 30%
                val troughThreshold1 = troughPrice + 0.1 * range  // Bottom 10% near trough
                val troughThreshold2 = troughPrice + 0.3 * range  // Next 20%

                when {
                    price == priceData.first.values.min() -> suffix = "⭐"
                    range < 0.06 -> suffix = ""  // Range too small to categorize
                    price > peakThreshold1 && range > 0.15 -> suffix = "‼\uFE0F"   // Top tier (closest to peak)
                    price == priceData.first.values.max() || price > peakThreshold2 -> suffix = "❗"   // High tier
                    price < troughThreshold1 -> suffix = "⭐"  // Bottom tier (closest to trough)
                    price < troughThreshold2 && range > 0.10 -> suffix = "🌱"  // Low tier
                    else -> suffix = ""
                }
            }

            // Check for day transition in first 10 entries
            if (index > 0 && index <= 9) {
                val prevDate = keysList[index - 1]
                if (isNewDay(prevDate, date)) {
                    val currentDate = Calendar.getInstance().apply { time = date }
                    returnString += "\uD83C\uDF11 | —— ${currentDate.get(Calendar.DAY_OF_MONTH)} ${currentDate.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)} ——\n"
                }
            }

            if (index == 0) {
                returnString += "\uD83D\uDCA1 |  Now   -  €%.2f".format(price)
                returnString += suffix + "\n"
            } else {
                val formattedDate = dateFormat.format(date)
                var hourValue = formattedDate.split(":")[0].toInt()
                while (!dateTimeEmojiTable.containsKey(hourValue)) {
                    hourValue = (hourValue + 1) % 24
                }

                returnString += dateTimeEmojiTable[hourValue]
                returnString += " | $formattedDate  -  €%.2f".format(price)
                returnString += suffix
                returnString += "\n"
            }
        }

        return returnString
    }

    private fun isNewDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1[Calendar.DAY_OF_YEAR] != cal2[Calendar.DAY_OF_YEAR] || cal1[Calendar.YEAR] != cal2[Calendar.YEAR]
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
