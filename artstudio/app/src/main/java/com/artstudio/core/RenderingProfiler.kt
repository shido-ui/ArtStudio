package com.artstudio.core

/**
 * Snapshot of rendering performance and memory metrics.
 */
data class ProfilingSnapshot(
    val frameDurationMs: Float,
    val tilesRendered: Int,
    val tilesTotal: Int,
    val dirtyTilesUpdated: Int,
    val dirtyAreaPixels: Float,
    val canvasAreaPixels: Float,
    val cacheHits: Long,
    val cacheMisses: Long,
    val cacheHitRatePercent: Float,
    val cacheMemoryBytes: Long,
    val maxCacheMemoryBytes: Long,
    val heapMemoryUsedBytes: Long,
    val timestampNano: Long = System.nanoTime()
) {
    val dirtyAreaRatioPercent: Float
        get() = if (canvasAreaPixels > 0f) (dirtyAreaPixels / canvasAreaPixels) * 100f else 0f

    val memoryUtilizationPercent: Float
        get() = if (maxCacheMemoryBytes > 0L) (cacheMemoryBytes.toFloat() / maxCacheMemoryBytes) * 100f else 0f
}

/**
 * Thread-safe repeatable profiling hooks for rendering, caching, and allocation metrics.
 */
class RenderingProfiler {
    private var totalFrames: Long = 0L
    private var totalRenderDurationNs: Long = 0L
    private var lastFrameDurationMs: Float = 0f

    private var cacheHits: Long = 0L
    private var cacheMisses: Long = 0L

    private var lastTilesRendered: Int = 0
    private var lastTilesTotal: Int = 0
    private var lastDirtyTiles: Int = 0
    private var lastDirtyArea: Float = 0f
    private var lastCanvasArea: Float = 0f

    fun recordFrame(durationMs: Float, tilesRendered: Int, tilesTotal: Int) {
        synchronized(this) {
            totalFrames++
            totalRenderDurationNs += (durationMs * 1_000_000f).toLong()
            lastFrameDurationMs = durationMs
            lastTilesRendered = tilesRendered
            lastTilesTotal = tilesTotal
        }
    }

    fun recordDirtyUpdate(dirtyTiles: Int, dirtyArea: Float, canvasArea: Float) {
        synchronized(this) {
            lastDirtyTiles = dirtyTiles
            lastDirtyArea = dirtyArea
            lastCanvasArea = canvasArea
        }
    }

    fun recordCacheHit() {
        synchronized(this) {
            cacheHits++
        }
    }

    fun recordCacheMiss() {
        synchronized(this) {
            cacheMisses++
        }
    }

    fun resetCounters() {
        synchronized(this) {
            totalFrames = 0L
            totalRenderDurationNs = 0L
            cacheHits = 0L
            cacheMisses = 0L
        }
    }

    fun captureSnapshot(
        currentCacheBytes: Long,
        maxCacheBytes: Long
    ): ProfilingSnapshot {
        synchronized(this) {
            val totalLookups = cacheHits + cacheMisses
            val hitRate = if (totalLookups > 0L) (cacheHits.toFloat() / totalLookups) * 100f else 100f

            val runtime = Runtime.getRuntime()
            val heapUsed = runtime.totalMemory() - runtime.freeMemory()

            return ProfilingSnapshot(
                frameDurationMs = lastFrameDurationMs,
                tilesRendered = lastTilesRendered,
                tilesTotal = lastTilesTotal,
                dirtyTilesUpdated = lastDirtyTiles,
                dirtyAreaPixels = lastDirtyArea,
                canvasAreaPixels = lastCanvasArea,
                cacheHits = cacheHits,
                cacheMisses = cacheMisses,
                cacheHitRatePercent = hitRate,
                cacheMemoryBytes = currentCacheBytes,
                maxCacheMemoryBytes = maxCacheBytes,
                heapMemoryUsedBytes = heapUsed
            )
        }
    }
}
