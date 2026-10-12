package com.artstudio.history

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.withUpdatedLayer
import com.artstudio.text.FontEngine
import com.artstudio.text.TextAlignment
import com.artstudio.text.TextObject
import com.artstudio.vector.VectorObject
import java.util.UUID

/**
 * Reversible command to add a new [LayerNode.TextLayer] to the document hierarchy.
 */
data class AddTextLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val textLayer: LayerNode.TextLayer,
    val parentGroupId: String? = null,
    val index: Int = -1,
    override val description: String = "Add Text Layer '${textLayer.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.toMutableList()
        if (index in 0..updatedChildren.size) {
            updatedChildren.add(index, textLayer)
        } else {
            updatedChildren.add(textLayer)
        }
        val updatedRoot = root.copy(children = updatedChildren)
        return document.copy(
            rootGroup = updatedRoot,
            selectedLayerId = textLayer.id,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun revert(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.filterNot { it.id == textLayer.id }
        val updatedRoot = root.copy(children = updatedChildren)
        val newSelection = if (document.selectedLayerId == textLayer.id) {
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
 * Reversible command to update a [TextObject] within an existing [LayerNode.TextLayer].
 */
data class UpdateTextObjectCommand(
    override val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val oldTextObject: TextObject,
    val newTextObject: TextObject,
    override val description: String = "Edit Text",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            if (node is LayerNode.TextLayer) {
                node.copy(textObject = newTextObject, name = newTextObject.text.take(24))
            } else node
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(layerId) { node ->
            if (node is LayerNode.TextLayer) {
                node.copy(textObject = oldTextObject, name = oldTextObject.text.take(24))
            } else node
        }
    }
}

/**
 * Reversible explicit command to rasterize a [LayerNode.TextLayer] into a [LayerNode.RasterLayer].
 *
 * Preserves the source text layer in memory so that undo restores 100% editable vector typography.
 */
data class RasterizeTextLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val sourceTextLayer: LayerNode.TextLayer,
    val generatedRasterLayer: LayerNode.RasterLayer,
    override val description: String = "Rasterize Text Layer '${sourceTextLayer.name}'",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        (generatedRasterLayer.width.toLong() * generatedRasterLayer.height * 4) + 1024L

    override fun apply(document: ArtDocument): ArtDocument {
        val root = document.rootGroup
        val updatedChildren = root.children.map { child ->
            if (child.id == sourceTextLayer.id) generatedRasterLayer else child
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
            if (child.id == generatedRasterLayer.id) sourceTextLayer else child
        }
        return document.copy(
            rootGroup = root.copy(children = updatedChildren),
            selectedLayerId = sourceTextLayer.id,
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        /**
         * Factory method that creates a [RasterizeTextLayerCommand] by rendering the text layer
         * onto a bitmap buffer with native typography metrics.
         */
        fun create(
            document: ArtDocument,
            textLayerId: String,
            context: android.content.Context? = null
        ): RasterizeTextLayerCommand {
            val textLayer = document.findLayerById(textLayerId) as? LayerNode.TextLayer
                ?: throw IllegalArgumentException("Layer $textLayerId is not a TextLayer")

            val canvasW = document.canvas.width
            val canvasH = document.canvas.height
            val bitmap = runCatching {
                Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            }.getOrNull()

            if (bitmap != null) {
                val canvas = Canvas(bitmap)
                val textObj = textLayer.textObject
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val colorInt = textObj.textColor.toInt()
                    val alpha = (Color.alpha(colorInt) * textLayer.opacity).toInt().coerceIn(0, 255)
                    color = (colorInt and 0x00FFFFFF) or (alpha shl 24)
                    textSize = textObj.fontSize
                    typeface = FontEngine.createTypeface(context, textObj.fontFamily, textObj.fontStyle)
                    textAlign = when (textObj.alignment) {
                        TextAlignment.LEFT -> Paint.Align.LEFT
                        TextAlignment.CENTER -> Paint.Align.CENTER
                        TextAlignment.RIGHT -> Paint.Align.RIGHT
                    }
                    isUnderlineText = textObj.isUnderline
                    isStrikeThruText = textObj.isStrikeThrough
                }

                val lines = textObj.lines()
                val lineHeight = textObj.fontSize * textObj.lineSpacingMultiplier
                var currentY = textObj.y + textObj.fontSize

                for (line in lines) {
                    canvas.drawText(line, textObj.x, currentY, paint)
                    currentY += lineHeight
                }
                bitmap.recycle()
            }

            val rasterLayer = LayerNode.RasterLayer(
                id = UUID.randomUUID().toString(),
                name = "${textLayer.name} (Rasterized)",
                visible = textLayer.visible,
                opacity = textLayer.opacity,
                blendMode = textLayer.blendMode,
                transform = textLayer.transform,
                isLocked = textLayer.isLocked,
                isClippingMask = textLayer.isClippingMask,
                isAlphaLocked = textLayer.isAlphaLocked,
                mask = textLayer.mask,
                width = canvasW,
                height = canvasH
            )

            return RasterizeTextLayerCommand(
                sourceTextLayer = textLayer,
                generatedRasterLayer = rasterLayer
            )
        }
    }
}
