package com.artstudio.colorstack

import com.artstudio.color.ColorData
import com.artstudio.document.model.BlendModeType
import java.util.UUID

/**
 * Functional processing stages in the modular Colour Stack pipeline.
 *
 * Ordered canonically from foundational base fills to final lens effects,
 * with linework preserved independently.
 */
enum class ColorStageType(val displayName: String, val defaultBlendMode: BlendModeType) {
    BASE_FILL("Base Fills", BlendModeType.Normal),
    CEL_SHADOW("Cel Shadows", BlendModeType.Multiply),
    GRADIENT("Gradients", BlendModeType.Multiply),
    HIGHLIGHT("Highlights", BlendModeType.Screen),
    MEDIUM_TEXTURE("Medium Texture", BlendModeType.Overlay),
    PALETTE_TRANSFORM("Palette Transforms", BlendModeType.Normal),
    FINISHING_EFFECT("Finishing Effects", BlendModeType.Add),
    LINEWORK("Linework / Line Art", BlendModeType.Multiply)
}

/**
 * Stable identifiers for all 15 Colour Lab styles plus Style 0 (No Colour Baseline)
 * and custom recipes.
 */
enum class ColorStyleId(
    val idString: String,
    val styleNumber: Int,
    val displayName: String,
    val description: String
) {
    NO_COLOUR(
        "style_0_no_colour",
        0,
        "No Colour Baseline",
        "Pure line art and contour reconstruction preservation without automatic colour fills"
    ),
    CLASSIC_CEL(
        "style_1_classic_cel",
        1,
        "Classic Cel Shading",
        "Crisp 2-tone hard-edged shadows and specular highlights inspired by traditional animation"
    ),
    SOFT_GRADIENT(
        "style_2_soft_gradient",
        2,
        "Soft Gradient Shading",
        "Smooth ambient occlusion gradients and soft directional light falloffs"
    ),
    HYBRID_CEL_SOFT(
        "style_3_hybrid_cel_soft",
        3,
        "Hybrid Cel + Soft Shading",
        "Crisp primary shadow boundaries blended with secondary ambient soft gradients"
    ),
    FLAT_ILLUSTRATION(
        "style_4_flat_illustration",
        4,
        "Flat Illustration",
        "Clean graphic unshaded flats with precise contour boundaries and vibrant palette"
    ),
    PAINTERLY(
        "style_5_painterly",
        5,
        "Painterly",
        "Rich blended brush strokes with varied edges and subtle color transitions"
    ),
    WATERCOLOUR(
        "style_6_watercolour",
        6,
        "Watercolour",
        "Fluid pigment pooling, wet edge puddle rings, paper granulation, and translucent wash"
    ),
    COLOURED_PENCIL(
        "style_7_coloured_pencil",
        7,
        "Coloured Pencil",
        "Directional stippled hatching with visible paper grain tooth texture"
    ),
    MARKER(
        "style_8_marker",
        8,
        "Marker",
        "Translucent overlapping alcohol ink bands with chisel-tip edges and capillary bleed"
    ),
    MANGA_SCREENTONE(
        "style_9_manga_screentone",
        9,
        "Manga Screentone",
        "Halftone dot patterns and line screens at calibrated angles for comic shading"
    ),
    COMIC_BOOK(
        "style_10_comic_book",
        10,
        "Comic Book",
        "High-contrast dynamic ink shading with dot matrix fills and vivid pop accents"
    ),
    OIL_PAINT(
        "style_11_oil_paint",
        11,
        "Oil Paint",
        "Viscous multi-filament bristle ridges and tactile impasto relief lighting"
    ),
    GOUACHE(
        "style_12_gouache",
        12,
        "Gouache",
        "Matte opaque coverage with crisp planar brushwork and velvety finish"
    ),
    PASTEL(
        "style_13_pastel",
        13,
        "Pastel Palette",
        "Soft chalky pigment with low-saturation pastel tints and textured paper tooth"
    ),
    NEON_CYBERPUNK(
        "style_14_neon_cyberpunk",
        14,
        "Neon / Cyberpunk",
        "High-energy electric glowing rims, chromatic bloom, and intense saturated highlights"
    ),
    MONOCHROME(
        "style_15_monochrome",
        15,
        "Monochrome",
        "Sumie ink and grayscale tonal values strictly preserving luminance hierarchy"
    ),
    CUSTOM(
        "style_custom",
        -1,
        "Custom Recipe",
        "User-assembled composite multi-stage colour stack recipe"
    );

    companion object {
        fun fromId(id: String): ColorStyleId {
            return entries.firstOrNull { it.idString.equals(id, ignoreCase = true) } ?: CUSTOM
        }

        fun fromNumber(number: Int): ColorStyleId {
            return entries.firstOrNull { it.styleNumber == number } ?: CUSTOM
        }
    }
}

