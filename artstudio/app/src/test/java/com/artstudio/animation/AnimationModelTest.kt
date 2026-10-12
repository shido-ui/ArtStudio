package com.artstudio.animation

import com.artstudio.document.io.DocumentConversionAuditor
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.AnimationTimelineConfig
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.history.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Verification test suite for Frame-by-Frame Animation Document Model and Timeline (Prompt 33).
 *
 * Covers:
 * 1. Frame order and sequential indexing
 * 2. Variable frame durations and total tick/time mapping
 * 3. Frame addition at start, middle, and end within limits
 * 4. Cel-reference duplication (Hold Cel) vs content cloning
 * 5. Frame deletion and minimum-1-frame limit invariant
 * 6. Frame reordering with preservation of cels and durations
 * 7. Explicit project limits enforcement (MAX_FRAMES, FPS, durations)
 * 8. Playback controller state transitions under LOOP, ONCE, and PING_PONG
 * 9. Native document persistence (.ffart JSON) round-trip with 100% parameter preservation
 * 10. Canonical 3-frame fixture sequential playback order
 * 11. Memory and storage metrics quantification (cel-referencing vs naive snapshot duplication)
 * 12. Transaction history reversibility (undo/redo)
 * 13. Conversion auditor lossy flattening disclosure
 */
class AnimationModelTest {

    // =========================================================================
    // 1. Frame Order & Sequential Indexing
    // =========================================================================

    @Test
    fun testFrameOrder_initialSequentialFrames() {
        val f1 = AnimationFrame(name = "Key 1", celLayerIds = listOf("cel_1"))
        val f2 = AnimationFrame(name = "Inbetween 1", celLayerIds = listOf("cel_2"))
        val f3 = AnimationFrame(name = "Key 2", celLayerIds = listOf("cel_3"))

        val timeline = AnimationTimeline(
            fps = 12,
            frames = listOf(f1, f2, f3),
            currentFrameIndex = 0
        )

        assertEquals(3, timeline.frames.size)
        assertEquals("Key 1", timeline.frames[0].name)
        assertEquals("Inbetween 1", timeline.frames[1].name)
        assertEquals("Key 2", timeline.frames[2].name)
        assertEquals("Key 1", timeline.currentFrame.name)

        val updated = timeline.withSelectedFrame(2)
        assertEquals(2, updated.currentFrameIndex)
        assertEquals("Key 2", updated.currentFrame.name)
    }

    // =========================================================================
    // 2. Variable Frame Durations & Tick Calculations
    // =========================================================================

    @Test
    fun testFrameDurations_variableDurationsAndTickCalculations() {
        // Frame 0: 1 tick, Frame 1: 2 ticks ("on twos"), Frame 2: 3 ticks ("on threes")
        val f0 = AnimationFrame(name = "F0", durationTicks = 1)
        val f1 = AnimationFrame(name = "F1", durationTicks = 2)
        val f2 = AnimationFrame(name = "F2", durationTicks = 3)

        val timeline = AnimationTimeline(
            fps = 12,
            frames = listOf(f0, f1, f2)
        )

        // Total duration = 1 + 2 + 3 = 6 ticks
        assertEquals(6, timeline.totalDurationTicks)
        // At 12 FPS, 6 ticks = (6 / 12) * 1000 = 500ms
        assertEquals(500L, timeline.totalDurationMs)

        // Tick offsets
        assertEquals(0, timeline.tickStartForFrame(0))
        assertEquals(1, timeline.tickStartForFrame(1))
        assertEquals(3, timeline.tickStartForFrame(2))

        // Tick to Frame index resolution:
        // Tick 0 -> Frame 0
        assertEquals(0, timeline.frameIndexAtTick(0))
        // Tick 1 -> Frame 1
        assertEquals(1, timeline.frameIndexAtTick(1))
        // Tick 2 -> Frame 1 (second tick of Frame 1)
        assertEquals(1, timeline.frameIndexAtTick(2))
        // Tick 3 -> Frame 2
        assertEquals(2, timeline.frameIndexAtTick(3))
        // Tick 4 -> Frame 2 (second tick of Frame 2)
        assertEquals(2, timeline.frameIndexAtTick(4))
        // Tick 5 -> Frame 2 (third tick of Frame 2)
        assertEquals(2, timeline.frameIndexAtTick(5))
    }

