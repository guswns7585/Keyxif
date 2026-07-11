package com.keyxif.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.FileNameRule
import com.keyxif.app.domain.model.NicknameStyle
import com.keyxif.app.domain.model.OutputFormat
import com.keyxif.app.domain.model.PaletteAnalysisMode
import com.keyxif.app.domain.model.QualityPreset
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.keyxifSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "keyxif_settings",
)

class AppSettingsRepository(
    private val context: Context,
) {
    val settingsFlow: Flow<AppSettings> = context.keyxifSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decode)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.keyxifSettingsDataStore.edit { preferences ->
            val next = transform(decode(preferences)).normalized()
            preferences[Keys.WEBP_QUALITY] = next.webpQuality
            preferences[Keys.OUTPUT_FORMAT] = next.outputFormat.name
            preferences[Keys.KEEP_ORIGINAL_RESOLUTION] = next.keepOriginalResolution
            preferences[Keys.MAX_LONG_SIDE_PX] = next.maxLongSidePx ?: ORIGINAL_SIZE
            preferences[Keys.FILE_NAME_RULE] = next.fileNameRule.name
            preferences[Keys.SAVE_DIRECTORY_NAME] = next.saveDirectoryName
            preferences[Keys.OPEN_GALLERY_AFTER_SAVE] = next.openGalleryAfterSave
            preferences[Keys.SHOW_SAVE_TOAST] = next.showSaveToast
            preferences[Keys.SKIP_FAILED_ON_BATCH_SAVE] = next.skipFailedOnBatchSave
            preferences[Keys.REMEMBER_LAST_TEMPLATE] = next.rememberLastTemplate
            preferences[Keys.REMEMBER_LAST_NICKNAME] = next.rememberLastNickname
            preferences[Keys.RECENT_INPUT_LIMIT] = next.recentInputLimit
            preferences[Keys.COPY_PREVIOUS_BUILD_INFO_ON_ADD] = next.copyPreviousBuildInfoOnAdd
            preferences[Keys.DEFAULT_TEMPLATE] = next.defaultTemplate.name
            preferences[Keys.USE_CURRENT_PHOTO_FOR_TEMPLATE_PREVIEW] = next.useCurrentPhotoForTemplatePreview
            preferences[Keys.PROTECT_CENTER_AREA_FOR_OVERLAY] = next.protectCenterAreaForOverlay
            preferences[Keys.TEXT_SCALE] = next.textScale
            preferences[Keys.QUALITY_PRESET] = next.qualityPreset.name
            preferences[Keys.AUTO_RESTORE_DRAFT_SESSION] = next.autoRestoreDraftSession
            preferences[Keys.ASK_BEFORE_RESTORE_DRAFT] = next.askBeforeRestoreDraft
            preferences[Keys.NICKNAME_STYLE] = next.nicknameStyle.name
            preferences[Keys.NICKNAME_EMPHASIS] = next.nicknameEmphasis
            preferences[Keys.SHOW_SWITCH_PRESETS] = next.showSwitchPresets
            preferences[Keys.ENABLE_EXPORT_PREVIEW_ZOOM] = next.enableExportPreviewZoom
            preferences[Keys.SHOW_BUILD_INFO_IN_PLAIN_EXPORT] = next.showBuildInfoInPlainExport
            preferences[Keys.UPDATE_JSON_URL] = next.updateJsonUrl
            preferences[Keys.SHOW_PALETTE_COLORS] = next.showPaletteColors
            preferences[Keys.PALETTE_COLOR_COUNT] = next.paletteColorCount
            preferences[Keys.PALETTE_ANALYSIS_MODE] = next.paletteAnalysisMode.name
            preferences[Keys.AUTO_SELECT_LOGO_CONTRAST_VARIANT] = next.autoSelectLogoContrastVariant
        }
    }

    private fun decode(preferences: Preferences): AppSettings {
        return AppSettings(
            webpQuality = (preferences[Keys.WEBP_QUALITY] ?: 92).coerceIn(70, 100),
            outputFormat = enumValueOrDefault(preferences[Keys.OUTPUT_FORMAT], OutputFormat.WEBP),
            keepOriginalResolution = preferences[Keys.KEEP_ORIGINAL_RESOLUTION] ?: true,
            maxLongSidePx = (preferences[Keys.MAX_LONG_SIDE_PX] ?: ORIGINAL_SIZE).takeIf { it > 0 },
            fileNameRule = enumValueOrDefault(preferences[Keys.FILE_NAME_RULE], FileNameRule.KEYXIF_INDEX),
            saveDirectoryName = preferences[Keys.SAVE_DIRECTORY_NAME]?.ifBlank { "Keyxif" } ?: "Keyxif",
            openGalleryAfterSave = preferences[Keys.OPEN_GALLERY_AFTER_SAVE] ?: false,
            showSaveToast = preferences[Keys.SHOW_SAVE_TOAST] ?: true,
            skipFailedOnBatchSave = preferences[Keys.SKIP_FAILED_ON_BATCH_SAVE] ?: true,
            rememberLastTemplate = preferences[Keys.REMEMBER_LAST_TEMPLATE] ?: true,
            rememberLastNickname = preferences[Keys.REMEMBER_LAST_NICKNAME] ?: true,
            recentInputLimit = (preferences[Keys.RECENT_INPUT_LIMIT] ?: 20).coerceIn(10, 50),
            copyPreviousBuildInfoOnAdd = preferences[Keys.COPY_PREVIOUS_BUILD_INFO_ON_ADD] ?: false,
            defaultTemplate = enumValueOrDefault(preferences[Keys.DEFAULT_TEMPLATE], CardTemplate.ClassicFrame),
            useCurrentPhotoForTemplatePreview = preferences[Keys.USE_CURRENT_PHOTO_FOR_TEMPLATE_PREVIEW] ?: true,
            protectCenterAreaForOverlay = preferences[Keys.PROTECT_CENTER_AREA_FOR_OVERLAY] ?: true,
            textScale = preferences[Keys.TEXT_SCALE] ?: 1.0f,
            qualityPreset = enumValueOrDefault(preferences[Keys.QUALITY_PRESET], QualityPreset.Recommended),
            autoRestoreDraftSession = preferences[Keys.AUTO_RESTORE_DRAFT_SESSION] ?: true,
            askBeforeRestoreDraft = preferences[Keys.ASK_BEFORE_RESTORE_DRAFT] ?: true,
            nicknameStyle = enumValueOrDefault(preferences[Keys.NICKNAME_STYLE], NicknameStyle.Plain),
            nicknameEmphasis = preferences[Keys.NICKNAME_EMPHASIS] ?: 1.1f,
            showSwitchPresets = preferences[Keys.SHOW_SWITCH_PRESETS] ?: true,
            enableExportPreviewZoom = preferences[Keys.ENABLE_EXPORT_PREVIEW_ZOOM] ?: true,
            showBuildInfoInPlainExport = preferences[Keys.SHOW_BUILD_INFO_IN_PLAIN_EXPORT] ?: false,
            updateJsonUrl = preferences[Keys.UPDATE_JSON_URL].orEmpty(),
            showPaletteColors = preferences[Keys.SHOW_PALETTE_COLORS] ?: true,
            paletteColorCount = preferences[Keys.PALETTE_COLOR_COUNT] ?: 4,
            paletteAnalysisMode = enumValueOrDefault(
                preferences[Keys.PALETTE_ANALYSIS_MODE],
                PaletteAnalysisMode.CenterCrop,
            ),
            autoSelectLogoContrastVariant = preferences[Keys.AUTO_SELECT_LOGO_CONTRAST_VARIANT] ?: true,
        ).normalized()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String?,
        default: T,
    ): T = value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun AppSettings.normalized(): AppSettings {
        return copy(
            webpQuality = webpQuality.coerceIn(70, 100),
            recentInputLimit = when {
                recentInputLimit <= 10 -> 10
                recentInputLimit <= 20 -> 20
                else -> 50
            },
            maxLongSidePx = maxLongSidePx?.takeIf { it > 0 },
            saveDirectoryName = saveDirectoryName.ifBlank { "Keyxif" },
            textScale = textScale.coerceIn(0.85f, 1.35f),
            nicknameEmphasis = nicknameEmphasis.coerceIn(0.9f, 1.35f),
            updateJsonUrl = updateJsonUrl.trim(),
            paletteColorCount = paletteColorCount.coerceIn(3, 5),
        )
    }

    private object Keys {
        val WEBP_QUALITY = intPreferencesKey("webp_quality")
        val OUTPUT_FORMAT = stringPreferencesKey("output_format")
        val KEEP_ORIGINAL_RESOLUTION = booleanPreferencesKey("keep_original_resolution")
        val MAX_LONG_SIDE_PX = intPreferencesKey("max_long_side_px")
        val FILE_NAME_RULE = stringPreferencesKey("file_name_rule")
        val SAVE_DIRECTORY_NAME = stringPreferencesKey("save_directory_name")
        val OPEN_GALLERY_AFTER_SAVE = booleanPreferencesKey("open_gallery_after_save")
        val SHOW_SAVE_TOAST = booleanPreferencesKey("show_save_toast")
        val SKIP_FAILED_ON_BATCH_SAVE = booleanPreferencesKey("skip_failed_on_batch_save")
        val REMEMBER_LAST_TEMPLATE = booleanPreferencesKey("remember_last_template")
        val REMEMBER_LAST_NICKNAME = booleanPreferencesKey("remember_last_nickname")
        val RECENT_INPUT_LIMIT = intPreferencesKey("recent_input_limit")
        val COPY_PREVIOUS_BUILD_INFO_ON_ADD = booleanPreferencesKey("copy_previous_build_info_on_add")
        val DEFAULT_TEMPLATE = stringPreferencesKey("default_template")
        val USE_CURRENT_PHOTO_FOR_TEMPLATE_PREVIEW = booleanPreferencesKey("use_current_photo_for_template_preview")
        val PROTECT_CENTER_AREA_FOR_OVERLAY = booleanPreferencesKey("protect_center_area_for_overlay")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val QUALITY_PRESET = stringPreferencesKey("quality_preset")
        val AUTO_RESTORE_DRAFT_SESSION = booleanPreferencesKey("auto_restore_draft_session")
        val ASK_BEFORE_RESTORE_DRAFT = booleanPreferencesKey("ask_before_restore_draft")
        val NICKNAME_STYLE = stringPreferencesKey("nickname_style")
        val NICKNAME_EMPHASIS = floatPreferencesKey("nickname_emphasis")
        val SHOW_SWITCH_PRESETS = booleanPreferencesKey("show_switch_presets")
        val ENABLE_EXPORT_PREVIEW_ZOOM = booleanPreferencesKey("enable_export_preview_zoom")
        val SHOW_BUILD_INFO_IN_PLAIN_EXPORT = booleanPreferencesKey("show_build_info_in_plain_export")
        val UPDATE_JSON_URL = stringPreferencesKey("update_json_url")
        val SHOW_PALETTE_COLORS = booleanPreferencesKey("show_palette_colors")
        val PALETTE_COLOR_COUNT = intPreferencesKey("palette_color_count")
        val PALETTE_ANALYSIS_MODE = stringPreferencesKey("palette_analysis_mode")
        val AUTO_SELECT_LOGO_CONTRAST_VARIANT = booleanPreferencesKey("auto_select_logo_contrast_variant")
    }

    private companion object {
        const val ORIGINAL_SIZE = -1
    }
}
