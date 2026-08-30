package com.lainsmain.mneme.data

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/** Small, non-content preferences that keep each diary namespace where the user left it. */
class EntrySessionRepository(context: Context) {
    private val preferences = context.getSharedPreferences("mneme_entry_sessions", Context.MODE_PRIVATE)

    fun selectedDate(journalId: JournalId): LocalDate = preferences.getString(key(journalId, "date"), null)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()

    fun visibleMonth(journalId: JournalId): YearMonth = preferences.getString(key(journalId, "month"), null)
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: YearMonth.now()

    fun destination(journalId: JournalId): String =
        preferences.getString(key(journalId, "destination"), "Journal").orEmpty()

    fun setSelectedDate(journalId: JournalId, date: LocalDate) {
        preferences.edit().putString(key(journalId, "date"), date.toString()).apply()
    }

    fun setVisibleMonth(journalId: JournalId, month: YearMonth) {
        preferences.edit().putString(key(journalId, "month"), month.toString()).apply()
    }

    fun setDestination(journalId: JournalId, destination: String) {
        preferences.edit().putString(key(journalId, "destination"), destination).apply()
    }

    private fun key(journalId: JournalId, suffix: String) = "${journalId.storageValue}_$suffix"
}