/**
 * Rendering quality levels for Colour Stack execution.
 */
enum class RenderQuality(val maxDimension: Int, val isReducedPreview: Boolean) {
    /** Reduced resolution surface (max 512px) for instant sub-16ms interactive UI preview */
    PREVIEW(512, true),

    /** Full canvas resolution (1024px, 2048px, etc.) for final layer baking and export */
    FINAL(4096, false)
}

/**
 * Base interface for stage configuration specifications.
 */
interface ColorStageConfig {
    val stageId: String
    val stageType: ColorStageType
    val name: String
    val enabled: Boolean
    val opacity: Float
    val strength: Float
    val blendMode: BlendModeType
    val targetRegionIds: Set<String>? // null means all regions

    fun toMap(): Map<String, Any>
}

/**
 * Base Fills configuration.
 */
data class BaseFillStageConfig(
    override val stageId: String = "stage_base_fill_${UUID.randomUUID().toString().take(8)}",
    override val name: String = "Base Fills",
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    override val strength: Float = 1.0f,
    override val blendMode: BlendModeType = BlendModeType.Normal,
    override val targetRegionIds: Set<String>? = null,
    val fillType: BaseFillType = BaseFillType.FLAT,
    val seed: Long = 42L
) : ColorStageConfig {
    override val stageType: ColorStageType = ColorStageType.BASE_FILL

    enum class BaseFillType { FLAT, SOFT_TINT, PAPER_TONE }

    override fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["stageId"] = stageId
        map["stageType"] = stageType.name
        map["name"] = name
        map["enabled"] = enabled
        map["opacity"] = opacity
        map["strength"] = strength
        map["blendMode"] = blendMode.name
        targetRegionIds?.let { map["targetRegionIds"] = it.toList() }
        map["fillType"] = fillType.name
        map["seed"] = seed
        return map
    }
}

/**
 * Cel Shadows configuration.
 */
data class CelShadowStageConfig(
    override val stageId: String = "stage_cel_shadow_${UUID.randomUUID().toString().take(8)}",
    override val name: String = "Cel Shadows",
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    override val strength: Float = 1.0f,
    override val blendMode: BlendModeType = BlendModeType.Multiply,
    override val targetRegionIds: Set<String>? = null,
    val shadowDarkness: Float = 0.45f, // [0.0, 1.0]
    val lightAngleDeg: Float = 45.0f, // 0 to 360 degrees
    val shadowExtent: Float = 0.50f, // [0.0, 1.0] fraction of region covered by shadow
    val coolShadowTint: Boolean = true // Blends slight indigo/blue tone into shadow
) : ColorStageConfig {
    override val stageType: ColorStageType = ColorStageType.CEL_SHADOW

    override fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["stageId"] = stageId
        map["stageType"] = stageType.name
        map["name"] = name
        map["enabled"] = enabled
        map["opacity"] = opacity
        map["strength"] = strength
        map["blendMode"] = blendMode.name
        targetRegionIds?.let { map["targetRegionIds"] = it.toList() }
        map["shadowDarkness"] = shadowDarkness
        map["lightAngleDeg"] = lightAngleDeg
        map["shadowExtent"] = shadowExtent
        map["coolShadowTint"] = coolShadowTint
        return map
    }
}

/**
 * Gradients configuration.
 */
