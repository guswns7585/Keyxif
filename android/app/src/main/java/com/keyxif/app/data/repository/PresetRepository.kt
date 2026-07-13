package com.keyxif.app.data.repository

import com.keyxif.app.data.presets.PresetData
import com.keyxif.app.domain.model.HousingPreset
import com.keyxif.app.domain.model.KeycapPreset
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.LogoPreset
import com.keyxif.app.domain.model.SwitchPreset
import com.keyxif.app.domain.model.VendorPreset
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull

data class PresetChoice<T>(
    val title: String,
    val subtitle: String? = null,
    val preset: T? = null,
    val isRecent: Boolean = false,
)

class PresetRepository {
    val plates: List<String> = PresetData.plates
    val mounts: List<String> = PresetData.mounts
    val switches: List<SwitchPreset> = PresetData.switches
    val logos: List<LogoPreset> = PresetData.logos

    fun housingById(id: String?) = PresetData.housings.firstOrNull { it.id == id }

    fun housingByName(value: String): HousingPreset? {
        val normalized = value.normalizedKey()
        if (normalized.isBlank()) return null
        return PresetData.housings.firstOrNull { preset ->
            preset.id.normalizedKey() == normalized ||
                preset.name.normalizedKey() == normalized ||
                preset.aliases.any { it.normalizedKey() == normalized }
        }
    }

    fun logoPreset(id: String?): LogoPreset? = PresetData.logos.firstOrNull { it.id == id }

    fun logoName(id: String?): String? = logoPreset(id)?.name

    fun logoIdForHousing(preset: HousingPreset): String? {
        return preset.logoId ?: vendorById(preset.vendorId)?.logoId ?: vendorByName(preset.vendor)?.logoId
    }

    fun logoForBuildInfo(info: KeyboardBuildInfo): LogoPreset? {
        if (info.logoDisabled) return null
        info.logoId?.let { logoPreset(it)?.let { logo -> return logo } }
        val housingText = info.housing.meaningfulBuildTextOrNull() ?: return null
        housingByName(housingText)?.let { housing ->
            logoIdForHousing(housing)?.let { logoId -> logoPreset(logoId)?.let { return it } }
        }
        return logoByText(housingText)
    }

    fun searchHousing(
        query: String,
        recentValues: List<String>,
    ): List<PresetChoice<HousingPreset>> {
        val normalized = query.trim()
        val normalizedQuery = normalized.normalizedKey()
        val recentChoices = recentValues
            .filter { it.matchesPresetQuery(normalized, normalizedQuery) || normalized.isBlank() }
            .map { PresetChoice<HousingPreset>(title = it, subtitle = "최근 사용", isRecent = true) }

        val presetChoices = PresetData.housings
            .filter { preset ->
                val vendor = vendorById(preset.vendorId)
                normalized.isBlank() ||
                    preset.name.matchesPresetQuery(normalized, normalizedQuery) ||
                    preset.vendor?.matchesPresetQuery(normalized, normalizedQuery) == true ||
                    preset.designer?.matchesPresetQuery(normalized, normalizedQuery) == true ||
                    preset.aliases.any { it.matchesPresetQuery(normalized, normalizedQuery) } ||
                    vendor?.aliases?.any { it.matchesPresetQuery(normalized, normalizedQuery) } == true
            }
            .sortedBy { it.searchRank(normalized, normalizedQuery) }
            .map { preset ->
                val maker = listOfNotNull(preset.vendor, preset.designer).distinct().joinToString(" / ")
                PresetChoice(title = preset.name, subtitle = maker.ifBlank { null }, preset = preset)
            }

        return (recentChoices + presetChoices)
            .distinctBy { "${it.title.normalizedKey()}|${it.subtitle.orEmpty().normalizedKey()}" }
            .take(resultLimit(normalized))
    }

    fun searchSwitch(
        query: String,
        recentValues: List<String>,
        includePresets: Boolean,
    ): List<PresetChoice<SwitchPreset>> {
        val normalized = query.trim()
        val normalizedQuery = normalized.normalizedKey()
        val recentChoices = recentValues
            .filter { it.matchesPresetQuery(normalized, normalizedQuery) || normalized.isBlank() }
            .map { PresetChoice<SwitchPreset>(title = it, subtitle = "최근 사용", isRecent = true) }

        if (!includePresets) return recentChoices.distinctBy { it.title.normalizedKey() }.take(resultLimit(normalized))

        val presetChoices = PresetData.switches
            .filter { preset ->
                normalized.isBlank() ||
                    preset.name.matchesPresetQuery(normalized, normalizedQuery) ||
                    preset.manufacturer?.matchesPresetQuery(normalized, normalizedQuery) == true ||
                    preset.aliases.any { it.matchesPresetQuery(normalized, normalizedQuery) }
            }
            .sortedBy { it.searchRank(normalized, normalizedQuery) }
            .map { preset ->
                PresetChoice(title = preset.name, subtitle = preset.manufacturer ?: "앱 지원", preset = preset)
            }

        return (recentChoices + presetChoices)
            .distinctBy { "${it.title.normalizedKey()}|${it.subtitle.orEmpty().normalizedKey()}" }
            .take(resultLimit(normalized))
    }

