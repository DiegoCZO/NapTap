package com.diego.dobletoque

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val KEY_HOME = "home_enabled"
    const val KEY_LOCK = "lock_enabled"
    const val KEY_TIMEOUT = "double_tap_timeout"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_POCKET = "pocket_protection"
    /** Version whose popup was closed with "Later" (not shown again on app open). */
    const val KEY_DISMISSED_VERSION = "dismissed_update_version"

    const val DEFAULT_TIMEOUT = 300   // max ms between the two taps
    const val MIN_TIMEOUT = 150
    const val MAX_TIMEOUT = 600

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
