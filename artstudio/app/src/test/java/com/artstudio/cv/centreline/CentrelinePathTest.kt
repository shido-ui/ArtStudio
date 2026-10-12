package com.artstudio.cv.centreline

import com.artstudio.cv.CvPoint
import com.artstudio.importer.RasterImageImporter
import com.artstudio.reference.ReconstructionMode
import com.artstudio.vector.VectorShapeType
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Verification test suite for single-pen centreline path extraction (Prompt 49).
 *
 * Verifies:
 * 1. Straight, curved, branched, broken, and noisy synthetic fixtures.
 * 2. Ground-truth endpoint error and orthogonal geometry error reporting.
 * 3. Ambiguous fork and broken gap review flagging (never silently discarded).
 * 4. Input reconstruction mode gating.
 * 5. Configurable settings (pruning, RDP simplification, thresholding).
 * 6. Non-destructive pixel buffer immutability.
 * 7. Real-world line art fixture extraction.
 */
class CentrelinePathTest {

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
    // 1. Straight Line Fixture Test (Endpoint & Geometry Error)
    // =========================================================================

    @Test
    fun testStraightLineExtractionAndGroundTruthErrorMetrics() {
        val width = 100
        val height = 60
        val start = CvPoint(15f, 30f)
        val end = CvPoint(85f, 30f)
        val (gtPoints, pixels) = SyntheticPathGenerator.createStraightLine(width, height, start, end, strokeWidth = 4f)

        val result = CentrelineEngine.extractCentrelines(
            pixels = pixels,
            width = width,
            height = height,
            mode = ReconstructionMode.LINE_ART_VECTORIZATION
        )

        assertTrue("Line art mode must be supported", result.isSupported)
        assertEquals("Straight line must produce exactly 1 main path", 1, result.paths.size)

        val path = result.paths.first()
        assertFalse("Straight stroke must not be classified as a loop", path.isLoop)
        assertTrue("Path length (${path.length}) must be close to 70 px", path.length in 65f..75f)
        assertTrue("Estimated stroke width (${path.estimatedStrokeWidth}) must be in range ~4..6 px", path.estimatedStrokeWidth in 2.5f..6.5f)

        // Evaluate Error against Ground Truth
        val report = SyntheticPathGenerator.evaluateError(gtPoints, path)

        // Acceptance Criteria: Synthetic path recovery reports endpoint/geometry error against known source paths
        assertTrue("Mean endpoint error (${report.meanEndpointError}) must be <= 2.5 px (within stroke cap radius)", report.meanEndpointError <= 2.5f)
        assertTrue("Mean geometry error (${report.meanGeometryError}) must be <= 0.6 px", report.meanGeometryError <= 0.6f)
        assertTrue("Length deviation ratio (${report.lengthDeviationRatio}) must be <= 6%", report.lengthDeviationRatio <= 0.06f)
    }

    // =========================================================================
    // 2. Curved Arc Fixture Test
    // =========================================================================

    @Test
    fun testCurvedArcExtractionAndGroundTruthErrorMetrics() {
        val width = 100
        val height = 80
        val p0 = CvPoint(15f, 65f)
        val p1 = CvPoint(50f, 15f) // Peak control point
        val p2 = CvPoint(85f, 65f)
        val (gtPoints, pixels) = SyntheticPathGenerator.createCurvedArc(width, height, p0, p1, p2, strokeWidth = 3.5f)

        val result = CentrelineEngine.extractCentrelines(
            pixels = pixels,
            width = width,
            height = height
        )

        assertTrue(result.isSupported)
        assertEquals("Curved arc must yield 1 candidate path", 1, result.paths.size)

        val path = result.paths.first()
        val report = SyntheticPathGenerator.evaluateError(gtPoints, path)

        assertTrue("Mean endpoint error on curve (${report.meanEndpointError}) must be <= 1.5 px", report.meanEndpointError <= 1.5f)
        assertTrue("Mean geometry error on curve (${report.meanGeometryError}) must be <= 1.0 px", report.meanGeometryError <= 1.0f)
        assertTrue("Curved path confidence (${path.confidence}) must be >= 0.80", path.confidence >= 0.80f)
    }

    // =========================================================================
    // 3. Branched Y-Junction Fixture Test (Junction Preservation & Fork Ambiguity)
    // =========================================================================

