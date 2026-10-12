package com.artstudio.core

import kotlin.math.PI

/**
 * Encapsulates extracted hardware stylus dynamics, angle conversions, and palm contact evaluation.
 */
data class StylusDynamicsResult(
    val tiltRadians: Float,
    val tiltDegrees: Float,
    val orientationRadians: Float,
    val orientationDegrees: Float,
    val contactArea: Float,
    val isPalmDetected: Boolean
)

object StylusDynamics {
    /**
     * Touch contact size threshold above which a touch is classified as palm/wrist contact.
     */
    const val PALM_SIZE_THRESHOLD = 0.55f

    /**
     * Default perpendicular tilt (0.0 rad / 0.0 deg).
     */
    const val DEFAULT_TILT_RAD = 0.0f

    /**
     * Default orientation (0.0 rad / 0.0 deg).
     */
    const val DEFAULT_ORIENTATION_RAD = 0.0f

    fun extractDynamics(
        sample: PointerSample,
        isStylusDrawingActive: Boolean = false,
        isCurrentActiveDrawingPointer: Boolean = false
    ): StylusDynamicsResult {
        // Tilt: angle in radians from perpendicular (0 to PI/2)
        val tiltRad = if (sample.tilt.isNaN() || sample.tilt < 0f) DEFAULT_TILT_RAD else sample.tilt
        val tiltDeg = (tiltRad * 180f / PI.toFloat()).coerceIn(0f, 90f)

        // Orientation: angle in radians around Z axis (-PI to PI)
        val orientRad = if (sample.orientation.isNaN()) DEFAULT_ORIENTATION_RAD else sample.orientation
        val orientDeg = (orientRad * 180f / PI.toFloat()).coerceIn(-180f, 180f)

        val contact = if (sample.contactArea.isNaN()) 0.0f else sample.contactArea

        // Palm detection:
        // 1. If contact area exceeds threshold -> palm contact
        // 2. If stylus is actively drawing and a DIFFERENT touch is a finger -> palm contact
        val isPalm = (contact >= PALM_SIZE_THRESHOLD) ||
                (isStylusDrawingActive && !isCurrentActiveDrawingPointer && sample.toolType == ToolType.FINGER)

        return StylusDynamicsResult(
            tiltRadians = tiltRad,
            tiltDegrees = tiltDeg,
            orientationRadians = orientRad,
            orientationDegrees = orientDeg,
            contactArea = contact,
            isPalmDetected = isPalm
        )
    }
}
