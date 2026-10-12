package com.artstudio.reference

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Comprehensive test suite for ReferenceInspector covering all fixture categories:
 * - Rotated EXIF (90° CW, 180°)
 * - Transparent PNG (binary mask, continuous gradient)
 * - Line Art (simple, dense)
 * - Flat Colour (simple, dense)
 * - Noisy Sketches (simple, dense)
 * - Corrupt / Truncated (PNG, ORA, SVG)
 * - Memory estimation and decode peak measurements across multiple fixture sizes
 * - Preserved source immutability invariant
 */
class ReferenceInspectorTest {

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

    private fun computeFileSha256(file: File): String {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // =========================================================================
    // 1. EXIF Rotated Fixture (90° CW)
    // =========================================================================

    @Test
    fun testInspectExifRotated90CwFixtureProducesCorrectOrientedCoordinates() {
        val file = resolveFixture("exif_rotated/exif_rotate_90_cw_01.jpg")
        val hashBefore = computeFileSha256(file)

        val report = ReferenceInspector.inspectFile(file)

        assertTrue("Inspection of valid JPEG must succeed", report.isSuccess)
        assertFalse("Valid JPEG must not be corrupt", report.isCorrupt)
        assertEquals(ReferenceFormat.JPEG, report.format)

        // Raw dimensions are 512x256; EXIF orientation is 6 (90° CW)
        assertEquals(Pair(512, 256), report.rawDimensions)
        assertEquals(6, report.exifOrientation)

        // Normalized dimensions must be 256x512 (transposed by 90° CW rotation)
        assertEquals(Pair(256, 512), report.normalizedDimensions)

        val norm = report.coordinateNormalization
        assertEquals(256, norm.normalizedWidth)
        assertEquals(512, norm.normalizedHeight)

        // Test Coordinate Mapping:
        // Raw (0, 0) -> Under 90 CW: normX = 256 - 1 - yRaw = 255, normY = xRaw = 0
        val (normX0, normY0) = norm.rawToNormalizedPixel(0, 0)
        assertEquals(255, normX0)
        assertEquals(0, normY0)

        // Inverse mapping: Norm (255, 0) -> Raw (0, 0)
        val (rawX0, rawY0) = norm.normalizedToRawPixel(255, 0)
        assertEquals(0, rawX0)
        assertEquals(0, rawY0)

        // Center pixel round-trip: Raw (256, 128)
        val (centerNormX, centerNormY) = norm.rawToNormalizedPixel(256, 128)
        val (centerRawX, centerRawY) = norm.normalizedToRawPixel(centerNormX, centerNormY)
        assertEquals(256, centerRawX)
        assertEquals(128, centerRawY)

        // INVARIANT: Source file must remain 100% unaltered
        val hashAfter = computeFileSha256(file)
        assertEquals("Source reference file must never be modified by inspection", hashBefore, hashAfter)
    }

    // =========================================================================
    // 2. EXIF Rotated Fixture (180°)
    // =========================================================================

    @Test
    fun testInspectExifRotated180FixtureProducesInvertedCoordinates() {
        val file = resolveFixture("exif_rotated/exif_rotate_180_02.jpg")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.JPEG, report.format)
        assertEquals(Pair(512, 512), report.rawDimensions)
        assertEquals(Pair(512, 512), report.normalizedDimensions)
        assertEquals(3, report.exifOrientation)

        val norm = report.coordinateNormalization
        // Under 180° inversion: Raw (0, 0) -> Norm (511, 511)
        val (normX, normY) = norm.rawToNormalizedPixel(0, 0)
        assertEquals(511, normX)
        assertEquals(511, normY)

        // Inverse mapping
        val (rawX, rawY) = norm.normalizedToRawPixel(511, 511)
        assertEquals(0, rawX)
        assertEquals(0, rawY)
    }

    // =========================================================================
    // 3. Transparent PNG (Cutout Binary Alpha Mask)
    // =========================================================================

    @Test
    fun testInspectTransparentCutoutFixtureDetectsBinaryAlphaMask() {
        val file = resolveFixture("transparent_png/transparent_cutout_01.png")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.PNG, report.format)
        assertNotNull(report.transparencyProfile)

