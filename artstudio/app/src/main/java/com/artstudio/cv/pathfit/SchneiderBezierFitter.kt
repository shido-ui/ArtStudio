package com.artstudio.cv.pathfit

import com.artstudio.cv.CvPoint
import com.artstudio.vector.VectorAnchor
import com.artstudio.vector.VectorSegment
import kotlin.math.*

/**
 * High-performance, pure-Kotlin implementation of Philip J. Schneider's algorithm
 * ("An Algorithm for Automatically Fitting Digitized Curves", Graphics Gems I).
 *
 * Automatically fits a minimal sequence of cubic Bézier splines to discrete 2D point sequences
 * within a specified error tolerance, preserving continuity and endpoint tangents.
 */
object SchneiderBezierFitter {

    /**
     * Mathematical cubic Bézier curve segment defined by 4 control points.
     */
    data class CubicBezierSegment(
        val p0: CvPoint,
        val c1: CvPoint,
        val c2: CvPoint,
        val p3: CvPoint
    ) {
        /**
         * Evaluates the cubic Bézier curve at parameter t in [0, 1].
         */
        fun evaluate(t: Float): CvPoint {
            val u = 1f - t
            val tt = t * t
            val uu = u * u
            val uuu = uu * u
            val ttt = tt * t

            val x = uuu * p0.x + 3f * uu * t * c1.x + 3f * u * tt * c2.x + ttt * p3.x
            val y = uuu * p0.y + 3f * uu * t * c1.y + 3f * u * tt * c2.y + ttt * p3.y
            return CvPoint(x, y)
        }

        /**
         * Evaluates first derivative B'(t).
         */
        fun evaluateDerivative(t: Float): CvPoint {
            val u = 1f - t
            val tt = t * t
            val uu = u * u

            val x = 3f * uu * (c1.x - p0.x) + 6f * u * t * (c2.x - c1.x) + 3f * tt * (p3.x - c2.x)
            val y = 3f * uu * (c1.y - p0.y) + 6f * u * t * (c2.y - c1.y) + 3f * tt * (p3.y - c2.y)
            return CvPoint(x, y)
        }

        /**
         * Evaluates second derivative B''(t).
         */
        fun evaluateSecondDerivative(t: Float): CvPoint {
            val u = 1f - t
            val x = 6f * u * (c2.x - 2f * c1.x + p0.x) + 6f * t * (p3.x - 2f * c2.x + c1.x)
            val y = 6f * u * (c2.y - 2f * c1.y + p0.y) + 6f * t * (p3.y - 2f * c2.y + c1.y)
            return CvPoint(x, y)
        }

        /**
         * Converts this segment into a [VectorSegment.CubicTo].
         */
        fun toVectorSegment(): VectorSegment.CubicTo {
            return VectorSegment.CubicTo(c1.x, c1.y, c2.x, c2.y, p3.x, p3.y)
        }
    }

    /**
     * Fits one or more cubic Bézier curve segments to a digitized point sequence.
     *
     * @param points Digitized point sequence.
     * @param maxError Maximum orthogonal pixel deviation tolerated before splitting.
     * @param maxIterations Number of Newton-Raphson parameter re-estimation passes.
     * @return List of fitted cubic Bézier curve segments.
     */
    fun fitCurve(
        points: List<CvPoint>,
        maxError: Float = 2.0f,
        maxIterations: Int = 3
    ): List<CubicBezierSegment> {
        val n = points.size
        if (n < 2) return emptyList()

        if (n == 2) {
            val p0 = points[0]
            val p3 = points[1]
            val dx = (p3.x - p0.x) / 3f
            val dy = (p3.y - p0.y) / 3f
            return listOf(
                CubicBezierSegment(
                    p0 = p0,
                    c1 = CvPoint(p0.x + dx, p0.y + dy),
                    c2 = CvPoint(p3.x - dx, p3.y - dy),
                    p3 = p3
                )
            )
        }

        // Estimate unit tangents at endpoints
        val tHat1 = computeLeftTangent(points, 0)
        val tHat2 = computeRightTangent(points, n - 1)

        val result = mutableListOf<CubicBezierSegment>()
        fitCubic(points, 0, n - 1, tHat1, tHat2, maxError, maxIterations, result)
        return result
    }

