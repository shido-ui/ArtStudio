package com.artstudio.document.model

import java.util.UUID

/**
 * Editable grayscale raster layer mask that non-destructively conceals or reveals layer pixels.
 *
 * @param id Unique persistent identifier for the mask
 * @param enabled Whether the mask is actively applied during visual rendering
 * @param inverted Whether black/white mask values are inverted (black reveals, white conceals)
 * @param assetPath Optional path to stored grayscale mask raster asset in package
 * @param width Mask canvas width
 * @param height Mask canvas height
 * @param transform Independent affine transform applied to the mask
 * @param linkTransformWithLayer If true, transforming the layer also transforms the mask
 */
data class LayerMask(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val inverted: Boolean = false,
    val assetPath: String? = null,
    val width: Int = 2048,
    val height: Int = 2048,
    val transform: LayerTransform = LayerTransform(),
    val linkTransformWithLayer: Boolean = true
) {
    init {
        require(width > 0) { "Mask width must be > 0, got $width" }
        require(height > 0) { "Mask height must be > 0, got $height" }
    }
}

/**
 * Common interface / base class for nodes in the authoritative ordered layer tree.
 * Supports raster layers, vector path layers, and recursive layer groups.
 */
sealed class LayerNode {
    abstract val id: String
    abstract val name: String
    abstract val visible: Boolean
    abstract val opacity: Float
    abstract val blendMode: BlendModeType
    abstract val transform: LayerTransform
    abstract val isLocked: Boolean
    abstract val isClippingMask: Boolean
    abstract val isAlphaLocked: Boolean
    abstract val mask: LayerMask?

    /**
     * Raster layer containing pixel data stored in container assets.
     */
    data class RasterLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val visible: Boolean = true,
        override val opacity: Float = 1.0f,
        override val blendMode: BlendModeType = BlendModeType.Normal,
        override val transform: LayerTransform = LayerTransform(),
        override val isLocked: Boolean = false,
        override val isClippingMask: Boolean = false,
        override val isAlphaLocked: Boolean = false,
        override val mask: LayerMask? = null,
        val assetPath: String? = null,
        val width: Int = 2048,
        val height: Int = 2048,
        val offsetX: Int = 0,
        val offsetY: Int = 0
    ) : LayerNode() {
        init {
            require(opacity in 0f..1f) { "Layer opacity must be between 0.0 and 1.0, got $opacity" }
            require(width > 0) { "Raster width must be > 0, got $width" }
            require(height > 0) { "Raster height must be > 0, got $height" }
        }
    }

    /**
     * Vector path layer containing mathematical stroke vectors and stylus dynamics.
     */
    data class VectorPathLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val visible: Boolean = true,
        override val opacity: Float = 1.0f,
        override val blendMode: BlendModeType = BlendModeType.Normal,
        override val transform: LayerTransform = LayerTransform(),
        override val isLocked: Boolean = false,
        override val isClippingMask: Boolean = false,
        override val isAlphaLocked: Boolean = false,
        override val mask: LayerMask? = null,
        val paths: List<VectorPathData> = emptyList(),
        val vectorObjects: List<com.artstudio.vector.VectorObject> = emptyList()
    ) : LayerNode() {
        init {
            require(opacity in 0f..1f) { "Layer opacity must be between 0.0 and 1.0, got $opacity" }
        }

        fun allVectorObjects(): List<com.artstudio.vector.VectorObject> {
            if (vectorObjects.isNotEmpty()) return vectorObjects
            return paths.map { path ->
                val anchors = path.points.map { pt ->
                    com.artstudio.vector.VectorAnchor(pt.x, pt.y)
                }
                com.artstudio.vector.VectorObject(
                    id = path.id,
                    name = "Path",
                    shapeType = com.artstudio.vector.VectorShapeType.POLYLINE,
                    anchors = anchors,
                    style = com.artstudio.vector.VectorStyle(
                        strokeColor = path.strokeColor,
                        strokeWidth = path.strokeWidth
                    )
                )
            }
        }
    }

    /**
     * Dedicated editable text layer containing a structured [com.artstudio.text.TextObject].
     *
     * Preserves typography styling, fonts, alignment, and document coordinates without rasterization.
     */
    data class TextLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val visible: Boolean = true,
        override val opacity: Float = 1.0f,
        override val blendMode: BlendModeType = BlendModeType.Normal,
        override val transform: LayerTransform = LayerTransform(),
        override val isLocked: Boolean = false,
        override val isClippingMask: Boolean = false,
        override val isAlphaLocked: Boolean = false,
        override val mask: LayerMask? = null,
        val textObject: com.artstudio.text.TextObject
    ) : LayerNode() {
        init {
            require(opacity in 0f..1f) { "Layer opacity must be between 0.0 and 1.0, got $opacity" }
        }
    }

    /**
     * Hierarchical Layer Group containing an ordered list of child layers and nested groups.
     * Children are ordered bottom-to-top (index 0 is bottom-most child).
     */
    data class LayerGroup(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val visible: Boolean = true,
        override val opacity: Float = 1.0f,
        override val blendMode: BlendModeType = BlendModeType.Normal,
        override val transform: LayerTransform = LayerTransform(),
        override val isLocked: Boolean = false,
        override val isClippingMask: Boolean = false,
        override val isAlphaLocked: Boolean = false,
        override val mask: LayerMask? = null,
        val children: List<LayerNode> = emptyList(),
        val isCollapsed: Boolean = false
    ) : LayerNode() {
        init {
            require(opacity in 0f..1f) { "Group opacity must be between 0.0 and 1.0, got $opacity" }
        }
    }

    /**
     * Non-destructive adjustment layer containing a structured [com.artstudio.adjustment.ColorAdjustment].
     * Applies tonal or color grading to underlying layers or clipped base layers without destructively mutating pixel buffers.
     */
    data class AdjustmentLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val visible: Boolean = true,
        override val opacity: Float = 1.0f,
        override val blendMode: BlendModeType = BlendModeType.Normal,
        override val transform: LayerTransform = LayerTransform(),
        override val isLocked: Boolean = false,
        override val isClippingMask: Boolean = false,
        override val isAlphaLocked: Boolean = false,
        override val mask: LayerMask? = null,
        val adjustment: com.artstudio.adjustment.ColorAdjustment
    ) : LayerNode() {
        init {
            require(opacity in 0f..1f) { "Layer opacity must be between 0.0 and 1.0, got $opacity" }
        }
    }
}
