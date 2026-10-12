package com.artstudio.comic

import java.util.UUID
import kotlin.math.*

/**
 * Pure 2D point representation for comic and manga geometry.
 */
data class Point2D(val x: Float, val y: Float) {
    fun distanceTo(other: Point2D): Float =
        hypot(other.x - x, other.y - y)

    fun lerp(other: Point2D, t: Float): Point2D =
        Point2D(x + (other.x - x) * t, y + (other.y - y) * t)
}

/**
 * Pure 2D rectangle representation.
 */
data class Rect2D(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, bottom - top)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom

    fun inset(dx: Float, dy: Float): Rect2D =
        Rect2D(left + dx, top + dy, right - dx, bottom - dy)
}

/**
 * A single comic/manga panel frame.
 * Supports standard rectangular panels as well as dynamic polygonal/slanted action panels.
 *
 * @param id Unique identifier
 * @param name Display label (e.g. "Panel 1", "Action Slash Panel")
 * @param vertices Clockwise polygon vertices defining the panel border
 * @param strokeWidth Border line thickness in document pixels
 * @param strokeColor ARGB border stroke color (typically solid black)
 * @param fillColor ARGB panel background fill (typically white or transparent)
 * @param cornerRadius Optional corner rounding for rounded frames
 * @param innerMargin Safety padding inside the panel for nested drawing layers
 * @param clipContent Whether layers assigned to this panel should be clipped to its boundary
 */
data class ComicPanel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Panel",
    val vertices: List<Point2D>,
    val strokeWidth: Float = 4f,
    val strokeColor: Long = 0xFF000000L,
    val fillColor: Long = 0xFFFFFFFFL,
    val cornerRadius: Float = 0f,
    val innerMargin: Float = 8f,
    val clipContent: Boolean = true
) {
    init {
        require(vertices.size >= 3) { "A comic panel must have at least 3 vertices, got ${vertices.size}" }
    }

    /**
     * Computes the axis-aligned bounding box of this panel.
     */
    fun bounds(): Rect2D {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (v in vertices) {
            if (v.x < minX) minX = v.x
            if (v.y < minY) minY = v.y
            if (v.x > maxX) maxX = v.x
            if (v.y > maxY) maxY = v.y
        }
        return Rect2D(minX, minY, maxX, maxY)
    }

    val width: Float get() = bounds().width
    val height: Float get() = bounds().height

    /**
     * Determines whether this panel is an axis-aligned rectangle (4 vertices with orthogonal edges).
     */
    fun isRectangular(): Boolean {
        if (vertices.size != 4) return false
        val (p0, p1, p2, p3) = vertices
        val tol = 0.01f
        return (abs(p0.y - p1.y) < tol && abs(p1.x - p2.x) < tol && abs(p2.y - p3.y) < tol && abs(p3.x - p0.x) < tol) ||
               (abs(p0.x - p1.x) < tol && abs(p1.y - p2.y) < tol && abs(p2.x - p3.x) < tol && abs(p3.y - p0.y) < tol)
    }

    /**
     * Tests if a query point (px, py) is strictly inside this panel polygon using ray-casting.
     */
    fun containsPoint(px: Float, py: Float): Boolean {
        var inside = false
        val n = vertices.size
        for (i in 0 until n) {
            val p1 = vertices[i]
            val p2 = vertices[(i + 1) % n]
            if (((p1.y > py) != (p2.y > py)) &&
                (px < (p2.x - p1.x) * (py - p1.y) / ((p2.y - p1.y) + 1e-9f) + p1.x)
            ) {
                inside = !inside
            }
        }
        return inside
    }

    companion object {
        /**
         * Factory for standard rectangular comic panel.
         */
        fun createRectangular(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            name: String = "Panel",
            strokeWidth: Float = 4f,
            strokeColor: Long = 0xFF000000L,
            fillColor: Long = 0xFFFFFFFFL,
            cornerRadius: Float = 0f
        ): ComicPanel {
            val vertices = listOf(
                Point2D(x, y),
                Point2D(x + width, y),
                Point2D(x + width, y + height),
                Point2D(x, y + height)
            )
            return ComicPanel(
                name = name,
                vertices = vertices,
                strokeWidth = strokeWidth,
                strokeColor = strokeColor,
                fillColor = fillColor,
                cornerRadius = cornerRadius
            )
        }
    }
}

