package com.example.prijswijs

import java.util.Date

class PricesUnavailableException: Exception() {
  // Custom exception for when prices are unavailable
  // Should be able to return old prices in this case
  private lateinit var oldPrices: Triple<Map<Date, Double>, Double, Double>
  fun getOldPrices(): Triple<Map<Date, Double>, Double, Double> {
    return oldPrices
  }

  fun setOldPrices(_oldPrices: Triple<Map<Date, Double>, Double, Double>) {
    oldPrices = _oldPrices
  }

  override fun toString(): String {
    return "Prices are currently unavailable"
  }

}