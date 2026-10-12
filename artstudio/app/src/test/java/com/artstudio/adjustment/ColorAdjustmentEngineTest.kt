package com.artstudio.adjustment

import com.artstudio.document.io.DocumentConversionAuditor
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerMask
import com.artstudio.document.model.LayerNode
import com.artstudio.history.*
import com.artstudio.layer.LayerManager
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Verification test suite for Non-Destructive Tonal & Color Adjustments (Prompt 32).
 *
 * Covers:
 * 1. Boundary clamping and numerical precision across all 5 adjustment types
 * 2. Strict alpha preservation (A_dest == A_src) across transparent and translucent pixels
 * 3. Prompt 18 layer mask interaction and inversion modulation
 * 4. Non-destructive parameter persistence round-trip in native `.ffart` project schema
 * 5. Reversible history commands for adding, updating, and explicit rasterization/baking
 * 6. Audit disclosures for non-destructive adjustment flattening and unsupported color profiles
 */
class ColorAdjustmentEngineTest {

    // =========================================================================
    // 1. Brightness / Contrast
    // =========================================================================

    @Test
    fun testBrightnessContrast_boundariesAndClamping() {
        val src = intArrayOf(
            0xFF808080.toInt(), // Mid-gray (128)
            0xFF000000.toInt(), // Pure black (0)
            0xFFFFFFFF.toInt()  // Pure white (255)
        )

        // Positive brightness shift (+20) at neutral contrast (0)
        val adjBright = BrightnessContrastAdjustment(brightness = 20f, contrast = 0f)
        val outBright = ColorAdjustmentEngine.applyAdjustment(src, 3, 1, adjBright)

        // Factor = 1.0; 128 + 20 * 2.55 = 179
        val rBright = (outBright[0] ushr 16) and 0xFF
        assertEquals("Brightness +20 should map 128 to 179", 179, rBright)

        // Extreme brightness +100 should clamp to 255
        val adjMax = BrightnessContrastAdjustment(brightness = 100f, contrast = 0f)
        val outMax = ColorAdjustmentEngine.applyAdjustment(src, 3, 1, adjMax)
        val rMax = (outMax[0] ushr 16) and 0xFF
        assertEquals("Brightness +100 should clamp to 255", 255, rMax)

        // Extreme negative brightness -100 should clamp to 0
        val adjMin = BrightnessContrastAdjustment(brightness = -100f, contrast = 0f)
        val outMin = ColorAdjustmentEngine.applyAdjustment(src, 3, 1, adjMin)
        val rMin = (outMin[0] ushr 16) and 0xFF
        assertEquals("Brightness -100 should clamp to 0", 0, rMin)
    }

    @Test
    fun testBrightnessContrast_alphaPreservation() {
        val src = intArrayOf(
            0x00808080,         // Fully transparent (A = 0)
            0x40808080,         // 25% alpha (A = 64)
            0x80808080.toInt(), // 50% alpha (A = 128)
            0xFF808080.toInt()  // 100% alpha (A = 255)
        )

        val adj = BrightnessContrastAdjustment(brightness = 50f, contrast = 30f)
        val out = ColorAdjustmentEngine.applyAdjustment(src, 4, 1, adj)

        assertEquals("Fully transparent pixel alpha must remain 0", 0, (out[0] ushr 24) and 0xFF)
        assertEquals("25% alpha pixel must remain 64", 64, (out[1] ushr 24) and 0xFF)
        assertEquals("50% alpha pixel must remain 128", 128, (out[2] ushr 24) and 0xFF)
        assertEquals("100% alpha pixel must remain 255", 255, (out[3] ushr 24) and 0xFF)
    }

    // =========================================================================
    // 2. Levels
    // =========================================================================

    @Test
    fun testLevels_linearMappingAndGammaCurve() {
        val src = intArrayOf(
            0xFF323232.toInt(), // 50
            0xFF808080.toInt(), // 128
            0xFFC8C8C8.toInt()  // 200
        )

        // inBlack = 50, inWhite = 200, gamma = 1.0 (linear)
        val adj = LevelsAdjustment(
            inBlack = 50,
            inGamma = 1.0f,
            inWhite = 200,
            outBlack = 0,
            outWhite = 255
        )
        val out = ColorAdjustmentEngine.applyAdjustment(src, 3, 1, adj)

        val r0 = (out[0] ushr 16) and 0xFF
        val r2 = (out[2] ushr 16) and 0xFF
        assertEquals("inBlack cutoff (50) must map to outBlack (0)", 0, r0)
        assertEquals("inWhite cutoff (200) must map to outWhite (255)", 255, r2)
    }

