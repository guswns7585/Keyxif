package com.keyxif.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.keyxif.app.ui.screens.BuildInfoScreen
import com.keyxif.app.ui.screens.CustomTemplateEditorScreen
import com.keyxif.app.ui.screens.ExportScreen
import com.keyxif.app.ui.screens.ExportedGalleryScreen
import com.keyxif.app.ui.screens.PaletteScreen
import com.keyxif.app.ui.screens.PhotoPickerScreen
import com.keyxif.app.ui.screens.SettingsScreen
import com.keyxif.app.ui.screens.TemplateSelectScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CUSTOM_TEMPLATE_UI_ENABLED = false

@Composable
fun KeyxifApp(viewModel: KeyxifViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val customTemplateEditorVisible = CUSTOM_TEMPLATE_UI_ENABLED && state.customTemplateEditorState != null
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingExportRequest by remember { mutableStateOf<ExportRequest?>(null) }
    var pendingBackupRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri -> uri?.let(viewModel::createBackup) },
    )
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> pendingBackupRestoreUri = uri },
    )

    fun runExport(request: ExportRequest) {
        when (request) {
            ExportRequest.All -> viewModel.saveAll()
            is ExportRequest.Many -> viewModel.savePhotos(request.photoIds)
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

    if (state.showDraftRestorePrompt && !state.showUpdateDialog) {
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

    // 저장 단계 전체화면 미리보기가 떠 있는 동안 앱 콘텐츠 전체를 블러 처리한다.
    // 창 단위 블러(FLAG_BLUR_BEHIND)는 기기 지원 여부에 따라 무시되는 경우가 많아
    // 앱 내부 RenderEffect 블러를 사용한다. (Android 11 이하에서는 스크림만 적용)
    val previewDialogOpen = !state.isSettingsOpen &&
        !state.isGalleryOpen &&
        state.currentStep == AppStep.Export &&
        state.expandedExportPhotoId != null &&
        state.settings.enableExportPreviewZoom
    val backgroundBlurRadius by animateDpAsState(
        targetValue = if (previewDialogOpen) 20.dp else 0.dp,
        label = "previewBackgroundBlur",
    )

    Scaffold(
        modifier = if (backgroundBlurRadius > 0.dp) Modifier.blur(backgroundBlurRadius) else Modifier,
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
            if (!state.isSettingsOpen &&
                !state.isGalleryOpen &&
                !customTemplateEditorVisible &&
                !(state.currentStep == AppStep.Photos && state.photos.isEmpty())
            ) {
                StepBottomActions(
                    state = state,
                    onPrevious = viewModel::navigateToPreviousStep,
                    onNext = {
                        when (state.currentStep) {
                            AppStep.Photos -> viewModel.navigateToStep(AppStep.BuildInfo)
                            AppStep.BuildInfo -> viewModel.completeBuildInfo()
                            AppStep.Palette -> viewModel.navigateToStep(AppStep.Template)
                            AppStep.Template -> viewModel.navigateToStep(AppStep.Export)
                            AppStep.Export -> Unit
                        }
                    },
                    onSaveSelected = {
                        state.selectedExportPhotoIds
                            .takeIf { it.isNotEmpty() }
                            ?.let { requestExport(ExportRequest.Many(it.toList())) }
                    },
                    onSaveAll = { requestExport(ExportRequest.All) },
                    selectedExportCount = state.selectedExportPhotoIds.size,
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
                    selectedPageName = state.settingsPageName,
                    onSelectedPageNameChange = viewModel::selectSettingsPage,
                    onSettingsChange = viewModel::updateSettings,
                    onCheckUpdate = viewModel::checkForUpdate,
                    onInstallDownloadedUpdate = viewModel::installDownloadedUpdate,
                    onPruneMissingExportedImages = viewModel::pruneMissingExportedImages,
                    onClearExportedImageRecords = viewModel::clearExportedImageRecords,
                    onCreateBackup = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        backupCreateLauncher.launch("Keyxif_backup_$stamp.zip")
                    },
                    onChooseBackupToRestore = {
                        backupRestoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    onOpenContactEmail = viewModel::openSupportEmail,
                )
            } else if (state.isGalleryOpen) {
                ExportedGalleryScreen(
                    images = state.exportedImages,
                    onBackToCreate = viewModel::closeGallery,
                    onShare = viewModel::shareExportedImage,
                    onOpen = viewModel::openExportedImage,
                    onRemoveRecord = viewModel::removeExportedImageRecord,
                    onDeleteFiles = viewModel::deleteExportedImageFiles,
                )
            } else {
                when (state.currentStep) {
                    AppStep.Photos -> PhotoPickerScreen(
                        state = state,
                        onPick = viewModel::addPhotos,
                        onRemove = viewModel::removePhoto,
                        onClearAll = viewModel::clearPhotos,
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
                        onClearBuildInfo = viewModel::clearSelectedBuildInfo,
                        onApplyToAll = viewModel::applyBuildInfoToAll,
                        onPresetQueryChange = viewModel::updatePresetQuery,
                        onSavePreset = viewModel::saveBuildPreset,
                        onApplyPreset = viewModel::applyBuildPreset,
                        onDeletePreset = viewModel::deleteBuildPreset,
                        onDeleteRecentHousing = viewModel::removeRecentHousing,
                        onDeleteRecentSwitch = viewModel::removeRecentSwitch,
                        onDeleteRecentKeycap = viewModel::removeRecentKeycap,
                        onDeleteRecentNickname = viewModel::removeRecentNickname,
                    )

                    AppStep.Template -> {
                        val editorState = state.customTemplateEditorState
                        if (CUSTOM_TEMPLATE_UI_ENABLED && editorState != null) {
                            val buildInfo = state.selectedPhoto?.buildInfo ?: KeyboardBuildInfo()
                            val logoPreset = viewModel.logos.firstOrNull { it.id == buildInfo.logoId }
                            val logoModel: Any? = when {
                                buildInfo.logoDisabled -> null
                                buildInfo.customLogoUri != null -> buildInfo.customLogoUri
                                logoPreset != null -> logoPreset.blackDrawableResId
                                    ?: logoPreset.drawableResId
                                    ?: logoPreset.whiteDrawableResId
                                else -> null
                            }
                            CustomTemplateEditorScreen(
                                editorState = editorState,
                                selectedPhotoUri = state.selectedPhoto?.uri,
                                paletteColors = state.selectedPhoto?.analysisResult?.paletteColors ?: emptyList(),
                                buildInfo = buildInfo,
                                logoModel = logoModel,
                                onSelectTab = viewModel::selectCustomTemplateEditorTab,
                                onSelectTarget = viewModel::selectCustomTemplateTarget,
                                onSelectElement = viewModel::selectCustomTemplateElement,
                                onSelectCard = viewModel::selectCustomTemplateCard,
                                onAddTextElement = viewModel::addCustomTemplateTextElement,
                                onAddLogoElement = viewModel::addCustomTemplateLogoElement,
                                onAddColorChipElement = viewModel::addCustomTemplateColorChipElement,
                                onAddCard = viewModel::addCustomTemplateInternalCard,
                                onUpdateCardBounds = viewModel::updateCustomTemplateCardBounds,
                                onApplyCardStyle = viewModel::applySelectedCustomTemplateCardStyle,
                                onUpdateSelectedCardStyle = viewModel::updateSelectedCustomTemplateCardStyle,
                                onDuplicateSelected = viewModel::duplicateSelectedCustomTemplateElement,
                                onBeginInteraction = viewModel::beginCustomTemplateInteraction,
                                onUpdateElementBounds = viewModel::updateCustomTemplateElementBounds,
                                onUpdateElementPlacement = viewModel::updateCustomTemplateElementPlacement,
                                onUpdatePhotoBounds = viewModel::updateCustomTemplatePhotoBounds,
                                onUpdateFrameSize = viewModel::updateCustomTemplateFrameSize,
                                onUpdateOuterMargins = viewModel::updateCustomTemplateOuterMargins,
                                onFramePreset = viewModel::applyCustomTemplateFramePreset,
                                onPhotoAspectRatioResolved = viewModel::updateCustomTemplatePhotoAspectRatio,
                                onFinishInteraction = viewModel::finishCustomTemplateInteraction,
                                onDeleteSelected = viewModel::deleteSelectedCustomTemplateItem,
                                onUndo = viewModel::undoCustomTemplateEdit,
                                onRedo = viewModel::redoCustomTemplateEdit,
                                onSave = viewModel::saveCustomTemplate,
                                onNudgeSelected = viewModel::nudgeCustomTemplateSelection,
                                onAlignSelected = viewModel::alignCustomTemplateSelection,
                                onResetTemplate = viewModel::resetCustomTemplateDraft,
                                onClose = viewModel::closeCustomTemplateEditor,
                            )
                        } else {
                            TemplateSelectScreen(
                                selectedTemplate = state.selectedTemplate,
                                selectedCustomTemplateId = state.selectedCustomTemplateId.takeIf { CUSTOM_TEMPLATE_UI_ENABLED },
                                customTemplates = if (CUSTOM_TEMPLATE_UI_ENABLED) state.customTemplates else emptyList(),
                                buildInfo = state.selectedPhoto?.buildInfo ?: KeyboardBuildInfo(),
                                selectedPhotoLabel = state.selectedPhoto?.displayName,
                                customTemplateUiEnabled = CUSTOM_TEMPLATE_UI_ENABLED,
                                onSelect = viewModel::selectTemplate,
                                onSelectCustomTemplate = viewModel::selectCustomTemplate,
                                onCreateTemplate = viewModel::openCustomTemplateEditor,
                                onEditCustomTemplate = viewModel::editCustomTemplate,
                                onDuplicateCustomTemplate = viewModel::duplicateCustomTemplate,
                                onDeleteCustomTemplate = viewModel::deleteCustomTemplate,
                            )
                        }
                    }

                    AppStep.Palette -> PaletteScreen(
                        state = state,
                        onSettingsChange = viewModel::updateSettings,
                        onSelectPhoto = viewModel::selectPhoto,
                        onRenderStyleChange = viewModel::updateSelectedPhotoRenderStyle,
                        onAnalysisModeChange = viewModel::updateSelectedPhotoAnalysisMode,
                        onAnalysisQuadChange = viewModel::updateSelectedPhotoAnalysisQuad,
                        onCenterRatioChange = viewModel::updateSelectedPhotoCenterRatio,
                        onMaskChange = viewModel::updateSelectedPhotoMask,
                        onReanalyze = viewModel::reanalyzeSelectedPalette,
                    )

                    AppStep.Export -> ExportScreen(
                        state = state,
                        viewModel = viewModel,
                        onSaveOne = { requestExport(ExportRequest.One(it)) },
                        onSelectionChange = viewModel::setExportPhotoSelected,
                        onClearSelection = viewModel::clearExportSelection,
                    )
                }
            }
        }
    }
    pendingBackupRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingBackupRestoreUri = null },
            title = { Text("백업 복원") },
            text = { Text("백업의 설정을 적용하고 프리셋, 최근 내역, 완성 이미지를 현재 데이터와 합칩니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingBackupRestoreUri = null
                        viewModel.restoreBackup(uri)
                    },
                ) { Text("복원") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackupRestoreUri = null }) { Text("취소") }
            },
        )
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
    val language = currentKeyxifLanguage(state.settings.languageMode)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = language.text("뒤로가기", "Back"))
                    }
                    Text(
                        text = if (state.isSettingsOpen) {
                            language.text("설정", "Settings")
                        } else {
                            language.text("완성 이미지", "Finished Images")
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Keyxif",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.photos.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = language.text("사진 ${state.photos.size}장", "${state.photos.size} photos"),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onGallery) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = language.text("완성 이미지", "Finished Images"))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = language.text("설정", "Settings"))
                    }
                }
            }
        }
        if (!state.isSettingsOpen && !state.isGalleryOpen) {
            KeyxifStepIndicator(
                state = state,
                currentStep = state.currentStep,
                language = language,
                onStepClick = onStepClick,
            )
        }
    }
}

