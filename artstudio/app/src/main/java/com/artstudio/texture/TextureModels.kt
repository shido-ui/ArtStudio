package com.artstudio.texture

import com.artstudio.core.BrushTexture
import com.artstudio.document.io.ArtJson

/**
 * The four primary texture categories supported by the Art Studio brush engine.
 */
enum class TextureCategory(val id: String, val displayName: String, val description: String) {
    GRAIN(
        id = "grain",
        displayName = "Grain / Paper Tooth",
        description = "Continuous stochastic tooth or paper surface grain applied across strokes"
    ),
    PATTERN(
        id = "pattern",
        displayName = "Seamless Pattern",
        description = "Repeating 2D tileable pattern (halftone, textile, screens)"
    ),
    STAMP(
        id = "stamp",
        displayName = "Dab Stamp",
        description = "Discrete 2D alpha silhouette footprint dabbed at stroke points"
    ),
    TEXTURED_STROKE(
        id = "textured_stroke",
        displayName = "Textured Stroke",
        description = "Directional bristle and rake striations along stroke tangents"
    );

    companion object {
        fun fromId(id: String): TextureCategory {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GRAIN
        }
    }
}

/**
 * Immutable metadata record for a built-in or user-imported texture asset.
 */
data class TextureMetadata(
    val id: String,
    val name: String,
    val category: TextureCategory,
    val isBuiltIn: Boolean,
    val isFavorite: Boolean = false,
    val filePath: String? = null,
    val assetPath: String? = null,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val sha256: String,
    val author: String = "Art Studio Core Team",
    val license: String = "CC0-1.0",
    val provenance: String = "Synthetic procedural asset",
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Texture ID cannot be blank" }
        require(name.isNotBlank()) { "Texture name cannot be blank" }
        require(width > 0 && height > 0) { "Dimensions must be positive: $width x $height" }
        require(fileSizeBytes >= 0) { "File size must be non-negative: $fileSizeBytes" }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "category" to category.id,
        "isBuiltIn" to isBuiltIn,
        "isFavorite" to isFavorite,
        "filePath" to filePath,
        "assetPath" to assetPath,
        "width" to width,
        "height" to height,
        "fileSizeBytes" to fileSizeBytes,
        "sha256" to sha256,
        "author" to author,
        "license" to license,
        "provenance" to provenance,
        "createdAt" to createdAt
    )

    fun toJson(): String = ArtJson.stringify(toMap())

    companion object {
        fun fromMap(map: Map<String, Any?>): TextureMetadata {
            return TextureMetadata(
                id = map["id"] as? String ?: throw IllegalArgumentException("Missing id"),
                name = map["name"] as? String ?: "Untitled Texture",
                category = TextureCategory.fromId(map["category"] as? String ?: "grain"),
                isBuiltIn = map["isBuiltIn"] as? Boolean ?: false,
                isFavorite = map["isFavorite"] as? Boolean ?: false,
                filePath = map["filePath"] as? String,
                assetPath = map["assetPath"] as? String,
                width = (map["width"] as? Number)?.toInt() ?: 64,
                height = (map["height"] as? Number)?.toInt() ?: 64,
                fileSizeBytes = (map["fileSizeBytes"] as? Number)?.toLong() ?: 0L,
                sha256 = map["sha256"] as? String ?: "",
                author = map["author"] as? String ?: "Unknown",
                license = map["license"] as? String ?: "Unknown",
                provenance = map["provenance"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        @Suppress("UNCHECKED_CAST")
        fun fromJson(jsonStr: String): TextureMetadata {
            val map = ArtJson.parse(jsonStr) as Map<String, Any?>
            return fromMap(map)
        }
    }
}

/**
 * Standard error codes for texture validation and loading failures.
 */
enum class TextureErrorCode(val description: String) {
    FILE_NOT_FOUND("Asset file not found on disk or in APK assets"),
    EMPTY_FILE("Asset file is empty (0 bytes)"),
    FILE_TOO_LARGE("Asset file size exceeds maximum permitted budget (2 MB)"),
    DIMENSIONS_TOO_LARGE("Texture dimensions exceed maximum limit of 1024x1024"),
    DIMENSIONS_TOO_SMALL("Texture dimensions are smaller than minimum limit of 8x8"),
    INVALID_HEADER_OR_FORMAT("File header does not match valid PNG, WebP, or JPEG format"),
    CORRUPT_OR_TRUNCATED("Asset file is truncated or has invalid image chunk data"),
    DECOMPRESSION_FAILED("Image decompression or decode error"),
    BUILT_IN_CANNOT_BE_MODIFIED("Bundled system assets cannot be modified or renamed"),
    BUILT_IN_CANNOT_BE_DELETED("Bundled system assets cannot be deleted")
}

/**
 * Result of asset import validation.
 */
sealed class TextureValidationResult {
    data class Success(
        val metadata: TextureMetadata,
        val texture: BrushTexture
    ) : TextureValidationResult()

    data class Error(
        val code: TextureErrorCode,
        val message: String,
        val details: String? = null
    ) : TextureValidationResult() {
        fun toException(): TextureValidationException = when (code) {
            TextureErrorCode.DIMENSIONS_TOO_LARGE -> OversizedDimensionException(message)
            TextureErrorCode.FILE_TOO_LARGE -> OversizedFileException(message)
            TextureErrorCode.CORRUPT_OR_TRUNCATED, TextureErrorCode.INVALID_HEADER_OR_FORMAT -> CorruptAssetException(message)
            TextureErrorCode.EMPTY_FILE -> EmptyAssetException(message)
            TextureErrorCode.BUILT_IN_CANNOT_BE_MODIFIED -> CannotModifyBuiltInAssetException(message)
            TextureErrorCode.BUILT_IN_CANNOT_BE_DELETED -> CannotDeleteBuiltInAssetException(message)
            else -> TextureValidationException(message)
        }
    }
}

/**
 * Explicit error record emitted when an asset fails to load at runtime.
 * Guarantees zero silent failures by giving the artist full diagnostic visibility.
 */
data class TextureLoadError(
    val assetId: String,
    val assetName: String,
    val errorCode: TextureErrorCode,
    val userMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Runtime texture loading result ensuring graceful fallback with explicit error.
 */
sealed class TextureLoadResult {
    data class Success(val texture: BrushTexture) : TextureLoadResult()
    data class FallbackWithError(
        val error: TextureLoadError,
        val fallbackTexture: BrushTexture
    ) : TextureLoadResult()
}

// -----------------------------------------------------------------------------
// Specialized Exceptions
// -----------------------------------------------------------------------------

open class TextureValidationException(message: String) : RuntimeException(message)
class OversizedDimensionException(message: String) : TextureValidationException(message)
class OversizedFileException(message: String) : TextureValidationException(message)
class CorruptAssetException(message: String) : TextureValidationException(message)
class EmptyAssetException(message: String) : TextureValidationException(message)
class CannotModifyBuiltInAssetException(message: String) : TextureValidationException(message)
class CannotDeleteBuiltInAssetException(message: String) : TextureValidationException(message)
