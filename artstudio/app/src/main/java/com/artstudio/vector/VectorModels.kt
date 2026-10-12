package com.artstudio.vector

import android.graphics.Matrix
import android.graphics.RectF
import java.util.UUID
import kotlin.math.*

/**
 * Supported geometric vector shape primitives.
 */
enum class VectorShapeType(val id: String) {
    LINE("line"),
    POLYLINE("polyline"),
    BEZIER_PATH("bezier_path"),
    RECTANGLE("rectangle"),
    ELLIPSE("ellipse"),
    POLYGON("polygon");

    companion object {
        fun fromId(id: String): VectorShapeType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BEZIER_PATH
    }
}

/**
 * Winding rule for polygon / complex curve fill filling.
 */
enum class FillRule(val id: String) {
    NON_ZERO("nonzero"),
    EVEN_ODD("evenodd");

    companion object {
        fun fromId(id: String): FillRule =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NON_ZERO
    }
}

/**
 * Cap styles for open vector stroke paths.
 */
enum class StrokeCap(val id: String) {
    BUTT("butt"),
    ROUND("round"),
    SQUARE("square");

    companion object {
        fun fromId(id: String): StrokeCap =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ROUND
    }
}

/**
 * Join styles for vector path corners.
 */
enum class StrokeJoin(val id: String) {
    MITER("miter"),
    ROUND("round"),
    BEVEL("bevel");

    companion object {
        fun fromId(id: String): StrokeJoin =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ROUND
    }
}

/**
 * Pure mathematical 2D Affine Transformation (3x3 row-vector matrix).
 *
 * [ x' ]   [ m00  m01  m02 ] [ x ]
 * [ y' ] = [ m10  m11  m12 ] [ y ]
 * [ 1  ]   [  0    0    1  ] [ 1 ]
 */
data class AffineTransform2D(
    val m00: Float = 1f, val m01: Float = 0f, val m02: Float = 0f,
    val m10: Float = 0f, val m11: Float = 1f, val m12: Float = 0f
) {
    fun transformX(x: Float, y: Float): Float = m00 * x + m01 * y + m02
    fun transformY(x: Float, y: Float): Float = m10 * x + m11 * y + m12

    fun transformPoint(x: Float, y: Float): Pair<Float, Float> =
        Pair(transformX(x, y), transformY(x, y))

    operator fun times(other: AffineTransform2D): AffineTransform2D {
        return AffineTransform2D(
            m00 = this.m00 * other.m00 + this.m01 * other.m10,
            m01 = this.m00 * other.m01 + this.m01 * other.m11,
            m02 = this.m00 * other.m02 + this.m01 * other.m12 + this.m02,
            m10 = this.m10 * other.m00 + this.m11 * other.m10,
            m11 = this.m10 * other.m01 + this.m11 * other.m11,
            m12 = this.m10 * other.m02 + this.m11 * other.m12 + this.m12
        )
    }

    fun isIdentity(tolerance: Float = 1e-5f): Boolean {
        return abs(m00 - 1f) <= tolerance && abs(m01) <= tolerance && abs(m02) <= tolerance &&
                abs(m10) <= tolerance && abs(m11 - 1f) <= tolerance && abs(m12) <= tolerance
    }

    fun isTranslationOnly(tolerance: Float = 1e-5f): Boolean {
        return abs(m00 - 1f) <= tolerance && abs(m01) <= tolerance &&
                abs(m10) <= tolerance && abs(m11 - 1f) <= tolerance
    }

    companion object {
        val IDENTITY = AffineTransform2D()

        fun translation(dx: Float, dy: Float): AffineTransform2D =
            AffineTransform2D(1f, 0f, dx, 0f, 1f, dy)

        fun scale(sx: Float, sy: Float, px: Float = 0f, py: Float = 0f): AffineTransform2D {
            // translate(px, py) * scale(sx, sy) * translate(-px, -py)
            return AffineTransform2D(
                m00 = sx, m01 = 0f, m02 = px * (1f - sx),
                m10 = 0f, m11 = sy, m12 = py * (1f - sy)
            )
        }

        fun rotation(degrees: Float, px: Float = 0f, py: Float = 0f): AffineTransform2D {
            val rad = Math.toRadians(degrees.toDouble()).toFloat()
            val c = cos(rad)
            val s = sin(rad)
            return AffineTransform2D(
                m00 = c, m01 = -s, m02 = px * (1f - c) + py * s,
                m10 = s, m11 = c, m12 = py * (1f - c) - px * s
            )
        }

        fun skewX(angleDegrees: Float): AffineTransform2D {
            val rad = Math.toRadians(angleDegrees.toDouble()).toFloat()
            return AffineTransform2D(1f, tan(rad), 0f, 0f, 1f, 0f)
        }

        fun skewY(angleDegrees: Float): AffineTransform2D {
            val rad = Math.toRadians(angleDegrees.toDouble()).toFloat()
            return AffineTransform2D(1f, 0f, 0f, tan(rad), 1f, 0f)
        }

        fun matrix(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): AffineTransform2D {
            return AffineTransform2D(m00 = a, m01 = c, m02 = e, m10 = b, m11 = d, m12 = f)
        }
    }
}

