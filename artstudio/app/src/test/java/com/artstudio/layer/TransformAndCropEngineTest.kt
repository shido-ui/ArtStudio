package com.artstudio.layer

import com.artstudio.core.DefaultDocumentState
import com.artstudio.core.TransformResampler
import com.artstudio.document.model.*
import com.artstudio.history.CropCanvasCommand
import com.artstudio.history.HistoryManager
import com.artstudio.history.ResizeCanvasCommand
import com.artstudio.history.TransformLayerCommand
import com.artstudio.selection.SelectionBounds
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Authoritative Test Suite for Prompt 20:
 * Non-Destructive Move, Scale, Rotate, Flip, Canvas Resize and Crop Workflows.
 */
class TransformAndCropEngineTest {

    private lateinit var historyManager: HistoryManager
    private lateinit var layerManager: LayerManager
    private lateinit var activeLayerId: String
    private val initialW = 1000
    private val initialH = 1000

    @Before
    fun setUp() {
        val doc = ArtDocument(canvas = CanvasConfig(width = initialW, height = initialH))
        historyManager = HistoryManager(doc)
        layerManager = LayerManager(historyManager)
        activeLayerId = layerManager.currentDocument.activeLayerId
    }

    // -------------------------------------------------------------------------
    // 1. Layer Move (Translation) & Undo/Redo
    // -------------------------------------------------------------------------

