package com.artstudio.cv.palette

import com.artstudio.color.ColorData
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Rec. 709 perceived luminance computation [0.0, 1.0].
 */
val ColorData.perceivedLuminance: Float
    get() = (0.2126f * red + 0.7152f * green + 0.0722f * blue).coerceIn(0f, 1f)

/**
 * Inferred topological and visual lighting roles for colors associated with detected regions.
 *
 * In accordance with Prompt 57:
 * - Distinguishes raw observed pixel colors from inferred roles.
 * - Does NOT claim untested semantic biological interpretations (e.g. skin, hair, eyes).
 */
enum class InferredColorRole(val displayName: String, val badgeColor: Long) {
    /** Primary or midtone color of the detected region */
    BASE_COLOR("Base Midtone", 0xFF2196F3),

    /** Darker tonal variant relative to an adjacent or enclosing base color */
    SHADOW("Shadow / Shade", 0xFF5C6BC0),

    /** Lighter tonal variant relative to an adjacent base color */
    HIGHLIGHT("Highlight", 0xFFFFD54F),

    /** High-contrast dark perimeter contour or inking line */
    OUTLINE("Outline / Ink", 0xFF212121),

    /** Distinct high-chroma contrast color occurring in smaller proportions */
    ACCENT("Accent Contrast", 0xFFFF4081),

    /** Spatially dominant, canvas-filling or peripheral enclosing neutral area */
    BACKGROUND("Background", 0xFF78909C),

    /** Observed color without confident topological lighting role */
    UNASSIGNED("Observed Neutral", 0xFF9E9E9E)
}

/**
 * Strategy controlling how extracted reference colors are applied to drawing and fill layers.
 */
enum class PaletteAdaptationMode(val displayName: String, val description: String) {
    /** Strictly retain exact observed reference colors without modification */
    PRESERVE_SOURCE(
        "Preserve Source",
        "Strictly retains exact observed reference colors without alteration."
    ),

    /** Harmonize micro-variations and snap noisy edge pixels to nearest dominant palette swatch */
    GENTLE_ADAPTATION(
        "Gentle Adaptation",
        "Harmonizes micro-variations and snaps noisy edge pixels to clean representative swatches."
    ),

    /** Remap colors to aesthetic harmonic schemes while preserving relative luminance roles */
    ARTISTIC_REINTERPRETATION(
        "Artistic Reinterpretation",
        "Remaps colors to aesthetic harmonic schemes while strictly preserving relative shadow/light relationships."
    )
}

/**
 * Aesthetic harmonic color schemes available for [PaletteAdaptationMode.ARTISTIC_REINTERPRETATION].
 */
enum class ArtisticScheme(val displayName: String) {
    ORIGINAL_TONAL("Original Tonal"),
    WARM_SUNSET("Warm Sunset Shift"),
    COOL_TWILIGHT("Cool Twilight Shift"),
    VIBRANT_POP("Vibrant Anime Pop"),
    MUTED_EARTH("Muted Earth Tones"),
    MONOCHROME_INK("Monochrome Ink")
}

/**
 * Distinct representative color swatch extracted from the source image.
 */
data class SourcePaletteSwatch(
    val id: String = "swatch_${UUID.randomUUID().toString().take(8)}",
    val color: ColorData,
    val hex: String = color.hexRgb,
    val pixelCount: Int,
    val frequency: Float, // Fraction of total non-transparent pixels [0.0, 1.0]
    val luminance: Float, // Rec. 709 perceived luminance [0.0, 1.0]
    val inferredRole: InferredColorRole = InferredColorRole.UNASSIGNED,
    val isTransparentPlaceholder: Boolean = false
) {
    init {
        require(frequency in 0.0f..1.001f) { "Frequency must be in [0, 1], got $frequency" }
        require(luminance in -0.001f..1.001f) { "Luminance must be in [0, 1], got $luminance" }
    }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        map["hex"] = hex
        map["r"] = color.r
        map["g"] = color.g
        map["b"] = color.b
        map["a"] = color.a
        map["pixelCount"] = pixelCount
        map["frequency"] = frequency
        map["luminance"] = luminance
        map["inferredRole"] = inferredRole.name
        map["isTransparentPlaceholder"] = isTransparentPlaceholder
        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): SourcePaletteSwatch {
            val r = (map["r"] as? Number)?.toFloat() ?: 0f
            val g = (map["g"] as? Number)?.toFloat() ?: 0f
            val b = (map["b"] as? Number)?.toFloat() ?: 0f
            val a = (map["a"] as? Number)?.toFloat() ?: 1.0f
            val color = ColorData(r, g, b, a)

            val roleName = map["inferredRole"]?.toString() ?: InferredColorRole.UNASSIGNED.name
            val role = runCatching { InferredColorRole.valueOf(roleName) }
                .getOrDefault(InferredColorRole.UNASSIGNED)

            return SourcePaletteSwatch(
                id = map["id"]?.toString() ?: "swatch_unknown",
                color = color,
                hex = map["hex"]?.toString() ?: color.hexRgb,
                pixelCount = (map["pixelCount"] as? Number)?.toInt() ?: 0,
                frequency = (map["frequency"] as? Number)?.toFloat() ?: 0f,
                luminance = (map["luminance"] as? Number)?.toFloat() ?: color.perceivedLuminance,
                inferredRole = role,
                isTransparentPlaceholder = (map["isTransparentPlaceholder"] as? Boolean) ?: false
            )
        }
    }
}

