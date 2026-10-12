package com.artstudio.color

import com.artstudio.core.DocumentRasterCompositor
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Eyedropper sampling radius mode.
 */
enum class EyedropperSampleMode(val radius: Int, val displayName: String) {
    POINT_1X1(0, "1×1 Point Sample"),
    AVERAGE_3X3(1, "3×3 Average"),
    AVERAGE_5X5(2, "5×5 Average")
}

/**
 * Inspection data for floating loupe / magnifier overlay during eyedropper drag.
 */
data class LoupeData(
    val centerX: Int,
    val centerY: Int,
    val centerColor: ColorData,
    val gridSize: Int,
    val gridColors: List<ColorData> // gridSize x gridSize row-major
)

/**
 * Authoritative color sampling engine across raster layers and documents.
 */
object EyedropperEngine {

    /**
     * Samples color from a raw 32-bit ARGB pixel array.
     */
    fun samplePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        mode: EyedropperSampleMode = EyedropperSampleMode.POINT_1X1
    ): ColorData {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return ColorData.BLACK
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)

        val radius = mode.radius
        if (radius == 0) {
            val p = pixels[cy * width + cx]
            return ColorData.fromArgb(p)
        }

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var sumWeight = 0f
        var maxAlpha = 0f

        for (dy in -radius..radius) {
            val sy = (cy + dy).coerceIn(0, height - 1)
            val rowOffset = sy * width
            for (dx in -radius..radius) {
                val sx = (cx + dx).coerceIn(0, width - 1)
                val p = pixels[rowOffset + sx]
                val a = ((p ushr 24) and 0xFF) / 255f
                val r = ((p ushr 16) and 0xFF) / 255f
                val g = ((p ushr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f

                if (a > maxAlpha) maxAlpha = a
                // Weight color channels by alpha to avoid transparent black polluting average
                val weight = max(a, 0.001f)
                sumR += r * weight
                sumG += g * weight
                sumB += b * weight
                sumWeight += weight
            }
        }

        if (sumWeight <= 0f) return ColorData.TRANSPARENT
        val avgR = (sumR / sumWeight).coerceIn(0f, 1f)
        val avgG = (sumG / sumWeight).coerceIn(0f, 1f)
        val avgB = (sumB / sumWeight).coerceIn(0f, 1f)
        return ColorData(avgR, avgG, avgB, maxAlpha)
    }

    /**
     * Samples color from an ArtDocument or provided composite buffer.
     */
    fun sampleDocument(
        document: ArtDocument,
        x: Int,
        y: Int,
        mode: EyedropperSampleMode = EyedropperSampleMode.POINT_1X1,
        compositeBuffer: IntArray? = null
    ): ColorData {
        val w = document.canvas.width
        val h = document.canvas.height
        if (w <= 0 || h <= 0) return ColorData.BLACK

        if (compositeBuffer != null) {
            return samplePixels(compositeBuffer, w, h, x, y, mode)
        }

        return ColorData.fromArgb(document.canvas.backgroundColor.toInt())
    }

    /**
     * Generates loupe preview data around (centerX, centerY).
     */
    fun createLoupeData(
        pixels: IntArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        gridSize: Int = 9
    ): LoupeData {
        require(gridSize % 2 == 1) { "gridSize must be odd, got $gridSize" }
        val half = gridSize / 2
        val grid = mutableListOf<ColorData>()
        val centerColor = samplePixels(pixels, width, height, centerX, centerY, EyedropperSampleMode.POINT_1X1)

        for (dy in -half..half) {
            val y = (centerY + dy).coerceIn(0, height - 1)
            val rowOffset = y * width
            for (dx in -half..half) {
                val x = (centerX + dx).coerceIn(0, width - 1)
                val p = pixels[rowOffset + x]
                grid.add(ColorData.fromArgb(p))
            }
        }

        return LoupeData(
            centerX = centerX,
            centerY = centerY,
            centerColor = centerColor,
            gridSize = gridSize,
            gridColors = grid
        )
    }
}
