package com.lainsmain.mneme.ui.photo

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import com.lainsmain.mneme.data.DiaryAttachment
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FullScreenPhotoViewer(
    attachments: List<DiaryAttachment>,
    initialAttachmentId: String,
    onDismiss: () -> Unit,
    onMakePrimary: ((String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onSetCaption: ((String, String) -> Unit)? = null,
) {
    if (attachments.isEmpty()) return
    var currentIndex by remember(initialAttachmentId, attachments.map { it.id }) {
        mutableIntStateOf(attachments.indexOfFirst { it.id == initialAttachmentId }.coerceAtLeast(0))
    }
    val attachment = attachments[currentIndex.coerceIn(attachments.indices)]
    var showInfo by remember(attachment.id) { mutableStateOf(false) }
    var showActions by remember(attachment.id) { mutableStateOf(false) }
    var confirmDelete by remember(attachment.id) { mutableStateOf(false) }
    var editCaption by remember(attachment.id) { mutableStateOf(false) }
    var scale by remember(attachment.id) { mutableFloatStateOf(1f) }
    var offset by remember(attachment.id) { mutableStateOf(Offset.Zero) }
    val bitmap by produceState<Bitmap?>(null, attachment.originalPath) {
        value = withContext(Dispatchers.IO) { loadOrientedBitmap(attachment.originalPath) }
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + panChange
    }

    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            bitmap?.let { loaded ->
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = attachment.originalFileName ?: "Journal photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .transformable(transformState),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close photo", tint = Color.White)
                }
                Row {
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Icon(Icons.Rounded.Info, contentDescription = "Photo information", tint = Color.White)
                    }
                    if (onMakePrimary != null || onDelete != null || onSetCaption != null) {
                        Box {
                            IconButton(onClick = { showActions = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Photo actions", tint = Color.White)
                            }
                            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                                if (onSetCaption != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (attachment.caption.isBlank()) "Add caption" else "Edit caption") },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                        onClick = {
                                            showActions = false
                                            editCaption = true
                                        },
                                    )
                                }
                                if (onMakePrimary != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (currentIndex == 0) "Primary photo" else "Set as primary") },
                                        leadingIcon = { Icon(Icons.Rounded.Star, null) },
                                        enabled = currentIndex != 0,
                                        onClick = {
                                            showActions = false
                                            onMakePrimary(attachment.id)
                                        },
                                    )
                                }
                                if (onDelete != null) {
                                    DropdownMenuItem(
                                        text = { Text("Delete photo") },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                        onClick = {
                                            showActions = false
                                            confirmDelete = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showInfo,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                PhotoInformation(
                    attachment = attachment,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(12.dp),
                )
            }

            AnimatedVisibility(
                visible = !showInfo && attachment.caption.isNotBlank(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    modifier = Modifier.navigationBarsPadding().padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.66f),
                ) {
                    Text(
                        attachment.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            if (attachments.size > 1) {
                IconButton(
                    onClick = {
                        currentIndex = (currentIndex - 1 + attachments.size) % attachments.size
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous photo", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        currentIndex = (currentIndex + 1) % attachments.size
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next photo", tint = Color.White)
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this photo?") },
            text = { Text("It will be removed from this entry and Mneme's local storage.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(attachment.id)
                    if (attachments.size == 1) onDismiss()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (editCaption && onSetCaption != null) {
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
private fun PhotoInformation(attachment: DiaryAttachment, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xE61B1D21),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = attachment.originalFileName ?: "Photo details",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            attachment.caption.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
            attachment.capturedAtEpochMillis?.let { timestamp ->
                Text(
                    text = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())),
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            if (attachment.latitude != null && attachment.longitude != null) {
                Text(
                    text = "${"%.5f".format(attachment.latitude)}, ${"%.5f".format(attachment.longitude)}",
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            listOfNotNull(attachment.cameraMake, attachment.cameraModel, attachment.lensModel)
                .joinToString(" · ")
                .takeIf { it.isNotBlank() }
                ?.let { Text(it, color = Color.White.copy(alpha = 0.82f)) }
            val exposure = listOfNotNull(
                attachment.focalLength?.let { "$it mm" },
                attachment.aperture?.let { "f/$it" },
                attachment.exposureTime?.let { "${it}s" },
                attachment.iso?.let { "ISO $it" },
            ).joinToString(" · ")
            if (exposure.isNotBlank()) Text(exposure, color = Color.White.copy(alpha = 0.82f))
            Text(
                text = listOfNotNull(
                    attachment.width?.let { width -> attachment.height?.let { "$width × $it" } },
                    formatBytes(attachment.byteSize),
                ).joinToString(" · "),
                color = Color.White.copy(alpha = 0.82f),
            )
        }
    }
}

private fun loadOrientedBitmap(path: String): Bitmap? {
    val file = File(path)
    val bitmap = decodeSampledBitmap(file, 2800) ?: return null
    val rotation = runCatching { ExifInterface(file).rotationDegrees }.getOrDefault(0)
    if (rotation == 0) return bitmap
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(rotation.toFloat()) },
        true,
    ).also { if (it !== bitmap) bitmap.recycle() }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
