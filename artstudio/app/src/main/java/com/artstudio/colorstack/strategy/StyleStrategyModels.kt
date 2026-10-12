package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.cv.palette.SourcePaletteAnalysis
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.LayerTransform
import java.util.UUID

/**
 * Control setting governing how source reference colours are preserved or adapted
 * during style rendering passes.
 */
enum class SourcePreservationMode(val displayName: String, val description: String) {
    /** Retains exact observed RGB mean values from reference image pixels */
    PRESERVE_EXACT("Preserve Exact Source Colors", "Keeps 100% of raw observed reference colors unmutated"),

    /** Snaps region colors to the nearest representative global swatch to eliminate compression noise */
    GENTLE_ADAPT("Gentle Palette Adaptation", "Snaps region tones to clean dominant swatches while preserving hue"),

    /** Rebalances hues and values along harmonic aesthetic schemes while preserving luminance hierarchy */
    HARMONIC_TONAL("Harmonic Tonal Reinterpretation", "Applies aesthetic color harmony while maintaining shadow order")
}

/**
 * Functional role classification for an isolated style layer.
 */
enum class LayerRole {
    BASE_FLAT,
    SHADOW,
    GRADIENT,
    HIGHLIGHT,
    TEXTURE,
    LINEWORK
}

/**
 * Isolated raster layer output produced by a style strategy,
 * maintaining independent editability for non-destructive document baking.
 */
data class StyleLayerOutput(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: LayerRole,
    val pixels: IntArray, // 32-bit ARGB raster of size width * height
    val blendMode: BlendModeType = BlendModeType.Normal,
    val opacity: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StyleLayerOutput) return false
        return id == other.id &&
                name == other.name &&
                role == other.role &&
                blendMode == other.blendMode &&
                opacity == other.opacity &&
                pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + blendMode.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

/**
 * Base configuration interface for style strategies.
 */
interface StyleStrategyConfig {
    val sourcePreservation: SourcePreservationMode
}

/**
 * Configuration for Style 1: Classic Cel Shading.
 */
data class ClassicCelConfig(
    val shadowDarkness: Float = 0.45f, // [0.0, 1.0] Darkness ratio of shadow
    val lightAngleDeg: Float = 45.0f, // Direction of incident light
    val shadowExtent: Float = 0.50f, // [0.0, 1.0] Spatial coverage of shadow within region
    val highlightIntensity: Float = 0.70f, // [0.0, 1.0] Specular rim intensity
    val coolShadowTint: Boolean = true, // Cool indigo/blue tint for anime-style shadows
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(shadowDarkness in 0.0f..1.0f) { "shadowDarkness must be in [0, 1], got $shadowDarkness" }
        require(shadowExtent in 0.0f..1.0f) { "shadowExtent must be in [0, 1], got $shadowExtent" }
        require(highlightIntensity in 0.0f..1.0f) { "highlightIntensity must be in [0, 1], got $highlightIntensity" }
    }
}

/**
 * Configuration for Style 2: Soft Gradient Shading.
 */
data class SoftGradientConfig(
    val softness: Float = 0.65f, // [0.1, 3.0] Falloff exponent
    val shadowRatio: Float = 0.40f, // [0.0, 1.0] Maximum shadow depth in gradients
    val lightAngleDeg: Float = 45.0f, // Direction of ambient light
    val ambientBleed: Float = 0.25f, // [0.0, 1.0] Edge attenuation towards boundaries
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(softness in 0.1f..3.0f) { "softness must be in [0.1, 3.0], got $softness" }
        require(shadowRatio in 0.0f..1.0f) { "shadowRatio must be in [0, 1], got $shadowRatio" }
        require(ambientBleed in 0.0f..1.0f) { "ambientBleed must be in [0, 1], got $ambientBleed" }
    }
}

/**
 * Configuration for Style 3: Hybrid Cel + Soft Shading.
 */
data class HybridCelSoftConfig(
    val coreShadowDarkness: Float = 0.40f, // [0.0, 1.0] Depth of hard cel shadow core
    val ambientSoftness: Float = 0.50f, // [0.1, 2.0] Softness of ambient gradient fill
    val celVsSoftBalance: Float = 0.55f, // [0.0, 1.0] 0.0 = all soft, 1.0 = all cel
    val lightAngleDeg: Float = 45.0f,
    val highlightIntensity: Float = 0.60f,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(coreShadowDarkness in 0.0f..1.0f) { "coreShadowDarkness must be in [0, 1], got $coreShadowDarkness" }
        require(ambientSoftness in 0.1f..2.0f) { "ambientSoftness must be in [0.1, 2.0], got $ambientSoftness" }
        require(celVsSoftBalance in 0.0f..1.0f) { "celVsSoftBalance must be in [0, 1], got $celVsSoftBalance" }
        require(highlightIntensity in 0.0f..1.0f) { "highlightIntensity must be in [0, 1], got $highlightIntensity" }
    }
}

