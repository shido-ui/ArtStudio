package com.artstudio.cv.regionprofile

import com.artstudio.cv.CvPipelineParameters
import com.artstudio.cv.DenoiseMode
import com.artstudio.cv.EdgeMode
import com.artstudio.cv.ThresholdMode
import com.artstudio.cv.centreline.CentrelineSettings
import com.artstudio.cv.pathfit.PathFitSettings

/**
 * Standard factory presets for region analysis effort.
 */
enum class RegionProfilePreset(val displayName: String, val description: String) {
    STANDARD("Standard", "Balanced baseline profile for general illustration and line art"),
    HIGH_DETAIL("High Detail", "Fine feature sensitivity, tight curve fitting, and multi-scale refinement"),
    FINE_LINES("Fine Lines", "High edge sensitivity for delicate hairlines, eyelashes, and thin contours"),
    BROAD_STROKES("Broad Strokes", "Smooth outlines, high simplification, and aggressive noise suppression"),
    CUSTOM("Custom", "User-specified custom parameter configuration")
}

/**
 * Concrete analysis parameters for a region or global baseline.
 *
 * All parameters are fully exposed and can be inspected or overridden by the user.
 */
data class RegionAnalysisProfile(
    val preset: RegionProfilePreset = RegionProfilePreset.STANDARD,
    val minFeatureSize: Float = 4.0f,
    val edgeSensitivity: Float = 0.5f,
    val curveFittingTolerance: Float = 2.0f,
    val simplificationTolerance: Float = 1.5f,
    val gapClosingRadius: Int = 2,
    val detailPreservation: Float = 0.5f,
    val reviewSensitivity: Float = 0.5f,
    val boundaryMarginPx: Int = 4,
    val additionalPasses: Int = 0
) {
    init {
        require(minFeatureSize > 0.0f) { "minFeatureSize must be positive, got $minFeatureSize" }
        require(edgeSensitivity in 0.0f..1.0f) { "edgeSensitivity must be in [0, 1], got $edgeSensitivity" }
        require(curveFittingTolerance > 0.0f) { "curveFittingTolerance must be positive, got $curveFittingTolerance" }
        require(simplificationTolerance >= 0.0f) { "simplificationTolerance must be non-negative, got $simplificationTolerance" }
        require(gapClosingRadius >= 0) { "gapClosingRadius must be non-negative, got $gapClosingRadius" }
        require(detailPreservation in 0.0f..1.0f) { "detailPreservation must be in [0, 1], got $detailPreservation" }
        require(reviewSensitivity in 0.0f..1.0f) { "reviewSensitivity must be in [0, 1], got $reviewSensitivity" }
        require(boundaryMarginPx >= 0) { "boundaryMarginPx must be non-negative, got $boundaryMarginPx" }
        require(additionalPasses >= 0) { "additionalPasses must be non-negative, got $additionalPasses" }
    }

    /**
     * Converts this profile into [CvPipelineParameters] for deterministic CV execution.
     */
    fun toCvParameters(
        extractCentrelines: Boolean = false,
        vectorizeContours: Boolean = false
    ): CvPipelineParameters {
        // Map edgeSensitivity [0..1] to Canny low/high thresholds (higher sensitivity = lower thresholds)
        val cannyLow = (40.0f * (1.2f - edgeSensitivity)).coerceIn(5.0f, 60.0f)
        val cannyHigh = (100.0f * (1.2f - edgeSensitivity)).coerceIn(15.0f, 150.0f)
        val sobelThresh = (50.0f * (1.2f - edgeSensitivity)).coerceIn(10.0f, 80.0f)

        // Map detailPreservation [0..1] to corner angle threshold (higher detail = smaller angle preserved)
        val cornerAngle = (75.0f - detailPreservation * 45.0f).coerceIn(20.0f, 80.0f)

        return CvPipelineParameters(
            denoiseMode = if (edgeSensitivity > 0.8f) DenoiseMode.GAUSSIAN_BLUR else DenoiseMode.MEDIAN_DESPECKLE,
            denoiseRadius = if (edgeSensitivity > 0.8f) 1 else 1,
            gaussianSigma = if (edgeSensitivity > 0.8f) 0.6f else 1.0f,
            thresholdMode = ThresholdMode.OTSU_GLOBAL,
            edgeMode = EdgeMode.CANNY,
            sobelThreshold = sobelThresh,
            cannyLowThreshold = cannyLow,
            cannyHighThreshold = cannyHigh,
            contourMinArea = minFeatureSize,
            contourRdpEpsilon = simplificationTolerance,
            extractCentrelines = extractCentrelines,
            centrelineSettings = CentrelineSettings(
                minFeatureLength = maxOf(1, minFeatureSize.toInt()),
                gapDetectionDistance = (gapClosingRadius * 2).toFloat(),
                rdpEpsilon = simplificationTolerance
            ),
            vectorizeContours = vectorizeContours,
            pathFitSettings = PathFitSettings(
                maxFittingError = curveFittingTolerance,
                cornerAngleThresholdDeg = cornerAngle,
                preserveCorners = detailPreservation > 0.3f,
                minFeatureArea = minFeatureSize
            )
        )
    }

    fun toMap(): Map<String, String> {
        return mapOf(
            "preset" to preset.name,
            "minFeatureSize" to minFeatureSize.toString(),
            "edgeSensitivity" to edgeSensitivity.toString(),
            "curveFittingTolerance" to curveFittingTolerance.toString(),
            "simplificationTolerance" to simplificationTolerance.toString(),
            "gapClosingRadius" to gapClosingRadius.toString(),
            "detailPreservation" to detailPreservation.toString(),
            "reviewSensitivity" to reviewSensitivity.toString(),
            "boundaryMarginPx" to boundaryMarginPx.toString(),
            "additionalPasses" to additionalPasses.toString()
        )
    }

    companion object {
        fun standard(): RegionAnalysisProfile = RegionAnalysisProfile(
            preset = RegionProfilePreset.STANDARD,
            minFeatureSize = 4.0f,
            edgeSensitivity = 0.5f,
            curveFittingTolerance = 2.0f,
            simplificationTolerance = 1.5f,
            gapClosingRadius = 2,
            detailPreservation = 0.5f,
            reviewSensitivity = 0.5f,
            boundaryMarginPx = 4,
            additionalPasses = 0
        )

        fun highDetail(): RegionAnalysisProfile = RegionAnalysisProfile(
            preset = RegionProfilePreset.HIGH_DETAIL,
            minFeatureSize = 1.5f,
            edgeSensitivity = 0.85f,
            curveFittingTolerance = 0.75f,
            simplificationTolerance = 0.5f,
            gapClosingRadius = 4,
            detailPreservation = 0.9f,
            reviewSensitivity = 0.8f,
            boundaryMarginPx = 6,
            additionalPasses = 2
        )

        fun fineLines(): RegionAnalysisProfile = RegionAnalysisProfile(
            preset = RegionProfilePreset.FINE_LINES,
            minFeatureSize = 1.0f,
            edgeSensitivity = 0.95f,
            curveFittingTolerance = 0.5f,
            simplificationTolerance = 0.3f,
            gapClosingRadius = 3,
            detailPreservation = 0.95f,
            reviewSensitivity = 0.9f,
            boundaryMarginPx = 4,
            additionalPasses = 2
        )

        fun broadStrokes(): RegionAnalysisProfile = RegionAnalysisProfile(
            preset = RegionProfilePreset.BROAD_STROKES,
            minFeatureSize = 12.0f,
            edgeSensitivity = 0.3f,
            curveFittingTolerance = 4.0f,
            simplificationTolerance = 3.0f,
            gapClosingRadius = 1,
            detailPreservation = 0.2f,
            reviewSensitivity = 0.2f,
            boundaryMarginPx = 4,
            additionalPasses = 0
        )

        fun fromPreset(preset: RegionProfilePreset): RegionAnalysisProfile {
            return when (preset) {
                RegionProfilePreset.STANDARD -> standard()
                RegionProfilePreset.HIGH_DETAIL -> highDetail()
                RegionProfilePreset.FINE_LINES -> fineLines()
                RegionProfilePreset.BROAD_STROKES -> broadStrokes()
                RegionProfilePreset.CUSTOM -> standard().copy(preset = RegionProfilePreset.CUSTOM)
            }
        }

        fun fromMap(map: Map<String, String>): RegionAnalysisProfile {
            val presetName = map["preset"] ?: RegionProfilePreset.STANDARD.name
            val preset = runCatching { RegionProfilePreset.valueOf(presetName) }.getOrDefault(RegionProfilePreset.STANDARD)
            val base = fromPreset(preset)

            return base.copy(
                preset = preset,
                minFeatureSize = map["minFeatureSize"]?.toFloatOrNull() ?: base.minFeatureSize,
                edgeSensitivity = map["edgeSensitivity"]?.toFloatOrNull() ?: base.edgeSensitivity,
                curveFittingTolerance = map["curveFittingTolerance"]?.toFloatOrNull() ?: base.curveFittingTolerance,
                simplificationTolerance = map["simplificationTolerance"]?.toFloatOrNull() ?: base.simplificationTolerance,
                gapClosingRadius = map["gapClosingRadius"]?.toIntOrNull() ?: base.gapClosingRadius,
                detailPreservation = map["detailPreservation"]?.toFloatOrNull() ?: base.detailPreservation,
                reviewSensitivity = map["reviewSensitivity"]?.toFloatOrNull() ?: base.reviewSensitivity,
                boundaryMarginPx = map["boundaryMarginPx"]?.toIntOrNull() ?: base.boundaryMarginPx,
                additionalPasses = map["additionalPasses"]?.toIntOrNull() ?: base.additionalPasses
            )
        }
    }
}

