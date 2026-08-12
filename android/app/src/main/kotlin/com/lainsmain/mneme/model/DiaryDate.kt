package com.lainsmain.mneme.model

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

object DiaryDate {
    /**
     * Returns yesterday only during the late-night window from midnight until
     * [lateNightCutoffHour]. Every date remains editable at all times.
     */
    fun yesterdaySuggestion(clock: Clock, lateNightCutoffHour: Int = 6): LocalDate? {
        require(lateNightCutoffHour in 1..12)
        val now = LocalDateTime.now(clock)
        return now.takeIf { it.hour < lateNightCutoffHour }
            ?.toLocalDate()
            ?.minusDays(1)
    }
}
