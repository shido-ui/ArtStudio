package com.artstudio.cv.centreline

import com.artstudio.cv.CvContourEngine
import com.artstudio.cv.CvPoint
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Traverses 8-connected skeleton graphs to extract plausible single-pen centreline paths.
 *
 * Implements:
 * 1. Topological node classification (Endpoints, Regular path pixels, Junction forks).
 * 2. Connected-component junction clustering.
 * 3. Bidirectional path traversal with RDP simplification and distance-transform stroke width estimation.
 * 4. Structural ambiguity detection:
 *    - Ambiguous multi-way forks without clear collinear continuation.
 *    - Potential broken stroke gaps between aligned endpoints.
 *    - Clustered junction intersections.
 * 5. Honest confidence scoring based on length, curvature, and topological clarity.
 */
object CentrelinePathTracer {

    private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    /**
     * Traces all plausible paths, detects junctions, and flags structural ambiguities for review.
     */
    fun trace(
        skeleton: IntArray,
        distTransform: FloatArray,
        width: Int,
        height: Int,
        settings: CentrelineSettings
    ): Pair<List<CentrelinePathCandidate>, Pair<List<JunctionNode>, List<ReviewAmbiguity>>> {
        val total = width * height
        if (total == 0) return Pair(emptyList(), Pair(emptyList(), emptyList()))

        // 1. Identify pixel classifications
        val neighborCounts = IntArray(total)
        val isJunctionPixel = BooleanArray(total)
        val isEndpointPixel = BooleanArray(total)

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (skeleton[idx] != 255) continue

                val count = countSkeletonNeighbors(skeleton, width, height, x, y)
                neighborCounts[idx] = count

                if (count == 1) {
                    isEndpointPixel[idx] = true
                } else if (count >= 3) {
                    val transitions = countTransitionsAt(skeleton, width, height, x, y)
                    if (transitions >= 3) {
                        isJunctionPixel[idx] = true
                    }
                }
            }
        }

        // 2. Cluster adjacent junction pixels into JunctionNodes
        val (junctionNodes, junctionPixelMap) = clusterJunctions(
            isJunctionPixel,
            skeleton,
            width,
            height
        )

        // 3. Trace paths from endpoints and junctions
        val visitedEdges = BooleanArray(total)
        val candidates = mutableListOf<CentrelinePathCandidate>()
        var nextPathId = 1
        val ambiguities = mutableListOf<ReviewAmbiguity>()
        var nextAmbiguityId = 1

        // 3A. Trace starting from endpoints
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (skeleton[idx] == 255 && isEndpointPixel[idx] && !visitedEdges[idx]) {
                    val pathPoints = tracePathSegment(
                        skeleton,
                        isJunctionPixel,
                        visitedEdges,
                        width,
                        height,
                        startX = x,
                        startY = y
                    )
                    if (pathPoints.size >= 2) {
                        val candidate = buildCandidate(
                            id = nextPathId++,
                            points = pathPoints,
                            distTransform = distTransform,
                            width = width,
                            junctionNodes = junctionNodes,
                            junctionPixelMap = junctionPixelMap,
                            settings = settings
                        )
                        if (shouldKeepCandidate(candidate, settings)) {
                            candidates.add(candidate)
                        }
                    }
                }
            }
        }

        // 3B. Trace remaining segments starting adjacent to junctions
        for (jNode in junctionNodes) {
            val jx = jNode.location.x.toInt().coerceIn(0, width - 1)
            val jy = jNode.location.y.toInt().coerceIn(0, height - 1)

            for (d in 0 until 8) {
                val nx = jx + DX[d]
                val ny = jy + DY[d]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (skeleton[nIdx] == 255 && !isJunctionPixel[nIdx] && !visitedEdges[nIdx]) {
                        val pathPoints = tracePathSegment(
                            skeleton,
                            isJunctionPixel,
                            visitedEdges,
                            width,
                            height,
                            startX = nx,
                            startY = ny
                        )
                        if (pathPoints.size >= 2) {
                            // Prepend junction location
                            val fullPoints = listOf(jNode.location) + pathPoints
                            val candidate = buildCandidate(
                                id = nextPathId++,
                                points = fullPoints,
                                distTransform = distTransform,
                                width = width,
                                junctionNodes = junctionNodes,
                                junctionPixelMap = junctionPixelMap,
                                settings = settings
                            )
                            if (shouldKeepCandidate(candidate, settings)) {
                                candidates.add(candidate)
                            }
                        }
                    }
                }
            }
        }

        // 3C. Trace remaining unvisited closed loops
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (skeleton[idx] == 255 && !visitedEdges[idx] && !isJunctionPixel[idx]) {
                    val loopPoints = traceClosedLoop(
                        skeleton,
                        visitedEdges,
                        width,
                        height,
                        startX = x,
                        startY = y
                    )
                    if (loopPoints.size >= 3) {
                        val candidate = buildCandidate(
                            id = nextPathId++,
                            points = loopPoints,
                            distTransform = distTransform,
                            width = width,
                            junctionNodes = junctionNodes,
                            junctionPixelMap = junctionPixelMap,
                            settings = settings,
                            isLoop = true
                        )
                        candidates.add(candidate)
                    }
                }
            }
        }

        // 4. Update JunctionNode connectedPathIds
        val finalJunctions = junctionNodes.map { jNode ->
            val connectedPaths = candidates.filter { c ->
                c.startConnectedJunctionId == jNode.id ||
                c.endConnectedJunctionId == jNode.id ||
                distanceSq(c.startPoint, jNode.location) <= 16f ||
                distanceSq(c.endPoint, jNode.location) <= 16f
            }.map { it.id }.distinct()

            jNode.copy(
                degree = maxOf(jNode.degree, connectedPaths.size),
                connectedPathIds = connectedPaths
            )
        }

        // 5. Detect Ambiguities: Ambiguous Forks
        for (jNode in finalJunctions) {
            val incidentPaths = candidates.filter { it.id in jNode.connectedPathIds }
            if (incidentPaths.size >= 3 || jNode.degree >= 3) {
                val hasClearContinuation = checkClearJunctionContinuation(
                    jNode,
                    incidentPaths,
                    settings.junctionCollinearAngleDeg
                )

                if (!hasClearContinuation) {
                    val ambiguity = ReviewAmbiguity(
                        id = nextAmbiguityId++,
                        type = AmbiguityType.AMBIGUOUS_FORK,
                        location = jNode.location,
                        involvedPathIds = jNode.connectedPathIds,
                        description = "Junction #${jNode.id} has ${jNode.connectedPathIds.size.coerceAtLeast(jNode.degree)} branches without a dominant through-stroke continuation.",
                        confidencePenalty = 0.15f,
                        suggestedResolution = "Review branch connectivity or preserve as independent stroke segments."
                    )
                    ambiguities.add(ambiguity)
                }
            }
        }

        // 6. Detect Ambiguities: Potential Stroke Breaks (Gaps)
        val endpointCandidates = mutableListOf<Triple<Int, CvPoint, CvPoint>>() // pathId, endpoint, tangent
        for (c in candidates) {
            if (!c.isLoop) {
                if (c.startConnectedJunctionId == null) {
                    endpointCandidates.add(Triple(c.id, c.startPoint, c.startTangent))
                }
                if (c.endConnectedJunctionId == null) {
                    endpointCandidates.add(Triple(c.id, c.endPoint, c.endTangent))
                }
            }
        }

        for (i in endpointCandidates.indices) {
            val (pId1, ep1, tan1) = endpointCandidates[i]
            for (j in i + 1 until endpointCandidates.size) {
                val (pId2, ep2, tan2) = endpointCandidates[j]
                if (pId1 == pId2) continue

                val dx = ep2.x - ep1.x
                val dy = ep2.y - ep1.y
                val dist = sqrt(dx * dx + dy * dy)

                if (dist <= settings.gapDetectionDistance && dist > 0.5f) {
                    // Check if tangents align across gap (pointing towards each other)
                    val dirX = dx / dist
                    val dirY = dy / dist
                    // tan1 pointing towards ep2?
                    val dot1 = tan1.x * dirX + tan1.y * dirY
                    // tan2 pointing backwards towards ep1?
                    val dot2 = -tan2.x * dirX + -tan2.y * dirY

                    if (dot1 > 0.4f || dot2 > 0.4f || dist <= 3.0f) {
                        val midPt = CvPoint((ep1.x + ep2.x) / 2f, (ep1.y + ep2.y) / 2f)
                        val ambiguity = ReviewAmbiguity(
                            id = nextAmbiguityId++,
                            type = AmbiguityType.POTENTIAL_STROKE_BREAK,
                            location = midPt,
                            involvedPathIds = listOf(pId1, pId2),
                            description = "Potential stroke break detected between path #$pId1 and path #$pId2 across gap of ${"%.1f".format(dist)} px.",
                            confidencePenalty = 0.10f,
                            suggestedResolution = "Review whether to bridge gap into a single stroke or keep separate."
                        )
                        ambiguities.add(ambiguity)
                    }
                }
            }
        }

        // 7. Attach Ambiguities to Path Candidates and apply confidence penalties
        val resolvedCandidates = candidates.map { c ->
            val pathAmbiguities = ambiguities.filter { c.id in it.involvedPathIds }
            if (pathAmbiguities.isNotEmpty()) {
                val totalPenalty = pathAmbiguities.sumOf { it.confidencePenalty.toDouble() }.toFloat()
                val adjustedConf = (c.confidence - totalPenalty).coerceIn(0.10f, 0.95f)
                c.copy(confidence = adjustedConf, ambiguities = pathAmbiguities)
            } else {
                c
            }
        }

        return Pair(resolvedCandidates, Pair(finalJunctions, ambiguities))
    }

    private fun shouldKeepCandidate(c: CentrelinePathCandidate, settings: CentrelineSettings): Boolean {
        if (!settings.preserveShortDetails && c.isShortDetail) return false
        return true
    }

    private fun buildCandidate(
        id: Int,
        points: List<CvPoint>,
        distTransform: FloatArray,
        width: Int,
        junctionNodes: List<JunctionNode>,
        junctionPixelMap: Map<Int, Int>,
        settings: CentrelineSettings,
        isLoop: Boolean = false
    ): CentrelinePathCandidate {
        val simplified = CvContourEngine.rdpSimplify(points, settings.rdpEpsilon)

        var length = 0f
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            length += sqrt(dx * dx + dy * dy)
        }

        // Estimate stroke width from distance transform
        var sumWidth = 0f
        var widthCount = 0
        for (pt in points) {
            val px = pt.x.toInt()
            val py = pt.y.toInt()
            val pIdx = py * width + px
            if (pIdx in distTransform.indices) {
                sumWidth += 2f * distTransform[pIdx].coerceAtLeast(0.5f)
                widthCount++
            }
        }
        val estWidth = if (widthCount > 0) sumWidth / widthCount else 2.0f

        val start = points.first()
        val end = points.last()

        // Direction / Tangents
        val startTangent = computeTangent(points, fromStart = true)
        val endTangent = computeTangent(points, fromStart = false)

        val isShort = length < settings.minFeatureLength

        // Check if endpoints touch junction clusters (either exact pixel or within 4px Euclidean distance)
        val startJunc = junctionNodes.firstOrNull { distanceSq(start, it.location) <= 16f }
        val endJunc = junctionNodes.firstOrNull { distanceSq(end, it.location) <= 16f }
        val startIdx = start.y.toInt() * width + start.x.toInt()
        val endIdx = end.y.toInt() * width + end.x.toInt()
        val startJuncId = startJunc?.id ?: junctionPixelMap[startIdx]
        val endJuncId = endJunc?.id ?: junctionPixelMap[endIdx]

        // Base confidence: 0.90 for clean long paths, lower for short spurs
        var conf = 0.90f
        if (isShort) conf -= 0.25f
        if (points.size < 4) conf -= 0.10f
        conf = conf.coerceIn(0.20f, 0.95f)

        return CentrelinePathCandidate(
            id = id,
            rawSkeletonPoints = points,
            simplifiedPoints = simplified,
            startPoint = start,
            endPoint = end,
            startTangent = startTangent,
            endTangent = endTangent,
            length = length,
            estimatedStrokeWidth = estWidth,
            confidence = conf,
            isLoop = isLoop,
            isShortDetail = isShort,
            startConnectedJunctionId = startJuncId,
            endConnectedJunctionId = endJuncId
        )
    }

    private fun computeTangent(points: List<CvPoint>, fromStart: Boolean): CvPoint {
        if (points.size < 2) return CvPoint(1f, 0f)
        val p1 = if (fromStart) points[0] else points[points.size - 1]
        val sampleIdx = if (fromStart) minOf(3, points.size - 1) else maxOf(0, points.size - 4)
        val p2 = points[sampleIdx]

        val dx = if (fromStart) p2.x - p1.x else p1.x - p2.x
        val dy = if (fromStart) p2.y - p1.y else p1.y - p2.y
        val len = sqrt(dx * dx + dy * dy)
        return if (len > 0f) CvPoint(dx / len, dy / len) else CvPoint(1f, 0f)
    }

    private fun checkClearJunctionContinuation(
        junction: JunctionNode,
        paths: List<CentrelinePathCandidate>,
        angleThresholdDeg: Float
    ): Boolean {
        if (paths.size < 2) return false
        var collinearPairCount = 0

        for (i in paths.indices) {
            val p1 = paths[i]
            val dir1 = getRadiatingDirection(p1, junction.location)

            for (j in i + 1 until paths.size) {
                val p2 = paths[j]
                val dir2 = getRadiatingDirection(p2, junction.location)

                // Opposite directions means branches are collinear straight through: dir1 . dir2 approx -1
                val dot = dir1.x * dir2.x + dir1.y * dir2.y
                val angleDeg = Math.toDegrees(acos(dot.coerceIn(-1f, 1f).toDouble())).toFloat()

                // Near 180 degrees means branches continue straight through
                if (abs(180f - angleDeg) <= angleThresholdDeg) {
                    collinearPairCount++
                }
            }
        }

        // Exactly 1 collinear pair means a clear through-stroke with branch/stem
        return collinearPairCount == 1
    }

    private fun getRadiatingDirection(p: CentrelinePathCandidate, jLoc: CvPoint): CvPoint {
        val pts = if (p.rawSkeletonPoints.isNotEmpty()) p.rawSkeletonPoints else p.simplifiedPoints
        if (pts.size < 2) return CvPoint(1f, 0f)
        val dStart = distanceSq(pts.first(), jLoc)
        val dEnd = distanceSq(pts.last(), jLoc)
        val isStartNear = dStart <= dEnd
        val pFrom = if (isStartNear) pts.first() else pts.last()
        val sampleIdx = if (isStartNear) minOf(3, pts.size - 1) else maxOf(0, pts.size - 4)
        val pTo = pts[sampleIdx]
        val dx = pTo.x - pFrom.x
        val dy = pTo.y - pFrom.y
        val len = sqrt(dx * dx + dy * dy)
        return if (len > 0f) CvPoint(dx / len, dy / len) else CvPoint(1f, 0f)
    }

    private fun distanceSq(a: CvPoint, b: CvPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return dx * dx + dy * dy
    }

    private fun tracePathSegment(
        skeleton: IntArray,
        isJunctionPixel: BooleanArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int
    ): List<CvPoint> {
        val points = mutableListOf<CvPoint>()
        var currX = startX
        var currY = startY

        while (true) {
            val idx = currY * width + currX
            visited[idx] = true
            points.add(CvPoint(currX.toFloat(), currY.toFloat()))

            // If we reached a junction pixel, terminate path segment here
            if (isJunctionPixel[idx] && points.size > 1) {
                break
            }

            // Find next unvisited skeleton neighbor
            var nextX = -1
            var nextY = -1

            for (d in 0 until 8) {
                val nx = currX + DX[d]
                val ny = currY + DY[d]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (skeleton[nIdx] == 255 && !visited[nIdx]) {
                        nextX = nx
                        nextY = ny
                        break
                    }
                }
            }

            if (nextX == -1) break
            currX = nextX
            currY = nextY
        }

        return points
    }

    private fun traceClosedLoop(
        skeleton: IntArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int
    ): List<CvPoint> {
        val points = mutableListOf<CvPoint>()
        var currX = startX
        var currY = startY

        while (true) {
            val idx = currY * width + currX
            visited[idx] = true
            points.add(CvPoint(currX.toFloat(), currY.toFloat()))

            var nextX = -1
            var nextY = -1

            for (d in 0 until 8) {
                val nx = currX + DX[d]
                val ny = currY + DY[d]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (skeleton[nIdx] == 255 && !visited[nIdx]) {
                        nextX = nx
                        nextY = ny
                        break
                    }
                }
            }

            if (nextX == -1) break
            currX = nextX
            currY = nextY
        }

        return points
    }

    private fun clusterJunctions(
        isJunctionPixel: BooleanArray,
        skeleton: IntArray,
        width: Int,
        height: Int
    ): Pair<List<JunctionNode>, Map<Int, Int>> {
        val total = width * height
        val visitedJunctions = BooleanArray(total)
        val junctionNodes = mutableListOf<JunctionNode>()
        val pixelToJunctionMap = mutableMapOf<Int, Int>()
        var nextJunctionId = 1

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (isJunctionPixel[idx] && !visitedJunctions[idx]) {
                    // Flood fill connected junction pixels
                    val cluster = mutableListOf<Pair<Int, Int>>()
                    val queue = ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(x, y))
                    visitedJunctions[idx] = true

                    while (queue.isNotEmpty()) {
                        val (cx, cy) = queue.removeFirst()
                        cluster.add(Pair(cx, cy))

                        for (d in 0 until 8) {
                            val nx = cx + DX[d]
                            val ny = cy + DY[d]
                            if (nx in 0 until width && ny in 0 until height) {
                                val nIdx = ny * width + nx
                                if (isJunctionPixel[nIdx] && !visitedJunctions[nIdx]) {
                                    visitedJunctions[nIdx] = true
                                    queue.add(Pair(nx, ny))
                                }
                            }
                        }
                    }

                    // Compute cluster centroid
                    val avgX = cluster.map { it.first }.average().toFloat()
                    val avgY = cluster.map { it.second }.average().toFloat()
                    val jId = nextJunctionId++

                    for (pt in cluster) {
                        pixelToJunctionMap[pt.second * width + pt.first] = jId
                    }

                    junctionNodes.add(
                        JunctionNode(
                            id = jId,
                            location = CvPoint(avgX, avgY),
                            degree = cluster.size.coerceAtLeast(3),
                            connectedPathIds = emptyList(),
                            isAmbiguous = false
                        )
                    )
                }
            }
        }

        return Pair(junctionNodes, pixelToJunctionMap)
    }

    private fun countSkeletonNeighbors(skeleton: IntArray, width: Int, height: Int, x: Int, y: Int): Int {
        var count = 0
        for (d in 0 until 8) {
            val nx = x + DX[d]
            val ny = y + DY[d]
            if (nx in 0 until width && ny in 0 until height) {
                if (skeleton[ny * width + nx] == 255) count++
            }
        }
        return count
    }

    private fun countTransitionsAt(skeleton: IntArray, width: Int, height: Int, x: Int, y: Int): Int {
        val neighbors = IntArray(8)
        for (d in 0 until 8) {
            val nx = x + DX[d]
            val ny = y + DY[d]
            neighbors[d] = if (nx in 0 until width && ny in 0 until height && skeleton[ny * width + nx] == 255) 1 else 0
        }
        var transitions = 0
        for (i in 0 until 8) {
            if (neighbors[i] == 0 && neighbors[(i + 1) % 8] == 1) transitions++
        }
        return transitions
    }
}
