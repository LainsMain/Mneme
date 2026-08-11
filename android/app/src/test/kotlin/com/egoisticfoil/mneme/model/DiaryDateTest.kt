package com.egoisticfoil.mneme.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryDateTest {
    private val zone = ZoneId.of("Europe/Brussels")

    @Test
    fun suggestsYesterdayBeforeCutoff() {
        val clock = Clock.fixed(Instant.parse("2026-08-11T00:30:00Z"), zone)
        assertEquals(LocalDate.of(2026, 8, 10), DiaryDate.suggestedDate(clock, 4))
    }

    @Test
    fun suggestsTodayAfterCutoff() {
        val clock = Clock.fixed(Instant.parse("2026-08-11T08:00:00Z"), zone)
        assertEquals(LocalDate.of(2026, 8, 11), DiaryDate.suggestedDate(clock, 4))
    }
}
