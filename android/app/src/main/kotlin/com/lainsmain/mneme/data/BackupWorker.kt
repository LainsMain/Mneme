package com.lainsmain.mneme.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lainsmain.mneme.MnemeApplication
import java.util.concurrent.TimeUnit

class BackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as MnemeApplication
        if (!application.settingsRepository.settings.value.serverConnected) return Result.success()
        return application.backupRepository.backupNow().fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() },
        )
    }

    companion object {
        private const val UNIQUE_WORK = "mneme-periodic-vault-backup"
        private const val UNIQUE_SOON_WORK = "mneme-after-change-vault-backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun scheduleSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SOON_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SOON_WORK)
        }
    }
}
