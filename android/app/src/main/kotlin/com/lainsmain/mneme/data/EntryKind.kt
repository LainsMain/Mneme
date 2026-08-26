package com.lainsmain.mneme.data

/** Stable diary namespace persisted in Room, backups, and portable exports. */
enum class EntryKind(val storageValue: String) {
    Daily("daily"),
    Dream("dream");

    companion object {
        fun fromStorage(value: String?): EntryKind = entries.firstOrNull {
            it.storageValue.equals(value, ignoreCase = true)
        } ?: Daily
    }
}
