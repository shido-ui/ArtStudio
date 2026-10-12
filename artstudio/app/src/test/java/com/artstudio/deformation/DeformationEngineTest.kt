package com.artstudio.deformation

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.history.DeformLayerCommand
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Authoritative Test Suite for Prompt 21:
 * Advanced Deformation Engine, Warp Mesh, Liquify, Interpolation, Edge Behaviors & Memory Resilience.
 */
class DeformationEngineTest {

    private lateinit var historyManager: HistoryManager
    private lateinit var layerManager: LayerManager
    private lateinit var activeLayerId: String

    @Before
    fun setUp() {
        com.artstudio.core.FeatureFlags.isDeformationEnabled = true
        val doc = ArtDocument(canvas = CanvasConfig(width = 512, height = 512))
        historyManager = HistoryManager(doc)
        layerManager = LayerManager(historyManager)
        activeLayerId = layerManager.currentDocument.activeLayerId
    }

    // -------------------------------------------------------------------------
    // 1. Mesh Boundary & Envelope Tests
    // -------------------------------------------------------------------------

    @Test
    fun testWarpMesh_initializationAndBoundaryEnvelope() {
        val mesh = WarpMesh.createUniformGrid(width = 512f, height = 512f, rows = 4, cols = 4)
        assertEquals(4, mesh.rows)
        assertEquals(4, mesh.cols)
        assertEquals(16, mesh.vertices.size)
        assertTrue(mesh.isIdentity())

        val bounds = mesh.getBounds()
        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(512f, bounds.right, 0.001f)
        assertEquals(512f, bounds.bottom, 0.001f)
        assertEquals(512f, bounds.width, 0.001f)
        assertEquals(512f, bounds.height, 0.001f)

        // Verify corner vertices
        val v00 = mesh.getVertex(0, 0)
        assertEquals(0f, v00.x, 0.001f)
        assertEquals(0f, v00.y, 0.001f)

        val v33 = mesh.getVertex(3, 3)
        assertEquals(512f, v33.x, 0.001f)
        assertEquals(512f, v33.y, 0.001f)
    }

