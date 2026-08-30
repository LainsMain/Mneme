package com.lainsmain.mneme.data

import java.util.UUID

/** Stable journal namespace persisted in Room, backups, and portable exports. */
@JvmInline
value class JournalId(val storageValue: String) {
    init {
        require(storageValue == "daily" || storageValue == "dream" || CUSTOM.matches(storageValue)) {
            "Invalid journal id"
        }
    }

    companion object {
        val PRIMARY = JournalId("daily")
        val DREAM = JournalId("dream")
        private val CUSTOM = Regex("custom:[0-9a-fA-F-]{36}")

        fun custom(): JournalId = JournalId("custom:${UUID.randomUUID()}")

        fun fromStorage(value: String?): JournalId = runCatching {
            JournalId(value.orEmpty())
        }.getOrDefault(PRIMARY)

        fun parse(value: String?): JournalId? = runCatching { JournalId(value.orEmpty()) }.getOrNull()
    }
}

enum class JournalRole(val storageValue: String) {
    Primary("primary"),
    Dream("dream"),
    Custom("custom");

    companion object {
        fun fromStorage(value: String?): JournalRole = entries.firstOrNull {
            it.storageValue.equals(value, ignoreCase = true)
        } ?: Custom
    }
}

enum class JournalIconKind(val storageValue: String) {
    Material("material"),
    Emoji("emoji");

    companion object {
        fun fromStorage(value: String?): JournalIconKind = entries.firstOrNull {
            it.storageValue.equals(value, ignoreCase = true)
        } ?: Material
    }
}

data class JournalIcon(
    val kind: JournalIconKind,
    val value: String,
)

data class JournalDefinition(
    val id: JournalId,
    val role: JournalRole,
    val title: String,
    val icon: JournalIcon,
    val accentPalette: ColorPalette?,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null,
) {
    val isArchived: Boolean get() = archivedAtEpochMillis != null
    val supportsMonthlyRecaps: Boolean get() = role != JournalRole.Dream
    val supportsYesterdayPrompt: Boolean get() = role != JournalRole.Dream

    companion object {
        val Primary = JournalDefinition(
            id = JournalId.PRIMARY,
            role = JournalRole.Primary,
            title = "Journal",
            icon = JournalIcon(JournalIconKind.Material, "journal"),
            accentPalette = null,
            sortOrder = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )
        val Dream = JournalDefinition(
            id = JournalId.DREAM,
            role = JournalRole.Dream,
            title = "Dreams",
            icon = JournalIcon(JournalIconKind.Material, "dream"),
            accentPalette = null,
            sortOrder = 1,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )
    }
}

data class JournalContentCounts(
    val entries: Int,
    val photos: Int,
    val recaps: Int,
)
