package com.keyxif.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.keyxif.app.domain.model.ExportedImage
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    LaunchedEffect(images) {
        val validIds = images.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in validIds }.toSet()
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
                text = "Keyxif로 저장한 결과 이미지 ${images.size}개",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (images.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedIds.isNotEmpty()) {
                        Button(
                            onClick = { pendingDeleteImages = images.filter { it.id in selectedIds } },
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
        } else {
            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(156.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(images, key = { it.id }, contentType = { "exported_image" }) { image ->
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
