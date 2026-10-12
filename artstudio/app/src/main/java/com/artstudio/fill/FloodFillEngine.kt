package com.artstudio.fill

import com.artstudio.document.model.BlendModeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-performance, memory-bounded flood fill and global color replacement engine.
 * Supports:
 * - Contiguous scanline flood fill and global color selection.
 * - Numerical color distance tolerance in 4-channel RGBA space.
 * - Morphological barrier closing for bridging gaps in line art.
 * - Boundary expansion and contraction (bleed under line art).
 * - Edge anti-aliasing.
 * - Explicit transparency behaviors.
 * - Cooperative cancellation and off-UI-thread execution.
 */
object FloodFillEngine {

    /**
     * Executes fill operation synchronously with optional cancellation token.
     * Can be invoked on any thread (recommended on background dispatcher).
     */
    fun executeFill(
        sourcePixels: IntArray,
        targetPixels: IntArray,
        width: Int,
        height: Int,
        seedX: Int,
        seedY: Int,
        config: FillConfig,
        cancelToken: FillCancellationToken? = null
    ): FillResult? {
        val startTime = System.currentTimeMillis()

        if (width <= 0 || height <= 0 || sourcePixels.size != width * height || targetPixels.size != width * height) {
            return FillResult(success = false, filledPixelCount = 0, mask = ByteArray(0), resultPixels = null)
        }

        if (seedX !in 0 until width || seedY !in 0 until height) {
            return FillResult(success = false, filledPixelCount = 0, mask = ByteArray(width * height), resultPixels = targetPixels.clone())
        }

        if (cancelToken?.isCancelled == true) return null

        val seedIndex = seedY * width + seedX
        val seedColor = sourcePixels[seedIndex]

        // 1. Generate Raw Fill Mask
        var mask = when (config.fillMode) {
            FillMode.CONTIGUOUS -> {
                generateContiguousMask(
                    sourcePixels = sourcePixels,
                    width = width,
                    height = height,
                    seedX = seedX,
                    seedY = seedY,
                    seedColor = seedColor,
                    config = config,
                    cancelToken = cancelToken
                )
            }
            FillMode.GLOBAL_COLOR -> {
                generateGlobalColorMask(
                    sourcePixels = sourcePixels,
                    width = width,
                    height = height,
                    seedColor = seedColor,
                    config = config,
                    cancelToken = cancelToken
                )
            }
        } ?: return null // Cancelled

        if (cancelToken?.isCancelled == true) return null

        // 2. Apply Boundary Expansion / Contraction if specified
        if (config.boundaryExpansion != 0) {
            mask = if (config.boundaryExpansion > 0) {
                dilateMask(mask, width, height, config.boundaryExpansion, cancelToken)
            } else {
                erodeMask(mask, width, height, abs(config.boundaryExpansion), cancelToken)
            } ?: return null
        }

        if (cancelToken?.isCancelled == true) return null

        // 3. Apply Edge Anti-Aliasing if enabled
        if (config.antiAlias) {
            mask = antiAliasMask(mask, width, height, cancelToken) ?: return null
        }

        if (cancelToken?.isCancelled == true) return null

        // 4. Count filled pixels
        var filledCount = 0
        for (i in mask.indices) {
            if ((mask[i].toInt() and 0xFF) > 0) {
                filledCount++
            }
        }

        // 5. Composite Fill onto Target Pixels
        val resultPixels = targetPixels.clone()
        compositeFill(
            destPixels = resultPixels,
            mask = mask,
            width = width,
            height = height,
            config = config,
            cancelToken = cancelToken
        ) ?: return null

        val duration = System.currentTimeMillis() - startTime
        return FillResult(
            success = true,
            filledPixelCount = filledCount,
            mask = mask,
            resultPixels = resultPixels,
            durationMs = duration
        )
    }

    /**
     * Executes fill operation asynchronously on Dispatchers.Default with cancellation support.
     */
    suspend fun executeFillAsync(
        sourcePixels: IntArray,
        targetPixels: IntArray,
        width: Int,
        height: Int,
        seedX: Int,
        seedY: Int,
        config: FillConfig,
        cancelToken: FillCancellationToken? = null
    ): FillResult? = withContext(Dispatchers.Default) {
        executeFill(
            sourcePixels = sourcePixels,
            targetPixels = targetPixels,
            width = width,
            height = height,
            seedX = seedX,
            seedY = seedY,
            config = config,
            cancelToken = cancelToken
        )
    }

