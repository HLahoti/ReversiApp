package com.example.reversi.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [UpdateChecker] for the sideloaded app: compares the latest GitHub release
 * with the installed version, and opens the release page to update.
 *
 * Only needed while the app is not on the Play Store; see [UpdateChecker].
 *
 * Relies on two release conventions: every release is tagged `v<versionName>`
 * (e.g. `v1.6` for versionName "1.6"), and drafts and pre-releases are never
 * offered, which GitHub's `releases/latest` endpoint already guarantees. A
 * tag that doesn't parse as a version is ignored rather than offered.
 *
 * The request is unauthenticated: GitHub allows 60 an hour per IP, far above
 * the once a day [Updates] asks. It sends the player's IP address to GitHub.
 */
class GitHubReleaseChecker(
    private val installedVersion: String,
    private val repo: String = REPO,
) : UpdateChecker {

    override suspend fun latest(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$repo/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("GitHub answered ${conn.responseCode}")
            }
            parseLatest(conn.inputStream.bufferedReader().use { it.readText() }, installedVersion)
        } finally {
            conn.disconnect()
        }
    }

    override fun startUpdate(activity: Activity, update: AvailableUpdate) {
        val url = update.url ?: "https://github.com/$repo/releases/latest"
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser; nothing more we can do.
        }
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
    )

    companion object {
        const val REPO = "HLahoti/ReversiApp"
        private const val TIMEOUT_MS = 10_000
        private val json = Json { ignoreUnknownKeys = true }

        /** The update in a `releases/latest` response, or null if it isn't newer. */
        internal fun parseLatest(body: String, installedVersion: String): AvailableUpdate? {
            val release = json.decodeFromString(Release.serializer(), body)
            val version = release.tagName.removePrefix("v")
            if (!isNewer(version, installedVersion)) return null
            return AvailableUpdate(
                versionName = version,
                title = release.name?.takeIf { it.isNotBlank() },
                url = release.htmlUrl,
            )
        }

        /**
         * True if dotted version [candidate] is above [installed], comparing
         * part by part as numbers ("1.10" > "1.9", "1.5.1" > "1.5"). False if
         * either doesn't parse, so a malformed tag never prompts an update.
         */
        internal fun isNewer(candidate: String, installed: String): Boolean {
            val a = parts(candidate) ?: return false
            val b = parts(installed) ?: return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(v: String): List<Int>? =
            v.split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
    }
}
