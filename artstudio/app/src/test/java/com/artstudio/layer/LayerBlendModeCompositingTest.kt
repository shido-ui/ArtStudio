package com.artstudio.layer

import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import com.artstudio.history.HistoryManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Authoritative Layer Blend Modes & Compositing Verification Suite.
 * Verifies:
 * 1. The 8 exposed starter blend modes (Normal, Multiply, Screen, Add, Darken, Lighten, Overlay, Color Dodge).
 * 2. Deterministic hand-calculated test fixtures against mathematical goldens.
 * 3. Partial opacity compositing across blend modes.
 * 4. Transparent backdrop edge compositing preserving un-premultiplied color fidelity.
 * 5. Multi-layer stacked blend mode passes.
 * 6. Explicit exclusion and labeling of unsupported blend modes (no silent aliasing).
 * 7. Undo/redo history reversibility for blend mode changes.
 * 8. Whole-buffer compositing parity against per-pixel compositing.
 */
class LayerBlendModeCompositingTest {

    private lateinit var historyManager: HistoryManager
    private lateinit var layerManager: LayerManager

    // Standard hand-calculated test fixtures
    // Backdrop B = (200, 100, 50, 255) -> 0xC86432
    private val backdropColor = (255 shl 24) or (200 shl 16) or (100 shl 8) or 50

    // Source S = (100, 150, 200, 255) -> 0x6496C8
    private val sourceColor = (255 shl 24) or (100 shl 16) or (150 shl 8) or 200

    @Before
    fun setUp() {
        val doc = ArtDocument()
        historyManager = HistoryManager(doc)
        layerManager = LayerManager(historyManager)
    }

    private fun assertRgbMatches(expectedR: Int, expectedG: Int, expectedB: Int, actualColor: Int, delta: Int = 1) {
        val actualA = (actualColor ushr 24) and 0xFF
        val actualR = (actualColor ushr 16) and 0xFF
        val actualG = (actualColor ushr 8) and 0xFF
        val actualB = actualColor and 0xFF

        assertEquals("Alpha channel mismatch", 255, actualA)
        assertTrue(
            "Red channel mismatch: expected $expectedR, got $actualR (delta=$delta)",
            abs(expectedR - actualR) <= delta
        )
        assertTrue(
            "Green channel mismatch: expected $expectedG, got $actualG (delta=$delta)",
            abs(expectedG - actualG) <= delta
        )
        assertTrue(
            "Blue channel mismatch: expected $expectedB, got $actualB (delta=$delta)",
            abs(expectedB - actualB) <= delta
        )
    }

