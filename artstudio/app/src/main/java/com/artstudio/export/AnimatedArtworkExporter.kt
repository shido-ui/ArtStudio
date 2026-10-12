package com.artstudio.export

import com.artstudio.animation.AnimationFrame
import com.artstudio.animation.AnimationFrameRenderer
import com.artstudio.animation.AnimationTimeline
import com.artstudio.animation.LoopMode
import com.artstudio.document.model.ArtDocument
import com.artstudio.export.gif.GifEncoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Service orchestrating background animated artwork exports (GIF89a, MP4 video, ZIP sequence).
 *
 * Implements ADR-044 requirements:
 * 1. Off-UI thread execution (Dispatchers.IO).
 * 2. Streaming frame-by-frame encoding with O(1) memory consumption.
 * 3. Never commits onion-skin guide overlays into saved frame pixels (applyOnionSkin = false).
 * 4. Respects frame duration ticks, timeline FPS, loopMode, and dimensions scaling.
 * 5. Uses temporary shadow files with pre-flight storage verification and atomic rename.
 * 6. Truthful capability gating when format is unsupported on host runtime (e.g. MP4 on JVM).
 */
class AnimatedArtworkExporter(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultStorageHeadroomBytes: Long = 10 * 1024 * 1024L // 10 MB safety margin
) {

    private val GIF_MAGIC_89A = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val GIF_MAGIC_87A = "GIF87a".toByteArray(Charsets.US_ASCII)
    private val ZIP_MAGIC = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())

    /**
     * Exports [document] animation to [targetFile] according to [config].
     */
    suspend fun exportAnimatedArtwork(
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

        // 2. Validate capability
        if (!AnimatedExportCapabilities.isFormatSupported(config.format)) {
            val reason = AnimatedExportCapabilities.getDisabledReason(config.format)
                ?: "Format ${config.format} is unsupported on current platform"
            return@withContext ExportResult.Error(
                reason = reason,
                errorCode = ExportErrorCode.ENCODING_FAILED
            )
        }

        // 3. Resolve Dimensions & Timeline
        val origW = document.canvas.width
        val origH = document.canvas.height
        if (origW <= 0 || origH <= 0) {
            return@withContext ExportResult.Error(
                reason = "Invalid canvas dimensions: ${origW}x${origH}",
                errorCode = ExportErrorCode.INVALID_DIMENSIONS
            )
        }

        val scale = config.scaleFactor.coerceAtLeast(0.01f)
        val targetW = config.outputWidth ?: (origW * scale).roundToInt().coerceAtLeast(1)
        val targetH = config.outputHeight ?: (origH * scale).roundToInt().coerceAtLeast(1)

        val timeline: AnimationTimeline = document.animation?.toTimeline() ?: AnimationTimeline(
            fps = 12,
            frames = listOf(AnimationFrame(name = "Frame 1", durationTicks = 1))
        )

        val totalFrames = timeline.frames.size
        if (totalFrames <= 0) {
            return@withContext ExportResult.Error(
                reason = "Timeline contains no animation frames",
                errorCode = ExportErrorCode.INVALID_DIMENSIONS
            )
        }

        // 4. Resolve Filename Conflict
        val resolvedFile = try {
            if (targetFile.exists()) {
                when (config.conflictStrategy) {
                    FilenameConflictStrategy.FAIL_IF_EXISTS -> {
                        return@withContext ExportResult.Error(
                            reason = "Destination file already exists: ${targetFile.absolutePath}",
                            errorCode = ExportErrorCode.FILENAME_CONFLICT
                        )
                    }
                    FilenameConflictStrategy.OVERWRITE -> targetFile
                    FilenameConflictStrategy.AUTO_RENAME -> resolveAutoRename(targetFile)
                }
            } else {
                targetFile
            }
        } catch (e: Exception) {
            return@withContext ExportResult.Error(
                reason = "Failed resolving destination file path: ${e.message}",
                errorCode = ExportErrorCode.PERMISSION_DENIED_OR_IO,
                exception = e
            )
        }

        // 5. Pre-flight Storage Space Check
        val estimatedBytes = (totalFrames.toLong() * targetW * targetH * 2L)
            .coerceAtLeast(defaultStorageHeadroomBytes)

        val hasEnoughStorage = if (storageSpaceCheck != null) {
            storageSpaceCheck(resolvedFile, estimatedBytes)
        } else {
            val parent = resolvedFile.parentFile ?: File(".")
            val usable = parent.usableSpace
            usable == 0L || usable >= estimatedBytes
        }

        if (!hasEnoughStorage) {
            return@withContext ExportResult.Error(
                reason = "Insufficient storage space to complete animation export (estimated $estimatedBytes bytes needed)",
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

        // 8. Encode to Temporary Shadow File
        try {
            when (config.format) {
                ExportFormat.GIF -> {
                    encodeGif(
                        document = document,
                        timeline = timeline,
                        targetW = targetW,
                        targetH = targetH,
                        origW = origW,
                        origH = origH,
                        layerPixelBuffers = layerPixelBuffers,
                        tempFile = tempFile,
                        cancellationToken = cancellationToken
                    )
                }
                ExportFormat.ZIP_SEQUENCE -> {
                    encodeZipSequence(
                        document = document,
                        timeline = timeline,
                        targetW = targetW,
                        targetH = targetH,
                        origW = origW,
                        origH = origH,
                        layerPixelBuffers = layerPixelBuffers,
                        tempFile = tempFile,
                        cancellationToken = cancellationToken
                    )
                }
                ExportFormat.MP4 -> {
                    encodeMp4(
                        document = document,
                        timeline = timeline,
                        targetW = targetW,
                        targetH = targetH,
                        origW = origW,
                        origH = origH,
                        layerPixelBuffers = layerPixelBuffers,
                        tempFile = tempFile,
                        cancellationToken = cancellationToken
                    )
                }
                else -> {
                    cleanupTempFile(tempFile)
                    return@withContext ExportResult.Error(
                        reason = "Unsupported animated export format: ${config.format}",
                        errorCode = ExportErrorCode.ENCODING_FAILED
                    )
                }
            }
        } catch (_: ExportCancelledException) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Cancelled
        } catch (e: UnsupportedOperationException) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = e.message ?: "Encoding unsupported",
                errorCode = ExportErrorCode.ENCODING_FAILED,
                exception = e
            )
        } catch (e: Throwable) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Animated encoding failed: ${e.message}",
                errorCode = ExportErrorCode.ENCODING_FAILED,
                exception = e
            )
        }

        // Check cancellation post-encode
        if (cancellationToken?.isCancelled == true) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Cancelled
        }

        // 9. Post-Write Verification of Temporary File
        if (!tempFile.exists() || tempFile.length() <= 0L) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Export verification failed: temporary output is missing or empty",
                errorCode = ExportErrorCode.VERIFICATION_FAILED
            )
        }

        val isValid = verifySignature(tempFile, config.format)
        if (!isValid) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Export verification failed: file header does not match ${config.format} signature",
                errorCode = ExportErrorCode.VERIFICATION_FAILED
            )
        }

        // 10. Atomic Commit
        val tempSize = tempFile.length()
        try {
            if (resolvedFile.exists() && config.conflictStrategy == FilenameConflictStrategy.OVERWRITE) {
                resolvedFile.delete()
            }

            val moved = tempFile.renameTo(resolvedFile)
            if (!moved) {
                tempFile.inputStream().use { input ->
                    resolvedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                cleanupTempFile(tempFile)
            }
        } catch (e: Exception) {
            cleanupTempFile(tempFile)
            return@withContext ExportResult.Error(
                reason = "Failed committing exported animation to destination: ${e.message}",
                errorCode = ExportErrorCode.PERMISSION_DENIED_OR_IO,
                exception = e
            )
        }

        // 11. Final File Check
        if (!resolvedFile.exists() || resolvedFile.length() != tempSize) {
            return@withContext ExportResult.Error(
                reason = "Committed file verification failed: destination file missing or truncated",
                errorCode = ExportErrorCode.VERIFICATION_FAILED
            )
        }

        val duration = System.currentTimeMillis() - startTime
        return@withContext ExportResult.Success(
            outputFile = resolvedFile,
            format = config.format,
            width = targetW,
            height = targetH,
            fileSize = resolvedFile.length(),
            hasAlpha = config.format != ExportFormat.MP4,
            durationMs = duration
        )
    }

    private fun encodeGif(
        document: ArtDocument,
        timeline: AnimationTimeline,
        targetW: Int,
        targetH: Int,
        origW: Int,
        origH: Int,
        layerPixelBuffers: Map<String, IntArray>,
        tempFile: File,
        cancellationToken: ExportCancellationToken?
    ) {
        val loopCount = when (timeline.loopMode) {
            LoopMode.LOOP -> 0
            LoopMode.ONCE -> 1
            LoopMode.PING_PONG -> 0
        }

        FileOutputStream(tempFile).use { fos ->
            val encoder = GifEncoder(targetW, targetH, fos, loopCount = loopCount)

            fun renderAndWrite(frameIndex: Int) {
                if (cancellationToken?.isCancelled == true) throw ExportCancelledException()
                val frame = timeline.frames[frameIndex]

                var pixels = AnimationFrameRenderer.renderFrame(
                    document = document,
                    frameIndex = frameIndex,
                    timeline = timeline,
                    pixelBuffers = layerPixelBuffers,
                    applyOnionSkin = false // Strict non-destructive invariant: guides never committed
                )

                if (targetW != origW || targetH != origH) {
                    pixels = AnimationFrameRenderer.downsampleBuffer(pixels, origW, origH, targetW, targetH)
                }

                val delayCs = GifEncoder.calculateDelayCentiseconds(frame.durationTicks, timeline.fps)
                encoder.encodeFrame(pixels, delayCs, cancellationToken)
            }

            // Forward frames
            for (i in timeline.frames.indices) {
                renderAndWrite(i)
            }

            // Ping-pong reverse in-betweens if configured
            if (timeline.loopMode == LoopMode.PING_PONG && timeline.frames.size > 2) {
                for (i in timeline.frames.size - 2 downTo 1) {
                    renderAndWrite(i)
                }
            }

            encoder.finish()
        }
    }

    private fun encodeZipSequence(
        document: ArtDocument,
        timeline: AnimationTimeline,
        targetW: Int,
        targetH: Int,
        origW: Int,
        origH: Int,
        layerPixelBuffers: Map<String, IntArray>,
        tempFile: File,
        cancellationToken: ExportCancellationToken?
    ) {
        val frameBuffers = mutableListOf<IntArray>()

        for (i in timeline.frames.indices) {
            if (cancellationToken?.isCancelled == true) throw ExportCancelledException()
            var pixels = AnimationFrameRenderer.renderFrame(
                document = document,
                frameIndex = i,
                timeline = timeline,
                pixelBuffers = layerPixelBuffers,
                applyOnionSkin = false
            )
            if (targetW != origW || targetH != origH) {
                pixels = AnimationFrameRenderer.downsampleBuffer(pixels, origW, origH, targetW, targetH)
            }
            frameBuffers.add(pixels)
        }

        ZipSequenceEncoder.exportSequence(
            frameBuffers = frameBuffers,
            width = targetW,
            height = targetH,
            timeline = timeline,
            outputFile = tempFile,
            cancellationToken = cancellationToken
        )
    }

    private fun encodeMp4(
        document: ArtDocument,
        timeline: AnimationTimeline,
        targetW: Int,
        targetH: Int,
        origW: Int,
        origH: Int,
        layerPixelBuffers: Map<String, IntArray>,
        tempFile: File,
        cancellationToken: ExportCancellationToken?
    ) {
        val frameBuffers = mutableListOf<IntArray>()

        for (i in timeline.frames.indices) {
            if (cancellationToken?.isCancelled == true) throw ExportCancelledException()
            var pixels = AnimationFrameRenderer.renderFrame(
                document = document,
                frameIndex = i,
                timeline = timeline,
                pixelBuffers = layerPixelBuffers,
                applyOnionSkin = false
            )
            if (targetW != origW || targetH != origH) {
                pixels = AnimationFrameRenderer.downsampleBuffer(pixels, origW, origH, targetW, targetH)
            }
            frameBuffers.add(pixels)
        }

        PlatformVideoEncoder.exportMp4(
            frameBuffers = frameBuffers,
            width = targetW,
            height = targetH,
            timeline = timeline,
            outputFile = tempFile,
            cancellationToken = cancellationToken
        )
    }

    private fun verifySignature(file: File, format: ExportFormat): Boolean {
        return try {
            val bytes = FileInputStream(file).use { fis ->
                val buf = ByteArray(16)
                val read = fis.read(buf)
                if (read < 4) return false
                buf
            }

            when (format) {
                ExportFormat.GIF -> {
                    val is89a = (0..5).all { bytes[it] == GIF_MAGIC_89A[it] }
                    val is87a = (0..5).all { bytes[it] == GIF_MAGIC_87A[it] }
                    is89a || is87a
                }
                ExportFormat.ZIP_SEQUENCE -> {
                    (0..3).all { bytes[it] == ZIP_MAGIC[it] }
                }
                ExportFormat.MP4 -> {
                    // MP4 container contains 'ftyp' box in bytes 4..7
                    if (bytes.size < 8) return false
                    val ftyp = String(bytes.copyOfRange(4, 8), Charsets.US_ASCII)
                    ftyp == "ftyp"
                }
                else -> true
            }
        } catch (_: Exception) {
            false
        }
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

    private fun cleanupTempFile(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            android.util.Log.d("AnimatedArtworkExporter", "Temp file cleanup failed: ${e.message}")
        }
    }
}