/**
 * Comic page layout containing an ensemble of panels and gutter guides.
 */
data class ComicPanelLayout(
    val pageWidth: Float = 2048f,
    val pageHeight: Float = 2048f,
    val marginOuter: Float = 60f,
    val gutterHorizontal: Float = 20f,
    val gutterVertical: Float = 14f,
    val panels: List<ComicPanel> = emptyList()
)

/**
 * Variant families of speech and thought balloons.
 */
enum class SpeechBubbleType(val id: String, val displayName: String) {
    SPEECH_OVAL("speech_oval", "Speech Oval"),
    SPEECH_RECTANGLE("speech_rectangle", "Narration Box"),
    THOUGHT_CLOUD("thought_cloud", "Thought Cloud"),
    SHOUT_BURST("shout_burst", "Shout Burst"),
    WHISPER("whisper", "Whisper Dashed");

    companion object {
        fun fromId(id: String): SpeechBubbleType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SPEECH_OVAL
    }
}

/**
 * Tail connector types for speech and thought bubbles.
 */
enum class BubbleTailType(val id: String, val displayName: String) {
    STRAIGHT_POINTER("straight_pointer", "Straight Pointer"),
    CURVED_POINTER("curved_pointer", "Curved Pointer"),
    THOUGHT_DOTS("thought_dots", "Thought Dots"),
    NONE("none", "No Tail");

    companion object {
        fun fromId(id: String): BubbleTailType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STRAIGHT_POINTER
    }
}

/**
 * Editable comic speech or thought balloon object.
 *
 * Stores geometry, tail orientation, styling, and text placement parameters.
 */
