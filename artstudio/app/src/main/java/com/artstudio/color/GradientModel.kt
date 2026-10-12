package com.artstudio.color

import com.artstudio.document.io.ArtJson
import java.util.UUID
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Geometric type of gradient fill.
 */
enum class GradientType(val id: String, val displayName: String) {
    LINEAR("linear", "Linear Gradient"),
    RADIAL("radial", "Radial Gradient"),
    ANGULAR("angular", "Angular / Sweep Gradient");

    companion object {
        fun fromId(id: String): GradientType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LINEAR
    }
}

/**
 * Tiling / edge wrap behavior outside the [0, 1] gradient span.
 */
enum class GradientTileMode(val id: String) {
    CLAMP("clamp"),
    REPEAT("repeat"),
    MIRROR("mirror");

    companion object {
        fun fromId(id: String): GradientTileMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLAMP
    }
}

/**
 * An individual color stop along a gradient spectrum.
 */
data class GradientColorStop(
    val position: Float, // Normalized [0.0, 1.0]
    val color: ColorData
) {
    init {
        require(position in -0.001f..1.001f) { "Stop position must be in [0, 1], got $position" }
    }

    val positionClamped: Float get() = position.coerceIn(0f, 1f)
}

/**
 * Complete, serializable gradient definition.
 */
data class GradientConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Linear Gradient",
    val type: GradientType = GradientType.LINEAR,
    val stops: List<GradientColorStop> = listOf(
        GradientColorStop(0.0f, ColorData.BLACK),
        GradientColorStop(1.0f, ColorData.WHITE)
    ),
    // Linear endpoints (in document pixels or normalized canvas coordinates)
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 2048f,
    val endY: Float = 0f,
    // Radial center and radius
    val centerX: Float = 1024f,
    val centerY: Float = 1024f,
    val radius: Float = 1024f,
    val tileMode: GradientTileMode = GradientTileMode.CLAMP,
    val interpolateInLinearSpace: Boolean = true
) {
    init {
        require(stops.size >= 2) { "Gradient must have at least 2 color stops, got ${stops.size}" }
    }

    val sortedStops: List<GradientColorStop> by lazy {
        stops.sortedBy { it.positionClamped }
    }

    fun withStop(position: Float, color: ColorData): GradientConfig {
        val newStops = (stops + GradientColorStop(position.coerceIn(0f, 1f), color)).sortedBy { it.positionClamped }
        return copy(stops = newStops)
    }

    fun removeStop(index: Int): GradientConfig {
        if (stops.size <= 2 || index !in stops.indices) return this
        val newStops = stops.toMutableList().apply { removeAt(index) }
        return copy(stops = newStops)
    }

    fun updateStop(index: Int, position: Float, color: ColorData): GradientConfig {
        if (index !in stops.indices) return this
        val newStops = stops.toMutableList().apply {
            this[index] = GradientColorStop(position.coerceIn(0f, 1f), color)
        }.sortedBy { it.positionClamped }
        return copy(stops = newStops)
    }

    fun reverseStops(): GradientConfig {
        val reversed = stops.map {
            GradientColorStop(1f - it.positionClamped, it.color)
        }.sortedBy { it.positionClamped }
        return copy(stops = reversed)
    }

    /**
     * Samples the gradient color at normalized parameter t in [0, 1].
     */
    fun sampleAt(t: Float): ColorData {
        val mappedT = when (tileMode) {
            GradientTileMode.CLAMP -> t.coerceIn(0f, 1f)
            GradientTileMode.REPEAT -> {
                val rem = t - floor(t)
                if (rem < 0f) rem + 1f else rem
            }
            GradientTileMode.MIRROR -> {
                val cycle = (t % 2f + 2f) % 2f
                if (cycle > 1f) 2f - cycle else cycle
            }
        }

        val s = sortedStops
        if (mappedT <= s.first().positionClamped) return s.first().color
        if (mappedT >= s.last().positionClamped) return s.last().color

        // Find sandwiching stops
        for (i in 0 until s.size - 1) {
            val stop0 = s[i]
            val stop1 = s[i + 1]
            val p0 = stop0.positionClamped
            val p1 = stop1.positionClamped
            if (mappedT in p0..p1) {
                val span = p1 - p0
                val localT = if (span > 1e-6f) (mappedT - p0) / span else 0f
                return stop0.color.lerp(stop1.color, localT, inLinearSpace = interpolateInLinearSpace)
            }
        }
        return s.last().color
    }

    companion object {
        fun defaultPresets(): List<GradientConfig> = listOf(
            GradientConfig(
                id = "grad_black_white",
                name = "Monochrome",
                type = GradientType.LINEAR,
                stops = listOf(
                    GradientColorStop(0f, ColorData.BLACK),
                    GradientColorStop(1f, ColorData.WHITE)
                )
            ),
            GradientConfig(
                id = "grad_sunset",
                name = "Sunset Glow",
                type = GradientType.LINEAR,
                stops = listOf(
                    GradientColorStop(0f, ColorData.fromHex("#FF512F")),
                    GradientColorStop(0.5f, ColorData.fromHex("#F09819")),
                    GradientColorStop(1f, ColorData.fromHex("#7000FF"))
                )
            ),
            GradientConfig(
                id = "grad_cyberpunk",
                name = "Neon Cyber",
                type = GradientType.LINEAR,
                stops = listOf(
                    GradientColorStop(0f, ColorData.fromHex("#00F2FE")),
                    GradientColorStop(0.5f, ColorData.fromHex("#4FACFE")),
                    GradientColorStop(1f, ColorData.fromHex("#F72585"))
                )
            ),
            GradientConfig(
                id = "grad_radial_vignette",
                name = "Soft Radial Vignette",
                type = GradientType.RADIAL,
                stops = listOf(
                    GradientColorStop(0f, ColorData(1f, 1f, 1f, 0f)),
                    GradientColorStop(0.7f, ColorData(0f, 0f, 0f, 0.2f)),
                    GradientColorStop(1f, ColorData(0f, 0f, 0f, 0.85f))
                )
            )
        )
    }
}

