package com.artstudio.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.PI

/**
 * Verification test suite for Prompt 11:
 * - Accurate finger drawing & dynamic velocity-based pressure fallback
 * - Hardware stylus feature extraction (variable pressure, tilt, orientation)
 * - Multi-touch gesture arbitration & stroke priority lock
 * - Touch-only device complete lifecycle
 * - Pointer cancellation & app pause/resume safety
 * - Reversible coordinate normalization through ViewportTransform under pan, zoom, rotation, and flip
 * - Palm suppression & contact size filtering
 * - High-frequency historical batch unpacking
 */
class PointerInputAndStylusTest {

    private lateinit var viewport: ViewportTransform
    private lateinit var arbiter: TouchGestureArbiter
    private lateinit var pipeline: PointerInputPipeline
    private lateinit var listener: TestPointerListener

    @Before
    fun setUp() {
        viewport = ViewportTransform(
            viewportWidth = 1000f,
            viewportHeight = 1000f,
            documentWidth = 2048f,
            documentHeight = 2048f
        )
        arbiter = TouchGestureArbiter(TouchInteractionMode.DRAW_AND_NAVIGATE)
        listener = TestPointerListener()
        pipeline = PointerInputPipeline(
            viewport = viewport,
            listener = listener,
            arbiter = arbiter
        )
    }

    // --- Helper builders ---

    private fun sample(
        id: Int = 0,
        x: Float,
        y: Float,
        pressure: Float = 1.0f,
        tilt: Float = 0.0f,
        orientation: Float = 0.0f,
        contactArea: Float = 0.0f,
        toolType: ToolType = ToolType.FINGER,
        timeNano: Long = 1_000_000_000L
    ) = PointerSample(
        pointerId = id,
        toolType = toolType,
        screenX = x,
        screenY = y,
        pressure = pressure,
        tilt = tilt,
        orientation = orientation,
        contactArea = contactArea,
        timestampNano = timeNano
    )

    private fun down(vararg samples: PointerSample) = InputPointerEvent(
        action = InputAction.DOWN,
        actionPointerIndex = 0,
        pointers = samples.toList()
    )

    private fun move(vararg samples: PointerSample, batches: List<List<PointerSample>> = emptyList()) = InputPointerEvent(
        action = InputAction.MOVE,
        actionPointerIndex = 0,
        pointers = samples.toList(),
        historicalBatches = batches
    )

    private fun up(vararg samples: PointerSample) = InputPointerEvent(
        action = InputAction.UP,
        actionPointerIndex = 0,
        pointers = samples.toList()
    )

    private fun cancel(vararg samples: PointerSample) = InputPointerEvent(
        action = InputAction.CANCEL,
        actionPointerIndex = 0,
        pointers = samples.toList()
    )

    private fun pointerDown(actionIdx: Int, vararg samples: PointerSample) = InputPointerEvent(
        action = InputAction.POINTER_DOWN,
        actionPointerIndex = actionIdx,
        pointers = samples.toList()
    )

    private fun pointerUp(actionIdx: Int, vararg samples: PointerSample) = InputPointerEvent(
        action = InputAction.POINTER_UP,
        actionPointerIndex = actionIdx,
        pointers = samples.toList()
    )

    // --- 1. Pressure Fallback for Touch-Only Devices ---

