package com.keyxif.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val KeyxifColors = lightColorScheme(
    primary = Color(0xFF161616),
    onPrimary = Color.White,
    secondary = Color(0xFF2F6F73),
    onSecondary = Color.White,
    tertiary = Color(0xFFB4504E),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF161616),
    surface = Color.White,
    onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFEAEDEA),
    onSurfaceVariant = Color(0xFF555A57),
    outline = Color(0xFFC8CCC8),
)

private val KeyxifShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun KeyxifTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeyxifColors,
        shapes = KeyxifShapes,
        content = content,
    )
}
