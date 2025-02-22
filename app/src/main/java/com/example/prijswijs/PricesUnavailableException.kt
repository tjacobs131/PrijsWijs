package com.example.prijswijs

import java.util.Date

class PricesUnavailableException: Exception() {
  // Custom exception for when prices are unavailable
  // Should be able to return old prices in this case
  public lateinit var oldPrices: Triple<Map<Date, Double>, Double, Double>

  override fun toString(): String {
    return "Prices are currently unavailable"
  }

}