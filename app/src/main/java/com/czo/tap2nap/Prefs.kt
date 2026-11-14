package com.czo.tap2nap

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val KEY_HOME = "home_enabled"
    const val KEY_LOCK = "lock_enabled"
    const val KEY_TIMEOUT = "double_tap_timeout"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_VIBRATION_STRENGTH = "vibration_strength"
    const val KEY_ONBOARDING_DONE = "onboarding_done"
    const val KEY_THEME = "theme"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val KEY_POCKET = "pocket_protection"
    // Version whose update popup was closed with "Later".
    const val KEY_DISMISSED_VERSION = "dismissed_update_version"

    const val DEFAULT_TIMEOUT = 300   // max ms between the two taps
    const val MIN_TIMEOUT = 150
    const val MAX_TIMEOUT = 600

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
