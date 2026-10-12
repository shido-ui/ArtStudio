package com.artstudio.core

import android.graphics.Bitmap

/**
 * Memory-bounded, thread-safe bitmap pool for recycling and reusing bitmaps.
 * Prevents continuous native bitmap allocations and GC pause spikes.
 *
 * Invariants:
 * - Thread-safe access.
 * - Pool memory ceiling tied to DeviceMemoryPolicy.
 * - Recycles oldest bitmaps on overflow and onTrimMemory.
 * - Graceful degradation in pure JVM environments where Android Bitmap is mocked or stubbed.
 */
object BitmapPool {
    private val pool = mutableListOf<Bitmap>()
    private var currentPoolBytes: Long = 0L

    val maxPoolBytes: Long
        get() = DeviceMemoryPolicy.currentTier.maxTileCacheBytes / 2L

    @Synchronized
    fun acquire(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val bmp = iterator.next()
            if (!bmp.isRecycled && bmp.width == width && bmp.height == height && bmp.config == config) {
                iterator.remove()
                currentPoolBytes -= getBitmapBytes(bmp)
                try {
                    bmp.eraseColor(0)
                } catch (t: Throwable) {
                    android.util.Log.d("BitmapPool", "Could not erase recycled bitmap: ${t.message}")
                }
                return bmp
            }
        }

        return try {
            Bitmap.createBitmap(width, height, config)
        } catch (t: Throwable) {
            android.util.Log.w("BitmapPool", "Failed to allocate bitmap: ${t.message}")
            null
        }
    }

    @Synchronized
    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        val bytes = getBitmapBytes(bitmap)
        if (currentPoolBytes + bytes > maxPoolBytes) {
            try {
                bitmap.recycle()
            } catch (t: Throwable) {
                android.util.Log.w("BitmapPool", "Failed to recycle bitmap: ${t.message}")
            }
            return
        }
        pool.add(bitmap)
        currentPoolBytes += bytes
    }

    @Synchronized
    fun clear() {
        for (bmp in pool) {
            if (!bmp.isRecycled) {
                try {
                    bmp.recycle()
                } catch (t: Throwable) {
                    android.util.Log.w("BitmapPool", "Failed to recycle bitmap during clear: ${t.message}")
                }
            }
        }
        pool.clear()
        currentPoolBytes = 0L
    }

    @Synchronized
    fun trimToSize(targetBytes: Long) {
        while (currentPoolBytes > targetBytes && pool.isNotEmpty()) {
            val bmp = pool.removeAt(0)
            val bytes = getBitmapBytes(bmp)
            currentPoolBytes -= bytes
            if (!bmp.isRecycled) {
                try {
                    bmp.recycle()
                } catch (t: Throwable) {
                    android.util.Log.w("BitmapPool", "Failed to recycle bitmap during trim: ${t.message}")
                }
            }
        }
    }

    @Synchronized
    fun onTrimMemory(level: MemoryTrimLevel) {
        when (level) {
            MemoryTrimLevel.MODERATE -> trimToSize(maxPoolBytes / 2L)
            MemoryTrimLevel.CRITICAL -> clear()
        }
    }

    val pooledCount: Int
        @Synchronized get() = pool.size

    val pooledBytes: Long
        @Synchronized get() = currentPoolBytes

    private fun getBitmapBytes(bmp: Bitmap): Long {
        val count = try { bmp.allocationByteCount.toLong() } catch (_: Throwable) { 0L }
        return if (count > 0L) count else (bmp.width.toLong() * bmp.height.toLong() * 4L)
    }
}
