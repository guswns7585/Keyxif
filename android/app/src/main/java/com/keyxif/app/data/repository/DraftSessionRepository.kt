package com.keyxif.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.AppLanguageMode
import com.keyxif.app.domain.model.AppStep
import com.keyxif.app.domain.model.AppThemeMode
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.CropState
import com.keyxif.app.domain.model.DraftSession
import com.keyxif.app.domain.model.FileNameRule
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.NicknameStyle
import com.keyxif.app.domain.model.MaskStroke
import com.keyxif.app.domain.model.NormalizedPoint
import com.keyxif.app.domain.model.NormalizedRect
import com.keyxif.app.domain.model.NormalizedQuad
import com.keyxif.app.domain.model.toQuad
import com.keyxif.app.domain.model.OutputFormat
import com.keyxif.app.domain.model.PaletteAnalysisMode
import com.keyxif.app.domain.model.PhotoAnalysisResult
import com.keyxif.app.domain.model.PhotoItem
import com.keyxif.app.domain.model.PhotoRenderStyle
import com.keyxif.app.domain.model.QualityPreset
import com.keyxif.app.domain.model.TemplateFont
import com.keyxif.app.domain.model.RenderStatus
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.keyxifDraftDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "keyxif_draft_session",
)

class DraftSessionRepository(
    private val context: Context,
) {
    val draftFlow: Flow<DraftSession?> = context.keyxifDraftDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[Keys.DRAFT_JSON]?.let(::decode)
        }

    suspend fun getDraft(): DraftSession? = draftFlow.first()

    suspend fun save(session: DraftSession) {
        context.keyxifDraftDataStore.edit { preferences ->
            preferences[Keys.DRAFT_JSON] = encode(session.copy(lastUpdatedAt = System.currentTimeMillis())).toString()
        }
    }

    suspend fun clear() {
        context.keyxifDraftDataStore.edit { preferences ->
            preferences.remove(Keys.DRAFT_JSON)
        }
    }

    private fun encode(session: DraftSession): JSONObject = JSONObject().apply {
        put("selectedTemplate", session.selectedTemplate.name)
        put("currentStep", session.currentStep.name)
        put("selectedPhotoId", session.selectedPhotoId)
        put("lastUpdatedAt", session.lastUpdatedAt)
        put("settings", session.settings.toJson())
        put(
            "photos",
            JSONArray().apply {
                session.photoItems.forEach { photo ->
                    put(photo.toJson())
                }
            },
        )
    }

    private fun decode(raw: String): DraftSession? {
        return runCatching {
            val json = JSONObject(raw)
            val photos = json.optJSONArray("photos") ?: JSONArray()
            DraftSession(
                photoItems = buildList {
                    for (index in 0 until photos.length()) {
                        add(photos.getJSONObject(index).toPhotoItem())
                    }
                },
                selectedTemplate = enumValueOrDefault(json.optString("selectedTemplate"), CardTemplate.ClassicFrame),
                currentStep = decodeStep(json.optString("currentStep")),
                selectedPhotoId = json.optNullableString("selectedPhotoId"),
                settings = json.optJSONObject("settings")?.toSettings() ?: AppSettings(),
                lastUpdatedAt = json.optLong("lastUpdatedAt"),
            )
        }.getOrNull()
    }

    private fun PhotoItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("uri", uri.toString())
        put("displayName", displayName)
        put("cropState", cropState.toJson())
        put("buildInfo", buildInfo.toJson())
        put("analysisResult", analysisResult.toJson())
        put("renderStyle", renderStyle.toJson())
    }

    private fun JSONObject.toPhotoItem(): PhotoItem {
        return PhotoItem(
            id = optString("id"),
            uri = Uri.parse(optString("uri")),
            displayName = optString("displayName").ifBlank { "사진" },
            cropState = optJSONObject("cropState")?.toCropState() ?: CropState(),
            buildInfo = optJSONObject("buildInfo")?.toBuildInfo() ?: KeyboardBuildInfo(),
            analysisResult = optJSONObject("analysisResult")?.toAnalysisResult() ?: PhotoAnalysisResult(),
            renderStyle = optJSONObject("renderStyle")?.toRenderStyle() ?: PhotoRenderStyle(),
            renderStatus = RenderStatus.Idle,
        )
    }

    private fun CropState.toJson(): JSONObject = JSONObject().apply {
        put("scale", scale)
        put("offsetX", offsetX)
        put("offsetY", offsetY)
    }

    private fun JSONObject.toCropState(): CropState {
        return CropState(
            scale = optDouble("scale", 1.0).toFloat(),
            offsetX = optDouble("offsetX", 0.0).toFloat(),
            offsetY = optDouble("offsetY", 0.0).toFloat(),
        )
    }

    private fun KeyboardBuildInfo.toJson(): JSONObject = JSONObject().apply {
        put("housing", housing)
        put("switchName", switchName)
        put("plate", plate)
        put("mount", mount)
        put("keycap", keycap)
        put("nickname", nickname)
        put("logoId", logoId)
        put("customLogoUri", customLogoUri?.toString())
        put("logoDisabled", logoDisabled)
    }

    private fun PhotoAnalysisResult.toJson(): JSONObject = JSONObject().apply {
        put(
            "paletteColors",
            JSONArray().apply {
                paletteColors.forEach(::put)
            },
        )
        put("analyzedAt", analyzedAt)
        put("isAnalyzing", false)
        put("errorMessage", errorMessage)
        put("analysisMode", analysisMode?.name)
        put("analysisCenterCropRatio", analysisCenterCropRatio)
        put("analysisRectNormalized", analysisRectNormalized?.toJson())
        put("analysisQuadNormalized", analysisQuadNormalized?.toJson())
        put("paintedMaskStrokes", JSONArray().apply { paintedMaskStrokes.forEach { put(it.toJson()) } })
    }

    private fun JSONObject.toAnalysisResult(): PhotoAnalysisResult {
        return PhotoAnalysisResult(
            paletteColors = optJSONArray("paletteColors").toColorList(),
            analyzedAt = optLong("analyzedAt", 0L),
            isAnalyzing = false,
            errorMessage = optNullableString("errorMessage"),
            analysisMode = parsePaletteMode(optNullableString("analysisMode")),
            analysisCenterCropRatio = optDouble("analysisCenterCropRatio", 0.75).toFloat(),
            analysisRectNormalized = optJSONObject("analysisRectNormalized")?.toNormalizedRect(),
            analysisQuadNormalized = optJSONObject("analysisQuadNormalized")?.toNormalizedQuad()
                ?: optJSONObject("analysisRectNormalized")?.toNormalizedRect()?.toQuad(),
            paintedMaskStrokes = optJSONArray("paintedMaskStrokes").toMaskStrokes(),
        )
    }

    private fun NormalizedRect.toJson(): JSONObject = JSONObject().apply {
        put("left", left); put("top", top); put("right", right); put("bottom", bottom)
    }

    private fun JSONObject.toNormalizedRect(): NormalizedRect = NormalizedRect(
        left = optDouble("left", 0.15).toFloat(), top = optDouble("top", 0.39).toFloat(),
        right = optDouble("right", 0.85).toFloat(), bottom = optDouble("bottom", 0.61).toFloat(),
    ).normalized()

    private fun NormalizedQuad.toJson(): JSONObject = JSONObject().apply {
        put("topLeft", topLeft.toJson())
        put("topRight", topRight.toJson())
        put("bottomRight", bottomRight.toJson())
        put("bottomLeft", bottomLeft.toJson())
    }

    private fun NormalizedPoint.toJson(): JSONObject = JSONObject().apply {
        put("x", x); put("y", y)
    }

    private fun JSONObject.toNormalizedQuad(): NormalizedQuad = NormalizedQuad(
        topLeft = optJSONObject("topLeft").toNormalizedPoint(0.15f, 0.39f),
        topRight = optJSONObject("topRight").toNormalizedPoint(0.85f, 0.39f),
        bottomRight = optJSONObject("bottomRight").toNormalizedPoint(0.85f, 0.61f),
        bottomLeft = optJSONObject("bottomLeft").toNormalizedPoint(0.15f, 0.61f),
    ).normalized()

    private fun JSONObject?.toNormalizedPoint(defaultX: Float, defaultY: Float): NormalizedPoint = NormalizedPoint(
        x = this?.optDouble("x", defaultX.toDouble())?.toFloat() ?: defaultX,
        y = this?.optDouble("y", defaultY.toDouble())?.toFloat() ?: defaultY,
    )

    private fun MaskStroke.toJson(): JSONObject = JSONObject().apply {
        put("brushSizeNormalized", brushSizeNormalized); put("isEraser", isEraser)
        put("points", JSONArray().apply { points.forEach { put(JSONObject().apply { put("x", it.x); put("y", it.y) }) } })
    }

    private fun JSONArray?.toMaskStrokes(): List<MaskStroke> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) {
            val stroke = optJSONObject(index) ?: continue
            val pointsJson = stroke.optJSONArray("points") ?: JSONArray()
            val points = buildList {
                for (pointIndex in 0 until pointsJson.length()) {
                    val point = pointsJson.optJSONObject(pointIndex) ?: continue
                    add(NormalizedPoint(point.optDouble("x").toFloat().coerceIn(0f, 1f), point.optDouble("y").toFloat().coerceIn(0f, 1f)))
                }
            }
            if (points.isNotEmpty()) add(MaskStroke(points, stroke.optDouble("brushSizeNormalized", 0.06).toFloat(), stroke.optBoolean("isEraser")))
        }
    }

    private fun parsePaletteMode(value: String?): PaletteAnalysisMode = when (value) {
        "RectSelection", "TapAutoBox", "ManualBox" -> PaletteAnalysisMode.RectSelection
        "PaintedMask" -> PaletteAnalysisMode.PaintedMask
        else -> PaletteAnalysisMode.AutoCenter
    }

    private fun PhotoRenderStyle.toJson(): JSONObject = JSONObject().apply {
        put("usePaletteColorForCardBackground", usePaletteColorForCardBackground)
        put("paletteBackgroundColorIndex", paletteBackgroundColorIndex)
        put("customCardBackgroundColor", customCardBackgroundColor ?: JSONObject.NULL)
        put("usePaletteColorForText", usePaletteColorForText)
        put("paletteTextColorIndex", paletteTextColorIndex)
        put("customTextColor", customTextColor ?: JSONObject.NULL)
    }

    private fun JSONObject.toRenderStyle(): PhotoRenderStyle {
        return PhotoRenderStyle(
            usePaletteColorForCardBackground = optBoolean("usePaletteColorForCardBackground", false),
            paletteBackgroundColorIndex = optInt("paletteBackgroundColorIndex", 0).coerceIn(0, 4),
            customCardBackgroundColor = optNullableColor("customCardBackgroundColor"),
            usePaletteColorForText = optBoolean("usePaletteColorForText", false),
            paletteTextColorIndex = optInt("paletteTextColorIndex", 0).coerceIn(0, 4),
            customTextColor = optNullableColor("customTextColor"),
        )
    }

    private fun JSONObject.toBuildInfo(): KeyboardBuildInfo {
        return KeyboardBuildInfo(
            housing = optString("housing"),
            switchName = optString("switchName"),
            plate = optString("plate"),
            mount = optString("mount"),
            keycap = optString("keycap"),
            nickname = optString("nickname"),
            logoId = optNullableString("logoId"),
            customLogoUri = optNullableString("customLogoUri")?.let(Uri::parse),
            logoDisabled = optBoolean("logoDisabled", false),
        )
    }

    private fun AppSettings.toJson(): JSONObject = JSONObject().apply {
        put("webpQuality", webpQuality)
        put("outputFormat", outputFormat.name)
        put("keepOriginalResolution", keepOriginalResolution)
        put("maxLongSidePx", maxLongSidePx)
        put("fileNameRule", fileNameRule.name)
        put("saveDirectoryName", saveDirectoryName)
        put("openGalleryAfterSave", openGalleryAfterSave)
        put("showSaveToast", showSaveToast)
        put("skipFailedOnBatchSave", skipFailedOnBatchSave)
        put("rememberLastTemplate", rememberLastTemplate)
        put("rememberLastNickname", rememberLastNickname)
        put("recentInputLimit", recentInputLimit)
        put("copyPreviousBuildInfoOnAdd", copyPreviousBuildInfoOnAdd)
        put("defaultTemplate", defaultTemplate.name)
        put("useCurrentPhotoForTemplatePreview", useCurrentPhotoForTemplatePreview)
        put("protectCenterAreaForOverlay", protectCenterAreaForOverlay)
        put("textScale", textScale)
        put("qualityPreset", qualityPreset.name)
        put("autoRestoreDraftSession", autoRestoreDraftSession)
        put("askBeforeRestoreDraft", askBeforeRestoreDraft)
        put("nicknameStyle", nicknameStyle.name)
        put("nicknameEmphasis", nicknameEmphasis)
        put("showSwitchPresets", showSwitchPresets)
        put("enableExportPreviewZoom", enableExportPreviewZoom)
        put("showBuildInfoInPlainExport", showBuildInfoInPlainExport)
        put("updateJsonUrl", updateJsonUrl)
        put("showPaletteColors", showPaletteColors)
        put("paletteColorCount", paletteColorCount)
        put("paletteAnalysisMode", paletteAnalysisMode.name)
        put("paletteCenterCropRatio", paletteCenterCropRatio)
        put("usePaletteColorForCardBackground", usePaletteColorForCardBackground)
        put("paletteBackgroundColorIndex", paletteBackgroundColorIndex)
        put("autoSelectLogoContrastVariant", autoSelectLogoContrastVariant)
        put("languageMode", languageMode.name)
        put("themeMode", themeMode.name)
        put("templateFont", templateFont.name)
    }

    private fun JSONObject.toSettings(): AppSettings {
        return AppSettings(
            webpQuality = optInt("webpQuality", 92),
            outputFormat = enumValueOrDefault(optString("outputFormat"), OutputFormat.WEBP),
            keepOriginalResolution = optBoolean("keepOriginalResolution", true),
            maxLongSidePx = optNullableInt("maxLongSidePx"),
            fileNameRule = enumValueOrDefault(optString("fileNameRule"), FileNameRule.KEYXIF_INDEX),
            saveDirectoryName = optString("saveDirectoryName", "Keyxif"),
            openGalleryAfterSave = optBoolean("openGalleryAfterSave", false),
            showSaveToast = optBoolean("showSaveToast", true),
            skipFailedOnBatchSave = optBoolean("skipFailedOnBatchSave", true),
            rememberLastTemplate = optBoolean("rememberLastTemplate", true),
            rememberLastNickname = optBoolean("rememberLastNickname", true),
            recentInputLimit = optInt("recentInputLimit", 20),
            copyPreviousBuildInfoOnAdd = optBoolean("copyPreviousBuildInfoOnAdd", false),
            defaultTemplate = enumValueOrDefault(optString("defaultTemplate"), CardTemplate.ClassicFrame),
            useCurrentPhotoForTemplatePreview = optBoolean("useCurrentPhotoForTemplatePreview", true),
            protectCenterAreaForOverlay = optBoolean("protectCenterAreaForOverlay", true),
            textScale = optDouble("textScale", 1.12).toFloat(),
            qualityPreset = enumValueOrDefault(optString("qualityPreset"), QualityPreset.Recommended),
            autoRestoreDraftSession = optBoolean("autoRestoreDraftSession", true),
            askBeforeRestoreDraft = optBoolean("askBeforeRestoreDraft", true),
            nicknameStyle = enumValueOrDefault(optString("nicknameStyle"), NicknameStyle.Plain),
            nicknameEmphasis = optDouble("nicknameEmphasis", 1.1).toFloat(),
            showSwitchPresets = optBoolean("showSwitchPresets", true),
            enableExportPreviewZoom = optBoolean("enableExportPreviewZoom", true),
            showBuildInfoInPlainExport = optBoolean("showBuildInfoInPlainExport", false),
            updateJsonUrl = optString("updateJsonUrl"),
            showPaletteColors = optBoolean("showPaletteColors", true),
            paletteColorCount = optInt("paletteColorCount", 4),
            paletteAnalysisMode = enumValueOrDefault(
                optString("paletteAnalysisMode"),
                PaletteAnalysisMode.AutoCenter,
            ),
            paletteCenterCropRatio = optDouble("paletteCenterCropRatio", 0.75).toFloat(),
            usePaletteColorForCardBackground = optBoolean("usePaletteColorForCardBackground", false),
            paletteBackgroundColorIndex = optInt("paletteBackgroundColorIndex", 0),
            autoSelectLogoContrastVariant = optBoolean("autoSelectLogoContrastVariant", true),
            languageMode = enumValueOrDefault(optString("languageMode"), AppLanguageMode.System),
            themeMode = enumValueOrDefault(optString("themeMode"), AppThemeMode.System),
            templateFont = enumValueOrDefault(optString("templateFont"), TemplateFont.System),
        )
    }

    private fun decodeStep(value: String?): AppStep {
        return when (value) {
            "Adjust" -> AppStep.Template
            else -> enumValueOrDefault(value, AppStep.Photos)
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (isNull(key)) return null
        return optInt(key).takeIf { it > 0 }
    }

    private fun JSONObject.optNullableColor(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONArray?.toColorList(): List<Int> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optInt(index))
            }
        }.filter { it != 0 }
    }

    private object Keys {
        val DRAFT_JSON = stringPreferencesKey("draft_json")
    }
}