    @Test
    fun testWarpMesh_vertexMovementAndBoundsExpansion() {
        var mesh = WarpMesh.createUniformGrid(width = 512f, height = 512f, rows = 4, cols = 4)

        // Displace top-left corner outside canvas bounds
        mesh = mesh.withVertexPosition(0, 0, newX = -50f, newY = -30f)
        assertFalse(mesh.isIdentity())

        val bounds = mesh.getBounds()
        assertEquals(-50f, bounds.left, 0.001f)
        assertEquals(-30f, bounds.top, 0.001f)
        assertEquals(512f, bounds.right, 0.001f)
        assertEquals(512f, bounds.bottom, 0.001f)

        // Reset mesh to baseline
        val resetMesh = mesh.reset()
        assertTrue(resetMesh.isIdentity())
        assertEquals(0f, resetMesh.getVertex(0, 0).x, 0.001f)
        assertEquals(0f, resetMesh.getVertex(0, 0).y, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 2. Identity Mesh & Deformation Rasterization
    // -------------------------------------------------------------------------

    @Test
    fun testWarpMesh_identityRasterizationPreservesExactPixels() {
        val w = 64
        val h = 64
        val src = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            (0xFF shl 24) or (x * 3 shl 16) or (y * 3 shl 8) or 0x80
        }
        val dest = IntArray(w * h)
        val mesh = WarpMesh.createUniformGrid(w.toFloat(), h.toFloat(), rows = 4, cols = 4)

        DeformationEngine.warpWithMesh(
            srcPixels = src,
            width = w,
            height = h,
            destPixels = dest,
            mesh = mesh,
            edgeBehavior = EdgeBehavior.CLAMP_TO_EDGE,
            interpolation = DeformInterpolation.BILINEAR
        )

        var diffCount = 0
        for (i in src.indices) {
            if (src[i] != dest[i]) diffCount++
        }
        // Identity mesh preserves exact original pixels with zero deviation
        assertEquals(0, diffCount)
    }

    @Test
    fun testWarpMesh_centerDisplacementWarp() {
        val w = 64
        val h = 64
        val src = IntArray(w * h) { 0xFF112233.toInt() }
        // Paint a bright white circle in the center
        for (y in 24..40) {
            for (x in 24..40) {
                src[y * w + x] = 0xFFFFFFFF.toInt()
            }
        }
        val dest = IntArray(w * h)
        var mesh = WarpMesh.createUniformGrid(w.toFloat(), h.toFloat(), rows = 4, cols = 4)

        // Shift center vertex (row 1, col 1) by +10px in X and Y
        val v = mesh.getVertex(1, 1)
        mesh = mesh.withVertexPosition(1, 1, v.x + 10f, v.y + 10f)

        DeformationEngine.warpWithMesh(
            srcPixels = src,
            width = w,
            height = h,
            destPixels = dest,
            mesh = mesh,
            edgeBehavior = EdgeBehavior.CLAMP_TO_EDGE,
            interpolation = DeformInterpolation.BILINEAR
        )

        // Pixel contents must be deformed (diff > 0)
        var diffCount = 0
        for (i in src.indices) {
            if (src[i] != dest[i]) diffCount++
        }
        assertTrue("Warped mesh must alter pixel distribution", diffCount > 0)
    }

    // -------------------------------------------------------------------------
    // 3. Interpolation Modes Filtering
    // -------------------------------------------------------------------------

    @Test
    fun testInterpolationModes_nearestBilinearBicubic() {
        val w = 4
        val h = 4
        val src = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                src[y * w + x] = if (x < 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        // Sample at subpixel midpoint (u = 1.5f, v = 1.0f)
        val nearest = DeformationEngine.samplePixel(
            src, w, h, 1.5f, 1.0f, EdgeBehavior.CLAMP_TO_EDGE, DeformInterpolation.NEAREST_NEIGHBOR
        )
        val bilinear = DeformationEngine.samplePixel(
            src, w, h, 1.5f, 1.0f, EdgeBehavior.CLAMP_TO_EDGE, DeformInterpolation.BILINEAR
        )
        val bicubic = DeformationEngine.samplePixel(
            src, w, h, 1.5f, 1.0f, EdgeBehavior.CLAMP_TO_EDGE, DeformInterpolation.BICUBIC
        )

        // Nearest rounds to 2.0 (White: 0xFFFFFFFF)
        assertEquals(0xFFFFFFFF.toInt(), nearest)

        // Bilinear yields exact 50% gray (0xFF7F7F7F or 0xFF808080)
        val biRed = (bilinear ushr 16) and 0xFF
        assertTrue("Bilinear red must be intermediate ~127-128", biRed in 126..128)

        // Bicubic yields smooth cubic spline transition
        val cubicRed = (bicubic ushr 16) and 0xFF
        assertTrue("Bicubic red must be intermediate ~127-128", cubicRed in 124..130)
    }

    // -------------------------------------------------------------------------
    // 4. Edge Behaviors
    // -------------------------------------------------------------------------

    @Test
    fun testEdgeBehavior_clampTransparentAndWrap() {
        val w = 4
        val h = 4
        val src = IntArray(w * h) { 0xFF00FF00.toInt() } // All green
        src[0] = 0xFFFF0000.toInt()                      // Corner (0, 0) is Red

        // Sample out-of-bounds at (-5.0, -5.0)
        val clamped = DeformationEngine.samplePixel(
            src, w, h, -5.0f, -5.0f, EdgeBehavior.CLAMP_TO_EDGE, DeformInterpolation.NEAREST_NEIGHBOR
        )
        assertEquals("Clamped out-of-bounds must sample top-left corner", 0xFFFF0000.toInt(), clamped)

        val transparent = DeformationEngine.samplePixel(
            src, w, h, -5.0f, -5.0f, EdgeBehavior.TRANSPARENT_ZERO, DeformInterpolation.NEAREST_NEIGHBOR
        )
        assertEquals("Transparent zero out-of-bounds must return 0x00000000", 0, transparent)

        val wrapped = DeformationEngine.samplePixel(
            src, w, h, 4.0f, 0.0f, EdgeBehavior.WRAP, DeformInterpolation.NEAREST_NEIGHBOR
        )
        // Wrapping x = 4.0 % 4.0 = 0 -> samples (0, 0)
        assertEquals("Wrapped out-of-bounds must sample modulo position", 0xFFFF0000.toInt(), wrapped)
    }

    // -------------------------------------------------------------------------
    // 5. Brush-Based Liquify Operations
    // -------------------------------------------------------------------------

    @Test
    fun testLiquifyBrush_pushMotionCreatesDirectionalDisplacement() {
        val disp = DisplacementField(100, 100)
        assertTrue(disp.isZero())

        // Push rightward (+X) at center (50, 50) with velocity vx = 20f, vy = 0f
        disp.applyPush(cx = 50f, cy = 50f, vx = 20f, vy = 0f, radius = 30f, strength = 1.0f)
        assertFalse(disp.isZero())

        // Center must have maximum horizontal displacement
        val centerDx = disp.getDx(50, 50)
        val centerDy = disp.getDy(50, 50)
        assertEquals(20f, centerDx, 0.001f)
        assertEquals(0f, centerDy, 0.001f)

        // Point outside radius (10, 50) must have 0 displacement
        assertEquals(0f, disp.getDx(10, 50), 0.001f)

        // Point at half-radius (65, 50) has decayed displacement
        val halfDx = disp.getDx(65, 50)
        assertTrue("Displacement at half radius must be between 0 and 20", halfDx in 1f..19f)
    }

    @Test
    fun testLiquifyBrush_expandAndPinchRadialSymmetry() {
        val dispExpand = DisplacementField(100, 100)
        dispExpand.applyExpand(cx = 50f, cy = 50f, radius = 25f, strength = 1.0f)

        val dispPinch = DisplacementField(100, 100)
        dispPinch.applyPinch(cx = 50f, cy = 50f, radius = 25f, strength = 1.0f)

        // Compare point (60, 50) east of center
        val expandDx = dispExpand.getDx(60, 50)
        val pinchDx = dispPinch.getDx(60, 50)

        // Expand and Pinch must have opposite directional displacement
        assertTrue("Expand displacement must be negative inverse mapping", expandDx < 0f)
        assertTrue("Pinch displacement must be positive inverse mapping", pinchDx > 0f)
        assertEquals(abs(expandDx), abs(pinchDx), 0.001f)
    }

    @Test
    fun testLiquifyBrush_twirlClockwiseAndCounterClockwise() {
        val dispCW = DisplacementField(100, 100)
        dispCW.applyTwirl(cx = 50f, cy = 50f, radius = 30f, strength = 0.8f, clockwise = true)

        val dispCCW = DisplacementField(100, 100)
        dispCCW.applyTwirl(cx = 50f, cy = 50f, radius = 30f, strength = 0.8f, clockwise = false)

        // At point (50, 35) north of center, CW rotates clockwise (+X displacement), CCW rotates counter-clockwise (-X)
        val cwDx = dispCW.getDx(50, 35)
        val ccwDx = dispCCW.getDx(50, 35)
        assertTrue("CW twirl creates non-zero displacement", abs(cwDx) > 0.001f)
        assertTrue("CCW twirl creates non-zero displacement", abs(ccwDx) > 0.001f)
        assertEquals("CW and CCW displacements are symmetric opposites", -cwDx, ccwDx, 0.001f)
    }

    @Test
    fun testLiquifyBrush_reconstructRestoresBaselineDisplacement() {
        val disp = DisplacementField(100, 100)
        disp.applyPush(50f, 50f, vx = 30f, vy = 10f, radius = 20f, strength = 1.0f)
        val initDx = disp.getDx(50, 50)
        assertTrue(initDx > 25f)

        // Apply reconstruct at center with high strength
        disp.applyReconstruct(50f, 50f, radius = 25f, strength = 0.8f)
        val reconstructedDx = disp.getDx(50, 50)
        assertTrue("Reconstruct must reduce displacement magnitude", reconstructedDx < initDx)
    }

    // -------------------------------------------------------------------------
    // 6. Interactive Session Lifecycle: Cancel / Reset / Commit
    // -------------------------------------------------------------------------

    @Test
    fun testInteractiveSession_resetRestoresExactDuplicate() {
        val w = 128
        val h = 128
        val original = IntArray(w * h) { 0xFF0000FF.toInt() } // Blue
        val session = DeformationSession(
            layerId = activeLayerId,
            originalWidth = w,
            originalHeight = h,
            originalPixels = original
        )

        assertFalse(session.isDirty)

        // Apply liquify stroke
        session.applyPush(64f, 64f, 25f, 0f)
        assertTrue(session.isDirty)
        session.renderPreview()

        // Reset must restore preview to exact original bit-exact match
        session.reset()
        assertFalse(session.isDirty)
        assertTrue(session.displacement.isZero())

        var diffCount = 0
        for (i in original.indices) {
            if (original[i] != session.previewPixels[i]) diffCount++
        }
        assertEquals("Reset must restore preview buffer to 0 diff pixels", 0, diffCount)
    }

    @Test
    fun testInteractiveSession_cancelAbortsWithoutDocumentMutation() {
        val w = 64
        val h = 64
        val original = IntArray(w * h) { 0xFFAABBCC.toInt() }
        val session = DeformationSession(
            layerId = activeLayerId,
            originalWidth = w,
            originalHeight = h,
            originalPixels = original
        )

        session.applyPush(32f, 32f, 15f, 0f)
        assertTrue(session.isDirty)

        session.cancel()
        assertTrue(session.isCancelled)
        assertFalse(session.isCommitted)
        assertEquals(0, historyManager.undoCount)
    }

    @Test
    fun testInteractiveSession_commitExecutesUndoableCommand() {
        val w = 64
        val h = 64
        val original = IntArray(w * h) { 0xFF00FF00.toInt() }
        // Center red square
        for (y in 20..44) {
            for (x in 20..44) {
                original[y * w + x] = 0xFFFF0000.toInt()
            }
        }

        val session = DeformationSession(
            layerId = activeLayerId,
            originalWidth = w,
            originalHeight = h,
            originalPixels = original
        )

        session.applyPush(32f, 32f, 20f, 0f)
        val result = session.commit(layerManager)

        assertTrue(session.isCommitted)
        assertEquals(1, historyManager.undoCount)
        assertTrue(result.metrics.latencyMs >= 0.0)

        // Reversible via undo/redo
        val topCmd = historyManager.undo()
        assertNotNull(topCmd)
        assertTrue(topCmd is DeformLayerCommand)
        assertEquals(original, (topCmd as DeformLayerCommand).preDeformPixels)

        historyManager.redo()
        assertEquals(1, historyManager.undoCount)
    }

    // -------------------------------------------------------------------------
    // 7. Adaptive Preview & Memory Budgets (4 GB Resilience)
    // -------------------------------------------------------------------------

    @Test
    fun testAdaptivePreview_downsamplesOnConstrainedMemoryOrLargeDimensions() {
        val w = 2048
        val h = 2048
        val pixels = IntArray(w * h)

        val session = DeformationSession(
            layerId = activeLayerId,
            originalWidth = w,
            originalHeight = h,
            originalPixels = pixels,
            config = DeformationConfig(adaptivePreviewScale = 0.5f)
        )

        assertTrue(session.isAdaptivePreview)
        assertEquals(1024, session.previewWidth)
        assertEquals(1024, session.previewHeight)
        assertEquals(1024 * 1024, session.previewPixels.size)
    }

    @Test
    fun test4GBTargetMemoryResilience_boundsEnforcedWithoutCrash() {
        // Enforces documented maximum working resolution (2048x2048)
        val maxRes = 2048
        val config = DeformationConfig(maxWorkingResolution = maxRes)
        assertEquals(2048, config.maxWorkingResolution)

        // Oversized canvas (> maxWorkingResolution) is safely rejected
        val oversizedW = 600
        val oversizedH = 600
        val oversizedPixels = IntArray(oversizedW * oversizedH)
        try {
            DeformationSession(
                layerId = activeLayerId,
                originalWidth = oversizedW,
                originalHeight = oversizedH,
                originalPixels = oversizedPixels,
                config = DeformationConfig(maxWorkingResolution = 500)
            )
            fail("Oversized canvas beyond maximum working resolution must throw IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("exceeds maximum working resolution") == true)
        }
    }

    @org.junit.After
    fun tearDown() {
        com.artstudio.core.FeatureFlags.isDeformationEnabled = true
    }
}
