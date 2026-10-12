package com.artstudio.core

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.history.EditCommand
import com.artstudio.history.HistoryManager
import com.artstudio.history.StrokeCommand

/**
 * Narrow interface for authoritative document state.
 * Encapsulates canvas dimensions, layers, test bitmaps, committed stroke history,
 * and reversible command transactions.
 */
interface DocumentState {
    val width: Int
    val height: Int
    val backgroundColor: Int
    var testBitmap: Bitmap?
    val committedStrokes: List<BrushStroke>
    val historyManager: HistoryManager?
    val authoritativeDocument: ArtDocument
    val layerManager: com.artstudio.layer.LayerManager?
        get() = null
    val selectionManager: com.artstudio.selection.SelectionManager?
        get() = null

    val layerPixelBuffers: Map<String, IntArray>
        get() = emptyMap()
    val layerMaskBuffers: Map<String, IntArray>
        get() = emptyMap()

    fun getLayerPixels(layerId: String): IntArray? = null
    fun setLayerPixels(layerId: String, pixels: IntArray) {}
    fun getOrRenderLayerPixels(layerId: String): IntArray = IntArray(width * height)
    fun getAllLayerPixelBuffers(): Map<String, IntArray> = emptyMap()

    fun getBounds(): RectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
    fun addCommittedStroke(stroke: BrushStroke)
    fun addCommittedStrokes(strokes: List<BrushStroke>) {}
    fun clearCommittedStrokes()
    fun clearActiveLayerUndoably(): Boolean = false
    fun undo(): Boolean
    fun redo(): Boolean
    val canUndo: Boolean
    val canRedo: Boolean
    fun onTrimMemory(level: MemoryTrimLevel) {}
}

