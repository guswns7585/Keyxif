package com.keyxif.app.data.repository

import android.content.Context
import com.keyxif.app.domain.model.BackgroundFrame
import com.keyxif.app.domain.model.BuildFieldFormat
import com.keyxif.app.domain.model.BuildInfoField
import com.keyxif.app.domain.model.CUSTOM_TEMPLATE_VERSION
import com.keyxif.app.domain.model.CanvasCoordinateSpace
import com.keyxif.app.domain.model.CanvasElement
import com.keyxif.app.domain.model.CanvasElementType
import com.keyxif.app.domain.model.CardStyle
import com.keyxif.app.domain.model.ColorChipShape
import com.keyxif.app.domain.model.CustomPhotoFitMode
import com.keyxif.app.domain.model.CustomPhotoPlacement
import com.keyxif.app.domain.model.CustomTemplate
import com.keyxif.app.domain.model.ElementContent
import com.keyxif.app.domain.model.ElementFontWeight
import com.keyxif.app.domain.model.ElementStyle
import com.keyxif.app.domain.model.ElementTextAlign
import com.keyxif.app.domain.model.InternalCard
import com.keyxif.app.domain.model.TemplateFill
import com.keyxif.app.domain.model.TemplateFillType
import com.keyxif.app.domain.model.createBlankCustomTemplate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class CustomTemplateRepository(context: Context) {
    private val preferences = context.getSharedPreferences("keyxif_custom_templates", Context.MODE_PRIVATE)

    fun getAll(): List<CustomTemplate> {
        val raw = preferences.getString(KEY_TEMPLATES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toCustomTemplate())
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun save(template: CustomTemplate): CustomTemplate {
        val now = System.currentTimeMillis()
        val next = template.copy(
            name = template.name.trim().ifBlank { "새 커스텀 템플릿" },
            updatedAt = now,
        )
        persist(listOf(next) + getAll().filterNot { it.id == next.id })
        return next
    }

    fun duplicate(id: String): CustomTemplate? {
        val source = getAll().firstOrNull { it.id == id } ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} 복사본",
            createdAt = now,
            updatedAt = now,
        )
        persist(listOf(copy) + getAll())
        return copy
    }

    fun rename(id: String, name: String): CustomTemplate? {
        val templates = getAll()
        val now = System.currentTimeMillis()
        var renamed: CustomTemplate? = null
        persist(
            templates.map { template ->
                if (template.id == id) {
                    template.copy(name = name.trim().ifBlank { template.name }, updatedAt = now).also { renamed = it }
                } else {
                    template
                }
            },
        )
        return renamed
    }

    fun delete(id: String) {
        persist(getAll().filterNot { it.id == id })
    }

    private fun persist(templates: List<CustomTemplate>) {
        val array = JSONArray()
        templates.sortedByDescending { it.updatedAt }.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_TEMPLATES, array.toString()).apply()
    }

    private companion object {
        const val KEY_TEMPLATES = "templates"
    }
}

fun CustomTemplate.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("templateVersion", templateVersion)
    put("name", name)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("frame", frame.toJson())
    put("photoPlacement", photoPlacement.toJson())
    put("elements", JSONArray().also { array -> elements.forEach { array.put(it.toJson()) } })
    put("internalCards", JSONArray().also { array -> internalCards.forEach { array.put(it.toJson()) } })
    put("thumbnail", thumbnail)
}

fun JSONObject.toCustomTemplate(): CustomTemplate {
    val fallback = createBlankCustomTemplate()
    return fallback.copy(
        id = optString("id").ifBlank { fallback.id },
        templateVersion = optInt("templateVersion", CUSTOM_TEMPLATE_VERSION),
        name = optString("name").ifBlank { fallback.name },
        createdAt = optLong("createdAt", fallback.createdAt),
        updatedAt = optLong("updatedAt", fallback.updatedAt),
        frame = optJSONObject("frame")?.toBackgroundFrame() ?: fallback.frame,
        photoPlacement = optJSONObject("photoPlacement")?.toPhotoPlacement() ?: fallback.photoPlacement,
        elements = optJSONArray("elements")?.mapJsonObjects { it.toCanvasElement() } ?: emptyList(),
        internalCards = optJSONArray("internalCards")?.mapJsonObjects { it.toInternalCard() } ?: emptyList(),
        thumbnail = optNullableString("thumbnail"),
    )
}

