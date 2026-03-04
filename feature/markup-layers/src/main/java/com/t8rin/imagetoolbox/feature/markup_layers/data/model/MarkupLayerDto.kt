package com.t8rin.imagetoolbox.feature.markup_layers.data.model

import com.t8rin.imagetoolbox.core.domain.model.IntegerSize
import com.t8rin.imagetoolbox.core.domain.model.Outline
import com.t8rin.imagetoolbox.core.settings.domain.model.DomainFontFamily
import com.t8rin.imagetoolbox.core.settings.presentation.model.asDomain
import com.t8rin.imagetoolbox.core.settings.presentation.model.asFontType
import com.t8rin.imagetoolbox.feature.markup_layers.domain.LayerPosition
import com.t8rin.imagetoolbox.feature.markup_layers.domain.LayerType
import com.t8rin.imagetoolbox.feature.markup_layers.domain.MarkupLayer
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Objects (DTOs) for serializing the Markup Layer state to JSON.
 * These classes mirror the in-memory state but use only primitive, serializable types.
 */

enum class BackgroundDtoType { NONE, COLOR, IMAGE }
enum class LayerDtoType { TEXT, IMAGE, STICKER }

@JsonClass(generateAdapter = true)
data class MarkupProjectDto(
    val version: Int = 1,
    val background: BackgroundBehaviorDto = BackgroundBehaviorDto(type = BackgroundDtoType.NONE),
    val layers: List<MarkupLayerDto>
)

@JsonClass(generateAdapter = true)
data class BackgroundBehaviorDto(
    val type: BackgroundDtoType,
    val width: Int? = null,
    val height: Int? = null,
    val color: Int? = null,
    val assetName: String? = null
)

@JsonClass(generateAdapter = true)
data class MarkupLayerDto(
    val type: LayerTypeDto,
    val position: LayerPositionDto,
    val isActive: Boolean,
    val isVisible: Boolean,
    val coerceToBounds: Boolean
)

@JsonClass(generateAdapter = true)
data class LayerPositionDto(
    val scale: Float,
    val rotation: Float,
    val offsetX: Float,
    val offsetY: Float,
    val alpha: Float,
    val canvasWidth: Int,
    val canvasHeight: Int
)

@JsonClass(generateAdapter = true)
data class LayerTypeDto(
    val type: LayerDtoType,
    val color: Int? = null,
    val size: Float? = null,
    val fontPath: String? = null,
    val backgroundColor: Int? = null,
    val text: String? = null,
    val decorations: List<String>? = null,
    val alignment: String? = null,
    val outlineColor: Int? = null,
    val outlineWidth: Float? = null,
    val assetName: String? = null,
    val emojiString: String? = null
)

// --- MAPPING FUNCTIONS ---
fun MarkupLayer.toDto(assetName: String? = null): MarkupLayerDto = MarkupLayerDto(
    type = type.toDto(assetName),
    position = LayerPositionDto(
        scale = position.scale,
        rotation = position.rotation,
        offsetX = position.offsetX,
        offsetY = position.offsetY,
        alpha = position.alpha,
        canvasWidth = position.currentCanvasSize.width,
        canvasHeight = position.currentCanvasSize.height
    ),
    isActive = position.isActive,
    isVisible = position.isVisible,
    coerceToBounds = position.coerceToBounds
)

fun LayerType.toDto(assetName: String? = null): LayerTypeDto = when (this) {
    is LayerType.Text -> LayerTypeDto(
        type = LayerDtoType.TEXT,
        color = color,
        size = size,
        fontPath = font?.let {
            val domainFont = it.asDomain()
            if (domainFont !is DomainFontFamily.Custom) domainFont.asString() else null
        },
        backgroundColor = backgroundColor,
        text = text,
        decorations = decorations.map { it.name },
        alignment = alignment.name,
        outlineColor = outline?.color,
        outlineWidth = outline?.width
    )
    is LayerType.Picture.Image -> LayerTypeDto(
        type = LayerDtoType.IMAGE,
        assetName = assetName ?: ""
    )
    is LayerType.Picture.Sticker -> LayerTypeDto(
        type = LayerDtoType.STICKER,
        emojiString = imageData.toString()
    )
}

fun MarkupLayerDto.toDomainLayer(imageData: Any? = null): MarkupLayer = MarkupLayer(
    type = type.toDomain(imageData),
    position = LayerPosition(
        scale = position.scale,
        rotation = position.rotation,
        offsetX = position.offsetX,
        offsetY = position.offsetY,
        alpha = position.alpha,
        currentCanvasSize = IntegerSize(position.canvasWidth, position.canvasHeight),
        coerceToBounds = coerceToBounds,
        isActive = isActive,
        isVisible = isVisible
    )
)

fun LayerTypeDto.toDomain(imageData: Any? = null): LayerType = when (type) {
    LayerDtoType.TEXT -> LayerType.Text(
        color = color ?: 0,
        size = size ?: 0.2f,
        font = fontPath?.let { DomainFontFamily.fromString(it).asFontType() },
        backgroundColor = backgroundColor ?: 0,
        text = text ?: "",
        decorations = decorations?.mapNotNull {
            runCatching { LayerType.Text.Decoration.valueOf(it) }.getOrNull()
        } ?: emptyList(),
        alignment = alignment?.let { runCatching { LayerType.Text.Alignment.valueOf(it) }.getOrDefault(LayerType.Text.Alignment.Center) } ?: LayerType.Text.Alignment.Center,
        outline = if (outlineColor != null && outlineWidth != null) {
            Outline(color = outlineColor, width = outlineWidth)
        } else null
    )
    LayerDtoType.IMAGE -> LayerType.Picture.Image(
        imageData = imageData ?: assetName ?: ""
    )
    LayerDtoType.STICKER -> LayerType.Picture.Sticker(
        imageData = emojiString ?: ""
    )
}