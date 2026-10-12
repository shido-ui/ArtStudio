package com.artstudio.history

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.artstudio.document.model.*
import com.artstudio.vector.*
import java.util.UUID

/**
 * Reversible command to add a [VectorObject] to a [LayerNode.VectorPathLayer].
 */
data class AddVectorObjectCommand(
    override val id: String = UUID.randomUUID().toString(),
    val targetLayerId: String,
    val vectorObject: VectorObject,
    override val description: String = "Add Vector Object",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L + (vectorObject.anchors.size * 32L)

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(targetLayerId) { node ->
            if (node is LayerNode.VectorPathLayer) {
                node.copy(vectorObjects = node.vectorObjects + vectorObject)
            } else {
                node
            }
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(targetLayerId) { node ->
            if (node is LayerNode.VectorPathLayer) {
                node.copy(vectorObjects = node.vectorObjects.filterNot { it.id == vectorObject.id })
            } else {
                node
            }
        }
    }
}

/**
 * Reversible command to update a [VectorObject] within a [LayerNode.VectorPathLayer].
 */
data class UpdateVectorObjectCommand(
    override val id: String = UUID.randomUUID().toString(),
    val targetLayerId: String,
    val previousObject: VectorObject,
    val newObject: VectorObject,
    override val description: String = "Modify Vector Object",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 512L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(targetLayerId) { node ->
            if (node is LayerNode.VectorPathLayer) {
                val updatedList = node.vectorObjects.map { if (it.id == previousObject.id) newObject else it }
                node.copy(vectorObjects = updatedList)
            } else {
                node
            }
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(targetLayerId) { node ->
            if (node is LayerNode.VectorPathLayer) {
                val revertedList = node.vectorObjects.map { if (it.id == newObject.id) previousObject else it }
                node.copy(vectorObjects = revertedList)
            } else {
                node
            }
        }
    }
}

/**
 * Reversible command to delete a [VectorObject] from a [LayerNode.VectorPathLayer].
 */
