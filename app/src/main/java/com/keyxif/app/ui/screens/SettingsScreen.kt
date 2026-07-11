package com.keyxif.app.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keyxif.app.BuildConfig
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.FileNameRule
import com.keyxif.app.domain.model.NicknameStyle
import com.keyxif.app.domain.model.OutputFormat
import com.keyxif.app.domain.model.PaletteAnalysisMode
import com.keyxif.app.domain.model.QualityPreset
import com.keyxif.app.domain.model.UpdateCheckState
import com.keyxif.app.domain.model.UpdateDownloadState
import com.keyxif.app.domain.model.applyTo
import com.keyxif.app.domain.model.displayName
import com.keyxif.app.ui.components.ClearableTextField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    updateCheckState: UpdateCheckState,
    updateDownloadState: UpdateDownloadState,
    exportedImageCount: Int,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallDownloadedUpdate: () -> Unit,
    onPruneMissingExportedImages: () -> Unit,
    onClearExportedImageRecords: () -> Unit,
    onOpenContactEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsSection(title = "출력 설정") {
                Text("추천 품질 프리셋", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        QualityPreset.HighCompression,
                        QualityPreset.Recommended,
                        QualityPreset.Balanced,
                        QualityPreset.HighQuality,
                        QualityPreset.Maximum,
                    ).forEach { preset ->
                        FilterChip(
                            selected = settings.qualityPreset == preset,
                            onClick = { onSettingsChange { preset.applyTo(it) } },
                            label = { Text(preset.displayName()) },
                        )
                    }
                }
                Text(
                    text = "WEBP 품질 ${settings.webpQuality}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = settings.webpQuality.toFloat(),
                    onValueChange = { value ->
                        onSettingsChange {
                            it.copy(
                                webpQuality = value.toInt(),
                                qualityPreset = QualityPreset.Custom,
                            )
                        }
                    },
                    valueRange = 70f..100f,
                    steps = 29,
                )
                Text("출력 형식", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutputFormat.entries.forEach { format ->
                        FilterChip(
                            selected = settings.outputFormat == format,
                            enabled = format == OutputFormat.WEBP,
                            onClick = { onSettingsChange { it.copy(outputFormat = format) } },
                            label = { Text(format.displayName()) },
                        )
                    }
                }
                ToggleRow(
                    title = "원본 해상도 유지",
                    subtitle = "켜면 가능한 원본 크기로 저장합니다.",
                    checked = settings.keepOriginalResolution,
                    onCheckedChange = { checked ->
                        onSettingsChange {
                            it.copy(
                                keepOriginalResolution = checked,
                                qualityPreset = QualityPreset.Custom,
                            )
                        }
                    },
                )
                Text("최대 긴 변 제한", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    maxLongSideOptions.forEach { option ->
                        FilterChip(
                            enabled = !settings.keepOriginalResolution,
                            selected = settings.maxLongSidePx == option.value,
                            onClick = {
                                onSettingsChange {
                                    it.copy(
                                        maxLongSidePx = option.value,
                                        qualityPreset = QualityPreset.Custom,
                                    )
                                }
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text("저장 파일명 규칙", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileNameRule.entries.forEach { rule ->
                        FilterChip(
                            selected = settings.fileNameRule == rule,
                            onClick = { onSettingsChange { it.copy(fileNameRule = rule) } },
                            label = { Text(rule.displayName()) },
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = "표시 설정") {
                Text(
                    text = "텍스트 크기 ${"%.2f".format(settings.textScale)}x",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "작게" to 0.9f,
                        "기본" to 1.0f,
                        "크게" to 1.15f,
                        "매우 크게" to 1.3f,
                    ).forEach { (label, scale) ->
                        FilterChip(
                            selected = settings.textScale == scale,
                            onClick = { onSettingsChange { it.copy(textScale = scale) } },
                            label = { Text(label) },
                        )
                    }
                }
                Slider(
                    value = settings.textScale,
                    onValueChange = { value -> onSettingsChange { it.copy(textScale = value) } },
                    valueRange = 0.85f..1.35f,
                )
                Text("닉네임 표시 스타일", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NicknameStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.nicknameStyle == style,
                            onClick = { onSettingsChange { it.copy(nicknameStyle = style) } },
                            label = { Text(style.displayName()) },
                        )
                    }
                }
                Text(
                    text = "닉네임 강조 ${"%.2f".format(settings.nicknameEmphasis)}x",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = settings.nicknameEmphasis,
                    onValueChange = { value -> onSettingsChange { it.copy(nicknameEmphasis = value) } },
                    valueRange = 0.9f..1.35f,
                )
                HorizontalDivider()
                ToggleRow(
                    title = "대표 색상 표시",
                    subtitle = "사진에서 추출한 색상 팔레트를 일부 템플릿에 작게 표시합니다.",
                    checked = settings.showPaletteColors,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(showPaletteColors = checked) } },
                )
                Text("대표 색상 개수", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 4, 5).forEach { count ->
                        FilterChip(
                            selected = settings.paletteColorCount == count,
                            onClick = { onSettingsChange { it.copy(paletteColorCount = count) } },
                            label = { Text("${count}개") },
                        )
                    }
                }
                Text("색상 분석 영역", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaletteAnalysisMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.paletteAnalysisMode == mode,
                            onClick = { onSettingsChange { it.copy(paletteAnalysisMode = mode) } },
                            label = { Text(mode.displayName()) },
                        )
                    }
                }
                ToggleRow(
                    title = "로고 자동 대비 선택",
                    subtitle = "템플릿 배경에 따라 밝은/어두운 내장 로고를 자동으로 선택합니다.",
                    checked = settings.autoSelectLogoContrastVariant,
                    onCheckedChange = { checked ->
                        onSettingsChange { it.copy(autoSelectLogoContrastVariant = checked) }
                    },
                )
            }
        }

        item {
            SettingsSection(title = "저장 설정") {
                InfoRow("저장 위치", "Pictures/${settings.saveDirectoryName}")
                ToggleRow(
                    title = "저장 완료 후 갤러리 열기",
                    subtitle = "저장이 끝나면 마지막 저장 결과를 이미지 앱으로 엽니다.",
                    checked = settings.openGalleryAfterSave,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(openGalleryAfterSave = checked) } },
                )
                ToggleRow(
                    title = "저장 완료 메시지 표시",
                    subtitle = "저장 결과를 화면에 표시합니다.",
                    checked = settings.showSaveToast,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(showSaveToast = checked) } },
                )
                ToggleRow(
                    title = "전체 저장 시 실패 항목 건너뛰기",
                    subtitle = "끄면 첫 실패에서 전체 저장을 멈춥니다.",
                    checked = settings.skipFailedOnBatchSave,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(skipFailedOnBatchSave = checked) } },
                )
                ToggleRow(
                    title = "저장 미리보기 확대 허용",
                    subtitle = "저장 화면에서 렌더 미리보기를 크게 볼 수 있습니다.",
                    checked = settings.enableExportPreviewZoom,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(enableExportPreviewZoom = checked) } },
                )
            }
        }

        item {
            SettingsSection(title = "편집 설정") {
                ToggleRow(
                    title = "마지막 사용 템플릿 기억",
                    subtitle = "템플릿을 선택하면 기본 템플릿도 함께 갱신합니다.",
                    checked = settings.rememberLastTemplate,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(rememberLastTemplate = checked) } },
                )
                ToggleRow(
                    title = "마지막 닉네임 기억",
                    subtitle = "새 사진 추가 시 최근 닉네임을 제안합니다.",
                    checked = settings.rememberLastNickname,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(rememberLastNickname = checked) } },
                )
                Text("최근 사용 입력값 개수", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 50).forEach { limit ->
                        FilterChip(
                            selected = settings.recentInputLimit == limit,
                            onClick = { onSettingsChange { it.copy(recentInputLimit = limit) } },
                            label = { Text("${limit}개") },
                        )
                    }
                }
                ToggleRow(
                    title = "사진 추가 시 이전 빌드 정보 복사",
                    subtitle = "새 사진에 직전 사진의 빌드 정보를 자동으로 넣습니다.",
                    checked = settings.copyPreviousBuildInfoOnAdd,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(copyPreviousBuildInfoOnAdd = checked) } },
                )
                ToggleRow(
                    title = "스위치 추천 목록 표시",
                    subtitle = "스위치 입력칸에서 앱 지원 프리셋을 함께 보여줍니다.",
                    checked = settings.showSwitchPresets,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(showSwitchPresets = checked) } },
                )
            }
        }

        item {
            SettingsSection(title = "세션 설정") {
                ToggleRow(
                    title = "자동 세션 임시 저장",
                    subtitle = "사진 목록, 템플릿, crop, 빌드 정보를 임시 저장합니다.",
                    checked = settings.autoRestoreDraftSession,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(autoRestoreDraftSession = checked) } },
                )
                ToggleRow(
                    title = "앱 시작 시 복구 여부 묻기",
                    subtitle = "끄면 임시 저장된 작업을 자동 복구합니다.",
                    checked = settings.askBeforeRestoreDraft,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(askBeforeRestoreDraft = checked) } },
                )
            }
        }

        item {
            SettingsSection(title = "템플릿 설정") {
                Text("기본 템플릿", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardTemplate.entries.forEach { template ->
                        FilterChip(
                            selected = settings.defaultTemplate == template,
                            onClick = { onSettingsChange { it.copy(defaultTemplate = template) } },
                            label = { Text(template.displayName()) },
                        )
                    }
                }
                ToggleRow(
                    title = "템플릿 미리보기에서 실제 사진 사용",
                    subtitle = "끄면 샘플 미리보기 중심으로 봅니다.",
                    checked = settings.useCurrentPhotoForTemplatePreview,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(useCurrentPhotoForTemplatePreview = checked) } },
                )
                ToggleRow(
                    title = "내부 오버레이 중앙 영역 보호",
                    subtitle = "로고와 정보가 사진 중앙에 놓이지 않도록 제한합니다.",
                    checked = settings.protectCenterAreaForOverlay,
                    onCheckedChange = { checked -> onSettingsChange { it.copy(protectCenterAreaForOverlay = checked) } },
                )
            }
        }

        item {
            SettingsSection(title = "업데이트") {
                InfoRow("현재 버전", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow(
                    "최신 버전",
                    updateCheckState.latestInfo?.latestVersionName ?: "확인 전",
                )
                InfoRow("마지막 확인", updateCheckState.lastCheckedAt.formatCheckedAt())
                updateCheckState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (updateDownloadState.isDownloading) {
                    val progress = updateDownloadState.progressPercent
                    Text(
                        text = "APK 다운로드 중${progress?.let { " · $it%" }.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (progress ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                updateDownloadState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !updateCheckState.isChecking,
                    onClick = onCheckUpdate,
                ) {
                    Text(if (updateCheckState.isChecking) "확인 중..." else "업데이트 확인")
                }
                if (updateDownloadState.downloadedApkPath != null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !updateDownloadState.isDownloading,
                        onClick = onInstallDownloadedUpdate,
                    ) {
                        Text("설치 계속")
                    }
                }
                if (BuildConfig.DEBUG) {
                    ClearableTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = settings.updateJsonUrl,
                        onValueChange = { value -> onSettingsChange { it.copy(updateJsonUrl = value) } },
                        label = { Text("update.json URL") },
                        placeholder = { Text("https://example.com/keyxif/update.json") },
                        singleLine = true,
                    )
                }
            }
        }

        item {
            SettingsSection(title = "완성 이미지 갤러리 관리") {
                InfoRow("저장된 항목", "${exportedImageCount}개")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPruneMissingExportedImages,
                ) {
                    Text("파일 접근 불가 항목 정리")
                }
                Text(
                    text = "목록 기록 초기화는 실제 이미지 파일을 삭제하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClearExportedImageRecords,
                ) {
                    Text("목록 기록 초기화")
                }
            }
        }

        item {
            SettingsSection(title = "앱 정보") {
                Text(
                    text = "Keyxif",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                InfoRow("버전명", BuildConfig.VERSION_NAME)
                InfoRow("오픈소스 라이선스", "준비 중")
                HorizontalDivider()
                Text(
                    text = "사진과 빌드 정보는 기본적으로 기기 내부에서만 처리됩니다.\n서버 업로드나 외부 전송 없이, 사용자가 직접 저장한 결과물만 갤러리에 저장됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSection(title = "개발 정보") {
                InfoRow("개발자", "KGJun")
                ActionInfoRow("문의", "typenews902@gmail.com", onOpenContactEmail)
                InfoRow("GitHub", "공개 예정")
                InfoRow("기술 정보", "Kotlin · Jetpack Compose · Android SDK 26+")
                InfoRow("이미지 처리", "On-device rendering · WEBP Export · MediaStore Save")
                InfoRow("현재 기기", "Android ${Build.VERSION.SDK_INT}")
                if (BuildConfig.DEBUG) {
                    HorizontalDivider()
                    Text(
                        text = "디버그 빌드 전용 개발자 옵션 영역",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionInfoRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.TextButton(
            contentPadding = PaddingValues(0.dp),
            onClick = onClick,
        ) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Long?.formatCheckedAt(): String {
    val time = this?.takeIf { it > 0L } ?: return "확인 전"
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(time))
}

private data class MaxLongSideOption(
    val label: String,
    val value: Int?,
)

private val maxLongSideOptions = listOf(
    MaxLongSideOption("원본", null),
    MaxLongSideOption("2048px", 2048),
    MaxLongSideOption("2400px", 2400),
    MaxLongSideOption("3000px", 3000),
    MaxLongSideOption("4000px", 4000),
)