/**
 * Mode of flat planar color presentation.
 */
enum class FlatToneMode {
    SOLID_FLAT,
    PLANAR_STEP
}

/**
 * Configuration for Style 4: Flat Illustration.
 */
data class FlatIllustrationConfig(
    val boundaryBleedPx: Int = 1, // [-2, 3] Dilation under line art to prevent halos
    val contrastBoost: Float = 0.15f, // [0.0, 1.0] Saturated graphic pop boost
    val toneMode: FlatToneMode = FlatToneMode.SOLID_FLAT,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(boundaryBleedPx in -2..3) { "boundaryBleedPx must be in [-2, 3], got $boundaryBleedPx" }
        require(contrastBoost in 0.0f..1.0f) { "contrastBoost must be in [0, 1], got $contrastBoost" }
    }
}

/**
 * Configuration for Style 5: Painterly.
 */
data class PainterlyConfig(
    val brushScale: Float = 1.0f, // [0.5, 3.0] Bristle mark scale
    val colorJitter: Float = 0.10f, // [0.0, 0.5] Chromatic dab variance
    val blendingSoftness: Float = 0.60f, // [0.1, 1.0] Wet edge diffusion factor
    val canvasTooth: Float = 0.30f, // [0.0, 1.0] Underlying canvas texture strength
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(brushScale in 0.5f..3.0f) { "brushScale must be in [0.5, 3.0], got $brushScale" }
        require(colorJitter in 0.0f..0.5f) { "colorJitter must be in [0, 0.5], got $colorJitter" }
        require(blendingSoftness in 0.1f..1.0f) { "blendingSoftness must be in [0.1, 1.0], got $blendingSoftness" }
        require(canvasTooth in 0.0f..1.0f) { "canvasTooth must be in [0, 1], got $canvasTooth" }
    }
}

/**
 * Configuration for Style 6: Watercolour.
 */
data class WatercolourConfig(
    val washWetness: Float = 0.65f, // [0.1, 1.0] Wet edge pigment diffusion
    val granulation: Float = 0.40f, // [0.0, 1.0] Pigment settling in cold-press paper grain
    val puddleRingDarkness: Float = 0.35f, // [0.0, 1.0] Darkening factor along drying puddle rims
    val edgeDiffusionPx: Int = 2, // [1, 5] Width of capillary drying boundary
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(washWetness in 0.1f..1.0f) { "washWetness must be in [0.1, 1], got $washWetness" }
        require(granulation in 0.0f..1.0f) { "granulation must be in [0, 1], got $granulation" }
        require(puddleRingDarkness in 0.0f..1.0f) { "puddleRingDarkness must be in [0, 1], got $puddleRingDarkness" }
        require(edgeDiffusionPx in 1..5) { "edgeDiffusionPx must be in [1, 5], got $edgeDiffusionPx" }
    }
}

/**
 * Configuration for Style 7: Coloured Pencil.
 */
data class ColouredPencilConfig(
    val hatchAngleDeg: Float = 45.0f, // Direction of pencil stroke hatching
    val strokeSpacingPx: Float = 3.0f, // [1.0, 10.0] Pitch between adjacent pencil strokes
    val grainToothStrength: Float = 0.50f, // [0.0, 1.0] Skipping on tooth valleys
    val pressureVariation: Float = 0.25f, // [0.0, 1.0] Hand-drawn stroke weight variance
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(strokeSpacingPx in 1.0f..10.0f) { "strokeSpacingPx must be in [1, 10], got $strokeSpacingPx" }
        require(grainToothStrength in 0.0f..1.0f) { "grainToothStrength must be in [0, 1], got $grainToothStrength" }
        require(pressureVariation in 0.0f..1.0f) { "pressureVariation must be in [0, 1], got $pressureVariation" }
    }
}

/**
 * Configuration for Style 8: Marker.
 */
