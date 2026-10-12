package com.artstudio.history

import com.artstudio.core.BrushStroke
import com.artstudio.document.model.*
import java.util.UUID

/**
 * Common contract for all reversible editing commands in Art Studio.
 * Follows the Command Pattern with strict memory bounds and deterministic execution.
 */
sealed interface EditCommand {
    val id: String
    val description: String
    val timestamp: Long
    val estimatedMemoryBytes: Long

    /**
     * Executes or reapplies this command to the document, returning the modified [ArtDocument].
     */
    fun apply(document: ArtDocument): ArtDocument

    /**
     * Reverts the effect of this command, restoring the previous [ArtDocument] state.
     */
    fun revert(document: ArtDocument): ArtDocument
}

/**
 * Reversible stroke commit / erase command.
 * Stores deterministic stroke vectors and dynamics rather than full-frame bitmaps,
 * keeping per-action memory strictly bounded to a few kilobytes.
 */
data class StrokeCommand(
    override val id: String = UUID.randomUUID().toString(),
    val stroke: BrushStroke,
    val targetLayerId: String = "default_layer",
    override val description: String = if (stroke.config.type == com.artstudio.core.BrushType.ERASER) "Erase Stroke" else "Paint Stroke",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long
        get() = 256L + (stroke.points.size * 32L)

    override fun apply(document: ArtDocument): ArtDocument {
        // Find target layer; if vector layer, append to its paths
        val layer = document.findLayerById(targetLayerId)
        return if (layer is LayerNode.VectorPathLayer) {
            val vectorPath = VectorPathData(
                id = stroke.id,
                points = stroke.points.map { StrokePointData(it.x, it.y, it.pressure, it.tilt, 0f, it.timestampNano / 1_000_000L) },
                strokeWidth = stroke.config.size,
                strokeColor = stroke.config.color.toLong(),
                brushId = stroke.config.type.id
            )
            document.withUpdatedLayer(targetLayerId) {
                (it as LayerNode.VectorPathLayer).copy(paths = it.paths + vectorPath)
            }
        } else {
            // For raster/document-level strokes, update document metadata timestamp
            document.copy(updatedAt = System.currentTimeMillis())
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val layer = document.findLayerById(targetLayerId)
        return if (layer is LayerNode.VectorPathLayer) {
            document.withUpdatedLayer(targetLayerId) {
                val vLayer = it as LayerNode.VectorPathLayer
                vLayer.copy(paths = vLayer.paths.filterNot { p -> p.id == stroke.id })
            }
        } else {
            document.copy(updatedAt = System.currentTimeMillis())
        }
    }
}

/**
 * Transactional command grouping multiple synchronized strokes (e.g. multi-branch symmetry)
 * into a single unified undo/redo history step.
 */
data class CompoundStrokeCommand(
    override val id: String = UUID.randomUUID().toString(),
    val strokes: List<BrushStroke>,
    val targetLayerId: String = "default_layer",
    override val description: String = "Symmetric Stroke Group",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long
        get() = 256L + strokes.sumOf { 256L + (it.points.size * 32L) }

    override fun apply(document: ArtDocument): ArtDocument {
        val layer = document.findLayerById(targetLayerId)
        return if (layer is LayerNode.VectorPathLayer) {
            val vectorPaths = strokes.map { stroke ->
                VectorPathData(
                    id = stroke.id,
                    points = stroke.points.map { StrokePointData(it.x, it.y, it.pressure, it.tilt, 0f, it.timestampNano / 1_000_000L) },
                    strokeWidth = stroke.config.size,
                    strokeColor = stroke.config.color.toLong(),
                    brushId = stroke.config.type.id
                )
            }
            document.withUpdatedLayer(targetLayerId) {
                (it as LayerNode.VectorPathLayer).copy(paths = it.paths + vectorPaths)
            }
        } else {
            document.copy(updatedAt = System.currentTimeMillis())
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val layer = document.findLayerById(targetLayerId)
        return if (layer is LayerNode.VectorPathLayer) {
            val strokeIds = strokes.map { it.id }.toSet()
            document.withUpdatedLayer(targetLayerId) {
                val vLayer = it as LayerNode.VectorPathLayer
                vLayer.copy(paths = vLayer.paths.filterNot { p -> p.id in strokeIds })
            }
        } else {
            document.copy(updatedAt = System.currentTimeMillis())
        }
    }
}

/**
 * Reversible layer addition command.
 */
data class AddLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layer: LayerNode,
    val parentGroupId: String? = null,
    val index: Int = -1,
    val previousSelectedLayerId: String? = null,
    override val description: String = "Add Layer '${layer.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        val updated = document.withAddedLayer(layer, parentGroupId, index)
        return updated.copy(selectedLayerId = layer.id)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val updated = document.withRemovedLayer(layer.id)
        val fallbackSelection = previousSelectedLayerId ?: updated.rootGroup.children.lastOrNull()?.id
        return updated.copy(selectedLayerId = fallbackSelection)
    }
}

