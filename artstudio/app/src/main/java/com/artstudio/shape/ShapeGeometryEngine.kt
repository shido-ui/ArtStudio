package com.artstudio.shape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.artstudio.vector.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure-mathematical and raster rendering engine for geometric shape creation.
 */
object ShapeGeometryEngine {

    /**
     * Constructs a high-level [VectorObject] from user [ShapeCreationParams].
     */
    fun createShape(params: ShapeCreationParams): VectorObject {
        val style = params.toVectorStyle()
        return when (params.type) {
            GeometricShapeType.RECTANGLE -> {
                VectorObject.createRectangle(
                    x = params.x,
                    y = params.y,
                    width = params.width,
                    height = params.height,
                    rx = 0f,
                    ry = 0f,
                    style = style
                )
            }
            GeometricShapeType.ROUNDED_RECTANGLE -> {
                VectorObject.createRectangle(
                    x = params.x,
                    y = params.y,
                    width = params.width,
                    height = params.height,
                    rx = params.rx.coerceAtLeast(1f),
                    ry = if (params.ry > 0f) params.ry else params.rx.coerceAtLeast(1f),
                    style = style
                )
            }
            GeometricShapeType.ELLIPSE -> {
                val cx = params.x + params.width / 2f
                val cy = params.y + params.height / 2f
                val rx = params.width / 2f
                val ry = params.height / 2f
                VectorObject.createEllipse(cx, cy, rx, ry, style)
            }
            GeometricShapeType.CIRCLE -> {
                val r = (params.width.coerceAtLeast(params.height)) / 2f
                val cx = params.x + r
                val cy = params.y + r
                VectorObject.createCircle(cx, cy, r, style)
            }
            GeometricShapeType.LINE -> {
                VectorObject.createLine(
                    x1 = params.x,
                    y1 = params.y,
                    x2 = params.x + params.width,
                    y2 = params.y + params.height,
                    style = style
                )
            }
            GeometricShapeType.POLYGON -> {
                val radius = params.width.coerceAtLeast(params.height) / 2f
                val cx = params.x + radius
                val cy = params.y + radius
                createRegularPolygon(cx, cy, radius, params.polygonSides, style)
            }
            GeometricShapeType.STAR -> {
                val outerR = params.width.coerceAtLeast(params.height) / 2f
                val innerR = outerR * params.starInnerRatio.coerceIn(0.1f, 0.9f)
                val cx = params.x + outerR
                val cy = params.y + outerR
                createStar(cx, cy, outerR, innerR, params.starPoints, style)
            }
        }
    }

    /**
     * Constructs a regular N-sided polygon inscribed in a circle of given radius.
     */
    fun createRegularPolygon(
        cx: Float,
        cy: Float,
        radius: Float,
        sides: Int,
        style: VectorStyle = VectorStyle(),
        startAngleDegrees: Float = -90f
    ): VectorObject {
        require(sides >= 3) { "Polygon must have at least 3 sides, got $sides" }
        require(radius > 0f) { "Radius must be positive, got $radius" }

        val startRad = Math.toRadians(startAngleDegrees.toDouble())
        val step = 2.0 * Math.PI / sides
        val points = mutableListOf<Pair<Float, Float>>()

        for (i in 0 until sides) {
            val angle = startRad + i * step
            val px = cx + (radius * cos(angle)).toFloat()
            val py = cy + (radius * sin(angle)).toFloat()
            points.add(Pair(px, py))
        }

        return VectorObject.createPolygon(points, style).copy(name = "Regular Polygon (${sides}-gon)")
    }

    /**
     * Constructs a star polygon with alternating outer and inner vertices.
     */
    fun createStar(
        cx: Float,
        cy: Float,
        outerRadius: Float,
        innerRadius: Float,
        points: Int,
        style: VectorStyle = VectorStyle(),
        startAngleDegrees: Float = -90f
    ): VectorObject {
        require(points >= 3) { "Star must have at least 3 points, got $points" }
        require(outerRadius > 0f && innerRadius > 0f) { "Radii must be positive" }

        val totalVertices = points * 2
        val startRad = Math.toRadians(startAngleDegrees.toDouble())
        val step = Math.PI / points
        val pointList = mutableListOf<Pair<Float, Float>>()

        for (i in 0 until totalVertices) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val angle = startRad + i * step
            val px = cx + (r * cos(angle)).toFloat()
            val py = cy + (r * sin(angle)).toFloat()
            pointList.add(Pair(px, py))
        }

        return VectorObject.createPolygon(pointList, style).copy(name = "Star (${points}-point)")
    }

    /**
     * Rasterizes a [VectorObject] into an existing ARGB pixel buffer using document coordinates.
     */
    fun stampShapeToRasterBuffer(
        targetPixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        shape: VectorObject
    ): IntArray {
        val result = targetPixels.copyOf()
        val bitmap = try {
            Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            null
        }

        if (bitmap != null) {
            bitmap.setPixels(result, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)

            val canvas = Canvas(bitmap)
            val path = VectorGeometryEngine.toAndroidPath(shape)

            // Fill pass
            if (shape.style.fillColor != null) {
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    val c = shape.style.fillColor.toInt()
                    val a = (Color.alpha(c) * shape.style.opacity).toInt().coerceIn(0, 255)
                    color = (c and 0x00FFFFFF) or (a shl 24)
                }
                canvas.drawPath(path, fillPaint)
            }

            // Stroke pass
            if (shape.style.strokeColor != null && shape.style.strokeWidth > 0f) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = shape.style.strokeWidth
                    val c = shape.style.strokeColor.toInt()
                    val a = (Color.alpha(c) * shape.style.opacity).toInt().coerceIn(0, 255)
                    color = (c and 0x00FFFFFF) or (a shl 24)
                    strokeCap = when (shape.style.strokeCap) {
                        StrokeCap.BUTT -> Paint.Cap.BUTT
                        StrokeCap.ROUND -> Paint.Cap.ROUND
                        StrokeCap.SQUARE -> Paint.Cap.SQUARE
                    }
                    strokeJoin = when (shape.style.strokeJoin) {
                        StrokeJoin.MITER -> Paint.Join.MITER
                        StrokeJoin.ROUND -> Paint.Join.ROUND
                        StrokeJoin.BEVEL -> Paint.Join.BEVEL
                    }
                    strokeMiter = shape.style.strokeMiterLimit
                }
                canvas.drawPath(path, strokePaint)
            }

            bitmap.getPixels(result, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight)
            bitmap.recycle()
            return result
        } else {
            // Pure Kotlin fallback for headless JVM environments
            val mask = com.artstudio.cv.pathfit.ContourRasterComparator.rasterizeVectorObject(
                shape, canvasWidth, canvasHeight, fillPath = shape.style.fillColor != null
            )
            val drawColor = (shape.style.fillColor ?: shape.style.strokeColor ?: 0xFF000000.toLong()).toInt()
            for (i in 0 until minOf(result.size, mask.size)) {
                if (mask[i] != 0) {
                    result[i] = drawColor
                }
            }
            return result
        }
    }
}
