package com.nextick.app.core

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateChecker {
    data class Info(
        val tag: String,
        val url: String,
        val newer: Boolean
    )

    fun releasesUrl(repo: String): String =
        "https://github.com/$repo/releases"

    fun check(
        repo: String,
        currentVersion: String,
        callback: (Result<Info>) -> Unit
    ) {
        val main = Handler(Looper.getMainLooper())

        thread {
            val result = runCatching {
                val conn =
                    URL("https://api.github.com/repos/$repo/releases/latest")
                        .openConnection() as HttpURLConnection

                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
                )
                conn.setRequestProperty(
                    "User-Agent",
                    "NextTick-Android/$currentVersion"
                )
                conn.setRequestProperty(
                    "X-GitHub-Api-Version",
                    "2022-11-28"
                )

                val code = conn.responseCode
                if (code !in 200..299) {
                    val error = conn.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    conn.disconnect()
                    error("GitHub HTTP $code $error")
                }

                val body =
                    conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val tag = json.optString("tag_name")
                val url = json.optString(
                    "html_url",
                    releasesUrl(repo)
                )

                if (tag.isBlank()) {
                    error("Release tag is empty")
                }

                val latest = tag.removePrefix("v").trim()
                val current = currentVersion.trim()

                Info(
                    tag = tag,
                    url = url.ifBlank { releasesUrl(repo) },
                    newer = isNewer(latest, current)
                )
            }

            main.post {
                callback(result)
            }
        }
    }

    private fun isNewer(
        latest: String,
        current: String
    ): Boolean {
        val a =
            latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b =
            current.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }

            if (x != y) {
                return x > y
            }
        }

        return false
    }
}