/**
 * Color association mapping for a single detected region.
 *
 * Maintains strict separation between:
 * - [observedColor]: The unadulterated mean/median color observed within the source reference.
 * - [assignedColor]: The effective color generated by the selected adaptation mode.
 * - [userOverrideColor]: Explicit artist override if manually reassigned.
 * - [inferredRole]: The detected lighting/topological role (`BASE_COLOR`, `SHADOW`, `HIGHLIGHT`, etc.).
 */
data class RegionColorAssignment(
    val regionId: String,
    val observedColor: ColorData,
    val observedLuminance: Float = observedColor.perceivedLuminance,
    val colorVariance: Float = 0.0f, // Intra-region chromatic spread (flat color vs gradient)
    val mappedSwatchId: String,
    val assignedColor: ColorData,
    val inferredRole: InferredColorRole = InferredColorRole.BASE_COLOR,
    val userOverrideColor: ColorData? = null,
    val confidence: Float = 0.95f
) {
    val isUserOverridden: Boolean get() = userOverrideColor != null
    val effectiveColor: ColorData get() = userOverrideColor ?: assignedColor

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["regionId"] = regionId
        map["observedHex"] = observedColor.hexRgb
        map["observedR"] = observedColor.r
        map["observedG"] = observedColor.g
        map["observedB"] = observedColor.b
        map["observedA"] = observedColor.a
        map["observedLuminance"] = observedLuminance
        map["colorVariance"] = colorVariance
        map["mappedSwatchId"] = mappedSwatchId
        map["assignedHex"] = assignedColor.hexRgb
        map["assignedR"] = assignedColor.r
        map["assignedG"] = assignedColor.g
        map["assignedB"] = assignedColor.b
        map["assignedA"] = assignedColor.a
        map["inferredRole"] = inferredRole.name
        userOverrideColor?.let {
            map["overrideHex"] = it.hexRgb
            map["overrideR"] = it.r
            map["overrideG"] = it.g
            map["overrideB"] = it.b
            map["overrideA"] = it.a
        }
        map["confidence"] = confidence
        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): RegionColorAssignment {
            val obsR = (map["observedR"] as? Number)?.toFloat() ?: 0f
            val obsG = (map["observedG"] as? Number)?.toFloat() ?: 0f
            val obsB = (map["observedB"] as? Number)?.toFloat() ?: 0f
            val obsA = (map["observedA"] as? Number)?.toFloat() ?: 1f
            val observed = ColorData(obsR, obsG, obsB, obsA)

            val assR = (map["assignedR"] as? Number)?.toFloat() ?: obsR
            val assG = (map["assignedG"] as? Number)?.toFloat() ?: obsG
            val assB = (map["assignedB"] as? Number)?.toFloat() ?: obsB
            val assA = (map["assignedA"] as? Number)?.toFloat() ?: obsA
            val assigned = ColorData(assR, assG, assB, assA)

            val overrideColor = if (map.containsKey("overrideR")) {
                val ovR = (map["overrideR"] as? Number)?.toFloat() ?: 0f
                val ovG = (map["overrideG"] as? Number)?.toFloat() ?: 0f
                val ovB = (map["overrideB"] as? Number)?.toFloat() ?: 0f
                val ovA = (map["overrideA"] as? Number)?.toFloat() ?: 1f
                ColorData(ovR, ovG, ovB, ovA)
            } else null

            val roleName = map["inferredRole"]?.toString() ?: InferredColorRole.BASE_COLOR.name
            val role = runCatching { InferredColorRole.valueOf(roleName) }
                .getOrDefault(InferredColorRole.BASE_COLOR)

            return RegionColorAssignment(
                regionId = map["regionId"]?.toString() ?: "",
                observedColor = observed,
                observedLuminance = (map["observedLuminance"] as? Number)?.toFloat() ?: observed.perceivedLuminance,
                colorVariance = (map["colorVariance"] as? Number)?.toFloat() ?: 0f,
                mappedSwatchId = map["mappedSwatchId"]?.toString() ?: "",
                assignedColor = assigned,
                inferredRole = role,
                userOverrideColor = overrideColor,
                confidence = (map["confidence"] as? Number)?.toFloat() ?: 0.95f
            )
        }
    }
}

