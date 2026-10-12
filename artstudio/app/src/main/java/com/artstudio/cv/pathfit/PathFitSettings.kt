package com.artstudio.cv.pathfit

/**
 * Strategy mode for contour path fitting.
 */
enum class PathFittingMode {
    /**
     * Adaptive cubic Bézier spline fitting (Schneider algorithm) with corner preservation.
     */
    BEZIER_CURVES,

    /**
     * Polygonal polyline simplification using Ramer-Douglas-Peucker decimation.
     */
    POLYLINES,

    /**
     * Hybrid fitting: straight lines for collinear spans and cubic Bézier for curved arcs.
     */
    HYBRID
}

/**
 * Comprehensive configuration settings for converting discrete contour candidates
 * into editable geometric vector paths.
 *
 * @param maxFittingError Maximum allowable pixel deviation between the fitted curve/polyline
 *                        and the discrete contour points before splitting (default 2.0 px).
 * @param cornerAngleThresholdDeg Turning angle in degrees at or above which an anchor is
 *                                classified as a sharp corner/cusp (default 55.0°).
 * @param cornerWindowSize Neighborhood radius used for computing local tangent vectors (default 3).
 * @param preserveCorners Whether sharp corners are preserved as non-smooth cusp anchors.
 * @param preserveHoles Whether inner holes are detected, preserved, and associated with their outer regions.
 * @param preserveDisconnectedComponents Whether separate disconnected regions are vectorized as independent paths.
 * @param separateOutlineWidth Default outline/stroke width assigned to the vector style,
 *                             stored completely decoupled from path geometry.
 * @param minFeatureArea Minimum contour area in square pixels; smaller contours are pruned as noise.
 * @param fittingMode Geometric curve fitting model.
 * @param maxNewtonRaphsonIterations Number of parameter optimization passes per Bézier segment (default 3).
 */
data class PathFitSettings(
    val maxFittingError: Float = 2.0f,
    val cornerAngleThresholdDeg: Float = 55.0f,
    val cornerWindowSize: Int = 3,
    val preserveCorners: Boolean = true,
    val preserveHoles: Boolean = true,
    val preserveDisconnectedComponents: Boolean = true,
    val separateOutlineWidth: Float = 2.0f,
    val minFeatureArea: Float = 4.0f,
    val fittingMode: PathFittingMode = PathFittingMode.BEZIER_CURVES,
    val maxNewtonRaphsonIterations: Int = 3
) {
    init {
        require(maxFittingError > 0f) { "maxFittingError must be positive, got $maxFittingError" }
        require(cornerAngleThresholdDeg in 10.0f..170.0f) {
            "cornerAngleThresholdDeg must be in [10, 170], got $cornerAngleThresholdDeg"
        }
        require(cornerWindowSize >= 1) { "cornerWindowSize must be >= 1, got $cornerWindowSize" }
        require(separateOutlineWidth >= 0f) {
            "separateOutlineWidth must be non-negative, got $separateOutlineWidth"
        }
        require(maxNewtonRaphsonIterations >= 0) {
            "maxNewtonRaphsonIterations must be non-negative, got $maxNewtonRaphsonIterations"
        }
    }
}
