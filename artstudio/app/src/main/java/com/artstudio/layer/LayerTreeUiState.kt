package com.artstudio.layer

/**
 * Encapsulates transient UI presentation state for the layer hierarchy.
 * Tracks expanded/collapsed group UI folding and multi-selection, strictly decoupled
 * from authoritative [com.artstudio.document.model.ArtDocument] semantics so UI folding
 * toggles never dirty the document, mutate document hash, or generate undo command history.
 */
class LayerTreeUiState(
    initialExpandedGroups: Set<String> = emptySet(),
    initialMultiSelection: Set<String> = emptySet()
) {
    private val _expandedGroupIds = mutableSetOf<String>().apply { addAll(initialExpandedGroups) }
    val expandedGroupIds: Set<String>
        get() = _expandedGroupIds

    private val _selectedLayerIds = mutableSetOf<String>().apply { addAll(initialMultiSelection) }
    val selectedLayerIds: Set<String>
        get() = _selectedLayerIds

    /**
     * Checks if group is expanded in the UI tree.
     */
    fun isGroupExpanded(groupId: String): Boolean = _expandedGroupIds.contains(groupId)

    /**
     * Toggles expansion state of a group. Returns new expanded state.
     */
    fun toggleGroupExpanded(groupId: String): Boolean {
        return if (_expandedGroupIds.contains(groupId)) {
            _expandedGroupIds.remove(groupId)
            false
        } else {
            _expandedGroupIds.add(groupId)
            true
        }
    }

    /**
     * Expands group in UI.
     */
    fun expandGroup(groupId: String) {
        _expandedGroupIds.add(groupId)
    }

    /**
     * Collapses group in UI.
     */
    fun collapseGroup(groupId: String) {
        _expandedGroupIds.remove(groupId)
    }

    /**
     * Checks if layer is currently selected in multi-selection.
     */
    fun isLayerMultiSelected(layerId: String): Boolean = _selectedLayerIds.contains(layerId)

    /**
     * Selects single layer, optionally clearing prior multi-selection.
     */
    fun selectLayer(layerId: String, clearPrevious: Boolean = true) {
        if (clearPrevious) {
            _selectedLayerIds.clear()
        }
        _selectedLayerIds.add(layerId)
    }

    /**
     * Toggles a layer in multi-selection.
     */
    fun toggleMultiSelect(layerId: String) {
        if (_selectedLayerIds.contains(layerId)) {
            _selectedLayerIds.remove(layerId)
        } else {
            _selectedLayerIds.add(layerId)
        }
    }

    /**
     * Sets multiple selected layer IDs.
     */
    fun setMultiSelection(layerIds: Collection<String>) {
        _selectedLayerIds.clear()
        _selectedLayerIds.addAll(layerIds)
    }

    /**
     * Active editing target for the currently selected layer.
     */
    var activeEditTarget: LayerEditTarget = LayerEditTarget.CONTENT
        private set

    fun setEditTarget(target: LayerEditTarget) {
        activeEditTarget = target
    }

    fun isEditingMask(): Boolean = activeEditTarget == LayerEditTarget.MASK

    /**
     * Clears multi-selection.
     */
    fun clearMultiSelection() {
        _selectedLayerIds.clear()
    }

    /**
     * Creates an independent deep snapshot of this UI state.
     */
    fun snapshot(): LayerTreeUiState {
        val s = LayerTreeUiState(
            initialExpandedGroups = _expandedGroupIds.toSet(),
            initialMultiSelection = _selectedLayerIds.toSet()
        )
        s.setEditTarget(activeEditTarget)
        return s
    }
}

/**
 * Declares whether brush strokes and editing actions target layer pixel content
 * or the layer's grayscale transparency mask.
 */
enum class LayerEditTarget {
    CONTENT,
    MASK
}

