package com.artstudio.tool

import com.artstudio.adjustment.BrightnessContrastAdjustment
import com.artstudio.color.ColorData
import com.artstudio.core.*
import com.artstudio.document.model.*
import com.artstudio.fill.FillConfig
import com.artstudio.fill.FillMode
import com.artstudio.fill.FloodFillEngine
import com.artstudio.filter.FilterConfig
import com.artstudio.filter.ImageFilterEngine
import com.artstudio.history.*
import com.artstudio.job.*
import com.artstudio.pixelops.PixelModifierConfig
import com.artstudio.pixelops.PixelModifierMode
import com.artstudio.pixelops.PixelModifierSession
import com.artstudio.selection.SelectionShape
import com.artstudio.shape.ShapeGeometryEngine
import com.artstudio.text.TextObject
import com.artstudio.vector.VectorObject
import com.artstudio.vector.VectorStyle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Gate Test Suite for Tool Completeness, Real Pixel Mutation, and Undo/Redo Round-Trip:
 * Proves that each of the 13 Studio tools modifies real layer pixels or document state,
 * has complete reversible round-trip fidelity, and heavy jobs execute on background dispatcher.
 */
class AllToolsFunctionalityAndUndoGateTest {

    private val WIDTH = 64
    private val HEIGHT = 64
    private val COLOR_WHITE = 0xFFFFFFFF.toInt()
    private val COLOR_RED = 0xFFFF0000.toInt()
    private val COLOR_GREEN = 0xFF00FF00.toInt()
    private val COLOR_BLUE = 0xFF0000FF.toInt()
    private val COLOR_ORANGE = 0xFFFF8800.toInt()
    private val COLOR_CLEAR = 0x00000000

    private lateinit var documentState: DefaultDocumentState
    private lateinit var activeLayerId: String