/**
 * Nullable explicit overrides for a region's profile.
 *
 * Any property set to non-null overrides the corresponding property from the inherited preset.
 * Any property left null inherits from the base preset or global default.
 */
data class RegionProfileOverride(
    val minFeatureSize: Float? = null,
    val edgeSensitivity: Float? = null,
    val curveFittingTolerance: Float? = null,
    val simplificationTolerance: Float? = null,
    val gapClosingRadius: Int? = null,
    val detailPreservation: Float? = null,
    val reviewSensitivity: Float? = null,
    val boundaryMarginPx: Int? = null,
    val additionalPasses: Int? = null
) {
    init {
        minFeatureSize?.let { require(it > 0.0f) { "minFeatureSize must be positive, got $it" } }
        edgeSensitivity?.let { require(it in 0.0f..1.0f) { "edgeSensitivity must be in [0, 1], got $it" } }
        curveFittingTolerance?.let { require(it > 0.0f) { "curveFittingTolerance must be positive, got $it" } }
        simplificationTolerance?.let { require(it >= 0.0f) { "simplificationTolerance must be non-negative, got $it" } }
        gapClosingRadius?.let { require(it >= 0) { "gapClosingRadius must be non-negative, got $it" } }
        detailPreservation?.let { require(it in 0.0f..1.0f) { "detailPreservation must be in [0, 1], got $it" } }
        reviewSensitivity?.let { require(it in 0.0f..1.0f) { "reviewSensitivity must be in [0, 1], got $it" } }
        boundaryMarginPx?.let { require(it >= 0) { "boundaryMarginPx must be non-negative, got $it" } }
        additionalPasses?.let { require(it >= 0) { "additionalPasses must be non-negative, got $it" } }
    }

    fun hasOverrides(): Boolean {
        return minFeatureSize != null ||
                edgeSensitivity != null ||
                curveFittingTolerance != null ||
                simplificationTolerance != null ||
                gapClosingRadius != null ||
                detailPreservation != null ||
                reviewSensitivity != null ||
                boundaryMarginPx != null ||
                additionalPasses != null
    }

    fun overriddenPropertyNames(): Set<String> {
        val names = mutableSetOf<String>()
        if (minFeatureSize != null) names.add("minFeatureSize")
        if (edgeSensitivity != null) names.add("edgeSensitivity")
        if (curveFittingTolerance != null) names.add("curveFittingTolerance")
        if (simplificationTolerance != null) names.add("simplificationTolerance")
        if (gapClosingRadius != null) names.add("gapClosingRadius")
        if (detailPreservation != null) names.add("detailPreservation")
        if (reviewSensitivity != null) names.add("reviewSensitivity")
        if (boundaryMarginPx != null) names.add("boundaryMarginPx")
        if (additionalPasses != null) names.add("additionalPasses")
        return names
    }

    fun clear(): RegionProfileOverride = RegionProfileOverride()

    fun toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        minFeatureSize?.let { map["minFeatureSize"] = it.toString() }
        edgeSensitivity?.let { map["edgeSensitivity"] = it.toString() }
        curveFittingTolerance?.let { map["curveFittingTolerance"] = it.toString() }
        simplificationTolerance?.let { map["simplificationTolerance"] = it.toString() }
        gapClosingRadius?.let { map["gapClosingRadius"] = it.toString() }
        detailPreservation?.let { map["detailPreservation"] = it.toString() }
        reviewSensitivity?.let { map["reviewSensitivity"] = it.toString() }
        boundaryMarginPx?.let { map["boundaryMarginPx"] = it.toString() }
        additionalPasses?.let { map["additionalPasses"] = it.toString() }
        return map
    }

    companion object {
        fun fromMap(map: Map<String, String>): RegionProfileOverride {
            return RegionProfileOverride(
                minFeatureSize = map["minFeatureSize"]?.toFloatOrNull(),
                edgeSensitivity = map["edgeSensitivity"]?.toFloatOrNull(),
                curveFittingTolerance = map["curveFittingTolerance"]?.toFloatOrNull(),
                simplificationTolerance = map["simplificationTolerance"]?.toFloatOrNull(),
                gapClosingRadius = map["gapClosingRadius"]?.toIntOrNull(),
                detailPreservation = map["detailPreservation"]?.toFloatOrNull(),
                reviewSensitivity = map["reviewSensitivity"]?.toFloatOrNull(),
                boundaryMarginPx = map["boundaryMarginPx"]?.toIntOrNull(),
                additionalPasses = map["additionalPasses"]?.toIntOrNull()
            )
        }
    }
}

