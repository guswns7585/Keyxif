package com.keyxif.app.data.repository

import android.content.Context
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull

class RecentStore(context: Context) {
    private val preferences = context.getSharedPreferences("keyxif_recents", Context.MODE_PRIVATE)

    fun recentHousing(): List<String> = readList(KEY_HOUSING)

    fun recentSwitches(): List<String> = readList(KEY_SWITCH)

    fun recentKeycaps(): List<String> = readList(KEY_KEYCAP)

    fun recentNicknames(): List<String> = readList(KEY_NICKNAME)

    fun addHousing(value: String, limit: Int = DEFAULT_MAX_RECENTS) = addRecent(KEY_HOUSING, value, limit)

    fun addSwitch(value: String, limit: Int = DEFAULT_MAX_RECENTS) = addRecent(KEY_SWITCH, value, limit)

    fun addKeycap(value: String, limit: Int = DEFAULT_MAX_RECENTS) = addRecent(KEY_KEYCAP, value, limit)

    fun addNickname(value: String, limit: Int = DEFAULT_MAX_RECENTS) = addRecent(KEY_NICKNAME, value, limit)

    fun removeHousing(value: String) = removeRecent(KEY_HOUSING, value)

    fun removeSwitch(value: String) = removeRecent(KEY_SWITCH, value)

    fun removeKeycap(value: String) = removeRecent(KEY_KEYCAP, value)

    fun removeNickname(value: String) = removeRecent(KEY_NICKNAME, value)

    fun addBuildInfo(
        info: com.keyxif.app.domain.model.KeyboardBuildInfo,
        limit: Int = DEFAULT_MAX_RECENTS,
    ) {
        addHousing(info.housing, limit)
        addSwitch(info.switchName, limit)
        addKeycap(info.keycap, limit)
        addNickname(info.nickname, limit)
    }

    private fun addRecent(key: String, value: String, limit: Int) {
        val trimmed = value.meaningfulBuildTextOrNull() ?: return
        val updated = (listOf(trimmed) + readList(key))
            .distinctBy { it.lowercase() }
            .take(limit.coerceIn(10, 50))
        preferences.edit().putString(key, updated.joinToString(SEPARATOR)).apply()
    }

    private fun removeRecent(key: String, value: String) {
        val target = value.meaningfulBuildTextOrNull()?.lowercase() ?: return
        val updated = readList(key).filterNot { it.lowercase() == target }
        preferences.edit().putString(key, updated.joinToString(SEPARATOR)).apply()
    }

    private fun readList(key: String): List<String> {
        return preferences.getString(key, null)
            ?.split(SEPARATOR)
            ?.mapNotNull { it.meaningfulBuildTextOrNull() }
            .orEmpty()
    }

    private companion object {
        const val KEY_HOUSING = "housing"
        const val KEY_SWITCH = "switch"
        const val KEY_KEYCAP = "keycap"
        const val KEY_NICKNAME = "nickname"
        const val DEFAULT_MAX_RECENTS = 20
        const val SEPARATOR = "\u001F"
    }
}
