package com.egoisticfoil.mneme

import android.content.DialogInterface
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.egoisticfoil.mneme.ui.MnemeApp
import com.egoisticfoil.mneme.ui.diary.DiaryViewModel
import com.egoisticfoil.mneme.ui.settings.SettingsViewModel
import com.egoisticfoil.mneme.ui.settings.AppLockScreen
import com.egoisticfoil.mneme.ui.theme.MnemeTheme

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
                ),
            )
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            MnemeTheme(
                mode = settingsState.settings.theme,
                palette = settingsState.settings.colorPalette,
                dynamicColor = settingsState.settings.useMaterialYou,
            ) {
                var unlocked by rememberSaveable {
                    mutableStateOf(!settingsState.settings.appLockEnabled)
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
                val diaryViewModel: DiaryViewModel = viewModel(
                    factory = DiaryViewModel.Factory(application.diaryRepository, application.placeSearchRepository),
                )
                val state by diaryViewModel.uiState.collectAsStateWithLifecycle()
                MnemeApp(
                    state = state,
                    onPreviousDay = diaryViewModel::previousDay,
                    onNextDay = diaryViewModel::nextDay,
                    onToday = diaryViewModel::today,
                    onSelectDate = diaryViewModel::selectDate,
                    onDocumentChange = diaryViewModel::updateDocument,
                    onAddPhotos = diaryViewModel::addPhotos,
                    onMakePhotoPrimary = diaryViewModel::makePhotoPrimary,
                    onDeletePhoto = diaryViewModel::deletePhoto,
                    onSetLocation = diaryViewModel::setLocation,
                    onUsePhotoLocation = diaryViewModel::usePrimaryPhotoLocation,
                    onSearchPlaces = diaryViewModel::searchPlaces,
                    onClearPlaceSearch = diaryViewModel::clearPlaceSearch,
                    onPreviousMonth = diaryViewModel::previousMonth,
                    onNextMonth = diaryViewModel::nextMonth,
                    onCurrentMonth = diaryViewModel::currentMonth,
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
                    onCheckForUpdates = { settingsViewModel.checkForUpdates() },
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
}
