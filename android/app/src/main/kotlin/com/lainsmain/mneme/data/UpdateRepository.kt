package com.lainsmain.mneme.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ReleaseInfo(
    val version: String,
    val tag: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

class UpdateRepository(context: Context) {
    private val preferences = context.getSharedPreferences("mneme_updates", Context.MODE_PRIVATE)

    suspend fun latestRelease(force: Boolean = false): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!force && System.currentTimeMillis() - preferences.getLong(KEY_CHECKED_AT, 0L) < CHECK_INTERVAL) {
                return@runCatching cachedRelease()
            }
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                connection.setRequestProperty("User-Agent", "Mneme-Android")
                if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    saveRelease(null)
                    return@runCatching null
                }
                require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "GitHub returned HTTP ${connection.responseCode}."
                }
                val root = Json.parseToJsonElement(
                    connection.inputStream.bufferedReader().use { it.readText() },
                ).jsonObject
                val tag = root.getValue("tag_name").jsonPrimitive.content
                val releaseUrl = root.getValue("html_url").jsonPrimitive.content
                val apk = root["assets"]?.jsonArray
                    ?.map { it.jsonObject }
                    ?.firstOrNull { asset ->
                        asset["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true
                    }
                val release = ReleaseInfo(
                    version = tag.removePrefix("v"),
                    tag = tag,
                    downloadUrl = apk?.get("browser_download_url")?.jsonPrimitive?.content ?: releaseUrl,
                    releaseUrl = releaseUrl,
                )
                saveRelease(release)
                release
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun cachedRelease(): ReleaseInfo? {
        val tag = preferences.getString(KEY_TAG, null) ?: return null
        val releaseUrl = preferences.getString(KEY_RELEASE_URL, null) ?: return null
        return ReleaseInfo(
            version = tag.removePrefix("v"),
            tag = tag,
            downloadUrl = preferences.getString(KEY_DOWNLOAD_URL, releaseUrl) ?: releaseUrl,
            releaseUrl = releaseUrl,
        )
    }

    private fun saveRelease(release: ReleaseInfo?) {
        preferences.edit().apply {
            putLong(KEY_CHECKED_AT, System.currentTimeMillis())
            if (release == null) {
                remove(KEY_TAG)
                remove(KEY_RELEASE_URL)
                remove(KEY_DOWNLOAD_URL)
            } else {
                putString(KEY_TAG, release.tag)
                putString(KEY_RELEASE_URL, release.releaseUrl)
                putString(KEY_DOWNLOAD_URL, release.downloadUrl)
            }
        }.apply()
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/LainsMain/Mneme/releases/latest"
        const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_TAG = "tag"
        const val KEY_RELEASE_URL = "release_url"
        const val KEY_DOWNLOAD_URL = "download_url"
    }
}

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: Boolean,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
            .takeIf { it != 0 }
            ?: when {
                prerelease == other.prerelease -> 0
                prerelease -> -1
                else -> 1
            }

    companion object {
        fun parse(value: String): SemanticVersion? {
            val match = VERSION.matchEntire(value.trim().removePrefix("v")) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                prerelease = match.groupValues[4].isNotEmpty(),
            )
        }

        fun isNewer(candidate: String, current: String): Boolean {
            val candidateVersion = parse(candidate) ?: return false
            val currentVersion = parse(current) ?: return false
            return candidateVersion > currentVersion
        }

        private val VERSION = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)([-+].*)?$")
    }
}
