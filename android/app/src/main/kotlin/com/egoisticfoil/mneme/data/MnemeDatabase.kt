package com.egoisticfoil.mneme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DiaryPageEntity::class, AttachmentEntity::class, MonthlyRecapEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MnemeDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        fun create(context: Context): MnemeDatabase =
            Room.databaseBuilder(context, MnemeDatabase::class.java, "mneme.db")
                .addMigrations(MIGRATION_1_2)
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
    }
}
