package com.diego.dobletoque

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Background update check, run from the accessibility service (which is always alive).
 * At most once a day; notifies only once per new version.
 */
object UpdateNotifier {
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 1
    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

    fun checkIfDue(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs.get(app)
        if (!prefs.getBoolean(Prefs.KEY_UPDATE_NOTIFY, true)) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(Prefs.KEY_LAST_UPDATE_CHECK, 0L) < ONE_DAY_MS) return

        Thread {
            val release = UpdateChecker.fetchLatest() ?: return@Thread
            prefs.edit().putLong(Prefs.KEY_LAST_UPDATE_CHECK, now).apply()
            val alreadyNotified = prefs.getString(Prefs.KEY_LAST_NOTIFIED_VERSION, null)
            if (release.isNewer && release.version != alreadyNotified) {
                if (notify(app, release)) {
                    prefs.edit().putString(Prefs.KEY_LAST_NOTIFIED_VERSION, release.version).apply()
                }
            }
        }.start()
    }

    private fun notify(context: Context, release: UpdateChecker.Release): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.areNotificationsEnabled()) return false

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nap Tap ${release.version} is available")
            .setContentText("You have ${BuildConfig.VERSION_NAME}. Tap to download the update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
        return true
    }
}
