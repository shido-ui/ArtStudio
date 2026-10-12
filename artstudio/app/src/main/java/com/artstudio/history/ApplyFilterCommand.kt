package com.artstudio.history

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.withUpdatedLayer
import java.util.UUID

/**
 * Reversible edit command for committed image filter operations on a raster layer.
 *
 * Stores tile-level deltas instead of full pre/post canvas arrays, strictly bounding
 * memory usage to modified tiles.
 */
data class ApplyFilterCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val filterName: String,
    val width: Int,
    val height: Int,
    val tileDeltas: List<TileDelta>,
    override val description: String = "Apply Filter: $filterName",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    constructor(
        id: String = UUID.randomUUID().toString(),
        layerId: String,
        filterName: String,
        width: Int,
        height: Int,
        prePixels: IntArray,
        postPixels: IntArray,
        description: String = "Apply Filter: $filterName",
        timestamp: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        layerId = layerId,
        filterName = filterName,
        width = width,
        height = height,
        tileDeltas = TileDeltaHelper.extractTileDeltas(prePixels, postPixels, width, height),
        description = description,
        timestamp = timestamp
    )

    override val estimatedMemoryBytes: Long
        get() = 128L + tileDeltas.sumOf { it.estimatedMemoryBytes }

    val prePixels: IntArray
        get() {
            val arr = IntArray(width * height)
            for (d in tileDeltas) d.applyTo(arr, width, usePre = true)
            return arr
        }

    val postPixels: IntArray
        get() {
            val arr = IntArray(width * height)
            for (d in tileDeltas) d.applyTo(arr, width, usePre = false)
            return arr
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

    /**
     * Blits either the pre-operation or post-operation pixels into a target pixel buffer.
     * Used by runtime layer state managers during undo and redo.
     */
    fun restorePixels(targetPixels: IntArray, usePre: Boolean) {
        for (delta in tileDeltas) {
            delta.applyTo(targetPixels, width, usePre)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApplyFilterCommand
        if (id != other.id) return false
        if (layerId != other.layerId) return false
        if (filterName != other.filterName) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (tileDeltas != other.tileDeltas) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + layerId.hashCode()
        result = 31 * result + filterName.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + tileDeltas.hashCode()
        result = 31 * result + description.hashCode()
        return result
    }
}
