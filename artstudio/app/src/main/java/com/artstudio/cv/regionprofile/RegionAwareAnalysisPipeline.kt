package com.artstudio.cv.regionprofile

import com.artstudio.cv.CvDenoiseEngine
import com.artstudio.cv.CvEdgeEngine
import com.artstudio.cv.CvPipeline
import com.artstudio.cv.CvPipelineResult
import com.artstudio.reference.region.NamedReferenceRegion

/**
 * Region-Aware Analysis Pipeline.
 *
 * Allocates extra processing passes and tailored analysis profiles to selected regions
 * without degrading or mutating the global baseline profile across the rest of the image.
 */
object RegionAwareAnalysisPipeline {

    /**
     * Executes region-aware analysis over [pixels].
     *
     * @param pixels 32-bit ARGB image buffer.
     * @param width Image width.
     * @param height Image height.
     * @param globalProfile Global baseline profile used for full-canvas analysis.
     * @param regions Marked reference regions of interest.
     * @param regionOverrides Explicit property overrides keyed by region ID.
     * @param regionPresets Preset assignments keyed by region ID.
     */
    fun analyze(
        pixels: IntArray,
        width: Int,
        height: Int,
        globalProfile: RegionAnalysisProfile = RegionAnalysisProfile.standard(),
        regions: List<NamedReferenceRegion> = emptyList(),
        regionOverrides: Map<String, RegionProfileOverride> = emptyMap(),
        regionPresets: Map<String, RegionProfilePreset> = emptyMap()
    ): RegionAwareAnalysisResult {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(pixels.size == width * height) { "Pixel buffer size does not match dimensions" }

        val startTime = System.currentTimeMillis()
        val rt = Runtime.getRuntime()
        val memBeforeGlobal = rt.totalMemory() - rt.freeMemory()

        // 1. Execute Global Baseline Pass across the entire canvas
        val resolvedGlobal = RegionProfileResolver.resolve(globalDefault = globalProfile)
        val globalResult = CvPipeline.process(
            pixels = pixels,
            width = width,
            height = height,
            params = resolvedGlobal.toCvParameters()
        )

        // Initialize merged edges with an exact clone of the global baseline edge buffer
        val mergedEdges = globalResult.edges.clone()
        val regionMetrics = mutableListOf<RegionAnalysisMetrics>()
        val crossBoundaryNotes = mutableListOf<String>()

        // 2. Execute Focused Passes for each marked region
        for (region in regions) {
            if (!region.isVisible) continue

            val maskCount = region.selectedPixelCount
            if (maskCount == 0) {
                regionMetrics.add(
                    RegionAnalysisMetrics(
                        regionId = region.id,
                        regionName = region.name,
                        pixelCount = 0,
                        boundingArea = 0,
                        elapsedTimeMs = 0L,
                        peakMemoryBytes = 0L,
                        passesExecuted = 0,
                        featuresDetected = 0,
                        boundaryMarginUsed = 0,
                        appliedProfile = resolvedGlobal
                    )
                )
                continue
            }

            // Determine effective preset and override
            val preset = regionPresets[region.id] ?: getPresetFromRegion(region)
            val override = regionOverrides[region.id] ?: getOverrideFromRegion(region)
            val resolved = RegionProfileResolver.resolve(
                globalDefault = globalProfile,
                regionPreset = preset,
                override = override
            )

            // Measure region compute resources
            val regionStartNs = System.nanoTime()
            val memBeforeRegion = rt.totalMemory() - rt.freeMemory()

            // Find mask bounding box
            val bounds = region.bounds ?: continue
            val margin = resolved.boundaryMarginPx
            val minXDil = maxOf(0, bounds.minX - margin)
            val minYDil = maxOf(0, bounds.minY - margin)
            val maxXDil = minOf(width - 1, bounds.maxX + margin)
            val maxYDil = minOf(height - 1, bounds.maxY + margin)

            val subW = maxXDil - minXDil + 1
            val subH = maxYDil - minYDil + 1
            val subArea = subW * subH

            // Extract sub-patch buffers
            val subPixels = IntArray(subArea)
            val subMask = ByteArray(subArea)

            for (y in minYDil..maxYDil) {
                val fullRow = y * width
                val subRow = (y - minYDil) * subW
                for (x in minXDil..maxXDil) {
                    val fullIdx = fullRow + x
                    val subIdx = subRow + (x - minXDil)
                    subPixels[subIdx] = pixels[fullIdx]
                    subMask[subIdx] = region.mask[fullIdx]
                }
            }

            // Execute Pass 0: Sub-patch baseline with region-specific profile
            val subResult = CvPipeline.process(
                pixels = subPixels,
                width = subW,
                height = subH,
                params = resolved.toCvParameters()
            )
            val subEdges = subResult.edges.clone()

            // Execute Additional Passes if configured
            var totalPasses = 1
            if (resolved.additionalPasses > 0) {
                // Additional Pass 1: High-sensitivity subtle edge extraction
                val subGray = CvDenoiseEngine.toGrayscale(subPixels, subW, subH)
                if (resolved.edgeSensitivity > 0.7f) {
                    // Lower Canny thresholds for fine lines and hairlines
                    val fineLow = (resolved.edgeSensitivity.let { s -> 20.0f * (1.1f - s) }).coerceIn(4.0f, 25.0f)
                    val fineHigh = (fineLow * 2.2f).coerceIn(10.0f, 60.0f)
                    val extraEdges = CvEdgeEngine.cannyEdges(
                        gray = subGray,
                        width = subW,
                        height = subH,
                        lowThreshold = fineLow,
                        highThreshold = fineHigh
                    )

                    // Inject fine edges only within the core region mask
                    for (i in 0 until subArea) {
                        if (subMask[i] != 0.toByte() && extraEdges[i] == 255) {
                            subEdges[i] = 255
                        }
                    }
                    totalPasses++
                }

                // Additional Pass 2: Micro-gap bridging along detected lines
                if (resolved.gapClosingRadius > 0 && totalPasses <= resolved.additionalPasses + 1) {
                    val gapR = resolved.gapClosingRadius
                    bridgeLineGaps(subEdges, subMask, subW, subH, gapR)
                    totalPasses++
                }
            }

            // Count features detected inside region
            var regionFeatures = 0
            for (i in 0 until subArea) {
                if (subMask[i] != 0.toByte() && subEdges[i] == 255) {
                    regionFeatures++
                }
            }

            // 3. Merging with Boundary Margin and Continuity Preservation
            // Rule:
            // - Inside core mask: use region refined edges.
            // - Within boundary margin: stitch continuous edge segments that connect to mask.
            // - Outside core mask & margin: retain global baseline 100% bit-exact.
            for (y in minYDil..maxYDil) {
                val fullRow = y * width
                val subRow = (y - minYDil) * subW
                for (x in minXDil..maxXDil) {
                    val fullIdx = fullRow + x
                    val subIdx = subRow + (x - minXDil)

                    if (subMask[subIdx] != 0.toByte()) {
                        // Strictly inside region mask
                        mergedEdges[fullIdx] = subEdges[subIdx]
                    } else if (margin > 0 && subEdges[subIdx] == 255) {
                        // In boundary margin: preserve continuity if connected to region stroke
                        if (isConnectedToMask(subEdges, subMask, x - minXDil, y - minYDil, subW, subH, margin)) {
                            mergedEdges[fullIdx] = 255
                        }
                    }
                }
            }

            // Compute telemetry metrics
            val regionElapsedMs = maxOf(1L, (System.nanoTime() - regionStartNs) / 1_000_000L)
            val memAfterRegion = rt.totalMemory() - rt.freeMemory()
            val peakMem = maxOf(
                subArea.toLong() * 4L * (totalPasses + 1),
                memAfterRegion - memBeforeRegion
            )

            regionMetrics.add(
                RegionAnalysisMetrics(
                    regionId = region.id,
                    regionName = region.name,
                    pixelCount = maskCount,
                    boundingArea = subArea,
                    elapsedTimeMs = regionElapsedMs,
                    peakMemoryBytes = maxOf(subArea.toLong() * 4L, peakMem),
                    passesExecuted = totalPasses,
                    featuresDetected = regionFeatures,
                    boundaryMarginUsed = margin,
                    appliedProfile = resolved
                )
            )

            crossBoundaryNotes.add(
                "Region '${region.name}': Applied boundary margin of ${margin}px for spatial continuity. " +
                        "Outside boundary margin, global baseline edges are 100% bit-exact preserved."
            )
        }

        val totalElapsedMs = System.currentTimeMillis() - startTime
        val memAfterGlobal = rt.totalMemory() - rt.freeMemory()
        val totalPeakMem = maxOf(
            width.toLong() * height.toLong() * 4L * 3L,
            memAfterGlobal - memBeforeGlobal
        )

        return RegionAwareAnalysisResult(
            width = width,
            height = height,
            globalResult = globalResult,
            mergedEdges = mergedEdges,
            regionMetrics = regionMetrics,
            totalElapsedTimeMs = totalElapsedMs,
            totalPeakMemoryBytes = totalPeakMem,
            crossBoundaryNotes = crossBoundaryNotes
        )
    }

