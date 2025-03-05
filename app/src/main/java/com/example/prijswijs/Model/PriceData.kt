package com.example.prijswijs.Model

import java.util.Calendar
import java.util.Date

class PriceData(
  val priceTimeMap: MutableMap<Date, Double>?,
  val peakPrice: Double,
  val troughPrice: Double) {

  fun filterMapTimes(){
    val amsterdamTZ = java.util.TimeZone.getTimeZone("Europe/Amsterdam")
    val calendar = Calendar.getInstance(amsterdamTZ).apply {
      time = Date()
      add(Calendar.HOUR_OF_DAY, -1)
    }
    val localStartTime = calendar.time
    priceTimeMap!!.forEach { date ->
      if (date.key.before(localStartTime)) {
        priceTimeMap.remove(date.key)
      }
    }
  }

}