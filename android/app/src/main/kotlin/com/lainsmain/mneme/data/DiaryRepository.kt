package com.lainsmain.mneme.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import com.lainsmain.mneme.model.RichTextDocument
import java.io.File
import java.io.FileOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

data class DiaryPage(
    val id: String,
    val date: LocalDate,
    val document: RichTextDocument,
    val revision: Long,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationIsManual: Boolean = false,
)

data class DaySummary(
    val date: LocalDate,
    val hasWriting: Boolean,
    val wordCount: Int,
    val thumbnailFileName: String?,
    val plainText: String = "",
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val attachmentCount: Int = 0,
)

data class DatedAttachment(val date: LocalDate, val attachment: DiaryAttachment)

data class DiaryAttachment(
    val id: String,
    val pageId: String,
    val originalPath: String,
    val thumbnailPath: String?,
    val originalFileName: String?,
    val mimeType: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val capturedAtEpochMillis: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val cameraMake: String?,
    val cameraModel: String?,
    val lensModel: String?,
    val exposureTime: String?,
    val aperture: String?,
    val iso: Int?,
    val focalLength: String?,
    val caption: String,
    val sortOrder: Int,
)

data class PhotoImportResult(val imported: Int, val failed: Int)

class DiaryRepository(
    private val context: Context,
    private val diaryDao: DiaryDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun observePage(date: LocalDate): Flow<DiaryPage?> =
        diaryDao.observePage(date.toString()).map { entity -> entity?.toModel() }

    fun observeMonth(month: YearMonth): Flow<Map<LocalDate, DaySummary>> =
        diaryDao.observeMonth(
            firstDate = month.atDay(1).toString(),
            lastDate = month.atEndOfMonth().toString(),
        ).map { rows ->
            rows.associate { row ->
                val date = LocalDate.parse(row.diaryDate)
                date to DaySummary(
                    date = date,
                    hasWriting = row.plainText.isNotBlank(),
                    wordCount = row.plainText
                        .trim()
                        .split(Regex("\\s+"))
                        .count { it.isNotBlank() },
                    thumbnailFileName = row.thumbnailFileName,
                    plainText = row.plainText,
                    locationName = row.locationName,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    attachmentCount = row.attachmentCount,
                )
            }
        }

    fun observeAllDays(): Flow<List<DaySummary>> = diaryDao.observeAllDays().map { rows ->
        rows.map { row ->
            val date = LocalDate.parse(row.diaryDate)
            DaySummary(
                date = date,
                hasWriting = row.plainText.isNotBlank(),
                wordCount = row.plainText.trim().split(Regex("\\s+")).count { it.isNotBlank() },
                thumbnailFileName = row.thumbnailFileName,
                plainText = row.plainText,
                locationName = row.locationName,
                latitude = row.latitude,
                longitude = row.longitude,
                attachmentCount = row.attachmentCount,
            )
        }
    }

    fun observeAllMedia(): Flow<List<DatedAttachment>> = diaryDao.observeAllMedia().map { rows ->
        rows.map { DatedAttachment(LocalDate.parse(it.diaryDate), it.attachment.toModel()) }
    }

    fun observeAttachments(date: LocalDate): Flow<List<DiaryAttachment>> =
        diaryDao.observeAttachments(date.toString()).map { attachments ->
            attachments.map { it.toModel() }
        }

    suspend fun importPhotos(pageId: String, uris: List<Uri>): PhotoImportResult = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        var order = diaryDao.maximumAttachmentOrder(pageId) + 1
        var imported = 0
        var failed = 0

        uris.distinct().forEach { uri ->
            val result = runCatching {
                val id = UUID.randomUUID().toString()
                val originalName = displayName(uri)
                val mimeType = context.contentResolver.getType(uri) ?: "image/*"
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    ?: originalName?.substringAfterLast('.', "")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
                    ?: "image"
                val originalFile = File(directory, "$id.original.$extension")
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Photo is no longer available" }
                    FileOutputStream(originalFile).use { output ->
                        DigestOutputStream(output, digest).use { hashedOutput -> input.copyTo(hashedOutput) }
                    }
                }

                val exif = runCatching { ExifInterface(originalFile) }.getOrNull()
                val bounds = BitmapFactory.Options().also {
                    it.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(originalFile.path, it)
                }
                val thumbnailFile = File(directory, "$id.thumbnail.jpg")
                    .takeIf { createThumbnail(originalFile, it, exif?.rotationDegrees ?: 0) }
                val coordinates = exif?.latLong
                val normalizedExif = normalizedExif(exif)

                diaryDao.insertAttachment(
                    AttachmentEntity(
                        id = id,
                        pageId = pageId,
                        // Vault encryption will replace this file in place; the imported source is never rewritten.
                        encryptedFileName = originalFile.absolutePath,
                        thumbnailFileName = thumbnailFile?.absolutePath,
                        originalFileName = originalName,
                        mimeType = mimeType,
                        byteSize = originalFile.length(),
                        width = bounds.outWidth.takeIf { it > 0 },
                        height = bounds.outHeight.takeIf { it > 0 },
                        sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                        caption = "",
                        sortOrder = order++,
                        capturedAtEpochMillis = capturedAtEpochMillis(exif),
                        latitude = coordinates?.getOrNull(0),
                        longitude = coordinates?.getOrNull(1),
                        altitudeMeters = exif?.getAltitude(Double.NaN)?.takeUnless { it.isNaN() },
                        cameraMake = exif?.getAttribute(ExifInterface.TAG_MAKE),
                        cameraModel = exif?.getAttribute(ExifInterface.TAG_MODEL),
                        lensModel = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL),
                        exposureTime = exif?.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                        aperture = exif?.getAttribute(ExifInterface.TAG_F_NUMBER),
                        iso = exif?.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1)
                            ?.takeIf { it >= 0 },
                        focalLength = exif?.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                        normalizedExifJson = normalizedExif,
                        createdAtEpochMillis = clock.millis(),
                    ),
                )
            }
            if (result.isSuccess) imported++ else failed++
        }
        PhotoImportResult(imported = imported, failed = failed)
    }

    suspend fun save(date: LocalDate, existing: DiaryPage?, document: RichTextDocument): DiaryPage {
        val now = clock.millis()
        val page = DiaryPageEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            diaryDate = date.toString(),
            documentJson = json.encodeToString(RichTextDocument.serializer(), document),
            plainText = document.text,
            createdAtEpochMillis = existing?.let { diaryDao.pageById(it.id)?.createdAtEpochMillis } ?: now,
            updatedAtEpochMillis = now,
            revision = (existing?.revision ?: 0) + 1,
            locationName = existing?.locationName,
            latitude = existing?.latitude,
            longitude = existing?.longitude,
            locationIsManual = existing?.locationIsManual ?: false,
        )
        diaryDao.upsertPage(page)
        return page.toModel()
    }

    suspend fun makePhotoPrimary(attachmentId: String) = withContext(Dispatchers.IO) {
        diaryDao.makeAttachmentPrimary(attachmentId)
    }

    suspend fun deletePhoto(attachmentId: String) = withContext(Dispatchers.IO) {
        val attachment = diaryDao.attachmentById(attachmentId) ?: return@withContext
        diaryDao.deleteAttachmentById(attachmentId)
        File(attachment.encryptedFileName).delete()
        attachment.thumbnailFileName?.let { File(it).delete() }
    }

    suspend fun setManualLocation(
        pageId: String,
        name: String?,
        latitude: Double?,
        longitude: Double?,
    ) = withContext(Dispatchers.IO) {
        diaryDao.setManualLocation(pageId, name, latitude, longitude, clock.millis())
    }

    suspend fun usePrimaryPhotoLocation(pageId: String) = withContext(Dispatchers.IO) {
        diaryDao.clearManualLocation(pageId, clock.millis())
    }

    private fun DiaryPageEntity.toModel() = DiaryPage(
        id = id,
        date = LocalDate.parse(diaryDate),
        document = runCatching {
            json.decodeFromString(RichTextDocument.serializer(), documentJson)
        }.getOrElse { RichTextDocument(plainText) },
        revision = revision,
        locationName = locationName,
        latitude = latitude,
        longitude = longitude,
        locationIsManual = locationIsManual,
    )

    private fun AttachmentEntity.toModel() = DiaryAttachment(
        id = id,
        pageId = pageId,
        originalPath = encryptedFileName,
        thumbnailPath = thumbnailFileName,
        originalFileName = originalFileName,
        mimeType = mimeType,
        byteSize = byteSize,
        width = width,
        height = height,
        capturedAtEpochMillis = capturedAtEpochMillis,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        cameraMake = cameraMake,
        cameraModel = cameraModel,
        lensModel = lensModel,
        exposureTime = exposureTime,
        aperture = aperture,
        iso = iso,
        focalLength = focalLength,
        caption = caption,
        sortOrder = sortOrder,
    )

    private fun displayName(uri: Uri): String? = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }
                ?.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }

    private fun normalizedExif(exif: ExifInterface?): String {
        if (exif == null) return "{}"
        val tags = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
        )
        return buildJsonObject {
            tags.forEach { tag -> exif.getAttribute(tag)?.let { value -> put(tag, value) } }
        }.toString()
    }

    private fun capturedAtEpochMillis(exif: ExifInterface?): Long? {
        if (exif == null) return null
        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        val parsed = runCatching {
            LocalDateTime.parse(
                dateTime,
                DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US),
            )
        }.getOrNull() ?: return null
        val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED)
            ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
        return runCatching {
            if (offset != null) {
                parsed.toInstant(ZoneOffset.of(offset)).toEpochMilli()
            } else {
                parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }.getOrNull()
    }

    private fun createThumbnail(original: File, thumbnail: File, rotationDegrees: Int): Boolean {
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
        val oriented = if (rotationDegrees != 0) {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(rotationDegrees.toFloat()) },
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
}
