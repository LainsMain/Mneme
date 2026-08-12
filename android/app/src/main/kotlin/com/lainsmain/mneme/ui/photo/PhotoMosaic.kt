package com.lainsmain.mneme.ui.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lainsmain.mneme.data.DiaryAttachment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoMosaic(
    attachments: List<DiaryAttachment>,
    onOpen: (DiaryAttachment) -> Unit,
    onMakePrimary: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetCaption: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val gap = 5.dp
    when (attachments.size) {
        1 -> PhotoTile(
            attachment = attachments[0],
            onClick = { onOpen(attachments[0]) },
            isPrimary = true,
            onMakePrimary = onMakePrimary,
            onDelete = onDelete,
            onSetCaption = onSetCaption,
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp),
        )

        2 -> Row(modifier.fillMaxWidth().height(235.dp)) {
            PhotoTile(attachments[0], { onOpen(attachments[0]) }, Modifier.weight(1f), true, onMakePrimary, onDelete, onSetCaption)
            Box(Modifier.width(gap))
            PhotoTile(attachments[1], { onOpen(attachments[1]) }, Modifier.weight(1f), false, onMakePrimary, onDelete, onSetCaption)
        }

        3 -> Row(modifier.fillMaxWidth().height(285.dp)) {
            PhotoTile(attachments[0], { onOpen(attachments[0]) }, Modifier.weight(1.35f), true, onMakePrimary, onDelete, onSetCaption)
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .padding(start = gap),
            ) {
                PhotoTile(attachments[1], { onOpen(attachments[1]) }, Modifier.weight(1f), false, onMakePrimary, onDelete, onSetCaption)
                PhotoTile(
                    attachments[2],
                    { onOpen(attachments[2]) },
                    Modifier
                        .weight(1f)
                        .padding(top = gap),
                    false,
                    onMakePrimary,
                    onDelete,
                    onSetCaption,
                )
            }
        }

        else -> Row(modifier.fillMaxWidth().height(300.dp)) {
            Column(Modifier.weight(1f)) {
                PhotoTile(attachments[0], { onOpen(attachments[0]) }, Modifier.weight(1f), true, onMakePrimary, onDelete, onSetCaption)
                PhotoTile(
                    attachments[2],
                    { onOpen(attachments[2]) },
                    Modifier
                        .weight(1f)
                        .padding(top = gap),
                    false,
                    onMakePrimary,
                    onDelete,
                    onSetCaption,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = gap),
            ) {
                PhotoTile(attachments[1], { onOpen(attachments[1]) }, Modifier.weight(1f), false, onMakePrimary, onDelete, onSetCaption)
                PhotoTile(
                    attachment = attachments[3],
                    onClick = { onOpen(attachments[3]) },
                    isPrimary = false,
                    onMakePrimary = onMakePrimary,
                    onDelete = onDelete,
                    onSetCaption = onSetCaption,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = gap),
                    overflowCount = (attachments.size - 4).coerceAtLeast(0),
                )
            }
        }
    }
}

@Composable
private fun PhotoTile(
    attachment: DiaryAttachment,
    onClick: () -> Unit,
    modifier: Modifier,
    isPrimary: Boolean,
    onMakePrimary: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetCaption: (String, String) -> Unit,
    overflowCount: Int = 0,
) {
    val bitmap by rememberFileBitmap(attachment.thumbnailPath ?: attachment.originalPath)
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editCaption by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        bitmap?.let { loaded ->
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = attachment.originalFileName ?: "Journal photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (overflowCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflowCount",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (attachment.caption.isNotBlank() && overflowCount == 0) {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.62f),
            ) {
                Text(
                    attachment.caption,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(7.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f),
            ) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Photo actions",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (attachment.caption.isBlank()) "Add caption" else "Edit caption") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        editCaption = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (isPrimary) "Primary photo" else "Set as primary") },
                    leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                    enabled = !isPrimary,
                    onClick = {
                        menuExpanded = false
                        onMakePrimary(attachment.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete photo") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        confirmDelete = true
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this photo?") },
            text = { Text("It will be removed from this journal entry and from Mneme's local storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(attachment.id)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
    if (editCaption) {
        PhotoCaptionDialog(
            initialCaption = attachment.caption,
            onDismiss = { editCaption = false },
            onSave = {
                onSetCaption(attachment.id, it)
                editCaption = false
            },
        )
    }
}

@Composable
fun PhotoCaptionDialog(
    initialCaption: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var caption by remember(initialCaption) { mutableStateOf(initialCaption) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialCaption.isBlank()) "Add a photo caption" else "Edit photo caption") },
        text = {
            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 240) caption = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What made this moment worth keeping?") },
                supportingText = { Text("${caption.length}/240") },
                minLines = 2,
                maxLines = 4,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(caption.trim()) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (initialCaption.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun rememberFileBitmap(path: String?, maxDimension: Int = 1600) =
    produceState<Bitmap?>(initialValue = null, key1 = path, key2 = maxDimension) {
        value = withContext(Dispatchers.IO) {
            path?.let { decodeSampledBitmap(File(it), maxDimension) }
        }
    }

internal fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) sampleSize *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}
