package com.artstudio.cv.region

import com.artstudio.cv.CvPoint
import com.artstudio.importer.RasterImageImporter
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RegionMaskTest {

    private fun resolveFixture(relativePath: String): File {
        val candidates = listOf(
            File("tests/fixtures/artstudio/v1", relativePath),
            File("../tests/fixtures/artstudio/v1", relativePath),
            File("/root/artstudio/tests/fixtures/artstudio/v1", relativePath)
        )
        for (f in candidates) {
            if (f.exists()) return f
        }
        throw IllegalStateException("Fixture not found: $relativePath")
    }

    // -------------------------------------------------------------------------
    // 1. Flat Colour Solid Region Segmentation
    // -------------------------------------------------------------------------
    @Test
    fun testFlatColourRegionSegmentation() {
        val fixture = SyntheticRegionFixtureGenerator.createFlatColourFixture(width = 100, height = 100)
        val config = RegionMaskConfig(
            colorTolerance = 0.05f,
            gapClosingRadius = 1,
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = config,
            groundTruthMask = fixture.groundTruthMask
        )

        assertNotNull(result)
        assertFalse(result.isLeaky)
        assertFalse(result.requiresReview)
        assertTrue(result.contains(50, 50))
        assertTrue(result.contains(20, 20))
        assertTrue(result.contains(80, 80))
        assertFalse(result.contains(10, 10))

        val metrics = result.overlapMetrics
        assertNotNull(metrics)
        assertEquals(1.0, metrics!!.iou, 0.001)
        assertEquals(1.0, metrics.precision, 0.001)
        assertEquals(1.0, metrics.recall, 0.001)
        assertEquals(1.0, metrics.dice, 0.001)
        assertEquals(0, metrics.falsePositives)
        assertEquals(0, metrics.falseNegatives)
    }

    // -------------------------------------------------------------------------
    // 2. Closed Outline Box Segmentation
    // -------------------------------------------------------------------------
    @Test
    fun testClosedOutlineBoxSegmentation() {
        val fixture = SyntheticRegionFixtureGenerator.createClosedOutlineFixture(width = 100, height = 100)
        val config = RegionMaskConfig(
            colorTolerance = 0.10f,
            gapClosingRadius = 2,
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = config,
            groundTruthMask = fixture.groundTruthMask
        )

        assertFalse("Closed outline must not be flagged as leaky", result.isLeaky)
        assertTrue("No review items should exist for closed outline", result.uncertainBoundaries.isEmpty())
        assertEquals(RegionBoundaryType.CLOSED_OUTLINE, result.boundaryType)

        val metrics = result.overlapMetrics
        assertNotNull(metrics)
        assertTrue("IoU must exceed 0.99, got ${metrics!!.iou}", metrics.iou >= 0.99)
        assertTrue("Precision must exceed 0.99, got ${metrics.precision}", metrics.precision >= 0.99)
        assertTrue("Recall must exceed 0.99, got ${metrics.recall}", metrics.recall >= 0.99)
    }

    // -------------------------------------------------------------------------
    // 3. One-Pixel Micro-Gap Detection and Bridging
    // -------------------------------------------------------------------------
    @Test
    fun testOnePixelMicroGapDetectionAndClosing() {
        val fixture = SyntheticRegionFixtureGenerator.createOnePixelGapFixture(width = 100, height = 100)

        // A. Without gap closing or leak detection, flood spills to entire canvas
        val unconstrainedConfig = RegionMaskConfig(
            gapClosingRadius = 0,
            detectLeaks = false
        )
        val unconstrainedResult = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = unconstrainedConfig
        )
        assertTrue("Unconstrained flood must spill to outer canvas", unconstrainedResult.pixelCount > 6000)

        // B. With gap closing radius = 2, 1-pixel gap is detected and sealed
        val closedConfig = RegionMaskConfig(
            gapClosingRadius = 2,
            detectLeaks = true,
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )
        val closedResult = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = closedConfig,
            groundTruthMask = fixture.groundTruthMask
        )

        assertTrue("1-pixel gap must be flagged as leaky", closedResult.isLeaky)
        assertEquals(RegionBoundaryType.LEAKY_GAP, closedResult.boundaryType)
        assertTrue("Uncertain boundary reviews must be generated", closedResult.uncertainBoundaries.isNotEmpty())

        val review = closedResult.uncertainBoundaries.first()
        assertEquals(LeakSeverity.MINOR_TINY_GAP, review.severity)
        assertEquals(50f, review.leakLocation.x, 2.0f)
        assertEquals(20f, review.leakLocation.y, 2.0f)

        // Refined correctedMask is contained within the interior box
        val metrics = closedResult.overlapMetrics
        assertNotNull(metrics)
        assertTrue("Closed mask IoU must exceed 0.95, got ${metrics!!.iou}", metrics.iou >= 0.95)
        assertTrue("Recall must be near 1.0, got ${metrics.recall}", metrics.recall >= 0.95)
    }

    // -------------------------------------------------------------------------
    // 4. Multi-Pixel Gap Detection and Severity Grading
    // -------------------------------------------------------------------------
    @Test
    fun testMultiPixelGapDetectionAndSeverity() {
        val fixture = SyntheticRegionFixtureGenerator.createMultiPixelGapFixture(
            width = 100,
            height = 100,
            gapSize = 4
        )

        val config = RegionMaskConfig(
            gapClosingRadius = 3,
            detectLeaks = true,
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = config,
            groundTruthMask = fixture.groundTruthMask
        )

        assertTrue(result.isLeaky)
        assertTrue(result.uncertainBoundaries.isNotEmpty())

        val review = result.uncertainBoundaries.first()
        assertEquals(LeakSeverity.MODERATE_GAP, review.severity)
        assertTrue("Gap distance must be >= 3.0px, got ${review.gapDistance}", review.gapDistance >= 3.0f)
        assertEquals(50f, review.leakLocation.x, 3.0f)
        assertEquals(20f, review.leakLocation.y, 2.0f)
    }

    // -------------------------------------------------------------------------
    // 5. User Correction Bridge Application (Sealing a Leaky Region)
    // -------------------------------------------------------------------------
    @Test
    fun testUserCorrectionBridgeApplication() {
        val fixture = SyntheticRegionFixtureGenerator.createMultiPixelGapFixture(
            width = 100,
            height = 100,
            gapSize = 4
        )

        val config = RegionMaskConfig(
            gapClosingRadius = 3,
            detectLeaks = true
        )

        val initialResult = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = config
        )

        assertTrue("Initial segmentation must require review", initialResult.requiresReview)
        val review = initialResult.uncertainBoundaries.first()
        assertFalse(review.isUserCorrected)

        // Apply user correction with proposed bridge stroke
        val correctedResult = RegionMaskEngine.applyUserCorrection(
            result = initialResult,
            pixels = fixture.pixels,
            reviewId = review.id,
            bridgeStroke = review.proposedBridge,
            strokeWidth = 2,
            config = config
        )

        assertNotNull(correctedResult)
        assertFalse("Corrected result must no longer require review", correctedResult.requiresReview)
        val updatedReview = correctedResult.uncertainBoundaries.first()
        assertTrue("Review must be marked user-corrected", updatedReview.isUserCorrected)
        assertFalse("isLeaky must be false once all leaks are resolved", correctedResult.isLeaky)
        assertEquals(RegionBoundaryType.CLOSED_OUTLINE, correctedResult.boundaryType)
    }

    // -------------------------------------------------------------------------
    // 6. Donut Hole Preservation vs Hole Filling
    // -------------------------------------------------------------------------
    @Test
    fun testDonutHolePreservationVsFilling() {
        val fixture = SyntheticRegionFixtureGenerator.createDonutHoleFixture(width = 100, height = 100)

        // A. preserveHoles = true -> Center hole remains unmasked
        val preserveConfig = RegionMaskConfig(
            preserveHoles = true,
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )
        val preservedResult = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = preserveConfig,
            groundTruthMask = fixture.groundTruthMask
        )

        assertTrue("Donut must report hasHoles = true", preservedResult.hasHoles)
        assertTrue("Seed in ring must be masked", preservedResult.contains(fixture.seedX, fixture.seedY))
        assertFalse("Center hole at (50, 50) must NOT be masked", preservedResult.contains(50, 50))
        assertFalse("Outer canvas at (5, 5) must NOT be masked", preservedResult.contains(5, 5))

        val preservedMetrics = preservedResult.overlapMetrics
        assertNotNull(preservedMetrics)
        assertTrue("IoU with hole preserved must be >= 0.99, got ${preservedMetrics!!.iou}", preservedMetrics.iou >= 0.99)

        // B. preserveHoles = false -> Center hole is filled over
        val fillHolesConfig = RegionMaskConfig(
            preserveHoles = false,
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )
        val filledResult = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = fillHolesConfig
        )

        assertTrue("Filled hole result must contain center (50, 50)", filledResult.contains(50, 50))
        assertTrue("Filled pixel count must be larger than preserved count", filledResult.pixelCount > preservedResult.pixelCount)
    }

    // -------------------------------------------------------------------------
    // 7. Anti-Aliased Boundary Bleed and Smoothing
    // -------------------------------------------------------------------------
    @Test
    fun testAntiAliasedBoundaryExpansionAndSmoothing() {
        val fixture = SyntheticRegionFixtureGenerator.createAntiAliasedFixture(width = 100, height = 100)

        // A. expansion = 0, no smoothing (sharp binary stop)
        val configZero = RegionMaskConfig(
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )
        val resultZero = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = configZero
        )

        // B. expansion = +1, smoothing = true (expands into fringe and softens edge)
        val configBleed = RegionMaskConfig(
            boundaryExpansion = 1,
            antiAliasSmoothing = true
        )
        val resultBleed = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = configBleed
        )

        assertTrue("Boundary expansion must increase covered pixel count", resultBleed.pixelCount >= resultZero.pixelCount)

        // Verify sub-pixel anti-aliased gradient values in correctedMask
        var hasIntermediateAlpha = false
        for (b in resultBleed.correctedMask) {
            val v = b.toInt() and 0xFF
            if (v in 1..254) {
                hasIntermediateAlpha = true
                break
            }
        }
        assertTrue("Anti-alias smoothing must produce intermediate edge alpha values [1..254]", hasIntermediateAlpha)
    }

    // -------------------------------------------------------------------------
    // 8. Transparency Silhouette Segmentation
    // -------------------------------------------------------------------------
    @Test
    fun testTransparencySilhouetteSegmentation() {
        val fixture = SyntheticRegionFixtureGenerator.createTransparencySilhouetteFixture(width = 100, height = 100)
        val config = RegionMaskConfig(
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = config,
            groundTruthMask = fixture.groundTruthMask
        )

        assertEquals(RegionBoundaryType.TRANSPARENCY_SILHOUETTE, result.boundaryType)
        assertTrue(result.contains(50, 50))
        assertFalse(result.contains(10, 10))

        val metrics = result.overlapMetrics
        assertNotNull(metrics)
        assertEquals(1.0, metrics!!.iou, 0.001)
        assertEquals(1.0, metrics.precision, 0.001)
        assertEquals(1.0, metrics.recall, 0.001)
    }

    // -------------------------------------------------------------------------
    // 9. Salt-and-Pepper Noisy Despeckling
    // -------------------------------------------------------------------------
    @Test
    fun testNoisySpeckleDespeckling() {
        val fixture = SyntheticRegionFixtureGenerator.createNoisyFixture(width = 100, height = 100)

        // With despeckleRadius = 1
        val config = RegionMaskConfig(
            despeckleRadius = 1,
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = config,
            groundTruthMask = fixture.groundTruthMask
        )

        val metrics = result.overlapMetrics
        assertNotNull(metrics)
        assertTrue("IoU with despeckle must exceed 0.98, got ${metrics!!.iou}", metrics.iou >= 0.98)
    }

    // -------------------------------------------------------------------------
    // 10. Adjacent Touching Colours Barrier
    // -------------------------------------------------------------------------
    @Test
    fun testAdjacentTouchingColoursBarrier() {
        val fixture = SyntheticRegionFixtureGenerator.createAdjacentTouchingColoursFixture(width = 100, height = 100)
        val config = RegionMaskConfig(
            colorTolerance = 0.05f,
            boundaryExpansion = 0,
            antiAliasSmoothing = false
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = config,
            groundTruthMask = fixture.groundTruthMask
        )

        assertEquals(RegionBoundaryType.ADJACENT_COLOUR_BARRIER, result.boundaryType)
        assertTrue(result.contains(25, 50))
        assertFalse(result.contains(75, 50))

        val metrics = result.overlapMetrics
        assertNotNull(metrics)
        assertEquals(1.0, metrics!!.iou, 0.001)
        assertEquals(1.0, metrics.precision, 0.001)
        assertEquals(1.0, metrics.recall, 0.001)
    }

    // -------------------------------------------------------------------------
    // 11. Three-Tier Mask Separation and Generated Fill
    // -------------------------------------------------------------------------
    @Test
    fun testThreeTierMaskSeparationAndGeneratedFill() {
        val fixture = SyntheticRegionFixtureGenerator.createOnePixelGapFixture(width = 100, height = 100)
        val fillColor = 0xFFFF9900.toInt() // Vivid orange

        val config = RegionMaskConfig(
            gapClosingRadius = 2,
            detectLeaks = true,
            fillColor = fillColor,
            fillOpacity = 0.85f
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = config
        )

        // 1. Array instances must be strictly separate
        assertNotSame("sourceMask and correctedMask must not alias", result.sourceMask, result.correctedMask)
        assertNotNull("generatedFill must be generated when fillColor is set", result.generatedFill)

        // 2. Content differentiation:
        // In leaky gap, sourceMask spilled to >6000 pixels while correctedMask is confined to ~3400 pixels
        var sourceCount = 0
        var correctedCount = 0
        val total = fixture.width * fixture.height
        for (i in 0 until total) {
            if ((result.sourceMask[i].toInt() and 0xFF) > 128) sourceCount++
            if ((result.correctedMask[i].toInt() and 0xFF) > 128) correctedCount++
        }
        assertTrue("sourceMask must reflect raw flood spill ($sourceCount > $correctedCount)", sourceCount > correctedCount)

        // 3. generatedFill rendering correctness
        val fill = result.generatedFill!!
        val centerIdx = 50 * fixture.width + 50
        val centerPixel = fill[centerIdx]
        val centerAlpha = (centerPixel ushr 24) and 0xFF
        val centerR = (centerPixel ushr 16) and 0xFF
        val centerG = (centerPixel ushr 8) and 0xFF
        val centerB = centerPixel and 0xFF

        assertTrue("Center pixel alpha must be non-zero", centerAlpha > 200)
        assertEquals(0xFF, centerR)
        assertEquals(0x99, centerG)
        assertEquals(0x00, centerB)

        // 4. When fillColor is null, generatedFill is null
        val noFillConfig = config.copy(fillColor = null)
        val noFillResult = RegionMaskEngine.segmentRegion(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            seedX = fixture.seedX,
            seedY = fixture.seedY,
            config = noFillConfig
        )
        assertNull(noFillResult.generatedFill)
    }

    // -------------------------------------------------------------------------
    // 12. Real Raster Fixture Segmentation: Flat Colour PNG
    // -------------------------------------------------------------------------
    @Test
    fun testRealRasterFixtureFlatColourSegmentation() {
        val file = resolveFixture("flat_colour/flat_colour_simple_01.png")
        val decoded = RasterImageImporter.decodeRasterImage(file.readBytes(), file.name)
        assertNotNull("Failed to decode fixture image", decoded)
        val w = decoded!!.width
        val h = decoded.height
        val pixels = decoded.pixels

        val config = RegionMaskConfig(
            colorTolerance = 0.12f,
            gapClosingRadius = 1,
            fillColor = 0xFF336699.toInt()
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = pixels,
            width = w,
            height = h,
            seedX = w / 2,
            seedY = h / 2,
            config = config
        )

        assertNotNull(result)
        assertTrue("Segmented pixel count must be positive", result.pixelCount > 0)
        assertEquals(w, result.width)
        assertEquals(h, result.height)
        assertNotNull(result.generatedFill)
    }

    // -------------------------------------------------------------------------
    // 13. Real Raster Fixture Segmentation: Transparent PNG Cutout
    // -------------------------------------------------------------------------
    @Test
    fun testRealRasterFixtureTransparentCutoutSegmentation() {
        val file = resolveFixture("transparent_png/transparent_cutout_01.png")
        val decoded = RasterImageImporter.decodeRasterImage(file.readBytes(), file.name)
        assertNotNull("Failed to decode fixture image", decoded)
        val w = decoded!!.width
        val h = decoded.height
        val pixels = decoded.pixels

        // Seed in corner to segment transparent canvas background
        val config = RegionMaskConfig(
            colorTolerance = 0.08f,
            gapClosingRadius = 0
        )

        val result = RegionMaskEngine.segmentRegion(
            pixels = pixels,
            width = w,
            height = h,
            seedX = 0,
            seedY = 0,
            config = config
        )

        assertNotNull(result)
        assertTrue("Transparent background pixel count must be positive", result.pixelCount > 0)
        assertFalse(result.isLeaky)
    }

    // -------------------------------------------------------------------------
    // 14. Full Image All-Regions Segmentation
    // -------------------------------------------------------------------------
    @Test
    fun testSegmentAllRegionsCoverage() {
        val fixture = SyntheticRegionFixtureGenerator.createAdjacentTouchingColoursFixture(width = 40, height = 40)
        val config = RegionMaskConfig(
            colorTolerance = 0.05f,
            gapClosingRadius = 0,
            boundaryExpansion = 0,
            antiAliasSmoothing = false,
            detectLeaks = false
        )

        val regions = RegionMaskEngine.segmentAllRegions(
            pixels = fixture.pixels,
            width = fixture.width,
            height = fixture.height,
            config = config
        )

        assertTrue("Should segment at least 2 distinct color regions", regions.size >= 2)
        val totalCovered = regions.sumOf { it.pixelCount }
        assertEquals(fixture.width * fixture.height, totalCovered)
    }
}
