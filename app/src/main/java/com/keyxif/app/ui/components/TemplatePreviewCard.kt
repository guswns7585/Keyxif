package com.keyxif.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.displayName
import com.keyxif.app.domain.model.shortDescription

@Composable
fun TemplatePreviewCard(
    template: CardTemplate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (selected) 3.dp else 0.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .background(Color(0xFFE6E8E6), RoundedCornerShape(6.dp)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFBFC7C2), Color(0xFF6C7675), Color(0xFF292F31)),
                            start = Offset.Zero,
                            end = Offset(w, h),
                        ),
                    )
                    when (template) {
                        CardTemplate.ClassicFrame -> {
                            drawRect(Color(0xFFF7F7F3), Offset(0f, h * 0.84f), Size(w, h * 0.16f))
                            drawRoundRect(Color(0xFF171717), Offset(w * 0.06f, h * 0.88f), Size(w * 0.1f, h * 0.07f), CornerRadius(4f, 4f))
                            repeat(3) {
                                drawRect(Color(0xFF343434), Offset(w * (0.22f + it * 0.23f), h * 0.89f), Size(w * 0.15f, 3f))
                                drawRect(Color(0xFF888888), Offset(w * (0.22f + it * 0.23f), h * 0.94f), Size(w * 0.11f, 2f))
                            }
                        }
                        CardTemplate.MinimalCaption -> {
                            drawRect(Color(0xFFFCFCF9), Offset(0f, h * 0.86f), Size(w, h * 0.14f))
                            drawRect(Color(0xFF282828), Offset(w * 0.08f, h * 0.9f), Size(w * 0.34f, 3f))
                            drawRect(Color(0xFF929292), Offset(w * 0.08f, h * 0.95f), Size(w * 0.24f, 2f))
                        }
                        CardTemplate.BottomSpecBar -> {
                            drawRect(Color(0xFF232626), Offset(0f, h * 0.89f), Size(w, h * 0.11f))
                            repeat(3) {
                                drawRect(Color.White.copy(alpha = 0.9f), Offset(w * (0.08f + it * 0.31f), h * 0.94f), Size(w * 0.18f, 3f))
                            }
                        }
                        CardTemplate.CornerMark -> {
                            drawRoundRect(Color.Black.copy(alpha = 0.58f), Offset(w * 0.64f, h * 0.06f), Size(w * 0.3f, h * 0.09f), CornerRadius(5f, 5f))
                            drawCircle(Color.White, radius = h * 0.025f, center = Offset(w * 0.7f, h * 0.105f))
                            drawRect(Color.White, Offset(w * 0.75f, h * 0.1f), Size(w * 0.13f, 3f))
                        }
                        CardTemplate.PosterMargin -> {
                            drawRect(Color(0xFFF8F8F5))
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFBFC7C2), Color(0xFF303638)),
                                ),
                                topLeft = Offset(w * 0.04f, h * 0.04f),
                                size = Size(w * 0.92f, h * 0.76f),
                            )
                            drawRect(Color(0xFF171717), Offset(w * 0.09f, h * 0.87f), Size(w * 0.34f, 4f))
                            drawRect(Color(0xFF777777), Offset(w * 0.09f, h * 0.93f), Size(w * 0.45f, 3f))
                        }
                        CardTemplate.DarkGlassStrip -> {
                            drawRect(Color.Black.copy(alpha = 0.7f), Offset(0f, h * 0.88f), Size(w, h * 0.12f))
                            drawRoundRect(Color.White, Offset(w * 0.05f, h * 0.91f), Size(w * 0.11f, h * 0.06f), CornerRadius(4f, 4f))
                            repeat(3) {
                                drawRect(Color.White, Offset(w * (0.22f + it * 0.24f), h * 0.94f), Size(w * 0.14f, 3f))
                            }
                        }
                        CardTemplate.SideSpecRail -> {
                            drawRect(Color(0xFFF3F4F1), Offset(w * 0.82f, 0f), Size(w * 0.18f, h))
                            drawRect(Color(0xFFD9DAD6), Offset(w * 0.82f, 0f), Size(2f, h))
                            drawRoundRect(
                                Color(0xFF181818),
                                Offset(w * 0.855f, h * 0.095f),
                                Size(w * 0.11f, h * 0.035f),
                                CornerRadius(6f, 6f),
                            )
                            repeat(4) {
                                drawRect(Color(0xFF343434), Offset(w * 0.85f, h * (0.3f + it * 0.12f)), Size(w * 0.1f, 3f))
                                drawRect(Color(0xFF8B8B8B), Offset(w * 0.85f, h * (0.34f + it * 0.12f)), Size(w * 0.08f, 2f))
                            }
                        }
                        CardTemplate.TopNameplate -> {
                            drawRect(Color(0xFFFAFAF7), Offset.Zero, Size(w, h * 0.13f))
                            drawCircle(Color(0xFF191919), radius = h * 0.035f, center = Offset(w * 0.1f, h * 0.065f))
                            drawRect(Color(0xFF242424), Offset(w * 0.18f, h * 0.045f), Size(w * 0.32f, 3f))
                            drawRect(Color(0xFF8A8A8A), Offset(w * 0.18f, h * 0.085f), Size(w * 0.42f, 2f))
                        }
                        CardTemplate.MuseumMat -> {
                            drawRect(Color(0xFFF6F5EF))
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFBDC5C1), Color(0xFF33383A)),
                                ),
                                topLeft = Offset(w * 0.06f, h * 0.06f),
                                size = Size(w * 0.88f, h * 0.68f),
                            )
                            drawRect(Color(0xFF232323), Offset(w * 0.08f, h * 0.84f), Size(w * 0.33f, 4f))
                            drawRect(Color(0xFF777777), Offset(w * 0.08f, h * 0.9f), Size(w * 0.54f, 3f))
                            drawCircle(Color(0xFF202020), radius = h * 0.035f, center = Offset(w * 0.86f, h * 0.86f))
                        }
                        CardTemplate.CompactTicket -> {
                            drawRect(Color(0xFFEDEEEA), Offset(0f, h * 0.86f), Size(w, h * 0.14f))
                            drawRoundRect(Color(0xFFFBFAF6), Offset(w * 0.04f, h * 0.88f), Size(w * 0.92f, h * 0.1f), CornerRadius(8f, 8f))
                            drawCircle(Color(0xFF1D1D1D), radius = h * 0.026f, center = Offset(w * 0.12f, h * 0.93f))
                            drawRect(Color(0xFF282828), Offset(w * 0.18f, h * 0.91f), Size(w * 0.32f, 3f))
                            drawRect(Color(0xFF8D8D8D), Offset(w * 0.18f, h * 0.95f), Size(w * 0.48f, 2f))
                        }
                        CardTemplate.CleanSignature -> {
                            drawRect(Color(0xFFFAFAF7), Offset(0f, h * 0.84f), Size(w, h * 0.16f))
                            drawRect(Color(0xFF151515), Offset(w * 0.08f, h * 0.88f), Size(w * 0.34f, 4f))
                            drawRect(Color(0xFF797C76), Offset(w * 0.08f, h * 0.93f), Size(w * 0.25f, 3f))
                            drawRect(Color(0xFF2E2F2C), Offset(w * 0.08f, h * 0.97f), Size(w * 0.42f, 3f))
                            drawCircle(Color(0xFF222222), radius = h * 0.03f, center = Offset(w * 0.87f, h * 0.91f))
                        }
                        CardTemplate.PlainExport -> {
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFBFC7C2), Color(0xFF6C7675), Color(0xFF292F31)),
                                    start = Offset.Zero,
                                    end = Offset(w, h),
                                ),
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.2f),
                                topLeft = Offset(w * 0.06f, h * 0.06f),
                                size = Size(w * 0.88f, h * 0.88f),
                                cornerRadius = CornerRadius(4f, 4f),
                                style = Stroke(width = 2f),
                            )
                        }
                    }
                    if (template in palettePreviewTemplates) {
                        drawPreviewPaletteChips(template, w, h)
                    }
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(1.5f, 1.5f),
                        size = Size(w - 3f, h - 3f),
                        cornerRadius = CornerRadius(8f, 8f),
                        style = Stroke(width = if (selected) 4f else 1.5f),
                    )
                }
            }
            Text(
                text = template.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = template.shortDescription(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val palettePreviewTemplates = setOf(
    CardTemplate.ClassicFrame,
    CardTemplate.MinimalCaption,
    CardTemplate.BottomSpecBar,
    CardTemplate.PosterMargin,
    CardTemplate.DarkGlassStrip,
    CardTemplate.SideSpecRail,
    CardTemplate.TopNameplate,
    CardTemplate.MuseumMat,
    CardTemplate.CompactTicket,
    CardTemplate.CleanSignature,
)

private val previewPaletteColors = listOf(
    Color(0xFF343A40),
    Color(0xFFE8E2D4),
    Color(0xFFB7C9BF),
    Color(0xFFFF8E68),
)

private fun DrawScope.drawPreviewPaletteChips(
    template: CardTemplate,
    w: Float,
    h: Float,
) {
    val chipSize = h * 0.029f
    val gap = h * 0.010f
    val totalWidth = previewPaletteColors.size * chipSize + (previewPaletteColors.size - 1) * gap
    val right = when (template) {
        CardTemplate.ClassicFrame,
        CardTemplate.MinimalCaption,
        CardTemplate.BottomSpecBar,
        CardTemplate.PosterMargin,
        CardTemplate.DarkGlassStrip,
        CardTemplate.TopNameplate,
        CardTemplate.MuseumMat,
        CardTemplate.CompactTicket,
        CardTemplate.CleanSignature -> w * 0.94f
        CardTemplate.SideSpecRail -> w * 0.965f
        else -> return
    }
    val centerY = when (template) {
        CardTemplate.ClassicFrame -> h * 0.95f
        CardTemplate.MinimalCaption -> h * 0.96f
        CardTemplate.BottomSpecBar -> h * 0.91f
        CardTemplate.PosterMargin -> h * 0.94f
        CardTemplate.DarkGlassStrip -> h * 0.94f
        CardTemplate.SideSpecRail -> h * 0.22f
        CardTemplate.TopNameplate -> h * 0.10f
        CardTemplate.MuseumMat -> h * 0.93f
        CardTemplate.CompactTicket -> h * 0.93f
        CardTemplate.CleanSignature -> h * 0.96f
        else -> return
    }
    var left = right - totalWidth
    previewPaletteColors.forEach { color ->
        drawCircle(
            color = color,
            radius = chipSize / 2f,
            center = Offset(left + chipSize / 2f, centerY),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.22f),
            radius = chipSize / 2f,
            center = Offset(left + chipSize / 2f, centerY),
            style = Stroke(width = 1.1f),
        )
        left += chipSize + gap
    }
}
