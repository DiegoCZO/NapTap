package com.diego.dobletoque

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Versions 1.6 to 1.11 had an option to hide the icon. If it was hidden, show it again.
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
