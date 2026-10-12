package com.artstudio.guides

import android.graphics.*
import kotlin.math.*

/**
 * Renders non-destructive visual drawing guides and symmetry axes onto the canvas.
 * Designed to execute within document coordinate space, automatically scaling,
 * panning, and rotating in sync with ViewportTransform.
 */
class GuideOverlayRenderer {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val fillHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Renders active drawing guides and symmetry axes within document bounds.
     */
    fun render(
        canvas: Canvas,
        config: DrawingAidsConfig,
        docBounds: RectF
    ) {
        if (!config.guideVisible) return

        val guideColor = config.guideColorRgba.toInt()
        val alphaInt = (config.guideOpacity * 255f).toInt().coerceIn(0, 255)
        val finalGuideColor = (guideColor and 0x00FFFFFF) or (alphaInt shl 24)

        linePaint.color = finalGuideColor
        solidPaint.color = finalGuideColor
        fillHandlePaint.color = finalGuideColor

        // 1. Render active guide mode
        when (config.guideType) {
            GuideType.NONE -> { /* No guide */ }
            GuideType.STRAIGHT_LINE -> renderLineGuide(canvas, config.lineGuide, docBounds)
            GuideType.CIRCLE_ELLIPSE -> renderEllipseGuide(canvas, config.ellipseGuide)
            GuideType.RADIAL -> renderRadialGuide(canvas, config.radialGuide, docBounds)
            GuideType.PERSPECTIVE -> renderPerspectiveGuide(canvas, config.perspectiveGuide, docBounds)
        }

        // 2. Render active symmetry axes
        if (config.symmetry.type != SymmetryType.NONE && config.symmetry.showAxes) {
            renderSymmetryAxes(canvas, config.symmetry, docBounds)
        }
    }

    private fun renderLineGuide(canvas: Canvas, guide: LineGuideConfig, bounds: RectF) {
        val rad = Math.toRadians(guide.angleDegrees.toDouble())
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()

        // Draw long guide line spanning beyond document bounds
        val extent = max(bounds.width(), bounds.height()) * 2f
        val x1 = guide.originX - extent * ux
        val y1 = guide.originY - extent * uy
        val x2 = guide.originX + extent * ux
        val y2 = guide.originY + extent * uy

        canvas.drawLine(x1, y1, x2, y2, linePaint)
        // Draw origin handle
        canvas.drawCircle(guide.originX, guide.originY, 6f, fillHandlePaint)
    }

    private fun renderEllipseGuide(canvas: Canvas, guide: EllipseGuideConfig) {
        canvas.save()
        canvas.translate(guide.centerX, guide.centerY)
        canvas.rotate(guide.rotationDegrees)

        val rect = RectF(-guide.radiusX, -guide.radiusY, guide.radiusX, guide.radiusY)
        canvas.drawOval(rect, solidPaint)
        // Center handle
        canvas.drawCircle(0f, 0f, 5f, fillHandlePaint)

        canvas.restore()
    }

    private fun renderRadialGuide(canvas: Canvas, guide: RadialGuideConfig, bounds: RectF) {
        val maxExtent = hypot(bounds.width(), bounds.height()) * 1.5f
        val sectors = max(2, guide.rayCount)
        val angleStep = 360.0 / sectors

        for (i in 0 until sectors) {
            val rad = Math.toRadians(i * angleStep)
            val ex = guide.centerX + (maxExtent * cos(rad)).toFloat()
            val ey = guide.centerY + (maxExtent * sin(rad)).toFloat()
            canvas.drawLine(guide.centerX, guide.centerY, ex, ey, linePaint)
        }
        canvas.drawCircle(guide.centerX, guide.centerY, 6f, fillHandlePaint)
    }

    private fun renderPerspectiveGuide(canvas: Canvas, guide: PerspectiveGuideConfig, bounds: RectF) {
        // Horizon line
        canvas.drawLine(bounds.left - 500f, guide.horizonY, bounds.right + 500f, guide.horizonY, solidPaint)

        val activeVps = when (guide.type) {
            PerspectiveType.ONE_POINT -> listOf(guide.vp1)
            PerspectiveType.TWO_POINT -> listOf(guide.vp1, guide.vp2)
            PerspectiveType.THREE_POINT -> listOf(guide.vp1, guide.vp2, guide.vp3)
        }

        val stepDeg = max(5f, guide.raySpacingDegrees).toDouble()
        val extent = hypot(bounds.width(), bounds.height()) * 2f

        for (vp in activeVps) {
            // Draw vanishing point marker
            canvas.drawCircle(vp.x, vp.y, 8f, fillHandlePaint)

            // Radiate rays towards canvas bounds
            var deg = -90.0
            while (deg <= 90.0) {
                val rad = Math.toRadians(deg)
                val ex = vp.x + (extent * cos(rad)).toFloat()
                val ey = vp.y + (extent * sin(rad)).toFloat()
                canvas.drawLine(vp.x, vp.y, ex, ey, linePaint)
                deg += stepDeg
            }
        }
    }

    private fun renderSymmetryAxes(canvas: Canvas, symmetry: SymmetryConfig, bounds: RectF) {
        val axisColor = symmetry.axisColorRgba.toInt()
        linePaint.color = axisColor
        fillHandlePaint.color = axisColor

        val cx = symmetry.centerX
        val cy = symmetry.centerY

        when (symmetry.type) {
            SymmetryType.NONE -> {}
            SymmetryType.MIRROR_VERTICAL -> {
                canvas.drawLine(cx, bounds.top - 200f, cx, bounds.bottom + 200f, linePaint)
                canvas.drawCircle(cx, cy, 7f, fillHandlePaint)
            }
            SymmetryType.MIRROR_HORIZONTAL -> {
                canvas.drawLine(bounds.left - 200f, cy, bounds.right + 200f, cy, linePaint)
                canvas.drawCircle(cx, cy, 7f, fillHandlePaint)
            }
            SymmetryType.MIRROR_DUAL -> {
                canvas.drawLine(cx, bounds.top - 200f, cx, bounds.bottom + 200f, linePaint)
                canvas.drawLine(bounds.left - 200f, cy, bounds.right + 200f, cy, linePaint)
                canvas.drawCircle(cx, cy, 7f, fillHandlePaint)
            }
            SymmetryType.RADIAL -> {
                val sectors = max(2, symmetry.radialSectors)
                val angleStep = 360.0 / sectors
                val extent = hypot(bounds.width(), bounds.height()) * 1.5f

                for (i in 0 until sectors) {
                    val rad = Math.toRadians(i * angleStep)
                    val ex = cx + (extent * cos(rad)).toFloat()
                    val ey = cy + (extent * sin(rad)).toFloat()
                    canvas.drawLine(cx, cy, ex, ey, linePaint)
                }
                canvas.drawCircle(cx, cy, 7f, fillHandlePaint)
            }
        }
    }
}
