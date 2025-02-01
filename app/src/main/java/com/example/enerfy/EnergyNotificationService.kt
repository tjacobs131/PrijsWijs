package com.example.enerfy

import EnergyPriceAPI
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Spannable
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log
import android.text.SpannableString
import android.text.style.TypefaceSpan

class EnergyNotificationService : Service() {
    private val NOTIFICATION_ID = 1337
    private val FINAL_NOTIFICATION_ID = 1338 // Separate ID

    private val persistence: Persistence by lazy { Persistence(this) }
    private val settings: Settings by lazy { persistence.loadSettings() }

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
                val message = generateHourlyNotificationMessage(prices)

                withContext(Dispatchers.Main) {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(FINAL_NOTIFICATION_ID, buildFinalNotification(message))

                    // Stop service after showing notification
                    stopSelf()

//                    // Only stop after confirmation
//                    Handler(Looper.getMainLooper()).postDelayed({
//                        stopSelf()
//                    }, 2000) // 2-second delay to ensure delivery
                }
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
            .setOngoing(true)
            .build()
    }

    private fun generateHourlyNotificationMessage(priceData: Triple<Map<Date, Double>, Double, Double>): String{
        var returnString = ""
        val dateFormat = SimpleDateFormat("HH:mm", Locale.US)

        val peakPrice = priceData.second
        val troughPrice = priceData.third
        val range = peakPrice - troughPrice

        val suffixList = mutableListOf<String>()

        // Fill the suffix list with the appropriate suffixes
        priceData.first.values.forEach { price ->
            if (range > 0) {
                // Calculate dynamic thresholds based on 10% and 20% of the range
                val peakThreshold1 = peakPrice - 0.1 * range  // Top 10% near peak
                val peakThreshold2 = peakPrice - 0.4 * range  // Next 30%
                val troughThreshold1 = troughPrice + 0.1 * range  // Bottom 10% near trough
                val troughThreshold2 = troughPrice + 0.3 * range  // Next 20%

                when {
                    range < 0.06 -> suffixList.add("")  // Range too small to categorize
                    price > peakThreshold1 && range > 0.15 -> suffixList.add("‼\uFE0F")   // Top tier (closest to peak)
                    price == priceData.first.values.max() || price > peakThreshold2 && range > 0.10 -> suffixList.add("❗")   // High tier
                    price == priceData.first.values.min() || price < troughThreshold1 -> suffixList.add("⭐")  // Bottom tier (closest to trough)
                    price < troughThreshold2 && range > 0.10 -> suffixList.add("🌱")  // Low tier
                    else -> suffixList.add("")
                }
            } else {
                suffixList.add("")
            }
        }

        var lastPrice = 0.0
        priceData.first.keys.forEach { date ->
            val price = priceData.first[date]

            if (priceData.first.keys.indexOf(date) == 0) {
                returnString += "\uD83D\uDCA1 |  Now   -  €%.2f".format(priceData.first[date])
                returnString += suffixList[0] + "\n"

                lastPrice = price!!
                return@forEach
            }

            val formattedDate = dateFormat.format(date)

            returnString += if (price!!.compareTo(lastPrice) > 0) {
                "\uD83D\uDCC8"
            } else if (price.compareTo(lastPrice) < 0) {
                "\uD83D\uDCC9"
            } else {
                "\u2B1C"
            }
            returnString += " | $formattedDate  -  €%.2f".format(price)
            returnString += suffixList[priceData.first.keys.indexOf(date)]
            returnString += "\n"

            lastPrice = price
        }

        return returnString
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
