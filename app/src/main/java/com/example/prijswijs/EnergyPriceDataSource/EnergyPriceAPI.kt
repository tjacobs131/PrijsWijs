package com.example.prijswijs.EnergyPriceDataSource

import com.example.prijswijs.Model.PriceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow

class EnergyPriceAPI {
    private companion object {
        var cachedPrices: JSONArray? = null
    }

    fun getCachedPrices(): PriceData {
        if (cachedPrices == null || cachedPrices!!.length() == 0) {
            throw CachedPricesUnavailableException("No cached prices available.")
        }
        return processPrices(cachedPrices)
    }

    suspend fun getTodaysEnergyPrices(): PriceData = withContext(Dispatchers.IO) {
        val amsterdamTZ = java.util.TimeZone.getTimeZone("Europe/Amsterdam")
        val calendar = Calendar.getInstance(amsterdamTZ).apply {
            time = Date()
            add(Calendar.HOUR_OF_DAY, -1)
        }
        val localStartTime = calendar.time

        calendar.time = Date()
        calendar.add(Calendar.HOUR_OF_DAY, 32)
        val localEndTime = calendar.time

        val utcDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val utcStartDate = utcDateFormat.format(localStartTime)
        val utcEndDate = utcDateFormat.format(localEndTime)

        val url = createUrl(utcStartDate, utcEndDate)
        var prices: JSONArray? = null
        var retryCountdown = 2

        while (retryCountdown > 0) {
            try {
                val response = sendGet(url)
                prices = JSONObject(response).getJSONArray("Prices")
                cachedPrices = prices
                break
            } catch (ex: Exception) {
                if (--retryCountdown == 0) {
                    throw PricesUnavailableException("No cached prices available.")
                }
            }
        }

        return@withContext processPrices(prices)
    }

    private fun processPrices(prices: JSONArray?): PriceData {
        if (prices == null || prices.length() == 0) {
            return PriceData(emptyMap(), 0.0, 0.0)
        }

        val amsterdamTZ = java.util.TimeZone.getTimeZone("Europe/Amsterdam")
        val calendar = Calendar.getInstance(amsterdamTZ).apply {
            time = Date()
            add(Calendar.HOUR_OF_DAY, -1)
        }
        val localStartTime = calendar.time

        val readDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        val datesAndPrices = mutableMapOf<Date, Double>()
        for (i in 0 until prices.length()) {
            val priceInfo = prices.getJSONObject(i)
            val utcDate = readDateFormat.parse(priceInfo.getString("readingDate"))!!

            if (utcDate.before(localStartTime)) continue

            val amsterdamDate = Calendar.getInstance(amsterdamTZ).apply {
                timeInMillis = utcDate.time
            }.time
            datesAndPrices[amsterdamDate] = priceInfo.getDouble("price")
        }

        val sortedEntries = datesAndPrices.entries.sortedBy { it.key }
        val specialSet = getPeaksAndTroughs(sortedEntries)
        val finalSelection = mutableSetOf<Map.Entry<Date, Double>>().apply {
            addAll(specialSet)
            addAll(sortedEntries.take(4))
        }

        if (finalSelection.size < 10) {
            val remainingEntries = sortedEntries.filter { !finalSelection.contains(it) }
            val additionalNeeded = 10 - finalSelection.size
            val power = 1.8
            for (i in 0 until additionalNeeded) {
                val fraction = if (additionalNeeded == 1) 0.0 else i.toDouble() / (additionalNeeded - 1)
                val index = (fraction.pow(power) * (remainingEntries.size - 1)).toInt()
                finalSelection.add(remainingEntries[index])
            }
        }

        val sortedFinal = finalSelection.sortedBy { it.key }.toMutableList()
        while (sortedFinal.size > 10) {
            sortedFinal.removeIf { !specialSet.contains(it) }
        }

        val maxPrice = datesAndPrices.values.maxOrNull() ?: 0.0
        val minPrice = datesAndPrices.values.minOrNull() ?: 0.0

        return PriceData(sortedFinal.associate { it.key to it.value }, maxPrice, minPrice)
    }

    private fun getPeaksAndTroughs(sortedEntries: List<Map.Entry<Date, Double>>): MutableSet<Map.Entry<Date, Double>> {
        val overallStartTime = sortedEntries.first().key
        val overallEndTime = sortedEntries.last().key
        val totalDuration = overallEndTime.time - overallStartTime.time
        val medianTime = Date(overallStartTime.time + totalDuration / 2)

        val earlyEntries = sortedEntries.filter { it.key.before(medianTime) }
        val lateEntries = sortedEntries.filter { !it.key.before(medianTime) }

        val peak1 = earlyEntries.maxByOrNull { it.value }
        val peak2 = lateEntries.maxByOrNull { it.value }
        val trough1 = earlyEntries.minByOrNull { it.value }
        val trough2 = lateEntries.minByOrNull { it.value }

        return mutableSetOf<Map.Entry<Date, Double>>().apply {
            peak1?.let { add(it) }
            peak2?.let { add(it) }
            trough1?.let { add(it) }
            trough2?.let { add(it) }
        }
    }

    private fun sendGet(url: URL): String {
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            return inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun createUrl(fromDate: String, tillDate: String): URL {
        return URL("https://api.energyzero.nl/v1/energyprices?fromDate=$fromDate&tillDate=$tillDate&interval=4&usageType=1&inclBtw=true")
    }
}