package com.diego.dobletoque

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update: downloads the release APK and hands it to Android's PackageInstaller.
 * Android always shows its own "Update this app?" confirmation; after installing, the app
 * closes (that's how Android updates work) and the accessibility service keeps running.
 */
object AppUpdater {

    /** Android 8+: the user must allow Tap2Nap to "install unknown apps" (once). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Downloads the APK to the cache. [onProgress] gets 0..1, or null if the size is unknown.
     * Returns the file, or null if the download failed.
     */
    suspend fun download(context: Context, url: String, onProgress: (Float?) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "update.apk")
            try {
                file.delete()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true   // GitHub redirects to its file CDN
                    setRequestProperty("User-Agent", "Tap2Nap")
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                    val total = conn.contentLengthLong
                    var done = 0L
                    var lastReported = -1
                    conn.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent != lastReported) {
                                        lastReported = percent
                                        onProgress(percent / 100f)
                                    }
                                } else {
                                    onProgress(null)
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                file
            } catch (e: Exception) {
                file.delete()
                null
            }
        }

    /** Sends the APK to the system installer. The result arrives in [InstallResultReceiver]. */
    suspend fun install(context: Context, apk: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val callback = PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    flags
                )
                session.commit(callback.intentSender)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** Receives the installer's answer: shows Android's confirmation dialog, or an error. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // The app is replaced and closed by Android; nothing else to do.
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // The user tapped "Cancel" in Android's dialog.
            }
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Toast.makeText(
                    context, context.getString(R.string.install_failed, detail), Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