    @Test
    fun testPressureFallbackForTouchOnlyDevices() {
        // Touch-only finger drawing reports flat pressure = 1.0f
        var time = 1_000_000_000L

        // Down
        pipeline.processPointerEvent(down(sample(x = 100f, y = 100f, pressure = 1.0f, toolType = ToolType.FINGER, timeNano = time)))
        val stroke = pipeline.activeStroke
        assertNotNull("Stroke must be active after DOWN", stroke)
        assertEquals(1, stroke!!.points.size)

        // Point 1 entry pressure
        val initialPressure = stroke.points[0].pressure
        assertTrue("Entry pressure should be within valid bounds", initialPressure in 0.01f..1.0f)

        // Slow move: 20px in 100ms (200 px/sec) -> thicker line
        time += 100_000_000L
        pipeline.processPointerEvent(move(sample(x = 120f, y = 100f, pressure = 1.0f, toolType = ToolType.FINGER, timeNano = time)))

        // Fast move: 300px in 50ms (6000 px/sec) -> thinner line
        time += 50_000_000L
        pipeline.processPointerEvent(move(sample(x = 420f, y = 100f, pressure = 1.0f, toolType = ToolType.FINGER, timeNano = time)))

        val slowPtPressure = stroke.points[1].pressure
        val fastPtPressure = stroke.points[2].pressure

        assertTrue(
            "Fast stroke ($fastPtPressure) should produce thinner pressure than slower stroke ($slowPtPressure)",
            fastPtPressure < slowPtPressure
        )

        val diag = pipeline.captureDiagnostics()
        assertTrue("Diagnostics must indicate pressure fallback is active", diag.recentDiagnostics.last().isPressureFallback)
    }

    // --- 2. Hardware Stylus Pressure Preservation ---

    @Test
    fun testHardwareStylusPressurePreservation() {
        var time = 1_000_000_000L

        // Stylus with varying hardware pressure
        pipeline.processPointerEvent(down(sample(x = 50f, y = 50f, pressure = 0.25f, toolType = ToolType.STYLUS, timeNano = time)))
        val stroke = pipeline.activeStroke
        assertNotNull(stroke)
        assertEquals(ToolType.STYLUS, stroke!!.toolType)

        time += 16_000_000L
        pipeline.processPointerEvent(move(sample(x = 80f, y = 80f, pressure = 0.65f, toolType = ToolType.STYLUS, timeNano = time)))

        time += 16_000_000L
        pipeline.processPointerEvent(move(sample(x = 120f, y = 120f, pressure = 0.92f, toolType = ToolType.STYLUS, timeNano = time)))

        pipeline.processPointerEvent(up(sample(x = 125f, y = 125f, pressure = 0.40f, toolType = ToolType.STYLUS, timeNano = time + 16_000_000L)))

        assertEquals(1, listener.endedStrokes.size)
        val completed = listener.endedStrokes[0]
        assertEquals(4, completed.points.size)

        assertEquals("Hardware pressure 0.25 must be preserved", 0.25f, completed.points[0].pressure, 0.01f)
        assertEquals("Hardware pressure 0.65 must be preserved", 0.65f, completed.points[1].pressure, 0.01f)
        assertEquals("Hardware pressure 0.92 must be preserved", 0.92f, completed.points[2].pressure, 0.01f)
        assertEquals("Hardware pressure 0.40 must be preserved", 0.40f, completed.points[3].pressure, 0.01f)

        val diag = pipeline.captureDiagnostics()
        assertFalse("Fallback should not be active for hardware stylus", diag.recentDiagnostics.last().isPressureFallback)
    }

    // --- 3. Pointer Cancellation Clean State ---

    @Test
    fun testPointerCancellationCleanState() {
        pipeline.processPointerEvent(down(sample(x = 100f, y = 100f)))
        val strokeId = pipeline.activeStroke?.id
        assertNotNull(strokeId)
        assertEquals(TouchGestureState.DRAWING, arbiter.state)

        pipeline.processPointerEvent(move(sample(x = 110f, y = 110f)))

        // System sends ACTION_CANCEL (e.g. system gesture, incoming notification shade)
        pipeline.processPointerEvent(cancel(sample(x = 115f, y = 115f)))

        assertNull("Active stroke must be null after cancellation", pipeline.activeStroke)
        assertEquals("Arbiter state must be reset to IDLE", TouchGestureState.IDLE, arbiter.state)
        assertEquals("Active drawing pointer ID must be reset to -1", -1, arbiter.activeDrawingPointerId)
        assertEquals(1, listener.cancelledStrokeIds.size)
        assertEquals(strokeId, listener.cancelledStrokeIds[0])
        assertEquals(0, listener.endedStrokes.size)

        // Subsequent stroke must start cleanly
        pipeline.processPointerEvent(down(sample(x = 200f, y = 200f)))
        assertNotNull("New stroke should be active after clean cancellation", pipeline.activeStroke)
        assertNotEquals(strokeId, pipeline.activeStroke?.id)
    }

