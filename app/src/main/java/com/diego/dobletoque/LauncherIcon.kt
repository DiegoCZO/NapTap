package com.diego.dobletoque

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Shows / hides the app drawer icon by enabling / disabling the launcher alias. */
object LauncherIcon {
    private const val ALIAS = "com.diego.dobletoque.LauncherAlias"

    private fun component(context: Context) = ComponentName(context.packageName, ALIAS)

    fun isVisible(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setVisible(context: Context, visible: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
