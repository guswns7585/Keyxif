package com.keyxif.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 잉크 + 웜 페이퍼 + 코퍼 포인트. 키보드 빌드 시트 느낌의 차분한 톤.
private val Ink = Color(0xFF1C1B17)
private val Paper = Color(0xFFF7F5F0)
private val Copper = Color(0xFFA85D2B)

private val KeyxifColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFBFAF7),
    primaryContainer = Color(0xFFE9E5DC),
    onPrimaryContainer = Ink,
    secondary = Copper,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E5D4),
    onSecondaryContainer = Color(0xFF4A2A0E),
    tertiary = Color(0xFF50604F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7DC),
    onTertiaryContainer = Color(0xFF20301F),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFEFB),
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE9E1),
    onSurfaceVariant = Color(0xFF5E5A50),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFCF8),
    surfaceContainer = Color(0xFFF2F0EA),
    surfaceContainerHigh = Color(0xFFEDEAE3),
    surfaceContainerHighest = Color(0xFFE7E4DC),
    surfaceTint = Color(0xFF8A8376),
    outline = Color(0xFFCFCBC0),
    outlineVariant = Color(0xFFE4E1D8),
    error = Color(0xFFA83A32),
    onError = Color.White,
    errorContainer = Color(0xFFF7E0DD),
    onErrorContainer = Color(0xFF541F1A),
)

private val KeyxifShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val KeyxifTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        ),
        titleSmall = base.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        bodyMedium = base.bodyMedium.copy(
            lineHeight = 21.sp,
        ),
    )
}

@Composable
fun KeyxifTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeyxifColors,
        shapes = KeyxifShapes,
        typography = KeyxifTypography,
        content = content,
    )
}