    // -------------------------------------------------------------------------
    // Contiguous Flood Fill & Gap Closing
    // -------------------------------------------------------------------------

    private fun generateContiguousMask(
        sourcePixels: IntArray,
        width: Int,
        height: Int,
        seedX: Int,
        seedY: Int,
        seedColor: Int,
        config: FillConfig,
        cancelToken: FillCancellationToken?
    ): ByteArray? {
        val totalPixels = width * height
        val mask = ByteArray(totalPixels)

        // If seed pixel does not match under transparencyMode, nothing to fill
        if (!isColorMatch(seedColor, seedColor, config.tolerance, config.transparencyMode)) {
            return mask
        }

        // Handle Gap Closing via Dilated Barrier Mask
        val dilatedBarrier: ByteArray? = if (config.gapClosingRadius > 0) {
            val barrier = ByteArray(totalPixels)
            for (i in 0 until totalPixels) {
                val c = sourcePixels[i]
                barrier[i] = if (!isColorMatch(c, seedColor, config.tolerance, config.transparencyMode)) 1 else 0
            }
            if (cancelToken?.isCancelled == true) return null
            val dilated = dilateBinary(barrier, width, height, config.gapClosingRadius, cancelToken) ?: return null
            // Ensure the seed pixel itself is not blocked
            dilated[seedY * width + seedX] = 0
            dilated
        } else {
            null
        }

        if (cancelToken?.isCancelled == true) return null

        // 4-Way Scanline Flood Fill Stack
        // Each entry in stack: (xLeft, xRight, y, dy)
        val stackX1 = IntArray(totalPixels)
        val stackX2 = IntArray(totalPixels)
        val stackY = IntArray(totalPixels)
        val stackDy = IntArray(totalPixels)
        var stackPointer = 0

        fun pushSpan(x1: Int, x2: Int, y: Int, dy: Int) {
            if (y in 0 until height && stackPointer < totalPixels) {
                stackX1[stackPointer] = x1
                stackX2[stackPointer] = x2
                stackY[stackPointer] = y
                stackDy[stackPointer] = dy
                stackPointer++
            }
        }

        // Initialize with seed span
        pushSpan(seedX, seedX, seedY, 1)
        pushSpan(seedX, seedX, seedY - 1, -1)

        val visited = BooleanArray(totalPixels)
        var iterationCheck = 0

        while (stackPointer > 0) {
            if (++iterationCheck and 0xFFF == 0) {
                if (cancelToken?.isCancelled == true) return null
            }

            stackPointer--
            var x1 = stackX1[stackPointer]
            val x2 = stackX2[stackPointer]
            val y = stackY[stackPointer]
            val dy = stackDy[stackPointer]

            val rowOffset = y * width

            // Extend left
            var x = x1
            while (x >= 0) {
                val idx = rowOffset + x
                if (visited[idx]) break
                if (dilatedBarrier != null && dilatedBarrier[idx].toInt() != 0) break
                if (!isColorMatch(sourcePixels[idx], seedColor, config.tolerance, config.transparencyMode)) break
                x--
            }
            val left = x + 1

            // Extend right
            x = x1
            while (x < width) {
                val idx = rowOffset + x
                if (visited[idx]) break
                if (dilatedBarrier != null && dilatedBarrier[idx].toInt() != 0) break
                if (!isColorMatch(sourcePixels[idx], seedColor, config.tolerance, config.transparencyMode)) break
                x++
            }
            val right = x - 1

            if (left > right) continue

            // Mark span as visited and filled
            for (curX in left..right) {
                val idx = rowOffset + curX
                visited[idx] = true
                mask[idx] = 255.toByte()
            }

            // Scan adjacent row in same direction
            scanRow(left, right, y + dy, dy, width, height, sourcePixels, seedColor, dilatedBarrier, visited, config, ::pushSpan)

            // Scan adjacent row in opposite direction if range extended beyond parent
            if (left < x1) {
                scanRow(left, x1 - 1, y - dy, -dy, width, height, sourcePixels, seedColor, dilatedBarrier, visited, config, ::pushSpan)
            }
            if (right > x2) {
                scanRow(x2 + 1, right, y - dy, -dy, width, height, sourcePixels, seedColor, dilatedBarrier, visited, config, ::pushSpan)
            }
        }

        return if (config.gapClosingRadius > 0) {
            val dilated = dilateMask(mask, width, height, config.gapClosingRadius, cancelToken) ?: return null
            for (i in 0 until totalPixels) {
                if (!isColorMatch(sourcePixels[i], seedColor, config.tolerance, config.transparencyMode)) {
                    dilated[i] = 0
                }
            }
            dilated
        } else {
            mask
        }
    }

