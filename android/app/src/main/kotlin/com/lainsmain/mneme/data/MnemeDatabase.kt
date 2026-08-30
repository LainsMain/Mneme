package com.lainsmain.mneme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [JournalEntity::class, DiaryPageEntity::class, AttachmentEntity::class, MonthlyRecapEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class MnemeDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        fun create(context: Context): MnemeDatabase =
            Room.databaseBuilder(context, MnemeDatabase::class.java, "mneme.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE diary_pages ADD COLUMN locationName TEXT")
                database.execSQL("ALTER TABLE diary_pages ADD COLUMN latitude REAL")
                database.execSQL("ALTER TABLE diary_pages ADD COLUMN longitude REAL")
                database.execSQL(
                    "ALTER TABLE diary_pages ADD COLUMN locationIsManual INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE diary_pages ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE diary_pages ADD COLUMN entryKind TEXT NOT NULL DEFAULT 'daily'",
                )
                database.execSQL("DROP INDEX IF EXISTS index_diary_pages_diaryDate")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_diary_pages_entryKind_diaryDate " +
                        "ON diary_pages(entryKind, diaryDate)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS journals (
                        id TEXT NOT NULL PRIMARY KEY,
                        role TEXT NOT NULL,
                        title TEXT NOT NULL,
                        iconKind TEXT NOT NULL,
                        iconValue TEXT NOT NULL,
                        accentPalette TEXT,
                        sortOrder INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        archivedAtEpochMillis INTEGER
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO journals VALUES " +
                        "('daily','primary','Journal','material','journal',NULL,0,0,0,NULL)",
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO journals VALUES " +
                        "('dream','dream','Dreams','material','dream',NULL,1,0,0,NULL)",
                )
                database.execSQL(
                    "ALTER TABLE monthly_recaps ADD COLUMN journalId TEXT NOT NULL DEFAULT 'daily'",
                )
                database.execSQL("DROP INDEX IF EXISTS index_monthly_recaps_yearMonth")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_monthly_recaps_journalId_yearMonth " +
                        "ON monthly_recaps(journalId, yearMonth)",
                )
            }
        }
    }
}
