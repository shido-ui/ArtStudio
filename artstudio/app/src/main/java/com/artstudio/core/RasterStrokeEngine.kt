package com.artstudio.core

import android.graphics.*
import kotlin.math.*

/**
 * Shared raster stroke execution engine for the 7 core tools:
 * Pencil, Hard Pen, Soft Pen, Ink Brush, Basic Paintbrush, Airbrush, and Eraser.
 *
 * Implements:
 * - Sub-pixel dab interpolation with configurable spacing.
 * - Pressure-scaled radii and opacity modulation.
 * - Deterministic textured graphite noise (Pencil) with fixed seed.
 * - Feathered radial falloffs (Soft Pen, Airbrush).
 * - Multi-filament bristle tracking (Basic Paintbrush).
 * - Calligraphic dynamic ribbon (Ink Brush).
 * - PorterDuff.Mode.CLEAR pixel erasure (Eraser).
 * - High-speed incremental rendering for active in-flight strokes.
 */
class RasterStrokeEngine {

    private val dabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val scratchRect = RectF()
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scratchStampDst = RectF()
    private val stampCache = mutableMapOf<String, Bitmap>()

    fun getOrCreateStamp(config: BrushConfig): Bitmap? {
        val key = when {
            config.mediaProperties.family == PaintMediaFamily.OIL_PAINT ->
                "oil_${config.seed}_${config.mediaProperties.bristleDensity}_${config.mediaProperties.impastoDepth}"
            config.type == BrushType.SOFT_PEN -> "soft_pen"
            config.type == BrushType.AIRBRUSH -> "airbrush"
            config.type == BrushType.PENCIL -> "pencil_${config.seed}"
            config.type == BrushType.BASIC_PAINTBRUSH -> "brush_${config.seed}"
            else -> return null
        }
        val cached = stampCache[key]
        if (cached != null) return cached

        val size = 64
        val bmp = try {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            null
        } ?: return null

        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val center = size / 2f
        val rad = size / 2.2f

        when {
            config.mediaProperties.family == PaintMediaFamily.OIL_PAINT -> {
                val media = config.mediaProperties
                val bristles = media.bristleDensity.coerceIn(4, 48)
                var rng = config.seed.toInt()
                val bristleRadius = max(1.2f, rad * (2.2f / bristles))
                for (b in 0 until bristles) {
                    rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
                    val randVal = (rng and 0xFFFF) / 65535.0f
                    val k = -1.0f + 2.0f * (b.toFloat() / (bristles - 1).toFloat())
                    val jitter = (randVal - 0.5f) * 0.12f * rad
                    val offset = (k * rad) + jitter
                    val ridgeMod = if (b % 2 == 0) 1.0f + 0.20f * media.impastoDepth else 1.0f - 0.25f * media.impastoDepth
                    val bAlpha = (255 * media.viscosity * media.paintLoading * ridgeMod).toInt().coerceIn(1, 255)
                    p.alpha = bAlpha
                    c.drawCircle(center + offset, center, bristleRadius, p)
                }
            }
            config.type == BrushType.SOFT_PEN -> {
                p.alpha = (255 * 0.20f).toInt()
                c.drawCircle(center, center, rad, p)
                p.alpha = (255 * 0.55f).toInt()
                c.drawCircle(center, center, rad * 0.70f, p)
                p.alpha = 255
                c.drawCircle(center, center, rad * 0.40f, p)
            }
            config.type == BrushType.AIRBRUSH -> {
                p.alpha = (255 * 0.10f).toInt()
                c.drawCircle(center, center, rad, p)
                p.alpha = (255 * 0.35f).toInt()
                c.drawCircle(center, center, rad * 0.70f, p)
                p.alpha = (255 * 0.70f).toInt()
                c.drawCircle(center, center, rad * 0.40f, p)
                p.alpha = 255
                c.drawCircle(center, center, rad * 0.20f, p)
            }
            config.type == BrushType.PENCIL -> {
                var rng = config.seed.toInt()
                for (i in 0 until 18) {
                    rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
                    val r1 = (rng and 0xFFFF) / 65535.0f
                    rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
                    val r2 = (rng and 0xFFFF) / 65535.0f
                    val rNorm = sqrt(r1) * rad
                    val theta = r2 * (2.0f * PI.toFloat())
                    val px = center + rNorm * cos(theta)
                    val py = center + rNorm * sin(theta)
                    p.alpha = (255 * (0.25f + r1 * 0.5f)).toInt().coerceIn(0, 255)
                    c.drawCircle(px, py, 1.2f, p)
                }
            }
            config.type == BrushType.BASIC_PAINTBRUSH -> {
                val bristleCount = 10
                var rng = config.seed.toInt()
                for (b in 0 until bristleCount) {
                    rng = (rng * 1664525 + 1013904223) and 0x7FFFFFFF
                    val randVal = (rng and 0xFFFF) / 65535.0f
                    val k = -1.0f + 2.0f * (b.toFloat() / (bristleCount - 1).toFloat())
                    val jitter = (randVal - 0.5f) * 0.20f * rad
                    val offset = (k * rad) + jitter
                    p.alpha = (255 * 0.75f).toInt()
                    c.drawCircle(center + offset, center, max(1.0f, rad * 0.22f), p)
                }
            }
        }

        if (stampCache.size > 32) {
            stampCache.clear()
        }
        stampCache[key] = bmp
        return bmp
    }