    // --- 4. App Pause / Resume Safety ---

    @Test
    fun testAppPauseResumeCancellation() {
        pipeline.processPointerEvent(down(sample(x = 50f, y = 50f)))
        assertTrue("Drawing should be active", arbiter.isDrawingActive)

        // Activity onPause() triggered mid-stroke
        pipeline.pause()

        assertNull("Active stroke must be cleared on pause", pipeline.activeStroke)
        assertEquals("Arbiter must be IDLE on pause", TouchGestureState.IDLE, arbiter.state)
        assertEquals(1, listener.cancelledStrokeIds.size)

        // Activity onResume() triggered
        pipeline.resume()
        assertEquals(TouchGestureState.IDLE, arbiter.state)

        // New drawing stroke executes cleanly
        pipeline.processPointerEvent(down(sample(x = 300f, y = 300f)))
        pipeline.processPointerEvent(up(sample(x = 300f, y = 300f)))
        assertEquals(1, listener.endedStrokes.size)
    }

    // --- 5. Drawing While Zoomed, Rotated, and Flipped ---

    @Test
    fun testDrawingWhileZoomedAndRotated() {
        // Configure complex viewport transform: pan, zoom 2.5x, rotation 45 deg, horizontal flip
        viewport.reset()
        viewport.offsetPan(150f, 200f)
        viewport.scaleZoom(2.5f, 500f, 500f)
        viewport.rotateAround(45f, 500f, 500f)
        viewport.toggleFlipHorizontal()

        val screenX = 350f
        val screenY = 450f
        val expectedDocPt = viewport.screenToCanvas(screenX, screenY)

        pipeline.processPointerEvent(down(sample(x = screenX, y = screenY)))
        val stroke = pipeline.activeStroke
        assertNotNull(stroke)

        val pt = stroke!!.points[0]
        assertEquals("Document X must match inverse viewport transform", expectedDocPt.x, pt.x, 0.001f)
        assertEquals("Document Y must match inverse viewport transform", expectedDocPt.y, pt.y, 0.001f)

        // Second point
        val screenX2 = 380f
        val screenY2 = 470f
        val expectedDocPt2 = viewport.screenToCanvas(screenX2, screenY2)
        pipeline.processPointerEvent(move(sample(x = screenX2, y = screenY2)))

        val pt2 = stroke.points[1]
        assertEquals("Document X must match inverse transform", expectedDocPt2.x, pt2.x, 0.001f)
        assertEquals("Document Y must match inverse transform", expectedDocPt2.y, pt2.y, 0.001f)
    }

    // --- 6. Multi-Touch Gesture Arbitration: Stroke Priority Lock ---

