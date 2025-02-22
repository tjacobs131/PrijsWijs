package com.example.prijswijs.EnergyZeroAPI

import com.example.prijswijs.EnergyZeroAPI.PriceData

class PricesUnavailableException: Exception() {
  // Custom exception for when prices are unavailable
  // Should be able to return old prices in this case
  public lateinit var oldPrices: PriceData

  override fun toString(): String {
    return "Prices are currently unavailable"
  }

}