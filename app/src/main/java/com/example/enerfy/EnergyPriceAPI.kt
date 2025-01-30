import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class EnergyPriceAPI {
    suspend fun getTodaysEnergyPrices(): Triple<Map<Date, Double>, Double, Double> = withContext(Dispatchers.IO) {
        val datesAndPrices = mutableMapOf<Date, Double>()

        // Calculate time range in local time (Europe/Amsterdam)
        val amsterdamTZ = java.util.TimeZone.getTimeZone("Europe/Amsterdam")
        val calendar = Calendar.getInstance(amsterdamTZ).apply {
            time = Date() // Current time in Amsterdam
            add(Calendar.HOUR_OF_DAY, -1) // Start from previous hour
        }
        val localStartTime = calendar.time

        calendar.time = Date()
        calendar.add(Calendar.HOUR_OF_DAY, 24) // Cover next 24 hours
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
        val prices = JSONObject(response).getJSONArray("Prices")

        // Parse response dates as UTC and convert to Amsterdam time
        val readDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        for (i in 0 until prices.length()) {
            val priceInfo = prices.getJSONObject(i)
            val utcDate = readDateFormat.parse(priceInfo.getString("readingDate"))!!

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
            val keepInitialEntries = 4 // Keep first 3 entries for detailed view
            selected.addAll(sortedEntries.take(keepInitialEntries))

            val remainingEntries = totalEntries - keepInitialEntries
            val neededEntries = desiredMaxEntries - keepInitialEntries

            if (neededEntries > 0) {
                val step = remainingEntries.toDouble() / neededEntries.toDouble()
                var currentPosition = keepInitialEntries.toDouble()

                repeat(neededEntries) {
                    val index = currentPosition.toInt()
                    if (index < totalEntries) {
                        selected.add(sortedEntries[index])
                    }
                    currentPosition += step
                }
            }
            selected
        }

        val filteredDatesAndPrices = selectedEntries.associate { it.key to it.value }
        val maxPrice = filteredDatesAndPrices.values.maxOrNull() ?: 0.0
        val minPrice = filteredDatesAndPrices.values.minOrNull() ?: 0.0

        return@withContext Triple(filteredDatesAndPrices, maxPrice, minPrice)
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