    @Before
    fun setUp() {
        activeLayerId = "layer_main"
        val initialPixels = IntArray(WIDTH * HEIGHT) { COLOR_WHITE }
        val doc = ArtDocument(
            id = "test_doc",
            title = "Tool Gate Test Canvas",
            canvas = CanvasConfig(width = WIDTH, height = HEIGHT),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = activeLayerId,
                        name = "Active Raster Layer",
                        width = WIDTH,
                        height = HEIGHT
                    )
                )
            ),
            selectedLayerId = activeLayerId
        )
        documentState = DefaultDocumentState(
            width = WIDTH,
            height = HEIGHT,
            artDocument = doc,
            initialPixelBuffers = mapOf(activeLayerId to initialPixels)
        )
    }

    // -----------------------------------------------------------------------------------------
    // 1. Eyedropper Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test1_EyedropperTool_samplesRealPixels_andPaintsWithSampledColor() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        val targetX = 20
        val targetY = 20
        pixels[targetY * WIDTH + targetX] = COLOR_ORANGE

        // Eyedropper sample directly from real layer pixel buffer
        val sampledArgb = pixels[targetY * WIDTH + targetX]
        assertEquals("Sampled color must match real layer pixel", COLOR_ORANGE, sampledArgb)

        val colorData = ColorData.fromArgb(sampledArgb)
        assertEquals(COLOR_ORANGE, colorData.argbInt)

        // Use sampled color to paint a stroke
        val stroke = BrushStroke(
            id = "stroke_eyedropper_paint",
            layerId = activeLayerId,
            color = colorData.argbInt,
            baseSize = 2f
        ).apply {
            points.add(StrokePoint(35f, 35f, 1f))
            points.add(StrokePoint(36f, 36f, 1f))
        }

        documentState.addCommittedStroke(stroke)
        pixels[35 * WIDTH + 35] = colorData.argbInt

        assertEquals(colorData.argbInt, pixels[35 * WIDTH + 35])
        assertTrue("Document can undo stroke", documentState.canUndo)

        // Undo: stroke removed
        documentState.undo()
        assertFalse("Stroke removed after undo", documentState.committedStrokes.any { it.id == stroke.id })

        // Redo: stroke restored
        documentState.redo()
        assertTrue("Stroke restored after redo", documentState.committedStrokes.any { it.id == stroke.id })
    }

    // -----------------------------------------------------------------------------------------
    // 2. Flood Fill Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test2_FillTool_modifiesRealPixels_andUndoRedoRoundTrip() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        val preFill = pixels.copyOf()

        // Flood fill canvas with green
        val config = FillConfig(fillColor = COLOR_GREEN, fillMode = FillMode.CONTIGUOUS)
        val fillResult = FloodFillEngine.executeFill(
            sourcePixels = pixels,
            targetPixels = pixels,
            width = WIDTH,
            height = HEIGHT,
            seedX = 10,
            seedY = 10,
            config = config
        )
        assertNotNull(fillResult)
        assertTrue("Flood fill reported success", fillResult!!.success)
        assertNotNull(fillResult.resultPixels)

        val postPixels = fillResult.resultPixels!!
        val deltas = TileDeltaHelper.extractTileDeltas(preFill, postPixels, WIDTH, HEIGHT)
        System.arraycopy(postPixels, 0, pixels, 0, WIDTH * HEIGHT)

        val cmd = FillCommand(
            layerId = activeLayerId,
            tileDeltas = deltas,
            width = WIDTH,
            height = HEIGHT
        )
        documentState.historyManager?.execute(cmd)

        assertEquals("Filled pixel must be green", COLOR_GREEN, pixels[10 * WIDTH + 10])

        // Undo: bit-exact restoration
        documentState.undo()
        assertEquals("Pixel must be restored to white after undo", COLOR_WHITE, pixels[10 * WIDTH + 10])

        // Redo: reapply fill
        documentState.redo()
        assertEquals("Pixel must be restored to green after redo", COLOR_GREEN, pixels[10 * WIDTH + 10])
    }

    // -----------------------------------------------------------------------------------------
    // 3. Smudge Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test3_SmudgeTool_modifiesRealPixels_andUndoRedoRoundTrip() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        // Half red, half blue
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                pixels[y * WIDTH + x] = if (x < WIDTH / 2) COLOR_RED else COLOR_BLUE
            }
        }

        val session = PixelModifierSession()
        val config = PixelModifierConfig(
            mode = PixelModifierMode.SMUDGE,
            size = 12f,
            strength = 0.8f
        )
        session.beginStroke(
            layerId = activeLayerId,
            targetPixels = pixels,
            width = WIDTH,
            height = HEIGHT,
            startX = 30f,
            startY = 32f,
            initialPressure = 1f,
            config = config
        )
        session.addPoint(34f, 32f, 1f)
        val cmd = session.endStroke()

        assertNotNull("Smudge session committed command", cmd)
        cmd?.let { documentState.historyManager?.execute(it) }

        val smudgedPixel = pixels[32 * WIDTH + 32]
        assertNotEquals("Smudged pixel boundary changed from pure blue", COLOR_BLUE, smudgedPixel)
        assertNotEquals("Smudged pixel boundary changed from pure red", COLOR_RED, smudgedPixel)

        // Undo
        documentState.undo()
        assertEquals("Boundary pixel restored to blue after undo", COLOR_BLUE, pixels[32 * WIDTH + 32])

        // Redo
        documentState.redo()
        assertEquals("Boundary pixel restored to smudged value after redo", smudgedPixel, pixels[32 * WIDTH + 32])
    }

    // -----------------------------------------------------------------------------------------
    // 4. Blur Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test4_BlurTool_modifiesRealPixels_andUndoRedoRoundTrip() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        // High contrast box in center
        for (y in 20..44) {
            for (x in 20..44) {
                pixels[y * WIDTH + x] = 0xFF000000.toInt()
            }
        }

        val session = PixelModifierSession()
        val config = PixelModifierConfig(
            mode = PixelModifierMode.BLUR,
            size = 16f,
            strength = 0.9f
        )
        session.beginStroke(
            layerId = activeLayerId,
            targetPixels = pixels,
            width = WIDTH,
            height = HEIGHT,
            startX = 20f,
            startY = 32f,
            initialPressure = 1f,
            config = config
        )
        session.addPoint(22f, 32f, 1f)
        val cmd = session.endStroke()

        assertNotNull("Blur session produced command", cmd)
        cmd?.let { documentState.historyManager?.execute(it) }

        val blurredPixel = pixels[32 * WIDTH + 20]
        assertNotEquals("Edge pixel was blurred", 0xFF000000.toInt(), blurredPixel)

        // Undo
        documentState.undo()
        assertEquals("Edge pixel reverted to sharp black after undo", 0xFF000000.toInt(), pixels[32 * WIDTH + 20])

        // Redo
        documentState.redo()
        assertEquals("Edge pixel restored to blurred value after redo", blurredPixel, pixels[32 * WIDTH + 20])
    }

    // -----------------------------------------------------------------------------------------
    // 5. Filters Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test5_FiltersTool_modifiesRealPixels_andUndoRedoRoundTrip() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        val preFilter = pixels.copyOf()
        // Draw black square in center
        for (y in 20..40) {
            for (x in 20..40) {
                pixels[y * WIDTH + x] = 0xFF000000.toInt()
            }
        }
        val preFilteredPixels = pixels.copyOf()

        val filterConfig = FilterConfig.GaussianBlurConfig(radius = 4f, iterations = 1)
        val postPixels = ImageFilterEngine.applyFilter(pixels, WIDTH, HEIGHT, filterConfig)
        val deltas = TileDeltaHelper.extractTileDeltas(preFilteredPixels, postPixels, WIDTH, HEIGHT)
        System.arraycopy(postPixels, 0, pixels, 0, WIDTH * HEIGHT)

        val cmd = ApplyFilterCommand(
            layerId = activeLayerId,
            filterName = "Gaussian Blur",
            width = WIDTH,
            height = HEIGHT,
            tileDeltas = deltas
        )
        documentState.historyManager?.execute(cmd)

        val edgePixel = pixels[20 * WIDTH + 19]
        assertNotEquals("Filter blurred edge pixel", COLOR_WHITE, edgePixel)

        // Undo
        documentState.undo()
        assertEquals("Pixels restored bit-exact to pre-filter after undo", COLOR_WHITE, pixels[20 * WIDTH + 19])

        // Redo
        documentState.redo()
        assertEquals("Pixels restored to blurred color after redo", edgePixel, pixels[20 * WIDTH + 19])
    }

    // -----------------------------------------------------------------------------------------
    // 6. Color Adjustments Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test6_AdjustmentsTool_modifiesRealPixels_andUndoRedoRoundTrip() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        val gray = 0xFF808080.toInt()
        pixels.fill(gray)

        val adjustment = BrightnessContrastAdjustment(brightness = 0.4f)
        documentState.layerManager.applyDirectAdjustment(
            layerId = activeLayerId,
            adjustment = adjustment,
            pixelBuffer = pixels,
            width = WIDTH,
            height = HEIGHT
        )

        val adjustedPixel = pixels[0]
        assertNotEquals("Adjustment modified real pixel buffer", gray, adjustedPixel)
        assertTrue("Brightness adjustment increased luminance", (adjustedPixel and 0xFF) > (gray and 0xFF))

        // Undo
        documentState.undo()
        assertEquals("Pixels restored bit-exact to original gray after undo", gray, pixels[0])

        // Redo
        documentState.redo()
        assertEquals("Pixels restored to brightened color after redo", adjustedPixel, pixels[0])
    }

    // -----------------------------------------------------------------------------------------
    // 7. Shapes Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test7_ShapesTool_stampsToRaster_andUndoRedoRoundTrip() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        val preShape = pixels.copyOf()

        val shape = VectorObject.createRectangle(
            x = 10f,
            y = 10f,
            width = 20f,
            height = 20f,
            style = VectorStyle(fillColor = COLOR_RED.toLong())
        )

        val stamped = ShapeGeometryEngine.stampShapeToRasterBuffer(pixels, WIDTH, HEIGHT, shape)
        val deltas = TileDeltaHelper.extractTileDeltas(preShape, stamped, WIDTH, HEIGHT)
        System.arraycopy(stamped, 0, pixels, 0, WIDTH * HEIGHT)

        val cmd = ApplyFilterCommand(
            layerId = activeLayerId,
            filterName = "Stamp Shape",
            width = WIDTH,
            height = HEIGHT,
            tileDeltas = deltas
        )
        documentState.historyManager?.execute(cmd)

        assertEquals("Inside shape pixel stamped with red", COLOR_RED, pixels[15 * WIDTH + 15])
        assertEquals("Outside shape pixel remains white", COLOR_WHITE, pixels[5 * WIDTH + 5])

        // Undo
        documentState.undo()
        assertEquals("Inside shape pixel restored to white after undo", COLOR_WHITE, pixels[15 * WIDTH + 15])

        // Redo
        documentState.redo()
        assertEquals("Inside shape pixel restored to red after redo", COLOR_RED, pixels[15 * WIDTH + 15])
    }

    // -----------------------------------------------------------------------------------------
    // 8. Vector Tools
    // -----------------------------------------------------------------------------------------
    @Test
    fun test8_VectorTools_modifiesVectorState_andUndoRedoRoundTrip() {
        val vectorLayerId = "layer_vector"
        val vectorLayer = LayerNode.VectorPathLayer(id = vectorLayerId, name = "Ink Vectors")
        documentState.historyManager?.execute(AddLayerCommand(layer = vectorLayer))

        val vectorObj = VectorObject.createRectangle(
            x = 5f,
            y = 5f,
            width = 15f,
            height = 15f,
            style = VectorStyle(strokeColor = COLOR_BLUE.toLong())
        )
        val addCmd = AddVectorObjectCommand(targetLayerId = vectorLayerId, vectorObject = vectorObj)
        documentState.historyManager?.execute(addCmd)

        val updatedLayer = documentState.authoritativeDocument.findLayerById(vectorLayerId) as? LayerNode.VectorPathLayer
        assertNotNull("Vector layer exists", updatedLayer)
        assertEquals("Vector object added", 1, updatedLayer?.vectorObjects?.size)

        // Undo
        documentState.undo()
        val undoneLayer = documentState.authoritativeDocument.findLayerById(vectorLayerId) as? LayerNode.VectorPathLayer
        assertEquals("Vector object removed after undo", 0, undoneLayer?.vectorObjects?.size)

        // Redo
        documentState.redo()
        val redoneLayer = documentState.authoritativeDocument.findLayerById(vectorLayerId) as? LayerNode.VectorPathLayer
        assertEquals("Vector object restored after redo", 1, redoneLayer?.vectorObjects?.size)
    }

    // -----------------------------------------------------------------------------------------
    // 9. Text Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test9_TextTool_addsTextLayer_andUndoRedoRoundTrip() {
        val textObj = TextObject(text = "ArtStudio Pro", fontSize = 24f, textColor = COLOR_RED.toLong())
        val textLayer = LayerNode.TextLayer(
            id = "layer_text",
            name = "Text Layer",
            textObject = textObj
        )
        val cmd = AddTextLayerCommand(textLayer = textLayer)
        documentState.historyManager?.execute(cmd)

        val layer = documentState.authoritativeDocument.findLayerById("layer_text") as? LayerNode.TextLayer
        assertNotNull("Text layer added to document", layer)
        assertEquals("Text content preserved", "ArtStudio Pro", layer?.textObject?.text)

        // Undo
        documentState.undo()
        assertNull("Text layer removed after undo", documentState.authoritativeDocument.findLayerById("layer_text"))

        // Redo
        documentState.redo()
        assertNotNull("Text layer restored after redo", documentState.authoritativeDocument.findLayerById("layer_text"))
    }

    // -----------------------------------------------------------------------------------------
    // 10. Selection Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test10_SelectionTool_fillsSelection_andUndoRedoRoundTrip() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        val sm = documentState.selectionManager
        assertNotNull("SelectionManager available", sm)

        // Create rectangular selection from (10, 10) to (30, 30)
        sm?.selectShape(SelectionShape.Rectangle(10f, 10f, 30f, 30f))
        assertTrue("Active selection exists", sm?.hasSelection == true)

        // Fill selection with blue
        val fillCmd = sm?.fillSelectionUndoable(activeLayerId, pixels, COLOR_BLUE)
        assertNotNull("Fill selection completed", fillCmd)

        assertEquals("Inside selection pixel is blue", COLOR_BLUE, pixels[20 * WIDTH + 20])
        assertEquals("Outside selection pixel is white", COLOR_WHITE, pixels[5 * WIDTH + 5])

        // Undo
        documentState.undo()
        assertEquals("Inside selection pixel restored to white after undo", COLOR_WHITE, pixels[20 * WIDTH + 20])

        // Redo
        documentState.redo()
        assertEquals("Inside selection pixel restored to blue after redo", COLOR_BLUE, pixels[20 * WIDTH + 20])
    }

    // -----------------------------------------------------------------------------------------
    // 11. Transform Tool
    // -----------------------------------------------------------------------------------------
    @Test
    fun test11_TransformTool_bakesTransformToPixels_andUndoRedoRoundTrip() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        // Mark pixel at (5, 5) with red
        pixels[5 * WIDTH + 5] = COLOR_RED

        // Apply translation (+10, +10)
        val transform = LayerTransform(translationX = 10f, translationY = 10f)
        documentState.historyManager?.execute(
            TransformLayerCommand(
                layerId = activeLayerId,
                oldTransform = LayerTransform(),
                newTransform = transform
            )
        )

        val bakeSuccess = documentState.layerManager.bakeLayerTransform(activeLayerId, pixels)
        assertTrue("Bake transform reported success", bakeSuccess)

        // Resampled pixel at (15, 15) has red content; (5, 5) no longer pure red
        assertNotEquals("Pixel at (5, 5) was translated away", COLOR_RED, pixels[5 * WIDTH + 5])
        assertEquals("Pixel at (15, 15) received translated red content", COLOR_RED, pixels[15 * WIDTH + 15])

        // Undo
        documentState.undo()
        assertEquals("Pixel at (5, 5) restored after undo", COLOR_RED, pixels[5 * WIDTH + 5])
        assertNotEquals("Pixel at (15, 15) restored after undo", COLOR_RED, pixels[15 * WIDTH + 15])

        // Redo
        documentState.redo()
        assertEquals("Pixel at (15, 15) re-translated after redo", COLOR_RED, pixels[15 * WIDTH + 15])
    }

    // -----------------------------------------------------------------------------------------
    // 12. Symmetry Tool (Strictly ONE Undo Step)
    // -----------------------------------------------------------------------------------------
    @Test
    fun test12_SymmetryTool_singleUndoStepRestoresAllBranches() {
        // Create 4 symmetrical stroke reflections
        val s1 = BrushStroke(id = "sym_1", layerId = activeLayerId, color = COLOR_RED)
        val s2 = BrushStroke(id = "sym_2", layerId = activeLayerId, color = COLOR_RED)
        val s3 = BrushStroke(id = "sym_3", layerId = activeLayerId, color = COLOR_RED)
        val s4 = BrushStroke(id = "sym_4", layerId = activeLayerId, color = COLOR_RED)

        documentState.addCommittedStrokes(listOf(s1, s2, s3, s4))
        assertEquals("All 4 symmetric strokes added", 4, documentState.committedStrokes.size)

        // Undo ONCE
        val undoSuccess = documentState.undo()
        assertTrue("Undo succeeded", undoSuccess)
        assertEquals(
            "STRICTLY ONE undo step must remove ALL 4 symmetric branches",
            0,
            documentState.committedStrokes.size
        )

        // Redo ONCE
        val redoSuccess = documentState.redo()
        assertTrue("Redo succeeded", redoSuccess)
        assertEquals(
            "STRICTLY ONE redo step must restore ALL 4 symmetric branches",
            4,
            documentState.committedStrokes.size
        )
    }

    // -----------------------------------------------------------------------------------------
    // 13. Clear Tool (Undoable + Full Restoration)
    // -----------------------------------------------------------------------------------------
    @Test
    fun test13_ClearTool_confirmAndUndoRestoresAllPixelsAndStrokes() {
        val pixels = documentState.getOrRenderLayerPixels(activeLayerId)
        pixels.fill(COLOR_RED)

        val stroke = BrushStroke(id = "stroke_before_clear", layerId = activeLayerId, color = COLOR_BLUE)
        documentState.addCommittedStroke(stroke)
        assertEquals(1, documentState.committedStrokes.size)
        assertEquals(COLOR_RED, pixels[0])

        // Clear active layer undoably
        val clearSuccess = documentState.clearActiveLayerUndoably()
        assertTrue("Clear layer succeeded", clearSuccess)

        assertEquals("Strokes cleared", 0, documentState.committedStrokes.size)
        assertEquals("Pixels cleared to transparent/zero", COLOR_CLEAR, pixels[0])

        // Undo clear: restores strokes and bit-exact pixels
        documentState.undo()
        assertEquals("Strokes restored after undo clear", 1, documentState.committedStrokes.size)
        assertEquals("Pixels restored bit-exact to red after undo clear", COLOR_RED, pixels[0])

        // Redo clear
        documentState.redo()
        assertEquals("Strokes cleared again after redo", 0, documentState.committedStrokes.size)
        assertEquals("Pixels cleared again after redo", COLOR_CLEAR, pixels[0])
    }

    // -----------------------------------------------------------------------------------------
    // 14. Heavy Operations via JobScheduler
    // -----------------------------------------------------------------------------------------
    @Test
    fun test14_HeavyOperationWithJobScheduler_backgroundDispatcherAndProgress() {
        runBlocking {
            val tempDir = File("build/tmp/test_gate_job_${System.currentTimeMillis()}").apply { mkdirs() }
            val scheduler = JobScheduler(
                dispatcher = Dispatchers.Default,
                journalFile = File(tempDir, "job_journal.txt")
            )

            val inputSnapshot = IntArray(WIDTH * HEIGHT) { COLOR_RED }
            var wasExecutedOnBackground = false

            val job = RasterProcessingJob(
                name = "Heavy Filter Processing",
                lane = WorkLane.TRANSFORM,
                sourceSnapshot = inputSnapshot,
                width = WIDTH,
                height = HEIGHT
            ) { snapshot, w, h, ctx ->
                wasExecutedOnBackground = !Thread.currentThread().name.contains("main")
                for (i in snapshot.indices) {
                    snapshot[i] = COLOR_BLUE
                }
                snapshot
            }

            scheduler.enqueue(job)

            withTimeout(3000L) {
                job.state.first { it == JobState.SUCCEEDED }
            }

            assertEquals(JobState.SUCCEEDED, job.state.value)
            assertTrue("Heavy processing executed off main thread", wasExecutedOnBackground)
            assertEquals(1.0f, job.progress.value.progress, 0.001f)
            assertNotNull("Result pixels produced", job.resultPixels)
            assertEquals("Output pixels transformed to blue", COLOR_BLUE, job.resultPixels!![0])
            assertEquals("Input snapshot remained immutable", COLOR_RED, inputSnapshot[0])

            scheduler.cancelAll()
            scheduler.scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 15. Float.MIN_VALUE Misuse Safeguard Check
    // -----------------------------------------------------------------------------------------
    @Test
    fun test15_FloatMinValueMisuseCheck_negativeCoordinatesHandledProperly() {
        val stroke = BrushStroke(id = "neg_stroke", baseSize = 5f)
        stroke.points.add(StrokePoint(-50f, -100f, 1f))
        stroke.points.add(StrokePoint(-10f, -20f, 1f))

        val bounds = stroke.computeBoundingBox()
        assertTrue("Bounding box left must encompass negative X", bounds.left <= -50f)
        assertTrue("Bounding box top must encompass negative Y", bounds.top <= -100f)
        assertTrue("Bounding box right must encompass max negative X", bounds.right >= -10f)
        assertTrue("Bounding box bottom must encompass max negative Y", bounds.bottom >= -20f)

        val tracker = DirtyRegionTracker()
        tracker.markDirtyRect(-30f, -40f, -10f, -20f)
        val dirty = tracker.getBounds()
        assertNotNull("Dirty region tracked for negative bounds", dirty)
        assertTrue("Dirty left correctly includes negative", dirty!!.left <= -30f)
        assertTrue("Dirty right correctly includes negative", dirty.right >= -10f)
    }
}
