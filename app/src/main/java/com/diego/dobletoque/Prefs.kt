package com.diego.dobletoque

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val KEY_HOME = "home_enabled"
    const val KEY_LOCK = "lock_enabled"
    const val KEY_TIMEOUT = "double_tap_timeout"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_POCKET = "pocket_protection"
    const val KEY_UPDATE_NOTIFY = "update_notify"
    const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"

    const val DEFAULT_TIMEOUT = 300   // max ms between the two taps
    const val MIN_TIMEOUT = 150
    const val MAX_TIMEOUT = 600

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