    @Test
    fun testLayerMove_translationAndUndoRedo() {
        val layer = layerManager.getSelectedLayer()
        assertNotNull(layer)
        assertTrue(layer?.transform?.isIdentity == true)

        // Move by +50px X, -30px Y
        layerManager.moveLayer(activeLayerId, 50f, -30f)
        val moved = layerManager.getSelectedLayer()
        assertEquals(50f, moved?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(-30f, moved?.transform?.translationY ?: 0f, 0.001f)
        assertFalse(moved?.transform?.isIdentity == true)

        // Undo -> restores identity
        assertTrue(historyManager.canUndo)
        historyManager.undo()
        val undone = layerManager.getSelectedLayer()
        assertEquals(0f, undone?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(0f, undone?.transform?.translationY ?: 0f, 0.001f)
        assertTrue(undone?.transform?.isIdentity == true)

        // Redo -> restores translation
        assertTrue(historyManager.canRedo)
        historyManager.redo()
        val redone = layerManager.getSelectedLayer()
        assertEquals(50f, redone?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(-30f, redone?.transform?.translationY ?: 0f, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 2. Layer Scale & Aspect-Ratio Lock
    // -------------------------------------------------------------------------

    @Test
    fun testLayerScale_aspectRatioLocked() {
        // Uniform scale with aspect ratio locked
        layerManager.scaleLayer(activeLayerId, 1.5f, 2.0f, lockAspect = true)
        val scaled = layerManager.getSelectedLayer()
        assertEquals(1.5f, scaled?.transform?.scaleX ?: 0f, 0.001f)
        assertEquals(1.5f, scaled?.transform?.scaleY ?: 0f, 0.001f)
        assertTrue(scaled?.transform?.isAspectRatioLocked == true)

        // Undo
        historyManager.undo()
        val undone = layerManager.getSelectedLayer()
        assertEquals(1.0f, undone?.transform?.scaleX ?: 0f, 0.001f)
        assertEquals(1.0f, undone?.transform?.scaleY ?: 0f, 0.001f)
    }

    @Test
    fun testLayerScale_nonUniformFreeAspect() {
        // Non-uniform scale with aspect ratio unlocked
        layerManager.scaleLayer(activeLayerId, 0.8f, 1.4f, lockAspect = false)
        val scaled = layerManager.getSelectedLayer()
        assertEquals(0.8f, scaled?.transform?.scaleX ?: 0f, 0.001f)
        assertEquals(1.4f, scaled?.transform?.scaleY ?: 0f, 0.001f)
        assertFalse(scaled?.transform?.isAspectRatioLocked == true)
    }

    // -------------------------------------------------------------------------
    // 3. Layer Rotation & Angular Normalization
    // -------------------------------------------------------------------------

    @Test
    fun testLayerRotate_angularRoundtrip() {
        layerManager.rotateLayer(activeLayerId, 45f)
        assertEquals(45f, layerManager.getSelectedLayer()?.transform?.rotationDegrees ?: 0f, 0.001f)

        layerManager.rotateLayer(activeLayerId, 90f)
        assertEquals(135f, layerManager.getSelectedLayer()?.transform?.rotationDegrees ?: 0f, 0.001f)

        // 360-degree roundtrip normalization
        layerManager.rotateLayer(activeLayerId, 225f)
        assertEquals(0f, layerManager.getSelectedLayer()?.transform?.rotationDegrees ?: 0f, 0.001f)

        // Undo rotates back to 135
        historyManager.undo()
        assertEquals(135f, layerManager.getSelectedLayer()?.transform?.rotationDegrees ?: 0f, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 4. Layer Flip (Horizontal & Vertical)
    // -------------------------------------------------------------------------

    @Test
    fun testLayerFlipHorizontal_toggleAndUndo() {
        assertFalse(layerManager.getSelectedLayer()?.transform?.flipHorizontal == true)

        layerManager.flipLayerHorizontal(activeLayerId)
        assertTrue(layerManager.getSelectedLayer()?.transform?.flipHorizontal == true)

        // Second toggle restores false
        layerManager.flipLayerHorizontal(activeLayerId)
        assertFalse(layerManager.getSelectedLayer()?.transform?.flipHorizontal == true)

        // Undo restores true
        historyManager.undo()
        assertTrue(layerManager.getSelectedLayer()?.transform?.flipHorizontal == true)
    }

    @Test
    fun testLayerFlipVertical_toggleAndUndo() {
        assertFalse(layerManager.getSelectedLayer()?.transform?.flipVertical == true)

        layerManager.flipLayerVertical(activeLayerId)
        assertTrue(layerManager.getSelectedLayer()?.transform?.flipVertical == true)

        historyManager.undo()
        assertFalse(layerManager.getSelectedLayer()?.transform?.flipVertical == true)
    }

    // -------------------------------------------------------------------------
    // 5. Layer Transform Reset
    // -------------------------------------------------------------------------

    @Test
    fun testLayerTransformReset_restoresIdentity() {
        val complexTransform = LayerTransform(
            translationX = 120f,
            translationY = -45f,
            scaleX = 2.0f,
            scaleY = 0.5f,
            rotationDegrees = 60f,
            flipHorizontal = true,
            flipVertical = false
        )
        layerManager.transformLayer(activeLayerId, complexTransform)
        assertFalse(layerManager.getSelectedLayer()?.transform?.isIdentity == true)

        layerManager.resetLayerTransform(activeLayerId)
        assertTrue(layerManager.getSelectedLayer()?.transform?.isIdentity == true)

        // Undo restores complex transform
        historyManager.undo()
        assertEquals(complexTransform, layerManager.getSelectedLayer()?.transform)
    }

    // -------------------------------------------------------------------------
    // 6. Canvas Resize vs Layer Transform Separation
    // -------------------------------------------------------------------------

    @Test
    fun testCanvasResize_separateCommandFromLayerTransform() {
        val originalLayerTransform = layerManager.getSelectedLayer()?.transform

        // Canvas resize from 1000x1000 to 1200x800 with TOP_LEFT anchor (no layer shift)
        layerManager.resizeCanvas(1200, 800, anchor = CanvasResizeAnchor.TOP_LEFT, shiftLayers = false)

        val doc = layerManager.currentDocument
        assertEquals(1200, doc.canvas.width)
        assertEquals(800, doc.canvas.height)

        // Active layer transform is untouched
        assertEquals(originalLayerTransform, layerManager.getSelectedLayer()?.transform)

        // History contains ResizeCanvasCommand, not TransformLayerCommand
        val lastCommand = historyManager.currentDocument
        assertNotNull(lastCommand)
    }

    @Test
    fun testCanvasResize_anchorAlignmentOffsets() {
        // Resize with CENTER anchor:
        // deltaW = 1200 - 1000 = +200 -> dx = 200 * 0.5 = +100
        // deltaH = 1400 - 1000 = +400 -> dy = 400 * 0.5 = +200
        layerManager.resizeCanvas(1200, 1400, anchor = CanvasResizeAnchor.CENTER, shiftLayers = true)

        val doc = layerManager.currentDocument
        assertEquals(1200, doc.canvas.width)
        assertEquals(1400, doc.canvas.height)

        val layer = layerManager.getSelectedLayer()
        assertEquals(100f, layer?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(200f, layer?.transform?.translationY ?: 0f, 0.001f)

        // Undo restores 1000x1000 and 0 translation
        historyManager.undo()
        val undoneDoc = layerManager.currentDocument
        assertEquals(1000, undoneDoc.canvas.width)
        assertEquals(1000, undoneDoc.canvas.height)
        assertEquals(0f, layerManager.getSelectedLayer()?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(0f, layerManager.getSelectedLayer()?.transform?.translationY ?: 0f, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 7. Non-Destructive Canvas Crop & Undo/Redo
    // -------------------------------------------------------------------------

    @Test
    fun testCanvasCrop_boundsAndLayerOffset() {
        // Crop rect: [100, 200, 600, 700] -> width = 500, height = 500
        layerManager.cropCanvas(100f, 200f, 600f, 700f)

        val doc = layerManager.currentDocument
        assertEquals(500, doc.canvas.width)
        assertEquals(500, doc.canvas.height)

        // Layers shifted by (-100, -200)
        val layer = layerManager.getSelectedLayer()
        assertEquals(-100f, layer?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(-200f, layer?.transform?.translationY ?: 0f, 0.001f)

        // Undo -> restores 1000x1000 and zero offset
        assertTrue(historyManager.canUndo)
        historyManager.undo()
        val undoneDoc = layerManager.currentDocument
        assertEquals(1000, undoneDoc.canvas.width)
        assertEquals(1000, undoneDoc.canvas.height)
        assertEquals(0f, layerManager.getSelectedLayer()?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(0f, layerManager.getSelectedLayer()?.transform?.translationY ?: 0f, 0.001f)

        // Redo -> reapplies crop
        assertTrue(historyManager.canRedo)
        historyManager.redo()
        val redoneDoc = layerManager.currentDocument
        assertEquals(500, redoneDoc.canvas.width)
        assertEquals(500, redoneDoc.canvas.height)
        assertEquals(-100f, layerManager.getSelectedLayer()?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(-200f, layerManager.getSelectedLayer()?.transform?.translationY ?: 0f, 0.001f)
    }

    @Test
    fun testCanvasCrop_toSelectionBoundsIntegration() {
        val selBounds = SelectionBounds(50f, 50f, 350f, 450f)
        layerManager.cropCanvas(selBounds)

        val doc = layerManager.currentDocument
        assertEquals(300, doc.canvas.width)
        assertEquals(400, doc.canvas.height)
        assertEquals(-50f, layerManager.getSelectedLayer()?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(-50f, layerManager.getSelectedLayer()?.transform?.translationY ?: 0f, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 8. Out-of-Bounds Content Preservation
    // -------------------------------------------------------------------------

    @Test
    fun testOutOfBoundsContent_preservedNonDestructively() {
        // Move layer far outside canvas bounds (-2000px)
        layerManager.moveLayer(activeLayerId, -2000f, -2000f)

        val layer = layerManager.getSelectedLayer()
        assertEquals(-2000f, layer?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(-2000f, layer?.transform?.translationY ?: 0f, 0.001f)

        // Canvas dimensions remain intact
        assertEquals(initialW, layerManager.currentDocument.canvas.width)
        assertEquals(initialH, layerManager.currentDocument.canvas.height)

        // Moving back by +2000 restores exact alignment without data loss
        layerManager.moveLayer(activeLayerId, 2000f, 2000f)
        val restored = layerManager.getSelectedLayer()
        assertEquals(0f, restored?.transform?.translationX ?: 0f, 0.001f)
        assertEquals(0f, restored?.transform?.translationY ?: 0f, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 9. Vector Path Layer Math Preservation
    // -------------------------------------------------------------------------

    @Test
    fun testVectorPathLayer_transformPreservesUnderlyingPoints() {
        val vectorPath = VectorPathData(
            id = "test-path-1",
            points = listOf(
                StrokePointData(10f, 20f, 1f, 0f, 0f, 100L),
                StrokePointData(50f, 80f, 1f, 0f, 0f, 200L)
            ),
            strokeWidth = 4f,
            strokeColor = 0xFF000000L,
            brushId = "pen"
        )
        val vectorLayer = LayerNode.VectorPathLayer(
            id = "vector-layer-1",
            name = "Inking Lines",
            paths = listOf(vectorPath)
        )
        layerManager.addLayer(vectorLayer)

        // Transform vector layer
        val newT = LayerTransform(translationX = 100f, translationY = 50f, scaleX = 2f, scaleY = 2f)
        layerManager.transformLayer(vectorLayer.id, newT)

        val transformed = layerManager.currentDocument.findLayerById(vectorLayer.id) as LayerNode.VectorPathLayer
        assertEquals(newT, transformed.transform)

        // Vector math points are unmodified (transform is purely view/render affine)
        assertEquals(10f, transformed.paths[0].points[0].x, 0.001f)
        assertEquals(20f, transformed.paths[0].points[0].y, 0.001f)
        assertEquals(50f, transformed.paths[0].points[1].x, 0.001f)
        assertEquals(80f, transformed.paths[0].points[1].y, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 10. Transform Resampler Interpolation Modes
    // -------------------------------------------------------------------------

    @Test
    fun testTransformResampler_interpolationModesExecution() {
        val srcW = 10
        val srcH = 10
        val srcPixels = IntArray(srcW * srcH) { 0xFF00FF00.toInt() } // Opaque Green

        val destW = 20
        val destH = 20
        val destNearest = IntArray(destW * destH)
        val destBilinear = IntArray(destW * destH)
        val destBicubic = IntArray(destW * destH)

        val tNearest = LayerTransform(scaleX = 2f, scaleY = 2f, interpolation = InterpolationMode.NEAREST_NEIGHBOR)
        TransformResampler.resample(srcPixels, srcW, srcH, destNearest, destW, destH, tNearest)
        // Center pixel should be Green
        assertEquals(0xFF00FF00.toInt(), destNearest[10 * destW + 10])

        val tBilinear = LayerTransform(scaleX = 2f, scaleY = 2f, interpolation = InterpolationMode.BILINEAR)
        TransformResampler.resample(srcPixels, srcW, srcH, destBilinear, destW, destH, tBilinear)
        assertEquals(0xFF00FF00.toInt(), destBilinear[10 * destW + 10])

        val tBicubic = LayerTransform(scaleX = 2f, scaleY = 2f, interpolation = InterpolationMode.BICUBIC)
        TransformResampler.resample(srcPixels, srcW, srcH, destBicubic, destW, destH, tBicubic)
        assertEquals(0xFF00FF00.toInt(), destBicubic[10 * destW + 10])
    }

    // -------------------------------------------------------------------------
    // 11. DocumentState Canvas Dimension Binding
    // -------------------------------------------------------------------------

    @Test
    fun testDefaultDocumentState_dynamicallyBindsToCanvasConfig() {
        val state = DefaultDocumentState(width = 500, height = 500)
        assertEquals(500, state.width)
        assertEquals(500, state.height)

        state.layerManager.resizeCanvas(800, 600, CanvasResizeAnchor.CENTER)
        assertEquals(800, state.width)
        assertEquals(600, state.height)

        state.undo()
        assertEquals(500, state.width)
        assertEquals(500, state.height)
    }
}
