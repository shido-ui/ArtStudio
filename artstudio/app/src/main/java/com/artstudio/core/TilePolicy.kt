package com.artstudio.core

import android.graphics.Bitmap

data class TileKey(val layerId: String, val tileX: Int, val tileY: Int)

/**
 * Abstraction for tile raster buffers.
 * Enables deterministic testing on pure JVM without Android framework mocks.
 */
interface TileBuffer {
    val width: Int
    val height: Int
    val byteCount: Long
}

class AndroidBitmapTile(val bitmap: Bitmap) : TileBuffer {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override val byteCount: Long
        get() {
            val count = bitmap.allocationByteCount.toLong()
            return if (count > 0L) count else (width.toLong() * height.toLong() * 4L)
        }
}

data class MemoryTileBuffer(
    override val width: Int = 256,
    override val height: Int = 256,
    override val byteCount: Long = (256L * 256L * 4L)
) : TileBuffer

/**
 * Narrow interface for Tile Caching Policy.
 * Bounded memory cache with LRU eviction to enforce the 128 MB RAM ceiling from BASELINES.md.
 */
interface TileCachePolicy<T : TileBuffer> {
    val maxMemoryBytes: Long
    val currentMemoryBytes: Long
    val tileSize: Int

    fun getTile(key: TileKey): T?
    fun putTile(key: TileKey, tile: T)
    fun evictTile(key: TileKey)
    fun clear()
    fun getTileCount(): Int
}

class LruTileCache<T : TileBuffer>(
    override val maxMemoryBytes: Long = 128L * 1024 * 1024, // 128 MB ceiling from BASELINES.md
    override val tileSize: Int = 256
) : TileCachePolicy<T> {
    private val cache = LinkedHashMap<TileKey, T>(16, 0.75f, true)
    private var allocatedBytes: Long = 0L

    override val currentMemoryBytes: Long
        @Synchronized get() = allocatedBytes

    @Synchronized
    override fun getTile(key: TileKey): T? = cache[key]

    @Synchronized
    override fun putTile(key: TileKey, tile: T) {
        val size = tile.byteCount
        val old = cache.put(key, tile)
        if (old != null) {
            allocatedBytes -= old.byteCount
        }
        allocatedBytes += size
        trimToSize()
    }

    @Synchronized
    override fun evictTile(key: TileKey) {
        val removed = cache.remove(key)
        if (removed != null) {
            allocatedBytes -= removed.byteCount
        }
    }

    @Synchronized
    override fun clear() {
        cache.clear()
        allocatedBytes = 0L
    }

    @Synchronized
    override fun getTileCount(): Int = cache.size

    private fun trimToSize() {
        while (allocatedBytes > maxMemoryBytes && cache.isNotEmpty()) {
            val iterator = cache.entries.iterator()
            if (iterator.hasNext()) {
                val entry = iterator.next()
                allocatedBytes -= entry.value.byteCount
                iterator.remove()
            }
        }
    }
}
