package com.keyxif.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keyxif.app.data.repository.PresetChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PresetSearchField(
    label: String,
    value: String,
    placeholder: String,
    options: (String) -> List<PresetChoice<T>>,
    onValueChange: (String) -> Unit,
    onOptionSelected: (PresetChoice<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(value) { mutableStateOf(value) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClearableTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            trailingIcon = {
                TextButton(
                    modifier = Modifier.widthIn(min = 56.dp),
                    onClick = {
                        query = value
                        showPicker = true
                    },
                ) {
                    Text("검색")
                }
            },
        )
    }

    if (showPicker) {
        val pickerOptions = options(query)
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "$label 선택",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                ClearableSearchField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    labelText = "$label 검색",
                    placeholderText = placeholder,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = query.isNotBlank(),
                    onClick = {
                        onValueChange(query)
                        showPicker = false
                    },
                ) {
                    Text("직접 입력값 사용")
                }
                if (pickerOptions.isEmpty()) {
                    Text(
                        text = "검색 결과가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "최근 사용 / 앱 내장 목록",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(
                    pickerOptions,
                    key = { "${it.title}-${it.subtitle}-${it.isRecent}" },
                    contentType = { "preset_choice" },
                ) { option ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = option.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (option.isRecent) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        supportingContent = option.subtitle?.let { subtitle ->
                            { Text(text = subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    onOptionSelected(option)
                                    showPicker = false
                                },
                            ) {
                                Text(if (option.preset == null) "사용" else "선택")
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            }
        }
    }
}

@Composable
fun RecentChips(
    title: String,
    values: List<String>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.take(3).forEach { value ->
                AssistChip(
                    onClick = { onClick(value) },
                    label = {
                        Text(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            text = value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}
