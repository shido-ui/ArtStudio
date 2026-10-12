package com.artstudio.cv.palette

import com.artstudio.color.ColorData
import com.artstudio.cv.region.RegionMask
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SourcePaletteAnalysisTest {

    private fun createTestRegion(
        id: String,
        width: Int,
        height: Int,
        rectX: Int,
        rectY: Int,
        rectW: Int,
        rectH: Int
    ): RegionMask {
        val mask = ByteArray(width * height)
        for (y in rectY until (rectY + rectH).coerceAtMost(height)) {
            for (x in rectX until (rectX + rectW).coerceAtMost(width)) {
                mask[y * width + x] = 255.toByte()
            }
        }
        return RegionMask(
            regionId = id,
            width = width,
            height = height,
            mask = mask,
            confidence = 0.95f
        )
    }

    // -------------------------------------------------------------------------
    // 1. Transparent Pixels Excluded from Palette
    // -------------------------------------------------------------------------
    @Test
    fun testTransparentPixelsExcludedFromPalette() {
        val w = 20
        val h = 20
        val pixels = IntArray(w * h)
        // Left half transparent (alpha = 0), right half solid Blue (0xFF0000FF)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (x < 10) 0x00000000 else 0xFF0000FF.toInt()
            }
        }

        val analysis = SourcePaletteExtractor.analyze(pixels, w, h)
        assertEquals(200, analysis.transparentPixelCount)
        assertEquals(1, analysis.globalPalette.size)
        // Swatch must be Blue, NOT polluted with black or transparent values
        val swatch = analysis.globalPalette[0]
        assertEquals(0f, swatch.color.red, 0.01f)
        assertEquals(0f, swatch.color.green, 0.01f)
        assertEquals(1f, swatch.color.blue, 0.01f)
        assertEquals(1.0f, swatch.frequency, 0.01f) // 100% of opaque pixels
    }

    // -------------------------------------------------------------------------
    // 2. Near-Duplicate Colors Clustered
    // -------------------------------------------------------------------------
    @Test
    fun testNearDuplicateColorsClustered() {
        val w = 20
        val h = 10
        val pixels = IntArray(w * h)
        // 100 pixels of #00FF00 (pure green), 100 pixels of #03FD02 (near identical green)
        val green1 = 0xFF00FF00.toInt()
        val green2 = 0xFF03FD02.toInt()
        for (i in 0 until w * h) {
            pixels[i] = if (i < 100) green1 else green2
        }

        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            mergeDistanceThreshold = 0.08f
        )

        // Must merge near duplicates into 1 single green swatch
        assertEquals(1, analysis.globalPalette.size)
        val swatch = analysis.globalPalette[0]
        assertEquals(200, swatch.pixelCount)
        assertTrue(swatch.color.green > 0.98f)
    }

    // -------------------------------------------------------------------------
    // 3. Region-Local Observed Color Extraction
    // -------------------------------------------------------------------------
    @Test
    fun testRegionLocalObservedColorExtraction() {
        val w = 40
        val h = 40
        val pixels = IntArray(w * h) { 0xFFFFFFFF.toInt() } // White background
        // Paint red square at (10, 10) to (25, 25)
        for (y in 10 until 25) {
            for (x in 10 until 25) {
                pixels[y * w + x] = 0xFFFF0000.toInt()
            }
        }

        val redRegion = createTestRegion("reg_red", w, h, 10, 10, 15, 15)
        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            regions = listOf(redRegion)
        )

        val assignment = analysis.regionAssignments["reg_red"]
        assertNotNull(assignment)
        assertEquals(1f, assignment!!.observedColor.red, 0.01f)
        assertEquals(0f, assignment.observedColor.green, 0.01f)
        assertEquals(0f, assignment.observedColor.blue, 0.01f)
        assertTrue("Flat color region must have near-zero variance", assignment.colorVariance < 0.01f)
    }

    // -------------------------------------------------------------------------
    // 4. Luminance and Shadow Relationship Inference
    // -------------------------------------------------------------------------
    @Test
    fun testLuminanceAndShadowRelationshipInference() {
        val w = 50
        val h = 30
        val pixels = IntArray(w * h)
        // Reg 1: Base Light Blue (#3388EE, x: 0..24)
        // Reg 2: Adjacent Dark Blue (#113366, x: 25..49)
        val baseBlue = 0xFF3388EE.toInt()
        val shadowBlue = 0xFF113366.toInt()

        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (x < 25) baseBlue else shadowBlue
            }
        }

        val rBase = createTestRegion("reg_base", w, h, 0, 0, 25, h)
        val rShadow = createTestRegion("reg_shadow", w, h, 25, 0, 25, h)

        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            regions = listOf(rBase, rShadow)
        )

        assertTrue("Adjacency must be detected between adjacent regions", analysis.adjacencies.isNotEmpty())
        val adj = analysis.adjacencies.first()
        assertTrue("Pair must be classified as likely shadow pair", adj.isLikelyShadowPair)

        val assignShadow = analysis.regionAssignments["reg_shadow"]
        val assignBase = analysis.regionAssignments["reg_base"]
        assertNotNull(assignShadow)
        assertNotNull(assignBase)
        assertEquals(InferredColorRole.SHADOW, assignShadow!!.inferredRole)
        assertEquals(InferredColorRole.BASE_COLOR, assignBase!!.inferredRole)
        assertTrue(assignShadow.observedLuminance < assignBase.observedLuminance)
    }

    // -------------------------------------------------------------------------
    // 5. Observed Color Separated from Inferred Role
    // -------------------------------------------------------------------------
    @Test
    fun testObservedColorSeparatedFromInferredRole() {
        val w = 20
        val h = 20
        val pixels = IntArray(w * h) { 0xFFFFCC80.toInt() } // Warm amber
        val reg = createTestRegion("reg_character_skin", w, h, 0, 0, w, h)

        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            regions = listOf(reg)
        )

        val assignment = analysis.regionAssignments["reg_character_skin"]!!
        // Raw observed color is preserved as exact RGB values
        assertEquals(0xFFFFCC80.toInt(), assignment.observedColor.argbInt)
        // Inferred role is visual/topological (BASE_COLOR), NOT an untested semantic "SKIN" label
        assertEquals(InferredColorRole.BASE_COLOR, assignment.inferredRole)
    }

    // -------------------------------------------------------------------------
    // 6. Palette Adaptation Mode: PRESERVE_SOURCE
    // -------------------------------------------------------------------------
    @Test
    fun testPaletteAdaptationModePreserveSource() {
        val w = 20
        val h = 20
        val pixels = IntArray(w * h) { 0xFF123456.toInt() }
        val reg = createTestRegion("reg_exact", w, h, 0, 0, w, h)

        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            regions = listOf(reg),
            adaptationMode = PaletteAdaptationMode.PRESERVE_SOURCE
        )

        val assignment = analysis.regionAssignments["reg_exact"]!!
        assertEquals(assignment.observedColor, assignment.assignedColor)
    }

    // -------------------------------------------------------------------------
    // 7. Palette Adaptation Mode: GENTLE_ADAPTATION
    // -------------------------------------------------------------------------
    @Test
    fun testPaletteAdaptationModeGentleAdaptation() {
        val w = 20
        val h = 20
        val pixels = IntArray(w * h) { 0xFF00FF00.toInt() }
        // One pixel with slight noise #02FE01 inside region
        pixels[0] = 0xFF02FE01.toInt()

        val reg = createTestRegion("reg_noisy", w, h, 0, 0, w, h)
        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            regions = listOf(reg),
            adaptationMode = PaletteAdaptationMode.GENTLE_ADAPTATION
        )

        val assignment = analysis.regionAssignments["reg_noisy"]!!
        val swatch = analysis.globalPalette.find { it.id == assignment.mappedSwatchId }!!
        // Gentle adaptation snaps to the clean global swatch
        assertEquals(swatch.color, assignment.assignedColor)
    }

    // -------------------------------------------------------------------------
    // 8. Palette Adaptation Mode: ARTISTIC_REINTERPRETATION
    // -------------------------------------------------------------------------
    @Test
    fun testPaletteAdaptationModeArtisticReinterpretation() {
        val baseCol = ColorData(0.2f, 0.5f, 0.8f)
        val shadowCol = ColorData(0.1f, 0.25f, 0.4f)

        val monoBase = SourcePaletteExtractor.applyArtisticScheme(
            color = baseCol,
            role = InferredColorRole.BASE_COLOR,
            scheme = ArtisticScheme.MONOCHROME_INK
        )
        val monoShadow = SourcePaletteExtractor.applyArtisticScheme(
            color = shadowCol,
            role = InferredColorRole.SHADOW,
            scheme = ArtisticScheme.MONOCHROME_INK
        )

        // Monochromatic grayscale preserves shadow hierarchy
        assertTrue(monoShadow.perceivedLuminance < monoBase.perceivedLuminance)
        assertEquals(monoBase.red, monoBase.green, 0.01f)
        assertEquals(monoBase.green, monoBase.blue, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 9. User Region Color Override and Reassignment
    // -------------------------------------------------------------------------
    @Test
    fun testUserRegionColorOverrideAndReassignment() {
        val w = 20
        val h = 20
        val pixels = IntArray(w * h) { 0xFF0000FF.toInt() } // Blue
        val reg = createTestRegion("reg_custom", w, h, 0, 0, w, h)

        val customYellow = ColorData.YELLOW
        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            regions = listOf(reg),
            userOverrides = mapOf("reg_custom" to customYellow)
        )

        val assignment = analysis.regionAssignments["reg_custom"]!!
        assertTrue(assignment.isUserOverridden)
        assertEquals(customYellow, assignment.effectiveColor)
        // Original observed color remains blue
        assertEquals(1f, assignment.observedColor.blue, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 10. Palette Extraction Error RMSE Measurement
    // -------------------------------------------------------------------------
    @Test
    fun testPaletteExtractionErrorRmseMeasurement() {
        val w = 50
        val h = 50
        val pixels = IntArray(w * h)
        // 50% Red (#FF0000), 50% Green (#00FF00)
        for (i in pixels.indices) {
            pixels[i] = if (i < (w * h) / 2) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()
        }

        val r1 = createTestRegion("r1", w, h, 0, 0, w, h / 2)
        val r2 = createTestRegion("r2", w, h, 0, h / 2, w, h / 2)

        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            regions = listOf(r1, r2)
        )

        // Perfect flat colors should have RMSE very close to 0.0
        assertTrue("RMSE error must be tiny for clean flat colors", analysis.paletteExtractionErrorRmse < 0.05f)
    }

    // -------------------------------------------------------------------------
    // 11. Line Art and Flat Colour Fixture Handling
    // -------------------------------------------------------------------------
    @Test
    fun testLineArtAndFlatColourFixturesExtraction() {
        val w = 50
        val h = 50
        val pixels = IntArray(w * h) { 0xFFFFFFFF.toInt() } // White background
        // Draw black ink line
        for (x in 0 until w) {
            pixels[25 * w + x] = 0xFF000000.toInt()
        }

        val analysis = SourcePaletteExtractor.analyze(pixels, w, h)
        assertEquals(2, analysis.globalPalette.size)
        val outlineSwatch = analysis.globalPalette.find { it.inferredRole == InferredColorRole.OUTLINE }
        val bgSwatch = analysis.globalPalette.find { it.inferredRole == InferredColorRole.BACKGROUND }

        assertNotNull("Line art must have OUTLINE swatch", outlineSwatch)
        assertNotNull("Line art must have BACKGROUND swatch", bgSwatch)
        assertEquals(0f, outlineSwatch!!.luminance, 0.01f)
        assertEquals(1f, bgSwatch!!.luminance, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 12. Source Palette Persistence Round-Trip and Style Availability
    // -------------------------------------------------------------------------
    @Test
    fun testSourcePalettePersistenceRoundTripAndStyleAvailability() {
        val w = 30
        val h = 30
        val pixels = IntArray(w * h) { 0xFFFF5722.toInt() } // Deep Orange
        val reg = createTestRegion("reg_style_test", w, h, 0, 0, w, h)

        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            regions = listOf(reg),
            adaptationMode = PaletteAdaptationMode.GENTLE_ADAPTATION
        )

        val json = SourcePaletteSerializer.toJson(analysis)
        assertTrue(json.contains("reg_style_test"))
        assertTrue(json.contains("GENTLE_ADAPTATION"))

        val restored = SourcePaletteSerializer.fromJson(json)
        assertEquals(analysis.id, restored.id)
        assertEquals(analysis.adaptationMode, restored.adaptationMode)
        assertEquals(analysis.globalPalette.size, restored.globalPalette.size)
        assertEquals(analysis.regionAssignments.size, restored.regionAssignments.size)

        // Availability to every Colour Lab style
        val stylePalette = restored.toColorStylePalette()
        assertTrue(stylePalette.containsKey("reg_style_test"))
        val color = stylePalette["reg_style_test"]!!
        assertTrue(color.red > 0.95f)
    }
}
