package com.lainsmain.mneme.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryKindTest {
    @Test
    fun unknownOrMissingStorageValue_isDaily() {
        assertEquals(EntryKind.Daily, EntryKind.fromStorage(null))
        assertEquals(EntryKind.Daily, EntryKind.fromStorage("future-kind"))
    }

    @Test
    fun oldVaultPageWithoutKind_restoresAsDaily() {
        val page = Json { ignoreUnknownKeys = true }.decodeFromString<VaultPage>(
            """
            {
              "id":"old-page",
              "date":"2026-08-26",
              "document":"{}",
              "plainText":"Existing journal",
              "createdAtEpochMillis":1,
              "updatedAtEpochMillis":2,
              "revision":3
            }
            """.trimIndent(),
        )

        assertEquals(EntryKind.Daily.storageValue, page.entryKind)
    }
}
