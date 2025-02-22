package com.example.prijswijs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
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

        var retryCountdown: Int = 3
        while (true) {
            try {
                val response = sendGet(url)

                prices = JSONObject(response).getJSONArray("Prices")
                lastPrices = prices // Store the fetched prices as lastPrices

                break

            } catch (ex: Exception){
                if (retryCountdown == 0) {
                    val exception = PricesUnavailableException()
                    exception.oldPrices = processPrices(lastPrices)
                    throw exception
                }
                retryCountdown--
            }
        }

        return@withContext processPrices(prices) // Call extracted function to process prices, which can be null or lastPrices
    }

    private fun processPrices(prices: JSONArray?): Triple<Map<Date, Double>, Double, Double> {
        val datesAndPrices = mutableMapOf<Date, Double>()
        if (prices == null || prices.length() == 0) {
            return Triple(emptyMap(), 0.0, 0.0)
        }

        val amsterdamTZ = java.util.TimeZone.getTimeZone("Europe/Amsterdam")
        val calendar = Calendar.getInstance(amsterdamTZ).apply {
            time = Date()
            add(Calendar.HOUR_OF_DAY, -1)
        }
        val localStartTime = calendar.time

        val readDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        for (i in 0 until prices.length()) {
            val priceInfo = prices.getJSONObject(i)
            val utcDate = readDateFormat.parse(priceInfo.getString("readingDate"))!!
            if (utcDate.before(localStartTime)) continue

            val amsterdamCalendar = Calendar.getInstance(amsterdamTZ).apply { timeInMillis = utcDate.time }
            val amsterdamDate = amsterdamCalendar.time
            datesAndPrices[amsterdamDate] = priceInfo.getDouble("price")
        }

        val sortedEntries = datesAndPrices.entries.sortedBy { it.key }
        // Calculate overall time span and median time
        val overallStartTime = sortedEntries.first().key
        val overallEndTime = sortedEntries.last().key
        val totalDuration = overallEndTime.time - overallStartTime.time
        val medianTime = Date(overallStartTime.time + totalDuration / 2)
        val minGap = totalDuration / 3  // threshold if we need fallback

        // Split entries into two groups based on median time
        val earlyEntries = sortedEntries.filter { it.key.before(medianTime) }
        val lateEntries = sortedEntries.filter { !it.key.before(medianTime) }

        // Select peaks: highest price from each group if possible
        var peak1: Map.Entry<Date, Double>? = earlyEntries.maxByOrNull { it.value }
        var peak2: Map.Entry<Date, Double>? = lateEntries.maxByOrNull { it.value }
        // Fallback if one group is empty:
        if (peak1 == null || peak2 == null) {
            val candidatePeaks = sortedEntries.sortedByDescending { it.value }
            if (candidatePeaks.isNotEmpty()) {
                peak1 = candidatePeaks[0]
                peak2 = candidatePeaks.drop(1).firstOrNull {
                    kotlin.math.abs(it.key.time - peak1!!.key.time) >= minGap
                } ?: if (candidatePeaks.size > 1) candidatePeaks[1] else null
            }
        }

        // Select troughs: lowest price from each group if possible
        var trough1: Map.Entry<Date, Double>? = earlyEntries.minByOrNull { it.value }
        var trough2: Map.Entry<Date, Double>? = lateEntries.minByOrNull { it.value }
        if (trough1 == null || trough2 == null) {
            val candidateTroughs = sortedEntries.sortedBy { it.value }
            if (candidateTroughs.isNotEmpty()) {
                trough1 = candidateTroughs[0]
                trough2 = candidateTroughs.drop(1).firstOrNull {
                    kotlin.math.abs(it.key.time - trough1!!.key.time) >= minGap
                } ?: if (candidateTroughs.size > 1) candidateTroughs[1] else null
            }
        }

        // Build a set with the special entries
        val specialSet = mutableSetOf<Map.Entry<Date, Double>>().apply {
            if (peak1 != null) add(peak1)
            if (peak2 != null) add(peak2)
            if (trough1 != null) add(trough1)
            if (trough2 != null) add(trough2)
        }

        // Then fill the final selection with initial entries near current time
        val desiredMaxEntries = 10
        val finalSelection = mutableSetOf<Map.Entry<Date, Double>>().apply {
            addAll(specialSet)
        }
        val keepInitialEntries = 4
        for (entry in sortedEntries.take(keepInitialEntries)) {
            if (finalSelection.size < desiredMaxEntries) finalSelection.add(entry)
        }
        // Fill remaining slots using a non-linear distribution from the rest
        if (finalSelection.size < desiredMaxEntries) {
            val remainingEntries = sortedEntries.filter { !finalSelection.contains(it) }
            val additionalNeeded = desiredMaxEntries - finalSelection.size
            val power = 2.0
            for (i in 0 until additionalNeeded) {
                val fraction = if (additionalNeeded == 1) 0.0 else i.toDouble() / (additionalNeeded - 1)
                val index = (fraction.pow(power) * (remainingEntries.size - 1)).toInt()
                finalSelection.add(remainingEntries[index])
            }
        }
        // Trim if needed while keeping special entries
        val sortedFinal = finalSelection.sortedBy { it.key }.toMutableList()
        while (sortedFinal.size > desiredMaxEntries) {
            val candidate = sortedFinal.firstOrNull { !specialSet.contains(it) }
            if (candidate != null) {
                sortedFinal.remove(candidate)
            } else {
                break
            }
        }

        val maxPrice = datesAndPrices.values.maxOrNull() ?: 0.0
        val minPrice = datesAndPrices.values.minOrNull() ?: 0.0

        return Triple(sortedFinal.associate { it.key to it.value }, maxPrice, minPrice)
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