data class GradientStageConfig(
    override val stageId: String = "stage_gradient_${UUID.randomUUID().toString().take(8)}",
    override val name: String = "Gradients",
    override val enabled: Boolean = true,
    override val opacity: Float = 0.85f,
    override val strength: Float = 1.0f,
    override val blendMode: BlendModeType = BlendModeType.Multiply,
    override val targetRegionIds: Set<String>? = null,
    val gradientType: GradientFalloffType = GradientFalloffType.AMBIENT_FALLOFF,
    val lightAngleDeg: Float = 45.0f,
    val softness: Float = 0.50f,
    val shadowRatio: Float = 0.35f
) : ColorStageConfig {
    override val stageType: ColorStageType = ColorStageType.GRADIENT

    enum class GradientFalloffType { AMBIENT_FALLOFF, DIRECTIONAL_LIGHT, RADIAL_GLOW }

    override fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["stageId"] = stageId
        map["stageType"] = stageType.name
        map["name"] = name
        map["enabled"] = enabled
        map["opacity"] = opacity
        map["strength"] = strength
        map["blendMode"] = blendMode.name
        targetRegionIds?.let { map["targetRegionIds"] = it.toList() }
        map["gradientType"] = gradientType.name
        map["lightAngleDeg"] = lightAngleDeg
        map["softness"] = softness
        map["shadowRatio"] = shadowRatio
        return map
    }
}

/**
 * Highlights configuration.
 */
data class HighlightStageConfig(
    override val stageId: String = "stage_highlight_${UUID.randomUUID().toString().take(8)}",
    override val name: String = "Highlights",
    override val enabled: Boolean = true,
    override val opacity: Float = 0.90f,
    override val strength: Float = 1.0f,
    override val blendMode: BlendModeType = BlendModeType.Screen,
    override val targetRegionIds: Set<String>? = null,
    val highlightType: HighlightType = HighlightType.SPECULAR_RIM,
    val intensity: Float = 0.70f,
    val lightAngleDeg: Float = 45.0f,
    val sizeRatio: Float = 0.15f
) : ColorStageConfig {
    override val stageType: ColorStageType = ColorStageType.HIGHLIGHT

    enum class HighlightType { SPECULAR_RIM, GLOSS_SPECULAR, SOFT_BOUNCE }

    override fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["stageId"] = stageId
        map["stageType"] = stageType.name
        map["name"] = name
        map["enabled"] = enabled
        map["opacity"] = opacity
        map["strength"] = strength
        map["blendMode"] = blendMode.name
        targetRegionIds?.let { map["targetRegionIds"] = it.toList() }
        map["highlightType"] = highlightType.name
        map["intensity"] = intensity
        map["lightAngleDeg"] = lightAngleDeg
        map["sizeRatio"] = sizeRatio
        return map
    }
}

/**
 * Medium Texture configuration.
 */
data class MediumTextureStageConfig(
    override val stageId: String = "stage_medium_texture_${UUID.randomUUID().toString().take(8)}",
    override val name: String = "Medium Texture",
    override val enabled: Boolean = true,
    override val opacity: Float = 0.75f,
    override val strength: Float = 1.0f,
    override val blendMode: BlendModeType = BlendModeType.Overlay,
    override val targetRegionIds: Set<String>? = null,
    val mediumType: MediumTextureType = MediumTextureType.PAPER_GRAIN,
    val scale: Float = 1.0f,
    val roughness: Float = 0.35f,
    val seed: Long = 42L
) : ColorStageConfig {
    override val stageType: ColorStageType = ColorStageType.MEDIUM_TEXTURE

    enum class MediumTextureType {
        PAPER_GRAIN,
        OIL_IMPASTO,
        SCREENTONE_DOTS,
        CANVAS_TOOTH,
        PENCIL_HATCH
    }

    override fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["stageId"] = stageId
        map["stageType"] = stageType.name
        map["name"] = name
        map["enabled"] = enabled
        map["opacity"] = opacity
        map["strength"] = strength
        map["blendMode"] = blendMode.name
        targetRegionIds?.let { map["targetRegionIds"] = it.toList() }
        map["mediumType"] = mediumType.name
        map["scale"] = scale
        map["roughness"] = roughness
        map["seed"] = seed
        return map
    }
}