    @Test
    fun testMultiTouchGestureArbitrationStrokePriority() {
        // Pointer 0 starts drawing
        val p0 = sample(id = 0, x = 100f, y = 100f)
        pipeline.processPointerEvent(down(p0))
        assertTrue("Drawing must be active", arbiter.isDrawingActive)
        assertEquals(0, arbiter.activeDrawingPointerId)

        // Secondary pointer 1 touches down (palm or accidental finger)
        val p1 = sample(id = 1, x = 400f, y = 400f)
        pipeline.processPointerEvent(pointerDown(1, p0, p1))

        // Both pointers move
        val p0Move = sample(id = 0, x = 120f, y = 110f)
        val p1Move = sample(id = 1, x = 450f, y = 450f)
        pipeline.processPointerEvent(move(p0Move, p1Move))

        // INVARIANT: Viewport navigation MUST be locked; no pan/zoom occurred
        assertEquals("Navigation must be locked while drawing", 0, listener.panZoomCount)
        assertNotNull("Active stroke must remain active", pipeline.activeStroke)
        assertEquals(2, pipeline.activeStroke!!.points.size)

        // Secondary pointer lifts
        pipeline.processPointerEvent(pointerUp(1, p0Move, p1Move))
        assertNotNull("Stroke continues when secondary pointer lifts", pipeline.activeStroke)

        // Drawing pointer lifts
        pipeline.processPointerEvent(up(p0Move))
        assertNull("Stroke finishes when drawing pointer lifts", pipeline.activeStroke)
        assertEquals(1, listener.endedStrokes.size)
        assertEquals(TouchGestureState.IDLE, arbiter.state)
    }

    // --- 7. Two-Finger Navigation When Idle ---

    @Test
    fun testTwoFingerNavigationWhenIdle() {
        assertEquals(TouchGestureState.IDLE, arbiter.state)

        // Two fingers touch down simultaneously
        val p0 = sample(id = 0, x = 200f, y = 200f)
        val p1 = sample(id = 1, x = 400f, y = 200f)
        pipeline.processPointerEvent(pointerDown(1, p0, p1))

        // Pointers pinch apart
        val p0Move = sample(id = 0, x = 150f, y = 200f)
        val p1Move = sample(id = 1, x = 450f, y = 200f)
        pipeline.processPointerEvent(move(p0Move, p1Move))

        assertTrue("Arbiter must enter VIEWPORT_NAVIGATION", arbiter.isNavigatingActive)
        assertNull("No drawing stroke should be created during navigation", pipeline.activeStroke)
        assertEquals(0, listener.startedStrokes.size)
        assertTrue("Viewport pan/zoom callback should be invoked", listener.panZoomCount > 0)

        // Both pointers lift
        pipeline.processPointerEvent(up(p0Move, p1Move))
        assertEquals(TouchGestureState.IDLE, arbiter.state)
    }

    // --- 8. Stylus Drawing with Palm Suppression ---

    @Test
    fun testStylusDrawingWithPalmSuppression() {
        // Stylus starts drawing
        val stylus = sample(id = 0, x = 200f, y = 300f, toolType = ToolType.STYLUS)
        pipeline.processPointerEvent(down(stylus))
        assertEquals(ToolType.STYLUS, arbiter.activeDrawingToolType)

        // Finger palm touch touches down while stylus is drawing
        val palm = sample(id = 1, x = 500f, y = 600f, toolType = ToolType.FINGER, contactArea = 0.6f)
        pipeline.processPointerEvent(pointerDown(1, stylus, palm))

        // Stylus moves
        val stylusMove = sample(id = 0, x = 220f, y = 320f, toolType = ToolType.STYLUS)
        pipeline.processPointerEvent(move(stylusMove, palm))

        assertNotNull("Stylus stroke continues undisturbed", pipeline.activeStroke)
        assertEquals(ToolType.STYLUS, pipeline.activeStroke!!.toolType)
        assertEquals(2, pipeline.activeStroke!!.points.size)
    }

    // --- 9. Palm Contact Size Detection ---

    @Test
    fun testPalmContactSizeDetection() {
        // Oversized contact area (wrist or resting palm)
        val palmTouch = sample(x = 300f, y = 400f, contactArea = 0.75f, toolType = ToolType.FINGER)
        val handled = pipeline.processPointerEvent(down(palmTouch))

        assertTrue("Palm contact should be handled/suppressed", handled)
        assertNull("Oversized palm contact must NOT create a drawing stroke", pipeline.activeStroke)
        assertEquals(0, listener.startedStrokes.size)
        assertEquals(TouchGestureState.IDLE, arbiter.state)
    }

    // --- 10. Switching Between Navigation and Drawing ---