    private fun fitCubic(
        points: List<CvPoint>,
        first: Int,
        last: Int,
        tHat1: CvPoint,
        tHat2: CvPoint,
        error: Float,
        maxIterations: Int,
        result: MutableList<CubicBezierSegment>
    ) {
        val nPts = last - first + 1
        if (nPts == 2) {
            val p0 = points[first]
            val p3 = points[last]
            val dist = hypot(p3.x - p0.x, p3.y - p0.y) / 3f
            result.add(
                CubicBezierSegment(
                    p0 = p0,
                    c1 = CvPoint(p0.x + tHat1.x * dist, p0.y + tHat1.y * dist),
                    c2 = CvPoint(p3.x - tHat2.x * dist, p3.y - tHat2.y * dist),
                    p3 = p3
                )
            )
            return
        }

        // Parameterize points using chord length
        var u = chordLengthParameterize(points, first, last)

        // Fit single cubic curve
        var bezCurve = generateBezier(points, first, last, u, tHat1, tHat2)

        // Find maximum error and position
        var (maxError, splitPoint) = computeMaxError(points, first, last, bezCurve, u)
        if (maxError < error) {
            result.add(bezCurve)
            return
        }

        // If error not too large, try Newton-Raphson reparameterization
        if (maxError < error * 4f) {
            for (iter in 0 until maxIterations) {
                u = reparameterize(bezCurve, points, first, last, u)
                bezCurve = generateBezier(points, first, last, u, tHat1, tHat2)
                val err = computeMaxError(points, first, last, bezCurve, u)
                maxError = err.first
                splitPoint = err.second
                if (maxError < error) {
                    result.add(bezCurve)
                    return
                }
            }
        }

        // Fitting failed: recursively split at point of maximum error
        var split = splitPoint
        if (split <= first) split = first + 1
        if (split >= last) split = last - 1

        val tHatCenter = computeCenterTangent(points, split)
        fitCubic(points, first, split, tHat1, tHatCenter, error, maxIterations, result)
        fitCubic(points, split, last, tHatCenter, tHat2, error, maxIterations, result)
    }

    private fun generateBezier(
        points: List<CvPoint>,
        first: Int,
        last: Int,
        uPrime: FloatArray,
        tHat1: CvPoint,
        tHat2: CvPoint
    ): CubicBezierSegment {
        val p0 = points[first]
        val p3 = points[last]
        val nPts = last - first + 1

        // Matrix equation: C * [alpha1, alpha2]^T = X
        var c00 = 0.0
        var c01 = 0.0
        var c11 = 0.0
        var x0 = 0.0
        var x1 = 0.0

        for (i in 0 until nPts) {
            val uVal = uPrime[i].toDouble()
            val omU = 1.0 - uVal
            val b0 = omU * omU * omU
            val b1 = 3.0 * uVal * omU * omU
            val b2 = 3.0 * uVal * uVal * omU
            val b3 = uVal * uVal * uVal

            val a1x = tHat1.x * b1
            val a1y = tHat1.y * b1
            val a2x = -tHat2.x * b2
            val a2y = -tHat2.y * b2

            c00 += a1x * a1x + a1y * a1y
            c01 += a1x * a2x + a1y * a2y
            c11 += a2x * a2x + a2y * a2y

            val pt = points[first + i]
            val qx = pt.x - (b0 * p0.x + b1 * p0.x + b2 * p3.x + b3 * p3.x)
            val qy = pt.y - (b0 * p0.y + b1 * p0.y + b2 * p3.y + b3 * p3.y)

            x0 += a1x * qx + a1y * qy
            x1 += a2x * qx + a2y * qy
        }

        val det = c00 * c11 - c01 * c01
        var alpha1: Double
        var alpha2: Double

        if (abs(det) > 1e-6) {
            alpha1 = (x0 * c11 - x1 * c01) / det
            alpha2 = (c00 * x1 - c01 * x0) / det
        } else {
            alpha1 = 0.0
            alpha2 = 0.0
        }

        // If alpha is negative or zero, fall back to heuristic 1/3 distance
        val chordDist = hypot(p3.x - p0.x, p3.y - p0.y) / 3.0
        if (alpha1 <= 1e-4 || alpha2 <= 1e-4 || alpha1 > chordDist * 10 || alpha2 > chordDist * 10) {
            alpha1 = chordDist
            alpha2 = chordDist
        }

        val c1 = CvPoint(
            (p0.x + tHat1.x * alpha1).toFloat(),
            (p0.y + tHat1.y * alpha1).toFloat()
        )
        val c2 = CvPoint(
            (p3.x - tHat2.x * alpha2).toFloat(),
            (p3.y - tHat2.y * alpha2).toFloat()
        )

        return CubicBezierSegment(p0, c1, c2, p3)
    }

    private fun reparameterize(
        curve: CubicBezierSegment,
        points: List<CvPoint>,
        first: Int,
        last: Int,
        u: FloatArray
    ): FloatArray {
        val nPts = last - first + 1
        val uPrime = FloatArray(nPts)

        for (i in 0 until nPts) {
            uPrime[i] = findRoot(curve, points[first + i], u[i])
        }
        return uPrime
    }

