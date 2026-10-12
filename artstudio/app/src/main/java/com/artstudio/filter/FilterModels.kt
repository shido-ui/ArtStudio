package com.artstudio.filter

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enumeration of all verified filter operations in the extensible filter subsystem.
 */
enum class FilterType(val id: String, val displayName: String, val category: FilterCategory) {
    GAUSSIAN_BLUR("gaussian_blur", "Gaussian Blur", FilterCategory.BLUR_SHARPEN),
    SHARPEN("sharpen", "Sharpen", FilterCategory.BLUR_SHARPEN),
    OUTLINE("outline", "Outline / Edge", FilterCategory.STYLIZATION),
    GLOW("glow", "Bloom / Glow", FilterCategory.COLOR_EFFECT),
    NOISE("noise", "Procedural Noise", FilterCategory.STYLIZATION),
    PIXELATE("pixelate", "Pixelate / Mosaic", FilterCategory.DISTORTION),
    RIPPLE("ripple", "Ripple Wave", FilterCategory.DISTORTION);

    companion object {
        fun fromId(id: String): FilterType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GAUSSIAN_BLUR
    }
}

/**
 * Filter classification categories for UI presentation and grouping.
 */
enum class FilterCategory(val displayName: String) {
    BLUR_SHARPEN("Blur & Sharpen"),
    COLOR_EFFECT("Color & Light Effects"),
    STYLIZATION("Stylization"),
    DISTORTION("Distortion")
}

/**
 * Base sealed class for strongly-typed filter parameter configurations.
 */
sealed class FilterConfig(val type: FilterType) {

    /**
     * Gaussian Blur filter configuration.
     *
     * @param radius Blur kernel radius in pixels [1.0, 50.0].
     * @param iterations Number of convolution box blur passes [1, 3] (default 2).
     */
    data class GaussianBlurConfig(
        val radius: Float = 10f,
        val iterations: Int = 2
    ) : FilterConfig(FilterType.GAUSSIAN_BLUR) {
        init {
            require(radius in 1f..50f) { "Blur radius must be in 1.0..50.0, got $radius" }
            require(iterations in 1..3) { "Iterations must be in 1..3, got $iterations" }
        }
    }

    /**
     * Sharpen (Unsharp Mask) filter configuration.
     *
     * @param strength Sharpen enhancement strength [0.1, 5.0] (default 1.5).
     * @param radius Mask blur radius in pixels [1.0, 10.0] (default 2.0).
     */
    data class SharpenConfig(
        val strength: Float = 1.5f,
        val radius: Float = 2f
    ) : FilterConfig(FilterType.SHARPEN) {
        init {
            require(strength in 0.1f..5f) { "Sharpen strength must be in 0.1..5.0, got $strength" }
            require(radius in 1f..10f) { "Sharpen radius must be in 1.0..10.0, got $radius" }
        }
    }

    /**
     * Outline / Edge Detection filter configuration using Sobel operator.
     *
     * @param threshold Gradient magnitude cutoff threshold [1.0, 255.0] (default 30.0).
     * @param edgeColor ARGB 32-bit color for detected outlines (default black 0xFF000000).
     * @param backgroundColor ARGB 32-bit color for non-edge regions (default transparent 0x00000000).
     * @param invert Inverts edge and background assignments (default false).
     */
    data class OutlineConfig(
        val threshold: Float = 30f,
        val edgeColor: Long = 0xFF000000L,
        val backgroundColor: Long = 0x00000000L,
        val invert: Boolean = false
    ) : FilterConfig(FilterType.OUTLINE) {
        init {
            require(threshold in 1f..255f) { "Outline threshold must be in 1.0..255.0, got $threshold" }
        }
    }

