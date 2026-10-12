package com.artstudio.cv.centreline

import com.artstudio.cv.CvPoint
import com.artstudio.cv.CvRect
import com.artstudio.reference.ReconstructionMode
import com.artstudio.vector.VectorAnchor
import com.artstudio.vector.VectorObject
import com.artstudio.vector.VectorShapeType
import com.artstudio.vector.VectorStyle
import java.util.UUID

/**
 * Types of structural ambiguities detected during centreline extraction that require artist review.
 */
enum class AmbiguityType {
    /** Junction has 3+ branches without a single dominant collinear continuation */
    AMBIGUOUS_FORK,

    /** Two stroke endpoints are within gap tolerance and point towards each other across a break */
    POTENTIAL_STROKE_BREAK,

    /** Multiple junction points clustered within 2 pixels, obscuring topology */
    JUNCTION_CLUSTER,

    /** Abrupt directional change on thin stroke with uncertain path progression */
    HIGH_CURVATURE_UNCERTAINTY
}

/**
 * Diagnostic review metadata attached to ambiguous forks or broken paths.
 * Ambiguous cases are NEVER silently discarded; they are flagged with structured review proposals.
 */
data class ReviewAmbiguity(
    val id: Int,
    val type: AmbiguityType,
    val location: CvPoint,
    val involvedPathIds: List<Int>,
    val description: String,
    val confidencePenalty: Float,
    val suggestedResolution: String
)

/**
 * Configurable settings for the deterministic centreline extraction pipeline.
 */
data class CentrelineSettings(
    /** Explicit binarization threshold [0..255] when manual thresholding is used */
    val binarizeThreshold: Int = 128,

    /** If true, uses Otsu binarization to automatically determine the threshold */
    val useAutoThreshold: Boolean = true,

    /** Invert polarity: true treats dark lines (< threshold) as foreground strokes on bright paper */
    val invertPolarity: Boolean = true,

    /** Minimum feature length in pixels; branches shorter than this are subject to pruning or low confidence */
    val minFeatureLength: Int = 3,

    /** If true, preserves short feature stems as low-confidence details rather than pruning them */
    val preserveShortDetails: Boolean = true,

    /** RDP decimation tolerance for path simplification */
    val rdpEpsilon: Float = 1.0f,

    /** Maximum distance between stroke endpoints to detect potential broken gaps */
    val gapDetectionDistance: Float = 6.0f,

    /** Maximum angular deviation (degrees) from 180° for two branches to be considered a continuous stroke */
    val junctionCollinearAngleDeg: Float = 40.0f,

    /** If true, computes local stroke width from the distance transform */
    val calculateStrokeWidth: Boolean = true,

    /** Force extraction even if the input reconstruction mode is not ideally suited for line art */
    val forceExtraction: Boolean = false
)

/**
 * A branch junction or fork node in the topological skeleton graph.
 */
data class JunctionNode(
    val id: Int,
    val location: CvPoint,
    val degree: Int,
    val connectedPathIds: List<Int>,
    val isAmbiguous: Boolean
)

/**
 * Plausible single-pen centreline path candidate extracted from raster line art.
 *
 * Rather than asserting that it reproduces the original artist's exact physical strokes,
 * it exposes an honest confidence metric and geometric metadata.
 */
data class CentrelinePathCandidate(
    val id: Int,
    val rawSkeletonPoints: List<CvPoint>,
    val simplifiedPoints: List<CvPoint>,
    val startPoint: CvPoint,
    val endPoint: CvPoint,
    val startTangent: CvPoint,
    val endTangent: CvPoint,
    val length: Float,
    val estimatedStrokeWidth: Float,
    val confidence: Float,
    val isLoop: Boolean,
    val isShortDetail: Boolean,
    val startConnectedJunctionId: Int? = null,
    val endConnectedJunctionId: Int? = null,
    val ambiguities: List<ReviewAmbiguity> = emptyList()
) {
    /**
     * Converts this candidate into an editable Art Studio [VectorObject].
     */
    fun toVectorObject(
        name: String = "Centreline Stroke #$id",
        style: VectorStyle = VectorStyle(
            strokeWidth = estimatedStrokeWidth.coerceAtLeast(1.0f),
            strokeColor = 0xFF000000L
        )
    ): VectorObject {
        val points = if (simplifiedPoints.size >= 2) simplifiedPoints else rawSkeletonPoints
        val anchors = points.map { pt ->
            VectorAnchor(x = pt.x, y = pt.y, isSmooth = true)
        }
        return VectorObject(
            id = UUID.randomUUID().toString(),
            name = name,
            shapeType = VectorShapeType.POLYLINE,
            anchors = anchors,
            isClosed = isLoop,
            style = style
        )
    }
}

/**
 * Structured comparison report between ground-truth synthetic paths and extracted centreline paths.
 */
data class SyntheticPathComparisonReport(
    val groundTruthLength: Float,
    val extractedLength: Float,
    val startEndpointError: Float,
    val endEndpointError: Float,
    val meanEndpointError: Float,
    val meanGeometryError: Float,
    val maxGeometryError: Float,
    val lengthDeviationRatio: Float
)

/**
 * Comprehensive output result of the centreline extraction pipeline.
 */
data class CentrelineExtractionResult(
    val width: Int,
    val height: Int,
    val isSupported: Boolean,
    val supportDiagnostic: String,
    val skeletonMask: IntArray,
    val distanceTransform: FloatArray,
    val paths: List<CentrelinePathCandidate>,
    val junctions: List<JunctionNode>,
    val ambiguities: List<ReviewAmbiguity>,
    val unresolvedBreakCount: Int,
    val ambiguousForkCount: Int,
    val elapsedTimeMs: Long,
    val peakMemoryBytes: Long,
    val settings: CentrelineSettings
) {
    val totalPathCount: Int get() = paths.size
    val totalJunctionCount: Int get() = junctions.size
    val totalAmbiguityCount: Int get() = ambiguities.size
}

/**
 * Utility determining whether an input reconstruction mode supports single-pen centreline extraction.
 */
object CentrelineModeGating {
    fun isModeSupported(mode: ReconstructionMode): Boolean {
        return when (mode) {
            ReconstructionMode.LINE_ART_VECTORIZATION,
            ReconstructionMode.PENCIL_CLEANUP_AND_INK -> true
            ReconstructionMode.FLAT_REGION_CEL_SHADING,
            ReconstructionMode.MULTI_LAYER_HYBRID_DECOMPOSITION,
            ReconstructionMode.CONTINUOUS_TONE_PAINT_LAYER -> false
        }
    }
}
