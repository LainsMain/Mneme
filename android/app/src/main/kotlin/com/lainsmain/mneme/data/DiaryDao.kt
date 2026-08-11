package com.lainsmain.mneme.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_pages WHERE deletedAtEpochMillis IS NULL ORDER BY diaryDate")
    suspend fun pagesForBackup(): List<DiaryPageEntity>

    @Query("SELECT * FROM attachments ORDER BY pageId, sortOrder, createdAtEpochMillis")
    suspend fun attachmentsForBackup(): List<AttachmentEntity>

    @Query("SELECT * FROM diary_pages WHERE diaryDate = :diaryDate AND deletedAtEpochMillis IS NULL LIMIT 1")
    fun observePage(diaryDate: String): Flow<DiaryPageEntity?>

    @Query("SELECT * FROM diary_pages WHERE id = :id LIMIT 1")
    suspend fun pageById(id: String): DiaryPageEntity?

    @Upsert
    suspend fun upsertPage(page: DiaryPageEntity)

    @Query(
        """
        SELECT * FROM diary_pages
        WHERE deletedAtEpochMillis IS NULL AND plainText LIKE '%' || :query || '%'
        ORDER BY diaryDate DESC
        LIMIT :limit
        """,
    )
    fun search(query: String, limit: Int = 100): Flow<List<DiaryPageEntity>>

    @Query("SELECT diaryDate FROM diary_pages WHERE deletedAtEpochMillis IS NULL ORDER BY diaryDate DESC")
    fun observeWrittenDates(): Flow<List<String>>

    @Query(
        """
        SELECT
            page.diaryDate AS diaryDate,
            page.plainText AS plainText,
            (
                SELECT attachment.thumbnailFileName
                FROM attachments AS attachment
                WHERE attachment.pageId = page.id
                ORDER BY attachment.sortOrder
                LIMIT 1
            ) AS thumbnailFileName,
            COALESCE(page.locationName,
                CASE WHEN (
                    SELECT attachment.latitude FROM attachments AS attachment
                    WHERE attachment.pageId = page.id ORDER BY attachment.sortOrder LIMIT 1
                ) IS NOT NULL THEN 'Photo location' END
            ) AS locationName,
            COALESCE(page.latitude, (
                SELECT attachment.latitude FROM attachments AS attachment
                WHERE attachment.pageId = page.id ORDER BY attachment.sortOrder LIMIT 1
            )) AS latitude,
            COALESCE(page.longitude, (
                SELECT attachment.longitude FROM attachments AS attachment
                WHERE attachment.pageId = page.id ORDER BY attachment.sortOrder LIMIT 1
            )) AS longitude,
            (SELECT COUNT(*) FROM attachments AS attachment WHERE attachment.pageId = page.id) AS attachmentCount
        FROM diary_pages AS page
        WHERE page.deletedAtEpochMillis IS NULL
            AND page.diaryDate BETWEEN :firstDate AND :lastDate
        ORDER BY page.diaryDate
        """,
    )
    fun observeMonth(firstDate: String, lastDate: String): Flow<List<MonthDayRow>>

    @Query(
        """
        SELECT
            page.diaryDate AS diaryDate,
            page.plainText AS plainText,
            (
                SELECT attachment.thumbnailFileName FROM attachments AS attachment
                WHERE attachment.pageId = page.id ORDER BY attachment.sortOrder LIMIT 1
            ) AS thumbnailFileName,
            COALESCE(page.locationName,
                CASE WHEN (
                    SELECT attachment.latitude FROM attachments AS attachment
                    WHERE attachment.pageId = page.id ORDER BY attachment.sortOrder LIMIT 1
                ) IS NOT NULL THEN 'Photo location' END
            ) AS locationName,
            COALESCE(page.latitude, (
                SELECT attachment.latitude FROM attachments AS attachment
                WHERE attachment.pageId = page.id ORDER BY attachment.sortOrder LIMIT 1
            )) AS latitude,
            COALESCE(page.longitude, (
                SELECT attachment.longitude FROM attachments AS attachment
                WHERE attachment.pageId = page.id ORDER BY attachment.sortOrder LIMIT 1
            )) AS longitude,
            (SELECT COUNT(*) FROM attachments AS attachment WHERE attachment.pageId = page.id) AS attachmentCount
        FROM diary_pages AS page
        WHERE page.deletedAtEpochMillis IS NULL
        ORDER BY page.diaryDate DESC
        """,
    )
    fun observeAllDays(): Flow<List<MonthDayRow>>

    @Query(
        """
        SELECT attachment.*, page.diaryDate AS diaryDate
        FROM attachments AS attachment
        INNER JOIN diary_pages AS page ON page.id = attachment.pageId
        WHERE page.deletedAtEpochMillis IS NULL
        ORDER BY page.diaryDate DESC, attachment.sortOrder, attachment.createdAtEpochMillis
        """,
    )
    fun observeAllMedia(): Flow<List<MediaAttachmentRow>>

    @Query(
        """
        SELECT attachment.*
        FROM attachments AS attachment
        INNER JOIN diary_pages AS page ON page.id = attachment.pageId
        WHERE page.diaryDate = :diaryDate AND page.deletedAtEpochMillis IS NULL
        ORDER BY attachment.sortOrder, attachment.createdAtEpochMillis
        """,
    )
    fun observeAttachments(diaryDate: String): Flow<List<AttachmentEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM attachments WHERE pageId = :pageId")
    suspend fun maximumAttachmentOrder(pageId: String): Int

    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE id = :attachmentId LIMIT 1")
    suspend fun attachmentById(attachmentId: String): AttachmentEntity?

    @Query(
        """
        UPDATE attachments SET sortOrder = sortOrder + 1
        WHERE pageId = (SELECT pageId FROM attachments WHERE id = :attachmentId)
            AND id != :attachmentId
        """,
    )
    suspend fun moveOtherAttachmentsBack(attachmentId: String)

    @Query("UPDATE attachments SET sortOrder = 0 WHERE id = :attachmentId")
    suspend fun moveAttachmentFirst(attachmentId: String)

    @Transaction
    suspend fun makeAttachmentPrimary(attachmentId: String) {
        moveOtherAttachmentsBack(attachmentId)
        moveAttachmentFirst(attachmentId)
    }

    @Query("DELETE FROM attachments WHERE id = :attachmentId")
    suspend fun deleteAttachmentById(attachmentId: String)

    @Query(
        """
        UPDATE diary_pages SET locationName = :name, latitude = :latitude, longitude = :longitude,
            locationIsManual = 1, updatedAtEpochMillis = :updatedAt, revision = revision + 1
        WHERE id = :pageId
        """,
    )
    suspend fun setManualLocation(
        pageId: String,
        name: String?,
        latitude: Double?,
        longitude: Double?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE diary_pages SET locationName = :name,
            updatedAtEpochMillis = :updatedAt, revision = revision + 1
        WHERE id = :pageId AND locationIsManual = 0 AND locationName IS NOT :name
        """,
    )
    suspend fun setAutomaticLocationName(pageId: String, name: String?, updatedAt: Long)

    @Query(
        """
        UPDATE diary_pages SET locationName = NULL, latitude = NULL, longitude = NULL,
            locationIsManual = 0, updatedAtEpochMillis = :updatedAt, revision = revision + 1
        WHERE id = :pageId
        """,
    )
    suspend fun clearManualLocation(pageId: String, updatedAt: Long)
}

data class MonthDayRow(
    val diaryDate: String,
    val plainText: String,
    val thumbnailFileName: String?,
    val locationName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val attachmentCount: Int,
)

data class MediaAttachmentRow(
    @Embedded val attachment: AttachmentEntity,
    val diaryDate: String,
)