/**
 * Resolved region analysis profile combining inherited preset values with explicit overrides.
 */
data class ResolvedRegionProfile(
    val basePreset: RegionProfilePreset,
    val minFeatureSize: Float,
    val edgeSensitivity: Float,
    val curveFittingTolerance: Float,
    val simplificationTolerance: Float,
    val gapClosingRadius: Int,
    val detailPreservation: Float,
    val reviewSensitivity: Float,
    val boundaryMarginPx: Int,
    val additionalPasses: Int,
    val overriddenProperties: Set<String>,
    val isInheritedFromGlobal: Boolean = false
) {
    fun isPropertyOverridden(propertyName: String): Boolean = propertyName in overriddenProperties

    fun toProfile(): RegionAnalysisProfile = RegionAnalysisProfile(
        preset = if (overriddenProperties.isNotEmpty()) RegionProfilePreset.CUSTOM else basePreset,
        minFeatureSize = minFeatureSize,
        edgeSensitivity = edgeSensitivity,
        curveFittingTolerance = curveFittingTolerance,
        simplificationTolerance = simplificationTolerance,
        gapClosingRadius = gapClosingRadius,
        detailPreservation = detailPreservation,
        reviewSensitivity = reviewSensitivity,
        boundaryMarginPx = boundaryMarginPx,
        additionalPasses = additionalPasses
    )

    fun toCvParameters(
        extractCentrelines: Boolean = false,
        vectorizeContours: Boolean = false
    ): CvPipelineParameters = toProfile().toCvParameters(extractCentrelines, vectorizeContours)
}

