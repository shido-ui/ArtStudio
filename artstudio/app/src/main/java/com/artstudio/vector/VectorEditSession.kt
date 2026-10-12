package com.artstudio.vector

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.history.*
import kotlin.math.abs

/**
 * Interactive tools available when working in vector path editing mode.
 */
enum class VectorToolMode(val label: String) {
    SELECT("Select & Move"),
    ANCHOR_EDIT("Anchor Edit"),
    PEN("Pen (Bezier)"),
    LINE("Line"),
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse"),
    POLYGON("Polygon")
}

/**
 * Handle type for tangent manipulation.
 */
enum class HandleType {
    NONE,
    HANDLE_IN,
    HANDLE_OUT
}

/**
 * Stateful interactive session managing vector geometry creation, anchor editing,
 * handle manipulation, and explicit rasterization commands.
 */
class VectorEditSession(
    var activeLayerId: String,
    var currentTool: VectorToolMode = VectorToolMode.SELECT,
    var activeStyle: VectorStyle = VectorStyle(
        strokeColor = 0xFF000000L,
        strokeWidth = 4.0f,
        fillColor = null
    )
) {
    var selectedObjectId: String? = null
    var selectedAnchorIndex: Int? = null
    var selectedHandleType: HandleType = HandleType.NONE

    private var dragStartX: Float = 0f
    private var dragStartY: Float = 0f
    private var isDragging: Boolean = false
    private var initialObjectState: VectorObject? = null
    var inProgressObject: VectorObject? = null

    /**
     * Finds the currently selected [VectorObject] from the document.
     */
    fun getSelectedObject(document: ArtDocument): VectorObject? {
        val layer = document.findLayerById(activeLayerId) as? LayerNode.VectorPathLayer ?: return null
        return layer.vectorObjects.firstOrNull { it.id == selectedObjectId }
    }

    /**
     * Hit tests document coordinates to select an object, anchor, or handle.
     */
    fun handlePointerDown(document: ArtDocument, x: Float, y: Float): EditCommand? {
        dragStartX = x
        dragStartY = y
        isDragging = true

        val layer = document.findLayerById(activeLayerId) as? LayerNode.VectorPathLayer ?: return null
        val currentObj = layer.vectorObjects.firstOrNull { it.id == selectedObjectId }

        when (currentTool) {
            VectorToolMode.SELECT -> {
                // Find top-most object whose bounds include (x, y)
                val hitObj = layer.vectorObjects.reversed().firstOrNull {
                    it.computeBounds().contains(x, y)
                }
                selectedObjectId = hitObj?.id
                selectedAnchorIndex = null
                selectedHandleType = HandleType.NONE
                initialObjectState = hitObj
                return null
            }

            VectorToolMode.ANCHOR_EDIT -> {
                if (currentObj != null) {
                    // 1. Check handle in
                    val hIn = VectorGeometryEngine.hitTestHandleIn(currentObj, x, y)
                    if (hIn != null) {
                        selectedAnchorIndex = hIn
                        selectedHandleType = HandleType.HANDLE_IN
                        initialObjectState = currentObj
                        return null
                    }
                    // 2. Check handle out
                    val hOut = VectorGeometryEngine.hitTestHandleOut(currentObj, x, y)
                    if (hOut != null) {
                        selectedAnchorIndex = hOut
                        selectedHandleType = HandleType.HANDLE_OUT
                        initialObjectState = currentObj
                        return null
                    }
                    // 3. Check anchor point
                    val aIdx = VectorGeometryEngine.hitTestAnchor(currentObj, x, y)
                    if (aIdx != null) {
                        selectedAnchorIndex = aIdx
                        selectedHandleType = HandleType.NONE
                        initialObjectState = currentObj
                        return null
                    }
                }
                // Try selecting another object
                val hitObj = layer.vectorObjects.reversed().firstOrNull {
                    it.computeBounds().contains(x, y)
                }
                selectedObjectId = hitObj?.id
                selectedAnchorIndex = null
                selectedHandleType = HandleType.NONE
                initialObjectState = hitObj
                return null
            }

            VectorToolMode.PEN -> {
                // If we have an existing open Bezier path being constructed
                val obj = currentObj ?: VectorObject.createBezierPath(
                    anchors = emptyList(),
                    isClosed = false,
                    style = activeStyle,
                    name = "Path ${layer.vectorObjects.size + 1}"
                ).also {
                    selectedObjectId = it.id
                }

                val newAnchor = VectorAnchor(x, y)
                val updated = VectorGeometryEngine.insertAnchor(obj, obj.anchors.size, newAnchor)
                selectedAnchorIndex = updated.anchors.size - 1
                selectedHandleType = HandleType.HANDLE_OUT
                initialObjectState = obj
                inProgressObject = updated
                return null
            }

            VectorToolMode.LINE -> {
                inProgressObject = VectorObject.createLine(x, y, x, y, activeStyle)
                return null
            }

            VectorToolMode.RECTANGLE -> {
                inProgressObject = VectorObject.createRectangle(x, y, 0f, 0f, 0f, 0f, activeStyle)
                return null
            }

            VectorToolMode.ELLIPSE -> {
                inProgressObject = VectorObject.createEllipse(x, y, 0f, 0f, activeStyle)
                return null
            }

            VectorToolMode.POLYGON -> {
                val obj = currentObj ?: VectorObject.createPolygon(emptyList(), activeStyle).also {
                    selectedObjectId = it.id
                }
                val newAnchor = VectorAnchor(x, y)
                val updated = VectorGeometryEngine.insertAnchor(obj, obj.anchors.size, newAnchor)
                selectedAnchorIndex = updated.anchors.size - 1
                initialObjectState = obj
                inProgressObject = updated
                return null
            }
        }
    }

    /**
     * Handles pointer drag for translating objects, dragging handles, or expanding shapes.
     */
    fun handlePointerMove(x: Float, y: Float) {
        if (!isDragging) return
        val dx = x - dragStartX
        val dy = y - dragStartY

        when (currentTool) {
            VectorToolMode.SELECT -> {
                val init = initialObjectState ?: return
                inProgressObject = VectorGeometryEngine.translate(init, dx, dy)
            }

            VectorToolMode.ANCHOR_EDIT -> {
                val init = initialObjectState ?: return
                val aIdx = selectedAnchorIndex ?: return
                when (selectedHandleType) {
                    HandleType.NONE -> {
                        inProgressObject = VectorGeometryEngine.moveAnchor(init, aIdx, x, y)
                    }
                    HandleType.HANDLE_IN -> {
                        inProgressObject = VectorGeometryEngine.moveHandleIn(init, aIdx, x, y)
                    }
                    HandleType.HANDLE_OUT -> {
                        inProgressObject = VectorGeometryEngine.moveHandleOut(init, aIdx, x, y)
                    }
                }
            }

            VectorToolMode.PEN -> {
                // Dragging outgoing handle while placing anchor
                val prog = inProgressObject ?: return
                val aIdx = selectedAnchorIndex ?: return
                inProgressObject = VectorGeometryEngine.moveHandleOut(prog, aIdx, x, y)
            }

            VectorToolMode.LINE -> {
                inProgressObject = VectorObject.createLine(dragStartX, dragStartY, x, y, activeStyle)
            }

            VectorToolMode.RECTANGLE -> {
                val left = minOf(dragStartX, x)
                val top = minOf(dragStartY, y)
                val w = abs(x - dragStartX)
                val h = abs(y - dragStartY)
                inProgressObject = VectorObject.createRectangle(left, top, w, h, 0f, 0f, activeStyle)
            }

            VectorToolMode.ELLIPSE -> {
                val rx = abs(x - dragStartX)
                val ry = abs(y - dragStartY)
                inProgressObject = VectorObject.createEllipse(dragStartX, dragStartY, rx, ry, activeStyle)
            }

            VectorToolMode.POLYGON -> {
                // Nothing dynamic during drag for polygon
            }
        }
    }

    /**
     * Commits the interactive modification or shape creation into an undoable [EditCommand].
     */
    fun handlePointerUp(document: ArtDocument, x: Float, y: Float): EditCommand? {
        isDragging = false
        val finalObj = inProgressObject
        inProgressObject = null

        if (finalObj == null) {
            return null
        }

        val layer = document.findLayerById(activeLayerId) as? LayerNode.VectorPathLayer ?: return null
        val existing = layer.vectorObjects.firstOrNull { it.id == finalObj.id }

        return if (existing != null) {
            UpdateVectorObjectCommand(
                targetLayerId = activeLayerId,
                previousObject = existing,
                newObject = finalObj
            )
        } else {
            selectedObjectId = finalObj.id
            AddVectorObjectCommand(
                targetLayerId = activeLayerId,
                vectorObject = finalObj
            )
        }
    }

    /**
     * Toggles smooth constraint on the selected anchor.
     */
    fun toggleAnchorSmooth(document: ArtDocument): EditCommand? {
        val obj = getSelectedObject(document) ?: return null
        val aIdx = selectedAnchorIndex ?: return null
        val anchor = obj.anchors.getOrNull(aIdx) ?: return null
        val updated = VectorGeometryEngine.setAnchorSmooth(obj, aIdx, !anchor.isSmooth)
        return UpdateVectorObjectCommand(
            targetLayerId = activeLayerId,
            previousObject = obj,
            newObject = updated,
            description = "Toggle Anchor Smooth"
        )
    }

    /**
     * Deletes the currently selected anchor point.
     */
    fun deleteSelectedAnchor(document: ArtDocument): EditCommand? {
        val obj = getSelectedObject(document) ?: return null
        val aIdx = selectedAnchorIndex ?: return null
        val updated = VectorGeometryEngine.deleteAnchor(obj, aIdx)
        selectedAnchorIndex = null
        selectedHandleType = HandleType.NONE
        return UpdateVectorObjectCommand(
            targetLayerId = activeLayerId,
            previousObject = obj,
            newObject = updated,
            description = "Delete Anchor Point"
        )
    }

    /**
     * Explicitly rasterizes the active vector layer.
     */
    fun rasterizeActiveLayer(document: ArtDocument, createNewLayer: Boolean = false): RasterizeVectorLayerCommand {
        return RasterizeVectorLayerCommand.create(document, activeLayerId, createNewLayer)
    }
}
