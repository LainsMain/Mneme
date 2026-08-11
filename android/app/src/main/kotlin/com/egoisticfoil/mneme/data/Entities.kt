package com.egoisticfoil.mneme.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diary_pages",
    indices = [Index(value = ["diaryDate"], unique = true)],
)
data class DiaryPageEntity(
    @PrimaryKey val id: String,
    val diaryDate: String,
    val documentJson: String,
    val plainText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val revision: Long,
    val deletedAtEpochMillis: Long? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationIsManual: Boolean = false,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = DiaryPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId"), Index("sha256")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val encryptedFileName: String,
    val thumbnailFileName: String?,
    val originalFileName: String?,
    val mimeType: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val sha256: String,
    val caption: String,
    val sortOrder: Int,
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
    /** The complete original EXIF payload is preserved with the encrypted original. */
    val normalizedExifJson: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "monthly_recaps",
    indices = [Index(value = ["yearMonth"], unique = true)],
)
data class MonthlyRecapEntity(
    @PrimaryKey val id: String,
    val yearMonth: String,
    val documentJson: String,
    val plainText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val revision: Long,
    val deletedAtEpochMillis: Long? = null,
)
