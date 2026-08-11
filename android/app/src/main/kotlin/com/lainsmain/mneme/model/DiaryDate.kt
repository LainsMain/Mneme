package com.lainsmain.mneme.model

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

object DiaryDate {
    /**
     * The suggested page is yesterday before [lateNightCutoffHour], but this is
     * only a convenience. Every date remains editable at all times.
     */
    fun suggestedDate(clock: Clock, lateNightCutoffHour: Int = 4): LocalDate {
        require(lateNightCutoffHour in 0..23)
        val now = LocalDateTime.now(clock)
        return if (now.hour < lateNightCutoffHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }
}