private fun BackgroundFrame.toJson(): JSONObject = JSONObject().apply {
    put("logicalWidth", logicalWidth)
    put("logicalHeight", logicalHeight)
    put("aspectRatio", aspectRatio)
    put("fill", fill.toJson())
}

private fun JSONObject.toBackgroundFrame(): BackgroundFrame {
    val width = optFloat("logicalWidth", 1f).coerceAtLeast(0.65f)
    val height = optFloat("logicalHeight", 1.25f).coerceAtLeast(0.65f)
    return BackgroundFrame(
        logicalWidth = width,
        logicalHeight = height,
        aspectRatio = optFloat("aspectRatio", width / height).takeIf { it > 0f } ?: width / height,
        fill = optJSONObject("fill")?.toTemplateFill() ?: TemplateFill(color = "#F7F5F0"),
    )
}

private fun TemplateFill.toJson(): JSONObject = JSONObject().apply {
    put("type", type.name)
    put("color", color)
    put("colorSlotId", colorSlotId)
}

private fun JSONObject.toTemplateFill(): TemplateFill = TemplateFill(
    type = enumValueOrDefault(optString("type"), TemplateFillType.Solid),
    color = optString("color").ifBlank { "#F7F5F0" },
    colorSlotId = optNullableString("colorSlotId"),
)

private fun CustomPhotoPlacement.toJson(): JSONObject = JSONObject().apply {
    put("x", x)
    put("y", y)
    put("width", width)
    put("height", height)
    put("scale", scale)
    put("aspectRatio", aspectRatio)
    put("fitMode", fitMode.name)
    put("visualGap", visualGap)
    put("safePadding", safePadding)
}

private fun JSONObject.toPhotoPlacement(): CustomPhotoPlacement = CustomPhotoPlacement(
    x = optFloat("x", 0.08f),
    y = optFloat("y", 0.08f),
    width = optFloat("width", 0.84f).coerceIn(0.01f, 1f),
    height = optFloat("height", 0.68f).coerceIn(0.01f, 1f),
    scale = optFloat("scale", 1f),
    aspectRatio = optFloat("aspectRatio", 1f).takeIf { it > 0f } ?: 1f,
    fitMode = enumValueOrDefault(optString("fitMode"), CustomPhotoFitMode.Contain),
    visualGap = optFloat("visualGap", 0f).coerceIn(0f, 0.2f),
    safePadding = optFloat("safePadding", 0.035f).coerceIn(0f, 0.2f),
)

private fun CanvasElement.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("type", type.name)
    put("containerId", containerId)
    put("coordinateSpace", coordinateSpace.name)
    put("x", x)
    put("y", y)
    put("width", width)
    put("height", height)
    put("rotation", rotation)
    put("zIndex", zIndex)
    put("locked", locked)
    put("hidden", hidden)
    put("style", style.toJson())
    put("content", content.toJson())
}

private fun JSONObject.toCanvasElement(): CanvasElement = CanvasElement(
    id = optString("id").ifBlank { UUID.randomUUID().toString() },
    type = enumValueOrDefault(optString("type"), CanvasElementType.Text),
    containerId = optString("containerId").ifBlank { "frame" },
    coordinateSpace = enumValueOrDefault(optString("coordinateSpace"), CanvasCoordinateSpace.Frame),
    x = optFloat("x", 0f),
    y = optFloat("y", 0f),
    width = optFloat("width", 0.1f).coerceAtLeast(0.01f),
    height = optFloat("height", 0.1f).coerceAtLeast(0.01f),
    rotation = optFloat("rotation", 0f),
    zIndex = optInt("zIndex", 0),
    locked = optBoolean("locked", false),
    hidden = optBoolean("hidden", false),
    style = optJSONObject("style")?.toElementStyle() ?: ElementStyle(),
    content = optJSONObject("content")?.toElementContent() ?: ElementContent.StaticText(),
)

private fun ElementStyle.toJson(): JSONObject = JSONObject().apply {
    put("textColor", textColor)
    put("fontSize", fontSize)
    put("fontWeight", fontWeight.name)
    put("textAlign", textAlign.name)
    put("lineHeight", lineHeight)
    put("letterSpacing", letterSpacing)
    put("maxLines", maxLines)
    put("uppercase", uppercase)
    put("opacity", opacity)
    put("cornerRadius", cornerRadius)
    put("chipShape", chipShape.name)
}

