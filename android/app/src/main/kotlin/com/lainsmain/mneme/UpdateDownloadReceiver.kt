package com.lainsmain.mneme

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lainsmain.mneme.data.UpdateRepository

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = UpdateRepository(context)
        val downloadIds = when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> longArrayOf(
                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L),
            )
            DownloadManager.ACTION_NOTIFICATION_CLICKED ->
                intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS) ?: longArrayOf()
            else -> return
        }
        downloadIds.firstOrNull(repository::isTrackedDownload)?.let { downloadId ->
            repository.openInstaller(downloadId)
        }
    }
}
