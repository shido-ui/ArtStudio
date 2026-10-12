package com.artstudio.document.model

import java.util.UUID

/**
 * High-precision point along a vector stroke path with stylus dynamics.
 */
data class StrokePointData(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val timestampMs: Long = 0L
)

/**
 * A persistent vector stroke path within a vector layer.
 */
data class VectorPathData(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePointData>,
    val brushId: String = "inking_pen",
    val strokeColor: Long = 0xFF000000L, // ARGB Black
    val strokeWidth: Float = 4.0f
) {
    init {
        require(strokeWidth > 0f) { "Stroke width must be positive, got $strokeWidth" }
    }
}
