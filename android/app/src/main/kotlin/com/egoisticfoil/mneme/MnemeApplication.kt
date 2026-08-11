package com.egoisticfoil.mneme

import android.app.Application
import com.egoisticfoil.mneme.data.AppSettingsRepository
import com.egoisticfoil.mneme.data.DiaryRepository
import com.egoisticfoil.mneme.data.MnemeDatabase
import com.egoisticfoil.mneme.data.PlaceSearchRepository
import com.egoisticfoil.mneme.data.BackupRepository
import com.egoisticfoil.mneme.data.BackupWorker
import com.egoisticfoil.mneme.data.UpdateRepository
import com.egoisticfoil.mneme.data.DiaryExportRepository
import org.maplibre.android.MapLibre

class MnemeApplication : Application() {
    val database by lazy { MnemeDatabase.create(this) }
    val diaryRepository by lazy { DiaryRepository(this, database.diaryDao()) }
    val settingsRepository by lazy { AppSettingsRepository(this) }
    val placeSearchRepository by lazy { PlaceSearchRepository(this) }
    val backupRepository by lazy { BackupRepository(this, database.diaryDao(), settingsRepository) }
    val updateRepository by lazy { UpdateRepository(this) }
    val exportRepository by lazy { DiaryExportRepository(this, database.diaryDao()) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        if (settingsRepository.settings.value.serverConnected) BackupWorker.schedule(this)
    }
}
