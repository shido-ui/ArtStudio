package com.artstudio.document.model

import java.util.UUID

/**
 * Color swatch inside a palette.
 */
data class ColorSwatchData(
    val hex: String,
    val name: String? = null
)

/**
 * Palette definition stored with the document.
 */
data class PaletteDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val swatches: List<ColorSwatchData> = emptyList()
)

/**
 * Reference image placed over or beside the canvas for artist tracing/reference.
 */
data class ReferenceImageConfig(
    val id: String = UUID.randomUUID().toString(),
    val assetPath: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float,
    val height: Float,
    val opacity: Float = 0.8f,
    val visible: Boolean = true
) {
    init {
        require(width > 0f) { "Reference width must be positive, got $width" }
        require(height > 0f) { "Reference height must be positive, got $height" }
        require(opacity in 0f..1f) { "Reference opacity must be in 0.0..1.0, got $opacity" }
    }
}

/**
 * Frame track mapping a layer to frame visibility or opacity keyframes.
 */
data class FrameTrackData(
    val layerId: String,
    val activeFrames: List<Int> = emptyList()
)

/**
 * Animation timeline metadata for flipbook / frame animation.
 */
data class AnimationTimelineConfig(
    val fps: Int = 12,
    val totalFrames: Int = 24,
    val frameTracks: List<FrameTrackData> = emptyList(),
    val onionSkinEnabled: Boolean = false,
    val onionSkinFramesBefore: Int = 1,
    val onionSkinFramesAfter: Int = 1,
    val onionSkinTintBefore: Long = 0xFFFF4500L,
    val onionSkinTintAfter: Long = 0xFF00BFFFL,
    val onionSkinOpacityBefore: Float = 0.35f,
    val onionSkinOpacityAfter: Float = 0.25f,
    val onionSkinShowBefore: Boolean = true,
    val onionSkinShowAfter: Boolean = true,
    val onionSkinTintMode: String = "tinted",
    val onionSkinFalloff: String = "linear",
    val onionSkinSuppressDuringPlayback: Boolean = true,
    val frames: List<com.artstudio.animation.AnimationFrame> = emptyList(),
    val currentFrameIndex: Int = 0,
    val staticLayerIds: List<String> = emptyList(),
    val loopMode: String = "LOOP"
) {
    init {
        require(fps in 1..120) { "FPS must be between 1 and 120, got $fps" }
        require(totalFrames >= 1) { "Total frames must be >= 1, got $totalFrames" }
    }

    /**
     * Converts to authoritative [com.artstudio.animation.AnimationTimeline].
     * Synthesizes frame list from frameTracks or totalFrames if [frames] is empty.
     */
    fun toTimeline(): com.artstudio.animation.AnimationTimeline {
        val safeFrames = if (frames.isNotEmpty()) {
            frames
        } else {
            // Synthesize from frameTracks or totalFrames for backwards-compatibility
            val count = totalFrames.coerceIn(1, com.artstudio.animation.AnimationLimits.MAX_FRAMES)
            (0 until count).map { idx ->
                val activeCels = frameTracks.filter { it.activeFrames.contains(idx) }.map { it.layerId }
                com.artstudio.animation.AnimationFrame(
                    id = "frame_$idx",
                    name = "Frame ${idx + 1}",
                    durationTicks = 1,
                    celLayerIds = activeCels
                )
            }
        }

        return com.artstudio.animation.AnimationTimeline(
            fps = fps.coerceIn(com.artstudio.animation.AnimationLimits.MIN_FPS, com.artstudio.animation.AnimationLimits.MAX_FPS),
            frames = if (safeFrames.isEmpty()) listOf(com.artstudio.animation.AnimationFrame(name = "Frame 1")) else safeFrames,
            currentFrameIndex = currentFrameIndex.coerceIn(0, (safeFrames.size - 1).coerceAtLeast(0)),
            staticLayerIds = staticLayerIds,
            onionSkin = com.artstudio.animation.OnionSkinSettings(
                enabled = onionSkinEnabled,
                framesBefore = onionSkinFramesBefore.coerceIn(1, com.artstudio.animation.AnimationLimits.MAX_ONION_SKIN_FRAMES),
                framesAfter = onionSkinFramesAfter.coerceIn(1, com.artstudio.animation.AnimationLimits.MAX_ONION_SKIN_FRAMES),
                tintBefore = onionSkinTintBefore,
                tintAfter = onionSkinTintAfter,
                opacityBefore = onionSkinOpacityBefore,
                opacityAfter = onionSkinOpacityAfter,
                showBefore = onionSkinShowBefore,
                showAfter = onionSkinShowAfter,
                tintMode = com.artstudio.animation.OnionSkinTintMode.fromId(onionSkinTintMode),
                falloff = com.artstudio.animation.OnionSkinFalloff.fromId(onionSkinFalloff),
                suppressDuringPlayback = onionSkinSuppressDuringPlayback
            ),
            loopMode = com.artstudio.animation.LoopMode.fromId(loopMode)
        )
    }

    companion object {
        fun fromTimeline(timeline: com.artstudio.animation.AnimationTimeline): AnimationTimelineConfig {
            // Build frameTracks from celLayerIds for backward compatibility
            val allLayerIds = timeline.frames.flatMap { it.celLayerIds }.distinct()
            val tracks = allLayerIds.map { layerId ->
                val activeIndices = timeline.frames.mapIndexedNotNull { index, frame ->
                    if (frame.celLayerIds.contains(layerId)) index else null
                }
                FrameTrackData(layerId = layerId, activeFrames = activeIndices)
            }

            return AnimationTimelineConfig(
                fps = timeline.fps,
                totalFrames = timeline.frames.size,
                frameTracks = tracks,
                onionSkinEnabled = timeline.onionSkin.enabled,
                onionSkinFramesBefore = timeline.onionSkin.framesBefore,
                onionSkinFramesAfter = timeline.onionSkin.framesAfter,
                onionSkinTintBefore = timeline.onionSkin.tintBefore,
                onionSkinTintAfter = timeline.onionSkin.tintAfter,
                onionSkinOpacityBefore = timeline.onionSkin.opacityBefore,
                onionSkinOpacityAfter = timeline.onionSkin.opacityAfter,
                onionSkinShowBefore = timeline.onionSkin.showBefore,
                onionSkinShowAfter = timeline.onionSkin.showAfter,
                onionSkinTintMode = timeline.onionSkin.tintMode.id,
                onionSkinFalloff = timeline.onionSkin.falloff.id,
                onionSkinSuppressDuringPlayback = timeline.onionSkin.suppressDuringPlayback,
                frames = timeline.frames,
                currentFrameIndex = timeline.currentFrameIndex,
                staticLayerIds = timeline.staticLayerIds,
                loopMode = timeline.loopMode.id
            )
        }
    }
}

/**
 * Provenance tracking for layers generated via AutoDraw CV reconstruction.
 */
data class AutoDrawProvenance(
    val sourceImageHash: String? = null, // SHA-256
    val algorithm: String = "offline_morphological_v1",
    val edgeThresholdLow: Float = 50f,
    val edgeThresholdHigh: Float = 150f,
    val generatedLayerIds: List<String> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis(),
    val aiAugmentation: com.artstudio.ai.model.AiAugmentation? = null,
    val stylePresetId: String? = null,
    val autoDrawGroupId: String? = null,
    val regions: List<com.artstudio.autodraw.integration.RegionProvenanceInfo> = emptyList(),
    val userOverrides: List<com.artstudio.autodraw.integration.UserOverrideRecord> = emptyList(),
    val layerRoles: Map<String, String> = emptyMap(),
    val customParameters: Map<String, String> = emptyMap()
)

/**
 * Analysis provenance container recording CV and optional AI model augmentations.
 */
data class AnalysisProvenance(
    val referenceHash: String? = null,
    val classicalCvAlgorithm: String = "classical_cv_v1",
    val aiAugmentation: com.artstudio.ai.model.AiAugmentation? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
