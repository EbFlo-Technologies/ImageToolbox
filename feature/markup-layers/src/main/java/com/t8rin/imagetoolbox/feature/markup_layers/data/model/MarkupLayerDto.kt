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

@JsonClass(generateAdapter = true)
data class MarkupProjectDto(
    val version: Int = 1,
    val background: BackgroundBehaviorDto = BackgroundBehaviorDto.None,
    val layers: List<MarkupLayerDto>
)

@JsonClass(generateAdapter = true)
sealed interface BackgroundBehaviorDto {
    @JsonClass(generateAdapter = true)
    data object None : BackgroundBehaviorDto

    @JsonClass(generateAdapter = true)
    data class Color(
        val width: Int,
        val height: Int,
        val color: Int
    ) : BackgroundBehaviorDto

    @JsonClass(generateAdapter = true)
    data class Image(
        val assetName: String
    ) : BackgroundBehaviorDto
}

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
sealed interface LayerTypeDto {

    @JsonClass(generateAdapter = true)
    data class Text(
        val color: Int,
        val size: Float,
        val fontPath: String?, // Path or identifier for the font
        val backgroundColor: Int,
        val text: String,
        val decorations: List<String>, // "Bold", "Italic", etc.
        val alignment: String, // "Start", "Center", "End"
        val outlineColor: Int?,
        val outlineWidth: Float?
    ) : LayerTypeDto

    @JsonClass(generateAdapter = true)
    data class Image(
        val assetName: String // e.g., "assets/layer_2_image.png" inside the zip
    ) : LayerTypeDto

    @JsonClass(generateAdapter = true)
    data class Sticker(
        val emojiString: String // Or however stickers are defined
    ) : LayerTypeDto
}

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
    isActive = position.isActive,       // Safely retrieved from Domain
    isVisible = position.isVisible,     // Safely retrieved from Domain
    coerceToBounds = position.coerceToBounds
)

fun LayerType.toDto(assetName: String? = null): LayerTypeDto = when (this) {
    is LayerType.Text -> LayerTypeDto.Text(
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
    is LayerType.Picture.Image -> LayerTypeDto.Image(
        assetName = assetName ?: ""
    )
    is LayerType.Picture.Sticker -> LayerTypeDto.Sticker(
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
        isActive = isActive,            // Restored from DTO
        isVisible = isVisible           // Restored from DTO
    )
)

fun LayerTypeDto.toDomain(imageData: Any? = null): LayerType = when (this) {
    is LayerTypeDto.Text -> LayerType.Text(
        color = color,
        size = size,
        font = fontPath?.let { DomainFontFamily.fromString(it).asFontType() },
        backgroundColor = backgroundColor,
        text = text,
        decorations = decorations.mapNotNull {
            runCatching { LayerType.Text.Decoration.valueOf(it) }.getOrNull()
        },
        alignment = runCatching { LayerType.Text.Alignment.valueOf(alignment) }
            .getOrDefault(LayerType.Text.Alignment.Center),
        outline = if (outlineColor != null && outlineWidth != null) {
            Outline(color = outlineColor, width = outlineWidth)
        } else null
    )
    is LayerTypeDto.Image -> LayerType.Picture.Image(
        imageData = imageData ?: assetName
    )
    is LayerTypeDto.Sticker -> LayerType.Picture.Sticker(
        imageData = emojiString
    )
}