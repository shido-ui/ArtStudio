package com.artstudio.selection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.artstudio.document.model.LayerMask
import java.util.UUID
import kotlin.math.*

/**
 * Authoritative Document-Space Selection Mask.
 * Represents an 8-bit grayscale transparency mask (0 = unselected, 255 = fully selected,
 * 1..254 = feathered / anti-aliased) defined in document coordinates.
 *
 * Fully supports:
 * - Geometric primitives: Rectangle, Ellipse, Freehand Lasso, and Polygon.
 * - Boolean combinations: REPLACE, ADD (Union), SUBTRACT (Difference), INTERSECT.
 * - Mask transformations: Invert, Clear, Expand (Dilation), Contract (Erosion), Feather (Gaussian blur).
 * - Integration with layer masks, fills, transforms, and region analysis.
 */
class SelectionMask(
    val width: Int,
    val height: Int,
    initialBuffer: ByteArray? = null
) {
    init {
        require(width > 0) { "SelectionMask width must be > 0, got $width" }
        require(height > 0) { "SelectionMask height must be > 0, got $height" }
    }

    // 8-bit unsigned alpha buffer [0..255] stored as signed Bytes
    val buffer: ByteArray = ByteArray(width * height)

    init {
        if (initialBuffer != null) {
            System.arraycopy(initialBuffer, 0, buffer, 0, minOf(buffer.size, initialBuffer.size))
        }
    }

    /**
     * Set of shapes contributing to this selection for vector reconstruction.
     */
    private val _shapes = mutableListOf<SelectionShape>()
    val shapes: List<SelectionShape> get() = _shapes.toList()

    /**
     * Checks if selection is completely empty (all pixels zero alpha).
     */
    val isEmpty: Boolean
        get() {
            for (i in buffer.indices) {
                if (buffer[i] != 0.toByte()) return false
            }
            return true
        }

    /**
     * Returns total number of pixels with alpha > 0.
     */
    val selectedPixelCount: Int
        get() {
            var count = 0
            for (i in buffer.indices) {
                if ((buffer[i].toInt() and 0xFF) > 0) count++
            }
            return count
        }

    /**
     * Resolves the bounding box of non-zero selection pixels.
     * Returns empty SelectionBounds if selection is empty.
     */
    val bounds: SelectionBounds
        get() {
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1

            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    if ((buffer[rowOffset + x].toInt() and 0xFF) > 0) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            return if (maxX >= minX && maxY >= minY) {
                SelectionBounds(minX.toFloat(), minY.toFloat(), (maxX + 1).toFloat(), (maxY + 1).toFloat())
            } else {
                SelectionBounds()
            }
        }

    /**
     * Returns alpha value [0..255] at document coordinates (x, y).
     */
    fun getAlpha(x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) return 0
        return buffer[y * width + x].toInt() and 0xFF
    }

    /**
     * Sets alpha value [0..255] at document coordinates (x, y).
     */
    fun setAlpha(x: Int, y: Int, alpha: Int) {
        if (x in 0 until width && y in 0 until height) {
            buffer[y * width + x] = alpha.coerceIn(0, 255).toByte()
        }
    }

    /**
     * Hit-test at document coordinates. Returns true if alpha >= threshold.
     */
    fun isSelected(x: Int, y: Int, threshold: Int = 128): Boolean {
        return getAlpha(x, y) >= threshold
    }

    /**
     * Clears the entire selection (sets all pixels to 0 alpha).
     */
    fun clear() {
        buffer.fill(0)
        _shapes.clear()
    }

    /**
     * Inverts the selection mask: M'(x, y) = 255 - M(x, y).
     */
    fun invert() {
        for (i in buffer.indices) {
            val a = buffer[i].toInt() and 0xFF
            buffer[i] = (255 - a).toByte()
        }
    }

    /**
     * Applies a selection shape according to the specified boolean [SelectionCombineMode].
     */
    fun applyShape(shape: SelectionShape, mode: SelectionCombineMode) {
        val shapeBuffer = ByteArray(width * height)
        rasterizeShape(shape, shapeBuffer)

        when (mode) {
            SelectionCombineMode.REPLACE -> {
                System.arraycopy(shapeBuffer, 0, buffer, 0, buffer.size)
                _shapes.clear()
                _shapes.add(shape)
            }
            SelectionCombineMode.ADD -> {
                for (i in buffer.indices) {
                    val curr = buffer[i].toInt() and 0xFF
                    val added = shapeBuffer[i].toInt() and 0xFF
                    buffer[i] = minOf(255, curr + added).toByte()
                }
                _shapes.add(shape)
            }
            SelectionCombineMode.SUBTRACT -> {
                for (i in buffer.indices) {
                    val curr = buffer[i].toInt() and 0xFF
                    val sub = shapeBuffer[i].toInt() and 0xFF
                    buffer[i] = maxOf(0, curr - sub).toByte()
                }
                _shapes.add(shape)
            }
            SelectionCombineMode.INTERSECT -> {
                for (i in buffer.indices) {
                    val curr = buffer[i].toInt() and 0xFF
                    val inter = shapeBuffer[i].toInt() and 0xFF
                    buffer[i] = minOf(curr, inter).toByte()
                }
                _shapes.add(shape)
            }
        }
    }

    /**
     * Rasterizes a [SelectionShape] into an 8-bit destination buffer using deterministic math.
     */
    private fun rasterizeShape(shape: SelectionShape, destBuffer: ByteArray) {
        when (shape) {
            is SelectionShape.Rectangle -> {
                val x0 = maxOf(0, shape.left.roundToInt())
                val y0 = maxOf(0, shape.top.roundToInt())
                val x1 = minOf(width, shape.right.roundToInt())
                val y1 = minOf(height, shape.bottom.roundToInt())
                for (y in y0 until y1) {
                    val rowOffset = y * width
                    for (x in x0 until x1) {
                        destBuffer[rowOffset + x] = 255.toByte()
                    }
                }
            }
            is SelectionShape.Ellipse -> {
                val cx = shape.centerX
                val cy = shape.centerY
                val rx = shape.radiusX
                val ry = shape.radiusY
                if (rx <= 0f || ry <= 0f) return

                val x0 = maxOf(0, floor(cx - rx).toInt())
                val y0 = maxOf(0, floor(cy - ry).toInt())
                val x1 = minOf(width, ceil(cx + rx).toInt())
                val y1 = minOf(height, ceil(cy + ry).toInt())

                val invRx2 = 1.0 / (rx * rx)
                val invRy2 = 1.0 / (ry * ry)

                for (y in y0 until y1) {
                    val dy = y + 0.5 - cy
                    val dyTerm = dy * dy * invRy2
                    val rowOffset = y * width
                    for (x in x0 until x1) {
                        val dx = x + 0.5 - cx
                        if (dx * dx * invRx2 + dyTerm <= 1.0) {
                            destBuffer[rowOffset + x] = 255.toByte()
                        }
                    }
                }
            }
            is SelectionShape.Lasso -> {
                scanlineFillPolygon(shape.points, destBuffer)
            }
            is SelectionShape.Polygon -> {
                scanlineFillPolygon(shape.vertices, destBuffer)
            }
        }
    }

    /**
     * Standard scanline polygon filling algorithm with sub-pixel precision.
     */
    private fun scanlineFillPolygon(vertices: List<com.artstudio.core.CanvasPoint>, destBuffer: ByteArray) {
        if (vertices.size < 3) return

        var minY = height.toFloat()
        var maxY = 0f
        for (v in vertices) {
            if (v.y < minY) minY = v.y
            if (v.y > maxY) maxY = v.y
        }

        val yStart = maxOf(0, floor(minY).toInt())
        val yEnd = minOf(height - 1, ceil(maxY).toInt())
        val n = vertices.size

        for (y in yStart..yEnd) {
            val scanY = y + 0.5f
            val intersections = mutableListOf<Float>()

            for (i in 0 until n) {
                val p1 = vertices[i]
                val p2 = vertices[(i + 1) % n]

                if ((p1.y <= scanY && p2.y > scanY) || (p2.y <= scanY && p1.y > scanY)) {
                    val t = (scanY - p1.y) / (p2.y - p1.y)
                    val x = p1.x + t * (p2.x - p1.x)
                    intersections.add(x)
                }
            }

            intersections.sort()

            var j = 0
            val rowOffset = y * width
            while (j + 1 < intersections.size) {
                val x0 = maxOf(0, floor(intersections[j]).toInt())
                val x1 = minOf(width, ceil(intersections[j + 1]).toInt())
                for (x in x0 until x1) {
                    destBuffer[rowOffset + x] = 255.toByte()
                }
                j += 2
            }
        }
    }

    /**
     * Morphological Dilation (Expand selection boundary).
     *
     * @param radius Expansion radius in pixels in [SelectionLimits.MIN_EXPAND_CONTRACT..SelectionLimits.MAX_EXPAND_CONTRACT].
     */
    fun expand(radius: Int) {
        val r = radius.coerceIn(SelectionLimits.MIN_EXPAND_CONTRACT, SelectionLimits.MAX_EXPAND_CONTRACT)
        val copy = buffer.copyOf()
        val r2 = r * r

        // Precompute circular offsets for exact Euclidean dilation
        val offsets = mutableListOf<Pair<Int, Int>>()
        for (dy in -r..r) {
            for (dx in -r..r) {
                if (dx * dx + dy * dy <= r2) {
                    offsets.add(Pair(dx, dy))
                }
            }
        }

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                var maxVal = 0
                for ((dx, dy) in offsets) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height) {
                        val v = copy[ny * width + nx].toInt() and 0xFF
                        if (v > maxVal) {
                            maxVal = v
                            if (maxVal == 255) break
                        }
                    }
                }
                buffer[rowOffset + x] = maxVal.toByte()
            }
        }
    }

    /**
     * Morphological Erosion (Contract selection boundary).
     *
     * @param radius Contraction radius in pixels in [SelectionLimits.MIN_EXPAND_CONTRACT..SelectionLimits.MAX_EXPAND_CONTRACT].
     */
    fun contract(radius: Int) {
        val r = radius.coerceIn(SelectionLimits.MIN_EXPAND_CONTRACT, SelectionLimits.MAX_EXPAND_CONTRACT)
        val copy = buffer.copyOf()
        val r2 = r * r

        val offsets = mutableListOf<Pair<Int, Int>>()
        for (dy in -r..r) {
            for (dx in -r..r) {
                if (dx * dx + dy * dy <= r2) {
                    offsets.add(Pair(dx, dy))
                }
            }
        }

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                var minVal = 255
                for ((dx, dy) in offsets) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height) {
                        val v = copy[ny * width + nx].toInt() and 0xFF
                        if (v < minVal) {
                            minVal = v
                            if (minVal == 0) break
                        }
                    } else {
                        minVal = 0
                        break
                    }
                }
                buffer[rowOffset + x] = minVal.toByte()
            }
        }
    }

    /**
     * Applies a separable Gaussian/box blur to create a smooth feathered falloff.
     *
     * @param radius Feather radius in pixels in [SelectionLimits.MIN_FEATHER_RADIUS..SelectionLimits.MAX_FEATHER_RADIUS].
     */
    fun feather(radius: Float) {
        val r = radius.coerceIn(SelectionLimits.MIN_FEATHER_RADIUS, SelectionLimits.MAX_FEATHER_RADIUS)
        val intRadius = maxOf(1, round(r).toInt())

        // Pass 1: Horizontal 1D box blur into temp buffer
        val temp = FloatArray(width * height)
        val kernelSize = intRadius * 2 + 1
        val invKernel = 1.0f / kernelSize

        for (y in 0 until height) {
            val rowOffset = y * width
            var windowSum = 0

            // Initialize window sum for x = 0
            for (k in -intRadius..intRadius) {
                val clampedX = k.coerceIn(0, width - 1)
                windowSum += (buffer[rowOffset + clampedX].toInt() and 0xFF)
            }
            temp[rowOffset] = windowSum * invKernel

            for (x in 1 until width) {
                val removeX = (x - intRadius - 1).coerceIn(0, width - 1)
                val addX = (x + intRadius).coerceIn(0, width - 1)
                windowSum += (buffer[rowOffset + addX].toInt() and 0xFF) - (buffer[rowOffset + removeX].toInt() and 0xFF)
                temp[rowOffset + x] = windowSum * invKernel
            }
        }

        // Pass 2: Vertical 1D box blur back into buffer
        for (x in 0 until width) {
            var windowSum = 0f

            for (k in -intRadius..intRadius) {
                val clampedY = k.coerceIn(0, height - 1)
                windowSum += temp[clampedY * width + x]
            }
            buffer[x] = (round(windowSum * invKernel).toInt().coerceIn(0, 255)).toByte()

            for (y in 1 until height) {
                val removeY = (y - intRadius - 1).coerceIn(0, height - 1)
                val addY = (y + intRadius).coerceIn(0, height - 1)
                windowSum += temp[addY * width + x] - temp[removeY * width + x]
                buffer[y * width + x] = (round(windowSum * invKernel).toInt().coerceIn(0, 255)).toByte()
            }
        }
    }

    /**
     * Converts this selection mask into an editable [LayerMask].
     */
    fun toLayerMask(): LayerMask {
        return LayerMask(
            id = UUID.randomUUID().toString(),
            enabled = true,
            inverted = false,
            width = width,
            height = height
        )
    }

    /**
     * Fills color modulated by this selection mask into an ARGB [targetPixels] buffer.
     *
     * @param targetPixels Destination ARGB_8888 buffer of size [width * height].
     * @param fillColor 32-bit ARGB color integer.
     * @param opacity Global fill opacity multiplier in [0.0..1.0].
     */
    fun fill(targetPixels: IntArray, fillColor: Int, opacity: Float = 1.0f) {
        val count = minOf(targetPixels.size, buffer.size)
        val fillA = ((fillColor ushr 24) and 0xFF) / 255.0f
        val fillR = ((fillColor ushr 16) and 0xFF) / 255.0f
        val fillG = ((fillColor ushr 8) and 0xFF) / 255.0f
        val fillB = (fillColor and 0xFF) / 255.0f
        val effOpacity = opacity.coerceIn(0f, 1f)

        for (i in 0 until count) {
            val maskA = ((buffer[i].toInt() and 0xFF) / 255.0f) * effOpacity
            if (maskA <= 0f) continue

            val dst = targetPixels[i]
            val dstA = ((dst ushr 24) and 0xFF) / 255.0f
            val dstR = ((dst ushr 16) and 0xFF) / 255.0f
            val dstG = ((dst ushr 8) and 0xFF) / 255.0f
            val dstB = (dst and 0xFF) / 255.0f

            val srcA = fillA * maskA
            val outA = srcA + dstA * (1.0f - srcA)
            if (outA <= 0f) continue

            val outR = (fillR * srcA + dstR * dstA * (1.0f - srcA)) / outA
            val outG = (fillG * srcA + dstG * dstA * (1.0f - srcA)) / outA
            val outB = (fillB * srcA + dstB * dstA * (1.0f - srcA)) / outA

            val aInt = round(outA * 255f).toInt().coerceIn(0, 255)
            val rInt = round(outR * 255f).toInt().coerceIn(0, 255)
            val gInt = round(outG * 255f).toInt().coerceIn(0, 255)
            val bInt = round(outB * 255f).toInt().coerceIn(0, 255)

            targetPixels[i] = (aInt shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
        }
    }

    /**
     * Converts the selection mask shapes into a composite Android [Path].
     */
    fun toPath(): Path {
        val compositePath = Path()
        for (shape in _shapes) {
            compositePath.addPath(shape.toPath())
        }
        return compositePath
    }

    /**
     * Deep clones this selection mask.
     */
    fun clone(): SelectionMask {
        val cloned = SelectionMask(width, height, buffer)
        cloned._shapes.addAll(_shapes)
        return cloned
    }
}
