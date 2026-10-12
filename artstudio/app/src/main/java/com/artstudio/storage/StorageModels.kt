package com.artstudio.storage

import java.io.File

/**
 * Categorization of studio background and I/O workloads.
 */
enum class JobType {
    PROJECT_SAVE,
    ARTWORK_EXPORT,
    RASTER_IMPORT,
    ANALYSIS_PROCESSING
}

/**
 * Storage health state evaluating disk space headroom before and during execution.
 */
enum class StorageHealthState {
    /** Disk storage is healthy with abundant headroom. */
    SUFFICIENT,

    /** Disk storage is approaching low limits; user should be warned before risking exhaustion. */
    LOW_STORAGE_WARNING,

    /** Available disk space is below required job size + safety buffer; execution must stop gracefully. */
    CRITICAL_INSUFFICIENT
}

/**
 * Concrete recovery action exposed to the user or orchestrator when storage/memory is low.
 */
enum class RecoveryActionType {
    /** Retry the operation after external disk space is freed or permissions are restored. */
    RETRY,

    /** Reduce resolution, downsample budget, or export scale factor to drastically cut storage requirements. */
    REDUCE_QUALITY,

    /** Safely purge temporary scratch files and expired caches without touching valid projects or recovery checkpoints. */
    FREE_TEMP_SPACE
}

/**
 * Actionable recovery option presented to the user.
 */
data class FailureRecoveryOption(
    val type: RecoveryActionType,
    val label: String,
    val description: String,
    val estimatedSavingsBytes: Long? = null
)

/**
 * Storage requirement estimate calculated before launching a job.
 */
data class StorageEstimate(
    val jobType: JobType,
    val estimatedBytesNeeded: Long,
    val safetyHeadroomBytes: Long = 10 * 1024 * 1024L, // 10 MB default safety headroom
    val description: String = ""
) {
    val totalRequiredBytes: Long
        get() = estimatedBytesNeeded + safetyHeadroomBytes
}

/**
 * Diagnostic health report evaluating whether a job can safely proceed.
 */
data class StorageHealthReport(
    val state: StorageHealthState,
    val freeSpaceBytes: Long,
    val estimate: StorageEstimate,
    val warningMessage: String? = null,
    val availableRecoveryActions: List<FailureRecoveryOption> = emptyList()
) {
    val canProceedSafely: Boolean
        get() = state != StorageHealthState.CRITICAL_INSUFFICIENT
}

/**
 * Summary of a safe temporary storage cleanup operation.
 */
data class CleanupResult(
    val freedBytes: Long,
    val deletedFilesCount: Int,
    val protectedFilesPreservedCount: Int,
    val protectedFilePaths: List<String> = emptyList()
)

/**
 * Exception raised when storage is critically insufficient, halting execution gracefully.
 */
class InsufficientStorageException(
    val report: StorageHealthReport
) : java.io.IOException(
    "Insufficient storage: requires ${report.estimate.totalRequiredBytes} bytes (estimated ${report.estimate.estimatedBytesNeeded} + ${report.estimate.safetyHeadroomBytes} headroom), only ${report.freeSpaceBytes} available"
)