class DefaultDocumentState(
    width: Int = 2048,
    height: Int = 2048,
    override val backgroundColor: Int = Color.WHITE,
    override var testBitmap: Bitmap? = null,
    artDocument: ArtDocument? = null,
    initialPixelBuffers: Map<String, IntArray> = emptyMap(),
    initialMaskBuffers: Map<String, IntArray> = emptyMap()
) : DocumentState {
    override val width: Int
        get() = authoritativeDocument.canvas.width
    override val height: Int
        get() = authoritativeDocument.canvas.height

    private val _layerPixelBuffers = mutableMapOf<String, IntArray>().apply {
        putAll(initialPixelBuffers)
    }
    override val layerPixelBuffers: Map<String, IntArray>
        get() = _layerPixelBuffers

    private val _layerMaskBuffers = mutableMapOf<String, IntArray>().apply {
        putAll(initialMaskBuffers)
    }
    override val layerMaskBuffers: Map<String, IntArray>
        get() = _layerMaskBuffers

    private val _committedStrokes = mutableListOf<BrushStroke>()
    override val committedStrokes: List<BrushStroke>
        get() = _committedStrokes

    private val initialDocument: ArtDocument = artDocument ?: ArtDocument(
        canvas = CanvasConfig(width = width, height = height)
    )

    override val historyManager: HistoryManager = HistoryManager(initialDocument)

    override val authoritativeDocument: ArtDocument
        get() = historyManager.currentDocument

    override val layerManager: com.artstudio.layer.LayerManager = com.artstudio.layer.LayerManager(
        historyManager = historyManager,
        onDuplicateStrokes = { sourceId, dupId ->
            val duplicatedStrokes = _committedStrokes.filter { it.layerId == sourceId }.map { s ->
                s.copy(id = java.util.UUID.randomUUID().toString(), layerId = dupId)
            }
            _committedStrokes.addAll(duplicatedStrokes)
        },
        onRemoveStrokes = { removedId ->
            _committedStrokes.removeAll { it.layerId == removedId }
        },
        onMergeStrokes = { sourceIds, mergedId ->
            val mapping = mutableMapOf<String, String>()
            for (i in _committedStrokes.indices) {
                val s = _committedStrokes[i]
                if (s.layerId in sourceIds) {
                    mapping[s.id] = s.layerId
                    _committedStrokes[i] = s.copy(layerId = mergedId)
                }
            }
            mapping
        },
        onRestoreStrokes = { mapping ->
            for (i in _committedStrokes.indices) {
                val s = _committedStrokes[i]
                val orig = mapping[s.id]
                if (orig != null) {
                    _committedStrokes[i] = s.copy(layerId = orig)
                }
            }
        },
        onReapplyStrokes = { strokeIds, mergedId ->
            for (i in _committedStrokes.indices) {
                val s = _committedStrokes[i]
                if (s.id in strokeIds) {
                    _committedStrokes[i] = s.copy(layerId = mergedId)
                }
            }
        }
    )

    val thumbnailGenerator: com.artstudio.layer.LayerThumbnailGenerator =
        com.artstudio.layer.LayerThumbnailGenerator()

    override val selectionManager: com.artstudio.selection.SelectionManager =
        com.artstudio.selection.SelectionManager(width, height, historyManager)

    override val canUndo: Boolean
        get() = historyManager.canUndo

    override val canRedo: Boolean
        get() = historyManager.canRedo

    override fun addCommittedStroke(stroke: BrushStroke) {
        val targetLayerId = if (stroke.layerId.isNotEmpty()) stroke.layerId else authoritativeDocument.activeLayerId
        val targetLayer = authoritativeDocument.findLayerById(targetLayerId)

        // Guard: Drawing into locked layers is prohibited
        if (targetLayer?.isLocked == true) {
            return
        }

        val resolvedStroke = if (stroke.layerId.isEmpty()) stroke.copy(layerId = targetLayerId) else stroke
        _committedStrokes.add(resolvedStroke)
        historyManager.execute(StrokeCommand(stroke = resolvedStroke, targetLayerId = targetLayerId))
    }

    override fun addCommittedStrokes(strokes: List<BrushStroke>) {
        if (strokes.isEmpty()) return
        if (strokes.size == 1) {
            addCommittedStroke(strokes[0])
            return
        }
        val targetLayerId = if (strokes[0].layerId.isNotEmpty()) strokes[0].layerId else authoritativeDocument.activeLayerId
        val targetLayer = authoritativeDocument.findLayerById(targetLayerId)
        if (targetLayer?.isLocked == true) return

        val resolvedStrokes = strokes.map { s -> if (s.layerId.isEmpty()) s.copy(layerId = targetLayerId) else s }
        _committedStrokes.addAll(resolvedStrokes)
        historyManager.execute(com.artstudio.history.CompoundStrokeCommand(strokes = resolvedStrokes, targetLayerId = targetLayerId))
    }

    override fun clearActiveLayerUndoably(): Boolean {
        val targetLayerId = authoritativeDocument.activeLayerId
        val targetLayer = authoritativeDocument.findLayerById(targetLayerId) as? com.artstudio.document.model.LayerNode.RasterLayer ?: return false
        if (targetLayer.isLocked) return false

        val clearedStrokes = _committedStrokes.filter { it.layerId == targetLayerId || (it.layerId.isEmpty() && targetLayerId == authoritativeDocument.activeLayerId) }
        _committedStrokes.removeAll(clearedStrokes.toSet())

        val w = targetLayer.width
        val h = targetLayer.height
        val buf = getOrRenderLayerPixels(targetLayerId)
        val blank = IntArray(w * h) { 0 }
        val deltas = com.artstudio.history.TileDeltaHelper.extractTileDeltas(buf, blank, w, h)
        System.arraycopy(blank, 0, buf, 0, w * h)
        _layerPixelBuffers[targetLayerId] = buf

        val cmd = com.artstudio.history.ClearLayerCommand(
            layerId = targetLayerId,
            clearedStrokes = clearedStrokes,
            tileDeltas = deltas,
            width = w,
            height = h
        )
        historyManager.execute(cmd)
        return true
    }

    override fun undo(): Boolean {
        val undone = historyManager.undo() ?: return false
        when (undone) {
            is StrokeCommand -> _committedStrokes.remove(undone.stroke)
            is com.artstudio.history.CompoundStrokeCommand -> _committedStrokes.removeAll(undone.strokes.toSet())
            is com.artstudio.history.ClearLayerCommand -> {
                _committedStrokes.addAll(undone.clearedStrokes)
                val buf = _layerPixelBuffers[undone.layerId]
                if (buf != null) undone.restorePixels(buf, usePre = true)
            }
            is com.artstudio.history.FillSelectionCommand -> {
                val buf = _layerPixelBuffers[undone.layerId]
                if (buf != null) undone.restorePixels(buf, usePre = true)
            }
            is com.artstudio.history.BakeTransformCommand -> {
                val buf = _layerPixelBuffers[undone.layerId]
                if (buf != null) undone.restorePixels(buf, usePre = true)
            }
            is com.artstudio.history.ApplyDirectAdjustmentCommand -> {
                val buf = _layerPixelBuffers[undone.targetLayerId]
                if (buf != null) undone.restorePixels(buf, usePre = true)
            }
            is com.artstudio.history.MergeLayersCommand -> layerManager.restoreMergedStrokes(undone.strokeIdToOriginalLayerId)
            is com.artstudio.history.MergeVisibleCommand -> layerManager.restoreMergedStrokes(undone.strokeIdToOriginalLayerId)
            is com.artstudio.history.FlattenDocumentCommand -> layerManager.restoreMergedStrokes(undone.strokeIdToOriginalLayerId)
            is com.artstudio.history.ApplyFilterCommand -> {
                val buf = _layerPixelBuffers[undone.layerId]
                if (buf != null) {
                    undone.restorePixels(buf, usePre = true)
                }
            }
            is com.artstudio.history.FillCommand -> {
                val buf = _layerPixelBuffers[undone.layerId]
                if (buf != null) {
                    undone.restorePixels(buf, usePre = true)
                }
            }
            is com.artstudio.history.PixelModifierCommand -> {
                val buf = _layerPixelBuffers[undone.layerId]
                if (buf != null) {
                    undone.restorePatch(buf, width, usePre = true)
                }
            }
            is com.artstudio.history.ApplyGradientCommand -> {
                val buf = _layerPixelBuffers[undone.layerId]
                if (buf != null && undone.preGradientPixels.size == buf.size) {
                    System.arraycopy(undone.preGradientPixels, 0, buf, 0, buf.size)
                }
            }
            is com.artstudio.history.DeformLayerCommand -> {
                val buf = _layerPixelBuffers[undone.layerId]
                if (buf != null && undone.preDeformPixels.size == buf.size) {
                    System.arraycopy(undone.preDeformPixels, 0, buf, 0, buf.size)
                }
            }
            else -> {}
        }
        return true
    }

    override fun redo(): Boolean {
        val redone = historyManager.redo() ?: return false
        when (redone) {
            is StrokeCommand -> _committedStrokes.add(redone.stroke)
            is com.artstudio.history.CompoundStrokeCommand -> _committedStrokes.addAll(redone.strokes)
            is com.artstudio.history.ClearLayerCommand -> {
                _committedStrokes.removeAll(redone.clearedStrokes.toSet())
                val buf = _layerPixelBuffers[redone.layerId]
                if (buf != null) redone.restorePixels(buf, usePre = false)
            }
            is com.artstudio.history.FillSelectionCommand -> {
                val buf = _layerPixelBuffers[redone.layerId]
                if (buf != null) redone.restorePixels(buf, usePre = false)
            }
            is com.artstudio.history.BakeTransformCommand -> {
                val buf = _layerPixelBuffers[redone.layerId]
                if (buf != null) redone.restorePixels(buf, usePre = false)
            }
            is com.artstudio.history.ApplyDirectAdjustmentCommand -> {
                val buf = _layerPixelBuffers[redone.targetLayerId]
                if (buf != null) redone.restorePixels(buf, usePre = false)
            }
            is com.artstudio.history.MergeLayersCommand -> layerManager.reapplyMergedStrokes(redone.strokeIdToOriginalLayerId.keys, redone.mergedLayer.id)
            is com.artstudio.history.MergeVisibleCommand -> layerManager.reapplyMergedStrokes(redone.strokeIdToOriginalLayerId.keys, redone.mergedLayer.id)
            is com.artstudio.history.FlattenDocumentCommand -> layerManager.reapplyMergedStrokes(redone.strokeIdToOriginalLayerId.keys, redone.flattenedLayer.id)
            is com.artstudio.history.ApplyFilterCommand -> {
                val buf = _layerPixelBuffers[redone.layerId]
                if (buf != null) {
                    redone.restorePixels(buf, usePre = false)
                }
            }
            is com.artstudio.history.FillCommand -> {
                val buf = _layerPixelBuffers[redone.layerId]
                if (buf != null) {
                    redone.restorePixels(buf, usePre = false)
                }
            }
            is com.artstudio.history.PixelModifierCommand -> {
                val buf = _layerPixelBuffers[redone.layerId]
                if (buf != null) {
                    redone.restorePatch(buf, width, usePre = false)
                }
            }
            is com.artstudio.history.ApplyGradientCommand -> {
                val buf = _layerPixelBuffers[redone.layerId]
                if (buf != null && redone.postGradientPixels.size == buf.size) {
                    System.arraycopy(redone.postGradientPixels, 0, buf, 0, buf.size)
                }
            }
            is com.artstudio.history.DeformLayerCommand -> {
                val buf = _layerPixelBuffers[redone.layerId]
                if (buf != null && redone.postDeformPixels.size == buf.size) {
                    System.arraycopy(redone.postDeformPixels, 0, buf, 0, buf.size)
                }
            }
            else -> {}
        }
        return true
    }

    override fun onTrimMemory(level: MemoryTrimLevel) {
        historyManager.onTrimMemory(level)
        BitmapPool.onTrimMemory(level)
    }

    override fun getLayerPixels(layerId: String): IntArray? = _layerPixelBuffers[layerId]

    override fun setLayerPixels(layerId: String, pixels: IntArray) {
        _layerPixelBuffers[layerId] = pixels
    }

    override fun getOrRenderLayerPixels(layerId: String): IntArray {
        val layer = authoritativeDocument.findLayerById(layerId) as? com.artstudio.document.model.LayerNode.RasterLayer
        val layerW = layer?.width ?: width
        val layerH = layer?.height ?: height
        val existing = _layerPixelBuffers[layerId]
        val activeId = authoritativeDocument.activeLayerId
        val layerStrokes = _committedStrokes.filter {
            (it.layerId == layerId || (it.layerId.isEmpty() && layerId == activeId)) && !it.isMaskStroke
        }

        if (layerStrokes.isEmpty()) {
            if (existing != null && existing.size == layerW * layerH) {
                return existing
            }
            val blank = IntArray(layerW * layerH) { 0 }
            _layerPixelBuffers[layerId] = blank
            return blank
        }

        val bmp = try {
            if (existing != null && existing.size == layerW * layerH) {
                Bitmap.createBitmap(existing, layerW, layerH, Bitmap.Config.ARGB_8888)?.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                Bitmap.createBitmap(layerW, layerH, Bitmap.Config.ARGB_8888)
            }
        } catch (e: Throwable) {
            null
        }

        if (bmp != null) {
            val canvas = android.graphics.Canvas(bmp)
            val strokeEngine = RasterStrokeEngine()
            for (stroke in layerStrokes) {
                strokeEngine.renderStroke(canvas, stroke)
            }
            val resultPixels = IntArray(layerW * layerH)
            bmp.getPixels(resultPixels, 0, layerW, 0, 0, layerW, layerH)
            _layerPixelBuffers[layerId] = resultPixels
            return resultPixels
        } else {
            // Software rendering fallback for JVM environments (e.g. unit tests where android.graphics is stubbed)
            val resultPixels = existing?.copyOf() ?: IntArray(layerW * layerH) { 0 }
            for (stroke in layerStrokes) {
                renderStrokeSoftware(resultPixels, layerW, layerH, stroke)
            }
            _layerPixelBuffers[layerId] = resultPixels
            return resultPixels
        }
    }

    private fun renderStrokeSoftware(pixels: IntArray, width: Int, height: Int, stroke: BrushStroke) {
        val color = stroke.color
        val baseRadius = (stroke.baseSize / 2f).coerceAtLeast(1f)
        val pts = stroke.points
        if (pts.isEmpty()) return

        fun stampDab(cx: Float, cy: Float, pressure: Float) {
            val r = (baseRadius * pressure).toInt().coerceAtLeast(1)
            val minX = (cx - r).toInt().coerceIn(0, width - 1)
            val maxX = (cx + r).toInt().coerceIn(0, width - 1)
            val minY = (cy - r).toInt().coerceIn(0, height - 1)
            val maxY = (cy + r).toInt().coerceIn(0, height - 1)
            val r2 = r * r
            for (y in minY..maxY) {
                val dy = y - cy
                val rowOffset = y * width
                for (x in minX..maxX) {
                    val dx = x - cx
                    if (dx * dx + dy * dy <= r2) {
                        pixels[rowOffset + x] = color
                    }
                }
            }
        }

        if (pts.size == 1) {
            stampDab(pts[0].x, pts[0].y, pts[0].pressure)
            return
        }

        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val dist = kotlin.math.hypot(dx, dy)
            val steps = kotlin.math.max(1, (dist / 2f).toInt())
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val x = p0.x + dx * t
                val y = p0.y + dy * t
                val pressure = p0.pressure + (p1.pressure - p0.pressure) * t
                stampDab(x, y, pressure)
            }
        }
    }

    override fun getAllLayerPixelBuffers(): Map<String, IntArray> {
        val result = mutableMapOf<String, IntArray>()
        for (node in authoritativeDocument.allLayersFlat()) {
            if (node is com.artstudio.document.model.LayerNode.RasterLayer) {
                result[node.id] = getOrRenderLayerPixels(node.id).clone()
            }
        }
        return result
    }

    override fun clearCommittedStrokes() {
        _committedStrokes.clear()
        thumbnailGenerator.clearCache()
        historyManager.reinitializeForReopen(authoritativeDocument, preserveHistory = false)
    }
}
