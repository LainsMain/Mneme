package com.lainsmain.mneme.ui.list

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lainsmain.mneme.data.DaySummary
import com.lainsmain.mneme.ui.FavoriteGold
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EntryListScreen(
    entries: List<DaySummary>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = entries.groupBy { YearMonth.from(it.date) }
    if (entries.isEmpty()) {
        EmptyList(modifier)
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        grouped.forEach { (month, monthEntries) ->
            item(key = "header-$month") { MonthLabel(month) }
            items(monthEntries, key = { it.date.toEpochDay() }) { entry ->
                EntryRow(entry = entry, onClick = { onOpenDay(entry.date) })
            }
        }
    }
}

@Composable
private fun MonthLabel(month: YearMonth) {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    Surface(color = MaterialTheme.colorScheme.background) {
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun EntryRow(entry: DaySummary, onClick: () -> Unit) {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    val lines = entry.plainText.lines().filter { it.isNotBlank() }
    val title = lines.firstOrNull() ?: if (entry.attachmentCount > 0) "Photo entry" else "Journal entry"
    val excerpt = lines.drop(1).joinToString(" ").ifBlank { entry.plainText }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .then(
                if (entry.isFavorite) {
                    Modifier.border(1.dp, FavoriteGold.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                entry.date.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(entry.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isFavorite) {
                    Icon(Icons.Rounded.Star, null, Modifier.padding(start = 5.dp).size(15.dp), FavoriteGold)
                }
            }
            if (excerpt.isNotBlank() && excerpt != title) {
                Text(
                    excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.locationName?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        entry.thumbnailFileName?.let { path -> EntryThumbnail(path) }
    }
    if (!entry.isFavorite) {
        HorizontalDivider(modifier = Modifier.padding(start = 74.dp, end = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun EntryThumbnail(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { File(path).takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.path) } }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun EmptyList(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your journal will appear here", style = MaterialTheme.typography.titleLarge)
        Text("Entries are listed newest first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
