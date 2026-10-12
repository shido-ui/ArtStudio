package com.artstudio.history

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.withUpdatedLayer
import java.util.UUID

/**
 * Reversible pixel modification command for tools that mutate existing layer pixels
 * (Smudge, Blur, Blend).
 *
 * Stores bounded tile-level deltas rather than entire canvas frames,
 * strictly bounding memory usage proportional to the touched area.
 */
data class PixelModifierCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val patchX: Int,
    val patchY: Int,
    val patchWidth: Int,
    val patchHeight: Int,
    val tileDeltas: List<TileDelta>,
    override val description: String = "Pixel Operation",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    constructor(
        id: String = UUID.randomUUID().toString(),
        layerId: String,
        patchX: Int,
        patchY: Int,
        patchWidth: Int,
        patchHeight: Int,
        prePixels: IntArray,
        postPixels: IntArray,
        description: String = "Pixel Operation",
        timestamp: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        layerId = layerId,
        patchX = patchX,
        patchY = patchY,
        patchWidth = patchWidth,
        patchHeight = patchHeight,
        tileDeltas = listOf(
            TileDelta(
                col = patchX / TileDeltaHelper.DEFAULT_TILE_SIZE,
                row = patchY / TileDeltaHelper.DEFAULT_TILE_SIZE,
                x = patchX,
                y = patchY,
                width = patchWidth,
                height = patchHeight,
                prePixels = prePixels,
                postPixels = postPixels
            )
        ),
        description = description,
        timestamp = timestamp
    )

    override val estimatedMemoryBytes: Long
        get() = 128L + tileDeltas.sumOf { it.estimatedMemoryBytes }

    val prePixels: IntArray
        get() = tileDeltas.firstOrNull()?.prePixels ?: IntArray(patchWidth * patchHeight)

    val postPixels: IntArray
        get() = tileDeltas.firstOrNull()?.postPixels ?: IntArray(patchWidth * patchHeight)

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy()
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            when (node) {
                is LayerNode.RasterLayer -> node.copy()
                else -> node
            }
        }.copy(updatedAt = System.currentTimeMillis())
    }

    /**
     * Blits either the pre-operation or post-operation patch/tile deltas directly into a target pixel buffer.
     * Used by runtime layer state managers during undo and redo.
     */
    fun restorePatch(targetPixels: IntArray, canvasWidth: Int, usePre: Boolean) {
        if (tileDeltas.isNotEmpty()) {
            for (delta in tileDeltas) {
                delta.applyTo(targetPixels, canvasWidth, usePre)
            }
        } else if (patchWidth > 0 && patchHeight > 0) {
            val src = if (usePre) prePixels else postPixels
            for (row in 0 until patchHeight) {
                val srcOffset = row * patchWidth
                val dstOffset = (patchY + row) * canvasWidth + patchX
                if (dstOffset >= 0 && dstOffset + patchWidth <= targetPixels.size && srcOffset + patchWidth <= src.size) {
                    System.arraycopy(src, srcOffset, targetPixels, dstOffset, patchWidth)
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PixelModifierCommand
        if (id != other.id) return false
        if (layerId != other.layerId) return false
        if (patchX != other.patchX) return false
        if (patchY != other.patchY) return false
        if (patchWidth != other.patchWidth) return false
        if (patchHeight != other.patchHeight) return false
        if (tileDeltas != other.tileDeltas) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + layerId.hashCode()
        result = 31 * result + patchX
        result = 31 * result + patchY
        result = 31 * result + patchWidth
        result = 31 * result + patchHeight
        result = 31 * result + tileDeltas.hashCode()
        result = 31 * result + description.hashCode()
        return result
    }
}
