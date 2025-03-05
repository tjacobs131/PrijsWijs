package com.example.prijswijs.Notifications

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.prijswijs.EnergyPriceDataSource.CachedPricesUnavailableException
import com.example.prijswijs.EnergyPriceDataSource.EnergyPriceAPI
import com.example.prijswijs.Persistence.Persistence
import com.example.prijswijs.Model.PriceData
import com.example.prijswijs.Model.Settings
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

    private val persistence: Persistence by lazy { Persistence() }
    private val settings: Settings by lazy { persistence.loadSettings(this) }
    private val notificationBuilder by lazy { NotificationBuilder(this, settings) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create channels and start service in foreground with a processing notification.
        notificationBuilder.createNotificationChannels()
        startForeground(NOTIFICATION_ID, notificationBuilder.buildProcessingNotification())
        showNotification(this)
        return START_NOT_STICKY
    }

    private fun showNotification(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("PrijsWijs", "Fetching energy prices...")
                lateinit var prices: PriceData
                try {
                    prices = EnergyPriceAPI().getTodaysEnergyPrices(context)
                } catch (cacheEx: CachedPricesUnavailableException) {
                    Log.e("PrijsWijs", "Failed to fetch prices", cacheEx)
                    val errorMessage = "Error: " + cacheEx.message
                    withContext(Dispatchers.Main) {
                        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                            .notify(FINAL_NOTIFICATION_ID, notificationBuilder.buildFinalNotification(errorMessage, isError = true))
                    }
                    stopSelf()
                }

                Log.d("PrijsWijs", "Prices fetched successfully.")
                val message = generateHourlyNotificationMessage(prices)
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
                    }, 5000)
                }

                // Stop service after showing the notification
                stopSelf()
            } catch (e: Exception) {
                Log.e("PrijsWijs", "Notification failed to show", e)
                val errorMessage = "Error: Notification failed to show"
                withContext(Dispatchers.Main) {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(FINAL_NOTIFICATION_ID, notificationBuilder.buildFinalNotification(errorMessage, isError = true))
                }
                stopSelf()
            }
        }
    }

    // Returns a string of fixed-width figure spaces needed to pad the numeric part to the target pixel width,
// then appends the given suffix.
    private fun padSuffix(
        numberString: String,
        suffix: String,
    ): String {
        if (numberString.count { it == '1' } == 2) {
            return " $suffix"
        } else if (numberString.count { it == '1'} == 3 && suffix.contains("❗")) {
            return " $suffix"
        } else if (numberString.count { it == '1' } == 4) {
            return "  $suffix"
        }

        return suffix
    }

    private fun generateHourlyNotificationMessage(priceData: PriceData): String {
        var returnString = ""
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

        // Create a Paint object for text measurement using a monospaced font.
        // Ensure that the textSize and typeface match those of the notification text.
        val paint = Paint().apply {
            textSize = 16f // set to your actual notification text size in pixels
            typeface = Typeface.MONOSPACE // ensures fixed width digits
        }

        // Compute the maximum width (in pixels) of the numeric part among all prices.
        val maxWidthPx = priceData.priceTimeMap!!.values
            .map { price -> paint.measureText("%.2f".format(price)) }
            .maxOrNull() ?: 0f

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

                suffix = when {
                    price == priceData.priceTimeMap.values.min() -> "⭐"
                    range < 0.06 -> ""
                    price > peakThreshold1 && range > 0.15 -> "‼\uFE0F"
                    price == priceData.priceTimeMap.values.max() || price > peakThreshold2 -> "❗"
                    price < troughThreshold1 -> "⭐"
                    price < troughThreshold2 && range > 0.10 -> "🌱"
                    else -> ""
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

            // Format the price and then align the suffix based on the measured width.
            if (index == 0) {
                // "Now" line
                val formattedPrice = "€%.2f".format(price)
                // Remove the euro symbol for measurement
                val numberPart = formattedPrice.substring(1)
                val alignedSuffix = padSuffix(numberPart, suffix)
                returnString += "\uD83D\uDCA1 |  Now   -  $formattedPrice$alignedSuffix\n"
            } else {
                val formattedDate = dateFormat.format(date)
                var hourValue = formattedDate.split(":")[0].toInt()
                while (!dateTimeEmojiTable.containsKey(hourValue)) {
                    hourValue = (hourValue + 1) % 24
                }
                val formattedPrice = "€%.2f".format(price)
                val numberPart = formattedPrice.substring(1)
                val alignedSuffix = padSuffix(numberPart, suffix)
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
