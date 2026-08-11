package com.egoisticfoil.mneme.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egoisticfoil.mneme.data.DatedAttachment
import com.egoisticfoil.mneme.ui.photo.FullScreenPhotoViewer
import com.egoisticfoil.mneme.ui.photo.rememberFileBitmap
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MediaScreen(
    media: List<DatedAttachment>,
    todayJumpKey: Int,
    onMakePrimary: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val orderedMedia = remember(media) { media.sortedBy { it.date } }
    val gridState = rememberLazyGridState()
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }

    LaunchedEffect(todayJumpKey) {
        if (todayJumpKey > 0 && orderedMedia.isNotEmpty()) {
            val today = LocalDate.now()
            val target = orderedMedia.indexOfFirst { it.date == today }
                .takeIf { it >= 0 }
                ?: orderedMedia.lastIndex
            gridState.animateScrollToItem(target)
        }
    }

    if (media.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Your photo story starts here",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Photos added to journal entries will collect here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp, 2.dp, 2.dp, 28.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(orderedMedia, key = { it.attachment.id }) { item ->
                val bitmap by rememberFileBitmap(
                    item.attachment.thumbnailPath ?: item.attachment.originalPath,
                    1100,
                )
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { selectedId = item.attachment.id },
                ) {
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = item.attachment.originalFileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.52f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.62f),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = item.date.dayOfMonth.toString(),
                            color = Color.White,
                            fontSize = 38.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Light,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 5f),
                            ),
                        )
                        Text(
                            text = item.date.format(monthFormatter),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelSmall.copy(
                                shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 3f),
                            ),
                        )
                    }
                }
            }
        }
    }

    selectedId?.let { id ->
        FullScreenPhotoViewer(
            attachments = orderedMedia.map { it.attachment },
            initialAttachmentId = id,
            onDismiss = { selectedId = null },
            onMakePrimary = onMakePrimary,
            onDelete = onDelete,
        )
    }
}
