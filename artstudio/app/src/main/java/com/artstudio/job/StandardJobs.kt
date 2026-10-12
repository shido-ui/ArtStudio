package com.artstudio.job

import com.artstudio.document.model.ArtDocument
import com.artstudio.export.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/**
 * Synthetic long-running job for testing and benchmarking concurrency,
 * progress updates, cooperative cancellation, pausing, and process death.
 */
class SyntheticLongRunningJob(
    id: String = UUID.randomUUID().toString(),
    name: String = "Synthetic Job",
    lane: WorkLane = WorkLane.TRANSFORM,
    estimatedMemoryBytes: Long = 8 * 1024 * 1024L,
    retryPolicy: RetryPolicy = RetryPolicy.NO_RETRY,
    val totalSteps: Int = 10,
    val stepDelayMs: Long = 20L,
    val failAtStep: Int? = null,
    val failureError: JobError? = null,
    val tempFileDirectory: File? = null
) : StudioJob(id, name, lane, estimatedMemoryBytes, retryPolicy) {

    var completedSteps: Int = 0
        private set

    override suspend fun execute(context: JobExecutionContext): JobResult {
        // Register a temporary work file if directory is provided
        val tempFile = tempFileDirectory?.let { dir ->
            File(dir, "temp_work_${id}.tmp").also { f ->
                f.writeText("working_data")
                context.registerTempFile(f)
            }
        }

        while (completedSteps < totalSteps) {
            // Check cancellation cooperatively
            context.cancellationToken.throwIfCancelled()

            if (isPaused) {
                return JobResult.Paused
            }

            // Simulate simulated failure at specific step
            if (failAtStep != null && completedSteps == failAtStep) {
                val err = failureError ?: JobError(
                    code = "SYNTHETIC_FAILURE",
                    message = "Simulated failure at step $completedSteps",
                    isRecoverable = retryPolicy.maxRetries > 0
                )
                return JobResult.Failure(err)
            }

            delay(stepDelayMs)
            completedSteps++
            val progressFraction = completedSteps.toFloat() / totalSteps.toFloat()
            context.updateProgress(progressFraction, "Processed step $completedSteps of $totalSteps", completedSteps, totalSteps)
        }

        // Promote temp file if successfully completed
        tempFile?.let { context.unregisterTempFile(it) }

        return JobResult.Success("Completed $totalSteps steps successfully", tempFile)
    }

    override fun serializeCheckpoint(): String {
        return "$completedSteps,$totalSteps"
    }

    override fun restoreCheckpoint(data: String) {
        val parts = data.split(",")
        if (parts.size >= 2) {
            completedSteps = parts[0].toIntOrNull() ?: 0
        }
    }
}

/**
 * Asynchronous artwork export job delegating to [ArtworkExportService].
 */
class ExportJob(
    id: String = UUID.randomUUID().toString(),
    name: String = "Export Artwork",
    val document: ArtDocument,
    val config: ExportConfig,
    val targetFile: File,
    val exportService: ArtworkExportService = ArtworkExportService(),
    layerPixelBuffers: Map<String, IntArray> = emptyMap(),
    layerMaskBuffers: Map<String, IntArray> = emptyMap(),
    retryPolicy: RetryPolicy = RetryPolicy.DEFAULT_IO
) : StudioJob(id, name, WorkLane.EXPORT, estimatedMemoryBytes = 16 * 1024 * 1024L, retryPolicy) {

    val layerPixelBuffers: Map<String, IntArray> = layerPixelBuffers.mapValues { it.value.clone() }
    val layerMaskBuffers: Map<String, IntArray> = layerMaskBuffers.mapValues { it.value.clone() }

    override suspend fun execute(context: JobExecutionContext): JobResult {
        context.updateProgress(0.1f, "Initializing export job...", 1, 4)

        // Set up bridge between JobCancellationToken and ExportCancellationToken
        val exportCancelToken = ExportCancellationToken()
        context.cancellationToken.onCancel {
            exportCancelToken.cancel()
        }

        // Register shadow file for safe cleanup if aborted
        val shadowFile = File(targetFile.parentFile ?: File("."), ".export_tmp_${targetFile.name}")
        context.registerTempFile(shadowFile)

        context.updateProgress(0.4f, "Compositing and encoding ${config.format.name}...", 2, 4)

        val result = exportService.exportArtwork(
            document = document,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = layerPixelBuffers,
            layerMaskBuffers = layerMaskBuffers,
            cancellationToken = exportCancelToken
        )

        return when (result) {
            is ExportResult.Success -> {
                context.unregisterTempFile(shadowFile)
                context.updateProgress(1.0f, "Export complete (${result.fileSize} bytes)", 4, 4)
                JobResult.Success("Exported ${config.format.name} successfully", targetFile)
            }
            is ExportResult.Cancelled -> {
                JobResult.Cancelled
            }
            is ExportResult.Error -> {
                val isRecoverable = result.errorCode == ExportErrorCode.PERMISSION_DENIED_OR_IO
                val err = JobError(code = result.errorCode.name, message = result.reason, isRecoverable = isRecoverable)
                JobResult.Failure(err)
            }
        }
    }
}

/**
 * Asynchronous background job executing heavy raster processing (filters, adjustments,
 * bicubic resamplings, mesh warps) against an immutable snapshot.
 * Coordinates progress updates and cooperative cancellation through [JobScheduler].
 */
class RasterProcessingJob(
    id: String = UUID.randomUUID().toString(),
    name: String = "Raster Processing",
    lane: WorkLane = WorkLane.TRANSFORM,
    val sourceSnapshot: IntArray,
    val width: Int,
    val height: Int,
    retryPolicy: RetryPolicy = RetryPolicy.NO_RETRY,
    val processor: suspend (snapshot: IntArray, width: Int, height: Int, context: JobExecutionContext) -> IntArray
) : StudioJob(id, name, lane, estimatedMemoryBytes = width.toLong() * height * 4L * 2L, retryPolicy) {

    var resultPixels: IntArray? = null
        private set

    override suspend fun execute(context: JobExecutionContext): JobResult {
        context.updateProgress(0.1f, "Initializing $name...", 1, 4)
        context.cancellationToken.throwIfCancelled()

        val inputCopy = sourceSnapshot.copyOf()
        context.updateProgress(0.3f, "Executing $name on background worker...", 2, 4)
        context.cancellationToken.throwIfCancelled()

        val output = processor(inputCopy, width, height, context)
        context.cancellationToken.throwIfCancelled()

        context.updateProgress(0.9f, "Finalizing output...", 3, 4)
        resultPixels = output

        context.updateProgress(1.0f, "$name complete", 4, 4)
        return JobResult.Success("$name completed successfully")
    }
}