/**
 * Reversible layer removal command.
 */
data class RemoveLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val removedLayer: LayerNode,
    val parentGroupId: String? = null,
    val originalIndex: Int = 0,
    val previousSelectedLayerId: String? = null,
    override val description: String = "Remove Layer '${removedLayer.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        val updated = document.withRemovedLayer(removedLayer.id)
        val newSelection = if (document.selectedLayerId == removedLayer.id) {
            val children = updated.rootGroup.children
            if (children.isNotEmpty()) {
                val safeIdx = (originalIndex - 1).coerceIn(0, children.size - 1)
                children[safeIdx].id
            } else null
        } else {
            document.selectedLayerId
        }
        return updated.copy(selectedLayerId = newSelection)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val updated = document.withAddedLayer(removedLayer, parentGroupId, originalIndex)
        return updated.copy(selectedLayerId = previousSelectedLayerId ?: removedLayer.id)
    }
}

/**
 * Reversible layer duplication command.
 */
data class DuplicateLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val sourceLayerId: String,
    val duplicatedLayer: LayerNode,
    val insertIndex: Int,
    val parentGroupId: String? = null,
    override val description: String = "Duplicate Layer '${duplicatedLayer.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        val updated = document.withAddedLayer(duplicatedLayer, parentGroupId, insertIndex)
        return updated.copy(selectedLayerId = duplicatedLayer.id)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val updated = document.withRemovedLayer(duplicatedLayer.id)
        return updated.copy(selectedLayerId = sourceLayerId)
    }
}

/**
 * Reversible layer selection command.
 */
data class SelectLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val oldLayerId: String?,
    val newLayerId: String?,
    override val description: String = "Select Layer",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 128L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.copy(selectedLayerId = newLayerId, updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.copy(selectedLayerId = oldLayerId, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Reversible layer reordering command within a group or canvas root.
 */
data class ReorderLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val fromIndex: Int,
    val toIndex: Int,
    val parentGroupId: String? = null,
    override val description: String = "Reorder Layer",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withReorderedLayer(layerId, fromIndex, toIndex, parentGroupId)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withReorderedLayer(layerId, toIndex, fromIndex, parentGroupId)
    }
}

/**
 * Reversible layer property mutation command (opacity, visibility, blend mode, name, lock).
 */
