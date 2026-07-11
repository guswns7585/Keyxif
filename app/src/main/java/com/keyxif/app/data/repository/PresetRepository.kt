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
        val recentChoices = recentValues
            .filter { it.contains(normalized, ignoreCase = true) || normalized.isBlank() }
            .map { PresetChoice<HousingPreset>(title = it, subtitle = "최근 사용", isRecent = true) }

        val presetChoices = PresetData.housings
            .filter { preset ->
                val vendor = vendorById(preset.vendorId)
                normalized.isBlank() ||
                    preset.name.contains(normalized, ignoreCase = true) ||
                    preset.vendor?.contains(normalized, ignoreCase = true) == true ||
                    preset.designer?.contains(normalized, ignoreCase = true) == true ||
                    preset.aliases.any { it.contains(normalized, ignoreCase = true) } ||
                    vendor?.aliases?.any { it.contains(normalized, ignoreCase = true) } == true
            }
            .map { preset ->
                val maker = listOfNotNull(preset.vendor, preset.designer).distinct().joinToString(" / ")
                PresetChoice(title = preset.name, subtitle = maker.ifBlank { null }, preset = preset)
            }

        return (recentChoices + presetChoices)
            .distinctBy { it.title.normalizedKey() }
            .take(8)
    }

    fun searchSwitch(
        query: String,
        recentValues: List<String>,
        includePresets: Boolean,
    ): List<PresetChoice<SwitchPreset>> {
        val normalized = query.trim()
        val recentChoices = recentValues
            .filter { it.contains(normalized, ignoreCase = true) || normalized.isBlank() }
            .map { PresetChoice<SwitchPreset>(title = it, subtitle = "최근 사용", isRecent = true) }

        if (!includePresets) return recentChoices.distinctBy { it.title.normalizedKey() }.take(8)

        val presetChoices = PresetData.switches
            .filter { preset ->
                normalized.isBlank() ||
                    preset.name.contains(normalized, ignoreCase = true) ||
                    preset.manufacturer?.contains(normalized, ignoreCase = true) == true ||
                    preset.aliases.any { it.contains(normalized, ignoreCase = true) }
            }
            .map { preset ->
                PresetChoice(title = preset.name, subtitle = preset.manufacturer ?: "앱 지원", preset = preset)
            }

        return (recentChoices + presetChoices)
            .distinctBy { it.title.normalizedKey() }
            .take(10)
    }

    fun searchKeycap(
        query: String,
        recentValues: List<String>,
    ): List<PresetChoice<KeycapPreset>> {
        val normalized = query.trim()
        val recentChoices = recentValues
            .filter { it.contains(normalized, ignoreCase = true) || normalized.isBlank() }
            .map { PresetChoice<KeycapPreset>(title = it, subtitle = "최근 사용", isRecent = true) }

        val presetChoices = PresetData.keycaps
            .filter { preset ->
                normalized.isBlank() ||
                    preset.name.contains(normalized, ignoreCase = true) ||
                    preset.manufacturer?.contains(normalized, ignoreCase = true) == true ||
                    preset.aliases.any { it.contains(normalized, ignoreCase = true) }
            }
            .map { preset ->
                PresetChoice(title = preset.name, subtitle = preset.manufacturer, preset = preset)
            }

        return (recentChoices + presetChoices)
            .distinctBy { it.title.normalizedKey() }
            .take(8)
    }

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
}
