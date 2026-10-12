package com.artstudio.core

import android.view.MotionEvent
import kotlin.math.hypot

interface PointerInputListener {
    fun onStrokeStart(stroke: BrushStroke)
    fun onStrokeUpdate(stroke: BrushStroke, newPoints: List<StrokePoint>)
    fun onStrokeEnd(stroke: BrushStroke)
    fun onStrokeCancelled(strokeId: String) {}
    fun onViewportPanZoom(dx: Float, dy: Float, scaleFactor: Float, focalX: Float, focalY: Float)
    fun onDiagnosticsUpdated(state: InputDiagnosticsState) {}
}

/**
 * Handles raw pointer input, multi-touch gesture arbitration,
 * hardware stylus dynamics (pressure, tilt, orientation), velocity-based pressure fallback,
 * palm rejection, and normalizes all coordinates through ViewportTransform to canonical document space.
 */
class PointerInputPipeline(
    val viewport: ViewportTransform,
    var activeColor: Int = 0xFF000000.toInt(),
    var activeSize: Float = 12f,
    var listener: PointerInputListener? = null,
    val arbiter: TouchGestureArbiter = TouchGestureArbiter(),
    val pressureResolver: InputPressureResolver = InputPressureResolver(),
    val diagnosticsRecorder: InputDiagnosticsRecorder = InputDiagnosticsRecorder()
) {
    var activeStroke: BrushStroke? = null
        private set

    var palmSuppressionEnabled: Boolean = true
    var activeBrushConfig: BrushConfig = BrushPresets.hardPen()
    var drawingAidsConfig: com.artstudio.guides.DrawingAidsConfig = com.artstudio.guides.DrawingAidsConfig()

    private val stabilizer = StrokeStabilizer()
    private var strokeIdCounter = 0L
    private var isMultiTouchGesture = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastDistance = 0f

    /**
     * Resolves and optionally snaps a screen coordinate to active drawing guides in document space.
     */
    fun resolveCanvasPoint(screenX: Float, screenY: Float): CanvasPoint {
        val rawPt = viewport.screenToCanvas(screenX, screenY)
        return if (drawingAidsConfig.snappingEnabled && drawingAidsConfig.guideType != com.artstudio.guides.GuideType.NONE) {
            val (sx, sy) = com.artstudio.guides.DrawingAidsEngine.snapPoint(rawPt.x, rawPt.y, drawingAidsConfig)
            CanvasPoint(sx, sy)
        } else {
            rawPt
        }
    }

    /**
     * Primary entry point for Android view onTouchEvent callbacks.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val inputEvent = MotionEventAdapter.fromMotionEvent(event)
        return processPointerEvent(inputEvent)
    }

    /**
     * Normalized testable input processing entry point.
     */
    fun processPointerEvent(event: InputPointerEvent): Boolean {
        diagnosticsRecorder.incrementEventCount()
        diagnosticsRecorder.updateArbiterState(arbiter.state, arbiter.interactionMode, event.pointerCount)

        // 1. Navigation arbitration
        if (arbiter.shouldProcessNavigation(event)) {
            if (!isMultiTouchGesture) {
                arbiter.onNavigationStarted()
            }
            handleMultiTouchPanZoom(event)
            if (event.action == InputAction.UP || event.action == InputAction.CANCEL) {
                isMultiTouchGesture = false
                arbiter.onNavigationFinished()
            }
            listener?.onDiagnosticsUpdated(diagnosticsRecorder.captureSnapshot())
            return true
        }

        // 2. Drawing arbitration
        if (arbiter.shouldProcessDrawing(event)) {
            val drawingPointerId = if (arbiter.isDrawingActive) {
                arbiter.activeDrawingPointerId
            } else {
                event.pointers.firstOrNull()?.pointerId ?: return false
            }

            val sample = event.findPointerById(drawingPointerId) ?: run {
                diagnosticsRecorder.recordMissedPointer()
                return false
            }

            // Palm rejection check for incoming touch
            if (palmSuppressionEnabled) {
                val isStylusActive = arbiter.isDrawingActive && arbiter.activeDrawingToolType == ToolType.STYLUS
                val dynamics = StylusDynamics.extractDynamics(
                    sample = sample,
                    isStylusDrawingActive = isStylusActive,
                    isCurrentActiveDrawingPointer = (sample.pointerId == arbiter.activeDrawingPointerId)
                )
                if (dynamics.isPalmDetected) {
                    diagnosticsRecorder.recordSample(
                        sample = sample,
                        documentX = 0f,
                        documentY = 0f,
                        resolvedPressure = ResolvedPressure(sample.pressure, false, pressureResolver.mode),
                        dynamics = dynamics,
                        eventTimeNano = event.eventTimeNano
                    )
                    return true // Suppressed as palm contact
                }
            }

            when (event.action) {
                InputAction.DOWN -> {
                    val canvasPt = resolveCanvasPoint(sample.screenX, sample.screenY)
                    pressureResolver.startStroke(sample)
                    val resolved = pressureResolver.resolvePressure(sample)
                    val dynamics = StylusDynamics.extractDynamics(
                        sample = sample,
                        isStylusDrawingActive = false,
                        isCurrentActiveDrawingPointer = true
                    )

                    val stroke = BrushStroke(
                        id = "stroke_${++strokeIdCounter}",
                        color = activeColor,
                        baseSize = activeSize,
                        toolType = sample.toolType,
                        config = activeBrushConfig.copy(
                            color = activeColor,
                            size = activeSize,
                            type = if (sample.toolType == ToolType.ERASER) BrushType.ERASER else activeBrushConfig.type
                        )
                    )

                    stabilizer.weight = activeBrushConfig.stabilizationWeight
                    stabilizer.reset()
                    val (stabX, stabY) = stabilizer.push(canvasPt.x, canvasPt.y)

                    val pt = StrokePoint(
                        x = stabX,
                        y = stabY,
                        pressure = resolved.effectivePressure,
                        tilt = dynamics.tiltRadians,
                        timestampNano = sample.timestampNano,
                        orientation = dynamics.orientationRadians
                    )
                    stroke.points.add(pt)
                    activeStroke = stroke
                    arbiter.onStrokeStarted(drawingPointerId, sample.toolType)

                    diagnosticsRecorder.recordSample(
                        sample = sample,
                        documentX = canvasPt.x,
                        documentY = canvasPt.y,
                        resolvedPressure = resolved,
                        dynamics = dynamics,
                        eventTimeNano = event.eventTimeNano
                    )

                    listener?.onStrokeStart(stroke)
                }

                InputAction.MOVE -> {
                    val stroke = activeStroke ?: return false
                    val newPoints = ArrayList<StrokePoint>()
                    val isStylusActive = arbiter.activeDrawingToolType == ToolType.STYLUS

                    // Process historical batch points for high-frequency digitizer fidelity
                    for (batch in event.historicalBatches) {
                        val histSample = batch.firstOrNull { it.pointerId == drawingPointerId } ?: continue
                        val histCanvasPt = resolveCanvasPoint(histSample.screenX, histSample.screenY)
                        val (hStabX, hStabY) = stabilizer.push(histCanvasPt.x, histCanvasPt.y)
                        val histResolved = pressureResolver.resolvePressure(histSample)
                        val histDynamics = StylusDynamics.extractDynamics(
                            sample = histSample,
                            isStylusDrawingActive = isStylusActive,
                            isCurrentActiveDrawingPointer = true
                        )

                        newPoints.add(
                            StrokePoint(
                                x = hStabX,
                                y = hStabY,
                                pressure = histResolved.effectivePressure,
                                tilt = histDynamics.tiltRadians,
                                timestampNano = histSample.timestampNano,
                                orientation = histDynamics.orientationRadians
                            )
                        )

                        diagnosticsRecorder.recordSample(
                            sample = histSample,
                            documentX = histCanvasPt.x,
                            documentY = histCanvasPt.y,
                            resolvedPressure = histResolved,
                            dynamics = histDynamics,
                            eventTimeNano = event.eventTimeNano
                        )
                    }

                    if (event.historicalBatches.isNotEmpty()) {
                        diagnosticsRecorder.incrementBatchedPoints(newPoints.size)
                    }

                    // Current sample point
                    val canvasPt = resolveCanvasPoint(sample.screenX, sample.screenY)
                    val (cStabX, cStabY) = stabilizer.push(canvasPt.x, canvasPt.y)
                    val resolved = pressureResolver.resolvePressure(sample)
                    val dynamics = StylusDynamics.extractDynamics(
                        sample = sample,
                        isStylusDrawingActive = isStylusActive,
                        isCurrentActiveDrawingPointer = true
                    )

                    newPoints.add(
                        StrokePoint(
                            x = cStabX,
                            y = cStabY,
                            pressure = resolved.effectivePressure,
                            tilt = dynamics.tiltRadians,
                            timestampNano = sample.timestampNano,
                            orientation = dynamics.orientationRadians
                        )
                    )

                    diagnosticsRecorder.recordSample(
                        sample = sample,
                        documentX = canvasPt.x,
                        documentY = canvasPt.y,
                        resolvedPressure = resolved,
                        dynamics = dynamics,
                        eventTimeNano = event.eventTimeNano
                    )

                    stroke.points.addAll(newPoints)
                    listener?.onStrokeUpdate(stroke, newPoints)
                }

                InputAction.UP -> {
                    val stroke = activeStroke ?: return false
                    val canvasPt = resolveCanvasPoint(sample.screenX, sample.screenY)
                    val resolved = pressureResolver.resolvePressure(sample)
                    val isStylusActive = arbiter.activeDrawingToolType == ToolType.STYLUS
                    val dynamics = StylusDynamics.extractDynamics(
                        sample = sample,
                        isStylusDrawingActive = isStylusActive,
                        isCurrentActiveDrawingPointer = true
                    )

                    stroke.points.add(
                        StrokePoint(
                            x = canvasPt.x,
                            y = canvasPt.y,
                            pressure = resolved.effectivePressure,
                            tilt = dynamics.tiltRadians,
                            timestampNano = sample.timestampNano,
                            orientation = dynamics.orientationRadians
                        )
                    )

                    diagnosticsRecorder.recordSample(
                        sample = sample,
                        documentX = canvasPt.x,
                        documentY = canvasPt.y,
                        resolvedPressure = resolved,
                        dynamics = dynamics,
                        eventTimeNano = event.eventTimeNano
                    )

                    val completedStroke = stroke.copy(isComplete = true)
                    activeStroke = null
                    arbiter.onStrokeFinished()
                    pressureResolver.reset()
                    listener?.onStrokeEnd(completedStroke)
                }

                InputAction.CANCEL -> {
                    val cancelledId = activeStroke?.id
                    activeStroke = null
                    arbiter.onCancelled()
                    pressureResolver.reset()
                    if (cancelledId != null) {
                        listener?.onStrokeCancelled(cancelledId)
                    }
                }

                InputAction.POINTER_DOWN -> {
                    // Secondary pointer touched down while drawing:
                    // Invariant: Palm/ignored touch; active stroke continues uninterrupted.
                }

                InputAction.POINTER_UP -> {
                    val actionPointer = event.getActionPointer()
                    if (actionPointer?.pointerId == drawingPointerId) {
                        // The primary drawing pointer was lifted
                        val stroke = activeStroke
                        if (stroke != null) {
                            val canvasPt = resolveCanvasPoint(sample.screenX, sample.screenY)
                            val resolved = pressureResolver.resolvePressure(sample)
                            val dynamics = StylusDynamics.extractDynamics(sample, true)

                            stroke.points.add(
                                StrokePoint(
                                    x = canvasPt.x,
                                    y = canvasPt.y,
                                    pressure = resolved.effectivePressure,
                                    tilt = dynamics.tiltRadians,
                                    timestampNano = sample.timestampNano,
                                    orientation = dynamics.orientationRadians
                                )
                            )

                            val completedStroke = stroke.copy(isComplete = true)
                            activeStroke = null
                            arbiter.onStrokeFinished()
                            pressureResolver.reset()
                            listener?.onStrokeEnd(completedStroke)
                        }
                    }
                    // Else: A secondary pointer was lifted; drawing continues.
                }
            }

            listener?.onDiagnosticsUpdated(diagnosticsRecorder.captureSnapshot())
            return true
        }

        listener?.onDiagnosticsUpdated(diagnosticsRecorder.captureSnapshot())
        return false
    }

    private fun handleMultiTouchPanZoom(event: InputPointerEvent) {
        val count = event.pointerCount
        if (count < 2) return

        val p0 = event.pointers[0]
        val p1 = event.pointers[1]

        val focusX = (p0.screenX + p1.screenX) / 2f
        val focusY = (p0.screenY + p1.screenY) / 2f

        val dx = p1.screenX - p0.screenX
        val dy = p1.screenY - p0.screenY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        when (event.action) {
            InputAction.POINTER_DOWN -> {
                isMultiTouchGesture = true
                lastFocusX = focusX
                lastFocusY = focusY
                lastDistance = distance
            }

            InputAction.MOVE -> {
                if (isMultiTouchGesture) {
                    val panDeltaX = focusX - lastFocusX
                    val panDeltaY = focusY - lastFocusY

                    val scaleFactor = if (lastDistance > 0f && distance > 0f) {
                        distance / lastDistance
                    } else {
                        1.0f
                    }

                    lastFocusX = focusX
                    lastFocusY = focusY
                    lastDistance = distance

                    // Apply pan and zoom to viewport
                    viewport.offsetPan(panDeltaX, panDeltaY)
                    if (scaleFactor != 1.0f) {
                        viewport.scaleZoom(scaleFactor, focusX, focusY)
                    }

                    listener?.onViewportPanZoom(panDeltaX, panDeltaY, scaleFactor, focusX, focusY)
                }
            }

            InputAction.POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    isMultiTouchGesture = false
                }
            }

            InputAction.UP, InputAction.CANCEL -> {
                isMultiTouchGesture = false
            }

            else -> {}
        }
    }

    /**
     * Safely cancels any active stroke in-flight and cleans up pipeline state.
     */
    fun cancelActiveStroke() {
        val strokeId = activeStroke?.id
        activeStroke = null
        arbiter.onCancelled()
        pressureResolver.reset()
        isMultiTouchGesture = false
        if (strokeId != null) {
            listener?.onStrokeCancelled(strokeId)
        }
    }

    /**
     * Called when the hosting activity or composable is paused.
     */
    fun pause() {
        cancelActiveStroke()
        arbiter.onPause()
    }

    /**
     * Called when the hosting activity or composable is resumed.
     */
    fun resume() {
        arbiter.reset()
        pressureResolver.reset()
        isMultiTouchGesture = false
    }

    fun captureDiagnostics(): InputDiagnosticsState {
        return diagnosticsRecorder.captureSnapshot()
    }
}
