package com.example.enerfy

import android.content.Context
import android.content.SharedPreferences

class Persistence {

  var context: Context? = null

  constructor(context: Context){
    // Initialize persistence
    this.context = context
  }

  fun saveSettings(settings: Settings) {
    // Save settings to disk using SharedPreferences
    val sharedPrefs = this.context!!.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val editor = sharedPrefs.edit()
    editor.putBoolean("vibrate", settings.vibrate)
    editor.putInt("bedTime", settings.bedTime)
    editor.putInt("wakeUpTime", settings.wakeUpTime)
    editor.apply()
  }

  fun loadSettings(): Settings {
    val sharedPrefs = this.context!!.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val settings = Settings()

    // Load settings from disk using SharedPreferences
    settings.vibrate = sharedPrefs.getBoolean("vibrate", true)
    settings.bedTime = sharedPrefs.getInt("bedTime", 21)
    settings.wakeUpTime = sharedPrefs.getInt("wakeUpTime", 6)

    return settings
  }


}