data class SetLayerPropertyCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val propertyName: String,
    val oldValue: Any,
    val newValue: Any,
    override val description: String = "Change $propertyName on layer",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return applyProperty(document, newValue)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return applyProperty(document, oldValue)
    }

    private fun applyProperty(document: ArtDocument, value: Any): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (propertyName.lowercase()) {
                "opacity" -> updateOpacity(node, (value as Number).toFloat())
                "visible", "visibility" -> updateVisibility(node, value as Boolean)
                "blendmode" -> updateBlendMode(node, value as BlendModeType)
                "name" -> updateName(node, value as String)
                "islocked", "locked" -> updateLocked(node, value as Boolean)
                "isclippingmask", "clippingmask" -> updateClippingMask(node, value as Boolean)
                "isalphalocked", "alphalock" -> updateAlphaLock(node, value as Boolean)
                else -> node
            }
        }
    }

    private fun updateAlphaLock(node: LayerNode, locked: Boolean): LayerNode = when (node) {
        is LayerNode.RasterLayer -> node.copy(isAlphaLocked = locked)
        is LayerNode.VectorPathLayer -> node.copy(isAlphaLocked = locked)
        is LayerNode.TextLayer -> node.copy(isAlphaLocked = locked)
        is LayerNode.LayerGroup -> node.copy(isAlphaLocked = locked)
        is LayerNode.AdjustmentLayer -> node.copy(isAlphaLocked = locked)
    }

    private fun updateOpacity(node: LayerNode, opacity: Float): LayerNode = when (node) {
        is LayerNode.RasterLayer -> node.copy(opacity = opacity.coerceIn(0f, 1f))
        is LayerNode.VectorPathLayer -> node.copy(opacity = opacity.coerceIn(0f, 1f))
        is LayerNode.TextLayer -> node.copy(opacity = opacity.coerceIn(0f, 1f))
        is LayerNode.LayerGroup -> node.copy(opacity = opacity.coerceIn(0f, 1f))
        is LayerNode.AdjustmentLayer -> node.copy(opacity = opacity.coerceIn(0f, 1f))
    }

    private fun updateVisibility(node: LayerNode, visible: Boolean): LayerNode = when (node) {
        is LayerNode.RasterLayer -> node.copy(visible = visible)
        is LayerNode.VectorPathLayer -> node.copy(visible = visible)
        is LayerNode.TextLayer -> node.copy(visible = visible)
        is LayerNode.LayerGroup -> node.copy(visible = visible)
        is LayerNode.AdjustmentLayer -> node.copy(visible = visible)
    }

    private fun updateBlendMode(node: LayerNode, mode: BlendModeType): LayerNode = when (node) {
        is LayerNode.RasterLayer -> node.copy(blendMode = mode)
        is LayerNode.VectorPathLayer -> node.copy(blendMode = mode)
        is LayerNode.TextLayer -> node.copy(blendMode = mode)
        is LayerNode.LayerGroup -> node.copy(blendMode = mode)
        is LayerNode.AdjustmentLayer -> node.copy(blendMode = mode)
    }

    private fun updateName(node: LayerNode, name: String): LayerNode = when (node) {
        is LayerNode.RasterLayer -> node.copy(name = name)
        is LayerNode.VectorPathLayer -> node.copy(name = name)
        is LayerNode.TextLayer -> node.copy(name = name)
        is LayerNode.LayerGroup -> node.copy(name = name)
        is LayerNode.AdjustmentLayer -> node.copy(name = name)
    }

    private fun updateLocked(node: LayerNode, locked: Boolean): LayerNode = when (node) {
        is LayerNode.RasterLayer -> node.copy(isLocked = locked)
        is LayerNode.VectorPathLayer -> node.copy(isLocked = locked)
        is LayerNode.TextLayer -> node.copy(isLocked = locked)
        is LayerNode.LayerGroup -> node.copy(isLocked = locked)
        is LayerNode.AdjustmentLayer -> node.copy(isLocked = locked)
    }

    private fun updateClippingMask(node: LayerNode, clip: Boolean): LayerNode = when (node) {
        is LayerNode.RasterLayer -> node.copy(isClippingMask = clip)
        is LayerNode.VectorPathLayer -> node.copy(isClippingMask = clip)
        is LayerNode.TextLayer -> node.copy(isClippingMask = clip)
        is LayerNode.LayerGroup -> node.copy(isClippingMask = clip)
        is LayerNode.AdjustmentLayer -> node.copy(isClippingMask = clip)
    }
}

/**
 * Reversible spatial / affine layer transform command.
 */
data class TransformLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val oldTransform: LayerTransform,
    val newTransform: LayerTransform,
    override val description: String = "Transform Layer",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(transform = newTransform)
                is LayerNode.VectorPathLayer -> node.copy(transform = newTransform)
                is LayerNode.TextLayer -> node.copy(transform = newTransform)
                is LayerNode.LayerGroup -> node.copy(transform = newTransform)
                is LayerNode.AdjustmentLayer -> node.copy(transform = newTransform)
            }
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(transform = oldTransform)
                is LayerNode.VectorPathLayer -> node.copy(transform = oldTransform)
                is LayerNode.TextLayer -> node.copy(transform = oldTransform)
                is LayerNode.LayerGroup -> node.copy(transform = oldTransform)
                is LayerNode.AdjustmentLayer -> node.copy(transform = oldTransform)
            }
        }
    }
}

/**
 * Reversible vector path insertion command for stylus paths and future AutoDraw paths.
 */
data class VectorPathCommand(
    override val id: String = UUID.randomUUID().toString(),
    val targetLayerId: String,
    val pathData: VectorPathData,
    override val description: String = "Add Vector Path",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long
        get() = 128L + (pathData.points.size * 24L)

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(targetLayerId) { node ->
            if (node is LayerNode.VectorPathLayer) {
                node.copy(paths = node.paths + pathData)
            } else {
                node
            }
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(targetLayerId) { node ->
            if (node is LayerNode.VectorPathLayer) {
                node.copy(paths = node.paths.filterNot { it.id == pathData.id })
            } else {
                node
            }
        }
    }
}

/**
 * Reversible compound / transaction command grouping multiple atomic operations into one unit.
 */
