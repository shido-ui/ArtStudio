package com.artstudio.selection

import android.graphics.Path
import android.graphics.RectF
import com.artstudio.document.model.LayerMask
import com.artstudio.history.EditCommand
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager

/**
 * Transactional Controller for Document-Space Selections.
 * Coordinates selection tools (Rectangle, Ellipse, Lasso, Polygon),
 * boolean combination modes (Replace, Add, Subtract, Intersect),
 * and precision mask modifications (Invert, Clear, Expand, Contract, Feather).
 */
class SelectionManager(
    val canvasWidth: Int,
    val canvasHeight: Int,
    var historyManager: HistoryManager? = null,
    var onSelectionChanged: ((SelectionMask) -> Unit)? = null
) {
    /**
     * Authoritative document-space selection mask.
     */
    var currentSelection: SelectionMask = SelectionMask(canvasWidth, canvasHeight)
        private set

    /**
     * Currently active selection tool.
     */
    var activeTool: SelectionToolType = SelectionToolType.RECTANGLE

    /**
     * Active combination mode when drawing new selection shapes.
     */
    var combineMode: SelectionCombineMode = SelectionCombineMode.REPLACE

    /**
     * Optional feathering radius in pixels to apply after selection commits.
     */
    var featherRadius: Float = 0f

    /**
     * Checks if there is an active non-empty selection.
     */
    val hasSelection: Boolean
        get() = !currentSelection.isEmpty

    val activeMask: SelectionMask?
        get() = if (hasSelection) currentSelection else null

    /**
     * Returns total count of selected pixels.
     */
    val selectedPixelCount: Int
        get() = currentSelection.selectedPixelCount

    /**
     * Bounding box of active selection in document coordinates.
     */
    val bounds: SelectionBounds
        get() = currentSelection.bounds

    fun getBoundsRectF(): RectF = bounds.toRectF()

    /**
     * Executes a selection mutation transaction, snapshotting before and after
     * to record undoable commands in [historyManager].
     */
    fun mutateSelection(description: String, block: (SelectionMask) -> Unit) {
        val oldCopy = currentSelection.clone()
        val workingCopy = currentSelection.clone()
        block(workingCopy)
        val hm = historyManager
        if (hm != null) {
            hm.execute(
                com.artstudio.history.SetSelectionCommand(
                    oldMaskBytes = oldCopy.buffer.copyOf(),
                    newMaskBytes = workingCopy.buffer.copyOf(),
                    width = canvasWidth,
                    height = canvasHeight,
                    selectionManager = this,
                    description = description
                )
            )
        } else {
            currentSelection = workingCopy
            notifyChanged()
        }
    }

    /**
     * Applies a selection shape in document coordinates according to [mode].
     */
    fun selectShape(
        shape: SelectionShape,
        mode: SelectionCombineMode = combineMode,
        feather: Float = featherRadius
    ) {
        mutateSelection("Select ${shape.javaClass.simpleName}") { mask ->
            mask.applyShape(shape, mode)
            if (feather >= SelectionLimits.MIN_FEATHER_RADIUS) {
                mask.feather(feather)
            }
        }
    }

    /**
     * Inverts the active selection.
     */
    fun invert() {
        mutateSelection("Invert Selection") { mask ->
            mask.invert()
        }
    }

    /**
     * Clears the active selection.
     */
    fun clear() {
        if (!currentSelection.isEmpty) {
            mutateSelection("Clear Selection") { mask ->
                mask.clear()
            }
        }
    }

    /**
     * Expands (dilates) the active selection by [radius] pixels.
     */
    fun expand(radius: Int) {
        if (!currentSelection.isEmpty) {
            mutateSelection("Expand Selection ($radius px)") { mask ->
                mask.expand(radius)
            }
        }
    }

    /**
     * Contracts (erodes) the active selection by [radius] pixels.
     */
    fun contract(radius: Int) {
        if (!currentSelection.isEmpty) {
            mutateSelection("Contract Selection ($radius px)") { mask ->
                mask.contract(radius)
            }
        }
    }

    /**
     * Feathers the active selection by [radius] pixels.
     */
    fun feather(radius: Float) {
        if (!currentSelection.isEmpty) {
            mutateSelection("Feather Selection ($radius px)") { mask ->
                mask.feather(radius)
            }
        }
    }

    /**
     * Replaces the current selection mask with a deep copy of [newMask].
     */
    fun setSelectionMask(newMask: SelectionMask) {
        currentSelection = newMask.clone()
        notifyChanged()
    }

    /**
     * Creates a reusable non-destructive [LayerMask] directly from the active selection.
     */
    fun createLayerMaskFromSelection(targetLayerId: String, layerManager: LayerManager): LayerMask? {
        if (currentSelection.isEmpty) return null
        val mask = currentSelection.toLayerMask()
        return layerManager.addLayerMask(targetLayerId, mask)
    }

    /**
     * Fills the active selection with [color] into an ARGB [targetPixels] buffer.
     */
    fun fillSelection(targetPixels: IntArray, color: Int, opacity: Float = 1.0f) {
        if (!currentSelection.isEmpty) {
            currentSelection.fill(targetPixels, color, opacity)
        }
    }

    /**
     * Fills the active selection with [color] into [targetPixels] recording an undoable [FillSelectionCommand].
     */
    fun fillSelectionUndoable(
        targetLayerId: String,
        targetPixels: IntArray,
        color: Int,
        opacity: Float = 1.0f
    ): com.artstudio.history.FillSelectionCommand? {
        if (currentSelection.isEmpty) return null
        val pre = targetPixels.copyOf()
        currentSelection.fill(targetPixels, color, opacity)
        val deltas = com.artstudio.history.TileDeltaHelper.extractTileDeltas(pre, targetPixels, canvasWidth, canvasHeight)
        val cmd = com.artstudio.history.FillSelectionCommand(
            layerId = targetLayerId,
            color = color,
            opacity = opacity,
            tileDeltas = deltas,
            width = canvasWidth,
            height = canvasHeight
        )
        historyManager?.execute(cmd)
        return cmd
    }

    /**
     * Returns the composite vector path representing the selection boundary.
     */
    fun getSelectionPath(): Path {
        return currentSelection.toPath()
    }

    private fun notifyChanged() {
        onSelectionChanged?.invoke(currentSelection)
    }
}