    @Test
    fun testNormalBlendModeHandCalculated() {
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Normal, 1.0f)
        assertRgbMatches(100, 150, 200, result)
    }

    @Test
    fun testMultiplyBlendModeHandCalculated() {
        // B=(200,100,50), S=(100,150,200) -> Multiply = B * S / 255 = (78, 59, 39)
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Multiply, 1.0f)
        assertRgbMatches(78, 59, 39, result)
    }

    @Test
    fun testScreenBlendModeHandCalculated() {
        // B + S - (B * S / 255) = (222, 191, 211)
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Screen, 1.0f)
        assertRgbMatches(222, 191, 211, result)
    }

    @Test
    fun testAddLinearDodgeBlendModeHandCalculated() {
        // min(255, B + S) = (255, 250, 250)
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Add, 1.0f)
        assertRgbMatches(255, 250, 250, result)
    }

    @Test
    fun testDarkenBlendModeHandCalculated() {
        // min(B, S) = (100, 100, 50)
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Darken, 1.0f)
        assertRgbMatches(100, 100, 50, result)
    }

    @Test
    fun testLightenBlendModeHandCalculated() {
        // max(B, S) = (200, 150, 200)
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Lighten, 1.0f)
        assertRgbMatches(200, 150, 200, result)
    }

    @Test
    fun testOverlayBlendModeHandCalculated() {
        // Overlay = 2*B*S if B <= 0.5 else 1 - 2*(1-B)*(1-S) -> (188, 118, 78)
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Overlay, 1.0f)
        assertRgbMatches(188, 118, 78, result)
    }

    @Test
    fun testColorDodgeBlendModeHandCalculated() {
        // Color Dodge = min(1.0, B / (1 - S)) -> (255, 243, 232)
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.ColorDodge, 1.0f)
        assertRgbMatches(255, 243, 232, result)
    }

    @Test
    fun testPartialOpacityCompositing() {
        // Source with 50% opacity in Multiply mode over B=(200, 100, 50)
        // Expected: (139, 79, 45)
        val result = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Multiply, 0.5f)
        assertRgbMatches(139, 79, 45, result)
    }

    @Test
    fun testTransparentBackdropEdgeCompositing() {
        // Source with 50% opacity over fully transparent backdrop (0x00000000)
        val transparentBackdrop = 0x00000000
        val result = LayerBlendCompositor.blendPixel(transparentBackdrop, sourceColor, BlendModeType.Normal, 0.5f)

        val outA = (result ushr 24) and 0xFF
        val outR = (result ushr 16) and 0xFF
        val outG = (result ushr 8) and 0xFF
        val outB = result and 0xFF

        assertEquals("Alpha channel must reflect 50% opacity", 128, outA)
        assertEquals("Red channel must remain un-premultiplied source red", 100, outR)
        assertEquals("Green channel must remain un-premultiplied source green", 150, outG)
        assertEquals("Blue channel must remain un-premultiplied source blue", 200, outB)
    }

    @Test
    fun testMultipleStackedModesCompositing() {
        // Base: White (255, 255, 255)
        var canvas = (255 shl 24) or (255 shl 16) or (255 shl 8) or 255

        // Pass 1: Solid Blue (0, 0, 255) in Normal mode
        val blue = (255 shl 24) or 255
        canvas = LayerBlendCompositor.blendPixel(canvas, blue, BlendModeType.Normal, 1.0f)
        assertRgbMatches(0, 0, 255, canvas)

        // Pass 2: Solid Yellow (255, 255, 0) in Multiply mode
        // (0, 0, 1) * (1, 1, 0) = (0, 0, 0) -> Black
        val yellow = (255 shl 24) or (255 shl 16) or (255 shl 8)
        canvas = LayerBlendCompositor.blendPixel(canvas, yellow, BlendModeType.Multiply, 1.0f)
        assertRgbMatches(0, 0, 0, canvas)

        // Pass 3: Solid Red (255, 0, 0) in Screen mode
        // Black Screen Red = Red (255, 0, 0)
        val red = (255 shl 24) or (255 shl 16)
        canvas = LayerBlendCompositor.blendPixel(canvas, red, BlendModeType.Screen, 1.0f)
        assertRgbMatches(255, 0, 0, canvas)
    }

    @Test
    fun testUnsupportedModesExplicitlyMarkedAndExcludedFromSupportedList() {
        val supported = BlendModeType.supportedModes
        assertEquals("Curated supported starter set must contain exactly 8 verified modes", 8, supported.size)

        val expectedSupportedKeys = setOf(
            "normal", "multiply", "screen", "add", "darken", "lighten", "overlay", "color_dodge"
        )
        val actualSupportedKeys = supported.map { it.key }.toSet()
        assertEquals(expectedSupportedKeys, actualSupportedKeys)

        // Verify all unsupported modes are flagged isSupported = false and explicitly labeled
        val unsupported = BlendModeType.entries.filterNot { it.isSupported }
        assertTrue("Unsupported modes list must not be empty", unsupported.isNotEmpty())
        for (mode in unsupported) {
            assertFalse("${mode.name} must be marked isSupported = false", mode.isSupported)
            assertTrue(
                "${mode.name} display name must indicate unsupported status",
                mode.displayName.contains("Unsupported", ignoreCase = true)
            )
        }
    }

    @Test
    fun testSetLayerBlendModeWithHistoryUndoRedo() {
        val layer = layerManager.createLayer(name = "Blend Layer")
        assertEquals(BlendModeType.Normal, layer.blendMode)

        // Set to Multiply
        val changed = layerManager.setLayerBlendMode(layer.id, BlendModeType.Multiply)
        assertTrue("Setting blend mode must succeed", changed)
        val updatedLayer = layerManager.currentDocument.findLayerById(layer.id)
        assertEquals(BlendModeType.Multiply, updatedLayer?.blendMode)

        // Undo
        val reverted = historyManager.undo()
        assertNotNull("Undo must succeed", reverted)
        val revertedLayer = layerManager.currentDocument.findLayerById(layer.id)
        assertEquals("Undo must restore previous Normal blend mode", BlendModeType.Normal, revertedLayer?.blendMode)

        // Redo
        val reapplied = historyManager.redo()
        assertNotNull("Redo must succeed", reapplied)
        val reappliedLayer = layerManager.currentDocument.findLayerById(layer.id)
        assertEquals("Redo must reapply Multiply blend mode", BlendModeType.Multiply, reappliedLayer?.blendMode)
    }

    @Test
    fun testBufferCompositingMatchesPixelByPixel() {
        val count = 16
        val dest = IntArray(count) { backdropColor }
        val src = IntArray(count) { sourceColor }

        // Composite buffer in place
        LayerBlendCompositor.compositeBuffers(dest, src, BlendModeType.Overlay, 0.8f)

        // Verify each pixel against individual blendPixel call
        for (i in 0 until count) {
            val expected = LayerBlendCompositor.blendPixel(backdropColor, sourceColor, BlendModeType.Overlay, 0.8f)
            assertEquals("Buffer pixel at index $i must match blendPixel result", expected, dest[i])
        }
    }

    @Test
    fun testColorDodgeSingularityBoundaries() {
        // Zero backdrop yields 0
        val zeroBackdrop = (255 shl 24) // RGB 0,0,0
        val resultZero = LayerBlendCompositor.blendPixel(zeroBackdrop, sourceColor, BlendModeType.ColorDodge, 1.0f)
        assertRgbMatches(0, 0, 0, resultZero)

        // Full source 1.0 (255, 255, 255) yields 255, 255, 255
        val fullSource = (255 shl 24) or (255 shl 16) or (255 shl 8) or 255
        val resultFull = LayerBlendCompositor.blendPixel(backdropColor, fullSource, BlendModeType.ColorDodge, 1.0f)
        assertRgbMatches(255, 255, 255, resultFull)
    }
}
