package com.artstudio.ai.model

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Lifecycle state of a model download task.
 */
sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    object Queued : ModelDownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState()
    data class Paused(val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState()
    object Verifying : ModelDownloadState()
    object Downloaded : ModelDownloadState()
    data class Error(val reason: String) : ModelDownloadState()
}

/**
 * Result of the pre-flight check before initiating a model download.
 */
sealed class DownloadPreflightResult {
    object CanDownload : DownloadPreflightResult()
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : DownloadPreflightResult()
    object WifiRequired : DownloadPreflightResult()
    object AlreadyDownloaded : DownloadPreflightResult()
    object HeuristicModelNoDownloadNeeded : DownloadPreflightResult()
}

/**
 * Specific exception thrown on cryptographic checksum or length mismatch.
 */
class DownloadVerificationFailure(message: String) : Exception(message)

/**
 * On-demand streaming download manager for optional AI model neural weights.
 *
 * In strict alignment with ADR-004 and ADR-050:
 * - Downloads are initiated ONLY upon explicit user opt-in consent.
 * - Defaults to Wi-Fi only to prevent cellular metered data usage.
 * - Requires at least 2x model size in available disk space before starting.
 * - Enforces post-download SHA-256 and byte-length verification.
 * - Automatically purges corrupted or incomplete files upon verification failure.
 */
