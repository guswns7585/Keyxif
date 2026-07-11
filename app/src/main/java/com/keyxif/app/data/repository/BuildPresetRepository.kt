package com.keyxif.app.data.repository

import android.content.Context
import android.net.Uri
import com.keyxif.app.domain.model.BuildPreset
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class BuildPresetRepository(context: Context) {
    private val preferences = context.getSharedPreferences("keyxif_build_presets", Context.MODE_PRIVATE)

    fun getAll(): List<BuildPreset> {
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

    fun save(
        presetName: String,
        buildInfo: KeyboardBuildInfo,
    ): BuildPreset {
        val now = System.currentTimeMillis()
        val preset = BuildPreset(
            id = UUID.randomUUID().toString(),
            presetName = presetName.trim().ifBlank { automaticName(buildInfo) },
            buildInfo = buildInfo,
            createdAt = now,
            updatedAt = now,
        )
        persist(listOf(preset) + getAll())
        return preset
    }

    fun delete(id: String) {
        persist(getAll().filterNot { it.id == id })
    }

    private fun persist(presets: List<BuildPreset>) {
        val array = JSONArray()
        presets.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    private fun automaticName(info: KeyboardBuildInfo): String {
        return listOf(
            info.housing.meaningfulBuildTextOrNull(),
            info.keycap.meaningfulBuildTextOrNull() ?: info.switchName.meaningfulBuildTextOrNull(),
        )
            .filterNotNull()
            .joinToString(" + ")
            .ifBlank { "새 빌드" }
    }

    private fun BuildPreset.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("presetName", presetName)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("buildInfo", buildInfo.toJson())
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
    }

    private fun JSONObject.toPreset(): BuildPreset {
        val info = getJSONObject("buildInfo")
        return BuildPreset(
            id = getString("id"),
            presetName = getString("presetName"),
            buildInfo = KeyboardBuildInfo(
                housing = info.optString("housing"),
                switchName = info.optString("switchName"),
                plate = info.optString("plate"),
                mount = info.optString("mount"),
                keycap = info.optString("keycap"),
                nickname = info.optString("nickname"),
                logoId = info.optNullableString("logoId"),
                customLogoUri = info.optNullableString("customLogoUri")?.let(Uri::parse),
            ),
            createdAt = optLong("createdAt"),
            updatedAt = optLong("updatedAt"),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private companion object {
        const val KEY_PRESETS = "presets"
    }
}
