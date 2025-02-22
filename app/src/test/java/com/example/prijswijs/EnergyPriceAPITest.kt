package com.example.prijswijs

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// PricesUnavailableException is assumed to be defined in your project.
// For testing purposes, you can define a dummy version if needed.
class PricesUnavailableException : Exception() {
  var oldPrices: Triple<Map<Date, Double>, Double, Double>? = null
}

class EnergyPriceAPITest {

  // Helper to generate a fake JSON response.
  private fun generateFakeResponse(numEntries: Int): String {
    val pricesArray = JSONArray()
    // Create entries starting from "now + 1 hour" so they're after the localStartTime (now - 1 hour).
    val now = System.currentTimeMillis()
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    for (i in 1..numEntries) {
      val date = Date(now + i * 3600_000)
      val dateStr = sdf.format(date)
      val obj = JSONObject().put("readingDate", dateStr).put("price", i * 1.0)
      pricesArray.put(obj)
    }
    return JSONObject().put("Prices", pricesArray).toString()
  }

  @Test
  fun testGetTodaysEnergyPricesSuccess() = runBlocking {
    // Arrange: spy the API instance and stub the private sendGet method.
    val api = spyk(EnergyPriceAPI())
    val fakeResponse = generateFakeResponse(12)

    // Stub the private sendGet(URL) so that it always returns our fake JSON.
    every { api["sendGet"](any<URL>()) } returns fakeResponse

    // Act
    val result = api.getTodaysEnergyPrices()

    // Assert:
    // According to the selection logic, 12 entries should be reduced to 10.
    assertEquals(10, result.first.size)
    // In our fake data prices go from 1.0 to 12.0
    assertEquals(12.0, result.second) // Global max
    assertEquals(1.0, result.third)   // Global min
  }

  @Test
  fun testGetTodaysEnergyPricesRetryLogic() = runBlocking {
    // Arrange: simulate failure on the first two calls and success on the third.
    val api = spyk(EnergyPriceAPI())
    val fakeResponse = generateFakeResponse(12)
    var callCount = 0

    every { api["sendGet"](any<URL>()) } answers {
      callCount++
      if (callCount < 3) throw Exception("Simulated network error")
      else fakeResponse
    }

    // Act
    val result = api.getTodaysEnergyPrices()

    // Assert
    assertEquals(10, result.first.size)
    assertEquals(12.0, result.second)
    assertEquals(1.0, result.third)
    assertEquals(3, callCount)  // Ensure three attempts were made.
  }

  @Test
  fun testGetTodaysEnergyPricesFailureWithLastPrices() = runBlocking {
    // Arrange: perform one successful call to set lastPrices.
    val api = spyk(EnergyPriceAPI())
    val fakeResponse = generateFakeResponse(12)
    every { api["sendGet"](any<URL>()) } returns fakeResponse

    // First call succeeds.
    val firstResult = api.getTodaysEnergyPrices()
    assertEquals(10, firstResult.first.size)

    // Now, clear previous stubbing and simulate failure on every call.
    clearMocks(api)
    every { api["sendGet"](any<URL>()) } throws Exception("Simulated network error")

    // Act & Assert: expect a com.example.prijswijs.PricesUnavailableException containing old prices.
    val exception = assertThrows<PricesUnavailableException> {
      runBlocking { api.getTodaysEnergyPrices() }
    }
    // Check that oldPrices was set.
    val oldPrices = exception.oldPrices
    // In our fake data, global max is 12.0 and min is 1.0.
    assertEquals(12.0, oldPrices?.second)
    assertEquals(1.0, oldPrices?.third)
  }
}
