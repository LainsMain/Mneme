package com.lainsmain.mneme.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalIdTest {
    @Test
    fun unknownOrMissingStorageValue_isDaily() {
        assertEquals(JournalId.PRIMARY, JournalId.fromStorage(null))
        assertEquals(JournalId.PRIMARY, JournalId.fromStorage("future-kind"))
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

        assertEquals(JournalId.PRIMARY.storageValue, page.entryKind)
        assertEquals(null, page.journalId)
    }

    @Test
    fun customJournalId_isStableAndValid() {
        val id = JournalId.custom()
        assertEquals(id, JournalId.parse(id.storageValue))
    }

    @Test
    fun vaultV3_roundTripsCustomJournalAndScopedRecap() {
        val json = Json { ignoreUnknownKeys = true }
        val customId = "custom:00000000-0000-0000-0000-000000000035"
        val manifest = VaultManifest(
            format = "mneme-vault-v3",
            deviceId = "test",
            revision = 1,
            createdAtEpochMillis = 2,
            journals = listOf(
                VaultJournal(customId, "custom", "Workout", "material", "fitness", "Forest", 2, 1, 2),
            ),
            pages = emptyList(),
            attachments = emptyList(),
            monthlyRecaps = listOf(VaultMonthlyRecap("r", customId, "2026-08", "{}", "", 1, 2, 1)),
        )

        val restored = json.decodeFromString<VaultManifest>(json.encodeToString(manifest))
        assertEquals(customId, restored.journals.single().id)
        assertEquals(customId, restored.monthlyRecaps.single().journalId)
    }

    @Test
    fun vaultV2ManifestWithoutJournals_remainsReadable() {
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<VaultManifest>(
            """{"format":"mneme-vault-v2","deviceId":"old","revision":1,"createdAtEpochMillis":2,"pages":[],"attachments":[]}""",
        )
        assertEquals(emptyList<VaultJournal>(), manifest.journals)
    }
}
