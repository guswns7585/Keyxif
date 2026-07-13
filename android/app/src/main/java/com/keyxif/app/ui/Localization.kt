package com.keyxif.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.keyxif.app.domain.model.AppLanguageMode
import java.util.Locale

internal enum class KeyxifLanguage {
    Korean,
    English,
}

@Composable
internal fun currentKeyxifLanguage(mode: AppLanguageMode): KeyxifLanguage {
    val configuration = LocalConfiguration.current
    val systemLanguage = runCatching { configuration.locales.get(0).language }
        .getOrDefault(Locale.getDefault().language)
    return when (mode) {
        AppLanguageMode.System -> if (systemLanguage.equals("ko", ignoreCase = true)) {
            KeyxifLanguage.Korean
        } else {
            KeyxifLanguage.English
        }
        AppLanguageMode.Korean -> KeyxifLanguage.Korean
        AppLanguageMode.English -> KeyxifLanguage.English
    }
}

internal fun KeyxifLanguage.text(
    ko: String,
    en: String,
): String = when (this) {
    KeyxifLanguage.Korean -> ko
    KeyxifLanguage.English -> en
}
