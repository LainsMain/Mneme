package com.lainsmain.mneme.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class BackupResult(
    val revision: Long,
    val pageCount: Int,
    val photoCount: Int,
    val uploadedBytes: Long,
)

data class RestoreResult(
    val pageCount: Int,
    val photoCount: Int,
    val recapCount: Int,
)

class BackupRepository(
    private val context: Context,
    private val diaryDao: DiaryDao,
    private val settingsRepository: AppSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val recoveryKeyManager = RecoveryKeyManager(context)
    private val activityPreferences = context.getSharedPreferences("mneme_backup_activity", Context.MODE_PRIVATE)
    private val backupMutex = Mutex()

    fun recoveryCode(): String = recoveryKeyManager.recoveryCode()

    fun recoveryCodeNeedsSaving(): Boolean = recoveryKeyManager.needsAcknowledgement()

    fun acknowledgeRecoveryCode() = recoveryKeyManager.acknowledge()

    fun lastSuccessfulBackupAt(): Long? =
        activityPreferences.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L }

    fun lastBackupError(): String? = activityPreferences.getString(KEY_LAST_ERROR, null)

    suspend fun backupNow(): Result<BackupResult> = backupMutex.withLock {
        val result = withContext(Dispatchers.IO) { runCatching { performBackup() } }
        result.fold(
            onSuccess = {
                activityPreferences.edit()
                    .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
                    .remove(KEY_LAST_ERROR)
                    .apply()
            },
            onFailure = { error ->
                activityPreferences.edit()
                    .putString(KEY_LAST_ERROR, error.message ?: "Backup failed.")
                    .apply()
            },
        )
        result
    }

    suspend fun restoreFromServer(recoveryCode: String): Result<RestoreResult> = backupMutex.withLock {
        withContext(Dispatchers.IO) { runCatching {
            require(diaryDao.localContentCount() == 0) {
                "Restore is only available on an empty Mneme installation to avoid overwriting local entries."
            }
            val settings = connectedSettings()
            val keyBytes = RecoveryKeyManager.parseCode(recoveryCode)
            val createdFiles = mutableListOf<File>()
            try {
                val pointers = json.decodeFromString<List<RemoteManifestPointer>>(
                    downloadBytes(settings, "/v1/manifests").toString(Charsets.UTF_8),
                )
                val manifest = pointers.sortedByDescending(RemoteManifestPointer::revision)
                    .firstNotNullOfOrNull { pointer ->
                        runCatching {
                            val encrypted = downloadBytes(settings, "/v1/manifests/${pointer.deviceId}")
                            json.decodeFromString<VaultManifest>(decryptBytes(encrypted, keyBytes).toString(Charsets.UTF_8))
                                .takeIf { it.format == VAULT_FORMAT }
                        }.getOrNull()
                    }
                    ?: error(
                        "No backup matches this recovery code. Backups made before Mneme 0.1.5 must be " +
                            "backed up again from the original phone.",
                    )

                val pages = manifest.pages.map { page ->
                    DiaryPageEntity(
                        id = page.id,
                        diaryDate = page.date,
                        documentJson = page.document,
                        plainText = page.plainText,
                        createdAtEpochMillis = page.createdAtEpochMillis,
                        updatedAtEpochMillis = page.updatedAtEpochMillis,
                        revision = page.revision,
                        deletedAtEpochMillis = page.deletedAtEpochMillis,
                        locationName = page.locationName,
                        latitude = page.latitude,
                        longitude = page.longitude,
                        locationIsManual = page.locationIsManual,
                    )
                }
                val attachmentDirectory = File(context.filesDir, "attachments").apply { mkdirs() }
                val attachments = manifest.attachments.map { attachment ->
                    require(attachment.id.matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
                        "The backup contains an invalid attachment identifier."
                    }
                    require(attachment.objectHash.matches(Regex("[a-f0-9]{64}"))) {
                        "The backup contains an invalid object hash."
                    }
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(attachment.mimeType)
                        ?: attachment.originalFileName?.substringAfterLast('.', "")
                            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
                        ?: "image"
                    val original = File(attachmentDirectory, "${attachment.id}.original.$extension")
                    val encrypted = File.createTempFile("mneme-restore-", ".vault", context.cacheDir)
                    try {
                        downloadFile(settings, "/v1/objects/${attachment.objectHash}", encrypted)
                        decryptFile(encrypted, original, keyBytes)
                    } finally {
                        encrypted.delete()
                    }
                    createdFiles += original
                    require(sha256(original) == attachment.plaintextSha256) {
                        "A restored photo failed its integrity check."
                    }
                    val thumbnail = File(attachmentDirectory, "${attachment.id}.thumbnail.jpg")
                        .takeIf { createThumbnail(original, it) }
                    thumbnail?.let(createdFiles::add)
                    AttachmentEntity(
                        id = attachment.id,
                        pageId = attachment.pageId,
                        encryptedFileName = original.absolutePath,
                        thumbnailFileName = thumbnail?.absolutePath,
                        originalFileName = attachment.originalFileName,
                        mimeType = attachment.mimeType,
                        byteSize = original.length(),
                        width = attachment.width,
                        height = attachment.height,
                        sha256 = attachment.plaintextSha256,
                        caption = attachment.caption,
                        sortOrder = attachment.sortOrder,
                        capturedAtEpochMillis = attachment.capturedAtEpochMillis,
                        latitude = attachment.latitude,
                        longitude = attachment.longitude,
                        altitudeMeters = attachment.altitudeMeters,
                        cameraMake = attachment.cameraMake,
                        cameraModel = attachment.cameraModel,
                        lensModel = attachment.lensModel,
                        exposureTime = attachment.exposureTime,
                        aperture = attachment.aperture,
                        iso = attachment.iso,
                        focalLength = attachment.focalLength,
                        normalizedExifJson = attachment.normalizedExif,
                        createdAtEpochMillis = attachment.createdAtEpochMillis,
                    )
                }
                val recaps = manifest.monthlyRecaps.map { recap ->
                    MonthlyRecapEntity(
                        id = recap.id,
                        yearMonth = recap.yearMonth,
                        documentJson = recap.document,
                        plainText = recap.plainText,
                        createdAtEpochMillis = recap.createdAtEpochMillis,
                        updatedAtEpochMillis = recap.updatedAtEpochMillis,
                        revision = recap.revision,
                        deletedAtEpochMillis = recap.deletedAtEpochMillis,
                    )
                }
                recoveryKeyManager.importRecoveryCode(recoveryCode)
                diaryDao.restoreBackup(pages, attachments, recaps)
                objectCache().edit().clear().apply()
                RestoreResult(pages.size, attachments.size, recaps.size)
            } catch (error: Exception) {
                createdFiles.forEach(File::delete)
                throw error
            } finally {
                keyBytes.fill(0)
            }
        } }
    }

    private suspend fun performBackup(): BackupResult {
        val settings = connectedSettings()
        val pages = diaryDao.pagesForBackup()
        val attachments = diaryDao.attachmentsForBackup()
        val recaps = diaryDao.recapsForBackup()
        val keyBytes = recoveryKeyManager.keyBytes()
        var uploadedBytes = 0L
        val encryptedObjects = mutableMapOf<String, EncryptedObject>()
        try {
            val keyFingerprint = sha256(keyBytes).take(16)
            attachments.forEach { attachment ->
                val original = File(attachment.encryptedFileName)
                require(original.isFile) {
                    "A photo belonging to this diary is missing from local storage. Backup stopped to avoid data loss."
                }
                val cacheKey = "$VAULT_FORMAT:$keyFingerprint:${attachment.sha256}"
                val cachedHash = objectCache().getString(cacheKey, null)
                    ?.takeIf { objectExists(settings, it) }
                val encrypted = cachedHash?.let { EncryptedObject(file = null, hash = it) }
                    ?: encryptFile(original, keyBytes)
                encryptedObjects[attachment.id] = encrypted
                if (encrypted.file != null && !objectExists(settings, encrypted.hash)) {
                    uploadObject(settings, encrypted)
                    uploadedBytes += encrypted.file.length()
                    objectCache().edit().putString(cacheKey, encrypted.hash).apply()
                }
            }

            val revision = System.currentTimeMillis().coerceAtLeast(1L)
            val manifest = buildManifest(revision, pages, attachments, recaps, encryptedObjects)
            val encryptedManifest = encryptBytes(json.encodeToString(manifest).toByteArray(), keyBytes)
            val manifestFile = requireNotNull(encryptedManifest.file)
            try {
                uploadManifest(settings, revision, encryptedManifest)
                uploadedBytes += manifestFile.length()
            } finally {
                manifestFile.delete()
            }
            return BackupResult(revision, pages.size, encryptedObjects.size, uploadedBytes)
        } finally {
            encryptedObjects.values.forEach { it.file?.delete() }
            keyBytes.fill(0)
        }
    }

    private fun buildManifest(
        revision: Long,
        pages: List<DiaryPageEntity>,
        attachments: List<AttachmentEntity>,
        recaps: List<MonthlyRecapEntity>,
        objects: Map<String, EncryptedObject>,
    ) = VaultManifest(
        format = VAULT_FORMAT,
        deviceId = deviceId(),
        revision = revision,
        createdAtEpochMillis = System.currentTimeMillis(),
        pages = pages.map { page ->
            VaultPage(
                id = page.id,
                date = page.diaryDate,
                document = page.documentJson,
                plainText = page.plainText,
                createdAtEpochMillis = page.createdAtEpochMillis,
                updatedAtEpochMillis = page.updatedAtEpochMillis,
                revision = page.revision,
                deletedAtEpochMillis = page.deletedAtEpochMillis,
                locationName = page.locationName,
                latitude = page.latitude,
                longitude = page.longitude,
                locationIsManual = page.locationIsManual,
            )
        },
        attachments = attachments.mapNotNull { attachment ->
            val encrypted = objects[attachment.id] ?: return@mapNotNull null
            VaultAttachment(
                id = attachment.id,
                pageId = attachment.pageId,
                objectHash = encrypted.hash,
                plaintextSha256 = attachment.sha256,
                originalFileName = attachment.originalFileName,
                mimeType = attachment.mimeType,
                byteSize = attachment.byteSize,
                width = attachment.width,
                height = attachment.height,
                capturedAtEpochMillis = attachment.capturedAtEpochMillis,
                latitude = attachment.latitude,
                longitude = attachment.longitude,
                altitudeMeters = attachment.altitudeMeters,
                cameraMake = attachment.cameraMake,
                cameraModel = attachment.cameraModel,
                lensModel = attachment.lensModel,
                exposureTime = attachment.exposureTime,
                aperture = attachment.aperture,
                iso = attachment.iso,
                focalLength = attachment.focalLength,
                caption = attachment.caption,
                sortOrder = attachment.sortOrder,
                normalizedExif = attachment.normalizedExifJson,
                createdAtEpochMillis = attachment.createdAtEpochMillis,
            )
        },
        monthlyRecaps = recaps.map { recap ->
            VaultMonthlyRecap(
                id = recap.id,
                yearMonth = recap.yearMonth,
                document = recap.documentJson,
                plainText = recap.plainText,
                createdAtEpochMillis = recap.createdAtEpochMillis,
                updatedAtEpochMillis = recap.updatedAtEpochMillis,
                revision = recap.revision,
                deletedAtEpochMillis = recap.deletedAtEpochMillis,
            )
        },
    )

    private fun encryptFile(source: File, keyBytes: ByteArray): EncryptedObject {
        val target = File.createTempFile("mneme-backup-", ".vault", context.cacheDir)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        }
        FileOutputStream(target).use { output ->
            output.write(MAGIC_V2)
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            CipherOutputStream(output, cipher).use { encrypted -> source.inputStream().use { it.copyTo(encrypted) } }
        }
        return EncryptedObject(target, sha256(target))
    }

    private fun encryptBytes(source: ByteArray, keyBytes: ByteArray): EncryptedObject {
        val target = File.createTempFile("mneme-manifest-", ".vault", context.cacheDir)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        }
        FileOutputStream(target).use { output ->
            output.write(MAGIC_V2)
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            CipherOutputStream(output, cipher).use { it.write(source) }
        }
        return EncryptedObject(target, sha256(target))
    }

    private fun decryptBytes(source: ByteArray, keyBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        decryptStream(ByteArrayInputStream(source), output, keyBytes)
        return output.toByteArray()
    }

    private fun decryptFile(source: File, target: File, keyBytes: ByteArray) {
        try {
            source.inputStream().use { input ->
                FileOutputStream(target).use { output -> decryptStream(input, output, keyBytes) }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun decryptStream(source: java.io.InputStream, target: java.io.OutputStream, keyBytes: ByteArray) {
        require(source.readExactly(MAGIC_V2.size).contentEquals(MAGIC_V2)) {
            "This backup predates portable recovery codes. Back it up again from the original phone."
        }
        val ivLength = source.read()
        require(ivLength in 12..32) { "The backup encryption header is invalid." }
        val iv = source.readExactly(ivLength)
        require(iv.size == ivLength) { "The backup encryption header is incomplete." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        }
        CipherInputStream(source, cipher).use { decrypted -> decrypted.copyTo(target) }
    }

    private fun connectedSettings(): AppSettings = settingsRepository.settings.value.also { settings ->
        require(settings.serverConnected && settings.serverUrl.isNotBlank() && settings.serverToken.isNotBlank()) {
            "Connect your Mneme server first."
        }
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

    private fun downloadBytes(settings: AppSettings, path: String): ByteArray {
        val connection = authenticatedConnection(settings, path, "GET")
        return try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Backup download returned HTTP ${connection.responseCode}."
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(settings: AppSettings, path: String, target: File) {
        val connection = authenticatedConnection(settings, path, "GET")
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Photo download returned HTTP ${connection.responseCode}."
            }
            FileOutputStream(target).use { output -> connection.inputStream.use { it.copyTo(output) } }
        } finally {
            connection.disconnect()
        }
    }

    private fun authenticatedConnection(settings: AppSettings, path: String, method: String): HttpURLConnection =
        (URL(settings.serverUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun createThumbnail(original: File, thumbnail: File): Boolean {
        val exif = runCatching { ExifInterface(original) }.getOrNull()
        val bounds = BitmapFactory.Options().also {
            it.inJustDecodeBounds = true
            BitmapFactory.decodeFile(original.path, it)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 1400) sampleSize *= 2
        val decoded = BitmapFactory.decodeFile(
            original.path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return false
        val rotation = exif?.rotationDegrees ?: 0
        val oriented = if (rotation != 0) {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        return try {
            FileOutputStream(thumbnail).use { oriented.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        } finally {
            oriented.recycle()
        }
    }

    private fun java.io.InputStream.readExactly(byteCount: Int): ByteArray {
        val result = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val count = read(result, offset, byteCount - offset)
            if (count < 0) return result.copyOf(offset)
            offset += count
        }
        return result
    }

    private data class EncryptedObject(val file: File?, val hash: String)

    private companion object {
        const val VAULT_FORMAT = "mneme-vault-v2"
        val MAGIC_V2 = "MNEME2".toByteArray(Charsets.US_ASCII)
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LAST_SUCCESS = "last_success"
        const val KEY_LAST_ERROR = "last_error"
    }
}