        val trans = report.transparencyProfile!!
        assertTrue("Cutout fixture must report alpha channel", trans.hasAlpha)
        assertEquals(TransparencyType.BINARY_MASK, trans.type)
        assertTrue("Alpha ratio must be positive", trans.alphaRatio > 0.05f)

        assertNotNull("Cutout must have a non-null content bounding box", trans.contentBoundingBox)
        val bbox = trans.contentBoundingBox!!
        assertTrue("Bounding box width must be <= canvas width", bbox.width <= 512)
        assertTrue("Bounding box height must be <= canvas height", bbox.height <= 512)
    }

    // =========================================================================
    // 4. Transparent PNG (Continuous Gradient Wash)
    // =========================================================================

    @Test
    fun testInspectTransparentGradientFixtureDetectsContinuousAlphaWash() {
        val file = resolveFixture("transparent_png/transparent_gradient_02.png")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.PNG, report.format)
        assertNotNull(report.transparencyProfile)

        val trans = report.transparencyProfile!!
        assertTrue(trans.hasAlpha)
        assertEquals(TransparencyType.SEMI_TRANSPARENT_GRADIENT, trans.type)
        assertTrue("Alpha ratio must reflect semi-transparent pixels", trans.alphaRatio > 0.1f)
    }

    // =========================================================================
    // 5. Line Art Fixture (Simple, 512x512)
    // =========================================================================

    @Test
    fun testInspectLineArtSimpleFixtureSuggestsVectorization() {
        val file = resolveFixture("line_art/lineart_simple_01.png")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.PNG, report.format)
        assertEquals(Pair(512, 512), report.rawDimensions)
        assertEquals(LikelyImageClass.LINE_ART, report.likelyImageClass)

        val suggestion = report.suggestion
        assertEquals(ReconstructionMode.LINE_ART_VECTORIZATION, suggestion.suggestedMode)
        assertEquals(ReconstructionMode.LINE_ART_VECTORIZATION, suggestion.effectiveMode)
        assertTrue("Confidence must be high for pure line art", suggestion.confidence >= 0.85f)
        assertTrue("Visible limitations must be populated", suggestion.visibleLimitations.isNotEmpty())
    }

    // =========================================================================
    // 6. Line Art Fixture (Dense Cross-Hatch, 1024x1024)
    // =========================================================================

    @Test
    fun testInspectLineArtDenseFixtureSuggestsVectorization() {
        val file = resolveFixture("line_art/lineart_dense_02.png")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.PNG, report.format)
        assertEquals(Pair(1024, 1024), report.rawDimensions)
        assertEquals(LikelyImageClass.LINE_ART, report.likelyImageClass)
        assertEquals(ReconstructionMode.LINE_ART_VECTORIZATION, report.suggestion.suggestedMode)
    }

    // =========================================================================
    // 7. Flat Colour Fixture (Simple, 512x512)
    // =========================================================================

    @Test
    fun testInspectFlatColourSimpleFixtureSuggestsCelShading() {
        val file = resolveFixture("flat_colour/flat_colour_simple_01.png")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.PNG, report.format)
        assertNotNull(report.colorProfile)
        assertFalse("Flat colour fixture must not be detected as grayscale", report.colorProfile!!.isGrayscale)
        assertEquals(LikelyImageClass.FLAT_COLOUR, report.likelyImageClass)

        val suggestion = report.suggestion
        assertEquals(ReconstructionMode.FLAT_REGION_CEL_SHADING, suggestion.suggestedMode)
        assertTrue(suggestion.confidence >= 0.85f)
        assertTrue(suggestion.visibleLimitations.isNotEmpty())
    }

    // =========================================================================
    // 8. Flat Colour Fixture (Dense Multi-Region, 1024x1024)
    // =========================================================================

    @Test
    fun testInspectFlatColourDenseFixtureSuggestsCelShading() {
        val file = resolveFixture("flat_colour/flat_colour_dense_02.png")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.PNG, report.format)
        assertEquals(Pair(1024, 1024), report.rawDimensions)
        assertEquals(LikelyImageClass.FLAT_COLOUR, report.likelyImageClass)
        assertEquals(ReconstructionMode.FLAT_REGION_CEL_SHADING, report.suggestion.suggestedMode)
    }

    // =========================================================================
    // 9. Noisy Sketch Fixture (Simple, 512x512)
    // =========================================================================

    @Test
    fun testInspectNoisySketchSimpleFixtureSuggestsPencilCleanup() {
        val file = resolveFixture("noisy_sketches/sketch_noisy_simple_01.png")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.PNG, report.format)
        assertEquals(LikelyImageClass.SKETCH, report.likelyImageClass)

        val suggestion = report.suggestion
        assertEquals(ReconstructionMode.PENCIL_CLEANUP_AND_INK, suggestion.suggestedMode)
        assertTrue(suggestion.confidence >= 0.80f)
        assertTrue(suggestion.visibleLimitations.any { it.contains("threshold", ignoreCase = true) || it.contains("graphite", ignoreCase = true) })
    }

    // =========================================================================
    // 10. Noisy Sketch Fixture (Dense Textured, 1024x1024)
    // =========================================================================

    @Test
    fun testInspectNoisySketchDenseFixtureSuggestsPencilCleanup() {
        val file = resolveFixture("noisy_sketches/sketch_noisy_dense_02.png")
        val report = ReferenceInspector.inspectFile(file)

        assertTrue(report.isSuccess)
        assertEquals(ReferenceFormat.PNG, report.format)
        assertEquals(Pair(1024, 1024), report.rawDimensions)
        assertEquals(LikelyImageClass.SKETCH, report.likelyImageClass)
        assertEquals(ReconstructionMode.PENCIL_CLEANUP_AND_INK, report.suggestion.suggestedMode)
    }

    // =========================================================================
    // 11. Corrupt / Truncated PNG Fails Safely
    // =========================================================================

    @Test
    fun testInspectCorruptTruncatedPngFailsSafely() {
        val file = resolveFixture("corrupt_truncated/truncated_idat_01.png")
        val report = ReferenceInspector.inspectFile(file)

        // Must fail safely without crashing or throwing
        assertFalse("Corrupted input must report isSuccess = false", report.isSuccess)
        assertTrue("Corrupted input must report isCorrupt = true", report.isCorrupt)
        assertNotNull("Error detail must be present", report.errorDetail)
        assertTrue("Error message must indicate corruption", report.errorDetail!!.isNotEmpty())
    }

    // =========================================================================
    // 12. Corrupt OpenRaster Archive Fails Safely
    // =========================================================================

    @Test
    fun testInspectCorruptOpenRasterArchiveFailsSafely() {
        val file = resolveFixture("corrupt_truncated/corrupted_archive_02.ora")
        val report = ReferenceInspector.inspectFile(file)

        assertFalse(report.isSuccess)
        assertTrue(report.isCorrupt)
        assertEquals(ReferenceFormat.OPEN_RASTER, report.format)
        assertNotNull(report.errorDetail)
    }

    // =========================================================================
    // 13. Malformed SVG Fails Safely
    // =========================================================================

    @Test
    fun testInspectMalformedSvgFailsSafely() {
        val file = resolveFixture("corrupt_truncated/malformed_tags_03.svg")
        val report = ReferenceInspector.inspectFile(file)

        assertFalse(report.isSuccess)
        assertTrue(report.isCorrupt)
        assertEquals(ReferenceFormat.SVG, report.format)
        assertNotNull(report.errorDetail)
    }

    // =========================================================================
    // 14. User Mode Override Support
    // =========================================================================

    @Test
    fun testInspectHonorsUserModeOverride() {
        val file = resolveFixture("line_art/lineart_simple_01.png")

        // Default: suggests LINE_ART_VECTORIZATION
        val defaultReport = ReferenceInspector.inspectFile(file)
        assertEquals(ReconstructionMode.LINE_ART_VECTORIZATION, defaultReport.suggestion.suggestedMode)
        assertEquals(ReconstructionMode.LINE_ART_VECTORIZATION, defaultReport.suggestion.effectiveMode)
        assertNull(defaultReport.suggestion.userOverride)

        // User explicit override: change to CONTINUOUS_TONE_PAINT_LAYER
        val overriddenReport = ReferenceInspector.inspectFile(
            file = file,
            options = InspectionOptions(userOverride = ReconstructionMode.CONTINUOUS_TONE_PAINT_LAYER)
        )

        // Suggestion remains LINE_ART_VECTORIZATION, but effectiveMode reflects the override
        assertEquals(ReconstructionMode.LINE_ART_VECTORIZATION, overriddenReport.suggestion.suggestedMode)
        assertEquals(ReconstructionMode.CONTINUOUS_TONE_PAINT_LAYER, overriddenReport.suggestion.userOverride)
        assertEquals(ReconstructionMode.CONTINUOUS_TONE_PAINT_LAYER, overriddenReport.suggestion.effectiveMode)
    }

    // =========================================================================
    // 15. Memory Estimate & Actual Decode Peak Comparison Across Two Sizes
    // =========================================================================

    @Test
    fun testMemoryFootprintAndDecodePeakAcrossTwoFixtureSizes() {
        val file512 = resolveFixture("line_art/lineart_simple_01.png")
        val file1024 = resolveFixture("line_art/lineart_dense_02.png")

        val report512 = ReferenceInspector.inspectFile(file512)
        val report1024 = ReferenceInspector.inspectFile(file1024)

        assertTrue(report512.isSuccess)
        assertTrue(report1024.isSuccess)

        val mem512 = report512.memoryEstimate
        val mem1024 = report1024.memoryEstimate

        // 512x512: 512 * 512 * 4 * 2 = 2,097,152 bytes (2.0 MB)
        assertEquals(2_097_152L, mem512.estimatedDecodedBytes)
        assertTrue("Actual peak decode for 512x512 must be >= pixel buffer size (1 MB)", mem512.actualDecodePeakBytes >= 1_048_576L)
        assertEquals(MemoryAdvice.NORMAL_MEMORY, mem512.advice)

        // 1024x1024: 1024 * 1024 * 4 * 2 = 8,388,608 bytes (8.0 MB)
        assertEquals(8_388_608L, mem1024.estimatedDecodedBytes)
        assertTrue("Actual peak decode for 1024x1024 must be >= pixel buffer size (4 MB)", mem1024.actualDecodePeakBytes >= 4_194_304L)
        assertEquals(MemoryAdvice.NORMAL_MEMORY, mem1024.advice)

        // Verifies exactly 4x memory scaling between 512x512 and 1024x1024
        val ratio = mem1024.estimatedDecodedBytes.toDouble() / mem512.estimatedDecodedBytes.toDouble()
        assertEquals(4.0, ratio, 0.001)

        println("-----------------------------------------------------------------")
        println("MEMORY BASELINE MEASUREMENTS:")
        println("  512x512  Estimate: ${mem512.estimatedDecodedBytes} bytes (${mem512.estimatedDecodedBytes / 1024 / 1024} MB) | Actual Peak: ${mem512.actualDecodePeakBytes} bytes")
        println("  1024x1024 Estimate: ${mem1024.estimatedDecodedBytes} bytes (${mem1024.estimatedDecodedBytes / 1024 / 1024} MB) | Actual Peak: ${mem1024.actualDecodePeakBytes} bytes")
        println("-----------------------------------------------------------------")
    }

    // =========================================================================
    // 16. Preserved Source File Unaltered Invariant
    // =========================================================================

    @Test
    fun testPreservedSourceFileUnalteredInvariant() {
        val testFiles = listOf(
            resolveFixture("line_art/lineart_simple_01.png"),
            resolveFixture("flat_colour/flat_colour_simple_01.png"),
            resolveFixture("exif_rotated/exif_rotate_90_cw_01.jpg"),
            resolveFixture("transparent_png/transparent_cutout_01.png")
        )

        for (file in testFiles) {
            val hashBefore = computeFileSha256(file)
            val report = ReferenceInspector.inspectFile(file)
            assertTrue("Inspection must succeed for ${file.name}", report.isSuccess)
            val hashAfter = computeFileSha256(file)
            assertEquals("File ${file.name} must not be modified in place", hashBefore, hashAfter)
        }
    }
}
