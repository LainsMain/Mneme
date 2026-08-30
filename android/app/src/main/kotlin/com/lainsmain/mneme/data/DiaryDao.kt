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
    @Query("SELECT * FROM journals ORDER BY sortOrder, createdAtEpochMillis")
    fun observeJournals(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journals ORDER BY sortOrder, createdAtEpochMillis")
    suspend fun journalsForBackup(): List<JournalEntity>

    @Query("SELECT * FROM journals WHERE id = :journalId LIMIT 1")
    suspend fun journalById(journalId: String): JournalEntity?

    @Upsert
    suspend fun upsertJournal(journal: JournalEntity)

    @Query("UPDATE journals SET sortOrder = :sortOrder, updatedAtEpochMillis = :updatedAt WHERE id = :journalId")
    suspend fun setJournalSortOrder(journalId: String, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE journals SET archivedAtEpochMillis = :archivedAt, updatedAtEpochMillis = :updatedAt WHERE id = :journalId")
    suspend fun setJournalArchived(journalId: String, archivedAt: Long?, updatedAt: Long)

    @Query("SELECT * FROM diary_pages WHERE deletedAtEpochMillis IS NULL ORDER BY diaryDate")
    suspend fun pagesForBackup(): List<DiaryPageEntity>

    @Query(
        """
        SELECT attachment.* FROM attachments AS attachment
        INNER JOIN diary_pages AS page ON page.id = attachment.pageId
        WHERE page.deletedAtEpochMillis IS NULL
        ORDER BY attachment.pageId, attachment.sortOrder, attachment.createdAtEpochMillis
        """,
    )
    suspend fun attachmentsForBackup(): List<AttachmentEntity>

    @Query("SELECT * FROM monthly_recaps WHERE deletedAtEpochMillis IS NULL ORDER BY yearMonth")
    suspend fun recapsForBackup(): List<MonthlyRecapEntity>

    @Query(
        "SELECT * FROM monthly_recaps WHERE journalId = :journalId AND yearMonth = :yearMonth " +
            "AND deletedAtEpochMillis IS NULL LIMIT 1",
    )
    fun observeRecap(journalId: String, yearMonth: String): Flow<MonthlyRecapEntity?>

    @Query(
        "SELECT yearMonth FROM monthly_recaps WHERE journalId = :journalId " +
            "AND deletedAtEpochMillis IS NULL ORDER BY yearMonth",
    )
    fun observeRecapMonths(journalId: String): Flow<List<String>>

    @Query(
        "SELECT (SELECT COUNT(*) FROM diary_pages) + (SELECT COUNT(*) FROM attachments) + " +
            "(SELECT COUNT(*) FROM monthly_recaps) + (SELECT COUNT(*) FROM journals WHERE role = 'custom')",
    )
    suspend fun localContentCount(): Int

    @Transaction
    suspend fun exportSnapshot(): DiaryExportSnapshot = DiaryExportSnapshot(
        journals = journalsForBackup(),
        pages = pagesForBackup(),
        attachments = attachmentsForBackup(),
        recaps = recapsForBackup(),
    )

    @Query(
        "SELECT * FROM diary_pages WHERE entryKind = :journalId AND diaryDate = :diaryDate " +
            "AND deletedAtEpochMillis IS NULL LIMIT 1",
    )
    fun observePage(journalId: String, diaryDate: String): Flow<DiaryPageEntity?>

    @Query("SELECT * FROM diary_pages WHERE id = :id LIMIT 1")
    suspend fun pageById(id: String): DiaryPageEntity?

    @Upsert
    suspend fun upsertPage(page: DiaryPageEntity)

    @Query(
        """
        SELECT * FROM diary_pages
        WHERE entryKind = :journalId AND deletedAtEpochMillis IS NULL
            AND plainText LIKE '%' || :query || '%'
        ORDER BY diaryDate DESC
        LIMIT :limit
        """,
    )
    fun search(journalId: String, query: String, limit: Int = 100): Flow<List<DiaryPageEntity>>

    @Query(
        "SELECT diaryDate FROM diary_pages WHERE entryKind = :journalId " +
            "AND deletedAtEpochMillis IS NULL ORDER BY diaryDate DESC",
    )
    fun observeWrittenDates(journalId: String): Flow<List<String>>

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
            (SELECT COUNT(*) FROM attachments AS attachment WHERE attachment.pageId = page.id) AS attachmentCount,
            page.isFavorite AS isFavorite,
            COALESCE((
                SELECT GROUP_CONCAT(attachment.caption, ' ')
                FROM attachments AS attachment
                WHERE attachment.pageId = page.id AND attachment.caption != ''
            ), '') AS photoCaptions
        FROM diary_pages AS page
        WHERE page.entryKind = :journalId AND page.deletedAtEpochMillis IS NULL
            AND page.diaryDate BETWEEN :firstDate AND :lastDate
        ORDER BY page.diaryDate
        """,
    )
    fun observeMonth(journalId: String, firstDate: String, lastDate: String): Flow<List<MonthDayRow>>

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
            (SELECT COUNT(*) FROM attachments AS attachment WHERE attachment.pageId = page.id) AS attachmentCount,
            page.isFavorite AS isFavorite,
            COALESCE((
                SELECT GROUP_CONCAT(attachment.caption, ' ')
                FROM attachments AS attachment
                WHERE attachment.pageId = page.id AND attachment.caption != ''
            ), '') AS photoCaptions
        FROM diary_pages AS page
        WHERE page.entryKind = :journalId AND page.deletedAtEpochMillis IS NULL
        ORDER BY page.diaryDate DESC
        """,
    )
    fun observeAllDays(journalId: String): Flow<List<MonthDayRow>>

    @Query(
        """
        SELECT attachment.*, page.diaryDate AS diaryDate, page.isFavorite AS isFavorite
        FROM attachments AS attachment
        INNER JOIN diary_pages AS page ON page.id = attachment.pageId
        WHERE page.entryKind = :journalId AND page.deletedAtEpochMillis IS NULL
        ORDER BY page.diaryDate, attachment.sortOrder, attachment.createdAtEpochMillis
        """,
    )
    fun observeAllMedia(journalId: String): Flow<List<MediaAttachmentRow>>

    @Query(
        """
        SELECT attachment.*
        FROM attachments AS attachment
        INNER JOIN diary_pages AS page ON page.id = attachment.pageId
        WHERE page.entryKind = :journalId AND page.diaryDate = :diaryDate
            AND page.deletedAtEpochMillis IS NULL
        ORDER BY attachment.sortOrder, attachment.createdAtEpochMillis
        """,
    )
    fun observeAttachments(journalId: String, diaryDate: String): Flow<List<AttachmentEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM attachments WHERE pageId = :pageId")
    suspend fun maximumAttachmentOrder(pageId: String): Int

    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertRecap(recap: MonthlyRecapEntity)

    @Query("SELECT * FROM monthly_recaps WHERE id = :id LIMIT 1")
    suspend fun recapById(id: String): MonthlyRecapEntity?

    @Transaction
    suspend fun restoreBackup(
        journals: List<JournalEntity>,
        pages: List<DiaryPageEntity>,
        attachments: List<AttachmentEntity>,
        recaps: List<MonthlyRecapEntity>,
    ) {
        journals.forEach { upsertJournal(it) }
        pages.forEach { upsertPage(it) }
        attachments.forEach { upsertAttachment(it) }
        recaps.forEach { upsertRecap(it) }
    }

    @Query(
        "SELECT attachment.* FROM attachments AS attachment INNER JOIN diary_pages AS page " +
            "ON page.id = attachment.pageId WHERE attachment.id = :attachmentId " +
            "AND page.entryKind = :journalId LIMIT 1",
    )
    suspend fun attachmentById(journalId: String, attachmentId: String): AttachmentEntity?

    @Query(
        """
        UPDATE attachments SET sortOrder = sortOrder + 1
        WHERE pageId = (
            SELECT attachment.pageId FROM attachments AS attachment
            INNER JOIN diary_pages AS page ON page.id = attachment.pageId
            WHERE attachment.id = :attachmentId AND page.entryKind = :journalId
        )
            AND id != :attachmentId
        """,
    )
    suspend fun moveOtherAttachmentsBack(journalId: String, attachmentId: String)

    @Query(
        "UPDATE attachments SET sortOrder = 0 WHERE id = :attachmentId AND pageId IN " +
            "(SELECT id FROM diary_pages WHERE entryKind = :journalId)",
    )
    suspend fun moveAttachmentFirst(journalId: String, attachmentId: String)

    @Transaction
    suspend fun makeAttachmentPrimary(journalId: String, attachmentId: String) {
        moveOtherAttachmentsBack(journalId, attachmentId)
        moveAttachmentFirst(journalId, attachmentId)
    }

    @Query(
        "DELETE FROM attachments WHERE id = :attachmentId AND pageId IN " +
            "(SELECT id FROM diary_pages WHERE entryKind = :journalId)",
    )
    suspend fun deleteAttachmentById(journalId: String, attachmentId: String)

    @Query(
        "UPDATE attachments SET caption = :caption WHERE id = :attachmentId AND pageId IN " +
            "(SELECT id FROM diary_pages WHERE entryKind = :journalId)",
    )
    suspend fun setAttachmentCaption(journalId: String, attachmentId: String, caption: String)

    @Query(
        """
        UPDATE diary_pages SET isFavorite = :favorite,
            updatedAtEpochMillis = :updatedAt, revision = revision + 1
        WHERE id = :pageId AND entryKind = :journalId
        """,
    )
    suspend fun setFavorite(journalId: String, pageId: String, favorite: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE diary_pages SET locationName = :name, latitude = :latitude, longitude = :longitude,
            locationIsManual = 1, updatedAtEpochMillis = :updatedAt, revision = revision + 1
        WHERE id = :pageId AND entryKind = :journalId
        """,
    )
    suspend fun setManualLocation(
        pageId: String,
        journalId: String,
        name: String?,
        latitude: Double?,
        longitude: Double?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE diary_pages SET locationName = :name,
            updatedAtEpochMillis = :updatedAt, revision = revision + 1
        WHERE id = :pageId AND entryKind = :journalId AND locationIsManual = 0 AND locationName IS NOT :name
        """,
    )
    suspend fun setAutomaticLocationName(journalId: String, pageId: String, name: String?, updatedAt: Long)

    @Query(
        """
        UPDATE diary_pages SET locationName = NULL, latitude = NULL, longitude = NULL,
            locationIsManual = 0, updatedAtEpochMillis = :updatedAt, revision = revision + 1
        WHERE id = :pageId AND entryKind = :journalId
        """,
    )
    suspend fun clearManualLocation(journalId: String, pageId: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM diary_pages WHERE entryKind = :journalId AND deletedAtEpochMillis IS NULL")
    suspend fun entryCount(journalId: String): Int

    @Query(
        "SELECT COUNT(*) FROM attachments WHERE pageId IN " +
            "(SELECT id FROM diary_pages WHERE entryKind = :journalId AND deletedAtEpochMillis IS NULL)",
    )
    suspend fun photoCount(journalId: String): Int

    @Query("SELECT COUNT(*) FROM monthly_recaps WHERE journalId = :journalId AND deletedAtEpochMillis IS NULL")
    suspend fun recapCount(journalId: String): Int

    @Query(
        "SELECT * FROM attachments WHERE pageId IN " +
            "(SELECT id FROM diary_pages WHERE entryKind = :journalId)",
    )
    suspend fun attachmentsForJournal(journalId: String): List<AttachmentEntity>

    @Query("DELETE FROM monthly_recaps WHERE journalId = :journalId")
    suspend fun deleteRecapsForJournal(journalId: String)

    @Query("DELETE FROM diary_pages WHERE entryKind = :journalId")
    suspend fun deletePagesForJournal(journalId: String)

    @Query("DELETE FROM journals WHERE id = :journalId")
    suspend fun deleteJournalRow(journalId: String)

    @Transaction
    suspend fun deleteJournal(journalId: String) {
        deleteRecapsForJournal(journalId)
        deletePagesForJournal(journalId)
        deleteJournalRow(journalId)
    }
}

data class MonthDayRow(
    val diaryDate: String,
    val plainText: String,
    val thumbnailFileName: String?,
    val locationName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val attachmentCount: Int,
    val isFavorite: Boolean,
    val photoCaptions: String,
)

data class MediaAttachmentRow(
    @Embedded val attachment: AttachmentEntity,
    val diaryDate: String,
    val isFavorite: Boolean,
)

data class DiaryExportSnapshot(
    val journals: List<JournalEntity>,
    val pages: List<DiaryPageEntity>,
    val attachments: List<AttachmentEntity>,
    val recaps: List<MonthlyRecapEntity>,
)