data class SpeechBubble(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Speech Bubble",
    val bubbleType: SpeechBubbleType = SpeechBubbleType.SPEECH_OVAL,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val tailType: BubbleTailType = BubbleTailType.STRAIGHT_POINTER,
    val tailTipX: Float = x + width / 2f,
    val tailTipY: Float = y + height + 50f,
    val tailBaseAngleDeg: Float = 90f,
    val tailBaseWidth: Float = 28f,
    val strokeWidth: Float = 3.5f,
    val strokeColor: Long = 0xFF000000L,
    val fillColor: Long = 0xFFFFFFFFL,
    val cornerRadius: Float = 16f,
    val burstSpikeCount: Int = 16,
    val burstSpikeDepth: Float = 0.25f,
    val cloudPuffCount: Int = 12,
    val text: String = "",
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 22f,
    val textColor: Long = 0xFF000000L
) {
    init {
        require(width > 0f) { "Bubble width must be > 0, got $width" }
        require(height > 0f) { "Bubble height must be > 0, got $height" }
        require(burstSpikeCount >= 6) { "Spike count must be >= 6, got $burstSpikeCount" }
        require(cloudPuffCount >= 4) { "Puff count must be >= 4, got $cloudPuffCount" }
    }

    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
    val radiusX: Float get() = width / 2f
    val radiusY: Float get() = height / 2f

    /**
     * Calculates the inner safe rectangular bounds for text placement inside this bubble,
     * ensuring dialogue text never collides with bubble borders, scalloped puffs, or spikes.
     */
    fun computeInnerSafeTextBounds(padding: Float = 10f): Rect2D {
        val cx = centerX
        val cy = centerY
        val rx = radiusX
        val ry = radiusY

        return when (bubbleType) {
            SpeechBubbleType.SPEECH_OVAL, SpeechBubbleType.WHISPER -> {
                // Max inscribed rectangle in ellipse has dimensions rx * sqrt(2), ry * sqrt(2)
                val halfW = max(10f, (rx * 0.7071f) - padding)
                val halfH = max(10f, (ry * 0.7071f) - padding)
                Rect2D(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
            }
            SpeechBubbleType.SPEECH_RECTANGLE -> {
                val pad = max(padding, cornerRadius * 0.5f)
                Rect2D(x + pad, y + pad, x + width - pad, y + height - pad)
            }
            SpeechBubbleType.THOUGHT_CLOUD -> {
                // Clouds have inner scalloped valleys at ~0.80 of guide radius
                val effectiveRx = rx * 0.78f
                val effectiveRy = ry * 0.78f
                val halfW = max(10f, (effectiveRx * 0.7071f) - padding)
                val halfH = max(10f, (effectiveRy * 0.7071f) - padding)
                Rect2D(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
            }
            SpeechBubbleType.SHOUT_BURST -> {
                // Spikes dip down to (1.0 - burstSpikeDepth) of radius
                val innerRatio = max(0.4f, 1.0f - burstSpikeDepth)
                val effectiveRx = rx * innerRatio
                val effectiveRy = ry * innerRatio
                val halfW = max(10f, (effectiveRx * 0.7071f) - padding)
                val halfH = max(10f, (effectiveRy * 0.7071f) - padding)
                Rect2D(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
            }
        }
    }
}

/**
 * Supported pattern types for manga screentones and hatching fills.
 */
enum class ScreentonePattern(val id: String, val displayName: String) {
    DOT("dot", "Halftone Dot"),
    LINE("line", "Line Hatching"),
    CROSS_HATCH("cross_hatch", "Cross Hatching"),
    DIAMOND("diamond", "Diamond Pattern");

    companion object {
        fun fromId(id: String): ScreentonePattern =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DOT
    }
}

/**
 * Scalable procedural specification for comic/manga screentone fills.
 *
 * Retains infinite resolution and zero excessive raster memory by computing
 * tone frequencies analytically at render time.
 *
 * @param id Unique identifier
 * @param name User-visible label (e.g. "Ami-tone 30%", "Line Hatch 45°")
 * @param pattern Type of pattern (halftone dot, line hatching, cross hatching)
 * @param frequency Distance between repeat grid centers (dot pitch / line pitch in document pixels)
 * @param density Coverage ratio in [0.05, 0.95] (e.g. 0.20 = 20% tone, 0.50 = 50% tone)
 * @param angleDeg Screen rotation angle in degrees (standard manga default is 45° to minimize moiré)
 * @param foregroundColor ARGB tone tint (typically solid black 0xFF000000)
 * @param backgroundColor ARGB background color (typically transparent 0x00000000)
 * @param softness Edge transition anti-aliasing feathering width in pixels
 */
data class ScreentoneConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Screentone",
    val pattern: ScreentonePattern = ScreentonePattern.DOT,
    val frequency: Float = 16f,
    val density: Float = 0.25f,
    val angleDeg: Float = 45f,
    val foregroundColor: Long = 0xFF000000L,
    val backgroundColor: Long = 0x00000000L,
    val softness: Float = 0.5f
) {
    init {
        require(frequency >= 2f) { "Frequency pitch must be >= 2 px, got $frequency" }
        require(density in 0.01f..0.99f) { "Density must be between 0.01 and 0.99, got $density" }
    }

    /**
     * Dot radius calculated analytically from density coverage ratio.
     * Cell area = pitch^2. Dot area = pi * r^2. Density = (pi * r^2) / pitch^2.
     * => r = pitch * sqrt(density / pi).
     */
    val dotRadius: Float
        get() = frequency * sqrt(density / Math.PI.toFloat())

    /**
     * Line thickness calculated analytically from density coverage ratio.
     */
    val lineWidth: Float
        get() = frequency * density

    /**
     * Scales the frequency proportionately with canvas zoom or export resolution
     * without raster interpolation artifacts.
     */
    fun scaled(scaleFactor: Float): ScreentoneConfig {
        require(scaleFactor > 0f) { "Scale factor must be > 0" }
        return copy(
            frequency = max(2f, frequency * scaleFactor),
            softness = max(0.1f, softness * scaleFactor)
        )
    }
}

/**
 * Standard Manga and Comic Page Alignment Guides.
 *
 * Defines Bleed edge, Trim edge, and Inner Safe/Live drawing area.
 */
data class ComicPageGuides(
    val enabled: Boolean = true,
    val bleedMargin: Float = 0f,
    val trimMargin: Float = 40f,
    val safeAreaMargin: Float = 120f,
    val showGutterGuides: Boolean = true,
    val guideColor: Long = 0x803B82F6L
) {
    fun computeTrimBounds(canvasWidth: Float, canvasHeight: Float): Rect2D =
        Rect2D(trimMargin, trimMargin, canvasWidth - trimMargin, canvasHeight - trimMargin)

    fun computeSafeAreaBounds(canvasWidth: Float, canvasHeight: Float): Rect2D =
        Rect2D(safeAreaMargin, safeAreaMargin, canvasWidth - safeAreaMargin, canvasHeight - safeAreaMargin)
}