/**
 * Axis-aligned bounding box with mathematical containment testing.
 */
data class VectorBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom

    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

/**
 * Styling attributes applied to vector shapes and paths.
 */
data class VectorStyle(
    val strokeColor: Long? = 0xFF000000L,
    val strokeWidth: Float = 2.0f,
    val fillColor: Long? = null,
    val fillRule: FillRule = FillRule.NON_ZERO,
    val strokeCap: StrokeCap = StrokeCap.ROUND,
    val strokeJoin: StrokeJoin = StrokeJoin.ROUND,
    val strokeMiterLimit: Float = 4.0f,
    val opacity: Float = 1.0f
) {
    init {
        require(strokeWidth >= 0f) { "Stroke width must be non-negative, got $strokeWidth" }
        require(opacity in 0.0f..1.0f) { "Opacity must be in [0, 1], got $opacity" }
    }
}

/**
 * Path anchor point with optional cubic Bezier handles for incoming and outgoing tangents.
 *
 * Coordinates are stored in document space.
 * When [isSmooth] is true, incoming and outgoing control handles are constrained to be collinear.
 */
data class VectorAnchor(
    val x: Float,
    val y: Float,
    val handleInX: Float? = null,
    val handleInY: Float? = null,
    val handleOutX: Float? = null,
    val handleOutY: Float? = null,
    val isSmooth: Boolean = false
) {
    val hasHandleIn: Boolean get() = handleInX != null && handleInY != null
    val hasHandleOut: Boolean get() = handleOutX != null && handleOutY != null

    fun transformed(t: AffineTransform2D): VectorAnchor {
        val (nx, ny) = t.transformPoint(x, y)
        val nIn = if (hasHandleIn) t.transformPoint(handleInX!!, handleInY!!) else null
        val nOut = if (hasHandleOut) t.transformPoint(handleOutX!!, handleOutY!!) else null
        return copy(
            x = nx,
            y = ny,
            handleInX = nIn?.first,
            handleInY = nIn?.second,
            handleOutX = nOut?.first,
            handleOutY = nOut?.second
        )
    }

    fun translated(dx: Float, dy: Float): VectorAnchor {
        return copy(
            x = x + dx,
            y = y + dy,
            handleInX = handleInX?.let { it + dx },
            handleInY = handleInY?.let { it + dy },
            handleOutX = handleOutX?.let { it + dx },
            handleOutY = handleOutY?.let { it + dy }
        )
    }
}

/**
 * Explicit geometry specification for rectangles, including optional corner rounding.
 */
data class VectorRectData(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rx: Float = 0f,
    val ry: Float = 0f
) {
    init {
        require(width >= 0f) { "Width must be non-negative, got $width" }
        require(height >= 0f) { "Height must be non-negative, got $height" }
        require(rx >= 0f && ry >= 0f) { "Corner radii must be non-negative" }
    }

    val effectiveRx: Float get() = rx.coerceAtMost(width / 2f)
    val effectiveRy: Float get() = (if (ry > 0f) ry else rx).coerceAtMost(height / 2f)

    fun bounds(): VectorBounds = VectorBounds(x, y, x + width, y + height)
}

/**
 * Explicit geometry specification for ellipses and circles.
 */
data class VectorEllipseData(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float
) {
    init {
        require(rx >= 0f) { "rx must be non-negative, got $rx" }
        require(ry >= 0f) { "ry must be non-negative, got $ry" }
    }

    fun bounds(): VectorBounds = VectorBounds(cx - rx, cy - ry, cx + rx, cy + ry)
}

/**
 * Low-level mathematical path command segment.
 */
sealed class VectorSegment {
    data class MoveTo(val x: Float, val y: Float) : VectorSegment()
    data class LineTo(val x: Float, val y: Float) : VectorSegment()
    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : VectorSegment()
    data class CubicTo(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x: Float, val y: Float
    ) : VectorSegment()
    object Close : VectorSegment()

    fun transformed(t: AffineTransform2D): VectorSegment {
        return when (this) {
            is MoveTo -> MoveTo(t.transformX(x, y), t.transformY(x, y))
            is LineTo -> LineTo(t.transformX(x, y), t.transformY(x, y))
            is QuadTo -> QuadTo(
                t.transformX(x1, y1), t.transformY(x1, y1),
                t.transformX(x, y), t.transformY(x, y)
            )
            is CubicTo -> CubicTo(
                t.transformX(x1, y1), t.transformY(x1, y1),
                t.transformX(x2, y2), t.transformY(x2, y2),
                t.transformX(x, y), t.transformY(x, y)
            )
            is Close -> Close
        }
    }
}

