package com.artstudio.core

import android.graphics.Path
import android.graphics.RectF

enum class ToolType {
    STYLUS,
    FINGER,
    MOUSE,
    ERASER
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tilt: Float = 0.0f,
    val timestampNano: Long = System.nanoTime(),
    val orientation: Float = 0.0f
)

data class BrushStroke(
    val id: String,
    val color: Int = 0xFF000000.toInt(),
    val baseSize: Float = 10f,
    val toolType: ToolType = ToolType.STYLUS,
    val points: MutableList<StrokePoint> = mutableListOf(),
    val isComplete: Boolean = false,
    val config: BrushConfig = BrushConfig(
        size = baseSize,
        color = color,
        type = if (toolType == ToolType.ERASER) BrushType.ERASER else BrushType.HARD_PEN
    ),
    val layerId: String = "",
    val isMaskStroke: Boolean = false
) {
    constructor(
        id: String,
        config: BrushConfig,
        toolType: ToolType = ToolType.STYLUS,
        points: MutableList<StrokePoint> = mutableListOf(),
        isComplete: Boolean = false,
        layerId: String = "",
        isMaskStroke: Boolean = false
    ) : this(
        id = id,
        color = config.color,
        baseSize = config.size,
        toolType = toolType,
        points = points,
        isComplete = isComplete,
        config = config,
        layerId = layerId,
        isMaskStroke = isMaskStroke
    )
    fun computeBoundingBox(): CanvasRect {
        if (points.isEmpty()) return CanvasRect(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val padding = baseSize * 1.5f
        return CanvasRect(minX - padding, minY - padding, maxX + padding, maxY + padding)
    }

    fun computeBoundingBoxRectF(): RectF {
        val box = computeBoundingBox()
        return RectF(box.left, box.top, box.right, box.bottom)
    }

    fun toPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            // Midpoint quadratic bezier smoothing
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        if (points.size > 1) {
            val last = points.last()
            path.lineTo(last.x, last.y)
        }
        return path
    }
}
