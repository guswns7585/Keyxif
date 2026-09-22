package com.keyxif.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.CustomTemplate
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import com.keyxif.app.ui.components.TemplatePreviewCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TemplateSelectScreen(
    selectedTemplate: CardTemplate,
    selectedCustomTemplateId: String?,
    customTemplates: List<CustomTemplate>,
    buildInfo: KeyboardBuildInfo,
    selectedPhotoLabel: String?,
    customTemplateUiEnabled: Boolean = false,
    onSelect: (CardTemplate) -> Unit,
    onSelectCustomTemplate: (String) -> Unit,
    onCreateTemplate: () -> Unit,
    onEditCustomTemplate: (String) -> Unit,
    onDuplicateCustomTemplate: (String) -> Unit,
    onDeleteCustomTemplate: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<CustomTemplate?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "템플릿",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = listOfNotNull(
                        selectedPhotoLabel?.let { "편집 중: $it" },
                        buildInfo.housing.meaningfulBuildTextOrNull(),
                    ).joinToString(" · ").ifBlank { "사진을 가리지 않는 카드 스타일을 선택하세요." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (customTemplateUiEnabled) {
            Button(onClick = onCreateTemplate) {
                Text("템플릿 만들기")
            }
            }
        }
        if (customTemplateUiEnabled) {
        CustomTemplateList(
            customTemplates = customTemplates,
            selectedCustomTemplateId = selectedCustomTemplateId,
            onSelect = onSelectCustomTemplate,
            onEdit = onEditCustomTemplate,
            onDuplicate = onDuplicateCustomTemplate,
            onDeleteRequest = { deleteTarget = it },
        )
        }
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Adaptive(minSize = 156.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(CardTemplate.entries, key = { it.name }) { template ->
                TemplatePreviewCard(
                    template = template,
                    selected = (!customTemplateUiEnabled || selectedCustomTemplateId == null) && selectedTemplate == template,
                    onClick = { onSelect(template) },
                )
            }
        }
    }
    if (customTemplateUiEnabled) deleteTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("커스텀 템플릿 삭제") },
            text = { Text("`${template.name}` 템플릿을 삭제할까요? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomTemplate(template.id)
                        deleteTarget = null
                    },
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun CustomTemplateList(
    customTemplates: List<CustomTemplate>,
    selectedCustomTemplateId: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDeleteRequest: (CustomTemplate) -> Unit,
) {
    if (customTemplates.isEmpty()) {
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "내 커스텀 템플릿",
            style = MaterialTheme.typography.titleMedium,
        )
        customTemplates.forEach { template ->
            val selected = selectedCustomTemplateId == template.id
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
                onClick = { onSelect(template.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "요소 ${template.elements.size}개 · 카드 ${template.internalCards.size}개 · ${formatTemplateTime(template.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { onEdit(template.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text("편집")
                    }
                    IconButton(onClick = { onDuplicate(template.id) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "복제")
                    }
                    IconButton(onClick = { onDeleteRequest(template) }) {
                        Icon(Icons.Default.Delete, contentDescription = "삭제")
                    }
                }
            }
        }
    }
}

private fun formatTemplateTime(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(timestamp))
    }.getOrDefault("저장 시각 알 수 없음")
}
