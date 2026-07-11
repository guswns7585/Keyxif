package com.keyxif.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keyxif.app.BuildConfig
import com.keyxif.app.domain.model.AppStep
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.UpdateDownloadState
import com.keyxif.app.domain.model.UpdateInfo
import com.keyxif.app.domain.model.displayName
import com.keyxif.app.ui.screens.BuildInfoScreen
import com.keyxif.app.ui.screens.ExportScreen
import com.keyxif.app.ui.screens.ExportedGalleryScreen
import com.keyxif.app.ui.screens.PhotoPickerScreen
import com.keyxif.app.ui.screens.SettingsScreen
import com.keyxif.app.ui.screens.TemplateSelectScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KeyxifApp(viewModel: KeyxifViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingExportRequest by remember { mutableStateOf<ExportRequest?>(null) }

    fun runExport(request: ExportRequest) {
        when (request) {
            ExportRequest.All -> viewModel.saveAll()
            is ExportRequest.One -> viewModel.savePhoto(request.photoId)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            val request = pendingExportRequest
            pendingExportRequest = null
            request?.let(::runExport)
        },
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            val request = pendingExportRequest
            if (!granted || request == null) {
                pendingExportRequest = null
                viewModel.showExportMessage("Android 9 이하에서는 저장 권한이 필요합니다.")
                return@rememberLauncherForActivityResult
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@rememberLauncherForActivityResult
            }
            pendingExportRequest = null
            runExport(request)
        },
    )

    fun hasWriteAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestExport(request: ExportRequest) {
        if (!hasWriteAccess()) {
            pendingExportRequest = request
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (!hasNotificationAccess()) {
            pendingExportRequest = request
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runExport(request)
        }
    }

    BackHandler(enabled = state.isSettingsOpen || state.isGalleryOpen || state.currentStep != AppStep.Photos) {
        viewModel.handleSystemBack()
    }

    LaunchedEffect(state.uiMessage) {
        val message = state.uiMessage ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(message)
    }

    if (state.showDraftRestorePrompt) {
        DraftRestoreDialog(
            lastUpdatedAt = state.draftLastUpdatedAt,
            onRestore = viewModel::restoreDraftSession,
            onDiscard = viewModel::discardDraftSession,
        )
    }

    state.updateCheckState.latestInfo
        ?.takeIf { state.showUpdateDialog }
        ?.let { updateInfo ->
            UpdateAvailableDialog(
                updateInfo = updateInfo,
                downloadState = state.updateDownloadState,
                onDismiss = viewModel::dismissUpdateDialog,
                onOpenApk = viewModel::openUpdateApk,
                onOpenReleaseNotes = viewModel::openReleaseNotes,
            )
        }

    if (state.showUnknownSourcesDialog) {
        UnknownSourcesDialog(
            onDismiss = viewModel::dismissUnknownSourcesDialog,
            onOpenSettings = viewModel::openUnknownSourcesSettings,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KeyxifTopBar(
                state = state,
                onSettings = viewModel::openSettings,
                onGallery = viewModel::openGallery,
                onBackFromSettings = viewModel::closeSettings,
                onBackFromGallery = viewModel::closeGallery,
                onStepClick = viewModel::navigateToStep,
            )
        },
        bottomBar = {
            if (!state.isSettingsOpen && !state.isGalleryOpen) {
                StepBottomActions(
                    state = state,
                    onPrevious = viewModel::navigateToPreviousStep,
                    onNext = {
                        when (state.currentStep) {
                            AppStep.Photos -> viewModel.navigateToStep(AppStep.BuildInfo)
                            AppStep.BuildInfo -> viewModel.completeBuildInfo()
                            AppStep.Template -> viewModel.navigateToStep(AppStep.Export)
                            AppStep.Export -> Unit
                        }
                    },
                    onSaveSelected = {
                        state.selectedPhoto?.id?.let { requestExport(ExportRequest.One(it)) }
                    },
                    onSaveAll = { requestExport(ExportRequest.All) },
                )
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (state.isSettingsOpen) {
                SettingsScreen(
                    settings = state.settings,
                    updateCheckState = state.updateCheckState,
                    updateDownloadState = state.updateDownloadState,
                    exportedImageCount = state.exportedImages.size,
                    onSettingsChange = viewModel::updateSettings,
                    onCheckUpdate = viewModel::checkForUpdate,
                    onInstallDownloadedUpdate = viewModel::installDownloadedUpdate,
                    onPruneMissingExportedImages = viewModel::pruneMissingExportedImages,
                    onClearExportedImageRecords = viewModel::clearExportedImageRecords,
                    onOpenContactEmail = viewModel::openSupportEmail,
                )
            } else if (state.isGalleryOpen) {
                ExportedGalleryScreen(
                    images = state.exportedImages,
                    onBackToCreate = viewModel::closeGallery,
                    onShare = viewModel::shareExportedImage,
                    onOpen = viewModel::openExportedImage,
                    onRemoveRecord = viewModel::removeExportedImageRecord,
                    onDeleteFile = viewModel::deleteExportedImageFile,
                )
            } else {
                when (state.currentStep) {
                    AppStep.Photos -> PhotoPickerScreen(
                        state = state,
                        onPick = viewModel::addPhotos,
                        onRemove = viewModel::removePhoto,
                        onMove = viewModel::movePhoto,
                        onDismissMessage = viewModel::clearShareMessage,
                    )

                    AppStep.BuildInfo -> BuildInfoScreen(
                        state = state,
                        plates = viewModel.plates,
                        mounts = viewModel.mounts,
                        logos = viewModel.logos,
                        presets = state.buildPresets,
                        housingOptions = viewModel::housingOptions,
                        switchOptions = viewModel::switchOptions,
                        keycapOptions = viewModel::keycapOptions,
                        onSelectPhoto = viewModel::selectPhoto,
                        onBuildInfoChange = viewModel::updateBuildInfo,
                        onHousingPreset = viewModel::selectHousingPreset,
                        onSwitchPreset = viewModel::selectSwitchPreset,
                        onKeycapPreset = viewModel::selectKeycapPreset,
                        onApplyToAll = viewModel::applyBuildInfoToAll,
                        onPresetQueryChange = viewModel::updatePresetQuery,
                        onSavePreset = viewModel::saveBuildPreset,
                        onApplyPreset = viewModel::applyBuildPreset,
                        onDeletePreset = viewModel::deleteBuildPreset,
                    )

                    AppStep.Template -> TemplateSelectScreen(
                        selectedTemplate = state.selectedTemplate,
                        buildInfo = state.selectedPhoto?.buildInfo ?: KeyboardBuildInfo(),
                        selectedPhotoLabel = state.selectedPhoto?.displayName,
                        onSelect = viewModel::selectTemplate,
                    )

                    AppStep.Export -> ExportScreen(
                        state = state,
                        viewModel = viewModel,
                        onSaveOne = { requestExport(ExportRequest.One(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyxifTopBar(
    state: KeyxifUiState,
    onSettings: () -> Unit,
    onGallery: () -> Unit,
    onBackFromSettings: () -> Unit,
    onBackFromGallery: () -> Unit,
    onStepClick: (AppStep) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isSettingsOpen || state.isGalleryOpen) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = if (state.isSettingsOpen) onBackFromSettings else onBackFromGallery) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                    Text(
                        text = if (state.isSettingsOpen) "설정" else "완성 이미지",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = "Keyxif",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "사진 ${state.photos.size}장",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onGallery) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "완성 이미지")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            }
        }
        if (!state.isSettingsOpen && !state.isGalleryOpen) {
            KeyxifStepIndicator(
                currentStep = state.currentStep,
                onStepClick = onStepClick,
            )
        }
    }
}

@Composable
private fun KeyxifStepIndicator(
    currentStep: AppStep,
    onStepClick: (AppStep) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppStep.entries.forEach { step ->
            val selected = step == currentStep
            val completed = step.ordinal < currentStep.ordinal
            val color = when {
                selected -> MaterialTheme.colorScheme.primary
                completed -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val textColor = when {
                selected -> MaterialTheme.colorScheme.primary
                completed -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onStepClick(step) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = step.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StepBottomActions(
    state: KeyxifUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSaveSelected: () -> Unit,
    onSaveAll: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val previous = previousStep(state.currentStep)
                if (previous == null) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !state.exportProgress.isSaving,
                        onClick = onPrevious,
                    ) {
                        Text("이전")
                    }
                }

                if (state.currentStep == AppStep.Export) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = state.selectedPhoto != null && !state.exportProgress.isSaving,
                        onClick = onSaveSelected,
                    ) {
                        Text("선택 저장")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = state.photos.isNotEmpty() && !state.exportProgress.isSaving,
                        onClick = onSaveAll,
                    ) {
                        Text("전체 저장")
                    }
                } else {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !state.exportProgress.isSaving,
                        onClick = onNext,
                    ) {
                        Text(nextActionLabel(state.currentStep))
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftRestoreDialog(
    lastUpdatedAt: Long?,
    onRestore: () -> Unit,
    onDiscard: () -> Unit,
) {
    val dateText = remember(lastUpdatedAt) {
        lastUpdatedAt
            ?.takeIf { it > 0L }
            ?.let { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(it)) }
            ?: "저장 시각 알 수 없음"
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("이전 작업을 복구할까요?") },
        text = { Text("임시 저장된 Keyxif 작업이 있습니다.\n마지막 저장: $dateText") },
        confirmButton = {
            Button(onClick = onRestore) {
                Text("복구")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("새로 시작")
            }
        },
    )
}

@Composable
private fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onOpenApk: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
) {
    val forced = updateInfo.forceUpdate ||
        updateInfo.minRequiredVersionCode > BuildConfig.VERSION_CODE
    AlertDialog(
        onDismissRequest = { if (!forced) onDismiss() },
        title = { Text(updateInfo.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (updateInfo.message.isNotBlank()) {
                    Text(updateInfo.message)
                }
                Text("현재 버전: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("최신 버전: ${updateInfo.latestVersionName} (${updateInfo.latestVersionCode})")
                if (downloadState.isDownloading) {
                    val progress = downloadState.progressPercent
                    LinearProgressIndicator(
                        progress = { (progress ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("APK 다운로드 중${progress?.let { " · $it%" }.orEmpty()}")
                }
                downloadState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (forced) {
                    Text(
                        text = "이 버전은 업데이트 후 계속 사용할 수 있습니다.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !downloadState.isDownloading,
                onClick = onOpenApk,
            ) {
                Text(if (downloadState.downloadedApkPath == null) "지금 업데이트" else "설치 계속")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (updateInfo.releaseNoteUrl != null) {
                    TextButton(onClick = onOpenReleaseNotes) {
                        Text("릴리즈 노트")
                    }
                }
                if (!forced) {
                    TextButton(onClick = onDismiss) {
                        Text("나중에")
                    }
                }
            }
        },
    )
}

@Composable
private fun UnknownSourcesDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설치 권한이 필요합니다") },
        text = {
            Text(
                "Android 8.0 이상에서는 앱별로 APK 설치를 허용해야 합니다.\n" +
                    "Keyxif 업데이트 APK는 Android 설치 화면에서 사용자가 직접 승인해야 설치됩니다.",
            )
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text("설정 열기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

private fun previousStep(step: AppStep): AppStep? = when (step) {
    AppStep.Photos -> null
    AppStep.BuildInfo -> AppStep.Photos
    AppStep.Template -> AppStep.BuildInfo
    AppStep.Export -> AppStep.Template
}

private fun nextActionLabel(step: AppStep): String = when (step) {
    AppStep.Photos -> "정보"
    AppStep.BuildInfo -> "템플릿"
    AppStep.Template -> "저장"
    AppStep.Export -> "저장"
}

private sealed interface ExportRequest {
    data object All : ExportRequest
    data class One(val photoId: String) : ExportRequest
}