    fun searchKeycap(
        query: String,
        recentValues: List<String>,
    ): List<PresetChoice<KeycapPreset>> {
        val normalized = query.trim()
        val normalizedQuery = normalized.normalizedKey()
        val recentChoices = recentValues
            .filter { it.matchesPresetQuery(normalized, normalizedQuery) || normalized.isBlank() }
            .map { PresetChoice<KeycapPreset>(title = it, subtitle = "최근 사용", isRecent = true) }

        val presetChoices = PresetData.keycaps
            .filter { preset ->
                normalized.isBlank() ||
                    preset.name.matchesPresetQuery(normalized, normalizedQuery) ||
                    preset.manufacturer?.matchesPresetQuery(normalized, normalizedQuery) == true ||
                    preset.aliases.any { it.matchesPresetQuery(normalized, normalizedQuery) }
            }
            .sortedBy { it.searchRank(normalized, normalizedQuery) }
            .map { preset ->
                PresetChoice(title = preset.name, subtitle = preset.manufacturer, preset = preset)
            }

        return (recentChoices + presetChoices)
            .distinctBy { "${it.title.normalizedKey()}|${it.subtitle.orEmpty().normalizedKey()}" }
            .take(resultLimit(normalized))
    }

    private fun resultLimit(query: String): Int = if (query.isBlank()) 80 else 160

    private fun HousingPreset.searchRank(
        query: String,
        normalizedQuery: String,
    ): Int = minOf(
        name.searchRank(query, normalizedQuery),
        vendor?.searchRank(query, normalizedQuery) ?: Int.MAX_VALUE,
        designer?.searchRank(query, normalizedQuery) ?: Int.MAX_VALUE,
        aliases.minOfOrNull { it.searchRank(query, normalizedQuery) } ?: Int.MAX_VALUE,
    )

    private fun SwitchPreset.searchRank(
        query: String,
        normalizedQuery: String,
    ): Int = minOf(
        name.searchRank(query, normalizedQuery),
        manufacturer?.searchRank(query, normalizedQuery) ?: Int.MAX_VALUE,
        aliases.minOfOrNull { it.searchRank(query, normalizedQuery) } ?: Int.MAX_VALUE,
    )

    private fun KeycapPreset.searchRank(
        query: String,
        normalizedQuery: String,
    ): Int = minOf(
        name.searchRank(query, normalizedQuery),
        manufacturer?.searchRank(query, normalizedQuery) ?: Int.MAX_VALUE,
        aliases.minOfOrNull { it.searchRank(query, normalizedQuery) } ?: Int.MAX_VALUE,
    )

    private fun vendorById(id: String?): VendorPreset? = PresetData.vendors.firstOrNull { it.id == id }

    private fun vendorByName(value: String?): VendorPreset? {
        val normalized = value.orEmpty().normalizedKey()
        if (normalized.isBlank()) return null
        return PresetData.vendors.firstOrNull { vendor ->
            vendor.id.normalizedKey() == normalized ||
                vendor.name.normalizedKey() == normalized ||
                vendor.aliases.any { it.normalizedKey() == normalized }
        }
    }

    private fun logoByText(value: String): LogoPreset? {
        val normalized = value.normalizedKey()
        if (normalized.isBlank()) return null
        return PresetData.logos.firstOrNull { logo ->
            normalized.contains(logo.name.normalizedKey()) ||
                logo.aliases.any { normalized.contains(it.normalizedKey()) }
        }
    }

    private fun String.normalizedKey(): String {
        return trim().lowercase().replace(Regex("[^a-z0-9가-힣]+"), "")
    }

    private fun String.matchesPresetQuery(
        query: String,
        normalizedQuery: String,
    ): Boolean {
        if (query.isBlank()) return true
        if (contains(query, ignoreCase = true)) return true
        return normalizedQuery.isNotBlank() && normalizedKey().contains(normalizedQuery)
    }

    private fun String.searchRank(
        query: String,
        normalizedQuery: String,
    ): Int {
        if (query.isBlank()) return 0
        val normalizedValue = normalizedKey()
        return when {
            equals(query, ignoreCase = true) -> 0
            startsWith(query, ignoreCase = true) -> 1
            normalizedQuery.isNotBlank() && normalizedValue == normalizedQuery -> 2
            normalizedQuery.isNotBlank() && normalizedValue.startsWith(normalizedQuery) -> 3
            contains(query, ignoreCase = true) -> 4
            normalizedQuery.isNotBlank() && normalizedValue.contains(normalizedQuery) -> 5
            else -> Int.MAX_VALUE
        }
    }
}
