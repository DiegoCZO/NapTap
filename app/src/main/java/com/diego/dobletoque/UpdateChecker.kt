package com.diego.dobletoque

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Reads the latest release of the (public) GitHub repo and compares it with this version. */
object UpdateChecker {

    data class Release(
        val version: String,
        val apkUrl: String?,
        val pageUrl: String,
        /** Changelog (the GitHub release description), cleaned up for plain text. */
        val notes: String
    ) {
        val isNewer: Boolean get() = isNewer(version, BuildConfig.VERSION_NAME)
        /** Direct APK download if the release has one, otherwise the release page. */
        val downloadUrl: String get() = apkUrl ?: pageUrl
    }

    val repoUrl: String get() = "https://github.com/${BuildConfig.GITHUB_REPO}"

    /** Blocking network call: never run it on the main thread. Returns null on any error. */
    fun fetchLatest(): Release? = try {
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "NapTap")
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

    /** Light Markdown → plain text: "- x" / "* x" → "• x", no "#" headings, no "**". */
    private fun cleanNotes(markdown: String): String =
        markdown.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains("/compare/") }
            .joinToString("\n") { line ->
                val l = line.trimStart('#').trim().replace("**", "")
                if (l.startsWith("- ") || l.startsWith("* ")) "• " + l.substring(2) else l
            }

    suspend fun fetchLatestAsync(): Release? = withContext(Dispatchers.IO) { fetchLatest() }

    /** "1.10" > "1.9" > "1.2.1" > "1.2" */
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