private fun JSONObject.toElementStyle(): ElementStyle = ElementStyle(
    textColor = optString("textColor").ifBlank { "#111111" },
    fontSize = optFloat("fontSize", 0.045f),
    fontWeight = enumValueOrDefault(optString("fontWeight"), ElementFontWeight.Regular),
    textAlign = enumValueOrDefault(optString("textAlign"), ElementTextAlign.Start),
    lineHeight = optFloat("lineHeight", 1.12f),
    letterSpacing = optFloat("letterSpacing", 0f),
    maxLines = optInt("maxLines", 2).coerceAtLeast(1),
    uppercase = optBoolean("uppercase", false),
    opacity = optFloat("opacity", 1f),
    cornerRadius = optFloat("cornerRadius", 0f),
    chipShape = enumValueOrDefault(optString("chipShape"), ColorChipShape.Rounded),
)

private fun ElementContent.toJson(): JSONObject = JSONObject().apply {
    when (this@toJson) {
        is ElementContent.StaticText -> {
            put("type", "staticText")
            put("text", text)
        }
        is ElementContent.BuildField -> {
            put("type", "buildField")
            put("field", field.name)
            put("format", format.name)
        }
        ElementContent.LogoImage -> put("type", "logoImage")
        is ElementContent.ColorChip -> {
            put("type", "colorChip")
            put("color", color)
            put("colorSlotId", colorSlotId)
        }
    }
}

private fun JSONObject.toElementContent(): ElementContent {
    return when (optString("type")) {
        "staticText" -> ElementContent.StaticText(optString("text"))
        "buildField" -> ElementContent.BuildField(
            field = enumValueOrDefault(optString("field"), BuildInfoField.Board),
            format = enumValueOrDefault(optString("format"), BuildFieldFormat.ValueOnly),
        )
        "logoImage" -> ElementContent.LogoImage
        "colorChip" -> ElementContent.ColorChip(
            color = optString("color").ifBlank { "#B7C9BF" },
            colorSlotId = optNullableString("colorSlotId"),
        )
        else -> ElementContent.StaticText(optString("text"))
    }
}

private fun InternalCard.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("containerId", containerId)
    put("x", x)
    put("y", y)
    put("width", width)
    put("height", height)
    put("zIndex", zIndex)
    put("locked", locked)
    put("hidden", hidden)
    put("style", style.toJson())
}

private fun JSONObject.toInternalCard(): InternalCard = InternalCard(
    id = optString("id").ifBlank { UUID.randomUUID().toString() },
    containerId = optString("containerId").ifBlank { "photo" },
    x = optFloat("x", 0.14f),
    y = optFloat("y", 0.14f),
    width = optFloat("width", 0.48f).coerceAtLeast(0.01f),
    height = optFloat("height", 0.28f).coerceAtLeast(0.01f),
    zIndex = optInt("zIndex", 0),
    locked = optBoolean("locked", false),
    hidden = optBoolean("hidden", false),
    style = optJSONObject("style")?.toCardStyle() ?: CardStyle(),
)

private fun CardStyle.toJson(): JSONObject = JSONObject().apply {
    put("backgroundColor", backgroundColor)
    put("colorSlotId", colorSlotId)
    put("opacity", opacity)
    put("radius", radius)
    put("borderEnabled", borderEnabled)
    put("borderColor", borderColor)
    put("borderWidth", borderWidth)
    put("padding", padding)
    put("shadowEnabled", shadowEnabled)
    put("shadowBlur", shadowBlur)
    put("shadowOpacity", shadowOpacity)
}

private fun JSONObject.toCardStyle(): CardStyle = CardStyle(
    backgroundColor = optString("backgroundColor").ifBlank { "#FFFFFF" },
    colorSlotId = optNullableString("colorSlotId"),
    opacity = optFloat("opacity", 0.92f),
    radius = optFloat("radius", 0.035f),
    borderEnabled = optBoolean("borderEnabled", false),
    borderColor = optString("borderColor").ifBlank { "#000000" },
    borderWidth = optFloat("borderWidth", 0.002f),
    padding = optFloat("padding", 0.035f),
    shadowEnabled = optBoolean("shadowEnabled", false),
    shadowBlur = optFloat("shadowBlur", 0.02f),
    shadowOpacity = optFloat("shadowOpacity", 0.2f),
)

private fun <T> JSONArray.mapJsonObjects(transform: (JSONObject) -> T): List<T> {
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { add(transform(it)) }
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T {
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
}

private fun JSONObject.optFloat(key: String, default: Float): Float {
    return if (has(key)) optDouble(key, default.toDouble()).toFloat() else default
}

private fun JSONObject.optNullableString(key: String): String? {
    if (isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}
