package com.example.prijswijs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow

class EnergyPriceAPI {
    private companion object{
        lateinit var lastPrices: JSONArray
    }
    suspend fun getTodaysEnergyPrices(): Triple<Map<Date, Double>, Double, Double> = withContext(Dispatchers.IO) {
        var prices: JSONArray? = null

        try {
            // Calculate time range in local time (Europe/Amsterdam)
            val amsterdamTZ = java.util.TimeZone.getTimeZone("Europe/Amsterdam")
            val calendar = Calendar.getInstance(amsterdamTZ).apply {
                time = Date() // Current time in Amsterdam
                add(Calendar.HOUR_OF_DAY, -1) // Start from previous hour
            }
            val localStartTime = calendar.time

            calendar.time = Date()
            calendar.add(Calendar.HOUR_OF_DAY, 20) // Cover next 20 hours
            val localEndTime = calendar.time

            // Format API request dates as UTC
            val utcDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val utcStartDate = utcDateFormat.format(localStartTime)
            val utcEndDate = utcDateFormat.format(localEndTime)

            // Fetch data using UTC timestamps
            val url = createUrl(utcStartDate, utcEndDate)
            val response = sendGet(url)

            prices = JSONObject(response).getJSONArray("Prices")
            lastPrices = prices // Store the fetched prices as lastPrices

        } catch (ex: JSONException){
            val exception = PricesUnavailableException()
            exception.setOldPrices(processPrices(lastPrices))
            throw exception
        }

        return@withContext processPrices(prices) // Call extracted function to process prices, which can be null or lastPrices
    }

    private fun processPrices(prices: JSONArray?): Triple<Map<Date, Double>, Double, Double> {
        val datesAndPrices = mutableMapOf<Date, Double>()

        if (prices == null || prices.length() == 0) {
            return Triple(emptyMap(), 0.0, 0.0) // Return default if no prices available
        }

        // Calculate time range in local time (Europe/Amsterdam) again for date comparisons - could be passed as parameter for optimization if needed in a hot loop
        val amsterdamTZ = java.util.TimeZone.getTimeZone("Europe/Amsterdam")
        val calendar = Calendar.getInstance(amsterdamTZ).apply {
            time = Date() // Current time in Amsterdam
            add(Calendar.HOUR_OF_DAY, -1) // Start from previous hour
        }
        val localStartTime = calendar.time


        // Parse response dates as UTC and convert to Amsterdam time
        val readDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        for (i in 0 until prices.length()) {
            val priceInfo = prices.getJSONObject(i)
            val utcDate = readDateFormat.parse(priceInfo.getString("readingDate"))!!

            // Skip past times, but include the current hour
            if (utcDate.before(localStartTime)) {
                continue
            }

            // Convert UTC Date to Amsterdam time representation
            val amsterdamCalendar = Calendar.getInstance(amsterdamTZ).apply {
                timeInMillis = utcDate.time
            }
            val amsterdamDate = amsterdamCalendar.time

            datesAndPrices[amsterdamDate] = priceInfo.getDouble("price")
        }

        // Sort entries chronologically
        val sortedEntries = datesAndPrices.entries.sortedBy { it.key }
        val totalEntries = sortedEntries.size
        val desiredMaxEntries = 10

        val selectedEntries = if (totalEntries <= desiredMaxEntries) {
            sortedEntries
        } else {
            val selected = mutableListOf<Map.Entry<Date, Double>>()
            val keepInitialEntries = 4
            selected.addAll(sortedEntries.take(keepInitialEntries))

            val remainingEntries = sortedEntries.subList(keepInitialEntries, sortedEntries.size)
            val neededEntries = desiredMaxEntries - keepInitialEntries

            val power = 2.0 // Adjust this value to control the rate of step increase
            for (i in 0 until neededEntries) {
                val fraction: Double =
                    if (neededEntries == 1) 0.0 else i.toDouble() / (neededEntries - 1)
                val indexInRemaining =
                    (fraction.pow(power) * (remainingEntries.size - 1)).toInt()
                selected.add(remainingEntries[indexInRemaining])
            }

            selected
        }

        // Calculate global min/max from ALL prices (not just filtered)
        val maxPrice = datesAndPrices.values.maxOrNull() ?: 0.0
        val minPrice = datesAndPrices.values.minOrNull() ?: 0.0

        return Triple(
            selectedEntries.associate { it.key to it.value },  // Filtered subset
            maxPrice,  // Global maximum
            minPrice   // Global minimum
        )
    }

    private fun sendGet(url: URL): String {
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            return inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun createUrl(fromDate: String, tillDate: String): URL {
        return URL(
            "https://api.energyzero.nl/v1/energyprices" +
                    "?fromDate=$fromDate&tillDate=$tillDate&interval=4&usageType=1&inclBtw=true"
        )
    }
}