    // =========================================================================
    // 3. Add Frame at Various Positions
    // =========================================================================

    @Test
    fun testAddFrame_insertionAtVariousPositions() {
        val f0 = AnimationFrame(name = "A")
        val f1 = AnimationFrame(name = "B")
        var timeline = AnimationTimeline(frames = listOf(f0, f1), currentFrameIndex = 0)

        // Insert at index 1 (between A and B)
        timeline = timeline.withFrameAdded(insertIndex = 1, name = "Mid", durationTicks = 2)
        assertEquals(3, timeline.frames.size)
        assertEquals("A", timeline.frames[0].name)
        assertEquals("Mid", timeline.frames[1].name)
        assertEquals("B", timeline.frames[2].name)
        assertEquals(1, timeline.currentFrameIndex)

        // Insert at beginning (index 0)
        timeline = timeline.withFrameAdded(insertIndex = 0, name = "Start")
        assertEquals(4, timeline.frames.size)
        assertEquals("Start", timeline.frames[0].name)
        assertEquals("A", timeline.frames[1].name)
        assertEquals(0, timeline.currentFrameIndex)

        // Insert at end (index 4)
        timeline = timeline.withFrameAdded(insertIndex = 4, name = "End")
        assertEquals(5, timeline.frames.size)
        assertEquals("End", timeline.frames[4].name)
        assertEquals(4, timeline.currentFrameIndex)
    }

    // =========================================================================
    // 4. Duplicate Frame: Hold Cel vs Deep Copy
    // =========================================================================

    @Test
    fun testDuplicateFrame_holdCelVsCloneDrawing() {
        val f0 = AnimationFrame(name = "Drawing 1", celLayerIds = listOf("cel_layer_01"))
        val timeline = AnimationTimeline(frames = listOf(f0), currentFrameIndex = 0)

        // 1. Hold Cel (share layer ID reference without duplicating pixels)
        val heldTimeline = timeline.withFrameDuplicated(index = 0, newCelLayerIds = null)
        assertEquals(2, heldTimeline.frames.size)
        assertEquals(listOf("cel_layer_01"), heldTimeline.frames[0].celLayerIds)
        assertEquals(listOf("cel_layer_01"), heldTimeline.frames[1].celLayerIds)
        assertNotEquals("Each frame must have unique ID", heldTimeline.frames[0].id, heldTimeline.frames[1].id)

        // 2. Clone Drawing (unique newly cloned layer ID)
        val clonedTimeline = timeline.withFrameDuplicated(index = 0, newCelLayerIds = listOf("cel_layer_01_copy"))
        assertEquals(2, clonedTimeline.frames.size)
        assertEquals(listOf("cel_layer_01"), clonedTimeline.frames[0].celLayerIds)
        assertEquals(listOf("cel_layer_01_copy"), clonedTimeline.frames[1].celLayerIds)
    }

    // =========================================================================
    // 5. Delete Frame & Minimum Invariant
    // =========================================================================

    @Test
    fun testDeleteFrame_removesFrameAndMaintainsInvariant() {
        val f0 = AnimationFrame(name = "A")
        val f1 = AnimationFrame(name = "B")
        val f2 = AnimationFrame(name = "C")
        var timeline = AnimationTimeline(frames = listOf(f0, f1, f2), currentFrameIndex = 2)

        // Delete frame at index 1 ("B")
        timeline = timeline.withFrameDeleted(index = 1)
        assertEquals(2, timeline.frames.size)
        assertEquals("A", timeline.frames[0].name)
        assertEquals("C", timeline.frames[1].name)
        assertEquals(1, timeline.currentFrameIndex)

        // Delete frame at index 1 ("C")
        timeline = timeline.withFrameDeleted(index = 1)
        assertEquals(1, timeline.frames.size)
        assertEquals("A", timeline.frames[0].name)
        assertEquals(0, timeline.currentFrameIndex)

        // Deleting the last remaining frame must throw IllegalArgumentException
        assertThrows(IllegalArgumentException::class.java) {
            timeline.withFrameDeleted(index = 0)
        }
    }

    // =========================================================================
    // 6. Reorder Frame
    // =========================================================================

