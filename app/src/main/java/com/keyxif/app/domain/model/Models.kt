package com.keyxif.app.domain.model

import android.net.Uri

data class KeyboardBuildInfo(
    val housing: String = "",
    val switchName: String = "",
    val plate: String = "",
    val mount: String = "",
    val keycap: String = "",
    val nickname: String = "",
    val logoId: String? = null,
    val customLogoUri: Uri? = null,
)

data class VendorPreset(
    val id: String,
    val name: String,
    val logoId: String? = null,
    val aliases: List<String> = emptyList(),
)

data class HousingPreset(
    val id: String,
    val name: String,
    val vendorId: String? = null,
    val vendor: String? = null,
    val designer: String? = null,
    val logoId: String? = null,
    val aliases: List<String> = emptyList(),
)

data class SwitchPreset(
    val id: String,
    val name: String,
    val manufacturer: String? = null,
    val aliases: List<String> = emptyList(),
)

data class KeycapPreset(
    val id: String,
    val name: String,
    val manufacturer: String? = null,
    val aliases: List<String> = emptyList(),
)

data class LogoPreset(
    val id: String,
    val name: String,
    val drawableResId: Int? = null,
    val whiteDrawableResId: Int? = null,
    val blackDrawableResId: Int? = null,
    val aliases: List<String> = emptyList(),
)

data class PhotoAnalysisResult(
    val paletteColors: List<Int> = emptyList(),
    val analyzedAt: Long = 0L,
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
    val analysisMode: PaletteAnalysisMode? = null,
)

data class PhotoItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val cropState: CropState = CropState(),
    val buildInfo: KeyboardBuildInfo = KeyboardBuildInfo(),
    val analysisResult: PhotoAnalysisResult = PhotoAnalysisResult(),
    val renderStatus: RenderStatus = RenderStatus.Idle,
    val errorMessage: String? = null,
)

data class DraftSession(
    val photoItems: List<PhotoItem> = emptyList(),
    val selectedTemplate: CardTemplate = CardTemplate.ClassicFrame,
    val currentStep: AppStep = AppStep.Photos,
    val selectedPhotoId: String? = null,
    val settings: AppSettings = AppSettings(),
    val lastUpdatedAt: Long = 0L,
)

data class BuildPreset(
    val id: String,
    val presetName: String,
    val buildInfo: KeyboardBuildInfo,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ExportedImage(
    val id: String,
    val uri: String,
    val fileName: String,
    val createdAt: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val templateName: String,
    val housing: String? = null,
    val switchName: String? = null,
    val keycap: String? = null,
    val nickname: String? = null,
    val paletteColors: List<Int> = emptyList(),
)

data class CropState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

data class ExportProgress(
    val isSaving: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val message: String? = null,
)

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minRequiredVersionCode: Int = 1,
    val title: String = "새 버전이 있습니다",
    val message: String = "",
    val apkUrl: String,
    val releaseNoteUrl: String? = null,
    val forceUpdate: Boolean = false,
)

data class UpdateCheckState(
    val isChecking: Boolean = false,
    val latestInfo: UpdateInfo? = null,
    val lastCheckedAt: Long? = null,
    val errorMessage: String? = null,
)

data class UpdateDownloadState(
    val isDownloading: Boolean = false,
    val progressPercent: Int? = null,
    val downloadedApkPath: String? = null,
    val errorMessage: String? = null,
)

data class AppSettings(
    val webpQuality: Int = 92,
    val outputFormat: OutputFormat = OutputFormat.WEBP,
    val keepOriginalResolution: Boolean = true,
    val maxLongSidePx: Int? = null,
    val fileNameRule: FileNameRule = FileNameRule.KEYXIF_INDEX,
    val saveDirectoryName: String = "Keyxif",
    val openGalleryAfterSave: Boolean = false,
    val showSaveToast: Boolean = true,
    val skipFailedOnBatchSave: Boolean = true,
    val rememberLastTemplate: Boolean = true,
    val rememberLastNickname: Boolean = true,
    val recentInputLimit: Int = 20,
    val copyPreviousBuildInfoOnAdd: Boolean = false,
    val defaultTemplate: CardTemplate = CardTemplate.ClassicFrame,
    val useCurrentPhotoForTemplatePreview: Boolean = true,
    val protectCenterAreaForOverlay: Boolean = true,
    val textScale: Float = 1.0f,
    val qualityPreset: QualityPreset = QualityPreset.Recommended,
    val autoRestoreDraftSession: Boolean = true,
    val askBeforeRestoreDraft: Boolean = true,
    val nicknameStyle: NicknameStyle = NicknameStyle.Plain,
    val nicknameEmphasis: Float = 1.1f,
    val showSwitchPresets: Boolean = true,
    val enableExportPreviewZoom: Boolean = true,
    val showBuildInfoInPlainExport: Boolean = false,
    val updateJsonUrl: String = "",
    val showPaletteColors: Boolean = true,
    val paletteColorCount: Int = 4,
    val paletteAnalysisMode: PaletteAnalysisMode = PaletteAnalysisMode.CenterCrop,
    val autoSelectLogoContrastVariant: Boolean = true,
)

enum class OutputFormat {
    WEBP,
    PNG,
}

enum class FileNameRule {
    KEYXIF_INDEX,
    HOUSING_INDEX,
    NICKNAME_INDEX,
    HOUSING_KEYCAP_INDEX,
}

enum class QualityPreset {
    HighCompression,
    Recommended,
    Balanced,
    HighQuality,
    Maximum,
    Custom,
}

enum class NicknameStyle {
    Plain,
    AtPrefix,
    Credit,
}

enum class PaletteAnalysisMode {
    FullImage,
    CenterCrop,
}

