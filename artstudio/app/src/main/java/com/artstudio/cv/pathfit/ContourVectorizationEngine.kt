package com.artstudio.cv.pathfit

import com.artstudio.cv.*
import com.artstudio.vector.*
import kotlin.math.*

/**
 * Top-level vectorization engine that converts discrete contour candidates into
 * high-fidelity, editable geometric vector paths with corner preservation, hole containment,
 * disconnected component handling, and honest confidence scoring.
 */
object ContourVectorizationEngine {

    /**
     * Vectorizes a list of discrete contour candidates into editable geometric paths.
     *
     * @param contours Detected contours from [CvContourEngine].
     * @param settings Configuration settings for error tolerance, corner preservation, and modes.
     * @return Complete [ContourVectorizationResult] containing all fitted paths and metadata.
     */
    fun vectorizeContours(
        contours: List<ContourCandidate>,
        settings: PathFitSettings = PathFitSettings()
    ): ContourVectorizationResult {
        val startTime = System.currentTimeMillis()
        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        // 1. Filter micro-speckles
        val validContours = contours.filter { it.area >= settings.minFeatureArea }
        if (validContours.isEmpty()) {
            return ContourVectorizationResult(
                paths = emptyList(),
                totalRegions = 0,
                totalHoles = 0,
                disconnectedComponents = 0,
                maxGlobalDeviation = 0f,
                meanGlobalDeviation = 0f,
                elapsedTimeMs = 0L,
                peakMemoryBytes = 0L
            )
        }

        // 2. Separate outer boundaries and holes
        val outerContours = validContours.filter { !it.isHole }
        val holeContours = if (settings.preserveHoles) validContours.filter { it.isHole } else emptyList()

        val fittedPaths = mutableListOf<FittedPathCandidate>()

        // 3. Process outer contours (disconnected components)
        val outerRegionMap = mutableMapOf<Int, String>()
        var regionIndex = 1

        for (outer in outerContours) {
            val regionId = "region_$regionIndex"
            outerRegionMap[outer.id] = regionId
            regionIndex++

            val fitted = fitSingleContour(
                contour = outer,
                regionId = regionId,
                parentRegionId = null,
                settings = settings
            )
            fittedPaths.add(fitted)
        }

        // 4. Process inner holes and establish containment hierarchy
        var holeIndex = 1
        for (hole in holeContours) {
            // Find parent outer region containing hole centroid
            val parentOuter = findContainingOuterContour(hole, outerContours)
            val parentRegionId = parentOuter?.let { outerRegionMap[it.id] }
            val holeRegionId = if (parentRegionId != null) {
                "${parentRegionId}_hole_$holeIndex"
            } else {
                "hole_$holeIndex"
            }
            holeIndex++

            val fitted = fitSingleContour(
                contour = hole,
                regionId = holeRegionId,
                parentRegionId = parentRegionId,
                settings = settings
            )
            fittedPaths.add(fitted)
        }

        // 5. Compute global deviation metrics
        var maxGlobalDev = 0f
        var sumGlobalDev = 0.0

        for (p in fittedPaths) {
            if (p.maxContourDeviation > maxGlobalDev) {
                maxGlobalDev = p.maxContourDeviation
            }
            sumGlobalDev += p.meanContourDeviation
        }
        val meanGlobalDev = if (fittedPaths.isNotEmpty()) {
            (sumGlobalDev / fittedPaths.size).toFloat()
        } else {
            0f
        }

        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val peakMem = max(0L, memAfter - memBefore)
        val elapsedTime = System.currentTimeMillis() - startTime

        return ContourVectorizationResult(
            paths = fittedPaths,
            totalRegions = outerContours.size,
            totalHoles = holeContours.size,
            disconnectedComponents = outerContours.size,
            maxGlobalDeviation = maxGlobalDev,
            meanGlobalDeviation = meanGlobalDev,
            elapsedTimeMs = elapsedTime,
            peakMemoryBytes = peakMem
        )
    }

    /**
     * Convenience method to extract and vectorize contours directly from a binary raster mask.
     */
    fun vectorizeBinaryMask(
        mask: IntArray,
        width: Int,
        height: Int,
        settings: PathFitSettings = PathFitSettings()
    ): ContourVectorizationResult {
        val contours = CvContourEngine.extractContours(
            binaryMask = mask,
            width = width,
            height = height,
            minArea = settings.minFeatureArea,
            rdpEpsilon = 1.0f
        )
        return vectorizeContours(contours, settings)
    }