data class CompoundEditCommand(
    override val id: String = UUID.randomUUID().toString(),
    val commands: List<EditCommand>,
    override val description: String,
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long
        get() = 256L + commands.sumOf { it.estimatedMemoryBytes }

    override fun apply(document: ArtDocument): ArtDocument {
        var current = document
        for (cmd in commands) {
            current = cmd.apply(current)
        }
        return current
    }

    override fun revert(document: ArtDocument): ArtDocument {
        var current = document
        // Revert in reverse order
        for (cmd in commands.asReversed()) {
            current = cmd.revert(current)
        }
        return current
    }
}

/**
 * Reversible group creation and layer wrapping command.
 */
data class CreateGroupCommand(
    override val id: String = UUID.randomUUID().toString(),
    val group: LayerNode.LayerGroup,
    val parentGroupId: String? = null,
    val insertIndex: Int = -1,
    val initialChildLayersWithIndices: List<Pair<LayerNode, Int>> = emptyList(),
    override val description: String = "Create Group '${group.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        var current = document
        for ((child, _) in initialChildLayersWithIndices) {
            current = current.withRemovedLayer(child.id)
        }
        val groupWithChildren = group.copy(children = initialChildLayersWithIndices.map { it.first })
        current = current.withAddedLayer(groupWithChildren, parentGroupId, insertIndex)
        return current.copy(selectedLayerId = group.id)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        var current = document.withRemovedLayer(group.id)
        for ((child, origIdx) in initialChildLayersWithIndices) {
            current = current.withAddedLayer(child, parentGroupId, origIdx)
        }
        val restoredSelection = initialChildLayersWithIndices.firstOrNull()?.first?.id
            ?: current.rootGroup.children.lastOrNull()?.id
        return current.copy(selectedLayerId = restoredSelection)
    }
}

/**
 * Reversible move layer across groups in hierarchy.
 */
data class MoveLayerHierarchyCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val sourceParentGroupId: String,
    val sourceIndex: Int,
    val targetParentGroupId: String,
    val targetIndex: Int,
    override val description: String = "Move Layer into Group",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withMovedLayer(layerId, targetParentGroupId, targetIndex)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withMovedLayer(layerId, sourceParentGroupId, sourceIndex)
    }
}

/**
 * Reversible ungroup command which dissolves a group node and restores its children in parent.
 */
data class UngroupCommand(
    override val id: String = UUID.randomUUID().toString(),
    val removedGroup: LayerNode.LayerGroup,
    val parentGroupId: String? = null,
    val groupIndex: Int,
    override val description: String = "Ungroup '${removedGroup.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        var current = document.withRemovedLayer(removedGroup.id)
        for ((idx, child) in removedGroup.children.withIndex()) {
            current = current.withAddedLayer(child, parentGroupId, groupIndex + idx)
        }
        val firstChildId = removedGroup.children.firstOrNull()?.id ?: parentGroupId
        return current.copy(selectedLayerId = firstChildId)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        var current = document
        for (child in removedGroup.children) {
            current = current.withRemovedLayer(child.id)
        }
        current = current.withAddedLayer(removedGroup, parentGroupId, groupIndex)
        return current.copy(selectedLayerId = removedGroup.id)
    }
}

/**
 * Reversible merge selected layers into a single raster layer.
 */
data class MergeLayersCommand(
    override val id: String = UUID.randomUUID().toString(),
    val sourceLayersWithIndices: List<Pair<LayerNode, Int>>,
    val parentGroupId: String? = null,
    val mergedLayer: LayerNode.RasterLayer,
    val insertIndex: Int,
    val strokeIdToOriginalLayerId: Map<String, String> = emptyMap(),
    override val description: String = "Merge Selected Layers",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        var current = document
        for ((layer, _) in sourceLayersWithIndices) {
            current = current.withRemovedLayer(layer.id)
        }
        current = current.withAddedLayer(mergedLayer, parentGroupId, insertIndex)
        return current.copy(selectedLayerId = mergedLayer.id)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        var current = document.withRemovedLayer(mergedLayer.id)
        for ((layer, origIdx) in sourceLayersWithIndices) {
            current = current.withAddedLayer(layer, parentGroupId, origIdx)
        }
        return current.copy(selectedLayerId = sourceLayersWithIndices.firstOrNull()?.first?.id)
    }
}

/**
 * Reversible merge of visible layers into a single raster layer.
 */
