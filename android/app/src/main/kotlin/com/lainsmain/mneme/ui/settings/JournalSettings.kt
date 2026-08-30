package com.lainsmain.mneme.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lainsmain.mneme.data.ColorPalette
import com.lainsmain.mneme.data.JournalContentCounts
import com.lainsmain.mneme.data.JournalDefinition
import com.lainsmain.mneme.data.JournalIcon
import com.lainsmain.mneme.data.JournalIconKind
import com.lainsmain.mneme.data.JournalId
import com.lainsmain.mneme.data.JournalRepository
import com.lainsmain.mneme.data.JournalRole
import com.lainsmain.mneme.ui.JournalIconView
import com.lainsmain.mneme.ui.journalMaterialIcon

@Composable
fun JournalSettingsCard(
    journals: List<JournalDefinition>,
    dreamEnabled: Boolean,
    onDreamEnabledChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LibraryBooks, null) } }
        Text("Journals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Dream diary", fontWeight = FontWeight.SemiBold)
                    Text(
                        "A separate wake-up-date journal. Hiding it never deletes it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(dreamEnabled, onDreamEnabledChange)
            }
            Text(
                "${journals.count { !it.isArchived && (it.role != JournalRole.Dream || dreamEnabled) }} visible · " +
                    "${journals.count { it.role == JournalRole.Custom }} custom",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Text("Create journal", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                Text("Manage journals")
            }
        }
    }
}

