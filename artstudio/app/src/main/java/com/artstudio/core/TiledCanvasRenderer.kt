package com.artstudio.core

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Hardware-accelerated Tiled Canvas Renderer.
 * Implements bounded surface rendering, dirty-region updates, frustum culling of off-screen tiles,
 * and zero-latency active stroke separation.
 */
class TiledCanvasRenderer(
    val engine: TiledCanvasEngine<AndroidBitmapTile> = TiledCanvasEngine(),
    private val profiler: RenderingProfiler = engine.profiler
) : DrawingRenderer {

    private val fillPaint = Paint()
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF888888.toInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun render(
        canvas: Canvas,
        viewport: ViewportTransform,
        document: DocumentState,
        activeStroke: BrushStroke?
    ) {
        val startTime = System.nanoTime()

        canvas.save()
        // Hardware-accelerated viewport transformation
        canvas.concat(viewport.getTransformMatrix())

        val canvasBounds = document.getBounds()

        // 1. Canvas background
        fillPaint.color = document.backgroundColor
        canvas.drawRect(canvasBounds, fillPaint)

        // 2. Visible frustum bounds in canvas space
        val visibleBounds = viewport.getVisibleCanvasBounds(
            screenWidth = if (viewport.viewportWidth > 0f) viewport.viewportWidth else document.width.toFloat(),
            screenHeight = if (viewport.viewportHeight > 0f) viewport.viewportHeight else document.height.toFloat()
        )

        // 3. Render low-cost downsampled preview if available
        val preview = engine.lowCostPreview
        if (preview != null) {
            canvas.drawBitmap(preview.bitmap, null, canvasBounds, tilePaint)
        }

        // 4. Render visible cached tiles
        val visibleTiles = engine.getVisibleTiles(visibleBounds)
        var tilesDrawn = 0
        for ((col, row) in visibleTiles) {
            val key = engine.getTileKey("layer_default", col, row)
            val tile = engine.getTile(key)
            if (tile != null) {
                val left = (col * engine.tileSize).toFloat()
                val top = (row * engine.tileSize).toFloat()
                canvas.drawBitmap(tile.bitmap, left, top, tilePaint)
                tilesDrawn++
            }
        }

        // 6. Committed strokes
        for (stroke in document.committedStrokes) {
            renderStroke(canvas, stroke)
        }

        // 7. Active stroke rendered directly on top (instant zero-latency response)
        if (activeStroke != null) {
            renderStroke(canvas, activeStroke)
        }

        // 8. Canvas border
        canvas.drawRect(canvasBounds, borderPaint)

        canvas.restore()

        val durationNs = System.nanoTime() - startTime
        profiler.recordFrame(
            durationMs = durationNs / 1_000_000f,
            tilesRendered = tilesDrawn,
            tilesTotal = engine.totalTiles
        )
    }

    override fun renderDirty(
        canvas: Canvas,
        viewport: ViewportTransform,
        document: DocumentState,
        activeStroke: BrushStroke?,
        dirtyRect: RectF
    ) {
        canvas.save()
        canvas.clipRect(dirtyRect)
        render(canvas, viewport, document, activeStroke)
        canvas.restore()
    }

    private val rasterEngine = RasterStrokeEngine()

    private fun renderStroke(canvas: Canvas, stroke: BrushStroke) {
        rasterEngine.renderStroke(canvas, stroke)
    }
}
