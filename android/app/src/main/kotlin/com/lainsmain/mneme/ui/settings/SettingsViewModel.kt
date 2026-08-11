package com.lainsmain.mneme.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lainsmain.mneme.data.AppSettings
import com.lainsmain.mneme.data.AppSettingsRepository
import com.lainsmain.mneme.data.ColorPalette
import com.lainsmain.mneme.data.ThemePreference
import com.lainsmain.mneme.data.BackupRepository
import com.lainsmain.mneme.data.ReleaseInfo
import com.lainsmain.mneme.data.SemanticVersion
import com.lainsmain.mneme.data.UpdateRepository
import com.lainsmain.mneme.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ServerConnectionStatus { Idle, Testing, Connected, Error }
enum class BackupStatus { Idle, Running, Complete, Error }
enum class UpdateStatus { Idle, Checking, Current, Available, Error }

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val connectionStatus: ServerConnectionStatus = ServerConnectionStatus.Idle,
    val connectionMessage: String? = null,
    val lockMessage: String? = null,
    val backupStatus: BackupStatus = BackupStatus.Idle,
    val backupMessage: String? = null,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val updateMessage: String? = null,
    val availableUpdate: ReleaseInfo? = null,
    val latestRelease: ReleaseInfo? = null,
    val serverOutdated: Boolean = false,
)

class SettingsViewModel(
    private val repository: AppSettingsRepository,
    private val backupRepository: BackupRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(settings = repository.settings.value))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    settings = settings,
                    connectionStatus = if (settings.serverConnected) {
                        ServerConnectionStatus.Connected
                    } else if (_uiState.value.connectionStatus == ServerConnectionStatus.Connected) {
                        ServerConnectionStatus.Idle
                    } else {
                        _uiState.value.connectionStatus
                    },
                    serverOutdated = _uiState.value.latestRelease?.let {
                        SemanticVersion.isNewer(it.version, settings.serverVersion)
                    } == true,
                )
            }
        }
        checkForUpdates(force = false)
        if (repository.settings.value.serverConnected) refreshServerVersion()
    }

    fun setTheme(theme: ThemePreference) = repository.setTheme(theme)

    fun setMaterialYou(enabled: Boolean) = repository.setMaterialYou(enabled)

    fun setColorPalette(palette: ColorPalette) = repository.setColorPalette(palette)

    fun savePin(pin: String) {
        runCatching { repository.setPin(pin) }.fold(
            onSuccess = { _uiState.value = _uiState.value.copy(lockMessage = "App lock enabled.") },
            onFailure = { _uiState.value = _uiState.value.copy(lockMessage = it.message) },
        )
    }

    fun setAppLock(enabled: Boolean) {
        runCatching { repository.setAppLock(enabled) }.onFailure {
            _uiState.value = _uiState.value.copy(lockMessage = it.message)
        }
    }

    fun setBiometric(enabled: Boolean) {
        runCatching { repository.setBiometric(enabled) }.onFailure {
            _uiState.value = _uiState.value.copy(lockMessage = it.message)
        }
    }

    fun verifyPin(pin: String): Boolean = repository.verifyPin(pin)

    fun connect(serverUrl: String, token: String) {
        if (_uiState.value.connectionStatus == ServerConnectionStatus.Testing) return
        _uiState.value = _uiState.value.copy(
            connectionStatus = ServerConnectionStatus.Testing,
            connectionMessage = "Checking your server…",
        )
        viewModelScope.launch {
            try {
                repository.connect(serverUrl, token).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            connectionStatus = ServerConnectionStatus.Connected,
                            connectionMessage = "Connected to your Mneme server.",
                        )
                        checkForUpdates(force = false)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            connectionStatus = ServerConnectionStatus.Error,
                            connectionMessage = error.message ?: "Could not connect to the server.",
                        )
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
    }

    fun checkForUpdates(force: Boolean = true) {
        if (_uiState.value.updateStatus == UpdateStatus.Checking) return
        _uiState.value = _uiState.value.copy(
            updateStatus = UpdateStatus.Checking,
            updateMessage = if (force) "Checking GitHub…" else null,
        )
        viewModelScope.launch {
            updateRepository.latestRelease(force).fold(
                onSuccess = { release ->
                    val appOutdated = release?.let {
                        SemanticVersion.isNewer(it.version, BuildConfig.VERSION_NAME)
                    } == true
                    val serverOutdated = release?.let {
                        SemanticVersion.isNewer(it.version, repository.settings.value.serverVersion)
                    } == true
                    _uiState.value = _uiState.value.copy(
                        updateStatus = if (appOutdated) UpdateStatus.Available else UpdateStatus.Current,
                        updateMessage = when {
                            release == null -> "No published releases yet."
                            appOutdated -> "Mneme ${release.version} is available."
                            else -> "Mneme is up to date."
                        },
                        availableUpdate = release?.takeIf { appOutdated },
                        latestRelease = release,
                        serverOutdated = serverOutdated,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        updateStatus = UpdateStatus.Error,
                        updateMessage = error.message ?: "Could not check GitHub for updates.",
                    )
                },
            )
        }
    }

    private fun refreshServerVersion() {
        viewModelScope.launch {
            repository.refreshServerVersion().onSuccess { version ->
                _uiState.value = _uiState.value.copy(
                    serverOutdated = _uiState.value.latestRelease?.let {
                        SemanticVersion.isNewer(it.version, version)
                    } == true,
                )
            }
        }
    }

    fun disconnect() {
        repository.disconnectServer()
        _uiState.value = _uiState.value.copy(
            connectionStatus = ServerConnectionStatus.Idle,
            connectionMessage = "Server disconnected.",
        )
    }

    fun backupNow() {
        if (_uiState.value.backupStatus == BackupStatus.Running) return
        _uiState.value = _uiState.value.copy(
            backupStatus = BackupStatus.Running,
            backupMessage = "Encrypting your diary…",
        )
        viewModelScope.launch {
            try {
                backupRepository.backupNow().fold(
                    onSuccess = { result ->
                        val size = if (result.uploadedBytes < 1024 * 1024) {
                            "${result.uploadedBytes / 1024} KB"
                        } else {
                            "%.1f MB".format(result.uploadedBytes / 1024.0 / 1024.0)
                        }
                        _uiState.value = _uiState.value.copy(
                            backupStatus = BackupStatus.Complete,
                            backupMessage = "Backed up ${result.pageCount} entries and ${result.photoCount} photos ($size uploaded).",
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            backupStatus = BackupStatus.Error,
                            backupMessage = error.message ?: "Backup failed.",
                        )
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
    }

    class Factory(
        private val repository: AppSettingsRepository,
        private val backupRepository: BackupRepository,
        private val updateRepository: UpdateRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository, backupRepository, updateRepository) as T
    }
}
