package com.lainsmain.mneme.ui.diary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lainsmain.mneme.data.DiaryAttachment
import com.lainsmain.mneme.data.PlaceSuggestion
import com.lainsmain.mneme.model.RichTextDocument
import com.lainsmain.mneme.ui.editor.RichTextEditor
import com.lainsmain.mneme.ui.editor.RichTextFormattingBar
import com.lainsmain.mneme.ui.editor.rememberRichTextEditorState
import com.lainsmain.mneme.ui.FavoriteGold
import com.lainsmain.mneme.ui.map.LocationPickerDialog
import com.lainsmain.mneme.ui.photo.FullScreenPhotoViewer
import com.lainsmain.mneme.ui.photo.PhotoMosaic
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DiaryScreen(
    state: DiaryUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDocumentChange: (RichTextDocument) -> Unit,
    onChoosePhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onMakePhotoPrimary: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onSetPhotoCaption: (String, String) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetLocation: (String, Double?, Double?) -> Unit,
    onSetLocationFromMap: (Double, Double) -> Unit,
    onUsePhotoLocation: () -> Unit,
    onSearchPlaces: (String) -> Unit,
    onClearPlaceSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPhoto by remember(state.selectedDate) {
        mutableStateOf<DiaryAttachment?>(null)
    }
    var showLocationDialog by remember(state.selectedDate) { mutableStateOf(false) }
    var showMapPicker by remember(state.selectedDate) { mutableStateOf(false) }
    val editorState = rememberRichTextEditorState(state.selectedDate.toString(), state.document)

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            EntryDateHeader(
                date = state.selectedDate,
                isSaving = state.isSaving,
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay,
                onChooseFromGallery = onChoosePhotos,
                onTakePhoto = onTakePhoto,
                onEditLocation = { showLocationDialog = true },
                isFavorite = state.isFavorite,
                onToggleFavorite = onToggleFavorite,
            )

            state.yesterdaySuggestion
                ?.takeIf { state.selectedDate != it }
                ?.let { yesterday ->
                    AssistChip(
                        modifier = Modifier.padding(top = 10.dp),
                        onClick = { onSelectDate(yesterday) },
                        label = { Text("Still writing about yesterday?") },
                    )
                }

            EntryLocationPill(
                location = state.location,
                onClick = { showLocationDialog = true },
                modifier = Modifier.padding(top = 16.dp),
            )

            RichTextEditor(
                document = state.document,
                documentKey = state.selectedDate.toString(),
                state = editorState,
                onDocumentChange = onDocumentChange,
                modifier = Modifier.padding(top = 20.dp),
            )
            if (state.isImportingPhotos) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )
            }
            if (state.attachments.isNotEmpty()) {
                PhotoMosaic(
                    attachments = state.attachments,
                    onOpen = { selectedPhoto = it },
                    onMakePrimary = onMakePhotoPrimary,
                    onDelete = onDeletePhoto,
                    onSetCaption = onSetPhotoCaption,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            if (state.photoImportFailures > 0) {
                Text(
                    text = "${state.photoImportFailures} photo${if (state.photoImportFailures == 1) "" else "s"} could not be added.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(if (editorState.isFocused) 18.dp else 48.dp))
        }

        AnimatedVisibility(
            visible = editorState.isFocused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                RichTextFormattingBar(
                    document = state.document,
                    state = editorState,
                    onDocumentChange = onDocumentChange,
                )
            }
        }
    }

    selectedPhoto?.let { attachment ->
        FullScreenPhotoViewer(
            attachments = state.attachments,
            initialAttachmentId = attachment.id,
            onDismiss = { selectedPhoto = null },
            onMakePrimary = onMakePhotoPrimary,
            onDelete = onDeletePhoto,
            onSetCaption = onSetPhotoCaption,
        )
    }
    if (showLocationDialog) {
        LocationDialog(
            location = state.location,
            suggestions = state.placeSuggestions,
            isSearching = state.isSearchingPlaces,
            searchMessage = state.placeSearchMessage,
            hasPrimaryPhotoLocation = state.attachments.firstOrNull()?.let {
                it.latitude != null && it.longitude != null
            } == true,
            onDismiss = {
                onClearPlaceSearch()
                showLocationDialog = false
            },
            onSearchPlaces = onSearchPlaces,
            onSelectSuggestion = { suggestion ->
                onSetLocation(suggestion.name, suggestion.latitude, suggestion.longitude)
                onClearPlaceSearch()
                showLocationDialog = false
            },
            onSave = { name, latitude, longitude ->
                onSetLocation(name, latitude, longitude)
                showLocationDialog = false
            },
            onUsePhotoLocation = {
                onUsePhotoLocation()
                showLocationDialog = false
            },
            onChooseOnMap = {
                onClearPlaceSearch()
                showLocationDialog = false
                showMapPicker = true
            },
        )
    }
    if (showMapPicker) {
        LocationPickerDialog(
            initialLatitude = state.location?.latitude,
            initialLongitude = state.location?.longitude,
            onDismiss = {
                showMapPicker = false
                showLocationDialog = true
            },
            onConfirm = { latitude, longitude ->
                onSetLocationFromMap(latitude, longitude)
                showMapPicker = false
            },
        )
    }
}

