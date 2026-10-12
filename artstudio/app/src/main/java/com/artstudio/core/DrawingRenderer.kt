package com.artstudio.core

import android.graphics.Canvas
import android.graphics.RectF

/**
 * Narrow interface for drawing surface renderers.
 * Decouples drawing implementation (Canvas, Skia, SurfaceView, TextureView)
 * from document state and input pipelines.
 */
interface DrawingRenderer {
    /**
     * Renders the document, background, raster layers, and active in-progress stroke.
     * @param canvas The target hardware-accelerated or software Canvas
     * @param viewport Current viewport transform state (pan, zoom, rotation)
     * @param document Current authoritative document state
     * @param activeStroke Currently active in-flight stroke (if any)
     */
    fun render(
        canvas: Canvas,
        viewport: ViewportTransform,
        document: DocumentState,
        activeStroke: BrushStroke?
    )

    /**
     * Renders only the invalid dirty rectangle to minimize redraw overhead.
     */
    fun renderDirty(
        canvas: Canvas,
        viewport: ViewportTransform,
        document: DocumentState,
        activeStroke: BrushStroke?,
        dirtyRect: RectF
    )
}