    /**
     * Bloom / Glow filter configuration.
     *
     * @param radius Glow halo spread radius in pixels [1.0, 50.0] (default 15.0).
     * @param intensity Additive bloom intensity multiplier [0.1, 3.0] (default 1.2).
     * @param threshold Minimum luminance threshold [0.0, 1.0] for highlights (default 0.5).
     */
    data class GlowConfig(
        val radius: Float = 15f,
        val intensity: Float = 1.2f,
        val threshold: Float = 0.5f
    ) : FilterConfig(FilterType.GLOW) {
        init {
            require(radius in 1f..50f) { "Glow radius must be in 1.0..50.0, got $radius" }
            require(intensity in 0.1f..3f) { "Glow intensity must be in 0.1..3.0, got $intensity" }
            require(threshold in 0f..1f) { "Glow threshold must be in 0.0..1.0, got $threshold" }
        }
    }

    /**
     * Procedural Noise filter configuration.
     *
     * @param amount Noise intensity fraction [0.01, 1.0] (default 0.25).
     * @param monochrome Whether noise is monochrome (true) or RGB chromatic (false).
     * @param seed Deterministic integer seed for reproducible noise generation.
     */
    data class NoiseConfig(
        val amount: Float = 0.25f,
        val monochrome: Boolean = true,
        val seed: Int = 42
    ) : FilterConfig(FilterType.NOISE) {
        init {
            require(amount in 0.01f..1f) { "Noise amount must be in 0.01..1.0, got $amount" }
        }
    }

    /**
     * Pixelate / Mosaic distortion filter configuration.
     *
     * @param blockSize Spatial dimension of square pixel blocks [2, 64] px (default 8).
     */
    data class PixelateConfig(
        val blockSize: Int = 8
    ) : FilterConfig(FilterType.PIXELATE) {
        init {
            require(blockSize in 2..64) { "Pixelate block size must be in 2..64, got $blockSize" }
        }
    }

    /**
     * Ripple wave distortion filter configuration.
     *
     * @param amplitude Wave displacement amplitude in pixels [1.0, 50.0] (default 10.0).
     * @param wavelength Distance between wave crests in pixels [5.0, 200.0] (default 40.0).
     * @param phaseDeg Wave phase shift angle in degrees [0.0, 360.0] (default 0.0).
     */
    data class RippleConfig(
        val amplitude: Float = 10f,
        val wavelength: Float = 40f,
        val phaseDeg: Float = 0f
    ) : FilterConfig(FilterType.RIPPLE) {
        init {
            require(amplitude in 1f..50f) { "Ripple amplitude must be in 1.0..50.0, got $amplitude" }
            require(wavelength in 5f..200f) { "Ripple wavelength must be in 5.0..200.0, got $wavelength" }
            require(phaseDeg in 0f..360f) { "Ripple phaseDeg must be in 0.0..360.0, got $phaseDeg" }
        }
    }
}

/**
 * Cooperative cancellation token for long-running filter computations.
 */
class FilterCancellationToken {
    private val cancelled = AtomicBoolean(false)

    val isCancelled: Boolean
        get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
    }

    fun reset() {
        cancelled.set(false)
    }
}

/**
 * Runtime state representation for live preview rendering.
 */
data class FilterPreviewState(
    val isProcessing: Boolean = false,
    val previewPixels: IntArray? = null,
    val previewWidth: Int = 0,
    val previewHeight: Int = 0,
    val processingTimeMs: Long = 0L,
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FilterPreviewState
        if (isProcessing != other.isProcessing) return false
        if (previewWidth != other.previewWidth) return false
        if (previewHeight != other.previewHeight) return false
        if (processingTimeMs != other.processingTimeMs) return false
        if (errorMessage != other.errorMessage) return false
        if (previewPixels != null) {
            if (other.previewPixels == null) return false
            if (!previewPixels.contentEquals(other.previewPixels)) return false
        } else if (other.previewPixels != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isProcessing.hashCode()
        result = 31 * result + (previewPixels?.contentHashCode() ?: 0)
        result = 31 * result + previewWidth
        result = 31 * result + previewHeight
        result = 31 * result + processingTimeMs.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}
