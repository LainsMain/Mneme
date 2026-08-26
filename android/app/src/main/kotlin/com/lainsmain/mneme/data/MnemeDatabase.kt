package com.lainsmain.mneme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DiaryPageEntity::class, AttachmentEntity::class, MonthlyRecapEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class MnemeDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        fun create(context: Context): MnemeDatabase =
            Room.databaseBuilder(context, MnemeDatabase::class.java, "mneme.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
    }
}
