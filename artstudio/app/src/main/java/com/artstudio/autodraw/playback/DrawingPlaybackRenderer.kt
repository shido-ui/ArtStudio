package com.artstudio.autodraw.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.artstudio.autodraw.schedule.DrawingSchedule
import com.artstudio.autodraw.schedule.DrawingScheduleEntry
import com.artstudio.autodraw.schedule.ScheduledBrushParams
import com.artstudio.core.BrushConfig
import com.artstudio.core.BrushStroke
import com.artstudio.core.BrushType
import com.artstudio.core.RasterStrokeEngine
import com.artstudio.core.StrokePoint
import com.artstudio.cv.CvPoint
import kotlin.math.*

/**
 * High-performance, deterministic visual renderer for drawing schedule playback on canvas.
 *
 * Separates committed artwork state from transient stroke overlays, guaranteeing
 * that playback cancellation or pausing never corrupts or erases committed artwork.
 * Utilizes the unified [RasterStrokeEngine] to guarantee playback renders identically to manual drawing.
 */
class DrawingPlaybackRenderer(
    val width: Int,
    val height: Int,
    val backgroundColor: Int = 0xFFFFFFFF.toInt() // Default white canvas
) {
    val strokeEngine: RasterStrokeEngine = RasterStrokeEngine()
    private val pixelCount = width * height

    // 1. Authoritative committed artwork buffer (committed at checkpoints or prior to playback)
    private val committedPixels = IntArray(pixelCount) { backgroundColor }

    // 2. Transient in-flight stroke buffer (discardable upon cancellation)
    private val transientPixels = IntArray(pixelCount) { 0 }

    // Combined composite buffer for real-time display
    private val displayCompositePixels = IntArray(pixelCount) { backgroundColor }

    // Dirty bounding box for high-performance localized display updates
    private var dirtyMinX: Int = 0
    private var dirtyMinY: Int = 0
    private var dirtyMaxX: Int = width - 1
    private var dirtyMaxY: Int = height - 1
    private var hasFullDirty: Boolean = true

    // Transient strokes tracked since last checkpoint
    private val transientStrokeIndices = mutableListOf<Int>()

    init {
        require(width > 0 && height > 0) { "Canvas dimensions must be positive, got ${width}x$height" }
        updateDisplayComposite()
    }

    /**
     * Resets the entire canvas, clearing both committed and transient artwork to background color.
     */
    fun resetAll() {
        committedPixels.fill(backgroundColor)
        transientPixels.fill(0)
        transientStrokeIndices.clear()
        hasFullDirty = true
        updateDisplayComposite()
    }

    /**
     * Commits all current transient strokes into the authoritative committed buffer.
     * Subsequent playback cancellations will preserve everything up to this checkpoint.
     */
    fun commitCheckpoint(strokeIndex: Int) {
        System.arraycopy(displayCompositePixels, 0, committedPixels, 0, pixelCount)
        transientPixels.fill(0)
        transientStrokeIndices.clear()
        hasFullDirty = true
    }

    /**
     * Discards all transient in-flight strokes without modifying committed artwork.
     * Called when playback is cancelled or rewound.
     */
    fun discardTransient() {
        transientPixels.fill(0)
        transientStrokeIndices.clear()
        System.arraycopy(committedPixels, 0, displayCompositePixels, 0, pixelCount)
        hasFullDirty = true
    }

    /**
     * Clears only the transient surface without resetting committed artwork.
     */
    fun clearTransientSurface() {
        transientPixels.fill(0)
        hasFullDirty = true
        updateDisplayComposite()
    }

    /**
     * Converts a scheduled entry into an authoritative [BrushStroke] matching the manual drawing engine.
     */
    private fun entryToBrushStroke(
        entry: DrawingScheduleEntry,
        waypoints: List<CvPoint>,
        clampedProgress: Float
    ): BrushStroke {
        val totalLength = calculatePathLength(waypoints)
        val drawTargetLength = totalLength * clampedProgress
        var accumulatedLength = 0f
        val strokePts = mutableListOf<StrokePoint>()

        for (i in 0 until waypoints.size - 1) {
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]
            val segLen = euclidean(p1, p2)

            if (i == 0) {
                strokePts.add(StrokePoint(p1.x, p1.y, pressure = 0.5f))
            }

            if (accumulatedLength + segLen <= drawTargetLength || (clampedProgress >= 0.9999f && i == waypoints.size - 2)) {
                strokePts.add(StrokePoint(p2.x, p2.y, pressure = 0.5f))
                accumulatedLength += segLen
            } else {
                val remainingLen = drawTargetLength - accumulatedLength
                val t = if (segLen > 0f) (remainingLen / segLen).coerceIn(0f, 1f) else 1f
                val interpX = p1.x + (p2.x - p1.x) * t
                val interpY = p1.y + (p2.y - p1.y) * t
                strokePts.add(StrokePoint(interpX, interpY, pressure = 0.5f))
                break
            }
        }

        return BrushStroke(
            id = entry.id,
            color = entry.brushParams.colorArgb.toInt(),
            baseSize = entry.brushParams.sizePx,
            points = strokePts,
            config = BrushConfig(
                size = entry.brushParams.sizePx,
                color = entry.brushParams.colorArgb.toInt(),
                opacity = entry.brushParams.opacity.coerceIn(0f, 1f),
                type = BrushType.HARD_PEN
            ),
            layerId = entry.layerId
        )
    }

    /**
     * Renders a scheduled stroke entry either fully or partially up to parameter [progress] in [0, 1].
     * Executes via [RasterStrokeEngine] to guarantee parity with manual drawing.
     */
    fun renderStroke(
        entry: DrawingScheduleEntry,
        progress: Float = 1.0f,
        isTransient: Boolean = true
    ) {
        if (progress <= 0f) return
        val clampedProgress = progress.coerceIn(0f, 1f)
        val targetBuffer = if (isTransient) transientPixels else committedPixels

        val waypoints = if (entry.waypoints.size >= 2) {
            entry.waypoints
        } else {
            listOf(entry.startPoint, entry.endPoint)
        }

        val stroke = entryToBrushStroke(entry, waypoints, clampedProgress)
        val bbox = stroke.computeBoundingBox()
        val pad = stroke.baseSize + 2f
        markDirtyBounds(
            (bbox.left - pad).toInt(),
            (bbox.top - pad).toInt(),
            (bbox.right + pad).toInt(),
            (bbox.bottom + pad).toInt()
        )

        strokeEngine.renderStrokeSoftware(targetBuffer, width, height, stroke)

        if (isTransient) {
            updateDisplayComposite()
        }
    }

    /**
     * Renders a complete schedule directly into a new pixel buffer in a single pass.
     * Serves as the ground truth reference for verifying deterministic playback replay.
     */
    fun directRenderSchedule(schedule: DrawingSchedule): IntArray {
        val directBuffer = IntArray(pixelCount) { backgroundColor }
        for (entry in schedule.entries) {
            val waypoints = if (entry.waypoints.size >= 2) entry.waypoints else listOf(entry.startPoint, entry.endPoint)
            val stroke = entryToBrushStroke(entry, waypoints, 1.0f)
            strokeEngine.renderStrokeSoftware(directBuffer, width, height, stroke)
        }
        return directBuffer
    }

    /**
     * Copies and returns current display composite pixels.
     */
    fun copyDisplayPixels(): IntArray = displayCompositePixels.clone()

    /**
     * Copies and returns current committed pixels.
     */
    fun copyCommittedPixels(): IntArray = committedPixels.clone()

    /**
     * Creates an Android [Bitmap] from the current display composite.
     */
    fun toBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(displayCompositePixels, 0, width, 0, 0, width, height)
        return bmp
    }

    /**
     * Renders visual pen tip cursor onto target Android [Canvas].
     */
    fun renderPenCursor(
        canvas: Canvas,
        telemetry: StrokePlaybackTelemetry
    ) {
        val x = telemetry.penPosition.x
        val y = telemetry.penPosition.y
        val size = max(3f, telemetry.brushParams.sizePx)
        val color = telemetry.brushParams.colorArgb.toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (telemetry.penState) {
            PenTipState.DRAWING -> {
                // Solid cursor ring showing depositing pigment
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.0f
                paint.color = color
                canvas.drawCircle(x, y, size / 2.0f, paint)

                // Central contact dot
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, 2.0f, paint)
            }
            PenTipState.LIFTED_TRANSIT -> {
                // Dashed / semi-transparent transit halo showing air movement
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = 0x803B82F6.toInt() // Semi-transparent blue
                canvas.drawCircle(x, y, max(size, 8.0f), paint)
                // Small crosshair tip
                canvas.drawLine(x - 4f, y, x + 4f, y, paint)
                canvas.drawLine(x, y - 4f, x, y + 4f, paint)
            }
            PenTipState.IDLE -> {
                paint.style = Paint.Style.FILL
                paint.color = 0x40888888
                canvas.drawCircle(x, y, 4.0f, paint)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal Rasterization & Blending Helpers
    // -------------------------------------------------------------------------

    fun markDirtyBounds(minX: Int, minY: Int, maxX: Int, maxY: Int) {
        val cMinX = minX.coerceIn(0, width - 1)
        val cMinY = minY.coerceIn(0, height - 1)
        val cMaxX = maxX.coerceIn(0, width - 1)
        val cMaxY = maxY.coerceIn(0, height - 1)

        if (hasFullDirty) {
            dirtyMinX = 0
            dirtyMinY = 0
            dirtyMaxX = width - 1
            dirtyMaxY = height - 1
        } else if (dirtyMinX > dirtyMaxX || dirtyMinY > dirtyMaxY) {
            dirtyMinX = cMinX
            dirtyMinY = cMinY
            dirtyMaxX = cMaxX
            dirtyMaxY = cMaxY
        } else {
            dirtyMinX = min(dirtyMinX, cMinX)
            dirtyMinY = min(dirtyMinY, cMinY)
            dirtyMaxX = max(dirtyMaxX, cMaxX)
            dirtyMaxY = max(dirtyMaxY, cMaxY)
        }
    }

    private fun updateDisplayComposite() {
        if (hasFullDirty) {
            for (i in 0 until pixelCount) {
                val trans = transientPixels[i]
                val transAlpha = (trans ushr 24) and 0xFF
                if (transAlpha == 0) {
                    displayCompositePixels[i] = committedPixels[i]
                } else if (transAlpha == 255) {
                    displayCompositePixels[i] = trans
                } else {
                    val comm = committedPixels[i]
                    displayCompositePixels[i] = blendSrcOver(comm, trans)
                }
            }
            hasFullDirty = false
            dirtyMinX = Int.MAX_VALUE
            dirtyMinY = Int.MAX_VALUE
            dirtyMaxX = Int.MIN_VALUE
            dirtyMaxY = Int.MIN_VALUE
            return
        }

        if (dirtyMinX > dirtyMaxX || dirtyMinY > dirtyMaxY) return

        for (py in dirtyMinY..dirtyMaxY) {
            val rowOffset = py * width
            for (px in dirtyMinX..dirtyMaxX) {
                val i = rowOffset + px
                val trans = transientPixels[i]
                val transAlpha = (trans ushr 24) and 0xFF
                if (transAlpha == 0) {
                    displayCompositePixels[i] = committedPixels[i]
                } else if (transAlpha == 255) {
                    displayCompositePixels[i] = trans
                } else {
                    val comm = committedPixels[i]
                    displayCompositePixels[i] = blendSrcOver(comm, trans)
                }
            }
        }

        dirtyMinX = Int.MAX_VALUE
        dirtyMinY = Int.MAX_VALUE
        dirtyMaxX = Int.MIN_VALUE
        dirtyMaxY = Int.MIN_VALUE
    }

    private fun drawThickSegment(
        buffer: IntArray,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        radius: Float,
        colorArgb: Int,
        opacity: Float
    ) {
        val segMinX = (min(x1, x2) - radius - 2).toInt()
        val segMaxX = (max(x1, x2) + radius + 2).toInt()
        val segMinY = (min(y1, y2) - radius - 2).toInt()
        val segMaxY = (max(y1, y2) + radius + 2).toInt()
        markDirtyBounds(segMinX, segMinY, segMaxX, segMaxY)

        val dist = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
        val step = max(0.5f, radius * 0.4f)
        val numSteps = max(1, (dist / step).toInt())

        val srcA = (((colorArgb ushr 24) and 0xFF) * opacity).roundToInt().coerceIn(0, 255)
        val srcR = (colorArgb ushr 16) and 0xFF
        val srcG = (colorArgb ushr 8) and 0xFF
        val srcB = colorArgb and 0xFF
        val srcColor = (srcA shl 24) or (srcR shl 16) or (srcG shl 8) or srcB

        for (s in 0..numSteps) {
            val t = s.toFloat() / numSteps
            val cx = x1 + (x2 - x1) * t
            val cy = y1 + (y2 - y1) * t
            drawCircleDab(buffer, cx, cy, radius, srcColor)
        }
    }

    private fun drawCircleDab(
        buffer: IntArray,
        cx: Float,
        cy: Float,
        radius: Float,
        srcColor: Int
    ) {
        val minX = max(0, (cx - radius).toInt())
        val maxX = min(width - 1, (cx + radius).toInt() + 1)
        val minY = max(0, (cy - radius).toInt())
        val maxY = min(height - 1, (cy + radius).toInt() + 1)
        val r2 = radius * radius

        val srcA = (srcColor ushr 24) and 0xFF
        if (srcA == 0) return

        for (py in minY..maxY) {
            val dy = py.toFloat() - cy
            val dy2 = dy * dy
            val rowOffset = py * width
            for (px in minX..maxX) {
                val dx = px.toFloat() - cx
                val d2 = dx * dx + dy2
                if (d2 <= r2) {
                    val idx = rowOffset + px
                    buffer[idx] = blendSrcOver(buffer[idx], srcColor)
                }
            }
        }
    }

    private fun blendSrcOver(dst: Int, src: Int): Int {
        val sa = (src ushr 24) and 0xFF
        if (sa == 0) return dst
        if (sa == 255) return src

        val da = (dst ushr 24) and 0xFF
        val invSa = 255 - sa
        val outA = sa + (da * invSa) / 255

        if (outA == 0) return 0

        val sr = (src ushr 16) and 0xFF
        val sg = (src ushr 8) and 0xFF
        val sb = src and 0xFF

        val dr = (dst ushr 16) and 0xFF
        val dg = (dst ushr 8) and 0xFF
        val db = dst and 0xFF

        val outR = (sr * sa + dr * da * invSa / 255) / outA
        val outG = (sg * sa + dg * da * invSa / 255) / outA
        val outB = (sb * sa + db * da * invSa / 255) / outA

        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun calculatePathLength(points: List<CvPoint>): Float {
        var len = 0f
        for (i in 0 until points.size - 1) {
            len += euclidean(points[i], points[i + 1])
        }
        return max(1.0f, len)
    }

    private fun euclidean(p1: CvPoint, p2: CvPoint): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }
}
