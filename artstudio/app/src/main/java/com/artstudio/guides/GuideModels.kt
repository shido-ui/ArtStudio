package com.artstudio.guides

import com.artstudio.color.ColorData
import com.artstudio.color.EyedropperEngine
import com.artstudio.color.EyedropperSampleMode
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Supported non-destructive drawing guide modes.
 */
enum class GuideType(val id: String, val displayName: String) {
    NONE("none", "Off"),
    STRAIGHT_LINE("straight_line", "Straight Line"),
    CIRCLE_ELLIPSE("circle_ellipse", "Circle / Ellipse"),
    RADIAL("radial", "Radial Rays"),
    PERSPECTIVE("perspective", "Perspective")
}

/**
 * Supported symmetry modes.
 */
enum class SymmetryType(val id: String, val displayName: String) {
    NONE("none", "Off"),
    MIRROR_VERTICAL("mirror_vertical", "Vertical Mirror"),
    MIRROR_HORIZONTAL("mirror_horizontal", "Horizontal Mirror"),
    MIRROR_DUAL("mirror_dual", "Dual Mirror (Quad)"),
    RADIAL("radial", "Radial Symmetry")
}

/**
 * Configuration for straight-line guide and angle snapping.
 */
data class LineGuideConfig(
    val originX: Float = 0f,
    val originY: Float = 0f,
    val angleDegrees: Float = 0f, // 0 = horizontal line
    val snapToAngleIncrements: Boolean = false,
    val angleIncrementDegrees: Float = 15f
)

/**
 * Configuration for circle and ellipse guide.
 */
data class EllipseGuideConfig(
    val centerX: Float = 1024f,
    val centerY: Float = 1024f,
    val radiusX: Float = 300f,
    val radiusY: Float = 300f,
    val rotationDegrees: Float = 0f
) {
    val isCircle: Boolean
        get() = kotlin.math.abs(radiusX - radiusY) < 0.001f

    init {
        require(radiusX > 0f) { "radiusX must be positive, got $radiusX" }
        require(radiusY > 0f) { "radiusY must be positive, got $radiusY" }
    }
}

/**
 * Configuration for radial guide rays.
 */
data class RadialGuideConfig(
    val centerX: Float = 1024f,
    val centerY: Float = 1024f,
    val rayCount: Int = 12, // e.g. 12 rays = 30° spacing
    val continuousRayAngle: Float? = null // if non-null, snaps to this single ray
) {
    init {
        require(rayCount >= 2) { "rayCount must be >= 2, got $rayCount" }
    }
}

/**
 * Perspective mode: 1-point, 2-point, or 3-point vanishing points.
 */
enum class PerspectiveType(val vanishingPointsCount: Int) {
    ONE_POINT(1),
    TWO_POINT(2),
    THREE_POINT(3)
}

/**
 * Vanishing point coordinate in document space.
 */
data class VanishingPoint(
    val x: Float,
    val y: Float
)

/**
 * Configuration for perspective guides.
 */
data class PerspectiveGuideConfig(
    val type: PerspectiveType = PerspectiveType.TWO_POINT,
    val horizonY: Float = 1024f,
    val vp1: VanishingPoint = VanishingPoint(-1000f, 1024f),
    val vp2: VanishingPoint = VanishingPoint(3048f, 1024f),
    val vp3: VanishingPoint = VanishingPoint(1024f, -2000f), // vertical vanishing point for 3-point
    val raySpacingDegrees: Float = 10f,
    val snapToVerticals: Boolean = true,
    val snapToHorizon: Boolean = true
)

/**
 * Configuration for symmetry transformations.
 */
data class SymmetryConfig(
    val type: SymmetryType = SymmetryType.NONE,
    val centerX: Float = 1024f,
    val centerY: Float = 1024f,
    val radialSectors: Int = 6, // for RADIAL symmetry (2..36)
    val showAxes: Boolean = true,
    val axisColorRgba: Long = 0xFF00E5FFL // Cyan
) {
    init {
        require(radialSectors in 2..36) { "radialSectors must be between 2 and 36, got $radialSectors" }
    }
}

/**
 * Comprehensive non-destructive drawing aids configuration.
 */
data class DrawingAidsConfig(
    val guideType: GuideType = GuideType.NONE,
    val snappingEnabled: Boolean = true,
    val snapDistance: Float = 24f, // in document pixels
    val lineGuide: LineGuideConfig = LineGuideConfig(),
    val ellipseGuide: EllipseGuideConfig = EllipseGuideConfig(),
    val radialGuide: RadialGuideConfig = RadialGuideConfig(),
    val perspectiveGuide: PerspectiveGuideConfig = PerspectiveGuideConfig(),
    val symmetry: SymmetryConfig = SymmetryConfig(),
    val guideVisible: Boolean = true,
    val guideOpacity: Float = 0.65f,
    val guideColorRgba: Long = 0xFF3D5AFEL // Electric Indigo
)

/**
 * Reference image overlay entity placed over the workspace for artist tracing and color sampling.
 * Kept completely distinct from document raster/vector paint layers.
 */
data class ReferenceImageOverlay(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Reference Image",
    val assetPath: String = "",
    val x: Float = 100f,
    val y: Float = 100f,
    val width: Float = 512f,
    val height: Float = 512f,
    val opacity: Float = 0.8f,
    val visible: Boolean = true,
    val pinnedToCanvas: Boolean = true,
    val pixels: IntArray? = null,
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0
) {
    init {
        require(width > 0f) { "width must be > 0, got $width" }
        require(height > 0f) { "height must be > 0, got $height" }
        require(opacity in 0f..1f) { "opacity must be in 0..1, got $opacity" }
    }

    /**
     * Samples color from reference image at document coordinates (docX, docY).
     * Returns null if coordinate is outside reference bounds or if image has no pixel data.
     */
    fun sampleColorAtDocumentCoord(
        docX: Float,
        docY: Float,
        sampleMode: EyedropperSampleMode = EyedropperSampleMode.POINT_1X1
    ): ColorData? {
        val pxs = pixels ?: return null
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
        if (docX < x || docX > x + width || docY < y || docY > y + height) return null

        val u = ((docX - x) / width).coerceIn(0f, 1f)
        val v = ((docY - y) / height).coerceIn(0f, 1f)

        val imgX = (u * (bitmapWidth - 1)).roundToInt()
        val imgY = (v * (bitmapHeight - 1)).roundToInt()

        return EyedropperEngine.samplePixels(
            pixels = pxs,
            width = bitmapWidth,
            height = bitmapHeight,
            x = imgX,
            y = imgY,
            mode = sampleMode
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReferenceImageOverlay) return false
        return id == other.id &&
                name == other.name &&
                assetPath == other.assetPath &&
                x == other.x &&
                y == other.y &&
                width == other.width &&
                height == other.height &&
                opacity == other.opacity &&
                visible == other.visible &&
                pinnedToCanvas == other.pinnedToCanvas &&
                bitmapWidth == other.bitmapWidth &&
                bitmapHeight == other.bitmapHeight
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }
}