    @Test
    fun testSwitchingBetweenNavigationAndDrawing() {
        // 1. Two-finger navigation
        val p0 = sample(id = 0, x = 200f, y = 200f)
        val p1 = sample(id = 1, x = 300f, y = 200f)
        pipeline.processPointerEvent(pointerDown(1, p0, p1))
        pipeline.processPointerEvent(move(sample(0, 210f, 200f), sample(1, 320f, 200f)))
        pipeline.processPointerEvent(up(sample(0, 210f, 200f), sample(1, 320f, 200f)))

        assertEquals(TouchGestureState.IDLE, arbiter.state)
        assertEquals(0, listener.endedStrokes.size)

        // 2. Immediate single-finger drawing
        pipeline.processPointerEvent(down(sample(id = 0, x = 500f, y = 500f)))
        pipeline.processPointerEvent(move(sample(id = 0, x = 520f, y = 530f)))
        pipeline.processPointerEvent(up(sample(id = 0, x = 540f, y = 550f)))

        assertEquals(1, listener.endedStrokes.size)
        assertEquals(TouchGestureState.IDLE, arbiter.state)
    }

    // --- 11. Historical Batch Point Processing ---

    @Test
    fun testHistoricalBatchPointProcessing() {
        val t0 = 1_000_000_000L
        pipeline.processPointerEvent(down(sample(x = 100f, y = 100f, timeNano = t0)))

        // Move event containing 3 historical batches + 1 current point
        val batch1 = listOf(sample(x = 110f, y = 105f, timeNano = t0 + 4_000_000L))
        val batch2 = listOf(sample(x = 120f, y = 112f, timeNano = t0 + 8_000_000L))
        val batch3 = listOf(sample(x = 130f, y = 120f, timeNano = t0 + 12_000_000L))
        val currentSample = sample(x = 140f, y = 130f, timeNano = t0 + 16_000_000L)

        pipeline.processPointerEvent(move(currentSample, batches = listOf(batch1, batch2, batch3)))

        val stroke = pipeline.activeStroke
        assertNotNull(stroke)
        // 1 initial down + 3 batches + 1 current = 5 points
        assertEquals(5, stroke!!.points.size)

        val diag = pipeline.captureDiagnostics()
        assertEquals("Historical batched points counter must be 3", 3L, diag.totalBatchedPointsCount)
    }

    // --- 12. Tilt and Orientation Extraction and Fallback ---

    @Test
    fun testTiltAndOrientationExtractionAndFallback() {
        // Stylus with 30° tilt (PI/6 rad) and 90° orientation (PI/2 rad)
        val tiltRad = (PI / 6.0).toFloat()
        val orientRad = (PI / 2.0).toFloat()
        pipeline.processPointerEvent(down(sample(x = 100f, y = 100f, tilt = tiltRad, orientation = orientRad, toolType = ToolType.STYLUS)))

        val stroke = pipeline.activeStroke
        assertNotNull(stroke)
        val pt = stroke!!.points[0]
        assertEquals("Tilt must match hardware report", tiltRad, pt.tilt, 0.001f)
        assertEquals("Orientation must match hardware report", orientRad, pt.orientation, 0.001f)

        pipeline.processPointerEvent(up(sample(x = 100f, y = 100f, tilt = tiltRad, orientation = orientRad, toolType = ToolType.STYLUS)))

        // Touch-only device defaults to perpendicular (0.0 tilt)
        pipeline.processPointerEvent(down(sample(x = 200f, y = 200f, tilt = 0f, orientation = 0f, toolType = ToolType.FINGER)))
        val fingerStroke = pipeline.activeStroke
        assertNotNull(fingerStroke)
        val fingerPt = fingerStroke!!.points[0]
        assertEquals("Touch-only tilt must default to 0.0", 0.0f, fingerPt.tilt, 0.001f)
        assertEquals("Touch-only orientation must default to 0.0", 0.0f, fingerPt.orientation, 0.001f)
    }

