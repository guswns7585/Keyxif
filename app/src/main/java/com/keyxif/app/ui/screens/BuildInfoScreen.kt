package com.keyxif.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.keyxif.app.data.repository.PresetChoice
import com.keyxif.app.domain.model.BuildPreset
import com.keyxif.app.domain.model.HousingPreset
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.KeycapPreset
import com.keyxif.app.domain.model.LogoPreset
import com.keyxif.app.domain.model.SwitchPreset
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import com.keyxif.app.ui.KeyxifUiState
import com.keyxif.app.ui.components.ClearableSearchField
import com.keyxif.app.ui.components.ClearableTextField
import com.keyxif.app.ui.components.PresetSearchField
import com.keyxif.app.ui.components.RecentChips

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BuildInfoScreen(
    state: KeyxifUiState,
    plates: List<String>,
    mounts: List<String>,
    logos: List<LogoPreset>,
    presets: List<BuildPreset>,
    housingOptions: (String) -> List<PresetChoice<HousingPreset>>,
    switchOptions: (String) -> List<PresetChoice<SwitchPreset>>,
    keycapOptions: (String) -> List<PresetChoice<KeycapPreset>>,
    onSelectPhoto: (String) -> Unit,
    onBuildInfoChange: (KeyboardBuildInfo) -> Unit,
    onHousingPreset: (HousingPreset) -> Unit,
    onSwitchPreset: (SwitchPreset) -> Unit,
    onKeycapPreset: (KeycapPreset) -> Unit,
    onApplyToAll: () -> Unit,
    onPresetQueryChange: (String) -> Unit,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (BuildPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDeleteRecentHousing: (String) -> Unit,
    onDeleteRecentSwitch: (String) -> Unit,
    onDeleteRecentKeycap: (String) -> Unit,
    onDeleteRecentNickname: (String) -> Unit,
) {
    val selectedPhoto = state.selectedPhoto
    val info = selectedPhoto?.buildInfo ?: KeyboardBuildInfo()
    var presetName by rememberSaveable(selectedPhoto?.id) { mutableStateOf("") }
    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { onBuildInfoChange(info.copy(customLogoUri = it, logoId = null)) }
        },
    )
    var showPresetPicker by rememberSaveable { mutableStateOf(false) }
    var presetPendingDelete by remember { mutableStateOf<BuildPreset?>(null) }
    val appliedPresetName = presets.firstOrNull { it.buildInfo == info }?.presetName

    presetPendingDelete?.let { preset ->
        DeletePresetConfirmDialog(
            preset = preset,
            onConfirm = {
                onDeletePreset(preset.id)
                presetPendingDelete = null
            },
            onDismiss = { presetPendingDelete = null },
        )
    }

    if (showPresetPicker) {
        BuildPresetPickerBottomSheet(
            presets = presets,
            query = state.presetQuery,
            onQueryChange = onPresetQueryChange,
            onApplyPreset = {
                onApplyPreset(it)
                showPresetPicker = false
            },
            onDeletePreset = { presetPendingDelete = it },
            onDismiss = { showPresetPicker = false },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "editing_photo", contentType = "section") {
            FormSection(title = "편집 중인 사진") {
                selectedPhoto?.let { photo ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.displayName,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = photo.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${state.photos.indexOf(photo) + 1} / ${state.photos.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.photos, key = { it.id }) { photo ->
                        val selected = photo.id == selectedPhoto?.id
                        Surface(
                            modifier = Modifier
                                .width(72.dp)
                                .aspectRatio(1f)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                            shape = RoundedCornerShape(6.dp),
                            onClick = { onSelectPhoto(photo.id) },
                        ) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.displayName,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                OutlinedButton(
                    enabled = selectedPhoto != null && state.photos.size > 1,
                    onClick = onApplyToAll,
                ) {
                    Text("이 빌드 정보를 모든 사진에 적용")
                }
            }
        }

        item(key = "build_presets", contentType = "section") {
            FormSection(title = "빌드 프리셋") {
                Text(
                    text = appliedPresetName?.let { "현재 적용: $it" } ?: "현재 적용된 프리셋 없음",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showPresetPicker = true },
                ) {
                    Text("빌드 프리셋 불러오기")
                }
                presets.take(3).takeIf { it.isNotEmpty() }?.let { recentPresets ->
                    Text(
                        text = "최근 프리셋",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    recentPresets.forEach { preset ->
                        PresetSummaryRow(
                            preset = preset,
                            onApplyPreset = onApplyPreset,
                            onDeletePreset = { presetPendingDelete = preset },
                        )
                    }
                    TextButton(onClick = { showPresetPicker = true }) {
                        Text("전체 보기")
                    }
                } ?: Text(
                    text = "저장된 프리셋이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClearableTextField(
                        modifier = Modifier.weight(1f),
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("새 프리셋 이름") },
                        placeholder = { Text("비우면 자동 생성") },
                        singleLine = true,
                    )
                    Button(
                        enabled = selectedPhoto != null,
                        onClick = {
                            onSavePreset(presetName)
                            presetName = ""
                        },
                    ) {
                        Text("저장")
                    }
                }
            }
        }

        item(key = "build_info", contentType = "section") {
            FormSection(title = "빌드 정보") {
                RecentChips(
                    title = "최근 사용한 하우징",
                    values = state.recentHousing,
                    onClick = { onBuildInfoChange(info.copy(housing = it)) },
                )
                PresetSearchField(
                    label = "하우징",
                    value = info.housing,
                    placeholder = "하우징 검색 또는 직접 입력",
                    options = housingOptions,
                    onValueChange = { onBuildInfoChange(info.copy(housing = it)) },
                    onOptionSelected = { choice ->
                        choice.preset?.let(onHousingPreset)
                            ?: onBuildInfoChange(info.copy(housing = choice.title))
                    },
                    onDeleteRecent = onDeleteRecentHousing,
                )
                RecentChips(
                    title = "최근 사용한 스위치",
                    values = state.recentSwitches,
                    onClick = { onBuildInfoChange(info.copy(switchName = it)) },
                )
                PresetSearchField(
                    label = "스위치",
                    value = info.switchName,
                    placeholder = "스위치 검색 또는 직접 입력",
                    options = switchOptions,
                    onValueChange = { onBuildInfoChange(info.copy(switchName = it)) },
                    onOptionSelected = { choice ->
                        choice.preset?.let(onSwitchPreset)
                            ?: onBuildInfoChange(info.copy(switchName = choice.title))
                    },
                    onDeleteRecent = onDeleteRecentSwitch,
                )
                RecentChips(
                    title = "최근 사용한 키캡",
                    values = state.recentKeycaps,
                    onClick = { onBuildInfoChange(info.copy(keycap = it)) },
                )
                PresetSearchField(
                    label = "키캡",
                    value = info.keycap,
                    placeholder = "키캡 검색 또는 직접 입력",
                    options = keycapOptions,
                    onValueChange = { onBuildInfoChange(info.copy(keycap = it)) },
                    onOptionSelected = { choice ->
                        choice.preset?.let(onKeycapPreset)
                            ?: onBuildInfoChange(info.copy(keycap = choice.title))
                    },
                    onDeleteRecent = onDeleteRecentKeycap,
                )
            }
        }

        item(key = "plate", contentType = "section") {
            FormSection(title = "보강판") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    plates.forEach { plate ->
                        FilterChip(
                            selected = info.plate == plate,
                            onClick = { onBuildInfoChange(info.copy(plate = plate)) },
                            label = { Text(plate) },
                        )
                    }
                }
            }
        }

        item(key = "mount", contentType = "section") {
            FormSection(title = "마운트") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    mounts.forEach { mount ->
                        FilterChip(
                            selected = info.mount == mount,
                            onClick = { onBuildInfoChange(info.copy(mount = mount)) },
                            label = { Text(mount) },
                        )
                    }
                }
            }
        }

        item(key = "nickname_logo", contentType = "section") {
            FormSection(title = "닉네임과 로고") {
                RecentChips(
                    title = "최근 사용한 닉네임",
                    values = state.recentNicknames,
                    onClick = { onBuildInfoChange(info.copy(nickname = it)) },
                )
                ClearableTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = info.nickname,
                    onValueChange = { onBuildInfoChange(info.copy(nickname = it)) },
                    label = { Text("닉네임") },
                    placeholder = { Text("입력한 경우에만 표시합니다") },
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = info.logoId == null && info.customLogoUri == null,
                        onClick = { onBuildInfoChange(info.copy(logoId = null, customLogoUri = null)) },
                        label = { Text("자동") },
                    )
                    logos.forEach { logo ->
                        FilterChip(
                            selected = info.logoId == logo.id && info.customLogoUri == null,
                            onClick = { onBuildInfoChange(info.copy(logoId = logo.id, customLogoUri = null)) },
                            label = { Text(logo.name) },
                        )
                    }
                    FilterChip(
                        selected = info.customLogoUri != null,
                        onClick = {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        label = { Text(if (info.customLogoUri == null) "로고 업로드" else "사용자 로고") },
                        leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                    )
                }
                info.customLogoUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "사용자 로고 미리보기",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildPresetPickerBottomSheet(
    presets: List<BuildPreset>,
    query: String,
    onQueryChange: (String) -> Unit,
    onApplyPreset: (BuildPreset) -> Unit,
    onDeletePreset: (BuildPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered by remember(presets, query) {
        derivedStateOf {
            presets.filter { preset ->
                val q = query.trim()
                q.isBlank() ||
                    preset.presetName.contains(q, ignoreCase = true) ||
                    preset.buildInfo.housing.contains(q, ignoreCase = true) ||
                    preset.buildInfo.switchName.contains(q, ignoreCase = true) ||
                    preset.buildInfo.keycap.contains(q, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "빌드 프리셋 불러오기",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            ClearableSearchField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = onQueryChange,
                labelText = "프리셋 검색",
            )
            if (filtered.isEmpty()) {
                Text(
                    text = "검색 결과가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
            ) {
                items(filtered, key = { it.id }, contentType = { "build_preset" }) { preset ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = preset.presetName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = preset.descriptionText(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { onApplyPreset(preset) }) {
                                    Text("적용")
                                }
                                IconButton(onClick = { onDeletePreset(preset) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "${preset.presetName} 삭제",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PresetSummaryRow(
    preset: BuildPreset,
    onApplyPreset: (BuildPreset) -> Unit,
    onDeletePreset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.presetName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset.descriptionText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onApplyPreset(preset) }) {
                Text("적용")
            }
            IconButton(onClick = onDeletePreset) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "${preset.presetName} 삭제",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeletePresetConfirmDialog(
    preset: BuildPreset,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("프리셋을 삭제할까요?") },
        text = {
            Text(
                text = "\"${preset.presetName}\" 프리셋을 삭제합니다. 이 작업은 되돌릴 수 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                onClick = onConfirm,
            ) {
                Text("삭제")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

private fun BuildPreset.descriptionText(): String {
    return listOf(buildInfo.housing, buildInfo.switchName, buildInfo.keycap)
        .mapNotNull { it.meaningfulBuildTextOrNull() }
        .joinToString(" · ")
        .ifBlank { "세부 정보 없음" }
}