data class MarkerConfig(
    val bandWidthPx: Float = 12.0f, // [4.0, 32.0] Chisel-tip nib width
    val overlapDarkening: Float = 0.30f, // [0.0, 1.0] Multiply optical density on stroke overlaps
    val capillaryBleedPx: Int = 1, // [0, 3] Porous paper ink bleed
    val strokeAngleDeg: Float = -30.0f, // Marker swipe angle
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(bandWidthPx in 4.0f..32.0f) { "bandWidthPx must be in [4, 32], got $bandWidthPx" }
        require(overlapDarkening in 0.0f..1.0f) { "overlapDarkening must be in [0, 1], got $overlapDarkening" }
        require(capillaryBleedPx in 0..3) { "capillaryBleedPx must be in [0, 3], got $capillaryBleedPx" }
    }
}

/**
 * Screentone dot pattern geometry.
 */
enum class ScreentoneDotShape {
    ROUND_DOT,
    ELLIPSE,
    LINE_SCREEN
}

/**
 * Configuration for Style 9: Manga Screentone.
 */
data class MangaScreentoneConfig(
    val screenPitchPx: Float = 6.0f, // [2.0, 20.0] Tone spacing / period between dots
    val screenAngleDeg: Float = 45.0f, // Screen rotation angle (default 45 deg)
    val dotShape: ScreentoneDotShape = ScreentoneDotShape.ROUND_DOT,
    val densityScale: Float = 0.60f, // [0.1, 1.0] Shading darkness scale
    val invertTones: Boolean = false,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(screenPitchPx in 2.0f..20.0f) { "screenPitchPx must be in [2, 20], got $screenPitchPx" }
        require(densityScale in 0.1f..1.0f) { "densityScale must be in [0.1, 1], got $densityScale" }
    }
}

/**
 * Configuration for Style 10: Comic Book.
 */
data class ComicBookConfig(
    val comicContrast: Float = 0.40f, // [0.0, 1.0] Dynamic pop steepening
    val benDayDotScale: Float = 8.0f, // [3.0, 24.0] Vintage Ben-Day dot pitch
    val inkShadowThreshold: Float = 0.45f, // [0.0, 1.0] Solid black shadow pool cutoff
    val colorOffsetPx: Int = 1, // [0, 3] Vintage misregistration print offset
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(comicContrast in 0.0f..1.0f) { "comicContrast must be in [0, 1], got $comicContrast" }
        require(benDayDotScale in 3.0f..24.0f) { "benDayDotScale must be in [3, 24], got $benDayDotScale" }
        require(inkShadowThreshold in 0.0f..1.0f) { "inkShadowThreshold must be in [0, 1], got $inkShadowThreshold" }
        require(colorOffsetPx in 0..3) { "colorOffsetPx must be in [0, 3], got $colorOffsetPx" }
    }
}

/**
 * Configuration for Style 0: No Colour Baseline.
 * Retains line art and contour reconstruction while skipping automatic colour fills,
 * preserving clean masks and original palette data for manual colouring.
 */
data class NoColourConfig(
    val paperBackground: Boolean = true, // true: pure opaque white paper (0xFFFFFFFF), false: transparent (0x00000000)
    val lineArtOpacity: Float = 1.0f, // [0.0, 1.0] Opacity of preserved linework
    val preserveRegionMasks: Boolean = true, // Keeps region contours and masks ready for manual painting
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(lineArtOpacity in 0.0f..1.0f) { "lineArtOpacity must be in [0, 1], got $lineArtOpacity" }
    }
}

/**
 * Configuration for Style 11: Oil Paint.
 * Simulates heavy impasto brush strokes, directional bristle ridges, specular relief lighting,
 * and coarse linen canvas weave.
 */
data class OilPaintConfig(
    val impastoDepth: Float = 0.50f, // [0.0, 1.0] Heightfield relief depth for specular ridges and valley shadows
    val bristleDensity: Float = 0.60f, // [0.1, 1.0] Frequency of multi-filament bristle tracks
    val canvasWeave: Float = 0.35f, // [0.0, 1.0] Coarse linen canvas tooth modulation
    val lightAngleDeg: Float = 45.0f, // Direction of incident light casting impasto highlights
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(impastoDepth in 0.0f..1.0f) { "impastoDepth must be in [0, 1], got $impastoDepth" }
        require(bristleDensity in 0.1f..1.0f) { "bristleDensity must be in [0.1, 1], got $bristleDensity" }
        require(canvasWeave in 0.0f..1.0f) { "canvasWeave must be in [0, 1], got $canvasWeave" }
    }
}

/**
 * Configuration for Style 12: Gouache.
 * Simulates opaque, non-reflective matte poster paint with crisp planar brushwork steps,
 * chalky velvety finish, and drying rim contours.
 */
