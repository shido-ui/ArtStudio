package com.artstudio.cv.region

import com.artstudio.cv.CvPoint
import java.util.UUID

/**
 * Topological characterization of a detected region's boundary perimeter.
 */
enum class RegionBoundaryType {
    /** Fully enclosed boundary by solid ink or color contour. */
    CLOSED_OUTLINE,

    /** Boundary has a gap or rupture that permits flood leakage unless bridged. */
    LEAKY_GAP,

    /** Soft gradient / anti-aliased edge requiring bleed expansion to avoid white fringing. */
    ANTI_ALIASED_FRINGE,

    /** Boundary formed by chromatic difference between two adjacent color areas without ink stroke. */
    ADJACENT_COLOUR_BARRIER,

    /** Boundary formed by alpha channel transition to transparent canvas. */
    TRANSPARENCY_SILHOUETTE
}

/**
 * Severity ranking for unsealed boundary gaps.
 */
enum class LeakSeverity {
    /** 1 to 2 pixel micro-opening easily bridged by low-radius morphological closing. */
    MINOR_TINY_GAP,

    /** 3 to 5 pixel opening requiring moderate bridging. */
    MODERATE_GAP,

    /** Large rupture where region contents spill broadly into adjacent canvas areas. */
    WIDE_RUPTURE
}

/**
 * Review record identifying a specific uncertain or leaky boundary location,
 * providing the user with coordinates, severity, and an actionable proposed bridging stroke.
 */
data class UncertainBoundaryReview(
    val id: String = UUID.randomUUID().toString(),
    val leakLocation: CvPoint,
    val gapDistance: Float,
    val severity: LeakSeverity,
    val proposedBridge: Pair<CvPoint, CvPoint>,
    val isUserCorrected: Boolean = false,
    val suggestedResolution: String = "Bridge gap between (${proposedBridge.first.x.toInt()}, ${proposedBridge.first.y.toInt()}) and (${proposedBridge.second.x.toInt()}, ${proposedBridge.second.y.toInt()}) to seal region."
)

/**
 * Documented parameters controlling region segmentation, gap closing, boundary expansion,
 * hole preservation, and leak detection.
 *
 * @param colorTolerance Color distance threshold in normalized 4-channel space [0.0, 1.0] (default 0.12).
 * @param gapClosingRadius Radius in pixels for morphological closing to bridge 1-to-N pixel line art breaks.
 * @param boundaryExpansion Pixels to expand (+dilate) or shrink (-erode) the mask into anti-aliased fringes.
 * @param antiAliasSmoothing Whether to soften binary mask transitions with edge anti-aliasing.
 * @param despeckleRadius Filter radius to remove stray 1-pixel graphite dust or speckles inside the mask.
 * @param preserveHoles Whether internal islands and holes are preserved (unmasked) rather than filled over.
 * @param detectLeaks Whether unconstrained vs barrier-constrained flood analysis is executed to detect leaks.
 * @param leakThresholdRatio Ratio of canvas area (default 0.65) beyond which an open flood is flagged as a leak.
 * @param fillColor Optional 32-bit ARGB color used to produce the separate [generatedFill] layer.
 * @param fillOpacity Opacity in [0.0, 1.0] applied when rendering the generated fill.
 */
data class RegionMaskConfig(
    val colorTolerance: Float = 0.12f,
    val gapClosingRadius: Int = 2,
    val boundaryExpansion: Int = 1,
    val antiAliasSmoothing: Boolean = true,
    val despeckleRadius: Int = 1,
    val preserveHoles: Boolean = true,
    val detectLeaks: Boolean = true,
    val leakThresholdRatio: Float = 0.65f,
    val fillColor: Int? = null,
    val fillOpacity: Float = 1.0f
) {
    init {
        require(colorTolerance in 0.0f..1.0f) { "colorTolerance must be in [0, 1], got $colorTolerance" }
        require(gapClosingRadius >= 0) { "gapClosingRadius must be non-negative, got $gapClosingRadius" }
        require(boundaryExpansion in -20..20) { "boundaryExpansion must be in [-20, 20], got $boundaryExpansion" }
        require(despeckleRadius >= 0) { "despeckleRadius must be non-negative, got $despeckleRadius" }
        require(leakThresholdRatio in 0.05f..1.0f) { "leakThresholdRatio must be in [0.05, 1], got $leakThresholdRatio" }
        require(fillOpacity in 0.0f..1.0f) { "fillOpacity must be in [0, 1], got $fillOpacity" }
    }
}

/**
 * Statistical spatial overlap metrics comparing an extracted region mask against ground-truth.
 */
data class MaskOverlapMetrics(
    val intersectionCount: Int,
    val unionCount: Int,
    val groundTruthCount: Int,
    val maskCount: Int,
    val iou: Double,
    val precision: Double,
    val recall: Double,
    val dice: Double,
    val falsePositives: Int,
    val falseNegatives: Int
)

