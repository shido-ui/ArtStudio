package com.artstudio.export

import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import kotlin.math.roundToInt

/**
 * Exception thrown when export is cancelled cooperatively.
 */
class ExportCancelledException(message: String = "Export operation cancelled by user") : RuntimeException(message)

/**
 * Rendered raw image buffer ready for encoding.
 */
data class RenderedArtwork(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RenderedArtwork) return false
        return width == other.width &&
                height == other.height &&
                hasAlpha == other.hasAlpha &&
                pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + hasAlpha.hashCode()
        return result
    }
}

/**
 * Authoritative off-screen renderer for exporting ArtStudio documents.
 * Composites layers, applies clipping masks, layer masks, and adjustment layers,
 * extracts crop regions, and scales dimensions without altering source project documents.
 */
object ArtworkExportRenderer {

    /**
     * Renders an [ArtDocument] to a flat 32-bit ARGB pixel buffer according to [config].
     *
     * @param document The immutable source document
     * @param config Export configuration parameters
     * @param layerPixelBuffers Map of layer UUID to IntArray pixel buffer
     * @param layerMaskBuffers Map of layer UUID to IntArray grayscale mask buffer
     * @param cancellationToken Optional cancellation token for early exit
     * @return RenderedArtwork with verified dimensions and pixels
     */
    fun renderArtwork(
        document: ArtDocument,
        config: ExportConfig,
        layerPixelBuffers: Map<String, IntArray> = emptyMap(),
        layerMaskBuffers: Map<String, IntArray> = emptyMap(),
        cancellationToken: ExportCancellationToken? = null
    ): RenderedArtwork {
        checkCancellation(cancellationToken)

        val canvasW = document.canvas.width
        val canvasH = document.canvas.height
        val canvasSize = canvasW * canvasH

        // 1. Initialize canvas backdrop
        val canvasPixels = IntArray(canvasSize)
        if (!config.transparentBackground) {
            val bg = document.canvas.backgroundColor.toInt()
            canvasPixels.fill(bg)
        }

        // 2. Composite layers according to scope
        when (config.scope) {
            ExportScope.SELECTED_LAYER -> {
                val targetId = config.targetLayerOrGroupId ?: document.activeLayerId
                val targetLayer = document.findLayerById(targetId)
                if (targetLayer != null && targetLayer.visible) {
                    renderSingleLayer(
                        destPixels = canvasPixels,
                        layer = targetLayer,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        layerPixelBuffers = layerPixelBuffers,
                        layerMaskBuffers = layerMaskBuffers,
                        cancellationToken = cancellationToken
                    )
                }
            }

            ExportScope.SELECTED_GROUP -> {
                val targetId = config.targetLayerOrGroupId ?: document.activeLayerId
                val group = document.findLayerById(targetId) as? LayerNode.LayerGroup
                if (group != null && group.visible) {
                    renderGroupChildren(
                        destPixels = canvasPixels,
                        group = group,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        layerPixelBuffers = layerPixelBuffers,
                        layerMaskBuffers = layerMaskBuffers,
                        cancellationToken = cancellationToken
                    )
                }
            }

            ExportScope.FULL_CANVAS, ExportScope.EXPLICIT_FLATTENED, ExportScope.CROP_REGION -> {
                renderGroupChildren(
                    destPixels = canvasPixels,
                    group = document.rootGroup,
                    canvasW = canvasW,
                    canvasH = canvasH,
                    layerPixelBuffers = layerPixelBuffers,
                    layerMaskBuffers = layerMaskBuffers,
                    cancellationToken = cancellationToken
                )
            }
        }

        checkCancellation(cancellationToken)

        // 3. Sub-rectangle crop
        var currentPixels = canvasPixels
        var currentW = canvasW
        var currentH = canvasH

        val crop = config.cropRect
        if (crop != null || config.scope == ExportScope.CROP_REGION) {
            val effectiveCrop = crop ?: ExportCropRect(0, 0, canvasW, canvasH)
            val cLeft = effectiveCrop.left.coerceIn(0, canvasW - 1)
            val cTop = effectiveCrop.top.coerceIn(0, canvasH - 1)
            val cRight = effectiveCrop.right.coerceIn(cLeft + 1, canvasW)
            val cBottom = effectiveCrop.bottom.coerceIn(cTop + 1, canvasH)
            val cropW = cRight - cLeft
            val cropH = cBottom - cTop

            val cropped = IntArray(cropW * cropH)
            for (y in 0 until cropH) {
                if (y % 64 == 0) checkCancellation(cancellationToken)
                val srcY = cTop + y
                System.arraycopy(canvasPixels, srcY * canvasW + cLeft, cropped, y * cropW, cropW)
            }
            currentPixels = cropped
            currentW = cropW
            currentH = cropH
        }

        checkCancellation(cancellationToken)

        // 4. Output Dimension Scaling / Resampling
        val targetW = when {
            config.outputWidth != null && config.outputHeight != null -> config.outputWidth
            config.scaleFactor != 1.0f -> (currentW * config.scaleFactor).roundToInt().coerceAtLeast(1)
            else -> currentW
        }

        val targetH = when {
            config.outputWidth != null && config.outputHeight != null -> config.outputHeight
            config.scaleFactor != 1.0f -> (currentH * config.scaleFactor).roundToInt().coerceAtLeast(1)
            else -> currentH
        }

        val finalPixels = if (targetW != currentW || targetH != currentH) {
            resampleBilinear(
                srcPixels = currentPixels,
                srcW = currentW,
                srcH = currentH,
                dstW = targetW,
                dstH = targetH,
                cancellationToken = cancellationToken
            )
        } else {
            currentPixels
        }

        checkCancellation(cancellationToken)

        // 5. Detect Alpha channel presence
        var hasAlpha = false
        for (p in finalPixels) {
            if (((p ushr 24) and 0xFF) < 255) {
                hasAlpha = true
                break
            }
        }

        return RenderedArtwork(
            pixels = finalPixels,
            width = targetW,
            height = targetH,
            hasAlpha = hasAlpha
        )
    }

