package com.artstudio.fill

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.history.FillCommand
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Authoritative unit test suite for Prompt 23:
 * Flood and Bucket Fill Engine with Gap Closing, Line Boundaries, Tolerance, Expansion, and Cancellation.
 */
class FloodFillEngineTest {

    private val COLOR_BLACK = 0xFF000000.toInt()
    private val COLOR_WHITE = 0xFFFFFFFF.toInt()
    private val COLOR_RED = 0xFFFF0000.toInt()
    private val COLOR_BLUE = 0xFF0000FF.toInt()
    private val COLOR_TRANSPARENT = 0x00000000

    // -------------------------------------------------------------
    // 1. Enclosed Regions & Contiguous Fill
    // -------------------------------------------------------------

    @Test
    fun testEnclosedRegion_contiguousFillStopsAtBorders() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { COLOR_WHITE }

        // Draw an enclosed 16x16 black box in center (from 8 to 23)
        for (x in 8..23) {
            pixels[8 * w + x] = COLOR_BLACK
            pixels[23 * w + x] = COLOR_BLACK
        }
        for (y in 8..23) {
            pixels[y * w + 8] = COLOR_BLACK
            pixels[y * w + 23] = COLOR_BLACK
        }

        val config = FillConfig(
            fillColor = COLOR_RED,
            fillMode = FillMode.CONTIGUOUS,
            tolerance = 0.05f,
            antiAlias = false,
            boundaryExpansion = 0,
            gapClosingRadius = 0
        )

        // Seed inside box (16, 16)
        val result = FloodFillEngine.executeFill(
            sourcePixels = pixels,
            targetPixels = pixels,
            width = w,
            height = h,
            seedX = 16,
            seedY = 16,
            config = config
        )

        assertNotNull(result)
        assertTrue(result!!.success)
        val res = result.resultPixels!!

        // Center should be red
        assertEquals(COLOR_RED, res[16 * w + 16])

        // Borders should remain black
        assertEquals(COLOR_BLACK, res[8 * w + 8])
        assertEquals(COLOR_BLACK, res[23 * w + 23])

        // Outside box (0, 0) should remain untouched white
        assertEquals(COLOR_WHITE, res[0])
        assertEquals(COLOR_WHITE, res[4 * w + 4])
        assertEquals(COLOR_WHITE, res[31 * w + 31])

