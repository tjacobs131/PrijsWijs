package com.example.prijswijs

import com.example.prijswijs.EnergyPriceDataSource.EnergyPriceAPI
import com.example.prijswijs.EnergyPriceDataSource.PricesUnavailableException
import com.example.prijswijs.Model.PriceData
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

/**
 * Dummy PricesUnavailableException for testing.
 */
class PricesUnavailableException : Exception() {
  var oldPrices: PriceData? = null
}

class EnergyPriceAPITest {

  // Helper to generate a fake JSON response.
  private fun generateFakeResponse(numEntries: Int): String {
    val pricesArray = JSONArray()
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
    // Arrange: stub the private sendGet method.
    val api = spyk(EnergyPriceAPI())
    val fakeResponse = generateFakeResponse(12)
    every { api["sendGet"](any<URL>()) } returns fakeResponse

    // Act
    val priceData = api.getTodaysEnergyPrices()

    // Assert: Expect exactly 10 prices and global min/max from fake data.
    assertEquals(10, priceData.priceTimeMap.size)
    assertEquals(12.0, priceData.peakPrice)
    assertEquals(1.0, priceData.troughPrice)
  }

  @Test
  fun testGetTodaysEnergyPricesRetryLogic() = runBlocking {
    // Arrange: simulate two failures followed by a successful call.
    val api = spyk(EnergyPriceAPI())
    val fakeResponse = generateFakeResponse(12)
    var callCount = 0

    every { api["sendGet"](any<URL>()) } answers {
      callCount++
      if (callCount < 3) throw Exception("Simulated network error")
      else fakeResponse
    }

    // Act
    val priceData = api.getTodaysEnergyPrices()

    // Assert: Check that three attempts were made and data is as expected.
    assertEquals(10, priceData.priceTimeMap.size)
    assertEquals(12.0, priceData.peakPrice)
    assertEquals(1.0, priceData.troughPrice)
    assertEquals(3, callCount)
  }

  @Test
  fun testGetTodaysEnergyPricesFailureWithLastPrices() = runBlocking {
    // Arrange: first get a successful call to set cached prices.
    val api = spyk(EnergyPriceAPI())
    val fakeResponse = generateFakeResponse(12)
    every { api["sendGet"](any<URL>()) } returns fakeResponse

    val firstResult = api.getTodaysEnergyPrices()
    assertEquals(10, firstResult.priceTimeMap.size)

    // Now simulate failures on every call.
    clearMocks(api)
    every { api["sendGet"](any<URL>()) } throws Exception("Simulated network error")

    // Act & Assert: expect a PricesUnavailableException with oldPrices set.
    val exception = assertThrows<PricesUnavailableException> {
      runBlocking { api.getTodaysEnergyPrices() }
    }
    val oldPrices = exception.oldPrices
    assertEquals(12.0, oldPrices?.peakPrice)
    assertEquals(1.0, oldPrices?.troughPrice)
  }
}