    private fun renderGroupChildren(
        destPixels: IntArray,
        group: LayerNode.LayerGroup,
        canvasW: Int,
        canvasH: Int,
        layerPixelBuffers: Map<String, IntArray>,
        layerMaskBuffers: Map<String, IntArray>,
        cancellationToken: ExportCancellationToken?
    ) {
        var lastBaseLayerPixels: IntArray? = null

        for (child in group.children) {
            checkCancellation(cancellationToken)
            if (!child.visible || child.opacity <= 0.0f) continue

            when (child) {
                is LayerNode.LayerGroup -> {
                    // Recursive group rendering into an isolated buffer
                    val groupBuffer = IntArray(canvasW * canvasH)
                    renderGroupChildren(
                        destPixels = groupBuffer,
                        group = child,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        layerPixelBuffers = layerPixelBuffers,
                        layerMaskBuffers = layerMaskBuffers,
                        cancellationToken = cancellationToken
                    )

                    // Apply group mask if present
                    val mask = child.mask
                    if (mask != null && mask.enabled) {
                        val maskBuffer = layerMaskBuffers[child.id]
                        if (maskBuffer != null) {
                            LayerBlendCompositor.applyMaskToBuffer(
                                groupBuffer,
                                maskBuffer,
                                inverted = mask.inverted,
                                enabled = true
                            )
                        }
                    }

                    // Composite group into destination
                    LayerBlendCompositor.compositeBuffers(
                        destPixels = destPixels,
                        srcPixels = groupBuffer,
                        mode = child.blendMode,
                        layerOpacity = child.opacity
                    )

                    lastBaseLayerPixels = groupBuffer
                }

                is LayerNode.AdjustmentLayer -> {
                    val maskBuffer = layerMaskBuffers[child.id]
                    LayerBlendCompositor.applyAdjustmentLayer(
                        targetPixels = destPixels,
                        width = canvasW,
                        height = canvasH,
                        layer = child,
                        maskPixels = maskBuffer
                    )
                }

                is LayerNode.RasterLayer -> {
                    val srcBuffer = layerPixelBuffers[child.id]
                    if (srcBuffer != null && srcBuffer.isNotEmpty()) {
                        val renderBuffer = srcBuffer.clone()

                        // Handle clipping mask
                        if (child.isClippingMask && lastBaseLayerPixels != null) {
                            LayerBlendCompositor.applyClippingMaskToBuffer(renderBuffer, lastBaseLayerPixels)
                        }

                        // Handle layer mask
                        val mask = child.mask
                        if (mask != null && mask.enabled) {
                            val maskBuffer = layerMaskBuffers[child.id]
                            if (maskBuffer != null) {
                                LayerBlendCompositor.applyMaskToBuffer(
                                    renderBuffer,
                                    maskBuffer,
                                    inverted = mask.inverted,
                                    enabled = true
                                )
                            }
                        }

                        // Composite buffer
                        LayerBlendCompositor.compositeBuffers(
                            destPixels = destPixels,
                            srcPixels = renderBuffer,
                            mode = child.blendMode,
                            layerOpacity = child.opacity
                        )

                        if (!child.isClippingMask) {
                            lastBaseLayerPixels = srcBuffer
                        }
                    }
                }

                is LayerNode.VectorPathLayer -> {
                    val srcBuffer = layerPixelBuffers[child.id]
                    if (srcBuffer != null && srcBuffer.isNotEmpty()) {
                        val renderBuffer = srcBuffer.clone()
                        if (child.isClippingMask && lastBaseLayerPixels != null) {
                            LayerBlendCompositor.applyClippingMaskToBuffer(renderBuffer, lastBaseLayerPixels)
                        }
                        LayerBlendCompositor.compositeBuffers(
                            destPixels = destPixels,
                            srcPixels = renderBuffer,
                            mode = child.blendMode,
                            layerOpacity = child.opacity
                        )
                        if (!child.isClippingMask) {
                            lastBaseLayerPixels = srcBuffer
                        }
                    }
                }

                is LayerNode.TextLayer -> {
                    val srcBuffer = layerPixelBuffers[child.id]
                    if (srcBuffer != null && srcBuffer.isNotEmpty()) {
                        val renderBuffer = srcBuffer.clone()
                        if (child.isClippingMask && lastBaseLayerPixels != null) {
                            LayerBlendCompositor.applyClippingMaskToBuffer(renderBuffer, lastBaseLayerPixels)
                        }
                        LayerBlendCompositor.compositeBuffers(
                            destPixels = destPixels,
                            srcPixels = renderBuffer,
                            mode = child.blendMode,
                            layerOpacity = child.opacity
                        )
                        if (!child.isClippingMask) {
                            lastBaseLayerPixels = srcBuffer
                        }
                    }
                }
            }
        }
    }