enum class TemplateBackgroundTone {
    Light,
    Dark,
    Mixed,
}

enum class CardTemplate {
    PlainExport,
    ClassicFrame,
    MinimalCaption,
    BottomSpecBar,
    CornerMark,
    PosterMargin,
    DarkGlassStrip,
    SideSpecRail,
    TopNameplate,
    MuseumMat,
    CompactTicket,
    CleanSignature,
}

enum class RenderStatus {
    Idle,
    Rendering,
    Saved,
    Error,
}

enum class AppStep {
    Photos,
    BuildInfo,
    Template,
    Export,
}

fun CardTemplate.displayName(): String = when (this) {
    CardTemplate.ClassicFrame -> "클래식 프레임"
    CardTemplate.MinimalCaption -> "미니멀 캡션"
    CardTemplate.BottomSpecBar -> "하단 스펙 바"
    CardTemplate.CornerMark -> "코너 마크"
    CardTemplate.PosterMargin -> "포스터 마진"
    CardTemplate.DarkGlassStrip -> "다크 글래스 스트립"
    CardTemplate.SideSpecRail -> "사이드 스펙 레일"
    CardTemplate.TopNameplate -> "상단 네임플레이트"
    CardTemplate.MuseumMat -> "뮤지엄 매트"
    CardTemplate.CompactTicket -> "컴팩트 티켓"
    CardTemplate.CleanSignature -> "클린 시그니처"
    CardTemplate.PlainExport -> "Plain Export"
}

fun CardTemplate.shortDescription(): String = when (this) {
    CardTemplate.ClassicFrame -> "사진 밖의 얇은 바에 전체 빌드 정보를 정돈합니다."
    CardTemplate.MinimalCaption -> "밝은 하단 여백에 핵심 정보만 담습니다."
    CardTemplate.BottomSpecBar -> "아주 얇은 하단 바에 주요 스펙을 배열합니다."
    CardTemplate.CornerMark -> "사진 모서리에 로고와 하우징만 작게 표시합니다."
    CardTemplate.PosterMargin -> "사진집 같은 프레임과 하단 여백을 만듭니다."
    CardTemplate.DarkGlassStrip -> "하단 가장자리에 얇은 반투명 3열 정보를 표시합니다."
    CardTemplate.SideSpecRail -> "오른쪽 외부 레일에 로고와 세부 스펙을 세로로 배치합니다."
    CardTemplate.TopNameplate -> "사진 위쪽 여백에 큰 이름표와 로고를 올립니다."
    CardTemplate.MuseumMat -> "넓은 매트 여백과 작품 라벨 같은 정보를 만듭니다."
    CardTemplate.CompactTicket -> "하단 티켓형 라벨에 주요 정보를 작게 모읍니다."
    CardTemplate.CleanSignature -> "하단 여백에 하우징, 키캡, 닉네임을 서명처럼 정리합니다."
    CardTemplate.PlainExport -> "꾸밈 없이 원본 사진을 WEBP로 저장합니다."
}

fun AppStep.displayName(): String = when (this) {
    AppStep.Photos -> "사진"
    AppStep.BuildInfo -> "정보"
    AppStep.Template -> "템플릿"
    AppStep.Export -> "저장"
}

fun OutputFormat.displayName(): String = when (this) {
    OutputFormat.WEBP -> "WEBP"
    OutputFormat.PNG -> "PNG"
}

fun FileNameRule.displayName(): String = when (this) {
    FileNameRule.KEYXIF_INDEX -> "Keyxif_번호"
    FileNameRule.HOUSING_INDEX -> "Housing_번호"
    FileNameRule.NICKNAME_INDEX -> "Nickname_번호"
    FileNameRule.HOUSING_KEYCAP_INDEX -> "Housing_Keycap_번호"
}

fun QualityPreset.displayName(): String = when (this) {
    QualityPreset.HighCompression -> "고압축"
    QualityPreset.Recommended -> "권장"
    QualityPreset.Balanced -> "균형"
    QualityPreset.HighQuality -> "고화질"
    QualityPreset.Maximum -> "최고화질"
    QualityPreset.Custom -> "사용자 지정"
}

fun NicknameStyle.displayName(): String = when (this) {
    NicknameStyle.Plain -> "그대로"
    NicknameStyle.AtPrefix -> "@닉네임"
    NicknameStyle.Credit -> "Credit"
}

fun PaletteAnalysisMode.displayName(): String = when (this) {
    PaletteAnalysisMode.FullImage -> "전체 이미지"
    PaletteAnalysisMode.CenterCrop -> "중앙 영역"
}

fun QualityPreset.applyTo(settings: AppSettings): AppSettings = when (this) {
    QualityPreset.HighCompression -> settings.copy(
        webpQuality = 80,
        keepOriginalResolution = false,
        maxLongSidePx = 1920,
        qualityPreset = this,
    )
    QualityPreset.Recommended -> settings.copy(
        webpQuality = 88,
        keepOriginalResolution = false,
        maxLongSidePx = 2048,
        qualityPreset = this,
    )
    QualityPreset.Balanced -> settings.copy(
        webpQuality = 92,
        keepOriginalResolution = true,
        maxLongSidePx = null,
        qualityPreset = this,
    )
    QualityPreset.HighQuality -> settings.copy(
        webpQuality = 96,
        keepOriginalResolution = true,
        maxLongSidePx = null,
        qualityPreset = this,
    )
    QualityPreset.Maximum -> settings.copy(
        webpQuality = 100,
        keepOriginalResolution = true,
        maxLongSidePx = null,
        qualityPreset = this,
    )
    QualityPreset.Custom -> settings.copy(qualityPreset = this)
}
