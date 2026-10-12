package com.artstudio.animation

import java.util.UUID

/**
 * Architectural Definition: Cel-Based Frame-by-Frame Animation
 *
 * In Art Studio, an [AnimationFrame] references one or more layer cels ([celLayerIds]) rather than
 * full-canvas snapshot bitmaps.
 *
 * Benefits:
 * 1. Zero Duplicate Allocation for Unchanged Artwork: Static layers (e.g. backgrounds, foregrounds)
 *    are stored once in [AnimationTimeline.staticLayerIds] and never duplicated across frames.
 * 2. Held Exposures (Cycles & Holds): When a frame is held across multiple timeline ticks,
 *    [durationTicks] increases without allocating new layer memory. If a drawing is repeated,
 *    multiple frames reference the same layer ID.
 * 3. Native Canvas Engine Integration: Because a cel is simply a [com.artstudio.document.model.LayerNode],
 *    the existing brush, eraser, fill, vector, and adjustment engines work directly on the active frame cel.
 */

/**
 * Represents a single frame in the frame-by-frame animation timeline.
 *
 * @param id Unique persistent identifier for this frame
 * @param name Human-readable frame label (e.g. "Frame 1", "Key 01")
 * @param durationTicks Duration in timeline frames / exposures (default 1 = on-ones, 2 = on-twos, etc.)
 * @param celLayerIds Layer IDs in the document that belong specifically to this frame's animation drawing
 * @param layerOpacityOverrides Optional per-frame opacity overrides for referenced cels
 * @param tag Optional color tag or semantic marker (e.g. "keyframe", "breakdown", "inbetween")
 */
