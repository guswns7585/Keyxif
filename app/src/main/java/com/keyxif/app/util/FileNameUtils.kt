package com.keyxif.app.util

import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.FileNameRule
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.OutputFormat
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull

object FileNameUtils {
    fun outputName(
        buildInfo: KeyboardBuildInfo,
        index: Int,
        settings: AppSettings,
    ): String {
        val number = index.toString().padStart(2, '0')
        val rawPrefix = when (settings.fileNameRule) {
            FileNameRule.KEYXIF_INDEX -> "Keyxif"
            FileNameRule.HOUSING_INDEX -> buildInfo.housing.meaningfulBuildTextOrNull().orEmpty()
            FileNameRule.NICKNAME_INDEX -> buildInfo.nickname.meaningfulBuildTextOrNull().orEmpty()
            FileNameRule.HOUSING_KEYCAP_INDEX -> listOf(buildInfo.housing, buildInfo.keycap)
                .mapNotNull { it.meaningfulBuildTextOrNull() }
                .joinToString("_")
        }
        val prefix = sanitize(rawPrefix).ifBlank { "Keyxif" }
        return "${prefix}_${number}.${settings.outputFormat.extension}"
    }

    fun outputName(
        buildName: String,
        index: Int,
    ): String {
        val prefix = sanitize(buildName).ifBlank { "Keyxif" }
        return "${prefix}_Keyxif_${index.toString().padStart(2, '0')}.webp"
    }

    fun sanitize(value: String): String {
        return value
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\s+"), "_")
            .take(64)
    }

    private val OutputFormat.extension: String
        get() = when (this) {
            OutputFormat.WEBP -> "webp"
            OutputFormat.PNG -> "png"
        }
}