    private fun renderSingleLayer(
        destPixels: IntArray,
        layer: LayerNode,
        canvasW: Int,
        canvasH: Int,
        layerPixelBuffers: Map<String, IntArray>,
        layerMaskBuffers: Map<String, IntArray>,
        cancellationToken: ExportCancellationToken?
    ) {
        checkCancellation(cancellationToken)
        when (layer) {
            is LayerNode.RasterLayer -> {
                val srcBuffer = layerPixelBuffers[layer.id] ?: return
                val renderBuffer = srcBuffer.clone()
                val mask = layer.mask
                if (mask != null && mask.enabled) {
                    val maskBuffer = layerMaskBuffers[layer.id]
                    if (maskBuffer != null) {
                        LayerBlendCompositor.applyMaskToBuffer(
                            renderBuffer,
                            maskBuffer,
                            inverted = mask.inverted,
                            enabled = true
                        )
                    }
                }
                LayerBlendCompositor.compositeBuffers(
                    destPixels = destPixels,
                    srcPixels = renderBuffer,
                    mode = layer.blendMode,
                    layerOpacity = layer.opacity
                )
            }

            is LayerNode.LayerGroup -> {
                renderGroupChildren(
                    destPixels = destPixels,
                    group = layer,
                    canvasW = canvasW,
                    canvasH = canvasH,
                    layerPixelBuffers = layerPixelBuffers,
                    layerMaskBuffers = layerMaskBuffers,
                    cancellationToken = cancellationToken
                )
            }

            is LayerNode.AdjustmentLayer -> {
                val maskBuffer = layerMaskBuffers[layer.id]
                LayerBlendCompositor.applyAdjustmentLayer(
                    targetPixels = destPixels,
                    width = canvasW,
                    height = canvasH,
                    layer = layer,
                    maskPixels = maskBuffer
                )
            }

            is LayerNode.VectorPathLayer, is LayerNode.TextLayer -> {
                val srcBuffer = layerPixelBuffers[layer.id] ?: return
                val renderBuffer = srcBuffer.clone()
                LayerBlendCompositor.compositeBuffers(
                    destPixels = destPixels,
                    srcPixels = renderBuffer,
                    mode = layer.blendMode,
                    layerOpacity = layer.opacity
                )
            }
        }
    }

