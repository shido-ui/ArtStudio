package com.artstudio.cv.region

import com.artstudio.cv.CvPoint
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic Computer Vision Region and Mask Generation Engine.
 *
 * Provides:
 * 1. Three-tier mask separation: [sourceMask], [correctedMask], and [generatedFill].
 * 2. 4-channel color distance matching (ARGB) respecting full canvas transparency.
 * 3. Morphological gap closing for 1-to-N pixel micro-breaks in line art boundaries.
 * 4. Dual-flood leak detection localizing constriction necks and saddle points.
 * 5. Topological interior hole preservation vs unconstrained hole filling.
 * 6. Sub-pixel anti-aliased edge smoothing and boundary bleed expansion (+dilate/-erode) to eliminate white halos.
 * 7. Interactive inspection and user correction bridge application.
 * 8. Statistical spatial overlap metrics (IoU, Dice, Precision, Recall).
 */
object RegionMaskEngine {

    /**
     * Normalized 4-channel ARGB color distance in range [0.0, 1.0].
     * Treats full alpha transparency (alpha <= 5) symmetrically with zero distance.
     */
    fun colorDistance(c1: Int, c2: Int): Float {
        val a1 = (c1 ushr 24) and 0xFF
        val a2 = (c2 ushr 24) and 0xFF

        // If both pixels are transparent, they match identically
        if (a1 <= 5 && a2 <= 5) return 0.0f

        val da = (a1 - a2).toFloat() / 255.0f
        val dr = (((c1 ushr 16) and 0xFF) - ((c2 ushr 16) and 0xFF)).toFloat() / 255.0f
        val dg = (((c1 ushr 8) and 0xFF) - ((c2 ushr 8) and 0xFF)).toFloat() / 255.0f
        val db = ((c1 and 0xFF) - (c2 and 0xFF)).toFloat() / 255.0f

        val rgbDistSq = (dr * dr + dg * dg + db * db) / 3.0f
        val alphaDistSq = da * da

        // Weighted distance: 30% alpha transition, 70% chromatic RGB step
        return sqrt((alphaDistSq * 0.3f + rgbDistSq * 0.7f).coerceIn(0f, 1f))
    }

    /**
     * Segments a connected region starting from ([seedX], [seedY]) using [config].
     * Computes raw [sourceMask], refined [correctedMask], and optional [generatedFill].
     */
    fun segmentRegion(
        pixels: IntArray,
        width: Int,
        height: Int,
        seedX: Int,
        seedY: Int,
        config: RegionMaskConfig = RegionMaskConfig(),
        groundTruthMask: ByteArray? = null
    ): RegionSegmentationResult {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        val total = width * height
        require(pixels.size == total) { "Pixel buffer size (${pixels.size}) does not match $width x $height" }
        require(seedX in 0 until width && seedY in 0 until height) {
            "Seed ($seedX, $seedY) out of bounds for canvas ($width x $height)"
        }

        val seedColor = pixels[seedY * width + seedX]
        val regionId = "region_${UUID.randomUUID().toString().take(8)}"

        // 1. Mark barrier pixels where color exceeds tolerance
        val barrier = BooleanArray(total)
        for (i in 0 until total) {
            barrier[i] = colorDistance(pixels[i], seedColor) > config.colorTolerance
        }

        // 2. Perform raw unconstrained flood fill to create sourceMask
        val rawFlood = runFloodFill(barrier, width, height, seedX, seedY)
        val sourceMask = ByteArray(total)
        var rawCount = 0
        for (i in 0 until total) {
            if (rawFlood[i]) {
                sourceMask[i] = 255.toByte()
                rawCount++
            }
        }

        // 3. Gap closing & leak detection
        val reviews = mutableListOf<UncertainBoundaryReview>()
        var isLeaky = false
        var baseMask = rawFlood

        if (config.detectLeaks && config.gapClosingRadius > 0) {
            val r = config.gapClosingRadius
            val dilatedBarrier = dilate(barrier, width, height, r)

            // Flood inside dilated barrier
            val closedFlood = runFloodFill(dilatedBarrier, width, height, seedX, seedY)

            // Expand closed flood by r constrained within !barrier
            val dilatedClosed = dilate(closedFlood, width, height, r)
            val expandedClosed = BooleanArray(total) { idx -> dilatedClosed[idx] && !barrier[idx] }

            var closedCount = 0
            for (i in 0 until total) {
                if (expandedClosed[i]) closedCount++
            }

            // Detect leaked spill that reaches canvas border
            val spill = BooleanArray(total) { idx -> rawFlood[idx] && !expandedClosed[idx] }
            val borderSpill = floodFromBorderInSpill(spill, width, height)

            val trueLeakPixels = mutableListOf<Int>()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    if (expandedClosed[idx]) {
                        for (dy in -1..1) {
                            val ny = y + dy
                            if (ny !in 0 until height) continue
                            for (dx in -1..1) {
                                val nx = x + dx
                                if (nx in 0 until width && borderSpill[ny * width + nx]) {
                                    trueLeakPixels.add(idx)
                                    break
                                }
                            }
                            if (trueLeakPixels.isNotEmpty() && trueLeakPixels.last() == idx) break
                        }
                    }
                }
            }