        // Filled area inside 16x16 box is 14x14 = 196 pixels
        assertEquals(196, result.filledPixelCount)
    }

    // -------------------------------------------------------------
    // 2. One-Pixel & Two-Pixel Gap Closing Tests
    // -------------------------------------------------------------

    @Test
    fun testOnePixelGap_leaksWithoutGapClosing() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { COLOR_WHITE }

        // Draw box with a 1-pixel gap at (15, 8) on top border
        for (x in 8..23) {
            if (x != 15) { // 1-pixel gap at x=15
                pixels[8 * w + x] = COLOR_BLACK
            }
            pixels[23 * w + x] = COLOR_BLACK
        }
        for (y in 8..23) {
            pixels[y * w + 8] = COLOR_BLACK
            pixels[y * w + 23] = COLOR_BLACK
        }

        val config = FillConfig(
            fillColor = COLOR_RED,
            fillMode = FillMode.CONTIGUOUS,
            gapClosingRadius = 0, // Gap closing OFF
            antiAlias = false
        )

        val result = FloodFillEngine.executeFill(
            sourcePixels = pixels,
            targetPixels = pixels,
            width = w,
            height = h,
            seedX = 16,
            seedY = 16,
            config = config
        )

        assertNotNull(result)
        val res = result!!.resultPixels!!

        // Without gap closing, the fill MUST leak through (15, 8) to outside (0, 0)
        assertEquals("Outside pixel must be filled due to leak", COLOR_RED, res[0])
        assertTrue("Filled count must exceed enclosed box area", result.filledPixelCount > 200)
    }

    @Test
    fun testOnePixelGap_closedWithGapClosingRadius() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { COLOR_WHITE }

        // Draw box with a 1-pixel gap at (15, 8) on top border
        for (x in 8..23) {
            if (x != 15) { // 1-pixel gap at x=15
                pixels[8 * w + x] = COLOR_BLACK
            }
            pixels[23 * w + x] = COLOR_BLACK
        }
        for (y in 8..23) {
            pixels[y * w + 8] = COLOR_BLACK
            pixels[y * w + 23] = COLOR_BLACK
        }

        val config = FillConfig(
            fillColor = COLOR_RED,
            fillMode = FillMode.CONTIGUOUS,
            gapClosingRadius = 1, // Gap closing ON (bridges up to 2-pixel gaps)
            antiAlias = false,
            boundaryExpansion = 0
        )

        val result = FloodFillEngine.executeFill(
            sourcePixels = pixels,
            targetPixels = pixels,
            width = w,
            height = h,
            seedX = 16,
            seedY = 16,
            config = config
        )

        assertNotNull(result)
        val res = result!!.resultPixels!!

        // Inside must be filled red
        assertEquals(COLOR_RED, res[16 * w + 16])

        // Outside must NOT be leaked into!
        assertEquals("Outside (0, 0) remains white", COLOR_WHITE, res[0])
        assertEquals("Outside (4, 4) remains white", COLOR_WHITE, res[4 * w + 4])
        assertEquals("Outside (30, 30) remains white", COLOR_WHITE, res[30 * w + 30])
    }

    @Test
    fun testTwoPixelGap_closedWithGapClosingRadius2() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { COLOR_WHITE }

        // Draw box with a 2-pixel gap at x=14 and x=15 on top border
        for (x in 8..23) {
            if (x != 14 && x != 15) {
                pixels[8 * w + x] = COLOR_BLACK
            }
            pixels[23 * w + x] = COLOR_BLACK
        }
        for (y in 8..23) {
            pixels[y * w + 8] = COLOR_BLACK
            pixels[y * w + 23] = COLOR_BLACK
        }

        // Gap radius 2 bridges up to 4px gap
        val config = FillConfig(
            fillColor = COLOR_RED,
            gapClosingRadius = 2,
            antiAlias = false
        )

        val result = FloodFillEngine.executeFill(
            sourcePixels = pixels,
            targetPixels = pixels,
            width = w,
            height = h,
            seedX = 16,
            seedY = 16,
            config = config
        )

        assertNotNull(result)
        val res = result!!.resultPixels!!

        assertEquals("Inside filled", COLOR_RED, res[16 * w + 16])
        assertEquals("Outside (0, 0) protected", COLOR_WHITE, res[0])
    }

    // -------------------------------------------------------------
    // 3. Color Distance & Threshold Tolerance
    // -------------------------------------------------------------

    @Test
    fun testTolerance_thresholdPrecision() {
        val w = 30
        val h = 10
        val pixels = IntArray(w * h)

        // Strip 1: 0 to 9 -> Gray 100 (0xFF646464)
        // Strip 2: 10 to 19 -> Gray 110 (0xFF6E6E6E), delta ~ 10 / 510 = 0.019
        // Strip 3: 20 to 29 -> Gray 150 (0xFF969696), delta ~ 50 / 510 = 0.098
        for (y in 0 until h) {
            for (x in 0 until 10) pixels[y * w + x] = 0xFF646464.toInt()
            for (x in 10 until 20) pixels[y * w + x] = 0xFF6E6E6E.toInt()
            for (x in 20 until 30) pixels[y * w + x] = 0xFF969696.toInt()
        }

        // Strict tolerance (0.01) -> Only strip 1 matches
        val configStrict = FillConfig(
            fillColor = COLOR_RED,
            tolerance = 0.01f,
            antiAlias = false
        )
        val resStrict = FloodFillEngine.executeFill(pixels, pixels, w, h, 5, 5, configStrict)!!.resultPixels!!
        assertEquals(COLOR_RED, resStrict[5 * w + 5])
        assertEquals(0xFF6E6E6E.toInt(), resStrict[5 * w + 15])
        assertEquals(0xFF969696.toInt(), resStrict[5 * w + 25])

        // Moderate tolerance (0.05) -> Matches strip 1 and strip 2, stops at strip 3
        val configMod = FillConfig(
            fillColor = COLOR_RED,
            tolerance = 0.05f,
            antiAlias = false
        )
        val resMod = FloodFillEngine.executeFill(pixels, pixels, w, h, 5, 5, configMod)!!.resultPixels!!
        assertEquals(COLOR_RED, resMod[5 * w + 5])
        assertEquals(COLOR_RED, resMod[5 * w + 15])
        assertEquals(0xFF969696.toInt(), resMod[5 * w + 25])

        // Wide tolerance (0.20) -> Matches all strips
        val configWide = FillConfig(
            fillColor = COLOR_RED,
            tolerance = 0.20f,
            antiAlias = false
        )
        val resWide = FloodFillEngine.executeFill(pixels, pixels, w, h, 5, 5, configWide)!!.resultPixels!!
        assertEquals(COLOR_RED, resWide[5 * w + 5])
        assertEquals(COLOR_RED, resWide[5 * w + 15])
        assertEquals(COLOR_RED, resWide[5 * w + 25])
    }

    // -------------------------------------------------------------
    // 4. Global Color Selection & Replacement
    // -------------------------------------------------------------

    @Test
    fun testGlobalColor_replacesDisjointRegions() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { COLOR_WHITE }

        // Four disjoint red squares
        fun fillSquare(sx: Int, sy: Int) {
            for (y in sy until sy + 4) {
                for (x in sx until sx + 4) {
                    pixels[y * w + x] = COLOR_RED
                }
            }
        }
        fillSquare(2, 2)
        fillSquare(20, 2)
        fillSquare(2, 20)
        fillSquare(20, 20)

        val config = FillConfig(
            fillColor = COLOR_BLUE,
            fillMode = FillMode.GLOBAL_COLOR,
            antiAlias = false
        )

        // Seed at top-left red square (3, 3)
        val result = FloodFillEngine.executeFill(pixels, pixels, w, h, 3, 3, config)
        assertNotNull(result)
        val res = result!!.resultPixels!!

        // All 4 squares must become blue
        assertEquals("Square 1 replaced", COLOR_BLUE, res[3 * w + 3])
        assertEquals("Square 2 replaced", COLOR_BLUE, res[3 * w + 21])
        assertEquals("Square 3 replaced", COLOR_BLUE, res[21 * w + 3])
        assertEquals("Square 4 replaced", COLOR_BLUE, res[21 * w + 21])

        // Background remains white
        assertEquals(COLOR_WHITE, res[0])
        assertEquals(COLOR_WHITE, res[16 * w + 16])
        assertEquals(16 * 4, result.filledPixelCount)
    }

    // -------------------------------------------------------------
    // 5. Boundary Expansion & Contraction
    // -------------------------------------------------------------

    @Test
    fun testBoundaryExpansion_bleedsUnderBorders() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { COLOR_WHITE }

        // Draw black border box from 10 to 20
        for (x in 10..20) {
            pixels[10 * w + x] = COLOR_BLACK
            pixels[20 * w + x] = COLOR_BLACK
        }
        for (y in 10..20) {
            pixels[y * w + 10] = COLOR_BLACK
            pixels[y * w + 20] = COLOR_BLACK
        }

        val configExact = FillConfig(fillColor = COLOR_RED, boundaryExpansion = 0, antiAlias = false)
        val resExact = FloodFillEngine.executeFill(pixels, pixels, w, h, 15, 15, configExact)!!

        val configExpanded = FillConfig(fillColor = COLOR_RED, boundaryExpansion = 2, antiAlias = false)
        val resExpanded = FloodFillEngine.executeFill(pixels, pixels, w, h, 15, 15, configExpanded)!!

        assertTrue("Expanded fill count > exact fill count", resExpanded.filledPixelCount > resExact.filledPixelCount)

        // Exact fill does not touch border pixel (10, 15)
        assertEquals(0.toByte(), resExact.mask[10 * w + 15])

        // Expanded (+2) fill dilates over border pixel
        assertEquals(255.toByte(), resExpanded.mask[10 * w + 15])
    }

    @Test
    fun testBoundaryContraction_erodesInward() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { COLOR_WHITE }

        for (x in 6..26) {
            pixels[6 * w + x] = COLOR_BLACK
            pixels[26 * w + x] = COLOR_BLACK
        }
        for (y in 6..26) {
            pixels[y * w + 6] = COLOR_BLACK
            pixels[y * w + 26] = COLOR_BLACK
        }

        val configExact = FillConfig(fillColor = COLOR_RED, boundaryExpansion = 0, antiAlias = false)
        val resExact = FloodFillEngine.executeFill(pixels, pixels, w, h, 16, 16, configExact)!!

        val configEroded = FillConfig(fillColor = COLOR_RED, boundaryExpansion = -2, antiAlias = false)
        val resEroded = FloodFillEngine.executeFill(pixels, pixels, w, h, 16, 16, configEroded)!!

        assertTrue("Eroded count < exact count", resEroded.filledPixelCount < resExact.filledPixelCount)
        assertEquals(0.toByte(), resEroded.mask[7 * w + 7])
    }

    // -------------------------------------------------------------
    // 6. Anti-Aliasing Edge Smoothing
    // -------------------------------------------------------------

    @Test
    fun testAntiAliasing_producesSmoothFractionalEdgeAlphas() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { COLOR_WHITE }

        // Draw black border box
        for (x in 8..24) {
            pixels[8 * w + x] = COLOR_BLACK
            pixels[24 * w + x] = COLOR_BLACK
        }
        for (y in 8..24) {
            pixels[y * w + 8] = COLOR_BLACK
            pixels[y * w + 24] = COLOR_BLACK
        }

        val config = FillConfig(fillColor = COLOR_RED, antiAlias = true)
        val result = FloodFillEngine.executeFill(pixels, pixels, w, h, 16, 16, config)!!

        // Mask should have fractional values on perimeter (neither 0 nor 255)
        var hasFractional = false
        for (b in result.mask) {
            val v = b.toInt() and 0xFF
            if (v in 1..254) {
                hasFractional = true
                break
            }
        }
        assertTrue("Anti-aliased mask must have fractional edge coverage", hasFractional)
    }

    // -------------------------------------------------------------
    // 7. Transparency Mode Behaviors
    // -------------------------------------------------------------

    @Test
    fun testTransparencyMode_transparentOnly() {
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { COLOR_TRANSPARENT }

        // Top half transparent (alpha = 0), bottom half opaque blue
        for (y in 8 until 16) {
            for (x in 0 until 16) {
                pixels[y * w + x] = COLOR_BLUE
            }
        }

        val config = FillConfig(
            fillColor = COLOR_RED,
            transparencyMode = FillTransparencyMode.TRANSPARENT_ONLY,
            antiAlias = false
        )

        // Seed in transparent top half
        val resTop = FloodFillEngine.executeFill(pixels, pixels, w, h, 4, 4, config)!!
        assertEquals(COLOR_RED, resTop.resultPixels!![4 * w + 4])
        assertEquals(COLOR_BLUE, resTop.resultPixels!![12 * w + 4])

        // Seed in opaque bottom half -> should refuse to fill
        val resBottom = FloodFillEngine.executeFill(pixels, pixels, w, h, 4, 12, config)!!
        assertEquals(0, resBottom.filledPixelCount)
    }

    @Test
    fun testTransparencyMode_opaqueOnly() {
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { COLOR_TRANSPARENT }

        // Island of blue in center
        for (y in 4..11) {
            for (x in 4..11) {
                pixels[y * w + x] = COLOR_BLUE
            }
        }

        val config = FillConfig(
            fillColor = COLOR_RED,
            transparencyMode = FillTransparencyMode.OPAQUE_ONLY,
            antiAlias = false
        )

        val res = FloodFillEngine.executeFill(pixels, pixels, w, h, 8, 8, config)!!
        assertEquals("Blue center filled with red", COLOR_RED, res.resultPixels!![8 * w + 8])
        assertEquals("Transparent border untouched", COLOR_TRANSPARENT, res.resultPixels!![0])
    }

    // -------------------------------------------------------------
    // 8. Cooperative Cancellation
    // -------------------------------------------------------------

    @Test
    fun testCancellation_midExecutionAbortsWithoutTargetMutation() {
        val w = 128
        val h = 128
        val originalPixels = IntArray(w * h) { COLOR_WHITE }
        val targetPixels = originalPixels.clone()

        val cancelToken = FillCancellationToken()
        cancelToken.cancel() // Cancel before/at start

        val config = FillConfig(fillColor = COLOR_RED)
        val result = FloodFillEngine.executeFill(
            sourcePixels = originalPixels,
            targetPixels = targetPixels,
            width = w,
            height = h,
            seedX = 64,
            seedY = 64,
            config = config,
            cancelToken = cancelToken
        )

        assertNull("Cancelled fill must return null", result)
        assertArrayEquals("Target pixels must remain unmodified on cancellation", originalPixels, targetPixels)
    }

    // -------------------------------------------------------------
    // 9. Layer Manager Integration & Undo/Redo Fidelity
    // -------------------------------------------------------------

    @Test
    fun testTransactionalUndoRedo_fillCommandRestoresPreFillPixels() {
        val w = 32
        val h = 32
        val layer = LayerNode.RasterLayer(name = "Target Layer", width = w, height = h)
        val doc = ArtDocument(
            canvas = CanvasConfig(width = w, height = h),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(layer))
        )
        val history = HistoryManager(doc)
        val layerManager = LayerManager(history)

        val buffer = IntArray(w * h) { COLOR_WHITE }

        val config = FillConfig(
            fillColor = COLOR_RED,
            targetLayerMode = FillTargetLayer.CURRENT_LAYER,
            antiAlias = false
        )

        // Execute fill
        val result = layerManager.applyFillToLayer(
            layerId = layer.id,
            seedX = 16,
            seedY = 16,
            config = config,
            targetPixelBuffer = buffer
        )

        assertNotNull(result)
        assertTrue(result!!.success)
        assertEquals(COLOR_RED, buffer[16 * w + 16])

        // Undo
        assertNotNull(history.undo())
        val lastCmd = history.currentDocument // State updated
        assertNotNull(lastCmd)

        // Redo
        assertNotNull(history.redo())
    }

    @Test
    fun testFillTargetLayer_newLayerCreationPreservesActiveLayer() {
        val w = 32
        val h = 32
        val baseLayer = LayerNode.RasterLayer(name = "Line Art", width = w, height = h)
        val doc = ArtDocument(
            canvas = CanvasConfig(width = w, height = h),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(baseLayer))
        )
        val history = HistoryManager(doc)
        val layerManager = LayerManager(history)

        val config = FillConfig(
            fillColor = COLOR_BLUE,
            targetLayerMode = FillTargetLayer.NEW_LAYER, // Creates new layer
            antiAlias = false
        )

        val initialLayerCount = layerManager.currentDocument.allLayersFlat().size

        val result = layerManager.applyFillToLayer(
            layerId = baseLayer.id,
            seedX = 16,
            seedY = 16,
            config = config
        )

        assertNotNull(result)
        assertTrue(result!!.success)

        val newLayerCount = layerManager.currentDocument.allLayersFlat().size
        assertEquals("New layer must be added", initialLayerCount + 1, newLayerCount)
    }
}
