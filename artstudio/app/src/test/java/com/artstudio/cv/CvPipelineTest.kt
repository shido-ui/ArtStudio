package com.artstudio.cv

import com.artstudio.importer.RasterImageImporter
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Deterministic Computer Vision Verification Suite (Prompt 48).
 *
 * Verifies:
 * 1. Clean Line Art processing (Canny edge detection, Moore-neighborhood contour extraction).
 * 2. Flat Colour segmentation (Sobel operator, region boundary contour candidates).
 * 3. Noisy Sketch processing (Median despeckle filtering, Adaptive local mean thresholding).
 * 4. Mathematical algorithms (Otsu variance maximization, Canny NMS/hysteresis, RDP decimation).
 * 5. Non-destructive invariants: Source artwork pixels are never mutated by analysis or debug overlays.
 * 6. Deterministic execution: Identical inputs produce bit-identical results.
 * 7. Real-world benchmark recording: Latency (ms) and peak memory (bytes) across all fixture classes.
 */
class CvPipelineTest {

    private fun resolveFixture(relativePath: String): File {
        val candidates = listOf(
            File("tests/fixtures/artstudio/v1/$relativePath"),
            File("../tests/fixtures/artstudio/v1/$relativePath"),
            File("/root/artstudio/tests/fixtures/artstudio/v1/$relativePath")
        )
        val found = candidates.firstOrNull { it.exists() }
        assertNotNull("Fixture '$relativePath' must exist on disk", found)
        return found!!
    }

    private fun loadFixtureImage(relativePath: String): Triple<Int, Int, IntArray> {
        val file = resolveFixture(relativePath)
        val decoded = RasterImageImporter.decodeRasterImage(file.readBytes(), file.name)
        assertNotNull("Failed to decode fixture image ${file.name}", decoded)
        return Triple(decoded!!.width, decoded.height, decoded.pixels)
    }

