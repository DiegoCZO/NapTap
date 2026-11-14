package com.diego.dobletoque

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Shows / hides the app drawer icon by enabling / disabling the launcher alias.
 *
 * The alias is disabled in the manifest: on Android 10+, that's the only way a hidden icon
 * disappears completely (otherwise Android shows an "App info" icon in its place).
 */
object LauncherIcon {
    private const val ALIAS = "com.diego.dobletoque.LauncherAlias"

    private fun component(context: Context) = ComponentName(context.packageName, ALIAS)

    fun isVisible(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setVisible(context: Context, visible: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Fresh install (icon never shown nor hidden by the user) → show it.
     * If the user chose to hide it, that choice is kept.
     */
    fun showOnFirstRun(context: Context) {
        val state = context.packageManager.getComponentEnabledSetting(component(context))
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) setVisible(context, true)
    }
}
