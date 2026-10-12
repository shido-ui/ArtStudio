package com.artstudio.autodraw.playback

import com.artstudio.autodraw.schedule.*
import com.artstudio.cv.CvPoint
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for controllable visual drawing playback on canvas.
 *
 * Verifies play/pause/resume, restart, speed multipliers, step forward/back,
 * skip/replay region, layer progress tracking, checkpoint save/restore,
 * cancellation isolation invariant, truthful time estimation, and
 * deterministic fixture replay matching direct rendering.
 */
class DrawingPlaybackTest {

    private lateinit var sampleSchedule: DrawingSchedule
    private lateinit var renderer: DrawingPlaybackRenderer
    private lateinit var controller: DrawingPlaybackController

    private fun createEntry(
        id: String,
        layerId: String = "layer_1",
        regionId: String? = "region_A",
        seq: Int,
        start: CvPoint,
        end: CvPoint,
        waypoints: List<CvPoint> = emptyList(),
        length: Float = 100f,
        durationMs: Long = 100L,
        delayMs: Long = 20L,
        isContour: Boolean = false,
        role: StrokeSemanticRole = StrokeSemanticRole.OUTLINE
    ): DrawingScheduleEntry {
        return DrawingScheduleEntry(
            id = id,
            pathId = "path_$id",
            layerId = layerId,
            regionId = regionId,
            sequenceIndex = seq,
            direction = StrokeDirection.FORWARD,
            brushParams = ScheduledBrushParams(sizePx = 4.0f, colorArgb = 0xFF000000L),
            timing = ScheduleTiming(
                estimatedDurationMs = durationMs,
                delayBeforeMs = delayMs,
                startTimestampMs = seq * 120L
            ),
            startPoint = start,
            endPoint = end,
            waypoints = if (waypoints.isNotEmpty()) waypoints else listOf(start, end),
            pathLengthPx = length,
            isContour = isContour,
            semanticRole = role
        )
    }

    @Before
    fun setUp() {
        val entries = listOf(
            createEntry("e1", "layer_bg", "region_A", 0, CvPoint(10f, 10f), CvPoint(50f, 10f), isContour = true, role = StrokeSemanticRole.OUTLINE),
            createEntry("e2", "layer_bg", "region_A", 1, CvPoint(50f, 10f), CvPoint(50f, 50f), isContour = false, role = StrokeSemanticRole.INTERNAL_DETAIL),
            createEntry("e3", "layer_fg", "region_A", 2, CvPoint(50f, 50f), CvPoint(10f, 50f), isContour = true, role = StrokeSemanticRole.OUTLINE),
            createEntry("e4", "layer_fg", "region_B", 3, CvPoint(10f, 50f), CvPoint(10f, 10f), isContour = false, role = StrokeSemanticRole.HATCHING),
            createEntry("e5", "layer_fg", "region_B", 4, CvPoint(20f, 20f), CvPoint(40f, 40f), isContour = false, role = StrokeSemanticRole.HATCHING)
        )
        sampleSchedule = DrawingSchedule(
            entries = entries,
            config = DrawingScheduleConfig(),
            totalDurationMs = 600L,
            totalPenTravelPx = 100f,
            totalDrawingLengthPx = 250f,
            scheduleHash = DrawingSchedule.computeScheduleHash(entries)
        )
        renderer = DrawingPlaybackRenderer(width = 200, height = 200)
        controller = DrawingPlaybackController(sampleSchedule, renderer)
    }

    // -------------------------------------------------------------------------
    // 1. Play, Pause, Resume Transitions
    // -------------------------------------------------------------------------
    @Test
    fun testPlayPauseResume_transitionsStateCorrectly() {
        assertEquals(PlaybackStateEnum.IDLE, controller.currentState)

        controller.play()
        assertEquals(PlaybackStateEnum.PLAYING, controller.currentState)

        controller.pause()
        assertEquals(PlaybackStateEnum.PAUSED, controller.currentState)

        controller.resume()
        assertEquals(PlaybackStateEnum.PLAYING, controller.currentState)
    }

