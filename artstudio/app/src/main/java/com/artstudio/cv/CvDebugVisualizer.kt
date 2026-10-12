package com.artstudio.cv

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Non-destructive debug visualization renderer.
 * Produces isolated debug overlay bitmaps for visual auditing without modifying the artwork.
 */
object CvDebugVisualizer {

    private val CONTOUR_COLORS = intArrayOf(
        0xFF00E5FF.toInt(), // Vibrant Cyan
        0xFFFF9100.toInt(), // Vibrant Orange
        0xFFFFD600.toInt(), // Bright Yellow
        0xFFD500F9.toInt(), // Deep Magenta
        0xFF00E676.toInt(), // Emerald Green
        0xFFFF1744.toInt()  // Hot Pink
    )

    /**
     * Renders a standalone ARGB debug overlay image. Never mutates [originalPixels].
     */
    fun renderDebugOverlay(
        result: CvPipelineResult,
        originalPixels: IntArray,
        mode: CvDebugOverlayMode = CvDebugOverlayMode.EDGE_MAP
    ): IntArray {
        val width = result.width
        val height = result.height
        val total = width * height
        val output = IntArray(total)

        when (mode) {
            CvDebugOverlayMode.EDGE_MAP -> {
                // Dimmed original + bright green edges
                val edgeColor = 0xFF00FF66.toInt()
                for (i in 0 until total) {
                    if (result.edges[i] == 255) {
                        output[i] = edgeColor
                    } else {
                        output[i] = dimPixel(originalPixels[i], factor = 0.45f)
                    }
                }
            }

            CvDebugOverlayMode.CONTOUR_POLYGONS -> {
                // Dimmed original background
                for (i in 0 until total) {
                    output[i] = dimPixel(originalPixels[i], factor = 0.35f)
                }

                // Draw each contour with a distinct cycling color
                for (cIdx in result.contours.indices) {
                    val contour = result.contours[cIdx]
                    val color = CONTOUR_COLORS[cIdx % CONTOUR_COLORS.size]
                    val pts = contour.simplifiedPoints

                    if (pts.size >= 2) {
                        for (i in pts.indices) {
                            val p1 = pts[i]
                            val p2 = pts[(i + 1) % pts.size]
                            drawLine(output, width, height, p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), color)
                            drawDot(output, width, height, p1.x.toInt(), p1.y.toInt(), 0xFFFFFFFF.toInt())
                        }
                    }
                }
            }

            CvDebugOverlayMode.BINARY_THRESHOLD -> {
                for (i in 0 until total) {
                    val v = result.thresholded[i]
                    output[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
            }

            CvDebugOverlayMode.DENOISED_PREVIEW -> {
                for (i in 0 until total) {
                    val v = result.denoised[i]
                    output[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
            }

            CvDebugOverlayMode.CENTRELINE_SKELETON -> {
                // Dimmed original background
                for (i in 0 until total) {
                    output[i] = dimPixel(originalPixels[i], factor = 0.35f)
                }

                val cRes = result.centrelineResult
                if (cRes != null) {
                    // 1. Draw paths with cycling vibrant colors
                    val pathColor = 0xFF00E5FF.toInt() // Cyan
                    for (cIdx in cRes.paths.indices) {
                        val path = cRes.paths[cIdx]
                        val color = CONTOUR_COLORS[cIdx % CONTOUR_COLORS.size]
                        val pts = path.simplifiedPoints
                        if (pts.size >= 2) {
                            for (i in 0 until pts.size - 1) {
                                val p1 = pts[i]
                                val p2 = pts[i + 1]
                                drawLine(output, width, height, p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), color)
                                drawDot(output, width, height, p1.x.toInt(), p1.y.toInt(), 0xFFFFFFFF.toInt())
                            }
                            drawDot(output, width, height, pts.last().x.toInt(), pts.last().y.toInt(), 0xFFFFFFFF.toInt())
                        }
                    }

                    // 2. Draw junction nodes as bright yellow markers
                    val juncColor = 0xFFFFD600.toInt() // Bright Yellow
                    for (junc in cRes.junctions) {
                        drawCircleMarker(output, width, height, junc.location.x.toInt(), junc.location.y.toInt(), 3, juncColor)
                    }

                    // 3. Highlight ambiguities as hot pink warning rings
                    val ambigColor = 0xFFFF1744.toInt() // Hot Pink
                    for (ambig in cRes.ambiguities) {
                        drawCircleMarker(output, width, height, ambig.location.x.toInt(), ambig.location.y.toInt(), 5, ambigColor)
                    }
                }
            }

            CvDebugOverlayMode.FITTED_PATHS -> {
                // Dimmed original background
                for (i in 0 until total) {
                    output[i] = dimPixel(originalPixels[i], factor = 0.35f)
                }

                val vRes = result.vectorizationResult
                if (vRes != null) {
                    for (pIdx in vRes.paths.indices) {
                        val path = vRes.paths[pIdx]
                        val pathColor = if (path.isHole) {
                            0xFFFFD600.toInt() // Bright Yellow for inner holes
                        } else {
                            CONTOUR_COLORS[pIdx % CONTOUR_COLORS.size]
                        }

                        val pts = com.artstudio.cv.pathfit.ContourRasterComparator.sampleVectorObjectPoints(
                            path.toVectorObject(),
                            samplesPerSegment = 15
                        )

                        if (pts.size >= 2) {
                            for (i in 0 until pts.size - 1) {
                                val p1 = pts[i]
                                val p2 = pts[i + 1]
                                drawLine(output, width, height, p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), pathColor)
                            }
                        }

                        // Draw corner anchors in bright red/orange
                        val cornerColor = 0xFFFF1744.toInt()
                        for (c in path.cornersDetected) {
                            drawCircleMarker(output, width, height, c.x.toInt(), c.y.toInt(), 3, cornerColor)
                        }

                        // Draw smooth anchors in white
                        val anchorColor = 0xFFFFFFFF.toInt()
                        for (a in path.anchors) {
                            drawDot(output, width, height, a.x.toInt(), a.y.toInt(), anchorColor)
                        }
                    }
                }
            }

            CvDebugOverlayMode.REGION_MASKS -> {
                // Dimmed original background
                for (i in 0 until total) {
                    output[i] = dimPixel(originalPixels[i], factor = 0.35f)
                }

                // Segment connected regions across image and tint each with cycling vibrant colors
                val regions = com.artstudio.cv.region.RegionMaskEngine.segmentAllRegions(
                    originalPixels,
                    width,
                    height,
                    com.artstudio.cv.region.RegionMaskConfig(gapClosingRadius = 1, detectLeaks = false)
                )

                for (rIdx in regions.indices) {
                    val reg = regions[rIdx]
                    val color = CONTOUR_COLORS[rIdx % CONTOUR_COLORS.size]
                    val mask = reg.correctedMask
                    for (i in 0 until total) {
                        if ((mask[i].toInt() and 0xFF) > 128) {
                            output[i] = color
                        }
                    }
                }
            }
        }

        return output
    }

    private fun dimPixel(argb: Int, factor: Float): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (((argb ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun drawLine(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        color: Int
    ) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy

        while (true) {
            if (x in 0 until width && y in 0 until height) {
                pixels[y * width + x] = color
            }
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }

    private fun drawDot(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Int,
        cy: Int,
        color: Int
    ) {
        for (dy in -1..1) {
            for (dx in -1..1) {
                val px = cx + dx
                val py = cy + dy
                if (px in 0 until width && py in 0 until height) {
                    pixels[py * width + px] = color
                }
            }
        }
    }

    private fun drawCircleMarker(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Int,
        cy: Int,
        radius: Int,
        color: Int
    ) {
        val r2 = radius * radius
        val innerR2 = (radius - 1) * (radius - 1)
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val d2 = dx * dx + dy * dy
                if (d2 <= r2 && (radius <= 2 || d2 >= innerR2)) {
                    val px = cx + dx
                    val py = cy + dy
                    if (px in 0 until width && py in 0 until height) {
                        pixels[py * width + px] = color
                    }
                }
            }
        }
    }
}
