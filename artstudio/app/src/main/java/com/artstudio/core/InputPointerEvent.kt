package com.artstudio.core

import android.view.MotionEvent

/**
 * Normalized pointer input actions decoupled from Android framework constants.
 */
enum class InputAction {
    DOWN,
    MOVE,
    UP,
    CANCEL,
    POINTER_DOWN,
    POINTER_UP
}

/**
 * Point sample captured from hardware digitizer or touch surface.
 */
data class PointerSample(
    val pointerId: Int,
    val toolType: ToolType = ToolType.FINGER,
    val screenX: Float,
    val screenY: Float,
    val pressure: Float = 1.0f,
    val tilt: Float = 0.0f,
    val orientation: Float = 0.0f,
    val contactArea: Float = 0.0f,
    val timestampNano: Long = System.nanoTime()
)

/**
 * Normalized pointer event containing all active pointer samples, action pointer index,
 * and high-frequency historical batch samples.
 */
data class InputPointerEvent(
    val action: InputAction,
    val actionPointerIndex: Int,
    val pointers: List<PointerSample>,
    val historicalBatches: List<List<PointerSample>> = emptyList(),
    val eventTimeNano: Long = System.nanoTime()
) {
    val pointerCount: Int
        get() = pointers.size

    fun getPointer(index: Int): PointerSample? {
        return pointers.getOrNull(index)
    }

    fun findPointerById(pointerId: Int): PointerSample? {
        return pointers.firstOrNull { it.pointerId == pointerId }
    }

    fun findPointerIndexById(pointerId: Int): Int {
        return pointers.indexOfFirst { it.pointerId == pointerId }
    }

    fun getActionPointer(): PointerSample? {
        return pointers.getOrNull(actionPointerIndex)
    }
}

/**
 * Adapts raw Android MotionEvents into normalized InputPointerEvent structures.
 */
object MotionEventAdapter {

    fun fromMotionEvent(event: MotionEvent): InputPointerEvent {
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> InputAction.DOWN
            MotionEvent.ACTION_MOVE -> InputAction.MOVE
            MotionEvent.ACTION_UP -> InputAction.UP
            MotionEvent.ACTION_CANCEL -> InputAction.CANCEL
            MotionEvent.ACTION_POINTER_DOWN -> InputAction.POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> InputAction.POINTER_UP
            else -> InputAction.MOVE
        }

        val pointerCount = event.pointerCount
        val pointers = ArrayList<PointerSample>(pointerCount)

        for (i in 0 until pointerCount) {
            val pointerId = event.getPointerId(i)
            val toolType = when (event.getToolType(i)) {
                MotionEvent.TOOL_TYPE_STYLUS -> ToolType.STYLUS
                MotionEvent.TOOL_TYPE_ERASER -> ToolType.ERASER
                MotionEvent.TOOL_TYPE_MOUSE -> ToolType.MOUSE
                else -> ToolType.FINGER
            }

            val x = event.getX(i)
            val y = event.getY(i)
            val rawPressure = safeGetPressure(event, i)
            val tilt = safeGetAxis(event, MotionEvent.AXIS_TILT, i)
            val orientation = safeGetAxis(event, MotionEvent.AXIS_ORIENTATION, i)
            val contactArea = safeGetContactArea(event, i)

            pointers.add(
                PointerSample(
                    pointerId = pointerId,
                    toolType = toolType,
                    screenX = x,
                    screenY = y,
                    pressure = rawPressure,
                    tilt = tilt,
                    orientation = orientation,
                    contactArea = contactArea,
                    timestampNano = event.eventTime * 1_000_000L
                )
            )
        }

        // Unpack historical batches if present
        val historySize = event.historySize
        val historicalBatches = ArrayList<List<PointerSample>>(historySize)

        for (h in 0 until historySize) {
            val batch = ArrayList<PointerSample>(pointerCount)
            val histTimeNano = try {
                event.getHistoricalEventTimeNanos(h)
            } catch (t: Throwable) {
                event.getHistoricalEventTime(h) * 1_000_000L
            }

            for (i in 0 until pointerCount) {
                val pointerId = event.getPointerId(i)
                val toolType = when (event.getToolType(i)) {
                    MotionEvent.TOOL_TYPE_STYLUS -> ToolType.STYLUS
                    MotionEvent.TOOL_TYPE_ERASER -> ToolType.ERASER
                    MotionEvent.TOOL_TYPE_MOUSE -> ToolType.MOUSE
                    else -> ToolType.FINGER
                }
                val hx = event.getHistoricalX(i, h)
                val hy = event.getHistoricalY(i, h)
                val hp = safeGetHistoricalPressure(event, i, h)
                val ht = safeGetHistoricalAxis(event, MotionEvent.AXIS_TILT, i, h)
                val ho = safeGetHistoricalAxis(event, MotionEvent.AXIS_ORIENTATION, i, h)
                val ha = safeGetHistoricalContactArea(event, i, h)

                batch.add(
                    PointerSample(
                        pointerId = pointerId,
                        toolType = toolType,
                        screenX = hx,
                        screenY = hy,
                        pressure = hp,
                        tilt = ht,
                        orientation = ho,
                        contactArea = ha,
                        timestampNano = histTimeNano
                    )
                )
            }
            historicalBatches.add(batch)
        }

        return InputPointerEvent(
            action = action,
            actionPointerIndex = event.actionIndex,
            pointers = pointers,
            historicalBatches = historicalBatches,
            eventTimeNano = event.eventTime * 1_000_000L
        )
    }

    private fun safeGetPressure(event: MotionEvent, pointerIndex: Int): Float {
        return try {
            val p = event.getPressure(pointerIndex)
            if (p.isNaN() || p.isInfinite()) 1.0f else p
        } catch (_: Throwable) {
            1.0f
        }
    }

    private fun safeGetAxis(event: MotionEvent, axis: Int, pointerIndex: Int): Float {
        return try {
            val a = event.getAxisValue(axis, pointerIndex)
            if (a.isNaN() || a.isInfinite()) 0.0f else a
        } catch (_: Throwable) {
            0.0f
        }
    }

    private fun safeGetContactArea(event: MotionEvent, pointerIndex: Int): Float {
        return try {
            val s = event.getSize(pointerIndex)
            if (s.isNaN() || s.isInfinite()) 0.0f else s
        } catch (_: Throwable) {
            0.0f
        }
    }

    private fun safeGetHistoricalPressure(event: MotionEvent, pointerIndex: Int, pos: Int): Float {
        return try {
            val p = event.getHistoricalPressure(pointerIndex, pos)
            if (p.isNaN() || p.isInfinite()) 1.0f else p
        } catch (_: Throwable) {
            1.0f
        }
    }

    private fun safeGetHistoricalAxis(event: MotionEvent, axis: Int, pointerIndex: Int, pos: Int): Float {
        return try {
            val a = event.getHistoricalAxisValue(axis, pointerIndex, pos)
            if (a.isNaN() || a.isInfinite()) 0.0f else a
        } catch (_: Throwable) {
            0.0f
        }
    }

    private fun safeGetHistoricalContactArea(event: MotionEvent, pointerIndex: Int, pos: Int): Float {
        return try {
            val s = event.getHistoricalSize(pointerIndex, pos)
            if (s.isNaN() || s.isInfinite()) 0.0f else s
        } catch (_: Throwable) {
            0.0f
        }
    }
}
