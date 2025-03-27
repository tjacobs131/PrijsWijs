package com.example.prijswijs.Model

import java.util.Calendar
import java.util.Date

class PriceData(
  val priceTimeMap: MutableMap<Date, Double>?,
  val peakPrice: Double,
  val troughPrice: Double) {

  fun filterMapTimes(): PriceData{
    val amsterdamTZ = java.util.TimeZone.getTimeZone("Europe/Amsterdam")
    val calendar = Calendar.getInstance(amsterdamTZ).apply {
      time = Date()
      add(Calendar.HOUR_OF_DAY, -1)
    }
    val localStartTime = calendar.time
    priceTimeMap?.entries?.removeIf { it.key.before(localStartTime) }

    return this
  }


}