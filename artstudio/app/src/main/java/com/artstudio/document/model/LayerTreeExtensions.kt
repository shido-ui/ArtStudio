package com.artstudio.document.model

/**
 * Functional, immutable manipulation utilities for hierarchical [ArtDocument] layer trees.
 * Ensures all mutations return clean, new [ArtDocument] instances without mutating original trees.
 */

fun ArtDocument.withAddedLayer(
    layer: LayerNode,
    parentGroupId: String? = null,
    index: Int = -1
): ArtDocument {
    val updatedRoot = if (parentGroupId == null || parentGroupId == rootGroup.id) {
        val newChildren = rootGroup.children.toMutableList()
        val targetIdx = if (index in 0..newChildren.size) index else newChildren.size
        newChildren.add(targetIdx, layer)
        rootGroup.copy(children = newChildren)
    } else {
        insertChildInGroup(rootGroup, parentGroupId, layer, index)
    }
    return this.copy(rootGroup = updatedRoot, updatedAt = System.currentTimeMillis())
}

fun ArtDocument.withRemovedLayer(layerId: String): ArtDocument {
    val updatedRoot = removeChildFromGroup(rootGroup, layerId)
    return this.copy(rootGroup = updatedRoot, updatedAt = System.currentTimeMillis())
}

fun ArtDocument.withReorderedLayer(
    layerId: String,
    fromIndex: Int,
    toIndex: Int,
    parentGroupId: String? = null
): ArtDocument {
    val updatedRoot = if (parentGroupId == null || parentGroupId == rootGroup.id) {
        val newChildren = rootGroup.children.toMutableList()
        if (fromIndex in 0 until newChildren.size) {
            val item = newChildren.removeAt(fromIndex)
            val safeTo = toIndex.coerceIn(0, newChildren.size)
            newChildren.add(safeTo, item)
        }
        rootGroup.copy(children = newChildren)
    } else {
        reorderChildInGroup(rootGroup, parentGroupId, fromIndex, toIndex)
    }
    return this.copy(rootGroup = updatedRoot, updatedAt = System.currentTimeMillis())
}

fun ArtDocument.withUpdatedLayer(
    layerId: String,
    transform: (LayerNode) -> LayerNode
): ArtDocument {
    val updatedRoot = updateNodeInGroup(rootGroup, layerId, transform)
    return this.copy(rootGroup = updatedRoot, updatedAt = System.currentTimeMillis())
}

private fun insertChildInGroup(
    group: LayerNode.LayerGroup,
    targetGroupId: String,
    layerToInsert: LayerNode,
    index: Int
): LayerNode.LayerGroup {
    val updatedChildren = group.children.map { child ->
        if (child is LayerNode.LayerGroup) {
            if (child.id == targetGroupId) {
                val list = child.children.toMutableList()
                val targetIdx = if (index in 0..list.size) index else list.size
                list.add(targetIdx, layerToInsert)
                child.copy(children = list)
            } else {
                insertChildInGroup(child, targetGroupId, layerToInsert, index)
            }
        } else {
            child
        }
    }
    return group.copy(children = updatedChildren)
}

private fun removeChildFromGroup(
    group: LayerNode.LayerGroup,
    targetLayerId: String
): LayerNode.LayerGroup {
    val filteredChildren = group.children.filterNot { it.id == targetLayerId }.map { child ->
        if (child is LayerNode.LayerGroup) {
            removeChildFromGroup(child, targetLayerId)
        } else {
            child
        }
    }
    return group.copy(children = filteredChildren)
}

private fun reorderChildInGroup(
    group: LayerNode.LayerGroup,
    targetGroupId: String,
    fromIndex: Int,
    toIndex: Int
): LayerNode.LayerGroup {
    val updatedChildren = group.children.map { child ->
        if (child is LayerNode.LayerGroup) {
            if (child.id == targetGroupId) {
                val list = child.children.toMutableList()
                if (fromIndex in 0 until list.size) {
                    val item = list.removeAt(fromIndex)
                    val safeTo = toIndex.coerceIn(0, list.size)
                    list.add(safeTo, item)
                }
                child.copy(children = list)
            } else {
                reorderChildInGroup(child, targetGroupId, fromIndex, toIndex)
            }
        } else {
            child
        }
    }
    return group.copy(children = updatedChildren)
}

private fun updateNodeInGroup(
    group: LayerNode.LayerGroup,
    targetLayerId: String,
    transform: (LayerNode) -> LayerNode
): LayerNode.LayerGroup {
    if (group.id == targetLayerId) {
        return transform(group) as LayerNode.LayerGroup
    }
    val updatedChildren = group.children.map { child ->
        if (child.id == targetLayerId) {
            transform(child)
        } else if (child is LayerNode.LayerGroup) {
            updateNodeInGroup(child, targetLayerId, transform)
        } else {
            child
        }
    }
    return group.copy(children = updatedChildren)
}

