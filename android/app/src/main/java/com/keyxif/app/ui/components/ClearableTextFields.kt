package com.keyxif.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ClearableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        trailingIcon = combinedTrailingIcon(
            value = value,
            onClear = { onValueChange("") },
            trailingIcon = trailingIcon,
        ),
    )
}

@Composable
fun ClearableSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    labelText: String,
    modifier: Modifier = Modifier,
    placeholderText: String? = null,
) {
    ClearableTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(labelText) },
        placeholder = placeholderText?.let { { Text(it) } },
        singleLine = true,
    )
}

private fun combinedTrailingIcon(
    value: String,
    onClear: () -> Unit,
    trailingIcon: @Composable (() -> Unit)?,
): @Composable (() -> Unit)? {
    val showClear = value.isNotBlank()
    if (!showClear && trailingIcon == null) return null
    return {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showClear) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "입력 내용 지우기",
                    )
                }
            }
            trailingIcon?.invoke()
        }
    }
}
