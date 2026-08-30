package com.lainsmain.mneme.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lainsmain.mneme.ui.calendar.MonthScreen
import com.lainsmain.mneme.ui.diary.DiaryScreen
import com.lainsmain.mneme.ui.diary.DiaryUiState
import com.lainsmain.mneme.ui.list.EntryListScreen
import com.lainsmain.mneme.ui.map.MapScreen
import com.lainsmain.mneme.ui.media.MediaScreen
import com.lainsmain.mneme.ui.search.SearchScreen
import com.lainsmain.mneme.ui.recap.MonthlyRecapScreen
import com.lainsmain.mneme.data.ThemePreference
import com.lainsmain.mneme.data.ReleaseInfo
import com.lainsmain.mneme.data.ColorPalette
import com.lainsmain.mneme.data.RemoteManifestPointer
import com.lainsmain.mneme.data.JournalDefinition
import com.lainsmain.mneme.data.JournalId
import com.lainsmain.mneme.data.JournalRole
import com.lainsmain.mneme.data.JournalContentCounts
import com.lainsmain.mneme.data.JournalIcon
import com.lainsmain.mneme.ui.settings.SettingsScreen
import com.lainsmain.mneme.ui.settings.SettingsUiState
import java.time.LocalDate
import java.time.YearMonth

