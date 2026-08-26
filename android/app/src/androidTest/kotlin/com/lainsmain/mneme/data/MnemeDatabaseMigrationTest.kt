package com.lainsmain.mneme.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MnemeDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MnemeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun removeDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate3To4_keepsExistingPagesDaily_andAllowsDreamOnSameDate() {
        helper.createDatabase(TEST_DATABASE, 3).use { database ->
            database.execSQL(
                """
                INSERT INTO diary_pages (
                    id, diaryDate, documentJson, plainText, createdAtEpochMillis,
                    updatedAtEpochMillis, revision, locationIsManual, isFavorite
                ) VALUES ('daily-id', '2026-08-26', '{}', 'Existing writing', 1, 2, 3, 0, 1)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO attachments (
                    id, pageId, encryptedFileName, mimeType, byteSize, sha256, caption,
                    sortOrder, normalizedExifJson, createdAtEpochMillis
                ) VALUES ('photo-id', 'daily-id', '/photo.jpg', 'image/jpeg', 42,
                    'hash', 'Caption', 0, '{}', 3)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO monthly_recaps (
                    id, yearMonth, documentJson, plainText, createdAtEpochMillis,
                    updatedAtEpochMillis, revision
                ) VALUES ('recap-id', '2026-08', '{}', 'August recap', 1, 2, 1)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            MnemeDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query(
                "SELECT entryKind, plainText, isFavorite FROM diary_pages WHERE id = 'daily-id'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(EntryKind.Daily.storageValue, cursor.getString(0))
                assertEquals("Existing writing", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
            }
            database.query(
                "SELECT attachment.caption FROM attachments AS attachment " +
                    "JOIN diary_pages AS page ON page.id = attachment.pageId " +
                    "WHERE page.entryKind = 'daily'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Caption", cursor.getString(0))
            }
            database.query("SELECT plainText FROM monthly_recaps WHERE id = 'recap-id'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("August recap", cursor.getString(0))
            }
            database.execSQL(
                """
                INSERT INTO diary_pages (
                    id, entryKind, diaryDate, documentJson, plainText, createdAtEpochMillis,
                    updatedAtEpochMillis, revision, locationIsManual, isFavorite
                ) VALUES ('dream-id', 'dream', '2026-08-26', '{}', 'A dream', 4, 5, 1, 0, 0)
                """.trimIndent(),
            )
            database.query("SELECT COUNT(*) FROM diary_pages WHERE diaryDate = '2026-08-26'").use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "mneme-migration-test"
    }
}
