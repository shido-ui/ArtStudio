package com.artstudio.cv

import com.artstudio.cv.centreline.CentrelineExtractionResult
import com.artstudio.cv.centreline.CentrelineSettings
import com.artstudio.cv.pathfit.ContourVectorizationResult
import com.artstudio.cv.pathfit.PathFitSettings

/**
 * Filter mode applied during initial preprocessing / denoising.
 */
enum class DenoiseMode {
    NONE,
    GAUSSIAN_BLUR,
    MEDIAN_DESPECKLE,
    BILATERAL
}

/**
 * Binarization / thresholding algorithm.
 */
enum class ThresholdMode {
    OTSU_GLOBAL,
    ADAPTIVE_LOCAL_MEAN,
    MANUAL
}

/**
 * Mathematical edge detection operator.
 */
enum class EdgeMode {
    SOBEL,
    CANNY
}

/**
 * Source mask used for vector contour discovery.
 */
enum class ContourSource {
    /** Auto-detect: prefers edge map if edge pixels are present, falling back to thresholded mask */
    AUTO,
    /** Extracts contours from edge detection operator output (Canny / Sobel) */
    EDGES,
    /** Extracts contours directly from binarized/despeckled threshold mask */
    THRESHOLD
}

/**
 * Debug overlay visualization modes that can be toggled without becoming part of the artwork.
 */
enum class CvDebugOverlayMode {
    EDGE_MAP,
    CONTOUR_POLYGONS,
    BINARY_THRESHOLD,
    DENOISED_PREVIEW,
    CENTRELINE_SKELETON,
    FITTED_PATHS,
    REGION_MASKS
}

/**
 * Point in 2D coordinate space.
 */
data class CvPoint(val x: Float, val y: Float)

/**
 * Axis-aligned bounding rectangle.
 */
data class CvRect(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = (maxX - minX + 1f).coerceAtLeast(0f)
    val height: Float get() = (maxY - minY + 1f).coerceAtLeast(0f)
}

/**
 * Extracted vector contour candidate with topology and geometric metrics.
 */
data class ContourCandidate(
    val id: Int,
    val points: List<CvPoint>,
    val simplifiedPoints: List<CvPoint>,
    val isHole: Boolean,
    val area: Double,
    val perimeter: Double,
    val boundingBox: CvRect,
    val centroid: CvPoint
)

/**
 * Fully configurable parameters for the deterministic computer vision pipeline.
 */
data class CvPipelineParameters(
    val denoiseMode: DenoiseMode = DenoiseMode.MEDIAN_DESPECKLE,
    val denoiseRadius: Int = 1,
    val gaussianSigma: Float = 1.0f,
    val thresholdMode: ThresholdMode = ThresholdMode.OTSU_GLOBAL,
    val manualThreshold: Int = 128,
    val adaptiveWindowRadius: Int = 7,
    val adaptiveConstantC: Int = 5,
    val edgeMode: EdgeMode = EdgeMode.CANNY,
    val sobelThreshold: Float = 40.0f,
    val cannyLowThreshold: Float = 30.0f,
    val cannyHighThreshold: Float = 80.0f,
    val contourMinArea: Float = 4.0f,
    val contourRdpEpsilon: Float = 1.5f,
    val invertPolarity: Boolean = false,
    val extractCentrelines: Boolean = false,
    val centrelineSettings: CentrelineSettings = CentrelineSettings(),
    val vectorizeContours: Boolean = false,
    val pathFitSettings: PathFitSettings = PathFitSettings(),
    val normalizeIllumination: Boolean = false,
    val illuminationRadius: Int = 32,
    val speckleRemovalMinPixels: Int = 0,
    val maxDimension: Int = 1536,
    val contourSource: ContourSource = ContourSource.AUTO
)

/**
 * Output result of the deterministic CV pipeline.
 */
data class CvPipelineResult(
    val width: Int,
    val height: Int,
    val grayscale: IntArray,
    val denoised: IntArray,
    val thresholded: IntArray,
    val edges: IntArray,
    val contours: List<ContourCandidate>,
    val elapsedTimeMs: Long,
    val peakMemoryBytes: Long,
    val parameters: CvPipelineParameters,
    val centrelineResult: CentrelineExtractionResult? = null,
    val vectorizationResult: ContourVectorizationResult? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CvPipelineResult) return false
        return width == other.width &&
                height == other.height &&
                contours == other.contours &&
                parameters == other.parameters &&
                edges.contentEquals(other.edges)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + contours.hashCode()
        result = 31 * result + parameters.hashCode()
        return result
    }
}
