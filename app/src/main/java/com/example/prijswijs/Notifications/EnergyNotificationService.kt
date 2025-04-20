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
import android.widget.TextView
import com.example.prijswijs.Alarms.AlarmScheduler
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
import kotlin.math.ceil

class EnergyNotificationService : Service() {
    private val NOTIFICATION_ID = 1337420
    private val FINAL_NOTIFICATION_ID = 1338

    private val persistence: Persistence by lazy { Persistence() }
    private val settings: Settings by lazy { persistence.loadSettings(this) }
    private val notificationBuilder by lazy { NotificationBuilder(this, settings) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        this.startForeground(NOTIFICATION_ID, notificationBuilder.buildProcessingNotification())
        // Create channels and start service in foreground with a processing notification.
        notificationBuilder.createNotificationChannels()
        showNotification(this)

        return START_STICKY
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
                    return@launch
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
                            // Stop service after showing the notification
                            stopSelf()
                        }
                    }, 5000)
                }
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

    private fun formatLine(
        emoji: String,
        formattedDate: String,
        formattedPrice: String,
        suffix: String,
        targetWidth: Float
    ): String {
        val textView = TextView(this)
        val systemTextSize = textView.textSize
        val paint = Paint().apply {
            textSize = systemTextSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val baseLine = "$emoji | $formattedDate  -  $formattedPrice"
        val baseWidth = paint.measureText(baseLine)
        val spaceWidth = paint.measureText(" ")

        // Calculate the pixel gap until the target width.
        val neededPx = targetWidth - baseWidth
        // Round up so we fill the gap exactly.
        val spaceCount = if (neededPx > 0) ceil(neededPx / spaceWidth).toInt() else 0

        val padding = " ".repeat(spaceCount)
        return baseLine + padding + "\t" + suffix
    }


    private fun generateHourlyNotificationMessage(priceData: PriceData): String {
        var returnString = ""
        val dateFormat = SimpleDateFormat("HH:mm", Locale.US)
        val range = priceData.peakPrice - priceData.troughPrice

        // 0‑23 mapped once, then reused.
        val dateTimeEmojiTable = (0..23).associateWith { h ->
            when (h) {
                in 6..7   -> "🌅"  // Sunrise
                in 8..11  -> "🌇"  // Morning city
                in 12..17 -> "🏙️"  // Daytime city
                in 18..20 -> "🌆"  // Sunset city
                else      -> "🌃"  // Night city
            }
        }

        // Prepare a Paint instance
        val textView = TextView(this)
        val systemTextSize = textView.textSize
        val paint = Paint().apply {
            textSize = systemTextSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        // Use a fixed tab width based on 4 spaces.
        val spaceWidth = paint.measureText(" ")
        val fixedTabWidth = spaceWidth * 4

        // First pass: compute the maximum base width.
        var maxBaseWidth = 0f
        val keysList = priceData.priceTimeMap!!.keys.toList()
        val baseLineData = mutableListOf<Triple<String, String, String>>()  // (emoji, formattedDate, formattedPrice)

        priceData.priceTimeMap.entries.forEachIndexed { index, entry ->
            val date = entry.key
            val price = entry.value
            val formattedPrice = "€%.2f".format(price)
            val emoji: String
            val formattedDate: String
            if (index == 0) {
                emoji = "\uD83D\uDCA1" // "Now" line
                formattedDate = " Now "
            } else {
                formattedDate = dateFormat.format(date)
                val hourValue = formattedDate.substring(0, 2).toInt()
                emoji = dateTimeEmojiTable[hourValue] ?: "🌃" // Default to night city
            }
            val baseLine = "$emoji | $formattedDate  -  $formattedPrice"
            val width = paint.measureText(baseLine)
            if (width > maxBaseWidth) maxBaseWidth = width
            baseLineData.add(Triple(emoji, formattedDate, formattedPrice))
        }

        // Compute the next tab stop and target width.
        val nextTabStop = (ceil(maxBaseWidth / fixedTabWidth) * fixedTabWidth).toFloat()
        val targetWidth = nextTabStop - (fixedTabWidth / 2f) + spaceWidth

        val lastPrice = persistence.loadLastPrice(this)

        // Second pass: build final message using the computed targetWidth.
        priceData.priceTimeMap.entries.forEachIndexed { index, entry ->
            val date = entry.key
            val price = entry.value
            var suffix = ""
            val formattedPrice = "€%.2f".format(price)

            if (range > 0) {
                val peakThreshold1 = priceData.peakPrice - 0.1 * range
                val peakThreshold2 = priceData.peakPrice - 0.4 * range
                val troughThreshold1 = priceData.troughPrice + 0.1 * range
                val troughThreshold2 = priceData.troughPrice + 0.3 * range

                suffix = generateSuffix(
                    price,
                    priceData.troughPrice,
                    priceData.peakPrice,
                    range,
                    peakThreshold1,
                    peakThreshold2,
                    troughThreshold1,
                    troughThreshold2
                )
                if (index == 0) {
                    if (lastPrice.peakPrice != -99.0) {
                        val lastSuffix = generateSuffix(
                            lastPrice.priceTimeMap!!.values.first(),
                            lastPrice.troughPrice,
                            lastPrice.peakPrice,
                            lastPrice.peakPrice - 0.1 * (lastPrice.peakPrice - lastPrice.troughPrice),
                            lastPrice.peakPrice - 0.1 * (lastPrice.peakPrice - lastPrice.troughPrice),
                            lastPrice.peakPrice - 0.4 * (lastPrice.peakPrice - lastPrice.troughPrice),
                            lastPrice.troughPrice + 0.1 * (lastPrice.peakPrice - lastPrice.troughPrice),
                            lastPrice.troughPrice + 0.3 * (lastPrice.peakPrice - lastPrice.troughPrice)
                        )
                        if (lastSuffix !in listOf("❗", "‼️") && suffix in listOf("❗", "‼️")) {
                            if (settings.vibrate) {
                                notificationBuilder.doVibration(settings.vibrate)
                            } else {
                                notificationBuilder.doVibration(false)
                            }
                        } else {
                            notificationBuilder.doVibration(false)
                        }
                    } else {
                        notificationBuilder.doVibration(settings.vibrate)
                    }
                }
            }

            // Add header line when a new day is detected.
            if (index > 0) {
                val prevDate = keysList[index - 1]
                if (isNewDay(prevDate, date)) {
                    val currentDate = Calendar.getInstance().apply { time = date }
                    returnString += "\uD83C\uDF03   —— ${currentDate.get(Calendar.DAY_OF_MONTH)} ${currentDate.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)} ——\n"
                }
            }

            // Use our precomputed baseLineData along with the targetWidth.
            if (index == 0) {
                returnString += formatLine("\uD83D\uDCA1", " Now ", formattedPrice, suffix, targetWidth) + "\n"
            } else {
                returnString += formatLine(
                    baseLineData[index].first,
                    baseLineData[index].second,
                    baseLineData[index].third,
                    suffix,
                    targetWidth
                ) + "\n"
            }
        }

        return returnString.trimEnd()
    }


    private fun generateSuffix(
        price: Double,
        min: Double,
        max: Double,
        range: Double,
        peakThreshold1: Double,
        peakThreshold2: Double,
        troughThreshold1: Double,
        troughThreshold2: Double
    ) = when {
        price == min -> "|⭐"
        range < 0.06 -> "|"
        price > peakThreshold1 && range > 0.15 -> "|‼\uFE0F"
        price == max || price > peakThreshold2 -> "|❗"
        price < troughThreshold1 -> "|⭐"
        price < troughThreshold2 && range > 0.10 -> "|🌱"
        else -> "|"
    }

    private fun isNewDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR) ||
                cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
    }

    override fun onBind(intent: Intent?) = null
}