    @Test
    fun testLevels_boundariesAndValidation() {
        assertThrows(IllegalArgumentException::class.java) {
            LevelsAdjustment(inBlack = 254) // max is 253
        }
        assertThrows(IllegalArgumentException::class.java) {
            LevelsAdjustment(inBlack = 100, inWhite = 101) // must be at least inBlack + 2
        }
        assertThrows(IllegalArgumentException::class.java) {
            LevelsAdjustment(inGamma = 0.05f) // min is 0.1
        }
    }

    // =========================================================================
    // 3. Hue / Saturation
    // =========================================================================

    @Test
    fun testHueSaturation_primaryColorHueRotation() {
        // Pure Red: (255, 0, 0)
        val redPixel = intArrayOf(0xFFFF0000.toInt())

        // 120 degrees hue rotation should map Pure Red to Pure Green (0, 255, 0)
        val adj120 = HueSaturationAdjustment(hueShiftDeg = 120f)
        val outGreen = ColorAdjustmentEngine.applyAdjustment(redPixel, 1, 1, adj120)
        val r1 = (outGreen[0] ushr 16) and 0xFF
        val g1 = (outGreen[0] ushr 8) and 0xFF
        val b1 = outGreen[0] and 0xFF
        assertEquals("120-deg rotation: Red channel must be 0", 0, r1)
        assertEquals("120-deg rotation: Green channel must be 255", 255, g1)
        assertEquals("120-deg rotation: Blue channel must be 0", 0, b1)

        // -120 degrees hue rotation should map Pure Red to Pure Blue (0, 0, 255)
        val adjMinus120 = HueSaturationAdjustment(hueShiftDeg = -120f)
        val outBlue = ColorAdjustmentEngine.applyAdjustment(redPixel, 1, 1, adjMinus120)
        val r2 = (outBlue[0] ushr 16) and 0xFF
        val g2 = (outBlue[0] ushr 8) and 0xFF
        val b2 = outBlue[0] and 0xFF
        assertEquals("-120-deg rotation: Red channel must be 0", 0, r2)
        assertEquals("-120-deg rotation: Green channel must be 0", 0, g2)
        assertEquals("-120-deg rotation: Blue channel must be 255", 255, b2)
    }

    @Test
    fun testHueSaturation_completeDesaturationMatchesGrayscale() {
        val colorPixel = intArrayOf(0xFFFF8000.toInt()) // Vibrant orange

        val adjDesat = HueSaturationAdjustment(saturationPercent = -100f)
        val out = ColorAdjustmentEngine.applyAdjustment(colorPixel, 1, 1, adjDesat)

        val r = (out[0] ushr 16) and 0xFF
        val g = (out[0] ushr 8) and 0xFF
        val b = out[0] and 0xFF
        assertEquals("Complete desaturation must yield equal R and G", r, g)
        assertEquals("Complete desaturation must yield equal G and B", g, b)
    }

    // =========================================================================
    // 4. Grayscale
    // =========================================================================

    @Test
    fun testGrayscale_bt709LuminanceWeights() {
        val pureGreen = intArrayOf(0xFF00FF00.toInt()) // (0, 255, 0)
        val pureRed = intArrayOf(0xFFFF0000.toInt())   // (255, 0, 0)
        val pureBlue = intArrayOf(0xFF0000FF.toInt())  // (0, 0, 255)

        val adj = GrayscaleAdjustment(redWeight = 0.2126f, greenWeight = 0.7152f, blueWeight = 0.0722f)

        val outG = ColorAdjustmentEngine.applyAdjustment(pureGreen, 1, 1, adj)
        val outR = ColorAdjustmentEngine.applyAdjustment(pureRed, 1, 1, adj)
        val outB = ColorAdjustmentEngine.applyAdjustment(pureBlue, 1, 1, adj)

        // 255 * 0.7152 = 182.376 -> 182
        assertEquals(182, (outG[0] ushr 16) and 0xFF)
        // 255 * 0.2126 = 54.213 -> 54
        assertEquals(54, (outR[0] ushr 16) and 0xFF)
        // 255 * 0.0722 = 18.411 -> 18
        assertEquals(18, (outB[0] ushr 16) and 0xFF)
    }

    @Test
    fun testGrayscale_tintApplication() {
        val grayPixel = intArrayOf(0xFF808080.toInt()) // Mid gray 128
        // Sepia tint: 0xFF704214 (R=112, G=66, B=20), intensity = 1.0 (100% tint)
        val adj = GrayscaleAdjustment(
            tintColor = 0xFF704214L,
            tintIntensity = 1.0f
        )
        val out = ColorAdjustmentEngine.applyAdjustment(grayPixel, 1, 1, adj)

        assertEquals("Full tint should produce target Sepia Red 112", 112, (out[0] ushr 16) and 0xFF)
        assertEquals("Full tint should produce target Sepia Green 66", 66, (out[0] ushr 8) and 0xFF)
        assertEquals("Full tint should produce target Sepia Blue 20", 20, out[0] and 0xFF)
    }