/**
 * Palette Transforms configuration.
 */
data class PaletteTransformStageConfig(
    override val stageId: String = "stage_palette_transform_${UUID.randomUUID().toString().take(8)}",
    override val name: String = "Palette Transforms",
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    override val strength: Float = 1.0f,
    override val blendMode: BlendModeType = BlendModeType.Normal,
    override val targetRegionIds: Set<String>? = null,
    val transformType: TransformType = TransformType.MONOCHROME_DESATURATE,
    val saturation: Float = 0.0f, // 0.0 = full grayscale, 1.0 = unchanged
    val tintHex: String? = null
) : ColorStageConfig {
    override val stageType: ColorStageType = ColorStageType.PALETTE_TRANSFORM

    enum class TransformType {
        MONOCHROME_DESATURATE,
        PASTEL_SHIFT,
        WARM_SUNSET,
        COOL_TWILIGHT,
        DUOTONE,
        SEPIA
    }

    override fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["stageId"] = stageId
        map["stageType"] = stageType.name
        map["name"] = name
        map["enabled"] = enabled
        map["opacity"] = opacity
        map["strength"] = strength
        map["blendMode"] = blendMode.name
        targetRegionIds?.let { map["targetRegionIds"] = it.toList() }
        map["transformType"] = transformType.name
        map["saturation"] = saturation
        tintHex?.let { map["tintHex"] = it }
        return map
    }
}

/**
 * Finishing Effects configuration.
 */
data class FinishingEffectStageConfig(
    override val stageId: String = "stage_finishing_effect_${UUID.randomUUID().toString().take(8)}",
    override val name: String = "Finishing Effects",
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    override val strength: Float = 1.0f,
    override val blendMode: BlendModeType = BlendModeType.Add,
    override val targetRegionIds: Set<String>? = null,
    val effectType: FinishingEffectType = FinishingEffectType.NEON_BLOOM,
    val bloomRadius: Float = 12.0f,
    val bloomIntensity: Float = 0.80f,
    val seed: Long = 42L
) : ColorStageConfig {
    override val stageType: ColorStageType = ColorStageType.FINISHING_EFFECT

    enum class FinishingEffectType {
        NEON_BLOOM,
        VIGNETTE,
        CHROMATIC_ABERRATION,
        POSTERIZE
    }

    override fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["stageId"] = stageId
        map["stageType"] = stageType.name
        map["name"] = name
        map["enabled"] = enabled
        map["opacity"] = opacity
        map["strength"] = strength
        map["blendMode"] = blendMode.name
        targetRegionIds?.let { map["targetRegionIds"] = it.toList() }
        map["effectType"] = effectType.name
        map["bloomRadius"] = bloomRadius
        map["bloomIntensity"] = bloomIntensity
        map["seed"] = seed
        return map
    }
}

/**
 * Factory deserializer for [ColorStageConfig] instances from untyped maps.
 */