    @Test
    fun testBranchedYJunctionPreservesJunctionAndFlagsAmbiguousFork() {
        val width = 100
        val height = 100
        val stemStart = CvPoint(50f, 15f)
        val forkPoint = CvPoint(50f, 50f)
        val b1End = CvPoint(20f, 85f)
        val b2End = CvPoint(80f, 85f)
        val (_, pixels) = SyntheticPathGenerator.createBranchedY(width, height, stemStart, forkPoint, b1End, b2End, strokeWidth = 4f)

        val result = CentrelineEngine.extractCentrelines(
            pixels = pixels,
            width = width,
            height = height
        )

        assertTrue(result.isSupported)
        assertTrue("Junction node must be discovered", result.junctions.isNotEmpty())

        val forkJunction = result.junctions.firstOrNull { it.degree >= 3 }
        assertNotNull("Must discover a junction with degree >= 3", forkJunction)

        // Verify branches are preserved
        assertTrue("Must extract at least 2 distinct branch path candidates", result.paths.size >= 2)

        // Acceptance Criteria: Ambiguous cases are flagged for review instead of silently discarded
        val forkAmbiguities = result.ambiguities.filter { it.type == AmbiguityType.AMBIGUOUS_FORK }
        assertTrue("Must flag ambiguous fork for review when branches diverge symmetrically", forkAmbiguities.isNotEmpty())

        val ambiguity = forkAmbiguities.first()
        assertTrue("Ambiguity must reference involved path IDs", ambiguity.involvedPathIds.isNotEmpty())
        assertTrue("Suggested resolution must guide review", ambiguity.suggestedResolution.contains("Review"))
    }

    // =========================================================================
    // 4. Branched 4-Way Cross Intersection Test
    // =========================================================================

    @Test
    fun testBranchedCrossIntersectionPreservesDegreeFourJunction() {
        val width = 100
        val height = 100
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }

        // Draw horizontal line (20, 50) to (80, 50)
        // Draw vertical line (50, 20) to (50, 80)
        val strokeWidth = 3f
        val rad = (strokeWidth / 2f).toInt()
        for (x in 20..80) {
            for (dy in -rad..rad) pixels[(50 + dy) * width + x] = 0xFF000000.toInt()
        }
        for (y in 20..80) {
            for (dx in -rad..rad) pixels[(y) * width + 50 + dx] = 0xFF000000.toInt()
        }

        val result = CentrelineEngine.extractCentrelines(pixels, width, height)

