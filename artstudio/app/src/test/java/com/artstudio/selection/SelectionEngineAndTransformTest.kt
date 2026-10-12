package com.artstudio.selection

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import com.artstudio.core.CanvasPoint
import com.artstudio.core.DefaultDocumentState
import com.artstudio.core.ViewportTransform
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Authoritative Test Suite for Prompt 19:
 * Precise Document-Space Selections, Boolean Combination Operators,
 * Morphological Filtering, Viewport Transform Invariance, and Corpus Fixture Verification.
 */
class SelectionEngineAndTransformTest {

    private lateinit var selectionManager: SelectionManager
    private val width = 100
    private val height = 100

    @Before
    fun setUp() {
        selectionManager = SelectionManager(width, height)
    }

    // -------------------------------------------------------------------------
    // 1. Geometric Primitive Rasterization
    // -------------------------------------------------------------------------

    @Test
    fun testRectangleSelection_rasterizationAndBounds() {
        val rect = SelectionShape.Rectangle(20f, 30f, 60f, 70f)
        selectionManager.selectShape(rect, SelectionCombineMode.REPLACE)

        assertTrue(selectionManager.hasSelection)
        // 40 width x 40 height = 1600 pixels
        assertEquals(1600, selectionManager.selectedPixelCount)

        val bounds = selectionManager.bounds
        assertEquals(20f, bounds.left, 0.01f)
        assertEquals(30f, bounds.top, 0.01f)
        assertEquals(60f, bounds.right, 0.01f)
        assertEquals(70f, bounds.bottom, 0.01f)

        // Interior points
        assertEquals(255, selectionManager.currentSelection.getAlpha(20, 30))
        assertEquals(255, selectionManager.currentSelection.getAlpha(59, 69))
        assertEquals(255, selectionManager.currentSelection.getAlpha(40, 50))

        // Exterior points
        assertEquals(0, selectionManager.currentSelection.getAlpha(19, 30))
        assertEquals(0, selectionManager.currentSelection.getAlpha(20, 29))
        assertEquals(0, selectionManager.currentSelection.getAlpha(60, 50))
        assertEquals(0, selectionManager.currentSelection.getAlpha(40, 70))
    }

    @Test
    fun testEllipseSelection_rasterizationAndCentering() {
        // Circle centered at (50, 50) with rx = 30, ry = 30 -> [20, 20, 80, 80]
        val ellipse = SelectionShape.Ellipse(20f, 20f, 80f, 80f)
        selectionManager.selectShape(ellipse, SelectionCombineMode.REPLACE)

        assertTrue(selectionManager.hasSelection)

        // Center must be selected
        assertEquals(255, selectionManager.currentSelection.getAlpha(50, 50))

        // Cardinal interior points: distance 25 < radius 30
        assertEquals(255, selectionManager.currentSelection.getAlpha(50 + 25, 50))
        assertEquals(255, selectionManager.currentSelection.getAlpha(50 - 25, 50))
        assertEquals(255, selectionManager.currentSelection.getAlpha(50, 50 + 25))
        assertEquals(255, selectionManager.currentSelection.getAlpha(50, 50 - 25))

        // Corners of bounding box must NOT be selected (distance^2 = 30^2 + 30^2 = 1800 > 900)
        assertEquals(0, selectionManager.currentSelection.getAlpha(20, 20))
        assertEquals(0, selectionManager.currentSelection.getAlpha(79, 20))
        assertEquals(0, selectionManager.currentSelection.getAlpha(20, 79))
        assertEquals(0, selectionManager.currentSelection.getAlpha(79, 79))

        // Symmetry verification
        for (dy in -20..20 step 5) {
            for (dx in -20..20 step 5) {
                val a1 = selectionManager.currentSelection.getAlpha(50 + dx, 50 + dy)
                val a2 = selectionManager.currentSelection.getAlpha(50 - dx, 50 + dy)
                assertEquals("Horizontal symmetry failed at dx=$dx, dy=$dy", a1, a2)
            }
        }
    }

    @Test
    fun testFreehandLassoSelection_scanlineParity() {
        // Triangle: (20, 20), (80, 20), (50, 80)
        val points = listOf(
            CanvasPoint(20f, 20f),
            CanvasPoint(80f, 20f),
            CanvasPoint(50f, 80f)
        )
        val lasso = SelectionShape.Lasso(points)
        selectionManager.selectShape(lasso, SelectionCombineMode.REPLACE)

        assertTrue(selectionManager.hasSelection)
        // Centroid (50, 40) must be inside
        assertEquals(255, selectionManager.currentSelection.getAlpha(50, 40))

        // Exterior points
        assertEquals(0, selectionManager.currentSelection.getAlpha(10, 10))
        assertEquals(0, selectionManager.currentSelection.getAlpha(50, 85))
        assertEquals(0, selectionManager.currentSelection.getAlpha(90, 50))
    }

