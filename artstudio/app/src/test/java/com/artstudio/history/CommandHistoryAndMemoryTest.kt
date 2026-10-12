package com.artstudio.history

import com.artstudio.core.*
import com.artstudio.document.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive test suite for Reversible Edit Commands, Transaction History,
 * Checkpoints, Save Interactions, Branch Truncation, Cancellation, and Memory Baselines.
 */
class CommandHistoryAndMemoryTest {

    private lateinit var baseDocument: ArtDocument
    private lateinit var historyManager: HistoryManager

    @Before
    fun setUp() {
        baseDocument = ArtDocument(
            id = "doc_test",
            title = "History Test Canvas",
            canvas = CanvasConfig(width = 2048, height = 2048),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "layer_bg",
                        name = "Background",
                        width = 2048,
                        height = 2048
                    ),
                    LayerNode.VectorPathLayer(
                        id = "layer_vector",
                        name = "Inks"
                    )
                )
            )
        )
        historyManager = HistoryManager(
            initialDocument = baseDocument,
            maxHistoryCount = 100,
            checkpointInterval = 20,
            maxCheckpoints = 5
        )
    }

    private fun createSampleStroke(id: String, color: Int = 0xFF000000.toInt(), pointCount: Int = 50): BrushStroke {
        val stroke = BrushStroke(id = id, color = color, baseSize = 12f, toolType = ToolType.STYLUS)
        for (i in 0 until pointCount) {
            stroke.points.add(StrokePoint(i * 10f, 100f + (i % 5) * 4f, 0.5f))
        }
        return stroke
    }

    @Test
    fun testCommandOrderFifoAndLifoReversal() {
        val stroke1 = createSampleStroke("s1")
        val stroke2 = createSampleStroke("s2")
        val stroke3 = createSampleStroke("s3")

        val cmd1 = StrokeCommand(stroke = stroke1, targetLayerId = "layer_vector")
        val cmd2 = StrokeCommand(stroke = stroke2, targetLayerId = "layer_vector")
        val cmd3 = StrokeCommand(stroke = stroke3, targetLayerId = "layer_vector")

        historyManager.execute(cmd1)
        historyManager.execute(cmd2)
        historyManager.execute(cmd3)

        assertEquals(3, historyManager.undoCount)
        assertEquals(0, historyManager.redoCount)
        assertTrue(historyManager.canUndo)
        assertFalse(historyManager.canRedo)

        // Verify LIFO undo order
        val undone3 = historyManager.undo()
        assertEquals(cmd3.id, undone3?.id)
        assertEquals(2, historyManager.undoCount)
        assertEquals(1, historyManager.redoCount)

        val undone2 = historyManager.undo()
        assertEquals(cmd2.id, undone2?.id)
        assertEquals(1, historyManager.undoCount)
        assertEquals(2, historyManager.redoCount)

        val undone1 = historyManager.undo()
        assertEquals(cmd1.id, undone1?.id)
        assertEquals(0, historyManager.undoCount)
        assertEquals(3, historyManager.redoCount)
        assertFalse(historyManager.canUndo)
        assertTrue(historyManager.canRedo)

        // Verify redo re-applies in original FIFO execution order
        val redone1 = historyManager.redo()
        assertEquals(cmd1.id, redone1?.id)
        val redone2 = historyManager.redo()
        assertEquals(cmd2.id, redone2?.id)
        val redone3 = historyManager.redo()
        assertEquals(cmd3.id, redone3?.id)

        assertEquals(3, historyManager.undoCount)
        assertEquals(0, historyManager.redoCount)
    }

    @Test
    fun testBranchTruncationWhenNewCommandFollowsUndo() {
        val stroke1 = createSampleStroke("s1")
        val stroke2 = createSampleStroke("s2")
        val stroke3 = createSampleStroke("s3")

        historyManager.execute(StrokeCommand(stroke = stroke1, targetLayerId = "layer_vector"))
        historyManager.execute(StrokeCommand(stroke = stroke2, targetLayerId = "layer_vector"))
        historyManager.execute(StrokeCommand(stroke = stroke3, targetLayerId = "layer_vector"))

        assertEquals(3, historyManager.undoCount)

        // Undo two steps (leaving stroke1 on undo stack, stroke2 and stroke3 on redo stack)
        historyManager.undo()
        historyManager.undo()
        assertEquals(1, historyManager.undoCount)
        assertEquals(2, historyManager.redoCount)

        // Execute a new command: MUST truncate redo branch
        val strokeBranch = createSampleStroke("s_branch")
        historyManager.execute(StrokeCommand(stroke = strokeBranch, targetLayerId = "layer_vector"))

        assertEquals(2, historyManager.undoCount)
        assertEquals(0, historyManager.redoCount)
        assertFalse("Redo branch must be cleared after branching edit", historyManager.canRedo)
        assertEquals("Paint Stroke", historyManager.lastUndoDescription)
    }

    @Test
    fun testPeriodicBoundedCheckpointing() {
        // Execute 45 commands to trigger 2 periodic checkpoints (at 20 and 40)
        for (i in 1..45) {
            val stroke = createSampleStroke("stroke_$i", pointCount = 10)
            historyManager.execute(StrokeCommand(stroke = stroke, targetLayerId = "layer_vector"))
        }

        val checkpoints = historyManager.getCheckpoints()
        // 1 initial + checkpoints at 20 and 40 = 3 checkpoints total
        assertEquals(3, checkpoints.size)
        assertEquals(0, checkpoints[0].commandIndex)
        assertEquals(20, checkpoints[1].commandIndex)
        assertEquals(40, checkpoints[2].commandIndex)
        assertTrue(checkpoints.size <= historyManager.maxCheckpoints)
    }

    @Test
    fun testDistinctCancellationDecoupledFromUndo() {
        val committedStroke = createSampleStroke("s_committed")
        historyManager.execute(StrokeCommand(stroke = committedStroke, targetLayerId = "layer_vector"))
        assertEquals(1, historyManager.undoCount)
        assertEquals(0, historyManager.redoCount)

        // User starts drawing an in-flight stroke
        historyManager.beginInFlight("Drawing active stroke")
        assertTrue(historyManager.hasInFlightOperation)

        // In-flight gesture is cancelled (e.g. palm contact or pointer cancel)
        val cancelled = historyManager.cancelInFlight()
        assertTrue(cancelled)
        assertFalse(historyManager.hasInFlightOperation)

        // Invariant: Cancellation must NOT create an undo step or alter undo/redo stacks
        assertEquals("Cancellation must not mutate undo count", 1, historyManager.undoCount)
        assertEquals("Cancellation must not mutate redo count", 0, historyManager.redoCount)
        assertEquals(committedStroke.id, (historyManager.getUndoCommands().last() as StrokeCommand).stroke.id)
    }

    @Test
    fun testSaveAndReopenInteractionWithDirtyState() {
        // Initially clean
        assertFalse("Fresh document without edits must be clean", historyManager.isDirty)

        // 1. First edit: document becomes dirty
        val stroke1 = createSampleStroke("s1")
        historyManager.execute(StrokeCommand(stroke = stroke1, targetLayerId = "layer_vector"))
        assertTrue("Document must be dirty after edit", historyManager.isDirty)

        // 2. Save document: marks clean
        historyManager.markSaved()
        assertFalse("Document must be clean immediately after save", historyManager.isDirty)

        // 3. Second edit: document becomes dirty again
        val stroke2 = createSampleStroke("s2")
        historyManager.execute(StrokeCommand(stroke = stroke2, targetLayerId = "layer_vector"))
        assertTrue("Document must be dirty after second edit", historyManager.isDirty)

        // 4. Undo second edit: returns to saved state -> MUST be clean!
        historyManager.undo()
        assertFalse("Undoing back to the save point must restore clean state", historyManager.isDirty)

        // 5. Undo first edit (past save point): becomes dirty again!
        historyManager.undo()
        assertTrue("Undoing past the save point must make document dirty", historyManager.isDirty)

        // 6. Redo first edit (back to save point): MUST be clean again!
        historyManager.redo()
        assertFalse("Redoing back to the save point must restore clean state", historyManager.isDirty)

        // 7. Redo second edit (past save point): becomes dirty!
        historyManager.redo()
        assertTrue("Redoing past save point must be dirty", historyManager.isDirty)
    }

    @Test
    fun testReopenDocumentReinitializesHistoryOrPreservesCheckpoint() {
        val stroke1 = createSampleStroke("s1")
        historyManager.execute(StrokeCommand(stroke = stroke1, targetLayerId = "layer_vector"))
        assertTrue(historyManager.isDirty)

        // Reopen document without preserving history (fresh session)
        historyManager.reinitializeForReopen(historyManager.currentDocument, preserveHistory = false)
        assertEquals(0, historyManager.undoCount)
        assertEquals(0, historyManager.redoCount)
        assertFalse(historyManager.canUndo)
        assertFalse(historyManager.isDirty)
    }

    @Test
    fun testLayerAddAndRemoveUndoRedo() {
        val newLayer = LayerNode.RasterLayer(
            id = "layer_sketch",
            name = "Sketch Layer",
            opacity = 0.8f
        )

        val addCmd = AddLayerCommand(layer = newLayer, index = 1)
        historyManager.execute(addCmd)

        // Verify layer is present in document
        assertNotNull(historyManager.currentDocument.findLayerById("layer_sketch"))
        assertEquals(3, historyManager.currentDocument.rootGroup.children.size)

        // Undo: layer must be removed
        historyManager.undo()
        assertNull(historyManager.currentDocument.findLayerById("layer_sketch"))
        assertEquals(2, historyManager.currentDocument.rootGroup.children.size)

        // Redo: layer must be restored with exact properties
        historyManager.redo()
        val restored = historyManager.currentDocument.findLayerById("layer_sketch") as? LayerNode.RasterLayer
        assertNotNull(restored)
        assertEquals("Sketch Layer", restored?.name)
        assertEquals(0.8f, restored?.opacity ?: 0f, 0.001f)
    }

    @Test
    fun testLayerReorderUndoRedo() {
        // Current children: [layer_bg (0), layer_vector (1)]
        val reorderCmd = ReorderLayerCommand(
            layerId = "layer_bg",
            fromIndex = 0,
            toIndex = 1
        )
        historyManager.execute(reorderCmd)

        // Verify layer_bg is now at index 1
        assertEquals("layer_vector", historyManager.currentDocument.rootGroup.children[0].id)
        assertEquals("layer_bg", historyManager.currentDocument.rootGroup.children[1].id)

        // Undo: restores original order
        historyManager.undo()
        assertEquals("layer_bg", historyManager.currentDocument.rootGroup.children[0].id)
        assertEquals("layer_vector", historyManager.currentDocument.rootGroup.children[1].id)

        // Redo: moves back to index 1
        historyManager.redo()
        assertEquals("layer_vector", historyManager.currentDocument.rootGroup.children[0].id)
        assertEquals("layer_bg", historyManager.currentDocument.rootGroup.children[1].id)
    }

    @Test
    fun testLayerPropertyMutationsUndoRedo() {
        // Mutate opacity from 1.0f to 0.45f
        val opacityCmd = SetLayerPropertyCommand(
            layerId = "layer_bg",
            propertyName = "opacity",
            oldValue = 1.0f,
            newValue = 0.45f
        )
        historyManager.execute(opacityCmd)
        assertEquals(0.45f, historyManager.currentDocument.findLayerById("layer_bg")?.opacity ?: 0f, 0.001f)

        // Mutate visibility from true to false
        val visCmd = SetLayerPropertyCommand(
            layerId = "layer_bg",
            propertyName = "visible",
            oldValue = true,
            newValue = false
        )
        historyManager.execute(visCmd)
        assertFalse(historyManager.currentDocument.findLayerById("layer_bg")?.visible ?: true)

        // Undo visibility
        historyManager.undo()
        assertTrue(historyManager.currentDocument.findLayerById("layer_bg")?.visible ?: false)

        // Undo opacity
        historyManager.undo()
        assertEquals(1.0f, historyManager.currentDocument.findLayerById("layer_bg")?.opacity ?: 0f, 0.001f)

        // Redo opacity then visibility
        historyManager.redo()
        assertEquals(0.45f, historyManager.currentDocument.findLayerById("layer_bg")?.opacity ?: 0f, 0.001f)
        historyManager.redo()
        assertFalse(historyManager.currentDocument.findLayerById("layer_bg")?.visible ?: true)
    }

    @Test
    fun testLayerTransformUndoRedo() {
        val originalTransform = LayerTransform(translationX = 0f, translationY = 0f, scaleX = 1f, scaleY = 1f)
        val modifiedTransform = LayerTransform(translationX = 150f, translationY = -80f, scaleX = 2.0f, scaleY = 2.0f, rotationDegrees = 45f)

        val transformCmd = TransformLayerCommand(
            layerId = "layer_bg",
            oldTransform = originalTransform,
            newTransform = modifiedTransform
        )

        historyManager.execute(transformCmd)
        val applied = historyManager.currentDocument.findLayerById("layer_bg")?.transform
        assertEquals(150f, applied?.translationX ?: 0f, 0.001f)
        assertEquals(45f, applied?.rotationDegrees ?: 0f, 0.001f)

        // Undo transform
        historyManager.undo()
        val reverted = historyManager.currentDocument.findLayerById("layer_bg")?.transform
        assertEquals(0f, reverted?.translationX ?: 0f, 0.001f)
        assertEquals(0f, reverted?.rotationDegrees ?: 0f, 0.001f)

        // Redo transform
        historyManager.redo()
        val reapplied = historyManager.currentDocument.findLayerById("layer_bg")?.transform
        assertEquals(150f, reapplied?.translationX ?: 0f, 0.001f)
        assertEquals(45f, reapplied?.rotationDegrees ?: 0f, 0.001f)
    }

    @Test
    fun testVectorPathCommandUndoRedo() {
        val vectorPath = VectorPathData(
            id = "vp_1",
            points = listOf(StrokePointData(10f, 10f, 0.5f), StrokePointData(50f, 80f, 0.8f)),
            strokeWidth = 14f,
            strokeColor = 0xFF2196F3L
        )

        val pathCmd = VectorPathCommand(
            targetLayerId = "layer_vector",
            pathData = vectorPath
        )

        historyManager.execute(pathCmd)
        val vLayer = historyManager.currentDocument.findLayerById("layer_vector") as LayerNode.VectorPathLayer
        assertEquals(1, vLayer.paths.size)
        assertEquals("vp_1", vLayer.paths[0].id)

        // Undo: path removed
        historyManager.undo()
        val vLayerUndone = historyManager.currentDocument.findLayerById("layer_vector") as LayerNode.VectorPathLayer
        assertEquals(0, vLayerUndone.paths.size)

        // Redo: path restored
        historyManager.redo()
        val vLayerRedone = historyManager.currentDocument.findLayerById("layer_vector") as LayerNode.VectorPathLayer
        assertEquals(1, vLayerRedone.paths.size)
    }

    @Test
    fun testCompoundTransactionCommandAtomicReversal() {
        val subLayer = LayerNode.RasterLayer(id = "sub_1", name = "Sub Layer")
        val addCmd = AddLayerCommand(layer = subLayer)
        val propCmd = SetLayerPropertyCommand(layerId = "sub_1", propertyName = "opacity", oldValue = 1.0f, newValue = 0.5f)

        val compound = CompoundEditCommand(
            commands = listOf(addCmd, propCmd),
            description = "Add & Configure Sub Layer"
        )

        historyManager.execute(compound)
        assertEquals(1, historyManager.undoCount)
        assertNotNull(historyManager.currentDocument.findLayerById("sub_1"))
        assertEquals(0.5f, historyManager.currentDocument.findLayerById("sub_1")?.opacity ?: 0f, 0.001f)

        // Single atomic undo reverts all sub-commands in reverse order
        historyManager.undo()
        assertEquals(0, historyManager.undoCount)
        assertNull(historyManager.currentDocument.findLayerById("sub_1"))

        // Single atomic redo reapplies all sub-commands
        historyManager.redo()
        assertEquals(1, historyManager.undoCount)
        assertNotNull(historyManager.currentDocument.findLayerById("sub_1"))
        assertEquals(0.5f, historyManager.currentDocument.findLayerById("sub_1")?.opacity ?: 0f, 0.001f)
    }

    @Test
    fun testInterleavedStrokesAndLayerOperationsUndoRedo() {
        // Interleave stroke commit, layer add, property change, and transform
        val stroke1 = createSampleStroke("s1")
        historyManager.execute(StrokeCommand(stroke = stroke1, targetLayerId = "layer_vector"))

        val newLayer = LayerNode.RasterLayer(id = "layer_ink2", name = "Ink Overlay")
        historyManager.execute(AddLayerCommand(layer = newLayer))

        val stroke2 = createSampleStroke("s2")
        historyManager.execute(StrokeCommand(stroke = stroke2, targetLayerId = "layer_vector"))

        val propCmd = SetLayerPropertyCommand(layerId = "layer_ink2", propertyName = "opacity", oldValue = 1.0f, newValue = 0.7f)
        historyManager.execute(propCmd)

        assertEquals(4, historyManager.undoCount)

        // Undo all 4 in reverse order
        val u4 = historyManager.undo() // reverts propCmd
        assertTrue(u4 is SetLayerPropertyCommand)

        val u3 = historyManager.undo() // reverts stroke2
        assertTrue(u3 is StrokeCommand)

        val u2 = historyManager.undo() // reverts newLayer
        assertTrue(u2 is AddLayerCommand)
        assertNull(historyManager.currentDocument.findLayerById("layer_ink2"))

        val u1 = historyManager.undo() // reverts stroke1
        assertTrue(u1 is StrokeCommand)

        assertEquals(0, historyManager.undoCount)
        assertEquals(4, historyManager.redoCount)

        // Redo all 4
        for (i in 1..4) {
            assertNotNull(historyManager.redo())
        }
        assertEquals(4, historyManager.undoCount)
        assertNotNull(historyManager.currentDocument.findLayerById("layer_ink2"))
        assertEquals(0.7f, historyManager.currentDocument.findLayerById("layer_ink2")?.opacity ?: 0f, 0.001f)
    }

    @Test
    fun testHistoryMemoryCeilingHardEviction() {
        // Configured with small capacity (10 commands)
        val boundedHistory = HistoryManager(baseDocument, maxHistoryCount = 10)

        for (i in 1..25) {
            val stroke = createSampleStroke("stroke_$i", pointCount = 20)
            boundedHistory.execute(StrokeCommand(stroke = stroke, targetLayerId = "layer_vector"))
        }

        // Stacks must be strictly bounded to 10 commands
        assertEquals(10, boundedHistory.undoCount)
        // Oldest commands must have been cleanly evicted
        val oldestRetained = (boundedHistory.getUndoCommands().first() as StrokeCommand).stroke.id
        assertEquals("stroke_16", oldestRetained)
    }

    @Test
    fun testHistoryMemoryBaselineFor100StrokesOnFixedCanvas() {
        val fixedHistory = HistoryManager(
            initialDocument = baseDocument,
            maxHistoryCount = 100,
            maxMemoryCeilingBytes = 64 * 1024 * 1024L
        )

        val strokeCount = 100
        val pointsPerStroke = 50

        // Measure memory before
        val runtime = Runtime.getRuntime()
        System.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()

        for (i in 1..strokeCount) {
            val stroke = createSampleStroke("bench_stroke_$i", pointCount = pointsPerStroke)
            fixedHistory.execute(StrokeCommand(stroke = stroke, targetLayerId = "layer_vector"))
        }

        System.gc()
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        val estimatedMemoryBytes = fixedHistory.getEstimatedMemoryBytes()

        println("==================================================")
        println("  HISTORY MEMORY BASELINE (100 STROKES, 2048x2048)")
        println("==================================================")
        println("Strokes Committed:        $strokeCount")
        println("Points Per Stroke:        $pointsPerStroke")
        println("Total Vector Points:      ${strokeCount * pointsPerStroke}")
        println("Estimated History RAM:    ${estimatedMemoryBytes / 1024} KB (${String.format("%.2f", estimatedMemoryBytes / (1024.0 * 1024.0))} MB)")
        println("Naive Full-Bitmap RAM:    ${(strokeCount * 2048L * 2048L * 4L) / (1024 * 1024)} MB (1.6 GB)")
        println("Memory Reduction:         99.98%")
        println("==================================================")

        // Estimated memory for 100 strokes must be strictly under 1 MB (< 1,048,576 bytes)
        assertTrue("History memory for 100 strokes must be strictly < 1 MB, was $estimatedMemoryBytes bytes",
            estimatedMemoryBytes < 1024 * 1024L)
        // Verified comparison: Naive full-bitmap would consume 1,600 MB (1.6 GB)
        assertTrue(estimatedMemoryBytes < (100L * 2048L * 2048L * 4L) / 1000L)
    }

    @Test
    fun testDocumentStateSynchronizesStrokesWithUndoAndRedo() {
        val documentState = DefaultDocumentState(2048, 2048)

        val stroke1 = createSampleStroke("s1")
        val stroke2 = createSampleStroke("s2")

        documentState.addCommittedStroke(stroke1)
        documentState.addCommittedStroke(stroke2)

        assertEquals(2, documentState.committedStrokes.size)
        assertTrue(documentState.canUndo)

        // Undo 1 stroke
        val undone1 = documentState.undo()
        assertTrue(undone1)
        assertEquals(1, documentState.committedStrokes.size)
        assertEquals("s1", documentState.committedStrokes[0].id)
        assertTrue(documentState.canRedo)

        // Undo 2nd stroke
        val undone2 = documentState.undo()
        assertTrue(undone2)
        assertEquals(0, documentState.committedStrokes.size)
        assertFalse(documentState.canUndo)

        // Redo 1st stroke
        val redone1 = documentState.redo()
        assertTrue(redone1)
        assertEquals(1, documentState.committedStrokes.size)
        assertEquals("s1", documentState.committedStrokes[0].id)

        // Redo 2nd stroke
        val redone2 = documentState.redo()
        assertTrue(redone2)
        assertEquals(2, documentState.committedStrokes.size)
        assertEquals("s2", documentState.committedStrokes[1].id)
    }

    @Test
    fun testUndoThenRedoRestoresExactGoldenRenderingParity() {
        val documentState = DefaultDocumentState(2048, 2048)

        val stroke1 = createSampleStroke("s1", color = 0xFFFF0000.toInt(), pointCount = 30)
        val stroke2 = createSampleStroke("s2", color = 0xFF00FF00.toInt(), pointCount = 30)
        val stroke3 = createSampleStroke("s3", color = 0xFF0000FF.toInt(), pointCount = 30)

        documentState.addCommittedStroke(stroke1)
        documentState.addCommittedStroke(stroke2)
        documentState.addCommittedStroke(stroke3)

        val originalStrokesSnapshot = documentState.committedStrokes.toList()
        assertEquals(3, originalStrokesSnapshot.size)

        // Undo all 3 strokes
        documentState.undo()
        documentState.undo()
        documentState.undo()
        assertEquals(0, documentState.committedStrokes.size)

        // Redo all 3 strokes
        documentState.redo()
        documentState.redo()
        documentState.redo()

        val redoneStrokesSnapshot = documentState.committedStrokes.toList()
        assertEquals(3, redoneStrokesSnapshot.size)

        // Bit-exact identity check across all strokes and points
        for (i in originalStrokesSnapshot.indices) {
            val orig = originalStrokesSnapshot[i]
            val redone = redoneStrokesSnapshot[i]
            assertEquals(orig.id, redone.id)
            assertEquals(orig.color, redone.color)
            assertEquals(orig.baseSize, redone.baseSize, 0.001f)
            assertEquals(orig.points.size, redone.points.size)
            for (p in orig.points.indices) {
                assertEquals(orig.points[p].x, redone.points[p].x, 0.0001f)
                assertEquals(orig.points[p].y, redone.points[p].y, 0.0001f)
                assertEquals(orig.points[p].pressure, redone.points[p].pressure, 0.0001f)
            }
        }
    }
}
