package com.artstudio.cv.regionprofile

import com.artstudio.reference.IntBoundingBox
import com.artstudio.reference.region.NamedReferenceRegion
import kotlin.math.sqrt

/**
 * Deterministic synthetic fixture data container for mixed-detail verification.
 */
data class MixedDetailFixtureData(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val focusRegion: NamedReferenceRegion,
    val outsideMask: ByteArray,
    val focusBounds: IntBoundingBox,
    val expectedOutsideEdgePixelsMin: Int,
    val expectedInsideHairlineY: Int
)

/**
 * Evaluation outcome proving regional extra detail capture and outside zero-regression.
 */
data class MixedDetailVerificationResult(
    val isZeroRegressionOutside: Boolean,
    val outsideDiffPixelCount: Int,
    val outsideRmse: Double,
    val insideBaselineEdgeCount: Int,
    val insideRegionalEdgeCount: Int,
    val isExtraDetailCapturedInside: Boolean,
    val details: String
)

/**
 * Generator and verifier for synthetic mixed-detail image fixtures.
 */
object SyntheticMixedDetailFixture {

    /**
     * Generates a deterministic $128 \times 128$ mixed-detail illustration fixture.
     *
     * - Outside area contains broad high-contrast strokes and solid boxes (standard features).
     * - Inside focus region contains delicate low-contrast hairlines, micro-details, and tiny gaps.
     */
    fun createFixture(width: Int = 128, height: Int = 128): MixedDetailFixtureData {
        require(width >= 64 && height >= 64) { "Dimensions must be >= 64" }

        val totalPixels = width * height
        val pixels = IntArray(totalPixels) { 0xFFFFFFFF.toInt() } // White background (255, 255, 255)

        // 1. Draw Outside Features: Standard thick high-contrast black strokes (0, 0, 0)
        // Horizontal top bar at y = 12, height 4px, x = [10..118]
        for (y in 12..15) {
            val row = y * width
            for (x in 10 until width - 10) {
                pixels[row + x] = 0xFF000000.toInt()
            }
        }

        // Vertical left bar at x = 12, width 4px, y = [10..118]
        for (y in 10 until height - 10) {
            val row = y * width
            for (x in 12..15) {
                pixels[row + x] = 0xFF000000.toInt()
            }
        }

        // Solid outside box at [100..116, 100..116]
        for (y in 100..116) {
            val row = y * width
            for (x in 100..116) {
                pixels[row + x] = 0xFF000000.toInt()
            }
        }

        // 2. Define Focus Region: Box [36..92, 36..92]
        val minX = 36
        val minY = 36
        val maxX = 92
        val maxY = 92
        val focusMask = ByteArray(totalPixels)
        val outsideMask = ByteArray(totalPixels)

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (x in minX..maxX && y in minY..maxY) {
                    focusMask[idx] = 255.toByte()
                } else {
                    outsideMask[idx] = 255.toByte()
                }
            }
        }

        // 3. Draw Focus Region Features:
        // A. Subtle low-contrast hairline: y = 50, x = [42..86], width 1px, grayscale 238 on 255 white
        val hairlineY = 50
        val hairlineColor = 0xFFEEEEEE.toInt() // Grayscale 238 (contrast 17, faint stroke)
        val hairlineRow = hairlineY * width
        for (x in 42..86) {
            pixels[hairlineRow + x] = hairlineColor
        }

        // B. Second faint hairline: y = 60, x = [45..82], grayscale 238
        val faintRow = 60 * width
        val faintColor = 0xFFEEEEEE.toInt()
        for (x in 45..82) {
            pixels[faintRow + x] = faintColor
        }

        // C. Tiny intricate loop: radius 2px at (64, 75), area < 4.0 px²
        val cx = 64
        val cy = 75
        val loopColor = 0xFF000000.toInt()
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = sqrt((dx * dx + dy * dy).toDouble())
                if (dist in 1.2..2.2) {
                    val px = cx + dx
                    val py = cy + dy
                    if (px in 0 until width && py in 0 until height) {
                        pixels[py * width + px] = loopColor
                    }
                }
            }
        }

        // D. 1-pixel micro-gap along a line at y = 84: x = [44..62] and [64..84] with gap at x = 63
        val gapRow = 84 * width
        for (x in 44..62) pixels[gapRow + x] = 0xFF000000.toInt()
        for (x in 64..84) pixels[gapRow + x] = 0xFF000000.toInt()
        // pixels[gapRow + 63] is left as white (gap)

        val focusRegion = NamedReferenceRegion(
            id = "region_mixed_detail_focus",
            name = "Delicate Eyes & Linework",
            instructions = "Extract fine hairlines and tiny loops without blurring",
            width = width,
            height = height,
            mask = focusMask,
            colorRgba = 0xFF00E5FF.toInt()
        )

        return MixedDetailFixtureData(
            width = width,
            height = height,
            pixels = pixels,
            focusRegion = focusRegion,
            outsideMask = outsideMask,
            focusBounds = IntBoundingBox(minX, minY, maxX, maxY),
            expectedOutsideEdgePixelsMin = 200,
            expectedInsideHairlineY = hairlineY
        )
    }

    /**
     * Verifies that regional extra processing:
     * 1. Successfully captures additional fine details inside the region.
     * 2. Preserves 100% bit-exact parity outside the region mask (zero regression, RMSE = 0.0).
     */
    fun verifyMixedDetail(
        fixture: MixedDetailFixtureData,
        regionalResult: RegionAwareAnalysisResult
    ): MixedDetailVerificationResult {
        val width = fixture.width
        val height = fixture.height
        val total = width * height

        val baselineEdges = regionalResult.globalResult.edges
        val mergedEdges = regionalResult.mergedEdges

        var outsideDiffCount = 0
        var outsideSumSqErr = 0.0
        var outsideTotalChecked = 0

        var insideBaselineEdges = 0
        var insideRegionalEdges = 0

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                val isInsideFocus = (fixture.focusRegion.mask[idx] != 0.toByte())

                if (isInsideFocus) {
                    if (baselineEdges[idx] == 255) insideBaselineEdges++
                    if (mergedEdges[idx] == 255) insideRegionalEdges++
                } else {
                    outsideTotalChecked++
                    val b = baselineEdges[idx]
                    val m = mergedEdges[idx]
                    if (b != m) {
                        outsideDiffCount++
                        val diff = (b - m).toDouble()
                        outsideSumSqErr += diff * diff
                    }
                }
            }
        }

        val outsideRmse = if (outsideTotalChecked > 0) {
            sqrt(outsideSumSqErr / outsideTotalChecked)
        } else 0.0

        val zeroRegression = (outsideDiffCount == 0 && outsideRmse == 0.0)
        val extraDetail = (insideRegionalEdges > insideBaselineEdges)

        val report = buildString {
            appendLine("=== Mixed-Detail Verification Report ===")
            appendLine("Outside Checked Pixels: $outsideTotalChecked")
            appendLine("Outside Diff Pixels: $outsideDiffCount (Zero Regression: $zeroRegression, RMSE: $outsideRmse)")
            appendLine("Inside Baseline Edges: $insideBaselineEdges")
            appendLine("Inside Regional Edges: $insideRegionalEdges (+${insideRegionalEdges - insideBaselineEdges} extra edges)")
            appendLine("Extra Detail Captured: $extraDetail")
            appendLine("Per-Region Metrics Recorded: ${regionalResult.regionMetrics.size}")
            for (m in regionalResult.regionMetrics) {
                appendLine("  -> Region '${m.regionName}': ${m.elapsedTimeMs} ms, ${m.peakMemoryBytes} B, passes: ${m.passesExecuted}, features: ${m.featuresDetected}")
            }
        }

        return MixedDetailVerificationResult(
            isZeroRegressionOutside = zeroRegression,
            outsideDiffPixelCount = outsideDiffCount,
            outsideRmse = outsideRmse,
            insideBaselineEdgeCount = insideBaselineEdges,
            insideRegionalEdgeCount = insideRegionalEdges,
            isExtraDetailCapturedInside = extraDetail,
            details = report
        )
    }
}
