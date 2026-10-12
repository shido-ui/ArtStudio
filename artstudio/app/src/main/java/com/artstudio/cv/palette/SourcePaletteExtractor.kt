package com.artstudio.cv.palette

import com.artstudio.color.ColorData
import com.artstudio.cv.region.RegionMask
import kotlin.math.*

/**
 * Pure-Kotlin deterministic source palette analysis and region color association engine.
 *
 * Implements:
 * 1. Global dominant/representative palette extraction with Median-Cut color clustering.
 * 2. Proper handling of transparency (transparent pixels are excluded from palette quantization).
 * 3. Near-duplicate color clustering and threshold merging.
 * 4. Region-local color extraction preserving raw observed color separate from inferred roles.
 * 5. Luminance & shadow relationship inference across adjacent regions.
 * 6. Source-palette preservation, gentle adaptation, and artistic reinterpretation.
 * 7. Measurement of palette reconstruction error (RMSE) against source pixels.
 */
object SourcePaletteExtractor {

    /**
     * Executes complete palette analysis over [pixels] and associates colors with [regions].
     *
     * @param pixels 32-bit ARGB image buffer.
     * @param width Canvas width.
     * @param height Canvas height.
     * @param regions Detected region masks.
     * @param maxSwatches Maximum number of dominant swatches to extract (default 16).
     * @param mergeDistanceThreshold Normalized distance threshold [0.0, 1.0] below which near-duplicate swatches are merged (default 0.08).
     * @param adaptationMode Color adaptation mode for region fills.
     * @param artisticScheme Color harmony scheme applied if [adaptationMode] is [PaletteAdaptationMode.ARTISTIC_REINTERPRETATION].
     * @param userOverrides Optional map of artist reassignments keyed by region ID.
     */
    fun analyze(
        pixels: IntArray,
        width: Int,
        height: Int,
        regions: List<RegionMask> = emptyList(),
        maxSwatches: Int = 16,
        mergeDistanceThreshold: Float = 0.08f,
        adaptationMode: PaletteAdaptationMode = PaletteAdaptationMode.PRESERVE_SOURCE,
        artisticScheme: ArtisticScheme = ArtisticScheme.ORIGINAL_TONAL,
        userOverrides: Map<String, ColorData> = emptyMap()
    ): SourcePaletteAnalysis {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(pixels.size == width * height) { "Pixel buffer size does not match $width x $height" }

        val totalPixels = width * height
        val step = if (totalPixels > 65536) max(1, totalPixels / 32768) else 1
        var transparentCount = 0

        val (rawSwatches, totalOpaque) = if (step == 1) {
            val opaqueBuf = IntArray(totalPixels)
            var opCount = 0
            for (p in pixels) {
                val a = (p ushr 24) and 0xFF
                if (a <= 5) {
                    transparentCount++
                } else {
                    opaqueBuf[opCount++] = p
                }
            }
            val opaquePixels = opaqueBuf.copyOf(opCount)
            val swatches = if (opCount == 0) {
                listOf(
                    SourcePaletteSwatch(
                        id = "swatch_default_white",
                        color = ColorData.WHITE,
                        pixelCount = totalPixels,
                        frequency = 1.0f,
                        luminance = 1.0f,
                        inferredRole = InferredColorRole.BACKGROUND,
                        isTransparentPlaceholder = true
                    )
                )
            } else {
                extractMedianCutSwatches(opaquePixels, opCount, maxSwatches)
            }
            Pair(swatches, opCount)
        } else {
            for (p in pixels) {
                if (((p ushr 24) and 0xFF) <= 5) transparentCount++
            }
            val totOpaque = totalPixels - transparentCount
            val sampledBuf = IntArray(totalPixels / step + 1)
            var sCount = 0
            for (i in pixels.indices step step) {
                val p = pixels[i]
                if (((p ushr 24) and 0xFF) > 5) {
                    sampledBuf[sCount++] = p
                }
            }
            val sampledOpaque = sampledBuf.copyOf(sCount)
            val swatches = if (totOpaque == 0 || sCount == 0) {
                listOf(
                    SourcePaletteSwatch(
                        id = "swatch_default_white",
                        color = ColorData.WHITE,
                        pixelCount = totalPixels,
                        frequency = 1.0f,
                        luminance = 1.0f,
                        inferredRole = InferredColorRole.BACKGROUND,
                        isTransparentPlaceholder = true
                    )
                )
            } else {
                extractMedianCutSwatches(sampledOpaque, totOpaque, maxSwatches)
            }
            Pair(swatches, totOpaque)
        }

        // 3. Cluster and merge near-duplicate colors
        val mergedSwatches = mergeNearDuplicates(rawSwatches, mergeDistanceThreshold, totalOpaque)

        // 4. Region-local color extraction (observing mean color and variance)
        val initialAssignments = mutableMapOf<String, RegionColorAssignment>()
        for (region in regions) {
            val assignment = computeRegionLocalColor(
                pixels = pixels,
                width = width,
                height = height,
                region = region,
                globalPalette = mergedSwatches
            )
            initialAssignments[region.regionId] = assignment
        }

        // 5. Detect region adjacency and infer luminance/shadow relationships
        val (adjacencies, updatedAssignments) = inferAdjacencyAndShadowRoles(
            pixels = pixels,
            width = width,
            height = height,
            regions = regions,
            assignments = initialAssignments
        )

        // 6. Apply adaptation mode & user overrides
        val finalAssignments = mutableMapOf<String, RegionColorAssignment>()
        for ((regionId, assignment) in updatedAssignments) {
            val swatch = mergedSwatches.find { it.id == assignment.mappedSwatchId }
                ?: mergedSwatches.firstOrNull() ?: SourcePaletteSwatch(color = ColorData.WHITE, pixelCount = 1, frequency = 1f, luminance = 1f)

            val override = userOverrides[regionId]
            val effectiveAssigned = when (adaptationMode) {
                PaletteAdaptationMode.PRESERVE_SOURCE -> assignment.observedColor
                PaletteAdaptationMode.GENTLE_ADAPTATION -> swatch.color
                PaletteAdaptationMode.ARTISTIC_REINTERPRETATION -> applyArtisticScheme(
                    color = assignment.observedColor,
                    role = assignment.inferredRole,
                    scheme = artisticScheme
                )
            }

            finalAssignments[regionId] = assignment.copy(
                assignedColor = effectiveAssigned,
                userOverrideColor = override
            )
        }

        // 7. Calculate Palette Reconstruction RMSE Error against source pixels
        val rmse = calculateExtractionErrorRmse(
            pixels = pixels,
            width = width,
            height = height,
            regions = regions,
            assignments = finalAssignments,
            globalPalette = mergedSwatches
        )

        return SourcePaletteAnalysis(
            globalPalette = mergedSwatches,
            regionAssignments = finalAssignments,
            adjacencies = adjacencies,
            adaptationMode = adaptationMode,
            artisticScheme = artisticScheme,
            paletteExtractionErrorRmse = rmse,
            transparentPixelCount = transparentCount,
            totalAnalyzedPixels = totalPixels,
            provenanceTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Extracts initial palette swatches using Median-Cut bounding box partitioning.
     */
    private fun extractMedianCutSwatches(
        sampledPixels: IntArray,
        totalOpaque: Int,
        maxSwatches: Int
    ): List<SourcePaletteSwatch> {
        val targetBoxes = maxSwatches.coerceIn(1, 32)
        val boxes = mutableListOf(ColorBox(sampledPixels))

        while (boxes.size < targetBoxes) {
            val splittable = boxes.filter { it.canSplit() }.maxByOrNull { it.maxRange } ?: break
            boxes.remove(splittable)
            val (b1, b2) = splittable.split() ?: break
            boxes.add(b1)
            boxes.add(b2)
        }

        val totalSampled = sampledPixels.size.toFloat()
        val swatches = mutableListOf<SourcePaletteSwatch>()

        for ((idx, box) in boxes.withIndex()) {
            val avg = box.averageColor()
            val lum = avg.perceivedLuminance
            val boxWeight = box.count / totalSampled
            val scaledPixelCount = (boxWeight * totalOpaque).roundToInt()
            val freq = (scaledPixelCount.toFloat() / totalOpaque.toFloat()).coerceIn(0f, 1f)

            // Infer global role heuristic
            val role = when {
                lum < 0.18f && freq < 0.25f -> InferredColorRole.OUTLINE
                freq > 0.35f || (lum > 0.88f && freq > 0.20f) -> InferredColorRole.BACKGROUND
                avg.saturation > 0.60f && freq < 0.15f -> InferredColorRole.ACCENT
                else -> InferredColorRole.BASE_COLOR
            }

            swatches.add(
                SourcePaletteSwatch(
                    id = "swatch_${idx + 1}",
                    color = avg,
                    pixelCount = scaledPixelCount,
                    frequency = freq,
                    luminance = lum,
                    inferredRole = role
                )
            )
        }

        return swatches.sortedByDescending { it.pixelCount }
    }

    /**
     * Iteratively merges near-duplicate swatches whose color distance is below [threshold].
     */
    fun mergeNearDuplicates(
        swatches: List<SourcePaletteSwatch>,
        threshold: Float,
        totalOpaquePixels: Int
    ): List<SourcePaletteSwatch> {
        if (swatches.size <= 1) return swatches

        val working = swatches.toMutableList()
        var mergedAny = true

        while (mergedAny && working.size > 1) {
            mergedAny = false
            var bestPair: Pair<Int, Int>? = null
            var minDistance = Float.MAX_VALUE

            for (i in 0 until working.size) {
                for (j in i + 1 until working.size) {
                    val dist = colorDistance(working[i].color, working[j].color)
                    if (dist < threshold && dist < minDistance) {
                        minDistance = dist
                        bestPair = Pair(i, j)
                    }
                }
            }

            if (bestPair != null) {
                val (i, j) = bestPair
                val s1 = working[i]
                val s2 = working[j]

                val totalCount = s1.pixelCount + s2.pixelCount
                val w1 = s1.pixelCount.toFloat() / totalCount
                val w2 = s2.pixelCount.toFloat() / totalCount

                val mergedColor = ColorData(
                    r = s1.color.r * w1 + s2.color.r * w2,
                    g = s1.color.g * w1 + s2.color.g * w2,
                    b = s1.color.b * w1 + s2.color.b * w2,
                    a = 1.0f
                )

                val mergedFrequency = if (totalOpaquePixels > 0) {
                    (totalCount.toFloat() / totalOpaquePixels).coerceIn(0f, 1f)
                } else s1.frequency + s2.frequency

                val mergedRole = if (s1.inferredRole != InferredColorRole.UNASSIGNED) s1.inferredRole else s2.inferredRole

                val mergedSwatch = SourcePaletteSwatch(
                    id = s1.id,
                    color = mergedColor,
                    pixelCount = totalCount,
                    frequency = mergedFrequency,
                    luminance = mergedColor.perceivedLuminance,
                    inferredRole = mergedRole
                )

                working.removeAt(j)
                working[i] = mergedSwatch
                mergedAny = true
            }
        }

        return working.sortedByDescending { it.pixelCount }
    }

    /**
     * Extracts observed mean color and intra-region chromatic variance for a region.
     */
    private fun computeRegionLocalColor(
        pixels: IntArray,
        width: Int,
        height: Int,
        region: RegionMask,
        globalPalette: List<SourcePaletteSwatch>
    ): RegionColorAssignment {
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val maskVal = region.mask[idx].toInt() and 0xFF
                if (maskVal > 128) {
                    val p = pixels[idx]
                    val a = (p ushr 24) and 0xFF
                    if (a > 5) {
                        sumR += ((p ushr 16) and 0xFF) / 255.0
                        sumG += ((p ushr 8) and 0xFF) / 255.0
                        sumB += (p and 0xFF) / 255.0
                        count++
                    }
                }
            }
        }

        if (count == 0) {
            return RegionColorAssignment(
                regionId = region.regionId,
                observedColor = ColorData.WHITE,
                observedLuminance = 1.0f,
                colorVariance = 0.0f,
                mappedSwatchId = globalPalette.firstOrNull()?.id ?: "swatch_none",
                assignedColor = ColorData.WHITE,
                inferredRole = InferredColorRole.UNASSIGNED,
                confidence = 0.5f
            )
        }

        val meanR = (sumR / count).toFloat().coerceIn(0f, 1f)
        val meanG = (sumG / count).toFloat().coerceIn(0f, 1f)
        val meanB = (sumB / count).toFloat().coerceIn(0f, 1f)
        val observed = ColorData(meanR, meanG, meanB, 1.0f)

        // Compute intra-region variance (standard deviation in RGB space)
        var sumVariance = 0.0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val maskVal = region.mask[idx].toInt() and 0xFF
                if (maskVal > 128) {
                    val p = pixels[idx]
                    val a = (p ushr 24) and 0xFF
                    if (a > 5) {
                        val pr = ((p ushr 16) and 0xFF) / 255.0
                        val pg = ((p ushr 8) and 0xFF) / 255.0
                        val pb = (p and 0xFF) / 255.0
                        val dr = pr - meanR
                        val dg = pg - meanG
                        val db = pb - meanB
                        sumVariance += (dr * dr + dg * dg + db * db)
                    }
                }
            }
        }
        val variance = sqrt(sumVariance / count).toFloat()

        // Match to closest swatch in global palette
        val closestSwatch = globalPalette.minByOrNull { colorDistance(observed, it.color) }
            ?: globalPalette.first()

        return RegionColorAssignment(
            regionId = region.regionId,
            observedColor = observed,
            observedLuminance = observed.perceivedLuminance,
            colorVariance = variance,
            mappedSwatchId = closestSwatch.id,
            assignedColor = observed, // Preserved source by default
            inferredRole = InferredColorRole.BASE_COLOR,
            confidence = 0.95f
        )
    }

    /**
     * Analyzes topological boundary contact between neighboring regions to infer
     * luminance, shadow, and highlight relationships.
     */
    private fun inferAdjacencyAndShadowRoles(
        pixels: IntArray,
        width: Int,
        height: Int,
        regions: List<RegionMask>,
        assignments: Map<String, RegionColorAssignment>
    ): Pair<List<RegionAdjacency>, Map<String, RegionColorAssignment>> {
        if (regions.size < 2) return Pair(emptyList(), assignments)

        // Map pixel coordinates to region index
        val pixelRegionMap = Array(width * height) { mutableListOf<Int>() }
        for ((rIdx, region) in regions.withIndex()) {
            for (i in 0 until width * height) {
                if ((region.mask[i].toInt() and 0xFF) > 128) {
                    pixelRegionMap[i].add(rIdx)
                }
            }
        }

        // Count 4-connected boundary contacts between pairs
        val contactCounts = mutableMapOf<Pair<Int, Int>, Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val list1 = pixelRegionMap[idx]
                if (list1.isEmpty()) continue

                // Check right neighbor
                if (x + 1 < width) {
                    val rightList = pixelRegionMap[idx + 1]
                    for (r1 in list1) {
                        for (r2 in rightList) {
                            if (r1 != r2) {
                                val key = if (r1 < r2) Pair(r1, r2) else Pair(r2, r1)
                                contactCounts[key] = (contactCounts[key] ?: 0) + 1
                            }
                        }
                    }
                }

                // Check down neighbor
                if (y + 1 < height) {
                    val downList = pixelRegionMap[idx + width]
                    for (r1 in list1) {
                        for (r2 in downList) {
                            if (r1 != r2) {
                                val key = if (r1 < r2) Pair(r1, r2) else Pair(r2, r1)
                                contactCounts[key] = (contactCounts[key] ?: 0) + 1
                            }
                        }
                    }
                }
            }
        }

        val adjacencies = mutableListOf<RegionAdjacency>()
        val updatedAssignments = assignments.toMutableMap()

        for ((pair, contactLength) in contactCounts) {
            if (contactLength < 2) continue

            val reg1 = regions[pair.first]
            val reg2 = regions[pair.second]
            val a1 = assignments[reg1.regionId] ?: continue
            val a2 = assignments[reg2.regionId] ?: continue

            val l1 = a1.observedLuminance
            val l2 = a2.observedLuminance
            val lumDelta = l1 - l2
            val colDist = colorDistance(a1.observedColor, a2.observedColor)

            // Calculate chromatic distance (distance after neutralizing luminance differences)
            val chromaticDist = chromaticDistance(a1.observedColor, a2.observedColor)

            var isShadow = false
            var isHighlight = false

            // If regions share chromatic hue direction and differ significantly in luminance
            if (chromaticDist < 0.30f && abs(lumDelta) >= 0.15f) {
                isShadow = true
                if (lumDelta < 0) { // Reg 1 is darker than Reg 2
                    if (a1.inferredRole == InferredColorRole.BASE_COLOR) {
                        updatedAssignments[reg1.regionId] = a1.copy(inferredRole = InferredColorRole.SHADOW)
                    }
                } else { // Reg 2 is darker than Reg 1
                    if (a2.inferredRole == InferredColorRole.BASE_COLOR) {
                        updatedAssignments[reg2.regionId] = a2.copy(inferredRole = InferredColorRole.SHADOW)
                    }
                }
            }

            adjacencies.add(
                RegionAdjacency(
                    regionId1 = reg1.regionId,
                    regionId2 = reg2.regionId,
                    contactLengthPx = contactLength,
                    luminanceDelta = lumDelta,
                    colorDistance = colDist,
                    isLikelyShadowPair = isShadow,
                    isLikelyHighlightPair = isHighlight
                )
            )
        }

        return Pair(adjacencies, updatedAssignments)
    }

    /**
     * Transforms [color] according to an aesthetic harmonic [scheme] while strictly preserving
     * relative shadow and highlight luminance hierarchies.
     */
    fun applyArtisticScheme(
        color: ColorData,
        role: InferredColorRole,
        scheme: ArtisticScheme
    ): ColorData {
        val hsv = color.hsv
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]

        return when (scheme) {
            ArtisticScheme.ORIGINAL_TONAL -> color

            ArtisticScheme.WARM_SUNSET -> {
                // Shift hue toward gold/amber (around 35-45 deg), boost warm saturation
                val newH = (h * 0.5f + 35f * 0.5f).coerceIn(0f, 360f)
                val newS = (s * 1.15f).coerceIn(0f, 1f)
                val newV = if (role == InferredColorRole.SHADOW) (v * 0.85f).coerceIn(0f, 1f) else v
                ColorData.fromHsv(newH, newS, newV)
            }

            ArtisticScheme.COOL_TWILIGHT -> {
                // Shift hue toward cobalt/violet (around 220-250 deg)
                val newH = (h * 0.5f + 230f * 0.5f).coerceIn(0f, 360f)
                val newS = (s * 1.10f).coerceIn(0f, 1f)
                val newV = if (role == InferredColorRole.SHADOW) (v * 0.80f).coerceIn(0f, 1f) else v
                ColorData.fromHsv(newH, newS, newV)
            }

            ArtisticScheme.VIBRANT_POP -> {
                // Boost saturation significantly while preserving relative value
                val newS = (s * 1.35f + 0.10f).coerceIn(0f, 1f)
                val newV = if (role == InferredColorRole.SHADOW) (v * 0.88f).coerceIn(0f, 1f) else v
                ColorData.fromHsv(h, newS, newV)
            }

            ArtisticScheme.MUTED_EARTH -> {
                // Desaturate slightly and bias toward warm earth tones
                val newS = (s * 0.70f).coerceIn(0f, 1f)
                val newH = (h * 0.6f + 30f * 0.4f).coerceIn(0f, 360f)
                val newV = if (role == InferredColorRole.SHADOW) (v * 0.82f).coerceIn(0f, 1f) else v
                ColorData.fromHsv(newH, newS, newV)
            }

            ArtisticScheme.MONOCHROME_INK -> {
                // Clean grayscale luminance rendering
                val lum = color.perceivedLuminance
                val adjustedLum = when (role) {
                    InferredColorRole.SHADOW -> (lum * 0.75f).coerceIn(0f, 1f)
                    InferredColorRole.HIGHLIGHT -> (lum * 1.15f).coerceIn(0f, 1f)
                    InferredColorRole.OUTLINE -> 0.05f
                    else -> lum
                }
                ColorData(adjustedLum, adjustedLum, adjustedLum, 1.0f)
            }
        }
    }

    /**
     * Calculates the Root Mean Square Error (RMSE) between source pixels and reconstructed palette colors.
     */
    private fun calculateExtractionErrorRmse(
        pixels: IntArray,
        width: Int,
        height: Int,
        regions: List<RegionMask>,
        assignments: Map<String, RegionColorAssignment>,
        globalPalette: List<SourcePaletteSwatch>
    ): Float {
        var totalSquaredError = 0.0
        var sampleCount = 0

        // Subsample for fast determinism if image is large
        val step = if (pixels.size > 262144) 4 else 1

        for (i in pixels.indices step step) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a <= 5) continue

            val pr = ((p ushr 16) and 0xFF) / 255.0
            val pg = ((p ushr 8) and 0xFF) / 255.0
            val pb = (p and 0xFF) / 255.0

            // Check if pixel belongs to a region
            val x = i % width
            val y = i / width
            var assignedColor: ColorData? = null

            for (reg in regions) {
                if (reg.contains(x, y)) {
                    assignedColor = assignments[reg.regionId]?.effectiveColor
                    break
                }
            }

            if (assignedColor == null) {
                // Nearest global swatch
                val srcCol = ColorData(pr.toFloat(), pg.toFloat(), pb.toFloat())
                assignedColor = globalPalette.minByOrNull { colorDistance(srcCol, it.color) }?.color
                    ?: ColorData.WHITE
            }

            val dr = pr - assignedColor.r
            val dg = pg - assignedColor.g
            val db = pb - assignedColor.b
            totalSquaredError += (dr * dr + dg * dg + db * db) / 3.0
            sampleCount++
        }

        return if (sampleCount > 0) {
            sqrt(totalSquaredError / sampleCount).toFloat()
        } else 0.0f
    }

    /**
     * Normalized Euclidean distance between two colors in [0.0, 1.0].
     */
    fun colorDistance(c1: ColorData, c2: ColorData): Float {
        val dr = c1.red - c2.red
        val dg = c1.green - c2.green
        val db = c1.blue - c2.blue
        return sqrt((dr * dr + dg * dg + db * db) / 3.0f).coerceIn(0f, 1f)
    }

    /**
     * Normalized chromatic distance ignoring luminance steps.
     */
    fun chromaticDistance(c1: ColorData, c2: ColorData): Float {
        val l1 = max(0.01f, c1.perceivedLuminance)
        val l2 = max(0.01f, c2.perceivedLuminance)

        val nr1 = c1.red / l1
        val ng1 = c1.green / l1
        val nb1 = c1.blue / l1

        val nr2 = c2.red / l2
        val ng2 = c2.green / l2
        val nb2 = c2.blue / l2

        val dr = nr1 - nr2
        val dg = ng1 - ng2
        val db = nb1 - nb2
        return (sqrt(dr * dr + dg * dg + db * db) / 3.0f).coerceIn(0f, 1f)
    }

    /**
     * Private Median-Cut Color Box helper.
     */
    private class ColorBox(val pixels: IntArray) {
        val count: Int get() = pixels.size

        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0

        init {
            for (p in pixels) {
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                if (r < minR) minR = r; if (r > maxR) maxR = r
                if (g < minG) minG = g; if (g > maxG) maxG = g
                if (b < minB) minB = b; if (b > maxB) maxB = b
            }
        }

        val rangeR: Int get() = maxR - minR
        val rangeG: Int get() = maxG - minG
        val rangeB: Int get() = maxB - minB
        val maxRange: Int get() = max(rangeR, max(rangeG, rangeB))

        fun canSplit(): Boolean = pixels.size >= 2 && maxRange > 0

        fun averageColor(): ColorData {
            if (pixels.isEmpty()) return ColorData.BLACK
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (p in pixels) {
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            val n = pixels.size.toFloat()
            return ColorData.fromRgbInt(
                (sumR / n).roundToInt(),
                (sumG / n).roundToInt(),
                (sumB / n).roundToInt()
            )
        }

        fun split(): Pair<ColorBox, ColorBox>? {
            if (pixels.size < 2) return null

            val channelShift = when {
                rangeR >= rangeG && rangeR >= rangeB -> 16
                rangeG >= rangeR && rangeG >= rangeB -> 8
                else -> 0
            }

            val counts = IntArray(256)
            for (p in pixels) {
                counts[(p ushr channelShift) and 0xFF]++
            }
            val offsets = IntArray(256)
            for (i in 1..255) {
                offsets[i] = offsets[i - 1] + counts[i - 1]
            }
            val sorted = IntArray(pixels.size)
            for (p in pixels) {
                val key = (p ushr channelShift) and 0xFF
                sorted[offsets[key]++] = p
            }

            val mid = sorted.size / 2
            return Pair(
                ColorBox(sorted.copyOfRange(0, mid)),
                ColorBox(sorted.copyOfRange(mid, sorted.size))
            )
        }
    }
}
