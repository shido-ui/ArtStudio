package com.artstudio.autodraw.schedule

import com.artstudio.cv.CvPoint
import java.security.MessageDigest
import java.util.UUID

/**
 * Orientation in which a path is drawn by the physical or simulated pen cursor.
 */
enum class StrokeDirection {
    FORWARD,
    REVERSE,
    BIDIRECTIONAL
}

/**
 * Stroke pressure modulation profile along path parameter length t in [0, 1].
 */
enum class PressureProfileType {
    CONSTANT,
    TAPER_ENDS,
    CALLIGRAPHIC,
    NATURAL_RAMP
}

/**
 * Brush rendering and tool dynamics configured for a scheduled stroke.
 */
data class ScheduledBrushParams(
    val sizePx: Float = 2.0f,
    val opacity: Float = 1.0f,
    val colorArgb: Long = 0xFF000000L,
    val brushPresetId: String = "pen_fineliner",
    val pressureProfile: PressureProfileType = PressureProfileType.TAPER_ENDS
) {
    init {
        require(sizePx > 0f) { "Brush size must be positive, got $sizePx" }
        require(opacity in 0f..1f) { "Opacity must be in [0, 1], got $opacity" }
    }
}

/**
 * Timing telemetry and simulation timeline coordinates for a scheduled stroke.
 */
data class ScheduleTiming(
    val estimatedDurationMs: Long,
    val delayBeforeMs: Long = 0L,
    val startTimestampMs: Long = 0L,
    val speedFactor: Float = 1.0f
) {
    init {
        require(estimatedDurationMs >= 0L) { "Duration must be non-negative" }
        require(delayBeforeMs >= 0L) { "Delay must be non-negative" }
        require(startTimestampMs >= 0L) { "Start timestamp must be non-negative" }
    }

    val endTimestampMs: Long
        get() = startTimestampMs + estimatedDurationMs
}

/**
 * Semantic role of a path within reconstructed artwork.
 */
enum class StrokeSemanticRole {
    OUTLINE,
    INTERNAL_DETAIL,
    HATCHING,
    SHADOW_ACCENT,
    FREEHAND
}

/**
 * Anatomical or compositional categorization tag for guided drawing schedules.
 */
enum class AnatomicalTag(val rank: Int) {
    HEAD(0),
    EYES(1),
    FACE(2),
    HAIR(3),
    TORSO(4),
    LIMBS(5),
    BACKGROUND(6),
    GENERAL_DETAIL(7)
}

/**
 * Ordering strategy guiding how paths are sequenced.
 */
enum class ScheduleOrderingStrategy {
    CONTOUR_FIRST,
    LAYER_BY_LAYER,
    SPATIALLY_EFFICIENT,
    ANATOMICAL
}

/**
 * Global configuration parameters governing schedule synthesis.
 */
data class DrawingScheduleConfig(
    val orderingStrategy: ScheduleOrderingStrategy = ScheduleOrderingStrategy.CONTOUR_FIRST,
    val nominalPenSpeedPxPerSec: Float = 400.0f,
    val penLiftSpeedPxPerSec: Float = 1200.0f,
    val singlePenCursorMode: Boolean = true,
    val allowDirectionReversal: Boolean = true,
    val allowWorkerPreprocessing: Boolean = true,
    val userPriorityOverrides: Map<String, Int> = emptyMap(),
    val randomizeOrder: Boolean = false,
    val seed: Long = 42L
) {
    init {
        require(nominalPenSpeedPxPerSec > 0f) { "Nominal speed must be positive" }
        require(penLiftSpeedPxPerSec > 0f) { "Pen lift speed must be positive" }
    }
}

/**
 * An individual drawing action scheduled for execution on the canvas.
 */
data class DrawingScheduleEntry(
    val id: String = UUID.randomUUID().toString(),
    val pathId: String,
    val regionId: String? = null,
    val layerId: String,
    val sequenceIndex: Int,
    val direction: StrokeDirection = StrokeDirection.FORWARD,
    val brushParams: ScheduledBrushParams = ScheduledBrushParams(),
    val timing: ScheduleTiming,
    val dependencies: Set<String> = emptySet(),
    val startPoint: CvPoint,
    val endPoint: CvPoint,
    val waypoints: List<CvPoint> = emptyList(),
    val pathLengthPx: Float,
    val isContour: Boolean = false,
    val isClosed: Boolean = false,
    val semanticRole: StrokeSemanticRole = StrokeSemanticRole.OUTLINE,
    val anatomicalTag: AnatomicalTag? = null
)

/**
 * Authoritative complete drawing schedule container.
 *
 * NOTE ON HISTORICAL FIDELITY:
 * This synthesized reconstruction schedule represents an optimized machine drawing plan;
 * it does not claim to reproduce the original artist's real human stroke history.
 */
data class DrawingSchedule(
    val id: String = UUID.randomUUID().toString(),
    val documentId: String? = null,
    val entries: List<DrawingScheduleEntry>,
    val config: DrawingScheduleConfig,
    val totalDurationMs: Long,
    val totalPenTravelPx: Float,
    val totalDrawingLengthPx: Float,
    val scheduleHash: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val disclaimer: String = "This synthesized reconstruction schedule represents an optimized machine drawing plan; it does not claim to reproduce the original artist's real human stroke history."
) {
    companion object {
        /**
         * Computes a deterministic SHA-256 hash representing the exact ordering,
         * directions, timings, and dependencies of a schedule.
         */
        fun computeScheduleHash(entries: List<DrawingScheduleEntry>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (entry in entries) {
                val line = buildString {
                    append(entry.pathId).append(':')
                    append(entry.sequenceIndex).append(':')
                    append(entry.direction.name).append(':')
                    append(entry.layerId).append(':')
                    append(entry.timing.startTimestampMs).append(':')
                    append(entry.timing.estimatedDurationMs).append(':')
                    append(entry.dependencies.sorted().joinToString(","))
                }
                digest.update(line.toByteArray(Charsets.UTF_8))
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