data class MergeVisibleCommand(
    override val id: String = UUID.randomUUID().toString(),
    val originalRootGroup: LayerNode.LayerGroup,
    val originalSelectedLayerId: String? = null,
    val hiddenLayers: List<LayerNode>,
    val mergedLayer: LayerNode.RasterLayer,
    val strokeIdToOriginalLayerId: Map<String, String> = emptyMap(),
    override val description: String = "Merge Visible Layers",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        val newChildren = listOf(mergedLayer) + hiddenLayers
        val newRoot = document.rootGroup.copy(children = newChildren)
        return document.copy(
            rootGroup = newRoot,
            selectedLayerId = mergedLayer.id,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.copy(
            rootGroup = originalRootGroup,
            selectedLayerId = originalSelectedLayerId ?: originalRootGroup.children.lastOrNull()?.id,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible explicit named flatten document command.
 */
data class FlattenDocumentCommand(
    override val id: String = UUID.randomUUID().toString(),
    val originalRootGroup: LayerNode.LayerGroup,
    val originalSelectedLayerId: String?,
    val flattenedLayer: LayerNode.RasterLayer,
    val strokeIdToOriginalLayerId: Map<String, String> = emptyMap(),
    override val description: String = "Flatten Document",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        val newRoot = document.rootGroup.copy(children = listOf(flattenedLayer))
        return document.copy(
            rootGroup = newRoot,
            selectedLayerId = flattenedLayer.id,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.copy(
            rootGroup = originalRootGroup,
            selectedLayerId = originalSelectedLayerId,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible layer mask addition command.
 */
data class AddMaskCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val mask: LayerMask,
    override val description: String = "Add Layer Mask",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(mask = mask)
                is LayerNode.VectorPathLayer -> node.copy(mask = mask)
                is LayerNode.TextLayer -> node.copy(mask = mask)
                is LayerNode.LayerGroup -> node.copy(mask = mask)
                is LayerNode.AdjustmentLayer -> node.copy(mask = mask)
            }
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(mask = null)
                is LayerNode.VectorPathLayer -> node.copy(mask = null)
                is LayerNode.TextLayer -> node.copy(mask = null)
                is LayerNode.LayerGroup -> node.copy(mask = null)
                is LayerNode.AdjustmentLayer -> node.copy(mask = null)
            }
        }
    }
}

/**
 * Reversible layer mask removal command.
 */
data class RemoveMaskCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val removedMask: LayerMask,
    override val description: String = "Remove Layer Mask",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(mask = null)
                is LayerNode.VectorPathLayer -> node.copy(mask = null)
                is LayerNode.TextLayer -> node.copy(mask = null)
                is LayerNode.LayerGroup -> node.copy(mask = null)
                is LayerNode.AdjustmentLayer -> node.copy(mask = null)
            }
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(mask = removedMask)
                is LayerNode.VectorPathLayer -> node.copy(mask = removedMask)
                is LayerNode.TextLayer -> node.copy(mask = removedMask)
                is LayerNode.LayerGroup -> node.copy(mask = removedMask)
                is LayerNode.AdjustmentLayer -> node.copy(mask = removedMask)
            }
        }
    }
}

/**
 * Reversible layer mask inversion command.
 */
data class InvertMaskCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val oldInverted: Boolean,
    val newInverted: Boolean,
    override val description: String = "Invert Layer Mask",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 128L

    override fun apply(document: ArtDocument): ArtDocument {
        return updateInverted(document, newInverted)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return updateInverted(document, oldInverted)
    }

    private fun updateInverted(document: ArtDocument, inverted: Boolean): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            val m = node.mask ?: return@withUpdatedLayer node
            val updatedMask = m.copy(inverted = inverted)
            when (node) {
                is LayerNode.RasterLayer -> node.copy(mask = updatedMask)
                is LayerNode.VectorPathLayer -> node.copy(mask = updatedMask)
                is LayerNode.TextLayer -> node.copy(mask = updatedMask)
                is LayerNode.LayerGroup -> node.copy(mask = updatedMask)
                is LayerNode.AdjustmentLayer -> node.copy(mask = updatedMask)
            }
        }
    }
}

/**
 * Reversible mask property modification command (enabled, linkTransformWithLayer).
 */