/**
 * Pure Kotlin raster evaluation engine for gradients.
 */
object GradientRenderer {

    /**
     * Evaluates linear gradient projection parameter t for point (x, y).
     */
    fun calculateLinearT(
        x: Float, y: Float,
        x0: Float, y0: Float,
        x1: Float, y1: Float
    ): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-6f) return 0f
        return ((x - x0) * dx + (y - y0) * dy) / lenSq
    }

    /**
     * Evaluates radial gradient parameter t for point (x, y).
     */
    fun calculateRadialT(
        x: Float, y: Float,
        cx: Float, cy: Float,
        radius: Float
    ): Float {
        if (radius <= 1e-6f) return 0f
        val dist = hypot(x - cx, y - cy)
        return dist / radius
    }

    /**
     * Renders a gradient into a destination ARGB integer pixel buffer.
     * @param dest Destination IntArray of size (width * height).
     * @param width Destination width.
     * @param height Destination height.
     * @param config Gradient configuration.
     * @param selectionMask Optional 8-bit alpha mask to restrict or modulate fill.
     */
    fun renderGradient(
        dest: IntArray,
        width: Int,
        height: Int,
        config: GradientConfig,
        selectionMask: ByteArray? = null
    ) {
        require(dest.size == width * height) { "Destination buffer size mismatch: ${dest.size} != ${width * height}" }
        if (selectionMask != null) {
            require(selectionMask.size == width * height) { "Selection mask size mismatch" }
        }

        val isRadial = config.type == GradientType.RADIAL

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val index = rowOffset + x

                val maskAlpha = selectionMask?.let { (it[index].toInt() and 0xFF) / 255f } ?: 1.0f
                if (maskAlpha <= 0f) continue

                val t = if (isRadial) {
                    calculateRadialT(x.toFloat(), y.toFloat(), config.centerX, config.centerY, config.radius)
                } else {
                    calculateLinearT(x.toFloat(), y.toFloat(), config.startX, config.startY, config.endX, config.endY)
                }

                val sample = config.sampleAt(t)
                val finalAlpha = sample.alpha * maskAlpha

                val finalColor = if (finalAlpha < 1.0f) {
                    ColorData(sample.red, sample.green, sample.blue, finalAlpha)
                } else {
                    sample
                }

                dest[index] = finalColor.argbInt
            }
        }
    }
}