    // --- 13. Touch-Only Users Can Complete All Basic Drawing Actions ---

    @Test
    fun testTouchOnlyUsersCanCompleteAllBasicDrawingActions() {
        // Touch-only user draws 3 strokes with fingers:
        // Action 1: Tap dot
        pipeline.processPointerEvent(down(sample(x = 100f, y = 100f, toolType = ToolType.FINGER)))
        pipeline.processPointerEvent(up(sample(x = 100f, y = 100f, toolType = ToolType.FINGER)))
        assertEquals(1, listener.endedStrokes.size)

        // Action 2: Multi-point stroke with simulated pressure
        pipeline.processPointerEvent(down(sample(x = 200f, y = 200f, toolType = ToolType.FINGER)))
        for (i in 1..5) {
            pipeline.processPointerEvent(move(sample(x = 200f + i * 20f, y = 200f + i * 15f, toolType = ToolType.FINGER)))
        }
        pipeline.processPointerEvent(up(sample(x = 300f, y = 275f, toolType = ToolType.FINGER)))
        assertEquals(2, listener.endedStrokes.size)

        // Action 3: Stroke cancelled mid-drawing
        pipeline.processPointerEvent(down(sample(x = 400f, y = 400f, toolType = ToolType.FINGER)))
        pipeline.processPointerEvent(cancel(sample(x = 450f, y = 450f, toolType = ToolType.FINGER)))
        assertEquals(1, listener.cancelledStrokeIds.size)
        assertEquals(2, listener.endedStrokes.size)

        // Action 4: Pan/zoom canvas then draw another stroke
        pipeline.processPointerEvent(pointerDown(1, sample(0, 100f, 100f), sample(1, 300f, 100f)))
        pipeline.processPointerEvent(move(sample(0, 80f, 100f), sample(1, 320f, 100f)))
        pipeline.processPointerEvent(up(sample(0, 80f, 100f), sample(1, 320f, 100f)))

        pipeline.processPointerEvent(down(sample(x = 500f, y = 500f, toolType = ToolType.FINGER)))
        pipeline.processPointerEvent(up(sample(x = 500f, y = 500f, toolType = ToolType.FINGER)))
        assertEquals(3, listener.endedStrokes.size)
    }

    // --- 14. Missed Pointer Detection ---

    @Test
    fun testMissedPointerDetection() {
        arbiter.onStrokeStarted(pointerId = 99) // Non-existent pointer
        val event = move(sample(id = 1, x = 100f, y = 100f)) // Event contains pointer 1, not 99
        val handled = pipeline.processPointerEvent(event)

        assertFalse("Event referencing missing active pointer should not be handled", handled)
        val diag = pipeline.captureDiagnostics()
        assertEquals("Missed pointer event counter must be incremented", 1L, diag.missedPointerEventsCount)
    }

    // --- Test Listener Implementation ---

    class TestPointerListener : PointerInputListener {
        val startedStrokes = mutableListOf<BrushStroke>()
        val updatedStrokes = mutableListOf<BrushStroke>()
        val endedStrokes = mutableListOf<BrushStroke>()
        val cancelledStrokeIds = mutableListOf<String>()
        var panZoomCount = 0

        override fun onStrokeStart(stroke: BrushStroke) {
            startedStrokes.add(stroke)
        }

        override fun onStrokeUpdate(stroke: BrushStroke, newPoints: List<StrokePoint>) {
            updatedStrokes.add(stroke)
        }

        override fun onStrokeEnd(stroke: BrushStroke) {
            endedStrokes.add(stroke)
        }

        override fun onStrokeCancelled(strokeId: String) {
            cancelledStrokeIds.add(strokeId)
        }

        override fun onViewportPanZoom(dx: Float, dy: Float, scaleFactor: Float, focalX: Float, focalY: Float) {
            panZoomCount++
        }
    }
}