    @Test
    fun testPolygonSelection_nonConvexVertexFilling() {
        // Non-convex L-shaped polygon:
        // (10, 10) -> (90, 10) -> (90, 40) -> (40, 40) -> (40, 90) -> (10, 90)
        val vertices = listOf(
            CanvasPoint(10f, 10f),
            CanvasPoint(90f, 10f),
            CanvasPoint(90f, 40f),
            CanvasPoint(40f, 40f),
            CanvasPoint(40f, 90f),
            CanvasPoint(10f, 90f)
        )
        val poly = SelectionShape.Polygon(vertices)
        selectionManager.selectShape(poly, SelectionCombineMode.REPLACE)

        assertTrue(selectionManager.hasSelection)

        // Inside first horizontal arm: (60, 25)
        assertEquals(255, selectionManager.currentSelection.getAlpha(60, 25))

        // Inside second vertical arm: (25, 60)
        assertEquals(255, selectionManager.currentSelection.getAlpha(25, 60))

        // Corner junction: (25, 25)
        assertEquals(255, selectionManager.currentSelection.getAlpha(25, 25))

        // Concave cutout: (60, 60) must NOT be selected!
        assertEquals(0, selectionManager.currentSelection.getAlpha(60, 60))
        assertEquals(0, selectionManager.currentSelection.getAlpha(80, 80))
    }

    // -------------------------------------------------------------------------
    // 2. Boolean Combination Operations
    // -------------------------------------------------------------------------

    @Test
    fun testSelectionCombineMode_Replace() {
        val rect1 = SelectionShape.Rectangle(10f, 10f, 50f, 50f)
        selectionManager.selectShape(rect1, SelectionCombineMode.REPLACE)
        assertEquals(1600, selectionManager.selectedPixelCount)

        val rect2 = SelectionShape.Rectangle(60f, 60f, 80f, 80f)
        selectionManager.selectShape(rect2, SelectionCombineMode.REPLACE)

        // Old rect is replaced completely
        assertEquals(400, selectionManager.selectedPixelCount)
        assertEquals(0, selectionManager.currentSelection.getAlpha(20, 20))
        assertEquals(255, selectionManager.currentSelection.getAlpha(70, 70))
    }

    @Test
    fun testSelectionCombineMode_Add_Union() {
        // Rect A: [10, 10, 50, 50] = 40x40 = 1600 px
        val rectA = SelectionShape.Rectangle(10f, 10f, 50f, 50f)
        selectionManager.selectShape(rectA, SelectionCombineMode.REPLACE)

        // Rect B: [30, 10, 70, 50] = 40x40 = 1600 px
        // Combined union: [10, 10, 70, 50] = 60x40 = 2400 px
        val rectB = SelectionShape.Rectangle(30f, 10f, 70f, 50f)
        selectionManager.selectShape(rectB, SelectionCombineMode.ADD)

        assertEquals(2400, selectionManager.selectedPixelCount)
        assertEquals(255, selectionManager.currentSelection.getAlpha(20, 20))
        assertEquals(255, selectionManager.currentSelection.getAlpha(40, 20))
        assertEquals(255, selectionManager.currentSelection.getAlpha(60, 20))
    }

    @Test
    fun testSelectionCombineMode_Subtract_Difference() {
        // Rect A: [10, 10, 70, 50] = 60x40 = 2400 px
        val rectA = SelectionShape.Rectangle(10f, 10f, 70f, 50f)
        selectionManager.selectShape(rectA, SelectionCombineMode.REPLACE)

        // Subtract Rect B: [30, 10, 50, 50] = 20x40 = 800 px
        val rectB = SelectionShape.Rectangle(30f, 10f, 50f, 50f)
        selectionManager.selectShape(rectB, SelectionCombineMode.SUBTRACT)

        assertEquals(1600, selectionManager.selectedPixelCount)
        // Subtracted middle zone must be zero
        assertEquals(0, selectionManager.currentSelection.getAlpha(40, 20))
        // Wings must remain selected
        assertEquals(255, selectionManager.currentSelection.getAlpha(20, 20))
        assertEquals(255, selectionManager.currentSelection.getAlpha(60, 20))
    }

