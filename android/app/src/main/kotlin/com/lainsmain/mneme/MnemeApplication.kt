package com.lainsmain.mneme

import android.app.Application
import com.lainsmain.mneme.data.AppSettingsRepository
import com.lainsmain.mneme.data.DiaryRepository
import com.lainsmain.mneme.data.MnemeDatabase
import com.lainsmain.mneme.data.PlaceSearchRepository
import com.lainsmain.mneme.data.BackupRepository
import com.lainsmain.mneme.data.BackupWorker
import com.lainsmain.mneme.data.UpdateRepository
import org.maplibre.android.MapLibre

class MnemeApplication : Application() {
    val database by lazy { MnemeDatabase.create(this) }
    val diaryRepository by lazy { DiaryRepository(this, database.diaryDao()) }
    val settingsRepository by lazy { AppSettingsRepository(this) }
    val placeSearchRepository by lazy { PlaceSearchRepository(this) }
    val backupRepository by lazy { BackupRepository(this, database.diaryDao(), settingsRepository) }
    val updateRepository by lazy { UpdateRepository(this) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        if (settingsRepository.settings.value.serverConnected) BackupWorker.schedule(this)
    }
}
