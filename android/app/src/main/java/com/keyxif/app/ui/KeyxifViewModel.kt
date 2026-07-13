package com.keyxif.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.keyxif.app.BuildConfig
import com.keyxif.app.data.exported.ExportedImageRepository
import com.keyxif.app.data.repository.AppSettingsRepository
import com.keyxif.app.data.repository.BuildPresetRepository
import com.keyxif.app.data.repository.DraftSessionRepository
import com.keyxif.app.data.repository.PresetChoice
import com.keyxif.app.data.repository.PresetRepository
import com.keyxif.app.data.repository.RecentStore
import com.keyxif.app.data.update.UpdateRepository
import com.keyxif.app.data.update.UpdateDownloadWorker
import com.keyxif.app.domain.analysis.PhotoPaletteAnalyzer
import com.keyxif.app.domain.export.ExportWorkPayload
import com.keyxif.app.domain.export.ExportWorkPayloadCodec
import com.keyxif.app.domain.export.ExportWorker
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.AppStep
import com.keyxif.app.domain.model.BuildPreset
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.DraftSession
import com.keyxif.app.domain.model.ExportProgress
import com.keyxif.app.domain.model.ExportedImage
import com.keyxif.app.domain.model.HousingPreset
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.KeycapPreset
import com.keyxif.app.domain.model.LogoPreset
import com.keyxif.app.domain.model.MaskStroke
import com.keyxif.app.domain.model.NormalizedRect
import com.keyxif.app.domain.model.PaletteAnalysisMode
import com.keyxif.app.domain.model.PhotoAnalysisResult
import com.keyxif.app.domain.model.PhotoItem
import com.keyxif.app.domain.model.PhotoRenderStyle
import com.keyxif.app.domain.model.RenderStatus
import com.keyxif.app.domain.model.SwitchPreset
import com.keyxif.app.domain.model.UpdateCheckState
import com.keyxif.app.domain.model.UpdateDownloadState
import com.keyxif.app.domain.model.defaultPaletteAnalysisRect
import com.keyxif.app.domain.renderer.KeyxifCanvasRenderer
import com.keyxif.app.util.BitmapUtils
import com.keyxif.app.util.FileNameUtils
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class KeyxifUiState(
    val currentStep: AppStep = AppStep.Photos,
    val photos: List<PhotoItem> = emptyList(),
    val selectedPhotoId: String? = null,
    val selectedExportPhotoIds: Set<String> = emptySet(),
    val expandedExportPhotoId: String? = null,
    val selectedTemplate: CardTemplate = CardTemplate.ClassicFrame,
    val settings: AppSettings = AppSettings(),
    val isSettingsOpen: Boolean = false,
    val settingsPageName: String? = null,
    val isGalleryOpen: Boolean = false,
    val exportedImages: List<ExportedImage> = emptyList(),
    val buildPresets: List<BuildPreset> = emptyList(),
    val presetQuery: String = "",
    val recentHousing: List<String> = emptyList(),
    val recentSwitches: List<String> = emptyList(),
    val recentKeycaps: List<String> = emptyList(),
    val recentNicknames: List<String> = emptyList(),
    val shareMessage: String? = null,
    val uiMessage: String? = null,
    val exportProgress: ExportProgress = ExportProgress(),
    val showDraftRestorePrompt: Boolean = false,
    val draftLastUpdatedAt: Long? = null,
    val updateCheckState: UpdateCheckState = UpdateCheckState(),
    val updateDownloadState: UpdateDownloadState = UpdateDownloadState(),
    val showUpdateDialog: Boolean = false,
    val showUnknownSourcesDialog: Boolean = false,
) {
    val selectedPhoto: PhotoItem?
        get() = photos.firstOrNull { it.id == selectedPhotoId } ?: photos.firstOrNull()
}

class KeyxifViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val recentStore = RecentStore(application)
    private val presetRepository = PresetRepository()
    private val buildPresetRepository = BuildPresetRepository(application)
    private val settingsRepository = AppSettingsRepository(application)
    private val draftSessionRepository = DraftSessionRepository(application)
    private val exportedImageRepository = ExportedImageRepository(application)
    private val updateRepository = UpdateRepository(application)
    private val workManager = WorkManager.getInstance(application)
    private val renderer = KeyxifCanvasRenderer(presetRepository)
    private val paletteAnalyzer = PhotoPaletteAnalyzer()
    private val previewRenderSemaphore = Semaphore(2)
    private var appliedInitialSettings = false
    private var pendingDraftSession: DraftSession? = null
    private var autoSaveReady = false
    private var lastCompletedExportWorkId: UUID? = null
    private var lastCompletedUpdateWorkId: UUID? = null
    private var autoUpdateCheckAttempted = false
    private var paletteAnalysisJob: Job? = null

    private val _uiState = MutableStateFlow(KeyxifUiState())
    val uiState: StateFlow<KeyxifUiState> = _uiState.asStateFlow()

    val plates: List<String> = presetRepository.plates
    val mounts: List<String> = presetRepository.mounts
    val logos: List<LogoPreset> = presetRepository.logos

    init {
        refreshPersistentData()
        observeSettings()
        observeExportedImages()
        loadDraftOnStart()
        observeAutoSaveDraft()
        observeExportWork()
        observeUpdateDownloadWork()
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true, isGalleryOpen = false) }
    }

    fun closeSettings() {
        _uiState.update { state ->
            if (state.settingsPageName != null) {
                state.copy(settingsPageName = null)
            } else {
                state.copy(isSettingsOpen = false)
            }
        }
    }

    fun selectSettingsPage(name: String?) {
        _uiState.update { state ->
            if (state.isSettingsOpen) state.copy(settingsPageName = name) else state
        }
    }

    fun openGallery() {
        _uiState.update { it.copy(isGalleryOpen = true, isSettingsOpen = false, settingsPageName = null) }
    }

    fun closeGallery() {
        _uiState.update { it.copy(isGalleryOpen = false) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.update(transform)
        }
    }

    fun checkForUpdate() {
        checkForUpdate(manual = true)
    }

    fun dismissUpdateDialog() {
        val info = uiState.value.updateCheckState.latestInfo
        val forced = info != null &&
            (info.forceUpdate || info.minRequiredVersionCode > BuildConfig.VERSION_CODE)
        if (!forced) {
            _uiState.update { it.copy(showUpdateDialog = false) }
        }
    }

    fun openUpdateApk() {
        val info = uiState.value.updateCheckState.latestInfo ?: return
        uiState.value.updateDownloadState.downloadedApkPath?.let { path ->
            if (File(path).exists()) {
                installDownloadedUpdate(path)
                return
            }
        }
        if (info.apkUrl.isBlank()) {
            _uiState.update { it.copy(uiMessage = "APK URL이 비어 있습니다.") }
            return
        }
        val request = UpdateDownloadWorker.request(
            apkUrl = info.apkUrl,
            versionName = info.latestVersionName,
        )
        workManager.enqueueUniqueWork(
            UpdateDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        _uiState.update {
            it.copy(
                updateDownloadState = UpdateDownloadState(isDownloading = true, progressPercent = 0),
                uiMessage = "업데이트 APK 다운로드를 시작했습니다.",
            )
        }
    }

    fun openReleaseNotes() {
        val url = uiState.value.updateCheckState.latestInfo?.releaseNoteUrl.orEmpty()
        openUrl(url, "릴리즈 노트를 열 수 없습니다.")
    }

    fun installDownloadedUpdate() {
        val path = uiState.value.updateDownloadState.downloadedApkPath
        if (path.isNullOrBlank()) {
            _uiState.update { it.copy(uiMessage = "설치할 업데이트 APK가 없습니다.") }
            return
        }
        installDownloadedUpdate(path)
    }

    fun dismissUnknownSourcesDialog() {
        _uiState.update { it.copy(showUnknownSourcesDialog = false) }
    }

    fun openUnknownSourcesSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            getApplication<Application>().startActivity(intent)
        }.onFailure {
            openUrl("package:${BuildConfig.APPLICATION_ID}", "설치 권한 설정을 열 수 없습니다.")
        }
        _uiState.update { it.copy(showUnknownSourcesDialog = false) }
    }

    fun openSupportEmail() {
        val email = SUPPORT_EMAIL
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "Keyxif 문의")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            getApplication<Application>().startActivity(intent)
        }.isSuccess
        if (!opened) {
            copySupportEmailToClipboard()
            _uiState.update {
                it.copy(uiMessage = "메일 앱을 열 수 없어 이메일 주소를 복사했습니다.")
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(uiMessage = null) }
    }

    fun navigateToStep(step: AppStep) {
        val state = uiState.value
        val targetStep = normalizeStepForSettings(step, state.settings)
        val message = navigationBlockMessage(targetStep, state)
        if (message != null) {
            _uiState.update { it.copy(uiMessage = message) }
            return
        }
        val warning = navigationWarningMessage(targetStep, state)
        _uiState.update {
            it.copy(
                currentStep = targetStep,
                isSettingsOpen = false,
                settingsPageName = null,
                isGalleryOpen = false,
                uiMessage = warning,
            )
        }
    }

    fun navigateToPreviousStep() {
        previousStep(uiState.value)?.let(::navigateToStep)
    }

    fun handleSystemBack(): Boolean {
        val state = uiState.value
        return when {
            state.isSettingsOpen -> {
                closeSettings()
                true
            }
            state.isGalleryOpen -> {
                closeGallery()
                true
            }
            state.currentStep != AppStep.Photos -> {
                navigateToPreviousStep()
                true
            }
            else -> false
        }
    }

    fun addPhotos(uris: List<Uri>) {
        importPhotoUris(uris, fromShare = false)
    }

    fun addSharedImages(uris: List<Uri>) {
        importPhotoUris(uris, fromShare = true)
    }

    fun clearShareMessage() {
        _uiState.update { it.copy(shareMessage = null) }
    }

    fun restoreDraftSession() {
        val draft = pendingDraftSession ?: return
        viewModelScope.launch {
            autoSaveReady = false
            settingsRepository.update { draft.settings }
            _uiState.update { state ->
                state.copy(
                    photos = draft.photoItems.map(::validatedRestoredPhoto),
                    selectedTemplate = draft.selectedTemplate,
                    currentStep = normalizeStep(draft.currentStep),
                    selectedPhotoId = draft.selectedPhotoId,
                    settings = draft.settings,
                    isSettingsOpen = false,
                    showDraftRestorePrompt = false,
                    draftLastUpdatedAt = draft.lastUpdatedAt,
                    uiMessage = "이전 작업을 복구했습니다.",
                )
            }
            pendingDraftSession = null
            autoSaveReady = true
            schedulePaletteAnalysis(draft.settings)
        }
    }

    fun discardDraftSession() {
        viewModelScope.launch(Dispatchers.IO) {
            draftSessionRepository.clear()
            pendingDraftSession = null
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        photos = emptyList(),
                        selectedPhotoId = null,
                        currentStep = AppStep.Photos,
                        showDraftRestorePrompt = false,
                        draftLastUpdatedAt = null,
                        uiMessage = "이전 작업을 폐기했습니다.",
                    )
                }
                autoSaveReady = true
            }
        }
    }

    fun startNewSession() {
        viewModelScope.launch(Dispatchers.IO) {
            draftSessionRepository.clear()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        currentStep = AppStep.Photos,
                        photos = emptyList(),
                        selectedPhotoId = null,
                        exportProgress = ExportProgress(),
                        showDraftRestorePrompt = false,
                        draftLastUpdatedAt = null,
                    )
                }
            }
        }
    }

    private fun importPhotoUris(
        uris: List<Uri>,
        fromShare: Boolean,
    ) {
        if (uris.isEmpty()) {
            if (fromShare) {
                _uiState.update {
                    it.copy(
                        currentStep = AppStep.Photos,
                        shareMessage = "공유한 이미지가 없습니다.",
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentStep = if (fromShare) AppStep.Photos else it.currentStep,
                    shareMessage = if (fromShare) "공유한 이미지를 가져오는 중입니다." else it.shareMessage,
                    uiMessage = if (!fromShare) "사진을 가져오는 중입니다." else it.uiMessage,
                )
            }
            val state = uiState.value
            val copiedInfo = when {
                state.settings.copyPreviousBuildInfoOnAdd -> state.photos.lastOrNull()?.buildInfo ?: state.selectedPhoto?.buildInfo
                state.settings.rememberLastNickname -> KeyboardBuildInfo(nickname = state.recentNicknames.firstOrNull().orEmpty())
                else -> null
            }
            val imported = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                uris.distinctBy(Uri::toString).mapNotNull { uri ->
                    runCatching {
                        val displayName = displayNameFor(app, uri)
                        val localUri = copyUriToSourceStore(uri, displayName)
                        PhotoItem(
                            id = UUID.randomUUID().toString(),
                            uri = localUri,
                            displayName = displayName,
                            buildInfo = copiedInfo ?: KeyboardBuildInfo(),
                        )
                    }.getOrNull()
                }
            }

            _uiState.update { current ->
                current.copy(
                    photos = current.photos + imported,
                    selectedPhotoId = current.selectedPhotoId ?: imported.firstOrNull()?.id,
                    currentStep = if (fromShare) AppStep.Photos else current.currentStep,
                    shareMessage = if (fromShare) {
                        when {
                            imported.isNotEmpty() -> "공유한 이미지 ${imported.size}장을 사진 목록에 추가했습니다."
                            else -> "가져올 수 있는 새 이미지가 없습니다."
                        }
                    } else {
                        current.shareMessage
                    },
                    uiMessage = if (!fromShare) {
                        when {
                            imported.isNotEmpty() -> "사진 ${imported.size}장을 추가했습니다."
                            else -> "가져올 수 있는 새 사진이 없습니다."
                        }
                    } else {
                        current.uiMessage
                    },
                )
            }
            schedulePaletteAnalysis()
        }
    }

    fun removePhoto(id: String) {
        _uiState.update { state ->
            state.photos.firstOrNull { it.id == id }?.let(::deleteLocalPhotoFile)
            val updated = state.photos.filterNot { it.id == id }
            state.copy(
                photos = updated,
                selectedExportPhotoIds = state.selectedExportPhotoIds - id,
                expandedExportPhotoId = state.expandedExportPhotoId?.takeIf { it != id },
                selectedPhotoId = state.selectedPhotoId
                    ?.takeIf { selected -> updated.any { it.id == selected } }
                    ?: updated.firstOrNull()?.id,
            )
        }
    }

    fun clearPhotos() {
        _uiState.update { state ->
            state.photos.forEach(::deleteLocalPhotoFile)
            state.copy(
                photos = emptyList(),
                selectedPhotoId = null,
                selectedExportPhotoIds = emptySet(),
                expandedExportPhotoId = null,
                currentStep = AppStep.Photos,
                exportProgress = ExportProgress(),
                uiMessage = "사진 목록을 비웠습니다.",
            )
        }
    }

    fun movePhoto(id: String, direction: Int) {
        _uiState.update { state ->
            val index = state.photos.indexOfFirst { it.id == id }
            if (index < 0) return@update state
            val newIndex = (index + direction).coerceIn(0, state.photos.lastIndex)
            if (index == newIndex) return@update state
            val mutable = state.photos.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(newIndex, item)
            state.copy(photos = mutable)
        }
    }

    fun selectPhoto(id: String) {
        _uiState.update { state ->
            if (state.photos.any { it.id == id }) state.copy(selectedPhotoId = id) else state
        }
    }

    fun setExportPhotoSelected(id: String, selected: Boolean) {
        _uiState.update { state ->
            if (state.photos.none { it.id == id }) return@update state
            state.copy(
                selectedExportPhotoIds = if (selected) {
                    state.selectedExportPhotoIds + id
                } else {
                    state.selectedExportPhotoIds - id
                },
            )
        }
    }

    fun clearExportSelection() {
        _uiState.update { it.copy(selectedExportPhotoIds = emptySet()) }
    }

    fun setExpandedExportPhoto(photoId: String?) {
        _uiState.update { state ->
            if (photoId != null && state.photos.none { it.id == photoId }) return@update state
            state.copy(expandedExportPhotoId = photoId)
        }
    }

    fun updateBuildInfo(buildInfo: KeyboardBuildInfo) {
        val photoId = uiState.value.selectedPhoto?.id ?: return
        updatePhoto(photoId) { it.copy(buildInfo = buildInfo) }
    }

    fun updateSelectedPhotoRenderStyle(transform: (PhotoRenderStyle) -> PhotoRenderStyle) {
        val photoId = uiState.value.selectedPhoto?.id ?: return
        updatePhoto(photoId) { photo ->
            photo.copy(renderStyle = transform(photo.renderStyle))
        }
    }

    fun updateSelectedPhotoAnalysisMode(mode: PaletteAnalysisMode) {
        val photoId = uiState.value.selectedPhoto?.id ?: return
        updatePhoto(photoId) { photo ->
            val current = photo.analysisResult
            photo.copy(
                analysisResult = current.copy(
                    analysisMode = mode,
                    analysisRectNormalized = if (mode == PaletteAnalysisMode.RectSelection) {
                        current.analysisRectNormalized ?: defaultPaletteAnalysisRect()
                    } else {
                        current.analysisRectNormalized
                    },
                    analyzedAt = 0L,
                    isAnalyzing = false,
                    errorMessage = null,
                ),
            )
        }
        val result = uiState.value.photos.firstOrNull { it.id == photoId }?.analysisResult ?: return
        if (mode != PaletteAnalysisMode.PaintedMask || result.paintedMaskStrokes.isNotEmpty()) {
            schedulePaletteAnalysis()
        }
    }

    fun updateSelectedPhotoAnalysisRect(rect: NormalizedRect) {
        val photoId = uiState.value.selectedPhoto?.id ?: return
        updatePhoto(photoId) { photo ->
            photo.copy(analysisResult = photo.analysisResult.copy(analysisRectNormalized = rect.normalized()))
        }
    }

    fun updateSelectedPhotoCenterRatio(ratio: Float) {
        val photoId = uiState.value.selectedPhoto?.id ?: return
        updatePhoto(photoId) { photo ->
            photo.copy(
                analysisResult = photo.analysisResult.copy(
                    analysisCenterCropRatio = ratio.coerceIn(0.35f, 1f),
                ),
            )
        }
    }

    fun updateSelectedPhotoMask(strokes: List<MaskStroke>) {
        val photoId = uiState.value.selectedPhoto?.id ?: return
        updatePhoto(photoId) { photo ->
            photo.copy(analysisResult = photo.analysisResult.copy(paintedMaskStrokes = strokes))
        }
    }

    fun reanalyzeSelectedPalette() {
        val photoId = uiState.value.selectedPhoto?.id ?: return
        updatePhoto(photoId) { photo ->
            photo.copy(
                analysisResult = photo.analysisResult.copy(
                    analyzedAt = 0L,
                    isAnalyzing = false,
                    errorMessage = null,
                ),
            )
        }
        schedulePaletteAnalysis()
    }

    fun clearSelectedBuildInfo() {
        updateSelectedBuildInfo { KeyboardBuildInfo() }
    }

    fun applyBuildInfoToAll() {
        val info = uiState.value.selectedPhoto?.buildInfo ?: return
        _uiState.update { state ->
            state.copy(photos = state.photos.map { it.copy(buildInfo = info) })
        }
        rememberBuildInfo(info)
    }

    fun selectHousingPreset(preset: HousingPreset) {
        updateSelectedBuildInfo { info ->
            info.copy(
                housing = preset.name,
                logoId = presetRepository.logoIdForHousing(preset) ?: info.logoId,
                customLogoUri = null,
                logoDisabled = false,
            )
        }
    }

    fun selectSwitchPreset(preset: SwitchPreset) {
        updateSelectedBuildInfo { it.copy(switchName = preset.name) }
    }

    fun selectKeycapPreset(preset: KeycapPreset) {
        updateSelectedBuildInfo { it.copy(keycap = preset.name) }
    }

    fun selectTemplate(template: CardTemplate) {
        _uiState.update { it.copy(selectedTemplate = template) }
        if (uiState.value.settings.rememberLastTemplate) {
            updateSettings { it.copy(defaultTemplate = template) }
        }
    }

    fun updatePresetQuery(query: String) {
        _uiState.update { it.copy(presetQuery = query) }
    }

    fun saveBuildPreset(name: String) {
        val info = uiState.value.selectedPhoto?.buildInfo ?: return
        val limit = uiState.value.settings.recentInputLimit
        viewModelScope.launch(Dispatchers.IO) {
            buildPresetRepository.save(name, info)
            recentStore.addBuildInfo(info, limit)
            refreshPersistentData()
        }
    }

    fun applyBuildPreset(preset: BuildPreset) {
        updateBuildInfo(preset.buildInfo)
        rememberBuildInfo(preset.buildInfo)
    }

    fun deleteBuildPreset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            buildPresetRepository.delete(id)
            refreshPersistentData()
        }
    }

    fun removeRecentHousing(value: String) {
        removeRecentValue { recentStore.removeHousing(value) }
    }

    fun removeRecentSwitch(value: String) {
        removeRecentValue { recentStore.removeSwitch(value) }
    }

    fun removeRecentKeycap(value: String) {
        removeRecentValue { recentStore.removeKeycap(value) }
    }

    fun removeRecentNickname(value: String) {
        removeRecentValue { recentStore.removeNickname(value) }
    }

    private fun removeRecentValue(action: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            action()
            refreshPersistentData()
        }
    }

    fun shareExportedImage(image: ExportedImage) {
        val uri = Uri.parse(image.uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Keyxif 이미지 공유").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            getApplication<Application>().startActivity(chooser)
        }.onFailure {
            _uiState.update { it.copy(uiMessage = "공유할 앱을 열 수 없습니다.") }
        }
    }

    fun openExportedImage(image: ExportedImage) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(image.uri), "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            getApplication<Application>().startActivity(intent)
        }.onFailure {
            _uiState.update { it.copy(uiMessage = "이미지를 열 수 없습니다.") }
        }
    }

    fun removeExportedImageRecord(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            exportedImageRepository.remove(id)
        }
    }

    fun deleteExportedImageFile(image: ExportedImage) {
        deleteExportedImageFiles(listOf(image))
    }

    fun deleteExportedImageFiles(images: List<ExportedImage>) {
        if (images.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var deleted = 0
            images.forEach { image ->
                val didDelete = runCatching {
                    getApplication<Application>().contentResolver.delete(Uri.parse(image.uri), null, null) > 0
                }.getOrDefault(false)
                if (didDelete) deleted++
                exportedImageRepository.remove(image.id)
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        uiMessage = if (deleted == images.size) {
                            "이미지 ${images.size}개를 삭제했습니다."
                        } else {
                            "이미지 ${images.size}개를 목록에서 제거했습니다. 일부 파일은 삭제 권한이 없었습니다."
                        },
                    )
                }
            }
        }
    }

    fun deleteAllExportedImages() {
        deleteExportedImageFiles(uiState.value.exportedImages)
    }

    fun pruneMissingExportedImages() {
        viewModelScope.launch(Dispatchers.IO) {
            val removed = exportedImageRepository.pruneMissing()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(uiMessage = "접근 불가 항목 ${removed}개를 정리했습니다.") }
            }
        }
    }

    fun clearExportedImageRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            exportedImageRepository.clear()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(uiMessage = "완성 이미지 목록 기록을 초기화했습니다.") }
            }
        }
    }

    fun completeBuildInfo() {
        val infos = uiState.value.photos.map { it.buildInfo }.distinct()
        val limit = uiState.value.settings.recentInputLimit
        viewModelScope.launch(Dispatchers.IO) {
            infos.forEach { recentStore.addBuildInfo(it, limit) }
            refreshPersistentData()
            withContext(Dispatchers.Main) {
                navigateToStep(if (uiState.value.settings.showPaletteColors) AppStep.Palette else AppStep.Template)
            }
        }
    }

    fun housingOptions(query: String): List<PresetChoice<HousingPreset>> {
        return presetRepository.searchHousing(query, uiState.value.recentHousing)
    }

    fun switchOptions(query: String): List<PresetChoice<SwitchPreset>> {
        return presetRepository.searchSwitch(
            query = query,
            recentValues = uiState.value.recentSwitches,
            includePresets = uiState.value.settings.showSwitchPresets,
        )
    }

    fun keycapOptions(query: String): List<PresetChoice<KeycapPreset>> {
        return presetRepository.searchKeycap(query, uiState.value.recentKeycaps)
    }

    suspend fun renderPreviewBitmap(
        photoId: String,
        maxLongSide: Int = BitmapUtils.PREVIEW_LONG_SIDE_LIMIT,
    ) = withContext(Dispatchers.IO) {
        val state = uiState.value
        val photo = state.photos.firstOrNull { it.id == photoId } ?: return@withContext null
        // 그리드에서 여러 셀이 한 번에 렌더링을 요청하면 대형 비트맵이 동시에 올라와
        // 메모리 부족으로 실패하기 쉬우므로 동시 렌더링 수를 제한한다.
        previewRenderSemaphore.withPermit {
            renderer.render(
                context = getApplication(),
                photo = photo,
                template = state.selectedTemplate,
                settings = state.settings,
                maxLongSide = maxLongSide,
            )
        }
    }

    suspend fun renderSourcePreviewBitmap(
        photoId: String,
        maxLongSide: Int = BitmapUtils.PREVIEW_LONG_SIDE_LIMIT,
    ) = withContext(Dispatchers.IO) {
        val photo = uiState.value.photos.firstOrNull { it.id == photoId } ?: return@withContext null
        BitmapUtils.decodeOrientedBitmap(
            context = getApplication(),
            uri = photo.uri,
            maxLongSide = maxLongSide,
        )
    }

    fun savePhoto(photoId: String) {
        enqueueExport(listOf(photoId))
    }

    fun savePhotos(photoIds: List<String>) {
        enqueueExport(photoIds)
    }

    fun saveAll() {
        enqueueExport(uiState.value.photos.map { it.id })
    }

    fun showExportMessage(message: String) {
        _uiState.update { it.copy(exportProgress = it.exportProgress.copy(message = message)) }
    }

    private fun enqueueExport(photoIds: List<String>) {
        if (photoIds.isEmpty()) {
            _uiState.update { it.copy(uiMessage = "저장할 사진이 없습니다.") }
            return
        }
        viewModelScope.launch {
            val state = uiState.value
            val photos = photoIds.mapNotNull { id -> state.photos.firstOrNull { it.id == id } }
            if (photos.isEmpty()) {
                _uiState.update { it.copy(uiMessage = "저장할 사진을 찾을 수 없습니다.") }
                return@launch
            }
            val directoryLabel = outputDirectoryLabel(state.settings)
            _uiState.update {
                it.copy(
                    exportProgress = ExportProgress(
                        isSaving = true,
                        total = photos.size,
                        message = "저장 작업을 준비하는 중입니다.",
                    ),
                    photos = it.photos.map { photo ->
                        if (photos.any { target -> target.id == photo.id }) {
                            photo.copy(renderStatus = RenderStatus.Rendering, errorMessage = null)
                        } else {
                            photo
                        }
                    },
                )
            }

            val payloadFile = runCatching {
                prepareExportPayload(photos, state.selectedTemplate, state.settings)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        exportProgress = ExportProgress(
                            isSaving = false,
                            total = photos.size,
                            failureCount = photos.size,
                            message = error.message ?: "저장 작업을 준비할 수 없습니다.",
                        ),
                    )
                }
                return@launch
            }

            val request = ExportWorker.request(payloadFile.absolutePath)
            workManager.enqueueUniqueWork(
                ExportWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            _uiState.update {
                it.copy(
                    exportProgress = it.exportProgress.copy(
                        isSaving = true,
                        message = "${directoryLabel}에 백그라운드 저장을 시작했습니다.",
                    ),
                )
            }
        }
    }

    private suspend fun prepareExportPayload(
        photos: List<PhotoItem>,
        template: CardTemplate,
        settings: AppSettings,
    ): File = withContext(Dispatchers.IO) {
        val workId = UUID.randomUUID().toString()
        val workDir = File(getApplication<Application>().cacheDir, "keyxif_export/$workId").apply {
            mkdirs()
        }
        val cachedPhotos = photos.mapIndexed { index, photo ->
            val sourceUri = copyUriToExportCache(
                uri = photo.uri,
                target = File(workDir, "photo_${index}.bin"),
            )
            val cachedLogoUri = photo.buildInfo.customLogoUri?.let { logoUri ->
                copyUriToExportCache(
                    uri = logoUri,
                    target = File(workDir, "logo_${index}.bin"),
                )
            }
            photo.copy(
                uri = sourceUri,
                buildInfo = photo.buildInfo.copy(customLogoUri = cachedLogoUri),
                renderStatus = RenderStatus.Idle,
                errorMessage = null,
            )
        }
        val payload = ExportWorkPayload(
            photos = cachedPhotos,
            template = template,
            settings = settings,
        )
        File(workDir, "request.json").also { file ->
            file.writeText(ExportWorkPayloadCodec.encode(payload).toString(), Charsets.UTF_8)
        }
    }

    private fun copyUriToExportCache(
        uri: Uri,
        target: File,
    ): Uri {
        openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("이미지 파일을 읽을 수 없습니다.")
        return target.toUri()
    }

    private fun copyUriToSourceStore(
        uri: Uri,
        displayName: String,
    ): Uri {
        val sourceDir = File(getApplication<Application>().filesDir, "keyxif_sources").apply {
            mkdirs()
        }
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?: "img"
        val safeName = FileNameUtils.sanitize(displayName.substringBeforeLast('.', displayName))
            .ifBlank { "photo" }
        val target = File(sourceDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}_${safeName}.$extension")
        openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("사진을 앱 내부 저장소로 복사할 수 없습니다.")
        return target.toUri()
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let(::File)?.inputStream()
        } else {
            getApplication<Application>().contentResolver.openInputStream(uri)
        }
    }

    private fun deleteLocalPhotoFile(photo: PhotoItem) {
        if (photo.uri.scheme != "file") return
        val sourceRoot = runCatching {
            File(getApplication<Application>().filesDir, "keyxif_sources").canonicalFile
        }.getOrNull() ?: return
        val target = runCatching { photo.uri.path?.let(::File)?.canonicalFile }.getOrNull() ?: return
        if (target.path.startsWith(sourceRoot.path)) {
            runCatching { target.delete() }
        }
    }

    private fun observeExportWork() {
        viewModelScope.launch {
            while (true) {
                val infos = withContext(Dispatchers.IO) {
                    runCatching {
                        workManager.getWorkInfosForUniqueWork(ExportWorker.UNIQUE_WORK_NAME).get()
                    }.getOrDefault(emptyList())
                }
                val active = infos.firstOrNull {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
                val finished = infos.firstOrNull { it.state.isFinished }
                when {
                    active != null -> applyActiveWorkInfo(active)
                    finished != null -> applyFinishedWorkInfo(finished)
                }
                delay(if (active != null) 500L else 1500L)
            }
        }
    }

    private fun observeUpdateDownloadWork() {
        viewModelScope.launch {
            absorbCompletedUpdateDownloadWorkOnStart()
            while (true) {
                val infos = withContext(Dispatchers.IO) {
                    runCatching {
                        workManager.getWorkInfosForUniqueWork(UpdateDownloadWorker.UNIQUE_WORK_NAME).get()
                    }.getOrDefault(emptyList())
                }
                val active = infos.firstOrNull {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
                val finished = infos.firstOrNull { it.state.isFinished }
                when {
                    active != null -> {
                        val progress = active.progress.getInt(UpdateDownloadWorker.KEY_PROGRESS_PERCENT, -1)
                        _uiState.update {
                            it.copy(
                                updateDownloadState = it.updateDownloadState.copy(
                                    isDownloading = true,
                                    progressPercent = progress.takeIf { value -> value >= 0 },
                                    errorMessage = null,
                                ),
                            )
                        }
                    }
                    finished != null -> applyFinishedUpdateDownload(finished)
                }
                delay(if (active != null) 500L else 1500L)
            }
        }
    }

    private suspend fun absorbCompletedUpdateDownloadWorkOnStart() {
        val finished = withContext(Dispatchers.IO) {
            runCatching {
                workManager.getWorkInfosForUniqueWork(UpdateDownloadWorker.UNIQUE_WORK_NAME)
                    .get()
                    .firstOrNull { it.state.isFinished }
            }.getOrNull()
        } ?: return
        lastCompletedUpdateWorkId = finished.id
        if (finished.state == WorkInfo.State.SUCCEEDED) {
            val apkPath = finished.outputData.getString(UpdateDownloadWorker.KEY_APK_PATH)
            if (!apkPath.isNullOrBlank() && File(apkPath).exists()) {
                _uiState.update {
                    it.copy(
                        updateDownloadState = UpdateDownloadState(
                            isDownloading = false,
                            progressPercent = 100,
                            downloadedApkPath = apkPath,
                        ),
                    )
                }
            }
        }
    }

    private fun applyFinishedUpdateDownload(info: WorkInfo) {
        if (lastCompletedUpdateWorkId == info.id) return
        lastCompletedUpdateWorkId = info.id
        val output = info.outputData
        val apkPath = output.getString(UpdateDownloadWorker.KEY_APK_PATH)
        val error = output.getString(UpdateDownloadWorker.KEY_ERROR_MESSAGE)
        if (info.state == WorkInfo.State.SUCCEEDED && !apkPath.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    updateDownloadState = UpdateDownloadState(
                        isDownloading = false,
                        progressPercent = 100,
                        downloadedApkPath = apkPath,
                    ),
                    uiMessage = "업데이트 APK 다운로드가 완료되었습니다.",
                )
            }
            installDownloadedUpdate(apkPath)
        } else if (info.state == WorkInfo.State.FAILED || info.state == WorkInfo.State.CANCELLED) {
            _uiState.update {
                it.copy(
                    updateDownloadState = UpdateDownloadState(
                        isDownloading = false,
                        errorMessage = error ?: "업데이트 다운로드가 실패했습니다.",
                    ),
                    uiMessage = error ?: "업데이트 다운로드가 실패했습니다.",
                )
            }
        }
    }

    private fun applyActiveWorkInfo(info: WorkInfo) {
        val progress = info.progress
        val current = progress.getInt(ExportWorker.KEY_CURRENT, 0)
        val total = progress.getInt(ExportWorker.KEY_TOTAL, 0)
        val success = progress.getInt(ExportWorker.KEY_SUCCESS_COUNT, 0)
        val failure = progress.getInt(ExportWorker.KEY_FAILURE_COUNT, 0)
        val message = progress.getString(ExportWorker.KEY_MESSAGE) ?: "Keyxif 저장 중"
        val currentPhotoId = progress.getString(ExportWorker.KEY_CURRENT_PHOTO_ID)
        _uiState.update { state ->
            state.copy(
                exportProgress = ExportProgress(
                    isSaving = true,
                    current = current,
                    total = total,
                    successCount = success,
                    failureCount = failure,
                    message = message,
                ),
                photos = state.photos.map { photo ->
                    if (photo.id == currentPhotoId) {
                        photo.copy(renderStatus = RenderStatus.Rendering, errorMessage = null)
                    } else {
                        photo
                    }
                },
            )
        }
    }

    private fun applyFinishedWorkInfo(info: WorkInfo) {
        if (lastCompletedExportWorkId == info.id) return
        lastCompletedExportWorkId = info.id

        val output = info.outputData
        val total = output.getInt(ExportWorker.KEY_TOTAL, 0)
        val current = output.getInt(ExportWorker.KEY_CURRENT, total)
        val success = output.getInt(ExportWorker.KEY_SUCCESS_COUNT, 0)
        val failure = output.getInt(ExportWorker.KEY_FAILURE_COUNT, 0)
        val message = output.getString(ExportWorker.KEY_MESSAGE)
            ?: if (info.state == WorkInfo.State.SUCCEEDED) "저장이 완료되었습니다." else "저장 작업이 실패했습니다."
        val photoIds = output.getString(ExportWorker.KEY_PHOTO_IDS)?.toStringList().orEmpty()
        val failedIds = output.getString(ExportWorker.KEY_FAILED_IDS)?.toStringList().orEmpty().toSet()
        val savedUri = output.getString(ExportWorker.KEY_SAVED_URI)?.let(Uri::parse)

        _uiState.update { state ->
            state.copy(
                exportProgress = ExportProgress(
                    isSaving = false,
                    current = current,
                    total = total,
                    successCount = success,
                    failureCount = failure,
                    message = if (state.settings.showSaveToast) message else null,
                ),
                photos = state.photos.map { photo ->
                    when {
                        photo.id in failedIds -> photo.copy(
                            renderStatus = RenderStatus.Error,
                            errorMessage = "백그라운드 저장 실패",
                        )
                        photoIds.isEmpty() || photo.id in photoIds -> photo.copy(
                            renderStatus = if (info.state == WorkInfo.State.SUCCEEDED && photo.id !in failedIds) {
                                RenderStatus.Saved
                            } else {
                                RenderStatus.Error
                            },
                            errorMessage = if (info.state == WorkInfo.State.SUCCEEDED && photo.id !in failedIds) null else message,
                        )
                        else -> photo
                    }
                },
            )
        }
        if (uiState.value.settings.openGalleryAfterSave) {
            savedUri?.let(::openSavedImage)
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { state ->
                    val defaultTemplateChangedInSettings = state.isSettingsOpen &&
                        state.settings.defaultTemplate != settings.defaultTemplate
                    state.copy(
                        settings = settings,
                        selectedTemplate = if (!appliedInitialSettings || defaultTemplateChangedInSettings) {
                            settings.defaultTemplate
                        } else {
                            state.selectedTemplate
                        },
                    )
                }
                appliedInitialSettings = true
                maybeCheckUpdateOnLaunch(settings)
                schedulePaletteAnalysis(settings)
            }
        }
    }

    private fun observeExportedImages() {
        viewModelScope.launch {
            exportedImageRepository.exportedImagesFlow.collect { images ->
                _uiState.update { it.copy(exportedImages = images) }
            }
        }
    }

    private fun maybeCheckUpdateOnLaunch(settings: AppSettings) {
        if (autoUpdateCheckAttempted) return
        autoUpdateCheckAttempted = true
        _uiState.update {
            it.copy(
                updateCheckState = it.updateCheckState.copy(
                    lastCheckedAt = updateRepository.lastCheckedAt(),
                ),
            )
        }
        if (updateRepository.isPlaceholderUrl(effectiveUpdateJsonUrl(settings))) return
        if (!updateRepository.shouldAutoCheck()) return
        checkForUpdate(manual = false, settingsOverride = settings)
    }

    private fun schedulePaletteAnalysis(settingsOverride: AppSettings = uiState.value.settings) {
        if (!settingsOverride.showPaletteColors) return
        paletteAnalysisJob?.cancel()
        _uiState.update { state ->
            state.copy(photos = state.photos.map { photo ->
                if (photo.analysisResult.isAnalyzing) photo.copy(analysisResult = photo.analysisResult.copy(isAnalyzing = false)) else photo
            })
        }
        paletteAnalysisJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val settings = uiState.value.settings
                if (!settings.showPaletteColors) break
                val target = uiState.value.photos.firstOrNull { photo ->
                    photo.needsPaletteAnalysis()
                } ?: break
                val request = target.analysisResult

                _uiState.update { state ->
                    state.copy(
                        photos = state.photos.map { photo ->
                            if (photo.id == target.id) {
                                photo.copy(
                                    analysisResult = photo.analysisResult.copy(
                                        isAnalyzing = true,
                                        errorMessage = null,
                                    ),
                                )
                            } else {
                                photo
                            }
                        },
                    )
                }

                val result = runCatching {
                    paletteAnalyzer.analyze(
                        context = getApplication(),
                        uri = target.uri,
                        mode = request.analysisMode,
                        maxColors = 5,
                        centerCropRatio = request.analysisCenterCropRatio,
                        rectNormalized = request.analysisRectNormalized,
                        maskStrokes = request.paintedMaskStrokes,
                    )
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val analyzedAt = System.currentTimeMillis()
                _uiState.update { state ->
                    state.copy(
                        photos = state.photos.map { photo ->
                            if (photo.id == target.id) {
                                if (photo.analysisResult.analysisMode != request.analysisMode ||
                                    photo.analysisResult.analysisRectNormalized != request.analysisRectNormalized ||
                                    photo.analysisResult.paintedMaskStrokes != request.paintedMaskStrokes
                                ) return@map photo
                                val colors = result.getOrDefault(emptyList())
                                photo.copy(
                                    analysisResult = photo.analysisResult.copy(
                                        paletteColors = colors,
                                        analyzedAt = analyzedAt,
                                        isAnalyzing = false,
                                        errorMessage = result.exceptionOrNull()?.message
                                            ?: if (colors.isEmpty()) "대표 색상을 찾지 못했습니다." else null,
                                    ),
                                )
                            } else {
                                photo
                            }
                        },
                    )
                }
                delay(40L)
            }
        }
    }

    private fun PhotoItem.needsPaletteAnalysis(): Boolean =
        !analysisResult.isAnalyzing && analysisResult.analyzedAt <= 0L &&
            (analysisResult.analysisMode != PaletteAnalysisMode.PaintedMask || analysisResult.paintedMaskStrokes.isNotEmpty())

    private fun checkForUpdate(
        manual: Boolean,
        settingsOverride: AppSettings? = null,
    ) {
        val settings = settingsOverride ?: uiState.value.settings
        val updateJsonUrl = effectiveUpdateJsonUrl(settings)
        if (updateRepository.isPlaceholderUrl(updateJsonUrl)) {
            if (manual) {
                _uiState.update {
                    it.copy(
                        updateCheckState = it.updateCheckState.copy(
                            isChecking = false,
                            lastCheckedAt = updateRepository.lastCheckedAt(),
                            statusMessage = null,
                            errorMessage = "업데이트 JSON URL이 설정되지 않았습니다.",
                        ),
                        uiMessage = "업데이트 JSON URL이 설정되지 않았습니다.",
                    )
                }
            }
            return
        }

        if (!manual && !updateRepository.shouldAutoCheck()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateCheckState = it.updateCheckState.copy(
                        isChecking = true,
                        statusMessage = null,
                        errorMessage = null,
                        lastCheckedAt = updateRepository.lastCheckedAt(),
                    ),
                )
            }
            val result = runCatching { updateRepository.fetchUpdateInfo(updateJsonUrl) }
            val checkedAt = System.currentTimeMillis()
            updateRepository.markChecked(checkedAt)
            result
                .onSuccess { info ->
                    val requiresUpdate = info.latestVersionCode > BuildConfig.VERSION_CODE ||
                        info.minRequiredVersionCode > BuildConfig.VERSION_CODE
                    _uiState.update { state ->
                        state.copy(
                            updateCheckState = UpdateCheckState(
                                isChecking = false,
                                latestInfo = info,
                                lastCheckedAt = checkedAt,
                                statusMessage = if (requiresUpdate) {
                                    "새 버전 ${info.latestVersionName} (${info.latestVersionCode})을 사용할 수 있습니다."
                                } else {
                                    "현재 최신 버전입니다. (${BuildConfig.VERSION_NAME})"
                                },
                                errorMessage = null,
                            ),
                            showUpdateDialog = requiresUpdate,
                            uiMessage = if (manual && !requiresUpdate) "현재 최신 버전입니다." else state.uiMessage,
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "업데이트 확인에 실패했습니다."
                    _uiState.update { state ->
                        state.copy(
                            updateCheckState = state.updateCheckState.copy(
                                isChecking = false,
                                lastCheckedAt = checkedAt,
                                statusMessage = null,
                                errorMessage = "업데이트 확인 실패: $message",
                            ),
                            uiMessage = if (manual) "업데이트 확인에 실패했습니다." else state.uiMessage,
                        )
                    }
                }
        }
    }

    private fun effectiveUpdateJsonUrl(settings: AppSettings): String {
        return settings.updateJsonUrl
            .takeIf { BuildConfig.DEBUG && it.isNotBlank() }
            ?: BuildConfig.UPDATE_JSON_URL
    }

    private fun loadDraftOnStart() {
        viewModelScope.launch(Dispatchers.IO) {
            val draft = draftSessionRepository.getDraft()
            val persistedSettings = settingsRepository.settingsFlow.first()
            withContext(Dispatchers.Main) {
                val settings = persistedSettings
                if (draft == null || draft.photoItems.isEmpty() || !settings.autoRestoreDraftSession) {
                    autoSaveReady = true
                    return@withContext
                }
                pendingDraftSession = draft
                if (settings.askBeforeRestoreDraft) {
                    _uiState.update {
                        it.copy(
                            showDraftRestorePrompt = true,
                            draftLastUpdatedAt = draft.lastUpdatedAt,
                        )
                    }
                } else {
                    restoreDraftSession()
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeAutoSaveDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            uiState
                .drop(1)
                .debounce(700)
                .collect { state ->
                    if (!autoSaveReady || state.showDraftRestorePrompt || !state.settings.autoRestoreDraftSession) return@collect
                    if (state.photos.isEmpty()) {
                        draftSessionRepository.clear()
                    } else {
                        draftSessionRepository.save(state.toDraftSession())
                    }
                }
        }
    }

    private fun openSavedImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            getApplication<Application>().startActivity(intent)
        }
    }

    private fun installDownloadedUpdate(apkPath: String) {
        val app = getApplication<Application>()
        val apkFile = File(apkPath)
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            _uiState.update { it.copy(uiMessage = "다운로드된 APK 파일을 찾을 수 없습니다.") }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            _uiState.update {
                it.copy(
                    showUnknownSourcesDialog = true,
                    updateDownloadState = it.updateDownloadState.copy(downloadedApkPath = apkPath),
                )
            }
            return
        }
        val uri = FileProvider.getUriForFile(
            app,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newUri(app.contentResolver, "Keyxif update APK", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        grantApkReadPermission(app, uri, intent)
        val opened = runCatching {
            app.startActivity(intent)
        }.onFailure { error ->
            _uiState.update {
                it.copy(uiMessage = "설치 화면을 열 수 없습니다: ${error.message ?: "알 수 없는 오류"}")
            }
        }.isSuccess
        if (!opened) {
            val fallbackUrl = uiState.value.updateCheckState.latestInfo?.apkUrl.orEmpty()
            if (fallbackUrl.isNotBlank()) {
                openUrl(fallbackUrl, "설치 화면을 열 수 없어 브라우저 다운로드로 전환합니다.")
            }
        }
    }

    private fun grantApkReadPermission(
        app: Application,
        uri: Uri,
        intent: Intent,
    ) {
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        app.packageManager.queryIntentActivities(intent, flags).forEach { resolveInfo ->
            runCatching {
                app.grantUriPermission(
                    resolveInfo.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private fun openUrl(
        url: String,
        failureMessage: String,
    ) {
        if (url.isBlank()) {
            _uiState.update { it.copy(uiMessage = failureMessage) }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            getApplication<Application>().startActivity(intent)
        }.isSuccess
        if (!opened) {
            _uiState.update { it.copy(uiMessage = failureMessage) }
        }
    }

    private fun copySupportEmailToClipboard() {
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Keyxif 문의 이메일", SUPPORT_EMAIL),
        )
    }

    private fun navigationBlockMessage(
        target: AppStep,
        state: KeyxifUiState,
    ): String? {
        if (target == AppStep.Photos) return null
        if (state.photos.isEmpty()) return "먼저 사진을 추가해 주세요."
        return null
    }

    private fun navigationWarningMessage(
        target: AppStep,
        state: KeyxifUiState,
    ): String? {
        return null
    }

    private fun updateSelectedBuildInfo(transform: (KeyboardBuildInfo) -> KeyboardBuildInfo) {
        val photo = uiState.value.selectedPhoto ?: return
        updatePhoto(photo.id) { it.copy(buildInfo = transform(it.buildInfo)) }
    }

    private fun updatePhoto(
        photoId: String,
        transform: (PhotoItem) -> PhotoItem,
    ) {
        _uiState.update { state ->
            state.copy(
                photos = state.photos.map { photo ->
                    if (photo.id == photoId) transform(photo) else photo
                },
            )
        }
    }

    private fun rememberBuildInfo(info: KeyboardBuildInfo) {
        val limit = uiState.value.settings.recentInputLimit
        viewModelScope.launch(Dispatchers.IO) {
            recentStore.addBuildInfo(info, limit)
            refreshPersistentData()
        }
    }

    private fun refreshPersistentData() {
        _uiState.update {
            it.copy(
                buildPresets = buildPresetRepository.getAll(),
                recentHousing = recentStore.recentHousing(),
                recentSwitches = recentStore.recentSwitches(),
                recentKeycaps = recentStore.recentKeycaps(),
                recentNicknames = recentStore.recentNicknames(),
            )
        }
    }

    private fun outputDirectoryLabel(settings: AppSettings): String {
        val directory = FileNameUtils.sanitize(settings.saveDirectoryName).ifBlank { "Keyxif" }
        return "Pictures/$directory"
    }

    private fun KeyxifUiState.toDraftSession(): DraftSession {
        return DraftSession(
            photoItems = photos,
            selectedTemplate = selectedTemplate,
            currentStep = currentStep,
            selectedPhotoId = selectedPhotoId,
            settings = settings,
            lastUpdatedAt = System.currentTimeMillis(),
        )
    }

    private fun validatedRestoredPhoto(photo: PhotoItem): PhotoItem {
        val canOpen = runCatching {
            openInputStream(photo.uri)?.use { }
        }.isSuccess
        return if (canOpen) {
            photo.copy(
                analysisResult = photo.analysisResult.copy(isAnalyzing = false),
                renderStatus = RenderStatus.Idle,
                errorMessage = null,
            )
        } else {
            photo.copy(
                analysisResult = photo.analysisResult.copy(isAnalyzing = false),
                renderStatus = RenderStatus.Error,
                errorMessage = "사진 접근 권한이 만료되었습니다.",
            )
        }
    }

    private fun previousStep(state: KeyxifUiState): AppStep? = when (state.currentStep) {
        AppStep.Photos -> null
        AppStep.BuildInfo -> AppStep.Photos
        AppStep.Palette -> AppStep.BuildInfo
        AppStep.Template -> if (state.settings.showPaletteColors) AppStep.Palette else AppStep.BuildInfo
        AppStep.Export -> AppStep.Template
    }

    private fun normalizeStep(step: AppStep): AppStep {
        return when (step) {
            AppStep.Photos,
            AppStep.BuildInfo,
            AppStep.Palette,
            AppStep.Template,
            AppStep.Export -> step
        }
    }

    private fun normalizeStepForSettings(
        step: AppStep,
        settings: AppSettings,
    ): AppStep {
        return if (step == AppStep.Palette && !settings.showPaletteColors) AppStep.Template else step
    }

    private fun displayNameFor(
        application: Application,
        uri: Uri,
    ): String {
        val resolver = application.contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "사진"
    }

    private fun String.toStringList(): List<String> {
        return runCatching {
            val array = JSONArray(this)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getString(index))
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val SUPPORT_EMAIL = "typenews902@gmail.com"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
