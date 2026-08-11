package com.egoisticfoil.mneme.ui.search

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.egoisticfoil.mneme.data.DaySummary
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    entries: List<DaySummary>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(entries, query) {
        if (query.isBlank()) emptyList()
        else entries.filter {
            it.plainText.contains(query.trim(), ignoreCase = true) ||
                it.locationName?.contains(query.trim(), ignoreCase = true) == true
        }
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            placeholder = { Text("Search your journal") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )

        when {
            query.isBlank() -> SearchMessage(
                title = "Find a memory",
                message = "Search words from your writing or the name of a place.",
            )
            results.isEmpty() -> SearchMessage(
                title = "No matching entries",
                message = "Try a different word or place name.",
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Text(
                        "${results.size} ${if (results.size == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(results, key = { it.date.toEpochDay() }) { entry ->
                    SearchResultRow(entry, onOpenDay)
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SearchResultRow(entry: DaySummary, onOpenDay: (LocalDate) -> Unit) {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    val title = entry.plainText.lineSequence().firstOrNull { it.isNotBlank() } ?: "Photo entry"
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenDay(entry.date) }.padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.plainText.replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        entry.thumbnailFileName?.let { SearchThumbnail(it) }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun SearchThumbnail(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            File(path).takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.path) }
        }
    }
    Box(Modifier.size(66.dp).clip(RoundedCornerShape(12.dp))) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
