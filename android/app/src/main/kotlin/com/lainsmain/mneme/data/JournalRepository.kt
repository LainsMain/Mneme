package com.lainsmain.mneme.data

import android.content.Context
import com.lainsmain.mneme.widget.MnemeWidgetUpdater
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JournalRepository(
    private val context: Context,
    private val diaryDao: DiaryDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val journals: Flow<List<JournalDefinition>> = diaryDao.observeJournals().map { rows ->
        rows.map(JournalEntity::toModel)
    }

    init {
        scope.launch { ensureBuiltIns() }
    }

    suspend fun ensureBuiltIns() = withContext(Dispatchers.IO) {
        if (diaryDao.journalById(JournalId.PRIMARY.storageValue) == null) {
            diaryDao.upsertJournal(JournalDefinition.Primary.toEntity())
        }
        if (diaryDao.journalById(JournalId.DREAM.storageValue) == null) {
            diaryDao.upsertJournal(JournalDefinition.Dream.toEntity())
        }
    }

    suspend fun create(
        title: String,
        icon: JournalIcon,
        palette: ColorPalette,
    ): Result<JournalDefinition> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedTitle = validateTitle(title)
            validateUniqueTitle(normalizedTitle, excluding = null)
            val normalizedIcon = validateIcon(icon)
            val now = System.currentTimeMillis()
            val nextOrder = diaryDao.journalsForBackup().maxOfOrNull(JournalEntity::sortOrder)?.plus(1) ?: 2
            JournalDefinition(
                id = JournalId.custom(),
                role = JournalRole.Custom,
                title = normalizedTitle,
                icon = normalizedIcon,
                accentPalette = palette,
                sortOrder = nextOrder,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ).also { diaryDao.upsertJournal(it.toEntity()) }
                .also { BackupWorker.scheduleSoon(context) }
        }
    }

    suspend fun update(
        id: JournalId,
        title: String,
        icon: JournalIcon,
        palette: ColorPalette,
    ): Result<JournalDefinition> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = requireNotNull(diaryDao.journalById(id.storageValue)) { "Journal no longer exists." }
            require(JournalRole.fromStorage(existing.role) == JournalRole.Custom) { "Built-in journals cannot be edited." }
            val normalizedTitle = validateTitle(title)
            validateUniqueTitle(normalizedTitle, excluding = id)
            val updated = existing.copy(
                title = normalizedTitle,
                iconKind = validateIcon(icon).kind.storageValue,
                iconValue = validateIcon(icon).value,
                accentPalette = palette.name,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            diaryDao.upsertJournal(updated)
            BackupWorker.scheduleSoon(context)
            updated.toModel()
        }
    }

    suspend fun reorder(customIds: List<JournalId>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        customIds.distinct().forEachIndexed { index, id ->
            val row = diaryDao.journalById(id.storageValue) ?: return@forEachIndexed
            if (JournalRole.fromStorage(row.role) == JournalRole.Custom) {
                diaryDao.setJournalSortOrder(id.storageValue, index + 2, now)
            }
        }
        BackupWorker.scheduleSoon(context)
    }

    suspend fun setArchived(id: JournalId, archived: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val row = requireNotNull(diaryDao.journalById(id.storageValue)) { "Journal no longer exists." }
            require(JournalRole.fromStorage(row.role) == JournalRole.Custom) { "Built-in journals cannot be archived." }
            diaryDao.setJournalArchived(
                journalId = id.storageValue,
                archivedAt = if (archived) System.currentTimeMillis() else null,
                updatedAt = System.currentTimeMillis(),
            )
            BackupWorker.scheduleSoon(context)
        }
    }

    suspend fun counts(id: JournalId): JournalContentCounts = withContext(Dispatchers.IO) {
        JournalContentCounts(
            entries = diaryDao.entryCount(id.storageValue),
            photos = diaryDao.photoCount(id.storageValue),
            recaps = diaryDao.recapCount(id.storageValue),
        )
    }

    suspend fun delete(id: JournalId, confirmationTitle: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val row = requireNotNull(diaryDao.journalById(id.storageValue)) { "Journal no longer exists." }
            require(JournalRole.fromStorage(row.role) == JournalRole.Custom) { "Built-in journals cannot be deleted." }
            require(row.archivedAtEpochMillis != null) { "Archive this journal before deleting it." }
            require(confirmationTitle.trim() == row.title) { "Type the journal title exactly." }
            val files = diaryDao.attachmentsForJournal(id.storageValue).flatMap { attachment ->
                listOfNotNull(attachment.encryptedFileName, attachment.thumbnailFileName)
            }
            diaryDao.deleteJournal(id.storageValue)
            files.forEach { path -> File(path).delete() }
            BackupWorker.scheduleSoon(context)
            MnemeWidgetUpdater.requestUpdate(context)
        }
    }

    private suspend fun validateUniqueTitle(title: String, excluding: JournalId?) {
        val duplicate = diaryDao.journalsForBackup().any {
            it.id != excluding?.storageValue && it.title.equals(title, ignoreCase = true)
        }
        require(!duplicate) { "A journal with this title already exists." }
    }

    private fun validateTitle(value: String): String = value.trim().also {
        require(it.length in 1..40) { "Use a title between 1 and 40 characters." }
    }

    private fun validateIcon(icon: JournalIcon): JournalIcon = when (icon.kind) {
        JournalIconKind.Material -> icon.copy(
            value = icon.value.takeIf(MATERIAL_ICON_KEYS::contains) ?: "journal",
        )
        JournalIconKind.Emoji -> icon.copy(value = icon.value.trim()).also {
            val iterator = android.icu.text.BreakIterator.getCharacterInstance().apply { setText(it.value) }
            iterator.first()
            val first = iterator.next()
            val second = iterator.next()
            require(it.value.isNotBlank() && first == it.value.length && second == android.icu.text.BreakIterator.DONE) {
                "Choose one emoji."
            }
        }
    }

    companion object {
        val MATERIAL_ICON_KEYS = setOf(
            "journal", "fitness", "gratitude", "travel", "food", "work", "study", "health",
            "nature", "music", "art", "family", "heart", "star", "idea", "camera",
        )
    }
}

internal fun JournalEntity.toModel() = JournalDefinition(
    id = JournalId.fromStorage(id),
    role = JournalRole.fromStorage(role),
    title = title,
    icon = JournalIcon(JournalIconKind.fromStorage(iconKind), iconValue),
    accentPalette = accentPalette?.let { stored -> ColorPalette.entries.firstOrNull { it.name == stored } },
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    archivedAtEpochMillis = archivedAtEpochMillis,
)

internal fun JournalDefinition.toEntity() = JournalEntity(
    id = id.storageValue,
    role = role.storageValue,
    title = title,
    iconKind = icon.kind.storageValue,
    iconValue = icon.value,
    accentPalette = accentPalette?.name,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    archivedAtEpochMillis = archivedAtEpochMillis,
)
