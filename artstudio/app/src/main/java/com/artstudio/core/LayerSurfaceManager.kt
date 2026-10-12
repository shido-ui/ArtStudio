package com.artstudio.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.history.TileDelta

/**
 * Manages cached per-layer bitmap surfaces for hardware-accelerated rendering.
 *
 * Invariants:
 * - Each finished stroke is baked once onto its target layer's cached bitmap surface.
 * - Active strokes are rendered incrementally, compositing cached layer bitmaps.
 * - Zero stroke replaying on subsequent frames.
 * - The eraser affects only the active layer's surface (PorterDuff CLEAR on layer bitmap).
 * - Bounded memory usage aligned with device limits and BitmapPool recycling.
 */
class LayerSurfaceManager(
    val strokeEngine: RasterStrokeEngine = RasterStrokeEngine()
) {
    class LayerSurface(
        val layerId: String,
        val width: Int,
        val height: Int,
        val bitmap: Bitmap?,
        val canvas: Canvas?
    ) {
        var isDirty: Boolean = true
        var bakedStrokesCount: Int = 0
        var intBuffer: IntArray? = null

        fun bakeStroke(stroke: BrushStroke, engine: RasterStrokeEngine) {
            if (canvas != null) {
                engine.renderStroke(canvas, stroke)
            }
            val buf = intBuffer
            if (buf != null && buf.size == width * height) {
                engine.renderStrokeSoftware(buf, width, height, stroke)
            }
            isDirty = true
            bakedStrokesCount++
        }

        fun updatePixels(pixels: IntArray) {
            if (bitmap != null && !bitmap.isRecycled && pixels.size == width * height) {
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            }
            val buf = intBuffer
            if (buf != null && buf.size == pixels.size) {
                System.arraycopy(pixels, 0, buf, 0, pixels.size)
            } else {
                intBuffer = pixels.copyOf()
            }
            isDirty = true
        }

        fun applyTileDeltas(deltas: List<TileDelta>, usePre: Boolean) {
            val buf = intBuffer
            for (delta in deltas) {
                val src = if (usePre) delta.prePixels else delta.postPixels
                if (buf != null) {
                    delta.applyTo(buf, width, usePre)
                }
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.setPixels(src, 0, delta.width, delta.x, delta.y, delta.width, delta.height)
                }
            }
            isDirty = true
        }

        fun clear() {
            if (canvas != null) {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            }
            intBuffer?.fill(0)
            isDirty = true
            bakedStrokesCount = 0
        }

        fun syncPixels(documentState: DocumentState) {
            val px = if (bitmap != null && !bitmap.isRecycled) {
                val arr = intBuffer ?: IntArray(width * height).also { intBuffer = it }
                bitmap.getPixels(arr, 0, width, 0, 0, width, height)
                arr
            } else {
                intBuffer
            }
            if (px != null) {
                (documentState as? DefaultDocumentState)?.setLayerPixels(layerId, px)
            }
        }
    }

    private val surfaces = mutableMapOf<String, LayerSurface>()

    fun getSurface(layerId: String): LayerSurface? = surfaces[layerId]

    fun getOrCreateSurface(
        layer: LayerNode.RasterLayer,
        documentState: DocumentState
    ): LayerSurface {
        val existing = surfaces[layer.id]
        if (existing != null && existing.width == layer.width && existing.height == layer.height) {
            return existing
        }

        val existingPixels = documentState.getLayerPixels(layer.id)
        val bmp = try {
            val pooled = BitmapPool.acquire(layer.width, layer.height, Bitmap.Config.ARGB_8888)
            if (pooled != null && existingPixels != null && existingPixels.size == layer.width * layer.height) {
                pooled.setPixels(existingPixels, 0, layer.width, 0, 0, layer.width, layer.height)
            }
            pooled
        } catch (e: Throwable) {
            null
        }

        val canvas = if (bmp != null) Canvas(bmp) else null
        val surface = LayerSurface(
            layerId = layer.id,
            width = layer.width,
            height = layer.height,
            bitmap = bmp,
            canvas = canvas
        )
        if (existingPixels != null) {
            surface.intBuffer = existingPixels.copyOf()
        } else {
            surface.intBuffer = IntArray(layer.width * layer.height) { 0 }
        }

        // Bake any already-committed strokes for this layer
        val activeId = documentState.authoritativeDocument.activeLayerId
        val strokes = documentState.committedStrokes.filter {
            (it.layerId == layer.id || (it.layerId.isEmpty() && layer.id == activeId)) && !it.isMaskStroke
        }
        for (stroke in strokes) {
            surface.bakeStroke(stroke, strokeEngine)
        }

        surfaces[layer.id] = surface
        return surface
    }

    fun bakeFinishedStroke(
        stroke: BrushStroke,
        documentState: DocumentState
    ) {
        val layerId = if (stroke.layerId.isNotEmpty()) stroke.layerId else documentState.authoritativeDocument.activeLayerId
        val targetLayer = documentState.authoritativeDocument.findLayerById(layerId) as? LayerNode.RasterLayer
        val surface = if (targetLayer != null) {
            getOrCreateSurface(targetLayer, documentState)
        } else {
            surfaces[layerId]
        }
        if (surface != null) {
            surface.bakeStroke(stroke, strokeEngine)
            surface.syncPixels(documentState)
        }
    }

    fun updateLayerPixels(layerId: String, pixels: IntArray) {
        surfaces[layerId]?.updatePixels(pixels)
    }

    fun applyTileDeltas(layerId: String, deltas: List<TileDelta>, usePre: Boolean) {
        surfaces[layerId]?.applyTileDeltas(deltas, usePre)
    }

    fun rebakeAll(documentState: DocumentState) {
        clearAll()
        for (layer in documentState.authoritativeDocument.allLayersFlat()) {
            if (layer is LayerNode.RasterLayer) {
                getOrCreateSurface(layer, documentState)
            }
        }
    }

    fun onTrimMemory(level: MemoryTrimLevel) {
        if (level == MemoryTrimLevel.CRITICAL) {
            clearAll()
        }
    }

    fun clearAll() {
        for (surface in surfaces.values) {
            surface.clear()
            surface.bitmap?.let { BitmapPool.release(it) }
        }
        surfaces.clear()
    }
}
