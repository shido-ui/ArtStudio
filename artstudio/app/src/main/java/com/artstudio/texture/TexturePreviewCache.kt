package com.artstudio.texture

import com.artstudio.core.BrushTexture
import java.util.Collections
import kotlin.math.min

/**
 * Thread-safe LRU preview cache for lazy-loaded texture thumbnails.
 * Guarantees that opening texture dialogs or browsing hundreds of assets does not
 * incur premature memory allocations or disk stalls.
 */
class TexturePreviewCache(private val maxEntries: Int = 64) {

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, BrushTexture>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BrushTexture>?): Boolean {
                return size > maxEntries
            }
        }
    )

    /**
     * Retrieves or generates a lazy-loaded downsampled 64x64 preview thumbnail.
     */
    fun getPreview(
        metadata: TextureMetadata,
        loadSourceTexture: () -> BrushTexture?
    ): BrushTexture {
        val cacheKey = "${metadata.id}_${metadata.width}x${metadata.height}_${metadata.sha256}"
        cache[cacheKey]?.let { return it }

        val fullTexture = loadSourceTexture()
        val preview = if (fullTexture != null) {
            downsample(fullTexture, 64, 64)
        } else {
            // Diagnostic fallback preview
            generateDiagnosticPlaceholder(64, 64)
        }

        cache[cacheKey] = preview
        return preview
    }

    /**
     * Checks if a preview is currently cached in memory without triggering a load.
     */
    fun isCached(metadata: TextureMetadata): Boolean {
        val cacheKey = "${metadata.id}_${metadata.width}x${metadata.height}_${metadata.sha256}"
        return cache.containsKey(cacheKey)
    }

    /**
     * Clears all cached thumbnails.
     */
    fun clear() {
        cache.clear()
    }

    val cachedCount: Int
        get() = cache.size

    // -------------------------------------------------------------------------
    // Downsampling & Diagnostic Generation
    // -------------------------------------------------------------------------

    private fun downsample(src: BrushTexture, targetW: Int, targetH: Int): BrushTexture {
        if (src.width == targetW && src.height == targetH) return src

        val outData = ByteArray(targetW * targetH)
        val xRatio = src.width.toFloat() / targetW
        val yRatio = src.height.toFloat() / targetH

        for (ty in 0 until targetH) {
            val sy = (ty * yRatio).toInt().coerceIn(0, src.height - 1)
            for (tx in 0 until targetW) {
                val sx = (tx * xRatio).toInt().coerceIn(0, src.width - 1)
                outData[ty * targetW + tx] = src.data[sy * src.width + sx]
            }
        }
        return BrushTexture(targetW, targetH, outData)
    }

    private fun generateDiagnosticPlaceholder(targetW: Int, targetH: Int): BrushTexture {
        val outData = ByteArray(targetW * targetH)
        // Checkerboard placeholder pattern (8x8 cells)
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val isCheck = ((x / 8) + (y / 8)) % 2 == 0
                outData[y * targetW + x] = if (isCheck) 200.toByte() else 50.toByte()
            }
        }
        return BrushTexture(targetW, targetH, outData)
    }
}
