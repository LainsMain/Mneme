package com.lainsmain.mneme

import android.content.DialogInterface
import android.Manifest
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lainsmain.mneme.ui.MnemeApp
import com.lainsmain.mneme.ui.diary.DiaryViewModel
import com.lainsmain.mneme.ui.settings.SettingsViewModel
import com.lainsmain.mneme.ui.settings.AppLockScreen
import com.lainsmain.mneme.ui.theme.MnemeTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = application as MnemeApplication
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    application.settingsRepository,
                    application.backupRepository,
                    application.updateRepository,
                    application.exportRepository,
                ),
            )
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            MnemeTheme(
                mode = settingsState.settings.theme,
                palette = settingsState.settings.colorPalette,
                dynamicColor = settingsState.settings.useMaterialYou,
            ) {
                var unlocked by remember {
                    mutableStateOf(!settingsState.settings.appLockEnabled)
                }
                val notificationPermission = rememberLauncherForActivityResult(RequestPermission()) { }
                LaunchedEffect(settingsState.settings.serverConnected) {
                    if (
                        settingsState.settings.serverConnected &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !application.settingsRepository.backupNotificationPermissionAsked()
                    ) {
                        application.settingsRepository.markBackupNotificationPermissionAsked()
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val diaryViewModel: DiaryViewModel = viewModel(
                    factory = DiaryViewModel.Factory(application.diaryRepository, application.placeSearchRepository),
                )
                val state by diaryViewModel.uiState.collectAsStateWithLifecycle()
                var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
                val photoPicker = rememberLauncherForActivityResult(PickMultipleVisualMedia(20)) { uris ->
                    unlocked = true
                    diaryViewModel.addPhotos(uris)
                }
                val camera = rememberLauncherForActivityResult(TakePicture()) { saved ->
                    val capturedUri = pendingCameraUri?.let(Uri::parse)
                    pendingCameraUri = null
                    unlocked = true
                    if (saved && capturedUri != null) {
                        diaryViewModel.addPhotos(listOf(capturedUri))
                    }
                }
                val diaryExport = rememberLauncherForActivityResult(CreateDocument("application/zip")) { uri ->
                    uri?.let(settingsViewModel::exportDiary)
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                val appLockEnabled by rememberUpdatedState(settingsState.settings.appLockEnabled)
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            settingsViewModel.onAppForegrounded()
                        }
                        if (
                            event == Lifecycle.Event.ON_STOP &&
                            appLockEnabled &&
                            !this@MainActivity.isChangingConfigurations
                        ) {
                            unlocked = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(settingsState.settings.appLockEnabled) {
                    if (!settingsState.settings.appLockEnabled) unlocked = true
                }
                if (settingsState.settings.appLockEnabled && !unlocked) {
                    AppLockScreen(
                        biometricEnabled = settingsState.settings.biometricEnabled,
                        onCheckPin = settingsViewModel::verifyPin,
                        onBiometricRequest = {
                            showBiometricPrompt(onSuccess = { unlocked = true })
                        },
                        onUnlocked = { unlocked = true },
                    )
                    return@MnemeTheme
                }
                MnemeApp(
                    state = state,
                    onPreviousDay = diaryViewModel::previousDay,
                    onNextDay = diaryViewModel::nextDay,
                    onToday = diaryViewModel::today,
                    onSelectDate = diaryViewModel::selectDate,
                    onDocumentChange = diaryViewModel::updateDocument,
                    onChoosePhotos = {
                        photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    },
                    onTakePhoto = {
                        val uri = runCatching { createCameraUri() }.getOrNull()
                        if (uri != null) {
                            pendingCameraUri = uri.toString()
                            runCatching { camera.launch(uri) }
                                .onFailure { pendingCameraUri = null }
                        }
                    },
                    onMakePhotoPrimary = diaryViewModel::makePhotoPrimary,
                    onDeletePhoto = diaryViewModel::deletePhoto,
                    onSetLocation = diaryViewModel::setLocation,
                    onSetLocationFromMap = diaryViewModel::setLocationFromMap,
                    onUsePhotoLocation = diaryViewModel::usePrimaryPhotoLocation,
                    onSearchPlaces = diaryViewModel::searchPlaces,
                    onClearPlaceSearch = diaryViewModel::clearPlaceSearch,
                    onPreviousMonth = diaryViewModel::previousMonth,
                    onNextMonth = diaryViewModel::nextMonth,
                    onCurrentMonth = diaryViewModel::currentMonth,
                    onOpenRecap = diaryViewModel::openRecap,
                    onCloseRecap = diaryViewModel::closeRecap,
                    onPreviousRecapMonth = diaryViewModel::previousRecapMonth,
                    onNextRecapMonth = diaryViewModel::nextRecapMonth,
                    onRecapDocumentChange = diaryViewModel::updateRecapDocument,
                    settingsState = settingsState,
                    onThemeChange = settingsViewModel::setTheme,
                    onMaterialYouChange = settingsViewModel::setMaterialYou,
                    onColorPaletteChange = settingsViewModel::setColorPalette,
                    onSavePin = settingsViewModel::savePin,
                    onAppLockChange = settingsViewModel::setAppLock,
                    onBiometricChange = settingsViewModel::setBiometric,
                    onConnectServer = settingsViewModel::connect,
                    onDisconnectServer = settingsViewModel::disconnect,
                    onBackupNow = settingsViewModel::backupNow,
                    onAcknowledgeRecoveryCode = settingsViewModel::acknowledgeRecoveryCode,
                    onRestoreBackup = settingsViewModel::restoreBackup,
                    onExportDiary = {
                        diaryExport.launch("Mneme-diary-${java.time.LocalDate.now()}.zip")
                    },
                    onRefreshServerOverview = settingsViewModel::refreshServerOverview,
                    onCollectServerGarbage = settingsViewModel::collectServerGarbage,
                    onCheckForUpdates = { settingsViewModel.checkForUpdates() },
                    onDownloadUpdate = settingsViewModel::downloadUpdate,
                    onInstallDownloadedUpdate = settingsViewModel::installDownloadedUpdate,
                )
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Unlock Mneme")
            .setSubtitle("Open your private journal")
            .setNegativeButton("Use PIN", mainExecutor) { _: DialogInterface, _: Int -> }
            .build()
        prompt.authenticate(
            CancellationSignal(),
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }
            },
        )
    }

    private fun createCameraUri(): Uri {
        val cameraDirectory = File(cacheDir, "camera").apply { mkdirs() }
        val photo = File.createTempFile("mneme-", ".jpg", cameraDirectory)
        return FileProvider.getUriForFile(this, "$packageName.files", photo)
    }
}
