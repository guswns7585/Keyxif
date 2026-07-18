package com.keyxif.app.data.repository

import android.content.Context
import com.keyxif.app.domain.model.ColorPreset
import com.keyxif.app.domain.model.PhotoRenderStyle
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class ColorPresetRepository(context: Context) {
    private val preferences = context.getSharedPreferences("keyxif_color_presets", Context.MODE_PRIVATE)

    fun getAll(): List<ColorPreset> {
        val raw = preferences.getString(KEY_PRESETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toPreset())
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun save(presetName: String, renderStyle: PhotoRenderStyle): ColorPreset {
        val now = System.currentTimeMillis()
        val preset = ColorPreset(
            id = UUID.randomUUID().toString(),
            presetName = presetName.trim().ifBlank { "색상 프리셋 ${getAll().size + 1}" },
            renderStyle = renderStyle.colorPresetFieldsOnly(),
            createdAt = now,
            updatedAt = now,
        )
        persist(listOf(preset) + getAll())
        return preset
    }

    fun delete(id: String) {
        persist(getAll().filterNot { it.id == id })
    }

    fun getRecentCustomColors(): List<Int> {
        val raw = preferences.getString(KEY_RECENT_CUSTOM_COLORS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optNullableColor(index)?.let { add(it) }
                }
            }.distinct().take(MAX_RECENT_COLORS)
        }.getOrDefault(emptyList())
    }

    fun rememberCustomColor(color: Int) {
        val opaqueColor = color or ColorOpaqueMask
        val next = listOf(opaqueColor) + getRecentCustomColors().filterNot { it == opaqueColor }
        persistRecentCustomColors(next)
    }

    fun deleteRecentCustomColor(color: Int) {
        persistRecentCustomColors(getRecentCustomColors().filterNot { it == color })
    }

    private fun persist(presets: List<ColorPreset>) {
        val array = JSONArray()
        presets.take(MAX_PRESETS).forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    private fun persistRecentCustomColors(colors: List<Int>) {
        val array = JSONArray()
        colors.distinct().take(MAX_RECENT_COLORS).forEach { array.put(it) }
        preferences.edit().putString(KEY_RECENT_CUSTOM_COLORS, array.toString()).apply()
    }

    private fun ColorPreset.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("presetName", presetName)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("renderStyle", renderStyle.toJson())
    }

    private fun PhotoRenderStyle.toJson(): JSONObject = JSONObject().apply {
        put("usePaletteColorForCardBackground", usePaletteColorForCardBackground)
        put("paletteBackgroundColorIndex", paletteBackgroundColorIndex)
        put("customCardBackgroundColor", customCardBackgroundColor ?: JSONObject.NULL)
        put("usePaletteColorForText", usePaletteColorForText)
        put("paletteTextColorIndex", paletteTextColorIndex)
        put("customTextColor", customTextColor ?: JSONObject.NULL)
    }

    private fun JSONObject.toPreset(): ColorPreset {
        val style = optJSONObject("renderStyle") ?: JSONObject()
        return ColorPreset(
            id = optString("id").ifBlank { UUID.randomUUID().toString() },
            presetName = optString("presetName").ifBlank { "색상 프리셋" },
            renderStyle = PhotoRenderStyle(
                usePaletteColorForCardBackground = style.optBoolean("usePaletteColorForCardBackground", false),
                paletteBackgroundColorIndex = style.optInt("paletteBackgroundColorIndex", 0).coerceIn(0, 4),
                customCardBackgroundColor = style.optNullableColor("customCardBackgroundColor"),
                usePaletteColorForText = style.optBoolean("usePaletteColorForText", false),
                paletteTextColorIndex = style.optInt("paletteTextColorIndex", 0).coerceIn(0, 4),
                customTextColor = style.optNullableColor("customTextColor"),
            ),
            createdAt = optLong("createdAt", 0L),
            updatedAt = optLong("updatedAt", 0L),
        )
    }

    private fun JSONObject.optNullableColor(key: String): Int? {
        if (isNull(key)) return null
        return opt(key)?.let {
            when (it) {
                is Number -> it.toInt()
                is String -> it.toIntOrNull()
                else -> null
            }
        }
    }

    private fun JSONArray.optNullableColor(index: Int): Int? {
        if (isNull(index)) return null
        return opt(index)?.let {
            when (it) {
                is Number -> it.toInt()
                is String -> it.toIntOrNull()
                else -> null
            }
        }
    }

    private fun PhotoRenderStyle.colorPresetFieldsOnly(): PhotoRenderStyle = PhotoRenderStyle(
        usePaletteColorForCardBackground = usePaletteColorForCardBackground,
        paletteBackgroundColorIndex = paletteBackgroundColorIndex.coerceIn(0, 4),
        customCardBackgroundColor = customCardBackgroundColor,
        usePaletteColorForText = usePaletteColorForText,
        paletteTextColorIndex = paletteTextColorIndex.coerceIn(0, 4),
        customTextColor = customTextColor,
    )

    private companion object {
        const val KEY_PRESETS = "presets"
        const val KEY_RECENT_CUSTOM_COLORS = "recent_custom_colors"
        const val MAX_PRESETS = 50
        const val MAX_RECENT_COLORS = 24
        const val ColorOpaqueMask = -0x1000000
    }
}