/**
 * Contact adjacency and relative lighting relationship between two neighboring regions.
 */
data class RegionAdjacency(
    val regionId1: String,
    val regionId2: String,
    val contactLengthPx: Int,
    val luminanceDelta: Float, // L1 - L2
    val colorDistance: Float, // Normalized distance [0.0, 1.0]
    val isLikelyShadowPair: Boolean = false,
    val isLikelyHighlightPair: Boolean = false
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["regionId1"] = regionId1
        map["regionId2"] = regionId2
        map["contactLengthPx"] = contactLengthPx
        map["luminanceDelta"] = luminanceDelta
        map["colorDistance"] = colorDistance
        map["isLikelyShadowPair"] = isLikelyShadowPair
        map["isLikelyHighlightPair"] = isLikelyHighlightPair
        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): RegionAdjacency {
            return RegionAdjacency(
                regionId1 = map["regionId1"]?.toString() ?: "",
                regionId2 = map["regionId2"]?.toString() ?: "",
                contactLengthPx = (map["contactLengthPx"] as? Number)?.toInt() ?: 0,
                luminanceDelta = (map["luminanceDelta"] as? Number)?.toFloat() ?: 0f,
                colorDistance = (map["colorDistance"] as? Number)?.toFloat() ?: 0f,
                isLikelyShadowPair = (map["isLikelyShadowPair"] as? Boolean) ?: false,
                isLikelyHighlightPair = (map["isLikelyHighlightPair"] as? Boolean) ?: false
            )
        }
    }
}

/**
 * Complete immutable output of the source palette analysis.
 *
 * Stored separately from downstream style output and available to every Colour Lab style.
 */
data class SourcePaletteAnalysis(
    val id: String = "palette_analysis_${UUID.randomUUID().toString().take(8)}",
    val globalPalette: List<SourcePaletteSwatch>,
    val regionAssignments: Map<String, RegionColorAssignment>,
    val adjacencies: List<RegionAdjacency> = emptyList(),
    val adaptationMode: PaletteAdaptationMode = PaletteAdaptationMode.PRESERVE_SOURCE,
    val artisticScheme: ArtisticScheme = ArtisticScheme.ORIGINAL_TONAL,
    val paletteExtractionErrorRmse: Float = 0.0f,
    val transparentPixelCount: Int = 0,
    val totalAnalyzedPixels: Int = 0,
    val provenanceTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Exports effective region colors mapped by region ID for consumption by all downstream color styles.
     */
    fun toColorStylePalette(): Map<String, ColorData> {
        return regionAssignments.mapValues { it.value.effectiveColor }
    }

    /**
     * Resolves an effective color for a given region, applying user override if present.
     */
    fun getEffectiveColor(regionId: String): ColorData? {
        return regionAssignments[regionId]?.effectiveColor
    }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        map["globalPalette"] = globalPalette.map { it.toMap() }
        map["regionAssignments"] = regionAssignments.mapValues { it.value.toMap() }
        map["adjacencies"] = adjacencies.map { it.toMap() }
        map["adaptationMode"] = adaptationMode.name
        map["artisticScheme"] = artisticScheme.name
        map["paletteExtractionErrorRmse"] = paletteExtractionErrorRmse
        map["transparentPixelCount"] = transparentPixelCount
        map["totalAnalyzedPixels"] = totalAnalyzedPixels
        map["provenanceTimestamp"] = provenanceTimestamp
        return map
    }
}