/**
 * Engine for resolving profile inheritance from global defaults and explicit region overrides.
 */
object RegionProfileResolver {

    /**
     * Resolves the effective profile for a region by applying overrides over the region's preset
     * (or the global default profile if no preset is specified).
     *
     * @param globalDefault Global baseline profile used when region specifies no preset or inherits.
     * @param regionPreset Optional preset assigned to the region.
     * @param override Optional explicit property overrides for the region.
     */
    fun resolve(
        globalDefault: RegionAnalysisProfile = RegionAnalysisProfile.standard(),
        regionPreset: RegionProfilePreset? = null,
        override: RegionProfileOverride? = null
    ): ResolvedRegionProfile {
        val isInherited = (regionPreset == null || regionPreset == globalDefault.preset)
        val baseProfile = if (regionPreset != null && regionPreset != RegionProfilePreset.CUSTOM) {
            RegionAnalysisProfile.fromPreset(regionPreset)
        } else {
            globalDefault
        }

        val basePreset = regionPreset ?: globalDefault.preset
        val overridden = mutableSetOf<String>()

        val finalMinFeatureSize = if (override?.minFeatureSize != null) {
            overridden.add("minFeatureSize")
            override.minFeatureSize
        } else baseProfile.minFeatureSize

        val finalEdgeSensitivity = if (override?.edgeSensitivity != null) {
            overridden.add("edgeSensitivity")
            override.edgeSensitivity
        } else baseProfile.edgeSensitivity

        val finalCurveFittingTolerance = if (override?.curveFittingTolerance != null) {
            overridden.add("curveFittingTolerance")
            override.curveFittingTolerance
        } else baseProfile.curveFittingTolerance

        val finalSimplificationTolerance = if (override?.simplificationTolerance != null) {
            overridden.add("simplificationTolerance")
            override.simplificationTolerance
        } else baseProfile.simplificationTolerance

        val finalGapClosingRadius = if (override?.gapClosingRadius != null) {
            overridden.add("gapClosingRadius")
            override.gapClosingRadius
        } else baseProfile.gapClosingRadius

        val finalDetailPreservation = if (override?.detailPreservation != null) {
            overridden.add("detailPreservation")
            override.detailPreservation
        } else baseProfile.detailPreservation

        val finalReviewSensitivity = if (override?.reviewSensitivity != null) {
            overridden.add("reviewSensitivity")
            override.reviewSensitivity
        } else baseProfile.reviewSensitivity

        val finalBoundaryMarginPx = if (override?.boundaryMarginPx != null) {
            overridden.add("boundaryMarginPx")
            override.boundaryMarginPx
        } else baseProfile.boundaryMarginPx

        val finalAdditionalPasses = if (override?.additionalPasses != null) {
            overridden.add("additionalPasses")
            override.additionalPasses
        } else baseProfile.additionalPasses

        return ResolvedRegionProfile(
            basePreset = basePreset,
            minFeatureSize = finalMinFeatureSize,
            edgeSensitivity = finalEdgeSensitivity,
            curveFittingTolerance = finalCurveFittingTolerance,
            simplificationTolerance = finalSimplificationTolerance,
            gapClosingRadius = finalGapClosingRadius,
            detailPreservation = finalDetailPreservation,
            reviewSensitivity = finalReviewSensitivity,
            boundaryMarginPx = finalBoundaryMarginPx,
            additionalPasses = finalAdditionalPasses,
            overriddenProperties = overridden,
            isInheritedFromGlobal = isInherited && overridden.isEmpty()
        )
    }
}

/**
 * Compute performance and feature metrics captured during per-region analysis passes.
 */
data class RegionAnalysisMetrics(
    val regionId: String,
    val regionName: String,
    val pixelCount: Int,
    val boundingArea: Int,
    val elapsedTimeMs: Long,
    val peakMemoryBytes: Long,
    val passesExecuted: Int,
    val featuresDetected: Int,
    val boundaryMarginUsed: Int,
    val appliedProfile: ResolvedRegionProfile
)

/**
 * Composite result of region-aware image analysis.
 */
data class RegionAwareAnalysisResult(
    val width: Int,
    val height: Int,
    val globalResult: com.artstudio.cv.CvPipelineResult,
    val mergedEdges: IntArray,
    val regionMetrics: List<RegionAnalysisMetrics>,
    val totalElapsedTimeMs: Long,
    val totalPeakMemoryBytes: Long,
    val crossBoundaryNotes: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegionAwareAnalysisResult) return false
        return width == other.width &&
                height == other.height &&
                mergedEdges.contentEquals(other.mergedEdges) &&
                regionMetrics == other.regionMetrics
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + mergedEdges.contentHashCode()
        result = 31 * result + regionMetrics.hashCode()
        return result
    }
}