class AiModelDownloader(
    val storageDir: File,
    var allowCellular: Boolean = false,
    var isWifiConnected: Boolean = true
) {
    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    /**
     * Map of active download states per model ID.
     */
    private val downloadStates = mutableMapOf<String, ModelDownloadState>()

    /**
     * Resolves the target storage file path for a model descriptor.
     */
    fun getModelFile(descriptor: AiModelDescriptor): File {
        val extension = when (descriptor.runtimeType) {
            ModelRuntimeType.TFLITE -> ".tflite"
            ModelRuntimeType.ONNX -> ".onnx"
            ModelRuntimeType.CUSTOM_HEURISTIC -> ".bin"
        }
        return File(storageDir, "${descriptor.id}$extension")
    }

    /**
     * Checks if a model's weights file is already downloaded and valid on disk.
     */
    fun isDownloaded(descriptor: AiModelDescriptor): Boolean {
        if (descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) return true
        val file = getModelFile(descriptor)
        return file.exists() && file.length() == descriptor.weightsSizeBytes
    }

    /**
     * Queries the current download status for a model.
     */
    fun getDownloadState(descriptor: AiModelDescriptor): ModelDownloadState {
        if (descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) {
            return ModelDownloadState.Downloaded
        }
        if (isDownloaded(descriptor)) {
            return ModelDownloadState.Downloaded
        }
        return downloadStates[descriptor.id] ?: ModelDownloadState.NotDownloaded
    }

    /**
     * Pre-flight assessment checking Wi-Fi status, storage space, and download state.
     */
    fun performPreflight(
        descriptor: AiModelDescriptor,
        availableStorageBytes: Long
    ): DownloadPreflightResult {
        if (descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) {
            return DownloadPreflightResult.HeuristicModelNoDownloadNeeded
        }
        if (isDownloaded(descriptor)) {
            return DownloadPreflightResult.AlreadyDownloaded
        }
        if (!allowCellular && !isWifiConnected) {
            return DownloadPreflightResult.WifiRequired
        }

        // Storage requirement: at least 2x weights size
        val requiredStorage = descriptor.weightsSizeBytes * 2
        if (availableStorageBytes < requiredStorage) {
            return DownloadPreflightResult.InsufficientStorage(
                requiredBytes = requiredStorage,
                availableBytes = availableStorageBytes
            )
        }

        return DownloadPreflightResult.CanDownload
    }

    /**
     * Streams and writes model weights from an input stream with resume support,
     * reporting progress and performing post-download SHA-256 verification.
     *
     * @param descriptor The model metadata descriptor.
     * @param streamSupplier Lambda providing the data stream (e.g. HTTP connection stream or mock stream).
     * @param resumeOffset Starting byte offset if resuming an interrupted download.
     * @param onProgress Optional progress callback.
     * @return Result with verified File or failure.
     */
    fun downloadModelStreaming(
        descriptor: AiModelDescriptor,
        streamSupplier: (resumeOffset: Long) -> InputStream,
        resumeOffset: Long = 0L,
        onProgress: ((progress: Float, bytes: Long) -> Unit)? = null
    ): Result<File> {
        if (!com.artstudio.core.FeatureFlags.isAiEnabled) {
            return Result.failure(IllegalStateException("AI downloads are disabled: FeatureFlags.isAiEnabled is false"))
        }
        if (descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) {
            return Result.success(getModelFile(descriptor))
        }

        val targetFile = getModelFile(descriptor)
        val partFile = File(storageDir, "${descriptor.id}.part")

        downloadStates[descriptor.id] = ModelDownloadState.Downloading(
            progress = if (descriptor.weightsSizeBytes > 0) resumeOffset.toFloat() / descriptor.weightsSizeBytes else 0f,
            bytesDownloaded = resumeOffset,
            totalBytes = descriptor.weightsSizeBytes
        )

        try {
            val append = resumeOffset > 0L && partFile.exists() && partFile.length() == resumeOffset
            val outputStream = FileOutputStream(partFile, append)

            streamSupplier(resumeOffset).use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = resumeOffset
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val progress = if (descriptor.weightsSizeBytes > 0) {
                            (bytesDownloaded.toFloat() / descriptor.weightsSizeBytes).coerceIn(0f, 1f)
                        } else 1.0f

                        downloadStates[descriptor.id] = ModelDownloadState.Downloading(
                            progress = progress,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = descriptor.weightsSizeBytes
                        )
                        onProgress?.invoke(progress, bytesDownloaded)
                    }
                }
            }

            // Post-download verification
            downloadStates[descriptor.id] = ModelDownloadState.Verifying
            val verificationResult = verifyDownloadedFile(partFile, descriptor)
            if (verificationResult.isFailure) {
                partFile.delete()
                val err = verificationResult.exceptionOrNull()?.message ?: "Verification failed"
                downloadStates[descriptor.id] = ModelDownloadState.Error(err)
                return Result.failure(verificationResult.exceptionOrNull() ?: DownloadVerificationFailure(err))
            }

            // Move .part to final destination
            if (targetFile.exists()) targetFile.delete()
            if (!partFile.renameTo(targetFile)) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }

            downloadStates[descriptor.id] = ModelDownloadState.Downloaded
            return Result.success(targetFile)

        } catch (e: Exception) {
            val errMessage = e.message ?: "Download failed"
            downloadStates[descriptor.id] = ModelDownloadState.Error(errMessage)
            return Result.failure(e)
        }
    }

    /**
     * Cryptographically verifies the downloaded file's byte length and SHA-256 hash.
     */
    fun verifyDownloadedFile(file: File, descriptor: AiModelDescriptor): Result<Unit> {
        if (!file.exists()) {
            return Result.failure(DownloadVerificationFailure("Downloaded file not found at ${file.absolutePath}"))
        }

        // 1. Verify exact byte length
        if (file.length() != descriptor.weightsSizeBytes) {
            return Result.failure(
                DownloadVerificationFailure(
                    "File length mismatch for ${descriptor.id}: expected ${descriptor.weightsSizeBytes} bytes, got ${file.length()} bytes."
                )
            )
        }

        // 2. Verify SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val computedHash = digest.digest().joinToString("") { "%02x".format(it) }

        if (!computedHash.equals(descriptor.sha256, ignoreCase = true)) {
            return Result.failure(
                DownloadVerificationFailure(
                    "SHA-256 hash mismatch for ${descriptor.id}: expected ${descriptor.sha256}, got $computedHash."
                )
            )
        }

        return Result.success(Unit)
    }

    /**
     * Deletes a model's weights file from local storage to reclaim user storage space.
     */
    fun deleteModel(descriptor: AiModelDescriptor): Boolean {
        if (descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) return true
        val targetFile = getModelFile(descriptor)
        val partFile = File(storageDir, "${descriptor.id}.part")

        var deleted = false
        if (targetFile.exists()) deleted = targetFile.delete() || deleted
        if (partFile.exists()) deleted = partFile.delete() || deleted

        downloadStates.remove(descriptor.id)
        return deleted
    }

    /**
     * Purges all downloaded model files and temporary .part files from local storage.
     */
    fun clearAllModelStorage(): Long {
        var reclaimedBytes = 0L
        storageDir.listFiles()?.forEach { file ->
            reclaimedBytes += file.length()
            file.delete()
        }
        downloadStates.clear()
        return reclaimedBytes
    }

    /**
     * Computes the total on-disk storage currently consumed by AI models.
     */
    fun getTotalStorageConsumedBytes(): Long {
        var total = 0L
        storageDir.listFiles()?.forEach { file ->
            total += file.length()
        }
        return total
    }
}