    // -------------------------------------------------------------------------
    // 2. Restart Resets Stroke Index & Discards Transient Canvas
    // -------------------------------------------------------------------------
    @Test
    fun testRestart_resetsToStrokeZeroAndClearsTransient() {
        controller.play()
        controller.advanceTime(250L) // Progress through first two strokes
        assertTrue(controller.getProgressInfo().currentStrokeIndex > 0)

        controller.restart()
        val info = controller.getProgressInfo()
        assertEquals(PlaybackStateEnum.IDLE, info.state)
        assertEquals(0, info.currentStrokeIndex)
        assertEquals(0f, info.overallProgressFraction, 0.001f)
        assertEquals(PenTipState.IDLE, info.currentStroke?.penState)
    }

    // -------------------------------------------------------------------------
    // 3. Variable Speed Regulation
    // -------------------------------------------------------------------------
    @Test
    fun testSpeedMultipliers_scalesTimelineDeltaProportionally() {
        // At 1.0x speed, 50ms advances by 50ms
        controller.setSpeed(1.0f)
        controller.play()
        controller.advanceTime(60L) // 20ms transit + 40ms drawing (40% of 100ms duration)
        val progress1x = controller.getProgressInfo().currentStroke?.strokeProgress ?: 0f

        // Restart and test at 2.0x speed
        controller.restart()
        controller.setSpeed(2.0f)
        controller.play()
        controller.advanceTime(60L) // 60ms * 2 = 120ms (20ms transit + 100ms drawing = completes stroke 0 and starts stroke 1)
        val info2x = controller.getProgressInfo()

        assertTrue(
            "2.0x speed must complete first stroke faster than 1.0x",
            info2x.currentStrokeIndex > 0 || (info2x.currentStroke?.strokeProgress ?: 0f) > progress1x
        )
    }

    // -------------------------------------------------------------------------
    // 4. Step Forward and Step Backward
    // -------------------------------------------------------------------------
    @Test
    fun testStepForwardAndStepBack_strokeNavigation() {
        assertEquals(0, controller.getProgressInfo().currentStrokeIndex)

        controller.stepForward(2)
        assertEquals(2, controller.getProgressInfo().currentStrokeIndex)

        controller.stepForward(1)
        assertEquals(3, controller.getProgressInfo().currentStrokeIndex)

        controller.stepBack(2)
        assertEquals(1, controller.getProgressInfo().currentStrokeIndex)

        controller.stepBack(5) // Clamp to 0
        assertEquals(0, controller.getProgressInfo().currentStrokeIndex)
    }

    // -------------------------------------------------------------------------
    // 5. Skip Region
    // -------------------------------------------------------------------------
    @Test
    fun testSkipRegion_skipsRemainingStrokesOfCurrentRegion() {
        // Strokes 0, 1, 2 belong to region_A; strokes 3, 4 belong to region_B
        assertEquals(0, controller.getProgressInfo().currentStrokeIndex)
        assertEquals("region_A", controller.currentStroke?.regionId)

        controller.skipRegion()
        // Should skip all strokes in region_A and land at first stroke of region_B (index 3)
        assertEquals(3, controller.getProgressInfo().currentStrokeIndex)
        assertEquals("region_B", controller.currentStroke?.regionId)
    }

    // -------------------------------------------------------------------------
    // 6. Replay Region
    // -------------------------------------------------------------------------
    @Test
    fun testReplayRegion_rewindsToRegionStart() {
        // Advance to stroke 2 in region_A
        controller.stepForward(2)
        assertEquals(2, controller.getProgressInfo().currentStrokeIndex)
        assertEquals("region_A", controller.currentStroke?.regionId)

        controller.replayRegion("region_A")
        // Rewinds to stroke 0 (first stroke of region_A)
        assertEquals(0, controller.getProgressInfo().currentStrokeIndex)
    }

