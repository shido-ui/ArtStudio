package com.artstudio.history

import com.artstudio.adjustment.ColorAdjustment
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.withUpdatedLayer
import java.util.UUID

/**
 * Reversible command to add a new [LayerNode.AdjustmentLayer] into the document hierarchy.
 */
data class AddAdjustmentLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val adjustmentLayer: LayerNode.AdjustmentLayer,
    val index: Int = -1,
    override val description: String = "Add Adjustment Layer '${adjustmentLayer.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.toMutableList()
        if (index in 0..updatedChildren.size) {
            updatedChildren.add(index, adjustmentLayer)
        } else {
            updatedChildren.add(adjustmentLayer)
        }
        val updatedRoot = root.copy(children = updatedChildren)
        return document.copy(
            rootGroup = updatedRoot,
            selectedLayerId = adjustmentLayer.id,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.filterNot { it.id == adjustmentLayer.id }
        val updatedRoot = root.copy(children = updatedChildren)
        val newSelection = if (document.selectedLayerId == adjustmentLayer.id) {
            updatedChildren.lastOrNull()?.id
        } else {
            document.selectedLayerId
        }
        return document.copy(
            rootGroup = updatedRoot,
            selectedLayerId = newSelection,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command to update non-destructive adjustment parameters on an existing [LayerNode.AdjustmentLayer].
 */
data class UpdateAdjustmentCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val oldAdjustment: ColorAdjustment,
    val newAdjustment: ColorAdjustment,
    override val description: String = "Update Adjustment '${newAdjustment.type.displayName}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            if (node is LayerNode.AdjustmentLayer) {
                node.copy(adjustment = newAdjustment)
            } else node
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            if (node is LayerNode.AdjustmentLayer) {
                node.copy(adjustment = oldAdjustment)
            } else node
        }
    }
}

/**
 * Explicit command to rasterize a non-destructive [LayerNode.AdjustmentLayer] into a permanent [LayerNode.RasterLayer].
 * Preserves the source adjustment layer so undo restores editable parametric adjustment state with zero loss.
 */
data class RasterizeAdjustmentLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val sourceAdjustmentLayer: LayerNode.AdjustmentLayer,
    val generatedRasterLayer: LayerNode.RasterLayer,
    override val description: String = "Rasterize Adjustment Layer '${sourceAdjustmentLayer.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        (generatedRasterLayer.width.toLong() * generatedRasterLayer.height * 4) + 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.map { child ->
            if (child.id == sourceAdjustmentLayer.id) generatedRasterLayer else child
        }
        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            selectedLayerId = generatedRasterLayer.id,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.map { child ->
            if (child.id == generatedRasterLayer.id) sourceAdjustmentLayer else child
        }
        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            selectedLayerId = sourceAdjustmentLayer.id,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Explicit command to apply a [ColorAdjustment] directly to a raster layer's pixel buffer.
 * Bounded memory: captures prePixels and postPixels for 100% reversible undo/redo.
 */
data class ApplyDirectAdjustmentCommand(
    override val id: String = UUID.randomUUID().toString(),
    val targetLayerId: String,
    val adjustment: ColorAdjustment,
    val prePixels: IntArray,
    val postPixels: IntArray,
    val width: Int,
    val height: Int,
    override val description: String = "Apply Adjustment '${adjustment.type.displayName}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        (width.toLong() * height * 4L * 2L) + 512L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.copy(updatedAt = System.currentTimeMillis())
    }

    /**
     * Restores pixel buffers directly into target raster array.
     */
    fun restorePixels(target: IntArray, usePre: Boolean) {
        val src = if (usePre) prePixels else postPixels
        val count = minOf(target.size, src.size)
        System.arraycopy(src, 0, target, 0, count)
    }
}
