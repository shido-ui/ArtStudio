package com.artstudio.fill

import com.artstudio.document.model.BlendModeType

/**
 * Operating mode for the fill tool.
 */
enum class FillMode {
    /**
     * Standard contiguous flood fill starting from seed coordinates (x, y).
     * Only pixels connected to the seed pixel within tolerance are filled.
     */
    CONTIGUOUS,

    /**
     * Global color replacement.
     * All pixels across the entire target/layer matching the seed color are filled,
     * regardless of connectivity.
     */
    GLOBAL_COLOR
}

/**
 * Source surface used to sample color and boundary contours.
 */
enum class FillSampleSource {
    /**
     * Samples color boundaries only from the active/selected layer.
     */
    ACTIVE_LAYER,

    /**
     * Samples color boundaries from the merged composite of all visible layers.
     * Allows filling on an empty/color layer while referencing line art on another layer.
     */
    ALL_LAYERS
}

/**
 * Target destination where the filled pixels will be applied.
 */
enum class FillTargetLayer {
    /**
     * Paints directly onto the active/selected layer.
     */
    CURRENT_LAYER,

    /**
     * Automatically creates a new raster layer above the active layer and paints fill on it.
     */
    NEW_LAYER
}

/**
 * Behavior when encountering transparent or partially transparent pixels.
 */
enum class FillTransparencyMode {
    /**
     * Standard behavior: treats alpha as an equal component in color matching.
     * Transparent areas matching the seed are filled; colored areas matching the seed are filled.
     */
    NORMAL,

    /**
     * Restricts fill strictly to transparent pixels (alpha <= 30).
     * Opaque pixels are treated as impenetrable boundaries.
     */
    TRANSPARENT_ONLY,

    /**
     * Restricts fill strictly to opaque/colored pixels (alpha > 30).
     * Transparent pixels are treated as boundaries and never filled.
     */
    OPAQUE_ONLY,

    /**
     * Fills matching pixels but preserves the original transparency silhouette of the destination layer.
     */
    RESPECT_TRANSPARENCY
}

/**
 * Thread-safe cooperative cancellation token for long-running fill operations.
 */
class FillCancellationToken {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}

/**
 * Configuration parameters for the flood / bucket fill engine.
 */
data class FillConfig(
    val fillColor: Int = 0xFFFF0000.toInt(),
    val fillMode: FillMode = FillMode.CONTIGUOUS,
    val tolerance: Float = 0.10f, // 0.0f (exact) to 1.0f (any color)
    val antiAlias: Boolean = true,
    val boundaryExpansion: Int = 0, // -10 to +10 pixels (dilate or erode fill mask)
    val gapClosingRadius: Int = 0, // 0 (off) to 10 pixels (morphological closing on barriers)
    val sampleSource: FillSampleSource = FillSampleSource.ACTIVE_LAYER,
    val targetLayerMode: FillTargetLayer = FillTargetLayer.CURRENT_LAYER,
    val transparencyMode: FillTransparencyMode = FillTransparencyMode.NORMAL,
    val fillOpacity: Float = 1.0f,
    val blendMode: BlendModeType = BlendModeType.Normal
) {
    init {
        require(tolerance in 0.0f..1.0f) { "Tolerance must be in [0.0, 1.0], got $tolerance" }
        require(boundaryExpansion in -10..10) { "Boundary expansion must be in [-10, 10], got $boundaryExpansion" }
        require(gapClosingRadius in 0..10) { "Gap closing radius must be in [0, 10], got $gapClosingRadius" }
        require(fillOpacity in 0.0f..1.0f) { "Fill opacity must be in [0.0, 1.0], got $fillOpacity" }
    }
}

/**
 * Result data returned by the fill engine.
 */
data class FillResult(
    val success: Boolean,
    val filledPixelCount: Int,
    val mask: ByteArray, // 8-bit alpha mask (width * height), values 0..255
    val resultPixels: IntArray?, // Output 32-bit ARGB pixels after blending
    val durationMs: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FillResult
        if (success != other.success) return false
        if (filledPixelCount != other.filledPixelCount) return false
        if (!mask.contentEquals(other.mask)) return false
        if (resultPixels != null) {
            if (other.resultPixels == null) return false
            if (!resultPixels.contentEquals(other.resultPixels)) return false
        } else if (other.resultPixels != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + filledPixelCount
        result = 31 * result + mask.contentHashCode()
        result = 31 * result + (resultPixels?.contentHashCode() ?: 0)
        return result
    }
}