    // -------------------------------------------------------------------------
    // 7. Layer Progress Telemetry
    // -------------------------------------------------------------------------
    @Test
    fun testLayerProgressTracking_reportsAccurateStrokesAndPercentages() {
        // layer_bg has 2 strokes (e1, e2); layer_fg has 3 strokes (e3, e4, e5)
        val initialInfo = controller.getProgressInfo()
        assertEquals(2, initialInfo.layerProgressList.size)

        val bgLayer = initialInfo.layerProgressList.find { it.layerId == "layer_bg" }!!
        val fgLayer = initialInfo.layerProgressList.find { it.layerId == "layer_fg" }!!
        assertEquals(2, bgLayer.totalStrokes)
        assertEquals(0, bgLayer.strokesCompleted)
        assertEquals(3, fgLayer.totalStrokes)
        assertEquals(0, fgLayer.strokesCompleted)

        controller.stepForward(2) // Complete layer_bg
        val afterInfo = controller.getProgressInfo()
        val bgAfter = afterInfo.layerProgressList.find { it.layerId == "layer_bg" }!!
        assertEquals(2, bgAfter.strokesCompleted)
        assertTrue(bgAfter.isCompleted)
    }

    // -------------------------------------------------------------------------
    // 8. Checkpoint Creation and Restore
    // -------------------------------------------------------------------------
    @Test
    fun testCheckpointCreationAndRestore_preservesExactState() {
        controller.stepForward(2) // Advance to stroke 2
        val cp = controller.createCheckpoint("Midpoint Checkpoint")
        assertEquals(2, cp.strokeIndex)

        // Advance further
        controller.stepForward(2)
        assertEquals(4, controller.getProgressInfo().currentStrokeIndex)

        // Restore checkpoint
        controller.restoreCheckpoint(cp)
        assertEquals(2, controller.getProgressInfo().currentStrokeIndex)
    }

    // -------------------------------------------------------------------------
    // 9. Cancellation Safety Invariant (Committed Artwork Not Erased)
    // -------------------------------------------------------------------------
    @Test
    fun testCancellationSafety_doesNotEraseCommittedArtwork() {
        // 1. Advance and create a checkpoint (commits artwork to committed buffer)
        controller.stepForward(2)
        val cp = controller.createCheckpoint("Safe Base")
        val committedPixelsBefore = renderer.copyCommittedPixels()

        // 2. Play 2 more transient strokes
        controller.stepForward(2)
        assertEquals(4, controller.getProgressInfo().currentStrokeIndex)

        // 3. Cancel playback midway
        controller.cancelPlayback()
        assertEquals(PlaybackStateEnum.CANCELLED, controller.currentState)

        // INVARIANT CHECK: Committed artwork pixels must match checkpoint bit-for-bit
        val committedPixelsAfter = renderer.copyCommittedPixels()
        assertArrayEquals(
            "Cancelling playback must strictly preserve committed artwork",
            committedPixelsBefore,
            committedPixelsAfter
        )
    }

    // -------------------------------------------------------------------------
    // 10. Checkpoint Persistence Metadata
    // -------------------------------------------------------------------------
    @Test
    fun testCheckpointPersistenceMetadata_validatesAttributes() {
        controller.stepForward(1)
        val cp = controller.createCheckpoint("Test CP")

        assertEquals(1, cp.strokeIndex)
        assertEquals(sampleSchedule.scheduleHash, cp.scheduleHash)
        assertTrue(cp.createdAt > 0L)
        assertEquals("layer_bg", cp.activeLayerId)
    }

    // -------------------------------------------------------------------------
    // 11. Playback Modes Reordering
    // -------------------------------------------------------------------------
    @Test
    fun testPlaybackModeSwitching_reordersSequenceCorrectly() {
        // Line-Art First: Outlines first, details second
        controller.setPlaybackMode(PlaybackMode.LINE_ART_FIRST)
        val seqLineArt = DrawingPlaybackController.orderScheduleEntries(sampleSchedule.entries, PlaybackMode.LINE_ART_FIRST)
        assertTrue(seqLineArt[0].isContour || seqLineArt[0].semanticRole == StrokeSemanticRole.OUTLINE)

        // Layer-By-Layer: layer_bg before layer_fg
        controller.setPlaybackMode(PlaybackMode.LAYER_BY_LAYER)
        val seqLayers = DrawingPlaybackController.orderScheduleEntries(sampleSchedule.entries, PlaybackMode.LAYER_BY_LAYER)
        val lastBgIdx = seqLayers.indexOfLast { it.layerId == "layer_bg" }
        val firstFgIdx = seqLayers.indexOfFirst { it.layerId == "layer_fg" }
        assertTrue("All layer_bg strokes must precede layer_fg", lastBgIdx < firstFgIdx)

        // Interleaved: region_A grouped together before region_B
        controller.setPlaybackMode(PlaybackMode.DRAW_COLOUR_INTERLEAVED)
        val seqInterleaved = DrawingPlaybackController.orderScheduleEntries(sampleSchedule.entries, PlaybackMode.DRAW_COLOUR_INTERLEAVED)
        val lastRegionA = seqInterleaved.indexOfLast { it.regionId == "region_A" }
        val firstRegionB = seqInterleaved.indexOfFirst { it.regionId == "region_B" }
        assertTrue("region_A strokes must precede region_B", lastRegionA < firstRegionB)
    }

