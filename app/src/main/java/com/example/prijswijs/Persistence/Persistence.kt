package com.example.prijswijs.Persistence

import android.content.Context
import com.example.prijswijs.Model.PriceData
import com.example.prijswijs.Model.Settings
import java.util.Date

class Persistence() {

  companion object {
    private var instance: Persistence? = null

    fun getInstance(): Persistence {
      if (instance == null) {
        instance = Persistence()
      }
      return instance!!
    }
  }

  fun saveSettings(context: Context, settings: Settings) {
    // Save settings to disk using SharedPreferences
    val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val editor = sharedPrefs.edit()
    editor.putBoolean("vibrate", settings.vibrate)
    editor.putInt("bedTime", settings.bedTime)
    editor.putInt("wakeUpTime", settings.wakeUpTime)
    editor.apply()
  }

  fun loadSettings(context: Context): Settings {
    val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val settings = Settings()

    // Load settings from disk using SharedPreferences
    settings.vibrate = sharedPrefs.getBoolean("vibrate", false)
    settings.bedTime = sharedPrefs.getInt("bedTime", 21)
    settings.wakeUpTime = sharedPrefs.getInt("wakeUpTime", 6)

    return settings
  }

  fun saveCachedPrices(context: Context, priceData: PriceData){
    val editor = context.getSharedPreferences("cachedPrices", Context.MODE_PRIVATE).edit()

    editor.putFloat("peakPrice", priceData.peakPrice.toFloat())
    editor.putFloat("troughPrice", priceData.troughPrice.toFloat())

    val dates = priceData.priceTimeMap!!.keys
    for ((index, date) in dates.withIndex()){
      editor.putLong("date$index", date.time)
    }

    val prices = priceData.priceTimeMap.values
    for ((index, price) in prices.withIndex()){
      editor.putFloat("price$index", price.toFloat())
    }

    editor.apply()
  }

  fun loadCachedPrices(context: Context): PriceData{
    val sharedPreferences = context.getSharedPreferences("cachedPrices", Context.MODE_PRIVATE)

    val peakPrice = sharedPreferences.getFloat("peakPrice", 0.0F).toDouble()
    val troughPrice = sharedPreferences.getFloat("troughPrice", 0.0F).toDouble()

    val priceMap = mutableMapOf<Date, Double>()
    var index = 0
    while (true){
      val price = sharedPreferences.getFloat("price$index", -9999.0F)
      val date = Date(sharedPreferences.getLong("date$index", -9999))
      if (price == -9999.0F || date.time.toInt() == -9999){
        break
      }
      priceMap[date] = price.toDouble()
      index++
    }

    return PriceData(priceMap, peakPrice, troughPrice)
  }

}