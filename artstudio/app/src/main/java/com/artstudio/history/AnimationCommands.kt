package com.artstudio.history

import com.artstudio.animation.AnimationFrame
import com.artstudio.animation.AnimationTimeline
import com.artstudio.document.model.AnimationTimelineConfig
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import java.util.UUID

/**
 * Reversible command for changing the active frame selection in the timeline.
 */
data class SelectAnimationFrameCommand(
    override val id: String = UUID.randomUUID().toString(),
    val oldIndex: Int,
    val newIndex: Int,
    override val description: String = "Select Frame ${newIndex + 1}",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 128L

    override fun apply(document: ArtDocument): ArtDocument {
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val updatedTimeline = currentTimeline.withSelectedFrame(newIndex)
        val activeCels = updatedTimeline.currentFrame.celLayerIds
        val newSelection = activeCels.firstOrNull { document.findLayerById(it) != null } ?: document.selectedLayerId

        return document.copy(
            animation = AnimationTimelineConfig.fromTimeline(updatedTimeline),
            selectedLayerId = newSelection,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val revertedTimeline = currentTimeline.withSelectedFrame(oldIndex)
        val activeCels = revertedTimeline.currentFrame.celLayerIds
        val newSelection = activeCels.firstOrNull { document.findLayerById(it) != null } ?: document.selectedLayerId

        return document.copy(
            animation = AnimationTimelineConfig.fromTimeline(revertedTimeline),
            selectedLayerId = newSelection,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command for adding a new animation frame to the timeline.
 */
data class AddAnimationFrameCommand(
    override val id: String = UUID.randomUUID().toString(),
    val frame: AnimationFrame,
    val insertIndex: Int,
    val newCelLayers: List<LayerNode> = emptyList(),
    override val description: String = "Add Frame '${frame.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        256L + newCelLayers.sumOf { if (it is LayerNode.RasterLayer) (it.width * it.height * 4L) else 512L }

    override fun apply(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.toMutableList().apply { addAll(newCelLayers) }
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val updatedTimeline = currentTimeline.withFrameAdded(
            insertIndex = insertIndex,
            name = frame.name,
            durationTicks = frame.durationTicks,
            celLayerIds = frame.celLayerIds
        )
        val newSelection = frame.celLayerIds.firstOrNull() ?: document.selectedLayerId

        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            animation = AnimationTimelineConfig.fromTimeline(updatedTimeline),
            selectedLayerId = newSelection,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val celIdsToRemove = newCelLayers.map { it.id }.toSet()
        val updatedChildren = root.children.filterNot { celIdsToRemove.contains(it.id) }
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()

        val updatedTimeline = if (currentTimeline.frames.size > 1 && insertIndex in currentTimeline.frames.indices) {
            currentTimeline.withFrameDeleted(insertIndex)
        } else {
            currentTimeline
        }

        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            animation = AnimationTimelineConfig.fromTimeline(updatedTimeline),
            selectedLayerId = updatedChildren.lastOrNull()?.id,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command for duplicating an existing animation frame.
 */
data class DuplicateAnimationFrameCommand(
    override val id: String = UUID.randomUUID().toString(),
    val duplicatedFrame: AnimationFrame,
    val insertIndex: Int,
    val clonedCelLayers: List<LayerNode> = emptyList(),
    override val description: String = "Duplicate Frame '${duplicatedFrame.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        256L + clonedCelLayers.sumOf { if (it is LayerNode.RasterLayer) (it.width * it.height * 4L) else 512L }

    override fun apply(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.toMutableList().apply { addAll(clonedCelLayers) }
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val newFrames = currentTimeline.frames.toMutableList().apply {
            add(insertIndex.coerceIn(0, size), duplicatedFrame)
        }
        val updatedTimeline = currentTimeline.copy(
            frames = newFrames,
            currentFrameIndex = insertIndex.coerceIn(0, newFrames.lastIndex)
        )
        val newSelection = duplicatedFrame.celLayerIds.firstOrNull() ?: document.selectedLayerId

        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            animation = AnimationTimelineConfig.fromTimeline(updatedTimeline),
            selectedLayerId = newSelection,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val celIdsToRemove = clonedCelLayers.map { it.id }.toSet()
        val updatedChildren = root.children.filterNot { celIdsToRemove.contains(it.id) }
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()

        val updatedTimeline = if (currentTimeline.frames.size > 1 && insertIndex in currentTimeline.frames.indices) {
            currentTimeline.withFrameDeleted(insertIndex)
        } else {
            currentTimeline
        }

        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            animation = AnimationTimelineConfig.fromTimeline(updatedTimeline),
            selectedLayerId = updatedChildren.lastOrNull()?.id,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command for deleting an animation frame.
 */
data class DeleteAnimationFrameCommand(
    override val id: String = UUID.randomUUID().toString(),
    val deletedFrame: AnimationFrame,
    val deletedIndex: Int,
    val removedCelLayers: List<LayerNode> = emptyList(),
    override val description: String = "Delete Frame '${deletedFrame.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        256L + removedCelLayers.sumOf { if (it is LayerNode.RasterLayer) (it.width * it.height * 4L) else 512L }

    override fun apply(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val idsToRemove = removedCelLayers.map { it.id }.toSet()
        val updatedChildren = root.children.filterNot { idsToRemove.contains(it.id) }
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val updatedTimeline = currentTimeline.withFrameDeleted(deletedIndex)
        val newSelection = updatedTimeline.currentFrame.celLayerIds.firstOrNull() ?: updatedChildren.lastOrNull()?.id

        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            animation = AnimationTimelineConfig.fromTimeline(updatedTimeline),
            selectedLayerId = newSelection,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.toMutableList().apply { addAll(removedCelLayers) }
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val restoredFrames = currentTimeline.frames.toMutableList().apply {
            add(deletedIndex.coerceIn(0, size), deletedFrame)
        }
        val restoredTimeline = currentTimeline.copy(
            frames = restoredFrames,
            currentFrameIndex = deletedIndex.coerceIn(0, restoredFrames.lastIndex)
        )
        val newSelection = deletedFrame.celLayerIds.firstOrNull() ?: document.selectedLayerId

        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            animation = AnimationTimelineConfig.fromTimeline(restoredTimeline),
            selectedLayerId = newSelection,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command for reordering a frame within the timeline.
 */
data class ReorderAnimationFrameCommand(
    override val id: String = UUID.randomUUID().toString(),
    val fromIndex: Int,
    val toIndex: Int,
    override val description: String = "Move Frame ${fromIndex + 1} to ${toIndex + 1}",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 128L

    override fun apply(document: ArtDocument): ArtDocument {
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val updatedTimeline = currentTimeline.withFrameReordered(fromIndex, toIndex)

        return document.copy(
            animation = AnimationTimelineConfig.fromTimeline(updatedTimeline),
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val revertedTimeline = currentTimeline.withFrameReordered(toIndex, fromIndex)

        return document.copy(
            animation = AnimationTimelineConfig.fromTimeline(revertedTimeline),
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command for setting the exposure duration of a frame.
 */
data class SetAnimationFrameDurationCommand(
    override val id: String = UUID.randomUUID().toString(),
    val frameIndex: Int,
    val oldDuration: Int,
    val newDuration: Int,
    override val description: String = "Set Frame ${frameIndex + 1} Duration to $newDuration ticks",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 128L

    override fun apply(document: ArtDocument): ArtDocument {
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val updatedTimeline = currentTimeline.withFrameDurationSet(frameIndex, newDuration)

        return document.copy(
            animation = AnimationTimelineConfig.fromTimeline(updatedTimeline),
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val currentTimeline = document.animation?.toTimeline() ?: AnimationTimeline()
        val revertedTimeline = currentTimeline.withFrameDurationSet(frameIndex, oldDuration)

        return document.copy(
            animation = AnimationTimelineConfig.fromTimeline(revertedTimeline),
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command for updating timeline configuration (FPS, LoopMode, OnionSkin).
 */
data class UpdateAnimationConfigCommand(
    override val id: String = UUID.randomUUID().toString(),
    val oldTimeline: AnimationTimeline,
    val newTimeline: AnimationTimeline,
    override val description: String = "Update Animation Settings",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.copy(
            animation = AnimationTimelineConfig.fromTimeline(newTimeline),
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.copy(
            animation = AnimationTimelineConfig.fromTimeline(oldTimeline),
            updatedAt = System.currentTimeMillis()
        )
    }
}