object ColorStageConfigFactory {
    @Suppress("UNCHECKED_CAST")
    fun fromMap(map: Map<String, Any?>): ColorStageConfig {
        val stageTypeName = map["stageType"]?.toString() ?: ColorStageType.BASE_FILL.name
        val stageType = runCatching { ColorStageType.valueOf(stageTypeName) }
            .getOrDefault(ColorStageType.BASE_FILL)
        val stageId = map["stageId"]?.toString() ?: "stage_${UUID.randomUUID()}"
        val name = map["name"]?.toString() ?: stageType.displayName
        val enabled = (map["enabled"] as? Boolean) ?: true
        val opacity = (map["opacity"] as? Number)?.toFloat() ?: 1.0f
        val strength = (map["strength"] as? Number)?.toFloat() ?: 1.0f
        val blendModeName = map["blendMode"]?.toString() ?: stageType.defaultBlendMode.name
        val blendMode = runCatching { BlendModeType.valueOf(blendModeName) }
            .getOrDefault(stageType.defaultBlendMode)
        val targetRegionIds = (map["targetRegionIds"] as? List<String>)?.toSet()

        return when (stageType) {
            ColorStageType.BASE_FILL -> {
                val fillTypeName = map["fillType"]?.toString() ?: BaseFillStageConfig.BaseFillType.FLAT.name
                val fillType = runCatching { BaseFillStageConfig.BaseFillType.valueOf(fillTypeName) }
                    .getOrDefault(BaseFillStageConfig.BaseFillType.FLAT)
                val seed = (map["seed"] as? Number)?.toLong() ?: 42L
                BaseFillStageConfig(stageId, name, enabled, opacity, strength, blendMode, targetRegionIds, fillType, seed)
            }
            ColorStageType.CEL_SHADOW -> {
                val darkness = (map["shadowDarkness"] as? Number)?.toFloat() ?: 0.45f
                val angle = (map["lightAngleDeg"] as? Number)?.toFloat() ?: 45.0f
                val extent = (map["shadowExtent"] as? Number)?.toFloat() ?: 0.50f
                val coolTint = (map["coolShadowTint"] as? Boolean) ?: true
                CelShadowStageConfig(stageId, name, enabled, opacity, strength, blendMode, targetRegionIds, darkness, angle, extent, coolTint)
            }
            ColorStageType.GRADIENT -> {
                val gTypeName = map["gradientType"]?.toString() ?: GradientStageConfig.GradientFalloffType.AMBIENT_FALLOFF.name
                val gType = runCatching { GradientStageConfig.GradientFalloffType.valueOf(gTypeName) }
                    .getOrDefault(GradientStageConfig.GradientFalloffType.AMBIENT_FALLOFF)
                val angle = (map["lightAngleDeg"] as? Number)?.toFloat() ?: 45.0f
                val softness = (map["softness"] as? Number)?.toFloat() ?: 0.50f
                val shadowRatio = (map["shadowRatio"] as? Number)?.toFloat() ?: 0.35f
                GradientStageConfig(stageId, name, enabled, opacity, strength, blendMode, targetRegionIds, gType, angle, softness, shadowRatio)
            }
            ColorStageType.HIGHLIGHT -> {
                val hTypeName = map["highlightType"]?.toString() ?: HighlightStageConfig.HighlightType.SPECULAR_RIM.name
                val hType = runCatching { HighlightStageConfig.HighlightType.valueOf(hTypeName) }
                    .getOrDefault(HighlightStageConfig.HighlightType.SPECULAR_RIM)
                val intensity = (map["intensity"] as? Number)?.toFloat() ?: 0.70f
                val angle = (map["lightAngleDeg"] as? Number)?.toFloat() ?: 45.0f
                val sizeRatio = (map["sizeRatio"] as? Number)?.toFloat() ?: 0.15f
                HighlightStageConfig(stageId, name, enabled, opacity, strength, blendMode, targetRegionIds, hType, intensity, angle, sizeRatio)
            }
            ColorStageType.MEDIUM_TEXTURE -> {
                val mTypeName = map["mediumType"]?.toString() ?: MediumTextureStageConfig.MediumTextureType.PAPER_GRAIN.name
                val mType = runCatching { MediumTextureStageConfig.MediumTextureType.valueOf(mTypeName) }
                    .getOrDefault(MediumTextureStageConfig.MediumTextureType.PAPER_GRAIN)
                val scale = (map["scale"] as? Number)?.toFloat() ?: 1.0f
                val roughness = (map["roughness"] as? Number)?.toFloat() ?: 0.35f
                val seed = (map["seed"] as? Number)?.toLong() ?: 42L
                MediumTextureStageConfig(stageId, name, enabled, opacity, strength, blendMode, targetRegionIds, mType, scale, roughness, seed)
            }
            ColorStageType.PALETTE_TRANSFORM -> {
                val tTypeName = map["transformType"]?.toString() ?: PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE.name
                val tType = runCatching { PaletteTransformStageConfig.TransformType.valueOf(tTypeName) }
                    .getOrDefault(PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE)
                val sat = (map["saturation"] as? Number)?.toFloat() ?: 0.0f
                val tint = map["tintHex"]?.toString()
                PaletteTransformStageConfig(stageId, name, enabled, opacity, strength, blendMode, targetRegionIds, tType, sat, tint)
            }
            ColorStageType.FINISHING_EFFECT -> {
                val eTypeName = map["effectType"]?.toString() ?: FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM.name
                val eType = runCatching { FinishingEffectStageConfig.FinishingEffectType.valueOf(eTypeName) }
                    .getOrDefault(FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM)
                val radius = (map["bloomRadius"] as? Number)?.toFloat() ?: 12.0f
                val intensity = (map["bloomIntensity"] as? Number)?.toFloat() ?: 0.80f
                val seed = (map["seed"] as? Number)?.toLong() ?: 42L
                FinishingEffectStageConfig(stageId, name, enabled, opacity, strength, blendMode, targetRegionIds, eType, radius, intensity, seed)
            }
            ColorStageType.LINEWORK -> {
                BaseFillStageConfig(stageId, name, enabled, opacity, strength, blendMode, targetRegionIds)
            }
        }
    }
}

