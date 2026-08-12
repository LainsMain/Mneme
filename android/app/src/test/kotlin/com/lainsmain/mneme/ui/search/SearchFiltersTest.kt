package com.lainsmain.mneme.ui.search

import com.lainsmain.mneme.data.DaySummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFiltersTest {
    private val beach = DaySummary(
        date = LocalDate.of(2026, 8, 10),
        hasWriting = true,
        wordCount = 3,
        thumbnailFileName = "/photo.jpg",
        plainText = "A bright beach day",
        locationName = "Ostend",
        attachmentCount = 2,
        isFavorite = true,
    )
    private val quiet = DaySummary(
        date = LocalDate.of(2026, 7, 4),
        hasWriting = true,
        wordCount = 3,
        thumbnailFileName = null,
        plainText = "Read at home",
    )

    @Test
    fun `blank search without filters stays empty`() {
        assertEquals(emptyList<DaySummary>(), filterEntries(listOf(beach, quiet), "", SearchFilters()))
    }

    @Test
    fun `query searches writing and place names case insensitively`() {
        assertEquals(listOf(beach), filterEntries(listOf(beach, quiet), "OSTEND", SearchFilters()))
        assertEquals(listOf(quiet), filterEntries(listOf(beach, quiet), "read", SearchFilters()))
    }

    @Test
    fun `filters combine and date bounds are inclusive`() {
        val filters = SearchFilters(
            hasPhotos = true,
            hasLocation = true,
            favoritesOnly = true,
            fromDate = LocalDate.of(2026, 8, 10),
            toDate = LocalDate.of(2026, 8, 10),
        )
        assertEquals(listOf(beach), filterEntries(listOf(beach, quiet), "", filters))
    }
}
