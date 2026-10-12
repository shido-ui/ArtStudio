package com.artstudio.document.model

import java.util.UUID

/**
 * Authoritative document model for Art Studio projects (.ffart).
 * Shared across manual artwork, AutoDraw reconstruction, and Colour Lab recipes.
 */
data class ArtDocument(
    val schemaVersion: String = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "1.0.0",
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Artwork",
    val canvas: CanvasConfig = CanvasConfig(),
    val rootGroup: LayerNode.LayerGroup = LayerNode.LayerGroup(
        name = "Root",
        children = listOf(
            LayerNode.RasterLayer(
                name = "Background",
                width = canvas.width,
                height = canvas.height
            )
        )
    ),
    val selectedLayerId: String? = null,
    val palettes: List<PaletteDefinition> = emptyList(),
    val gradientPresets: List<com.artstudio.color.GradientConfig> = emptyList(),
    val referenceImages: List<ReferenceImageConfig> = emptyList(),
    val animation: AnimationTimelineConfig? = null,
    val autoDrawProvenance: AutoDrawProvenance? = null,
    val comicPanels: List<com.artstudio.comic.ComicPanel> = emptyList(),
    val speechBubbles: List<com.artstudio.comic.SpeechBubble> = emptyList(),
    val screentones: List<com.artstudio.comic.ScreentoneConfig> = emptyList(),
    val comicPageGuides: com.artstudio.comic.ComicPageGuides? = null,
    val drawingSchedule: com.artstudio.autodraw.schedule.DrawingSchedule? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Resolves the active layer ID, falling back to top layer in root group if unselected.
     */
    val activeLayerId: String
        get() = (selectedLayerId?.takeIf { findLayerById(it) != null })
            ?: rootGroup.children.lastOrNull()?.id
            ?: ""

    /**
     * Retrieves the currently selected layer node.
     */
    fun getSelectedLayer(): LayerNode? {
        val targetId = activeLayerId
        return if (targetId.isNotEmpty()) findLayerById(targetId) else null
    }
    companion object {
        const val CURRENT_SCHEMA_VERSION = "2.0.0"
        const val LEGACY_SCHEMA_VERSION = "1.0.0"
    }

    /**
     * Helper to flat list all layer nodes in the document in depth-first traversal order.
     */
    fun allLayersFlat(): List<LayerNode> {
        val result = mutableListOf<LayerNode>()
        fun traverse(group: LayerNode.LayerGroup) {
            for (child in group.children) {
                result.add(child)
                if (child is LayerNode.LayerGroup) {
                    traverse(child)
                }
            }
        }
        traverse(rootGroup)
        return result
    }

    /**
     * Finds a layer by its stable UUID.
     */
    fun findLayerById(layerId: String): LayerNode? {
        return allLayersFlat().find { it.id == layerId }
    }
}
