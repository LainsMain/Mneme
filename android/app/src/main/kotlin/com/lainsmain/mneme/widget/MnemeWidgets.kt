package com.lainsmain.mneme.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.lainsmain.mneme.MainActivity
import com.lainsmain.mneme.MnemeApplication
import com.lainsmain.mneme.R
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

const val ACTION_OPEN_DIARY_DATE = "com.lainsmain.mneme.action.OPEN_DIARY_DATE"
const val EXTRA_DIARY_DATE = "diary_date"

object MnemeWidgetUpdater {
    fun requestUpdate(context: Context) {
        updateProvider(context, TodayWidgetProvider::class.java)
        updateProvider(context, FavoriteWidgetProvider::class.java)
    }

    private fun updateProvider(context: Context, provider: Class<out AppWidgetProvider>) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, provider)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(context, provider).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            },
        )
    }
}

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as MnemeApplication
                val snapshot = application.database.diaryDao().exportSnapshot()
                val today = LocalDate.now()
                val page = snapshot.pages.firstOrNull { it.diaryDate == today.toString() }
                val photoCount = page?.let { selected ->
                    snapshot.attachments.count { it.pageId == selected.id }
                } ?: 0
                val locked = application.settingsRepository.settings.value.appLockEnabled
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_today)
                    views.setTextViewText(
                        R.id.widget_date,
                        today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    )
                    val status = when {
                        locked -> "Private journal · tap to unlock"
                        page == null -> "A blank page is waiting"
                        else -> {
                            val words = page.plainText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                            val parts = buildList {
                                if (words > 0) add("$words ${if (words == 1) "word" else "words"}")
                                if (photoCount > 0) add("$photoCount ${if (photoCount == 1) "photo" else "photos"}")
                            }
                            parts.joinToString(" · ").ifBlank { "Keep this day close" }
                        }
                    }
                    views.setTextViewText(R.id.widget_status, status)
                    val openToday = openDateIntent(context, today, id)
                    views.setOnClickPendingIntent(R.id.widget_today_root, openToday)
                    views.setOnClickPendingIntent(R.id.widget_write, openToday)
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class FavoriteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as MnemeApplication
                val snapshot = application.database.diaryDao().exportSnapshot()
                val favorite = snapshot.pages
                    .asSequence()
                    .filter { it.deletedAtEpochMillis == null && it.isFavorite }
                    .maxByOrNull { it.diaryDate }
                val photo = favorite?.let { page ->
                    snapshot.attachments.filter { it.pageId == page.id }.minByOrNull { it.sortOrder }
                }
                val locked = application.settingsRepository.settings.value.appLockEnabled
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_favorite)
                    when {
                        locked -> {
                            views.setTextViewText(R.id.widget_favorite_date, "FAVORITE MEMORY")
                            views.setTextViewText(R.id.widget_favorite_title, "Your journal is locked")
                            views.setTextViewText(R.id.widget_favorite_detail, "Tap to open Mneme privately")
                            views.setViewVisibility(R.id.widget_favorite_photo, View.GONE)
                        }
                        favorite == null -> {
                            views.setTextViewText(R.id.widget_favorite_date, "FAVORITE MEMORY")
                            views.setTextViewText(R.id.widget_favorite_title, "Keep something close")
                            views.setTextViewText(R.id.widget_favorite_detail, "Favorite an entry and it will live here")
                            views.setViewVisibility(R.id.widget_favorite_photo, View.GONE)
                        }
                        else -> {
                            val date = LocalDate.parse(favorite.diaryDate)
                            views.setTextViewText(
                                R.id.widget_favorite_date,
                                date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())).uppercase(),
                            )
                            views.setTextViewText(
                                R.id.widget_favorite_title,
                                favorite.plainText.lineSequence().firstOrNull { it.isNotBlank() } ?: "Photo memory",
                            )
                            views.setTextViewText(
                                R.id.widget_favorite_detail,
                                favorite.locationName ?: "Tap to revisit this entry",
                            )
                            val bitmap = (photo?.thumbnailFileName ?: photo?.encryptedFileName)?.let(::File)
                                ?.takeIf(File::isFile)
                                ?.let { BitmapFactory.decodeFile(it.path) }
                            if (bitmap != null) {
                                views.setViewVisibility(R.id.widget_favorite_photo, View.VISIBLE)
                                views.setImageViewBitmap(R.id.widget_favorite_photo, bitmap)
                            } else {
                                views.setViewVisibility(R.id.widget_favorite_photo, View.GONE)
                            }
                        }
                    }
                    val targetDate = favorite?.diaryDate?.let(LocalDate::parse) ?: LocalDate.now()
                    views.setOnClickPendingIntent(
                        R.id.widget_favorite_root,
                        openDateIntent(context, targetDate, 10_000 + id),
                    )
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

private fun openDateIntent(context: Context, date: LocalDate, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DIARY_DATE
            putExtra(EXTRA_DIARY_DATE, date.toString())
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
