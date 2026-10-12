package com.artstudio.storage

import com.artstudio.document.lifecycle.DefaultStorageDriver
import com.artstudio.document.lifecycle.StorageDriver
import com.artstudio.document.model.ArtDocument
import java.io.File
import kotlin.math.roundToLong

/**
 * Authoritative storage health and pre-job warning orchestrator.
 *
 * Guarantees:
 * 1. Accurately estimates disk requirements before initiating heavy jobs.
 * 2. Warns users before a job risks exhausting storage headroom.
 * 3. Gracefully halts on insufficient storage, preventing mid-write crashes or corrupted files.
 * 4. Exposes explicit Retry, Reduce-Quality, and Free-Space recovery choices.
 */
class StorageSpaceManager(
    val tempFileManager: BoundedTempFileManager? = null,
    val storageDriver: StorageDriver = DefaultStorageDriver(),
    val warningMarginRatio: Double = 3.0, // Warn if free space is less than 3x required
    val warningFloorBytes: Long = 50 * 1024 * 1024L // Warn if free space is less than 50 MB
) {

    /**
     * Estimates storage needed for saving a native project document.
     */
    fun estimateSave(document: ArtDocument, assetBytes: Long = 0L): StorageEstimate {
        val layerCount = countLayers(document.rootGroup)
        val pixelBufferEstimate = document.canvas.width.toLong() * document.canvas.height * 4L * layerCount.coerceAtLeast(1)
        // Highly compressed ZIP package is typically ~10-25% of raw pixel buffers
        val estimatedZipBytes = (pixelBufferEstimate * 0.20).roundToLong() + assetBytes + 64 * 1024L // 64 KB JSON overhead
        return StorageEstimate(
            jobType = JobType.PROJECT_SAVE,
            estimatedBytesNeeded = estimatedZipBytes.coerceAtLeast(1024 * 1024L), // At least 1 MB
            safetyHeadroomBytes = 10 * 1024 * 1024L, // 10 MB headroom
            description = "Save project '${document.title}' ($layerCount layers, ${document.canvas.width}x${document.canvas.height})"
        )
    }

    /**
     * Estimates storage needed for exporting artwork to raster/vector formats.
     */
    fun estimateExport(
        width: Int,
        height: Int,
        format: String,
        scaleFactor: Float = 1.0f
    ): StorageEstimate {
        val scaledW = (width * scaleFactor).roundToLong().coerceAtLeast(1L)
        val scaledH = (height * scaleFactor).roundToLong().coerceAtLeast(1L)
        val rawPixels = scaledW * scaledH * 4L

        val compressionFactor = when (format.uppercase()) {
            "PNG" -> 0.40
            "JPEG", "JPG" -> 0.15
            "WEBP" -> 0.12
            "ORA", "PSD" -> 0.60
            "SVG" -> 0.05
            else -> 0.50
        }
        val estimatedExportBytes = (rawPixels * compressionFactor).roundToLong().coerceAtLeast(64 * 1024L)
        return StorageEstimate(
            jobType = JobType.ARTWORK_EXPORT,
            estimatedBytesNeeded = estimatedExportBytes,
            safetyHeadroomBytes = 5 * 1024 * 1024L,
            description = "Export $format (${scaledW}x${scaledH} @ ${scaleFactor}x scale)"
        )
    }

    /**
     * Estimates storage and temporary memory needed for importing a raster image.
     */
    fun estimateImport(sourceFileBytes: Long, targetWidth: Int, targetHeight: Int): StorageEstimate {
        val uncompressedRaw = targetWidth.toLong() * targetHeight * 4L
        val estimatedBytes = sourceFileBytes + uncompressedRaw
        return StorageEstimate(
            jobType = JobType.RASTER_IMPORT,
            estimatedBytesNeeded = estimatedBytes.coerceAtLeast(1024 * 1024L),
            safetyHeadroomBytes = 10 * 1024 * 1024L,
            description = "Import raster image ($sourceFileBytes bytes -> ${targetWidth}x${targetHeight})"
        )
    }

    /**
     * Estimates storage needed for CV contour analysis or AutoDraw generation.
     */
    fun estimateAnalysis(width: Int, height: Int, regionCount: Int = 10): StorageEstimate {
        val maskBuffers = (width.toLong() * height * 2L) * 2 // binary mask + distance field
        val vectorCurveData = regionCount * 256 * 1024L // ~256 KB per region for paths
        val total = maskBuffers + vectorCurveData
        return StorageEstimate(
            jobType = JobType.ANALYSIS_PROCESSING,
            estimatedBytesNeeded = total.coerceAtLeast(2 * 1024 * 1024L),
            safetyHeadroomBytes = 10 * 1024 * 1024L,
            description = "AutoDraw/CV analysis (${width}x${height}, $regionCount regions)"
        )
    }

    /**
     * Diagnostic health check evaluating available storage against required job resources.
     */
    fun checkStorageHealth(
        directory: File,
        estimate: StorageEstimate
    ): StorageHealthReport {
        val freeSpace = storageDriver.getFreeSpaceBytes(directory)
        val neededTotal = estimate.totalRequiredBytes

        val tempSavings = tempFileManager?.currentUsageBytes ?: 0L

        val recoveryActions = mutableListOf<FailureRecoveryOption>()
        recoveryActions.add(
            FailureRecoveryOption(
                type = RecoveryActionType.RETRY,
                label = "Retry Operation",
                description = "Retry after freeing device storage or closing background applications."
            )
        )
        recoveryActions.add(
            FailureRecoveryOption(
                type = RecoveryActionType.REDUCE_QUALITY,
                label = "Reduce Output Quality / Scale",
                description = "Downscale resolution or dimensions by 50% to reduce storage needs by up to 75%.",
                estimatedSavingsBytes = (estimate.estimatedBytesNeeded * 0.75).roundToLong()
            )
        )
        if (tempSavings > 0L) {
            recoveryActions.add(
                FailureRecoveryOption(
                    type = RecoveryActionType.FREE_TEMP_SPACE,
                    label = "Free Temporary Storage",
                    description = "Purge unused cache and intermediate files safely without touching saved projects.",
                    estimatedSavingsBytes = tempSavings
                )
            )
        }

        return when {
            freeSpace < neededTotal -> {
                StorageHealthReport(
                    state = StorageHealthState.CRITICAL_INSUFFICIENT,
                    freeSpaceBytes = freeSpace,
                    estimate = estimate,
                    warningMessage = "Critically low storage! Operation requires ${formatBytes(neededTotal)}, but only ${formatBytes(freeSpace)} is available.",
                    availableRecoveryActions = recoveryActions
                )
            }
            freeSpace < (neededTotal * warningMarginRatio).roundToLong() || freeSpace < warningFloorBytes -> {
                StorageHealthReport(
                    state = StorageHealthState.LOW_STORAGE_WARNING,
                    freeSpaceBytes = freeSpace,
                    estimate = estimate,
                    warningMessage = "Warning: Device storage is running low (${formatBytes(freeSpace)} remaining). This job may leave minimal space.",
                    availableRecoveryActions = recoveryActions
                )
            }
            else -> {
                StorageHealthReport(
                    state = StorageHealthState.SUFFICIENT,
                    freeSpaceBytes = freeSpace,
                    estimate = estimate,
                    warningMessage = null,
                    availableRecoveryActions = emptyList()
                )
            }
        }
    }

    /**
     * Executes an operation protected by storage health guards.
     *
     * @param directory Target storage partition
     * @param estimate Pre-calculated estimate
     * @param onWarning Callback invoked when storage is tight. Return true to proceed, false to cancel.
     * @param block Operation to execute if storage is safe.
     */
    fun <T> executeWithStorageGuard(
        directory: File,
        estimate: StorageEstimate,
        onWarning: ((StorageHealthReport) -> Boolean)? = null,
        block: () -> T
    ): Result<T> {
        val health = checkStorageHealth(directory, estimate)

        if (health.state == StorageHealthState.CRITICAL_INSUFFICIENT) {
            return Result.failure(InsufficientStorageException(health))
        }

        if (health.state == StorageHealthState.LOW_STORAGE_WARNING && onWarning != null) {
            val shouldProceed = onWarning(health)
            if (!shouldProceed) {
                return Result.failure(java.util.concurrent.CancellationException("Job cancelled by user on low storage warning"))
            }
        }

        return try {
            Result.success(block())
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Derives a reduced-quality estimate by applying a scale multiplier (default 50%).
     */
    fun resolveReducedQualityEstimate(
        original: StorageEstimate,
        scaleMultiplier: Float = 0.5f
    ): StorageEstimate {
        val areaMultiplier = (scaleMultiplier * scaleMultiplier).toDouble()
        val reducedBytes = (original.estimatedBytesNeeded * areaMultiplier).roundToLong().coerceAtLeast(128 * 1024L)
        return original.copy(
            estimatedBytesNeeded = reducedBytes,
            description = "${original.description} (Reduced ${scaleMultiplier * 100}% scale)"
        )
    }

    private fun countLayers(group: com.artstudio.document.model.LayerNode.LayerGroup): Int {
        var count = 0
        for (child in group.children) {
            when (child) {
                is com.artstudio.document.model.LayerNode.RasterLayer -> count++
                is com.artstudio.document.model.LayerNode.VectorPathLayer -> count++
                is com.artstudio.document.model.LayerNode.TextLayer -> count++
                is com.artstudio.document.model.LayerNode.AdjustmentLayer -> count++
                is com.artstudio.document.model.LayerNode.LayerGroup -> count += countLayers(child)
            }
        }
        return count
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> "%.2f KB".format(bytes.toDouble() / 1024)
            else -> "$bytes bytes"
        }
    }
}
