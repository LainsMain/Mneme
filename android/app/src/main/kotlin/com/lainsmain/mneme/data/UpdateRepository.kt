package com.lainsmain.mneme.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.lainsmain.mneme.BuildConfig
import com.lainsmain.mneme.UpdateInstallActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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
    val sha256: String?,
)

class UpdateRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences("mneme_updates", Context.MODE_PRIVATE)
    private val downloadManager = applicationContext.getSystemService(DownloadManager::class.java)

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
                    sha256 = apk?.get("digest")?.jsonPrimitive?.content
                        ?.removePrefix("sha256:")
                        ?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                        ?.lowercase(),
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
            sha256 = preferences.getString(KEY_SHA256, null),
        )
    }

    private fun saveRelease(release: ReleaseInfo?) {
        preferences.edit().apply {
            putLong(KEY_CHECKED_AT, System.currentTimeMillis())
            if (release == null) {
                remove(KEY_TAG)
                remove(KEY_RELEASE_URL)
                remove(KEY_DOWNLOAD_URL)
                remove(KEY_SHA256)
            } else {
                putString(KEY_TAG, release.tag)
                putString(KEY_RELEASE_URL, release.releaseUrl)
                putString(KEY_DOWNLOAD_URL, release.downloadUrl)
                release.sha256?.let { putString(KEY_SHA256, it) } ?: remove(KEY_SHA256)
            }
        }.apply()
    }

    fun download(release: ReleaseInfo): Result<Long> = runCatching {
        require(release.downloadUrl.endsWith(".apk", ignoreCase = true)) {
            "This release does not contain an Android installer."
        }
        require(release.sha256 != null) {
            "This release does not provide a checksum, so Mneme will not install it automatically."
        }
        preferences.getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it >= 0L }?.let { downloadManager.remove(it) }
        val fileName = "Mneme-${release.version}.apk"
        applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { File(it, fileName) }
            ?.takeIf(File::exists)
            ?.delete()
        val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
            .setTitle("Mneme ${release.version}")
            .setDescription("Downloading the signed Mneme update")
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                applicationContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName,
            )
        downloadManager.enqueue(request).also { downloadId ->
            preferences.edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_DOWNLOAD_VERSION, release.version)
                .putString(KEY_DOWNLOAD_SHA256, release.sha256)
                .apply()
        }
    }

    fun currentDownload(): UpdateDownloadState {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId < 0L) return UpdateDownloadState(UpdateDownloadPhase.Idle)
        val downloadedVersion = preferences.getString(KEY_DOWNLOAD_VERSION, null)
        if (downloadedVersion == null || !SemanticVersion.isNewer(downloadedVersion, BuildConfig.VERSION_NAME)) {
            clearTrackedDownload(downloadId)
            return UpdateDownloadState(UpdateDownloadPhase.Idle)
        }
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) {
                clearTrackedDownload()
                return UpdateDownloadState(UpdateDownloadPhase.Idle)
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else null
            return when (status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_RUNNING,
                -> UpdateDownloadState(UpdateDownloadPhase.Downloading, downloadId, progress)
                DownloadManager.STATUS_SUCCESSFUL -> UpdateDownloadState(
                    UpdateDownloadPhase.Downloaded,
                    downloadId,
                    100,
                )
                DownloadManager.STATUS_FAILED -> UpdateDownloadState(
                    UpdateDownloadPhase.Error,
                    downloadId,
                    progress,
                    "The update download failed. Try again.",
                )
                else -> UpdateDownloadState(UpdateDownloadPhase.Idle)
            }
        }
    }

    fun isTrackedDownload(downloadId: Long): Boolean =
        downloadId >= 0L && preferences.getLong(KEY_DOWNLOAD_ID, -1L) == downloadId

    fun openInstaller(downloadId: Long): Result<Unit> = runCatching {
        require(isTrackedDownload(downloadId)) { "This download does not belong to Mneme." }
        applicationContext.startActivity(
            Intent(applicationContext, UpdateInstallActivity::class.java)
                .putExtra(UpdateInstallActivity.EXTRA_DOWNLOAD_ID, downloadId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun verifiedDownloadUri(downloadId: Long): Result<Uri> = runCatching {
        require(isTrackedDownload(downloadId)) { "This download does not belong to Mneme." }
        val state = currentDownload()
        require(state.phase == UpdateDownloadPhase.Downloaded) { "The update has not finished downloading." }
        val expected = preferences.getString(KEY_DOWNLOAD_SHA256, null)
            ?: error("The expected update checksum is missing.")
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
            ?: error("Android could not open the downloaded update.")
        val digest = MessageDigest.getInstance("SHA-256")
        applicationContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Android could not read the downloaded update." }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())) {
            "The downloaded update failed its security checksum."
        }
        uri
    }

    private fun clearTrackedDownload(downloadId: Long? = null) {
        downloadId?.let { downloadManager.remove(it) }
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_DOWNLOAD_VERSION)
            .remove(KEY_DOWNLOAD_SHA256)
            .apply()
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/LainsMain/Mneme/releases/latest"
        const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_TAG = "tag"
        const val KEY_RELEASE_URL = "release_url"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_SHA256 = "sha256"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_DOWNLOAD_VERSION = "download_version"
        const val KEY_DOWNLOAD_SHA256 = "download_sha256"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

enum class UpdateDownloadPhase { Idle, Downloading, Downloaded, Error }

data class UpdateDownloadState(
    val phase: UpdateDownloadPhase,
    val downloadId: Long = -1L,
    val progress: Int? = null,
    val message: String? = null,
)

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