/**
 * Isolated raster linework layer preserved independently from generated colour.
 */
data class LineworkData(
    val width: Int,
    val height: Int,
    val pixels: IntArray, // 32-bit ARGB raster containing black/dark strokes with alpha
    val blendMode: BlendModeType = BlendModeType.Multiply,
    val opacity: Float = 1.0f
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(pixels.size == width * height) { "Pixel buffer size must equal width * height" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LineworkData) return false
        return width == other.width &&
                height == other.height &&
                blendMode == other.blendMode &&
                opacity == other.opacity &&
                pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + blendMode.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

/**
 * Context passed into stage execution processors.
 */
data class StageExecutionContext(
    val width: Int,
    val height: Int,
    val backdropPixels: IntArray, // Pixel buffer composited from earlier stages
    val regionMasks: Map<String, ByteArray>, // Region ID -> 8-bit coverage mask
    val regionColors: Map<String, ColorData>, // Region ID -> assigned color
    val quality: RenderQuality,
    val seed: Long = 42L
)

/**
 * Output produced by an individual stage execution.
 */
data class StageExecutionOutput(
    val stageId: String,
    val stageType: ColorStageType,
    val stageLayerName: String,
    val stagePixels: IntArray, // Isolated contribution of THIS stage for separate layer baking
    val compositedBackdrop: IntArray, // Cumulative composited canvas buffer after this stage
    val executionTimeMs: Long,
    val memoryAllocatedBytes: Long,
    val configSnapshot: ColorStageConfig
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StageExecutionOutput) return false
        return stageId == other.stageId &&
                stageType == other.stageType &&
                stagePixels.contentEquals(other.stagePixels) &&
                compositedBackdrop.contentEquals(other.compositedBackdrop)
    }

    override fun hashCode(): Int {
        var result = stageId.hashCode()
        result = 31 * result + stageType.hashCode()
        result = 31 * result + stagePixels.contentHashCode()
        result = 31 * result + compositedBackdrop.contentHashCode()
        return result
    }
}

/**
 * Comprehensive render result returned by the Colour Stack pipeline.
 */
data class ColorStackRenderResult(
    val width: Int,
    val height: Int,
    val finalCompositePixels: IntArray,
    val stageOutputs: List<StageExecutionOutput>,
    val lineworkOutput: StageExecutionOutput?,
    val totalExecutionTimeMs: Long,
    val peakMemoryBytes: Long,
    val quality: RenderQuality,
    val isNoColour: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColorStackRenderResult) return false
        return width == other.width &&
                height == other.height &&
                quality == other.quality &&
                isNoColour == other.isNoColour &&
                finalCompositePixels.contentEquals(other.finalCompositePixels) &&
                stageOutputs == other.stageOutputs &&
                lineworkOutput == other.lineworkOutput
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + quality.hashCode()
        result = 31 * result + isNoColour.hashCode()
        result = 31 * result + finalCompositePixels.contentHashCode()
        result = 31 * result + stageOutputs.hashCode()
        result = 31 * result + (lineworkOutput?.hashCode() ?: 0)
        return result
    }
}
