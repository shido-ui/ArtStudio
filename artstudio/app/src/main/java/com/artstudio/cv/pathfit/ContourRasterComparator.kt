package com.artstudio.cv.pathfit

import com.artstudio.cv.CvPoint
import com.artstudio.cv.CvRect
import com.artstudio.vector.*
import kotlin.math.*

/**
 * High-precision evaluation tool that compares fitted vector paths against raster contour masks,
 * calculating IoU, Dice coefficients, Hausdorff maximum contour deviation, and mean error metrics.
 */
object ContourRasterComparator {

    /**
     * Quantitative spatial overlap metrics comparing a rasterized vector path against a ground-truth mask.
     */
    data class MaskComparisonMetrics(
        val intersectionCount: Int,
        val unionCount: Int,
        val groundTruthCount: Int,
        val rasterizedCount: Int,
        val iou: Double,
        val dice: Double,
        val falsePositives: Int,
        val falseNegatives: Int
    )

    /**
     * Geometric error metrics between discrete contour points and the continuous fitted vector path.
     */
    data class ContourDeviationMetrics(
        val maxDeviation: Float,
        val meanDeviation: Float,
        val rmsDeviation: Float,
        val sampleCount: Int
    )

    /**
     * Rasterizes a [VectorObject] into a binary 8-bit mask (255 for foreground, 0 for background).
     *
     * Uses deterministic scanline rasterization for filled closed paths and Bresenham stroke rasterization
     * for open or stroked outlines. Operates in 100% pure Kotlin for reliable headless JVM execution.
     */
    fun rasterizeVectorObject(
        obj: VectorObject,
        width: Int,
        height: Int,
        fillPath: Boolean = true
    ): IntArray {
        val mask = IntArray(width * height)
        val polyline = sampleVectorObjectPoints(obj, samplesPerSegment = 20)
        if (polyline.size < 2) return mask

        if (fillPath && (obj.isClosed || obj.shapeType == VectorShapeType.POLYGON)) {
            rasterizePolygonScanline(polyline, mask, width, height)
        } else {
            rasterizePolylineStroked(polyline, mask, width, height, strokeWidth = obj.style.strokeWidth)
        }

        return mask
    }

