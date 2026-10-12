package com.artstudio.cv.pathfit

import com.artstudio.cv.ContourCandidate
import com.artstudio.cv.CvPoint
import com.artstudio.cv.CvRect
import kotlin.math.*

/**
 * Analytical benchmark generator creating synthetic contours with known ground-truth geometry
 * (curves, sharp corners, holes, disconnected regions, and stars) for regression auditing.
 */
object SyntheticContourGenerator {

    /**
     * Generates a circular contour with known center and radius.
     */
    fun createCircleContour(
        cx: Float = 50f,
        cy: Float = 50f,
        radius: Float = 30f,
        numPoints: Int = 64,
        id: Int = 1
    ): ContourCandidate {
        val points = mutableListOf<CvPoint>()
        for (i in 0 until numPoints) {
            val angle = 2.0 * Math.PI * i / numPoints
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            points.add(CvPoint(x, y))
        }

        val area = Math.PI * radius * radius
        val perim = 2.0 * Math.PI * radius
        val bbox = CvRect(cx - radius, cy - radius, cx + radius, cy + radius)

        return ContourCandidate(
            id = id,
            points = points,
            simplifiedPoints = points,
            isHole = false,
            area = area,
            perimeter = perim,
            boundingBox = bbox,
            centroid = CvPoint(cx, cy)
        )
    }

    /**
     * Generates an axis-aligned rectangular contour with 4 sharp 90° corners.
     */
    fun createRectangleContour(
        x: Float = 20f,
        y: Float = 20f,
        width: Float = 60f,
        height: Float = 40f,
        pointsPerSide: Int = 15,
        id: Int = 1
    ): ContourCandidate {
        val points = mutableListOf<CvPoint>()

        // Top edge: (x, y) -> (x + width, y)
        for (i in 0 until pointsPerSide) {
            val t = i.toFloat() / pointsPerSide
            points.add(CvPoint(x + t * width, y))
        }
        // Right edge: (x + width, y) -> (x + width, y + height)
        for (i in 0 until pointsPerSide) {
            val t = i.toFloat() / pointsPerSide
            points.add(CvPoint(x + width, y + t * height))
        }
        // Bottom edge: (x + width, y + height) -> (x, y + height)
        for (i in 0 until pointsPerSide) {
            val t = i.toFloat() / pointsPerSide
            points.add(CvPoint(x + width - t * width, y + height))
        }
        // Left edge: (x, y + height) -> (x, y)
        for (i in 0 until pointsPerSide) {
            val t = i.toFloat() / pointsPerSide
            points.add(CvPoint(x, y + height - t * height))
        }

        val area = (width * height).toDouble()
        val perim = (2 * (width + height)).toDouble()
        val bbox = CvRect(x, y, x + width, y + height)

        return ContourCandidate(
            id = id,
            points = points,
            simplifiedPoints = points,
            isHole = false,
            area = area,
            perimeter = perim,
            boundingBox = bbox,
            centroid = CvPoint(x + width / 2f, y + height / 2f)
        )
    }

    /**
     * Generates a donut/torus fixture: an outer circular boundary plus an inner concentric hole.
     */
    fun createDonutContourPair(
        cx: Float = 50f,
        cy: Float = 50f,
        outerRadius: Float = 35f,
        innerRadius: Float = 15f,
        numPoints: Int = 64
    ): Pair<ContourCandidate, ContourCandidate> {
        val outer = createCircleContour(cx, cy, outerRadius, numPoints, id = 1)

        // Inner hole: counter-clockwise winding with isHole = true
        val holePoints = mutableListOf<CvPoint>()
        for (i in 0 until numPoints) {
            val angle = -2.0 * Math.PI * i / numPoints
            val x = cx + innerRadius * cos(angle).toFloat()
            val y = cy + innerRadius * sin(angle).toFloat()
            holePoints.add(CvPoint(x, y))
        }

        val holeArea = Math.PI * innerRadius * innerRadius
        val holePerim = 2.0 * Math.PI * innerRadius
        val holeBbox = CvRect(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius)

        val hole = ContourCandidate(
            id = 2,
            points = holePoints,
            simplifiedPoints = holePoints,
            isHole = true,
            area = holeArea,
            perimeter = holePerim,
            boundingBox = holeBbox,
            centroid = CvPoint(cx, cy)
        )

        return Pair(outer, hole)
    }

