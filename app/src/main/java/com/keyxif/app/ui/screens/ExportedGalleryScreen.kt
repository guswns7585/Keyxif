package com.keyxif.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.Icons
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
    onDeleteFile: (ExportedImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedImage by remember { mutableStateOf<ExportedImage?>(null) }
    var imagePendingDelete by remember { mutableStateOf<ExportedImage?>(null) }

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
                imagePendingDelete = image
            },
        )
    }

    imagePendingDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { imagePendingDelete = null },
            title = { Text("파일도 삭제할까요?") },
            text = { Text("\"${image.fileName}\" 파일을 기기에서도 삭제합니다. 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        onDeleteFile(image)
                        imagePendingDelete = null
                    },
                ) {
                    Text("파일도 삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { imagePendingDelete = null }) {
                    Text("취소")
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    ExportedImageGridItem(
                        image = image,
                        onClick = { selectedImage = image },
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
                text = "사진을 선택하고 Keyxif 카드로 변환해보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onBackToCreate) {
                Text("사진 선택하기")
            }
        }
    }
}

@Composable
private fun ExportedImageGridItem(
    image: ExportedImage,
    onClick: () -> Unit,
) {
    Card(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(
                model = image.uri,
                contentDescription = image.fileName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                contentScale = ContentScale.Crop,
            )
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
                            contentDescription = "파일도 삭제",
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
