package com.keyxif.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.keyxif.app.data.presets.PresetData
import com.keyxif.app.domain.model.ExportedImage
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ExportedGalleryScreen(
    images: List<ExportedImage>,
    onBackToCreate: () -> Unit,
    onShare: (ExportedImage) -> Unit,
    onOpen: (ExportedImage) -> Unit,
    onRemoveRecord: (String) -> Unit,
    onDeleteFiles: (List<ExportedImage>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedImage by remember { mutableStateOf<ExportedImage?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteImages by remember { mutableStateOf<List<ExportedImage>?>(null) }
    var groupingMode by remember { mutableStateOf(GalleryGroupingMode.All) }
    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    val groups = remember(images, groupingMode) { images.galleryGroups(groupingMode) }
    val activeGroup = groups.firstOrNull { it.key == selectedGroupKey }
    val visibleImages = activeGroup?.items ?: images
    val showingGroups = groupingMode != GalleryGroupingMode.All && activeGroup == null

    LaunchedEffect(images) {
        val validIds = images.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in validIds }.toSet()
    }
    LaunchedEffect(groupingMode, groups) {
        selectedGroupKey = selectedGroupKey?.takeIf { key -> groups.any { it.key == key } }
        selectedIds = emptySet()
    }

    selectedImage?.let { image ->
        ExportedImageDetailDialog(
            image = image,
            onDismiss = { selectedImage = null },
            onShare = { onShare(image) },
            onOpen = { onOpen(image) },
            onRemoveRecord = {
                selectedImage = null
                onRemoveRecord(image.id)
            },
            onDeleteFile = {
                selectedImage = null
                pendingDeleteImages = listOf(image)
            },
        )
    }

    pendingDeleteImages?.let { deleteTargets ->
        AlertDialog(
            onDismissRequest = { pendingDeleteImages = null },
            title = { Text("이미지를 삭제할까요?") },
            text = {
                Text(
                    if (deleteTargets.size == 1) {
                        "\"${deleteTargets.first().fileName}\" 파일을 기기에서 삭제하고 갤러리 목록에서도 제거합니다."
                    } else {
                        "선택한 이미지 ${deleteTargets.size}개를 기기에서 삭제하고 갤러리 목록에서도 제거합니다."
                    },
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        onDeleteFiles(deleteTargets)
                        selectedIds = selectedIds - deleteTargets.map { it.id }.toSet()
                        pendingDeleteImages = null
                    },
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteImages = null }) {
                    Text("취소")
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "완성 이미지",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (activeGroup == null) {
                    "Keyxif로 저장한 결과 이미지 ${images.size}개"
                } else {
                    "${activeGroup.title} 그룹 이미지 ${activeGroup.items.size}개"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GalleryGroupingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = groupingMode == mode,
                        onClick = {
                            groupingMode = mode
                            selectedGroupKey = null
                        },
                        label = { Text(mode.label) },
                    )
                }
            }
            activeGroup?.let { group ->
                OutlinedButton(onClick = { selectedGroupKey = null }) {
                    Text("${groupingMode.label} 그룹으로 돌아가기")
                }
            }
            if (images.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedIds.isNotEmpty()) {
                        Button(
                            onClick = { pendingDeleteImages = visibleImages.filter { it.id in selectedIds } },
                        ) {
                            Text("선택 삭제 ${selectedIds.size}")
                        }
                        TextButton(onClick = { selectedIds = emptySet() }) {
                            Text("선택 해제")
                        }
                    }
                    OutlinedButton(
                        modifier = if (selectedIds.isEmpty()) Modifier else Modifier.weight(1f),
                        onClick = { pendingDeleteImages = images },
                    ) {
                        Text("전체 삭제")
                    }
                }
            }
        }

        if (images.isEmpty()) {
            EmptyExportedGalleryState(onBackToCreate = onBackToCreate)
        } else if (showingGroups) {
            GalleryGroupGrid(
                groups = groups,
                onGroupClick = { selectedGroupKey = it.key },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(156.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visibleImages, key = { it.id }, contentType = { "exported_image" }) { image ->
                    val selected = image.id in selectedIds
                    ExportedImageGridItem(
                        image = image,
                        selected = selected,
                        selectionMode = selectedIds.isNotEmpty(),
                        onClick = {
                            if (selectedIds.isEmpty()) {
                                selectedImage = image
                            } else {
                                selectedIds = if (selected) selectedIds - image.id else selectedIds + image.id
                            }
                        },
                        onLongClick = {
                            selectedIds = selectedIds + image.id
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryGroupGrid(
    groups: List<GalleryGroup>,
    onGroupClick: (GalleryGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "그룹으로 묶을 이미지가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(172.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.key }, contentType = { "gallery_group" }) { group ->
            GalleryGroupCard(group = group, onClick = { onGroupClick(group) })
        }
    }
}

@Composable
private fun GalleryGroupCard(
    group: GalleryGroup,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.28f),
            ) {
                group.previewItems.take(3).forEachIndexed { index, image ->
                    AsyncImage(
                        model = image.uri,
                        contentDescription = image.fileName,
                        modifier = Modifier
                            .align(
                                when (index) {
                                    0 -> Alignment.CenterStart
                                    1 -> Alignment.Center
                                    else -> Alignment.CenterEnd
                                },
                            )
                            .padding(6.dp)
                            .fillMaxWidth(if (group.previewItems.size == 1) 1f else 0.58f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                group.colorChip?.let { color ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(color)),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${group.count}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyExportedGalleryState(onBackToCreate: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "아직 완성된 이미지가 없습니다.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "사진을 선택하고 Keyxif 카드로 저장해보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onBackToCreate) {
                Text("사진 선택하기")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExportedImageGridItem(
    image: ExportedImage,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                AsyncImage(
                    model = image.uri,
                    contentDescription = image.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    contentScale = ContentScale.Crop,
                )
                if (selectionMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(26.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                        },
                    ) {
                        if (selected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "선택됨",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = image.displayTitle(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = image.createdAt.formatDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExportedImageDetailDialog(
    image: ExportedImage,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onRemoveRecord: () -> Unit,
    onDeleteFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(image.displayTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = image.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
                InfoLine("파일", image.fileName)
                InfoLine("저장", image.createdAt.formatDate())
                InfoLine("템플릿", image.templateName)
                InfoLine("크기", "${image.width} x ${image.height}")
                image.buildRows().forEach { (label, value) ->
                    InfoLine(label, value)
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "공유")
                    }
                    IconButton(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "갤러리에서 열기")
                    }
                    IconButton(onClick = onDeleteFile) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "파일 삭제",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onRemoveRecord) {
                Text("목록에서만 제거")
            }
        },
    )
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Long.formatDate(): String {
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(this))
}

private fun ExportedImage.displayTitle(): String {
    return nickname.meaningfulBuildTextOrNull()
        ?: housing.meaningfulBuildTextOrNull()
        ?: fileName.meaningfulBuildTextOrNull()
        ?: createdAt.formatDate()
}

private fun ExportedImage.buildRows(): List<Pair<String, String>> {
    return buildList {
        housing.meaningfulBuildTextOrNull()?.let { add("Housing" to it) }
        switchName.meaningfulBuildTextOrNull()?.let { add("Switch" to it) }
        keycap.meaningfulBuildTextOrNull()?.let { add("Keycap" to it) }
        nickname.meaningfulBuildTextOrNull()?.let { add("Nickname" to it) }
    }
}

private enum class GalleryGroupingMode(val label: String) {
    All("전체"),
    Housing("하우징"),
    Brand("브랜드"),
    Color("색상"),
}

private data class GalleryGroup(
    val key: String,
    val title: String,
    val count: Int,
    val items: List<ExportedImage>,
    val previewItems: List<ExportedImage>,
    val colorChip: Int? = null,
)

private data class ColorGroupInfo(
    val key: String,
    val title: String,
    val chipColor: Int?,
)

private fun List<ExportedImage>.galleryGroups(mode: GalleryGroupingMode): List<GalleryGroup> {
    if (mode == GalleryGroupingMode.All) return emptyList()
    val grouped = groupBy { image ->
        when (mode) {
            GalleryGroupingMode.All -> ""
            GalleryGroupingMode.Housing -> image.galleryHousingTitle().groupKey("housing")
            GalleryGroupingMode.Brand -> image.galleryBrandTitle().groupKey("brand")
            GalleryGroupingMode.Color -> image.colorGroupInfo().key
        }
    }
    return grouped.map { (key, items) ->
        val sortedItems = items.sortedByDescending { it.createdAt }
        val first = sortedItems.first()
        val colorInfo = if (mode == GalleryGroupingMode.Color) first.colorGroupInfo() else null
        GalleryGroup(
            key = key,
            title = when (mode) {
                GalleryGroupingMode.All -> ""
                GalleryGroupingMode.Housing -> first.galleryHousingTitle()
                GalleryGroupingMode.Brand -> first.galleryBrandTitle()
                GalleryGroupingMode.Color -> colorInfo?.title ?: UNKNOWN_GROUP_TITLE
            },
            count = sortedItems.size,
            items = sortedItems,
            previewItems = sortedItems.take(3),
            colorChip = colorInfo?.chipColor,
        )
    }.sortedWith(
        compareByDescending<GalleryGroup> { it.key != UNKNOWN_GROUP_KEY }
            .thenByDescending { it.count }
            .thenBy { it.title },
    )
}

private fun ExportedImage.galleryHousingTitle(): String {
    return housing.meaningfulGalleryGroupText() ?: UNKNOWN_GROUP_TITLE
}

private fun ExportedImage.galleryBrandTitle(): String {
    val housingText = housing.meaningfulGalleryGroupText()
    if (housingText != null) {
        PresetData.housings.firstOrNull { preset ->
            preset.name.samePresetText(housingText) ||
                preset.aliases.any { it.samePresetText(housingText) } ||
                preset.id.samePresetText(housingText)
        }?.let { preset ->
            preset.vendor?.meaningfulGalleryGroupText()?.let { return normalizeBrandTitle(it) }
            preset.vendorId?.let { vendorId ->
                PresetData.vendors.firstOrNull { it.id == vendorId }
                    ?.name
                    ?.meaningfulGalleryGroupText()
                    ?.let { return normalizeBrandTitle(it) }
            }
        }
    }
    return UNKNOWN_GROUP_TITLE
}

private fun ExportedImage.colorGroupInfo(): ColorGroupInfo {
    val colors = paletteColors.filter { it != 0 }.take(3)
    if (colors.isEmpty()) {
        return ColorGroupInfo(UNKNOWN_GROUP_KEY, UNKNOWN_GROUP_TITLE, null)
    }
    val swatches = colors.map { color -> color to color.toHsvInfo() }
    val colorful = swatches.filter { (_, hsv) -> hsv.saturation >= 0.18f && hsv.value >= 0.18f }
    if (colorful.isEmpty()) {
        val averageLuminance = swatches.map { (color, _) -> Color(color).luminance() }.average()
        return when {
            averageLuminance <= 0.18 -> ColorGroupInfo("color-dark", "Black / Dark", 0xFF1F1F1F.toInt())
            averageLuminance >= 0.78 -> ColorGroupInfo("color-white", "White / Silver", 0xFFE8E4DA.toInt())
            else -> ColorGroupInfo("color-gray", "Gray", 0xFF8C8C86.toInt())
        }
    }

    val hueGroups = colorful.map { (_, hsv) -> hueGroup(hsv.hue) }
    val topGroup = hueGroups
        .groupingBy { it.key }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { -it.key.hashCode() })
        ?.key
    val dominant = hueGroups.firstOrNull { it.key == topGroup } ?: hueGroups.first()
    val groupShare = hueGroups.count { it.key == dominant.key }.toFloat() / hueGroups.size
    val hueSpread = colorful.map { (_, hsv) -> hsv.hue }.hueSpread()
    if (colorful.size >= 3 && groupShare < 0.67f && hueSpread > 85f) {
        return ColorGroupInfo("color-mixed", "Multi / Mixed", 0xFF9B8BD9.toInt())
    }
    return dominant
}

private fun hueGroup(hue: Float): ColorGroupInfo {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    return when {
        normalizedHue < 18f || normalizedHue >= 342f -> ColorGroupInfo("color-red", "Red / Pink", 0xFFE45B67.toInt())
        normalizedHue < 45f -> ColorGroupInfo("color-orange", "Orange", 0xFFE58A3A.toInt())
        normalizedHue < 72f -> ColorGroupInfo("color-yellow", "Yellow / Gold", 0xFFE0B93A.toInt())
        normalizedHue < 155f -> ColorGroupInfo("color-green", "Green", 0xFF4FA66A.toInt())
        normalizedHue < 195f -> ColorGroupInfo("color-cyan", "Cyan", 0xFF4CB6B7.toInt())
        normalizedHue < 250f -> ColorGroupInfo("color-blue", "Blue", 0xFF537ECF.toInt())
        normalizedHue < 292f -> ColorGroupInfo("color-purple", "Purple", 0xFF8B69CC.toInt())
        else -> ColorGroupInfo("color-red-pink", "Red / Pink", 0xFFD9689D.toInt())
    }
}

private data class HsvInfo(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

private fun Int.toHsvInfo(): HsvInfo {
    val red = (this shr 16 and 0xFF) / 255f
    val green = (this shr 8 and 0xFF) / 255f
    val blue = (this and 0xFF) / 255f
    val maxValue = max(red, max(green, blue))
    val minValue = min(red, min(green, blue))
    val delta = maxValue - minValue
    val hue = when {
        delta == 0f -> 0f
        maxValue == red -> 60f * (((green - blue) / delta) % 6f)
        maxValue == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (maxValue == 0f) 0f else delta / maxValue
    return HsvInfo(hue = hue, saturation = saturation, value = maxValue)
}

private fun List<Float>.hueSpread(): Float {
    if (size < 2) return 0f
    val sorted = map { ((it % 360f) + 360f) % 360f }.sorted()
    val maxGap = sorted.indices.maxOf { index ->
        val current = sorted[index]
        val next = sorted[(index + 1) % sorted.size] + if (index == sorted.lastIndex) 360f else 0f
        next - current
    }
    return 360f - maxGap
}

private fun String?.meaningfulGalleryGroupText(): String? {
    val value = this?.meaningfulBuildTextOrNull() ?: return null
    val normalized = value.normalizedGroupText()
    return value.takeUnless {
        normalized.isBlank() ||
            normalized == "untitledkeyboard" ||
            normalized == "unknown" ||
            normalized == "none" ||
            normalized == "null"
    }
}

private fun normalizeBrandTitle(value: String): String {
    return when (value.normalizedGroupText()) {
        "qwertykeys" -> "Qwertykeys"
        "geonworks", "geon" -> "Geonworks"
        "modedesigns", "mode" -> "Mode"
        "owlab" -> "Owlab"
        "singakbd", "singa" -> "SingaKBD"
        "matrixlab", "matrix" -> "Matrix Lab"
        "kbdfans" -> "KBDfans"
        "jjwconcepts", "jjw" -> "JJW"
        else -> value
    }
}

private fun String.groupKey(prefix: String): String {
    val normalized = normalizedGroupText()
    return if (normalized.isBlank() || this == UNKNOWN_GROUP_TITLE) UNKNOWN_GROUP_KEY else "$prefix-$normalized"
}

private fun String.samePresetText(other: String): Boolean = normalizedGroupText() == other.normalizedGroupText()

private fun String.normalizedGroupText(): String {
    return trim().lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9가-힣]+"), "")
}

private const val UNKNOWN_GROUP_TITLE = "정보 없음"
private const val UNKNOWN_GROUP_KEY = "unknown"
