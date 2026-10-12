package com.artstudio.export

import com.artstudio.document.model.ArtDocument
import java.io.File

/**
 * Supported raster export formats.
 */
enum class ExportFormat(val extension: String, val mimeType: String, val supportsAlpha: Boolean) {
    PNG("png", "image/png", supportsAlpha = true),
    JPEG("jpg", "image/jpeg", supportsAlpha = false),
    WEBP_LOSSLESS("webp", "image/webp", supportsAlpha = true),
    WEBP_LOSSY("webp", "image/webp", supportsAlpha = true),
    ORA("ora", "image/openraster", supportsAlpha = true),
    SVG("svg", "image/svg+xml", supportsAlpha = true),
    PSD("psd", "image/vnd.adobe.photoshop", supportsAlpha = true),
    GIF("gif", "image/gif", supportsAlpha = true),
    MP4("mp4", "video/mp4", supportsAlpha = false),
    ZIP_SEQUENCE("zip", "application/zip", supportsAlpha = true)
}

/**
 * Scope defining what part of the document is rendered for export.
 */
enum class ExportScope {
    /** Renders the full canvas with all visible layers */
    FULL_CANVAS,

    /** Renders a single selected layer */
    SELECTED_LAYER,

    /** Renders all visible layers within a selected layer group */
    SELECTED_GROUP,

    /** Renders a sub-rectangle (crop) of the canvas */
    CROP_REGION,

    /** Explicit flattened export of visible layers */
    EXPLICIT_FLATTENED
}

/**
 * Conflict resolution strategy when target file already exists.
 */
enum class FilenameConflictStrategy {
    /** Automatically append incrementing number: "file (1).png", "file (2).png" */
    AUTO_RENAME,

    /** Overwrite existing file atomically */
    OVERWRITE,

    /** Fail with FILENAME_CONFLICT error */
    FAIL_IF_EXISTS
}

/**
 * Sub-rectangle region for cropped export in canvas coordinates.
 */
data class ExportCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)

    init {
        require(right > left) { "right ($right) must be greater than left ($left)" }
        require(bottom > top) { "bottom ($bottom) must be greater than top ($top)" }
    }
}

/**
 * Export configuration defining output format, scope, dimensions, quality, and flags.
 */
data class ExportConfig(
    val format: ExportFormat = ExportFormat.PNG,
    val quality: Int = 90, // 1..100 (for JPEG and WEBP_LOSSY)
    val scope: ExportScope = ExportScope.FULL_CANVAS,
    val targetLayerOrGroupId: String? = null,
    val transparentBackground: Boolean = false,
    val outputWidth: Int? = null,
    val outputHeight: Int? = null,
    val scaleFactor: Float = 1.0f,
    val cropRect: ExportCropRect? = null,
    val conflictStrategy: FilenameConflictStrategy = FilenameConflictStrategy.AUTO_RENAME,
    val metadataComments: String? = null
) {
    init {
        require(quality in 1..100) { "Quality must be in 1..100, got $quality" }
        require(scaleFactor > 0f) { "Scale factor must be positive, got $scaleFactor" }
        if (outputWidth != null) require(outputWidth > 0) { "outputWidth must be > 0, got $outputWidth" }
        if (outputHeight != null) require(outputHeight > 0) { "outputHeight must be > 0, got $outputHeight" }
    }
}

/**
 * Result outcome of an export operation.
 */
sealed class ExportResult {
    data class Success(
        val outputFile: File,
        val format: ExportFormat,
        val width: Int,
        val height: Int,
        val fileSize: Long,
        val hasAlpha: Boolean,
        val durationMs: Long,
        val mediaStoreUri: android.net.Uri? = null,
        val displayLocation: String? = null
    ) : ExportResult()

    data class Error(
        val reason: String,
        val errorCode: ExportErrorCode,
        val exception: Throwable? = null
    ) : ExportResult()

    object Cancelled : ExportResult()
}

enum class ExportErrorCode {
    FILENAME_CONFLICT,
    INSUFFICIENT_STORAGE,
    PERMISSION_DENIED_OR_IO,
    TARGET_LAYER_NOT_FOUND,
    ENCODING_FAILED,
    VERIFICATION_FAILED,
    INVALID_DIMENSIONS
}

/**
 * Thread-safe cancellation token for export jobs.
 */
class ExportCancellationToken {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}
