package com.example.prijswijs

import android.content.Context

class Persistence(context: Context) {

  companion object {
    private var instance: Persistence? = null

    fun getInstance(context: Context): Persistence {
      if (instance == null) {
        instance = Persistence(context)
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


}