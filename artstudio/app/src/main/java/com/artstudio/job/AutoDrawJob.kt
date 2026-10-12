package com.artstudio.job

import com.artstudio.autodraw.AutoDrawV1Pipeline
import com.artstudio.autodraw.AutoDrawV1Result
import java.util.UUID

/**
 * Background AutoDraw line art analysis job executed via [JobScheduler] in [WorkLane.ANALYSIS].
 * Guarantees off-main-thread execution, cooperative cancellation, bounded memory, and progress telemetry.
 */
class AutoDrawJob(
    id: String = UUID.randomUUID().toString(),
    name: String = "AutoDraw Linework Analysis",
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val maxDimension: Int = AutoDrawV1Pipeline.DEFAULT_MAX_DIMENSION,
    val normalizeIllumination: Boolean = true,
    estimatedMemoryBytes: Long = 24 * 1024 * 1024L
) : StudioJob(
    id = id,
    name = name,
    lane = WorkLane.ANALYSIS,
    estimatedMemoryBytes = estimatedMemoryBytes,
    retryPolicy = RetryPolicy.NO_RETRY
) {
    var result: AutoDrawV1Result? = null
        private set

    override suspend fun execute(context: JobExecutionContext): JobResult {
        context.cancellationToken.throwIfCancelled()
        context.updateProgress(0.05f, "Preparing line art analysis", 1, 5)

        return try {
            val res = AutoDrawV1Pipeline.processLineArt(
                pixels = pixels,
                width = width,
                height = height,
                maxDimension = maxDimension,
                normalizeIllumination = normalizeIllumination,
                onProgress = { p, stage ->
                    context.cancellationToken.throwIfCancelled()
                    val stepIndex = when {
                        p < 0.20f -> 1
                        p < 0.40f -> 2
                        p < 0.70f -> 3
                        p < 0.90f -> 4
                        else -> 5
                    }
                    context.updateProgress(p, stage, stepIndex, 5)
                }
            )

            context.cancellationToken.throwIfCancelled()
            result = res
            JobResult.Success(
                message = "Extracted ${res.contours.size} contours and ${res.vectorObjects.size} vector paths in ${res.executionTimeMs} ms"
            )
        } catch (_: JobCancelledException) {
            JobResult.Cancelled
        } catch (_: kotlinx.coroutines.CancellationException) {
            JobResult.Cancelled
        } catch (e: Throwable) {
            JobResult.Failure(
                JobError(
                    code = "AUTODRAW_ANALYSIS_FAILED",
                    message = e.message ?: "Failed to analyze line art",
                    isRecoverable = false
                )
            )
        }
    }
}
