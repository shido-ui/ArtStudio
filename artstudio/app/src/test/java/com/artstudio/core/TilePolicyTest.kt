package com.artstudio.core

import org.junit.Assert.*
import org.junit.Test

class TilePolicyTest {

    @Test
    fun testTileKeyEqualityAndHash() {
        val key1 = TileKey("layer_1", 2, 3)
        val key2 = TileKey("layer_1", 2, 3)
        val key3 = TileKey("layer_1", 2, 4)

        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())
        assertNotEquals(key1, key3)
    }

    @Test
    fun testPutAndGetTile() {
        val cache = LruTileCache<MemoryTileBuffer>(maxMemoryBytes = 10L * 1024 * 1024)
        val key = TileKey("layer_0", 0, 0)
        val tile = MemoryTileBuffer(256, 256, 262144L)

        cache.putTile(key, tile)

        assertEquals(1, cache.getTileCount())
        assertEquals(tile, cache.getTile(key))
    }

    @Test
    fun testLruEvictionWhenCapacityExceeded() {
        // Each tile is 262,144 bytes
        // Set max capacity to hold exactly 2 tiles (524,288 bytes)
        val singleTileBytes = 262144L
        val maxBytes = singleTileBytes * 2
        val cache = LruTileCache<MemoryTileBuffer>(maxMemoryBytes = maxBytes)

        val key1 = TileKey("layer_0", 0, 0)
        val key2 = TileKey("layer_0", 1, 0)
        val key3 = TileKey("layer_0", 2, 0)

        val tile1 = MemoryTileBuffer(256, 256, singleTileBytes)
        val tile2 = MemoryTileBuffer(256, 256, singleTileBytes)
        val tile3 = MemoryTileBuffer(256, 256, singleTileBytes)

        cache.putTile(key1, tile1)
        cache.putTile(key2, tile2)
        assertEquals(2, cache.getTileCount())

        // Access key1 to make key2 the least recently used
        cache.getTile(key1)

        // Adding key3 must evict key2
        cache.putTile(key3, tile3)

        assertEquals(2, cache.getTileCount())
        assertNotNull(cache.getTile(key1))
        assertNotNull(cache.getTile(key3))
        assertNull(cache.getTile(key2)) // key2 must have been evicted
    }
}
