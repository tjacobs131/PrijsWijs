package com.example.enerfy

import android.icu.util.TimeZone
import android.icu.util.TimeZone.*
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.impl.client.DefaultHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnergyPriceAPI {
    suspend fun getTodaysEnergyPrices(): Map<Date, Double> = withContext(Dispatchers.IO) {
        // Create dictionary to store the dates and prices
        val datesAndPrices = mutableMapOf<Date, Double>()

        // Get the current date and time
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000'Z'", Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")

        val utcStartDate = dateFormat.format(Date(currentDate.time - 1 * 60 * 60 * 1000)) //  ~3~ We have to shift an hour cuz dumb
        val utcEndDate = dateFormat.format(Date(currentDate.time + 9 * 60 * 60 * 1000)) // Add 8 (9) hours in milliseconds

        // Get energy prices for the given period
        val url = createUrl(utcStartDate, utcEndDate)

        // Send the GET request
        val response = sendGet(url)

        // Process the response
        val prices = JSONObject(response).getJSONArray("Prices")
        // ~3~ Shift the values 1 hour back
        val readDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        for (i in 2 until prices.length()) {
            println(prices.getJSONObject(i))

            val oldPriceInfo = prices.getJSONObject(i - 2) // ~3~
            val priceInfo = prices.getJSONObject(i)
            val date = readDateFormat.parse(priceInfo.getString("readingDate"))
            val price = oldPriceInfo.getDouble("price")

            datesAndPrices[date!!] = price
        }

        return@withContext datesAndPrices
    }

    private fun sendGet(url: URL): String {
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"  // optional default is GET
            println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")

            inputStream.bufferedReader().use {
                return it.readText()
            }
        }
    }

    private fun createUrl(utcStartDate: String, utcEndDate: String): URL {
        val urlBuilder = StringBuilder()
        urlBuilder.append("https://api.energyzero.nl/v1/energyprices")
        urlBuilder.append("?fromDate=$utcStartDate")
        urlBuilder.append("&tillDate=$utcEndDate")
        urlBuilder.append("&interval=4")
        urlBuilder.append("&usageType=1")
        urlBuilder.append("&inclBtw=true")
        println(urlBuilder.toString())

        return URL(urlBuilder.toString())
    }
}