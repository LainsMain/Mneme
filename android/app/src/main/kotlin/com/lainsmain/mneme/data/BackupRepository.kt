package com.lainsmain.mneme.data

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class BackupResult(
    val revision: Long,
    val pageCount: Int,
    val photoCount: Int,
    val uploadedBytes: Long,
)

class BackupRepository(
    private val context: Context,
    private val diaryDao: DiaryDao,
    private val settingsRepository: AppSettingsRepository,
) {
    suspend fun backupNow(): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepository.settings.value
            require(settings.serverConnected && settings.serverUrl.isNotBlank() && settings.serverToken.isNotBlank()) {
                "Connect your Mneme server first."
            }
            val pages = diaryDao.pagesForBackup()
            val attachments = diaryDao.attachmentsForBackup()
            var uploadedBytes = 0L
            val encryptedObjects = mutableMapOf<String, EncryptedObject>()

            try {
                attachments.forEach { attachment ->
                    val original = File(attachment.encryptedFileName)
                    if (original.isFile) {
                        val cachedHash = objectCache().getString(attachment.sha256, null)
                            ?.takeIf { objectExists(settings, it) }
                        val encrypted = cachedHash?.let { EncryptedObject(file = null, hash = it) }
                            ?: encryptFile(original)
                        encryptedObjects[attachment.id] = encrypted
                        if (encrypted.file != null && !objectExists(settings, encrypted.hash)) {
                            uploadObject(settings, encrypted)
                            uploadedBytes += encrypted.file.length()
                            objectCache().edit().putString(attachment.sha256, encrypted.hash).apply()
                        }
                    }
                }

                val revision = System.currentTimeMillis().coerceAtLeast(1L)
                val manifest = buildManifest(revision, pages, attachments, encryptedObjects)
                val encryptedManifest = encryptBytes(manifest.toString().toByteArray())
                val manifestFile = requireNotNull(encryptedManifest.file)
                try {
                    uploadManifest(settings, revision, encryptedManifest)
                    uploadedBytes += manifestFile.length()
                } finally {
                    manifestFile.delete()
                }
                BackupResult(
                    revision = revision,
                    pageCount = pages.size,
                    photoCount = encryptedObjects.size,
                    uploadedBytes = uploadedBytes,
                )
            } finally {
                encryptedObjects.values.forEach { it.file?.delete() }
            }
        }
    }

    private fun buildManifest(
        revision: Long,
        pages: List<DiaryPageEntity>,
        attachments: List<AttachmentEntity>,
        objects: Map<String, EncryptedObject>,
    ): JsonObject = buildJsonObject {
        put("format", "mneme-vault-v1")
        put("deviceId", deviceId())
        put("revision", revision)
        put("createdAtEpochMillis", System.currentTimeMillis())
        put("pages", buildJsonArray {
            pages.forEach { page ->
                add(buildJsonObject {
                    put("id", page.id)
                    put("date", page.diaryDate)
                    put("document", page.documentJson)
                    put("plainText", page.plainText)
                    put("createdAtEpochMillis", page.createdAtEpochMillis)
                    put("updatedAtEpochMillis", page.updatedAtEpochMillis)
                    put("revision", page.revision)
                    page.deletedAtEpochMillis?.let { put("deletedAtEpochMillis", it) }
                    page.locationName?.let { put("locationName", it) }
                    page.latitude?.let { put("latitude", it) }
                    page.longitude?.let { put("longitude", it) }
                    put("locationIsManual", page.locationIsManual)
                })
            }
        })
        put("attachments", buildJsonArray {
            attachments.forEach { attachment ->
                val encrypted = objects[attachment.id] ?: return@forEach
                add(buildJsonObject {
                    put("id", attachment.id)
                    put("pageId", attachment.pageId)
                    put("objectHash", encrypted.hash)
                    attachment.originalFileName?.let { put("originalFileName", it) }
                    put("mimeType", attachment.mimeType)
                    put("byteSize", attachment.byteSize)
                    attachment.width?.let { put("width", it) }
                    attachment.height?.let { put("height", it) }
                    attachment.capturedAtEpochMillis?.let { put("capturedAtEpochMillis", it) }
                    attachment.latitude?.let { put("latitude", it) }
                    attachment.longitude?.let { put("longitude", it) }
                    attachment.altitudeMeters?.let { put("altitudeMeters", it) }
                    attachment.cameraMake?.let { put("cameraMake", it) }
                    attachment.cameraModel?.let { put("cameraModel", it) }
                    attachment.lensModel?.let { put("lensModel", it) }
                    attachment.exposureTime?.let { put("exposureTime", it) }
                    attachment.aperture?.let { put("aperture", it) }
                    attachment.iso?.let { put("iso", it) }
                    attachment.focalLength?.let { put("focalLength", it) }
                    put("caption", attachment.caption)
                    put("sortOrder", attachment.sortOrder)
                    put("normalizedExif", attachment.normalizedExifJson)
                })
            }
        })
    }

    private fun encryptFile(source: File): EncryptedObject {
        val target = File.createTempFile("mneme-backup-", ".vault", context.cacheDir)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, vaultKey()) }
        FileOutputStream(target).use { output ->
            output.write(MAGIC)
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            CipherOutputStream(output, cipher).use { encrypted -> source.inputStream().use { it.copyTo(encrypted) } }
        }
        return EncryptedObject(target, sha256(target))
    }

    private fun encryptBytes(source: ByteArray): EncryptedObject {
        val target = File.createTempFile("mneme-manifest-", ".vault", context.cacheDir)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, vaultKey()) }
        FileOutputStream(target).use { output ->
            output.write(MAGIC)
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            CipherOutputStream(output, cipher).use { it.write(source) }
        }
        return EncryptedObject(target, sha256(target))
    }

    private fun objectExists(settings: AppSettings, hash: String): Boolean {
        val connection = authenticatedConnection(settings, "/v1/objects/$hash", "HEAD")
        return try {
            connection.responseCode == HttpURLConnection.HTTP_OK
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadObject(settings: AppSettings, encrypted: EncryptedObject) {
        val file = requireNotNull(encrypted.file)
        val connection = authenticatedConnection(settings, "/v1/objects/${encrypted.hash}", "PUT")
        try {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(file.length())
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.outputStream.use { output -> file.inputStream().use { it.copyTo(output) } }
            require(connection.responseCode == HttpURLConnection.HTTP_CREATED) {
                "Object upload returned HTTP ${connection.responseCode}."
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadManifest(settings: AppSettings, revision: Long, encrypted: EncryptedObject) {
        val file = requireNotNull(encrypted.file)
        val connection = authenticatedConnection(settings, "/v1/manifests/${deviceId()}", "PUT")
        try {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(file.length())
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("X-Mneme-Revision", revision.toString())
            connection.outputStream.use { output -> file.inputStream().use { it.copyTo(output) } }
            require(connection.responseCode == HttpURLConnection.HTTP_CREATED) {
                "Manifest upload returned HTTP ${connection.responseCode}."
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun authenticatedConnection(settings: AppSettings, path: String, method: String): HttpURLConnection =
        (URL(settings.serverUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer ${settings.serverToken}")
            setRequestProperty("Accept", "application/json")
        }

    private fun deviceId(): String {
        val preferences = context.getSharedPreferences("mneme_backup", Context.MODE_PRIVATE)
        preferences.getString(KEY_DEVICE_ID, null)?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,48}")) }
        val value = "android-${androidId ?: UUID.randomUUID().toString().replace("-", "")}".take(64)
        preferences.edit().putString(KEY_DEVICE_ID, value).apply()
        return value
    }

    private fun objectCache() = context.getSharedPreferences("mneme_backup_objects", Context.MODE_PRIVATE)

    private fun vaultKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class EncryptedObject(val file: File?, val hash: String)

    private companion object {
        val MAGIC = "MNEME1".toByteArray(Charsets.US_ASCII)
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "mneme_vault_key_v1"
        const val KEY_DEVICE_ID = "device_id"
    }
}
