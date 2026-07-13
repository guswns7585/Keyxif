package com.keyxif.app.domain.renderer

import android.graphics.Paint

object TextDrawUtils {
    fun ellipsize(
        text: String,
        paint: Paint,
        maxWidth: Float,
    ): String {
        val value = text.trim()
        if (value.isBlank() || maxWidth <= 0f) return ""
        if (paint.measureText(value) <= maxWidth) return value
        val suffix = "..."
        if (paint.measureText(suffix) > maxWidth) return ""
        var end = value.length
        while (end > 1 && paint.measureText(value.substring(0, end) + suffix) > maxWidth) {
            end--
        }
        return value.substring(0, end).trimEnd() + suffix
    }
}
