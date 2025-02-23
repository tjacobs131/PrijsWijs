package com.example.prijswijs.EnergyPriceDataSource

import com.example.prijswijs.Model.PriceData

class PricesUnavailableException: Exception() {
  // Custom exception for when prices are unavailable
  // Should be able to return old prices in this case
  public lateinit var oldPrices: PriceData

  override fun toString(): String {
    return "Prices are currently unavailable"
  }

}