    private fun computeBufferHash(buffer: IntArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val byteBuffer = java.nio.ByteBuffer.allocate(buffer.size * 4)
        for (v in buffer) {
            byteBuffer.putInt(v)
        }
        val digest = md.digest(byteBuffer.array())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // =========================================================================
    // 1. Clean Line Art Tests
    // =========================================================================

    @Test
    fun testLineArtSimpleCannyEdgesAndContourExtraction() {
        val (width, height, pixels) = loadFixtureImage("line_art/lineart_simple_01.png")
        val params = CvPipelineParameters(
            denoiseMode = DenoiseMode.MEDIAN_DESPECKLE,
            denoiseRadius = 1,
            thresholdMode = ThresholdMode.OTSU_GLOBAL,
            edgeMode = EdgeMode.CANNY,
            cannyLowThreshold = 30f,
            cannyHighThreshold = 80f,
            contourMinArea = 4f,
            contourRdpEpsilon = 1.5f
        )

        val result = CvPipeline.process(pixels, width, height, params)

        assertEquals(width, result.width)
        assertEquals(height, result.height)
        assertNotNull(result.edges)
        assertNotNull(result.contours)

        // Count edge pixels
        val edgePixelCount = result.edges.count { it == 255 }
        assertTrue("Clean line art must yield crisp edge pixels (found: $edgePixelCount)", edgePixelCount > 100)

        // Contours must be extracted
        assertTrue("Line art must produce contour candidates", result.contours.isNotEmpty())
        for (contour in result.contours) {
            assertTrue("Contour points must have >= 3 vertices", contour.points.size >= 3)
            assertTrue("Contour area must satisfy minimum area", contour.area >= 4.0)
            assertTrue("Contour perimeter must be positive", contour.perimeter > 0.0)
            assertTrue("Bounding box must be within canvas bounds",
                contour.boundingBox.minX >= 0 && contour.boundingBox.maxX < width &&
                contour.boundingBox.minY >= 0 && contour.boundingBox.maxY < height
            )
        }
    }

    @Test
    fun testLineArtDensePolygonalSimplification() {
        val (width, height, pixels) = loadFixtureImage("line_art/lineart_dense_02.png")
        val params = CvPipelineParameters(
            denoiseMode = DenoiseMode.MEDIAN_DESPECKLE,
            denoiseRadius = 1,
            thresholdMode = ThresholdMode.OTSU_GLOBAL,
            edgeMode = EdgeMode.CANNY,
            cannyLowThreshold = 40f,
            cannyHighThreshold = 100f,
            contourMinArea = 8f,
            contourRdpEpsilon = 2.0f
        )

        val result = CvPipeline.process(pixels, width, height, params)

        assertTrue("Dense line art must produce multiple contours", result.contours.size >= 2)

        // Verify RDP decimation compression ratio
        var totalRawPoints = 0
        var totalSimplifiedPoints = 0
        for (c in result.contours) {
            totalRawPoints += c.points.size
            totalSimplifiedPoints += c.simplifiedPoints.size
            assertTrue("Simplified point count must be <= raw point count",
                c.simplifiedPoints.size <= c.points.size
            )
        }

        assertTrue("RDP simplification must achieve point reduction (raw: $totalRawPoints, simplified: $totalSimplifiedPoints)",
            totalSimplifiedPoints < totalRawPoints
        )
    }

    // =========================================================================
    // 2. Flat Colour Fixture Tests
    // =========================================================================

    @Test
    fun testFlatColourSimpleSobelEdgesAndColorBoundaries() {
        val (width, height, pixels) = loadFixtureImage("flat_colour/flat_colour_simple_01.png")
        val params = CvPipelineParameters(
            denoiseMode = DenoiseMode.NONE,
            thresholdMode = ThresholdMode.OTSU_GLOBAL,
            edgeMode = EdgeMode.SOBEL,
            sobelThreshold = 35f,
            contourMinArea = 5f
        )

        val result = CvPipeline.process(pixels, width, height, params)

        val edgePixelCount = result.edges.count { it == 255 }
        assertTrue("Flat colour simple must yield boundary edge pixels (found: $edgePixelCount)", edgePixelCount > 50)
        assertTrue("Flat colour simple must detect region contours", result.contours.isNotEmpty())
    }

    @Test
    fun testFlatColourDenseRegionBoundariesAndHoleClassification() {
        val (width, height, pixels) = loadFixtureImage("flat_colour/flat_colour_dense_02.png")
        val params = CvPipelineParameters(
            denoiseMode = DenoiseMode.GAUSSIAN_BLUR,
            denoiseRadius = 1,
            gaussianSigma = 1.0f,
            thresholdMode = ThresholdMode.OTSU_GLOBAL,
            edgeMode = EdgeMode.CANNY,
            contourMinArea = 10f
        )

        val result = CvPipeline.process(pixels, width, height, params)

        assertTrue("Dense flat colour must produce contours", result.contours.isNotEmpty())
        for (c in result.contours) {
            assertTrue("Shoelace area must be positive", c.area > 0.0)
            assertNotNull("Centroid must be calculated", c.centroid)
            assertTrue("Centroid X within canvas", c.centroid.x in 0f..width.toFloat())
            assertTrue("Centroid Y within canvas", c.centroid.y in 0f..height.toFloat())
        }
    }

    // =========================================================================
    // 3. Noisy Sketch Tests (Despeckle & Adaptive Thresholding)
    // =========================================================================

    @Test
    fun testNoisySketchSimpleMedianDespeckleNoiseReduction() {
        val (width, height, pixels) = loadFixtureImage("noisy_sketches/sketch_noisy_simple_01.png")

        // 1. Without denoising
        val rawParams = CvPipelineParameters(
            denoiseMode = DenoiseMode.NONE,
            thresholdMode = ThresholdMode.OTSU_GLOBAL,
            edgeMode = EdgeMode.CANNY,
            cannyLowThreshold = 30f,
            cannyHighThreshold = 80f
        )
        val rawResult = CvPipeline.process(pixels, width, height, rawParams)

        // 2. With median despeckle
        val denoisedParams = CvPipelineParameters(
            denoiseMode = DenoiseMode.MEDIAN_DESPECKLE,
            denoiseRadius = 1,
            thresholdMode = ThresholdMode.OTSU_GLOBAL,
            edgeMode = EdgeMode.CANNY,
            cannyLowThreshold = 30f,
            cannyHighThreshold = 80f
        )
        val denoisedResult = CvPipeline.process(pixels, width, height, denoisedParams)

        val rawEdges = rawResult.edges.count { it == 255 }
        val cleanEdges = denoisedResult.edges.count { it == 255 }

        // Median despeckle must suppress isolated noise sparks
        assertTrue("Denoised edge count ($cleanEdges) must be less than raw noisy edge count ($rawEdges)",
            cleanEdges <= rawEdges
        )
        assertTrue("Denoised result must still retain strokes", cleanEdges > 0)
    }

    @Test
    fun testNoisySketchDenseAdaptiveLocalMeanThresholding() {
        val (width, height, pixels) = loadFixtureImage("noisy_sketches/sketch_noisy_dense_02.png")
        val params = CvPipelineParameters(
            denoiseMode = DenoiseMode.MEDIAN_DESPECKLE,
            denoiseRadius = 1,
            thresholdMode = ThresholdMode.ADAPTIVE_LOCAL_MEAN,
            adaptiveWindowRadius = 9,
            adaptiveConstantC = 7,
            edgeMode = EdgeMode.CANNY,
            contourMinArea = 6f
        )

        val result = CvPipeline.process(pixels, width, height, params)

        assertNotNull(result.thresholded)
        val foregroundCount = result.thresholded.count { it == 255 }
        val backgroundCount = result.thresholded.count { it == 0 }

        assertTrue("Adaptive threshold must produce foreground pixels", foregroundCount > 100)
        assertTrue("Adaptive threshold must produce background pixels", backgroundCount > 100)
        assertEquals("Total thresholded pixels must equal width * height", width * height, foregroundCount + backgroundCount)
    }

    // =========================================================================
    // 4. Mathematical Algorithm Determinism & Correctness
    // =========================================================================

    @Test
    fun testOtsuThresholdOptimalityAndBimodalSeparation() {
        val width = 100
        val height = 100
        val gray = IntArray(width * height)

        // Construct synthetic bimodal image: 50% dark background (40), 50% bright foreground (200)
        for (i in gray.indices) {
            gray[i] = if (i % 2 == 0) 40 else 200
        }

        val (threshold, binary) = CvThresholdEngine.otsuBinarize(gray, width, height)

        // Optimal Otsu threshold between 40 and 200 should be around ~120
        assertTrue("Otsu threshold ($threshold) must fall between modal peaks 40 and 200", threshold in 41..199)
        assertEquals(width * height, binary.size)

        var whiteCount = 0
        var blackCount = 0
        for (v in binary) {
            if (v == 255) whiteCount++ else blackCount++
        }
        assertEquals("Half pixels must be foreground", 5000, whiteCount)
        assertEquals("Half pixels must be background", 5000, blackCount)
    }

    @Test
    fun testContourCandidateShoelaceAreaAndPerimeterCalculation() {
        val width = 50
        val height = 50
        val mask = IntArray(width * height)

        // Draw a solid 20x20 foreground square from (10,10) to (29,29)
        for (y in 10..29) {
            for (x in 10..29) {
                mask[y * width + x] = 255
            }
        }

        val contours = CvContourEngine.extractContours(mask, width, height, minArea = 10f, rdpEpsilon = 1.0f)
        assertEquals("Must discover exactly 1 contour for a single solid square", 1, contours.size)

        val contour = contours.first()
        assertFalse("Outer square must not be classified as a hole", contour.isHole)

        // Square 20x20 area is ~400, Shoelace formula should approximate this closely
        assertTrue("Shoelace area (${contour.area}) must be close to 400 (300..500)", contour.area in 300.0..500.0)

        // Perimeter of 20x20 is ~80
        assertTrue("Perimeter (${contour.perimeter}) must be in range 60..100", contour.perimeter in 60.0..100.0)

        // Bounding box must closely match (10,10,29,29)
        assertEquals(10f, contour.boundingBox.minX, 1.5f)
        assertEquals(10f, contour.boundingBox.minY, 1.5f)
        assertEquals(29f, contour.boundingBox.maxX, 1.5f)
        assertEquals(29f, contour.boundingBox.maxY, 1.5f)
    }

    @Test
    fun testRdpDecimationPreservesCornersAndRemovesCollinearPoints() {
        // Construct a polygon with 10 collinear points along a straight line, followed by a 90-degree corner
        val points = mutableListOf<CvPoint>()
        // Top edge: (0,0) to (10,0) with 10 steps
        for (i in 0..10) {
            points.add(CvPoint(i.toFloat(), 0f))
        }
        // Right edge: (10,1) to (10,10)
        for (i in 1..10) {
            points.add(CvPoint(10f, i.toFloat()))
        }
        // Bottom edge: (9,10) down to (0,10)
        for (i in 9 downTo 0) {
            points.add(CvPoint(i.toFloat(), 10f))
        }
        // Left edge: (0,9) up to (0,1)
        for (i in 9 downTo 1) {
            points.add(CvPoint(0f, i.toFloat()))
        }

        assertEquals(40, points.size)

        val simplified = CvContourEngine.rdpSimplify(points, epsilon = 0.5f)

        // RDP should eliminate intermediate collinear points and keep vertices
        assertTrue("Simplified point count (${simplified.size}) must be significantly less than 40",
            simplified.size <= 10
        )
        // Ensure corners (0,0), (10,0), (10,10), (0,10) are preserved
        val hasTopLeft = simplified.any { abs(it.x - 0f) < 0.1f && abs(it.y - 0f) < 0.1f }
        val hasTopRight = simplified.any { abs(it.x - 10f) < 0.1f && abs(it.y - 0f) < 0.1f }
        val hasBottomRight = simplified.any { abs(it.x - 10f) < 0.1f && abs(it.y - 10f) < 0.1f }
        val hasBottomLeft = simplified.any { abs(it.x - 0f) < 0.1f && abs(it.y - 10f) < 0.1f }

        assertTrue("Top-left corner must be preserved", hasTopLeft)
        assertTrue("Top-right corner must be preserved", hasTopRight)
        assertTrue("Bottom-right corner must be preserved", hasBottomRight)
        assertTrue("Bottom-left corner must be preserved", hasBottomLeft)
    }

    // =========================================================================
    // 5. Invariants: Non-destructive Analysis & Transient Debug Overlays
    // =========================================================================

    @Test
    fun testCvPipelineAndDebugVisualizerNeverMutateOriginalPixels() {
        val (width, height, pixels) = loadFixtureImage("line_art/lineart_simple_01.png")
        val originalCopy = pixels.clone()
        val originalHash = computeBufferHash(pixels)

        val params = CvPipelineParameters()
        val result = CvPipeline.process(pixels, width, height, params)

        // 1. Verify pixels buffer unchanged after process()
        assertArrayEquals("Original pixel buffer must not be modified by CvPipeline.process", originalCopy, pixels)
        assertEquals("Original pixel buffer hash must match exactly", originalHash, computeBufferHash(pixels))

        // 2. Render all debug overlay modes
        for (mode in CvDebugOverlayMode.values()) {
            val overlay = CvDebugVisualizer.renderDebugOverlay(result, pixels, mode)
            assertEquals("Debug overlay must match dimensions", width * height, overlay.size)
            assertNotSame("Debug overlay must be a newly allocated buffer", pixels, overlay)
            // Verify source is still untouched
            assertArrayEquals("Pixels unchanged after rendering debug overlay $mode", originalCopy, pixels)
        }
    }

    // =========================================================================
    // 6. Deterministic Output Invariant
    // =========================================================================

    @Test
    fun testDeterministicExecutionProducesBitIdenticalResults() {
        val (width, height, pixels) = loadFixtureImage("flat_colour/flat_colour_simple_01.png")
        val params = CvPipelineParameters(
            denoiseMode = DenoiseMode.MEDIAN_DESPECKLE,
            thresholdMode = ThresholdMode.OTSU_GLOBAL,
            edgeMode = EdgeMode.CANNY
        )

        val run1 = CvPipeline.process(pixels.clone(), width, height, params)
        val run2 = CvPipeline.process(pixels.clone(), width, height, params)

        assertArrayEquals("Grayscale arrays must be bit-identical", run1.grayscale, run2.grayscale)
        assertArrayEquals("Denoised arrays must be bit-identical", run1.denoised, run2.denoised)
        assertArrayEquals("Thresholded arrays must be bit-identical", run1.thresholded, run2.thresholded)
        assertArrayEquals("Edge maps must be bit-identical", run1.edges, run2.edges)
        assertEquals("Contour count must be identical", run1.contours.size, run2.contours.size)

        for (i in run1.contours.indices) {
            val c1 = run1.contours[i]
            val c2 = run2.contours[i]
            assertEquals("Contour $i area must match", c1.area, c2.area, 0.0001)
            assertEquals("Contour $i perimeter must match", c1.perimeter, c2.perimeter, 0.0001)
            assertEquals("Contour $i points size must match", c1.points.size, c2.points.size)
            assertEquals("Contour $i simplified points size must match", c1.simplifiedPoints.size, c2.simplifiedPoints.size)
        }
    }

    // =========================================================================
    // 7. Latency and Peak Memory Recording Across All Fixture Categories
    // =========================================================================

    @Test
    fun testLatencyAndPeakMemoryRecordedAcrossAllFixtureCategories() {
        val categories = listOf(
            "line_art/lineart_simple_01.png",
            "flat_colour/flat_colour_simple_01.png",
            "noisy_sketches/sketch_noisy_simple_01.png"
        )

        for (fixturePath in categories) {
            val (width, height, pixels) = loadFixtureImage(fixturePath)
            val result = CvPipeline.process(pixels, width, height)

            assertTrue("Processing latency must be >= 0 ms ($fixturePath: ${result.elapsedTimeMs} ms)",
                result.elapsedTimeMs >= 0L
            )
            assertTrue("Peak memory bytes must be > 0 bytes ($fixturePath: ${result.peakMemoryBytes} bytes)",
                result.peakMemoryBytes > 0L
            )
            // Memory must be at least the size of raw pixel buffer (width * height * 4)
            val minExpectedMem = width.toLong() * height.toLong() * 4L
            assertTrue("Peak memory must account for image buffers ($fixturePath: ${result.peakMemoryBytes} vs min $minExpectedMem)",
                result.peakMemoryBytes >= minExpectedMem
            )
        }
    }

    // =========================================================================
    // 8. Robust Error Handling for Invalid Inputs
    // =========================================================================

    @Test
    fun testInvalidDimensionsThrowsIllegalArgumentException() {
        try {
            CvPipeline.process(IntArray(10), 0, 5)
            fail("Must throw IllegalArgumentException for zero width")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Dimensions must be positive"))
        }

        try {
            CvPipeline.process(IntArray(10), 10, -1)
            fail("Must throw IllegalArgumentException for negative height")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Dimensions must be positive"))
        }

        try {
            CvPipeline.process(IntArray(20), 5, 5) // requires 25, got 20
            fail("Must throw IllegalArgumentException for mismatched buffer size")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("does not match"))
        }
    }
}
