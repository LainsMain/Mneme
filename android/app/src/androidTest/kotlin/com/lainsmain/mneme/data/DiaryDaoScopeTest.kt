package com.lainsmain.mneme.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryDaoScopeTest {
    private lateinit var database: MnemeDatabase
    private lateinit var dao: DiaryDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MnemeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.diaryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun dailyAndDreamContent_neverCrossScopedQueries() = runBlocking {
        val date = "2026-08-26"
        dao.upsertPage(page("daily-page", EntryKind.Daily, date, "Daily memory", favorite = true))
        dao.upsertPage(page("dream-page", EntryKind.Dream, date, "Dream memory", favorite = false))
        dao.insertAttachment(attachment("daily-photo", "daily-page", "Daily caption"))
        dao.insertAttachment(attachment("dream-photo", "dream-page", "Dream caption"))

        assertEquals(
            "daily-page",
            dao.observePage(EntryKind.Daily.storageValue, date).first()?.id,
        )
        assertEquals(
            "dream-page",
            dao.observePage(EntryKind.Dream.storageValue, date).first()?.id,
        )
        assertEquals(
            listOf("Daily memory"),
            dao.observeAllDays(EntryKind.Daily.storageValue).first().map(MonthDayRow::plainText),
        )
        assertEquals(
            listOf("Dream memory"),
            dao.observeAllDays(EntryKind.Dream.storageValue).first().map(MonthDayRow::plainText),
        )
        assertEquals(
            listOf("daily-photo"),
            dao.observeAllMedia(EntryKind.Daily.storageValue).first().map { it.attachment.id },
        )
        assertEquals(
            listOf("dream-photo"),
            dao.observeAllMedia(EntryKind.Dream.storageValue).first().map { it.attachment.id },
        )
        assertEquals(
            listOf("daily-photo"),
            dao.observeAttachments(EntryKind.Daily.storageValue, date).first().map(AttachmentEntity::id),
        )
        assertEquals(
            listOf("dream-photo"),
            dao.observeAttachments(EntryKind.Dream.storageValue, date).first().map(AttachmentEntity::id),
        )
        assertEquals(
            listOf("Daily memory"),
            dao.observeMonth(EntryKind.Daily.storageValue, date, date).first().map(MonthDayRow::plainText),
        )
        assertEquals(
            listOf("Dream memory"),
            dao.search(EntryKind.Dream.storageValue, "memory").first().map(DiaryPageEntity::plainText),
        )
    }

    private fun page(
        id: String,
        kind: EntryKind,
        date: String,
        text: String,
        favorite: Boolean,
    ) = DiaryPageEntity(
        id = id,
        entryKind = kind.storageValue,
        diaryDate = date,
        documentJson = "{}",
        plainText = text,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        revision = 1,
        locationName = if (kind == EntryKind.Daily) "Brussels" else "Antwerp",
        latitude = if (kind == EntryKind.Daily) 50.85 else 51.22,
        longitude = if (kind == EntryKind.Daily) 4.35 else 4.40,
        isFavorite = favorite,
    )

    private fun attachment(id: String, pageId: String, caption: String) = AttachmentEntity(
        id = id,
        pageId = pageId,
        encryptedFileName = "/$id.jpg",
        thumbnailFileName = null,
        originalFileName = "$id.jpg",
        mimeType = "image/jpeg",
        byteSize = 42,
        width = 100,
        height = 100,
        sha256 = id,
        caption = caption,
        sortOrder = 0,
        capturedAtEpochMillis = null,
        latitude = null,
        longitude = null,
        altitudeMeters = null,
        cameraMake = null,
        cameraModel = null,
        lensModel = null,
        exposureTime = null,
        aperture = null,
        iso = null,
        focalLength = null,
        normalizedExifJson = "{}",
        createdAtEpochMillis = 1,
    )
}