    // -------------------------------------------------------------------------
    // 12. Truthful Time Estimation (Only When Derived from Measured Progress)
    // -------------------------------------------------------------------------
    @Test
    fun testTruthfulTimeEstimation_exposesEstimateOnlyWhenDerivedFromMeasuredProgress() {
        // Initially or with < 3 strokes, estimatedRemainingTimeMs must be null!
        val initialInfo = controller.getProgressInfo()
        assertFalse(initialInfo.hasMeasuredProgress)
        assertNull("Estimated remaining time must be null when unmeasured", initialInfo.estimatedRemainingTimeMs)

        // Advance through >= 3 strokes with active drawing time
        controller.play()
        controller.advanceTime(600L) // Enough time to complete >= 3 strokes
        val infoAfter = controller.getProgressInfo()

        if (infoAfter.hasMeasuredProgress) {
            assertNotNull("Estimated remaining time must be populated when measured", infoAfter.estimatedRemainingTimeMs)
            assertTrue(infoAfter.estimatedRemainingTimeMs!! >= 0L)
        }
    }

    // -------------------------------------------------------------------------
    // 13. Deterministic Fixture Replay Matches Direct Rendering
    // -------------------------------------------------------------------------
    @Test
    fun testDeterministicFixtureReplay_matchesDirectRenderingGolden() {
        // Direct one-pass rendering reference
        val directGolden = renderer.directRenderSchedule(sampleSchedule)

        // Replay all strokes from stroke 0 to completion using advanceTime
        controller.restart()
        controller.setSpeed(100.0f) // Fast replay
        controller.play()

        // Advance until completed
        var safetyCounter = 0
        while (controller.currentState != PlaybackStateEnum.COMPLETED && safetyCounter < 500) {
            controller.advanceTime(50L)
            safetyCounter++
        }
        assertEquals(PlaybackStateEnum.COMPLETED, controller.currentState)

        val replayedPixels = renderer.copyDisplayPixels()
        assertArrayEquals(
            "Deterministic fixture replay must bit-exactly match direct rendering golden",
            directGolden,
            replayedPixels
        )
    }

    // -------------------------------------------------------------------------
    // 14. Frame Pacing and Memory Metrics Telemetry
    // -------------------------------------------------------------------------
    @Test
    fun testFramePacingAndMemoryMetrics_recordedAtMultipleSpeeds() {
        // Test at 1.0x speed
        controller.restart()
        controller.setSpeed(1.0f)
        controller.play()
        for (i in 1..10) {
            controller.advanceTime(16L) // 16ms target frames
        }
        val metrics1x = controller.getFramePacingMetrics()
        assertEquals(1.0f, metrics1x.speedMultiplier, 0.01f)
        assertTrue(metrics1x.totalFrames >= 10)
        assertTrue(metrics1x.peakMemoryBytes > 0L)

        // Test at 4.0x speed
        controller.restart()
        controller.setSpeed(4.0f)
        controller.play()
        for (i in 1..10) {
            controller.advanceTime(16L)
        }
        val metrics4x = controller.getFramePacingMetrics()
        assertEquals(4.0f, metrics4x.speedMultiplier, 0.01f)
        assertTrue(metrics4x.totalFrames >= 10)
    }
}
