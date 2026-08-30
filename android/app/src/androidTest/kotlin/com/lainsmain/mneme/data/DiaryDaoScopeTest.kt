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
        val workout = JournalId.custom()
        dao.upsertPage(page("daily-page", JournalId.PRIMARY, date, "Daily memory", favorite = true))
        dao.upsertPage(page("dream-page", JournalId.DREAM, date, "Dream memory", favorite = false))
        dao.upsertPage(page("workout-page", workout, date, "Workout memory", favorite = false))
        dao.insertAttachment(attachment("daily-photo", "daily-page", "Daily caption"))
        dao.insertAttachment(attachment("dream-photo", "dream-page", "Dream caption"))

        assertEquals(
            "daily-page",
            dao.observePage(JournalId.PRIMARY.storageValue, date).first()?.id,
        )
        assertEquals(
            "dream-page",
            dao.observePage(JournalId.DREAM.storageValue, date).first()?.id,
        )
        assertEquals(
            listOf("Daily memory"),
            dao.observeAllDays(JournalId.PRIMARY.storageValue).first().map(MonthDayRow::plainText),
        )
        assertEquals(
            listOf("Dream memory"),
            dao.observeAllDays(JournalId.DREAM.storageValue).first().map(MonthDayRow::plainText),
        )
        assertEquals(
            listOf("daily-photo"),
            dao.observeAllMedia(JournalId.PRIMARY.storageValue).first().map { it.attachment.id },
        )
        assertEquals(
            listOf("dream-photo"),
            dao.observeAllMedia(JournalId.DREAM.storageValue).first().map { it.attachment.id },
        )
        assertEquals(
            listOf("daily-photo"),
            dao.observeAttachments(JournalId.PRIMARY.storageValue, date).first().map(AttachmentEntity::id),
        )
        assertEquals(
            listOf("dream-photo"),
            dao.observeAttachments(JournalId.DREAM.storageValue, date).first().map(AttachmentEntity::id),
        )
        assertEquals(
            listOf("Daily memory"),
            dao.observeMonth(JournalId.PRIMARY.storageValue, date, date).first().map(MonthDayRow::plainText),
        )
        assertEquals(
            listOf("Dream memory"),
            dao.search(JournalId.DREAM.storageValue, "memory").first().map(DiaryPageEntity::plainText),
        )
        assertEquals(listOf("Workout memory"), dao.observeAllDays(workout.storageValue).first().map(MonthDayRow::plainText))
    }

    private fun page(
        id: String,
        kind: JournalId,
        date: String,
        text: String,
        favorite: Boolean,
    ) = DiaryPageEntity(
        id = id,
        journalId = kind.storageValue,
        diaryDate = date,
        documentJson = "{}",
        plainText = text,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        revision = 1,
        locationName = if (kind == JournalId.PRIMARY) "Brussels" else "Antwerp",
        latitude = if (kind == JournalId.PRIMARY) 50.85 else 51.22,
        longitude = if (kind == JournalId.PRIMARY) 4.35 else 4.40,
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