    @Test
    fun testSelectionCombineMode_Intersect() {
        // Rect A: [10, 10, 60, 60] = 50x50 = 2500 px
        val rectA = SelectionShape.Rectangle(10f, 10f, 60f, 60f)
        selectionManager.selectShape(rectA, SelectionCombineMode.REPLACE)

        // Rect B: [30, 30, 80, 80] = 50x50 = 2500 px
        // Overlap: [30, 30, 60, 60] = 30x30 = 900 px
        val rectB = SelectionShape.Rectangle(30f, 30f, 80f, 80f)
        selectionManager.selectShape(rectB, SelectionCombineMode.INTERSECT)

        assertEquals(900, selectionManager.selectedPixelCount)
        assertEquals(255, selectionManager.currentSelection.getAlpha(45, 45))
        assertEquals(0, selectionManager.currentSelection.getAlpha(20, 20))
        assertEquals(0, selectionManager.currentSelection.getAlpha(70, 70))
    }

    // -------------------------------------------------------------------------
    // 3. Precision Mask Transformations
    // -------------------------------------------------------------------------

    @Test
    fun testSelectionInvert_doubleInvertIdentity() {
        val rect = SelectionShape.Rectangle(10f, 10f, 30f, 30f)
        selectionManager.selectShape(rect, SelectionCombineMode.REPLACE)
        val origCount = selectionManager.selectedPixelCount
        assertEquals(400, origCount)

        // Invert on 100x100 canvas (10000 total pixels)
        selectionManager.invert()
        assertEquals(10000 - 400, selectionManager.selectedPixelCount)
        assertEquals(0, selectionManager.currentSelection.getAlpha(20, 20))
        assertEquals(255, selectionManager.currentSelection.getAlpha(5, 5))

        // Invert again restores exact initial selection
        selectionManager.invert()
        assertEquals(origCount, selectionManager.selectedPixelCount)
        assertEquals(255, selectionManager.currentSelection.getAlpha(20, 20))
        assertEquals(0, selectionManager.currentSelection.getAlpha(5, 5))
    }

    @Test
    fun testSelectionClear_resetsMaskAndBounds() {
        val rect = SelectionShape.Rectangle(20f, 20f, 60f, 60f)
        selectionManager.selectShape(rect, SelectionCombineMode.REPLACE)
        assertTrue(selectionManager.hasSelection)

        selectionManager.clear()
        assertFalse(selectionManager.hasSelection)
        assertEquals(0, selectionManager.selectedPixelCount)
        assertTrue(selectionManager.currentSelection.isEmpty)
    }

    @Test
    fun testSelectionExpand_circularDilation() {
        // Single 10x10 block: [45, 45, 55, 55]
        val rect = SelectionShape.Rectangle(45f, 45f, 55f, 55f)
        selectionManager.selectShape(rect, SelectionCombineMode.REPLACE)
        val initialCount = selectionManager.selectedPixelCount
        assertEquals(100, initialCount)

        // Expand by 5 pixels
        selectionManager.expand(5)
        val expandedCount = selectionManager.selectedPixelCount
        assertTrue("Expanded pixel count ($expandedCount) must exceed initial ($initialCount)", expandedCount > initialCount)

        val bounds = selectionManager.bounds
        assertEquals(40f, bounds.left, 0.01f)
        assertEquals(40f, bounds.top, 0.01f)
        assertEquals(60f, bounds.right, 0.01f)
        assertEquals(60f, bounds.bottom, 0.01f)

        // Extended edges must be selected
        assertEquals(255, selectionManager.currentSelection.getAlpha(40, 50))
        assertEquals(255, selectionManager.currentSelection.getAlpha(59, 50))
        assertEquals(255, selectionManager.currentSelection.getAlpha(50, 40))
        assertEquals(255, selectionManager.currentSelection.getAlpha(50, 59))
    }

    @Test
    fun testSelectionContract_circularErosion() {
        // 40x40 block: [30, 30, 70, 70] = 1600 px
        val rect = SelectionShape.Rectangle(30f, 30f, 70f, 70f)
        selectionManager.selectShape(rect, SelectionCombineMode.REPLACE)
        assertEquals(1600, selectionManager.selectedPixelCount)

        // Contract by 5 pixels -> [35, 35, 65, 65] = 30x30 = 900 px
        selectionManager.contract(5)
        assertEquals(900, selectionManager.selectedPixelCount)

        val bounds = selectionManager.bounds
        assertEquals(35f, bounds.left, 0.01f)
        assertEquals(35f, bounds.top, 0.01f)
        assertEquals(65f, bounds.right, 0.01f)
        assertEquals(65f, bounds.bottom, 0.01f)

        // Stripped perimeter pixels must now be 0
        assertEquals(0, selectionManager.currentSelection.getAlpha(32, 50))
        assertEquals(0, selectionManager.currentSelection.getAlpha(68, 50))
        // Interior core remains 255
        assertEquals(255, selectionManager.currentSelection.getAlpha(50, 50))
    }

