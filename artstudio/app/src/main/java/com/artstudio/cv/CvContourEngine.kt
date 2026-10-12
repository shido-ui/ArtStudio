package com.artstudio.cv

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic vector contour discovery and polygonal simplification.
 *
 * Implements:
 * 1. 8-Connected Moore-neighborhood boundary tracing.
 * 2. Topological area (Shoelace formula), perimeter, and hole classification.
 * 3. Ramer-Douglas-Peucker (RDP) polygonal decimation.
 */
object CvContourEngine {

    // 8-neighborhood directions clockwise starting from East
    private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    /**
     * Extracts discrete vector contours from a binary mask (pixels with value 255 are foreground).
     * Uses Moore-neighbor boundary tracing with Jacob's stopping criterion, correctly handling
     * 1-pixel thin spurs, touching strokes (figure-8 junctions), and concentric holes.
     */
    fun extractContours(
        binaryMask: IntArray,
        width: Int,
        height: Int,
        minArea: Float = 4.0f,
        rdpEpsilon: Float = 1.5f
    ): List<ContourCandidate> {
        val totalPixels = width * height
        if (totalPixels == 0) return emptyList()

        val visitedBoundaries = BooleanArray(totalPixels)
        val candidates = mutableListOf<ContourCandidate>()
        var nextId = 1

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                if (binaryMask[idx] == 255 && !visitedBoundaries[idx]) {
                    val leftIsBg = (x == 0 || binaryMask[idx - 1] == 0)
                    val rightIsBg = (x == width - 1 || binaryMask[idx + 1] == 0)
                    val topIsBg = (y == 0 || binaryMask[idx - width] == 0)
                    val bottomIsBg = (y == height - 1 || binaryMask[idx + width] == 0)

                    if (leftIsBg || rightIsBg || topIsBg || bottomIsBg) {
                        val isHole = !leftIsBg && rightIsBg
                        val startBgDir = if (leftIsBg) 4 else if (rightIsBg) 0 else if (topIsBg) 6 else 2

                        val polygon = traceMooreBorder(
                            mask = binaryMask,
                            visited = visitedBoundaries,
                            width = width,
                            height = height,
                            startX = x,
                            startY = y,
                            startBgDir = startBgDir
                        )

                        if (polygon.size >= 3) {
                            val area = computeSignedArea(polygon)
                            val absArea = abs(area)
                            if (absArea >= minArea) {
                                val perimeter = computePerimeter(polygon)
                                val bbox = computeBoundingBox(polygon)
                                val centroid = computeCentroid(polygon)
                                val simplified = rdpSimplify(polygon, rdpEpsilon)
                                candidates.add(
                                    ContourCandidate(
                                        id = nextId++,
                                        points = polygon,
                                        simplifiedPoints = simplified,
                                        isHole = isHole || (area < 0),
                                        area = absArea,
                                        perimeter = perimeter,
                                        boundingBox = bbox,
                                        centroid = centroid
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return candidates
    }

    private fun directionBetween(fromX: Int, fromY: Int, toX: Int, toY: Int): Int {
        val dx = toX - fromX
        val dy = toY - fromY
        return when {
            dx == 1 && dy == 0 -> 0
            dx == 1 && dy == 1 -> 1
            dx == 0 && dy == 1 -> 2
            dx == -1 && dy == 1 -> 3
            dx == -1 && dy == 0 -> 4
            dx == -1 && dy == -1 -> 5
            dx == 0 && dy == -1 -> 6
            dx == 1 && dy == -1 -> 7
            else -> 0
        }
    }

    /**
     * Moore-neighbor boundary tracing with Jacob's stopping criterion.
     * Prevents premature loop termination on thin spurs and avoids infinite cycling on touching strokes.
     */
    private fun traceMooreBorder(
        mask: IntArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        startBgDir: Int
    ): List<CvPoint> {
        val points = mutableListOf<CvPoint>()
        val p0X = startX
        val p0Y = startY

        // 1. Search clockwise around P0 starting after startBgDir for first foreground neighbor P1
        var p1X = -1
        var p1Y = -1

        for (step in 1..8) {
            val d = (startBgDir + step) % 8
            val nx = p0X + DX[d]
            val ny = p0Y + DY[d]
            if (nx in 0 until width && ny in 0 until height && mask[ny * width + nx] == 255) {
                p1X = nx
                p1Y = ny
                break
            }
        }

        points.add(CvPoint(p0X.toFloat(), p0Y.toFloat()))
        visited[p0Y * width + p0X] = true

        // Single isolated pixel
        if (p1X == -1) {
            return points
        }

        points.add(CvPoint(p1X.toFloat(), p1Y.toFloat()))
        visited[p1Y * width + p1X] = true

        var currX = p1X
        var currY = p1Y
        var backDir = directionBetween(p1X, p1Y, p0X, p0Y)

        val maxSteps = width * height * 4
        var steps = 0

        while (steps++ < maxSteps) {
            var foundNext = false
            var nextX = -1
            var nextY = -1

            for (step in 1..8) {
                val d = (backDir + step) % 8
                val nx = currX + DX[d]
                val ny = currY + DY[d]
                if (nx in 0 until width && ny in 0 until height && mask[ny * width + nx] == 255) {
                    nextX = nx
                    nextY = ny
                    foundNext = true
                    break
                }
            }

            if (!foundNext) break

            // Jacob's stopping criterion:
            // Stop when we are at P0 and the next pixel to visit is P1
            if (currX == p0X && currY == p0Y && nextX == p1X && nextY == p1Y) {
                if (points.size > 2 && points.last().x == p0X.toFloat() && points.last().y == p0Y.toFloat()) {
                    points.removeAt(points.size - 1)
                }
                break
            }

            points.add(CvPoint(nextX.toFloat(), nextY.toFloat()))
            visited[nextY * width + nextX] = true

            backDir = directionBetween(nextX, nextY, currX, currY)
            currX = nextX
            currY = nextY
        }

        return points
    }

    /**
     * Computes signed polygon area using the Shoelace formula.
     */
    fun computeSignedArea(points: List<CvPoint>): Double {
        val n = points.size
        if (n < 3) return 0.0

        var area = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += (points[i].x.toDouble() * points[j].y.toDouble()) -
                    (points[j].x.toDouble() * points[i].y.toDouble())
        }
        return area * 0.5
    }

    /**
     * Computes perimeter as total polyline length.
     */
    fun computePerimeter(points: List<CvPoint>): Double {
        val n = points.size
        if (n < 2) return 0.0

        var perim = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = points[j].x - points[i].x
            val dy = points[j].y - points[i].y
            perim += sqrt((dx * dx + dy * dy).toDouble())
        }
        return perim
    }

    private fun computeBoundingBox(points: List<CvPoint>): CvRect {
        if (points.isEmpty()) return CvRect(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return CvRect(minX, minY, maxX, maxY)
    }

    private fun computeCentroid(points: List<CvPoint>): CvPoint {
        if (points.isEmpty()) return CvPoint(0f, 0f)
        var sumX = 0f
        var sumY = 0f
        for (p in points) {
            sumX += p.x
            sumY += p.y
        }
        return CvPoint(sumX / points.size, sumY / points.size)
    }

    /**
     * Ramer-Douglas-Peucker (RDP) polygonal decimation algorithm.
     * Iterative implementation guaranteed to be stack-safe on 100k+ point contours.
     */
    fun rdpSimplify(points: List<CvPoint>, epsilon: Float): List<CvPoint> {
        if (points.size <= 2 || epsilon <= 0f) return points

        // For closed contours, split into two arcs at maximum distance
        val splitIdx = findFurthestPointIndex(points)
        if (splitIdx <= 0 || splitIdx >= points.size - 1) {
            return rdpSimplifyIterative(points, epsilon)
        }
        val arc1 = points.subList(0, splitIdx + 1)
        val arc2 = points.subList(splitIdx, points.size) + listOf(points.first())

        val simp1 = rdpSimplifyIterative(arc1, epsilon)
        val simp2 = rdpSimplifyIterative(arc2, epsilon)

        return (simp1.dropLast(1) + simp2).dropLast(1)
    }

    /**
     * Stack-safe iterative Ramer-Douglas-Peucker simplification using an explicit heap stack.
     */
    fun rdpSimplifyIterative(points: List<CvPoint>, epsilon: Float): List<CvPoint> {
        val n = points.size
        if (n <= 2 || epsilon <= 0f) return points

        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true

        var stackCap = 128
        var stackStart = IntArray(stackCap)
        var stackEnd = IntArray(stackCap)
        var stackSize = 0

        fun pushRange(s: Int, e: Int) {
            if (stackSize >= stackCap) {
                stackCap *= 2
                stackStart = stackStart.copyOf(stackCap)
                stackEnd = stackEnd.copyOf(stackCap)
            }
            stackStart[stackSize] = s
            stackEnd[stackSize] = e
            stackSize++
        }

        pushRange(0, n - 1)

        while (stackSize > 0) {
            stackSize--
            val s = stackStart[stackSize]
            val e = stackEnd[stackSize]

            if (e - s <= 1) continue

            val lineStart = points[s]
            val lineEnd = points[e]
            val dx = lineEnd.x - lineStart.x
            val dy = lineEnd.y - lineStart.y
            val lineLengthSq = dx * dx + dy * dy

            var maxDist = 0f
            var maxIdx = -1

            if (lineLengthSq == 0f) {
                for (i in (s + 1) until e) {
                    val pt = points[i]
                    val px = pt.x - lineStart.x
                    val py = pt.y - lineStart.y
                    val d = sqrt(px * px + py * py)
                    if (d > maxDist) {
                        maxDist = d
                        maxIdx = i
                    }
                }
            } else {
                val invLineLen = 1f / sqrt(lineLengthSq)
                val lineTerm = lineEnd.x * lineStart.y - lineEnd.y * lineStart.x
                for (i in (s + 1) until e) {
                    val pt = points[i]
                    val num = abs(dy * pt.x - dx * pt.y + lineTerm)
                    val dist = num * invLineLen
                    if (dist > maxDist) {
                        maxDist = dist
                        maxIdx = i
                    }
                }
            }

            if (maxDist > epsilon && maxIdx != -1) {
                keep[maxIdx] = true
                pushRange(s, maxIdx)
                pushRange(maxIdx, e)
            }
        }

        val result = ArrayList<CvPoint>(n / 4 + 2)
        for (i in 0 until n) {
            if (keep[i]) {
                result.add(points[i])
            }
        }
        return result
    }

    private fun findFurthestPointIndex(points: List<CvPoint>): Int {
        val first = points.first()
        var maxDistSq = -1f
        var maxIdx = points.size / 2

        for (i in 1 until points.size) {
            val dx = points[i].x - first.x
            val dy = points[i].y - first.y
            val d2 = dx * dx + dy * dy
            if (d2 > maxDistSq) {
                maxDistSq = d2
                maxIdx = i
            }
        }
        return maxIdx
    }

    private fun perpendicularDistance(pt: CvPoint, lineStart: CvPoint, lineEnd: CvPoint): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lineLengthSq = dx * dx + dy * dy

        if (lineLengthSq == 0f) {
            val px = pt.x - lineStart.x
            val py = pt.y - lineStart.y
            return sqrt(px * px + py * py)
        }

        // Area of triangle / base length
        val numerator = abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        return numerator / sqrt(lineLengthSq)
    }
}
