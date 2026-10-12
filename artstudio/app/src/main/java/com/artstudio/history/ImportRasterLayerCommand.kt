package com.artstudio.history

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.withAddedLayer
import com.artstudio.document.model.withRemovedLayer
import com.artstudio.importer.DecodedRasterImage
import java.util.UUID

/**
 * Reversible transactional command for importing a raster layer into the layer tree.
 * Preserves complete image pixels and metadata for bit-exact undo and redo.
 */
data class ImportRasterLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val importedLayer: LayerNode.RasterLayer,
    val decodedImage: DecodedRasterImage,
    val parentGroupId: String? = null,
    val index: Int = -1,
    val previousSelectedLayerId: String? = null,
    override val description: String = "Import Layer '${importedLayer.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        512L + (decodedImage.width.toLong() * decodedImage.height * 4L)

    override fun apply(document: ArtDocument): ArtDocument {
        val updated = document.withAddedLayer(importedLayer, parentGroupId, index)
        return updated.copy(selectedLayerId = importedLayer.id)
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val updated = document.withRemovedLayer(importedLayer.id)
        val fallbackSelection = previousSelectedLayerId ?: updated.rootGroup.children.lastOrNull()?.id
        return updated.copy(selectedLayerId = fallbackSelection)
    }
}
