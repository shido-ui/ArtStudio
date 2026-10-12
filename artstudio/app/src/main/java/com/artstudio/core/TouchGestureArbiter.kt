package com.artstudio.core

import android.view.MotionEvent

/**
 * Lifecycle states of touch input gesture recognition.
 */
enum class TouchGestureState {
    IDLE,
    DRAWING,
    VIEWPORT_NAVIGATION,
    PALM_SUPPRESSED
}

/**
 * Configurable gesture routing modes.
 */
enum class TouchInteractionMode {
    DRAW_AND_NAVIGATE,       // Stylus or single-finger draws; 2+ fingers navigate when idle
    STYLUS_ONLY_DRAWING,     // Only stylus draws; fingers navigate
    NAVIGATION_ONLY          // Viewport navigation mode only (hand tool); drawing disabled
}

/**
 * Arbitrates between high-frequency stylus/finger drawing and multi-touch viewport navigation.
 *
 * Prevents accidental pan/zoom when a drawing stroke is active by locking navigation
 * during in-flight strokes and filtering out secondary pointer/palm events.
 *
 * Invariant: Once a stroke begins (DRAWING state), multi-touch pointers cannot mutate
 * viewport pan, zoom, or rotation until the active stroke finishes or cancels cleanly.
 */
class TouchGestureArbiter(
    var interactionMode: TouchInteractionMode = TouchInteractionMode.DRAW_AND_NAVIGATE
) {
    var state: TouchGestureState = TouchGestureState.IDLE
        private set

    var activeDrawingPointerId: Int = -1
        private set

    var activeDrawingToolType: ToolType = ToolType.FINGER
        private set

    val isDrawingActive: Boolean
        get() = state == TouchGestureState.DRAWING

    val isNavigatingActive: Boolean
        get() = state == TouchGestureState.VIEWPORT_NAVIGATION

    val isPalmSuppressed: Boolean
        get() = state == TouchGestureState.PALM_SUPPRESSED

    /**
     * Determines whether an incoming normalized InputPointerEvent should be routed to drawing.
     */
    fun shouldProcessDrawing(event: InputPointerEvent): Boolean {
        if (interactionMode == TouchInteractionMode.NAVIGATION_ONLY) {
            return false
        }

        val primaryPointer = event.pointers.firstOrNull() ?: return false
        val isStylus = primaryPointer.toolType == ToolType.STYLUS || primaryPointer.toolType == ToolType.ERASER

        if (interactionMode == TouchInteractionMode.STYLUS_ONLY_DRAWING && !isStylus) {
            return false
        }

        when (state) {
            TouchGestureState.IDLE -> {
                // If 2 or more fingers touch down simultaneously while idle, prefer navigation
                if (event.pointerCount >= 2 && !isStylus) {
                    return false
                }
                return event.action == InputAction.DOWN
            }

            TouchGestureState.DRAWING -> {
                // When actively drawing, route event to drawing pipeline to process points or detect dropped pointers
                return true
            }

            TouchGestureState.VIEWPORT_NAVIGATION, TouchGestureState.PALM_SUPPRESSED -> {
                return false
            }
        }
    }

    /**
     * Determines whether an incoming normalized InputPointerEvent should be routed to navigation.
     */
    fun shouldProcessNavigation(event: InputPointerEvent): Boolean {
        if (interactionMode == TouchInteractionMode.NAVIGATION_ONLY) {
            return true
        }

        when (state) {
            TouchGestureState.DRAWING -> {
                // STRICT INVARIANT: When drawing is active, secondary pointers and multi-touch
                // are strictly arbitrated as palm contact / ignored touches; NAVIGATION IS LOCKED.
                return false
            }

            TouchGestureState.VIEWPORT_NAVIGATION -> {
                return true
            }

            TouchGestureState.IDLE -> {
                // Navigation begins when 2 or more pointers are present and idle
                return event.pointerCount >= 2
            }

            TouchGestureState.PALM_SUPPRESSED -> {
                return false
            }
        }
    }

    /**
     * Legacy MotionEvent overload for shouldProcessDrawing.
     */
    fun shouldProcessDrawing(event: MotionEvent): Boolean {
        return shouldProcessDrawing(MotionEventAdapter.fromMotionEvent(event))
    }

    /**
     * Legacy MotionEvent overload for shouldProcessNavigation.
     */
    fun shouldProcessNavigation(event: MotionEvent): Boolean {
        return shouldProcessNavigation(MotionEventAdapter.fromMotionEvent(event))
    }

    fun onStrokeStarted(pointerId: Int, toolType: ToolType = ToolType.FINGER) {
        state = TouchGestureState.DRAWING
        activeDrawingPointerId = pointerId
        activeDrawingToolType = toolType
    }

    fun onStrokeFinished() {
        state = TouchGestureState.IDLE
        activeDrawingPointerId = -1
        activeDrawingToolType = ToolType.FINGER
    }

    fun onNavigationStarted() {
        state = TouchGestureState.VIEWPORT_NAVIGATION
    }

    fun onNavigationFinished() {
        state = TouchGestureState.IDLE
    }

    fun onCancelled() {
        state = TouchGestureState.IDLE
        activeDrawingPointerId = -1
        activeDrawingToolType = ToolType.FINGER
    }

    fun onPause() {
        state = TouchGestureState.IDLE
        activeDrawingPointerId = -1
        activeDrawingToolType = ToolType.FINGER
    }

    fun reset() {
        state = TouchGestureState.IDLE
        activeDrawingPointerId = -1
        activeDrawingToolType = ToolType.FINGER
    }
}
