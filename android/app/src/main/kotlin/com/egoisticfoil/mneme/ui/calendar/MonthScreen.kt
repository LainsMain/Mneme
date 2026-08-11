package com.egoisticfoil.mneme.ui.calendar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.egoisticfoil.mneme.data.DaySummary
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MONTHS_EACH_SIDE = 120

@Composable
fun MonthScreen(
    focusedMonth: YearMonth,
    summaries: Map<LocalDate, DaySummary>,
    selectedDate: LocalDate,
    jumpKey: Int,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchor = remember { YearMonth.from(selectedDate) }
    val months = remember(anchor) {
        (-MONTHS_EACH_SIDE..MONTHS_EACH_SIDE).map { anchor.plusMonths(it.toLong()) }
    }
    val currentIndex = months.indexOf(focusedMonth).takeIf { it >= 0 } ?: MONTHS_EACH_SIDE
    val initialOffset = with(LocalDensity.current) { 170.dp.roundToPx() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = MONTHS_EACH_SIDE,
        initialFirstVisibleItemScrollOffset = initialOffset,
    )

    LaunchedEffect(jumpKey) {
        listState.animateScrollToItem(currentIndex.coerceAtLeast(0), initialOffset)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        stickyHeader { WeekdayHeader() }
        itemsIndexed(months, key = { _, month -> month.toString() }) { _, month ->
            MonthSection(
                month = month,
                summaries = summaries,
                selectedDate = selectedDate,
                onOpenDay = onOpenDay,
            )
        }
    }
}

@Composable
private fun MonthSection(
    month: YearMonth,
    summaries: Map<LocalDate, DaySummary>,
    selectedDate: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
    ) {
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 10.dp),
        )
        monthCells(month).chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(
                            date = date,
                            summary = summaries[date],
                            selected = date == selectedDate,
                            onClick = { onOpenDay(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            orderedWeekdays().forEach { day ->
                Text(
                    modifier = Modifier.weight(1f),
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, summary?.thumbnailFileName) {
        value = withContext(Dispatchers.IO) {
            summary?.thumbnailFileName?.let { storedName ->
                val file = File(storedName).takeIf { it.isAbsolute } ?: File(context.filesDir, storedName)
                file.takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.path) }
            }
        }
    }
    val writtenOnly = summary?.hasWriting == true && bitmap == null
    Surface(
        modifier = modifier
            .padding(bottom = 2.dp)
            .aspectRatio(1f)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = when {
            writtenOnly -> MaterialTheme.colorScheme.secondaryContainer
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (writtenOnly) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f),
                            ),
                        ),
                    ),
                )
            }
            bitmap?.let { loaded ->
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.34f),
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.24f),
                        ),
                    ),
                )
            }
            Text(
                text = date.dayOfMonth.toString(),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                color = when {
                    bitmap != null -> Color.White
                    writtenOnly -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            if (writtenOnly) {
                WrittenEntryPreview(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .height(17.dp),
                )
            }
        }
    }
}

@Composable
private fun WrittenEntryPreview(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(5.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.86f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ink.copy(alpha = 0.72f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.58f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ink.copy(alpha = 0.42f)),
            )
        }
    }
}

private fun monthCells(month: YearMonth): List<LocalDate?> {
    val leading = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    return buildList {
        repeat(leading) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
    }
}

private fun orderedWeekdays() = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)
