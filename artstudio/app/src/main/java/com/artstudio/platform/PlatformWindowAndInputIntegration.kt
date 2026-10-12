package com.artstudio.platform

import android.graphics.Rect
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/**
 * Handles platform windowing, display cutouts, edge-to-edge system insets,
 * stylus unbuffered dispatch, and API 29+ system-gesture exclusion rects along canvas edges.
 */
object PlatformWindowAndInputIntegration {

    /**
     * Initializes edge-to-edge and short-edges display cutout handling on the activity window.
     */
    fun setupEdgeToEdgeWindow(activity: ComponentActivity) {
        activity.enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val window: Window = activity.window
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }
    }

    /**
     * Updates system gesture exclusion rects along the left and right borders of the canvas
     * on Android 10+ (API 29+), preventing accidental triggering of the system back gesture
     * while drawing close to screen edges.
     *
     * Note: Android enforces a maximum vertical exclusion height of 200dp per edge.
     */
    fun updateSystemGestureExclusionRects(
        view: View,
        edgeWidthPx: Int = 48,
        exclusionHeightDp: Int = 200
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val width = view.width
            val height = view.height
            if (width <= 0 || height <= 0) return

            val density = view.resources.displayMetrics.density
            val maxExclusionHeightPx = (exclusionHeightDp * density).toInt().coerceAtMost(height)
            val top = (height - maxExclusionHeightPx) / 2
            val bottom = top + maxExclusionHeightPx

            val leftRect = Rect(0, top, edgeWidthPx.coerceAtMost(width / 4), bottom)
            val rightRect = Rect(width - edgeWidthPx.coerceAtMost(width / 4), top, width, bottom)

            view.systemGestureExclusionRects = listOf(leftRect, rightRect)
        }
    }

    /**
     * Requests unbuffered dispatch for stylus motion events to minimize input latency.
     */
    fun requestStylusUnbufferedDispatch(view: View, event: MotionEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val isStylus = (event.source and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS) ||
                    (event.pointerCount > 0 && (
                            event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                            event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                    ))
            if (isStylus) {
                view.requestUnbufferedDispatch(event)
            }
        }
    }

    /**
     * Detects whether an event represents a stylus hover event (proximity without contact).
     */
    fun isStylusHoverEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val isHover = action == MotionEvent.ACTION_HOVER_MOVE ||
                action == MotionEvent.ACTION_HOVER_ENTER ||
                action == MotionEvent.ACTION_HOVER_EXIT
        val isStylus = event.pointerCount > 0 &&
                (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                 event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)
        return isHover && isStylus
    }
}
