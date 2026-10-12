package com.artstudio.reference

import java.io.File

/**
 * Recognized raster and layered reference file formats.
 */
enum class ReferenceFormat {
    PNG,
    JPEG,
    WEBP,
    PSD,
    OPEN_RASTER,
    SVG,
    UNKNOWN,
    CORRUPT
}

/**
 * Categorization of reference transparency.
 */
enum class TransparencyType {
    /** Entire reference is 100% opaque (alpha = 255 for all pixels). */
    FULLY_OPAQUE,

    /** Reference contains sharp cutout alpha (pixels are either fully opaque or fully transparent). */
    BINARY_MASK,

    /** Reference contains smooth, continuous alpha gradations (e.g. watercolor washes, soft drop shadows). */
    SEMI_TRANSPARENT_GRADIENT
}

/**
 * Detailed transparency analysis and alpha bounding box.
 */
data class TransparencyProfile(
    val hasAlpha: Boolean,
    val type: TransparencyType,
    val transparentPixelCount: Int,
    val totalPixelCount: Int,
    val alphaRatio: Float,
    val contentBoundingBox: IntBoundingBox?
)

/**
 * Bounding box in integer pixel coordinates.
 */
data class IntBoundingBox(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int
) {
    val width: Int get() = (maxX - minX + 1).coerceAtLeast(0)
    val height: Int get() = (maxY - minY + 1).coerceAtLeast(0)
}

/**
 * Color and luminance characteristics extracted from the reference.
 */
data class ColorProfileEstimate(
    val isGrayscale: Boolean,
    val uniqueColorCountEstimate: Int,
    val meanLuminance: Float,
    val contrastRatio: Float,
    val hasHighFrequencyNoise: Boolean,
    val edgeDensity: Float
)

/**
 * Inferred artistic or photographic classification of the imported reference.
 */
enum class LikelyImageClass {
    /** High-contrast monochrome contours on clean or transparent background. */
    LINE_ART,

    /** Graphite, pencil, or charcoal sketch with mid-tone grain, paper tooth, and soft edges. */
    SKETCH,

    /** Distinct uniform color patches with sharp cell boundaries and low internal variance. */
    FLAT_COLOUR,

    /** Rich layered artwork combining crisp inking contours with multi-shade coloring. */
    DETAILED_ILLUSTRATION,

    /** Continuous-tone gradients, complex natural lighting, or organic painterly brush textures. */
    PHOTOGRAPHIC_PAINTERLY
}

/**
 * Proposed reconstruction and vectorization strategy for downstream analysis.
 */
enum class ReconstructionMode {
    /** Clean contour vectorization, morphological thinning, and Bezier path generation. */
    LINE_ART_VECTORIZATION,

    /** Contrast normalization, adaptive thresholding, grain despeckle, and ink extraction. */
    PENCIL_CLEANUP_AND_INK,

    /** Palette quantization, barrier region trapping, and flat cel fill separation. */
    FLAT_REGION_CEL_SHADING,

    /** Multi-pass line art isolation, base flat layer extraction, and shadow/highlight separation. */
    MULTI_LAYER_HYBRID_DECOMPOSITION,

    /** Continuous-tone raster underlay with organic painterly brush stroke guidance. */
    CONTINUOUS_TONE_PAINT_LAYER
}

/**
 * Reconstruction mode recommendation with visible confidence, rationale, and limitations.
 */
data class ReconstructionSuggestion(
    val suggestedMode: ReconstructionMode,
    val confidence: Float,
    val reasoning: String,
    val visibleLimitations: List<String>,
    val userOverride: ReconstructionMode? = null
) {
    /** Active mode used for analysis (honors user override if present). */
    val effectiveMode: ReconstructionMode get() = userOverride ?: suggestedMode
}

/**
 * Normalizes coordinate mapping between the raw reference file and the canonical analysis orientation
 * without mutating or altering the preserved reference source file on disk.
 */
