package com.lainsmain.mneme.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryDateTest {
    private val zone = ZoneId.of("Europe/Brussels")

    @Test
    fun suggestsYesterdayBetweenMidnightAndCutoff() {
        val clock = Clock.fixed(Instant.parse("2026-08-11T00:30:00Z"), zone)
        assertEquals(LocalDate.of(2026, 8, 10), DiaryDate.yesterdaySuggestion(clock, 6))
    }

    @Test
    fun stopsSuggestingAtCutoff() {
        val clock = Clock.fixed(Instant.parse("2026-08-11T04:00:00Z"), zone)
        assertEquals(null, DiaryDate.yesterdaySuggestion(clock, 6))
    }

    @Test
    fun respectsCustomCutoff() {
        val clock = Clock.fixed(Instant.parse("2026-08-11T05:30:00Z"), zone)
        assertEquals(LocalDate.of(2026, 8, 10), DiaryDate.yesterdaySuggestion(clock, 8))
    }
}
