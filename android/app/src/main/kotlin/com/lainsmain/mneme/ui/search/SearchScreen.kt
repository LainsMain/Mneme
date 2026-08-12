package com.lainsmain.mneme.ui.search

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.lainsmain.mneme.data.DaySummary
import com.lainsmain.mneme.ui.FavoriteGold
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchFilters(
    val hasPhotos: Boolean = false,
    val hasLocation: Boolean = false,
    val favoritesOnly: Boolean = false,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
) {
    val activeCount: Int
        get() = listOf(hasPhotos, hasLocation, favoritesOnly, fromDate != null, toDate != null).count { it }
}

internal fun filterEntries(
    entries: List<DaySummary>,
    query: String,
    filters: SearchFilters,
): List<DaySummary> {
    val term = query.trim()
    if (term.isBlank() && filters.activeCount == 0) return emptyList()
    return entries.filter { entry ->
        (term.isBlank() || entry.plainText.contains(term, ignoreCase = true) ||
            entry.locationName?.contains(term, ignoreCase = true) == true ||
            entry.photoCaptions.contains(term, ignoreCase = true)) &&
            (!filters.hasPhotos || entry.attachmentCount > 0) &&
            (!filters.hasLocation || entry.locationName != null ||
                (entry.latitude != null && entry.longitude != null)) &&
            (!filters.favoritesOnly || entry.isFavorite) &&
            (filters.fromDate == null || !entry.date.isBefore(filters.fromDate)) &&
            (filters.toDate == null || !entry.date.isAfter(filters.toDate))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    entries: List<DaySummary>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(SearchFilters()) }
    var filtersExpanded by remember { mutableStateOf(false) }
    var choosingStart by remember { mutableStateOf<Boolean?>(null) }
    val results = remember(entries, query, filters) { filterEntries(entries, query, filters) }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            placeholder = { Text("Words, places, memories…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    IconButton(onClick = { filtersExpanded = !filtersExpanded }) {
                        Box {
                            Icon(Icons.Rounded.FilterList, contentDescription = "Search filters")
                            if (filters.activeCount > 0) {
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).size(8.dp),
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {}
                            }
                        }
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )

        AnimatedVisibility(filtersExpanded) {
            FilterPanel(
                filters = filters,
                onFiltersChange = { filters = it },
                onChooseStart = { choosingStart = true },
                onChooseEnd = { choosingStart = false },
                onCollapse = { filtersExpanded = false },
            )
        }
        if (!filtersExpanded && filters.activeCount > 0) {
            Surface(
                onClick = { filtersExpanded = true },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.66f),
            ) {
                Text(
                    "${filters.activeCount} active ${if (filters.activeCount == 1) "filter" else "filters"}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        when {
            query.isBlank() && filters.activeCount == 0 -> SearchMessage(
                title = "Find a memory",
                message = "Search your writing and places, or use a filter.",
            )
            results.isEmpty() -> SearchMessage(
                title = "No matching entries",
                message = "Try another word, date, or fewer filters.",
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

    choosingStart?.let { start ->
        val initial = if (start) filters.fromDate else filters.toDate
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { choosingStart = null },
            confirmButton = {
                TextButton(onClick = {
                    val selected = picker.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    filters = if (start) {
                        filters.copy(
                            fromDate = selected,
                            toDate = filters.toDate?.takeIf { selected == null || !it.isBefore(selected) },
                        )
                    } else {
                        filters.copy(
                            toDate = selected,
                            fromDate = filters.fromDate?.takeIf { selected == null || !it.isAfter(selected) },
                        )
                    }
                    choosingStart = null
                }) { Text("Set") }
            },
            dismissButton = {
                Row {
                    if (initial != null) {
                        TextButton(onClick = {
                            filters = if (start) filters.copy(fromDate = null) else filters.copy(toDate = null)
                            choosingStart = null
                        }) { Text("Clear") }
                    }
                    TextButton(onClick = { choosingStart = null }) { Text("Cancel") }
                }
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun FilterPanel(
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
    onChooseStart: () -> Unit,
    onChooseEnd: () -> Unit,
    onCollapse: () -> Unit,
) {
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
    Surface(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.hasPhotos,
                    onClick = { onFiltersChange(filters.copy(hasPhotos = !filters.hasPhotos)) },
                    label = { Text("Photos") },
                    leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(17.dp)) },
                )
                FilterChip(
                    selected = filters.hasLocation,
                    onClick = { onFiltersChange(filters.copy(hasLocation = !filters.hasLocation)) },
                    label = { Text("Places") },
                    leadingIcon = { Icon(Icons.Rounded.LocationOn, null, Modifier.size(17.dp)) },
                )
                FilterChip(
                    selected = filters.favoritesOnly,
                    onClick = { onFiltersChange(filters.copy(favoritesOnly = !filters.favoritesOnly)) },
                    label = { Text("Favorites") },
                    leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(17.dp)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onChooseStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(17.dp))
                    Text(filters.fromDate?.format(dateFormat) ?: "From", Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onChooseEnd, modifier = Modifier.weight(1f)) {
                    Text(filters.toDate?.format(dateFormat) ?: "Until")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (filters.activeCount > 0) {
                    TextButton(onClick = { onFiltersChange(SearchFilters()) }) { Text("Clear all") }
                }
                TextButton(onClick = onCollapse) { Text("Done") }
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
    Surface(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = if (entry.isFavorite) BorderStroke(1.dp, FavoriteGold.copy(alpha = 0.78f)) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpenDay(entry.date) }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (entry.isFavorite) FavoriteGold else MaterialTheme.colorScheme.primary,
                    )
                    if (entry.isFavorite) {
                        Icon(Icons.Rounded.Star, null, Modifier.padding(start = 5.dp).size(14.dp), FavoriteGold)
                    }
                }
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
    }
    if (!entry.isFavorite) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 18.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
    }
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
