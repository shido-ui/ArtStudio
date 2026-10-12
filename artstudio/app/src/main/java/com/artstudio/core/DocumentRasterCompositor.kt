package com.artstudio.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * Manages incremental transient stroke rendering and non-blocking asynchronous
 * stroke commits into document raster surfaces/layers.
 */
class DocumentRasterCompositor(
    val strokeEngine: RasterStrokeEngine = RasterStrokeEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    var activeTransientBitmap: Bitmap? = null
        private set

    var activeStroke: BrushStroke? = null
        private set

    val hasActiveOverlay: Boolean
        get() = transientCanvas != null

    private var transientCanvas: Canvas? = null
    private var lastRenderedPointIndex: Int = 0

    /**
     * Prepares transient overlay surface for a new in-flight stroke.
     */
    fun beginStroke(width: Int, height: Int, stroke: BrushStroke? = null) {
        lastRenderedPointIndex = 0
        activeStroke = stroke
        if (activeTransientBitmap == null || activeTransientBitmap?.width != width || activeTransientBitmap?.height != height) {
            activeTransientBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            activeTransientBitmap?.eraseColor(Color.TRANSPARENT)
        }
        transientCanvas = Canvas(activeTransientBitmap!!)
    }

    fun setActiveStroke(stroke: BrushStroke?) {
        this.activeStroke = stroke
    }

    fun cancelActiveStroke() {
        discardActiveStroke()
    }

    /**
     * Incrementally renders new points of the active stroke into the transient overlay.
     * High-speed execution (sub-millisecond) designed for 60/120 Hz pointer loops.
     */
    fun renderIncremental(stroke: BrushStroke): Int {
        this.activeStroke = stroke
        val canvas = transientCanvas ?: return 0
        val count = strokeEngine.renderIncremental(canvas, stroke, lastRenderedPointIndex)
        lastRenderedPointIndex = stroke.points.size
        return count
    }

    /**
     * Discards the active in-flight stroke without committing any changes.
     */
    fun discardActiveStroke() {
        activeTransientBitmap?.eraseColor(Color.TRANSPARENT)
        transientCanvas = null
        activeStroke = null
        lastRenderedPointIndex = 0
    }

    /**
     * Asynchronously commits the completed stroke into the destination raster bitmap
     * off the UI thread to guarantee zero main-thread stalls.
     */
    fun commitStrokeAsync(
        stroke: BrushStroke,
        targetBitmap: Bitmap? = null,
        onComplete: (BrushStroke) -> Unit = {}
    ): Job {
        return scope.launch {
            if (targetBitmap != null) {
                val targetCanvas = Canvas(targetBitmap)
                strokeEngine.renderStroke(targetCanvas, stroke)
            }
            discardActiveStroke()
            onComplete(stroke)
        }
    }

    fun commitStrokeAsync(
        targetBitmap: Bitmap,
        stroke: BrushStroke,
        onComplete: (BrushStroke) -> Unit = {}
    ): Job = commitStrokeAsync(stroke = stroke, targetBitmap = targetBitmap, onComplete = onComplete)

    /**
     * Synchronous commit for testing or offline batch exports.
     */
    fun commitStrokeSync(
        stroke: BrushStroke,
        targetBitmap: Bitmap? = null
    ) {
        if (targetBitmap != null) {
            val targetCanvas = Canvas(targetBitmap)
            strokeEngine.renderStroke(targetCanvas, stroke)
        }
        discardActiveStroke()
    }

    fun commitStrokeSync(
        targetBitmap: Bitmap,
        stroke: BrushStroke
    ) {
        commitStrokeSync(stroke = stroke, targetBitmap = targetBitmap)
    }

    fun shutdown() {
        scope.cancel()
    }
}
