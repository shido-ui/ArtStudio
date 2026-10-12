package com.artstudio.cv.centreline

import com.artstudio.cv.CvPoint
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Generator for ground-truth synthetic line art fixtures and error evaluation metrics.
 *
 * Implements:
 * 1. Analytical rasterization of known straight lines, curved arcs, branched Y-forks, broken gaps, and noisy strokes.
 * 2. Quantitative geometry and endpoint error calculation against ground-truth source paths.
 */
object SyntheticPathGenerator {

    /**
     * Renders a clean straight stroke with known start and end coordinates and stroke width.
     * Returns ground-truth polyline points and an ARGB pixel buffer (dark stroke on white canvas).
     */
    fun createStraightLine(
        width: Int,
        height: Int,
        start: CvPoint,
        end: CvPoint,
        strokeWidth: Float = 3.0f
    ): Pair<List<CvPoint>, IntArray> {
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() } // White background
        val gtPoints = listOf(start, end)
        drawThickLine(pixels, width, height, start.x, start.y, end.x, end.y, strokeWidth)
        return Pair(gtPoints, pixels)
    }

    /**
     * Renders a curved quadratic Bezier arc with known control points.
     */
    fun createCurvedArc(
        width: Int,
        height: Int,
        p0: CvPoint,
        p1: CvPoint,
        p2: CvPoint,
        strokeWidth: Float = 3.0f,
        steps: Int = 50
    ): Pair<List<CvPoint>, IntArray> {
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        val gtPoints = mutableListOf<CvPoint>()

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val u = 1f - t
            val x = u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x
            val y = u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y
            gtPoints.add(CvPoint(x, y))
        }

        for (i in 0 until gtPoints.size - 1) {
            val ptA = gtPoints[i]
            val ptB = gtPoints[i + 1]
            drawThickLine(pixels, width, height, ptA.x, ptA.y, ptB.x, ptB.y, strokeWidth)
        }

        return Pair(gtPoints, pixels)
    }

    /**
     * Renders a branched Y-junction with a main stem and two diverging branches.
     */
    fun createBranchedY(
        width: Int,
        height: Int,
        stemStart: CvPoint,
        forkPoint: CvPoint,
        branch1End: CvPoint,
        branch2End: CvPoint,
        strokeWidth: Float = 3.0f
    ): Pair<List<List<CvPoint>>, IntArray> {
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        val stem = listOf(stemStart, forkPoint)
        val b1 = listOf(forkPoint, branch1End)
        val b2 = listOf(forkPoint, branch2End)

        drawThickLine(pixels, width, height, stemStart.x, stemStart.y, forkPoint.x, forkPoint.y, strokeWidth)
        drawThickLine(pixels, width, height, forkPoint.x, forkPoint.y, branch1End.x, branch1End.y, strokeWidth)
        drawThickLine(pixels, width, height, forkPoint.x, forkPoint.y, branch2End.x, branch2End.y, strokeWidth)

        return Pair(listOf(stem, b1, b2), pixels)
    }

    /**
     * Renders a straight stroke with an intentional broken gap in the middle.
     */
    fun createBrokenStroke(
        width: Int,
        height: Int,
        start: CvPoint,
        breakStart: CvPoint,
        breakEnd: CvPoint,
        end: CvPoint,
        strokeWidth: Float = 3.0f
    ): Pair<Pair<List<CvPoint>, List<CvPoint>>, IntArray> {
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        val seg1 = listOf(start, breakStart)
        val seg2 = listOf(breakEnd, end)

        drawThickLine(pixels, width, height, start.x, start.y, breakStart.x, breakStart.y, strokeWidth)
        drawThickLine(pixels, width, height, breakEnd.x, breakEnd.y, end.x, end.y, strokeWidth)

        return Pair(Pair(seg1, seg2), pixels)
    }

    /**
     * Renders a stroke with added speckle noise and high-frequency jitter.
     */
    fun createNoisyStroke(
        width: Int,
        height: Int,
        start: CvPoint,
        end: CvPoint,
        strokeWidth: Float = 3.0f,
        speckleCount: Int = 12
    ): Pair<List<CvPoint>, IntArray> {
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        val gtPoints = listOf(start, end)
        drawThickLine(pixels, width, height, start.x, start.y, end.x, end.y, strokeWidth)

        // Add isolated noise speckles near stroke
        val rng = java.util.Random(42)
        for (i in 0 until speckleCount) {
            val t = rng.nextFloat()
            val sx = start.x + t * (end.x - start.x) + (rng.nextFloat() - 0.5f) * 14f
            val sy = start.y + t * (end.y - start.y) + (rng.nextFloat() - 0.5f) * 14f
            val px = sx.toInt().coerceIn(0, width - 1)
            val py = sy.toInt().coerceIn(0, height - 1)
            pixels[py * width + px] = 0xFF000000.toInt()
        }

        return Pair(gtPoints, pixels)
    }

    /**
     * Compares an extracted centreline candidate against a known ground-truth polyline path.
     * Computes endpoint error, mean orthogonal geometry error, and length deviation.
     */
    fun evaluateError(
        groundTruth: List<CvPoint>,
        candidate: CentrelinePathCandidate
    ): SyntheticPathComparisonReport {
        val gtLength = computePolylineLength(groundTruth)
        val extLength = candidate.length

        val gtStart = groundTruth.first()
        val gtEnd = groundTruth.last()

        val extStart = candidate.startPoint
        val extEnd = candidate.endPoint

        // Check forward vs reverse orientation
        val forwardStartErr = distance(gtStart, extStart)
        val forwardEndErr = distance(gtEnd, extEnd)
        val forwardTotal = forwardStartErr + forwardEndErr

        val reverseStartErr = distance(gtStart, extEnd)
        val reverseEndErr = distance(gtEnd, extStart)
        val reverseTotal = reverseStartErr + reverseEndErr

        val (startErr, endErr) = if (forwardTotal <= reverseTotal) {
            Pair(forwardStartErr, forwardEndErr)
        } else {
            Pair(reverseStartErr, reverseEndErr)
        }
        val meanEndErr = (startErr + endErr) / 2f

        // Compute orthogonal geometry error for every point in candidate
        var sumGeoErr = 0f
        var maxGeoErr = 0f
        val pointsToTest = if (candidate.rawSkeletonPoints.isNotEmpty()) candidate.rawSkeletonPoints else candidate.simplifiedPoints

        for (pt in pointsToTest) {
            val distToGt = minDistanceToPolyline(pt, groundTruth)
            sumGeoErr += distToGt
            if (distToGt > maxGeoErr) maxGeoErr = distToGt
        }

        val meanGeoErr = if (pointsToTest.isNotEmpty()) sumGeoErr / pointsToTest.size else 0f
        val lenRatio = if (gtLength > 0f) abs(extLength - gtLength) / gtLength else 0f

        return SyntheticPathComparisonReport(
            groundTruthLength = gtLength,
            extractedLength = extLength,
            startEndpointError = startErr,
            endEndpointError = endErr,
            meanEndpointError = meanEndErr,
            meanGeometryError = meanGeoErr,
            maxGeometryError = maxGeoErr,
            lengthDeviationRatio = lenRatio
        )
    }

    private fun computePolylineLength(points: List<CvPoint>): Float {
        var len = 0f
        for (i in 0 until points.size - 1) {
            len += distance(points[i], points[i + 1])
        }
        return len
    }

    private fun minDistanceToPolyline(pt: CvPoint, polyline: List<CvPoint>): Float {
        var minD = Float.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val d = distanceToSegment(pt, polyline[i], polyline[i + 1])
            if (d < minD) minD = d
        }
        return minD
    }

    private fun distanceToSegment(p: CvPoint, a: CvPoint, b: CvPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        if (l2 == 0f) return distance(p, a)

        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        val px = p.x - projX
        val py = p.y - projY
        return sqrt(px * px + py * py)
    }

    private fun distance(a: CvPoint, b: CvPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun drawThickLine(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        strokeWidth: Float
    ) {
        val radius = strokeWidth / 2f
        val radCeil = radius.toInt() + 1
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        val steps = (len * 2f).toInt().coerceAtLeast(1)

        val inkColor = 0xFF000000.toInt()

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val cx = (x0 + t * dx).toInt()
            val cy = (y0 + t * dy).toInt()

            for (ry in -radCeil..radCeil) {
                for (rx in -radCeil..radCeil) {
                    if (rx * rx + ry * ry <= radius * radius) {
                        val px = cx + rx
                        val py = cy + ry
                        if (px in 0 until width && py in 0 until height) {
                            pixels[py * width + px] = inkColor
                        }
                    }
                }
            }
        }
    }
}
