package com.diego.dobletoque

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val KEY_HOME = "home_enabled"
    const val KEY_LOCK = "lock_enabled"
    const val KEY_TIMEOUT = "double_tap_timeout"
    const val KEY_VIBRATE = "vibrate"

    const val DEFAULT_TIMEOUT = 300   // max ms between the two taps
    const val MIN_TIMEOUT = 150
    const val MAX_TIMEOUT = 600

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