data class GouacheConfig(
    val matteOpacity: Float = 0.90f, // [0.5, 1.0] Opaque matte pigment saturation
    val planarStepCount: Int = 4, // [2, 8] Discrete tonal planar quantization steps
    val edgeDryDarkening: Float = 0.25f, // [0.0, 1.0] Drying border rim contrast
    val chalkyFinish: Float = 0.20f, // [0.0, 1.0] Velvety matte diffuse desaturation
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(matteOpacity in 0.5f..1.0f) { "matteOpacity must be in [0.5, 1], got $matteOpacity" }
        require(planarStepCount in 2..8) { "planarStepCount must be in [2, 8], got $planarStepCount" }
        require(edgeDryDarkening in 0.0f..1.0f) { "edgeDryDarkening must be in [0, 1], got $edgeDryDarkening" }
        require(chalkyFinish in 0.0f..1.0f) { "chalkyFinish must be in [0, 1], got $chalkyFinish" }
    }
}

/**
 * Configuration for Style 13: Pastel Palette.
 * Simulates soft chalky pastel pigment with delicate tint lifting, crumbly particle scattering,
 * heavy paper tooth valley skipping, and soft smudge blending.
 */
data class PastelConfig(
    val chalkDustStrength: Float = 0.40f, // [0.0, 1.0] Crumbly particle texture intensity
    val pastelTintLift: Float = 0.35f, // [0.0, 1.0] Pastel whitening / desaturation shift
    val paperToothDepth: Float = 0.50f, // [0.1, 1.0] Tooth valley pigment skip threshold
    val smudgeSoftness: Float = 0.40f, // [0.0, 1.0] Boundary smudge blend softness
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(chalkDustStrength in 0.0f..1.0f) { "chalkDustStrength must be in [0, 1], got $chalkDustStrength" }
        require(pastelTintLift in 0.0f..1.0f) { "pastelTintLift must be in [0, 1], got $pastelTintLift" }
        require(paperToothDepth in 0.1f..1.0f) { "paperToothDepth must be in [0.1, 1], got $paperToothDepth" }
        require(smudgeSoftness in 0.0f..1.0f) { "smudgeSoftness must be in [0, 1], got $smudgeSoftness" }
    }
}

/**
 * Configuration for Style 14: Neon / Cyberpunk.
 * Simulates high-energy futuristic aesthetic with deep noir underbases, electric glowing contour rims,
 * saturated chromatic bloom, and specular neon spikes.
 */
data class NeonCyberpunkConfig(
    val glowRadiusPx: Int = 6, // [2, 16] Bounded optical bloom kernel radius
    val bloomIntensity: Float = 0.70f, // [0.1, 1.0] Intensity of electric bloom halo
    val darkBaseFloor: Float = 0.20f, // [0.0, 0.5] Deep dark undertone floor for high dynamic contrast
    val neonSaturationBoost: Float = 0.60f, // [0.0, 1.0] Hyper-saturation of dominant region colors
    val chromaticShiftPx: Int = 1, // [0, 3] Cyan/magenta edge fringing
    val seed: Long = 42L,
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(glowRadiusPx in 2..16) { "glowRadiusPx must be in [2, 16], got $glowRadiusPx" }
        require(bloomIntensity in 0.1f..1.0f) { "bloomIntensity must be in [0.1, 1], got $bloomIntensity" }
        require(darkBaseFloor in 0.0f..0.5f) { "darkBaseFloor must be in [0, 0.5], got $darkBaseFloor" }
        require(neonSaturationBoost in 0.0f..1.0f) { "neonSaturationBoost must be in [0, 1], got $neonSaturationBoost" }
        require(chromaticShiftPx in 0..3) { "chromaticShiftPx must be in [0, 3], got $chromaticShiftPx" }
    }
}

/**
 * Tonal tint variants for Style 15: Monochrome.
 */
enum class MonochromeTintMode {
    PURE_GRAYSCALE,
    COOL_SLATE,
    WARM_SEPIA,
    HIGH_CONTRAST_NOIR
}

/**
 * Configuration for Style 15: Monochrome.
 * Strictly preserves luminance hierarchy and contrast via Rec. 709 perceived lightness transformation,
 * while retaining underlying source palette data for non-destructive recipe interchange.
 */