data class SetMaskPropertyCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val propertyName: String,
    val oldValue: Any,
    val newValue: Any,
    override val description: String = "Change mask $propertyName",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 128L

    override fun apply(document: ArtDocument): ArtDocument {
        return applyProperty(document, newValue)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return applyProperty(document, oldValue)
    }

    private fun applyProperty(document: ArtDocument, value: Any): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            val m = node.mask ?: return@withUpdatedLayer node
            val updatedMask = when (propertyName.lowercase()) {
                "enabled" -> m.copy(enabled = value as Boolean)
                "inverted" -> m.copy(inverted = value as Boolean)
                "linktransformwithlayer", "linked" -> m.copy(linkTransformWithLayer = value as Boolean)
                else -> m
            }
            when (node) {
                is LayerNode.RasterLayer -> node.copy(mask = updatedMask)
                is LayerNode.VectorPathLayer -> node.copy(mask = updatedMask)
                is LayerNode.TextLayer -> node.copy(mask = updatedMask)
                is LayerNode.LayerGroup -> node.copy(mask = updatedMask)
                is LayerNode.AdjustmentLayer -> node.copy(mask = updatedMask)
            }
        }
    }
}

/**
 * Reversible independent mask transformation command.
 */
data class TransformMaskCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val oldTransform: LayerTransform,
    val newTransform: LayerTransform,
    override val description: String = "Transform Layer Mask",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return updateTransform(document, newTransform)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return updateTransform(document, oldTransform)
    }

    private fun updateTransform(document: ArtDocument, transform: LayerTransform): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            val m = node.mask ?: return@withUpdatedLayer node
            val updatedMask = m.copy(transform = transform)
            when (node) {
                is LayerNode.RasterLayer -> node.copy(mask = updatedMask)
                is LayerNode.VectorPathLayer -> node.copy(mask = updatedMask)
                is LayerNode.TextLayer -> node.copy(mask = updatedMask)
                is LayerNode.LayerGroup -> node.copy(mask = updatedMask)
                is LayerNode.AdjustmentLayer -> node.copy(mask = updatedMask)
            }
        }
    }
}

/**
 * Reversible document selection update command.
 * Captures previous and updated selection mask states and integrates with SelectionManager.
 */