@Composable
fun JournalManagementScreen(
    journals: List<JournalDefinition>,
    dreamEnabled: Boolean,
    message: String?,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (JournalDefinition) -> Unit,
    onDreamEnabledChange: (Boolean) -> Unit,
    onReorder: (List<JournalId>) -> Unit,
    onSetArchived: (JournalId, Boolean) -> Unit,
    onLoadCounts: (JournalId, (JournalContentCounts) -> Unit) -> Unit,
    onDelete: (JournalId, String) -> Unit,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val custom = remember { mutableStateListOf<JournalDefinition>() }
    LaunchedEffect(journals) {
        custom.clear()
        custom.addAll(journals.filter { it.role == JournalRole.Custom && !it.isArchived }.sortedBy { it.sortOrder })
    }
    val archived = journals.filter { it.role == JournalRole.Custom && it.isArchived }.sortedBy { it.sortOrder }
    var archivedExpanded by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<JournalDefinition?>(null) }
    var deleteCounts by remember { mutableStateOf<JournalContentCounts?>(null) }
    var deleteConfirmation by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if (showHeader) ScreenHeader("Manage journals", onBack)
        Text(
            "Drag custom journals into the order you want. Journal and Dreams stay at the top.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        BuiltInJournalRow(JournalDefinition.Primary, enabled = true, onEnabledChange = null)
        Spacer(Modifier.height(8.dp))
        BuiltInJournalRow(JournalDefinition.Dream, enabled = dreamEnabled, onEnabledChange = onDreamEnabledChange)
        custom.forEach { journal ->
            Spacer(Modifier.height(8.dp))
            var dragDistance by remember(journal.id.storageValue) { mutableFloatStateOf(0f) }
            JournalManagementRow(
                journal = journal,
                onEdit = { onEdit(journal) },
                onArchive = { onSetArchived(journal.id, true) },
                modifier = Modifier.pointerInput(journal.id.storageValue, custom.size) {
                    detectDragGestures(
                        onDragEnd = { dragDistance = 0f },
                        onDragCancel = { dragDistance = 0f },
                    ) { change, amount ->
                        change.consume()
                        dragDistance += amount.y
                        if (kotlin.math.abs(dragDistance) > 56.dp.toPx()) {
                            val from = custom.indexOfFirst { it.id == journal.id }
                            val to = (from + if (dragDistance > 0) 1 else -1).coerceIn(0, custom.lastIndex)
                            if (from >= 0 && from != to) {
                                val moved = custom.removeAt(from)
                                custom.add(to, moved)
                                onReorder(custom.map { it.id })
                            }
                            dragDistance = 0f
                        }
                    }
                },
            )
        }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Icon(Icons.Rounded.Add, null)
            Text("Create journal", Modifier.padding(start = 8.dp))
        }
        if (archived.isNotEmpty()) {
            Surface(
                onClick = { archivedExpanded = !archivedExpanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Archived (${archived.size})", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(if (archivedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
            }
            if (archivedExpanded) archived.forEach { journal ->
                ArchivedJournalRow(
                    journal = journal,
                    onRestore = { onSetArchived(journal.id, false) },
                    onDelete = {
                        deleteTarget = journal
                        deleteCounts = null
                        deleteConfirmation = ""
                        onLoadCounts(journal.id) { deleteCounts = it }
                    },
                )
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(32.dp))
    }

    deleteTarget?.let { journal ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Permanently delete ${journal.title}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        deleteCounts?.let { "This removes ${it.entries} entries, ${it.photos} photos, and ${it.recaps} recaps from this device." }
                            ?: "Counting this journal’s content…",
                    )
                    Text(
                        "A new backup is scheduled immediately. Older encrypted server snapshots can still contain this journal until server retention cleanup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = deleteConfirmation,
                        onValueChange = { deleteConfirmation = it },
                        label = { Text("Type ${journal.title} to confirm") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(journal.id, deleteConfirmation)
                        deleteTarget = null
                    },
                    enabled = deleteCounts != null && deleteConfirmation == journal.title,
                ) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun JournalEditorScreen(
    journal: JournalDefinition?,
    errorMessage: String?,
    onCancel: () -> Unit,
    onCreate: (String, JournalIcon, ColorPalette) -> Unit,
    onUpdate: (String, JournalIcon, ColorPalette) -> Unit,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var title by remember(journal?.id?.storageValue) { mutableStateOf(journal?.title.orEmpty()) }
    var iconKind by remember(journal?.id?.storageValue) { mutableStateOf(journal?.icon?.kind ?: JournalIconKind.Material) }
    var materialIcon by remember(journal?.id?.storageValue) {
        mutableStateOf(journal?.icon?.value?.takeIf { journal.icon.kind == JournalIconKind.Material } ?: "journal")
    }
    var emoji by remember(journal?.id?.storageValue) {
        mutableStateOf(journal?.icon?.value?.takeIf { journal.icon.kind == JournalIconKind.Emoji } ?: "✨")
    }
    var palette by remember(journal?.id?.storageValue) { mutableStateOf(journal?.accentPalette ?: ColorPalette.Ocean) }
    val icon = JournalIcon(iconKind, if (iconKind == JournalIconKind.Material) materialIcon else emoji)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if (showHeader) ScreenHeader(if (journal == null) "New journal" else "Edit journal", onCancel)
        Surface(
            color = palette.previewColor().copy(alpha = 0.18f),
            border = BorderStroke(1.dp, palette.previewColor().copy(alpha = 0.65f)),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.previewColor(),
                    contentColor = Color.White,
                ) {
                    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        if (icon.kind == JournalIconKind.Emoji) Text(icon.value)
                        else Icon(journalMaterialIcon(icon.value), null)
                    }
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(title.ifBlank { "Your journal" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Journal · Today", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= 40) title = it },
            label = { Text("Journal title") },
            supportingText = { Text("${title.length}/40 · titles must be unique") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        Text("Icon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(iconKind == JournalIconKind.Material, { iconKind = JournalIconKind.Material }, { Text("Material icon") })
            FilterChip(iconKind == JournalIconKind.Emoji, { iconKind = JournalIconKind.Emoji }, { Text("Emoji") })
        }
        if (iconKind == JournalIconKind.Emoji) {
            OutlinedTextField(
                value = emoji,
                onValueChange = { emoji = it.take(12) },
                label = { Text("One emoji") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            JournalRepository.MATERIAL_ICON_KEYS.toList().chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        Surface(
                            onClick = { materialIcon = key },
                            shape = RoundedCornerShape(14.dp),
                            color = if (materialIcon == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = if (materialIcon == key) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.weight(1f),
                        ) { Box(Modifier.height(54.dp), contentAlignment = Alignment.Center) { Icon(journalMaterialIcon(key), key) } }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Text("Accent palette", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        ColorPalette.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected = palette == option,
                        onClick = { palette = option },
                        label = { Text(option.name) },
                        leadingIcon = { Box(Modifier.size(12.dp).background(option.previewColor(), CircleShape)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { if (journal == null) onCreate(title, icon, palette) else onUpdate(title, icon, palette) },
                enabled = title.trim().isNotEmpty() && title.length <= 40 && icon.value.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(if (journal == null) "Create journal" else "Save") }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BuiltInJournalRow(journal: JournalDefinition, enabled: Boolean, onEnabledChange: ((Boolean) -> Unit)?) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            JournalIconView(journal, Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(journal.title, fontWeight = FontWeight.SemiBold)
                Text("Built in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEnabledChange != null) Switch(enabled, onEnabledChange)
        }
    }
}

@Composable
private fun JournalManagementRow(journal: JournalDefinition, onEdit: () -> Unit, onArchive: () -> Unit, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DragHandle, "Drag to reorder", modifier.size(42.dp).padding(9.dp))
            JournalIconView(journal, Modifier.size(24.dp))
            Text(journal.title, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit") }
            IconButton(onClick = onArchive) { Icon(Icons.Rounded.Archive, "Archive") }
        }
    }
}

@Composable
private fun ArchivedJournalRow(journal: JournalDefinition, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        JournalIconView(journal, Modifier.size(22.dp))
        Text(journal.title, Modifier.weight(1f).padding(horizontal = 12.dp))
        IconButton(onClick = onRestore) { Icon(Icons.Rounded.Unarchive, "Restore") }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error) }
    }
}

private fun ColorPalette.previewColor(): Color = when (this) {
    ColorPalette.Ocean -> Color(0xFF39A7C8)
    ColorPalette.Forest -> Color(0xFF65A367)
    ColorPalette.Lavender -> Color(0xFF8B75BD)
    ColorPalette.Rose -> Color(0xFFC66B82)
    ColorPalette.Amber -> Color(0xFFD68A22)
    ColorPalette.Graphite -> Color(0xFF77737A)
}