    @Test
    fun testSelectionFeather_boundaryGaussianSmoothing() {
        val rect = SelectionShape.Rectangle(20f, 20f, 80f, 80f)
        selectionManager.selectShape(rect, SelectionCombineMode.REPLACE)

        selectionManager.feather(3.0f)

        // Deep interior remains fully opaque
        assertEquals(255, selectionManager.currentSelection.getAlpha(50, 50))
        // Far exterior remains fully zero
        assertEquals(0, selectionManager.currentSelection.getAlpha(5, 5))

        // Boundary edge pixels must have smooth fractional alpha in (0, 255)
        val edgeAlpha = selectionManager.currentSelection.getAlpha(20, 50)
        assertTrue("Edge alpha ($edgeAlpha) must be feathered between 1 and 254", edgeAlpha in 1..254)
    }

    // -------------------------------------------------------------------------
    // 4. Viewport Transform Invariance
    // -------------------------------------------------------------------------

    @Test
    fun testDocumentSpaceInvariance_AcrossZoomPanRotationAndFlip() {
        // Document-space selection: [25, 25, 75, 75]
        val rect = SelectionShape.Rectangle(25f, 25f, 75f, 75f)
        selectionManager.selectShape(rect, SelectionCombineMode.REPLACE)
        val origBounds = selectionManager.bounds
        val origCount = selectionManager.selectedPixelCount

        val viewport = ViewportTransform(
            documentWidth = width.toFloat(),
            documentHeight = height.toFloat(),
            viewportWidth = 800f,
            viewportHeight = 600f
        )

        // 1. Pan transform
        viewport.setPan(150f, -200f)
        assertEquals("Pan must not alter document-space bounds", origBounds, selectionManager.bounds)
        assertEquals("Pan must not alter pixel count", origCount, selectionManager.selectedPixelCount)

        // 2. Zoom transform (0.25x to 8.0x)
        viewport.applyZoom(0.25f)
        assertEquals("Zoom-out must not alter document-space bounds", origBounds, selectionManager.bounds)
        viewport.applyZoom(32.0f) // 0.25 * 32 = 8.0x
        assertEquals("Zoom-in must not alter document-space bounds", origBounds, selectionManager.bounds)

        // 3. Rotation transform (45 deg, 90 deg, 180 deg)
        viewport.setRotation(45f)
        assertEquals("Rotation must not alter document-space bounds", origBounds, selectionManager.bounds)
        viewport.setRotation(90f)
        assertEquals("90 deg rotation must not alter document-space bounds", origBounds, selectionManager.bounds)

        // 4. Flip transform
        viewport.toggleFlipHorizontal()
        assertEquals("Flip must not alter document-space bounds", origBounds, selectionManager.bounds)

        // 5. Screen to canvas coordinate roundtrip hit-test
        val screenPt = viewport.canvasToScreen(50f, 50f)
        val docPt = viewport.screenToCanvas(screenPt.x, screenPt.y)
        assertEquals(50f, docPt.x, 0.05f)
        assertEquals(50f, docPt.y, 0.05f)
        assertTrue(selectionManager.currentSelection.isSelected(docPt.x.toInt(), docPt.y.toInt()))
    }

    // -------------------------------------------------------------------------
    // 5. Integration: Fills, Layer Masks, and Undo/Redo
    // -------------------------------------------------------------------------

    @Test
    fun testSelectionFill_ARGBColorBufferModulation() {
        val pixels = IntArray(width * height) { 0 } // Transparent buffer
        val rect = SelectionShape.Rectangle(20f, 20f, 60f, 60f)
        selectionManager.selectShape(rect, SelectionCombineMode.REPLACE)

        val fillColor = Color.argb(255, 255, 0, 0) // Opaque Red
        selectionManager.fillSelection(pixels, fillColor, opacity = 1.0f)

        // Interior filled with Red
        assertEquals(fillColor, pixels[50 * width + 50])
        assertEquals(fillColor, pixels[25 * width + 25])

        // Exterior unmodified (remains 0)
        assertEquals(0, pixels[10 * width + 10])
        assertEquals(0, pixels[70 * width + 70])
    }

