package com.artstudio.reference.region

import com.artstudio.reference.IntBoundingBox
import com.artstudio.reference.ReconstructionMode
import java.util.UUID

/**
 * Selection tool types available for marking reference regions of interest.
 */
enum class ReferenceRegionToolType(val displayName: String, val icon: String) {
    BRUSH("Brush Select", "🖌️"),
    LASSO("Freehand Lasso", "➰"),
    POLYGON("Polygonal Lasso", "🔷"),
    RECTANGLE("Rectangle Select", "🔲"),
    ELLIPSE("Ellipse Select", "⚪")
}

/**
 * Boolean combination modes for combining new selection geometry with an existing region mask.
 */
enum class RegionCombineMode(val displayName: String, val symbol: String) {
    REPLACE("New Selection", "✨"),
    ADD("Add to Region", "➕"),
    SUBTRACT("Subtract from Region", "➖"),
    CLEAR("Clear Region", "🗑️")
}

/**
 * Priority weighting for downstream processing and reconstruction of a marked region.
 */
enum class FocusPriority(val displayName: String) {
    LOW("Low"),
    NORMAL("Normal"),
    HIGH("High"),
    CRITICAL("Critical")
}

/**
 * Independent focus settings and guidance directives attached to a named reference region.
 *
 * @param priority Relative focus priority in the downstream analysis pipeline.
 * @param targetReconstructionMode Specialized reconstruction mode for this region (or null to inherit project mode).
 * @param detailSensitivity Sensitivity scale [0.0, 1.0] for fine feature/edge detection.
 * @param preserveExactColor Whether chromatic palette quantization should strictly preserve region colors.
 * @param edgeDilationRadius Additional padding/bleed in pixels applied when isolating the region.
 * @param customParameters Flexible key-value metadata for extensible analysis parameters.
 */
data class ReferenceFocusSettings(
    val priority: FocusPriority = FocusPriority.NORMAL,
    val targetReconstructionMode: ReconstructionMode? = null,
    val detailSensitivity: Float = 0.5f,
    val preserveExactColor: Boolean = false,
    val edgeDilationRadius: Int = 0,
    val customParameters: Map<String, String> = emptyMap()
) {
    init {
        require(detailSensitivity in 0.0f..1.0f) {
            "detailSensitivity must be in [0, 1], got $detailSensitivity"
        }
        require(edgeDilationRadius >= 0) {
            "edgeDilationRadius must be non-negative, got $edgeDilationRadius"
        }
    }
}

/**
 * Distinct named region of interest marked on a preserved reference image.
 *
 * Each region maintains its own independent instructions, settings, display styling,
 * and 8-bit unsigned alpha mask buffer [0..255].
 */
data class NamedReferenceRegion(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val instructions: String = "",
    val settings: ReferenceFocusSettings = ReferenceFocusSettings(),
    val width: Int,
    val height: Int,
    val mask: ByteArray,
    val colorRgba: Int = 0xFF00E5FF.toInt(),
    val overlayOpacity: Float = 0.45f,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(mask.size == width * height) {
            "Mask size (${mask.size}) does not match dimensions ($width x $height)"
        }
        require(overlayOpacity in 0.0f..1.0f) {
            "overlayOpacity must be in [0, 1], got $overlayOpacity"
        }
    }

    /**
     * Checks if coordinates (x, y) are included in this region's mask (alpha > threshold).
     */
    fun contains(x: Int, y: Int, threshold: Int = 128): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return (mask[y * width + x].toInt() and 0xFF) >= threshold
    }

    /**
     * Total number of selected pixels with alpha > 0.
     */
    val selectedPixelCount: Int
        get() {
            var count = 0
            for (i in mask.indices) {
                if ((mask[i].toInt() and 0xFF) > 0) count++
            }
            return count
        }

    /**
     * Resolves the axis-aligned bounding box of non-zero mask pixels.
     * Returns null if region mask is empty.
     */
    val bounds: IntBoundingBox?
        get() {
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1

            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    if ((mask[rowOffset + x].toInt() and 0xFF) > 0) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            return if (maxX >= minX && maxY >= minY) {
                IntBoundingBox(minX, minY, maxX, maxY)
            } else {
                null
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NamedReferenceRegion
        if (id != other.id) return false
        if (name != other.name) return false
        if (instructions != other.instructions) return false
        if (settings != other.settings) return false
        if (width != other.width || height != other.height) return false
        if (colorRgba != other.colorRgba) return false
        if (overlayOpacity != other.overlayOpacity) return false
        if (isVisible != other.isVisible) return false
        if (isLocked != other.isLocked) return false
        if (!mask.contentEquals(other.mask)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + instructions.hashCode()
        result = 31 * result + settings.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + colorRgba
        result = 31 * result + overlayOpacity.hashCode()
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + mask.contentHashCode()
        return result
    }
}

/**
 * Metadata capturing the preserved source reference file identity and dimensions.
 */
data class ReferenceSourceMetadata(
    val sourceName: String,
    val sourceSha256: String,
    val rawWidth: Int,
    val rawHeight: Int,
    val normalizedWidth: Int,
    val normalizedHeight: Int,
    val exifOrientation: Int = 1
)

/**
 * Crop boundary sub-rectangle defined on the reference image.
 */
data class ReferenceCropBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0 && height > 0) { "Crop dimensions must be positive ($width x $height)" }
    }

    val right: Int get() = x + width - 1
    val bottom: Int get() = y + height - 1

    fun toIntBoundingBox(): IntBoundingBox = IntBoundingBox(x, y, right, bottom)
}

/**
 * Viewport transform state handling interactive pan, zoom, and source crop mapping
 * between screen coordinates and immutable reference document coordinates.
 */
data class ReferenceViewportState(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1.0f,
    val crop: ReferenceCropBounds? = null
) {
    init {
        require(zoom > 0f) { "Zoom must be positive, got $zoom" }
    }

    /**
     * Converts interactive screen coordinates to reference image coordinates.
     */
    fun screenToReference(screenX: Float, screenY: Float): Pair<Float, Float> {
        val refX = (screenX - panX) / zoom + (crop?.x?.toFloat() ?: 0f)
        val refY = (screenY - panY) / zoom + (crop?.y?.toFloat() ?: 0f)
        return Pair(refX, refY)
    }

    /**
     * Converts reference image coordinates to interactive screen coordinates.
     */
    fun referenceToScreen(refX: Float, refY: Float): Pair<Float, Float> {
        val relX = refX - (crop?.x?.toFloat() ?: 0f)
        val relY = refY - (crop?.y?.toFloat() ?: 0f)
        val screenX = relX * zoom + panX
        val screenY = relY * zoom + panY
        return Pair(screenX, screenY)
    }
}

/**
 * Complete standalone Analysis Project holding multiple named focus regions,
 * user instructions, settings, and reference provenance.
 */
data class ReferenceAnalysisProject(
    val projectId: String = UUID.randomUUID().toString(),
    val projectName: String,
    val referenceSource: ReferenceSourceMetadata,
    val cropBounds: ReferenceCropBounds? = null,
    val regions: List<NamedReferenceRegion> = emptyList(),
    val activeRegionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val activeRegion: NamedReferenceRegion?
        get() = regions.find { it.id == activeRegionId } ?: regions.firstOrNull()

    /**
     * Reference canvas width (taking into account crop if active).
     */
    val effectiveWidth: Int
        get() = cropBounds?.width ?: referenceSource.normalizedWidth

    /**
     * Reference canvas height (taking into account crop if active).
     */
    val effectiveHeight: Int
        get() = cropBounds?.height ?: referenceSource.normalizedHeight
}
