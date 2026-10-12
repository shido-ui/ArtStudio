package com.artstudio.animation

/**
 * Snapshot of runtime playback state.
 */
data class AnimationPlaybackState(
    val isPlaying: Boolean = false,
    val isScrubbing: Boolean = false,
    val currentFrameIndex: Int = 0,
    val currentTick: Int = 0,
    val speedMultiplier: Float = 1.0f,
    val direction: Int = 1, // +1 forward, -1 backward (for Ping-Pong)
    val accumulatedTimeMs: Long = 0L
)

/**
 * Deterministic, clock-driven transport controller for frame-by-frame animation.
 *
 * Supports variable frame durations (e.g. held frames / exposures on-twos),
 * playback speeds, and LoopModes (LOOP, ONCE, PING_PONG).
 */
class AnimationPlaybackController(
    var timeline: AnimationTimeline,
    var onFrameChanged: ((frameIndex: Int) -> Unit)? = null
) {
    private var state = AnimationPlaybackState(
        currentFrameIndex = timeline.currentFrameIndex,
        currentTick = timeline.tickStartForFrame(timeline.currentFrameIndex)
    )

    private var wasPlayingBeforeScrub: Boolean = false

    val currentState: AnimationPlaybackState get() = state

    val isPlaying: Boolean get() = state.isPlaying
    val isScrubbing: Boolean get() = state.isScrubbing
    val currentFrameIndex: Int get() = state.currentFrameIndex
    val currentTick: Int get() = state.currentTick
    val speedMultiplier: Float get() = state.speedMultiplier

    fun play() {
        if (!state.isPlaying) {
            state = state.copy(isPlaying = true, isScrubbing = false)
        }
    }

    fun pause() {
        if (state.isPlaying) {
            state = state.copy(isPlaying = false)
        }
    }

    fun togglePlayPause(): Boolean {
        if (state.isPlaying) pause() else play()
        return state.isPlaying
    }

    fun stop() {
        state = state.copy(
            isPlaying = false,
            isScrubbing = false,
            currentFrameIndex = 0,
            currentTick = 0,
            direction = 1,
            accumulatedTimeMs = 0L
        )
        onFrameChanged?.invoke(0)
    }

    fun goToFrame(index: Int): Int {
        val safeIndex = index.coerceIn(timeline.frames.indices)
        val tick = timeline.tickStartForFrame(safeIndex)
        val msPerTick = 1000.0 / timeline.fps
        val accumulated = (tick * msPerTick).toLong()
        state = state.copy(
            currentFrameIndex = safeIndex,
            currentTick = tick,
            accumulatedTimeMs = accumulated
        )
        onFrameChanged?.invoke(safeIndex)
        return safeIndex
    }

    fun goToFirstFrame(): Int = goToFrame(0)

    fun goToLastFrame(): Int = goToFrame(timeline.frames.lastIndex)

    // =========================================================================
    // Interactive Timeline Scrubbing
    // =========================================================================

    fun startScrubbing() {
        wasPlayingBeforeScrub = state.isPlaying
        state = state.copy(isPlaying = false, isScrubbing = true)
    }

    fun updateScrub(frameIndex: Int): Int {
        val safeIndex = frameIndex.coerceIn(timeline.frames.indices)
        val tick = timeline.tickStartForFrame(safeIndex)
        val msPerTick = 1000.0 / timeline.fps
        val accumulated = (tick * msPerTick).toLong()
        val oldIndex = state.currentFrameIndex
        state = state.copy(
            currentFrameIndex = safeIndex,
            currentTick = tick,
            accumulatedTimeMs = accumulated,
            isScrubbing = true
        )
        if (safeIndex != oldIndex) {
            onFrameChanged?.invoke(safeIndex)
        }
        return safeIndex
    }

    fun scrubToTick(tick: Int): Int {
        val totalTicks = timeline.totalDurationTicks
        val safeTick = tick.coerceIn(0, (totalTicks - 1).coerceAtLeast(0))
        val resolvedFrameIndex = timeline.frameIndexAtTick(safeTick)
        val msPerTick = 1000.0 / timeline.fps
        val accumulated = (safeTick * msPerTick).toLong()
        val oldIndex = state.currentFrameIndex
        state = state.copy(
            currentFrameIndex = resolvedFrameIndex,
            currentTick = safeTick,
            accumulatedTimeMs = accumulated,
            isScrubbing = true
        )
        if (resolvedFrameIndex != oldIndex) {
            onFrameChanged?.invoke(resolvedFrameIndex)
        }
        return resolvedFrameIndex
    }

    fun scrubToNormalized(progress: Float): Int {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val totalTicks = timeline.totalDurationTicks
        val targetTick = if (totalTicks <= 1) 0 else ((totalTicks - 1) * clampedProgress).toInt()
        return scrubToTick(targetTick)
    }

    fun scrubToTimeMs(timeMs: Long): Int {
        val msPerTick = 1000.0 / timeline.fps
        val totalCycleMs = (timeline.totalDurationTicks * msPerTick).toLong().coerceAtLeast(1L)
        val wrappedMs = (timeMs % totalCycleMs).coerceAtLeast(0L)
        val targetTick = (wrappedMs / msPerTick).toInt()
        return scrubToTick(targetTick)
    }

    fun finishScrubbing(resumeIfWasPlaying: Boolean = false) {
        state = state.copy(
            isScrubbing = false,
            isPlaying = if (resumeIfWasPlaying) wasPlayingBeforeScrub else false
        )
    }

    fun updateTimeline(newTimeline: AnimationTimeline) {
        timeline = newTimeline
        val safeIndex = state.currentFrameIndex.coerceIn(timeline.frames.indices)
        val tick = timeline.tickStartForFrame(safeIndex)
        val msPerTick = 1000.0 / timeline.fps
        val accumulated = (tick * msPerTick).toLong()
        state = state.copy(
            currentFrameIndex = safeIndex,
            currentTick = tick,
            accumulatedTimeMs = accumulated
        )
    }

    fun setFps(fps: Int) {
        val validFps = fps.coerceIn(AnimationLimits.MIN_FPS, AnimationLimits.MAX_FPS)
        updateTimeline(timeline.copy(fps = validFps))
    }

    fun stepForward(): Int {
        val nextFrameIndex = when (timeline.loopMode) {
            LoopMode.LOOP -> (state.currentFrameIndex + 1) % timeline.frames.size
            LoopMode.ONCE -> (state.currentFrameIndex + 1).coerceAtMost(timeline.frames.lastIndex)
            LoopMode.PING_PONG -> {
                if (state.currentFrameIndex >= timeline.frames.lastIndex) {
                    (state.currentFrameIndex - 1).coerceAtLeast(0)
                } else {
                    state.currentFrameIndex + 1
                }
            }
        }
        return goToFrame(nextFrameIndex)
    }

    fun stepBackward(): Int {
        val prevFrameIndex = when (timeline.loopMode) {
            LoopMode.LOOP -> if (state.currentFrameIndex > 0) state.currentFrameIndex - 1 else timeline.frames.lastIndex
            LoopMode.ONCE -> (state.currentFrameIndex - 1).coerceAtLeast(0)
            LoopMode.PING_PONG -> {
                if (state.currentFrameIndex <= 0) {
                    (state.currentFrameIndex + 1).coerceAtMost(timeline.frames.lastIndex)
                } else {
                    state.currentFrameIndex - 1
                }
            }
        }
        return goToFrame(prevFrameIndex)
    }

    fun setSpeed(speed: Float) {
        val validSpeed = speed.coerceIn(0.1f, 5.0f)
        state = state.copy(speedMultiplier = validSpeed)
    }

    /**
     * Advances playback clock by [deltaMs] milliseconds.
     * Computes tick advance based on timeline FPS and speed multiplier,
     * resolves the corresponding frame index, and notifies listeners if the frame changed.
     *
     * @return The updated currentFrameIndex
     */
    fun advanceTime(deltaMs: Long): Int {
        if (!state.isPlaying || deltaMs <= 0L || timeline.frames.isEmpty()) {
            return state.currentFrameIndex
        }

        val totalTicks = timeline.totalDurationTicks
        if (totalTicks <= 1) {
            return 0
        }

        val msPerTick = 1000.0 / timeline.fps
        val effectiveDeltaMs = (deltaMs * state.speedMultiplier).toLong()
        val newTimeMs = state.accumulatedTimeMs + effectiveDeltaMs

        // Raw tick progress
        val totalCycleMs = (totalTicks * msPerTick)
        var newDirection = state.direction
        var calculatedTick: Int

        when (timeline.loopMode) {
            LoopMode.LOOP -> {
                val cycleOffsetMs = (newTimeMs % totalCycleMs.toLong()).coerceAtLeast(0)
                calculatedTick = (cycleOffsetMs / msPerTick).toInt().coerceIn(0, totalTicks - 1)
            }
            LoopMode.ONCE -> {
                if (newTimeMs >= totalCycleMs) {
                    // Reached end of animation
                    state = state.copy(
                        isPlaying = false,
                        currentFrameIndex = timeline.frames.lastIndex,
                        currentTick = totalTicks - 1,
                        accumulatedTimeMs = totalCycleMs.toLong()
                    )
                    onFrameChanged?.invoke(timeline.frames.lastIndex)
                    return timeline.frames.lastIndex
                }
                calculatedTick = (newTimeMs / msPerTick).toInt().coerceIn(0, totalTicks - 1)
            }
            LoopMode.PING_PONG -> {
                // Two-way ping-pong cycle length is (totalTicks - 1) * 2
                val pingPongCycleTicks = (totalTicks - 1) * 2
                if (pingPongCycleTicks <= 0) {
                    calculatedTick = 0
                } else {
                    val pingPongCycleMs = pingPongCycleTicks * msPerTick
                    val cycleOffsetMs = (newTimeMs % pingPongCycleMs.toLong()).coerceAtLeast(0)
                    val rawCycleTick = (cycleOffsetMs / msPerTick).toInt() % pingPongCycleTicks
                    if (rawCycleTick < totalTicks) {
                        calculatedTick = rawCycleTick
                        newDirection = 1
                    } else {
                        calculatedTick = pingPongCycleTicks - rawCycleTick
                        newDirection = -1
                    }
                }
            }
        }

        val resolvedFrameIndex = timeline.frameIndexAtTick(calculatedTick)
        val oldFrameIndex = state.currentFrameIndex

        state = state.copy(
            currentFrameIndex = resolvedFrameIndex,
            currentTick = calculatedTick,
            direction = newDirection,
            accumulatedTimeMs = newTimeMs
        )

        if (resolvedFrameIndex != oldFrameIndex) {
            onFrameChanged?.invoke(resolvedFrameIndex)
        }

        return resolvedFrameIndex
    }
}