object LayerHierarchyConstants {
    /**
     * Maximum allowed nesting depth for hierarchical layer groups (Procreate/Photoshop mobile standard).
     * Depth 0 = Canvas Root group. Depth 1..5 = Nested layer groups.
     */
    const val MAX_GROUP_DEPTH = 5
}

class MaxGroupDepthExceededException(
    val requestedDepth: Int,
    val maxAllowed: Int = LayerHierarchyConstants.MAX_GROUP_DEPTH
) : IllegalStateException("Cannot nest layer group at depth $requestedDepth: maximum allowed depth is $maxAllowed")

/**
 * Finds the parent group ID containing [targetId]. Returns null if [targetId] is not found.
 */
fun ArtDocument.findParentGroupId(targetId: String): String? {
    fun search(group: LayerNode.LayerGroup): String? {
        for (child in group.children) {
            if (child.id == targetId) return group.id
            if (child is LayerNode.LayerGroup) {
                val found = search(child)
                if (found != null) return found
            }
        }
        return null
    }
    return search(rootGroup)
}

/**
 * Computes the nesting depth of [groupId]. Root group is depth 0.
 */
fun ArtDocument.computeGroupDepth(groupId: String): Int {
    if (groupId == rootGroup.id) return 0
    fun search(group: LayerNode.LayerGroup, currentDepth: Int): Int? {
        for (child in group.children) {
            if (child.id == groupId) return currentDepth + 1
            if (child is LayerNode.LayerGroup) {
                val found = search(child, currentDepth + 1)
                if (found != null) return found
            }
        }
        return null
    }
    return search(rootGroup, 0) ?: 0
}

/**
 * Checks if [candidateDescendantId] is a descendant of [ancestorId] to prevent cyclic tree insertion.
 */
fun ArtDocument.isDescendantOf(candidateDescendantId: String, ancestorId: String): Boolean {
    if (candidateDescendantId == ancestorId) return true
    val ancestor = findLayerById(ancestorId) as? LayerNode.LayerGroup ?: return false
    fun check(group: LayerNode.LayerGroup): Boolean {
        for (child in group.children) {
            if (child.id == candidateDescendantId) return true
            if (child is LayerNode.LayerGroup && check(child)) return true
        }
        return false
    }
    return check(ancestor)
}

/**
 * Moves a layer from its current parent group to [targetGroupId] at [targetIndex].
 */
fun ArtDocument.withMovedLayer(
    layerId: String,
    targetGroupId: String,
    targetIndex: Int = -1
): ArtDocument {
    val layer = findLayerById(layerId) ?: return this
    val withoutLayer = withRemovedLayer(layerId)
    return withoutLayer.withAddedLayer(layer, targetGroupId, targetIndex)
}

/**
 * Computes the maximum group nesting depth contained within [node] subtree.
 * Returns 0 if [node] is a leaf layer (not a group) or an empty group.
 */
fun computeSubtreeGroupDepth(node: LayerNode): Int {
    return when (node) {
        is LayerNode.LayerGroup -> {
            val childGroups = node.children.filterIsInstance<LayerNode.LayerGroup>()
            if (childGroups.isEmpty()) {
                1
            } else {
                1 + childGroups.maxOf { computeSubtreeGroupDepth(it) }
            }
        }
        else -> 0
    }
}

/**
 * Finds the child index of [targetId] within [parentGroupId] (or root group if null/root).
 * Returns -1 if not found.
 */
fun ArtDocument.findLayerIndexInParent(targetId: String, parentGroupId: String?): Int {
    val group = if (parentGroupId == null || parentGroupId == rootGroup.id) {
        rootGroup
    } else {
        findLayerById(parentGroupId) as? LayerNode.LayerGroup ?: rootGroup
    }
    return group.children.indexOfFirst { it.id == targetId }
}

/**
 * Functional traversal mapping every layer in the document hierarchy recursively.
 */
fun ArtDocument.mapLayers(transform: (LayerNode) -> LayerNode): ArtDocument {
    fun mapNode(node: LayerNode): LayerNode {
        val updated = transform(node)
        return if (updated is LayerNode.LayerGroup) {
            updated.copy(children = updated.children.map { mapNode(it) })
        } else {
            updated
        }
    }
    val newRoot = rootGroup.copy(children = rootGroup.children.map { mapNode(it) })
    return this.copy(rootGroup = newRoot, updatedAt = System.currentTimeMillis())
}