@Composable
private fun EntryDateHeader(
    date: LocalDate,
    isSaving: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onEditLocation: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.background,
        border = if (isFavorite) BorderStroke(1.dp, FavoriteGold.copy(alpha = 0.78f)) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onPreviousDay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous day")
                }
            }
            AnimatedContent(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                targetState = date,
                label = "entry date",
            ) { targetDate ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = targetDate.format(DateTimeFormatter.ofPattern("EEEE", locale)).uppercase(locale),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isFavorite) FavoriteGold else MaterialTheme.colorScheme.primary,
                        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.15f,
                        maxLines = 1,
                    )
                    Text(
                        text = targetDate.format(DateTimeFormatter.ofPattern("d MMMM", locale)),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = "${targetDate.year}  ·  ${if (isSaving) "Saving…" else "Saved"}" +
                            if (isFavorite) "  ·  Favorite" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isFavorite) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                onClick = onNextDay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next day")
                }
            }
            EntryOverflowMenu(
                onChooseFromGallery = onChooseFromGallery,
                onTakePhoto = onTakePhoto,
                onEditLocation = onEditLocation,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun EntryLocationPill(
    location: DiaryLocation?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (location == null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = location?.name ?: "Add a place",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EntryOverflowMenu(
    onChooseFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onEditLocation: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Entry actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = if (isFavorite) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {
                    expanded = false
                    onToggleFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text("Choose from gallery") },
                leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                onClick = {
                    expanded = false
                    onChooseFromGallery()
                },
            )
            DropdownMenuItem(
                text = { Text("Take a photo") },
                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                onClick = {
                    expanded = false
                    onTakePhoto()
                },
            )
            DropdownMenuItem(
                text = { Text("Set entry location") },
                leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEditLocation()
                },
            )
        }
    }
}

@Composable
private fun LocationDialog(
    location: DiaryLocation?,
    suggestions: List<PlaceSuggestion>,
    isSearching: Boolean,
    searchMessage: String?,
    hasPrimaryPhotoLocation: Boolean,
    onDismiss: () -> Unit,
    onSearchPlaces: (String) -> Unit,
    onSelectSuggestion: (PlaceSuggestion) -> Unit,
    onSave: (String, Double?, Double?) -> Unit,
    onUsePhotoLocation: () -> Unit,
    onChooseOnMap: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var name by remember(location) { mutableStateOf(location?.name.orEmpty()) }
    var latitude by remember(location) { mutableStateOf(location?.latitude?.toString().orEmpty()) }
    var longitude by remember(location) { mutableStateOf(location?.longitude?.toString().orEmpty()) }
    val parsedLatitude = latitude.toDoubleOrNull()
    val parsedLongitude = longitude.toDoubleOrNull()
    val coordinatesValid = (latitude.isBlank() && longitude.isBlank()) ||
        (parsedLatitude != null && parsedLatitude in -90.0..90.0 &&
            parsedLongitude != null && parsedLongitude in -180.0..180.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entry location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearchPlaces(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search places") },
                    placeholder = { Text("Antwerp Central, a café, an address…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    },
                    singleLine = true,
                )
                suggestions.forEach { suggestion ->
                    PlaceSuggestionRow(suggestion = suggestion, onClick = { onSelectSuggestion(suggestion) })
                }
                searchMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("Location search") || it.startsWith("Could") || it.startsWith("The device")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                OutlinedButton(onClick = onChooseOnMap, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Map, contentDescription = null, Modifier.size(18.dp))
                    Text("Choose on map", modifier = Modifier.padding(start = 8.dp))
                }
                HorizontalDivider()
                Text(
                    "Or enter it manually",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Place name") },
                    placeholder = { Text("Beach, Antwerp, home…") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!coordinatesValid) Text("Enter valid coordinates.", color = MaterialTheme.colorScheme.error)
                if (hasPrimaryPhotoLocation && location?.isManual == true) {
                    OutlinedButton(onClick = onUsePhotoLocation, modifier = Modifier.fillMaxWidth()) {
                        Text("Use primary photo location")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, latitude.toDoubleOrNull(), longitude.toDoubleOrNull()) },
                enabled = coordinatesValid && (name.isNotBlank() || latitude.isNotBlank()),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlaceSuggestionRow(suggestion: PlaceSuggestion, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(suggestion.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (suggestion.address.isNotBlank()) {
                    Text(
                        suggestion.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
