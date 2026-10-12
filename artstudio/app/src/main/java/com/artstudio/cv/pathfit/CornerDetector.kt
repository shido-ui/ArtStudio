package com.artstudio.cv.pathfit

import com.artstudio.cv.CvPoint
import kotlin.math.*

/**
 * Robust corner detection for discrete 2D contours and polylines.
 *
 * Uses localized multi-scale tangent analysis to identify sharp geometric vertices
 * (such as rectangle corners, polygon tips, and cusps) while filtering out high-frequency
 * raster pixel discretization noise and smooth continuous arcs.
 */
object CornerDetector {

    /**
     * Represents a detected sharp corner candidate with its index, location, and turning angle.
     */
    data class Corner(
        val index: Int,
        val point: CvPoint,
        val angleDeg: Float
    )

    /**
     * Detects sharp corners on an ordered sequence of points.
     *
     * @param points Dense contour points.
     * @param isClosed Whether the contour wraps from the last point back to the first.
     * @param angleThresholdDeg Minimum turning angle in degrees to be classified as a corner (e.g. 55.0°).
     * @param windowRadius Radius k used for computing forward and backward tangents (e.g. 3).
     * @return Ordered list of sharp corner indices and points.
     */
    fun detectCorners(
        points: List<CvPoint>,
        isClosed: Boolean = true,
        angleThresholdDeg: Float = 55.0f,
        windowRadius: Int = 3
    ): List<Corner> {
        val n = points.size
        val k = windowRadius.coerceAtLeast(1)
        if (n < 2 * k + 1) return emptyList()

        val turningAngles = FloatArray(n)

        for (i in 0 until n) {
            val prevIdx = if (isClosed) (i - k + n) % n else (i - k).coerceAtLeast(0)
            val nextIdx = if (isClosed) (i + k) % n else (i + k).coerceAtMost(n - 1)

            if (!isClosed && (i < k || i >= n - k)) {
                turningAngles[i] = 0f
                continue
            }

            val pPrev = points[prevIdx]
            val pCurr = points[i]
            val pNext = points[nextIdx]

            // Inward vector: pPrev -> pCurr
            val uX = pCurr.x - pPrev.x
            val uY = pCurr.y - pPrev.y
            val uLen = hypot(uX, uY)

            // Outward vector: pCurr -> pNext
            val vX = pNext.x - pCurr.x
            val vY = pNext.y - pCurr.y
            val vLen = hypot(vX, vY)

            if (uLen < 1e-4f || vLen < 1e-4f) {
                turningAngles[i] = 0f
                continue
            }

            // Dot product between normalized tangents
            val dot = ((uX * vX + uY * vY) / (uLen * vLen)).coerceIn(-1.0f, 1.0f)
            val angleRad = acos(dot.toDouble())
            turningAngles[i] = Math.toDegrees(angleRad).toFloat()
        }

        // Find local maxima exceeding the angle threshold
        val candidates = mutableListOf<Corner>()
        for (i in 0 until n) {
            val angle = turningAngles[i]
            if (angle < angleThresholdDeg) continue

            // Verify local maximum within neighborhood of size k
            var isLocalMax = true
            for (step in 1..k) {
                val leftIdx = if (isClosed) (i - step + n) % n else (i - step).coerceAtLeast(0)
                val rightIdx = if (isClosed) (i + step) % n else (i + step).coerceAtMost(n - 1)

                if (turningAngles[leftIdx] > angle || turningAngles[rightIdx] > angle) {
                    isLocalMax = false
                    break
                }
            }

            if (isLocalMax) {
                candidates.add(Corner(index = i, point = points[i], angleDeg = angle))
            }
        }

        // Suppress corners that are too close to each other (closer than k points)
        return suppressClusters(candidates, points.size, isClosed, minSpacing = k)
    }

    private fun suppressClusters(
        corners: List<Corner>,
        totalPoints: Int,
        isClosed: Boolean,
        minSpacing: Int
    ): List<Corner> {
        if (corners.size <= 1) return corners

        val filtered = mutableListOf<Corner>()
        var i = 0
        while (i < corners.size) {
            var best = corners[i]
            var j = i + 1
            while (j < corners.size) {
                val dist = if (isClosed) {
                    val d = abs(corners[j].index - corners[i].index)
                    min(d, totalPoints - d)
                } else {
                    corners[j].index - corners[i].index
                }

                if (dist <= minSpacing) {
                    if (corners[j].angleDeg > best.angleDeg) {
                        best = corners[j]
                    }
                    j++
                } else {
                    break
                }
            }
            filtered.add(best)
            i = j
        }

        // Also check wrap-around distance for closed contours
        if (isClosed && filtered.size > 1) {
            val first = filtered.first()
            val last = filtered.last()
            val wrapDist = min(abs(last.index - first.index), totalPoints - abs(last.index - first.index))
            if (wrapDist <= minSpacing) {
                if (last.angleDeg > first.angleDeg) {
                    filtered.removeAt(0)
                } else {
                    filtered.removeAt(filtered.size - 1)
                }
            }
        }

        return filtered
    }
}
