package com.egoisticfoil.mneme.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.egoisticfoil.mneme.data.ThemePreference
import com.egoisticfoil.mneme.data.ColorPalette
import com.egoisticfoil.mneme.BuildConfig
import com.egoisticfoil.mneme.data.ReleaseInfo
import com.egoisticfoil.mneme.data.UpdateDownloadPhase
import com.egoisticfoil.mneme.data.RemoteManifestPointer
import java.text.DateFormat
import java.util.Date
import java.time.Instant

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeChange: (ThemePreference) -> Unit,
    onMaterialYouChange: (Boolean) -> Unit,
    onColorPaletteChange: (ColorPalette) -> Unit,
    onSavePin: (String) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onBackupNow: () -> Unit,
    onAcknowledgeRecoveryCode: () -> Unit,
    onRestoreBackup: (String, RemoteManifestPointer?) -> Unit,
    onExportDiary: () -> Unit,
    onRefreshServerOverview: () -> Unit,
    onCollectServerGarbage: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (ReleaseInfo) -> Unit,
    onInstallDownloadedUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf(state.settings.serverUrl) }
    var token by remember { mutableStateOf(state.settings.serverToken) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var recoveryCodeVisible by remember { mutableStateOf(false) }
    var restoreDialogVisible by remember { mutableStateOf(false) }
    var restoreCode by remember { mutableStateOf("") }
    var restoreSnapshot by remember { mutableStateOf<RemoteManifestPointer?>(null) }
    var cleanupDialogVisible by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.settings.serverUrl, state.settings.serverToken) {
        serverUrl = state.settings.serverUrl
        token = state.settings.serverToken
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SettingsSectionTitle(
            icon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
            title = "Appearance",
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemePreference.entries.forEach { theme ->
                        FilterChip(
                            selected = state.settings.theme == theme,
                            onClick = { onThemeChange(theme) },
                            label = { Text(theme.name) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text("Color palette", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ColorPalette.entries.chunked(3).forEach { rowPalettes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowPalettes.forEach { palette ->
                            FilterChip(
                                selected = !state.settings.useMaterialYou &&
                                    state.settings.colorPalette == palette,
                                onClick = {
                                    onMaterialYouChange(false)
                                    onColorPaletteChange(palette)
                                },
                                label = { Text(palette.name) },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(11.dp)
                                            .background(palette.previewColor(), CircleShape),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Material You colors", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                "Use colors from your wallpaper"
                            } else {
                                "Requires Android 12 or newer"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.useMaterialYou,
                        onCheckedChange = onMaterialYouChange,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                }
            }
        }

        SettingsSectionTitle(
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            title = "Privacy",
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (state.settings.hasPin) "Change PIN" else "Set an app PIN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                        label = { Text("Confirm") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        onSavePin(pin)
                        pin = ""
                        confirmPin = ""
                    },
                    enabled = pin.length in 4..8 && pin == confirmPin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.settings.hasPin) "Change PIN" else "Set PIN") }
                SettingSwitchRow(
                    title = "Lock when Mneme opens",
                    subtitle = "Require your PIN before showing entries",
                    checked = state.settings.appLockEnabled,
                    enabled = state.settings.hasPin,
                    onCheckedChange = onAppLockChange,
                )
                SettingSwitchRow(
                    title = "Fingerprint or face",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        "Use the secure Android biometric prompt"
                    } else {
                        "Requires Android 9 or newer"
                    },
                    checked = state.settings.biometricEnabled,
                    enabled = state.settings.appLockEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
                    onCheckedChange = onBiometricChange,
                    icon = { Icon(Icons.Rounded.Fingerprint, contentDescription = null) },
                )
                state.lockMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        SettingsSectionTitle(
            icon = {
                Icon(
                    if (state.settings.serverConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                    contentDescription = null,
                )
            },
            title = "Self-hosted backup",
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Connect to your Docker server locally or through its Cloudflare Tunnel address.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server URL") },
                    placeholder = { Text("https://diary.example.com or http://10.0.2.2:8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Access token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text(
                    "Generate one with: docker compose exec mneme-server /mneme token create --name Android",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.connectionMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.connectionStatus == ServerConnectionStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                if (state.consecutiveBackupFailures > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text("Backup needs attention", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${state.consecutiveBackupFailures} recent attempt" +
                                        if (state.consecutiveBackupFailures == 1) " failed." else "s failed.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onConnect(serverUrl, token) },
                        enabled = state.connectionStatus != ServerConnectionStatus.Testing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.connectionStatus == ServerConnectionStatus.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(if (state.settings.serverConnected) "Test again" else "Connect")
                        }
                    }
                    if (state.settings.serverConnected) {
                        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                    }
                }
                if (state.settings.serverConnected) {
                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    if (state.serverOutdated) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                                Column(Modifier.weight(1f)) {
                                    Text("Server update available", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Connected: ${state.settings.serverVersion}. Latest: ${state.latestRelease?.version}. " +
                                            "Run docker compose pull, then docker compose up -d.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    } else if (state.settings.serverVersion.isNotBlank()) {
                        Text(
                            "Server ${state.settings.serverVersion}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Encrypted backups run automatically about every 6 hours when the device is online.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.lastSuccessfulBackupAt?.let {
                            "Last successful backup: ${DateFormat.getDateTimeInstance().format(Date(it))}"
                        } ?: "No successful portable backup yet.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onBackupNow,
                        enabled = state.backupStatus != BackupStatus.Running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.backupStatus == BackupStatus.Running) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                        }
                        Text("Back up now", Modifier.padding(start = 8.dp))
                    }
                    state.backupMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.backupStatus == BackupStatus.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    Text(
                        "Recovery code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "This code is the only way to decrypt your server backup after losing your phone. " +
                            "Keep it somewhere outside Mneme; the server token cannot replace it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.recoveryCodeNeedsSaving) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (recoveryCodeVisible) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            SelectionContainer {
                                Text(
                                    state.recoveryCode,
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { recoveryCodeVisible = !recoveryCodeVisible },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (recoveryCodeVisible) "Hide code" else "Reveal code") }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(state.recoveryCode))
                                onAcknowledgeRecoveryCode()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Copy code") }
                    }
                    OutlinedButton(
                        onClick = {
                            restoreSnapshot = null
                            restoreDialogVisible = true
                        },
                        enabled = state.restoreStatus != RestoreStatus.Running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.restoreStatus == RestoreStatus.Running) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        Text("Restore on this device", Modifier.padding(start = 8.dp))
                    }
                    state.restoreMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.restoreStatus == RestoreStatus.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null)
                        Text(
                            "Backup history",
                            Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = onRefreshServerOverview) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(18.dp))
                            Text("Refresh")
                        }
                    }
                    if (state.serverSnapshots.isEmpty()) {
                        Text(
                            if (state.maintenanceStatus == MaintenanceStatus.Loading) "Loading snapshots…" else "No snapshots reported yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.serverSnapshots.take(6).forEach { snapshot ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        snapshot.updatedAt.toDisplayDate(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        "${snapshot.deviceId.take(24)} · snapshot ${snapshot.revision}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = {
                                    restoreSnapshot = snapshot
                                    restoreDialogVisible = true
                                }) { Text("Restore") }
                            }
                        }
                        if (state.serverSnapshots.size > 6) {
                            Text(
                                "${state.serverSnapshots.size - 6} more snapshots are retained on the server.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.serverStorage?.let { storage ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Storage, contentDescription = null)
                                Column(Modifier.weight(1f)) {
                                    Text(formatBytes(storage.objectBytes), fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${storage.objectCount} encrypted objects · ${storage.manifestHistoryCount} snapshots",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (storage.incompleteManifestCount > 0) {
                                        Text(
                                            "${storage.incompleteManifestCount} legacy snapshot(s) are protected conservatively.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { cleanupDialogVisible = true },
                            enabled = state.maintenanceStatus != MaintenanceStatus.Running,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Safely clean server storage") }
                    }
                    state.maintenanceMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.maintenanceStatus == MaintenanceStatus.Error) {
                                MaterialTheme.colorScheme.error
                            } else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        SettingsSectionTitle(
            icon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
            title = "Export & ownership",
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Make a readable ZIP you can keep anywhere.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "It contains an HTML index, HTML and Markdown entries, original photos, and photo metadata. " +
                        "This export is not encrypted, so store it somewhere private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onExportDiary,
                    enabled = state.exportStatus != ExportStatus.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.exportStatus == ExportStatus.Running) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else Icon(Icons.Rounded.IosShare, contentDescription = null)
                    Text("Export diary", Modifier.padding(start = 8.dp))
                }
                state.exportMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.exportStatus == ExportStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        SettingsSectionTitle(
            icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
            title = "App updates",
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Installed version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Mneme checks its public GitHub releases when the app opens. Updates are always your choice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.updateMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.updateStatus == UpdateStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Button(
                    onClick = onCheckForUpdates,
                    enabled = state.updateStatus != UpdateStatus.Checking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.updateStatus == UpdateStatus.Checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                    }
                    Text("Check for updates", Modifier.padding(start = 8.dp))
                }
                state.availableUpdate?.let { release ->
                    if (state.updateDownloadPhase == UpdateDownloadPhase.Downloading) {
                        LinearProgressIndicator(
                            progress = { (state.updateDownloadProgress ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.updateDownloadMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.updateDownloadPhase == UpdateDownloadPhase.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (state.updateDownloadPhase == UpdateDownloadPhase.Downloaded) {
                                onInstallDownloadedUpdate()
                            } else {
                                onDownloadUpdate(release)
                            }
                        },
                        enabled = state.updateDownloadPhase != UpdateDownloadPhase.Downloading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Text(
                            if (state.updateDownloadPhase == UpdateDownloadPhase.Downloaded) {
                                "Install ${release.version}"
                            } else {
                                "Download ${release.version}"
                            },
                            Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
    }

    if (restoreDialogVisible) {
        AlertDialog(
            onDismissRequest = { restoreDialogVisible = false },
            title = { Text("Restore encrypted backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Use this on a new, empty Mneme installation. Existing local entries will never be overwritten." +
                            (restoreSnapshot?.let { " You selected the snapshot from ${it.updatedAt.toDisplayDate()}." } ?: ""),
                    )
                    OutlinedTextField(
                        value = restoreCode,
                        onValueChange = { restoreCode = it.uppercase() },
                        label = { Text("Recovery code") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        restoreDialogVisible = false
                        onRestoreBackup(restoreCode, restoreSnapshot)
                    },
                    enabled = restoreCode.count(Char::isLetterOrDigit) == 64,
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoreDialogVisible = false }) { Text("Cancel") }
            },
        )
    }
    if (cleanupDialogVisible) {
        AlertDialog(
            onDismissRequest = { cleanupDialogVisible = false },
            title = { Text("Clean server storage?") },
            text = {
                Text(
                    "Mneme will keep the newest 30 snapshots from every device and every encrypted object they use. " +
                        "Only proven-unused objects older than 24 hours are removed.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    cleanupDialogVisible = false
                    onCollectServerGarbage()
                }) { Text("Clean safely") }
            },
            dismissButton = {
                TextButton(onClick = { cleanupDialogVisible = false }) { Text("Cancel") }
            },
        )
    }
}

private fun String.toDisplayDate(): String = runCatching {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(Instant.parse(this)))
}.getOrElse { this }

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icon?.invoke()
        Column(Modifier.weight(1f).padding(start = if (icon == null) 0.dp else 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingsSectionTitle(
    icon: @Composable () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun ColorPalette.previewColor(): Color = when (this) {
    ColorPalette.Ocean -> Color(0xFF39A7C8)
    ColorPalette.Forest -> Color(0xFF65A367)
    ColorPalette.Lavender -> Color(0xFF8B75BD)
    ColorPalette.Rose -> Color(0xFFC66B82)
    ColorPalette.Amber -> Color(0xFFD68A22)
    ColorPalette.Graphite -> Color(0xFF77737A)
}
