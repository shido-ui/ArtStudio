package com.artstudio.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Validates tiled rendering, dirty region invalidation, LRU cache eviction,
 * memory-pressure handling, recoverable allocation failures, and benchmark baselines
 * for standard 2048x2048 and large 4096x4096 canvases.
 */
class TiledRenderingBenchmarkTest {

    private val tileSize = 256
    private val singleTileBytes = (256L * 256L * 4L) // 262,144 bytes (256 KB)

    @Test
    fun testTileGridCalculation_2048x2048And4096x4096() {
        // Standard Canvas: 2048 x 2048
        val engine2k = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 2048,
            canvasHeight = 2048,
            tileSize = 256
        )
        assertEquals(8, engine2k.totalCols)
        assertEquals(8, engine2k.totalRows)
        assertEquals(64, engine2k.totalTiles)

        // Large Canvas: 4096 x 4096
        val engine4k = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 4096,
            canvasHeight = 4096,
            tileSize = 256
        )
        assertEquals(16, engine4k.totalCols)
        assertEquals(16, engine4k.totalRows)
        assertEquals(256, engine4k.totalTiles)

        // Ultra-wide 4K UHD: 3840 x 2160
        val engineUhd = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 3840,
            canvasHeight = 2160,
            tileSize = 256
        )
        assertEquals(15, engineUhd.totalCols)
        assertEquals(9, engineUhd.totalRows)
        assertEquals(135, engineUhd.totalTiles)
    }

    @Test
    fun testDirtyRegionTracking_intersectsOnlyAffectedTiles() {
        val engine = TiledCanvasEngine<MemoryTileBuffer>(canvasWidth = 2048, canvasHeight = 2048)

        // Stroke in top-left quadrant (100, 100) with radius 15
        engine.markDirtyPoint(100f, 100f, 15f)

        val dirtyTiles = engine.getDirtyTiles()
        // Must intersect exactly tile (0, 0)
        assertEquals(1, dirtyTiles.size)
        assertEquals(Pair(0, 0), dirtyTiles[0])

        // Verify massive work reduction: 1 tile dirty out of 64 total tiles (98.44% saved)
        val reductionPercent = (1.0 - (dirtyTiles.size.toDouble() / engine.totalTiles)) * 100.0
        assertTrue("Work reduction was $reductionPercent%", reductionPercent >= 98.0)

        // Stroke spanning boundary between tile (0, 0) and tile (1, 0)
        engine.clearDirty()
        engine.markDirtyPoint(255f, 50f, 10f) // Spans across x=256 boundary
        val boundaryTiles = engine.getDirtyTiles()
        assertEquals(2, boundaryTiles.size)
        assertTrue(boundaryTiles.contains(Pair(0, 0)))
        assertTrue(boundaryTiles.contains(Pair(1, 0)))
    }

    @Test
    fun testLruTileCacheEviction_enforcesByteCeiling() {
        // Budget to hold at most 4 tiles (1 MB total)
        val maxBytes = singleTileBytes * 4
        val engine = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 2048,
            canvasHeight = 2048,
            maxMemoryBytes = maxBytes
        )

        // Insert 6 tiles
        for (i in 0 until 6) {
            val key = engine.getTileKey("layer_default", i, 0)
            engine.putTile(key, MemoryTileBuffer(256, 256, singleTileBytes))
        }

        // Cache must have strictly capped at 4 tiles / 1 MB
        assertEquals(4, engine.cache.getTileCount())
        assertEquals(maxBytes, engine.cache.currentMemoryBytes)

        // Tiles 0 and 1 must have been evicted by LRU
        assertNull(engine.getTile(engine.getTileKey("layer_default", 0, 0)))
        assertNull(engine.getTile(engine.getTileKey("layer_default", 1, 0)))

        // Tiles 2, 3, 4, 5 must still be in cache
        assertNotNull(engine.getTile(engine.getTileKey("layer_default", 2, 0)))
        assertNotNull(engine.getTile(engine.getTileKey("layer_default", 3, 0)))
        assertNotNull(engine.getTile(engine.getTileKey("layer_default", 4, 0)))
        assertNotNull(engine.getTile(engine.getTileKey("layer_default", 5, 0)))
    }

    @Test
    fun testMemoryPressureHandling_evictsOffscreenTiles() {
        // High capacity cache
        val engine = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 2048,
            canvasHeight = 2048,
            maxMemoryBytes = 64L * 1024 * 1024
        )

        // Populate 16 tiles (4x4 quadrant)
        for (c in 0 until 4) {
            for (r in 0 until 4) {
                val key = engine.getTileKey("layer_default", c, r)
                engine.putTile(key, MemoryTileBuffer(256, 256, singleTileBytes))
            }
        }
        assertEquals(16, engine.cache.getTileCount())

        // Viewport is currently zoomed in on top-left tile (0, 0) only: [0, 0, 250, 250]
        val visibleFrustum = CanvasRect(0f, 0f, 250f, 250f)
        val visibleTiles = engine.getVisibleTiles(visibleFrustum)
        assertEquals(1, visibleTiles.size)
        assertEquals(Pair(0, 0), visibleTiles[0])

        // Trigger MODERATE memory pressure with visible bounds
        engine.onTrimMemory(MemoryTrimLevel.MODERATE, visibleFrustum)

        // Only the visible tile (0, 0) must remain; all other 15 off-screen tiles evicted
        assertEquals(1, engine.cache.getTileCount())
        assertNotNull(engine.getTile(engine.getTileKey("layer_default", 0, 0)))
        assertNull(engine.getTile(engine.getTileKey("layer_default", 1, 1)))
        assertNull(engine.getTile(engine.getTileKey("layer_default", 3, 3)))
    }

    @Test
    fun testCriticalMemoryPressureHandling_clearsTileCache() {
        val engine = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 2048,
            canvasHeight = 2048,
            maxMemoryBytes = 32L * 1024 * 1024
        )

        for (i in 0 until 8) {
            engine.putTile(
                engine.getTileKey("layer_default", i, 0),
                MemoryTileBuffer(256, 256, singleTileBytes)
            )
        }
        assertTrue(engine.cache.getTileCount() > 0)

        // Trigger CRITICAL / LowMemory emergency
        engine.onLowMemory()

        assertEquals(0, engine.cache.getTileCount())
        assertEquals(0L, engine.cache.currentMemoryBytes)
    }

    @Test
    fun testRecoverableAllocationFailure_recoversFromOOM() {
        val engine = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 2048,
            canvasHeight = 2048,
            maxMemoryBytes = singleTileBytes * 4
        )

        // Populate cache to capacity
        for (i in 0 until 4) {
            engine.putTile(
                engine.getTileKey("layer_default", i, 0),
                MemoryTileBuffer(256, 256, singleTileBytes)
            )
        }
        assertEquals(4, engine.cache.getTileCount())

        // Enable simulated OOM allocation failure seam
        engine.simulatedAllocationFailureSeam = true

        val key = engine.getTileKey("layer_default", 5, 0)
        val allocated = engine.safeAllocateTile(key) {
            MemoryTileBuffer(256, 256, singleTileBytes)
        }

        // Must succeed via recovery without crashing
        assertNotNull(allocated)
        assertEquals(1, engine.recoverableAllocationCount)
        assertNotNull(engine.getTile(key))
    }

    @Test
    fun testDownsampledPreviewGeneration_boundedMemoryFootprint() {
        val engine2k = TiledCanvasEngine<MemoryTileBuffer>(canvasWidth = 2048, canvasHeight = 2048)
        val engine4k = TiledCanvasEngine<MemoryTileBuffer>(canvasWidth = 4096, canvasHeight = 4096)

        // 512 x 512 preview takes 1,048,576 bytes (1.0 MB)
        val previewBytes = (512L * 512L * 4L)
        val preview = MemoryTileBuffer(512, 512, previewBytes)

        engine2k.setLowCostPreview(preview)
        engine4k.setLowCostPreview(preview)

        assertEquals(previewBytes, engine2k.lowCostPreview?.byteCount)
        assertEquals(previewBytes, engine4k.lowCostPreview?.byteCount)

        // Compare full monolithic bitmap vs downsampled preview:
        // 2048x2048 monolithic: 16 MB. Preview: 1 MB (93.75% memory reduction)
        // 4096x4096 monolithic: 64 MB. Preview: 1 MB (98.44% memory reduction)
        val full2kBytes = 2048L * 2048L * 4L
        val full4kBytes = 4096L * 4096L * 4L

        assertTrue(previewBytes < full2kBytes / 10L)
        assertTrue(previewBytes < full4kBytes / 50L)
    }

    @Test
    fun testNoUnboundedBitmapCacheOrFullDocumentCopy() {
        // Enforce hard ceiling of 8 MB (32 tiles)
        val ceilingBytes = singleTileBytes * 32
        val engine = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 4096,
            canvasHeight = 4096,
            maxMemoryBytes = ceilingBytes
        )

        // Perform 500 successive drawing allocations
        for (i in 0 until 500) {
            val col = i % engine.totalCols
            val row = (i / engine.totalCols) % engine.totalRows
            val key = engine.getTileKey("layer_default", col, row)
            engine.putTile(key, MemoryTileBuffer(256, 256, singleTileBytes))

            // Hard invariant: memory NEVER exceeds ceiling
            assertTrue(
                "Memory footprint ${engine.cache.currentMemoryBytes} exceeded ceiling $ceilingBytes at iteration $i",
                engine.cache.currentMemoryBytes <= ceilingBytes
            )
        }

        assertEquals(32, engine.cache.getTileCount())
        assertEquals(ceilingBytes, engine.cache.currentMemoryBytes)
    }

    // --- REPEATABLE BENCHMARKS FOR 2048x2048 AND 4096x4096 ---

    @Test
    fun testBenchmark_2048x2048CanvasTiledRendering() {
        val engine = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 2048,
            canvasHeight = 2048,
            maxMemoryBytes = 32L * 1024 * 1024 // 32 MB ceiling
        )

        val iterations = 100
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            val x = (i * 17) % 2048f
            val y = (i * 23) % 2048f
            engine.markDirtyPoint(x, y, 12f)
            val dirtyTiles = engine.getDirtyTiles()

            for ((c, r) in dirtyTiles) {
                val key = engine.getTileKey("layer_default", c, r)
                if (engine.getTile(key) == null) {
                    engine.putTile(key, MemoryTileBuffer(256, 256, singleTileBytes))
                }
            }
            engine.clearDirty()
        }

        val totalTimeNs = System.nanoTime() - startTime
        val avgLatencyMs = (totalTimeNs / iterations.toDouble()) / 1_000_000.0

        val snapshot = engine.profiler.captureSnapshot(
            currentCacheBytes = engine.cache.currentMemoryBytes,
            maxCacheBytes = engine.maxMemoryBytes
        )

        println("=== 2048x2048 BENCHMARK RESULT ===")
        println("  Iterations:              $iterations")
        println("  Avg Dirty Update Latency: ${String.format("%.4f", avgLatencyMs)} ms")
        println("  Active Cached Tiles:     ${engine.cache.getTileCount()} / ${engine.totalTiles}")
        println("  Cache Memory Used:       ${engine.cache.currentMemoryBytes / 1024} KB (Ceiling: ${engine.maxMemoryBytes / 1024} KB)")
        println("  Cache Hit Rate:          ${String.format("%.1f", snapshot.cacheHitRatePercent)}%")
        println("  Memory Budget Met:       ${engine.cache.currentMemoryBytes <= engine.maxMemoryBytes}")

        assertTrue("Latency should be < 5ms per dirty update", avgLatencyMs < 5.0)
        assertTrue(engine.cache.currentMemoryBytes <= engine.maxMemoryBytes)
    }

    @Test
    fun testBenchmark_4096x4096LargeCanvasTiledRendering() {
        val engine = TiledCanvasEngine<MemoryTileBuffer>(
            canvasWidth = 4096,
            canvasHeight = 4096,
            maxMemoryBytes = 64L * 1024 * 1024 // 64 MB ceiling (vs 64 MB for single uncompressed layer)
        )

        val iterations = 100
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            val x = (i * 37) % 4096f
            val y = (i * 41) % 4096f
            engine.markDirtyPoint(x, y, 16f)
            val dirtyTiles = engine.getDirtyTiles()

            for ((c, r) in dirtyTiles) {
                val key = engine.getTileKey("layer_default", c, r)
                if (engine.getTile(key) == null) {
                    engine.putTile(key, MemoryTileBuffer(256, 256, singleTileBytes))
                }
            }
            engine.clearDirty()
        }

        val totalTimeNs = System.nanoTime() - startTime
        val avgLatencyMs = (totalTimeNs / iterations.toDouble()) / 1_000_000.0

        val snapshot = engine.profiler.captureSnapshot(
            currentCacheBytes = engine.cache.currentMemoryBytes,
            maxCacheBytes = engine.maxMemoryBytes
        )

        println("=== 4096x4096 LARGE CANVAS BENCHMARK RESULT ===")
        println("  Iterations:              $iterations")
        println("  Avg Dirty Update Latency: ${String.format("%.4f", avgLatencyMs)} ms")
        println("  Active Cached Tiles:     ${engine.cache.getTileCount()} / ${engine.totalTiles}")
        println("  Cache Memory Used:       ${engine.cache.currentMemoryBytes / 1024} KB (Ceiling: ${engine.maxMemoryBytes / 1024} KB)")
        println("  Cache Hit Rate:          ${String.format("%.1f", snapshot.cacheHitRatePercent)}%")
        println("  Memory Budget Met:       ${engine.cache.currentMemoryBytes <= engine.maxMemoryBytes}")

        assertTrue("Latency should be < 5ms per dirty update", avgLatencyMs < 5.0)
        assertTrue(engine.cache.currentMemoryBytes <= engine.maxMemoryBytes)
    }
}