    @Test
    fun testReorderFrame_preservesAllFrameProperties() {
        val f0 = AnimationFrame(id = "id_0", name = "A", durationTicks = 1, celLayerIds = listOf("c0"))
        val f1 = AnimationFrame(id = "id_1", name = "B", durationTicks = 2, celLayerIds = listOf("c1"))
        val f2 = AnimationFrame(id = "id_2", name = "C", durationTicks = 3, celLayerIds = listOf("c2"))
        val timeline = AnimationTimeline(frames = listOf(f0, f1, f2), currentFrameIndex = 0)

        // Move C (index 2) to the front (index 0) -> [C, A, B]
        val reordered = timeline.withFrameReordered(fromIndex = 2, toIndex = 0)
        assertEquals(3, reordered.frames.size)
        assertEquals("C", reordered.frames[0].name)
        assertEquals("id_2", reordered.frames[0].id)
        assertEquals(3, reordered.frames[0].durationTicks)
        assertEquals(listOf("c2"), reordered.frames[0].celLayerIds)

        assertEquals("A", reordered.frames[1].name)
        assertEquals("id_0", reordered.frames[1].id)
        assertEquals(1, reordered.frames[1].durationTicks)

        assertEquals("B", reordered.frames[2].name)
        assertEquals("id_1", reordered.frames[2].id)
        assertEquals(2, reordered.frames[2].durationTicks)
    }

    // =========================================================================
    // 7. Explicit Project Limits
    // =========================================================================

