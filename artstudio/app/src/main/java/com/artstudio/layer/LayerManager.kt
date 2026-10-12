package com.artstudio.layer

import com.artstudio.core.BrushStroke
import com.artstudio.document.model.*
import com.artstudio.history.*
import com.artstudio.importer.DecodedRasterImage
import com.artstudio.importer.ImportPlacementConfig
import com.artstudio.importer.RasterImageImporter
import java.util.UUID

/**
 * High-level authoritative Layer Management Controller.
 * Provides transactional, undoable layer lifecycle operations:
 * create, select, rename, duplicate, delete, hide/show, reorder, lock/unlock, and opacity.
 *
 * Invariants:
 * 1. Root layer group always contains at least 1 layer (minimum layer invariant).
 * 2. Layer ordering is strictly bottom-to-top (index 0 is bottom-most, index N-1 is top-most).
 * 3. Layer IDs are immutable, persistent UUIDs.
 * 4. Locked layers reject new stroke commits.
 * 5. Hidden layers are excluded from visual rendering.
 */
class LayerManager(
    val historyManager: HistoryManager,
    var onDuplicateStrokes: ((sourceLayerId: String, duplicateLayerId: String) -> Unit)? = null,
    var onRemoveStrokes: ((layerId: String) -> Unit)? = null,
    var onMergeStrokes: ((sourceLayerIds: List<String>, mergedLayerId: String) -> Map<String, String>)? = null,
    var onRestoreStrokes: ((mapping: Map<String, String>) -> Unit)? = null,
    var onReapplyStrokes: ((strokeIds: Collection<String>, mergedLayerId: String) -> Unit)? = null,
    val uiState: LayerTreeUiState = LayerTreeUiState()
) {

    val currentDocument: ArtDocument
        get() = historyManager.currentDocument

    /**
     * Returns all layers in the root group in document order (0 = bottom, N-1 = top).
     */
    fun getLayers(): List<LayerNode> {
        return currentDocument.rootGroup.children
    }

    /**
     * Returns all layers flat across all groups in depth-first traversal order.
     */
    fun getAllLayers(): List<LayerNode> {
        return currentDocument.allLayersFlat()
    }

    /**
     * Resolves the currently active / selected layer node.
     */
    fun getSelectedLayer(): LayerNode? {
        return currentDocument.getSelectedLayer()
    }

    /**
     * Resolves the currently selected layer ID.
     */
    fun getSelectedLayerId(): String {
        return currentDocument.activeLayerId
    }

    /**
     * Creates a new raster layer, inserts it into the document hierarchy,
     * updates active selection, and records an undoable command.
     *
     * @param name Optional layer name. Defaults to "Layer N".
     * @param parentGroupId Target parent group ID (null for rootGroup).
     * @param index Target index in parent group. If -1, inserts above current selection.
     * @return The created [LayerNode.RasterLayer].
     */
    fun createLayer(
        name: String? = null,
        parentGroupId: String? = null,
        index: Int = -1
    ): LayerNode.RasterLayer {
        val parent = if (parentGroupId == null || parentGroupId == currentDocument.rootGroup.id) {
            currentDocument.rootGroup
        } else {
            currentDocument.findLayerById(parentGroupId) as? LayerNode.LayerGroup ?: currentDocument.rootGroup
        }
        val children = parent.children
        val totalLayers = currentDocument.allLayersFlat().filterNot { it is LayerNode.LayerGroup }.size
        val maxAllowed = com.artstudio.core.DeviceMemoryPolicy.maxLayersForCanvas(currentDocument.canvas.width, currentDocument.canvas.height)
        if (totalLayers >= maxAllowed) {
            throw IllegalStateException(
                "Cannot add layer: Device memory policy limit reached ($maxAllowed layers max on ${com.artstudio.core.DeviceMemoryPolicy.currentTier.displayName})."
            )
        }
        val finalName = name ?: "Layer ${totalLayers + 1}"

        val newLayer = LayerNode.RasterLayer(
            id = UUID.randomUUID().toString(),
            name = finalName,
            width = currentDocument.canvas.width,
            height = currentDocument.canvas.height,
            visible = true,
            opacity = 1.0f,
            isLocked = false
        )

        val targetIndex = if (index in 0..children.size) {
            index
        } else {
            val selId = currentDocument.activeLayerId
            val selIndex = children.indexOfFirst { it.id == selId }
            if (selIndex >= 0) selIndex + 1 else children.size
        }

        val cmd = AddLayerCommand(
            layer = newLayer,
            parentGroupId = parentGroupId,
            index = targetIndex,
            previousSelectedLayerId = currentDocument.selectedLayerId
        )
        historyManager.execute(cmd)
        uiState.selectLayer(newLayer.id)
        return newLayer
    }

    /**
     * Adds an existing or custom [LayerNode] into the document hierarchy.
     */
    fun addLayer(
        layer: LayerNode,
        parentGroupId: String? = null,
        index: Int = -1
    ): LayerNode {
        val parent = if (parentGroupId == null || parentGroupId == currentDocument.rootGroup.id) {
            currentDocument.rootGroup
        } else {
            currentDocument.findLayerById(parentGroupId) as? LayerNode.LayerGroup ?: currentDocument.rootGroup
        }
        val children = parent.children
        val targetIndex = if (index in 0..children.size) {
            index
        } else {
            val selId = currentDocument.activeLayerId
            val selIndex = children.indexOfFirst { it.id == selId }
            if (selIndex >= 0) selIndex + 1 else children.size
        }

        val cmd = AddLayerCommand(
            layer = layer,
            parentGroupId = parentGroupId,
            index = targetIndex,
            previousSelectedLayerId = currentDocument.selectedLayerId
        )
        historyManager.execute(cmd)
        uiState.selectLayer(layer.id)
        return layer
    }

    /**
     * Selects an active layer by ID.
     * Reversible via undo/redo.
     */
    fun selectLayer(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val oldId = currentDocument.selectedLayerId
        if (oldId == layerId) return true

        val cmd = SelectLayerCommand(oldLayerId = oldId, newLayerId = layerId)
        historyManager.execute(cmd)
        uiState.selectLayer(layerId)
        return true
    }

    /**
     * Renames an existing layer.
     */
    fun renameLayer(layerId: String, newName: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        if (layer.name == newName) return true

        val cmd = SetLayerPropertyCommand(
            layerId = layerId,
            propertyName = "name",
            oldValue = layer.name,
            newValue = newName
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Duplicates an existing raster layer with a new UUID and name.
     * Inserts the clone immediately above the source layer.
     */
    fun duplicateLayer(layerId: String): LayerNode.RasterLayer? {
        val sourceLayer = currentDocument.findLayerById(layerId) as? LayerNode.RasterLayer ?: return null
        val parentGroupId = currentDocument.findParentGroupId(layerId)
        val parent = if (parentGroupId == null || parentGroupId == currentDocument.rootGroup.id) {
            currentDocument.rootGroup
        } else {
            currentDocument.findLayerById(parentGroupId) as? LayerNode.LayerGroup ?: currentDocument.rootGroup
        }
        val sourceIndex = parent.children.indexOfFirst { it.id == layerId }
        if (sourceIndex < 0) return null

        val totalLayers = currentDocument.allLayersFlat().filterNot { it is LayerNode.LayerGroup }.size
        val maxAllowed = com.artstudio.core.DeviceMemoryPolicy.maxLayersForCanvas(currentDocument.canvas.width, currentDocument.canvas.height)
        if (totalLayers >= maxAllowed) {
            throw IllegalStateException(
                "Cannot duplicate layer: Device memory policy limit reached ($maxAllowed layers max on ${com.artstudio.core.DeviceMemoryPolicy.currentTier.displayName})."
            )
        }

        val newId = UUID.randomUUID().toString()
        val duplicatedLayer = sourceLayer.copy(
            id = newId,
            name = "${sourceLayer.name} Copy"
        )

        val insertIndex = sourceIndex + 1
        val cmd = DuplicateLayerCommand(
            sourceLayerId = layerId,
            duplicatedLayer = duplicatedLayer,
            insertIndex = insertIndex,
            parentGroupId = parentGroupId
        )
        historyManager.execute(cmd)

        // Duplicate associated strokes if hook registered
        onDuplicateStrokes?.invoke(layerId, newId)
        uiState.selectLayer(newId)

        return duplicatedLayer
    }

    /**
     * Deletes a layer from the document hierarchy.
     * Guard: Cannot delete the last remaining drawing layer!
     */
    fun deleteLayer(layerId: String): Boolean {
        val drawingLayers = currentDocument.allLayersFlat().filterNot { it is LayerNode.LayerGroup }
        if (drawingLayers.size <= 1) {
            // Guard: Canvas must have at least 1 layer
            return false
        }

        val parentGroupId = currentDocument.findParentGroupId(layerId)
        val parent = if (parentGroupId == null || parentGroupId == currentDocument.rootGroup.id) {
            currentDocument.rootGroup
        } else {
            currentDocument.findLayerById(parentGroupId) as? LayerNode.LayerGroup ?: currentDocument.rootGroup
        }
        val originalIndex = parent.children.indexOfFirst { it.id == layerId }
        if (originalIndex < 0) return false

        val layerToRemove = parent.children[originalIndex]
        val cmd = RemoveLayerCommand(
            removedLayer = layerToRemove,
            parentGroupId = parentGroupId,
            originalIndex = originalIndex,
            previousSelectedLayerId = currentDocument.selectedLayerId
        )
        historyManager.execute(cmd)
        onRemoveStrokes?.invoke(layerId)
        uiState.selectedLayerIds.let {
            if (it.contains(layerId)) {
                uiState.clearMultiSelection()
                currentDocument.activeLayerId.takeIf { id -> id.isNotEmpty() }?.let { id -> uiState.selectLayer(id) }
            }
        }
        return true
    }

    /**
     * Sets visibility on a layer.
     */
    fun setLayerVisibility(layerId: String, visible: Boolean): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        if (layer.visible == visible) return true

        val cmd = SetLayerPropertyCommand(
            layerId = layerId,
            propertyName = "visible",
            oldValue = layer.visible,
            newValue = visible
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Toggles visibility of a layer.
     */
    fun toggleLayerVisibility(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        return setLayerVisibility(layerId, !layer.visible)
    }

    /**
     * Sets lock state on a layer.
     */
    fun setLayerLocked(layerId: String, isLocked: Boolean): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        if (layer.isLocked == isLocked) return true

        val cmd = SetLayerPropertyCommand(
            layerId = layerId,
            propertyName = "locked",
            oldValue = layer.isLocked,
            newValue = isLocked
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Toggles lock state of a layer.
     */
    fun toggleLayerLocked(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        return setLayerLocked(layerId, !layer.isLocked)
    }

    /**
     * Sets opacity on a layer, clamped to [0.0, 1.0].
     */
    fun setLayerOpacity(layerId: String, opacity: Float): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val clamped = opacity.coerceIn(0.0f, 1.0f)
        if (Math.abs(layer.opacity - clamped) < 0.0001f) return true

        val cmd = SetLayerPropertyCommand(
            layerId = layerId,
            propertyName = "opacity",
            oldValue = layer.opacity,
            newValue = clamped
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Sets the compositing blend mode on a layer.
     */
    fun setLayerBlendMode(layerId: String, blendMode: BlendModeType): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        if (layer.blendMode == blendMode) return true

        val cmd = SetLayerPropertyCommand(
            layerId = layerId,
            propertyName = "blendmode",
            oldValue = layer.blendMode,
            newValue = blendMode
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Sets clipping mask state on a layer.
     */
    fun setLayerClippingMask(layerId: String, isClipping: Boolean): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        if (layer.isClippingMask == isClipping) return true

        val cmd = SetLayerPropertyCommand(
            layerId = layerId,
            propertyName = "isclippingmask",
            oldValue = layer.isClippingMask,
            newValue = isClipping
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Toggles clipping mask state of a layer.
     */
    fun toggleLayerClippingMask(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        return setLayerClippingMask(layerId, !layer.isClippingMask)
    }

    /**
     * Sets alpha lock state on a layer.
     */
    fun setLayerAlphaLock(layerId: String, isAlphaLocked: Boolean): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        if (layer.isAlphaLocked == isAlphaLocked) return true

        val cmd = SetLayerPropertyCommand(
            layerId = layerId,
            propertyName = "isalphalocked",
            oldValue = layer.isAlphaLocked,
            newValue = isAlphaLocked
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Toggles alpha lock state of a layer.
     */
    fun toggleLayerAlphaLock(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        return setLayerAlphaLock(layerId, !layer.isAlphaLocked)
    }

    /**
     * Adds an editable grayscale layer mask to a layer.
     * If the layer already has a mask, returns the existing mask.
     */
    fun addLayerMask(layerId: String, initialMask: LayerMask? = null): LayerMask? {
        val layer = currentDocument.findLayerById(layerId) ?: return null
        if (layer.mask != null) return layer.mask

        val newMask = initialMask ?: LayerMask(
            width = currentDocument.canvas.width,
            height = currentDocument.canvas.height
        )
        val cmd = AddMaskCommand(
            layerId = layerId,
            mask = newMask
        )
        historyManager.execute(cmd)
        return newMask
    }

    /**
     * Removes the editable layer mask from a layer.
     */
    fun removeLayerMask(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val maskToRemove = layer.mask ?: return false

        val cmd = RemoveMaskCommand(
            layerId = layerId,
            removedMask = maskToRemove
        )
        historyManager.execute(cmd)
        if (uiState.isEditingMask() && (currentDocument.selectedLayerId == layerId || getSelectedLayerId() == layerId)) {
            uiState.setEditTarget(LayerEditTarget.CONTENT)
        }
        return true
    }

    /**
     * Enables or disables a layer mask.
     */
    fun setMaskEnabled(layerId: String, enabled: Boolean): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val mask = layer.mask ?: return false
        if (mask.enabled == enabled) return true

        val cmd = SetMaskPropertyCommand(
            layerId = layerId,
            propertyName = "enabled",
            oldValue = mask.enabled,
            newValue = enabled
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Toggles enabled state of a layer mask.
     */
    fun toggleMaskEnabled(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val mask = layer.mask ?: return false
        return setMaskEnabled(layerId, !mask.enabled)
    }

    /**
     * Inverts a layer mask (black becomes white and vice versa).
     */
    fun invertMask(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val mask = layer.mask ?: return false

        val cmd = InvertMaskCommand(
            layerId = layerId,
            oldInverted = mask.inverted,
            newInverted = !mask.inverted
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Sets inversion state of a layer mask.
     */
    fun setMaskInverted(layerId: String, inverted: Boolean): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val mask = layer.mask ?: return false
        if (mask.inverted == inverted) return true

        val cmd = InvertMaskCommand(
            layerId = layerId,
            oldInverted = mask.inverted,
            newInverted = inverted
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Transforms a layer mask independently.
     */
    fun transformMask(layerId: String, newTransform: LayerTransform): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val mask = layer.mask ?: return false
        if (mask.transform == newTransform) return true

        val cmd = TransformMaskCommand(
            layerId = layerId,
            oldTransform = mask.transform,
            newTransform = newTransform
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Applies a non-destructive 2D affine transform to [layerId].
     */
    fun transformLayer(layerId: String, newTransform: LayerTransform): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        if (layer.transform == newTransform) return true
        val cmd = TransformLayerCommand(
            layerId = layerId,
            oldTransform = layer.transform,
            newTransform = newTransform
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Moves a layer by [deltaX] and [deltaY] in document coordinates.
     */
    fun moveLayer(layerId: String, deltaX: Float, deltaY: Float): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val newTransform = layer.transform.copy(
            translationX = layer.transform.translationX + deltaX,
            translationY = layer.transform.translationY + deltaY
        )
        return transformLayer(layerId, newTransform)
    }

    /**
     * Scales a layer by [scaleX] and [scaleY]. If [lockAspect] is true, scales uniformly by [scaleX].
     */
    fun scaleLayer(layerId: String, scaleX: Float, scaleY: Float, lockAspect: Boolean = true): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val finalScaleY = if (lockAspect) scaleX else scaleY
        val newTransform = layer.transform.copy(
            scaleX = scaleX,
            scaleY = finalScaleY,
            isAspectRatioLocked = lockAspect
        )
        return transformLayer(layerId, newTransform)
    }

    /**
     * Rotates a layer by [degrees].
     */
    fun rotateLayer(layerId: String, degrees: Float): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val newTransform = layer.transform.copy(
            rotationDegrees = (layer.transform.rotationDegrees + degrees) % 360f
        )
        return transformLayer(layerId, newTransform)
    }

    /**
     * Toggles horizontal flip for a layer.
     */
    fun flipLayerHorizontal(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val newTransform = layer.transform.copy(
            flipHorizontal = !layer.transform.flipHorizontal
        )
        return transformLayer(layerId, newTransform)
    }

    /**
     * Toggles vertical flip for a layer.
     */
    fun flipLayerVertical(layerId: String): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val newTransform = layer.transform.copy(
            flipVertical = !layer.transform.flipVertical
        )
        return transformLayer(layerId, newTransform)
    }

    /**
     * Resets a layer's transform to identity.
     */
    fun resetLayerTransform(layerId: String): Boolean {
        return transformLayer(layerId, LayerTransform())
    }

    /**
     * Bakes the active 2D affine transform into the layer's raster pixel buffer.
     * Generates an undoable [BakeTransformCommand] with tile deltas.
     */
    fun bakeLayerTransform(layerId: String, pixelBuffer: IntArray? = null): Boolean {
        val layer = currentDocument.findLayerById(layerId) as? LayerNode.RasterLayer ?: return false
        if (layer.transform.isIdentity) return true
        val w = layer.width
        val h = layer.height
        val buf = pixelBuffer ?: IntArray(w * h)
        val pre = buf.copyOf()
        val dest = IntArray(w * h)
        com.artstudio.core.TransformResampler.resample(pre, w, h, dest, w, h, layer.transform)
        System.arraycopy(dest, 0, buf, 0, w * h)
        val deltas = com.artstudio.history.TileDeltaHelper.extractTileDeltas(pre, buf, w, h)
        val cmd = com.artstudio.history.BakeTransformCommand(
            layerId = layerId,
            oldTransform = layer.transform,
            tileDeltas = deltas,
            width = w,
            height = h
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Resizes the document canvas dimensions non-destructively, adjusting layer alignment based on [anchor].
     * Preserves out-of-bounds layer content without discarding pixels.
     */
    fun resizeCanvas(
        newWidth: Int,
        newHeight: Int,
        anchor: CanvasResizeAnchor = CanvasResizeAnchor.CENTER,
        shiftLayers: Boolean = true
    ): Boolean {
        require(newWidth > 0) { "Canvas width must be > 0, got $newWidth" }
        require(newHeight > 0) { "Canvas height must be > 0, got $newHeight" }
        val oldCanvas = currentDocument.canvas
        if (oldCanvas.width == newWidth && oldCanvas.height == newHeight) return true

        val newCanvas = oldCanvas.copy(width = newWidth, height = newHeight)
        val cmd = ResizeCanvasCommand(
            oldCanvas = oldCanvas,
            newCanvas = newCanvas,
            anchor = anchor,
            shiftLayers = shiftLayers
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Non-destructively crops the document canvas to the specified bounding box.
     * Content outside crop bounds is preserved in layer memory.
     */
    fun cropCanvas(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        require(right > left) { "Crop right ($right) must be > left ($left)" }
        require(bottom > top) { "Crop bottom ($bottom) must be > top ($top)" }
        val newW = (right - left).toInt()
        val newH = (bottom - top).toInt()
        val oldCanvas = currentDocument.canvas

        val newCanvas = oldCanvas.copy(width = newW, height = newH)
        val cmd = CropCanvasCommand(
            oldCanvas = oldCanvas,
            newCanvas = newCanvas,
            cropOffsetX = left,
            cropOffsetY = top
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Non-destructively crops canvas to the given [SelectionBounds].
     */
    fun cropCanvas(cropBounds: com.artstudio.selection.SelectionBounds): Boolean {
        if (cropBounds.isEmpty) return false
        return cropCanvas(cropBounds.left, cropBounds.top, cropBounds.right, cropBounds.bottom)
    }

    /**
     * Creates an interactive [com.artstudio.deformation.DeformationSession] on a duplicated preview surface for [layerId].
     */
    fun createDeformationSession(
        layerId: String,
        pixelSource: IntArray,
        config: com.artstudio.deformation.DeformationConfig = com.artstudio.deformation.DeformationConfig()
    ): com.artstudio.deformation.DeformationSession? {
        val layer = currentDocument.findLayerById(layerId) ?: return null
        val (width, height) = when (layer) {
            is LayerNode.RasterLayer -> Pair(layer.width, layer.height)
            else -> Pair(currentDocument.canvas.width, currentDocument.canvas.height)
        }
        return com.artstudio.deformation.DeformationSession(
            layerId = layerId,
            originalWidth = width,
            originalHeight = height,
            originalPixels = pixelSource,
            config = config
        )
    }

    /**
     * Switches editing mode between layer content and layer mask.
     */
    fun setEditTarget(target: LayerEditTarget) {
        uiState.setEditTarget(target)
    }

    /**
     * Returns true if brush strokes currently paint onto the mask.
     */
    fun isEditingMask(): Boolean = uiState.isEditingMask()

    /**
     * Reorders a layer to a target index in its parent group.
     */
    fun reorderLayer(layerId: String, toIndex: Int, parentGroupId: String? = null): Boolean {
        val parent = if (parentGroupId == null || parentGroupId == currentDocument.rootGroup.id) {
            currentDocument.rootGroup
        } else {
            currentDocument.findLayerById(parentGroupId) as? LayerNode.LayerGroup ?: currentDocument.rootGroup
        }
        val children = parent.children
        val fromIndex = children.indexOfFirst { it.id == layerId }
        if (fromIndex < 0) return false

        val safeTo = toIndex.coerceIn(0, children.size - 1)
        if (fromIndex == safeTo) return true

        val cmd = ReorderLayerCommand(
            layerId = layerId,
            fromIndex = fromIndex,
            toIndex = safeTo,
            parentGroupId = parentGroupId
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Moves a layer one position higher in document order (drawn above).
     */
    fun moveLayerUp(layerId: String): Boolean {
        val parentId = currentDocument.findParentGroupId(layerId)
        val parent = if (parentId == null || parentId == currentDocument.rootGroup.id) {
            currentDocument.rootGroup
        } else {
            currentDocument.findLayerById(parentId) as? LayerNode.LayerGroup ?: currentDocument.rootGroup
        }
        val index = parent.children.indexOfFirst { it.id == layerId }
        if (index < 0 || index >= parent.children.size - 1) return false
        return reorderLayer(layerId, index + 1, parentId)
    }

    /**
     * Moves a layer one position lower in document order (drawn below).
     */
    fun moveLayerDown(layerId: String): Boolean {
        val parentId = currentDocument.findParentGroupId(layerId)
        val parent = if (parentId == null || parentId == currentDocument.rootGroup.id) {
            currentDocument.rootGroup
        } else {
            currentDocument.findLayerById(parentId) as? LayerNode.LayerGroup ?: currentDocument.rootGroup
        }
        val index = parent.children.indexOfFirst { it.id == layerId }
        if (index <= 0) return false
        return reorderLayer(layerId, index - 1, parentId)
    }

    // =========================================================================
    // LAYER GROUPS & STRUCTURAL OPERATIONS
    // =========================================================================

    /**
     * Creates a new layer group, optionally wrapping selected layers.
     * Enforces documented MAX_GROUP_DEPTH limit (5).
     *
     * @param name Optional group name (e.g., "Group 1").
     * @param parentGroupId Target parent group ID (null for rootGroup).
     * @param index Insertion index in parent. If -1, inserts above current selection.
     * @param layerIdsToInclude Optional layer IDs to move into the new group.
     */
    fun createGroup(
        name: String? = null,
        parentGroupId: String? = null,
        index: Int = -1,
        layerIdsToInclude: List<String> = emptyList()
    ): LayerNode.LayerGroup {
        val parentDepth = parentGroupId?.let { currentDocument.computeGroupDepth(it) } ?: 0
        if (parentDepth + 1 > LayerHierarchyConstants.MAX_GROUP_DEPTH) {
            throw MaxGroupDepthExceededException(parentDepth + 1)
        }

        val parent = if (parentGroupId == null || parentGroupId == currentDocument.rootGroup.id) {
            currentDocument.rootGroup
        } else {
            currentDocument.findLayerById(parentGroupId) as? LayerNode.LayerGroup ?: currentDocument.rootGroup
        }
        val groupCount = currentDocument.allLayersFlat().filterIsInstance<LayerNode.LayerGroup>().size
        val finalName = name ?: "Group ${groupCount + 1}"

        val childrenToInclude: List<Pair<LayerNode, Int>> = layerIdsToInclude.mapNotNull { id ->
            val node = currentDocument.findLayerById(id)
            val pId = currentDocument.findParentGroupId(id)
            val idx = currentDocument.findLayerIndexInParent(id, pId)
            if (node != null && idx >= 0) Pair(node, idx) else null
        }

        val targetIndex = if (index in 0..parent.children.size) {
            index
        } else {
            val selId = currentDocument.activeLayerId
            val selIndex = parent.children.indexOfFirst { it.id == selId }
            if (selIndex >= 0) selIndex + 1 else parent.children.size
        }

        val newGroup = LayerNode.LayerGroup(
            id = UUID.randomUUID().toString(),
            name = finalName,
            visible = true,
            opacity = 1.0f,
            children = childrenToInclude.map { it.first }
        )

        val cmd = CreateGroupCommand(
            group = newGroup,
            parentGroupId = parentGroupId,
            insertIndex = targetIndex,
            initialChildLayersWithIndices = childrenToInclude
        )
        historyManager.execute(cmd)
        uiState.expandGroup(newGroup.id)
        uiState.selectLayer(newGroup.id)
        return (currentDocument.findLayerById(newGroup.id) as? LayerNode.LayerGroup) ?: newGroup
    }

    /**
     * Moves a layer or group into a target group at [index].
     * Enforces cycle prevention and nesting depth limit (MAX_GROUP_DEPTH = 5).
     */
    fun moveLayerIntoGroup(
        layerId: String,
        targetGroupId: String,
        index: Int = -1
    ): Boolean {
        val layer = currentDocument.findLayerById(layerId) ?: return false
        val targetGroup = currentDocument.findLayerById(targetGroupId) as? LayerNode.LayerGroup ?: return false

        // Cycle check: Cannot move an ancestor into its own descendant!
        if (currentDocument.isDescendantOf(targetGroupId, layerId)) {
            return false
        }

        // Depth check: Subtree depth + target group depth must not exceed MAX_GROUP_DEPTH
        val targetDepth = currentDocument.computeGroupDepth(targetGroupId)
        val subtreeDepth = computeSubtreeGroupDepth(layer)
        if (targetDepth + subtreeDepth > LayerHierarchyConstants.MAX_GROUP_DEPTH) {
            throw MaxGroupDepthExceededException(targetDepth + subtreeDepth)
        }

        val sourceParentId = currentDocument.findParentGroupId(layerId) ?: currentDocument.rootGroup.id
        val sourceIndex = currentDocument.findLayerIndexInParent(layerId, sourceParentId)
        if (sourceIndex < 0) return false

        val safeTargetIndex = if (index in 0..targetGroup.children.size) index else targetGroup.children.size

        val cmd = MoveLayerHierarchyCommand(
            layerId = layerId,
            sourceParentGroupId = sourceParentId,
            sourceIndex = sourceIndex,
            targetParentGroupId = targetGroupId,
            targetIndex = safeTargetIndex
        )
        historyManager.execute(cmd)
        uiState.expandGroup(targetGroupId)
        return true
    }

    /**
     * Moves a layer out of its current group into the grandparent group.
     */
    fun moveLayerOutOfGroup(layerId: String): Boolean {
        val currentParentId = currentDocument.findParentGroupId(layerId) ?: return false
        if (currentParentId == currentDocument.rootGroup.id) {
            return false // Already at canvas root
        }

        val grandParentId = currentDocument.findParentGroupId(currentParentId) ?: currentDocument.rootGroup.id
        val parentIndex = currentDocument.findLayerIndexInParent(currentParentId, grandParentId)
        val sourceIndex = currentDocument.findLayerIndexInParent(layerId, currentParentId)
        if (sourceIndex < 0) return false

        val targetIndex = if (parentIndex >= 0) parentIndex + 1 else -1

        val cmd = MoveLayerHierarchyCommand(
            layerId = layerId,
            sourceParentGroupId = currentParentId,
            sourceIndex = sourceIndex,
            targetParentGroupId = grandParentId,
            targetIndex = targetIndex
        )
        historyManager.execute(cmd)
        return true
    }

    /**
     * Dissolves a group, placing all of its children into the parent group at the group's position.
     */
    fun ungroup(groupId: String): Boolean {
        if (groupId == currentDocument.rootGroup.id) {
            return false // Cannot ungroup canvas root
        }
        val group = currentDocument.findLayerById(groupId) as? LayerNode.LayerGroup ?: return false
        val parentId = currentDocument.findParentGroupId(groupId)
        val groupIndex = currentDocument.findLayerIndexInParent(groupId, parentId)
        if (groupIndex < 0) return false

        val cmd = UngroupCommand(
            removedGroup = group,
            parentGroupId = parentId,
            groupIndex = groupIndex
        )
        historyManager.execute(cmd)
        uiState.collapseGroup(groupId)
        return true
    }

    // =========================================================================
    // MULTI-SELECTION APIS (Decoupled UI State)
    // =========================================================================

    fun multiSelect(layerId: String, clearPrevious: Boolean = false) {
        uiState.selectLayer(layerId, clearPrevious)
    }

    fun toggleMultiSelect(layerId: String) {
        uiState.toggleMultiSelect(layerId)
    }

    fun setMultiSelection(layerIds: Collection<String>) {
        uiState.setMultiSelection(layerIds)
    }

    fun clearMultiSelection() {
        uiState.clearMultiSelection()
    }

    fun getSelectedLayerIds(): Set<String> {
        return uiState.selectedLayerIds
    }

    // =========================================================================
    // MERGE & FLATTEN OPERATIONS
    // =========================================================================

    /**
     * Merges selected layers into a single raster layer.
     * Fully reversible via undo/redo.
     */
    fun mergeSelectedLayers(layerIds: List<String> = emptyList()): LayerNode.RasterLayer? {
        val targetIds = if (layerIds.isNotEmpty()) layerIds else uiState.selectedLayerIds.toList()
        if (targetIds.size < 2) return null

        val nodes = targetIds.mapNotNull { currentDocument.findLayerById(it) }
        if (nodes.size < 2) return null

        val layersWithIndices: List<Pair<LayerNode, Int>> = nodes.mapNotNull { node ->
            val pId = currentDocument.findParentGroupId(node.id)
            val idx = currentDocument.findLayerIndexInParent(node.id, pId)
            if (idx >= 0) Pair(node, idx) else null
        }
        if (layersWithIndices.size < 2) return null

        val firstPair = layersWithIndices.first()
        val primaryParentId = currentDocument.findParentGroupId(firstPair.first.id)
        val primaryIndex = firstPair.second

        val mergedId = UUID.randomUUID().toString()
        val mergedName = "${firstPair.first.name} Merged"
        val mergedLayer = LayerNode.RasterLayer(
            id = mergedId,
            name = mergedName,
            width = currentDocument.canvas.width,
            height = currentDocument.canvas.height,
            visible = true,
            opacity = 1.0f
        )

        val strokeMapping = onMergeStrokes?.invoke(targetIds, mergedId) ?: emptyMap()

        val cmd = MergeLayersCommand(
            sourceLayersWithIndices = layersWithIndices,
            parentGroupId = primaryParentId,
            mergedLayer = mergedLayer,
            insertIndex = primaryIndex,
            strokeIdToOriginalLayerId = strokeMapping
        )
        historyManager.execute(cmd)
        uiState.clearMultiSelection()
        uiState.selectLayer(mergedId)
        return mergedLayer
    }

    /**
     * Merges all visible layers into a single raster layer, preserving hidden layers intact.
     * Fully reversible via undo/redo.
     */
    fun mergeVisibleLayers(): LayerNode.RasterLayer? {
        val allFlat = currentDocument.allLayersFlat()
        val visibleNodes = allFlat.filter { it.visible && it !is LayerNode.LayerGroup }
        if (visibleNodes.isEmpty()) return null

        val hiddenNodes = allFlat.filter { !it.visible && it !is LayerNode.LayerGroup }

        val mergedId = UUID.randomUUID().toString()
        val mergedLayer = LayerNode.RasterLayer(
            id = mergedId,
            name = "Merged Visible",
            width = currentDocument.canvas.width,
            height = currentDocument.canvas.height,
            visible = true,
            opacity = 1.0f
        )

        val strokeMapping = onMergeStrokes?.invoke(visibleNodes.map { it.id }, mergedId) ?: emptyMap()

        val cmd = MergeVisibleCommand(
            originalRootGroup = currentDocument.rootGroup,
            originalSelectedLayerId = currentDocument.selectedLayerId,
            hiddenLayers = hiddenNodes,
            mergedLayer = mergedLayer,
            strokeIdToOriginalLayerId = strokeMapping
        )
        historyManager.execute(cmd)
        uiState.clearMultiSelection()
        uiState.selectLayer(mergedId)
        return mergedLayer
    }

    /**
     * Flattens the entire document down to a single background raster layer.
     *
     * INVARIANT: Flattening can occur ONLY through an explicit named command
     * with confirmation and test coverage. Silent flattening is strictly prohibited.
     *
     * @param confirmed Explicit user confirmation flag. Must be true.
     */
    fun flattenDocument(confirmed: Boolean = false): LayerNode.RasterLayer {
        if (!confirmed) {
            throw IllegalStateException(
                "Flattening requires explicit user confirmation. Silent flattening during save, import, analysis, or export is strictly prohibited."
            )
        }

        val allFlat = currentDocument.allLayersFlat()
        val flattenedId = UUID.randomUUID().toString()
        val flattenedLayer = LayerNode.RasterLayer(
            id = flattenedId,
            name = "Background",
            width = currentDocument.canvas.width,
            height = currentDocument.canvas.height,
            visible = true,
            opacity = 1.0f
        )

        val strokeMapping = onMergeStrokes?.invoke(allFlat.map { it.id }, flattenedId) ?: emptyMap()

        val cmd = FlattenDocumentCommand(
            originalRootGroup = currentDocument.rootGroup,
            originalSelectedLayerId = currentDocument.selectedLayerId,
            flattenedLayer = flattenedLayer,
            strokeIdToOriginalLayerId = strokeMapping
        )
        historyManager.execute(cmd)
        uiState.clearMultiSelection()
        uiState.selectLayer(flattenedId)
        return flattenedLayer
    }

    /**
     * Restores stroke layer IDs after undoing a merge/flatten operation.
     */
    fun restoreMergedStrokes(mapping: Map<String, String>) {
        onRestoreStrokes?.invoke(mapping)
    }

    /**
     * Re-associates stroke layer IDs after redoing a merge/flatten operation.
     */
    fun reapplyMergedStrokes(strokeIds: Collection<String>, mergedLayerId: String) {
        onReapplyStrokes?.invoke(strokeIds, mergedLayerId)
    }

    /**
     * Applies a linear or radial gradient to the specified raster layer with transactional undo/redo.
     */
    fun applyGradientToLayer(
        layerId: String,
        gradientConfig: com.artstudio.color.GradientConfig,
        pixelBuffer: IntArray? = null,
        selectionMask: ByteArray? = null
    ): Boolean {
        val layer = currentDocument.findLayerById(layerId) as? LayerNode.RasterLayer ?: return false
        val prePixels = pixelBuffer ?: IntArray(layer.width * layer.height)
        val postPixels = prePixels.clone()
        com.artstudio.color.GradientRenderer.renderGradient(
            dest = postPixels,
            width = layer.width,
            height = layer.height,
            config = gradientConfig,
            selectionMask = selectionMask
        )
        if (pixelBuffer != null) {
            System.arraycopy(postPixels, 0, pixelBuffer, 0, postPixels.size)
        }
        val command = com.artstudio.history.ApplyGradientCommand(
            layerId = layerId,
            preGradientPixels = prePixels,
            postGradientPixels = postPixels,
            width = layer.width,
            height = layer.height,
            gradientName = gradientConfig.name
        )
        historyManager.execute(command)
        return true
    }

    /**
     * Applies a flood/bucket fill to the specified raster layer with transactional undo/redo.
     * Supports targeting current layer or creating a new layer according to FillConfig.
     */
    fun applyFillToLayer(
        layerId: String,
        seedX: Int,
        seedY: Int,
        config: com.artstudio.fill.FillConfig,
        targetPixelBuffer: IntArray? = null,
        compositePixels: IntArray? = null,
        cancelToken: com.artstudio.fill.FillCancellationToken? = null
    ): com.artstudio.fill.FillResult? {
        val targetLayerId = if (config.targetLayerMode == com.artstudio.fill.FillTargetLayer.NEW_LAYER) {
            val newLayer = createLayer(name = "Fill Layer")
            newLayer.id
        } else {
            layerId
        }

        val layer = currentDocument.findLayerById(targetLayerId) as? LayerNode.RasterLayer ?: return null
        val w = layer.width
        val h = layer.height

        val targetBuf = targetPixelBuffer ?: IntArray(w * h)
        val samplePixels = when (config.sampleSource) {
            com.artstudio.fill.FillSampleSource.ALL_LAYERS -> compositePixels ?: targetBuf
            com.artstudio.fill.FillSampleSource.ACTIVE_LAYER -> targetBuf
        }

        val fillResult = com.artstudio.fill.FloodFillEngine.executeFill(
            sourcePixels = samplePixels,
            targetPixels = targetBuf,
            width = w,
            height = h,
            seedX = seedX,
            seedY = seedY,
            config = config,
            cancelToken = cancelToken
        ) ?: return null

        if (fillResult.success && fillResult.resultPixels != null) {
            val tileDeltas = com.artstudio.history.TileDeltaHelper.extractTileDeltas(
                prePixels = targetBuf,
                postPixels = fillResult.resultPixels,
                canvasWidth = w,
                canvasHeight = h
            )
            if (targetPixelBuffer != null) {
                System.arraycopy(fillResult.resultPixels, 0, targetPixelBuffer, 0, targetPixelBuffer.size)
            }
            val command = com.artstudio.history.FillCommand(
                layerId = targetLayerId,
                tileDeltas = tileDeltas,
                width = w,
                height = h,
                description = "Bucket Fill (${if (config.fillMode == com.artstudio.fill.FillMode.CONTIGUOUS) "Flood" else "Global"})"
            )
            historyManager.execute(command)
        }

        return fillResult
    }

    /**
     * Commits a completed pixel modification command (Smudge, Blur, Blend) to history,
     * updating the layer buffer and dispatching document state change.
     */
    fun applyPixelModifierCommand(
        command: com.artstudio.history.PixelModifierCommand,
        targetPixelBuffer: IntArray? = null
    ) {
        val layer = currentDocument.findLayerById(command.layerId) as? LayerNode.RasterLayer ?: return
        if (targetPixelBuffer != null) {
            command.restorePatch(targetPixelBuffer, layer.width, usePre = false)
        }
        historyManager.execute(command)
    }

    /**
     * Commits a completed image filter command to history,
     * blitting post-filter pixels into the active layer buffer.
     */
    fun applyFilterCommand(
        command: com.artstudio.history.ApplyFilterCommand,
        targetPixelBuffer: IntArray? = null
    ) {
        val layer = currentDocument.findLayerById(command.layerId) as? LayerNode.RasterLayer ?: return
        if (targetPixelBuffer != null) {
            command.restorePixels(targetPixelBuffer, usePre = false)
        }
        historyManager.execute(command)
    }

    /**
     * Adds a new non-destructive [LayerNode.AdjustmentLayer] into the document.
     */
    fun addAdjustmentLayer(
        adjustment: com.artstudio.adjustment.ColorAdjustment,
        name: String = "${adjustment.type.displayName} Layer",
        isClippingMask: Boolean = false
    ): LayerNode.AdjustmentLayer {
        val selectedIndex = getLayers().indexOfFirst { it.id == getSelectedLayerId() }
        val insertIndex = if (selectedIndex >= 0) selectedIndex + 1 else getLayers().size
        val adjLayer = LayerNode.AdjustmentLayer(
            name = name,
            isClippingMask = isClippingMask,
            adjustment = adjustment
        )
        val cmd = com.artstudio.history.AddAdjustmentLayerCommand(
            adjustmentLayer = adjLayer,
            index = insertIndex
        )
        historyManager.execute(cmd)
        return adjLayer
    }

    /**
     * Updates parameters on an existing [LayerNode.AdjustmentLayer].
     */
    fun updateAdjustmentLayer(
        layerId: String,
        newAdjustment: com.artstudio.adjustment.ColorAdjustment
    ) {
        val current = currentDocument.findLayerById(layerId) as? LayerNode.AdjustmentLayer ?: return
        val cmd = com.artstudio.history.UpdateAdjustmentCommand(
            layerId = layerId,
            oldAdjustment = current.adjustment,
            newAdjustment = newAdjustment
        )
        historyManager.execute(cmd)
    }

    /**
     * Explicitly rasterizes a [LayerNode.AdjustmentLayer] into a permanent [LayerNode.RasterLayer].
     */
    fun rasterizeAdjustmentLayer(
        layerId: String,
        width: Int = currentDocument.canvas.width,
        height: Int = currentDocument.canvas.height
    ): LayerNode.RasterLayer? {
        val source = currentDocument.findLayerById(layerId) as? LayerNode.AdjustmentLayer ?: return null
        val generatedRaster = LayerNode.RasterLayer(
            name = "${source.name} (Baked)",
            width = width,
            height = height,
            opacity = source.opacity,
            blendMode = source.blendMode,
            isClippingMask = source.isClippingMask,
            mask = source.mask
        )
        val cmd = com.artstudio.history.RasterizeAdjustmentLayerCommand(
            sourceAdjustmentLayer = source,
            generatedRasterLayer = generatedRaster
        )
        historyManager.execute(cmd)
        return generatedRaster
    }

    /**
     * Directly applies a [com.artstudio.adjustment.ColorAdjustment] to a target raster layer pixel buffer.
     */
    fun applyDirectAdjustment(
        layerId: String,
        adjustment: com.artstudio.adjustment.ColorAdjustment,
        pixelBuffer: IntArray,
        width: Int,
        height: Int,
        maskBuffer: IntArray? = null,
        maskInverted: Boolean = false
    ): com.artstudio.history.ApplyDirectAdjustmentCommand {
        val prePixels = pixelBuffer.clone()
        com.artstudio.adjustment.ColorAdjustmentEngine.applyAdjustmentInPlace(
            pixels = pixelBuffer,
            width = width,
            height = height,
            adjustment = adjustment,
            maskPixels = maskBuffer,
            maskInverted = maskInverted
        )
        val postPixels = pixelBuffer.clone()
        val cmd = com.artstudio.history.ApplyDirectAdjustmentCommand(
            targetLayerId = layerId,
            adjustment = adjustment,
            prePixels = prePixels,
            postPixels = postPixels,
            width = width,
            height = height
        )
        historyManager.execute(cmd)
        return cmd
    }

    /**
     * Imports a raster image as a new layer with undo/redo history tracking.
     */
    fun importRasterLayer(
        decodedImage: DecodedRasterImage,
        placement: ImportPlacementConfig = ImportPlacementConfig(),
        layerName: String? = null,
        parentGroupId: String? = null,
        index: Int = -1
    ): LayerNode.RasterLayer {
        val totalLayers = currentDocument.allLayersFlat().filterNot { it is LayerNode.LayerGroup }.size
        val maxAllowed = com.artstudio.core.DeviceMemoryPolicy.maxLayersForCanvas(currentDocument.canvas.width, currentDocument.canvas.height)
        if (totalLayers >= maxAllowed) {
            throw IllegalStateException(
                "Cannot import layer: Device memory policy limit reached ($maxAllowed layers max on ${com.artstudio.core.DeviceMemoryPolicy.currentTier.displayName})."
            )
        }
        val (_, layer) = RasterImageImporter.importAsLayer(
            document = currentDocument,
            decoded = decodedImage,
            placement = placement,
            layerName = layerName
        )
        val cmd = ImportRasterLayerCommand(
            importedLayer = layer,
            decodedImage = decodedImage,
            parentGroupId = parentGroupId,
            index = index,
            previousSelectedLayerId = currentDocument.selectedLayerId
        )
        historyManager.execute(cmd)
        uiState.selectLayer(layer.id)
        return layer
    }
}