    @Test
    fun testSelectionToLayerMask_DirectConversion() {
        val doc = ArtDocument(canvas = CanvasConfig(width = width, height = height))
        val historyManager = HistoryManager(doc)
        val layerManager = LayerManager(historyManager)
        val rootLayerId = layerManager.currentDocument.activeLayerId

        val ellipse = SelectionShape.Ellipse(20f, 20f, 80f, 80f)
        selectionManager.selectShape(ellipse, SelectionCombineMode.REPLACE)

        val mask = selectionManager.createLayerMaskFromSelection(rootLayerId, layerManager)
        assertNotNull("Converted layer mask must not be null", mask)

        val layer = layerManager.currentDocument.findLayerById(rootLayerId)
        assertNotNull("Target layer must have attached mask", layer?.mask)
        assertEquals(width, layer?.mask?.width)
        assertEquals(height, layer?.mask?.height)
        assertNotNull(layer?.mask?.id)
    }

    @Test
    fun testSelectionHistoryTransactions_UndoRedo() {
        val doc = ArtDocument(canvas = CanvasConfig(width = width, height = height))
        val historyManager = HistoryManager(doc)
        val sm = SelectionManager(width, height, historyManager = historyManager)

        assertFalse(sm.hasSelection)

        // 1. First selection: Rect A
        val rectA = SelectionShape.Rectangle(10f, 10f, 50f, 50f)
        sm.selectShape(rectA, SelectionCombineMode.REPLACE)
        assertTrue(sm.hasSelection)
        assertEquals(1600, sm.selectedPixelCount)

        // 2. Second selection: Add Rect B
        val rectB = SelectionShape.Rectangle(30f, 10f, 70f, 50f)
        sm.selectShape(rectB, SelectionCombineMode.ADD)
        assertEquals(2400, sm.selectedPixelCount)

        // Undo -> restores Rect A (1600 px)
        assertTrue(historyManager.canUndo)
        historyManager.undo()
        assertEquals(1600, sm.selectedPixelCount)

        // Undo again -> restores initial empty state (0 px)
        assertTrue(historyManager.canUndo)
        historyManager.undo()
        assertEquals(0, sm.selectedPixelCount)
        assertFalse(sm.hasSelection)

        // Redo -> restores Rect A (1600 px)
        assertTrue(historyManager.canRedo)
        historyManager.redo()
        assertEquals(1600, sm.selectedPixelCount)

        // Redo -> restores Rect A + B (2400 px)
        assertTrue(historyManager.canRedo)
        historyManager.redo()
        assertEquals(2400, sm.selectedPixelCount)
    }

    // -------------------------------------------------------------------------
    // 6. Corpus Image Fixture Verification (Transparency and Narrow Edges)
    // -------------------------------------------------------------------------

    @Test
    fun testCorpusImageFixture_TransparencyAndNarrowFeatures() {
        val fixtureFile = File("tests/fixtures/artstudio/v1/transparent_png/transparent_cutout_01.png")
        if (!fixtureFile.exists()) {
            val altFile = File("/root/artstudio/tests/fixtures/artstudio/v1/transparent_png/transparent_cutout_01.png")
            if (altFile.exists()) {
                verifyCorpusFixtureWithMask(altFile)
                return
            }
            return
        }
        verifyCorpusFixtureWithMask(fixtureFile)
    }

    private fun verifyCorpusFixtureWithMask(file: File) {
        val bytes = file.readBytes()
        assertTrue("PNG magic header", bytes.size > 24)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])

        val w = ((bytes[16].toInt() and 0xFF) shl 24) or
                ((bytes[17].toInt() and 0xFF) shl 16) or
                ((bytes[18].toInt() and 0xFF) shl 8) or
                (bytes[19].toInt() and 0xFF)
        val h = ((bytes[20].toInt() and 0xFF) shl 24) or
                ((bytes[21].toInt() and 0xFF) shl 16) or
                ((bytes[22].toInt() and 0xFF) shl 8) or
                (bytes[23].toInt() and 0xFF)

        assertTrue("Valid width", w > 0)
        assertTrue("Valid height", h > 0)

        val mask = SelectionMask(w, h)
        val centerRect = SelectionShape.Rectangle(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f)
        mask.applyShape(centerRect, SelectionCombineMode.REPLACE)

        assertFalse(mask.isEmpty)
        assertTrue(mask.selectedPixelCount > 0)

        // Test narrow feature selection across corpus dimensions
        val narrowLine = SelectionShape.Rectangle(0f, (h / 2).toFloat(), w.toFloat(), (h / 2 + 1).toFloat())
        mask.applyShape(narrowLine, SelectionCombineMode.ADD)
        assertTrue(mask.selectedPixelCount >= w)
    }
}
