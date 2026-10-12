package com.artstudio.pixelops

import com.artstudio.history.PixelModifierCommand
import com.artstudio.history.TileDelta
import com.artstudio.history.TileDeltaHelper
import com.artstudio.history.TileDeltaSlice
import com.artstudio.selection.SelectionMask
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Manages an interactive in-flight pixel modification stroke session.
 * Interpolates dabs along pointer movement, tracks dirty bounding boxes incrementally,
 * snapshots only touched tiles (zero full-canvas IntArray copies), and compiles a bounded-memory
 * [PixelModifierCommand] upon completion.
 */
class PixelModifierSession(
    val engine: PixelModifierEngine = PixelModifierEngine()
) {
    var isActive: Boolean = false
        private set

    private var targetPixels: IntArray? = null
    private val preTileSnapshots = mutableMapOf<Pair<Int, Int>, TileDeltaSlice>()
    private var width: Int = 0
    private var height: Int = 0
    private var layerId: String = ""
    private var config: PixelModifierConfig = PixelModifierConfig()
    private val reservoir: SmudgeReservoir = SmudgeReservoir()
    private var selectionMask: SelectionMask? = null
    private var layerMaskPixels: IntArray? = null
    private var isAlphaLocked: Boolean = false

    private val boundsTracker: PixelBoundingBox = PixelBoundingBox()
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastPressure: Float = 1.0f
    private var totalDabs: Int = 0
    private var totalModifiedPixels: Int = 0
    private var startTimeMs: Long = 0L

    /**
     * Begins an in-flight pixel modification stroke at (startX, startY).
     */
    fun beginStroke(
        layerId: String,
        targetPixels: IntArray,
        width: Int,
        height: Int,
        startX: Float,
        startY: Float,
        initialPressure: Float,
        config: PixelModifierConfig,
        selectionMask: SelectionMask? = null,
        layerMaskPixels: IntArray? = null,
        isAlphaLocked: Boolean = false
    ): Int {
        this.isActive = true
        this.layerId = layerId
        this.targetPixels = targetPixels
        this.preTileSnapshots.clear()
        this.width = width
        this.height = height
        this.config = config
        this.selectionMask = selectionMask
        this.layerMaskPixels = layerMaskPixels
        this.isAlphaLocked = isAlphaLocked
        this.reservoir.reset()
        this.boundsTracker.minX = Int.MAX_VALUE
        this.boundsTracker.minY = Int.MAX_VALUE
        this.boundsTracker.maxX = Int.MIN_VALUE
        this.boundsTracker.maxY = Int.MIN_VALUE
        this.lastX = startX
        this.lastY = startY
        this.lastPressure = initialPressure
        this.totalDabs = 0
        this.totalModifiedPixels = 0
        this.startTimeMs = System.currentTimeMillis()

        // Snapshot only the tiles touched by the initial dab before mutating pixels
        snapshotTilesForDab(startX, startY, config.radius)

        // Apply first dab at start position
        val modified = engine.applyDab(
            targetPixels = targetPixels,
            width = width,
            height = height,
            cx = startX,
            cy = startY,
            radius = config.radius,
            pressure = initialPressure,
            config = config,
            reservoir = reservoir,
            selectionMask = selectionMask,
            layerMaskPixels = layerMaskPixels,
            isAlphaLocked = isAlphaLocked,
            boundsTracker = boundsTracker
        )

        totalDabs++
        totalModifiedPixels += modified
        return modified
    }

    /**
     * Incrementally processes pointer movement to (currentX, currentY).
     * Interpolates dabs along the trajectory according to brush spacing.
     */
    fun addPoint(currentX: Float, currentY: Float, pressure: Float): Int {
        if (!isActive) return 0
        val pixels = targetPixels ?: return 0

        val dx = currentX - lastX
        val dy = currentY - lastY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        val spacingDist = max(1.0f, config.radius * 2.0f * config.spacing)
        val steps = max(1, ceil(dist / spacingDist).toInt())
        var stepModified = 0

        for (s in 1..steps) {
            val t = s.toFloat() / steps.toFloat()
            val x = lastX + t * dx
            val y = lastY + t * dy
            val p = lastPressure + t * (pressure - lastPressure)

            snapshotTilesForDab(x, y, config.radius)

            val count = engine.applyDab(
                targetPixels = pixels,
                width = width,
                height = height,
                cx = x,
                cy = y,
                radius = config.radius,
                pressure = p,
                config = config,
                reservoir = reservoir,
                selectionMask = selectionMask,
                layerMaskPixels = layerMaskPixels,
                isAlphaLocked = isAlphaLocked,
                boundsTracker = boundsTracker
            )
            totalDabs++
            stepModified += count
        }

        lastX = currentX
        lastY = currentY
        lastPressure = pressure
        totalModifiedPixels += stepModified
        return stepModified
    }

    /**
     * Concludes the stroke session, compiles the tile-bounded [PixelModifierCommand],
     * and clears transient stroke resources.
     */
    fun endStroke(): PixelModifierCommand? {
        if (!isActive) return null
        val pixels = targetPixels ?: return null

        boundsTracker.clamp(width, height)
        val cmd = if (!boundsTracker.isEmpty && preTileSnapshots.isNotEmpty()) {
            val patchX = boundsTracker.minX
            val patchY = boundsTracker.minY
            val patchW = boundsTracker.width
            val patchH = boundsTracker.height

            val deltas = mutableListOf<TileDelta>()
            val tileSize = TileDeltaHelper.DEFAULT_TILE_SIZE
            for ((_, preSlice) in preTileSnapshots) {
                val postSlice = TileDeltaHelper.extractTileSlice(pixels, width, height, preSlice.col, preSlice.row, tileSize)
                if (!preSlice.pixels.contentEquals(postSlice.pixels)) {
                    deltas.add(
                        TileDelta(
                            col = preSlice.col,
                            row = preSlice.row,
                            x = preSlice.x,
                            y = preSlice.y,
                            width = preSlice.width,
                            height = preSlice.height,
                            prePixels = preSlice.pixels,
                            postPixels = postSlice.pixels
                        )
                    )
                }
            }

            if (deltas.isEmpty()) {
                val first = preTileSnapshots.values.first()
                deltas.add(
                    TileDelta(
                        col = first.col,
                        row = first.row,
                        x = first.x,
                        y = first.y,
                        width = first.width,
                        height = first.height,
                        prePixels = first.pixels,
                        postPixels = first.pixels.clone()
                    )
                )
            }

            PixelModifierCommand(
                layerId = layerId,
                patchX = patchX,
                patchY = patchY,
                patchWidth = patchW,
                patchHeight = patchH,
                tileDeltas = deltas,
                description = "${config.mode.displayName} Stroke"
            )
        } else {
            null
        }

        reset()
        return cmd
    }

    /**
     * Cancels an active in-flight stroke, restoring original pixel values.
     */
    fun cancelStroke(): Boolean {
        if (!isActive) return false
        val pixels = targetPixels

        if (pixels != null && preTileSnapshots.isNotEmpty()) {
            for ((_, slice) in preTileSnapshots) {
                for (r in 0 until slice.height) {
                    val srcOffset = r * slice.width
                    val dstOffset = (slice.y + r) * width + slice.x
                    if (dstOffset >= 0 && dstOffset + slice.width <= pixels.size && srcOffset + slice.width <= slice.pixels.size) {
                        System.arraycopy(slice.pixels, srcOffset, pixels, dstOffset, slice.width)
                    }
                }
            }
        }

        reset()
        return true
    }

    private fun snapshotTilesForDab(cx: Float, cy: Float, radius: Float) {
        val pixels = targetPixels ?: return
        val tileSize = TileDeltaHelper.DEFAULT_TILE_SIZE
        val totalCols = max(1, ceil(width.toDouble() / tileSize).toInt())
        val totalRows = max(1, ceil(height.toDouble() / tileSize).toInt())

        val startCol = max(0, ((cx - radius) / tileSize).toInt())
        val endCol = min(totalCols - 1, ((cx + radius) / tileSize).toInt())
        val startRow = max(0, ((cy - radius) / tileSize).toInt())
        val endRow = min(totalRows - 1, ((cy + radius) / tileSize).toInt())

        for (r in startRow..endRow) {
            for (c in startCol..endCol) {
                val key = Pair(c, r)
                if (!preTileSnapshots.containsKey(key)) {
                    val slice = TileDeltaHelper.extractTileSlice(pixels, width, height, c, r, tileSize)
                    preTileSnapshots[key] = slice
                }
            }
        }
    }

    private fun reset() {
        isActive = false
        targetPixels = null
        preTileSnapshots.clear()
        selectionMask = null
        layerMaskPixels = null
        width = 0
        height = 0
        layerId = ""
        totalDabs = 0
        totalModifiedPixels = 0
    }
}
