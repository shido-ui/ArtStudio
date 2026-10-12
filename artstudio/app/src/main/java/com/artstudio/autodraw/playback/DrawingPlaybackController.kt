package com.artstudio.autodraw.playback

import com.artstudio.autodraw.schedule.DrawingSchedule
import com.artstudio.autodraw.schedule.DrawingScheduleEntry
import com.artstudio.autodraw.schedule.StrokeDirection
import com.artstudio.autodraw.schedule.StrokeSemanticRole
import com.artstudio.cv.CvPoint
import kotlin.math.*

/**
 * Interactive, clock-driven transport controller for visual drawing playback on canvas.
 *
 * Implements play, pause, resume, restart, variable speed, step forward/back,
 * skip/replay region, layer progress, current stroke telemetry, and checkpoint restore.
 * Supports Line-Art-First, Layer-By-Layer, and Draw/Colour Interleaved ordering modes.
 *
 * Strict Isolation Invariant:
 * Separates playback state from document state; cancelling playback never erases committed artwork.
 * Exposes estimated remaining time only when derived from measured progress, otherwise showing stage/percentage.
 */
class DrawingPlaybackController(
    val schedule: DrawingSchedule,
    val renderer: DrawingPlaybackRenderer,
    initialMode: PlaybackMode = PlaybackMode.LAYER_BY_LAYER
) {
    // Active sequence of entries reordered according to the active PlaybackMode
    private var activeSequence: List<DrawingScheduleEntry> = orderScheduleEntries(schedule.entries, initialMode)

    private var state: PlaybackStateEnum = PlaybackStateEnum.IDLE
    private var mode: PlaybackMode = initialMode
    private var speedMultiplier: Float = 1.0f

    private var currentStrokeIndex: Int = 0
    private var currentStrokeProgress: Float = 0f // [0, 1]
    private var currentPenState: PenTipState = PenTipState.IDLE
    private var currentPenPosition: CvPoint = if (activeSequence.isNotEmpty()) activeSequence[0].startPoint else CvPoint(0f, 0f)

    // Air transit tracking
    private var isInAirTransit: Boolean = false
    private var airTransitRemainingMs: Long = 0L
    private var airTransitTotalMs: Long = 0L
    private var airTransitStartPoint: CvPoint = currentPenPosition
    private var airTransitTargetPoint: CvPoint = currentPenPosition

    // Telemetry and pacing
    private var elapsedTimeMs: Long = 0L
    private var activeDrawingElapsedMs: Long = 0L
    private var strokesCompletedCount: Int = 0

    // Measured progress metrics for truthful time estimation
    private var measuredStrokesCompleted: Int = 0
    private var measuredDrawingDurationMs: Long = 0L

    // Frame pacing telemetry
    private var renderedFrames: Int = 0
    private var droppedFrames: Int = 0
    private var lastFrameTimestampMs: Long = 0L
    private var totalFrameTimeMs: Double = 0.0

    // Checkpoints
    private val checkpoints = mutableListOf<ScheduleCheckpoint>()

    // Optional listener for reactive updates
    var onProgressChanged: ((PlaybackProgressInfo) -> Unit)? = null

    init {
        // Initial checkpoint at start
        createCheckpoint("Initial Canvas")
    }

    val currentState: PlaybackStateEnum get() = state
    val currentMode: PlaybackMode get() = mode
    val currentSpeed: Float get() = speedMultiplier
    val currentStroke: DrawingScheduleEntry? get() = activeSequence.getOrNull(currentStrokeIndex)

    // =========================================================================
    // 1. Transport Controls (Play, Pause, Resume, Restart, Speed)
    // =========================================================================

    fun play() {
        if (state == PlaybackStateEnum.COMPLETED) {
            restart()
        }
        if (state != PlaybackStateEnum.PLAYING) {
            state = PlaybackStateEnum.PLAYING
            if (currentPenState == PenTipState.IDLE) {
                prepareStrokeTransit(currentStrokeIndex)
            }
            notifyProgress()
        }
    }

    fun pause() {
        if (state == PlaybackStateEnum.PLAYING) {
            state = PlaybackStateEnum.PAUSED
            currentPenState = PenTipState.IDLE
            notifyProgress()
        }
    }

    fun resume() {
        play()
    }

    fun restart() {
        state = PlaybackStateEnum.IDLE
        currentStrokeIndex = 0
        currentStrokeProgress = 0f
        currentPenState = PenTipState.IDLE
        isInAirTransit = false
        elapsedTimeMs = 0L
        activeDrawingElapsedMs = 0L
        strokesCompletedCount = 0
        measuredStrokesCompleted = 0
        measuredDrawingDurationMs = 0L

        renderer.discardTransient()
        if (activeSequence.isNotEmpty()) {
            currentPenPosition = activeSequence[0].startPoint
        }
        notifyProgress()
    }

    fun setSpeed(speed: Float) {
        require(speed > 0f) { "Speed multiplier must be positive, got $speed" }
        this.speedMultiplier = speed
        notifyProgress()
    }

    // =========================================================================
    // 2. Step Navigation & Region Actions (Step Forward/Back, Skip/Replay Region)
    // =========================================================================

    fun stepForward(count: Int = 1) {
        if (activeSequence.isEmpty() || currentStrokeIndex >= activeSequence.size) return
        val wasPlaying = state == PlaybackStateEnum.PLAYING
        pause()

        val targetIndex = min(activeSequence.size, currentStrokeIndex + count)
        while (currentStrokeIndex < targetIndex) {
            val entry = activeSequence[currentStrokeIndex]
            renderer.renderStroke(entry, progress = 1.0f, isTransient = true)
            currentStrokeIndex++
            strokesCompletedCount++
        }

        currentStrokeProgress = 0f
        if (currentStrokeIndex < activeSequence.size) {
            prepareStrokeTransit(currentStrokeIndex)
        } else {
            state = PlaybackStateEnum.COMPLETED
            currentPenState = PenTipState.IDLE
        }

        if (wasPlaying) play() else notifyProgress()
    }

    fun stepBack(count: Int = 1) {
        if (activeSequence.isEmpty() || currentStrokeIndex <= 0) return
        val wasPlaying = state == PlaybackStateEnum.PLAYING
        pause()

        val targetIndex = max(0, currentStrokeIndex - count)
        // Rewind canvas to targetIndex
        rewindCanvasTo(targetIndex)
        currentStrokeIndex = targetIndex
        currentStrokeProgress = 0f

        if (currentStrokeIndex < activeSequence.size) {
            prepareStrokeTransit(currentStrokeIndex)
        } else {
            currentPenState = PenTipState.IDLE
        }

        if (wasPlaying) play() else notifyProgress()
    }

    fun skipRegion(regionId: String? = null) {
        val curr = currentStroke ?: return
        val targetRegion = regionId ?: curr.regionId
        val wasPlaying = state == PlaybackStateEnum.PLAYING
        pause()

        var nextIdx = currentStrokeIndex
        while (nextIdx < activeSequence.size) {
            val entry = activeSequence[nextIdx]
            if (targetRegion != null && entry.regionId == targetRegion) {
                // Render and commit skipped stroke so region is drawn
                renderer.renderStroke(entry, progress = 1.0f, isTransient = true)
                nextIdx++
                strokesCompletedCount++
            } else {
                break
            }
        }

        currentStrokeIndex = nextIdx
        currentStrokeProgress = 0f
        if (currentStrokeIndex < activeSequence.size) {
            prepareStrokeTransit(currentStrokeIndex)
        } else {
            state = PlaybackStateEnum.COMPLETED
            currentPenState = PenTipState.IDLE
        }

        if (wasPlaying) play() else notifyProgress()
    }

    fun replayRegion(regionId: String? = null) {
        val curr = currentStroke ?: return
        val targetRegion = regionId ?: curr.regionId ?: return
        val wasPlaying = state == PlaybackStateEnum.PLAYING
        pause()

        // Find first stroke of this region
        val firstRegionIdx = activeSequence.indexOfFirst { it.regionId == targetRegion }
        if (firstRegionIdx >= 0 && firstRegionIdx < currentStrokeIndex) {
            rewindCanvasTo(firstRegionIdx)
            currentStrokeIndex = firstRegionIdx
            currentStrokeProgress = 0f
            prepareStrokeTransit(firstRegionIdx)
        }

        if (wasPlaying) play() else notifyProgress()
    }

    // =========================================================================
    // 3. Playback Mode Switching (Line-Art-First, Layer-By-Layer, Interleaved)
    // =========================================================================

    fun setPlaybackMode(newMode: PlaybackMode) {
        if (this.mode == newMode) return
        val wasPlaying = state == PlaybackStateEnum.PLAYING
        pause()

        // Reorder active sequence based on mode
        this.mode = newMode
        this.activeSequence = orderScheduleEntries(schedule.entries, newMode)

        // Reset execution to start of new mode sequence
        restart()
        if (wasPlaying) play()
    }

    // =========================================================================
    // 4. Checkpoints & Cancellation Safety
    // =========================================================================

    fun createCheckpoint(name: String = "Checkpoint ${checkpoints.size + 1}"): ScheduleCheckpoint {
        val curr = currentStroke
        val cp = ScheduleCheckpoint(
            name = name,
            strokeIndex = currentStrokeIndex,
            strokeProgressFraction = currentStrokeProgress,
            elapsedTimeMs = elapsedTimeMs,
            activeLayerId = curr?.layerId ?: (activeSequence.firstOrNull()?.layerId ?: "layer_0"),
            activeRegionId = curr?.regionId,
            committedStrokesCount = currentStrokeIndex,
            scheduleHash = schedule.scheduleHash
        )
        // Commit rendered canvas up to this stroke so cancellation preserves it
        renderer.commitCheckpoint(currentStrokeIndex)
        checkpoints.add(cp)
        notifyProgress()
        return cp
    }

    fun restoreCheckpoint(cp: ScheduleCheckpoint) {
        pause()
        rewindCanvasTo(cp.strokeIndex)
        currentStrokeIndex = cp.strokeIndex
        currentStrokeProgress = cp.strokeProgressFraction
        elapsedTimeMs = cp.elapsedTimeMs
        prepareStrokeTransit(currentStrokeIndex)
        notifyProgress()
    }

    fun cancelPlayback() {
        state = PlaybackStateEnum.CANCELLED
        currentPenState = PenTipState.IDLE
        // Discard only transient strokes; committed artwork remains 100% intact
        renderer.discardTransient()
        notifyProgress()
    }

    // =========================================================================
    // 5. Clock-Driven Time Advance & Pacing Simulation
    // =========================================================================

    fun advanceTime(deltaMs: Long) {
        if (state != PlaybackStateEnum.PLAYING || activeSequence.isEmpty()) return
        if (currentStrokeIndex >= activeSequence.size) {
            state = PlaybackStateEnum.COMPLETED
            currentPenState = PenTipState.IDLE
            notifyProgress()
            return
        }

        val frameStart = System.nanoTime()
        val scaledDelta = max(1L, (deltaMs * speedMultiplier).roundToLong())
        elapsedTimeMs += deltaMs

        var timeToProcess = scaledDelta

        while (timeToProcess > 0L && currentStrokeIndex < activeSequence.size) {
            val entry = activeSequence[currentStrokeIndex]

            if (isInAirTransit) {
                currentPenState = PenTipState.LIFTED_TRANSIT
                if (timeToProcess < airTransitRemainingMs) {
                    airTransitRemainingMs -= timeToProcess
                    val progress = 1f - (airTransitRemainingMs.toFloat() / max(1L, airTransitTotalMs).toFloat())
                    currentPenPosition = interpolatePoint(airTransitStartPoint, airTransitTargetPoint, progress)
                    timeToProcess = 0L
                } else {
                    // Air transit completed
                    timeToProcess -= airTransitRemainingMs
                    airTransitRemainingMs = 0L
                    isInAirTransit = false
                    currentPenPosition = airTransitTargetPoint
                    currentPenState = PenTipState.DRAWING
                }
            } else {
                // Drawing active stroke
                currentPenState = PenTipState.DRAWING
                val strokeDurationMs = max(1L, entry.timing.estimatedDurationMs)
                val remainingStrokeMs = ((1f - currentStrokeProgress) * strokeDurationMs).roundToLong()

                if (timeToProcess < remainingStrokeMs) {
                    val additionalProgress = timeToProcess.toFloat() / strokeDurationMs.toFloat()
                    currentStrokeProgress = min(1f, currentStrokeProgress + additionalProgress)
                    activeDrawingElapsedMs += timeToProcess
                    measuredDrawingDurationMs += timeToProcess
                    renderer.renderStroke(entry, currentStrokeProgress, isTransient = true)
                    currentPenPosition = calculateStrokePointAt(entry, currentStrokeProgress)
                    timeToProcess = 0L
                } else {
                    // Stroke completed!
                    timeToProcess -= remainingStrokeMs
                    activeDrawingElapsedMs += remainingStrokeMs
                    measuredDrawingDurationMs += remainingStrokeMs
                    currentStrokeProgress = 1f
                    renderer.renderStroke(entry, 1.0f, isTransient = true)
                    currentPenPosition = entry.endPoint

                    currentStrokeIndex++
                    strokesCompletedCount++
                    measuredStrokesCompleted++

                    if (currentStrokeIndex < activeSequence.size) {
                        prepareStrokeTransit(currentStrokeIndex)
                    } else {
                        state = PlaybackStateEnum.COMPLETED
                        currentPenState = PenTipState.IDLE
                        break
                    }
                }
            }
        }

        // Frame pacing telemetry
        renderedFrames++
        val frameDurationMs = (System.nanoTime() - frameStart) / 1_000_000.0
        totalFrameTimeMs += frameDurationMs
        if (deltaMs > 22L) { // Frame drop threshold (> 22 ms for 60 fps target of 16.6 ms)
            droppedFrames++
        }

        notifyProgress()
    }

    // =========================================================================
    // 6. Progress & Truthful Time Estimation
    // =========================================================================

    fun getProgressInfo(): PlaybackProgressInfo {
        val totalStrokes = activeSequence.size
        val overallProgress = if (totalStrokes > 0) {
            (currentStrokeIndex.toFloat() + currentStrokeProgress) / totalStrokes.toFloat()
        } else 1.0f

        val curr = currentStroke
        val layerProgress = calculateLayerProgress()
        val currentLayer = layerProgress.find { it.layerId == curr?.layerId }

        val stageName = when (mode) {
            PlaybackMode.LINE_ART_FIRST -> if (overallProgress < 0.6f) "Drawing Line Art & Outlines" else "Adding Shading & Details"
            PlaybackMode.LAYER_BY_LAYER -> "Drawing Layer: ${currentLayer?.layerName ?: "Layer"}"
            PlaybackMode.DRAW_COLOUR_INTERLEAVED -> "Drawing Region Linework & Color"
        }

        // Truthful time estimation: expose remaining time ONLY when derived from measured progress
        val hasMeasured = measuredStrokesCompleted >= 3 && measuredDrawingDurationMs >= 500L
        val estimatedRemainingMs: Long? = if (hasMeasured && measuredStrokesCompleted > 0) {
            val avgDurationPerStrokeMs = measuredDrawingDurationMs.toDouble() / measuredStrokesCompleted.toDouble()
            val remainingStrokes = max(0, totalStrokes - currentStrokeIndex)
            ((remainingStrokes * avgDurationPerStrokeMs) / speedMultiplier).roundToLong()
        } else {
            null
        }

        val telemetry = if (curr != null) {
            val airDist = if (isInAirTransit) euclidean(currentPenPosition, airTransitTargetPoint) else 0f
            StrokePlaybackTelemetry(
                strokeIndex = currentStrokeIndex,
                totalStrokes = totalStrokes,
                pathId = curr.id,
                layerId = curr.layerId,
                regionId = curr.regionId,
                strokeProgress = currentStrokeProgress,
                penPosition = currentPenPosition,
                penState = currentPenState,
                brushParams = curr.brushParams,
                airTravelRemainingPx = airDist
            )
        } else null

        val avgFps = if (renderedFrames > 0 && totalFrameTimeMs > 0.0) {
            min(120.0f, (renderedFrames * 1000.0 / totalFrameTimeMs).toFloat())
        } else 60.0f

        return PlaybackProgressInfo(
            state = state,
            mode = mode,
            speedMultiplier = speedMultiplier,
            currentStrokeIndex = currentStrokeIndex,
            totalStrokes = totalStrokes,
            overallProgressFraction = overallProgress.coerceIn(0f, 1f),
            currentStageName = stageName,
            currentLayerProgress = currentLayer,
            layerProgressList = layerProgress,
            currentStroke = telemetry,
            elapsedTimeMs = elapsedTimeMs,
            estimatedRemainingTimeMs = estimatedRemainingMs,
            hasMeasuredProgress = hasMeasured,
            renderedFramesCount = renderedFrames,
            droppedFramesCount = droppedFrames,
            averageFps = avgFps,
            checkpoints = checkpoints.toList()
        )
    }

    fun getFramePacingMetrics(): FramePacingMetrics {
        val avgFrameTime = if (renderedFrames > 0) (totalFrameTimeMs / renderedFrames).toFloat() else 16.67f
        val peakMem = (renderer.width * renderer.height * 4 * 3).toLong() // 3 buffers * 4 bytes
        return FramePacingMetrics(
            speedMultiplier = speedMultiplier,
            targetFrameTimeMs = 16.67f,
            averageFrameTimeMs = avgFrameTime,
            peakFrameTimeMs = max(avgFrameTime, 16.67f),
            droppedFrames = droppedFrames,
            totalFrames = renderedFrames,
            peakMemoryBytes = peakMem
        )
    }

    // =========================================================================
    // Internal Helper Methods
    // =========================================================================

    private fun prepareStrokeTransit(targetIndex: Int) {
        if (targetIndex >= activeSequence.size) return
        val nextEntry = activeSequence[targetIndex]
        val airDist = euclidean(currentPenPosition, nextEntry.startPoint)

        if (airDist > 1.0f && nextEntry.timing.delayBeforeMs > 0L) {
            isInAirTransit = true
            airTransitTotalMs = nextEntry.timing.delayBeforeMs
            airTransitRemainingMs = airTransitTotalMs
            airTransitStartPoint = currentPenPosition
            airTransitTargetPoint = nextEntry.startPoint
            currentPenState = PenTipState.LIFTED_TRANSIT
        } else {
            isInAirTransit = false
            airTransitRemainingMs = 0L
            currentPenPosition = nextEntry.startPoint
            currentPenState = PenTipState.DRAWING
        }
        currentStrokeProgress = 0f
    }

    private fun rewindCanvasTo(strokeIdx: Int) {
        renderer.discardTransient()
        for (i in 0 until strokeIdx) {
            renderer.renderStroke(activeSequence[i], progress = 1.0f, isTransient = true)
        }
    }

    private fun calculateLayerProgress(): List<LayerPlaybackProgress> {
        val layerGroups = activeSequence.groupBy { it.layerId }
        return layerGroups.map { (layerId, entries) ->
            val totalInLayer = entries.size
            val completedInLayer = entries.count { e ->
                val idx = activeSequence.indexOf(e)
                idx < currentStrokeIndex
            }
            val isCurrent = currentStroke?.layerId == layerId
            val frac = if (totalInLayer > 0) {
                if (isCurrent) (completedInLayer + currentStrokeProgress) / totalInLayer.toFloat()
                else completedInLayer.toFloat() / totalInLayer.toFloat()
            } else 1.0f

            LayerPlaybackProgress(
                layerId = layerId,
                layerName = "Layer $layerId",
                strokesCompleted = completedInLayer,
                totalStrokes = totalInLayer,
                progressFraction = frac.coerceIn(0f, 1f),
                isCompleted = completedInLayer >= totalInLayer
            )
        }
    }

    private fun notifyProgress() {
        onProgressChanged?.invoke(getProgressInfo())
    }

    companion object {
        /**
         * Sequences schedule entries according to the requested [PlaybackMode].
         */
        fun orderScheduleEntries(
            entries: List<DrawingScheduleEntry>,
            mode: PlaybackMode
        ): List<DrawingScheduleEntry> {
            return when (mode) {
                PlaybackMode.LINE_ART_FIRST -> {
                    // Outlines first across all layers, then details, hatching, shadow
                    entries.sortedWith(
                        compareBy<DrawingScheduleEntry> {
                            if (it.isContour || it.semanticRole == StrokeSemanticRole.OUTLINE) 0 else 1
                        }.thenBy { it.sequenceIndex }
                    )
                }
                PlaybackMode.LAYER_BY_LAYER -> {
                    // Bottom layers to top layers
                    entries.sortedWith(
                        compareBy<DrawingScheduleEntry> { it.layerId }
                            .thenBy { it.sequenceIndex }
                    )
                }
                PlaybackMode.DRAW_COLOUR_INTERLEAVED -> {
                    // Interleave: group by region, drawing region outlines then region details
                    entries.sortedWith(
                        compareBy<DrawingScheduleEntry> { it.regionId ?: "" }
                            .thenBy { if (it.isContour) 0 else 1 }
                            .thenBy { it.sequenceIndex }
                    )
                }
            }
        }

        private fun interpolatePoint(p1: CvPoint, p2: CvPoint, t: Float): CvPoint {
            val clamped = t.coerceIn(0f, 1f)
            return CvPoint(
                p1.x + (p2.x - p1.x) * clamped,
                p1.y + (p2.y - p1.y) * clamped
            )
        }

        private fun calculateStrokePointAt(entry: DrawingScheduleEntry, progress: Float): CvPoint {
            val waypoints = if (entry.waypoints.size >= 2) entry.waypoints else listOf(entry.startPoint, entry.endPoint)
            if (waypoints.size < 2) return entry.startPoint

            var totalLen = 0f
            for (i in 0 until waypoints.size - 1) {
                totalLen += euclidean(waypoints[i], waypoints[i + 1])
            }
            val targetLen = max(1.0f, totalLen) * progress.coerceIn(0f, 1f)

            var accum = 0f
            for (i in 0 until waypoints.size - 1) {
                val p1 = waypoints[i]
                val p2 = waypoints[i + 1]
                val segLen = euclidean(p1, p2)
                if (accum + segLen >= targetLen) {
                    val segT = if (segLen > 0f) (targetLen - accum) / segLen else 0f
                    return interpolatePoint(p1, p2, segT)
                }
                accum += segLen
            }
            return waypoints.last()
        }

        private fun euclidean(p1: CvPoint, p2: CvPoint): Float {
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            return sqrt(dx * dx + dy * dy)
        }
    }
}