data class DeleteVectorObjectCommand(
    override val id: String = UUID.randomUUID().toString(),
    val targetLayerId: String,
    val deletedObject: VectorObject,
    val originalIndex: Int,
    override val description: String = "Delete Vector Object",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long = 256L

    override fun apply(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(targetLayerId) { node ->
            if (node is LayerNode.VectorPathLayer) {
                node.copy(vectorObjects = node.vectorObjects.filterNot { it.id == deletedObject.id })
            } else {
                node
            }
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return document.withUpdatedLayer(targetLayerId) { node ->
            if (node is LayerNode.VectorPathLayer) {
                val mutable = node.vectorObjects.toMutableList()
                val insertAt = originalIndex.coerceIn(0, mutable.size)
                mutable.add(insertAt, deletedObject)
                node.copy(vectorObjects = mutable)
            } else {
                node
            }
        }
    }
}

/**
 * Explicit, non-destructive rasterization command converting vector geometry to raster pixels.
 *
 * Guarantees zero unrecoverable pixel loss: the original vector layer and its mathematical
 * contours are preserved in memory during session history, enabling 100% bit-exact undo/redo.
 */
data class RasterizeVectorLayerCommand(
    override val id: String = UUID.randomUUID().toString(),
    val sourceVectorLayer: LayerNode.VectorPathLayer,
    val rasterizedLayer: LayerNode.RasterLayer,
    val parentGroupId: String? = null,
    val originalIndex: Int = 0,
    val createNewLayer: Boolean = false,
    val rasterizedBitmap: Bitmap? = null,
    override val description: String = "Rasterize Vector Layer",
    override val timestamp: Long = System.currentTimeMillis()
) : EditCommand {

    override val estimatedMemoryBytes: Long =
        256L + (rasterizedLayer.width * rasterizedLayer.height * 4L)

    override fun apply(document: ArtDocument): ArtDocument {
        return if (createNewLayer) {
            // Keep vector layer, insert new raster layer directly above it
            document.withAddedLayer(rasterizedLayer, parentGroupId, originalIndex + 1)
                .copy(selectedLayerId = rasterizedLayer.id)
        } else {
            // Replace vector layer with raster layer
            document.withRemovedLayer(sourceVectorLayer.id)
                .withAddedLayer(rasterizedLayer, parentGroupId, originalIndex)
                .copy(selectedLayerId = rasterizedLayer.id)
        }
    }

    override fun revert(document: ArtDocument): ArtDocument {
        return if (createNewLayer) {
            // Remove newly inserted raster layer
            document.withRemovedLayer(rasterizedLayer.id)
                .copy(selectedLayerId = sourceVectorLayer.id)
        } else {
            // Remove raster layer and reinsert original vector layer
            document.withRemovedLayer(rasterizedLayer.id)
                .withAddedLayer(sourceVectorLayer, parentGroupId, originalIndex)
                .copy(selectedLayerId = sourceVectorLayer.id)
        }
    }

    companion object {
        fun create(
            document: ArtDocument,
            vectorLayerId: String,
            createNewLayer: Boolean = false
        ): RasterizeVectorLayerCommand {
            val node = document.findLayerById(vectorLayerId) as? LayerNode.VectorPathLayer
                ?: throw IllegalArgumentException("Layer $vectorLayerId is not a VectorPathLayer")

            val parentId = document.findParentGroupId(vectorLayerId)
            val parentGroup = (if (parentId != null) document.findLayerById(parentId) as? LayerNode.LayerGroup else null) ?: document.rootGroup
            val index = parentGroup.children.indexOfFirst { it.id == vectorLayerId }.coerceAtLeast(0)

            val w = document.canvas.width.coerceAtLeast(1)
            val h = document.canvas.height.coerceAtLeast(1)
            val bitmap = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (t: Throwable) {
                android.util.Log.w("RasterizeVector", "Could not allocate rasterization bitmap: ${t.message}")
                null
            }

            if (bitmap != null) {
                try {
                    val canvas = Canvas(bitmap)
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

                    val objects = node.allVectorObjects()
                    for (obj in objects) {
                        val path = VectorGeometryEngine.toAndroidPath(obj)
                        if (obj.style.fillColor != null) {
                            val baseColor = obj.style.fillColor.toInt()
                            val baseAlpha = Color.alpha(baseColor)
                            val finalAlpha = (baseAlpha * obj.style.opacity).toInt().coerceIn(0, 255)
                            fillPaint.color = (baseColor and 0x00FFFFFF) or (finalAlpha shl 24)
                            canvas.drawPath(path, fillPaint)
                        }
                        if (obj.style.strokeColor != null && obj.style.strokeWidth > 0f) {
                            val baseColor = obj.style.strokeColor.toInt()
                            val baseAlpha = Color.alpha(baseColor)
                            val finalAlpha = (baseAlpha * obj.style.opacity).toInt().coerceIn(0, 255)
                            strokePaint.color = (baseColor and 0x00FFFFFF) or (finalAlpha shl 24)
                            strokePaint.strokeWidth = obj.style.strokeWidth
                            strokePaint.strokeCap = when (obj.style.strokeCap) {
                                StrokeCap.BUTT -> Paint.Cap.BUTT
                                StrokeCap.ROUND -> Paint.Cap.ROUND
                                StrokeCap.SQUARE -> Paint.Cap.SQUARE
                            }
                            strokePaint.strokeJoin = when (obj.style.strokeJoin) {
                                StrokeJoin.MITER -> Paint.Join.MITER
                                StrokeJoin.ROUND -> Paint.Join.ROUND
                                StrokeJoin.BEVEL -> Paint.Join.BEVEL
                            }
                            strokePaint.strokeMiter = obj.style.strokeMiterLimit
                            canvas.drawPath(path, strokePaint)
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("RasterizeVector", "Vector drawing error during rasterization: ${t.message}")
                }
            }

            val rasterLayer = LayerNode.RasterLayer(
                id = if (createNewLayer) UUID.randomUUID().toString() else node.id,
                name = if (createNewLayer) "${node.name} (Rasterized)" else node.name,
                visible = node.visible,
                opacity = node.opacity,
                blendMode = node.blendMode,
                transform = node.transform,
                isLocked = node.isLocked,
                isClippingMask = node.isClippingMask,
                isAlphaLocked = node.isAlphaLocked,
                mask = node.mask,
                assetPath = "layers/rasterized_${node.id}.png",
                width = w,
                height = h
            )

            return RasterizeVectorLayerCommand(
                sourceVectorLayer = node,
                rasterizedLayer = rasterLayer,
                parentGroupId = parentId,
                originalIndex = index,
                createNewLayer = createNewLayer,
                rasterizedBitmap = bitmap
            )
        }
    }
}