data class CoordinateNormalization(
    val exifOrientation: Int,
    val rawWidth: Int,
    val rawHeight: Int,
    val normalizedWidth: Int,
    val normalizedHeight: Int
) {
    /**
     * Maps raw reference pixel coordinates to normalized upright analysis coordinates.
     */
    fun rawToNormalizedPixel(rawX: Int, rawY: Int): Pair<Int, Int> {
        val clampedX = rawX.coerceIn(0, rawWidth - 1)
        val clampedY = rawY.coerceIn(0, rawHeight - 1)
        return when (exifOrientation) {
            1 -> Pair(clampedX, clampedY)
            2 -> Pair(rawWidth - 1 - clampedX, clampedY)
            3 -> Pair(rawWidth - 1 - clampedX, rawHeight - 1 - clampedY)
            4 -> Pair(clampedX, rawHeight - 1 - clampedY)
            5 -> Pair(clampedY, clampedX)
            6 -> Pair(rawHeight - 1 - clampedY, clampedX)
            7 -> Pair(rawHeight - 1 - clampedY, rawWidth - 1 - clampedX)
            8 -> Pair(clampedY, rawWidth - 1 - clampedX)
            else -> Pair(clampedX, clampedY)
        }
    }

    /**
     * Maps normalized analysis pixel coordinates back to the preserved raw reference coordinates.
     */
    fun normalizedToRawPixel(normX: Int, normY: Int): Pair<Int, Int> {
        val clampedX = normX.coerceIn(0, normalizedWidth - 1)
        val clampedY = normY.coerceIn(0, normalizedHeight - 1)
        return when (exifOrientation) {
            1 -> Pair(clampedX, clampedY)
            2 -> Pair(rawWidth - 1 - clampedX, clampedY)
            3 -> Pair(rawWidth - 1 - clampedX, rawHeight - 1 - clampedY)
            4 -> Pair(clampedX, rawHeight - 1 - clampedY)
            5 -> Pair(clampedY, clampedX)
            6 -> Pair(clampedY, rawHeight - 1 - clampedX)
            7 -> Pair(rawWidth - 1 - clampedY, rawHeight - 1 - clampedX)
            8 -> Pair(rawWidth - 1 - clampedY, clampedX)
            else -> Pair(clampedX, clampedY)
        }
    }

    /**
     * Maps continuous floating-point coordinates from raw to normalized space.
     */
    fun rawToNormalized(rawX: Float, rawY: Float): Pair<Float, Float> {
        return when (exifOrientation) {
            1 -> Pair(rawX, rawY)
            2 -> Pair((rawWidth - 1) - rawX, rawY)
            3 -> Pair((rawWidth - 1) - rawX, (rawHeight - 1) - rawY)
            4 -> Pair(rawX, (rawHeight - 1) - rawY)
            5 -> Pair(rawY, rawX)
            6 -> Pair((rawHeight - 1) - rawY, rawX)
            7 -> Pair((rawHeight - 1) - rawY, (rawWidth - 1) - rawX)
            8 -> Pair(rawY, (rawWidth - 1) - rawX)
            else -> Pair(rawX, rawY)
        }
    }

    /**
     * Maps continuous floating-point coordinates from normalized space back to raw space.
     */
    fun normalizedToRaw(normX: Float, normY: Float): Pair<Float, Float> {
        return when (exifOrientation) {
            1 -> Pair(normX, normY)
            2 -> Pair((rawWidth - 1) - normX, normY)
            3 -> Pair((rawWidth - 1) - normX, (rawHeight - 1) - normY)
            4 -> Pair(normX, (rawHeight - 1) - normY)
            5 -> Pair(normY, normX)
            6 -> Pair(normY, (rawHeight - 1) - normX)
            7 -> Pair((rawWidth - 1) - normY, (rawHeight - 1) - normX)
            8 -> Pair((rawWidth - 1) - normY, normX)
            else -> Pair(normX, normY)
        }
    }
}

/**
 * Memory footprint advice for admission gating.
 */
enum class MemoryAdvice {
    NORMAL_MEMORY,
    HIGH_MEMORY_WARN,
    EXCEEDS_BUDGET_DOWNSAMPLE_RECOMMENDED
}

/**
 * Memory footprint estimate and actual measured decode peak.
 */
data class MemoryEstimate(
    val estimatedDecodedBytes: Long,
    val actualDecodePeakBytes: Long,
    val heapBudgetFraction: Float,
    val advice: MemoryAdvice
)

/**
 * Authoritative inspection report produced before initiating heavy analysis pipelines.
 */
data class ReferenceInspectionReport(
    val isSuccess: Boolean,
    val sourceName: String,
    val sourceByteLength: Long,
    val format: ReferenceFormat,
    val rawDimensions: Pair<Int, Int>,
    val normalizedDimensions: Pair<Int, Int>,
    val exifOrientation: Int,
    val coordinateNormalization: CoordinateNormalization,
    val transparencyProfile: TransparencyProfile?,
    val colorProfile: ColorProfileEstimate?,
    val likelyImageClass: LikelyImageClass,
    val suggestion: ReconstructionSuggestion,
    val memoryEstimate: MemoryEstimate,
    val normalizedPixels: IntArray? = null,
    val isCorrupt: Boolean = false,
    val errorDetail: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReferenceInspectionReport) return false
        return isSuccess == other.isSuccess &&
                sourceName == other.sourceName &&
                sourceByteLength == other.sourceByteLength &&
                format == other.format &&
                rawDimensions == other.rawDimensions &&
                normalizedDimensions == other.normalizedDimensions &&
                exifOrientation == other.exifOrientation &&
                likelyImageClass == other.likelyImageClass &&
                suggestion == other.suggestion &&
                isCorrupt == other.isCorrupt &&
                errorDetail == other.errorDetail
    }

    override fun hashCode(): Int {
        var result = isSuccess.hashCode()
        result = 31 * result + sourceName.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + rawDimensions.hashCode()
        result = 31 * result + normalizedDimensions.hashCode()
        result = 31 * result + likelyImageClass.hashCode()
        return result
    }
}