    private fun resampleBilinear(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        cancellationToken: ExportCancellationToken?
    ): IntArray {
        val dst = IntArray(dstW * dstH)
        val xRatio = srcW.toFloat() / dstW
        val yRatio = srcH.toFloat() / dstH

        for (y in 0 until dstH) {
            if (y % 32 == 0) checkCancellation(cancellationToken)

            val srcYFloat = (y + 0.5f) * yRatio - 0.5f
            val y0 = srcYFloat.toInt().coerceIn(0, srcH - 1)
            val y1 = (y0 + 1).coerceIn(0, srcH - 1)
            val yWeight = (srcYFloat - y0).coerceIn(0f, 1f)

            for (x in 0 until dstW) {
                val srcXFloat = (x + 0.5f) * xRatio - 0.5f
                val x0 = srcXFloat.toInt().coerceIn(0, srcW - 1)
                val x1 = (x0 + 1).coerceIn(0, srcW - 1)
                val xWeight = (srcXFloat - x0).coerceIn(0f, 1f)

                val p00 = srcPixels[y0 * srcW + x0]
                val p10 = srcPixels[y0 * srcW + x1]
                val p01 = srcPixels[y1 * srcW + x0]
                val p11 = srcPixels[y1 * srcW + x1]

                val a00 = (p00 ushr 24) and 0xFF
                val r00 = (p00 ushr 16) and 0xFF
                val g00 = (p00 ushr 8) and 0xFF
                val b00 = p00 and 0xFF

                val a10 = (p10 ushr 24) and 0xFF
                val r10 = (p10 ushr 16) and 0xFF
                val g10 = (p10 ushr 8) and 0xFF
                val b10 = p10 and 0xFF

                val a01 = (p01 ushr 24) and 0xFF
                val r01 = (p01 ushr 16) and 0xFF
                val g01 = (p01 ushr 8) and 0xFF
                val b01 = p01 and 0xFF

                val a11 = (p11 ushr 24) and 0xFF
                val r11 = (p11 ushr 16) and 0xFF
                val g11 = (p11 ushr 8) and 0xFF
                val b11 = p11 and 0xFF

                val aTop = a00 + (a10 - a00) * xWeight
                val rTop = r00 + (r10 - r00) * xWeight
                val gTop = g00 + (g10 - g00) * xWeight
                val bTop = b00 + (b10 - b00) * xWeight

                val aBottom = a01 + (a11 - a01) * xWeight
                val rBottom = r01 + (r11 - r01) * xWeight
                val gBottom = g01 + (g11 - g01) * xWeight
                val bBottom = b01 + (b11 - b01) * xWeight

                val a = (aTop + (aBottom - aTop) * yWeight).roundToInt().coerceIn(0, 255)
                val r = (rTop + (rBottom - rTop) * yWeight).roundToInt().coerceIn(0, 255)
                val g = (gTop + (gBottom - gTop) * yWeight).roundToInt().coerceIn(0, 255)
                val b = (bTop + (bBottom - bTop) * yWeight).roundToInt().coerceIn(0, 255)

                dst[y * dstW + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return dst
    }

    private fun checkCancellation(token: ExportCancellationToken?) {
        if (token != null && token.isCancelled) {
            throw ExportCancelledException()
        }
    }
}