    // =========================================================================
    // 5. Color Balance
    // =========================================================================

    @Test
    fun testColorBalance_channelShiftsAndClipping() {
        val midGray = intArrayOf(0xFF808080.toInt()) // (128, 128, 128)

        // Cyan/Red +50 -> deltaR = 50 * 1.28 = 64; Magenta/Green = 0; Yellow/Blue = -50 -> deltaB = -64
        val adj = ColorBalanceAdjustment(
            cyanRed = 50f,
            magentaGreen = 0f,
            yellowBlue = -50f,
            preserveLuminosity = false
        )
        val out = ColorAdjustmentEngine.applyAdjustment(midGray, 1, 1, adj)

        val r = (out[0] ushr 16) and 0xFF
        val g = (out[0] ushr 8) and 0xFF
        val b = out[0] and 0xFF

        assertEquals("Red should increase by ~64 to 192", 192, r)
        assertEquals("Green should remain neutral at 128", 128, g)
        assertEquals("Blue should decrease by ~64 to 64", 64, b)
    }

    @Test
    fun testColorBalance_preserveLuminosity() {
        val pixel = intArrayOf(0xFF808080.toInt())
        val origLum = 128f

        val adj = ColorBalanceAdjustment(
            cyanRed = 40f,
            magentaGreen = -30f,
            yellowBlue = 50f,
            preserveLuminosity = true
        )
        val out = ColorAdjustmentEngine.applyAdjustment(pixel, 1, 1, adj)

        val r = (out[0] ushr 16) and 0xFF
        val g = (out[0] ushr 8) and 0xFF
        val b = out[0] and 0xFF
        val newLum = 0.2126f * r + 0.7152f * g + 0.0722f * b

        assertTrue("Preserved luminosity should stay within 2.0 of original", abs(origLum - newLum) <= 2.0f)
    }

    // =========================================================================
    // 6. Mask Interaction (Prompt 18 Integration)
    // =========================================================================

    @Test
    fun testMaskInteraction_modulatesAdjustmentByMaskLuminance() {
        val src = intArrayOf(
            0xFF646464.toInt(), // Pixel 0: 100
            0xFF646464.toInt()  // Pixel 1: 100
        )
        // Mask: Pixel 0 = 0 (black/concealed), Pixel 1 = 255 (white/revealed)
        val mask = intArrayOf(0x00000000, 0xFFFFFFFF.toInt())

        val adj = BrightnessContrastAdjustment(brightness = 50f, contrast = 0f)
        val out = ColorAdjustmentEngine.applyAdjustment(src, 2, 1, adj, maskPixels = mask)

        val r0 = (out[0] ushr 16) and 0xFF
        val r1 = (out[1] ushr 16) and 0xFF

        assertEquals("Concealed pixel (mask=0) must remain completely untouched", 100, r0)
        assertEquals("Revealed pixel (mask=255) must receive full adjustment", 228, r1)
    }

    @Test
    fun testMaskInteraction_invertedMaskReversesEffect() {
        val src = intArrayOf(
            0xFF646464.toInt(), // Pixel 0: 100
            0xFF646464.toInt()  // Pixel 1: 100
        )
        // Mask: Pixel 0 = 0 (black), Pixel 1 = 255 (white)
        val mask = intArrayOf(0x00000000, 0xFFFFFFFF.toInt())

        // With maskInverted = true: Pixel 0 is revealed (lum = 1 - 0 = 1.0), Pixel 1 is concealed (lum = 1 - 1 = 0.0)
        val adj = BrightnessContrastAdjustment(brightness = 50f, contrast = 0f)
        val out = ColorAdjustmentEngine.applyAdjustment(src, 2, 1, adj, maskPixels = mask, maskInverted = true)

        val r0 = (out[0] ushr 16) and 0xFF
        val r1 = (out[1] ushr 16) and 0xFF

        assertEquals("Inverted: Pixel 0 (mask=0) should now be fully adjusted", 228, r0)
        assertEquals("Inverted: Pixel 1 (mask=255) should now be completely untouched", 100, r1)
    }

    // =========================================================================
    // 7. Schema Round-Trip & Native Document Persistence
    // =========================================================================

