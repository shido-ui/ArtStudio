package com.artstudio.cv

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Deterministic mathematical edge detection operators.
 *
 * Implements:
 * 1. Sobel edge detection (gradient magnitude and direction).
 * 2. Canny edge detector:
 *    - Gradient computation (Sobel kernels)
 *    - Directional Non-Maximum Suppression (NMS)
 *    - Dual thresholding (strong / weak edges)
 *    - 8-connected hysteresis edge tracking
 */
object CvEdgeEngine {

    /**
     * Sobel edge operator. Returns gradient magnitude float array and binarized edge mask.
     */
    fun sobelEdges(
        gray: IntArray,
        width: Int,
        height: Int,
        threshold: Float = 40.0f
    ): Pair<FloatArray, IntArray> {
        val totalPixels = width * height
        val magnitude = FloatArray(totalPixels)
        val binaryEdges = IntArray(totalPixels)

        for (y in 1 until height - 1) {
            val prevRow = (y - 1) * width
            val currRow = y * width
            val nextRow = (y + 1) * width

            for (x in 1 until width - 1) {
                // Sobel X kernel:
                // [-1, 0, 1]
                // [-2, 0, 2]
                // [-1, 0, 1]
                val gx = (gray[prevRow + x + 1] - gray[prevRow + x - 1]) +
                        2 * (gray[currRow + x + 1] - gray[currRow + x - 1]) +
                        (gray[nextRow + x + 1] - gray[nextRow + x - 1])

                // Sobel Y kernel:
                // [-1, -2, -1]
                // [ 0,  0,  0]
                // [ 1,  2,  1]
                val gy = (gray[nextRow + x - 1] - gray[prevRow + x - 1]) +
                        2 * (gray[nextRow + x] - gray[prevRow + x]) +
                        (gray[nextRow + x + 1] - gray[prevRow + x + 1])

                val mag = sqrt((gx * gx + gy * gy).toFloat())
                val idx = currRow + x
                magnitude[idx] = mag
                binaryEdges[idx] = if (mag >= threshold) 255 else 0
            }
        }

        return Pair(magnitude, binaryEdges)
    }

    /**
     * Full 5-stage Canny Edge Detector.
     */
    fun cannyEdges(
        gray: IntArray,
        width: Int,
        height: Int,
        lowThreshold: Float = 30.0f,
        highThreshold: Float = 80.0f
    ): IntArray {
        val totalPixels = width * height
        if (totalPixels == 0) return IntArray(0)

        // 1. Stage 1: Smoothing
        val smoothed = CvDenoiseEngine.gaussianBlur(gray, width, height, radius = 1, sigma = 1.0f)

        // 2. Stage 2: Gradient calculation
        val gxArr = FloatArray(totalPixels)
        val gyArr = FloatArray(totalPixels)
        val magArr = FloatArray(totalPixels)

        for (y in 1 until height - 1) {
            val prevRow = (y - 1) * width
            val currRow = y * width
            val nextRow = (y + 1) * width

            for (x in 1 until width - 1) {
                val gx = (smoothed[prevRow + x + 1] - smoothed[prevRow + x - 1]) +
                        2 * (smoothed[currRow + x + 1] - smoothed[currRow + x - 1]) +
                        (smoothed[nextRow + x + 1] - smoothed[nextRow + x - 1])

                val gy = (smoothed[nextRow + x - 1] - smoothed[prevRow + x - 1]) +
                        2 * (smoothed[nextRow + x] - smoothed[prevRow + x]) +
                        (smoothed[nextRow + x + 1] - smoothed[prevRow + x + 1])

                val idx = currRow + x
                gxArr[idx] = gx.toFloat()
                gyArr[idx] = gy.toFloat()
                magArr[idx] = sqrt((gx * gx + gy * gy).toFloat())
            }
        }

        // 3. Stage 3: Non-Maximum Suppression (NMS)
        val nms = FloatArray(totalPixels)
        for (y in 1 until height - 1) {
            val currRow = y * width
            val prevRow = (y - 1) * width
            val nextRow = (y + 1) * width

            for (x in 1 until width - 1) {
                val idx = currRow + x
                val mag = magArr[idx]
                if (mag < lowThreshold) continue

                val gx = gxArr[idx]
                val gy = gyArr[idx]
                val absGx = abs(gx)
                val absGy = abs(gy)

                var n1 = 0f
                var n2 = 0f

                // Quantize gradient normal to 4 sectors:
                if (absGx >= 2.414f * absGy) {
                    // Horizontal edge (vertical gradient normal)
                    n1 = magArr[currRow + x - 1]
                    n2 = magArr[currRow + x + 1]
                } else if (absGy >= 2.414f * absGx) {
                    // Vertical edge (horizontal gradient normal)
                    n1 = magArr[prevRow + x]
                    n2 = magArr[nextRow + x]
                } else if ((gx > 0 && gy > 0) || (gx < 0 && gy < 0)) {
                    // Diagonal 45°
                    n1 = magArr[prevRow + x - 1]
                    n2 = magArr[nextRow + x + 1]
                } else {
                    // Diagonal 135°
                    n1 = magArr[prevRow + x + 1]
                    n2 = magArr[nextRow + x - 1]
                }

                if (mag >= n1 && mag >= n2) {
                    nms[idx] = mag
                }
            }
        }

        // 4. Stage 4 & 5: Double Thresholding and 8-Connected Hysteresis
        // State: 0 = non-edge, 1 = weak edge, 2 = strong edge
        val edgeState = ByteArray(totalPixels)
        val queue = IntArray(totalPixels)
        var head = 0
        var tail = 0

        for (i in 0 until totalPixels) {
            val m = nms[i]
            if (m >= highThreshold) {
                edgeState[i] = 2
                queue[tail++] = i
            } else if (m >= lowThreshold) {
                edgeState[i] = 1
            }
        }

        // Breadth-first hysteresis edge tracking
        val dx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        val dy = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)

        while (head < tail) {
            val currIdx = queue[head++]
            val cx = currIdx % width
            val cy = currIdx / width

            for (d in 0 until 8) {
                val nx = cx + dx[d]
                val ny = cy + dy[d]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (edgeState[nIdx].toInt() == 1) {
                        edgeState[nIdx] = 2 // Promote weak edge to confirmed edge
                        queue[tail++] = nIdx
                    }
                }
            }
        }

        // Emit final binary mask: 255 for confirmed edges, 0 for others
        val result = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            if (edgeState[i].toInt() == 2) {
                result[i] = 255
            }
        }

        return result
    }
}
