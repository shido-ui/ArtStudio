package com.artstudio.selection

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.artstudio.core.CanvasPoint

/**
 * Pure Kotlin representation of selection bounds, invariant across platforms and Android SDK stubs.
 */
data class SelectionBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    val width: Float get() = maxOf(0f, right - left)
    val height: Float get() = maxOf(0f, bottom - top)
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun toRectF(): RectF = RectF().apply {
        left = this@SelectionBounds.left
        top = this@SelectionBounds.top
        right = this@SelectionBounds.right
        bottom = this@SelectionBounds.bottom
    }
}

/**
 * Geometric shape primitives used to define 2D selections in document coordinates.
 */
sealed class SelectionShape {

    /**
     * Axis-aligned rectangular selection defined in document canvas coordinates.
     */
    data class Rectangle(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) : SelectionShape() {
        constructor(rect: RectF) : this(rect.left, rect.top, rect.right, rect.bottom)
        init {
            require(right > left && bottom > top) { "Selection rectangle must have positive dimensions: left=$left, top=$top, right=$right, bottom=$bottom" }
        }
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val rect: RectF get() = RectF().apply {
            left = this@Rectangle.left
            top = this@Rectangle.top
            right = this@Rectangle.right
            bottom = this@Rectangle.bottom
        }
    }

    /**
     * Elliptical selection bounded by [left, top, right, bottom] in document canvas coordinates.
     */
    data class Ellipse(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) : SelectionShape() {
        constructor(bounds: RectF) : this(bounds.left, bounds.top, bounds.right, bounds.bottom)
        init {
            require(right > left && bottom > top) { "Selection ellipse bounds must have positive dimensions: left=$left, top=$top, right=$right, bottom=$bottom" }
        }
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
        val radiusX: Float get() = (right - left) / 2f
        val radiusY: Float get() = (bottom - top) / 2f
        val bounds: RectF get() = RectF().apply {
            left = this@Ellipse.left
            top = this@Ellipse.top
            right = this@Ellipse.right
            bottom = this@Ellipse.bottom
        }
    }

    /**
     * Freehand closed lasso selection defined by continuous sampled points.
     */
    data class Lasso(val points: List<CanvasPoint>) : SelectionShape() {
        init {
            require(points.size >= 3) { "Lasso selection requires at least 3 points, got ${points.size}" }
        }
    }

    /**
     * Closed polygonal selection defined by ordered vertices.
     */
    data class Polygon(val vertices: List<CanvasPoint>) : SelectionShape() {
        init {
            require(vertices.size >= 3) { "Polygonal selection requires at least 3 vertices, got ${vertices.size}" }
        }
    }

    /**
     * Converts this geometric shape into an Android [Path] in document coordinates.
     */
    fun toPath(): Path {
        val path = Path()
        when (this) {
            is Rectangle -> {
                path.addRect(left, top, right, bottom, Path.Direction.CW)
            }
            is Ellipse -> {
                path.addOval(bounds, Path.Direction.CW)
            }
            is Lasso -> {
                if (points.isNotEmpty()) {
                    path.moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        path.lineTo(points[i].x, points[i].y)
                    }
                    path.close()
                }
            }
            is Polygon -> {
                if (vertices.isNotEmpty()) {
                    path.moveTo(vertices[0].x, vertices[0].y)
                    for (i in 1 until vertices.size) {
                        path.lineTo(vertices[i].x, vertices[i].y)
                    }
                    path.close()
                }
            }
        }
        return path
    }

    /**
     * Computes the bounding box of this selection shape.
     */
    fun computeBounds(): SelectionBounds {
        return when (this) {
            is Rectangle -> SelectionBounds(left, top, right, bottom)
            is Ellipse -> SelectionBounds(left, top, right, bottom)
            is Lasso -> {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (pt in points) {
                    if (pt.x < minX) minX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y > maxY) maxY = pt.y
                }
                SelectionBounds(minX, minY, maxX, maxY)
            }
            is Polygon -> {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (pt in vertices) {
                    if (pt.x < minX) minX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y > maxY) maxY = pt.y
                }
                SelectionBounds(minX, minY, maxX, maxY)
            }
        }
    }
}

/**
 * Boolean combination modes for combining new selection shapes with the existing selection mask.
 */
enum class SelectionCombineMode(val displayName: String, val symbol: String) {
    REPLACE("New Selection", "✨"),
    ADD("Add to Selection", "➕"),
    SUBTRACT("Subtract from Selection", "➖"),
    INTERSECT("Intersect with Selection", "✖️")
}

/**
 * Available selection tools in Art Studio.
 */
enum class SelectionToolType(val displayName: String, val icon: String) {
    RECTANGLE("Rectangle Select", "🔲"),
    ELLIPSE("Ellipse Select", "⚪"),
    LASSO("Freehand Lasso", "➰"),
    POLYGON("Polygonal Lasso", "🔷")
}

/**
 * Authoritative range bounds for morphological and feathering operations.
 */
object SelectionLimits {
    const val MIN_EXPAND_CONTRACT = 1
    const val MAX_EXPAND_CONTRACT = 100
    const val MIN_FEATHER_RADIUS = 0.5f
    const val MAX_FEATHER_RADIUS = 50.0f
}