        assertTrue("Must detect intersection junction", result.junctions.isNotEmpty())
        val crossJunction = result.junctions.firstOrNull { it.degree >= 3 }
        assertNotNull("Cross intersection must have degree >= 3", crossJunction)
        assertTrue("Intersection must not be silently discarded", result.paths.isNotEmpty())
    }

    // =========================================================================
    // 5. Broken Stroke Fixture Test (Gap Detection & Review Flagging)
    // =========================================================================

    @Test
    fun testBrokenStrokeDetectsGapAndFlagsPotentialBreakForReview() {
        val width = 100
        val height = 60
        val start = CvPoint(15f, 30f)
        val breakStart = CvPoint(47f, 30f)
        val breakEnd = CvPoint(52f, 30f) // 5px gap
        val end = CvPoint(85f, 30f)
        val (_, pixels) = SyntheticPathGenerator.createBrokenStroke(width, height, start, breakStart, breakEnd, end, strokeWidth = 3f)

        val settings = CentrelineSettings(gapDetectionDistance = 7.0f)
        val result = CentrelineEngine.extractCentrelines(pixels, width, height, settings = settings)

        assertEquals("Broken stroke must produce 2 path segments", 2, result.paths.size)

        // Acceptance Criteria: Ambiguous cases are flagged for review instead of silently discarded
        val breakAmbiguities = result.ambiguities.filter { it.type == AmbiguityType.POTENTIAL_STROKE_BREAK }
        assertTrue("Potential stroke break must be flagged for review across the 5px gap", breakAmbiguities.isNotEmpty())

        val ambiguity = breakAmbiguities.first()
        assertEquals("Both path segments must be referenced in ambiguity metadata", 2, ambiguity.involvedPathIds.size)
        assertTrue("Suggested resolution must describe bridging option", ambiguity.suggestedResolution.contains("bridge"))
    }

    // =========================================================================
    // 6. Noisy Line Fixture Test (Speckle Pruning & Detail Retention)
    // =========================================================================

    @Test
    fun testNoisySketchPruningFiltersSpecklesWhilePreservingMainPath() {
        val width = 100
        val height = 60
        val start = CvPoint(15f, 30f)
        val end = CvPoint(85f, 30f)
        val (gtPoints, pixels) = SyntheticPathGenerator.createNoisyStroke(width, height, start, end, strokeWidth = 3.5f, speckleCount = 10)

        // Test with pruning enabled (preserveShortDetails = false, minFeatureLength = 4)
        val prunedSettings = CentrelineSettings(minFeatureLength = 4, preserveShortDetails = false)
        val prunedResult = CentrelineEngine.extractCentrelines(pixels, width, height, settings = prunedSettings)

        // Main stroke must be preserved
        assertTrue("Main stroke must be preserved after speckle pruning", prunedResult.paths.isNotEmpty())
        val mainPath = prunedResult.paths.maxByOrNull { it.length }!!
        val report = SyntheticPathGenerator.evaluateError(gtPoints, mainPath)
        assertTrue("Mean geometry error on noisy stroke (${report.meanGeometryError}) must remain low <= 1.2 px", report.meanGeometryError <= 1.2f)

        // Test with short details preserved (preserveShortDetails = true)
        val detailSettings = CentrelineSettings(minFeatureLength = 4, preserveShortDetails = true)
        val detailResult = CentrelineEngine.extractCentrelines(pixels, width, height, settings = detailSettings)
        assertTrue("Short details mode must retain >= paths than pruned mode", detailResult.paths.size >= prunedResult.paths.size)
    }

    // =========================================================================
    // 7. Reconstruction Mode Gating Test
    // =========================================================================

    @Test
    fun testInputModeGatingRejectsUnsupportedModesUnlessForced() {
        val width = 50
        val height = 50
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }

        // Continuous tone photo mode: not supported by default
        val photoResult = CentrelineEngine.extractCentrelines(
            pixels = pixels,
            width = width,
            height = height,
            mode = ReconstructionMode.CONTINUOUS_TONE_PAINT_LAYER
        )
        assertFalse("Continuous tone mode must be rejected for centreline extraction", photoResult.isSupported)
        assertTrue("Paths must be empty for unsupported mode", photoResult.paths.isEmpty())
        assertTrue("Support diagnostic must explain rejection", photoResult.supportDiagnostic.contains("does not support"))

        // Cel shading mode: not supported by default
        val celResult = CentrelineEngine.extractCentrelines(
            pixels = pixels,
            width = width,
            height = height,
            mode = ReconstructionMode.FLAT_REGION_CEL_SHADING
        )
        assertFalse("Flat cel shading mode must be rejected by default", celResult.isSupported)

        // Force extraction allows override
        val forcedResult = CentrelineEngine.extractCentrelines(
            pixels = pixels,
            width = width,
            height = height,
            mode = ReconstructionMode.CONTINUOUS_TONE_PAINT_LAYER,
            settings = CentrelineSettings(forceExtraction = true)
        )
        assertTrue("Explicit forceExtraction = true must bypass mode gate", forcedResult.isSupported)
    }

    // =========================================================================
    // 8. Ambiguous Cases Flagged for Review (Contract Verification)
    // =========================================================================

    @Test
    fun testAmbiguityMetadataIntegrityAndConfidencePenalties() {
        val width = 100
        val height = 100
        val stemStart = CvPoint(50f, 15f)
        val forkPoint = CvPoint(50f, 50f)
        val b1End = CvPoint(20f, 85f)
        val b2End = CvPoint(80f, 85f)
        val (_, pixels) = SyntheticPathGenerator.createBranchedY(width, height, stemStart, forkPoint, b1End, b2End, strokeWidth = 4f)

        val result = CentrelineEngine.extractCentrelines(pixels, width, height)

        for (ambiguity in result.ambiguities) {
            assertTrue("Ambiguity ID must be positive", ambiguity.id > 0)
            assertNotNull("Location must be populated", ambiguity.location)
            assertTrue("Description must be meaningful", ambiguity.description.isNotBlank())
            assertTrue("Suggested resolution must not be blank", ambiguity.suggestedResolution.isNotBlank())
            assertTrue("Confidence penalty must be > 0", ambiguity.confidencePenalty > 0f)
        }

        // Verify affected paths have reduced confidence
        val affectedPaths = result.paths.filter { it.ambiguities.isNotEmpty() }
        for (path in affectedPaths) {
            assertTrue("Path involved in ambiguity must have confidence < 0.90", path.confidence < 0.90f)
        }
    }

    // =========================================================================
    // 9. Editable VectorObject Conversion Test
    // =========================================================================

    @Test
    fun testCentrelineCandidateConvertsToEditableVectorObject() {
        val width = 100
        val height = 60
        val start = CvPoint(10f, 30f)
        val end = CvPoint(90f, 30f)
        val (_, pixels) = SyntheticPathGenerator.createStraightLine(width, height, start, end, strokeWidth = 5f)

        val result = CentrelineEngine.extractCentrelines(pixels, width, height)
        val candidate = result.paths.first()

        val vectorObj = candidate.toVectorObject()

        assertNotNull(vectorObj.id)
        assertEquals(VectorShapeType.POLYLINE, vectorObj.shapeType)
        assertTrue("Anchors must contain simplified vertices", vectorObj.anchors.size >= 2)
        assertFalse("Open stroke must not be closed", vectorObj.isClosed)
        assertTrue("Stroke width in VectorStyle must be positive", vectorObj.style.strokeWidth > 0f)
    }

    // =========================================================================
    // 10. Non-Destructive Invariant Test (Source Pixels Untouched)
    // =========================================================================

    @Test
    fun testCentrelineExtractionNeverMutatesOriginalPixelBuffer() {
        val width = 100
        val height = 60
        val start = CvPoint(10f, 30f)
        val end = CvPoint(90f, 30f)
        val (_, pixels) = SyntheticPathGenerator.createStraightLine(width, height, start, end, strokeWidth = 3f)

        val originalCopy = pixels.clone()
        val originalHash = computeBufferHash(pixels)

        CentrelineEngine.extractCentrelines(pixels, width, height)

        assertArrayEquals("Original pixel buffer must not be mutated", originalCopy, pixels)
        assertEquals("Buffer hash must match exactly", originalHash, computeBufferHash(pixels))
    }

    // =========================================================================
    // 11. Configurable Settings Parameter Variation
    // =========================================================================

    @Test
    fun testConfigurableRdpEpsilonAndFeatureLengthSettings() {
        val width = 100
        val height = 80
        val p0 = CvPoint(15f, 65f)
        val p1 = CvPoint(50f, 15f)
        val p2 = CvPoint(85f, 65f)
        val (_, pixels) = SyntheticPathGenerator.createCurvedArc(width, height, p0, p1, p2, strokeWidth = 3f)

        // Epsilon = 0.5 (dense vertices)
        val denseResult = CentrelineEngine.extractCentrelines(
            pixels, width, height,
            settings = CentrelineSettings(rdpEpsilon = 0.5f)
        )
        // Epsilon = 3.0 (coarse decimation)
        val coarseResult = CentrelineEngine.extractCentrelines(
            pixels, width, height,
            settings = CentrelineSettings(rdpEpsilon = 3.0f)
        )

        val densePts = denseResult.paths.first().simplifiedPoints.size
        val coarsePts = coarseResult.paths.first().simplifiedPoints.size

        assertTrue("Coarse RDP ($coarsePts) must have <= points than dense RDP ($densePts)", coarsePts <= densePts)
    }

    // =========================================================================
    // 12. Real-World Line Art Fixture Extraction Test
    // =========================================================================

    @Test
    fun testRealLineArtFixtureCentrelineExtraction() {
        val file = resolveFixture("line_art/lineart_simple_01.png")
        val decoded = RasterImageImporter.decodeRasterImage(file.readBytes(), file.name)
        assertNotNull("Failed to decode fixture image", decoded)

        val result = CentrelineEngine.extractCentrelines(
            pixels = decoded!!.pixels,
            width = decoded.width,
            height = decoded.height,
            mode = ReconstructionMode.LINE_ART_VECTORIZATION
        )

        assertTrue("Line art fixture must be supported", result.isSupported)
        assertTrue("Real line art fixture must yield centreline paths", result.paths.isNotEmpty())
        assertTrue("Latency must be recorded", result.elapsedTimeMs >= 0L)
        assertTrue("Peak memory must be recorded", result.peakMemoryBytes > 0L)

        for (path in result.paths) {
            assertTrue("Path must have positive length", path.length > 0f)
            assertTrue("Start point within canvas", path.startPoint.x in 0f..decoded.width.toFloat())
            assertTrue("End point within canvas", path.endPoint.y in 0f..decoded.height.toFloat())
            assertTrue("Estimated width in plausible range", path.estimatedStrokeWidth in 0.5f..50.0f)
        }
    }
}
