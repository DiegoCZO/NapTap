package com.diego.dobletoque

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The app drawer icon is always visible. Versions 1.6 to 1.11 could hide it; if it was
 * hidden, this puts it back to its default (visible) state.
 */
object LauncherIcon {
    private const val ALIAS = "com.diego.dobletoque.LauncherAlias"

    fun ensureVisible(context: Context) {
        val component = ComponentName(context.packageName, ALIAS)
        val pm = context.packageManager
        if (pm.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