    private inline fun scanRow(
        xLeft: Int,
        xRight: Int,
        targetY: Int,
        dy: Int,
        width: Int,
        height: Int,
        sourcePixels: IntArray,
        seedColor: Int,
        dilatedBarrier: ByteArray?,
        visited: BooleanArray,
        config: FillConfig,
        crossinline pushSpan: (Int, Int, Int, Int) -> Unit
    ) {
        if (targetY !in 0 until height) return
        val rowOffset = targetY * width
        var inSpan = false
        var spanStart = xLeft

        for (x in xLeft..xRight) {
            val idx = rowOffset + x
            val isValid = !visited[idx] &&
                    (dilatedBarrier == null || dilatedBarrier[idx].toInt() == 0) &&
                    isColorMatch(sourcePixels[idx], seedColor, config.tolerance, config.transparencyMode)

            if (isValid) {
                if (!inSpan) {
                    inSpan = true
                    spanStart = x
                }
            } else {
                if (inSpan) {
                    inSpan = false
                    pushSpan(spanStart, x - 1, targetY, dy)
                }
            }
        }
        if (inSpan) {
            pushSpan(spanStart, xRight, targetY, dy)
        }
    }

    // -------------------------------------------------------------------------
    // Global Color Replacement Mask
    // -------------------------------------------------------------------------

    private fun generateGlobalColorMask(
        sourcePixels: IntArray,
        width: Int,
        height: Int,
        seedColor: Int,
        config: FillConfig,
        cancelToken: FillCancellationToken?
    ): ByteArray? {
        val totalPixels = width * height
        val mask = ByteArray(totalPixels)

        for (i in 0 until totalPixels) {
            if (i and 0xFFF == 0 && cancelToken?.isCancelled == true) return null
            if (isColorMatch(sourcePixels[i], seedColor, config.tolerance, config.transparencyMode)) {
                mask[i] = 255.toByte()
            }
        }
        return mask
    }

    // -------------------------------------------------------------------------
    // Color Distance & Matching
    // -------------------------------------------------------------------------

    fun colorDistance(c1: Int, c2: Int): Float {
        val a1 = (c1 ushr 24) and 0xFF
        val a2 = (c2 ushr 24) and 0xFF

        // If both are fully transparent, color distance is 0.0
        if (a1 == 0 && a2 == 0) return 0.0f

        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF

        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF

        val da = a1 - a2
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2

        // Normalized Euclidean distance in 4D RGBA space: max distance is sqrt(4 * 255^2) = 510.0
        val distSq = da * da + dr * dr + dg * dg + db * db
        return (sqrt(distSq.toDouble()) / 510.0).toFloat()
    }

    fun isColorMatch(
        pixelColor: Int,
        seedColor: Int,
        tolerance: Float,
        transparencyMode: FillTransparencyMode
    ): Boolean {
        val alpha = (pixelColor ushr 24) and 0xFF

        when (transparencyMode) {
            FillTransparencyMode.TRANSPARENT_ONLY -> {
                return alpha <= 30
            }
            FillTransparencyMode.OPAQUE_ONLY -> {
                if (alpha <= 30) return false
                return colorDistance(pixelColor, seedColor) <= tolerance
            }
            FillTransparencyMode.NORMAL,
            FillTransparencyMode.RESPECT_TRANSPARENCY -> {
                return colorDistance(pixelColor, seedColor) <= tolerance
            }
        }
    }

    // -------------------------------------------------------------------------
    // Morphology: Dilation, Erosion, Closing
    // -------------------------------------------------------------------------

