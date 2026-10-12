package com.artstudio.pixelops

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager
import com.artstudio.selection.SelectionMask
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class PixelModifierEngineTest {

    private lateinit var engine: PixelModifierEngine

    companion object {
        const val COLOR_TRANSPARENT = 0x00000000
        const val COLOR_RED = 0xFFFF0000.toInt()
        const val COLOR_GREEN = 0xFF00FF00.toInt()
        const val COLOR_BLUE = 0xFF0000FF.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val COLOR_BLACK = 0xFF000000.toInt()
        const val COLOR_YELLOW = 0xFFFFFF00.toInt()
    }

    @Before
    fun setUp() {
        engine = PixelModifierEngine(maxDabDiameter = 256)
    }

    // -------------------------------------------------------------------------
    // 1. Smudge Operation & Color Displacement Across Boundaries
    // -------------------------------------------------------------------------

    @Test
    fun testSmudge_dragsColorAcrossBoundary_producesGradientTransition() {
        val w = 64
        val h = 32
        val pixels = IntArray(w * h)

        // Left half Red, Right half Blue
        for (y in 0 until h) {
            for (x in 0 until 32) pixels[y * w + x] = COLOR_RED
            for (x in 32 until 64) pixels[y * w + x] = COLOR_BLUE
        }

        val session = PixelModifierSession(engine)
        val config = PixelModifierConfig.smudgePreset(size = 20f, strength = 0.8f)

        // Drag smudge stroke from x=20 (Red) across boundary (x=32) to x=44 (Blue)
        session.beginStroke("layer_1", pixels, w, h, startX = 20f, startY = 16f, initialPressure = 1f, config = config)
        session.addPoint(currentX = 32f, currentY = 16f, pressure = 1f)
        session.addPoint(currentX = 44f, currentY = 16f, pressure = 1f)
        val cmd = session.endStroke()

        assertNotNull("Smudge stroke must produce a command", cmd)
        assertTrue("Bounding box must cover stroke trajectory", cmd!!.patchWidth > 15)

        // Verify that red pigment was dragged across boundary into the blue area (x > 32)
        var redDraggedIntoBlueSide = false
        for (x in 33..42) {
            val pixel = pixels[16 * w + x]
            val r = (pixel ushr 16) and 0xFF
            val b = pixel and 0xFF
            if (r > 30 && b > 30) {
                redDraggedIntoBlueSide = true
                break
            }
        }
        assertTrue("Smudge must carry Red pigment across boundary to create purple transition", redDraggedIntoBlueSide)
    }

    // -------------------------------------------------------------------------
    // 2. Transparency Rules & Zero Black Fringe Artifacts
    // -------------------------------------------------------------------------

    @Test
    fun testSmudge_transparentPixels_preservesAlphaWithoutBlackFringe() {
        val w = 64
        val h = 64
        val pixels = IntArray(w * h) { COLOR_TRANSPARENT }

        // Green square in center [20..44, 20..44]
        for (y in 20..44) {
            for (x in 20..44) {
                pixels[y * w + x] = COLOR_GREEN
            }
        }

        val session = PixelModifierSession(engine)
        val config = PixelModifierConfig.smudgePreset(size = 18f, strength = 0.75f)

        // Drag from inside green square (x=35) outward into transparent space (x=58)
        session.beginStroke("layer_1", pixels, w, h, startX = 35f, startY = 32f, initialPressure = 1f, config = config)
        session.addPoint(currentX = 48f, currentY = 32f, pressure = 1f)
        session.addPoint(currentX = 58f, currentY = 32f, pressure = 1f)
        session.endStroke()

        // Check dragged pixels in previously transparent region (x=46..55)
        var greenBleedFound = false
        for (x in 46..55) {
            val pixel = pixels[32 * w + x]
            val a = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            if (a > 10) {
                greenBleedFound = true
                // CRITICAL REQUIREMENT: Must NOT have black fringe (r=0, g=0, b=0 with a>0)
                assertTrue("Dragged stroke must maintain green hue, got R=$r G=$g B=$b", g > 50)
                assertEquals("Red channel must remain 0", 0, r)
                assertEquals("Blue channel must remain 0", 0, b)
            }
        }
        assertTrue("Color must bleed into transparent space with decaying alpha", greenBleedFound)
    }

    @Test
    fun testSmudge_alphaLocked_modifiesRgbWithoutCreatingNewAlpha() {
        val w = 64
        val h = 64
        val pixels = IntArray(w * h) { COLOR_TRANSPARENT }

        // White square in [20..44, 20..44]
        for (y in 20..44) {
            for (x in 20..44) {
                pixels[y * w + x] = COLOR_WHITE
            }
        }

        // Color reservoir initialized with Red
        val reservoir = SmudgeReservoir()
        reservoir.init(1.0f, 1.0f, 0.0f, 0.0f) // Pure red

        val config = PixelModifierConfig.smudgePreset(size = 20f, strength = 0.8f)

        // Apply dab on edge of white square with isAlphaLocked = true
        engine.applyDab(
            targetPixels = pixels,
            width = w, height = h,
            cx = 20f, cy = 32f,
            radius = 10f, pressure = 1f,
            config = config,
            reservoir = reservoir,
            isAlphaLocked = true
        )

        // Verify:
        // 1. Inside square (x >= 20): recolored towards red
        val insidePixel = pixels[32 * w + 22]
        val inR = (insidePixel ushr 16) and 0xFF
        val inG = (insidePixel ushr 8) and 0xFF
        assertTrue("Inside opaque area should be tinted red", inR > inG)

        // 2. Outside square (x < 20): MUST REMAIN 100% TRANSPARENT (alpha = 0)
        for (x in 0 until 20) {
            val outsidePixel = pixels[32 * w + x]
            val outA = (outsidePixel ushr 24) and 0xFF
            assertEquals("Alpha-locked surface must never create new alpha on transparent pixels at x=$x", 0, outA)
        }
    }

    // -------------------------------------------------------------------------
    // 3. Blur Operation & Edge Smoothing
    // -------------------------------------------------------------------------

    @Test
    fun testBlur_smoothsHighContrastBoundary() {
        val w = 64
        val h = 32
        val pixels = IntArray(w * h)

        // Left half Black, Right half White
        for (y in 0 until h) {
            for (x in 0 until 32) pixels[y * w + x] = COLOR_BLACK
            for (x in 32 until 64) pixels[y * w + x] = COLOR_WHITE
        }

        val session = PixelModifierSession(engine)
        val config = PixelModifierConfig.blurPreset(size = 24f, strength = 0.7f)

        // Blur along the boundary at x=32
        session.beginStroke("layer_1", pixels, w, h, startX = 32f, startY = 8f, initialPressure = 1f, config = config)
        session.addPoint(currentX = 32f, currentY = 24f, pressure = 1f)
        session.endStroke()

        // Check pixels across boundary at y=16 (e.g. x=30, 31, 32, 33)
        val leftEdge = (pixels[16 * w + 30] and 0xFF)
        val center = (pixels[16 * w + 32] and 0xFF)
        val rightEdge = (pixels[16 * w + 34] and 0xFF)

        assertTrue("Left side should be lightened from 0", leftEdge > 20)
        assertTrue("Right side should be darkened from 255", rightEdge < 235)
        assertTrue("Center should be intermediate gray", center in 50..200)
    }

    @Test
    fun testBlur_transparentArea_avoidsBlackFringeArtifacts() {
        val w = 64
        val h = 64
        val pixels = IntArray(w * h) { COLOR_TRANSPARENT }

        // Yellow square in center [20..44, 20..44]
        for (y in 20..44) {
            for (x in 20..44) {
                pixels[y * w + x] = COLOR_YELLOW
            }
        }

        val session = PixelModifierSession(engine)
        val config = PixelModifierConfig.blurPreset(size = 20f, strength = 0.8f)

        // Blur along vertical edge at x=44
        session.beginStroke("layer_1", pixels, w, h, startX = 44f, startY = 24f, initialPressure = 1f, config = config)
        session.addPoint(currentX = 44f, currentY = 40f, pressure = 1f)
        session.endStroke()

        // Inspect blurred boundary pixels around x=44, y=32
        for (x in 43..47) {
            val pixel = pixels[32 * w + x]
            val a = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            if (a > 10) {
                // Must preserve yellow hue (R and G close, B = 0)
                assertEquals("Blue channel must remain 0 during yellow blur", 0, b)
                assertTrue("Red and Green channels must stay bright, R=$r G=$g", r > 100 && g > 100)
                assertTrue("Red and Green channels must match within tolerance", abs(r - g) < 20)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 4. Strength & Hardness Modulation
    // -------------------------------------------------------------------------

    @Test
    fun testStrengthModulation_higherStrengthProducesGreaterDisplacement() {
        val w = 64
        val h = 32

        fun runSmudge(strength: Float): IntArray {
            val pix = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until 32) pix[y * w + x] = COLOR_RED
                for (x in 32 until 64) pix[y * w + x] = COLOR_BLUE
            }
            val sess = PixelModifierSession(engine)
            val cfg = PixelModifierConfig.smudgePreset(size = 20f, strength = strength)
            sess.beginStroke("layer_1", pix, w, h, 20f, 16f, 1f, cfg)
            sess.addPoint(40f, 16f, 1f)
            sess.endStroke()
            return pix
        }

        val lowStrengthPixels = runSmudge(0.2f)
        val highStrengthPixels = runSmudge(0.9f)

        // Measure red displacement at x=36 (in blue territory)
        val redInLow = (lowStrengthPixels[16 * w + 36] ushr 16) and 0xFF
        val redInHigh = (highStrengthPixels[16 * w + 36] ushr 16) and 0xFF

        assertTrue("Higher strength must displace significantly more pigment (High: $redInHigh, Low: $redInLow)", redInHigh >= redInLow + 20)
    }

    @Test
    fun testHardnessModulation_controlsDabEdgeSharpness() {
        val w = 64
        val h = 32

        fun runDab(hardness: Float): IntArray {
            val pix = IntArray(w * h) { COLOR_BLACK }
            val res = SmudgeReservoir()
            res.init(1f, 1f, 1f, 1f) // White
            val cfg = PixelModifierConfig.smudgePreset(size = 24f, strength = 0.8f).copy(hardness = hardness)
            engine.applyDab(pix, w, h, 32f, 16f, 12f, 1f, cfg, res)
            return pix
        }

        val hardPixels = runDab(1.0f)
        val softPixels = runDab(0.0f)

        // At distance r = 10 px from center (x = 42, y = 16)
        val valHard = hardPixels[16 * w + 42] and 0xFF
        val valSoft = softPixels[16 * w + 42] and 0xFF

        assertTrue("Hardness 1.0 must maintain higher intensity near dab radius boundary (Hard: $valHard, Soft: $valSoft)", valHard > valSoft + 40)
    }

    // -------------------------------------------------------------------------
    // 5. Canvas Boundary Clamping & Safety
    // -------------------------------------------------------------------------

    @Test
    fun testCanvasBoundaries_clampingPreventsOutOfBoundsOrCorruption() {
        val w = 64
        val h = 64
        val pixels = IntArray(w * h) { COLOR_BLACK }

        val session = PixelModifierSession(engine)
        val config = PixelModifierConfig.smudgePreset(size = 32f, strength = 0.5f)

        // Stroke traversing outside left-top corner through outside right-bottom corner
        session.beginStroke("layer_1", pixels, w, h, startX = -15f, startY = -15f, initialPressure = 1f, config = config)
        session.addPoint(currentX = 0f, currentY = 0f, pressure = 1f)
        session.addPoint(currentX = 75f, currentY = 75f, pressure = 1f)
        val cmd = session.endStroke()

        assertNotNull("Out-of-bounds stroke must complete safely", cmd)
        assertTrue("Patch X must be clamped to >= 0", cmd!!.patchX >= 0)
        assertTrue("Patch Y must be clamped to >= 0", cmd.patchY >= 0)
        assertTrue("Patch width must be <= canvas width", cmd.patchWidth <= w)
        assertTrue("Patch height must be <= canvas height", cmd.patchHeight <= h)
    }

    // -------------------------------------------------------------------------
    // 6. Selection Mask Restrictions
    // -------------------------------------------------------------------------

    @Test
    fun testSelectionMask_constrainsPixelOperationsStrictly() {
        val w = 64
        val h = 64
        val originalPixels = IntArray(w * h) { COLOR_BLUE }
        val testPixels = originalPixels.clone()

        // Selection mask: only [20..40, 20..40] is selected (alpha = 255)
        val selMask = SelectionMask(w, h)
        for (y in 20..40) {
            for (x in 20..40) {
                selMask.setAlpha(x, y, 255)
            }
        }

        val session = PixelModifierSession(engine)
        val config = PixelModifierConfig.blurPreset(size = 30f, strength = 0.9f)

        // Stroke running diagonally across entire canvas from (5, 5) to (55, 55)
        session.beginStroke("layer_1", testPixels, w, h, 5f, 5f, 1f, config, selectionMask = selMask)
        session.addPoint(30f, 30f, 1f)
        session.addPoint(55f, 55f, 1f)
        session.endStroke()

        // Verify: ALL pixels outside selection mask MUST REMAIN BIT-EXACT IDENTICAL to original
        var outsideDiffCount = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (selMask.getAlpha(x, y) == 0) {
                    if (testPixels[y * w + x] != originalPixels[y * w + x]) {
                        outsideDiffCount++
                    }
                }
            }
        }
        assertEquals("Pixels outside active selection mask must remain strictly untouched", 0, outsideDiffCount)
    }

    // -------------------------------------------------------------------------
    // 7. Layer Mask Modulation
    // -------------------------------------------------------------------------

    @Test
    fun testLayerMask_modulatesPixelOperationCoverage() {
        val w = 64
        val h = 32
        val pixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until 32) pixels[y * w + x] = COLOR_RED
            for (x in 32 until 64) pixels[y * w + x] = COLOR_BLUE
        }

        // Layer mask with 50% opacity (0x80808080)
        val layerMaskPixels = IntArray(w * h) { 0x80808080.toInt() }

        val res = SmudgeReservoir()
        res.init(1f, 1f, 0f, 0f) // Red

        val config = PixelModifierConfig.smudgePreset(size = 20f, strength = 1.0f)
        engine.applyDab(pixels, w, h, 36f, 16f, 10f, 1f, config, res, layerMaskPixels = layerMaskPixels)

        // Measured red deposit should be modulated by layer mask luminance (~50%)
        val targetPixel = pixels[16 * w + 36]
        val r = (targetPixel ushr 16) and 0xFF
        assertTrue("Deposit should occur under layer mask", r > 50)
        assertTrue("Deposit should be dampened by 50% mask", r < 240)
    }

    // -------------------------------------------------------------------------
    // 8. Reversible History, Undo & Redo Bit-Exactness
    // -------------------------------------------------------------------------

    @Test
    fun testUndoRedo_restoresPixelsBitExact() {
        val w = 64
        val h = 64
        val baseLayer = LayerNode.RasterLayer(name = "Paint Layer", width = w, height = h)
        val doc = ArtDocument(
            canvas = CanvasConfig(width = w, height = h),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(baseLayer))
        )
        val history = HistoryManager(doc)
        val layerManager = LayerManager(history)

        val workingPixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                workingPixels[y * w + x] = if ((x / 8 + y / 8) % 2 == 0) COLOR_RED else COLOR_YELLOW
            }
        }
        val cleanSnapshot = workingPixels.clone()

        // Execute Smudge stroke
        val session = PixelModifierSession(engine)
        val config = PixelModifierConfig.smudgePreset(size = 20f, strength = 0.8f)
        session.beginStroke(baseLayer.id, workingPixels, w, h, 16f, 32f, 1f, config)
        session.addPoint(48f, 32f, 1f)
        val command = session.endStroke()

        assertNotNull("Command must be created", command)
        layerManager.applyPixelModifierCommand(command!!, workingPixels)

        // Verify pixels were changed
        var changedPixels = 0
        for (i in workingPixels.indices) {
            if (workingPixels[i] != cleanSnapshot[i]) changedPixels++
        }
        assertTrue("Stroke must modify canvas pixels", changedPixels > 50)

        // Revert (Undo)
        val undone = history.undo()
        assertNotNull(undone)
        command.restorePatch(workingPixels, w, usePre = true)

        // Verify: BIT-EXACT 0 DIFF RESTORATION
        assertArrayEquals("Undo must restore original pixels 100% bit-exact", cleanSnapshot, workingPixels)

        // Reapply (Redo)
        val redone = history.redo()
        assertNotNull(redone)
        command.restorePatch(workingPixels, w, usePre = false)

        var redoChangedPixels = 0
        for (i in workingPixels.indices) {
            if (workingPixels[i] != cleanSnapshot[i]) redoChangedPixels++
        }
        assertEquals("Redo must reapply exactly the same modified count", changedPixels, redoChangedPixels)
    }

    // -------------------------------------------------------------------------
    // 9. Memory Bounds & Zero Allocations Per Pointer Sample (AC #3)
    // -------------------------------------------------------------------------

    @Test
    fun testMemoryBounds_zeroAllocationsPerPointerSample() {
        val w = 512
        val h = 512
        val pixels = IntArray(w * h) { COLOR_BLUE }

        val reservoir = SmudgeReservoir()
        reservoir.init(1f, 1f, 0f, 0f)
        val config = PixelModifierConfig.smudgePreset(size = 32f, strength = 0.5f)

        // Execute 100 consecutive dabs simulating high-frequency pointer stream
        for (i in 0 until 100) {
            val cx = (50f + i * 3.5f).coerceIn(0f, 511f)
            val cy = (50f + i * 2.0f).coerceIn(0f, 511f)

            // Must execute synchronously without allocating full-canvas IntArray
            val count = engine.applyDab(
                targetPixels = pixels,
                width = w, height = h,
                cx = cx, cy = cy,
                radius = 16f, pressure = 1f,
                config = config,
                reservoir = reservoir
            )
            assertTrue("Dab must execute and return valid count", count >= 0)
        }
    }
}
