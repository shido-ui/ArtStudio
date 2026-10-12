package com.artstudio.history

import com.artstudio.comic.ComicGeometryEngine
import com.artstudio.comic.ComicPanel
import com.artstudio.comic.ScreentoneConfig
import com.artstudio.comic.SpeechBubble
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import java.util.UUID

/**
 * Reversible command to add a new [ComicPanel] to the document.
 */
data class AddComicPanelCommand(
    val panel: ComicPanel,
    val createVectorLayer: Boolean = true,
    override val id: String = UUID.randomUUID().toString(),
    override val description: String = "Add Comic Panel '${panel.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        val updatedPanels = document.comicPanels + panel
        var updatedDoc = document.copy(
            comicPanels = updatedPanels,
            updatedAt = System.currentTimeMillis()
        )

        if (createVectorLayer) {
            val vecObj = ComicGeometryEngine.panelToVectorObject(panel)
            val layer = LayerNode.VectorPathLayer(
                id = "layer_panel_${panel.id.take(8)}",
                name = panel.name,
                vectorObjects = listOf(vecObj)
            )
            updatedDoc = updatedDoc.copy(
                rootGroup = updatedDoc.rootGroup.copy(
                    children = updatedDoc.rootGroup.children + layer
                ),
                selectedLayerId = layer.id
            )
        }

        return updatedDoc
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val updatedPanels = document.comicPanels.filter { it.id != panel.id }
        val targetLayerId = "layer_panel_${panel.id.take(8)}"
        val updatedChildren = document.rootGroup.children.filter { it.id != targetLayerId }

        return document.copy(
            comicPanels = updatedPanels,
            rootGroup = document.rootGroup.copy(children = updatedChildren),
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command that splits an existing comic panel into two child panels.
 */
data class SplitComicPanelCommand(
    val originalPanel: ComicPanel,
    val childPanel1: ComicPanel,
    val childPanel2: ComicPanel,
    override val id: String = UUID.randomUUID().toString(),
    override val description: String = "Split Panel '${originalPanel.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        val filtered = document.comicPanels.filter { it.id != originalPanel.id }
        return document.copy(
            comicPanels = filtered + childPanel1 + childPanel2,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val filtered = document.comicPanels.filter { it.id != childPanel1.id && it.id != childPanel2.id }
        return document.copy(
            comicPanels = filtered + originalPanel,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command to add a [SpeechBubble] with optional vector frame and text layer.
 */
data class AddSpeechBubbleCommand(
    val bubble: SpeechBubble,
    val createVectorAndTextLayers: Boolean = true,
    override val id: String = UUID.randomUUID().toString(),
    override val description: String = "Add Speech Bubble",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        val updatedBubbles = document.speechBubbles + bubble
        var updatedDoc = document.copy(
            speechBubbles = updatedBubbles,
            updatedAt = System.currentTimeMillis()
        )

        if (createVectorAndTextLayers) {
            val vecObj = ComicGeometryEngine.createBubbleVectorObject(bubble)
            val auxDots = ComicGeometryEngine.generateThoughtDots(bubble)
            val bubbleLayer = LayerNode.VectorPathLayer(
                id = "layer_bubble_${bubble.id.take(8)}",
                name = "${bubble.bubbleType.displayName} Frame",
                vectorObjects = listOf(vecObj) + auxDots
            )

            var newChildren = updatedDoc.rootGroup.children + bubbleLayer

            if (bubble.text.isNotBlank()) {
                val textObj = ComicGeometryEngine.generateBubbleTextObject(bubble)
                val textLayer = LayerNode.TextLayer(
                    id = "layer_bubble_txt_${bubble.id.take(8)}",
                    name = "Bubble Text",
                    textObject = textObj
                )
                newChildren = newChildren + textLayer
            }

            updatedDoc = updatedDoc.copy(
                rootGroup = updatedDoc.rootGroup.copy(children = newChildren),
                selectedLayerId = bubbleLayer.id
            )
        }

        return updatedDoc
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val updatedBubbles = document.speechBubbles.filter { it.id != bubble.id }
        val bubbleLayerId = "layer_bubble_${bubble.id.take(8)}"
        val textLayerId = "layer_bubble_txt_${bubble.id.take(8)}"
        val updatedChildren = document.rootGroup.children.filter {
            it.id != bubbleLayerId && it.id != textLayerId
        }

        return document.copy(
            speechBubbles = updatedBubbles,
            rootGroup = document.rootGroup.copy(children = updatedChildren),
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command to update a [SpeechBubble]'s geometry, styling, or dialogue text.
 */
data class UpdateSpeechBubbleCommand(
    val oldBubble: SpeechBubble,
    val newBubble: SpeechBubble,
    override val id: String = UUID.randomUUID().toString(),
    override val description: String = "Edit Speech Bubble",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        val updated = document.speechBubbles.map { if (it.id == oldBubble.id) newBubble else it }
        return document.copy(
            speechBubbles = updated,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val updated = document.speechBubbles.map { if (it.id == newBubble.id) oldBubble else it }
        return document.copy(
            speechBubbles = updated,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Reversible command to add a [ScreentoneConfig] procedural fill preset.
 */
data class AddScreentoneCommand(
    val screentone: ScreentoneConfig,
    override val id: String = UUID.randomUUID().toString(),
    override val description: String = "Add Screentone '${screentone.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.copy(
            screentones = document.screentones + screentone,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.copy(
            screentones = document.screentones.filter { it.id != screentone.id },
            updatedAt = System.currentTimeMillis()
        )
    }
}