    private fun findRoot(curve: CubicBezierSegment, pt: CvPoint, u: Float): Float {
        // Newton-Raphson method for parameter u minimizing distance ||B(u) - pt||^2
        var currU = u
        for (i in 0 until 4) {
            val q = curve.evaluate(currU)
            val qPrime = curve.evaluateDerivative(currU)
            val qPrimePrime = curve.evaluateSecondDerivative(currU)

            val diffX = q.x - pt.x
            val diffY = q.y - pt.y

            val num = diffX * qPrime.x + diffY * qPrime.y
            val den = qPrime.x * qPrime.x + qPrime.y * qPrime.y + diffX * qPrimePrime.x + diffY * qPrimePrime.y

            if (abs(den) < 1e-6f) break
            val delta = num / den
            currU -= delta
            if (abs(delta) < 1e-4f) break
        }
        return currU.coerceIn(0f, 1f)
    }

    private fun computeMaxError(
        points: List<CvPoint>,
        first: Int,
        last: Int,
        curve: CubicBezierSegment,
        u: FloatArray
    ): Pair<Float, Int> {
        var maxDist = 0f
        var splitPoint = (last - first) / 2 + first
        val nPts = last - first + 1

        for (i in 1 until nPts - 1) {
            val p = curve.evaluate(u[i])
            val pt = points[first + i]
            val dist = hypot(p.x - pt.x, p.y - pt.y)
            if (dist >= maxDist) {
                maxDist = dist
                splitPoint = first + i
            }
        }

        return Pair(maxDist, splitPoint)
    }

    private fun chordLengthParameterize(
        points: List<CvPoint>,
        first: Int,
        last: Int
    ): FloatArray {
        val nPts = last - first + 1
        val u = FloatArray(nPts)
        u[0] = 0f

        for (i in 1 until nPts) {
            val p1 = points[first + i]
            val p0 = points[first + i - 1]
            u[i] = u[i - 1] + hypot(p1.x - p0.x, p1.y - p0.y)
        }

        val totalLen = u[nPts - 1]
        if (totalLen > 1e-5f) {
            for (i in 1 until nPts) {
                u[i] /= totalLen
            }
        }
        return u
    }

    private fun computeLeftTangent(points: List<CvPoint>, end: Int): CvPoint {
        val p0 = points[end]
        val p1 = points[(end + 1).coerceAtMost(points.size - 1)]
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val len = hypot(dx, dy)
        return if (len > 1e-5f) CvPoint(dx / len, dy / len) else CvPoint(1f, 0f)
    }

    private fun computeRightTangent(points: List<CvPoint>, end: Int): CvPoint {
        val p0 = points[(end - 1).coerceAtLeast(0)]
        val p1 = points[end]
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val len = hypot(dx, dy)
        return if (len > 1e-5f) CvPoint(dx / len, dy / len) else CvPoint(1f, 0f)
    }

    private fun computeCenterTangent(points: List<CvPoint>, center: Int): CvPoint {
        val pPrev = points[(center - 1).coerceAtLeast(0)]
        val pNext = points[(center + 1).coerceAtMost(points.size - 1)]
        val dx = pNext.x - pPrev.x
        val dy = pNext.y - pPrev.y
        val len = hypot(dx, dy)
        return if (len > 1e-5f) CvPoint(dx / len, dy / len) else CvPoint(1f, 0f)
    }

    /**
     * Converts a continuous chain of cubic Bézier segments into a list of [VectorAnchor]s.
     */
    fun segmentsToAnchors(
        segments: List<CubicBezierSegment>,
        isClosed: Boolean = false
    ): List<VectorAnchor> {
        if (segments.isEmpty()) return emptyList()

        val anchors = mutableListOf<VectorAnchor>()

        // First anchor
        val firstSeg = segments.first()
        anchors.add(
            VectorAnchor(
                x = firstSeg.p0.x,
                y = firstSeg.p0.y,
                handleInX = if (isClosed) segments.last().c2.x else null,
                handleInY = if (isClosed) segments.last().c2.y else null,
                handleOutX = firstSeg.c1.x,
                handleOutY = firstSeg.c1.y,
                isSmooth = isClosed
            )
        )

        // Intermediate anchors
        for (i in 0 until segments.size - 1) {
            val currSeg = segments[i]
            val nextSeg = segments[i + 1]
            anchors.add(
                VectorAnchor(
                    x = currSeg.p3.x,
                    y = currSeg.p3.y,
                    handleInX = currSeg.c2.x,
                    handleInY = currSeg.c2.y,
                    handleOutX = nextSeg.c1.x,
                    handleOutY = nextSeg.c1.y,
                    isSmooth = true
                )
            )
        }

        // Last anchor (only added if open; if closed, it loops back to first anchor)
        if (!isClosed) {
            val lastSeg = segments.last()
            anchors.add(
                VectorAnchor(
                    x = lastSeg.p3.x,
                    y = lastSeg.p3.y,
                    handleInX = lastSeg.c2.x,
                    handleInY = lastSeg.c2.y,
                    handleOutX = null,
                    handleOutY = null,
                    isSmooth = false
                )
            )
        }

        return anchors
    }
}