    @Test
    fun testAnimationLimits_enforcesMaxFramesAndFps() {
        // Enforce MAX_FRAMES (240)
        val maxFramesList = (1..AnimationLimits.MAX_FRAMES).map {
            AnimationFrame(name = "F$it")
        }
        val fullTimeline = AnimationTimeline(frames = maxFramesList)
        assertEquals(240, fullTimeline.frames.size)

        // Adding beyond 240 must fail
        assertThrows(IllegalArgumentException::class.java) {
            fullTimeline.withFrameAdded()
        }

        // Duplicating beyond 240 must fail
        assertThrows(IllegalArgumentException::class.java) {
            fullTimeline.withFrameDuplicated()
        }

        // Enforce FPS range [1..60]
        assertThrows(IllegalArgumentException::class.java) {
            AnimationTimeline(fps = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnimationTimeline(fps = 61)
        }

        // Enforce Frame duration [1..120]
        assertThrows(IllegalArgumentException::class.java) {
            AnimationFrame(durationTicks = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnimationFrame(durationTicks = 121)
        }
    }

    // =========================================================================
    // 8. Playback Controller & Loop Modes
    // =========================================================================

    @Test
    fun testPlaybackController_loopModeAndPingPong() {
        // 3 frames: F0 (1 tick), F1 (2 ticks), F2 (1 tick) -> Total 4 ticks
        val f0 = AnimationFrame(name = "F0", durationTicks = 1)
        val f1 = AnimationFrame(name = "F1", durationTicks = 2)
        val f2 = AnimationFrame(name = "F2", durationTicks = 1)

        val timeline = AnimationTimeline(
            fps = 10, // 100ms per tick
            frames = listOf(f0, f1, f2),
            loopMode = LoopMode.LOOP
        )

        val controller = AnimationPlaybackController(timeline)
        controller.play()
        assertTrue(controller.isPlaying)

        // Advance 100ms -> Tick 1 -> Frame 1
        controller.advanceTime(100L)
        assertEquals(1, controller.currentFrameIndex)
        assertEquals(1, controller.currentTick)

        // Advance 100ms -> Tick 2 -> Frame 1 (second tick)
        controller.advanceTime(100L)
        assertEquals(1, controller.currentFrameIndex)
        assertEquals(2, controller.currentTick)

        // Advance 100ms -> Tick 3 -> Frame 2
        controller.advanceTime(100L)
        assertEquals(2, controller.currentFrameIndex)
        assertEquals(3, controller.currentTick)

        // Advance 100ms -> Tick 4 (wraps under LOOP) -> Tick 0 -> Frame 0
        controller.advanceTime(100L)
        assertEquals(0, controller.currentFrameIndex)
        assertEquals(0, controller.currentTick)

        // Test ONCE mode
        controller.timeline = timeline.copy(loopMode = LoopMode.ONCE)
        controller.stop()
        controller.play()
        // Advance 500ms (beyond 400ms total duration)
        controller.advanceTime(500L)
        assertEquals("ONCE must stop at last frame", 2, controller.currentFrameIndex)
        assertFalse("ONCE must stop playing when finished", controller.isPlaying)

        // Test PING_PONG mode
        controller.timeline = timeline.copy(loopMode = LoopMode.PING_PONG)
        controller.stop()
        controller.play()
        // Forward: 0ms -> Tick 0 (F0), 100ms -> Tick 1 (F1), 200ms -> Tick 2 (F1), 300ms -> Tick 3 (F2)
        controller.advanceTime(300L)
        assertEquals(2, controller.currentFrameIndex)
        // Reverse on Ping-Pong: 400ms -> Tick 2 (F1), 500ms -> Tick 1 (F1), 600ms -> Tick 0 (F0)
        controller.advanceTime(100L)
        assertEquals(1, controller.currentFrameIndex)
        assertEquals(-1, controller.currentState.direction)
    }

    // =========================================================================
    // 9. Native Document Persistence (.ffart JSON) Round-Trip
    // =========================================================================

    @Test
    fun testNativeDocumentPersistence_roundTripSavesAndRestoresAllProperties() {
        val f1 = AnimationFrame(id = "f_01", name = "Anticipation", durationTicks = 2, celLayerIds = listOf("l_ball_1"))
        val f2 = AnimationFrame(id = "f_02", name = "Action", durationTicks = 1, celLayerIds = listOf("l_ball_2"))
        val f3 = AnimationFrame(id = "f_03", name = "Recovery", durationTicks = 3, celLayerIds = listOf("l_ball_3"))

        val timeline = AnimationTimeline(
            fps = 24,
            frames = listOf(f1, f2, f3),
            currentFrameIndex = 1,
            staticLayerIds = listOf("l_bg_ground", "l_scenery"),
            onionSkin = OnionSkinSettings(
                enabled = true,
                framesBefore = 2,
                framesAfter = 1,
                tintBefore = 0xFFFF3300L,
                tintAfter = 0xFF0099FFL,
                opacityBefore = 0.4f,
                opacityAfter = 0.2f
            ),
            loopMode = LoopMode.PING_PONG
        )

        val doc = ArtDocument(
            canvas = CanvasConfig(512, 512),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "l_bg_ground", name = "Ground"),
                    LayerNode.RasterLayer(id = "l_scenery", name = "Trees"),
                    LayerNode.RasterLayer(id = "l_ball_1", name = "Ball 1"),
                    LayerNode.RasterLayer(id = "l_ball_2", name = "Ball 2"),
                    LayerNode.RasterLayer(id = "l_ball_3", name = "Ball 3")
                )
            ),
            animation = AnimationTimelineConfig.fromTimeline(timeline)
        )

        // Serialize to JSON
        val json = DocumentSerializer.toJsonString(doc)
        // Deserialize back
        val restoredDoc = DocumentSerializer.fromJsonString(json)

        assertNotNull(restoredDoc.animation)
        val restoredTimeline = restoredDoc.animation!!.toTimeline()

        assertEquals(24, restoredTimeline.fps)
        assertEquals(3, restoredTimeline.frames.size)
        assertEquals(1, restoredTimeline.currentFrameIndex)
        assertEquals(listOf("l_bg_ground", "l_scenery"), restoredTimeline.staticLayerIds)
        assertEquals(LoopMode.PING_PONG, restoredTimeline.loopMode)

        // Check Onion Skin
        assertTrue(restoredTimeline.onionSkin.enabled)
        assertEquals(2, restoredTimeline.onionSkin.framesBefore)
        assertEquals(1, restoredTimeline.onionSkin.framesAfter)
        assertEquals(0xFFFF3300L, restoredTimeline.onionSkin.tintBefore)
        assertEquals(0xFF0099FFL, restoredTimeline.onionSkin.tintAfter)

        // Check Individual Frames
        assertEquals("f_01", restoredTimeline.frames[0].id)
        assertEquals("Anticipation", restoredTimeline.frames[0].name)
        assertEquals(2, restoredTimeline.frames[0].durationTicks)
        assertEquals(listOf("l_ball_1"), restoredTimeline.frames[0].celLayerIds)

