package com.diego.dobletoque

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Checks the latest GitHub release (the repo has to be public).
object UpdateChecker {

    data class Release(
        val version: String,
        val apkUrl: String?,
        val pageUrl: String,
        // Text of the GitHub release, without the Markdown.
        val notes: String
    ) {
        val isNewer: Boolean get() = isNewer(version, BuildConfig.VERSION_NAME)
        // The APK if the release has one, otherwise the release page.
        val downloadUrl: String get() = apkUrl ?: pageUrl
    }

    val repoUrl: String get() = "https://github.com/${BuildConfig.GITHUB_REPO}"

    // Network call, don't run it on the main thread. Returns null if anything fails.
    fun fetchLatest(): Release? = try {
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Tap2Nap")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                var apk: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk")) {
                            apk = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                Release(
                    version = json.optString("tag_name").removePrefix("v"),
                    apkUrl = apk,
                    pageUrl = json.optString("html_url", repoUrl),
                    notes = cleanNotes(json.optString("body"))
                )
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    // Turns list items into bullets and removes headings and bold.
    private fun cleanNotes(markdown: String): String =
        markdown.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains("/compare/") }
            .joinToString("\n") { line ->
                val l = line.trimStart('#').trim().replace("**", "")
                if (l.startsWith("- ") || l.startsWith("* ")) "• " + l.substring(2) else l
            }

    suspend fun fetchLatestAsync(): Release? = withContext(Dispatchers.IO) { fetchLatest() }

    // Compares numbers part by part, so 1.10 is newer than 1.9.
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
