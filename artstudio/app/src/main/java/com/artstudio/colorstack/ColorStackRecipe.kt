package com.artstudio.colorstack

import com.artstudio.document.io.ArtJson
import com.artstudio.document.model.BlendModeType
import java.util.UUID

/**
 * Versioned, serializable multi-stage recipe defining the exact Colour Stack configuration.
 *
 * Recipes are 100% deterministic given a fixed [seed], enabling reproducible rendering
 * and non-destructive layer baking across sessions.
 */
data class ColorStackRecipe(
    val id: String = "recipe_${UUID.randomUUID().toString().take(8)}",
    val version: Int = 1,
    val name: String,
    val description: String = "",
    val styleId: ColorStyleId = ColorStyleId.CUSTOM,
    val isNoColour: Boolean = false,
    val stages: List<ColorStageConfig> = emptyList(),
    val seed: Long = 42L,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val modifiedTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns a copy of this recipe with the specified stage added or replaced.
     */
    fun withStage(stage: ColorStageConfig): ColorStackRecipe {
        val existingIdx = stages.indexOfFirst { it.stageId == stage.stageId }
        val updatedList = if (existingIdx != -1) {
            stages.toMutableList().apply { set(existingIdx, stage) }
        } else {
            stages + stage
        }
        return copy(stages = updatedList, modifiedTimestamp = System.currentTimeMillis())
    }

    /**
     * Returns a copy with the stage removed.
     */
    fun withoutStage(stageId: String): ColorStackRecipe {
        return copy(
            stages = stages.filter { it.stageId != stageId },
            modifiedTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Returns a copy with reordered stages.
     */
    fun withReorderedStages(newStages: List<ColorStageConfig>): ColorStackRecipe {
        return copy(stages = newStages, modifiedTimestamp = System.currentTimeMillis())
    }

    /**
     * Returns a copy with a specific stage enabled or disabled.
     */
    fun withStageToggled(stageId: String, enabled: Boolean): ColorStackRecipe {
        val updated = stages.map { stage ->
            if (stage.stageId == stageId) {
                when (stage) {
                    is BaseFillStageConfig -> stage.copy(enabled = enabled)
                    is CelShadowStageConfig -> stage.copy(enabled = enabled)
                    is GradientStageConfig -> stage.copy(enabled = enabled)
                    is HighlightStageConfig -> stage.copy(enabled = enabled)
                    is MediumTextureStageConfig -> stage.copy(enabled = enabled)
                    is PaletteTransformStageConfig -> stage.copy(enabled = enabled)
                    is FinishingEffectStageConfig -> stage.copy(enabled = enabled)
                    else -> stage
                }
            } else stage
        }
        return copy(stages = updated, modifiedTimestamp = System.currentTimeMillis())
    }

    /**
     * Returns a copy with a stage's opacity updated.
     */
    fun withStageOpacity(stageId: String, opacity: Float): ColorStackRecipe {
        val clOpacity = opacity.coerceIn(0.0f, 1.0f)
        val updated = stages.map { stage ->
            if (stage.stageId == stageId) {
                when (stage) {
                    is BaseFillStageConfig -> stage.copy(opacity = clOpacity)
                    is CelShadowStageConfig -> stage.copy(opacity = clOpacity)
                    is GradientStageConfig -> stage.copy(opacity = clOpacity)
                    is HighlightStageConfig -> stage.copy(opacity = clOpacity)
                    is MediumTextureStageConfig -> stage.copy(opacity = clOpacity)
                    is PaletteTransformStageConfig -> stage.copy(opacity = clOpacity)
                    is FinishingEffectStageConfig -> stage.copy(opacity = clOpacity)
                    else -> stage
                }
            } else stage
        }
        return copy(stages = updated, modifiedTimestamp = System.currentTimeMillis())
    }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        map["version"] = version
        map["name"] = name
        map["description"] = description
        map["styleId"] = styleId.idString
        map["isNoColour"] = isNoColour
        map["stages"] = stages.map { it.toMap() }
        map["seed"] = seed
        map["createdTimestamp"] = createdTimestamp
        map["modifiedTimestamp"] = modifiedTimestamp
        return map
    }
}

/**
 * Lossless JSON serialization engine for [ColorStackRecipe].
 */
object ColorStackRecipeSerializer {

    fun toJson(recipe: ColorStackRecipe, indent: Int = 2): String {
        return ArtJson.stringify(recipe.toMap(), indent)
    }

    @Suppress("UNCHECKED_CAST")
    fun fromJson(jsonStr: String): ColorStackRecipe {
        val root = ArtJson.parse(jsonStr) as? Map<String, Any?>
            ?: throw IllegalArgumentException("Invalid JSON root, expected Map object")
        return fromMap(root)
    }

    @Suppress("UNCHECKED_CAST")
    fun fromMap(map: Map<String, Any?>): ColorStackRecipe {
        val id = map["id"]?.toString() ?: "recipe_${UUID.randomUUID().toString().take(8)}"
        val version = (map["version"] as? Number)?.toInt() ?: 1
        val name = map["name"]?.toString() ?: "Untitled Recipe"
        val description = map["description"]?.toString() ?: ""
        val styleIdStr = map["styleId"]?.toString() ?: ColorStyleId.CUSTOM.idString
        val styleId = ColorStyleId.fromId(styleIdStr)
        val isNoColour = (map["isNoColour"] as? Boolean) ?: false
        val seed = (map["seed"] as? Number)?.toLong() ?: 42L
        val created = (map["createdTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val modified = (map["modifiedTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

        val rawStages = (map["stages"] as? List<Map<String, Any?>>) ?: emptyList()
        val stages = rawStages.map { ColorStageConfigFactory.fromMap(it) }

        return ColorStackRecipe(id, version, name, description, styleId, isNoColour, stages, seed, created, modified)
    }
}

/**
 * Built-in presets for all 15 Colour Lab styles and Style 0 (No Colour Baseline).
 */
object ColorStackPresets {

    fun noColour(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_0_no_colour",
            name = "No Colour Baseline",
            description = "Pure line art and contour reconstruction preservation without automatic colour fills",
            styleId = ColorStyleId.NO_COLOUR,
            isNoColour = true,
            stages = emptyList(),
            seed = seed
        )
    }

    fun classicCel(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_1_classic_cel",
            name = "Classic Cel Shading",
            description = "Crisp 2-tone hard-edged shadows and specular highlights",
            styleId = ColorStyleId.CLASSIC_CEL,
            stages = listOf(
                BaseFillStageConfig(name = "Base Fills", fillType = BaseFillStageConfig.BaseFillType.FLAT),
                CelShadowStageConfig(name = "Cel Shadows", shadowDarkness = 0.45f, lightAngleDeg = 45f),
                HighlightStageConfig(name = "Specular Highlights", intensity = 0.70f, lightAngleDeg = 45f)
            ),
            seed = seed
        )
    }

    fun softGradient(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_2_soft_gradient",
            name = "Soft Gradient Shading",
            description = "Smooth ambient occlusion gradients and soft directional light falloffs",
            styleId = ColorStyleId.SOFT_GRADIENT,
            stages = listOf(
                BaseFillStageConfig(name = "Base Fills", fillType = BaseFillStageConfig.BaseFillType.FLAT),
                GradientStageConfig(name = "Soft Ambient Gradients", softness = 0.65f, shadowRatio = 0.40f)
            ),
            seed = seed
        )
    }

    fun hybridCelSoft(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_3_hybrid_cel_soft",
            name = "Hybrid Cel + Soft Shading",
            description = "Crisp primary shadow boundaries blended with secondary ambient soft gradients",
            styleId = ColorStyleId.HYBRID_CEL_SOFT,
            stages = listOf(
                BaseFillStageConfig(name = "Base Fills"),
                CelShadowStageConfig(name = "Core Cel Shadows", shadowDarkness = 0.40f),
                GradientStageConfig(name = "Ambient Gradients", softness = 0.50f, shadowRatio = 0.25f),
                HighlightStageConfig(name = "Soft Highlights", intensity = 0.60f)
            ),
            seed = seed
        )
    }

    fun flatIllustration(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_4_flat_illustration",
            name = "Flat Illustration",
            description = "Clean graphic unshaded flats with precise contour boundaries and vibrant palette",
            styleId = ColorStyleId.FLAT_ILLUSTRATION,
            stages = listOf(
                BaseFillStageConfig(name = "Graphic Flat Fills", fillType = BaseFillStageConfig.BaseFillType.FLAT),
                CelShadowStageConfig(name = "Graphic Shadow Steps", shadowDarkness = 0.25f, opacity = 0.8f)
            ),
            seed = seed
        )
    }

    fun painterly(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_5_painterly",
            name = "Painterly",
            description = "Rich blended brush strokes with varied edges and subtle color transitions",
            styleId = ColorStyleId.PAINTERLY,
            stages = listOf(
                BaseFillStageConfig(name = "Underpainting Base"),
                CelShadowStageConfig(name = "Form Shadows", shadowDarkness = 0.35f),
                GradientStageConfig(name = "Atmospheric Blends", softness = 0.70f),
                MediumTextureStageConfig(name = "Canvas Tooth", mediumType = MediumTextureStageConfig.MediumTextureType.CANVAS_TOOTH, roughness = 0.25f)
            ),
            seed = seed
        )
    }

    fun watercolour(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_6_watercolour",
            name = "Watercolour",
            description = "Fluid pigment pooling, wet edge puddle rings, and translucent wash",
            styleId = ColorStyleId.WATERCOLOUR,
            stages = listOf(
                BaseFillStageConfig(name = "Fluid Wash Base", fillType = BaseFillStageConfig.BaseFillType.PAPER_TONE, opacity = 0.85f),
                GradientStageConfig(name = "Wet Diffusion Gradients", softness = 0.80f, opacity = 0.60f),
                MediumTextureStageConfig(name = "Cold-Press Paper Grain", mediumType = MediumTextureStageConfig.MediumTextureType.PAPER_GRAIN, roughness = 0.45f)
            ),
            seed = seed
        )
    }

    fun colouredPencil(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_7_coloured_pencil",
            name = "Coloured Pencil",
            description = "Directional stippled hatching with visible paper grain tooth texture",
            styleId = ColorStyleId.COLOURED_PENCIL,
            stages = listOf(
                BaseFillStageConfig(name = "Tint Base", opacity = 0.75f),
                MediumTextureStageConfig(name = "Pencil Hatching & Grain", mediumType = MediumTextureStageConfig.MediumTextureType.PENCIL_HATCH, roughness = 0.55f)
            ),
            seed = seed
        )
    }

    fun marker(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_8_marker",
            name = "Marker",
            description = "Translucent overlapping alcohol ink bands with chisel-tip edges",
            styleId = ColorStyleId.MARKER,
            stages = listOf(
                BaseFillStageConfig(name = "Alcohol Marker Flats", opacity = 0.90f),
                CelShadowStageConfig(name = "Layered Marker Overlap", shadowDarkness = 0.30f, opacity = 0.85f)
            ),
            seed = seed
        )
    }

    fun mangaScreentone(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_9_manga_screentone",
            name = "Manga Screentone",
            description = "Halftone dot patterns and line screens at calibrated angles for comic shading",
            styleId = ColorStyleId.MANGA_SCREENTONE,
            stages = listOf(
                BaseFillStageConfig(name = "Base Tones"),
                CelShadowStageConfig(name = "Ink Shadows", shadowDarkness = 0.50f),
                MediumTextureStageConfig(name = "Halftone Screentone", mediumType = MediumTextureStageConfig.MediumTextureType.SCREENTONE_DOTS, scale = 1.0f, roughness = 0.60f),
                PaletteTransformStageConfig(name = "Monochrome Ink Pass", transformType = PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE)
            ),
            seed = seed
        )
    }

    fun comicBook(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_10_comic_book",
            name = "Comic Book",
            description = "High-contrast dynamic ink shading with dot matrix fills and vivid pop accents",
            styleId = ColorStyleId.COMIC_BOOK,
            stages = listOf(
                BaseFillStageConfig(name = "Pop Art Flats"),
                CelShadowStageConfig(name = "Deep Contrast Inks", shadowDarkness = 0.55f),
                MediumTextureStageConfig(name = "Ben-Day Dot Matrix", mediumType = MediumTextureStageConfig.MediumTextureType.SCREENTONE_DOTS, scale = 1.5f),
                HighlightStageConfig(name = "Rim Highlights", intensity = 0.80f)
            ),
            seed = seed
        )
    }

    fun oilPaint(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_11_oil_paint",
            name = "Oil Paint",
            description = "Viscous multi-filament bristle ridges and tactile impasto relief lighting",
            styleId = ColorStyleId.OIL_PAINT,
            stages = listOf(
                BaseFillStageConfig(name = "Opaque Oil Base"),
                CelShadowStageConfig(name = "Viscous Shadows", shadowDarkness = 0.40f),
                MediumTextureStageConfig(name = "Impasto Bristle Relief", mediumType = MediumTextureStageConfig.MediumTextureType.OIL_IMPASTO, roughness = 0.65f),
                HighlightStageConfig(name = "Impasto Specular Sheen", intensity = 0.75f)
            ),
            seed = seed
        )
    }

    fun gouache(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_12_gouache",
            name = "Gouache",
            description = "Matte opaque coverage with crisp planar brushwork and velvety finish",
            styleId = ColorStyleId.GOUACHE,
            stages = listOf(
                BaseFillStageConfig(name = "Matte Opaque Base"),
                CelShadowStageConfig(name = "Planar Block Shadows", shadowDarkness = 0.35f),
                MediumTextureStageConfig(name = "Velvety Tooth", mediumType = MediumTextureStageConfig.MediumTextureType.PAPER_GRAIN, roughness = 0.20f)
            ),
            seed = seed
        )
    }

    fun pastel(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_13_pastel",
            name = "Pastel Palette",
            description = "Soft chalky pigment with low-saturation pastel tints and textured paper tooth",
            styleId = ColorStyleId.PASTEL,
            stages = listOf(
                BaseFillStageConfig(name = "Pastel Chalk Base", opacity = 0.90f),
                PaletteTransformStageConfig(name = "Pastel Tonal Shift", transformType = PaletteTransformStageConfig.TransformType.PASTEL_SHIFT, saturation = 0.65f),
                MediumTextureStageConfig(name = "Chalk Paper Tooth", mediumType = MediumTextureStageConfig.MediumTextureType.PAPER_GRAIN, roughness = 0.45f)
            ),
            seed = seed
        )
    }

    fun neonCyberpunk(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_14_neon_cyberpunk",
            name = "Neon / Cyberpunk",
            description = "High-energy electric glowing rims, chromatic bloom, and intense saturated highlights",
            styleId = ColorStyleId.NEON_CYBERPUNK,
            stages = listOf(
                BaseFillStageConfig(name = "Electric Flats"),
                CelShadowStageConfig(name = "Deep Synthetic Shadows", shadowDarkness = 0.60f),
                HighlightStageConfig(name = "Neon Rim Highlights", intensity = 0.95f),
                FinishingEffectStageConfig(name = "Neon Bloom Glow", effectType = FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM, bloomIntensity = 0.85f)
            ),
            seed = seed
        )
    }

    fun monochrome(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_15_monochrome",
            name = "Monochrome",
            description = "Sumie ink and grayscale tonal values strictly preserving luminance hierarchy",
            styleId = ColorStyleId.MONOCHROME,
            stages = listOf(
                BaseFillStageConfig(name = "Tonal Base"),
                CelShadowStageConfig(name = "Ink Wash Shadows", shadowDarkness = 0.50f),
                PaletteTransformStageConfig(name = "Rec. 709 Monochrome", transformType = PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE)
            ),
            seed = seed
        )
    }

    /**
     * Canonical multi-stage recipe demonstrating ordered semantics for Monochrome + Neon (Cyberpunk Noir).
     *
     * Monochrome desaturates the underlying base first, and Neon finishing effect adds glowing accents on top.
     */
    fun cyberpunkNoir(seed: Long = 42L): ColorStackRecipe {
        return ColorStackRecipe(
            id = "preset_cyberpunk_noir",
            name = "Cyberpunk Noir (Monochrome + Neon)",
            description = "Monochrome high-contrast grayscale base with vibrant neon glowing bloom accents on top",
            styleId = ColorStyleId.NEON_CYBERPUNK,
            stages = listOf(
                BaseFillStageConfig(name = "Base Flats"),
                CelShadowStageConfig(name = "High-Contrast Shadows", shadowDarkness = 0.55f),
                PaletteTransformStageConfig(name = "Monochrome Grayscale Base", transformType = PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE),
                FinishingEffectStageConfig(name = "Electric Neon Bloom", effectType = FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM, bloomIntensity = 0.90f)
            ),
            seed = seed
        )
    }

    fun getPresetForStyle(styleId: ColorStyleId, seed: Long = 42L): ColorStackRecipe {
        return when (styleId) {
            ColorStyleId.NO_COLOUR -> noColour(seed)
            ColorStyleId.CLASSIC_CEL -> classicCel(seed)
            ColorStyleId.SOFT_GRADIENT -> softGradient(seed)
            ColorStyleId.HYBRID_CEL_SOFT -> hybridCelSoft(seed)
            ColorStyleId.FLAT_ILLUSTRATION -> flatIllustration(seed)
            ColorStyleId.PAINTERLY -> painterly(seed)
            ColorStyleId.WATERCOLOUR -> watercolour(seed)
            ColorStyleId.COLOURED_PENCIL -> colouredPencil(seed)
            ColorStyleId.MARKER -> marker(seed)
            ColorStyleId.MANGA_SCREENTONE -> mangaScreentone(seed)
            ColorStyleId.COMIC_BOOK -> comicBook(seed)
            ColorStyleId.OIL_PAINT -> oilPaint(seed)
            ColorStyleId.GOUACHE -> gouache(seed)
            ColorStyleId.PASTEL -> pastel(seed)
            ColorStyleId.NEON_CYBERPUNK -> neonCyberpunk(seed)
            ColorStyleId.MONOCHROME -> monochrome(seed)
            ColorStyleId.CUSTOM -> classicCel(seed).copy(id = "preset_custom", name = "Custom Recipe", styleId = ColorStyleId.CUSTOM)
        }
    }

    fun allPresets(): List<ColorStackRecipe> {
        return ColorStyleId.entries.filter { it != ColorStyleId.CUSTOM }.map { getPresetForStyle(it) } + listOf(cyberpunkNoir())
    }
}