    /**
     * Fits a single discrete contour into a [FittedPathCandidate].
     */
    fun fitSingleContour(
        contour: ContourCandidate,
        regionId: String,
        parentRegionId: String? = null,
        settings: PathFitSettings = PathFitSettings()
    ): FittedPathCandidate {
        val points = contour.points
        val n = points.size

        if (n < 3) {
            val anchors = points.map { VectorAnchor(it.x, it.y) }
            val segs = listOf(VectorSegment.MoveTo(points[0].x, points[0].y))
            return FittedPathCandidate(
                regionId = regionId,
                parentRegionId = parentRegionId,
                isHole = contour.isHole,
                isClosed = false,
                anchors = anchors,
                segments = segs,
                sourceMapping = SourcePixelMapping(n, contour.boundingBox, points),
                provenance = PathProvenance(
                    sourceContourId = contour.id,
                    contourPerimeter = contour.perimeter,
                    contourArea = contour.area,
                    isHole = contour.isHole
                ),
                confidence = 0.50f,
                maxContourDeviation = 0f,
                meanContourDeviation = 0f
            )
        }

        // 1. Detect sharp corners
        val detectedCorners = if (settings.preserveCorners) {
            CornerDetector.detectCorners(
                points = points,
                isClosed = true,
                angleThresholdDeg = settings.cornerAngleThresholdDeg,
                windowRadius = settings.cornerWindowSize
            )
        } else {
            emptyList()
        }

        // 2. Segment and fit paths
        val (anchors, segments) = if (detectedCorners.isNotEmpty()) {
            fitContourWithCorners(points, detectedCorners, settings)
        } else {
            fitSmoothClosedContour(points, settings)
        }

        // 3. Build candidate
        val candidate = FittedPathCandidate(
            regionId = regionId,
            parentRegionId = parentRegionId,
            isHole = contour.isHole,
            isClosed = true,
            anchors = anchors,
            segments = segments,
            sourceMapping = SourcePixelMapping(
                pixelCount = points.size,
                boundingBox = contour.boundingBox,
                sampledPixels = points
            ),
            provenance = PathProvenance(
                sourceContourId = contour.id,
                contourPerimeter = contour.perimeter,
                contourArea = contour.area,
                isHole = contour.isHole
            ),
            confidence = 1.0f, // will update below
            maxContourDeviation = 0f,
            meanContourDeviation = 0f,
            cornersDetected = detectedCorners.map { it.point }
        )

        // 4. Measure deviation against source contour points
        val deviationMetrics = ContourRasterComparator.computeContourDeviation(candidate, points)

        // 5. Honest confidence scoring based on error and anchor efficiency
        val meanErrorRatio = (deviationMetrics.meanDeviation / (settings.maxFittingError * 1.5f)).coerceIn(0f, 1f)
        val baseConf = 0.98f - (meanErrorRatio * 0.40f)
        val finalConf = baseConf.coerceIn(0.10f, 0.99f)

        return candidate.copy(
            confidence = finalConf,
            maxContourDeviation = deviationMetrics.maxDeviation,
            meanContourDeviation = deviationMetrics.meanDeviation
        )
    }

    private fun fitContourWithCorners(
        points: List<CvPoint>,
        corners: List<CornerDetector.Corner>,
        settings: PathFitSettings
    ): Pair<List<VectorAnchor>, List<VectorSegment>> {
        val sortedCorners = corners.sortedBy { it.index }
        val m = sortedCorners.size
        val n = points.size

        val allAnchors = mutableListOf<VectorAnchor>()
        val allSegments = mutableListOf<VectorSegment>()

        // For each corner-to-corner span
        for (i in 0 until m) {
            val startIdx = sortedCorners[i].index
            val endIdx = sortedCorners[(i + 1) % m].index

            val arcPoints = extractArcPoints(points, startIdx, endIdx, n)
            val isLinear = isArcNearlyLinear(arcPoints, tolerance = 0.6f)

            if (isLinear || settings.fittingMode == PathFittingMode.POLYLINES) {
                // Linear segment between corners
                val pStart = arcPoints.first()
                val pEnd = arcPoints.last()

                if (i == 0) {
                    allSegments.add(VectorSegment.MoveTo(pStart.x, pStart.y))
                }
                allSegments.add(VectorSegment.LineTo(pEnd.x, pEnd.y))

                // Corner anchor at start
                allAnchors.add(
                    VectorAnchor(
                        x = pStart.x,
                        y = pStart.y,
                        handleInX = null,
                        handleInY = null,
                        handleOutX = null,
                        handleOutY = null,
                        isSmooth = false // Sharp corner!
                    )
                )
            } else {
                // Curved segment: fit cubic Bézier
                val bezSegments = SchneiderBezierFitter.fitCurve(
                    points = arcPoints,
                    maxError = settings.maxFittingError,
                    maxIterations = settings.maxNewtonRaphsonIterations
                )

                if (i == 0 && bezSegments.isNotEmpty()) {
                    allSegments.add(VectorSegment.MoveTo(bezSegments.first().p0.x, bezSegments.first().p0.y))
                }

                for (bez in bezSegments) {
                    allSegments.add(bez.toVectorSegment())
                }

                // Add corner anchor
                val firstSeg = bezSegments.first()
                allAnchors.add(
                    VectorAnchor(
                        x = firstSeg.p0.x,
                        y = firstSeg.p0.y,
                        handleInX = null,
                        handleInY = null,
                        handleOutX = firstSeg.c1.x,
                        handleOutY = firstSeg.c1.y,
                        isSmooth = false // Corner cusp
                    )
                )

                // Add intermediate smooth anchors if arc was split
                for (j in 0 until bezSegments.size - 1) {
                    val curr = bezSegments[j]
                    val next = bezSegments[j + 1]
                    allAnchors.add(
                        VectorAnchor(
                            x = curr.p3.x,
                            y = curr.p3.y,
                            handleInX = curr.c2.x,
                            handleInY = curr.c2.y,
                            handleOutX = next.c1.x,
                            handleOutY = next.c1.y,
                            isSmooth = true
                        )
                    )
                }
            }
        }

        allSegments.add(VectorSegment.Close)
        return Pair(allAnchors, allSegments)
    }