    fun dilateBinary(
        src: ByteArray,
        width: Int,
        height: Int,
        radius: Int,
        cancelToken: FillCancellationToken? = null
    ): ByteArray? {
        if (radius <= 0) return src.clone()
        val dest = src.clone()
        val rSq = radius * radius

        for (y in 0 until height) {
            if (y and 0x1F == 0 && cancelToken?.isCancelled == true) return null
            val yOffset = y * width
            for (x in 0 until width) {
                if (src[yOffset + x].toInt() != 0) {
                    val minY = max(0, y - radius)
                    val maxY = min(height - 1, y + radius)
                    val minX = max(0, x - radius)
                    val maxX = min(width - 1, x + radius)

                    for (ny in minY..maxY) {
                        val dy = ny - y
                        val dySq = dy * dy
                        val nRow = ny * width
                        for (nx in minX..maxX) {
                            val dx = nx - x
                            if (dx * dx + dySq <= rSq) {
                                dest[nRow + nx] = 1
                            }
                        }
                    }
                }
            }
        }
        return dest
    }

    fun erodeBinary(
        src: ByteArray,
        width: Int,
        height: Int,
        radius: Int,
        cancelToken: FillCancellationToken? = null
    ): ByteArray? {
        if (radius <= 0) return src.clone()
        val dest = ByteArray(width * height)
        val rSq = radius * radius

        for (y in 0 until height) {
            if (y and 0x1F == 0 && cancelToken?.isCancelled == true) return null
            val yOffset = y * width
            for (x in 0 until width) {
                if (src[yOffset + x].toInt() != 0) {
                    val minY = max(0, y - radius)
                    val maxY = min(height - 1, y + radius)
                    val minX = max(0, x - radius)
                    val maxX = min(width - 1, x + radius)

                    var allSet = true
                    checkLoop@ for (ny in minY..maxY) {
                        val dy = ny - y
                        val dySq = dy * dy
                        val nRow = ny * width
                        for (nx in minX..maxX) {
                            val dx = nx - x
                            if (dx * dx + dySq <= rSq) {
                                if (src[nRow + nx].toInt() == 0) {
                                    allSet = false
                                    break@checkLoop
                                }
                            }
                        }
                    }
                    if (allSet) {
                        dest[yOffset + x] = 1
                    }
                }
            }
        }
        return dest
    }

    fun dilateMask(
        mask: ByteArray,
        width: Int,
        height: Int,
        radius: Int,
        cancelToken: FillCancellationToken? = null
    ): ByteArray? {
        if (radius <= 0) return mask
        val dest = mask.clone()
        val rSq = radius * radius

        for (y in 0 until height) {
            if (y and 0x1F == 0 && cancelToken?.isCancelled == true) return null
            val yOffset = y * width
            for (x in 0 until width) {
                if ((mask[yOffset + x].toInt() and 0xFF) > 0) {
                    val minY = max(0, y - radius)
                    val maxY = min(height - 1, y + radius)
                    val minX = max(0, x - radius)
                    val maxX = min(width - 1, x + radius)

                    for (ny in minY..maxY) {
                        val dy = ny - y
                        val dySq = dy * dy
                        val nRow = ny * width
                        for (nx in minX..maxX) {
                            val dx = nx - x
                            if (dx * dx + dySq <= rSq) {
                                dest[nRow + nx] = 255.toByte()
                            }
                        }
                    }
                }
            }
        }
        return dest
    }

    fun erodeMask(
        mask: ByteArray,
        width: Int,
        height: Int,
        radius: Int,
        cancelToken: FillCancellationToken? = null
    ): ByteArray? {
        if (radius <= 0) return mask
        val dest = ByteArray(width * height)
        val rSq = radius * radius

        for (y in 0 until height) {
            if (y and 0x1F == 0 && cancelToken?.isCancelled == true) return null
            val yOffset = y * width
            for (x in 0 until width) {
                if ((mask[yOffset + x].toInt() and 0xFF) == 255) {
                    val minY = max(0, y - radius)
                    val maxY = min(height - 1, y + radius)
                    val minX = max(0, x - radius)
                    val maxX = min(width - 1, x + radius)

                    var allSet = true
                    checkLoop@ for (ny in minY..maxY) {
                        val dy = ny - y
                        val dySq = dy * dy
                        val nRow = ny * width
                        for (nx in minX..maxX) {
                            val dx = nx - x
                            if (dx * dx + dySq <= rSq) {
                                if ((mask[nRow + nx].toInt() and 0xFF) == 0) {
                                    allSet = false
                                    break@checkLoop
                                }
                            }
                        }
                    }
                    if (allSet) {
                        dest[yOffset + x] = 255.toByte()
                    }
                }
            }
        }
        return dest
    }

