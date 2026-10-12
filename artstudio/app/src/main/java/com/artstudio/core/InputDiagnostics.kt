package com.artstudio.core

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic metrics snapshot for a single pointer sample.
 */
data class PointerDiagnosticInfo(
    val timestampNano: Long,
    val pointerId: Int,
    val toolType: ToolType,
    val rawScreenX: Float,
    val rawScreenY: Float,
    val documentX: Float,
    val documentY: Float,
    val rawPressure: Float,
    val effectivePressure: Float,
    val isPressureFallback: Boolean,
    val tiltDegrees: Float,
    val orientationDegrees: Float,
    val contactArea: Float,
    val isPalm: Boolean
)

/**
 * High-level diagnostic state exposed to developer diagnostic tools.
 */
data class InputDiagnosticsState(
    val activePointerCount: Int = 0,
    val arbiterState: TouchGestureState = TouchGestureState.IDLE,
    val interactionMode: TouchInteractionMode = TouchInteractionMode.DRAW_AND_NAVIGATE,
    val totalEventsCount: Long = 0L,
    val totalBatchedPointsCount: Long = 0L,
    val missedPointerEventsCount: Long = 0L,
    val lastEventLatencyMs: Float = 0.0f,
    val recentDiagnostics: List<PointerDiagnosticInfo> = emptyList()
)

/**
 * Thread-safe recorder and profiler for touch and stylus input diagnostics.
 */
class InputDiagnosticsRecorder(val maxBufferSize: Int = 50) {
    private val buffer = ConcurrentLinkedDeque<PointerDiagnosticInfo>()
    private val totalEvents = AtomicLong(0L)
    private val totalBatchedPoints = AtomicLong(0L)
    private val missedPointers = AtomicLong(0L)

    @Volatile
    private var lastLatencyMs: Float = 0.0f

    @Volatile
    private var lastArbiterState: TouchGestureState = TouchGestureState.IDLE

    @Volatile
    private var lastInteractionMode: TouchInteractionMode = TouchInteractionMode.DRAW_AND_NAVIGATE

    @Volatile
    private var activePointerCount: Int = 0

    fun recordSample(
        sample: PointerSample,
        documentX: Float,
        documentY: Float,
        resolvedPressure: ResolvedPressure,
        dynamics: StylusDynamicsResult,
        eventTimeNano: Long
    ) {
        val nowNano = System.nanoTime()
        val latencyMs = if (nowNano > eventTimeNano) {
            (nowNano - eventTimeNano) / 1_000_000f
        } else {
            0.0f
        }
        lastLatencyMs = latencyMs

        val info = PointerDiagnosticInfo(
            timestampNano = sample.timestampNano,
            pointerId = sample.pointerId,
            toolType = sample.toolType,
            rawScreenX = sample.screenX,
            rawScreenY = sample.screenY,
            documentX = documentX,
            documentY = documentY,
            rawPressure = sample.pressure,
            effectivePressure = resolvedPressure.effectivePressure,
            isPressureFallback = resolvedPressure.isFallbackActive,
            tiltDegrees = dynamics.tiltDegrees,
            orientationDegrees = dynamics.orientationDegrees,
            contactArea = dynamics.contactArea,
            isPalm = dynamics.isPalmDetected
        )

        buffer.addLast(info)
        while (buffer.size > maxBufferSize) {
            buffer.pollFirst()
        }
    }

    fun incrementEventCount() {
        totalEvents.incrementAndGet()
    }

    fun incrementBatchedPoints(count: Int) {
        totalBatchedPoints.addAndGet(count.toLong())
    }

    fun recordMissedPointer() {
        missedPointers.incrementAndGet()
    }

    fun updateArbiterState(state: TouchGestureState, mode: TouchInteractionMode, pointerCount: Int) {
        lastArbiterState = state
        lastInteractionMode = mode
        activePointerCount = pointerCount
    }

    fun captureSnapshot(): InputDiagnosticsState {
        return InputDiagnosticsState(
            activePointerCount = activePointerCount,
            arbiterState = lastArbiterState,
            interactionMode = lastInteractionMode,
            totalEventsCount = totalEvents.get(),
            totalBatchedPointsCount = totalBatchedPoints.get(),
            missedPointerEventsCount = missedPointers.get(),
            lastEventLatencyMs = lastLatencyMs,
            recentDiagnostics = buffer.toList()
        )
    }

    fun reset() {
        buffer.clear()
        totalEvents.set(0L)
        totalBatchedPoints.set(0L)
        missedPointers.set(0L)
        lastLatencyMs = 0f
        activePointerCount = 0
    }
}
