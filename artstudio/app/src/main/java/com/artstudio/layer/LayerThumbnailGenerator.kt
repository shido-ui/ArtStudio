package com.artstudio.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import com.artstudio.core.BrushStroke
import com.artstudio.core.RasterStrokeEngine
import com.artstudio.document.model.LayerNode
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates and caches downscaled layer preview thumbnails without allocating
 * full-resolution 2048x2048 duplicate bitmap buffers.
 *
 * Memory Budget:
 * - Full resolution (2048x2048 ARGB_8888): 16,777,216 bytes (16 MB per layer, 160 MB for 10 layers).
 * - Downscaled thumbnail (64x64 ARGB_8888): 16,384 bytes (16 KB per layer, 160 KB for 10 layers).
 * - Bounded cache ceiling: 64 entries max (~1 MB memory footprint).
 *
 * Compatible with standard raster layers, imported assets, and future AutoDraw outputs.
 */
class LayerThumbnailGenerator(
    val thumbnailWidth: Int = DEFAULT_THUMBNAIL_SIZE,
    val thumbnailHeight: Int = DEFAULT_THUMBNAIL_SIZE
) {
    companion object {
        const val DEFAULT_THUMBNAIL_SIZE = 64
        const val MAX_CACHE_ENTRIES = 64
    }

    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val strokeEngine = RasterStrokeEngine()

    private val checkerPaint1 = Paint().apply { color = 0xFFE8E8E8.toInt() }
    private val checkerPaint2 = Paint().apply { color = 0xFFFFFFFF.toInt() }

    val estimatedBytesPerThumbnail: Int = thumbnailWidth * thumbnailHeight * 4

    /**
     * Generates or retrieves a downscaled preview thumbnail for the given layer.
     *
     * @param layer The layer node to render.
     * @param documentWidth Authoritative canvas width (e.g. 2048).
     * @param documentHeight Authoritative canvas height (e.g. 2048).
     * @param layerStrokes The committed strokes belonging to this layer.
     * @param layerBitmap Optional pre-rasterized bitmap for this layer (e.g. imported or baked asset).
     * @return A downscaled [Bitmap] strictly bounded to [thumbnailWidth] x [thumbnailHeight], or null on host JVM.
     */
    fun getThumbnail(
        layer: LayerNode,
        documentWidth: Int,
        documentHeight: Int,
        layerStrokes: List<BrushStroke> = emptyList(),
        layerBitmap: Bitmap? = null
    ): Bitmap? {
        val cacheKey = buildCacheKey(layer, layerStrokes, layerBitmap)
        cache[cacheKey]?.let { return it }

        val bmp = try {
            Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            null
        } ?: return null

        val canvas = try {
            Canvas(bmp)
        } catch (_: Throwable) {
            null
        } ?: return null

        // Clear transparent
        bmp.eraseColor(Color.TRANSPARENT)

        // Subtle transparency checkerboard
        drawCheckerboard(canvas)

        val scaleX = thumbnailWidth.toFloat() / documentWidth.coerceAtLeast(1)
        val scaleY = thumbnailHeight.toFloat() / documentHeight.coerceAtLeast(1)

        canvas.save()
        canvas.scale(scaleX, scaleY)

        // Draw direct raster bitmap if available
        if (layerBitmap != null && !layerBitmap.isRecycled) {
            val srcPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            srcPaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawBitmap(layerBitmap, 0f, 0f, srcPaint)
        }

        // Render strokes belonging to this layer
        for (stroke in layerStrokes) {
            strokeEngine.renderStroke(canvas, stroke)
        }

        canvas.restore()

        // Manage cache capacity
        if (cache.size >= MAX_CACHE_ENTRIES) {
            val firstKey = cache.keys().nextElement()
            cache.remove(firstKey)?.recycle()
        }

        cache[cacheKey] = bmp
        return bmp
    }

    /**
     * Clears cached thumbnails to free memory under low memory conditions.
     */
    fun clearCache() {
        for ((_, bmp) in cache) {
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
        cache.clear()
    }

    /**
     * Invalidate cached preview for a specific layer.
     */
    fun invalidateLayer(layerId: String) {
        val keysToRemove = cache.keys().toList().filter { it.startsWith(layerId) }
        for (k in keysToRemove) {
            cache.remove(k)?.recycle()
        }
    }

    private fun drawCheckerboard(canvas: Canvas) {
        val checkSize = 8f
        var isWhite = true
        var y = 0f
        while (y < thumbnailHeight) {
            var x = 0f
            while (x < thumbnailWidth) {
                val paint = if (isWhite) checkerPaint2 else checkerPaint1
                canvas.drawRect(x, y, x + checkSize, y + checkSize, paint)
                x += checkSize
                isWhite = !isWhite
            }
            y += checkSize
            if ((thumbnailWidth / checkSize.toInt()) % 2 == 0) {
                isWhite = !isWhite
            }
        }
    }

    private fun buildCacheKey(
        layer: LayerNode,
        strokes: List<BrushStroke>,
        bitmap: Bitmap?
    ): String {
        val bmpHash = bitmap?.hashCode() ?: 0
        val strokeHash = if (strokes.isEmpty()) 0 else strokes.size * 31 + strokes.last().id.hashCode()
        return "${layer.id}_${layer.opacity}_${layer.visible}_${layer.blendMode}_${layer.isLocked}_${strokes.size}_${strokeHash}_${bmpHash}"
    }
}
