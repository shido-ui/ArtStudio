package com.artstudio.job

import java.io.File
import java.util.UUID

/**
 * Lifecycle states of an asynchronous Studio job.
 */
enum class JobState {
    /** Job is waiting in its designated work lane queue. */
    QUEUED,

    /** Job is actively executing on a background worker thread. */
    RUNNING,

    /** Job execution has been paused and can be resumed. */
    PAUSED,

    /** Job finished successfully; output is verified. */
    SUCCEEDED,

    /** Job failed due to an error. May be retried if policy permits. */
    FAILED,

    /** Job was cancelled cooperatively and intermediate temporary files were cleaned up. */
    CANCELLED
}

/**
 * Work lanes segregating workloads by priority, execution model, and concurrency bounds.
 */
enum class WorkLane(val displayName: String, val defaultMaxConcurrency: Int, val priority: Int) {
    /**
     * Immediate priority: interactive drawing, touch input, viewport transformation.
     * Guaranteed never to be starved or queued behind background batch jobs.
     */
    CANVAS_INPUT("Canvas Input", defaultMaxConcurrency = 4, priority = 0),

    /**
     * Heavy matrix transformations, mesh deformation, and bicubic resamplings.
     * Bounded to strictly 1 worker to prevent memory thrashing.
     */
    TRANSFORM("Layer Transform", defaultMaxConcurrency = 1, priority = 1),

    /**
     * File serialization and compression (PNG, ORA, PSD, GIF, MP4, ZIP).
     * Bounded to 2 workers to prevent disk I/O saturation.
     */
    EXPORT("Artwork Export", defaultMaxConcurrency = 2, priority = 2),

    /**
     * Computer vision, contour vectorization, and line art discovery.
     * Bounded to 1 worker.
     */
    ANALYSIS("Line Art Analysis", defaultMaxConcurrency = 1, priority = 3),

    /**
     * Optional background pattern classification or ML inference.
     * Lowest priority, bounded to 1 worker, memory-gated.
     */
    AI_INFERENCE("AI Inference", defaultMaxConcurrency = 1, priority = 4)
}

/**
 * Real-time execution progress details for a running job.
 * Guarantees zero fake completion states: progress 1.0f only accompanies verified success.
 */
data class JobProgress(
    val progress: Float = 0.0f,
    val currentStep: String = "",
    val stepIndex: Int = 0,
    val totalSteps: Int = 1,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L
) {
    init {
        require(progress in 0.0f..1.0f) { "Progress must be in [0.0, 1.0], was $progress" }
    }

    val percentage: Int get() = (progress * 100f).toInt()
}

/**
 * Detailed error context when a job fails.
 */
data class JobError(
    val code: String,
    val message: String,
    val isRecoverable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val cause: Throwable? = null
)

/**
 * Configurable retry and backoff policy for transient failures.
 */
data class RetryPolicy(
    val maxRetries: Int = 0,
    val initialBackoffMs: Long = 50L,
    val backoffMultiplier: Double = 2.0,
    val isResumable: Boolean = false
) {
    companion object {
        val NO_RETRY = RetryPolicy(maxRetries = 0)
        val DEFAULT_IO = RetryPolicy(maxRetries = 3, initialBackoffMs = 50L, isResumable = false)
        val RESUMABLE_BATCH = RetryPolicy(maxRetries = 2, initialBackoffMs = 100L, isResumable = true)
    }
}

/**
 * Result returned by a job's execution block.
 */
sealed class JobResult {
    data class Success(val message: String = "Success", val outputArtifact: File? = null) : JobResult()
    data class Failure(val error: JobError) : JobResult()
    object Cancelled : JobResult()
    object Paused : JobResult()
}

/**
 * Immutable snapshot of a job for UI and external monitoring.
 */
data class JobSnapshot(
    val id: String,
    val name: String,
    val lane: WorkLane,
    val state: JobState,
    val progress: JobProgress,
    val error: JobError? = null,
    val retryCount: Int = 0,
    val isResumable: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
