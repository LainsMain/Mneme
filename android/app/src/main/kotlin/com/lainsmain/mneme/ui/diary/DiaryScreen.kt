package com.lainsmain.mneme.ui.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.platform.LocalContext
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
import com.lainsmain.mneme.ui.photo.FullScreenPhotoViewer
import com.lainsmain.mneme.ui.photo.PhotoMosaic
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.File

@Composable
fun DiaryScreen(
    state: DiaryUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDocumentChange: (RichTextDocument) -> Unit,
    onAddPhotos: (List<Uri>) -> Unit,
    onMakePhotoPrimary: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onSetLocation: (String, Double?, Double?) -> Unit,
    onUsePhotoLocation: () -> Unit,
    onSearchPlaces: (String) -> Unit,
    onClearPlaceSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPhoto by remember(state.selectedDate) {
        mutableStateOf<DiaryAttachment?>(null)
    }
    var showLocationDialog by remember(state.selectedDate) { mutableStateOf(false) }
    val editorState = rememberRichTextEditorState(state.selectedDate.toString(), state.document)
    val photoPicker = rememberLauncherForActivityResult(PickMultipleVisualMedia(20)) { uris ->
        onAddPhotos(uris)
    }
    val launchPhotoPicker = {
        photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(TakePicture()) { saved ->
        if (saved) pendingCameraUri?.let { onAddPhotos(listOf(it)) }
        pendingCameraUri = null
    }
    val launchCamera: () -> Unit = {
        runCatching { createCameraUri(context) }.getOrNull()?.let { uri ->
            pendingCameraUri = uri
            camera.launch(uri)
        }
        Unit
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        EntryDateHeader(
            date = state.selectedDate,
            isSaving = state.isSaving,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onChooseFromGallery = launchPhotoPicker,
            onTakePhoto = launchCamera,
            onEditLocation = { showLocationDialog = true },
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
        Spacer(Modifier.height(48.dp))
    }

        AnimatedVisibility(
            visible = editorState.isFocused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().padding(bottom = 12.dp),
        ) {
            RichTextFormattingBar(
                document = state.document,
                state = editorState,
                onDocumentChange = onDocumentChange,
            )
        }
    }

    selectedPhoto?.let { attachment ->
        FullScreenPhotoViewer(
            attachments = state.attachments,
            initialAttachmentId = attachment.id,
            onDismiss = { selectedPhoto = null },
            onMakePrimary = onMakePhotoPrimary,
            onDelete = onDeletePhoto,
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
) {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
                    color = MaterialTheme.colorScheme.primary,
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
                    text = "${targetDate.year}  ·  ${if (isSaving) "Saving…" else "Saved"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        )
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
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Entry actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                        color = if (it.startsWith("Connect") || it.startsWith("Could") || it.startsWith("The server")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
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

private fun createCameraUri(context: android.content.Context): Uri {
    val cameraDirectory = File(context.cacheDir, "camera").apply { mkdirs() }
    val photo = File.createTempFile("mneme-", ".jpg", cameraDirectory)
    return FileProvider.getUriForFile(context, "${context.packageName}.files", photo)
}
