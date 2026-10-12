package com.artstudio.animation

import com.artstudio.document.model.AnimationTimelineConfig
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.history.*
import java.util.UUID

/**
 * High-level manager coordinating frame-by-frame animation timeline operations
 * with the transactional history engine ([HistoryManager]).
 */
class AnimationTimelineManager(
    private val historyManager: HistoryManager
) {
    val currentDocument: ArtDocument get() = historyManager.currentDocument

    val timeline: AnimationTimeline
        get() = currentDocument.animation?.toTimeline() ?: AnimationTimeline()

    /**
     * Initializes animation timeline on document if not already configured.
     */
    fun ensureAnimationEnabled(): AnimationTimeline {
        val existing = currentDocument.animation
        if (existing != null) {
            return existing.toTimeline()
        }

        // Initialize timeline with existing active layer as first frame cel
        val initialCel = currentDocument.activeLayerId
        val initialFrame = AnimationFrame(
            name = "Frame 1",
            durationTicks = 1,
            celLayerIds = if (initialCel.isNotEmpty()) listOf(initialCel) else emptyList()
        )
        val initialTimeline = AnimationTimeline(
            fps = AnimationLimits.DEFAULT_FPS,
            frames = listOf(initialFrame),
            currentFrameIndex = 0
        )
        val command = UpdateAnimationConfigCommand(
            oldTimeline = initialTimeline,
            newTimeline = initialTimeline,
            description = "Initialize Animation Timeline"
        )
        historyManager.execute(command)
        return initialTimeline
    }

    /**
     * Selects active frame in the timeline and syncs layer selection.
     */
    fun selectFrame(index: Int) {
        val tl = timeline
        val safeIndex = index.coerceIn(tl.frames.indices)
        if (safeIndex == tl.currentFrameIndex) return

        val command = SelectAnimationFrameCommand(
            oldIndex = tl.currentFrameIndex,
            newIndex = safeIndex
        )
        historyManager.execute(command)
    }

    /**
     * Adds a new frame to the timeline.
     *
     * @param insertIndex Insertion index (-1 defaults to currentFrameIndex + 1)
     * @param name Frame label
     * @param durationTicks Exposure duration in ticks
     * @param createNewRasterCel Whether to create a dedicated raster cel layer for this frame
     */
    fun addFrame(
        insertIndex: Int = -1,
        name: String? = null,
        durationTicks: Int = 1,
        createNewRasterCel: Boolean = true
    ): AnimationFrame {
        val tl = timeline
        require(tl.frames.size < AnimationLimits.MAX_FRAMES) {
            "Maximum frame limit (${AnimationLimits.MAX_FRAMES}) reached"
        }

        val targetIndex = if (insertIndex < 0) tl.currentFrameIndex + 1 else insertIndex.coerceIn(0, tl.frames.size)
        val frameName = name ?: "Frame ${tl.frames.size + 1}"

        val newLayers = mutableListOf<LayerNode>()
        val celIds = mutableListOf<String>()

        if (createNewRasterCel) {
            val celLayer = LayerNode.RasterLayer(
                id = UUID.randomUUID().toString(),
                name = "$frameName Drawing",
                width = currentDocument.canvas.width,
                height = currentDocument.canvas.height
            )
            newLayers.add(celLayer)
            celIds.add(celLayer.id)
        }

        val newFrame = AnimationFrame(
            name = frameName,
            durationTicks = durationTicks.coerceIn(AnimationLimits.MIN_FRAME_DURATION, AnimationLimits.MAX_FRAME_DURATION),
            celLayerIds = celIds
        )

        val command = AddAnimationFrameCommand(
            frame = newFrame,
            insertIndex = targetIndex,
            newCelLayers = newLayers
        )
        historyManager.execute(command)
        return newFrame
    }

    /**
     * Duplicates an existing frame.
     *
     * @param index Frame index to duplicate (-1 defaults to currentFrameIndex)
     * @param duplicateCels If true, deep copies cel raster layers into new unique drawings.
     *                      If false, shares the existing cel references (Hold / Exposure Hold).
     */
    fun duplicateFrame(
        index: Int = -1,
        duplicateCels: Boolean = false
    ): AnimationFrame {
        val tl = timeline
        require(tl.frames.size < AnimationLimits.MAX_FRAMES) {
            "Maximum frame limit (${AnimationLimits.MAX_FRAMES}) reached"
        }

        val safeIndex = if (index < 0) tl.currentFrameIndex else index.coerceIn(tl.frames.indices)
        val source = tl.frames[safeIndex]
        val targetInsertIndex = safeIndex + 1

        val clonedLayers = mutableListOf<LayerNode>()
        val targetCelIds = mutableListOf<String>()

        if (duplicateCels) {
            for (celId in source.celLayerIds) {
                val origLayer = currentDocument.findLayerById(celId)
                if (origLayer is LayerNode.RasterLayer) {
                    val clonedLayer = origLayer.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${origLayer.name} (Copy)"
                    )
                    clonedLayers.add(clonedLayer)
                    targetCelIds.add(clonedLayer.id)
                } else if (origLayer != null) {
                    targetCelIds.add(origLayer.id)
                }
            }
        } else {
            // Cel Hold: share layer references without cloning pixels
            targetCelIds.addAll(source.celLayerIds)
        }

        val duplicated = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (Copy)",
            celLayerIds = targetCelIds
        )

        val command = DuplicateAnimationFrameCommand(
            duplicatedFrame = duplicated,
            insertIndex = targetInsertIndex,
            clonedCelLayers = clonedLayers
        )
        historyManager.execute(command)
        return duplicated
    }

    /**
     * Deletes an animation frame.
     *
     * @param index Frame index to delete (-1 defaults to currentFrameIndex)
     * @param deleteOrphanedCels Whether to delete cel layers if no other frame references them
     */
    fun deleteFrame(
        index: Int = -1,
        deleteOrphanedCels: Boolean = false
    ) {
        val tl = timeline
        require(tl.frames.size > AnimationLimits.MIN_FRAMES) {
            "Cannot delete: timeline must retain at least ${AnimationLimits.MIN_FRAMES} frame"
        }

        val safeIndex = if (index < 0) tl.currentFrameIndex else index.coerceIn(tl.frames.indices)
        val targetFrame = tl.frames[safeIndex]

        val layersToRemove = mutableListOf<LayerNode>()
        if (deleteOrphanedCels) {
            // Check if any other frame references these cels
            val remainingFrames = tl.frames.filterIndexed { i, _ -> i != safeIndex }
            val otherReferencedIds = remainingFrames.flatMap { it.celLayerIds }.toSet() + tl.staticLayerIds.toSet()

            for (celId in targetFrame.celLayerIds) {
                if (!otherReferencedIds.contains(celId)) {
                    val layer = currentDocument.findLayerById(celId)
                    if (layer != null) {
                        layersToRemove.add(layer)
                    }
                }
            }
        }

        val command = DeleteAnimationFrameCommand(
            deletedFrame = targetFrame,
            deletedIndex = safeIndex,
            removedCelLayers = layersToRemove
        )
        historyManager.execute(command)
    }

    /**
     * Reorders a frame in the timeline.
     */
    fun reorderFrame(fromIndex: Int, toIndex: Int) {
        val tl = timeline
        val safeFrom = fromIndex.coerceIn(tl.frames.indices)
        val safeTo = toIndex.coerceIn(tl.frames.indices)
        if (safeFrom == safeTo) return

        val command = ReorderAnimationFrameCommand(
            fromIndex = safeFrom,
            toIndex = safeTo
        )
        historyManager.execute(command)
    }

    /**
     * Sets the duration in ticks for a specific frame.
     */
    fun setFrameDuration(index: Int, durationTicks: Int) {
        val tl = timeline
        val safeIndex = index.coerceIn(tl.frames.indices)
        val validTicks = durationTicks.coerceIn(AnimationLimits.MIN_FRAME_DURATION, AnimationLimits.MAX_FRAME_DURATION)
        val oldDuration = tl.frames[safeIndex].durationTicks
        if (oldDuration == validTicks) return

        val command = SetAnimationFrameDurationCommand(
            frameIndex = safeIndex,
            oldDuration = oldDuration,
            newDuration = validTicks
        )
        historyManager.execute(command)
    }

    /**
     * Updates timeline-wide configuration parameters (FPS, LoopMode, OnionSkin).
     */
    fun updateTimelineConfig(
        fps: Int? = null,
        loopMode: LoopMode? = null,
        onionSkin: OnionSkinSettings? = null
    ) {
        val old = timeline
        val updated = old.copy(
            fps = fps?.coerceIn(AnimationLimits.MIN_FPS, AnimationLimits.MAX_FPS) ?: old.fps,
            loopMode = loopMode ?: old.loopMode,
            onionSkin = onionSkin ?: old.onionSkin
        )
        if (old == updated) return

        val command = UpdateAnimationConfigCommand(
            oldTimeline = old,
            newTimeline = updated
        )
        historyManager.execute(command)
    }

    /**
     * Toggles whether a layer is treated as static across all frames.
     */
    fun toggleStaticLayer(layerId: String, isStatic: Boolean) {
        val old = timeline
        val updated = old.withStaticLayerToggled(layerId, isStatic)
        if (old == updated) return

        val command = UpdateAnimationConfigCommand(
            oldTimeline = old,
            newTimeline = updated,
            description = if (isStatic) "Set layer '$layerId' as static background" else "Unset static layer '$layerId'"
        )
        historyManager.execute(command)
    }
}