    /**
     * Samples continuous cubic/quad segments and anchor lines of a [VectorObject] into discrete points.
     */
    fun sampleVectorObjectPoints(obj: VectorObject, samplesPerSegment: Int = 20): List<CvPoint> {
        val points = mutableListOf<CvPoint>()
        val segments = obj.toSegments()

        var currX = 0f
        var currY = 0f

        for (seg in segments) {
            when (seg) {
                is VectorSegment.MoveTo -> {
                    currX = seg.x
                    currY = seg.y
                    points.add(CvPoint(currX, currY))
                }
                is VectorSegment.LineTo -> {
                    val steps = samplesPerSegment.coerceAtLeast(2)
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val x = currX + t * (seg.x - currX)
                        val y = currY + t * (seg.y - currY)
                        points.add(CvPoint(x, y))
                    }
                    currX = seg.x
                    currY = seg.y
                }
                is VectorSegment.QuadTo -> {
                    val steps = samplesPerSegment.coerceAtLeast(4)
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val u = 1f - t
                        val x = u * u * currX + 2f * u * t * seg.x1 + t * t * seg.x
                        val y = u * u * currY + 2f * u * t * seg.y1 + t * t * seg.y
                        points.add(CvPoint(x, y))
                    }
                    currX = seg.x
                    currY = seg.y
                }
                is VectorSegment.CubicTo -> {
                    val steps = samplesPerSegment.coerceAtLeast(8)
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val u = 1f - t
                        val tt = t * t
                        val uu = u * u
                        val uuu = uu * u
                        val ttt = tt * t

                        val x = uuu * currX + 3f * uu * t * seg.x1 + 3f * u * tt * seg.x2 + ttt * seg.x
                        val y = uuu * currY + 3f * uu * t * seg.y1 + 3f * u * tt * seg.y2 + ttt * seg.y
                        points.add(CvPoint(x, y))
                    }
                    currX = seg.x
                    currY = seg.y
                }
                is VectorSegment.Close -> {
                    if (points.isNotEmpty()) {
                        points.add(points.first())
                    }
                }
            }
        }

        return points
    }

    private fun rasterizePolygonScanline(
        polygon: List<CvPoint>,
        mask: IntArray,
        width: Int,
        height: Int
    ) {
        val n = polygon.size
        if (n < 3) return

        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (p in polygon) {
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        val yStart = max(0, floor(minY).toInt())
        val yEnd = min(height - 1, ceil(maxY).toInt())

        val xIntersections = mutableListOf<Float>()

        for (y in yStart..yEnd) {
            xIntersections.clear()
            val yScan = y + 0.5f

            for (i in 0 until n) {
                val p1 = polygon[i]
                val p2 = polygon[(i + 1) % n]

                if ((p1.y <= yScan && p2.y > yScan) || (p2.y <= yScan && p1.y > yScan)) {
                    val dy = p2.y - p1.y
                    if (abs(dy) > 1e-5f) {
                        val x = p1.x + (yScan - p1.y) * (p2.x - p1.x) / dy
                        xIntersections.add(x)
                    }
                }
            }

            xIntersections.sort()

            var j = 0
            while (j + 1 < xIntersections.size) {
                val x1 = max(0, floor(xIntersections[j]).toInt())
                val x2 = min(width - 1, ceil(xIntersections[j + 1]).toInt())

                val rowOffset = y * width
                for (x in x1..x2) {
                    mask[rowOffset + x] = 255
                }
                j += 2
            }
        }
    }

    private fun rasterizePolylineStroked(
        polyline: List<CvPoint>,
        mask: IntArray,
        width: Int,
        height: Int,
        strokeWidth: Float
    ) {
        val radius = max(1, (strokeWidth / 2f).roundToInt())
        val rSq = radius * radius

        for (i in 0 until polyline.size - 1) {
            val p0 = polyline[i]
            val p1 = polyline[i + 1]

            val dist = hypot(p1.x - p0.x, p1.y - p0.y)
            val steps = max(1, ceil(dist).toInt())

            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val cx = (p0.x + t * (p1.x - p0.x)).roundToInt()
                val cy = (p0.y + t * (p1.y - p0.y)).roundToInt()

                for (dy in -radius..radius) {
                    val py = cy + dy
                    if (py !in 0 until height) continue
                    val rowOffset = py * width
                    for (dx in -radius..radius) {
                        val px = cx + dx
                        if (px !in 0 until width) continue
                        if (dx * dx + dy * dy <= rSq) {
                            mask[rowOffset + px] = 255
                        }
                    }
                }
            }
        }
    }

    /**
     * Compares two binary masks (255 foreground, 0 background) and computes spatial overlap metrics.
     */
    fun compareMasks(
        rasterizedMask: IntArray,
        groundTruthMask: IntArray,
        width: Int,
        height: Int
    ): MaskComparisonMetrics {
        require(rasterizedMask.size == width * height && groundTruthMask.size == width * height) {
            "Mask dimensions must match width * height ($width x $height)"
        }

        var intersection = 0
        var gtCount = 0
        var rastCount = 0
        var fp = 0
        var fn = 0

        for (i in rasterizedMask.indices) {
            val r = rasterizedMask[i] == 255
            val g = groundTruthMask[i] == 255

            if (r) rastCount++
            if (g) gtCount++

            if (r && g) {
                intersection++
            } else if (r && !g) {
                fp++
            } else if (!r && g) {
                fn++
            }
        }

        val union = rastCount + gtCount - intersection
        val iou = if (union > 0) intersection.toDouble() / union else 1.0
        val dice = if (rastCount + gtCount > 0) 2.0 * intersection / (rastCount + gtCount) else 1.0

        return MaskComparisonMetrics(
            intersectionCount = intersection,
            unionCount = union,
            groundTruthCount = gtCount,
            rasterizedCount = rastCount,
            iou = iou,
            dice = dice,
            falsePositives = fp,
            falseNegatives = fn
        )
    }

    /**
     * Computes the maximum (Hausdorff) and mean orthogonal Euclidean deviation between
     * discrete contour points and the continuous fitted vector path.
     */
    fun computeContourDeviation(
        candidate: FittedPathCandidate,
        contourPoints: List<CvPoint>
    ): ContourDeviationMetrics {
        if (contourPoints.isEmpty()) {
            return ContourDeviationMetrics(0f, 0f, 0f, 0)
        }

        val sampledPath = sampleVectorObjectPoints(candidate.toVectorObject(), samplesPerSegment = 25)
        if (sampledPath.isEmpty()) {
            return ContourDeviationMetrics(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, contourPoints.size)
        }

        var maxDev = 0f
        var sumDev = 0.0
        var sumSqDev = 0.0

        for (cp in contourPoints) {
            var minPtDist = Float.MAX_VALUE
            for (i in 0 until sampledPath.size - 1) {
                val p0 = sampledPath[i]
                val p1 = sampledPath[i + 1]
                val d = distanceToSegment(cp.x, cp.y, p0.x, p0.y, p1.x, p1.y)
                if (d < minPtDist) {
                    minPtDist = d
                }
            }

            val dist = minPtDist
            if (dist > maxDev) maxDev = dist
            sumDev += dist
            sumSqDev += dist * dist
        }

        val n = contourPoints.size
        val meanDev = (sumDev / n).toFloat()
        val rmsDev = sqrt(sumSqDev / n).toFloat()

        return ContourDeviationMetrics(
            maxDeviation = maxDev,
            meanDeviation = meanDev,
            rmsDeviation = rmsDev,
            sampleCount = n
        )
    }

    /**
     * Computes perpendicular distance from point (px, py) to segment (x0, y0)-(x1, y1).
     */
    fun distanceToSegment(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-6f) {
            return hypot(px - x0, py - y0)
        }
        val t = (((px - x0) * dx + (py - y0) * dy) / l2).coerceIn(0f, 1f)
        val projX = x0 + t * dx
        val projY = y0 + t * dy
        return hypot(px - projX, py - projY)
    }
}