data class MonochromeConfig(
    val contrast: Float = 1.15f, // [0.5, 2.0] S-curve tonal contrast
    val tintMode: MonochromeTintMode = MonochromeTintMode.PURE_GRAYSCALE,
    val inkWashTuning: Float = 0.10f, // [0.0, 0.5] Subtle wash dilution in highlights
    val blackPoint: Float = 0.05f, // [0.0, 0.3] Shadow crush threshold
    val whitePoint: Float = 0.95f, // [0.7, 1.0] Specular highlight cutoff
    override val sourcePreservation: SourcePreservationMode = SourcePreservationMode.PRESERVE_EXACT
) : StyleStrategyConfig {
    init {
        require(contrast in 0.5f..2.0f) { "contrast must be in [0.5, 2], got $contrast" }
        require(inkWashTuning in 0.0f..0.5f) { "inkWashTuning must be in [0, 0.5], got $inkWashTuning" }
        require(blackPoint in 0.0f..0.3f) { "blackPoint must be in [0, 0.3], got $blackPoint" }
        require(whitePoint in 0.7f..1.0f) { "whitePoint must be in [0.7, 1], got $whitePoint" }
        require(whitePoint > blackPoint) { "whitePoint ($whitePoint) must be greater than blackPoint ($blackPoint)" }
    }
}

/**
 * Input package supplied to style rendering strategies.
 */
data class StyleStrategyInput(
    val width: Int,
    val height: Int,
    val regionMasks: Map<String, ByteArray>,
    val regionColors: Map<String, ColorData>,
    val paletteAnalysis: SourcePaletteAnalysis? = null,
    val linework: LineworkData? = null,
    val config: StyleStrategyConfig,
    val quality: RenderQuality = RenderQuality.FINAL,
    val seed: Long = 42L
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
    }
}

/**
 * Comprehensive output bundle returned by style rendering strategies.
 */
data class StyleStrategyOutput(
    val styleId: ColorStyleId,
    val width: Int,
    val height: Int,
    val finalComposite: IntArray,
    val layers: List<StyleLayerOutput>,
    val renderTimeMs: Long,
    val memoryAllocatedBytes: Long,
    val recipeSnapshot: ColorStackRecipe
) {
    /**
     * Bakes the rendered style layers into an editable [LayerNode.LayerGroup] for native document integration.
     */
    fun toLayerGroup(
        document: ArtDocument,
        groupName: String = "${styleId.displayName} (${layers.size} Layers)"
    ): LayerNode.LayerGroup {
        val childLayers = layers.map { l ->
            LayerNode.RasterLayer(
                id = l.id,
                name = l.name,
                visible = true,
                opacity = l.opacity,
                blendMode = l.blendMode,
                width = width,
                height = height,
                transform = LayerTransform()
            )
        }
        return LayerNode.LayerGroup(
            id = UUID.randomUUID().toString(),
            name = groupName,
            visible = true,
            opacity = 1.0f,
            blendMode = BlendModeType.Normal,
            children = childLayers,
            isCollapsed = false
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StyleStrategyOutput) return false
        return styleId == other.styleId &&
                width == other.width &&
                height == other.height &&
                finalComposite.contentEquals(other.finalComposite) &&
                layers == other.layers
    }

    override fun hashCode(): Int {
        var result = styleId.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + finalComposite.contentHashCode()
        result = 31 * result + layers.hashCode()
        return result
    }
}

/**
 * Contract implemented by each dedicated Colour Lab rendering strategy.
 */
interface ColourStyleStrategy {
    val styleId: ColorStyleId
    val displayName: String
    val description: String
    val supportedInputModes: List<String>
    val documentedLimitations: String

    fun render(input: StyleStrategyInput): StyleStrategyOutput
}

/**
 * Resolves region color based on the selected SourcePreservationMode.
 */
fun resolveRegionColor(
    regionId: String,
    input: StyleStrategyInput,
    mode: SourcePreservationMode
): ColorData {
    val raw = input.regionColors[regionId] ?: ColorData(0.85f, 0.85f, 0.85f, 1.0f)
    return when (mode) {
        SourcePreservationMode.PRESERVE_EXACT -> raw
        SourcePreservationMode.GENTLE_ADAPT -> {
            val swatches = input.paletteAnalysis?.globalPalette
            if (!swatches.isNullOrEmpty()) {
                val closest = swatches.minByOrNull { s ->
                    val dr = s.color.red - raw.red
                    val dg = s.color.green - raw.green
                    val db = s.color.blue - raw.blue
                    dr * dr + dg * dg + db * db
                }
                closest?.color ?: raw
            } else raw
        }
        SourcePreservationMode.HARMONIC_TONAL -> {
            input.paletteAnalysis?.getEffectiveColor(regionId) ?: raw
        }
    }
}