data class SetSelectionCommand(
    override val id: String = UUID.randomUUID().toString(),
    val oldMaskBytes: ByteArray,
    val newMaskBytes: ByteArray,
    val width: Int,
    val height: Int,
    val selectionManager: com.artstudio.selection.SelectionManager? = null,
    override val description: String = "Modify Selection",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 128L + oldMaskBytes.size + newMaskBytes.size

    override fun apply(document: ArtDocument): ArtDocument {
        selectionManager?.let { sm ->
            val mask = com.artstudio.selection.SelectionMask(width, height, newMaskBytes)
            sm.setSelectionMask(mask)
        }
        return document.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        selectionManager?.let { sm ->
            val mask = com.artstudio.selection.SelectionMask(width, height, oldMaskBytes)
            sm.setSelectionMask(mask)
        }
        return document.copy(updatedAt = System.currentTimeMillis())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SetSelectionCommand
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Reversible canvas resize command.
 * Alters canvas dimensions and shifts layer positions according to [anchor].
 * Preserves out-of-bounds layer pixels non-destructively.
 */
data class ResizeCanvasCommand(
    override val id: String = UUID.randomUUID().toString(),
    val oldCanvas: CanvasConfig,
    val newCanvas: CanvasConfig,
    val anchor: CanvasResizeAnchor = CanvasResizeAnchor.CENTER,
    val shiftLayers: Boolean = true,
    override val description: String = "Resize Canvas to ${newCanvas.width}x${newCanvas.height}",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        val dx = if (shiftLayers) (newCanvas.width - oldCanvas.width) * anchor.alignX else 0f
        val dy = if (shiftLayers) (newCanvas.height - oldCanvas.height) * anchor.alignY else 0f
        val updatedDoc = if (dx != 0f || dy != 0f) {
            document.mapLayers { node ->
                val t = node.transform
                val newT = t.copy(translationX = t.translationX + dx, translationY = t.translationY + dy)
                when (node) {
                    is LayerNode.RasterLayer -> node.copy(transform = newT)
                    is LayerNode.VectorPathLayer -> node.copy(transform = newT)
                    is LayerNode.TextLayer -> node.copy(transform = newT)
                    is LayerNode.AdjustmentLayer -> node.copy(transform = newT)
                    is LayerNode.LayerGroup -> node.copy(transform = newT)
                }
            }
        } else {
            document
        }
        return updatedDoc.copy(canvas = newCanvas, updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val dx = if (shiftLayers) (newCanvas.width - oldCanvas.width) * anchor.alignX else 0f
        val dy = if (shiftLayers) (newCanvas.height - oldCanvas.height) * anchor.alignY else 0f
        val revertedDoc = if (dx != 0f || dy != 0f) {
            document.mapLayers { node ->
                val t = node.transform
                val newT = t.copy(translationX = t.translationX - dx, translationY = t.translationY - dy)
                when (node) {
                    is LayerNode.RasterLayer -> node.copy(transform = newT)
                    is LayerNode.VectorPathLayer -> node.copy(transform = newT)
                    is LayerNode.TextLayer -> node.copy(transform = newT)
                    is LayerNode.AdjustmentLayer -> node.copy(transform = newT)
                    is LayerNode.LayerGroup -> node.copy(transform = newT)
                }
            }
        } else {
            document
        }
        return revertedDoc.copy(canvas = oldCanvas, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Reversible non-destructive canvas crop command.
 * Adjusts canvas bounds to [newCanvas] and offsets layers by [cropOffsetX], [cropOffsetY].
 * Out-of-bounds content is strictly preserved in layer memory.
 */
data class CropCanvasCommand(
    override val id: String = UUID.randomUUID().toString(),
    val oldCanvas: CanvasConfig,
    val newCanvas: CanvasConfig,
    val cropOffsetX: Float,
    val cropOffsetY: Float,
    override val description: String = "Crop Canvas to ${newCanvas.width}x${newCanvas.height}",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        val updatedDoc = document.mapLayers { node ->
            val t = node.transform
            val newT = t.copy(translationX = t.translationX - cropOffsetX, translationY = t.translationY - cropOffsetY)
            when (node) {
                is LayerNode.RasterLayer -> node.copy(transform = newT)
                is LayerNode.VectorPathLayer -> node.copy(transform = newT)
                is LayerNode.TextLayer -> node.copy(transform = newT)
                is LayerNode.AdjustmentLayer -> node.copy(transform = newT)
                is LayerNode.LayerGroup -> node.copy(transform = newT)
            }
        }
        return updatedDoc.copy(canvas = newCanvas, updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val revertedDoc = document.mapLayers { node ->
            val t = node.transform
            val newT = t.copy(translationX = t.translationX + cropOffsetX, translationY = t.translationY + cropOffsetY)
            when (node) {
                is LayerNode.RasterLayer -> node.copy(transform = newT)
                is LayerNode.VectorPathLayer -> node.copy(transform = newT)
                is LayerNode.TextLayer -> node.copy(transform = newT)
                is LayerNode.AdjustmentLayer -> node.copy(transform = newT)
                is LayerNode.LayerGroup -> node.copy(transform = newT)
            }
        }
        return revertedDoc.copy(canvas = oldCanvas, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Reversible deformation command for raster layer pixels (Warp Mesh and Liquify).
 */
data class DeformLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val preDeformPixels: IntArray,
    val postDeformPixels: IntArray,
    val width: Int,
    val height: Int,
    override val description: String = "Deform Layer",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        (preDeformPixels.size.toLong() + postDeformPixels.size.toLong()) * 4L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(width = width, height = height)
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(width = width, height = height)
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeformLayerCommand

        if (id != other.id) return false
        if (layerId != other.layerId) return false
        if (!preDeformPixels.contentEquals(other.preDeformPixels)) return false
        if (!postDeformPixels.contentEquals(other.postDeformPixels)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + layerId.hashCode()
        result = 31 * result + preDeformPixels.contentHashCode()
        result = 31 * result + postDeformPixels.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + description.hashCode()
        return result
    }
}

/**
 * Transactional command applying a linear or radial gradient to a raster layer.
 * Records pre- and post-gradient pixel buffers for bit-exact undo/redo reversibility.
 */
data class ApplyGradientCommand(
    override val id: String = java.util.UUID.randomUUID().toString(),
    val layerId: String,
    val preGradientPixels: IntArray,
    val postGradientPixels: IntArray,
    val width: Int,
    val height: Int,
    val gradientName: String = "Gradient",
    override val description: String = "Apply Gradient ($gradientName)",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        (preGradientPixels.size.toLong() + postGradientPixels.size.toLong()) * 4L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(width = width, height = height)
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(width = width, height = height)
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApplyGradientCommand

        if (id != other.id) return false
        if (layerId != other.layerId) return false
        if (!preGradientPixels.contentEquals(other.preGradientPixels)) return false
        if (!postGradientPixels.contentEquals(other.postGradientPixels)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + layerId.hashCode()
        result = 31 * result + preGradientPixels.contentHashCode()
        result = 31 * result + postGradientPixels.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + description.hashCode()
        return result
    }
}

/**
 * Reversible flood / bucket fill edit command.
 * Records target layer ID and tile-level deltas for memory-bounded, 100% exact undo/redo restoration.
 */
data class FillCommand(
    override val id: String = java.util.UUID.randomUUID().toString(),
    val layerId: String,
    val tileDeltas: List<TileDelta>,
    val width: Int,
    val height: Int,
    override val description: String = "Bucket Fill",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    constructor(
        id: String = java.util.UUID.randomUUID().toString(),
        layerId: String,
        preFillPixels: IntArray,
        postFillPixels: IntArray,
        width: Int,
        height: Int,
        description: String = "Bucket Fill",
        timestamp: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        layerId = layerId,
        tileDeltas = TileDeltaHelper.extractTileDeltas(preFillPixels, postFillPixels, width, height),
        width = width,
        height = height,
        description = description,
        timestamp = timestamp
    )

    override val estimatedMemoryBytes: Long
        get() = 128L + tileDeltas.sumOf { it.estimatedMemoryBytes }

    val preFillPixels: IntArray
        get() {
            val arr = IntArray(width * height)
            for (d in tileDeltas) d.applyTo(arr, width, usePre = true)
            return arr
        }

    val postFillPixels: IntArray
        get() {
            val arr = IntArray(width * height)
            for (d in tileDeltas) d.applyTo(arr, width, usePre = false)
            return arr
        }

    fun restorePixels(targetPixels: IntArray, usePre: Boolean) {
        for (delta in tileDeltas) {
            delta.applyTo(targetPixels, width, usePre)
        }
    }

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(width = width, height = height)
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(width = width, height = height)
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FillCommand

        if (id != other.id) return false
        if (layerId != other.layerId) return false
        if (tileDeltas != other.tileDeltas) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + layerId.hashCode()
        result = 31 * result + tileDeltas.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + description.hashCode()
        return result
    }
}

/**
 * Reversible clear layer edit command.
 * Clears strokes and resets layer pixel buffers with tile deltas for 100% exact undo/redo restoration.
 */
data class ClearLayerCommand(
    override val id: String = java.util.UUID.randomUUID().toString(),
    val layerId: String,
    val clearedStrokes: List<BrushStroke>,
    val tileDeltas: List<TileDelta>,
    val width: Int,
    val height: Int,
    override val description: String = "Clear Layer",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long
        get() = 256L + (clearedStrokes.size * 64L) + tileDeltas.sumOf { it.estimatedMemoryBytes }

    override fun apply(document: ArtDocument): ArtDocument {
        return document.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.copy(updatedAt = System.currentTimeMillis())
    }

    fun restorePixels(targetPixels: IntArray, usePre: Boolean) {
        for (delta in tileDeltas) {
            delta.applyTo(targetPixels, width, usePre)
        }
    }
}

/**
 * Reversible command to fill an active selection into a raster layer's pixels.
 */
data class FillSelectionCommand(
    override val id: String = java.util.UUID.randomUUID().toString(),
    val layerId: String,
    val color: Int,
    val opacity: Float,
    val tileDeltas: List<TileDelta>,
    val width: Int,
    val height: Int,
    override val description: String = "Fill Selection",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long
        get() = 256L + tileDeltas.sumOf { it.estimatedMemoryBytes }

    override fun apply(document: ArtDocument): ArtDocument {
        return document.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.copy(updatedAt = System.currentTimeMillis())
    }

    fun restorePixels(targetPixels: IntArray, usePre: Boolean) {
        for (delta in tileDeltas) {
            delta.applyTo(targetPixels, width, usePre)
        }
    }
}

/**
 * Reversible command baking an affine [LayerTransform] into a layer's raster pixel buffer.
 */
data class BakeTransformCommand(
    override val id: String = java.util.UUID.randomUUID().toString(),
    val layerId: String,
    val oldTransform: LayerTransform,
    val tileDeltas: List<TileDelta>,
    val width: Int,
    val height: Int,
    override val description: String = "Bake Layer Transform",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long
        get() = 256L + tileDeltas.sumOf { it.estimatedMemoryBytes }

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(transform = LayerTransform())
                is LayerNode.VectorPathLayer -> node.copy(transform = LayerTransform())
                is LayerNode.TextLayer -> node.copy(transform = LayerTransform())
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy(transform = oldTransform)
                is LayerNode.VectorPathLayer -> node.copy(transform = oldTransform)
                is LayerNode.TextLayer -> node.copy(transform = oldTransform)
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    fun restorePixels(targetPixels: IntArray, usePre: Boolean) {
        for (delta in tileDeltas) {
            delta.applyTo(targetPixels, width, usePre)
        }
    }
}