    // -------------------------------------------------------------------------
    // Anti-Aliasing (Edge Smoothing)
    // -------------------------------------------------------------------------

    fun antiAliasMask(
        mask: ByteArray,
        width: Int,
        height: Int,
        cancelToken: FillCancellationToken? = null
    ): ByteArray? {
        val dest = mask.clone()

        for (y in 0 until height) {
            if (y and 0x1F == 0 && cancelToken?.isCancelled == true) return null
            val yOffset = y * width
            for (x in 0 until width) {
                val current = mask[yOffset + x].toInt() and 0xFF

                // Check if this pixel is near a boundary edge (has at least one different neighbor)
                val minY = max(0, y - 1)
                val maxY = min(height - 1, y + 1)
                val minX = max(0, x - 1)
                val maxX = min(width - 1, x + 1)

                var hasDiff = false
                var sum = 0
                var count = 0

                for (ny in minY..maxY) {
                    val nRow = ny * width
                    for (nx in minX..maxX) {
                        val nVal = mask[nRow + nx].toInt() and 0xFF
                        if ((nVal > 0) != (current > 0)) {
                            hasDiff = true
                        }
                        sum += nVal
                        count++
                    }
                }

                if (hasDiff) {
                    val smoothed = (sum.toFloat() / count.toFloat()).toInt()
                    dest[yOffset + x] = smoothed.toByte()
                }
            }
        }
        return dest
    }

    // -------------------------------------------------------------------------
    // Compositing Onto Target
    // -------------------------------------------------------------------------

    fun compositeFill(
        destPixels: IntArray,
        mask: ByteArray,
        width: Int,
        height: Int,
        config: FillConfig,
        cancelToken: FillCancellationToken? = null
    ): IntArray? {
        val fillA = ((config.fillColor ushr 24) and 0xFF) / 255.0f
        val fillR = ((config.fillColor ushr 16) and 0xFF) / 255.0f
        val fillG = ((config.fillColor ushr 8) and 0xFF) / 255.0f
        val fillB = (config.fillColor and 0xFF) / 255.0f

        val globalOpacity = config.fillOpacity

        for (i in destPixels.indices) {
            if (i and 0xFFF == 0 && cancelToken?.isCancelled == true) return null

            val coverage = (mask[i].toInt() and 0xFF) / 255.0f
            if (coverage <= 0.001f) continue

            val srcA = fillA * coverage * globalOpacity
            if (srcA <= 0.001f) continue

            val dstColor = destPixels[i]
            val dstA = ((dstColor ushr 24) and 0xFF) / 255.0f
            val dstR = ((dstColor ushr 16) and 0xFF) / 255.0f
            val dstG = ((dstColor ushr 8) and 0xFF) / 255.0f
            val dstB = (dstColor and 0xFF) / 255.0f

            // Porter-Duff Source-Over compositing
            val outA = srcA + dstA * (1.0f - srcA)
            if (outA <= 0.001f) {
                destPixels[i] = 0
                continue
            }

            var finalA = outA
            if (config.transparencyMode == FillTransparencyMode.RESPECT_TRANSPARENCY) {
                finalA = dstA
            }

            val outR = (fillR * srcA + dstR * dstA * (1.0f - srcA)) / outA
            val outG = (fillG * srcA + dstG * dstA * (1.0f - srcA)) / outA
            val outB = (fillB * srcA + dstB * dstA * (1.0f - srcA)) / outA

            val intA = (finalA * 255.0f + 0.5f).toInt().coerceIn(0, 255)
            val intR = (outR * 255.0f + 0.5f).toInt().coerceIn(0, 255)
            val intG = (outG * 255.0f + 0.5f).toInt().coerceIn(0, 255)
            val intB = (outB * 255.0f + 0.5f).toInt().coerceIn(0, 255)

            destPixels[i] = (intA shl 24) or (intR shl 16) or (intG shl 8) or intB
        }

        return destPixels
    }
}
