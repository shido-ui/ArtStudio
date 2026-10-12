package com.artstudio.cv.pathfit

import com.artstudio.cv.CvPoint
import com.artstudio.cv.CvRect
import com.artstudio.vector.*
import java.util.UUID

/**
 * Audit trail and parameter metadata recording how a vector path was generated from raster contours.
 */
data class PathProvenance(
    val algorithm: String = "SchneiderAdaptiveBezierFit",
    val timestampMs: Long = System.currentTimeMillis(),
    val sourceContourId: Int = 0,
    val contourPerimeter: Double = 0.0,
    val contourArea: Double = 0.0,
    val isHole: Boolean = false,
    val version: String = "1.0.0"
)

/**
 * Spatial and pixel mapping connecting a fitted vector path back to its source raster boundary.
 */
data class SourcePixelMapping(
    val pixelCount: Int,
    val boundingBox: CvRect,
    val sampledPixels: List<CvPoint>
)

/**
 * An editable geometric path candidate fitted from a discrete contour, containing anchors,
 * control handles, source pixel mapping, corner information, and quality metrics.
 */
data class FittedPathCandidate(
    val id: String = UUID.randomUUID().toString(),
    val regionId: String,
    val parentRegionId: String? = null,
    val isHole: Boolean = false,
    val isClosed: Boolean = true,
    val anchors: List<VectorAnchor>,
    val segments: List<VectorSegment> = emptyList(),
    val sourceMapping: SourcePixelMapping,
    val provenance: PathProvenance,
    val confidence: Float,
    val maxContourDeviation: Float,
    val meanContourDeviation: Float,
    val cornersDetected: List<CvPoint> = emptyList()
) {
    /**
     * Converts this candidate into an authoritative [VectorObject] that can be added to
     * a [com.artstudio.document.model.LayerNode.VectorPathLayer], saved to .ffart, and edited.
     *
     * Path width and outline attributes are defined separately in [VectorStyle],
     * completely decoupled from the underlying curve geometry.
     */
    fun toVectorObject(
        strokeColor: Long? = 0xFF000000L,
        strokeWidth: Float = 2.0f,
        fillColor: Long? = null,
        fillRule: FillRule = if (isHole) FillRule.EVEN_ODD else FillRule.NON_ZERO,
        name: String = if (isHole) "Contour Hole $regionId" else "Contour Path $regionId"
    ): VectorObject {
        val meta = mutableMapOf(
            "pathId" to id,
            "regionId" to regionId,
            "parentRegionId" to (parentRegionId ?: ""),
            "confidence" to "%.4f".format(confidence),
            "isHole" to isHole.toString(),
            "isClosed" to isClosed.toString(),
            "maxDeviation" to "%.4f".format(maxContourDeviation),
            "meanDeviation" to "%.4f".format(meanContourDeviation),
            "sourcePixelCount" to sourceMapping.pixelCount.toString(),
            "sourceBounds" to "${sourceMapping.boundingBox.minX},${sourceMapping.boundingBox.minY},${sourceMapping.boundingBox.maxX},${sourceMapping.boundingBox.maxY}",
            "provenanceAlgorithm" to provenance.algorithm,
            "provenanceTimestamp" to provenance.timestampMs.toString(),
            "cornerCount" to cornersDetected.size.toString()
        )

        val style = VectorStyle(
            strokeColor = strokeColor,
            strokeWidth = strokeWidth.coerceAtLeast(0f),
            fillColor = fillColor,
            fillRule = fillRule,
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.ROUND
        )

        return VectorObject(
            id = id,
            name = name,
            shapeType = VectorShapeType.BEZIER_PATH,
            anchors = anchors,
            segments = segments,
            isClosed = isClosed,
            style = style,
            metadata = meta
        )
    }
}

/**
 * Comprehensive result of contour vectorization across an image or region.
 */
data class ContourVectorizationResult(
    val paths: List<FittedPathCandidate>,
    val totalRegions: Int,
    val totalHoles: Int,
    val disconnectedComponents: Int,
    val maxGlobalDeviation: Float,
    val meanGlobalDeviation: Float,
    val elapsedTimeMs: Long,
    val peakMemoryBytes: Long
) {
    /**
     * Converts all paths into independent or compound [VectorObject] entities.
     * Holes are assigned [FillRule.EVEN_ODD] styling so they subtract cleanly when filled.
     */
    fun toVectorObjects(
        strokeColor: Long? = 0xFF000000L,
        strokeWidth: Float = 2.0f,
        fillColor: Long? = null
    ): List<VectorObject> {
        return paths.map { path ->
            path.toVectorObject(
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                fillColor = fillColor
            )
        }
    }
}