@Composable
private fun KeyxifStepIndicator(
    state: KeyxifUiState,
    currentStep: AppStep,
    language: KeyxifLanguage,
    onStepClick: (AppStep) -> Unit,
) {
    val steps = visibleSteps(state)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { index, step ->
                val selected = step == currentStep
                val locked = state.photos.isEmpty() && step != AppStep.Photos
                val completed = steps.indexOf(currentStep).let { currentIndex ->
                    currentIndex >= 0 && index < currentIndex
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (locked) 0.52f else 1f),
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                    onClick = { onStepClick(step) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        completed -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (completed) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                        Text(
                            text = stepDisplayName(step, language),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
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
    selectedExportCount: Int,
) {
    val language = currentKeyxifLanguage(state.settings.languageMode)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val previous = previousStep(state)
                if (previous != null) {
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        enabled = !state.exportProgress.isSaving,
                        onClick = onPrevious,
                    ) {
                        Text(language.text("이전", "Previous"))
                    }
                }

                if (state.currentStep == AppStep.Export) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        enabled = selectedExportCount > 0 && !state.exportProgress.isSaving,
                        onClick = onSaveSelected,
                    ) {
                        Text(
                            if (selectedExportCount > 0) {
                                language.text("선택 저장 $selectedExportCount", "Save selected $selectedExportCount")
                            } else {
                                language.text("선택 저장", "Save selected")
                            },
                        )
                    }
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        enabled = state.photos.isNotEmpty() && !state.exportProgress.isSaving,
                        onClick = onSaveAll,
                    ) {
                        Text(language.text("전체 저장", "Save all"))
                    }
                } else {
                    Button(
                        modifier = Modifier
                            .weight(if (previous == null) 1f else 2f)
                            .height(50.dp),
                        enabled = !state.exportProgress.isSaving,
                        onClick = onNext,
                    ) {
                        Text(nextActionLabel(state.currentStep, language, state))
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

private fun previousStep(state: KeyxifUiState): AppStep? {
    val steps = visibleSteps(state)
    val index = steps.indexOf(state.currentStep)
    return steps.getOrNull(index - 1)
}

private fun visibleSteps(state: KeyxifUiState): List<AppStep> {
    return if (state.settings.showPaletteColors) {
        AppStep.entries
    } else {
        AppStep.entries.filterNot { it == AppStep.Palette }
    }
}

private fun stepDisplayName(
    step: AppStep,
    language: KeyxifLanguage,
): String = when (step) {
    AppStep.Photos -> language.text("사진", "Photos")
    AppStep.BuildInfo -> language.text("정보", "Info")
    AppStep.Palette -> language.text("색상", "Color")
    AppStep.Template -> language.text("템플릿", "Template")
    AppStep.Export -> language.text("저장", "Export")
}

private fun nextActionLabel(
    step: AppStep,
    language: KeyxifLanguage,
    state: KeyxifUiState,
): String = when (step) {
    AppStep.Photos -> language.text("빌드 정보 입력", "Enter build info")
    AppStep.BuildInfo -> if (state.settings.showPaletteColors) {
        language.text("색상 확인", "Review colors")
    } else {
        language.text("템플릿 선택", "Choose template")
    }
    AppStep.Palette -> language.text("템플릿 선택", "Choose template")
    AppStep.Template -> language.text("미리보기 · 저장", "Preview & save")
    AppStep.Export -> language.text("저장", "Save")
}

private sealed interface ExportRequest {
    data object All : ExportRequest
    data class Many(val photoIds: List<String>) : ExportRequest
    data class One(val photoId: String) : ExportRequest
}