/**
 * Full output of region segmentation, maintaining strict separation between:
 * 1. [sourceMask]: Raw uncorrected segmentation mask directly from connectivity/color match.
 * 2. [correctedMask]: Refined mask after gap-closing, boundary expansion, hole preservation, and smoothing.
 * 3. [generatedFill]: 32-bit ARGB raster pixel buffer with target fill color, opacity, and alpha.
 */
data class RegionSegmentationResult(
    val regionId: String,
    val width: Int,
    val height: Int,
    val sourceMask: ByteArray,
    val correctedMask: ByteArray,
    val generatedFill: IntArray?,
    val pixelCount: Int,
    val boundaryType: RegionBoundaryType,
    val uncertainBoundaries: List<UncertainBoundaryReview> = emptyList(),
    val isLeaky: Boolean = false,
    val hasHoles: Boolean = false,
    val overlapMetrics: MaskOverlapMetrics? = null
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(sourceMask.size == width * height) { "sourceMask size must equal width * height" }
        require(correctedMask.size == width * height) { "correctedMask size must equal width * height" }
        if (generatedFill != null) {
            require(generatedFill.size == width * height) { "generatedFill size must equal width * height" }
        }
    }

    /**
     * Checks if coordinates (x, y) are included in the refined corrected mask.
     */
    fun contains(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return (correctedMask[y * width + x].toInt() and 0xFF) > 128
    }

    /**
     * Returns true if there are open uncertain boundaries awaiting review.
     */
    val requiresReview: Boolean get() = isLeaky || uncertainBoundaries.any { !it.isUserCorrected }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegionSegmentationResult
        if (regionId != other.regionId) return false
        if (width != other.width || height != other.height) return false
        if (!sourceMask.contentEquals(other.sourceMask)) return false
        if (!correctedMask.contentEquals(other.correctedMask)) return false
        if (generatedFill != null) {
            if (other.generatedFill == null || !generatedFill.contentEquals(other.generatedFill)) return false
        } else if (other.generatedFill != null) return false
        if (boundaryType != other.boundaryType) return false
        if (isLeaky != other.isLeaky) return false
        if (uncertainBoundaries != other.uncertainBoundaries) return false

        return true
    }

    override fun hashCode(): Int {
        var result = regionId.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + sourceMask.contentHashCode()
        result = 31 * result + correctedMask.contentHashCode()
        result = 31 * result + (generatedFill?.contentHashCode() ?: 0)
        result = 31 * result + boundaryType.hashCode()
        result = 31 * result + isLeaky.hashCode()
        return result
    }
}

/**
 * Unified region mask representation bridging classical CV segmentation and optional AI-assisted proposals.
 */
data class RegionMask(
    val regionId: String,
    val width: Int,
    val height: Int,
    val mask: ByteArray,
    val semanticTag: com.artstudio.ai.model.RegionSemanticTag = com.artstudio.ai.model.RegionSemanticTag.UNKNOWN,
    val confidence: Float = 1.0f,
    val source: com.artstudio.ai.model.RegionSource = com.artstudio.ai.model.RegionSource.CLASSICAL_CV,
    val aiAugmentation: com.artstudio.ai.model.AiAugmentation? = null
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(mask.size == width * height) { "Mask size must match width * height" }
    }

    /**
     * Total number of selected pixels with alpha > 128.
     */
    val selectedPixelCount: Int
        get() {
            var count = 0
            for (i in mask.indices) {
                if ((mask[i].toInt() and 0xFF) > 128) count++
            }
            return count
        }

    /**
     * Checks if coordinates (x, y) are contained in the mask.
     */
    fun contains(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return (mask[y * width + x].toInt() and 0xFF) > 128
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegionMask) return false
        return regionId == other.regionId &&
                width == other.width &&
                height == other.height &&
                semanticTag == other.semanticTag &&
                confidence == other.confidence &&
                source == other.source &&
                aiAugmentation == other.aiAugmentation &&
                mask.contentEquals(other.mask)
    }

    override fun hashCode(): Int {
        var result = regionId.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + semanticTag.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (aiAugmentation?.hashCode() ?: 0)
        result = 31 * result + mask.contentHashCode()
        return result
    }
}

/**
 * Converts a [RegionSegmentationResult] into a [RegionMask].
 */
fun RegionSegmentationResult.toRegionMask(
    semanticTag: com.artstudio.ai.model.RegionSemanticTag = com.artstudio.ai.model.RegionSemanticTag.UNKNOWN,
    source: com.artstudio.ai.model.RegionSource = com.artstudio.ai.model.RegionSource.CLASSICAL_CV,
    aiAugmentation: com.artstudio.ai.model.AiAugmentation? = null
): RegionMask = RegionMask(
    regionId = regionId,
    width = width,
    height = height,
    mask = correctedMask.copyOf(),
    semanticTag = semanticTag,
    confidence = 1.0f,
    source = source,
    aiAugmentation = aiAugmentation
)
