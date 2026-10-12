package com.artstudio.cv.review

import com.artstudio.cv.CvPoint
import com.artstudio.cv.pathfit.FittedPathCandidate
import com.artstudio.cv.region.RegionMask
import kotlin.math.*

/**
 * Visual inspection renderer supporting path/region/colour overlays, split comparison,
 * alpha blend compositing, and false-color difference heatmaps.
 */
object DetectionOverlayRenderer {

    /**
     * Palette of distinct translucent ARGB colors for region overlays.
     */
    private val REGION_COLORS = intArrayOf(
        0x66FF5722.toInt(), // Deep Orange
        0x664CAF50.toInt(), // Green
        0x662196F3.toInt(), // Blue
        0x669C27B0.toInt(), // Purple
        0x66FFEB3B.toInt(), // Yellow
        0x6600BCD4.toInt(), // Cyan
        0x66E91E63.toInt()  // Pink
    )

    /**
     * Renders an interactive inspection preview buffer according to [mode].
     *
     * @param sourcePixels 32-bit ARGB reference image buffer.
     * @param width Canvas width.
     * @param height Canvas height.
     * @param paths Active vector paths.
     * @param regions Active region masks.
     * @param mode Selected display mode.
     * @param splitRatio Slider split ratio [0.0, 1.0] for [OverlayDisplayMode.SPLIT_COMPARE].
     * @param blendAlpha Opacity weight [0.0, 1.0] for [OverlayDisplayMode.ALPHA_BLEND].
     * @return 32-bit ARGB rendered pixel buffer.
     */
    fun render(
        sourcePixels: IntArray,
        width: Int,
        height: Int,
        paths: List<FittedPathCandidate>,
        regions: List<RegionMask>,
        mode: OverlayDisplayMode = OverlayDisplayMode.SPLIT_COMPARE,
        splitRatio: Float = 0.5f,
        blendAlpha: Float = 0.5f
    ): IntArray {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(sourcePixels.size == width * height) { "Buffer size does not match dimensions" }

        val total = width * height

        return when (mode) {
            OverlayDisplayMode.SOURCE_ONLY -> sourcePixels.clone()

            OverlayDisplayMode.DETECTION_ONLY -> {
                val output = IntArray(total) { 0xFFFFFFFF.toInt() } // White background
                rasterizeRegions(output, width, height, regions)
                rasterizePaths(output, width, height, paths)
                output
            }

            OverlayDisplayMode.SPLIT_COMPARE -> {
                val output = sourcePixels.clone()
                val detection = IntArray(total) { 0xFFFFFFFF.toInt() }
                rasterizeRegions(detection, width, height, regions)
                rasterizePaths(detection, width, height, paths)

                val splitX = (width * splitRatio.coerceIn(0f, 1f)).roundToInt().coerceIn(0, width - 1)
                for (y in 0 until height) {
                    for (x in splitX until width) {
                        output[y * width + x] = detection[y * width + x]
                    }
                    // Draw a 1-px vertical dividing guide line
                    output[y * width + splitX] = 0xFF00E5FF.toInt() // Cyan divider
                }
                output
            }

            OverlayDisplayMode.ALPHA_BLEND -> {
                val output = sourcePixels.clone()
                val detection = IntArray(total) { 0xFFFFFFFF.toInt() }
                rasterizeRegions(detection, width, height, regions)
                rasterizePaths(detection, width, height, paths)

                val alphaF = blendAlpha.coerceIn(0f, 1f)
                val invAlpha = 1.0f - alphaF

                for (i in 0 until total) {
                    val s = output[i]
                    val d = detection[i]

                    val sr = (s ushr 16) and 0xFF
                    val sg = (s ushr 8) and 0xFF
                    val sb = s and 0xFF

                    val dr = (d ushr 16) and 0xFF
                    val dg = (d ushr 8) and 0xFF
                    val db = d and 0xFF

                    val r = (sr * invAlpha + dr * alphaF).roundToInt().coerceIn(0, 255)
                    val g = (sg * invAlpha + dg * alphaF).roundToInt().coerceIn(0, 255)
                    val b = (sb * invAlpha + db * alphaF).roundToInt().coerceIn(0, 255)

                    output[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                output
            }

            OverlayDisplayMode.DIFFERENCE_HEATMAP -> {
                val output = IntArray(total)
                val detection = IntArray(total) { 0xFFFFFFFF.toInt() }
                rasterizeRegions(detection, width, height, regions)
                rasterizePaths(detection, width, height, paths)

                for (i in 0 until total) {
                    val s = sourcePixels[i]
                    val d = detection[i]

                    val sr = (s ushr 16) and 0xFF
                    val sg = (s ushr 8) and 0xFF
                    val sb = s and 0xFF

                    val dr = (d ushr 16) and 0xFF
                    val dg = (d ushr 8) and 0xFF
                    val db = d and 0xFF

                    val deltaR = abs(sr - dr)
                    val deltaG = abs(sg - dg)
                    val deltaB = abs(sb - db)
                    val meanDelta = ((deltaR + deltaG + deltaB) / 3f) / 255f

                    // Map delta to heatmap: Green (low delta) -> Yellow -> Red (high delta)
                    val heatR = (meanDelta * 2f).coerceIn(0f, 1f) * 255f
                    val heatG = ((1f - meanDelta) * 2f).coerceIn(0f, 1f) * 255f
                    val heatB = 0f

                    output[i] = (0xFF shl 24) or (heatR.toInt() shl 16) or (heatG.toInt() shl 8) or heatB.toInt()
                }
                output
            }
        }
    }

    /**
     * Rasterizes colored region masks onto the target pixel buffer with translucent alpha blending.
     */
    private fun rasterizeRegions(
        target: IntArray,
        width: Int,
        height: Int,
        regions: List<RegionMask>
    ) {
        for ((idx, region) in regions.withIndex()) {
            val color = REGION_COLORS[idx % REGION_COLORS.size]
            val rColor = (color ushr 16) and 0xFF
            val gColor = (color ushr 8) and 0xFF
            val bColor = color and 0xFF
            val alphaF = ((color ushr 24) and 0xFF) / 255.0f

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val maskVal = (region.mask[y * width + x].toInt() and 0xFF)
                    if (maskVal > 128) {
                        val base = target[y * width + x]
                        val br = (base ushr 16) and 0xFF
                        val bg = (base ushr 8) and 0xFF
                        val bb = base and 0xFF

                        val r = (br * (1f - alphaF) + rColor * alphaF).roundToInt().coerceIn(0, 255)
                        val g = (bg * (1f - alphaF) + gColor * alphaF).roundToInt().coerceIn(0, 255)
                        val b = (bb * (1f - alphaF) + bColor * alphaF).roundToInt().coerceIn(0, 255)

                        target[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
    }

    /**
     * Rasterizes vector paths as solid black 2-pixel strokes onto target buffer.
     */
    private fun rasterizePaths(
        target: IntArray,
        width: Int,
        height: Int,
        paths: List<FittedPathCandidate>
    ) {
        val strokeColor = 0xFF000000.toInt()
        for (path in paths) {
            val anchors = path.anchors
            if (anchors.size < 2) continue

            for (i in 0 until anchors.size - 1) {
                drawLine(
                    target = target,
                    width = width,
                    height = height,
                    x0 = anchors[i].x.roundToInt(),
                    y0 = anchors[i].y.roundToInt(),
                    x1 = anchors[i + 1].x.roundToInt(),
                    y1 = anchors[i + 1].y.roundToInt(),
                    color = strokeColor
                )
            }
            if (path.isClosed && anchors.size >= 3) {
                drawLine(
                    target = target,
                    width = width,
                    height = height,
                    x0 = anchors.last().x.roundToInt(),
                    y0 = anchors.last().y.roundToInt(),
                    x1 = anchors.first().x.roundToInt(),
                    y1 = anchors.first().y.roundToInt(),
                    color = strokeColor
                )
            }
        }
    }

    /**
     * Bresenham line rasterizer with 2-pixel stroke thickness.
     */
    private fun drawLine(
        target: IntArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        color: Int
    ) {
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        var cx = x0
        var cy = y0

        while (true) {
            if (cx in 0 until width && cy in 0 until height) {
                target[cy * width + cx] = color
                // Draw 1px cross for thickness
                if (cx + 1 < width) target[cy * width + cx + 1] = color
                if (cy + 1 < height) target[(cy + 1) * width + cx] = color
            }

            if (cx == x1 && cy == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                cx += sx
            }
            if (e2 < dx) {
                err += dx
                cy += sy
            }
        }
    }
}
