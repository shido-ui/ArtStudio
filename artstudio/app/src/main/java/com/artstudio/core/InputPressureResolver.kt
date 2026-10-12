package com.artstudio.core

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Pressure handling modes supported by the input pipeline.
 */
enum class PressureCalibrationMode(val displayName: String) {
    AUTO_DETECT("Auto-Detect Hardware / Fallback"),
    FORCE_VELOCITY_FALLBACK("Velocity-Simulated Fallback"),
    HARDWARE_ONLY("Direct Hardware Pressure"),
    FIXED_FALLBACK("Fixed Constant Pressure")
}

/**
 * Result of resolving pressure for a pointer sample.
 */
data class ResolvedPressure(
    val effectivePressure: Float,
    val isFallbackActive: Boolean,
    val mode: PressureCalibrationMode
)

/**
 * Resolves pressure for stylus, finger, and touch-only devices.
 *
 * When hardware digitizer reports variable pressure, it is calibrated and passed through.
 * When devices lack pressure sensors (reporting constant 1.0f or 0.0f) or user draws with fingers,
 * smoothly simulates dynamic pressure based on stroke speed and entry tapers.
 */
class InputPressureResolver(
    var mode: PressureCalibrationMode = PressureCalibrationMode.AUTO_DETECT
) {
    private var lastScreenX: Float = 0f
    private var lastScreenY: Float = 0f
    private var lastTimestampNano: Long = 0L
    private var smoothedPressure: Float = 0.5f
    private var strokePointCount: Int = 0
    private var firstHardwarePressure: Float? = null
    private var hardwarePressureVarianceDetected: Boolean = false

    fun startStroke(initialSample: PointerSample) {
        lastScreenX = initialSample.screenX
        lastScreenY = initialSample.screenY
        lastTimestampNano = initialSample.timestampNano
        smoothedPressure = 0.35f // Initial entry pressure
        strokePointCount = 0
        firstHardwarePressure = initialSample.pressure
        hardwarePressureVarianceDetected = false
    }

    fun resolvePressure(sample: PointerSample): ResolvedPressure {
        strokePointCount++

        return when (mode) {
            PressureCalibrationMode.HARDWARE_ONLY -> {
                val raw = sample.pressure
                val p = if (raw <= 0f) 0.5f else raw.coerceIn(0.01f, 1.0f)
                ResolvedPressure(p, isFallbackActive = false, mode = mode)
            }

            PressureCalibrationMode.FIXED_FALLBACK -> {
                val p = computeFixedWithTaper()
                ResolvedPressure(p, isFallbackActive = true, mode = mode)
            }

            PressureCalibrationMode.FORCE_VELOCITY_FALLBACK -> {
                val p = computeVelocityPressure(sample)
                ResolvedPressure(p, isFallbackActive = true, mode = mode)
            }

            PressureCalibrationMode.AUTO_DETECT -> {
                val isStylus = sample.toolType == ToolType.STYLUS || sample.toolType == ToolType.ERASER

                // Check for variance in hardware pressure
                val rawP = sample.pressure
                val firstP = firstHardwarePressure
                if (firstP != null && Math.abs(rawP - firstP) > 0.02f) {
                    hardwarePressureVarianceDetected = true
                }

                if (isStylus && (hardwarePressureVarianceDetected || (rawP > 0.01f && rawP < 0.99f))) {
                    // Verified variable hardware pressure from stylus
                    ResolvedPressure(rawP.coerceIn(0.01f, 1.0f), isFallbackActive = false, mode = mode)
                } else if (!isStylus || !hardwarePressureVarianceDetected) {
                    // Finger touch or flat hardware pressure (1.0f or unvarying) -> use velocity fallback
                    val p = computeVelocityPressure(sample)
                    ResolvedPressure(p, isFallbackActive = true, mode = mode)
                } else {
                    ResolvedPressure(rawP.coerceIn(0.01f, 1.0f), isFallbackActive = false, mode = mode)
                }
            }
        }
    }

    private fun computeFixedWithTaper(): Float {
        // Taper first 3 points from 0.3 up to 0.75
        val base = 0.75f
        return when (strokePointCount) {
            1 -> 0.35f
            2 -> 0.55f
            else -> base
        }
    }

    private fun computeVelocityPressure(sample: PointerSample): Float {
        val dtNano = sample.timestampNano - lastTimestampNano
        val dtSec = if (dtNano > 0L) dtNano / 1_000_000_000.0 else 0.016

        val dx = sample.screenX - lastScreenX
        val dy = sample.screenY - lastScreenY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        lastScreenX = sample.screenX
        lastScreenY = sample.screenY
        lastTimestampNano = sample.timestampNano

        val speedPxPerSec = if (dtSec > 0.0) (dist / dtSec).toFloat() else 0f

        // Map speed (50 px/s slow -> 1600 px/s fast) to pressure (0.85 slow -> 0.25 fast)
        val minSpeed = 50f
        val maxSpeed = 1600f
        val speedNorm = ((speedPxPerSec - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f)

        // Faster stroke = thinner taper; slower stroke = thicker line
        val targetPressure = 0.85f - (0.60f * speedNorm)

        // Exponential moving average damping to prevent jitter
        val alpha = 0.35f
        smoothedPressure = (alpha * targetPressure) + ((1f - alpha) * smoothedPressure)

        // Entry taper for first 2 points
        if (strokePointCount <= 2) {
            smoothedPressure = min(smoothedPressure, 0.25f + (strokePointCount * 0.15f))
        }

        return smoothedPressure.coerceIn(0.01f, 1.0f)
    }

    fun reset() {
        strokePointCount = 0
        firstHardwarePressure = null
        hardwarePressureVarianceDetected = false
        smoothedPressure = 0.5f
    }
}
