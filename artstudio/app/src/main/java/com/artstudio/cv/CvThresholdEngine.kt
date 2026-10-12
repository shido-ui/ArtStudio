package com.artstudio.cv

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic binarization and thresholding algorithms.
 *
 * Implements:
 * 1. Global Otsu's thresholding (optimal bimodal clustering).
 * 2. Adaptive local mean thresholding using an O(1) integral image.
 * 3. Manual thresholding.
 */
object CvThresholdEngine {

    /**
     * Otsu's global binarization. Automatically selects the threshold that maximizes between-class variance.
     *
     * @param invertPolarity If true, dark strokes (< threshold) map to 255 (foreground) and bright paper to 0.
     */
    fun otsuBinarize(
        gray: IntArray,
        width: Int,
        height: Int,
        invertPolarity: Boolean = false
    ): Pair<Int, IntArray> {
        val totalPixels = width * height
        if (totalPixels == 0) return Pair(128, IntArray(0))

        // 1. Compute 256-bin histogram
        val histogram = IntArray(256)
        for (v in gray) {
            histogram[v.coerceIn(0, 255)]++
        }

        // 2. Compute total intensity sum
        var sumTotal = 0.0
        for (i in 0 until 256) {
            sumTotal += i.toDouble() * histogram[i]
        }

        var sumBackground = 0.0
        var weightBackground = 0
        var maxVariance = -1.0
        var bestThresholdSum = 0
        var bestThresholdCount = 0

        // 3. Search for optimal threshold
        for (t in 0 until 256) {
            weightBackground += histogram[t]
            if (weightBackground == 0) continue

            val weightForeground = totalPixels - weightBackground
            if (weightForeground == 0) break

            sumBackground += t.toDouble() * histogram[t]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sumTotal - sumBackground) / weightForeground

            val diff = meanBackground - meanForeground
            val betweenClassVariance = weightBackground.toDouble() * weightForeground.toDouble() * diff * diff

            if (betweenClassVariance > maxVariance + 1e-6) {
                maxVariance = betweenClassVariance
                bestThresholdSum = t
                bestThresholdCount = 1
            } else if (abs(betweenClassVariance - maxVariance) <= 1e-6) {
                bestThresholdSum += t
                bestThresholdCount++
            }
        }

        val bestThreshold = if (bestThresholdCount > 0) {
            (bestThresholdSum / bestThresholdCount).coerceIn(0, 255)
        } else {
            128
        }

        // 4. Binarize pixel buffer
        val binary = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            val v = gray[i]
            binary[i] = if (invertPolarity) {
                if (v < bestThreshold) 255 else 0
            } else {
                if (v >= bestThreshold) 255 else 0
            }
        }

        return Pair(bestThreshold, binary)
    }

    /**
     * Adaptive local mean thresholding with O(1) query time per pixel using an Integral Image.
     * Essential for handling non-uniform scanner gradients and lighting variations across artwork.
     */
    fun adaptiveLocalThreshold(
        gray: IntArray,
        width: Int,
        height: Int,
        windowRadius: Int = 7,
        c: Int = 5,
        invertPolarity: Boolean = false
    ): IntArray {
        val totalPixels = width * height
        if (totalPixels == 0) return IntArray(0)

        // 1. Build 2D Integral Image (using LongArray to prevent 32-bit overflow on large images)
        val integral = LongArray((width + 1) * (height + 1))
        val stride = width + 1

        for (y in 0 until height) {
            var rowSum = 0L
            val srcRowOffset = y * width
            val dstRowOffset = (y + 1) * stride

            for (x in 0 until width) {
                rowSum += gray[srcRowOffset + x]
                integral[dstRowOffset + (x + 1)] = integral[y * stride + (x + 1)] + rowSum
            }
        }

        // 2. Local threshold query in O(1)
        val r = windowRadius.coerceAtLeast(1)
        val binary = IntArray(totalPixels)

        for (y in 0 until height) {
            val y1 = max(0, y - r)
            val y2 = min(height - 1, y + r)
            val rowOffset = y * width

            for (x in 0 until width) {
                val x1 = max(0, x - r)
                val x2 = min(width - 1, x + r)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                // 4-corner integral sum: I(x2+1, y2+1) - I(x1, y2+1) - I(x2+1, y1) + I(x1, y1)
                val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                        integral[(y2 + 1) * stride + x1] -
                        integral[y1 * stride + (x2 + 1)] +
                        integral[y1 * stride + x1]

                val mean = (sum / count).toInt()
                val threshold = mean - c

                val v = gray[rowOffset + x]
                binary[rowOffset + x] = if (invertPolarity) {
                    if (v < threshold) 255 else 0
                } else {
                    if (v >= threshold) 255 else 0
                }
            }
        }

        return binary
    }

    /**
     * Simple fixed-threshold binarization.
     */
    fun manualThreshold(
        gray: IntArray,
        width: Int,
        height: Int,
        threshold: Int = 128,
        invertPolarity: Boolean = false
    ): IntArray {
        val totalPixels = width * height
        val binary = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            val v = gray[i]
            binary[i] = if (invertPolarity) {
                if (v < threshold) 255 else 0
            } else {
                if (v >= threshold) 255 else 0
            }
        }
        return binary
    }

    /**
     * Removes isolated noise speckles, graphite dust, and paper grain fibers from a binary mask.
     * Connected components of 8-connected foreground pixels with area < [minPixels] are cleared to 0.
     */
    fun despeckleBinaryMask(
        mask: IntArray,
        width: Int,
        height: Int,
        minPixels: Int = 4
    ): IntArray {
        val count = width * height
        if (count == 0 || minPixels <= 1) return mask.clone()

        val result = mask.clone()
        val visited = BooleanArray(count)
        val maxCompCapacity = minPixels + 8
        val queue = IntArray(maxCompCapacity)
        val componentPixels = IntArray(maxCompCapacity)

        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                if (result[idx] == 255 && !visited[idx]) {
                    visited[idx] = true
                    var head = 0
                    var tail = 0
                    queue[tail++] = idx
                    var compSize = 0
                    var exceedsLimit = false

                    while (head < tail) {
                        val curr = queue[head++]
                        if (compSize < componentPixels.size) {
                            componentPixels[compSize++] = curr
                        } else {
                            exceedsLimit = true
                            break
                        }

                        val cx = curr % width
                        val cy = curr / width

                        for (d in 0 until 8) {
                            val nx = cx + dx[d]
                            val ny = cy + dy[d]
                            if (nx in 0 until width && ny in 0 until height) {
                                val nIdx = ny * width + nx
                                if (result[nIdx] == 255 && !visited[nIdx]) {
                                    visited[nIdx] = true
                                    if (tail < queue.size) {
                                        queue[tail++] = nIdx
                                    } else {
                                        exceedsLimit = true
                                        break
                                    }
                                }
                            }
                        }
                        if (exceedsLimit) break
                    }

                    // If component size is strictly less than minPixels and didn't exceed queue, clear it to 0
                    if (!exceedsLimit && compSize < minPixels) {
                        for (i in 0 until compSize) {
                            result[componentPixels[i]] = 0
                        }
                    }
                }
            }
        }

        return result
    }
}
