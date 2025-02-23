package com.example.prijswijs.Notifications

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.prijswijs.EnergyZeroAPI.EnergyPriceAPI
import com.example.prijswijs.Persistence.Persistence
import com.example.prijswijs.EnergyZeroAPI.PriceData
import com.example.prijswijs.EnergyZeroAPI.PricesUnavailableException
import com.example.prijswijs.Persistence.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EnergyNotificationService : Service() {
    private val NOTIFICATION_ID = 1337420
    private val FINAL_NOTIFICATION_ID = 1338

    private val persistence: Persistence by lazy { Persistence(this) }
    private val settings: Settings by lazy { persistence.loadSettings(this) }
    private val notificationBuilder by lazy { NotificationBuilder(this, settings) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create channels and start service in foreground with a processing notification.
        notificationBuilder.createNotificationChannels()
        startForeground(NOTIFICATION_ID, notificationBuilder.buildProcessingNotification())
        showNotification()
        return START_NOT_STICKY
    }

    private fun showNotification() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("PrijsWijs", "Fetching energy prices...")
                lateinit var prices: PriceData
                var warningMessage = ""
                try {
                    prices = EnergyPriceAPI().getTodaysEnergyPrices()
                } catch (ex: PricesUnavailableException) {
                    prices = ex.oldPrices
                    warningMessage = ex.message.toString()
                }

                Log.d("PrijsWijs", "Prices fetched successfully.")
                val message = generateHourlyNotificationMessage(prices, warningMessage)
                Log.d("PrijsWijs", "Showing message: $message")

                withContext(Dispatchers.Main) {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(FINAL_NOTIFICATION_ID, notificationBuilder.buildFinalNotification(message))
                    Log.d("PrijsWijs", "Notification shown")

                    // Delay check to confirm the notification is active
                    Handler(Looper.getMainLooper()).postDelayed({
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        if (nm.activeNotifications.none { it.id == FINAL_NOTIFICATION_ID }) {
                            Log.d("PrijsWijs", "Notification not shown (after delay check), potentially an issue.")
                        } else {
                            Log.d("PrijsWijs", "Notification shown and confirmed.")
                        }
                    }, 15000)
                }

                // Stop service after showing the notification
                stopSelf()
            } catch (e: Exception) {
                Log.e("PrijsWijs", "Notification failed to show or data fetch error", e)
                val errorMessage = "⚠️ Failed to update prices. ⚠️\n${e.message}"
                withContext(Dispatchers.Main) {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(FINAL_NOTIFICATION_ID, notificationBuilder.buildFinalNotification(errorMessage, isError = true))
                }
                stopSelf()
            }
        }
    }

    private val wideDigits = setOf('0', '4', '6', '8', '9')
    private val thinDigits = setOf('1', '2', '3', '5', '7')

    // Calculate the “visual width” of a string (only considering digits and punctuation)
    private fun calculateWidth(numberString: String): Int {
        return numberString.sumOf { char ->
            when (char) {
                in wideDigits -> 2
                in thinDigits -> 1
                '.', ',' -> 1  // punctuation width (adjust if needed)
                else -> 1       // fallback width
            }.toInt()
        }
    }

    // Returns a string of spaces needed to pad the numeric part to the target width,
// then appends the given suffix.
    private fun padSuffix(numberString: String, suffix: String, targetWidth: Int): String {
        val currentWidth = calculateWidth(numberString)
        val spacesNeeded = (targetWidth - currentWidth) / 2
        return " ".repeat(spacesNeeded.coerceAtLeast(0)) + suffix
    }

    private fun generateHourlyNotificationMessage(priceData: PriceData, warningMessage: String): String {
        var returnString = if (warningMessage.isNotEmpty()) warningMessage + "\n" else ""
        val dateFormat = SimpleDateFormat("HH:mm", Locale.US)
        val range = priceData.peakPrice - priceData.troughPrice

        val dateTimeEmojiTable = mapOf(
            4 to "\uD83C\uDF11",
            6 to "\uD83C\uDF05",
            8 to "🌄",
            16 to "☀️",
            19 to "🌆",
            23 to "\uD83C\uDF19"
        )

        // Compute the maximum width of the numeric part among all prices.
        // We format the price without the euro symbol for the measurement.
        val maxWidth = priceData.priceTimeMap.values
            .map { "%.2f".format(it) }
            .map { calculateWidth(it) }
            .maxOrNull() ?: 0

        val keysList = priceData.priceTimeMap.keys.toList()

        priceData.priceTimeMap.entries.forEachIndexed { index, entry ->
            val date = entry.key
            val price = entry.value
            var suffix = ""

            // Determine suffix based on dynamic thresholds
            if (range > 0) {
                val peakThreshold1 = priceData.peakPrice - 0.1 * range
                val peakThreshold2 = priceData.peakPrice - 0.4 * range
                val troughThreshold1 = priceData.troughPrice + 0.1 * range
                val troughThreshold2 = priceData.troughPrice + 0.3 * range

                when {
                    price == priceData.priceTimeMap.values.min() -> suffix = "⭐"
                    range < 0.06 -> suffix = ""
                    price > peakThreshold1 && range > 0.15 -> suffix = "‼\uFE0F"
                    price == priceData.priceTimeMap.values.max() || price > peakThreshold2 -> suffix = "❗"
                    price < troughThreshold1 -> suffix = "⭐"
                    price < troughThreshold2 && range > 0.10 -> suffix = "🌱"
                    else -> suffix = ""
                }
            }

            // Add a header line when a new day is detected.
            if (index > 0) {
                val prevDate = keysList[index - 1]
                if (isNewDay(prevDate, date)) {
                    val currentDate = Calendar.getInstance().apply { time = date }
                    returnString += "\uD83C\uDF11 | —— ${currentDate.get(Calendar.DAY_OF_MONTH)} ${currentDate.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)} ——\n"
                }
            }

            // Format the price and then align the suffix based on the numeric width.
            if (index == 0) {
                // "Now" line
                val formattedPrice = "€%.2f".format(price)
                // Remove the euro symbol for measurement
                val numberPart = formattedPrice.substring(1)
                val alignedSuffix = padSuffix(numberPart, suffix, maxWidth)
                returnString += "\uD83D\uDCA1 |  Now   -  $formattedPrice$alignedSuffix\n"
            } else {
                val formattedDate = dateFormat.format(date)
                var hourValue = formattedDate.split(":")[0].toInt()
                while (!dateTimeEmojiTable.containsKey(hourValue)) {
                    hourValue = (hourValue + 1) % 24
                }
                val formattedPrice = "€%.2f".format(price)
                val numberPart = formattedPrice.substring(1)
                val alignedSuffix = padSuffix(numberPart, suffix, maxWidth)
                returnString += dateTimeEmojiTable[hourValue] + " | $formattedDate  -  $formattedPrice$alignedSuffix\n"
            }
        }
        return returnString
    }

    private fun isNewDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR) ||
                cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
    }


    override fun onBind(intent: Intent?) = null
}