    fun renderStrokeSoftware(pixels: IntArray, width: Int, height: Int, stroke: BrushStroke) {
        val color = if (stroke.config.type == BrushType.ERASER) 0 else stroke.color
        val baseRadius = (stroke.baseSize / 2f).coerceAtLeast(1f)
        val pts = stroke.points
        if (pts.isEmpty()) return

        val srcA = (((color ushr 24) and 0xFF) * stroke.config.opacity).roundToInt().coerceIn(0, 255)
        val srcR = (color ushr 16) and 0xFF
        val srcG = (color ushr 8) and 0xFF
        val srcB = color and 0xFF
        val srcColor = (srcA shl 24) or (srcR shl 16) or (srcG shl 8) or srcB

        fun stampDab(cx: Float, cy: Float, pressure: Float) {
            val r = (baseRadius * pressure).toInt().coerceAtLeast(1)
            val minX = (cx - r).toInt().coerceIn(0, width - 1)
            val maxX = (cx + r).toInt().coerceIn(0, width - 1)
            val minY = (cy - r).toInt().coerceIn(0, height - 1)
            val maxY = (cy + r).toInt().coerceIn(0, height - 1)
            val r2 = r * r

            if (stroke.config.type == BrushType.ERASER) {
                for (y in minY..maxY) {
                    val dy = y - cy
                    val rowOffset = y * width
                    for (x in minX..maxX) {
                        val dx = x - cx
                        if (dx * dx + dy * dy <= r2) {
                            pixels[rowOffset + x] = 0
                        }
                    }
                }
            } else if (srcA == 255) {
                for (y in minY..maxY) {
                    val dy = y - cy
                    val rowOffset = y * width
                    for (x in minX..maxX) {
                        val dx = x - cx
                        if (dx * dx + dy * dy <= r2) {
                            pixels[rowOffset + x] = srcColor
                        }
                    }
                }
            } else if (srcA > 0) {
                for (y in minY..maxY) {
                    val dy = y - cy
                    val rowOffset = y * width
                    for (x in minX..maxX) {
                        val dx = x - cx
                        if (dx * dx + dy * dy <= r2) {
                            val idx = rowOffset + x
                            pixels[idx] = blendSrcOver(pixels[idx], srcColor)
                        }
                    }
                }
            }
        }

        if (pts.size == 1) {
            stampDab(pts[0].x, pts[0].y, pts[0].pressure)
            return
        }

        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val dist = hypot(dx, dy)
            val step = max(0.5f, baseRadius * 0.4f)
            val steps = max(1, (dist / step).toInt())
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val x = p0.x + dx * t
                val y = p0.y + dy * t
                val pressure = p0.pressure + (p1.pressure - p0.pressure) * t
                stampDab(x, y, pressure)
            }
        }
    }

    private fun blendSrcOver(dst: Int, src: Int): Int {
        val sa = (src ushr 24) and 0xFF
        if (sa == 0) return dst
        if (sa == 255) return src

        val da = (dst ushr 24) and 0xFF
        val invSa = 255 - sa
        val outA = sa + (da * invSa) / 255
        if (outA == 0) return 0

        val sr = (src ushr 16) and 0xFF
        val sg = (src ushr 8) and 0xFF
        val sb = src and 0xFF

        val dr = (dst ushr 16) and 0xFF
        val dg = (dst ushr 8) and 0xFF
        val db = dst and 0xFF

        val outR = (sr * sa + dr * da * invSa / 255) / outA
        val outG = (sg * sa + dg * da * invSa / 255) / outA
        val outB = (sb * sa + db * da * invSa / 255) / outA

        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    /**
     * Renders an entire BrushStroke onto the given Canvas.
     */
    fun renderStroke(canvas: Canvas, stroke: BrushStroke) {
        val points = stroke.points
        if (points.isEmpty()) return

        val config = stroke.config

        if (points.size == 1) {
            renderDab(canvas, points[0].x, points[0].y, points[0].pressure, 0f, 0, config, 1.0f)
            return
        }

        var dabCounter = 0
        val totalSegments = max(1, points.size - 1)
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val segStartU = (i - 1).toFloat() / totalSegments.toFloat()
            val segEndU = i.toFloat() / totalSegments.toFloat()
            dabCounter = renderSegment(canvas, p0, p1, dabCounter, config, segStartU, segEndU)
        }
    }

    /**
     * Incremental renderer: renders only the new points added to the stroke
     * between [fromPointIndex] and the end of the stroke.
     */
    fun renderIncremental(
        canvas: Canvas,
        stroke: BrushStroke,
        fromPointIndex: Int
    ): Int {
        val points = stroke.points
        if (points.isEmpty()) return 0

        val config = stroke.config
        val startIdx = max(1, fromPointIndex)
        var dabCounter = startIdx * 10

        if (points.size == 1 && fromPointIndex == 0) {
            renderDab(canvas, points[0].x, points[0].y, points[0].pressure, 0f, 0, config, 1.0f)
            return 1
        }

        val totalSegments = max(1, points.size - 1)
        for (i in startIdx until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val segStartU = (i - 1).toFloat() / totalSegments.toFloat()
            val segEndU = i.toFloat() / totalSegments.toFloat()
            dabCounter = renderSegment(canvas, p0, p1, dabCounter, config, segStartU, segEndU)
        }
        return dabCounter
    }

    private fun calculateTaperFactor(u: Float, config: BrushConfig): Float {
        var factor = 1.0f
        if (config.startTaperRatio > 0.001f && u < config.startTaperRatio) {
            factor = min(factor, u / config.startTaperRatio)
        }
        if (config.endTaperRatio > 0.001f && (1.0f - u) < config.endTaperRatio) {
            factor = min(factor, (1.0f - u) / config.endTaperRatio)
        }
        return factor.coerceIn(0.01f, 1.0f)
    }

    private fun renderSegment(
        canvas: Canvas,
        p0: StrokePoint,
        p1: StrokePoint,
        startDabCounter: Int,
        config: BrushConfig,
        segStartU: Float = 0f,
        segEndU: Float = 1f
    ): Int {
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val dist = sqrt(dx * dx + dy * dy)
        val tangentAngle = if (config.followStrokeAngle || config.type == BrushType.INK_BRUSH || config.type == BrushType.BASIC_PAINTBRUSH || config.tipShape != BrushTipShape.ROUND) {
            atan2(dy.toDouble(), dx.toDouble()).toFloat()
        } else 0f

        val avgPressure = (p0.pressure + p1.pressure) / 2f
        val currentRadius = computeRadius(config, avgPressure)
        val spacingDist = max(1.0f, currentRadius * 2f * config.spacing)

        val steps = max(1, ceil(dist / spacingDist).toInt())
        var dabCounter = startDabCounter

        for (s in 0..steps) {
            val t = s.toFloat() / steps.toFloat()
            val x = p0.x + t * dx
            val y = p0.y + t * dy
            val p = p0.pressure + t * (p1.pressure - p0.pressure)
            val u = segStartU + t * (segEndU - segStartU)
            val taperFactor = calculateTaperFactor(u, config)

            renderDab(canvas, x, y, p, tangentAngle, dabCounter++, config, taperFactor)
        }

        // For Hard Pen and Ink Brush in standard mode, draw connecting line segment to guarantee zero gaps
        if (config.mediaProperties.family == PaintMediaFamily.STANDARD &&
            (config.type == BrushType.HARD_PEN || config.type == BrushType.INK_BRUSH)) {
            linePaint.color = config.color
            linePaint.alpha = (255 * config.opacity).toInt().coerceIn(0, 255)
            linePaint.strokeWidth = currentRadius * 2f
            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, linePaint)
        }

        return dabCounter
    }

    private fun renderDab(
        canvas: Canvas,
        x: Float,
        y: Float,
        pressure: Float,
        angle: Float,
        dabIndex: Int,
        config: BrushConfig,
        taperFactor: Float = 1.0f
    ) {
        val radius = computeRadius(config, pressure) * taperFactor
        val baseAlpha = (255 * config.opacity * taperFactor).toInt().coerceIn(0, 255)

        // 1. Paint Media Subsystem Dispatch (Prompt 24)
        if (config.mediaProperties.family != PaintMediaFamily.STANDARD) {
            renderPaintMediaDab(canvas, x, y, pressure, angle, dabIndex, config, radius, baseAlpha)
            return
        }

        // Custom tip shapes
        if (config.tipShape == BrushTipShape.SQUARE) {
            canvas.save()
            canvas.translate(x, y)
            val rot = config.tipAngleDegrees + (if (config.followStrokeAngle) (angle * 180f / PI.toFloat()) else 0f)
            canvas.rotate(rot)
            dabPaint.color = config.color
            dabPaint.alpha = baseAlpha
            canvas.drawRect(-radius, -radius, radius, radius, dabPaint)
            canvas.restore()
            return
        }

        if (config.tipShape == BrushTipShape.CHISEL) {
            canvas.save()
            canvas.translate(x, y)
            val rot = config.tipAngleDegrees + (if (config.followStrokeAngle) (angle * 180f / PI.toFloat()) else 0f)
            canvas.rotate(rot)
            dabPaint.color = config.color
            dabPaint.alpha = baseAlpha
            canvas.drawRect(-radius * 1.5f, -radius * 0.35f, radius * 1.5f, radius * 0.35f, dabPaint)
            canvas.restore()
            return
        }

        if (config.tipShape == BrushTipShape.ELLIPSE) {
            canvas.save()
            canvas.translate(x, y)
            val rot = config.tipAngleDegrees + (if (config.followStrokeAngle) (angle * 180f / PI.toFloat()) else 0f)
            canvas.rotate(rot)
            dabPaint.color = config.color
            dabPaint.alpha = baseAlpha
            scratchRect.set(-radius * 1.35f, -radius * 0.65f, radius * 1.35f, radius * 0.65f)
            canvas.drawOval(scratchRect, dabPaint)
            canvas.restore()
            return
        }

        val stamp = getOrCreateStamp(config)
        if (stamp != null) {
            scratchStampDst.set(x - radius, y - radius, x + radius, y + radius)
            stampPaint.colorFilter = PorterDuffColorFilter(config.color, PorterDuff.Mode.SRC_IN)
            stampPaint.alpha = baseAlpha
            if (angle != 0f && (config.type == BrushType.BASIC_PAINTBRUSH || config.followStrokeAngle)) {
                canvas.save()
                canvas.rotate(angle * 180f / PI.toFloat(), x, y)
                canvas.drawBitmap(stamp, null, scratchStampDst, stampPaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(stamp, null, scratchStampDst, stampPaint)
            }
            return
        }

        when (config.type) {
            BrushType.HARD_PEN -> {
                dabPaint.color = config.color
                if (config.hardness >= 0.99f) {
                    dabPaint.alpha = baseAlpha
                    canvas.drawCircle(x, y, radius, dabPaint)
                } else {
                    // Soft feathered edge based on hardness
                    dabPaint.alpha = (baseAlpha * (0.3f + 0.7f * config.hardness)).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x, y, radius, dabPaint)
                    dabPaint.alpha = baseAlpha
                    canvas.drawCircle(x, y, radius * config.hardness, dabPaint)
                }
            }

            BrushType.SOFT_PEN -> {
                // 3-ring feathered radial falloff
                val color = config.color
                dabPaint.color = color

                // Outer feathered ring
                dabPaint.alpha = (baseAlpha * 0.20f).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, radius, dabPaint)

                // Mid feathered ring
                dabPaint.alpha = (baseAlpha * 0.55f).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, radius * 0.70f, dabPaint)

                // Solid core
                dabPaint.alpha = baseAlpha
                canvas.drawCircle(x, y, radius * 0.40f, dabPaint)
            }

            BrushType.PENCIL -> {
                // Graphite tooth: deterministic pseudo-random particle cluster
                val seed = config.seed xor (dabIndex.toLong() * 2654435761L)
                var rng = seed.toInt()

                fun nextRand(): Float {
                    rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
                    return (rng and 0xFFFF) / 65535.0f
                }

                dabPaint.color = config.color
                val particleCount = (8 + (pressure * 20f)).toInt()

                for (i in 0 until particleCount) {
                    val rNorm = sqrt(nextRand()) * radius
                    val theta = nextRand() * (2.0f * PI.toFloat())
                    val px = x + rNorm * cos(theta)
                    val py = y + rNorm * sin(theta)
                    val pRadius = 0.8f + nextRand() * 1.2f
                    val pAlpha = (baseAlpha * (0.15f + nextRand() * 0.50f * pressure)).toInt().coerceIn(0, 255)

                    dabPaint.alpha = pAlpha
                    canvas.drawCircle(px, py, pRadius, dabPaint)
                }
            }

            BrushType.INK_BRUSH -> {
                // Dynamic calligraphic elliptical dab rotated with stroke tangent
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate((angle * 180f / PI.toFloat()) + 45f)

                dabPaint.color = config.color
                dabPaint.alpha = baseAlpha

                val rx = radius * 1.35f
                val ry = radius * 0.65f
                scratchRect.set(-rx, -ry, rx, ry)
                canvas.drawOval(scratchRect, dabPaint)
                canvas.restore()
            }

            BrushType.BASIC_PAINTBRUSH -> {
                // Multi-filament bristle distribution
                val bristleCount = 10
                val normalX = -sin(angle)
                val normalY = cos(angle)

                var rng = (config.seed xor (dabIndex.toLong() * 1013904223L)).toInt()
                fun nextRand(): Float {
                    rng = (rng * 1664525 + 1013904223) and 0x7FFFFFFF
                    return (rng and 0xFFFF) / 65535.0f
                }

                dabPaint.color = config.color

                for (b in 0 until bristleCount) {
                    val k = -1.0f + 2.0f * (b.toFloat() / (bristleCount - 1).toFloat())
                    val jitter = (nextRand() - 0.5f) * 0.20f * radius
                    val offset = (k * radius) + jitter

                    val bx = x + normalX * offset
                    val by = y + normalY * offset
                    val bristleRadius = max(1.0f, radius * 0.22f)
                    val bristleAlpha = (baseAlpha * (0.45f + 0.45f * pressure)).toInt().coerceIn(0, 255)

                    dabPaint.alpha = bristleAlpha
                    canvas.drawCircle(bx, by, bristleRadius, dabPaint)
                }
            }

            BrushType.AIRBRUSH -> {
                // Soft Gaussian radial spray with low flow
                val sprayRadius = radius * 1.4f
                val flowAlpha = (baseAlpha * config.flow * 0.25f).toInt().coerceIn(1, 255)
                dabPaint.color = config.color

                // 4-stage Gaussian radial expansion
                dabPaint.alpha = (flowAlpha * 0.10f).toInt().coerceIn(1, 255)
                canvas.drawCircle(x, y, sprayRadius, dabPaint)

                dabPaint.alpha = (flowAlpha * 0.35f).toInt().coerceIn(1, 255)
                canvas.drawCircle(x, y, sprayRadius * 0.70f, dabPaint)

                dabPaint.alpha = (flowAlpha * 0.70f).toInt().coerceIn(1, 255)
                canvas.drawCircle(x, y, sprayRadius * 0.40f, dabPaint)

                dabPaint.alpha = flowAlpha
                canvas.drawCircle(x, y, sprayRadius * 0.20f, dabPaint)
            }

            BrushType.ERASER -> {
                // Clears raster pixels to transparent 0x00000000
                if (config.hardness >= 0.8f) {
                    canvas.drawCircle(x, y, radius, erasePaint)
                } else {
                    // Soft feathered eraser
                    erasePaint.alpha = 255
                    canvas.drawCircle(x, y, radius * 0.5f, erasePaint)
                    erasePaint.alpha = 140
                    canvas.drawCircle(x, y, radius * 0.8f, erasePaint)
                    erasePaint.alpha = 60
                    canvas.drawCircle(x, y, radius, erasePaint)
                    erasePaint.alpha = 255
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Paint Media Rendering Subsystem (Prompt 24)
    // -------------------------------------------------------------------------

    private fun renderPaintMediaDab(
        canvas: Canvas,
        x: Float,
        y: Float,
        pressure: Float,
        angle: Float,
        dabIndex: Int,
        config: BrushConfig,
        radius: Float,
        baseAlpha: Int
    ) {
        when (config.mediaProperties.family) {
            PaintMediaFamily.STANDARD -> {} // Fallback to core
            PaintMediaFamily.MARKER -> renderMarkerDab(canvas, x, y, angle, config, radius, baseAlpha)
            PaintMediaFamily.WATERCOLOUR -> renderWatercolourDab(canvas, x, y, pressure, dabIndex, config, radius, baseAlpha)
            PaintMediaFamily.OIL_PAINT -> {
                val stamp = getOrCreateStamp(config)
                if (stamp != null) {
                    scratchStampDst.set(x - radius, y - radius, x + radius, y + radius)
                    stampPaint.colorFilter = PorterDuffColorFilter(config.color, PorterDuff.Mode.SRC_IN)
                    stampPaint.alpha = baseAlpha
                    if (angle != 0f) {
                        canvas.save()
                        canvas.rotate(angle * 180f / PI.toFloat(), x, y)
                        canvas.drawBitmap(stamp, null, scratchStampDst, stampPaint)
                        canvas.restore()
                    } else {
                        canvas.drawBitmap(stamp, null, scratchStampDst, stampPaint)
                    }
                } else {
                    renderOilPaintDab(canvas, x, y, pressure, angle, dabIndex, config, radius, baseAlpha)
                }
            }
            PaintMediaFamily.DRY_BRUSH -> renderDryBrushDab(canvas, x, y, pressure, angle, dabIndex, config, radius, baseAlpha)
            PaintMediaFamily.TEXTURED_STAMP -> renderTexturedStampDab(canvas, x, y, pressure, angle, dabIndex, config, radius, baseAlpha)
        }
    }

    private fun renderMarkerDab(
        canvas: Canvas,
        x: Float,
        y: Float,
        angle: Float,
        config: BrushConfig,
        radius: Float,
        baseAlpha: Int
    ) {
        val media = config.mediaProperties
        val halfW = radius * media.chiselAspect
        val halfH = radius
        val rot = media.markerAngleDegrees + (if (config.followStrokeAngle) (angle * 180f / PI.toFloat()) else 0f)

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rot)

        dabPaint.color = config.color
        dabPaint.style = Paint.Style.FILL

        // Outer ink bleed (feathered wicking fringe)
        if (media.inkBleed > 0.001f) {
            val bleedW = halfW * (1.0f + media.inkBleed * 0.25f)
            val bleedH = halfH * (1.0f + media.inkBleed * 0.25f)
            val bleedAlpha = (baseAlpha * media.inkBleed * 0.30f).toInt().coerceIn(1, 255)
            dabPaint.alpha = bleedAlpha
            canvas.drawRect(-bleedW, -bleedH, bleedW, bleedH, dabPaint)
        }

        // Core translucent chisel body with overlap accumulation
        val coreAlpha = (baseAlpha * (0.45f + 0.35f * media.overlapAccumulation)).toInt().coerceIn(1, 255)
        dabPaint.alpha = coreAlpha
        canvas.drawRect(-halfW, -halfH, halfW, halfH, dabPaint)

        canvas.restore()
    }

    private fun renderWatercolourDab(
        canvas: Canvas,
        x: Float,
        y: Float,
        pressure: Float,
        dabIndex: Int,
        config: BrushConfig,
        radius: Float,
        baseAlpha: Int
    ) {
        val media = config.mediaProperties
        dabPaint.color = config.color
        dabPaint.style = Paint.Style.FILL

        // 1. Central soft wash diffusion (inner puddles)
        val washAlpha = (baseAlpha * media.pigmentLoad * 0.40f).toInt().coerceIn(1, 255)
        dabPaint.alpha = (washAlpha * 0.35f).toInt().coerceIn(1, 255)
        canvas.drawCircle(x, y, radius, dabPaint)

        dabPaint.alpha = (washAlpha * (1.0f - media.waterDiffusion * 0.35f)).toInt().coerceIn(1, 255)
        canvas.drawCircle(x, y, radius * 0.70f, dabPaint)

        dabPaint.alpha = washAlpha
        canvas.drawCircle(x, y, radius * 0.35f, dabPaint)

        // 2. Wet Edge (darker puddle perimeter ring - coffee ring drying effect)
        val wetEdgeAlpha = (baseAlpha * media.pigmentLoad * (media.wetEdgeIntensity * 0.50f)).toInt().coerceIn(1, 255)
        val ringThickness = max(1.0f, radius * 0.18f)
        linePaint.color = config.color
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = ringThickness
        linePaint.alpha = wetEdgeAlpha
        canvas.drawCircle(x, y, radius * 0.92f, linePaint)

        // 3. Paper granulation (pigment settling into micro tooth)
        if (media.granulation > 0.05f) {
            val seed = config.seed xor (dabIndex.toLong() * 2147483647L)
            val specks = (12 * media.granulation).toInt().coerceAtLeast(3)
            var rng = seed.toInt()
            fun nextRand(): Float {
                rng = (rng * 1664525 + 1013904223) and 0x7FFFFFFF
                return (rng and 0xFFFF) / 65535.0f
            }
            dabPaint.style = Paint.Style.FILL
            dabPaint.alpha = (wetEdgeAlpha * media.granulation * 0.75f).toInt().coerceIn(1, 255)
            for (i in 0 until specks) {
                val rDist = sqrt(nextRand()) * radius * 0.90f
                val theta = nextRand() * (2.0f * PI.toFloat())
                val gx = x + rDist * cos(theta)
                val gy = y + rDist * sin(theta)
                canvas.drawCircle(gx, gy, 0.8f + nextRand() * 0.8f, dabPaint)
            }
        }
    }

    private fun renderOilPaintDab(
        canvas: Canvas,
        x: Float,
        y: Float,
        pressure: Float,
        angle: Float,
        dabIndex: Int,
        config: BrushConfig,
        radius: Float,
        baseAlpha: Int
    ) {
        val media = config.mediaProperties
        val bristles = media.bristleDensity.coerceIn(4, 48)
        val normalX = -sin(angle)
        val normalY = cos(angle)

        var rng = (config.seed xor (dabIndex.toLong() * 2654435761L)).toInt()
        fun nextRand(): Float {
            rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
            return (rng and 0xFFFF) / 65535.0f
        }

        dabPaint.color = config.color
        dabPaint.style = Paint.Style.FILL
        val bristleRadius = max(1.2f, radius * (2.2f / bristles))

        for (b in 0 until bristles) {
            val k = -1.0f + 2.0f * (b.toFloat() / (bristles - 1).toFloat())
            val jitter = (nextRand() - 0.5f) * 0.12f * radius
            val offset = (k * radius) + jitter

            val bx = x + normalX * offset
            val by = y + normalY * offset

            // Impasto depth tonal relief (simulated highlight/shadow ridges along tracks)
            val ridgeMod = if (b % 2 == 0) {
                1.0f + 0.20f * media.impastoDepth
            } else {
                1.0f - 0.25f * media.impastoDepth
            }

            val bAlpha = (baseAlpha * media.viscosity * media.paintLoading * ridgeMod).toInt().coerceIn(1, 255)
            dabPaint.alpha = bAlpha
            canvas.drawCircle(bx, by, bristleRadius, dabPaint)
        }
    }

    private fun renderDryBrushDab(
        canvas: Canvas,
        x: Float,
        y: Float,
        pressure: Float,
        angle: Float,
        dabIndex: Int,
        config: BrushConfig,
        radius: Float,
        baseAlpha: Int
    ) {
        val media = config.mediaProperties
        val normalX = -sin(angle)
        val normalY = cos(angle)

        var rng = (config.seed xor (dabIndex.toLong() * 1013904223L)).toInt()
        fun nextRand(): Float {
            rng = (rng * 1664525 + 1013904223) and 0x7FFFFFFF
            return (rng and 0xFFFF) / 65535.0f
        }

        dabPaint.color = config.color
        dabPaint.style = Paint.Style.FILL
        val bristleCount = 12

        // Effective tooth threshold is modulated by pressure
        val effectiveThreshold = media.toothThreshold * (1.0f - pressure * media.dryPressureSensitivity * 0.65f)

        for (b in 0 until bristleCount) {
            val k = -1.0f + 2.0f * (b.toFloat() / (bristleCount - 1).toFloat())
            val fray = (nextRand() - 0.5f) * media.fraySpread * radius * 1.5f
            val offset = (k * radius) + fray

            val bx = x + normalX * offset
            val by = y + normalY * offset

            // Deterministic paper tooth sampling at (bx, by)
            val toothVal = BrushAssetManager.getPaperTooth(config.seed).sample(
                (bx * media.textureScale) / 512.0f,
                (by * media.textureScale) / 512.0f
            )

            // If tooth peak is above threshold, deposit dry pigment; otherwise SKIP
            if (toothVal >= effectiveThreshold) {
                val coverage = ((toothVal - effectiveThreshold) / (1.0f - effectiveThreshold).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                val pAlpha = (baseAlpha * coverage * (0.6f + 0.4f * pressure)).toInt().coerceIn(1, 255)
                dabPaint.alpha = pAlpha
                val dotRadius = max(0.9f, radius * 0.16f * (0.7f + nextRand() * 0.5f))
                canvas.drawCircle(bx, by, dotRadius, dabPaint)
            }
        }
    }

    private fun renderTexturedStampDab(
        canvas: Canvas,
        x: Float,
        y: Float,
        pressure: Float,
        angle: Float,
        dabIndex: Int,
        config: BrushConfig,
        radius: Float,
        baseAlpha: Int
    ) {
        val media = config.mediaProperties
        val texture = BrushAssetManager.getTexture(
            shape = media.stampShape,
            customAssetPath = media.stampAssetPath,
            seed = config.seed
        )

        var rng = (config.seed xor (dabIndex.toLong() * 374761393L)).toInt()
        fun nextRand(): Float {
            rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
            return (rng and 0xFFFF) / 65535.0f
        }

        // Random rotation jitter
        var rot = if (config.followStrokeAngle) (angle * 180f / PI.toFloat()) else 0f
        rot += (nextRand() - 0.5f) * 2.0f * media.rotationJitter

        // Random scale jitter
        val scaleMod = 1.0f + (nextRand() - 0.5f) * 2.0f * media.scaleJitter
        val stampRadius = max(1.0f, radius * scaleMod)

        // Perpendicular scatter
        val scatterOffset = (nextRand() - 0.5f) * 2.0f * media.scatter * radius
        val sx = x - sin(angle) * scatterOffset
        val sy = y + cos(angle) * scatterOffset

        canvas.save()
        canvas.translate(sx, sy)
        canvas.rotate(rot)

        dabPaint.color = config.color
        dabPaint.style = Paint.Style.FILL

        // Sample texture mask onto a grid of discrete stamp cells (12x12)
        val gridRes = 12
        val step = (stampRadius * 2.0f) / gridRes.toFloat()
        val halfGrid = stampRadius

        for (gy in 0 until gridRes) {
            val py = -halfGrid + (gy + 0.5f) * step
            val v = (gy.toFloat() + 0.5f) / gridRes.toFloat()

            for (gx in 0 until gridRes) {
                val px = -halfGrid + (gx + 0.5f) * step
                val u = (gx.toFloat() + 0.5f) / gridRes.toFloat()

                val texVal = texture.sample(u, v)
                if (texVal > 0.05f) {
                    val pAlpha = (baseAlpha * texVal).toInt().coerceIn(1, 255)
                    dabPaint.alpha = pAlpha
                    val cellR = step * 0.70f
                    canvas.drawCircle(px, py, cellR, dabPaint)
                }
            }
        }

        canvas.restore()
    }

    fun computeRadius(config: BrushConfig, pressure: Float): Float {
        val curvedPressure = config.pressureResponse.applyCurve(pressure, config.pressureGamma)
        val scale = config.minSizeRatio + (config.maxSizeRatio - config.minSizeRatio) * curvedPressure
        return max(0.5f, (config.size * scale) / 2f)
    }
}