    @Test
    fun testNativeDocumentPersistence_adjustmentLayerSurvivesRoundTrip() {
        val levelsAdj = LevelsAdjustment(
            inBlack = 25,
            inGamma = 1.75f,
            inWhite = 230,
            outBlack = 5,
            outWhite = 250
        )
        val adjLayer = LayerNode.AdjustmentLayer(
            id = "adj_layer_01",
            name = "Cinematic Levels",
            opacity = 0.85f,
            isClippingMask = true,
            adjustment = levelsAdj
        )

        val doc = ArtDocument(
            canvas = CanvasConfig(512, 512),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "base_raster", name = "Base Layer"),
                    adjLayer
                )
            ),
            selectedLayerId = "adj_layer_01"
        )

        val json = DocumentSerializer.toJsonString(doc)
        val restored = DocumentSerializer.fromJsonString(json)

        val restoredLayer = restored.findLayerById("adj_layer_01") as? LayerNode.AdjustmentLayer
        assertNotNull("Restored document must contain AdjustmentLayer", restoredLayer)
        assertEquals("Cinematic Levels", restoredLayer!!.name)
        assertEquals(0.85f, restoredLayer.opacity, 0.001f)
        assertTrue("isClippingMask flag must survive round-trip", restoredLayer.isClippingMask)

        val restoredAdj = restoredLayer.adjustment as? LevelsAdjustment
        assertNotNull("Adjustment must be deserialized as LevelsAdjustment", restoredAdj)
        assertEquals(25, restoredAdj!!.inBlack)
        assertEquals(1.75f, restoredAdj.inGamma, 0.001f)
        assertEquals(230, restoredAdj.inWhite)
        assertEquals(5, restoredAdj.outBlack)
        assertEquals(250, restoredAdj.outWhite)
    }

    // =========================================================================
    // 8. Reversible History Commands
    // =========================================================================

    @Test
    fun testAdjustmentCommands_addAndRevertReversibility() {
        val doc = ArtDocument(
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(LayerNode.RasterLayer(id = "layer_base", name = "Base"))
            )
        )
        val historyManager = HistoryManager(doc)
        val layerManager = LayerManager(historyManager)

        val adj = HueSaturationAdjustment(hueShiftDeg = 90f, saturationPercent = 20f)
        val adjLayer = layerManager.addAdjustmentLayer(adj, name = "Vibrant Hue")

        assertEquals(2, layerManager.getLayers().size)
        assertTrue("History must record AddAdjustmentLayerCommand", historyManager.canUndo)

        // Perform undo
        historyManager.undo()
        assertEquals("Undo must remove AdjustmentLayer", 1, layerManager.getLayers().size)
        assertNull(layerManager.currentDocument.findLayerById(adjLayer.id))

        // Perform redo
        historyManager.redo()
        assertEquals("Redo must restore AdjustmentLayer", 2, layerManager.getLayers().size)
        assertNotNull(layerManager.currentDocument.findLayerById(adjLayer.id))
    }

    @Test
    fun testAdjustmentCommands_rasterizeExplicitCommand() {
        val adj = BrightnessContrastAdjustment(brightness = 40f, contrast = 20f)
        val adjLayer = LayerNode.AdjustmentLayer(
            id = "adj_target",
            name = "Brightness 40",
            adjustment = adj
        )
        val doc = ArtDocument(
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(adjLayer)
            ),
            selectedLayerId = "adj_target"
        )
        val historyManager = HistoryManager(doc)
        val layerManager = LayerManager(historyManager)

        val bakedRaster = layerManager.rasterizeAdjustmentLayer("adj_target")
        assertNotNull("Rasterize must return a new RasterLayer", bakedRaster)
        val currentLayer = layerManager.getSelectedLayer()
        assertTrue("Current layer should now be a RasterLayer", currentLayer is LayerNode.RasterLayer)
        assertEquals("Brightness 40 (Baked)", currentLayer?.name)

        // Undo rasterization
        historyManager.undo()
        val restoredLayer = layerManager.getSelectedLayer()
        assertTrue("Undo must restore editable AdjustmentLayer", restoredLayer is LayerNode.AdjustmentLayer)
        assertEquals("adj_target", restoredLayer?.id)
    }

    // =========================================================================
    // 9. Document Conversion Auditor Disclosures
    // =========================================================================

    @Test
    fun testDocumentConversionAuditor_disclosesAdjustmentAndColorProfile() {
        val adjLayer = LayerNode.AdjustmentLayer(
            id = "adj_lossy",
            name = "Levels Layer",
            adjustment = LevelsAdjustment()
        )
        val doc = ArtDocument(
            canvas = CanvasConfig(512, 512, colorSpace = "Display-P3"),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(adjLayer)
            )
        )

        val report = DocumentConversionAuditor.auditExport(doc, "PNG")
        assertTrue("Export must report lossy conversions", report.hasLossyConversions)

        val adjWarning = report.warnings.find { it.feature == "ADJUSTMENT_LAYER" }
        assertNotNull("Auditor must disclose that adjustment layers are baked into flat raster", adjWarning)

        val profileWarning = report.warnings.find { it.feature == "COLOR_PROFILE" }
        assertNotNull("Auditor must disclose that custom color space 'Display-P3' is unsupported", profileWarning)
    }
}