    /**
     * Checks if an edge pixel in the boundary margin connects to an edge pixel inside the core mask.
     */
    private fun isConnectedToMask(
        edges: IntArray,
        mask: ByteArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        maxSteps: Int
    ): Boolean {
        // Simple BFS up to maxSteps distance
        val visited = BooleanArray(width * height)
        val queueX = IntArray(width * height)
        val queueY = IntArray(width * height)
        var head = 0
        var tail = 0

        queueX[tail] = startX
        queueY[tail] = startY
        visited[startY * width + startX] = true
        tail++

        var steps = 0
        while (head < tail && steps < maxSteps * 8) {
            val cx = queueX[head]
            val cy = queueY[head]
            head++
            steps++

            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx in 0 until width && ny in 0 until height) {
                        val nIdx = ny * width + nx
                        if (!visited[nIdx] && edges[nIdx] == 255) {
                            if (mask[nIdx] != 0.toByte()) {
                                return true // Reached an edge pixel inside the core mask!
                            }
                            visited[nIdx] = true
                            queueX[tail] = nx
                            queueY[tail] = ny
                            tail++
                        }
                    }
                }
            }
        }
        return false
    }

    /**
     * Bridges small 1-pixel to gapRadius line breaks along detected edges within the mask.
     */
    private fun bridgeLineGaps(
        edges: IntArray,
        mask: ByteArray,
        width: Int,
        height: Int,
        gapRadius: Int
    ) {
        val r = gapRadius.coerceIn(1, 4)
        for (y in r until height - r) {
            val row = y * width
            for (x in r until width - r) {
                val idx = row + x
                if (mask[idx] != 0.toByte() && edges[idx] == 0) {
                    // Check horizontal gap bridge
                    if (edges[idx - 1] == 255 && edges[idx + 1] == 255) {
                        edges[idx] = 255
                    }
                    // Check vertical gap bridge
                    else if (edges[idx - width] == 255 && edges[idx + width] == 255) {
                        edges[idx] = 255
                    }
                }
            }
        }
    }

    private fun getPresetFromRegion(region: NamedReferenceRegion): RegionProfilePreset? {
        val presetStr = region.settings.customParameters["profilePreset"]
        if (presetStr != null) {
            return runCatching { RegionProfilePreset.valueOf(presetStr) }.getOrNull()
        }
        // Heuristic fallback based on detailSensitivity and priority
        return when {
            region.settings.detailSensitivity >= 0.85f -> RegionProfilePreset.FINE_LINES
            region.settings.detailSensitivity >= 0.70f -> RegionProfilePreset.HIGH_DETAIL
            else -> null
        }
    }

    private fun getOverrideFromRegion(region: NamedReferenceRegion): RegionProfileOverride? {
        val override = RegionProfileOverride.fromMap(region.settings.customParameters)
        return if (override.hasOverrides()) override else null
    }
}