private enum class JournalDestination(val label: String) {
    Journal("Journal"),
    List("List"),
    Calendar("Month"),
    Media("Media"),
    Map("Map"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemeApp(
    state: DiaryUiState,
    journalOpenKey: Int,
    journals: List<JournalDefinition>,
    initialDestination: String,
    onJournalChange: (JournalId) -> Unit,
    onDestinationChanged: (JournalId, String) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDocumentChange: (com.lainsmain.mneme.model.RichTextDocument) -> Unit,
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
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onVisibleMonthChange: (YearMonth) -> Unit,
    onOpenRecap: (YearMonth) -> Unit,
    onCloseRecap: () -> Unit,
    onPreviousRecapMonth: () -> Unit,
    onNextRecapMonth: () -> Unit,
    onRecapDocumentChange: (com.lainsmain.mneme.model.RichTextDocument) -> Unit,
    settingsState: SettingsUiState,
    onThemeChange: (ThemePreference) -> Unit,
    onMaterialYouChange: (Boolean) -> Unit,
    onColorPaletteChange: (ColorPalette) -> Unit,
    onYesterdayPromptCutoffHourChange: (Int) -> Unit,
    onDreamDiaryEnabledChange: (Boolean) -> Unit,
    onCreateJournal: (String, JournalIcon, ColorPalette, (JournalDefinition) -> Unit) -> Unit,
    onUpdateJournal: (String, JournalIcon, ColorPalette, JournalId, () -> Unit) -> Unit,
    onReorderJournals: (List<JournalId>) -> Unit,
    onSetJournalArchived: (JournalId, Boolean) -> Unit,
    onLoadJournalCounts: (JournalId, (JournalContentCounts) -> Unit) -> Unit,
    onDeleteJournal: (JournalId, String) -> Unit,
    onSavePin: (String) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onConnectServer: (String, String) -> Unit,
    onDisconnectServer: () -> Unit,
    onBackupNow: () -> Unit,
    onAcknowledgeRecoveryCode: () -> Unit,
    onRestoreBackup: (String, RemoteManifestPointer?) -> Unit,
    onExportDiary: () -> Unit,
    onRefreshServerOverview: () -> Unit,
    onCollectServerGarbage: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (ReleaseInfo) -> Unit,
    onInstallDownloadedUpdate: () -> Unit,
) {
    val destinations = remember { mutableStateMapOf<String, JournalDestination>() }
    val journalKey = state.journal.id.storageValue
    val destination = destinations.getOrPut(journalKey) { destinationFromStorage(initialDestination) }
    val currentJournalId by rememberUpdatedState(state.journal.id)
    val currentDestinationChanged by rememberUpdatedState(onDestinationChanged)
    val selectDestination: (JournalDestination) -> Unit = remember {
        { target ->
            val journalId = currentJournalId
            destinations[journalId.storageValue] = target
            currentDestinationChanged(journalId, target.name)
        }
    }
    val mediaTodayJumpKeys = remember { mutableStateMapOf<String, Int>() }
    val mediaTodayJumpKey = mediaTodayJumpKeys[journalKey] ?: 0
    var showSettings by remember { mutableStateOf(false) }
    var journalSettingsStart by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showJournalSwitcher by remember { mutableStateOf(false) }
    var dismissedUpdateTag by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissedRecoveryReminder by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(journalOpenKey) {
        if (journalOpenKey > 0) {
            onJournalChange(JournalId.PRIMARY)
            destinations[JournalId.PRIMARY.storageValue] = JournalDestination.Journal
            onDestinationChanged(JournalId.PRIMARY, JournalDestination.Journal.name)
            showSettings = false
            showSearch = false
        }
    }
    BackHandler(enabled = showSettings || showSearch || state.recapMonth != null) {
        when {
            showSettings && journalSettingsStart != null -> journalSettingsStart = null
            showSettings -> showSettings = false
            showSearch -> showSearch = false
            state.recapMonth != null -> onCloseRecap()
        }
    }

    Scaffold(
        topBar = {
            Column(
                Modifier.background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (!showSettings && !showSearch && state.recapMonth == null) {
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = RoundedCornerShape(11.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        JournalIconView(state.journal, Modifier.size(21.dp))
                                    }
                                }
                            }
                            if (
                                journals.size > 1 && !showSettings && !showSearch &&
                                state.recapMonth == null
                            ) {
                                JournalTitleSelector(state.journal) { showJournalSwitcher = true }
                            } else {
                                Text(
                                    text = when {
                                        showSettings -> when (journalSettingsStart) {
                                            "create" -> "New journal"
                                            "edit" -> "Edit journal"
                                            "manage" -> "Manage journals"
                                            else -> "Settings"
                                        }
                                        showSearch -> "Search"
                                        state.recapMonth != null -> "Monthly recap"
                                        else -> "Mneme"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (showSettings || showSearch || state.recapMonth != null) {
                            IconButton(onClick = {
                                when {
                                    showSettings && journalSettingsStart != null -> journalSettingsStart = null
                                    showSettings -> showSettings = false
                                    showSearch -> showSearch = false
                                    else -> onCloseRecap()
                                }
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    actions = {
                        if (!showSettings && !showSearch && state.recapMonth == null) {
                            TextButton(
                                onClick = {
                                    when (destination) {
                                        JournalDestination.Calendar -> onCurrentMonth()
                                        JournalDestination.Media -> mediaTodayJumpKeys[journalKey] = mediaTodayJumpKey + 1
                                        else -> onToday()
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.Today, contentDescription = null)
                                Text("Today", modifier = Modifier.padding(start = 6.dp))
                            }
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = if (state.journal.role == JournalRole.Dream) {
                                        "Search dreams"
                                    } else {
                                        "Search journal"
                                    },
                                )
                            }
                            IconButton(onClick = {
                                journalSettingsStart = null
                                showSettings = true
                            }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
                if (!showSettings && !showSearch && state.recapMonth == null) {
                    JournalNavigation(
                        selected = destination,
                        journal = state.journal,
                        onSelected = selectDestination,
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        if (state.recapMonth != null && !showSettings && !showSearch) {
            MonthlyRecapScreen(
                month = state.recapMonth,
                document = state.recapDocument,
                isSaving = state.recapIsSaving,
                onPreviousMonth = onPreviousRecapMonth,
                onNextMonth = onNextRecapMonth,
                onDocumentChange = onRecapDocumentChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding),
            )
        } else AnimatedContent(
            targetState = journalKey to Triple(showSettings, showSearch, destination),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding),
            label = "journal destination",
        ) { (_, screen) ->
            val (settingsVisible, searchVisible, target) = screen
            if (settingsVisible) {
                SettingsScreen(
                    state = settingsState,
                    journalStartPage = journalSettingsStart,
                    onJournalPageChange = { journalSettingsStart = it },
                    onThemeChange = onThemeChange,
                    onMaterialYouChange = onMaterialYouChange,
                    onColorPaletteChange = onColorPaletteChange,
                    onYesterdayPromptCutoffHourChange = onYesterdayPromptCutoffHourChange,
                    onDreamDiaryEnabledChange = onDreamDiaryEnabledChange,
                    onCreateJournal = onCreateJournal,
                    onUpdateJournal = onUpdateJournal,
                    onReorderJournals = onReorderJournals,
                    onSetJournalArchived = onSetJournalArchived,
                    onLoadJournalCounts = onLoadJournalCounts,
                    onDeleteJournal = onDeleteJournal,
                    onOpenJournal = { journalId ->
                        showSettings = false
                        onJournalChange(journalId)
                    },
                    onSavePin = onSavePin,
                    onAppLockChange = onAppLockChange,
                    onBiometricChange = onBiometricChange,
                    onConnect = onConnectServer,
                    onDisconnect = onDisconnectServer,
                    onBackupNow = onBackupNow,
                    onAcknowledgeRecoveryCode = onAcknowledgeRecoveryCode,
                    onRestoreBackup = onRestoreBackup,
                    onExportDiary = onExportDiary,
                    onRefreshServerOverview = onRefreshServerOverview,
                    onCollectServerGarbage = onCollectServerGarbage,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallDownloadedUpdate = onInstallDownloadedUpdate,
                )
            } else if (searchVisible) {
                SearchScreen(
                    entries = state.allDays,
                    onOpenDay = { date ->
                        onSelectDate(date)
                        selectDestination(JournalDestination.Journal)
                        showSearch = false
                    },
                )
            } else when (target) {
                JournalDestination.Journal -> DiaryScreen(
                    state = state,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onSelectDate = onSelectDate,
                    onDocumentChange = onDocumentChange,
                    onChoosePhotos = onChoosePhotos,
                    onTakePhoto = onTakePhoto,
                    onMakePhotoPrimary = onMakePhotoPrimary,
                    onDeletePhoto = onDeletePhoto,
                    onSetPhotoCaption = onSetPhotoCaption,
                    onToggleFavorite = onToggleFavorite,
                    onSetLocation = onSetLocation,
                    onSetLocationFromMap = onSetLocationFromMap,
                    onUsePhotoLocation = onUsePhotoLocation,
                    onSearchPlaces = onSearchPlaces,
                    onClearPlaceSearch = onClearPlaceSearch,
                )

                JournalDestination.List -> EntryListScreen(
                    entries = state.allDays,
                    journal = state.journal,
                    onOpenDay = { date ->
                        onSelectDate(date)
                        selectDestination(JournalDestination.Journal)
                    },
                )

                JournalDestination.Calendar -> MonthScreen(
                    focusedMonth = state.visibleMonth,
                    summaries = state.allDays.associateBy { it.date },
                    selectedDate = state.selectedDate,
                    jumpKey = state.calendarJumpKey,
                    onOpenDay = { date ->
                        onSelectDate(date)
                        selectDestination(JournalDestination.Journal)
                    },
                    recapMonths = state.recapMonths,
                    onOpenRecap = onOpenRecap,
                    showMonthlyRecaps = state.journal.supportsMonthlyRecaps,
                    onVisibleMonthChange = onVisibleMonthChange,
                )

                JournalDestination.Media -> MediaScreen(
                    media = state.allMedia,
                    journal = state.journal,
                    todayJumpKey = mediaTodayJumpKey,
                    onMakePrimary = onMakePhotoPrimary,
                    onDelete = onDeletePhoto,
                    onSetCaption = onSetPhotoCaption,
                )

                JournalDestination.Map -> MapScreen(
                    entries = state.allDays,
                    journal = state.journal,
                    onOpenDay = { date ->
                        onSelectDate(date)
                        selectDestination(JournalDestination.Journal)
                    },
                )
            }
        }
    }

    if (showJournalSwitcher) {
        ModalBottomSheet(onDismissRequest = { showJournalSwitcher = false }) {
            Text(
                "Switch journal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            journals.forEach { journal ->
                ListItem(
                    headlineContent = { Text(journal.title) },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = journal.switcherColor().copy(alpha = 0.24f),
                            contentColor = journal.switcherColor(),
                        ) { JournalIconView(journal, Modifier.padding(10.dp).size(22.dp)) }
                    },
                    trailingContent = {
                        if (journal.id == state.journal.id) {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected")
                        }
                    },
                    modifier = Modifier.clickable {
                        showJournalSwitcher = false
                        onJournalChange(journal.id)
                    },
                )
            }
            ListItem(
                headlineContent = { Text("New journal") },
                leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                modifier = Modifier.clickable {
                    showJournalSwitcher = false
                    journalSettingsStart = "create"
                    showSettings = true
                },
            )
            ListItem(
                headlineContent = { Text("Manage journals") },
                leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                modifier = Modifier.clickable {
                    showJournalSwitcher = false
                    journalSettingsStart = "manage"
                    showSettings = true
                },
            )
            Box(Modifier.height(24.dp))
        }
    }

    val showRecoveryReminder = settingsState.settings.serverConnected &&
        settingsState.recoveryCodeNeedsSaving && !dismissedRecoveryReminder
    if (showRecoveryReminder) {
        AlertDialog(
            onDismissRequest = { dismissedRecoveryReminder = true },
            title = { Text("Save your recovery code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "If this phone dies, this code and a server access token are what restore your encrypted diary. " +
                            "Mneme cannot recover the code for you.",
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        SelectionContainer {
                            Text(
                                settingsState.recoveryCode,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(settingsState.recoveryCode)) },
                    ) { Text("Copy recovery code") }
                }
            },
            confirmButton = {
                Button(onClick = onAcknowledgeRecoveryCode) { Text("I saved it") }
            },
            dismissButton = {
                TextButton(onClick = { dismissedRecoveryReminder = true }) { Text("Later") }
            },
        )
    } else {
        settingsState.availableUpdate
            ?.takeIf { it.tag != dismissedUpdateTag }
            ?.let { release ->
            AlertDialog(
                onDismissRequest = { dismissedUpdateTag = release.tag },
                title = { Text("A new Mneme is ready") },
                text = {
                    Text(
                        "Version ${release.version} is available. Download the signed APK from the public GitHub release?",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            dismissedUpdateTag = release.tag
                            onDownloadUpdate(release)
                        },
                    ) { Text("Download in Mneme") }
                },
                dismissButton = {
                    TextButton(onClick = { dismissedUpdateTag = release.tag }) { Text("Later") }
                },
            )
        }
    }
}

private fun destinationFromStorage(value: String): JournalDestination =
    JournalDestination.entries.firstOrNull { it.name == value } ?: JournalDestination.Journal

private fun JournalDefinition.switcherColor(): Color = when {
    role == JournalRole.Dream -> Color(0xFF8B7BD0)
    accentPalette == ColorPalette.Ocean -> Color(0xFF39A7C8)
    accentPalette == ColorPalette.Forest -> Color(0xFF65A367)
    accentPalette == ColorPalette.Lavender -> Color(0xFF8B75BD)
    accentPalette == ColorPalette.Rose -> Color(0xFFC66B82)
    accentPalette == ColorPalette.Amber -> Color(0xFFD68A22)
    accentPalette == ColorPalette.Graphite -> Color(0xFF77737A)
    else -> Color(0xFF8A6A55)
}

@Composable
private fun JournalTitleSelector(journal: JournalDefinition, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            journal.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text("  ▾", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun JournalNavigation(
    selected: JournalDestination,
    journal: JournalDefinition,
    onSelected: (JournalDestination) -> Unit,
) {
    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JournalDestination.entries.forEach { destination ->
                val active = selected == destination
                val labelColor by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "navigation color",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(destination) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (
                            destination == JournalDestination.Journal && journal.role == JournalRole.Dream
                        ) "Dream" else destination.label,
                        color = labelColor,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (active) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .width(30.dp)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComingSoonScreen(icon: ImageVector, title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