data class AnimationFrame(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Frame",
    val durationTicks: Int = 1,
    val celLayerIds: List<String> = emptyList(),
    val layerOpacityOverrides: Map<String, Float> = emptyMap(),
    val tag: String? = null
) {
    init {
        require(durationTicks in AnimationLimits.MIN_FRAME_DURATION..AnimationLimits.MAX_FRAME_DURATION) {
            "Frame duration must be in ${AnimationLimits.MIN_FRAME_DURATION}..${AnimationLimits.MAX_FRAME_DURATION} ticks, got $durationTicks"
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "durationTicks" to durationTicks,
        "celLayerIds" to celLayerIds,
        "layerOpacityOverrides" to layerOpacityOverrides,
        "tag" to tag
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): AnimationFrame {
            val id = map["id"]?.toString() ?: UUID.randomUUID().toString()
            val name = map["name"]?.toString() ?: "Frame"
            val durationTicks = ((map["durationTicks"] as? Number)?.toInt() ?: 1)
                .coerceIn(AnimationLimits.MIN_FRAME_DURATION, AnimationLimits.MAX_FRAME_DURATION)
            val celLayerIds = (map["celLayerIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val rawOverrides = map["layerOpacityOverrides"] as? Map<String, Any?> ?: emptyMap()
            val layerOpacityOverrides = rawOverrides.mapNotNull { (k, v) ->
                val op = (v as? Number)?.toFloat() ?: return@mapNotNull null
                k to op.coerceIn(0f, 1f)
            }.toMap()
            val tag = map["tag"]?.toString()

            return AnimationFrame(
                id = id,
                name = name,
                durationTicks = durationTicks,
                celLayerIds = celLayerIds,
                layerOpacityOverrides = layerOpacityOverrides,
                tag = tag
            )
        }
    }
}

/**
 * Playback looping behavior.
 */
enum class LoopMode(val id: String) {
    LOOP("loop"),
    ONCE("once"),
    PING_PONG("ping_pong");

    companion object {
        fun fromId(id: String?): LoopMode = entries.find { it.id.equals(id, ignoreCase = true) } ?: LOOP
    }
}

/**
 * Onion skin color tinting behavior.
 */
enum class OnionSkinTintMode(val id: String) {
    TINTED("tinted"),           // Orange for past, Cyan for future
    ORIGINAL_COLOR("original"); // Natural artwork colors with alpha attenuation

    companion object {
        fun fromId(id: String?): OnionSkinTintMode = entries.find { it.id.equals(id, ignoreCase = true) } ?: TINTED
    }
}

/**
 * Onion skin opacity attenuation across multiple past/future frames.
 */
enum class OnionSkinFalloff(val id: String) {
    LINEAR("linear"),       // Opacity decays linearly with step distance
    CONSTANT("constant"),   // Fixed opacity across all ghost frames
    QUADRATIC("quadratic"); // Faster decay for distant in-betweens

    companion object {
        fun fromId(id: String?): OnionSkinFalloff = entries.find { it.id.equals(id, ignoreCase = true) } ?: LINEAR
    }
}

/**
 * Non-destructive onion skin configuration for drawing in-betweens.
 *
 * @param enabled Whether onion skinning is active on the canvas
 * @param framesBefore Number of previous frames to display (1..5)
 * @param framesAfter Number of subsequent frames to display (1..5)
 * @param tintBefore ARGB color tint for preceding frames (default: Red-Orange)
 * @param tintAfter ARGB color tint for following frames (default: Cyan-Blue)
 * @param opacityBefore Maximum opacity for preceding frames
 * @param opacityAfter Maximum opacity for following frames
 * @param showBefore Toggle visibility of preceding frames
 * @param showAfter Toggle visibility of following frames
 * @param tintMode Tinted monochrome vs original layer colors
 * @param falloff Attenuation curve across step depth
 * @param suppressDuringPlayback If true, bypasses onion skin passes during real-time playback for max performance
 */
data class OnionSkinSettings(
    val enabled: Boolean = false,
    val framesBefore: Int = 1,
    val framesAfter: Int = 1,
    val tintBefore: Long = 0xFFFF4500L,
    val tintAfter: Long = 0xFF00BFFFL,
    val opacityBefore: Float = 0.35f,
    val opacityAfter: Float = 0.25f,
    val showBefore: Boolean = true,
    val showAfter: Boolean = true,
    val tintMode: OnionSkinTintMode = OnionSkinTintMode.TINTED,
    val falloff: OnionSkinFalloff = OnionSkinFalloff.LINEAR,
    val suppressDuringPlayback: Boolean = true
) {
    init {
        require(framesBefore in 1..AnimationLimits.MAX_ONION_SKIN_FRAMES) {
            "framesBefore must be in 1..${AnimationLimits.MAX_ONION_SKIN_FRAMES}, got $framesBefore"
        }
        require(framesAfter in 1..AnimationLimits.MAX_ONION_SKIN_FRAMES) {
            "framesAfter must be in 1..${AnimationLimits.MAX_ONION_SKIN_FRAMES}, got $framesAfter"
        }
        require(opacityBefore in 0f..1f) { "opacityBefore must be in 0..1, got $opacityBefore" }
        require(opacityAfter in 0f..1f) { "opacityAfter must be in 0..1, got $opacityAfter" }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "framesBefore" to framesBefore,
        "framesAfter" to framesAfter,
        "tintBefore" to tintBefore,
        "tintAfter" to tintAfter,
        "opacityBefore" to opacityBefore,
        "opacityAfter" to opacityAfter,
        "showBefore" to showBefore,
        "showAfter" to showAfter,
        "tintMode" to tintMode.id,
        "falloff" to falloff.id,
        "suppressDuringPlayback" to suppressDuringPlayback
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): OnionSkinSettings {
            val enabled = (map["enabled"] as? Boolean) ?: false
            val before = ((map["framesBefore"] as? Number)?.toInt() ?: 1)
                .coerceIn(1, AnimationLimits.MAX_ONION_SKIN_FRAMES)
            val after = ((map["framesAfter"] as? Number)?.toInt() ?: 1)
                .coerceIn(1, AnimationLimits.MAX_ONION_SKIN_FRAMES)
            val tintBefore = (map["tintBefore"] as? Number)?.toLong() ?: 0xFFFF4500L
            val tintAfter = (map["tintAfter"] as? Number)?.toLong() ?: 0xFF00BFFFL
            val opBefore = ((map["opacityBefore"] as? Number)?.toFloat() ?: 0.35f).coerceIn(0f, 1f)
            val opAfter = ((map["opacityAfter"] as? Number)?.toFloat() ?: 0.25f).coerceIn(0f, 1f)
            val showBefore = (map["showBefore"] as? Boolean) ?: true
            val showAfter = (map["showAfter"] as? Boolean) ?: true
            val tintMode = OnionSkinTintMode.fromId(map["tintMode"]?.toString())
            val falloff = OnionSkinFalloff.fromId(map["falloff"]?.toString())
            val suppressDuringPlayback = (map["suppressDuringPlayback"] as? Boolean) ?: true

            return OnionSkinSettings(
                enabled = enabled,
                framesBefore = before,
                framesAfter = after,
                tintBefore = tintBefore,
                tintAfter = tintAfter,
                opacityBefore = opBefore,
                opacityAfter = opAfter,
                showBefore = showBefore,
                showAfter = showAfter,
                tintMode = tintMode,
                falloff = falloff,
                suppressDuringPlayback = suppressDuringPlayback
            )
        }
    }
}

/**
 * Explicit project limits to ensure bounded memory usage on mobile devices.
 */
object AnimationLimits {
    const val MIN_FRAMES = 1
    const val MAX_FRAMES = 240
    const val MIN_FPS = 1
    const val MAX_FPS = 60
    const val DEFAULT_FPS = 12
    const val MIN_FRAME_DURATION = 1
    const val MAX_FRAME_DURATION = 120
    const val MAX_ONION_SKIN_FRAMES = 5
    const val MAX_CEL_LAYERS_PER_FRAME = 32
}

/**
 * Immutable timeline model for frame-by-frame animation projects.
 *
 * @param fps Frame playback rate in frames-per-second (1..60)
 * @param frames Ordered list of animation frames
 * @param currentFrameIndex Index of the currently selected / visible frame (0 until frames.size)
 * @param staticLayerIds Layer IDs visible across all frames (e.g. background scenery, guides)
 * @param onionSkin Onion skin overlay settings
 * @param loopMode Playback repeat behavior (LOOP, ONCE, PING_PONG)
 */
data class AnimationTimeline(
    val fps: Int = AnimationLimits.DEFAULT_FPS,
    val frames: List<AnimationFrame> = listOf(AnimationFrame(name = "Frame 1")),
    val currentFrameIndex: Int = 0,
    val staticLayerIds: List<String> = emptyList(),
    val onionSkin: OnionSkinSettings = OnionSkinSettings(),
    val loopMode: LoopMode = LoopMode.LOOP
) {
    init {
        require(fps in AnimationLimits.MIN_FPS..AnimationLimits.MAX_FPS) {
            "FPS must be in ${AnimationLimits.MIN_FPS}..${AnimationLimits.MAX_FPS}, got $fps"
        }
        require(frames.isNotEmpty()) { "Animation timeline must contain at least 1 frame" }
        require(frames.size <= AnimationLimits.MAX_FRAMES) {
            "Frame count exceeds limit of ${AnimationLimits.MAX_FRAMES}, got ${frames.size}"
        }
        require(currentFrameIndex in frames.indices) {
            "currentFrameIndex must be in 0 until ${frames.size}, got $currentFrameIndex"
        }
    }

    /** Total duration in timeline ticks (sum of all frame duration ticks) */
    val totalDurationTicks: Int get() = frames.sumOf { it.durationTicks }

    /** Total animation duration in milliseconds */
    val totalDurationMs: Long get() = ((totalDurationTicks.toDouble() / fps) * 1000.0).toLong()

    /** Currently active frame */
    val currentFrame: AnimationFrame get() = frames[currentFrameIndex]

    /**
     * Resolves all layer IDs that should be visible for a given frame index.
     * Union of static background layers and the frame's specific cels.
     */
    fun activeLayerIdsForFrame(frameIndex: Int): Set<String> {
        val safeIndex = frameIndex.coerceIn(frames.indices)
        val frame = frames[safeIndex]
        return (staticLayerIds + frame.celLayerIds).toSet()
    }

    /**
     * Maps an absolute timeline tick (0 until totalDurationTicks) to a frame index.
     */
    fun frameIndexAtTick(tick: Int): Int {
        if (frames.isEmpty()) return 0
        val clampedTick = tick.coerceIn(0, (totalDurationTicks - 1).coerceAtLeast(0))
        var accumulated = 0
        for (i in frames.indices) {
            accumulated += frames[i].durationTicks
            if (clampedTick < accumulated) {
                return i
            }
        }
        return frames.lastIndex
    }

    /**
     * Returns the tick offset where the specified frame starts in the timeline.
     */
    fun tickStartForFrame(frameIndex: Int): Int {
        var tick = 0
        for (i in 0 until frameIndex.coerceIn(0, frames.size)) {
            tick += frames[i].durationTicks
        }
        return tick
    }

    // =========================================================================
    // Immutable Timeline Operations (with boundary validation)
    // =========================================================================

    /**
     * Selects a frame by index.
     */
    fun withSelectedFrame(index: Int): AnimationTimeline {
        val safeIndex = index.coerceIn(frames.indices)
        return copy(currentFrameIndex = safeIndex)
    }

    /**
     * Adds a new frame at the specified insertion index (defaults to after the current frame).
     */
    fun withFrameAdded(
        insertIndex: Int = currentFrameIndex + 1,
        name: String? = null,
        durationTicks: Int = 1,
        celLayerIds: List<String> = emptyList()
    ): AnimationTimeline {
        require(frames.size < AnimationLimits.MAX_FRAMES) {
            "Cannot add frame: maximum limit of ${AnimationLimits.MAX_FRAMES} reached"
        }
        val targetIndex = insertIndex.coerceIn(0, frames.size)
        val frameName = name ?: "Frame ${frames.size + 1}"
        val newFrame = AnimationFrame(
            name = frameName,
            durationTicks = durationTicks,
            celLayerIds = celLayerIds
        )
        val newFrames = frames.toMutableList().apply { add(targetIndex, newFrame) }
        return copy(frames = newFrames, currentFrameIndex = targetIndex)
    }

    /**
     * Duplicates the specified frame.
     *
     * @param index Frame index to duplicate
     * @param newCelLayerIds If provided, assigns these newly cloned cel layer IDs.
     *                       If null/empty, re-uses the original celLayerIds (Hold Cel exposure).
     */
    fun withFrameDuplicated(
        index: Int = currentFrameIndex,
        newCelLayerIds: List<String>? = null
    ): AnimationTimeline {
        require(frames.size < AnimationLimits.MAX_FRAMES) {
            "Cannot duplicate frame: maximum limit of ${AnimationLimits.MAX_FRAMES} reached"
        }
        val safeIndex = index.coerceIn(frames.indices)
        val source = frames[safeIndex]
        val duplicatedFrame = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (Copy)",
            celLayerIds = newCelLayerIds ?: source.celLayerIds
        )
        val insertIndex = safeIndex + 1
        val newFrames = frames.toMutableList().apply { add(insertIndex, duplicatedFrame) }
        return copy(frames = newFrames, currentFrameIndex = insertIndex)
    }

    /**
     * Deletes the specified frame. Enforces minimum 1 frame invariant.
     */
    fun withFrameDeleted(index: Int = currentFrameIndex): AnimationTimeline {
        require(frames.size > AnimationLimits.MIN_FRAMES) {
            "Cannot delete frame: timeline must contain at least ${AnimationLimits.MIN_FRAMES} frame"
        }
        val safeIndex = index.coerceIn(frames.indices)
        val newFrames = frames.toMutableList().apply { removeAt(safeIndex) }
        val newSelectedIndex = currentFrameIndex.coerceAtMost(newFrames.lastIndex)
        return copy(frames = newFrames, currentFrameIndex = newSelectedIndex)
    }

    /**
     * Reorders a frame from [fromIndex] to [toIndex].
     */
    fun withFrameReordered(fromIndex: Int, toIndex: Int): AnimationTimeline {
        val safeFrom = fromIndex.coerceIn(frames.indices)
        val safeTo = toIndex.coerceIn(frames.indices)
        if (safeFrom == safeTo) return this

        val newFrames = frames.toMutableList()
        val item = newFrames.removeAt(safeFrom)
        newFrames.add(safeTo, item)
        val newSelectedIndex = if (currentFrameIndex == safeFrom) safeTo else currentFrameIndex
        return copy(frames = newFrames, currentFrameIndex = newSelectedIndex)
    }

    /**
     * Updates the duration of a specific frame.
     */
    fun withFrameDurationSet(index: Int, durationTicks: Int): AnimationTimeline {
        val safeIndex = index.coerceIn(frames.indices)
        val validTicks = durationTicks.coerceIn(AnimationLimits.MIN_FRAME_DURATION, AnimationLimits.MAX_FRAME_DURATION)
        val updated = frames[safeIndex].copy(durationTicks = validTicks)
        val newFrames = frames.toMutableList().apply { set(safeIndex, updated) }
        return copy(frames = newFrames)
    }

    /**
     * Sets or unsets a layer as a static background/foreground layer.
     */
    fun withStaticLayerToggled(layerId: String, isStatic: Boolean): AnimationTimeline {
        val updated = if (isStatic) {
            if (staticLayerIds.contains(layerId)) staticLayerIds else staticLayerIds + layerId
        } else {
            staticLayerIds.filter { it != layerId }
        }
        return copy(staticLayerIds = updated)
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "fps" to fps,
        "currentFrameIndex" to currentFrameIndex,
        "staticLayerIds" to staticLayerIds,
        "loopMode" to loopMode.id,
        "onionSkin" to onionSkin.toMap(),
        "frames" to frames.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): AnimationTimeline {
            val fps = ((map["fps"] as? Number)?.toInt() ?: AnimationLimits.DEFAULT_FPS)
                .coerceIn(AnimationLimits.MIN_FPS, AnimationLimits.MAX_FPS)
            val currentFrameIndex = (map["currentFrameIndex"] as? Number)?.toInt() ?: 0
            val staticLayerIds = (map["staticLayerIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val loopMode = LoopMode.fromId(map["loopMode"]?.toString())
            val onionSkin = (map["onionSkin"] as? Map<String, Any?>)?.let {
                OnionSkinSettings.fromMap(it)
            } ?: OnionSkinSettings()

            val rawFrames = (map["frames"] as? List<*>) ?: emptyList<Any?>()
            val frames = if (rawFrames.isNotEmpty()) {
                rawFrames.mapNotNull { f ->
                    (f as? Map<String, Any?>)?.let { AnimationFrame.fromMap(it) }
                }
            } else {
                listOf(AnimationFrame(name = "Frame 1"))
            }

            val safeFrames = if (frames.isEmpty()) listOf(AnimationFrame(name = "Frame 1")) else frames
            val safeCurrentIndex = currentFrameIndex.coerceIn(safeFrames.indices)

            return AnimationTimeline(
                fps = fps,
                frames = safeFrames,
                currentFrameIndex = safeCurrentIndex,
                staticLayerIds = staticLayerIds,
                onionSkin = onionSkin,
                loopMode = loopMode
            )
        }
    }
}

/**
 * Storage and memory measurement metrics for animation projects.
 * Used to verify cel-referencing efficiency vs naive snapshot duplication.
 */
data class AnimationMemoryMetrics(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val frameCount: Int,
    val totalTicks: Int,
    val uniqueCelLayerCount: Int,
    val staticLayerCount: Int,
    val celReferencedPixelBytes: Long,
    val naiveSnapshotPixelBytes: Long,
    val memorySavedBytes: Long,
    val savingsRatioPercent: Float,
    val serializedJsonBytes: Long
)