    private fun fitSmoothClosedContour(
        points: List<CvPoint>,
        settings: PathFitSettings
    ): Pair<List<VectorAnchor>, List<VectorSegment>> {
        val n = points.size

        if (settings.fittingMode == PathFittingMode.POLYLINES) {
            val rdpPoints = CvContourEngine.rdpSimplify(points, epsilon = settings.maxFittingError)
            val anchors = rdpPoints.map { VectorAnchor(it.x, it.y, isSmooth = true) }
            val segs = mutableListOf<VectorSegment>()
            if (rdpPoints.isNotEmpty()) {
                segs.add(VectorSegment.MoveTo(rdpPoints.first().x, rdpPoints.first().y))
                for (i in 1 until rdpPoints.size) {
                    segs.add(VectorSegment.LineTo(rdpPoints[i].x, rdpPoints[i].y))
                }
                segs.add(VectorSegment.Close)
            }
            return Pair(anchors, segs)
        }

        // For smooth curves without sharp corners, split into 4 quadrant arcs
        val splitIndices = listOf(0, n / 4, n / 2, (3 * n) / 4)
        val allBezSegments = mutableListOf<SchneiderBezierFitter.CubicBezierSegment>()

        for (i in 0 until 4) {
            val startIdx = splitIndices[i]
            val endIdx = if (i < 3) splitIndices[i + 1] else splitIndices[0]
            val arcPoints = extractArcPoints(points, startIdx, endIdx, n)

            val bezSegs = SchneiderBezierFitter.fitCurve(
                points = arcPoints,
                maxError = settings.maxFittingError,
                maxIterations = settings.maxNewtonRaphsonIterations
            )
            allBezSegments.addAll(bezSegs)
        }

        val anchors = SchneiderBezierFitter.segmentsToAnchors(allBezSegments, isClosed = true)
        val segs = mutableListOf<VectorSegment>()
        if (allBezSegments.isNotEmpty()) {
            segs.add(VectorSegment.MoveTo(allBezSegments.first().p0.x, allBezSegments.first().p0.y))
            for (bez in allBezSegments) {
                segs.add(bez.toVectorSegment())
            }
            segs.add(VectorSegment.Close)
        }

        return Pair(anchors, segs)
    }

    private fun extractArcPoints(
        points: List<CvPoint>,
        startIdx: Int,
        endIdx: Int,
        totalPoints: Int
    ): List<CvPoint> {
        val arc = mutableListOf<CvPoint>()
        if (startIdx <= endIdx) {
            for (i in startIdx..endIdx) {
                arc.add(points[i])
            }
        } else {
            for (i in startIdx until totalPoints) {
                arc.add(points[i])
            }
            for (i in 0..endIdx) {
                arc.add(points[i])
            }
        }
        return arc
    }

    private fun isArcNearlyLinear(arc: List<CvPoint>, tolerance: Float = 0.6f): Boolean {
        if (arc.size <= 2) return true
        val pStart = arc.first()
        val pEnd = arc.last()
        val dx = pEnd.x - pStart.x
        val dy = pEnd.y - pStart.y
        val len = hypot(dx, dy)
        if (len < 1e-4f) return true

        for (i in 1 until arc.size - 1) {
            val pt = arc[i]
            // Distance from pt to line (pStart, pEnd)
            val dist = abs(dy * pt.x - dx * pt.y + pEnd.x * pStart.y - pEnd.y * pStart.x) / len
            if (dist > tolerance) return false
        }
        return true
    }

    private fun findContainingOuterContour(
        hole: ContourCandidate,
        outers: List<ContourCandidate>
    ): ContourCandidate? {
        val hCentroid = hole.centroid
        for (outer in outers) {
            if (isPointInsideContour(hCentroid, outer.points)) {
                return outer
            }
        }
        // Fallback: check bounding box containment
        for (outer in outers) {
            val ob = outer.boundingBox
            val hb = hole.boundingBox
            if (hb.minX >= ob.minX && hb.maxX <= ob.maxX && hb.minY >= ob.minY && hb.maxY <= ob.maxY) {
                return outer
            }
        }
        return null
    }

    /**
     * Standard ray-casting point-in-polygon containment test.
     */
    fun isPointInsideContour(pt: CvPoint, polygon: List<CvPoint>): Boolean {
        var inside = false
        val n = polygon.size
        var j = n - 1
        for (i in 0 until n) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > pt.y) != (pj.y > pt.y) &&
                pt.x < (pj.x - pi.x) * (pt.y - pi.y) / (pj.y - pi.y + 1e-6f) + pi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