        assertEquals("f_02", restoredTimeline.frames[1].id)
        assertEquals("Action", restoredTimeline.frames[1].name)
        assertEquals(1, restoredTimeline.frames[1].durationTicks)
        assertEquals(listOf("l_ball_2"), restoredTimeline.frames[1].celLayerIds)

        assertEquals("f_03", restoredTimeline.frames[2].id)
        assertEquals("Recovery", restoredTimeline.frames[2].name)
        assertEquals(3, restoredTimeline.frames[2].durationTicks)
        assertEquals(listOf("l_ball_3"), restoredTimeline.frames[2].celLayerIds)
    }

    // =========================================================================
    // 10. Three-Frame Fixture Sequence Order
    // =========================================================================

    @Test
    fun testThreeFrameFixture_playsInCorrectOrder() {
        // Construct canonical 3-frame bouncing ball fixture
        val f1 = AnimationFrame(name = "Low", durationTicks = 1, celLayerIds = listOf("cel_1"))
        val f2 = AnimationFrame(name = "High", durationTicks = 2, celLayerIds = listOf("cel_2"))
        val f3 = AnimationFrame(name = "Landing", durationTicks = 1, celLayerIds = listOf("cel_3"))

        val timeline = AnimationTimeline(
            fps = 10,
            frames = listOf(f1, f2, f3),
            loopMode = LoopMode.LOOP
        )

        val controller = AnimationPlaybackController(timeline)
        controller.play()

        val observedSequence = mutableListOf<String>()
        val msPerTick = 100L

        // Step through 8 ticks
        for (step in 0 until 8) {
            observedSequence.add(timeline.frames[controller.currentFrameIndex].name)
            controller.advanceTime(msPerTick)
        }

        // Expected playback order:
        // Tick 0: Low (1 tick)
        // Tick 1: High (tick 1 of 2)
        // Tick 2: High (tick 2 of 2)
        // Tick 3: Landing (1 tick)
        // Tick 4: Low (loop back)
        // Tick 5: High
        // Tick 6: High
        // Tick 7: Landing
        val expected = listOf("Low", "High", "High", "Landing", "Low", "High", "High", "Landing")
        assertEquals(expected, observedSequence)
    }

    // =========================================================================
    // 11. Memory & Storage Metrics Recording
    // =========================================================================

    @Test
    fun testMemoryAndStorageMetrics_quantifiesCelReferencingSavings() {
        val width = 128
        val height = 128

        // Static background layer
        val bgLayer = LayerNode.RasterLayer(
            id = "layer_bg",
            name = "Background",
            width = width,
            height = height
        )

        // 3 animation cels
        val cel1 = LayerNode.RasterLayer(
            id = "cel_1",
            name = "Ball 1",
            width = width,
            height = height
        )
        val cel2 = LayerNode.RasterLayer(
            id = "cel_2",
            name = "Ball 2",
            width = width,
            height = height
        )
        val cel3 = LayerNode.RasterLayer(
            id = "cel_3",
            name = "Ball 3",
            width = width,
            height = height
        )

        // Create 6 frames where frames 3, 4, 5 re-use cels 1, 2, 3 (looping animation cycle)
        val frames = listOf(
            AnimationFrame(name = "F1", celLayerIds = listOf("cel_1")),
            AnimationFrame(name = "F2", celLayerIds = listOf("cel_2")),
            AnimationFrame(name = "F3", celLayerIds = listOf("cel_3")),
            AnimationFrame(name = "F4", celLayerIds = listOf("cel_1")), // Re-used cel
            AnimationFrame(name = "F5", celLayerIds = listOf("cel_2")), // Re-used cel
            AnimationFrame(name = "F6", celLayerIds = listOf("cel_3"))  // Re-used cel
        )

        val timeline = AnimationTimeline(
            fps = 12,
            frames = frames,
            staticLayerIds = listOf("layer_bg")
        )

        val doc = ArtDocument(
            canvas = CanvasConfig(width, height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(bgLayer, cel1, cel2, cel3)
            ),
            animation = AnimationTimelineConfig.fromTimeline(timeline)
        )

        val metrics = AnimationFrameRenderer.calculateMemoryMetrics(doc, timeline)

        assertEquals(128, metrics.canvasWidth)
        assertEquals(128, metrics.canvasHeight)
        assertEquals(6, metrics.frameCount)
        assertEquals(3, metrics.uniqueCelLayerCount)
        assertEquals(1, metrics.staticLayerCount)

        // Actual allocated layers = 1 static + 3 unique cels = 4 layers
        // Single canvas size = 128 * 128 * 4 = 65,536 bytes (64 KB)
        // celReferencedPixelBytes = 4 * 65,536 = 262,144 bytes (256 KB)
        assertEquals(262144L, metrics.celReferencedPixelBytes)

        // If naive full snapshots were stored for all 6 frames:
        // 6 * 65,536 = 393,216 bytes (384 KB)
        assertEquals(393216L, metrics.naiveSnapshotPixelBytes)

        // Memory saved = 393,216 - 262,144 = 131,072 bytes (128 KB saved, 33.33% reduction)
        assertEquals(131072L, metrics.memorySavedBytes)
        assertTrue("Savings ratio must be positive", metrics.savingsRatioPercent > 30f)
        assertTrue("Serialized JSON size must be recorded", metrics.serializedJsonBytes > 0L)
    }

    // =========================================================================
    // 12. History Commands Reversibility (Undo / Redo)
    // =========================================================================

    @Test
    fun testHistoryCommands_reversibilityOnTimelineOperations() {
        val initialFrame = AnimationFrame(name = "Initial", celLayerIds = listOf("layer_init"))
        val initialTimeline = AnimationTimeline(frames = listOf(initialFrame))
        val doc = ArtDocument(
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(LayerNode.RasterLayer(id = "layer_init", name = "Cel 1"))
            ),
            animation = AnimationTimelineConfig.fromTimeline(initialTimeline)
        )

        val historyManager = HistoryManager(doc)
        val timelineManager = AnimationTimelineManager(historyManager)

        // 1. Add Frame
        val addedFrame = timelineManager.addFrame(name = "Second Frame")
        assertEquals(2, timelineManager.timeline.frames.size)
        assertTrue(historyManager.canUndo)

        // Undo Add Frame
        historyManager.undo()
        assertEquals(1, timelineManager.timeline.frames.size)

        // Redo Add Frame
        historyManager.redo()
        assertEquals(2, timelineManager.timeline.frames.size)

        // 2. Duplicate Frame (Hold)
        val duplicated = timelineManager.duplicateFrame(index = 0, duplicateCels = false)
        assertEquals(3, timelineManager.timeline.frames.size)

        // Undo Duplicate
        historyManager.undo()
        assertEquals(2, timelineManager.timeline.frames.size)

        // 3. Set Frame Duration
        timelineManager.setFrameDuration(index = 0, durationTicks = 4)
        assertEquals(4, timelineManager.timeline.frames[0].durationTicks)

        // Undo Duration
        historyManager.undo()
        assertEquals(1, timelineManager.timeline.frames[0].durationTicks)

        // 4. Delete Frame
        timelineManager.deleteFrame(index = 1)
        assertEquals(1, timelineManager.timeline.frames.size)

        // Undo Delete
        historyManager.undo()
        assertEquals(2, timelineManager.timeline.frames.size)
    }

    // =========================================================================
    // 13. Conversion Auditor Disclosures
    // =========================================================================

    @Test
    fun testConversionAuditor_disclosesAnimationFlattening() {
        val f1 = AnimationFrame(name = "Frame 1")
        val f2 = AnimationFrame(name = "Frame 2")
        val timeline = AnimationTimeline(frames = listOf(f1, f2))
        val doc = ArtDocument(
            animation = AnimationTimelineConfig.fromTimeline(timeline)
        )

        val report = DocumentConversionAuditor.auditExport(doc, "PNG")
        assertTrue("Multi-frame export to static PNG must be flagged lossy", report.hasLossyConversions)

        val animWarning = report.warnings.find { it.feature == "ANIMATION_TIMELINE" }
        assertNotNull("Auditor must disclose that multi-frame animation is flattened to single image", animWarning)
        assertTrue(animWarning!!.actionTaken.contains("Flattened multi-frame animation"))
    }
}
