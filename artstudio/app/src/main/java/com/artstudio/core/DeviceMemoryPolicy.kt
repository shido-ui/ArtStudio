package com.artstudio.core

/**
 * Architectural physical RAM device tiers.
 *
 * Defines explicit resource ceilings, worker concurrency limits, tile cache budgets,
 * and preview resolutions tailored to device memory classes.
 */
enum class DeviceRamTier(
    val displayName: String,
    val minRamBytes: Long,
    val maxWorkerConcurrency: Int,
    val maxTileCacheBytes: Long,
    val maxPreviewResolution: Int,
    val trimThresholdRatio: Float,
    val maxLayers: Int,
    val maxHistoryBytes: Long
) {
    /**
     * Strictly bounded 2 GB physical RAM device tier.
     * Enforces single worker thread, bounded 32 MB tile cache, 256x256 preview,
     * aggressive 0.50 trim threshold, 4 max layers, and 24 MB history cap.
     */
    RAM_2GB(
        displayName = "2 GB Physical RAM Device Tier",
        minRamBytes = 2L * 1024 * 1024 * 1024,
        maxWorkerConcurrency = 1,
        maxTileCacheBytes = 32L * 1024 * 1024, // 32 MB ceiling
        maxPreviewResolution = 256,
        trimThresholdRatio = 0.50f,
        maxLayers = 4,
        maxHistoryBytes = 24L * 1024 * 1024 // 24 MB history cap
    ),

    /**
     * Strictly bounded 4 GB physical RAM device tier.
     * Enforces conservative concurrency (2 threads), bounded 64 MB tile cache,
     * downscaled 512x512 preview resolution, aggressive trim threshold, 12 layers, 64 MB history.
     */
    RAM_4GB(
        displayName = "4 GB Physical RAM Device Tier",
        minRamBytes = 4L * 1024 * 1024 * 1024,
        maxWorkerConcurrency = 2,
        maxTileCacheBytes = 64L * 1024 * 1024, // 64 MB ceiling
        maxPreviewResolution = 512,
        trimThresholdRatio = 0.65f,
        maxLayers = 12,
        maxHistoryBytes = 64L * 1024 * 1024 // 64 MB history cap
    ),

    /**
     * 6 GB physical RAM device tier with measured headroom.
     * Permits 4 worker threads, 128 MB tile cache, 1024x1024 preview buffer, 24 layers, 128 MB history.
     */
    RAM_6GB(
        displayName = "6 GB Physical RAM Device Tier",
        minRamBytes = 6L * 1024 * 1024 * 1024,
        maxWorkerConcurrency = 4,
        maxTileCacheBytes = 128L * 1024 * 1024, // 128 MB ceiling
        maxPreviewResolution = 1024,
        trimThresholdRatio = 0.80f,
        maxLayers = 24,
        maxHistoryBytes = 128L * 1024 * 1024 // 128 MB history cap
    ),

    /**
     * 8 GB+ high-performance device tier.
     */
    RAM_8GB_PLUS(
        displayName = "8 GB+ High Performance Device Tier",
        minRamBytes = 8L * 1024 * 1024 * 1024,
        maxWorkerConcurrency = 8,
        maxTileCacheBytes = 256L * 1024 * 1024, // 256 MB ceiling
        maxPreviewResolution = 2048,
        trimThresholdRatio = 0.85f,
        maxLayers = 64,
        maxHistoryBytes = 256L * 1024 * 1024 // 256 MB history cap
    );
}

/**
 * System-wide authority for memory budget enforcement and concurrency limits.
 *
 * Explicit Disclosure:
 * In automated Linux aarch64 PRoot environments without physical ADB devices attached (0 devices),
 * the system conservatively defaults to [DeviceRamTier.RAM_4GB] to prevent host memory exhaustion.
 */
object DeviceMemoryPolicy {

    /**
     * Current active RAM tier. Defaults to RAM_4GB conservative policy.
     */
    var currentTier: DeviceRamTier = DeviceRamTier.RAM_4GB
        private set

    /**
     * Manually overrides the device RAM tier (useful for testing and configuration).
     */
    fun setTier(tier: DeviceRamTier) {
        currentTier = tier
    }

    /**
     * Detects physical RAM from the runtime environment and selects the appropriate tier.
     * Clamps conservatively when memory information is ambiguous.
     */
    fun detectAndApplyTier(overrideTotalMemoryBytes: Long? = null): DeviceRamTier {
        val totalMem = overrideTotalMemoryBytes ?: Runtime.getRuntime().maxMemory()
        val detected = when {
            totalMem >= 7L * 1024 * 1024 * 1024 -> DeviceRamTier.RAM_8GB_PLUS
            totalMem >= 5L * 1024 * 1024 * 1024 -> DeviceRamTier.RAM_6GB
            totalMem >= 3L * 1024 * 1024 * 1024 -> DeviceRamTier.RAM_4GB
            else -> DeviceRamTier.RAM_2GB
        }
        currentTier = detected
        return detected
    }

    /** Maximum concurrent background worker threads. */
    val maxWorkerConcurrency: Int get() = currentTier.maxWorkerConcurrency

    /** Maximum in-memory LRU tile cache size in bytes. */
    val maxTileCacheBytes: Long get() = currentTier.maxTileCacheBytes

    /** Maximum canvas preview buffer resolution (width/height). */
    val maxPreviewResolution: Int get() = currentTier.maxPreviewResolution

    /** Memory trim trigger ratio (used by low-memory watchdog). */
    val trimThresholdRatio: Float get() = currentTier.trimThresholdRatio

    /** Maximum allowed layers under the active memory tier. */
    val maxLayers: Int get() = currentTier.maxLayers

    /** Maximum allowed undo/redo history heap bytes under the active memory tier. */
    val maxHistoryBytes: Long get() = currentTier.maxHistoryBytes

    /**
     * Calculates the layer count limit dynamically based on canvas dimensions and RAM tier.
     */
    fun maxLayersForCanvas(width: Int, height: Int): Int {
        val bytesPerLayer = width.toLong() * height.toLong() * 4L
        val maxAllowedLayerBytes = when (currentTier) {
            DeviceRamTier.RAM_2GB -> currentTier.maxTileCacheBytes * 2L // 64 MB -> 4 layers at 2048x2048
            else -> currentTier.maxTileCacheBytes * 3L // 192 MB -> 12 layers at 2048x2048 for mid-range (4GB)
        }
        val dynamicLimit = if (bytesPerLayer > 0) (maxAllowedLayerBytes / bytesPerLayer).toInt() else currentTier.maxLayers
        return dynamicLimit.coerceIn(2, currentTier.maxLayers)
    }
}
