package com.artstudio.guides

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.artstudio.color.ColorData
import com.artstudio.color.EyedropperSampleMode
import com.artstudio.core.ViewportTransform
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import java.util.UUID

/**
 * Manages reference image overlays, coordinates, color sampling, and isolation.
 * Strictly maintains isolation between reference overlays and authoritative artwork layers
 * unless explicitly imported by the user.
 */
class ReferenceImageManager {

    private val _references = mutableListOf<ReferenceImageOverlay>()
    val references: List<ReferenceImageOverlay>
        get() = _references.toList()

    var activeReferenceId: String? = null

    val activeReference: ReferenceImageOverlay?
        get() = _references.find { it.id == activeReferenceId } ?: _references.firstOrNull()

    /**
     * Adds or updates a reference image overlay.
     */
    fun addReference(overlay: ReferenceImageOverlay) {
        _references.removeAll { it.id == overlay.id }
        _references.add(overlay)
        activeReferenceId = overlay.id
    }

    /**
     * Removes a reference overlay by ID.
     */
    fun removeReference(id: String) {
        _references.removeAll { it.id == id }
        if (activeReferenceId == id) {
            activeReferenceId = _references.firstOrNull()?.id
        }
    }

    /**
     * Updates position in document space.
     */
    fun updatePosition(id: String, x: Float, y: Float): Boolean {
        val idx = _references.indexOfFirst { it.id == id }
        if (idx == -1) return false
        _references[idx] = _references[idx].copy(x = x, y = y)
        return true
    }

    /**
     * Updates size in document space.
     */
    fun updateSize(id: String, width: Float, height: Float): Boolean {
        if (width <= 0f || height <= 0f) return false
        val idx = _references.indexOfFirst { it.id == id }
        if (idx == -1) return false
        _references[idx] = _references[idx].copy(width = width, height = height)
        return true
    }

    /**
     * Updates opacity (0.0 to 1.0).
     */
    fun updateOpacity(id: String, opacity: Float): Boolean {
        val clamped = opacity.coerceIn(0f, 1f)
        val idx = _references.indexOfFirst { it.id == id }
        if (idx == -1) return false
        _references[idx] = _references[idx].copy(opacity = clamped)
        return true
    }

    /**
     * Toggles visibility without modifying artwork layers.
     */
    fun toggleVisibility(id: String): Boolean {
        val idx = _references.indexOfFirst { it.id == id }
        if (idx == -1) return false
        _references[idx] = _references[idx].copy(visible = !_references[idx].visible)
        return true
    }

    /**
     * Samples color from reference image at document coordinates (docX, docY).
     */
    fun sampleColor(
        id: String,
        docX: Float,
        docY: Float,
        mode: EyedropperSampleMode = EyedropperSampleMode.POINT_1X1
    ): ColorData? {
        val ref = _references.find { it.id == id } ?: return null
        return ref.sampleColorAtDocumentCoord(docX, docY, mode)
    }

    /**
     * Explicitly imports the reference image into the document layer tree as a real paint layer.
     *
     * INVARIANT: References are NEVER merged into paint layers or document exports automatically.
     * They are only incorporated through this explicit user-invoked operation.
     */
    fun importReferenceToLayer(
        document: ArtDocument,
        referenceId: String,
        layerName: String? = null
    ): Pair<ArtDocument, LayerNode.RasterLayer> {
        val ref = _references.find { it.id == referenceId }
            ?: throw IllegalArgumentException("Reference image '$referenceId' not found")

        val newLayerId = "ref_layer_${UUID.randomUUID()}"
        val name = layerName ?: "Imported: ${ref.name}"

        val newLayer = LayerNode.RasterLayer(
            id = newLayerId,
            name = name,
            width = document.canvas.width,
            height = document.canvas.height,
            visible = true,
            opacity = ref.opacity,
            transform = com.artstudio.document.model.LayerTransform(
                translationX = ref.x,
                translationY = ref.y
            )
        )

        // Add to document root group
        val updatedChildren = document.rootGroup.children + newLayer
        val updatedRoot = document.rootGroup.copy(children = updatedChildren)
        val updatedDoc = document.copy(
            rootGroup = updatedRoot,
            selectedLayerId = newLayerId,
            updatedAt = System.currentTimeMillis()
        )

        return Pair(updatedDoc, newLayer)
    }

    /**
     * Checks whether a reference overlay has been explicitly imported into the document.
     */
    fun isMergedIntoDocument(document: ArtDocument, referenceId: String): Boolean {
        val ref = _references.find { it.id == referenceId } ?: return false
        return document.allLayersFlat().any { it.name.contains(ref.name) }
    }

    /**
     * Renders visible reference overlays to an interactive canvas surface.
     * Respects viewport transformation when pinned to canvas.
     */
    fun renderOverlays(
        canvas: Canvas,
        viewport: ViewportTransform,
        paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    ) {
        for (ref in _references) {
            if (!ref.visible || ref.opacity <= 0f) continue
            val pxs = ref.pixels ?: continue
            if (ref.bitmapWidth <= 0 || ref.bitmapHeight <= 0) continue

            // Create temporary or cached bitmap
            val bmp = Bitmap.createBitmap(pxs, ref.bitmapWidth, ref.bitmapHeight, Bitmap.Config.ARGB_8888)

            paint.alpha = (ref.opacity * 255f).toInt().coerceIn(0, 255)

            if (ref.pinnedToCanvas) {
                // Rendered inside canvas coordinate space
                val dstRect = RectF(ref.x, ref.y, ref.x + ref.width, ref.y + ref.height)
                canvas.drawBitmap(bmp, null, dstRect, paint)
            } else {
                // Floating screen-pinned overlay
                canvas.save()
                canvas.setMatrix(android.graphics.Matrix())
                val dstRect = RectF(ref.x, ref.y, ref.x + ref.width, ref.y + ref.height)
                canvas.drawBitmap(bmp, null, dstRect, paint)
                canvas.restore()
            }
        }
    }
}
