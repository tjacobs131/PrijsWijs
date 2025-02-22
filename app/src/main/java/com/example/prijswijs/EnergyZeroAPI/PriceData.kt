package com.example.prijswijs.EnergyZeroAPI

import java.util.Date
import kotlin.properties.Delegates

class PriceData(
  val priceTimeMap: Map<Date, Double>,
  val peakPrice: Double,
  val troughPrice: Double) {

}