package com.artstudio.pixelops

import com.artstudio.document.io.ArtJson
import kotlin.math.max
import kotlin.math.min

/**
 * Pixel modification tool modes that alter existing layer pixels.
 */
enum class PixelModifierMode(val id: String, val displayName: String) {
    SMUDGE("smudge", "Smudge"),
    BLUR("blur", "Blur"),
    BLEND("blend", "Blend / Wet Mixer");

    companion object {
        fun fromId(id: String): PixelModifierMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SMUDGE
        }
    }
}

/**
 * Configuration parameters for pixel modification operations (Smudge, Blur, Blend).
 * All properties are fully validated with numerical constraints and serializable to JSON.
 */
data class PixelModifierConfig(
    val mode: PixelModifierMode = PixelModifierMode.SMUDGE,
    val size: Float = 32.0f,
    val strength: Float = 0.5f,
    val spacing: Float = 0.10f,
    val hardness: Float = 0.8f,
    val sampleRadiusRatio: Float = 0.5f,
    val reservoirRetention: Float = 0.70f,
    val pressureSensitivity: Float = 1.0f,
    val preserveAlpha: Boolean = false
) {
    init {
        require(size in 1.0f..512.0f) { "Size must be in [1.0, 512.0], got $size" }
        require(strength in 0.01f..1.0f) { "Strength must be in [0.01, 1.0], got $strength" }
        require(spacing in 0.01f..1.0f) { "Spacing must be in [0.01, 1.0], got $spacing" }
        require(hardness in 0.0f..1.0f) { "Hardness must be in [0.0, 1.0], got $hardness" }
        require(sampleRadiusRatio in 0.05f..1.0f) { "Sample radius ratio must be in [0.05, 1.0], got $sampleRadiusRatio" }
        require(reservoirRetention in 0.0f..1.0f) { "Reservoir retention must be in [0.0, 1.0], got $reservoirRetention" }
        require(pressureSensitivity in 0.0f..2.0f) { "Pressure sensitivity must be in [0.0, 2.0], got $pressureSensitivity" }
    }

    val radius: Float get() = size / 2.0f

    fun toMap(): Map<String, Any> {
        return mapOf(
            "mode" to mode.id,
            "size" to size.toDouble(),
            "strength" to strength.toDouble(),
            "spacing" to spacing.toDouble(),
            "hardness" to hardness.toDouble(),
            "sampleRadiusRatio" to sampleRadiusRatio.toDouble(),
            "reservoirRetention" to reservoirRetention.toDouble(),
            "pressureSensitivity" to pressureSensitivity.toDouble(),
            "preserveAlpha" to preserveAlpha
        )
    }

    fun toJson(): String {
        return ArtJson.stringify(toMap())
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): PixelModifierConfig {
            return PixelModifierConfig(
                mode = PixelModifierMode.fromId(map["mode"] as? String ?: "smudge"),
                size = (map["size"] as? Number)?.toFloat() ?: 32.0f,
                strength = (map["strength"] as? Number)?.toFloat() ?: 0.5f,
                spacing = (map["spacing"] as? Number)?.toFloat() ?: 0.10f,
                hardness = (map["hardness"] as? Number)?.toFloat() ?: 0.8f,
                sampleRadiusRatio = (map["sampleRadiusRatio"] as? Number)?.toFloat() ?: 0.5f,
                reservoirRetention = (map["reservoirRetention"] as? Number)?.toFloat() ?: 0.70f,
                pressureSensitivity = (map["pressureSensitivity"] as? Number)?.toFloat() ?: 1.0f,
                preserveAlpha = map["preserveAlpha"] as? Boolean ?: false
            )
        }

        fun smudgePreset(size: Float = 32f, strength: Float = 0.6f): PixelModifierConfig {
            return PixelModifierConfig(
                mode = PixelModifierMode.SMUDGE,
                size = size,
                strength = strength,
                spacing = 0.08f,
                hardness = 0.75f,
                reservoirRetention = 0.70f
            )
        }

        fun blurPreset(size: Float = 40f, strength: Float = 0.5f): PixelModifierConfig {
            return PixelModifierConfig(
                mode = PixelModifierMode.BLUR,
                size = size,
                strength = strength,
                spacing = 0.12f,
                hardness = 0.60f,
                sampleRadiusRatio = 0.5f
            )
        }

        fun blendPreset(size: Float = 32f, strength: Float = 0.5f): PixelModifierConfig {
            return PixelModifierConfig(
                mode = PixelModifierMode.BLEND,
                size = size,
                strength = strength,
                spacing = 0.10f,
                hardness = 0.70f,
                reservoirRetention = 0.40f
            )
        }
    }
}

/**
 * Mutable bounding box tracking affected canvas coordinates during a pixel operation stroke.
 */
data class PixelBoundingBox(
    var minX: Int = Int.MAX_VALUE,
    var minY: Int = Int.MAX_VALUE,
    var maxX: Int = Int.MIN_VALUE,
    var maxY: Int = Int.MIN_VALUE
) {
    val isEmpty: Boolean get() = minX > maxX || minY > maxY

    val width: Int get() = if (isEmpty) 0 else maxX - minX + 1
    val height: Int get() = if (isEmpty) 0 else maxY - minY + 1

    fun include(x: Float, y: Float, radius: Float, canvasWidth: Int, canvasHeight: Int) {
        val r = kotlin.math.ceil(radius).toInt() + 1
        val left = max(0, (x - r).toInt())
        val top = max(0, (y - r).toInt())
        val right = min(canvasWidth - 1, (x + r).toInt())
        val bottom = min(canvasHeight - 1, (y + r).toInt())

        if (left <= right && top <= bottom) {
            minX = min(minX, left)
            minY = min(minY, top)
            maxX = max(maxX, right)
            maxY = max(maxY, bottom)
        }
    }

    fun clamp(canvasWidth: Int, canvasHeight: Int) {
        if (!isEmpty) {
            minX = max(0, minX)
            minY = max(0, minY)
            maxX = min(canvasWidth - 1, maxX)
            maxY = min(canvasHeight - 1, maxY)
        }
    }
}

/**
 * Result report from executing a pixel modification operation.
 */
data class PixelModifierResult(
    val success: Boolean,
    val bounds: PixelBoundingBox,
    val dabCount: Int,
    val modifiedPixelCount: Int,
    val durationMs: Long
)