/**
 * High-level authoritative vector object in document space.
 *
 * Can represent structured shapes (rectangle, ellipse, polygon) or freeform paths
 * (line, polyline, cubic/quad Bezier curves) with anchor points and explicit styles.
 */
data class VectorObject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Vector Object",
    val shapeType: VectorShapeType = VectorShapeType.BEZIER_PATH,
    val anchors: List<VectorAnchor> = emptyList(),
    val segments: List<VectorSegment> = emptyList(),
    val rectData: VectorRectData? = null,
    val ellipseData: VectorEllipseData? = null,
    val isClosed: Boolean = false,
    val style: VectorStyle = VectorStyle(),
    val metadata: Map<String, String> = emptyMap()
) {

    /**
     * Converts high-level shape data or anchor chains into an evaluated sequence of segments.
     */
    fun toSegments(): List<VectorSegment> {
        if (segments.isNotEmpty()) {
            return segments
        }

        when (shapeType) {
            VectorShapeType.RECTANGLE -> {
                val r = rectData ?: return emptyList()
                val rx = r.effectiveRx
                val ry = r.effectiveRy
                if (rx <= 0f && ry <= 0f) {
                    return listOf(
                        VectorSegment.MoveTo(r.x, r.y),
                        VectorSegment.LineTo(r.x + r.width, r.y),
                        VectorSegment.LineTo(r.x + r.width, r.y + r.height),
                        VectorSegment.LineTo(r.x, r.y + r.height),
                        VectorSegment.Close
                    )
                }
                // Rounded rectangle with cubic corner arcs (kappa approx 0.5522847)
                val kx = rx * 0.55228475f
                val ky = ry * 0.55228475f
                return listOf(
                    VectorSegment.MoveTo(r.x + rx, r.y),
                    VectorSegment.LineTo(r.x + r.width - rx, r.y),
                    VectorSegment.CubicTo(
                        r.x + r.width - rx + kx, r.y,
                        r.x + r.width, r.y + ry - ky,
                        r.x + r.width, r.y + ry
                    ),
                    VectorSegment.LineTo(r.x + r.width, r.y + r.height - ry),
                    VectorSegment.CubicTo(
                        r.x + r.width, r.y + r.height - ry + ky,
                        r.x + r.width - rx + kx, r.y + r.height,
                        r.x + r.width - rx, r.y + r.height
                    ),
                    VectorSegment.LineTo(r.x + rx, r.y + r.height),
                    VectorSegment.CubicTo(
                        r.x + rx - kx, r.y + r.height,
                        r.x, r.y + r.height - ry + ky,
                        r.x, r.y + r.height - ry
                    ),
                    VectorSegment.LineTo(r.x, r.y + ry),
                    VectorSegment.CubicTo(
                        r.x, r.y + ry - ky,
                        r.x + rx - kx, r.y,
                        r.x + rx, r.y
                    ),
                    VectorSegment.Close
                )
            }
            VectorShapeType.ELLIPSE -> {
                val el = ellipseData ?: return emptyList()
                val cx = el.cx
                val cy = el.cy
                val rx = el.rx
                val ry = el.ry
                val kx = rx * 0.55228475f
                val ky = ry * 0.55228475f
                return listOf(
                    VectorSegment.MoveTo(cx + rx, cy),
                    VectorSegment.CubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
                    VectorSegment.CubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
                    VectorSegment.CubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
                    VectorSegment.CubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy),
                    VectorSegment.Close
                )
            }
            VectorShapeType.LINE, VectorShapeType.POLYLINE, VectorShapeType.POLYGON, VectorShapeType.BEZIER_PATH -> {
                if (anchors.isEmpty()) return emptyList()
                val list = mutableListOf<VectorSegment>()
                list.add(VectorSegment.MoveTo(anchors[0].x, anchors[0].y))

                for (i in 1 until anchors.size) {
                    val prev = anchors[i - 1]
                    val curr = anchors[i]
                    if (prev.hasHandleOut || curr.hasHandleIn) {
                        val cp1X = prev.handleOutX ?: prev.x
                        val cp1Y = prev.handleOutY ?: prev.y
                        val cp2X = curr.handleInX ?: curr.x
                        val cp2Y = curr.handleInY ?: curr.y
                        list.add(VectorSegment.CubicTo(cp1X, cp1Y, cp2X, cp2Y, curr.x, curr.y))
                    } else {
                        list.add(VectorSegment.LineTo(curr.x, curr.y))
                    }
                }

                if (isClosed || shapeType == VectorShapeType.POLYGON) {
                    if (anchors.size > 2) {
                        val last = anchors.last()
                        val first = anchors.first()
                        if (last.hasHandleOut || first.hasHandleIn) {
                            val cp1X = last.handleOutX ?: last.x
                            val cp1Y = last.handleOutY ?: last.y
                            val cp2X = first.handleInX ?: first.x
                            val cp2Y = first.handleInY ?: first.y
                            list.add(VectorSegment.CubicTo(cp1X, cp1Y, cp2X, cp2Y, first.x, first.y))
                        }
                    }
                    list.add(VectorSegment.Close)
                }
                return list
            }
        }
    }

    /**
     * Computes the bounding rectangle encompassing the vector object.
     */
    fun computeBounds(): VectorBounds {
        rectData?.let { return it.bounds() }
        ellipseData?.let { return it.bounds() }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        fun update(x: Float, y: Float) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        if (anchors.isNotEmpty()) {
            for (a in anchors) {
                update(a.x, a.y)
                a.handleInX?.let { hx -> a.handleInY?.let { hy -> update(hx, hy) } }
                a.handleOutX?.let { hx -> a.handleOutY?.let { hy -> update(hx, hy) } }
            }
        } else {
            for (seg in toSegments()) {
                when (seg) {
                    is VectorSegment.MoveTo -> update(seg.x, seg.y)
                    is VectorSegment.LineTo -> update(seg.x, seg.y)
                    is VectorSegment.QuadTo -> {
                        update(seg.x1, seg.y1)
                        update(seg.x, seg.y)
                    }
                    is VectorSegment.CubicTo -> {
                        update(seg.x1, seg.y1)
                        update(seg.x2, seg.y2)
                        update(seg.x, seg.y)
                    }
                    is VectorSegment.Close -> {}
                }
            }
        }

        return if (minX.isFinite() && minY.isFinite()) {
            VectorBounds(minX, minY, maxX, maxY)
        } else {
            VectorBounds(0f, 0f, 0f, 0f)
        }
    }

    companion object {
        fun createLine(x1: Float, y1: Float, x2: Float, y2: Float, style: VectorStyle = VectorStyle()): VectorObject {
            return VectorObject(
                name = "Line",
                shapeType = VectorShapeType.LINE,
                anchors = listOf(VectorAnchor(x1, y1), VectorAnchor(x2, y2)),
                isClosed = false,
                style = style
            )
        }

        fun createPolyline(points: List<Pair<Float, Float>>, style: VectorStyle = VectorStyle()): VectorObject {
            val anchors = points.map { VectorAnchor(it.first, it.second) }
            return VectorObject(
                name = "Polyline",
                shapeType = VectorShapeType.POLYLINE,
                anchors = anchors,
                isClosed = false,
                style = style
            )
        }

        fun createPolygon(points: List<Pair<Float, Float>>, style: VectorStyle = VectorStyle()): VectorObject {
            val anchors = points.map { VectorAnchor(it.first, it.second) }
            return VectorObject(
                name = "Polygon",
                shapeType = VectorShapeType.POLYGON,
                anchors = anchors,
                isClosed = true,
                style = style
            )
        }

        fun createRectangle(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            rx: Float = 0f,
            ry: Float = 0f,
            style: VectorStyle = VectorStyle()
        ): VectorObject {
            return VectorObject(
                name = "Rectangle",
                shapeType = VectorShapeType.RECTANGLE,
                rectData = VectorRectData(x, y, width, height, rx, ry),
                isClosed = true,
                style = style
            )
        }

        fun createEllipse(
            cx: Float,
            cy: Float,
            rx: Float,
            ry: Float,
            style: VectorStyle = VectorStyle()
        ): VectorObject {
            return VectorObject(
                name = "Ellipse",
                shapeType = VectorShapeType.ELLIPSE,
                ellipseData = VectorEllipseData(cx, cy, rx, ry),
                isClosed = true,
                style = style
            )
        }

        fun createCircle(
            cx: Float,
            cy: Float,
            r: Float,
            style: VectorStyle = VectorStyle()
        ): VectorObject {
            return createEllipse(cx, cy, r, r, style)
        }

        fun createBezierPath(
            anchors: List<VectorAnchor>,
            isClosed: Boolean = false,
            style: VectorStyle = VectorStyle(),
            name: String = "Bezier Path"
        ): VectorObject {
            return VectorObject(
                name = name,
                shapeType = VectorShapeType.BEZIER_PATH,
                anchors = anchors,
                isClosed = isClosed,
                style = style
            )
        }

        fun fromSegments(
            segments: List<VectorSegment>,
            style: VectorStyle = VectorStyle(),
            isClosed: Boolean = false,
            name: String = "Vector Path"
        ): VectorObject {
            return VectorObject(
                name = name,
                shapeType = VectorShapeType.BEZIER_PATH,
                segments = segments,
                isClosed = isClosed,
                style = style
            )
        }
    }
}
