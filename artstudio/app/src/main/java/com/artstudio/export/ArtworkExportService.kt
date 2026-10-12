package com.artstudio.export

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Service orchestrating background artwork export jobs with cancellation,
 * collision resolution, storage checks, and post-write verification.
 *
 * INVARIANT: Never mutates the source ArtDocument or any layer properties.
 * INVARIANT: A failed export never displays a success state and cleans up temp files.
 */
class ArtworkExportService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultStorageHeadroomBytes: Long = 10 * 1024 * 1024L // 10 MB safety margin
) {

    private val PNG_MAGIC = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val WEBP_RIFF = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
    private val WEBP_MAGIC = byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte())
    private val PSD_MAGIC = byteArrayOf(0x38.toByte(), 0x42.toByte(), 0x50.toByte(), 0x53.toByte()) // '8BPS'

    /**
     * Executes export of [document] to [targetFile] according to [config].
     *
     * @param document Source document (strictly immutable)
     * @param config Export parameters (format, scope, quality, dimensions, etc.)
     * @param targetFile Destination file requested
     * @param layerPixelBuffers Map of layer UUID to IntArray pixel buffer
     * @param layerMaskBuffers Map of layer UUID to IntArray mask buffer
     * @param cancellationToken Optional cancellation token for early abort
     * @param storageSpaceCheck Optional custom predicate to simulate/test storage limits
     * @return [ExportResult.Success], [ExportResult.Error], or [ExportResult.Cancelled]
     */
    suspend fun exportArtwork(
        document: ArtDocument,
        config: ExportConfig,
        targetFile: File,
        layerPixelBuffers: Map<String, IntArray> = emptyMap(),
        layerMaskBuffers: Map<String, IntArray> = emptyMap(),
        cancellationToken: ExportCancellationToken? = null,
        storageSpaceCheck: ((File, Long) -> Boolean)? = null
    ): ExportResult = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()

        // 1. Initial cancellation check
        if (cancellationToken?.isCancelled == true) {
            return@withContext ExportResult.Cancelled
        }

        // 2. Validate dimensions and parameters
        if (document.canvas.width <= 0 || document.canvas.height <= 0) {
            return@withContext ExportResult.Error(
                reason = "Invalid canvas dimensions: ${document.canvas.width}x${document.canvas.height}",
                errorCode = ExportErrorCode.INVALID_DIMENSIONS
            )
        }

        if (config.outputWidth != null && config.outputWidth <= 0) {
            return@withContext ExportResult.Error(
                reason = "Target outputWidth must be > 0, got ${config.outputWidth}",
                errorCode = ExportErrorCode.INVALID_DIMENSIONS
            )
        }

        if (config.outputHeight != null && config.outputHeight <= 0) {
            return@withContext ExportResult.Error(
                reason = "Target outputHeight must be > 0, got ${config.outputHeight}",
                errorCode = ExportErrorCode.INVALID_DIMENSIONS
            )
        }

        if (config.scaleFactor <= 0f) {
            return@withContext ExportResult.Error(
                reason = "Scale factor must be > 0, got ${config.scaleFactor}",
                errorCode = ExportErrorCode.INVALID_DIMENSIONS
            )
        }

        // 2b. Dispatch animated formats to AnimatedArtworkExporter
        if (config.format == ExportFormat.GIF || config.format == ExportFormat.MP4 || config.format == ExportFormat.ZIP_SEQUENCE) {
            return@withContext AnimatedArtworkExporter(dispatcher, defaultStorageHeadroomBytes).exportAnimatedArtwork(
                document = document,
                config = config,
                targetFile = targetFile,
                layerPixelBuffers = layerPixelBuffers,
                layerMaskBuffers = layerMaskBuffers,
                cancellationToken = cancellationToken,
                storageSpaceCheck = storageSpaceCheck
            )
        }

        // 3. Validate Scope Targets
        when (config.scope) {
            ExportScope.SELECTED_LAYER -> {
                val targetId = config.targetLayerOrGroupId ?: document.activeLayerId
                if (document.findLayerById(targetId) == null) {
                    return@withContext ExportResult.Error(
                        reason = "Selected layer '$targetId' not found in document",
                        errorCode = ExportErrorCode.TARGET_LAYER_NOT_FOUND
                    )
                }
            }
            ExportScope.SELECTED_GROUP -> {
                val targetId = config.targetLayerOrGroupId ?: document.activeLayerId
                val group = document.findLayerById(targetId) as? LayerNode.LayerGroup
                if (group == null) {
                    return@withContext ExportResult.Error(
                        reason = "Selected group '$targetId' not found in document",
                        errorCode = ExportErrorCode.TARGET_LAYER_NOT_FOUND
                    )
                }
            }
            else -> { /* Valid full scopes */ }
        }

        // 4. Resolve Filename Conflicts
        val resolvedFile = try {
            if (targetFile.exists()) {
                when (config.conflictStrategy) {
                    FilenameConflictStrategy.FAIL_IF_EXISTS -> {
                        return@withContext ExportResult.Error(
                            reason = "Destination file already exists: ${targetFile.absolutePath}",
                            errorCode = ExportErrorCode.FILENAME_CONFLICT
                        )
                    }
                    FilenameConflictStrategy.OVERWRITE -> {
                        targetFile
                    }
                    FilenameConflictStrategy.AUTO_RENAME -> {
                        resolveAutoRename(targetFile)
                    }
                }
            } else {
                targetFile
            }
        } catch (e: Exception) {
            return@withContext ExportResult.Error(
                reason = "Failed resolving target file path: ${e.message}",
                errorCode = ExportErrorCode.PERMISSION_DENIED_OR_IO,
                exception = e
            )
        }

        // 5. Storage Space Check
        val estimatedBytes = (document.canvas.width.toLong() * document.canvas.height * 4L)
            .coerceAtLeast(defaultStorageHeadroomBytes)

        val hasEnoughStorage = if (storageSpaceCheck != null) {
            storageSpaceCheck(resolvedFile, estimatedBytes)
        } else {
            val parent = resolvedFile.parentFile ?: File(".")
            val usable = parent.usableSpace
            usable == 0L || usable >= estimatedBytes // usable == 0L if unsupported on system
        }

        if (!hasEnoughStorage) {
            return@withContext ExportResult.Error(
                reason = "Insufficient storage space to complete export (estimated ${estimatedBytes} bytes needed)",
                errorCode = ExportErrorCode.INSUFFICIENT_STORAGE
            )
        }

        // 6. Ensure Parent Directory
        val parentDir = resolvedFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            val created = parentDir.mkdirs()
            if (!created && !parentDir.exists()) {
                return@withContext ExportResult.Error(
                    reason = "Failed to create destination directory: ${parentDir.absolutePath}",
                    errorCode = ExportErrorCode.PERMISSION_DENIED_OR_IO
                )
            }
        }

        // 7. Atomic Temp File
        val tempFile = File(
            parentDir,
            ".export_tmp_${UUID.randomUUID()}.${config.format.extension}.tmp"
        )

        // 8. Render Pixel Buffer
        val renderedArtwork = try {
            ArtworkExportRenderer.renderArtwork(
                document = document,
                config = config,
                layerPixelBuffers = layerPixelBuffers,
                layerMaskBuffers = layerMaskBuffers,
                cancellationToken = cancellationToken
            )
        } catch (_: ExportCancelledException) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Cancelled
        } catch (e: Throwable) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Rendering failed: ${e.message}",
                errorCode = ExportErrorCode.ENCODING_FAILED,
                exception = e
            )
        }

        // Check cancellation post-render
        if (cancellationToken?.isCancelled == true) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Cancelled
        }

        // 9. Encode to Temporary File
        try {
            if (config.format == ExportFormat.ORA) {
                com.artstudio.interchange.ora.OpenRasterExporter.exportOra(
                    document = document,
                    layerPixelBuffers = layerPixelBuffers,
                    outputFile = tempFile
                )
            } else if (config.format == ExportFormat.SVG) {
                val (svgContent, _) = com.artstudio.vector.svg.SafeSvgExporter.exportDocument(document)
                tempFile.writeText(svgContent, Charsets.UTF_8)
            } else if (config.format == ExportFormat.PSD) {
                com.artstudio.document.io.psd.PsdWriter.exportPsd(
                    document = document,
                    layerPixelBuffers = layerPixelBuffers,
                    layerMaskBuffers = layerMaskBuffers,
                    outputFile = tempFile
                )
            } else {
                FileOutputStream(tempFile).use { fos ->
                    val ok = ImageEncoderEngine.encode(
                        pixels = renderedArtwork.pixels,
                        width = renderedArtwork.width,
                        height = renderedArtwork.height,
                        config = config,
                        outputStream = fos
                    )
                    if (!ok) {
                        cleanupTempFile(tempFile)
                        return@withContext ExportResult.Error(
                            reason = "Encoder failed to produce ${config.format} stream",
                            errorCode = ExportErrorCode.ENCODING_FAILED
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Permission denied writing export: ${e.message}",
                errorCode = ExportErrorCode.PERMISSION_DENIED_OR_IO,
                exception = e
            )
        } catch (e: IOException) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "I/O error during file export: ${e.message}",
                errorCode = ExportErrorCode.PERMISSION_DENIED_OR_IO,
                exception = e
            )
        } catch (e: Throwable) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Unexpected error encoding export: ${e.message}",
                errorCode = ExportErrorCode.ENCODING_FAILED,
                exception = e
            )
        }

        // Check cancellation post-encode
        if (cancellationToken?.isCancelled == true) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Cancelled
        }

        // 10. Verify Created Temp File
        if (!tempFile.exists() || tempFile.length() <= 0L) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Export verification failed: temporary output is missing or empty",
                errorCode = ExportErrorCode.VERIFICATION_FAILED
            )
        }

        val isValidSignature = verifyFileSignature(tempFile, config.format)
        if (!isValidSignature) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Export verification failed: file header does not match ${config.format} signature",
                errorCode = ExportErrorCode.VERIFICATION_FAILED
            )
        }

        // 11. Atomic Commit (Rename / Replace with Fail-Safe Rollback)
        val tempSize = tempFile.length()
        var exportBackupFile: File? = null
        try {
            if (resolvedFile.exists() && config.conflictStrategy == FilenameConflictStrategy.OVERWRITE) {
                val bak = File(parentDir, "${resolvedFile.name}.export_bak_${System.currentTimeMillis()}")
                if (bak.exists()) bak.delete()
                if (!resolvedFile.renameTo(bak)) {
                    resolvedFile.copyTo(bak, overwrite = true)
                    resolvedFile.delete()
                }
                exportBackupFile = bak
            }

            var commitSuccess = false
            try {
                val moved = tempFile.renameTo(resolvedFile)
                if (!moved) {
                    // Fallback copy if cross-filesystem or OS restriction
                    tempFile.inputStream().use { input ->
                        resolvedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    cleanupTempFile(tempFile)
                }

                if (!resolvedFile.exists() || resolvedFile.length() != tempSize) {
                    throw IOException("Committed file verification failed: destination file missing or length mismatch")
                }
                commitSuccess = true
            } catch (commitEx: Throwable) {
                // Rollback: restore destination file from backup if commit failed
                if (exportBackupFile != null && exportBackupFile.exists()) {
                    try {
                        if (resolvedFile.exists()) resolvedFile.delete()
                        if (!exportBackupFile.renameTo(resolvedFile)) {
                            exportBackupFile.copyTo(resolvedFile, overwrite = true)
                            exportBackupFile.delete()
                        }
                    } catch (_: Throwable) {
                        // Best effort rollback
                    }
                }
                throw commitEx
            } finally {
                // Clean backup file only after verified commit success
                if (commitSuccess && exportBackupFile != null && exportBackupFile.exists()) {
                    exportBackupFile.delete()
                }
            }
        } catch (e: Exception) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Failed committing exported file to destination: ${e.message}",
                errorCode = ExportErrorCode.PERMISSION_DENIED_OR_IO,
                exception = e
            )
        }

        // 12. Final Destination File Verification
        if (!resolvedFile.exists() || resolvedFile.length() != tempSize) {
            return@withContext ExportResult.Error(
                reason = "Committed file verification failed: destination file missing or corrupted",
                errorCode = ExportErrorCode.VERIFICATION_FAILED
            )
        }

        val duration = System.currentTimeMillis() - startTime
        return@withContext ExportResult.Success(
            outputFile = resolvedFile,
            format = config.format,
            width = renderedArtwork.width,
            height = renderedArtwork.height,
            fileSize = resolvedFile.length(),
            hasAlpha = renderedArtwork.hasAlpha,
            durationMs = duration
        )
    }

    private fun resolveAutoRename(file: File): File {
        if (!file.exists()) return file
        val parent = file.parentFile ?: File(".")
        val baseName = file.nameWithoutExtension
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var counter = 1
        var candidate = File(parent, "$baseName ($counter)$ext")
        while (candidate.exists()) {
            counter++
            candidate = File(parent, "$baseName ($counter)$ext")
        }
        return candidate
    }

    private fun verifyFileSignature(file: File, format: ExportFormat): Boolean {
        return try {
            val bytes = FileInputStream(file).use { fis ->
                val buf = ByteArray(16)
                val read = fis.read(buf)
                if (read < 4) return false
                buf
            }

            when (format) {
                ExportFormat.PNG -> {
                    if (bytes.size < PNG_MAGIC.size) return false
                    PNG_MAGIC.indices.all { bytes[it] == PNG_MAGIC[it] }
                }
                ExportFormat.JPEG -> {
                    if (bytes.size < JPEG_MAGIC.size) return false
                    JPEG_MAGIC.indices.all { bytes[it] == JPEG_MAGIC[it] }
                }
                ExportFormat.WEBP_LOSSLESS, ExportFormat.WEBP_LOSSY -> {
                    if (bytes.size < 12) return false
                    val isRiff = (0..3).all { bytes[it] == WEBP_RIFF[it] }
                    val isWebp = (0..3).all { bytes[8 + it] == WEBP_MAGIC[it] }
                    isRiff && isWebp
                }
                ExportFormat.ORA -> {
                    if (bytes.size < 4) return false
                    bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
                }
                ExportFormat.SVG -> {
                    if (bytes.size < 2) return false
                    val text = String(bytes, Charsets.UTF_8).trimStart()
                    text.startsWith("<")
                }
                ExportFormat.PSD -> {
                    if (bytes.size < PSD_MAGIC.size) return false
                    PSD_MAGIC.indices.all { bytes[it] == PSD_MAGIC[it] }
                }
                ExportFormat.GIF -> {
                    if (bytes.size < 6) return false
                    val is89 = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() &&
                            bytes[3] == '8'.code.toByte() && bytes[4] == '9'.code.toByte() && bytes[5] == 'a'.code.toByte()
                    val is87 = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() &&
                            bytes[3] == '8'.code.toByte() && bytes[4] == '7'.code.toByte() && bytes[5] == 'a'.code.toByte()
                    is89 || is87
                }
                ExportFormat.ZIP_SEQUENCE -> {
                    if (bytes.size < 4) return false
                    bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
                }
                ExportFormat.MP4 -> {
                    if (bytes.size < 8) return false
                    val ftyp = String(bytes.copyOfRange(4, 8), Charsets.US_ASCII)
                    ftyp == "ftyp"
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanupTempFile(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
            // Best effort cleanup
        }
    }
}
