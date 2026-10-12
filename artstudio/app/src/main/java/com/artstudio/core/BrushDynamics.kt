package com.artstudio.core

import kotlin.math.*

/**
 * Pressure curve response modes for tailoring stylus or velocity pressure response.
 */
enum class PressureResponseMode(val id: String, val displayName: String) {
    LINEAR("linear", "Linear (Standard)"),
    SOFT("soft", "Soft (Sensitive / Light Touch)"),
    FIRM("firm", "Firm (Heavy / Deliberate Touch)"),
    S_CURVE("s_curve", "S-Curve (High Contrast)");

    fun applyCurve(rawPressure: Float, gamma: Float = 1.0f): Float {
        val p = rawPressure.coerceIn(0.0f, 1.0f)
        return when (this) {
            LINEAR -> {
                if (abs(gamma - 1.0f) < 0.01f) p else p.pow(gamma)
            }
            SOFT -> {
                // Sensitive: raises lower pressures (gamma ~0.60)
                p.pow(0.60f * gamma)
            }
            FIRM -> {
                // Firm: suppresses light pressure, requires deliberate pressure (gamma ~1.75)
                p.pow(1.75f * gamma)
            }
            S_CURVE -> {
                // Smooth cubic Hermite interpolation (Smoothstep)
                val s = p * p * (3.0f - 2.0f * p)
                if (abs(gamma - 1.0f) < 0.01f) s else s.pow(gamma)
            }
        }.coerceIn(0.0f, 1.0f)
    }

    companion object {
        fun fromId(id: String): PressureResponseMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown pressure response mode: '$id'")
        }
    }
}

/**
 * Brush tip shapes supported by the shared stroke raster engine.
 */
enum class BrushTipShape(val id: String, val displayName: String) {
    ROUND("round", "Round"),
    SQUARE("square", "Square"),
    CHISEL("chisel", "Chisel / Calligraphic"),
    ELLIPSE("ellipse", "Elliptical Ribbon"),
    TEXTURE_GRAIN("texture_grain", "Textured Grain");

    companion object {
        fun fromId(id: String): BrushTipShape {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown brush tip shape: '$id'")
        }
    }
}

/**
 * Real-time stroke stabilization engine using exponential moving average (EMA)
 * smoothing to filter out tremor and micro-jitter from raw pointer streams.
 */
class StrokeStabilizer(
    var weight: Float = 0.0f
) {
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var isFirstPoint = true

    fun reset() {
        smoothedX = 0f
        smoothedY = 0f
        isFirstPoint = true
    }

    /**
     * Stabilizes incoming pointer coordinate (x, y) according to the configured weight in [0.0, 0.95].
     * If weight == 0, returns the raw coordinates unmodified.
     */
    fun push(x: Float, y: Float): Pair<Float, Float> {
        val w = weight.coerceIn(0.0f, 0.95f)
        if (w <= 0.001f || isFirstPoint) {
            smoothedX = x
            smoothedY = y
            isFirstPoint = false
            return Pair(x, y)
        }

        // Exponential moving average: target = prev + (curr - prev) * (1 - weight)
        val alpha = 1.0f - w
        smoothedX += (x - smoothedX) * alpha
        smoothedY += (y - smoothedY) * alpha

        return Pair(smoothedX, smoothedY)
    }

    /**
     * Batch stabilizes an entire sequence of StrokePoints.
     */
    fun stabilizePoints(points: List<StrokePoint>): List<StrokePoint> {
        if (weight <= 0.001f || points.isEmpty()) return points

        reset()
        val result = ArrayList<StrokePoint>(points.size)
        for (pt in points) {
            val (sx, sy) = push(pt.x, pt.y)
            result.add(
                StrokePoint(
                    x = sx,
                    y = sy,
                    pressure = pt.pressure,
                    tilt = pt.tilt,
                    timestampNano = pt.timestampNano,
                    orientation = pt.orientation
                )
            )
        }
        return result
    }
}
