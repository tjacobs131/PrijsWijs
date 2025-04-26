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
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.ui.res.stringArrayResource
import com.example.prijswijs.Alarms.AlarmScheduler
import com.example.prijswijs.EnergyPriceDataSource.CachedPricesUnavailableException
import com.example.prijswijs.EnergyPriceDataSource.EnergyPriceAPI
import com.example.prijswijs.MainActivity
import com.example.prijswijs.Persistence.Persistence
import com.example.prijswijs.Model.PriceData
import com.example.prijswijs.Model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class EnergyNotificationService : Service(), CoroutineScope by MainScope() {
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

    override fun onDestroy() {
        cancel()         // cancels everything launched from this scope
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        initPaint(this)
    }

    private companion object {
        private const val NOTIFICATION_ID = 1337420
        private const val FINAL_NOTIFICATION_ID = 1338

        private lateinit var paint: Paint
        fun initPaint(context: Context) {
            // get the system default text size in SP
            val textSize = TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)

            paint = Paint().apply {
                this.textSize = textSize
                typeface = Typeface.MONOSPACE
            }
        }

        val dateFormat = SimpleDateFormat("HH:mm", Locale.US)

        // 0‑23 mapped once, then reused.
        val dateTimeEmojiTable = (0..23).associateWith { h ->
            when (h) {
                in 6..11  -> "\uD83C\uDF05"  // Morning city
                in 12..17 -> "🏙️"  // Daytime city
                in 18..22 -> "🌆"  // Sunset city
                else      -> "🌃"  // Night city
            }
        }
    }

    private fun showNotification(context: Context) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("PrijsWijs", "Fetching energy prices...")
                lateinit var prices: PriceData
                try {
                    prices = EnergyPriceAPI().getTodaysEnergyPrices(context)
                } catch (cacheEx: CachedPricesUnavailableException) {
                    Log.e("PrijsWijs", "Failed to fetch prices", cacheEx)
                    val errorMessage = "Error: " + cacheEx.message
                    notificationManager.notify(FINAL_NOTIFICATION_ID, notificationBuilder.buildFinalNotification(errorMessage, isError = true))
                    stopSelf()
                    return@launch
                }

                Log.d("PrijsWijs", "Prices fetched successfully.")
                val message = generateHourlyNotificationMessage(prices)
                Log.d("PrijsWijs", "Showing message: $message")

                withContext(Dispatchers.Main) {
                    notificationManager.notify(FINAL_NOTIFICATION_ID, notificationBuilder.buildFinalNotification(message))
                    Log.d("PrijsWijs", "Notification shown")

                    // Delay check to confirm the notification is active
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (notificationManager.activeNotifications.none { it.id == FINAL_NOTIFICATION_ID }) {
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
                notificationManager.notify(FINAL_NOTIFICATION_ID, notificationBuilder.buildFinalNotification(errorMessage, isError = true))
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
        val baseLine = "$emoji | $formattedDate  -  $formattedPrice"
        val baseWidth = paint.measureText(baseLine)
        val spaceWidth = paint.measureText(" ")

        // Calculate the pixel gap until the target width.
        val neededPx = targetWidth - baseWidth
        // Round up so we fill the gap exactly.
        val spaceCount = if (neededPx > 0) ceil(neededPx / spaceWidth!!).toInt() else 0

        val padding = " ".repeat(spaceCount)
        return baseLine + padding + "\t" + suffix
    }


    private fun generateHourlyNotificationMessage(priceData: PriceData): String {
        val stringBuilder = StringBuilder(512)
        val range = priceData.peakPrice - priceData.troughPrice

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

        // Set whether notifications should vibrate.
        if (lastPrice.peakPrice != -99.0) {
            val lastPriceRange = PriceRange(lastPrice.peakPrice, lastPrice.troughPrice)
            val lastSuffix = generateSuffix(
                lastPrice.priceTimeMap!!.values.first(),
                lastPriceRange,
            )
            if (lastSuffix in listOf("❗", "‼️")) {
                notificationBuilder.doVibration(settings.vibrate)
            } else {
                notificationBuilder.doVibration(false)
            }
        } else {
            notificationBuilder.doVibration(settings.vibrate)
        }

        var formattedPrice = "€%.2f".format(priceData.priceTimeMap.values.first())
        var suffix = if (range > 0) {
            generateSuffix(
                priceData.priceTimeMap.values.first(),
                PriceRange(priceData.peakPrice, priceData.troughPrice)
            )
        } else {
            ""
        }
        stringBuilder.appendLine(formatLine(
            "\uD83D\uDCA1", " Now ", formattedPrice, suffix, targetWidth)
        )

        // Second pass: build final message using the computed targetWidth.
        priceData.priceTimeMap.entries.forEachIndexed { index, entry ->
            if (index == 0) return@forEachIndexed // Skip the first entry (now line)

            val date = entry.key
            val price = entry.value
            formattedPrice = "€%.2f".format(price)

            if (range > 0) {
                val priceRange = PriceRange(priceData.peakPrice, priceData.troughPrice)

                suffix = generateSuffix(
                    price,
                    priceRange
                )
            }
            val prevDate = keysList[index - 1]
            if (isNewDay(prevDate, date)) {
                val currentDate = Calendar.getInstance().apply { time = date }
                stringBuilder.appendLine(
                    "\uD83C\uDF03   —— ${currentDate.get(Calendar.DAY_OF_MONTH)} ${currentDate.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)} ——"
                )
            }

            stringBuilder.appendLine(
                formatLine(
                    baseLineData[index].first,
                    baseLineData[index].second,
                    baseLineData[index].third,
                    suffix,
                    targetWidth
                )
            )
        }

        return stringBuilder.toString().trimEnd()
    }

    data class PriceRange(
        val peak: Double,
        val trough: Double,
    ) {
        val range = peak - trough
        val peak1  = peak - 0.1 * range
        val peak2  = peak - 0.4 * range
        val low1   = trough + 0.1 * range
        val low2   = trough + 0.3 * range
    }

    private fun generateSuffix(
        price: Double,
        range: PriceRange,
    ) = when {
        price < range.low1
            -> " |⭐"

        range.range < 0.06
        && price < range.low2
            -> " |"

        range.range < 0.06
        && price > range.peak1
        -> " |❗"

        price > range.peak1
            -> " |‼\uFE0F"

        price > range.peak2
        && range.range > 0.10
            -> " |❗"

        price < range.low2
        && range.range > 0.10
            -> " |🌱"

        else -> " |"
    }

    private fun isNewDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR) ||
                cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
    }

    override fun onBind(intent: Intent?) = null
}
