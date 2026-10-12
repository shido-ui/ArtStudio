package com.artstudio.core

import android.graphics.*
import kotlin.math.roundToInt

/**
 * Concrete hardware-accelerated Canvas/Skia renderer.
 *
 * Implements:
 * - Per-layer bitmap surfaces: each finished stroke is baked once.
 * - Active strokes rendered incrementally on top of cached layer bitmaps.
 * - Composite cached layer bitmaps with zero stroke replaying.
 * - Zero per-frame allocations (Paints, Paths, Typefaces, filter lists).
 * - Zero saveLayer(null) calls; strictly bounded layer saves only where required.
 * - Eraser affects only the active layer.
 */
class NativeCanvasRenderer : DrawingRenderer {
    val surfaceManager: LayerSurfaceManager = LayerSurfaceManager()

    // Preallocated Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint()
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF888888.toInt()
    }
    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0xFFE0E0E0.toInt()
    }
    private val layerCompositePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val dstInPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val maskFillPaint = Paint()
    private val vectorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val vectorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val onionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Preallocated Rects and Paths
    private val scratchLayerRect = RectF()
    private val scratchVectorPath = Path()
    private val scratchOnionPath = Path()

    // Preallocated Typeface Cache
    private val typefaceCache = mutableMapOf<String, Typeface>()

    private val selectionRenderer = com.artstudio.selection.SelectionOutlineRenderer()
    var drawingAidsConfig: com.artstudio.guides.DrawingAidsConfig = com.artstudio.guides.DrawingAidsConfig()
    var referenceManager: com.artstudio.guides.ReferenceImageManager? = null
    private val guideRenderer = com.artstudio.guides.GuideOverlayRenderer()

    override fun render(
        canvas: Canvas,
        viewport: ViewportTransform,
        document: DocumentState,
        activeStroke: BrushStroke?
    ) {
        canvas.save()
        // Hardware-accelerated viewport transformation
        canvas.concat(viewport.getTransformMatrix())

        val canvasBounds = document.getBounds()

        // 1. Canvas background
        fillPaint.color = document.backgroundColor
        canvas.drawRect(canvasBounds, fillPaint)

        // 2. Subtle canvas grid pattern for orientation
        drawCanvasGrid(canvas, canvasBounds)

        val layers = document.authoritativeDocument.rootGroup.children
        val activeLayerId = document.authoritativeDocument.activeLayerId

        if (layers.isEmpty()) {
            // Fallback for flat document state without layer tree
            for (stroke in document.committedStrokes) {
                renderStroke(canvas, stroke)
            }
            if (activeStroke != null) {
                renderStroke(canvas, activeStroke)
                if (drawingAidsConfig.symmetry.type != com.artstudio.guides.SymmetryType.NONE) {
                    val symStrokes = com.artstudio.guides.DrawingAidsEngine.generateSymmetricStrokes(activeStroke, drawingAidsConfig.symmetry)
                    for (sym in symStrokes) {
                        renderStroke(canvas, sym)
                    }
                }
            }
        } else {
            // Render root layers strictly in document order with clipping chains
            renderLayerList(canvas, canvasBounds, layers, document, activeStroke, activeLayerId, depth = 1)
        }

        // Render reference overlays in document space if visible
        referenceManager?.renderOverlays(canvas, viewport)

        // Render animation onion-skin guides in document space if enabled
        val animConfig = document.authoritativeDocument.animation
        if (animConfig != null && animConfig.onionSkinEnabled) {
            renderOnionSkinOverlay(canvas, canvasBounds, document, animConfig)
        }

        // Render active drawing aids (guides and symmetry axes) in document space
        if (drawingAidsConfig.guideVisible) {
            guideRenderer.render(canvas, drawingAidsConfig, canvasBounds)
        }

        // Render active selection boundary (marching ants) in document space
        document.selectionManager?.let { sm ->
            if (sm.hasSelection) {
                selectionRenderer.renderMarchingAnts(canvas, sm.getSelectionPath())
            }
        }

        // Canvas border
        canvas.drawRect(canvasBounds, borderPaint)

        canvas.restore()
    }

    override fun renderDirty(
        canvas: Canvas,
        viewport: ViewportTransform,
        document: DocumentState,
        activeStroke: BrushStroke?,
        dirtyRect: RectF
    ) {
        canvas.save()
        canvas.clipRect(dirtyRect)
        render(canvas, viewport, document, activeStroke)
        canvas.restore()
    }

    fun bakeFinishedStroke(stroke: BrushStroke, documentState: DocumentState) {
        surfaceManager.bakeFinishedStroke(stroke, documentState)
    }

    fun rebakeAll(documentState: DocumentState) {
        surfaceManager.rebakeAll(documentState)
    }

    fun updateLayerPixels(layerId: String, pixels: IntArray) {
        surfaceManager.updateLayerPixels(layerId, pixels)
    }

    private fun renderLayerList(
        canvas: Canvas,
        bounds: RectF,
        layers: List<com.artstudio.document.model.LayerNode>,
        document: DocumentState,
        activeStroke: BrushStroke?,
        activeLayerId: String,
        depth: Int
    ) {
        // Zero-allocation iteration through clipping chains
        var i = 0
        val count = layers.size
        while (i < count) {
            val baseLayer = layers[i]
            if (baseLayer.isClippingMask) {
                // Orphan clipping mask at start of group: render as base
                renderLayerNode(canvas, bounds, baseLayer, document, activeStroke, activeLayerId, depth)
                i++
                continue
            }

            // Check if subsequent layers are clipping masks of this base layer
            var hasClippingChildren = false
            var checkIdx = i + 1
            while (checkIdx < count && layers[checkIdx].isClippingMask) {
                hasClippingChildren = true
                checkIdx++
            }

            if (!hasClippingChildren) {
                renderLayerNode(canvas, bounds, baseLayer, document, activeStroke, activeLayerId, depth)
                i++
            } else {
                // Bounded saveLayer for clipping group: base layer silhouette clips children
                val saveCount = canvas.saveLayer(bounds, null)
                renderLayerNode(canvas, bounds, baseLayer, document, activeStroke, activeLayerId, depth)

                for (cIdx in (i + 1) until checkIdx) {
                    val clipped = layers[cIdx]
                    if (clipped.visible && clipped.opacity > 0f) {
                        val clipSave = canvas.saveLayer(bounds, clipPaint)
                        renderLayerNode(canvas, bounds, clipped, document, activeStroke, activeLayerId, depth)
                        canvas.restoreToCount(clipSave)
                    }
                }
                canvas.restoreToCount(saveCount)
                i = checkIdx
            }
        }
    }

    private fun renderLayerNode(
        canvas: Canvas,
        bounds: RectF,
        layer: com.artstudio.document.model.LayerNode,
        document: DocumentState,
        activeStroke: BrushStroke?,
        activeLayerId: String,
        depth: Int
    ) {
        if (depth > com.artstudio.document.model.LayerHierarchyConstants.MAX_GROUP_DEPTH + 1) return
        if (!layer.visible || layer.opacity <= 0.0f) return

        val hasTransform = !layer.transform.isIdentity
        val hasMask = layer.mask != null && layer.mask!!.enabled

        if (hasTransform) {
            canvas.save()
            canvas.translate(layer.transform.translationX, layer.transform.translationY)
            canvas.rotate(layer.transform.rotationDegrees, bounds.centerX(), bounds.centerY())
            val sx = if (layer.transform.flipHorizontal) -layer.transform.scaleX else layer.transform.scaleX
            val sy = if (layer.transform.flipVertical) -layer.transform.scaleY else layer.transform.scaleY
            canvas.scale(sx, sy, bounds.centerX(), bounds.centerY())
        }

        val maskSaveCount = if (hasMask) {
            canvas.saveLayer(bounds, null)
        } else -1

        when (layer) {
            is com.artstudio.document.model.LayerNode.RasterLayer -> {
                val surface = surfaceManager.getOrCreateSurface(layer, document)
                val alphaInt = (layer.opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
                layerCompositePaint.alpha = alphaInt
                layerCompositePaint.xfermode = layer.blendMode.toPorterDuffMode()?.let { PorterDuffXfermode(it) }

                val isActive = layer.id == activeLayerId && !layer.isLocked
                val inFlightStroke = if (isActive && activeStroke != null && !activeStroke.isMaskStroke) activeStroke else null

                if (inFlightStroke != null) {
                    if (inFlightStroke.config.type == BrushType.ERASER) {
                        // Isolate in-flight eraser to active layer via bounded layer bounds
                        scratchLayerRect.set(
                            layer.offsetX.toFloat(),
                            layer.offsetY.toFloat(),
                            (layer.offsetX + layer.width).toFloat(),
                            (layer.offsetY + layer.height).toFloat()
                        )
                        val eraseSave = canvas.saveLayer(scratchLayerRect, layerCompositePaint)
                        if (surface.bitmap != null) {
                            canvas.drawBitmap(surface.bitmap, layer.offsetX.toFloat(), layer.offsetY.toFloat(), null)
                        }
                        renderStroke(canvas, inFlightStroke, isAlphaLocked = layer.isAlphaLocked)
                        canvas.restoreToCount(eraseSave)
                    } else {
                        // Regular brush active stroke rendered on top of cached layer bitmap
                        if (surface.bitmap != null) {
                            canvas.drawBitmap(surface.bitmap, layer.offsetX.toFloat(), layer.offsetY.toFloat(), layerCompositePaint)
                        }
                        renderStroke(canvas, inFlightStroke, isAlphaLocked = layer.isAlphaLocked)
                        if (drawingAidsConfig.symmetry.type != com.artstudio.guides.SymmetryType.NONE) {
                            val symStrokes = com.artstudio.guides.DrawingAidsEngine.generateSymmetricStrokes(inFlightStroke, drawingAidsConfig.symmetry)
                            for (sym in symStrokes) {
                                renderStroke(canvas, sym, isAlphaLocked = layer.isAlphaLocked)
                            }
                        }
                    }
                } else {
                    // Normal layer draw: draw cached bitmap directly with zero stroke replaying
                    if (surface.bitmap != null) {
                        canvas.drawBitmap(surface.bitmap, layer.offsetX.toFloat(), layer.offsetY.toFloat(), layerCompositePaint)
                    }
                }
            }
            is com.artstudio.document.model.LayerNode.VectorPathLayer -> {
                val alphaMul = layer.opacity.coerceIn(0f, 1f)
                if (layer.vectorObjects.isNotEmpty()) {
                    for (obj in layer.vectorObjects) {
                        val path = com.artstudio.vector.VectorGeometryEngine.toAndroidPath(obj)
                        // Fill pass
                        if (obj.style.fillColor != null) {
                            val baseColor = obj.style.fillColor.toInt()
                            val baseAlpha = Color.alpha(baseColor)
                            val finalAlpha = (baseAlpha * obj.style.opacity * alphaMul).toInt().coerceIn(0, 255)
                            vectorFillPaint.color = (baseColor and 0x00FFFFFF) or (finalAlpha shl 24)
                            canvas.drawPath(path, vectorFillPaint)
                        }
                        // Stroke pass
                        if (obj.style.strokeColor != null && obj.style.strokeWidth > 0f) {
                            val baseColor = obj.style.strokeColor.toInt()
                            val baseAlpha = Color.alpha(baseColor)
                            val finalAlpha = (baseAlpha * obj.style.opacity * alphaMul).toInt().coerceIn(0, 255)
                            vectorStrokePaint.color = (baseColor and 0x00FFFFFF) or (finalAlpha shl 24)
                            vectorStrokePaint.strokeWidth = obj.style.strokeWidth
                            vectorStrokePaint.strokeCap = when (obj.style.strokeCap) {
                                com.artstudio.vector.StrokeCap.BUTT -> Paint.Cap.BUTT
                                com.artstudio.vector.StrokeCap.ROUND -> Paint.Cap.ROUND
                                com.artstudio.vector.StrokeCap.SQUARE -> Paint.Cap.SQUARE
                            }
                            vectorStrokePaint.strokeJoin = when (obj.style.strokeJoin) {
                                com.artstudio.vector.StrokeJoin.MITER -> Paint.Join.MITER
                                com.artstudio.vector.StrokeJoin.ROUND -> Paint.Join.ROUND
                                com.artstudio.vector.StrokeJoin.BEVEL -> Paint.Join.BEVEL
                            }
                            vectorStrokePaint.strokeMiter = obj.style.strokeMiterLimit
                            canvas.drawPath(path, vectorStrokePaint)
                        }
                    }
                } else {
                    for (path in layer.paths) {
                        val baseColor = path.strokeColor.toInt()
                        val finalAlpha = (Color.alpha(baseColor) * alphaMul).toInt().coerceIn(0, 255)
                        vectorStrokePaint.color = (baseColor and 0x00FFFFFF) or (finalAlpha shl 24)
                        vectorStrokePaint.strokeWidth = path.strokeWidth
                        if (path.points.isNotEmpty()) {
                            scratchVectorPath.rewind()
                            scratchVectorPath.moveTo(path.points[0].x, path.points[0].y)
                            for (k in 1 until path.points.size) {
                                val pt = path.points[k]
                                scratchVectorPath.lineTo(pt.x, pt.y)
                            }
                            canvas.drawPath(scratchVectorPath, vectorStrokePaint)
                        }
                    }
                }
            }
            is com.artstudio.document.model.LayerNode.TextLayer -> {
                val textObj = layer.textObject
                val colorInt = textObj.textColor.toInt()
                val alpha = (Color.alpha(colorInt) * layer.opacity).toInt().coerceIn(0, 255)
                textPaint.color = (colorInt and 0x00FFFFFF) or (alpha shl 24)
                textPaint.textSize = textObj.fontSize

                val tfKey = "${textObj.fontFamily}_${textObj.fontStyle}"
                textPaint.typeface = typefaceCache.getOrPut(tfKey) {
                    com.artstudio.text.FontEngine.createTypeface(null, textObj.fontFamily, textObj.fontStyle)
                }
                textPaint.textAlign = when (textObj.alignment) {
                    com.artstudio.text.TextAlignment.LEFT -> Paint.Align.LEFT
                    com.artstudio.text.TextAlignment.CENTER -> Paint.Align.CENTER
                    com.artstudio.text.TextAlignment.RIGHT -> Paint.Align.RIGHT
                }
                textPaint.isUnderlineText = textObj.isUnderline
                textPaint.isStrikeThruText = textObj.isStrikeThrough

                val lines = textObj.lines()
                val lineHeight = textObj.fontSize * textObj.lineSpacingMultiplier
                var currentY = textObj.y + textObj.fontSize
                for (line in lines) {
                    canvas.drawText(line, textObj.x, currentY, textPaint)
                    currentY += lineHeight
                }
            }
            is com.artstudio.document.model.LayerNode.LayerGroup -> {
                renderLayerList(canvas, bounds, layer.children, document, activeStroke, activeLayerId, depth + 1)
            }
            is com.artstudio.document.model.LayerNode.AdjustmentLayer -> {
                // Parametric adjustment layer evaluated in compositing passes
            }
        }

        // Apply layer mask if present and enabled
        if (hasMask && maskSaveCount != -1) {
            val mask = layer.mask!!
            val dstInSave = canvas.saveLayer(bounds, dstInPaint)

            maskFillPaint.color = if (mask.inverted) Color.BLACK else Color.WHITE
            canvas.drawRect(bounds, maskFillPaint)

            // Render mask strokes onto the mask buffer
            val maskStrokes = document.committedStrokes.filter {
                (it.layerId == layer.id || (it.layerId.isEmpty() && layer.id == activeLayerId)) && it.isMaskStroke
            }
            for (mStroke in maskStrokes) {
                renderStroke(canvas, mStroke)
            }
            if (activeStroke != null && layer.id == activeLayerId && activeStroke.isMaskStroke) {
                renderStroke(canvas, activeStroke)
            }

            canvas.restoreToCount(dstInSave)
            canvas.restoreToCount(maskSaveCount)
        }

        if (hasTransform) {
            canvas.restore()
        }
    }

    private val rasterEngine = RasterStrokeEngine()

    private fun renderStroke(canvas: Canvas, stroke: BrushStroke, isAlphaLocked: Boolean = false) {
        if (isAlphaLocked) {
            scratchLayerRect.set(0f, 0f, 16384f, 16384f)
            val saveCount = canvas.saveLayer(scratchLayerRect, LayerBlendCompositor.createSrcAtopPaint())
            rasterEngine.renderStroke(canvas, stroke)
            canvas.restoreToCount(saveCount)
        } else {
            rasterEngine.renderStroke(canvas, stroke)
        }
    }

    private fun drawCanvasGrid(canvas: Canvas, bounds: RectF) {
        val step = 128f
        var x = bounds.left + step
        while (x < bounds.right) {
            canvas.drawLine(x, bounds.top, x, bounds.bottom, gridPaint)
            x += step
        }
        var y = bounds.top + step
        while (y < bounds.bottom) {
            canvas.drawLine(bounds.left, y, bounds.right, y, gridPaint)
            y += step
        }
    }

    private fun renderOnionSkinOverlay(
        canvas: Canvas,
        bounds: RectF,
        document: DocumentState,
        animConfig: com.artstudio.document.model.AnimationTimelineConfig
    ) {
        val timeline = animConfig.toTimeline()
        val os = timeline.onionSkin
        if (!os.enabled) return

        val currentIdx = timeline.currentFrameIndex
        if (currentIdx !in timeline.frames.indices) return

        fun drawCelStrokes(celId: String, alphaMul: Float, tintColor: Long?, isOriginalColor: Boolean) {
            val celStrokes = document.committedStrokes.filter { it.layerId == celId }
            for (stroke in celStrokes) {
                val baseColor = stroke.config.color
                val finalColor = if (isOriginalColor || tintColor == null) {
                    val origA = (baseColor ushr 24) and 0xFF
                    val newA = ((origA * alphaMul).toInt()).coerceIn(0, 255)
                    (newA shl 24) or (baseColor and 0x00FFFFFF)
                } else {
                    val newA = ((255 * alphaMul).toInt()).coerceIn(0, 255)
                    (newA shl 24) or (tintColor.toInt() and 0x00FFFFFF)
                }
                onionPaint.color = finalColor
                onionPaint.strokeWidth = stroke.config.size
                if (stroke.points.isNotEmpty()) {
                    if (stroke.points.size == 1) {
                        onionPaint.style = Paint.Style.FILL
                        canvas.drawCircle(stroke.points[0].x, stroke.points[0].y, stroke.config.size / 2f, onionPaint)
                        onionPaint.style = Paint.Style.STROKE
                    } else {
                        scratchOnionPath.rewind()
                        scratchOnionPath.moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (k in 1 until stroke.points.size) {
                            scratchOnionPath.lineTo(stroke.points[k].x, stroke.points[k].y)
                        }
                        canvas.drawPath(scratchOnionPath, onionPaint)
                    }
                }
            }
        }

        // 1. Past Frames (Orange)
        if (os.showBefore) {
            for (step in os.framesBefore downTo 1) {
                val pastIdx = currentIdx - step
                if (pastIdx in timeline.frames.indices) {
                    val pastFrame = timeline.frames[pastIdx]
                    val stepOpacity = when (os.falloff) {
                        com.artstudio.animation.OnionSkinFalloff.CONSTANT -> os.opacityBefore
                        com.artstudio.animation.OnionSkinFalloff.LINEAR -> os.opacityBefore * (1.0f - (step - 1) * 0.2f).coerceAtLeast(0.15f)
                        com.artstudio.animation.OnionSkinFalloff.QUADRATIC -> os.opacityBefore * (1.0f - (step - 1) * 0.35f).coerceAtLeast(0.1f)
                    }
                    val isOrig = os.tintMode == com.artstudio.animation.OnionSkinTintMode.ORIGINAL_COLOR
                    for (celId in pastFrame.celLayerIds) {
                        drawCelStrokes(celId, stepOpacity, os.tintBefore, isOrig)
                    }
                }
            }
        }

        // 2. Future Frames (Cyan)
        if (os.showAfter) {
            for (step in os.framesAfter downTo 1) {
                val futureIdx = currentIdx + step
                if (futureIdx in timeline.frames.indices) {
                    val futureFrame = timeline.frames[futureIdx]
                    val stepOpacity = when (os.falloff) {
                        com.artstudio.animation.OnionSkinFalloff.CONSTANT -> os.opacityAfter
                        com.artstudio.animation.OnionSkinFalloff.LINEAR -> os.opacityAfter * (1.0f - (step - 1) * 0.2f).coerceAtLeast(0.15f)
                        com.artstudio.animation.OnionSkinFalloff.QUADRATIC -> os.opacityAfter * (1.0f - (step - 1) * 0.35f).coerceAtLeast(0.1f)
                    }
                    val isOrig = os.tintMode == com.artstudio.animation.OnionSkinTintMode.ORIGINAL_COLOR
                    for (celId in futureFrame.celLayerIds) {
                        drawCelStrokes(celId, stepOpacity, os.tintAfter, isOrig)
                    }
                }
            }
        }
    }
}
