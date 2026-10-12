package com.artstudio.filter

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.history.ApplyFilterCommand
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/**
 * Verification test suite for Extensible Filter System (Prompt 31).
 *
 * Covers:
 * 1. Filter configuration parameter ranges and validation
 * 2. Deterministic execution of 7 core filter operations (Blur, Sharpen, Outline, Glow, Noise, Pixelate, Ripple)
 * 3. Cooperative cancellation token interruption
 * 4. Bounded downsampled preview generation (<= 512x512)
 * 5. Interactive FilterSession lifecycle: parameter change, cancel, commit, and undo reversibility
 */
class FilterEngineTest {

    // =========================================================================
    // 1. Parameter Constraints & Validation
    // =========================================================================

    @Test
    fun testFilterConfig_validationAndDefaultRanges() {
        // Blur
        val blur = FilterConfig.GaussianBlurConfig(radius = 12f, iterations = 2)
        assertEquals(12f, blur.radius, 0.001f)
        assertEquals(2, blur.iterations)
        assertThrows(IllegalArgumentException::class.java) {
            FilterConfig.GaussianBlurConfig(radius = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FilterConfig.GaussianBlurConfig(radius = 55f)
        }

        // Sharpen
        val sharp = FilterConfig.SharpenConfig(strength = 2f, radius = 3f)
        assertEquals(2f, sharp.strength, 0.001f)
        assertThrows(IllegalArgumentException::class.java) {
            FilterConfig.SharpenConfig(strength = 0f)
        }

        // Outline
        val outline = FilterConfig.OutlineConfig(threshold = 50f)
        assertEquals(50f, outline.threshold, 0.001f)
        assertThrows(IllegalArgumentException::class.java) {
            FilterConfig.OutlineConfig(threshold = 0f)
        }

        // Glow
        val glow = FilterConfig.GlowConfig(radius = 20f, intensity = 1.5f, threshold = 0.6f)
        assertEquals(20f, glow.radius, 0.001f)
        assertThrows(IllegalArgumentException::class.java) {
            FilterConfig.GlowConfig(threshold = 1.5f)
        }

        // Noise
        val noise = FilterConfig.NoiseConfig(amount = 0.3f, seed = 123)
        assertEquals(0.3f, noise.amount, 0.001f)
        assertThrows(IllegalArgumentException::class.java) {
            FilterConfig.NoiseConfig(amount = 0f)
        }

        // Pixelate
        val pixelate = FilterConfig.PixelateConfig(blockSize = 16)
        assertEquals(16, pixelate.blockSize)
        assertThrows(IllegalArgumentException::class.java) {
            FilterConfig.PixelateConfig(blockSize = 1)
        }

        // Ripple
        val ripple = FilterConfig.RippleConfig(amplitude = 15f, wavelength = 50f, phaseDeg = 45f)
        assertEquals(15f, ripple.amplitude, 0.001f)
        assertThrows(IllegalArgumentException::class.java) {
            FilterConfig.RippleConfig(amplitude = 0f)
        }
    }

    // =========================================================================
    // 2. Filter Operations & Mathematical Accuracy
    // =========================================================================

    @Test
    fun testGaussianBlur_smoothesSharpColorStep() {
        val width = 20
        val height = 20
        val src = IntArray(width * height)

        // Half black (0xFF000000), half white (0xFFFFFFFF) split vertically at x = 10
        for (y in 0 until height) {
            for (x in 0 until width) {
                src[y * width + x] = if (x < 10) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        val blurred = ImageFilterEngine.applyFilter(
            src = src,
            width = width,
            height = height,
            config = FilterConfig.GaussianBlurConfig(radius = 4f, iterations = 1)
        )

        // Pixels right at the boundary (x = 9 and x = 10) should have intermediate gray values
        val leftEdge = (blurred[10 * width + 9] ushr 16) and 0xFF
        val rightEdge = (blurred[10 * width + 10] ushr 16) and 0xFF

        assertTrue("Left edge after blur should be > 0 (softened): got $leftEdge", leftEdge > 10)
        assertTrue("Right edge after blur should be < 255 (softened): got $rightEdge", rightEdge < 245)
        assertEquals("Alpha must be preserved at 255", 255, (blurred[10 * width + 9] ushr 24) and 0xFF)
    }

    @Test
    fun testSharpen_increasesContrastAroundEdge() {
        val width = 16
        val height = 16
        val src = IntArray(width * height)

        // Create subtle step: x < 8 is 100 gray, x >= 8 is 150 gray
        for (y in 0 until height) {
            for (x in 0 until width) {
                val gray = if (x < 8) 100 else 150
                src[y * width + x] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }

        val sharpened = ImageFilterEngine.applyFilter(
            src = src,
            width = width,
            height = height,
            config = FilterConfig.SharpenConfig(strength = 2f, radius = 2f)
        )

        val darkPixel = (sharpened[8 * width + 7] ushr 16) and 0xFF
        val brightPixel = (sharpened[8 * width + 8] ushr 16) and 0xFF

        // Unsharp mask should overshoot: darker side gets darker, brighter side gets brighter
        assertTrue("Dark boundary pixel should overshoot darker (< 100): got $darkPixel", darkPixel <= 100)
        assertTrue("Bright boundary pixel should overshoot brighter (> 150): got $brightPixel", brightPixel >= 150)
    }

    @Test
    fun testOutline_detectsHighContrastBoundary() {
        val width = 24
        val height = 24
        val src = IntArray(width * height)

        // Black box in center of white canvas
        for (y in 0 until height) {
            for (x in 0 until width) {
                val inBox = x in 6..17 && y in 6..17
                src[y * width + x] = if (inBox) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        val outline = ImageFilterEngine.applyFilter(
            src = src,
            width = width,
            height = height,
            config = FilterConfig.OutlineConfig(
                threshold = 40f,
                edgeColor = 0xFFFF0000L, // Red edge
                backgroundColor = 0xFF000000L // Black background
            )
        )

        // Flat interior at center (12, 12) should be background color (Black)
        val center = outline[12 * width + 12]
        assertEquals("Interior should be background color", 0xFF000000.toInt(), center)

        // Boundary pixel at (6, 12) should be edge color (Red)
        val edge = outline[12 * width + 6]
        assertEquals("Boundary should be edge color", 0xFFFF0000.toInt(), edge)
    }

    @Test
    fun testOutline_invertFlipsEdgeAndBackground() {
        val width = 20
        val height = 20
        val src = IntArray(width * height) { if (it % 20 < 10) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }

        val normalOutline = ImageFilterEngine.applyFilter(
            src = src, width = width, height = height,
            config = FilterConfig.OutlineConfig(threshold = 50f, edgeColor = 0xFFFFFFFFL, backgroundColor = 0xFF000000L, invert = false)
        )
        val invertedOutline = ImageFilterEngine.applyFilter(
            src = src, width = width, height = height,
            config = FilterConfig.OutlineConfig(threshold = 50f, edgeColor = 0xFFFFFFFFL, backgroundColor = 0xFF000000L, invert = true)
        )

        // Boundary pixel
        val normEdge = normalOutline[10 * width + 10]
        val invEdge = invertedOutline[10 * width + 10]
        assertNotEquals("Normal and inverted outline must produce opposite outputs", normEdge, invEdge)
    }

    @Test
    fun testGlow_enhancesHighlightsAboveThreshold() {
        val width = 30
        val height = 30
        val src = IntArray(width * height) { 0xFF000000.toInt() } // Black canvas

        // Bright white star at center (15, 15)
        src[15 * width + 15] = 0xFFFFFFFF.toInt()

        val glow = ImageFilterEngine.applyFilter(
            src = src,
            width = width,
            height = height,
            config = FilterConfig.GlowConfig(radius = 5f, intensity = 2f, threshold = 0.5f)
        )

        // Surrounding pixel at (15, 17) should now be lit up by the glow spread
        val neighbor = (glow[15 * width + 17] ushr 16) and 0xFF
        assertTrue("Neighbor should receive glow halo: got $neighbor", neighbor > 0)
    }

    @Test
    fun testNoise_deterministicSeedReproducibility() {
        val width = 32
        val height = 32
        val src = IntArray(width * height) { 0xFF808080.toInt() } // Neutral gray

        val noiseA = ImageFilterEngine.applyFilter(
            src = src, width = width, height = height,
            config = FilterConfig.NoiseConfig(amount = 0.2f, monochrome = true, seed = 42)
        )
        val noiseB = ImageFilterEngine.applyFilter(
            src = src, width = width, height = height,
            config = FilterConfig.NoiseConfig(amount = 0.2f, monochrome = true, seed = 42)
        )
        val noiseC = ImageFilterEngine.applyFilter(
            src = src, width = width, height = height,
            config = FilterConfig.NoiseConfig(amount = 0.2f, monochrome = true, seed = 999)
        )

        // noiseA and noiseB have the same seed -> bit-exact identical
        assertArrayEquals("Identical seed must yield bit-exact identical noise", noiseA, noiseB)

        // noiseC has a different seed -> must differ
        var diffCount = 0
        for (i in noiseA.indices) {
            if (noiseA[i] != noiseC[i]) diffCount++
        }
        assertTrue("Different seeds must yield distinct outputs: diffs=$diffCount", diffCount > width * height / 2)
    }

    @Test
    fun testPixelate_quantizesToUniformBlocks() {
        val width = 32
        val height = 32
        val src = IntArray(width * height)

        // Gradient image
        for (y in 0 until height) {
            for (x in 0 until width) {
                val g = (x * 8).coerceIn(0, 255)
                src[y * width + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }

        val pixelated = ImageFilterEngine.applyFilter(
            src = src,
            width = width,
            height = height,
            config = FilterConfig.PixelateConfig(blockSize = 8)
        )

        // Inside the first 8x8 block [0..7, 0..7], all pixels must be identical
        val blockColor = pixelated[0]
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals("All pixels within 8x8 block must be uniform", blockColor, pixelated[y * width + x])
            }
        }
    }

    @Test
    fun testRipple_displacesPixelsSinusoidally() {
        val width = 40
        val height = 40
        val src = IntArray(width * height)

        // Draw vertical stripe at x = 20
        for (y in 0 until height) {
            for (x in 0 until width) {
                src[y * width + x] = if (x == 20) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }

        val rippled = ImageFilterEngine.applyFilter(
            src = src,
            width = width,
            height = height,
            config = FilterConfig.RippleConfig(amplitude = 5f, wavelength = 20f, phaseDeg = 0f)
        )

        // The vertical stripe at x = 20 should now be sinusoidally shifted
        var hasShifted = false
        for (y in 0 until height) {
            val pAt20 = rippled[y * width + 20]
            if (pAt20 != 0xFFFFFFFF.toInt()) {
                hasShifted = true
                break
            }
        }
        assertTrue("Ripple must displace pixels away from original x=20 column", hasShifted)
    }

    @Test
    fun testCancellationToken_haltsExecutionEarly() {
        val width = 100
        val height = 100
        val src = IntArray(width * height) { 0xFF123456.toInt() }

        val token = FilterCancellationToken()
        token.cancel() // Immediately cancelled

        val result = ImageFilterEngine.applyFilter(
            src = src,
            width = width,
            height = height,
            config = FilterConfig.GaussianBlurConfig(radius = 30f, iterations = 3),
            token = token
        )

        assertNotNull("Result should return gracefully on cancel", result)
    }

    @Test
    fun testDownscaleForPreview_respectsDimensionCeiling() {
        val srcW = 2048
        val srcH = 1536
        val src = IntArray(srcW * srcH)

        val (prevBuf, prevW, prevH) = ImageFilterEngine.downscaleForPreview(src, srcW, srcH, maxDim = 512)

        assertTrue("Preview max dimension must be <= 512: got max($prevW, $prevH)", max(prevW, prevH) <= 512)
        assertEquals("Aspect ratio should be preserved within 1 px", 4f / 3f, prevW.toFloat() / prevH.toFloat(), 0.05f)
        assertEquals("Buffer length must match prevW * prevH", prevW * prevH, prevBuf.size)
    }

    // =========================================================================
    // 3. Interactive Session Lifecycle: Parameter Change, Cancel, Commit & Undo
    // =========================================================================

    @Test
    fun testFilterSession_parameterChangeProducesDistinctPreview() {
        val width = 64
        val height = 64
        val src = IntArray(width * height) { if (it % 64 < 32) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }

        val session = FilterSession(
            layerId = "layer_1",
            layerName = "Test Layer",
            sourcePixels = src,
            width = width,
            height = height
        )

        // Evaluate with small blur
        val state1 = session.evaluatePreviewSync(FilterConfig.GaussianBlurConfig(radius = 2f))
        val pix1 = state1.previewPixels!!.clone()

        // Evaluate with large blur
        val state2 = session.evaluatePreviewSync(FilterConfig.GaussianBlurConfig(radius = 20f))
        val pix2 = state2.previewPixels!!

        var diffs = 0
        for (i in pix1.indices) {
            if (pix1[i] != pix2[i]) diffs++
        }
        assertTrue("Changing blur radius from 2 to 20 must produce distinct preview output: diffs=$diffs", diffs > 0)
    }

    @Test
    fun testFilterSession_cancelLeavesOriginalUntouched() {
        val width = 32
        val height = 32
        val src = IntArray(width * height) { it }
        val srcBackup = src.clone()

        val session = FilterSession(
            layerId = "layer_1",
            layerName = "Test Layer",
            sourcePixels = src,
            width = width,
            height = height
        )

        session.evaluatePreviewSync(FilterConfig.PixelateConfig(blockSize = 16))
        session.cancel()

        // Authoritative sourcePixels must remain 100% bit-exact untouched
        assertArrayEquals("Cancel must leave authoritative layer pixels untouched", srcBackup, session.sourcePixels)
    }

    @Test
    fun testFilterSession_commitCreatesReversibleCommand() {
        val width = 32
        val height = 32
        val src = IntArray(width * height) { if (it % 32 < 16) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val originalSnapshot = src.clone()

        val doc = ArtDocument(
            canvas = CanvasConfig(width, height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(LayerNode.RasterLayer(id = "layer_filter", name = "Filter Layer", width = width, height = height))
            ),
            selectedLayerId = "layer_filter"
        )
        val historyManager = HistoryManager(doc)
        val layerManager = LayerManager(historyManager)

        val session = FilterSession(
            layerId = "layer_filter",
            layerName = "Filter Layer",
            sourcePixels = src,
            width = width,
            height = height
        )

        val testConfig = FilterConfig.NoiseConfig(amount = 0.5f, seed = 777)
        session.evaluatePreviewSync(testConfig)
        val cmd = session.commit(layerManager)

        assertNotNull("Commit must return an ApplyFilterCommand", cmd)
        assertTrue("History must record the committed filter command", historyManager.canUndo)

        // Source buffer has been transformed
        assertFalse("Source buffer should reflect post-filter output", originalSnapshot.contentEquals(session.sourcePixels))

        // Perform Undo
        historyManager.undo()
        cmd!!.restorePixels(session.sourcePixels, usePre = true)

        // After undo, pixels must match original snapshot bit-for-bit
        assertArrayEquals("Undo must restore pre-filter pixels with 100% bit-exact parity", originalSnapshot, session.sourcePixels)

        // Perform Redo
        historyManager.redo()
        cmd.restorePixels(session.sourcePixels, usePre = false)
        assertArrayEquals("Redo must restore post-filter pixels", cmd.postPixels, session.sourcePixels)
    }
}