    /**
     * Generates a multi-component fixture with 3 separate disconnected geometric regions:
     * a circle, a square, and an equilateral triangle.
     */
    fun createDisconnectedRegions(): List<ContourCandidate> {
        val circle = createCircleContour(cx = 25f, cy = 25f, radius = 15f, numPoints = 40, id = 1)
        val square = createRectangleContour(x = 60f, y = 10f, width = 30f, height = 30f, pointsPerSide = 10, id = 2)

        // Triangle at bottom center: vertices at (50, 60), (30, 90), (70, 90)
        val triPoints = mutableListOf<CvPoint>()
        val v0 = CvPoint(50f, 60f)
        val v1 = CvPoint(70f, 90f)
        val v2 = CvPoint(30f, 90f)
        val steps = 10

        for (i in 0 until steps) {
            val t = i.toFloat() / steps
            triPoints.add(CvPoint(v0.x + t * (v1.x - v0.x), v0.y + t * (v1.y - v0.y)))
        }
        for (i in 0 until steps) {
            val t = i.toFloat() / steps
            triPoints.add(CvPoint(v1.x + t * (v2.x - v1.x), v1.y + t * (v2.y - v1.y)))
        }
        for (i in 0 until steps) {
            val t = i.toFloat() / steps
            triPoints.add(CvPoint(v2.x + t * (v0.x - v2.x), v2.y + t * (v0.y - v2.y)))
        }

        val triangle = ContourCandidate(
            id = 3,
            points = triPoints,
            simplifiedPoints = triPoints,
            isHole = false,
            area = 0.5 * 40.0 * 30.0,
            perimeter = 30.0 + 2.0 * hypot(20.0, 30.0),
            boundingBox = CvRect(30f, 60f, 70f, 90f),
            centroid = CvPoint(50f, 80f)
        )

        return listOf(circle, square, triangle)
    }

    /**
     * Generates a 5-pointed star contour with alternating outer tips and inner acute angles.
     */
    fun createStarContour(
        cx: Float = 50f,
        cy: Float = 50f,
        outerRadius: Float = 35f,
        innerRadius: Float = 15f,
        pointsCount: Int = 5,
        samplesPerEdge: Int = 6,
        id: Int = 1
    ): ContourCandidate {
        val starVertices = mutableListOf<CvPoint>()
        val totalVertices = pointsCount * 2
        for (i in 0 until totalVertices) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val angle = -Math.PI / 2.0 + (i.toDouble() * Math.PI / pointsCount)
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            starVertices.add(CvPoint(x, y))
        }

        // Subsample each edge
        val densePoints = mutableListOf<CvPoint>()
        for (i in 0 until totalVertices) {
            val p0 = starVertices[i]
            val p1 = starVertices[(i + 1) % totalVertices]
            for (s in 0 until samplesPerEdge) {
                val t = s.toFloat() / samplesPerEdge
                densePoints.add(CvPoint(p0.x + t * (p1.x - p0.x), p0.y + t * (p1.y - p0.y)))
            }
        }

        return ContourCandidate(
            id = id,
            points = densePoints,
            simplifiedPoints = starVertices,
            isHole = false,
            area = 800.0,
            perimeter = 250.0,
            boundingBox = CvRect(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius),
            centroid = CvPoint(cx, cy)
        )
    }

    /**
     * Generates a ground-truth binary raster mask of size [width] x [height] for a given polygon.
     */
    fun renderPolygonToBinaryMask(
        polygon: List<CvPoint>,
        width: Int,
        height: Int
    ): IntArray {
        val mask = IntArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                if (ContourVectorizationEngine.isPointInsideContour(CvPoint(x.toFloat() + 0.5f, y.toFloat() + 0.5f), polygon)) {
                    mask[rowOffset + x] = 255
                }
            }
        }
        return mask
    }
}
