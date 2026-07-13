package com.keyxif.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import com.keyxif.app.ui.components.TemplatePreviewCard

@Composable
fun TemplateSelectScreen(
    selectedTemplate: CardTemplate,
    buildInfo: KeyboardBuildInfo,
    selectedPhotoLabel: String?,
    onSelect: (CardTemplate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Adaptive(minSize = 156.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(CardTemplate.entries, key = { it.name }) { template ->
                TemplatePreviewCard(
                    template = template,
                    selected = selectedTemplate == template,
                    onClick = { onSelect(template) },
                )
            }
        }
    }
}
