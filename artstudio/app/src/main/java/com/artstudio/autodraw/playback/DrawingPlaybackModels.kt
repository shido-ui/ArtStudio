package com.artstudio.autodraw.playback

import com.artstudio.autodraw.schedule.ScheduledBrushParams
import com.artstudio.cv.CvPoint
import java.util.UUID

/**
 * High-level execution state of the visual drawing playback process.
 */
enum class PlaybackStateEnum {
    IDLE,
    PLAYING,
    PAUSED,
    COMPLETED,
    CANCELLED
}

/**
 * Mechanical or simulated status of the pen cursor tip.
 */
enum class PenTipState {
    /** Pen is raised above canvas in rapid transport travel between strokes. */
    LIFTED_TRANSIT,

    /** Pen is in physical contact with canvas surface depositing pigment. */
    DRAWING,

    /** Pen is resting at origin or paused at stationary coordinate. */
    IDLE
}

/**
 * Sequencing mode guiding stroke replay order on the canvas.
 */
enum class PlaybackMode {
    /**
     * Prioritizes drawing all perimeter outlines and structural line-art first
     * before filling in internal details, shading, or textures.
     */
    LINE_ART_FIRST,

    /**
     * Sequences strictly from bottom layer to top layer, drawing all elements
     * of each layer before progressing upwards in the layer stack.
     */
    LAYER_BY_LAYER,

    /**
     * Interleaves line art and color: draws contour boundary for a region,
     * then immediately draws internal hatching and color passes for that region
     * before proceeding to the next region.
     */
    DRAW_COLOUR_INTERLEAVED
}

/**
 * Presets and multipliers for playback speed regulation.
 */
object PlaybackSpeed {
    const val SLOW_025X = 0.25f
    const val HALF_050X = 0.50f
    const val NORMAL_100X = 1.00f
    const val DOUBLE_200X = 2.00f
    const val FAST_400X = 4.00f
    const val SUPER_800X = 8.00f
    const val MAX_INSTANT = 1000.0f
}

/**
 * Authoritative snapshot of schedule execution at a confirmed checkpoint.
 * Allows save and resume without losing committed artwork or progress.
 */
data class ScheduleCheckpoint(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Checkpoint",
    val strokeIndex: Int,
    val strokeProgressFraction: Float = 0f,
    val elapsedTimeMs: Long,
    val activeLayerId: String,
    val activeRegionId: String? = null,
    val committedStrokesCount: Int,
    val scheduleHash: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Progress telemetry for an individual layer during playback.
 */
data class LayerPlaybackProgress(
    val layerId: String,
    val layerName: String,
    val strokesCompleted: Int,
    val totalStrokes: Int,
    val progressFraction: Float,
    val isCompleted: Boolean
)

/**
 * Real-time telemetry describing the active stroke and pen tip dynamics.
 */
data class StrokePlaybackTelemetry(
    val strokeIndex: Int,
    val totalStrokes: Int,
    val pathId: String,
    val layerId: String,
    val regionId: String? = null,
    val strokeProgress: Float = 0f, // [0, 1]
    val penPosition: CvPoint,
    val penState: PenTipState,
    val brushParams: ScheduledBrushParams,
    val airTravelRemainingPx: Float = 0f
)

/**
 * Aggregated live state and progress telemetry published to the UI.
 */
data class PlaybackProgressInfo(
    val state: PlaybackStateEnum = PlaybackStateEnum.IDLE,
    val mode: PlaybackMode = PlaybackMode.LINE_ART_FIRST,
    val speedMultiplier: Float = 1.0f,
    val currentStrokeIndex: Int = 0,
    val totalStrokes: Int = 0,
    val overallProgressFraction: Float = 0f, // [0, 1]
    val currentStageName: String = "Ready",
    val currentLayerProgress: LayerPlaybackProgress? = null,
    val layerProgressList: List<LayerPlaybackProgress> = emptyList(),
    val currentStroke: StrokePlaybackTelemetry? = null,
    val elapsedTimeMs: Long = 0L,
    /**
     * Estimated remaining time in milliseconds.
     * Expose only when derived from measured progress, otherwise null.
     */
    val estimatedRemainingTimeMs: Long? = null,
    val hasMeasuredProgress: Boolean = false,
    val renderedFramesCount: Int = 0,
    val droppedFramesCount: Int = 0,
    val averageFps: Float = 60.0f,
    val checkpoints: List<ScheduleCheckpoint> = emptyList()
)

/**
 * Measured telemetry tracking frame pacing, frame drop rate, and working memory.
 */
data class FramePacingMetrics(
    val speedMultiplier: Float,
    val targetFrameTimeMs: Float = 16.67f,
    val averageFrameTimeMs: Float = 16.67f,
    val peakFrameTimeMs: Float = 16.67f,
    val droppedFrames: Int = 0,
    val totalFrames: Int = 0,
    val peakMemoryBytes: Long = 0L
)