            if (trueLeakPixels.isNotEmpty() || (rawCount.toFloat() / total >= config.leakThresholdRatio && closedCount < rawCount / 2)) {
                isLeaky = true
                baseMask = expandedClosed

                if (trueLeakPixels.isNotEmpty()) {
                    val avgX = trueLeakPixels.sumOf { it % width }.toFloat() / trueLeakPixels.size
                    val avgY = trueLeakPixels.sumOf { it / width }.toFloat() / trueLeakPixels.size
                    val leakPt = CvPoint(avgX, avgY)

                    val endpoints = findBarrierEndpoints(barrier, width, height, avgX, avgY, r * 2 + 2)
                    val gapDist = sqrt(
                        (endpoints.first.x - endpoints.second.x) * (endpoints.first.x - endpoints.second.x) +
                                (endpoints.first.y - endpoints.second.y) * (endpoints.first.y - endpoints.second.y)
                    )

                    val severity = when {
                        gapDist <= 2.5f -> LeakSeverity.MINOR_TINY_GAP
                        gapDist <= 5.5f -> LeakSeverity.MODERATE_GAP
                        else -> LeakSeverity.WIDE_RUPTURE
                    }

                    reviews.add(
                        UncertainBoundaryReview(
                            leakLocation = leakPt,
                            gapDistance = gapDist,
                            severity = severity,
                            proposedBridge = endpoints
                        )
                    )
                }
            }
        } else if (config.detectLeaks && rawCount.toFloat() / total >= config.leakThresholdRatio) {
            isLeaky = true
            reviews.add(
                UncertainBoundaryReview(
                    leakLocation = CvPoint(seedX.toFloat(), seedY.toFloat()),
                    gapDistance = 10f,
                    severity = LeakSeverity.WIDE_RUPTURE,
                    proposedBridge = Pair(CvPoint(seedX.toFloat(), seedY.toFloat()), CvPoint(seedX.toFloat(), seedY.toFloat()))
                )
            )
        }

        // 4. Hole Preservation
        var hasHoles = false
        val holeAdjustedMask = BooleanArray(total)
        for (i in 0 until total) holeAdjustedMask[i] = baseMask[i]

        val exteriorReach = floodExterior(baseMask, width, height)
        for (i in 0 until total) {
            if (!baseMask[i] && !exteriorReach[i]) {
                hasHoles = true
                if (!config.preserveHoles) {
                    holeAdjustedMask[i] = true // Fill in hole
                }
            }
        }

        // 5. Despeckle filtering
        val despeckledMask = if (config.despeckleRadius > 0) {
            despeckle(holeAdjustedMask, width, height, config.despeckleRadius)
        } else {
            holeAdjustedMask
        }

        // 6. Boundary expansion / choke (dilation/erosion)
        val expandedMask = when {
            config.boundaryExpansion > 0 -> dilate(despeckledMask, width, height, config.boundaryExpansion)
            config.boundaryExpansion < 0 -> erode(despeckledMask, width, height, -config.boundaryExpansion)
            else -> despeckledMask
        }

        // 7. Anti-alias edge smoothing
        val correctedMask = ByteArray(total)
        if (config.antiAliasSmoothing) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    if (isBoundaryPixel(expandedMask, width, height, x, y)) {
                        val count = countMaskNeighbors(expandedMask, width, height, x, y)
                        val alpha = ((count * 255) / 9).coerceIn(0, 255)
                        correctedMask[idx] = alpha.toByte()
                    } else if (expandedMask[idx]) {
                        correctedMask[idx] = 255.toByte()
                    } else {
                        correctedMask[idx] = 0.toByte()
                    }
                }
            }
        } else {
            for (i in 0 until total) {
                correctedMask[i] = if (expandedMask[i]) 255.toByte() else 0.toByte()
            }
        }

        // 8. Render generated fill layer
        val generatedFill = config.fillColor?.let { fc ->
            renderFillLayer(correctedMask, width, height, fc, config.fillOpacity)
        }

        // 9. Classify boundary type
        val boundaryType = classifyBoundary(pixels, expandedMask, width, height, isLeaky)

        // 10. Compute overlap metrics against ground-truth if provided
        val metrics = groundTruthMask?.let { gt ->
            calculateOverlapMetrics(correctedMask, gt, width, height)
        }

        var finalPixelCount = 0
        for (i in 0 until total) {
            if ((correctedMask[i].toInt() and 0xFF) > 128) finalPixelCount++
        }

        return RegionSegmentationResult(
            regionId = regionId,
            width = width,
            height = height,
            sourceMask = sourceMask,
            correctedMask = correctedMask,
            generatedFill = generatedFill,
            pixelCount = finalPixelCount,
            boundaryType = boundaryType,
            uncertainBoundaries = reviews,
            isLeaky = isLeaky,
            hasHoles = hasHoles,
            overlapMetrics = metrics
        )
    }

    /**
     * Applies an explicit user correction bridge stroke between [bridgeStroke] endpoints,
     * sealing a previously identified leaky gap.
     */
    fun applyUserCorrection(
        result: RegionSegmentationResult,
        pixels: IntArray,
        reviewId: String,
        bridgeStroke: Pair<CvPoint, CvPoint>,
        strokeWidth: Int = 1,
        config: RegionMaskConfig = RegionMaskConfig()
    ): RegionSegmentationResult {
        val width = result.width
        val height = result.height

        // Find the matching review
        val review = result.uncertainBoundaries.find { it.id == reviewId }
            ?: result.uncertainBoundaries.firstOrNull()

        // Clone pixels and burn an ink barrier along the bridge line
        val modifiedPixels = pixels.clone()
        val barrierColor = 0xFF000000.toInt()
        rasterizeLine(
            pixels = modifiedPixels,
            width = width,
            height = height,
            x0 = bridgeStroke.first.x.roundToInt(),
            y0 = bridgeStroke.first.y.roundToInt(),
            x1 = bridgeStroke.second.x.roundToInt(),
            y1 = bridgeStroke.second.y.roundToInt(),
            thickness = strokeWidth,
            color = barrierColor
        )

        // Find a representative seed inside the original region
        var seedX = width / 2
        var seedY = height / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((result.correctedMask[y * width + x].toInt() and 0xFF) > 200) {
                    seedX = x
                    seedY = y
                    break
                }
            }
        }

        // Re-segment with sealed barrier
        val updated = segmentRegion(
            pixels = modifiedPixels,
            width = width,
            height = height,
            seedX = seedX,
            seedY = seedY,
            config = config
        )

        // Mark the review as user-corrected
        val updatedReviews = result.uncertainBoundaries.map { r ->
            if (r.id == (review?.id ?: "")) {
                r.copy(isUserCorrected = true)
            } else {
                r
            }
        }

        return updated.copy(
            regionId = result.regionId,
            uncertainBoundaries = updatedReviews,
            isLeaky = updatedReviews.any { !it.isUserCorrected && updated.isLeaky },
            boundaryType = if (updatedReviews.all { it.isUserCorrected }) RegionBoundaryType.CLOSED_OUTLINE else updated.boundaryType
        )
    }

    /**
     * Segments all connected regions across the entire canvas image.
     */
    fun segmentAllRegions(
        pixels: IntArray,
        width: Int,
        height: Int,
        config: RegionMaskConfig = RegionMaskConfig()
    ): List<RegionSegmentationResult> {
        val total = width * height
        val visited = BooleanArray(total)
        val regions = mutableListOf<RegionSegmentationResult>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!visited[idx]) {
                    val seg = segmentRegion(pixels, width, height, x, y, config)
                    for (i in 0 until total) {
                        if (seg.sourceMask[i] != 0.toByte()) {
                            visited[i] = true
                        }
                    }
                    visited[idx] = true
                    regions.add(seg)
                }
            }
        }

        return regions
    }

    /**
     * Renders a 32-bit ARGB fill layer from [correctedMask].
     */
    fun renderFillLayer(
        correctedMask: ByteArray,
        width: Int,
        height: Int,
        fillColor: Int,
        fillOpacity: Float = 1.0f
    ): IntArray {
        val total = width * height
        val fill = IntArray(total)
        val baseAlpha = (fillColor ushr 24) and 0xFF
        val r = (fillColor ushr 16) and 0xFF
        val g = (fillColor ushr 8) and 0xFF
        val b = fillColor and 0xFF

        for (i in 0 until total) {
            val maskVal = correctedMask[i].toInt() and 0xFF
            if (maskVal > 0) {
                val effectiveAlpha = (baseAlpha * fillOpacity * (maskVal / 255.0f)).roundToInt().coerceIn(0, 255)
                fill[i] = (effectiveAlpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return fill
    }

    /**
     * Statistical spatial overlap metrics comparing an extracted region mask against ground-truth.
     */
    fun calculateOverlapMetrics(
        mask: ByteArray,
        groundTruth: ByteArray,
        width: Int,
        height: Int
    ): MaskOverlapMetrics {
        require(mask.size == width * height && groundTruth.size == width * height)
        var intersection = 0
        var union = 0
        var gtCount = 0
        var maskCount = 0
        val total = width * height

        for (i in 0 until total) {
            val m = (mask[i].toInt() and 0xFF) > 128
            val gt = (groundTruth[i].toInt() and 0xFF) > 128
            if (m && gt) intersection++
            if (m || gt) union++
            if (gt) gtCount++
            if (m) maskCount++
        }

        val iou = if (union > 0) intersection.toDouble() / union else 1.0
        val precision = if (maskCount > 0) intersection.toDouble() / maskCount else 1.0
        val recall = if (gtCount > 0) intersection.toDouble() / gtCount else 1.0
        val dice = if (maskCount + gtCount > 0) (2.0 * intersection) / (maskCount + gtCount) else 1.0
        val fp = maskCount - intersection
        val fn = gtCount - intersection

        return MaskOverlapMetrics(
            intersectionCount = intersection,
            unionCount = union,
            groundTruthCount = gtCount,
            maskCount = maskCount,
            iou = iou,
            precision = precision,
            recall = recall,
            dice = dice,
            falsePositives = fp,
            falseNegatives = fn
        )
    }

    // -------------------------------------------------------------------------
    // Private Helpers: Morphology, Floods, and Geometry
    // -------------------------------------------------------------------------

    private fun runFloodFill(
        barrier: BooleanArray,
        width: Int,
        height: Int,
        seedX: Int,
        seedY: Int
    ): BooleanArray {
        val total = width * height
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        val seedIdx = seedY * width + seedX
        if (barrier[seedIdx]) {
            visited[seedIdx] = true
            return visited
        }

        visited[seedIdx] = true
        queue[tail++] = seedIdx

        val dx = intArrayOf(0, 0, 1, -1)
        val dy = intArrayOf(1, -1, 0, 0)

        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % width
            val cy = curr / width

            for (d in 0..3) {
                val nx = cx + dx[d]
                val ny = cy + dy[d]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (!visited[nIdx] && !barrier[nIdx]) {
                        visited[nIdx] = true
                        queue[tail++] = nIdx
                    }
                }
            }
        }

        return visited
    }

    private fun floodFromBorderInSpill(spill: BooleanArray, width: Int, height: Int): BooleanArray {
        val total = width * height
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        fun enqueue(x: Int, y: Int) {
            val idx = y * width + x
            if (spill[idx] && !visited[idx]) {
                visited[idx] = true
                queue[tail++] = idx
            }
        }

        for (x in 0 until width) {
            enqueue(x, 0)
            enqueue(x, height - 1)
        }
        for (y in 0 until height) {
            enqueue(0, y)
            enqueue(width - 1, y)
        }

        val dx = intArrayOf(0, 0, 1, -1)
        val dy = intArrayOf(1, -1, 0, 0)

        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % width
            val cy = curr / width
            for (d in 0..3) {
                val nx = cx + dx[d]
                val ny = cy + dy[d]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (spill[nIdx] && !visited[nIdx]) {
                        visited[nIdx] = true
                        queue[tail++] = nIdx
                    }
                }
            }
        }
        return visited
    }

    private fun floodExterior(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val total = width * height
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        fun enqueue(x: Int, y: Int) {
            val idx = y * width + x
            if (!mask[idx] && !visited[idx]) {
                visited[idx] = true
                queue[tail++] = idx
            }
        }

        for (x in 0 until width) {
            enqueue(x, 0)
            enqueue(x, height - 1)
        }
        for (y in 0 until height) {
            enqueue(0, y)
            enqueue(width - 1, y)
        }

        val dx = intArrayOf(0, 0, 1, -1)
        val dy = intArrayOf(1, -1, 0, 0)

        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % width
            val cy = curr / width

            for (d in 0..3) {
                val nx = cx + dx[d]
                val ny = cy + dy[d]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (!visited[nIdx] && !mask[nIdx]) {
                        visited[nIdx] = true
                        queue[tail++] = nIdx
                    }
                }
            }
        }

        return visited
    }

    private fun despeckle(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val total = width * height
        val out = mask.clone()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                var trueNeighbors = 0
                var totalNeighbors = 0
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx !in 0 until width || (dx == 0 && dy == 0)) continue
                        totalNeighbors++
                        if (mask[ny * width + nx]) trueNeighbors++
                    }
                }

                if (mask[idx] && trueNeighbors <= 1) {
                    out[idx] = false
                } else if (!mask[idx] && trueNeighbors >= totalNeighbors - 1) {
                    out[idx] = true
                }
            }
        }
        return out
    }

    private fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val total = width * height
        val out = BooleanArray(total)
        val r2 = radius * radius

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    for (dy in -radius..radius) {
                        val ny = y + dy
                        if (ny !in 0 until height) continue
                        for (dx in -radius..radius) {
                            if (dx * dx + dy * dy <= r2) {
                                val nx = x + dx
                                if (nx in 0 until width) {
                                    out[ny * width + nx] = true
                                }
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun erode(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val total = width * height
        val out = BooleanArray(total)
        val r2 = radius * radius

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!mask[y * width + x]) continue
                var keep = true
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny !in 0 until height) { keep = false; break }
                    for (dx in -radius..radius) {
                        if (dx * dx + dy * dy <= r2) {
                            val nx = x + dx
                            if (nx !in 0 until width || !mask[ny * width + nx]) {
                                keep = false
                                break
                            }
                        }
                    }
                    if (!keep) break
                }
                if (keep) out[y * width + x] = true
            }
        }
        return out
    }

    private fun isBoundaryPixel(mask: BooleanArray, width: Int, height: Int, x: Int, y: Int): Boolean {
        val self = mask[y * width + x]
        for (dy in -1..1) {
            val ny = y + dy
            if (ny !in 0 until height) return true
            for (dx in -1..1) {
                val nx = x + dx
                if (nx !in 0 until width) return true
                if (mask[ny * width + nx] != self) return true
            }
        }
        return false
    }

    private fun countMaskNeighbors(mask: BooleanArray, width: Int, height: Int, x: Int, y: Int): Int {
        var count = 0
        for (dy in -1..1) {
            val ny = y + dy
            for (dx in -1..1) {
                val nx = x + dx
                if (nx in 0 until width && ny in 0 until height && mask[ny * width + nx]) {
                    count++
                }
            }
        }
        return count
    }

    private fun findBarrierEndpoints(
        barrier: BooleanArray,
        width: Int,
        height: Int,
        cx: Float,
        cy: Float,
        searchRadius: Int
    ): Pair<CvPoint, CvPoint> {
        val candidates = mutableListOf<CvPoint>()
        val rad = searchRadius.coerceAtLeast(3)
        for (dy in -rad..rad) {
            val ny = cy.roundToInt() + dy
            if (ny !in 0 until height) continue
            for (dx in -rad..rad) {
                val nx = cx.roundToInt() + dx
                if (nx in 0 until width && barrier[ny * width + nx]) {
                    candidates.add(CvPoint(nx.toFloat(), ny.toFloat()))
                }
            }
        }

        if (candidates.size < 2) {
            return Pair(
                CvPoint((cx - 1f).coerceAtLeast(0f), cy),
                CvPoint((cx + 1f).coerceAtMost((width - 1).toFloat()), cy)
            )
        }

        // Sort candidates by proximity to gap center
        candidates.sortBy { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) }
        val p1 = candidates[0]
        val v1x = p1.x - cx
        val v1y = p1.y - cy

        // Pick closest candidate on opposite side of (cx, cy)
        var p2: CvPoint? = null
        for (i in 1 until candidates.size) {
            val c = candidates[i]
            val v2x = c.x - cx
            val v2y = c.y - cy
            val dot = v1x * v2x + v1y * v2y
            if (dot <= 0.05f) {
                p2 = c
                break
            }
        }

        val finalP2 = p2 ?: candidates.getOrNull(1) ?: candidates[0]
        return Pair(p1, finalP2)
    }

    private fun classifyBoundary(
        pixels: IntArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        isLeaky: Boolean
    ): RegionBoundaryType {
        if (isLeaky) return RegionBoundaryType.LEAKY_GAP

        var transparentNeighbors = 0
        var totalBorderPixels = 0
        var darkEdges = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    for (dy in -1..1) {
                        val ny = y + dy
                        if (ny !in 0 until height) continue
                        for (dx in -1..1) {
                            val nx = x + dx
                            if (nx in 0 until width && !mask[ny * width + nx]) {
                                totalBorderPixels++
                                val c = pixels[ny * width + nx]
                                val a = (c ushr 24) and 0xFF
                                if (a <= 10) transparentNeighbors++
                                val lum = (((c ushr 16) and 0xFF) + ((c ushr 8) and 0xFF) + (c and 0xFF)) / 3
                                if (lum < 60) darkEdges++
                            }
                        }
                    }
                }
            }
        }

        if (totalBorderPixels > 0 && transparentNeighbors.toFloat() / totalBorderPixels >= 0.20f) {
            return RegionBoundaryType.TRANSPARENCY_SILHOUETTE
        }

        if (totalBorderPixels > 0 && darkEdges.toFloat() / totalBorderPixels >= 0.25f) {
            return RegionBoundaryType.CLOSED_OUTLINE
        }

        return RegionBoundaryType.ADJACENT_COLOUR_BARRIER
    }

    private fun rasterizeLine(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        thickness: Int,
        color: Int
    ) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        val halfThick = thickness / 2

        while (true) {
            for (ty in -halfThick..halfThick) {
                for (tx in -halfThick..halfThick) {
                    val px = x + tx
                    val py = y + ty
                    if (px in 0 until width && py in 0 until height) {
                        pixels[py * width + px] = color
                    }
                }
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
}
