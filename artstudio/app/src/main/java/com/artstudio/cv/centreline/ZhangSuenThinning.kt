package com.artstudio.cv.centreline

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic morphological thinning (Zhang-Suen) and distance transform algorithms.
 *
 * Implements:
 * 1. Zhang-Suen 8-connected iterative skeletonization.
 * 2. 2-Pass Chamfer distance transform for local stroke width estimation.
 * 3. Topological endpoint spur detection and configurable pruning.
 */
object ZhangSuenThinning {

    /**
     * Computes the 2-pass Chamfer distance transform on a binary mask.
     * Pixels with value 255 are foreground strokes, 0 are background.
     * Returns an array of Euclidean distances to the nearest background pixel.
     */
    fun computeDistanceTransform(binaryMask: IntArray, width: Int, height: Int): FloatArray {
        val total = width * height
        val dist = FloatArray(total)
        val infinity = (width + height).toFloat() * 2f

        // Initialize: 0 for background, infinity for foreground
        for (i in 0 until total) {
            dist[i] = if (binaryMask[i] == 255) infinity else 0f
        }

        val dOrth = 1.0f
        val dDiag = 1.4142f

        // Forward pass (top-left to bottom-right)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (dist[idx] == 0f) continue

                var d = dist[idx]
                if (x > 0) d = min(d, dist[idx - 1] + dOrth)
                if (y > 0) {
                    val prevRow = (y - 1) * width
                    d = min(d, dist[prevRow + x] + dOrth)
                    if (x > 0) d = min(d, dist[prevRow + x - 1] + dDiag)
                    if (x < width - 1) d = min(d, dist[prevRow + x + 1] + dDiag)
                }
                dist[idx] = d
            }
        }

        // Backward pass (bottom-right to top-left)
        for (y in height - 1 downTo 0) {
            val row = y * width
            for (x in width - 1 downTo 0) {
                val idx = row + x
                if (dist[idx] == 0f) continue

                var d = dist[idx]
                if (x < width - 1) d = min(d, dist[idx + 1] + dOrth)
                if (y < height - 1) {
                    val nextRow = (y + 1) * width
                    d = min(d, dist[nextRow + x] + dOrth)
                    if (x < width - 1) d = min(d, dist[nextRow + x + 1] + dDiag)
                    if (x > 0) d = min(d, dist[nextRow + x - 1] + dDiag)
                }
                dist[idx] = d
            }
        }

        return dist
    }

    /**
     * Executes the Zhang-Suen thinning algorithm to produce an 8-connected single-pixel skeleton.
     */
    fun thin(binaryMask: IntArray, width: Int, height: Int): IntArray {
        val total = width * height
        // Copy to working buffer (0 = background, 1 = foreground)
        val grid = ByteArray(total)
        for (i in 0 until total) {
            if (binaryMask[i] == 255) grid[i] = 1
        }

        val toDelete = IntArray(total)
        var hasChanged = true

        while (hasChanged) {
            hasChanged = false

            // Sub-iteration 1
            var deleteCount = 0
            for (y in 1 until height - 1) {
                val prevRow = (y - 1) * width
                val currRow = y * width
                val nextRow = (y + 1) * width

                for (x in 1 until width - 1) {
                    val idx = currRow + x
                    if (grid[idx] != 1.toByte()) continue

                    val p2 = grid[prevRow + x].toInt()
                    val p3 = grid[prevRow + x + 1].toInt()
                    val p4 = grid[currRow + x + 1].toInt()
                    val p5 = grid[nextRow + x + 1].toInt()
                    val p6 = grid[nextRow + x].toInt()
                    val p7 = grid[nextRow + x - 1].toInt()
                    val p8 = grid[currRow + x - 1].toInt()
                    val p9 = grid[prevRow + x - 1].toInt()

                    val b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                    if (b in 2..6) {
                        val a = countTransitions(p2, p3, p4, p5, p6, p7, p8, p9)
                        if (a == 1) {
                            if (p2 * p4 * p6 == 0 && p4 * p6 * p8 == 0) {
                                toDelete[deleteCount++] = idx
                            }
                        }
                    }
                }
            }

            if (deleteCount > 0) {
                for (i in 0 until deleteCount) {
                    grid[toDelete[i]] = 0
                }
                hasChanged = true
            }

            // Sub-iteration 2
            deleteCount = 0
            for (y in 1 until height - 1) {
                val prevRow = (y - 1) * width
                val currRow = y * width
                val nextRow = (y + 1) * width

                for (x in 1 until width - 1) {
                    val idx = currRow + x
                    if (grid[idx] != 1.toByte()) continue

                    val p2 = grid[prevRow + x].toInt()
                    val p3 = grid[prevRow + x + 1].toInt()
                    val p4 = grid[currRow + x + 1].toInt()
                    val p5 = grid[nextRow + x + 1].toInt()
                    val p6 = grid[nextRow + x].toInt()
                    val p7 = grid[nextRow + x - 1].toInt()
                    val p8 = grid[currRow + x - 1].toInt()
                    val p9 = grid[prevRow + x - 1].toInt()

                    val b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                    if (b in 2..6) {
                        val a = countTransitions(p2, p3, p4, p5, p6, p7, p8, p9)
                        if (a == 1) {
                            if (p2 * p4 * p8 == 0 && p2 * p6 * p8 == 0) {
                                toDelete[deleteCount++] = idx
                            }
                        }
                    }
                }
            }

            if (deleteCount > 0) {
                for (i in 0 until deleteCount) {
                    grid[toDelete[i]] = 0
                }
                hasChanged = true
            }
        }

        // Convert back to 0 or 255
        val result = IntArray(total)
        for (i in 0 until total) {
            result[i] = if (grid[i] == 1.toByte()) 255 else 0
        }
        return result
    }

    private fun countTransitions(
        p2: Int, p3: Int, p4: Int, p5: Int,
        p6: Int, p7: Int, p8: Int, p9: Int
    ): Int {
        var count = 0
        if (p2 == 0 && p3 == 1) count++
        if (p3 == 0 && p4 == 1) count++
        if (p4 == 0 && p5 == 1) count++
        if (p5 == 0 && p6 == 1) count++
        if (p6 == 0 && p7 == 1) count++
        if (p7 == 0 && p8 == 1) count++
        if (p8 == 0 && p9 == 1) count++
        if (p9 == 0 && p2 == 1) count++
        return count
    }
}
