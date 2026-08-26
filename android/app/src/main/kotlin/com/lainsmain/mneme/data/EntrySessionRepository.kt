package com.lainsmain.mneme.data

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/** Small, non-content preferences that keep each diary namespace where the user left it. */
class EntrySessionRepository(context: Context) {
    private val preferences = context.getSharedPreferences("mneme_entry_sessions", Context.MODE_PRIVATE)

    fun selectedDate(kind: EntryKind): LocalDate = preferences.getString(key(kind, "date"), null)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()

    fun visibleMonth(kind: EntryKind): YearMonth = preferences.getString(key(kind, "month"), null)
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: YearMonth.now()

    fun destination(kind: EntryKind): String =
        preferences.getString(key(kind, "destination"), "Journal").orEmpty()

    fun setSelectedDate(kind: EntryKind, date: LocalDate) {
        preferences.edit().putString(key(kind, "date"), date.toString()).apply()
    }

    fun setVisibleMonth(kind: EntryKind, month: YearMonth) {
        preferences.edit().putString(key(kind, "month"), month.toString()).apply()
    }

    fun setDestination(kind: EntryKind, destination: String) {
        preferences.edit().putString(key(kind, "destination"), destination).apply()
    }

    private fun key(kind: EntryKind, suffix: String) = "${kind.storageValue}_$suffix"
}
