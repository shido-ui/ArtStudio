package com.artstudio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.artstudio.core.*

class ArtStudioDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), PointerInputListener {

    val viewport = ViewportTransform()
    var document: DocumentState = DefaultDocumentState(2048, 2048)
        private set
    val renderer: NativeCanvasRenderer = NativeCanvasRenderer()
    val inputPipeline = PointerInputPipeline(viewport, listener = this)

    val referenceManager: com.artstudio.guides.ReferenceImageManager = com.artstudio.guides.ReferenceImageManager()

    var drawingAidsConfig: com.artstudio.guides.DrawingAidsConfig = com.artstudio.guides.DrawingAidsConfig()
        set(value) {
            field = value
            inputPipeline.drawingAidsConfig = value
            renderer.drawingAidsConfig = value
            invalidate()
        }

    var onMetricsUpdated: ((renderTimeMs: Float, strokeCount: Int, zoomLevel: Float) -> Unit)? = null
    var onStrokeCommitted: ((BrushStroke) -> Unit)? = null

    private var lastRenderDurationMs = 0f
    private var sampleBitmapLoaded = false

    init {
        renderer.referenceManager = referenceManager
        renderer.drawingAidsConfig = drawingAidsConfig
        inputPipeline.drawingAidsConfig = drawingAidsConfig
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewport.viewportWidth = w.toFloat()
        viewport.viewportHeight = h.toFloat()
        viewport.documentWidth = document.width.toFloat()
        viewport.documentHeight = document.height.toFloat()
        // Center the document inside the visible viewport
        if (oldw == 0 && oldh == 0 && w > 0 && h > 0) {
            viewport.fitToScreen(document.width.toFloat(), document.height.toFloat(), w.toFloat(), h.toFloat())
        }
        com.artstudio.platform.PlatformWindowAndInputIntegration.updateSystemGestureExclusionRects(this)
    }

    var isFillToolActive: Boolean = false
    var fillConfig: com.artstudio.fill.FillConfig = com.artstudio.fill.FillConfig()

    var isEyedropperActive: Boolean = false
    var onColorPicked: ((Int) -> Unit)? = null

    var isPixelOpsActive: Boolean = false
    var pixelOpsConfig: com.artstudio.pixelops.PixelModifierConfig = com.artstudio.pixelops.PixelModifierConfig()
    private val pixelModifierSession = com.artstudio.pixelops.PixelModifierSession()

    var isSelectionToolActive: Boolean = false
    private var selectionStartX: Float = 0f
    private var selectionStartY: Float = 0f

    var deformationSession: com.artstudio.deformation.DeformationSession? = null
    private var draggedMeshRow: Int = -1
    private var draggedMeshCol: Int = -1
    private var lastDeformTouchX: Float = 0f
    private var lastDeformTouchY: Float = 0f
    private var deformationPreviewBitmap: Bitmap? = null

    fun startDeformation(layerId: String? = null): com.artstudio.deformation.DeformationSession {
        val targetLayerId = layerId ?: document.authoritativeDocument.activeLayerId
        val ds = document as? DefaultDocumentState
        val pixels = ds?.getOrRenderLayerPixels(targetLayerId)
            ?: IntArray(document.width * document.height)
        val session = com.artstudio.deformation.DeformationSession(
            layerId = targetLayerId,
            originalWidth = document.width,
            originalHeight = document.height,
            originalPixels = pixels.clone()
        )
        this.deformationSession = session
        updateDeformationPreviewBitmap(session)
        invalidate()
        return session
    }

    fun commitDeformation(): Boolean {
        val session = deformationSession ?: return false
        val lm = document.layerManager ?: return false
        val result = session.commit(lm)
        val ds = document as? DefaultDocumentState
        ds?.setLayerPixels(session.layerId, result.command.postDeformPixels)
        renderer.updateLayerPixels(session.layerId, result.command.postDeformPixels)
        deformationSession = null
        deformationPreviewBitmap = null
        invalidate()
        return true
    }

    fun resetDeformation() {
        val session = deformationSession ?: return
        session.reset()
        updateDeformationPreviewBitmap(session)
        invalidate()
    }

    fun cancelDeformation() {
        val session = deformationSession ?: return
        session.cancel()
        deformationSession = null
        deformationPreviewBitmap = null
        invalidate()
    }

    private fun updateDeformationPreviewBitmap(session: com.artstudio.deformation.DeformationSession) {
        val w = session.previewWidth
        val h = session.previewHeight
        val bmp = deformationPreviewBitmap?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { deformationPreviewBitmap = it }
        bmp.setPixels(session.previewPixels, 0, w, 0, 0, w, h)
    }

    private var isStylusHovering: Boolean = false
    private var hoverX: Float = 0f
    private var hoverY: Float = 0f

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (com.artstudio.platform.PlatformWindowAndInputIntegration.isStylusHoverEvent(event)) {
            hoverX = event.x
            hoverY = event.y
            isStylusHovering = (event.actionMasked != MotionEvent.ACTION_HOVER_EXIT)
            invalidate()
            return true
        }
        return super.onHoverEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        com.artstudio.platform.PlatformWindowAndInputIntegration.requestStylusUnbufferedDispatch(this, event)
        val activeDefSession = deformationSession
        if (activeDefSession != null) {
            val canvasPt = viewport.screenToCanvas(event.x, event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastDeformTouchX = canvasPt.x
                    lastDeformTouchY = canvasPt.y
                    if (activeDefSession.activeToolMode == com.artstudio.deformation.DeformToolMode.WARP_MESH) {
                        var closestDist = Float.MAX_VALUE
                        var bestRow = -1
                        var bestCol = -1
                        val mesh = activeDefSession.warpMesh
                        for (r in 0 until mesh.rows) {
                            for (c in 0 until mesh.cols) {
                                val v = mesh.getVertex(r, c)
                                val vx = v.x / activeDefSession.previewScale
                                val vy = v.y / activeDefSession.previewScale
                                val d = (canvasPt.x - vx) * (canvasPt.x - vx) + (canvasPt.y - vy) * (canvasPt.y - vy)
                                if (d < closestDist) {
                                    closestDist = d
                                    bestRow = r
                                    bestCol = c
                                }
                            }
                        }
                        if (closestDist < 2500f) {
                            draggedMeshRow = bestRow
                            draggedMeshCol = bestCol
                        } else {
                            draggedMeshRow = -1
                            draggedMeshCol = -1
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (activeDefSession.activeToolMode == com.artstudio.deformation.DeformToolMode.WARP_MESH) {
                        if (draggedMeshRow != -1 && draggedMeshCol != -1) {
                            activeDefSession.updateMeshVertex(
                                draggedMeshRow,
                                draggedMeshCol,
                                canvasPt.x * activeDefSession.previewScale,
                                canvasPt.y * activeDefSession.previewScale
                            )
                            activeDefSession.renderPreview()
                            updateDeformationPreviewBitmap(activeDefSession)
                            invalidate()
                        }
                    } else {
                        val dx = canvasPt.x - lastDeformTouchX
                        val dy = canvasPt.y - lastDeformTouchY
                        activeDefSession.applyPush(canvasPt.x, canvasPt.y, dx, dy)
                        activeDefSession.renderPreview()
                        updateDeformationPreviewBitmap(activeDefSession)
                        invalidate()
                    }
                    lastDeformTouchX = canvasPt.x
                    lastDeformTouchY = canvasPt.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    draggedMeshRow = -1
                    draggedMeshCol = -1
                }
            }
            return true
        }
        if (isEyedropperActive) {
            val canvasPt = viewport.screenToCanvas(event.x, event.y)
            val colorData = sampleColorAt(canvasPt.x.toInt(), canvasPt.y.toInt())
            onColorPicked?.invoke(colorData.argbInt)
            return true
        }

        if (isFillToolActive) {
            if (event.action == MotionEvent.ACTION_UP) {
                val canvasPt = viewport.screenToCanvas(event.x, event.y)
                performFill(canvasPt.x.toInt(), canvasPt.y.toInt())
            }
            return true
        }

        if (isPixelOpsActive) {
            val canvasPt = viewport.screenToCanvas(event.x, event.y)
            val activeLayerId = document.authoritativeDocument.activeLayerId
            val ds = document as? DefaultDocumentState
            val targetPixels = ds?.getOrRenderLayerPixels(activeLayerId)
            if (targetPixels != null) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pixelModifierSession.beginStroke(
                            layerId = activeLayerId,
                            targetPixels = targetPixels,
                            width = document.width,
                            height = document.height,
                            startX = canvasPt.x,
                            startY = canvasPt.y,
                            initialPressure = event.pressure.coerceAtLeast(0.1f),
                            config = pixelOpsConfig,
                            selectionMask = document.selectionManager?.activeMask
                        )
                        renderer.updateLayerPixels(activeLayerId, targetPixels)
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        pixelModifierSession.addPoint(canvasPt.x, canvasPt.y, event.pressure.coerceAtLeast(0.1f))
                        renderer.updateLayerPixels(activeLayerId, targetPixels)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        val cmd = pixelModifierSession.endStroke()
                        if (cmd != null) {
                            document.historyManager?.execute(cmd)
                        }
                        renderer.updateLayerPixels(activeLayerId, targetPixels)
                        invalidate()
                    }
                }
            }
            return true
        }

        if (isSelectionToolActive) {
            val canvasPt = viewport.screenToCanvas(event.x, event.y)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    selectionStartX = canvasPt.x
                    selectionStartY = canvasPt.y
                }
                MotionEvent.ACTION_UP -> {
                    val minX = minOf(selectionStartX, canvasPt.x)
                    val minY = minOf(selectionStartY, canvasPt.y)
                    val maxX = maxOf(selectionStartX, canvasPt.x)
                    val maxY = maxOf(selectionStartY, canvasPt.y)
                    if (maxX - minX > 2f && maxY - minY > 2f) {
                        document.selectionManager?.selectShape(com.artstudio.selection.SelectionShape.Rectangle(minX, minY, maxX, maxY))
                        invalidate()
                    }
                }
            }
            return true
        }

        val handled = inputPipeline.onTouchEvent(event)
        if (handled) {
            invalidate()
        }
        return handled
    }

    fun performFill(docX: Int, docY: Int): Boolean {
        val layerMgr = document.layerManager ?: return false
        val activeLayerId = document.authoritativeDocument.activeLayerId
        val layerPixels = (document as? DefaultDocumentState)?.getOrRenderLayerPixels(activeLayerId)
        val result = layerMgr.applyFillToLayer(
            layerId = activeLayerId,
            seedX = docX,
            seedY = docY,
            config = fillConfig,
            compositePixels = layerPixels
        )
        if (result != null && result.success) {
            if (result.resultPixels != null) {
                (document as? DefaultDocumentState)?.setLayerPixels(activeLayerId, result.resultPixels)
                renderer.updateLayerPixels(activeLayerId, result.resultPixels)
            }
            invalidate()
            return true
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val startTime = System.nanoTime()
        renderer.render(canvas, viewport, document, inputPipeline.activeStroke)

        val defSession = deformationSession
        val previewBmp = deformationPreviewBitmap
        if (defSession != null && previewBmp != null) {
            val srcRect = android.graphics.Rect(0, 0, previewBmp.width, previewBmp.height)
            val p1 = viewport.canvasToScreen(0f, 0f)
            val p2 = viewport.canvasToScreen(document.width.toFloat(), document.height.toFloat())
            val dstRect = android.graphics.RectF(p1.x, p1.y, p2.x, p2.y)
            canvas.drawBitmap(previewBmp, srcRect, dstRect, null)

            if (defSession.activeToolMode == com.artstudio.deformation.DeformToolMode.WARP_MESH) {
                val linePaint = Paint().apply {
                    color = Color.argb(180, 0, 180, 255)
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                val dotPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val dotBorderPaint = Paint().apply {
                    color = Color.argb(220, 0, 120, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                    isAntiAlias = true
                }
                val mesh = defSession.warpMesh
                for (r in 0 until mesh.rows) {
                    for (c in 0 until mesh.cols) {
                        val v = mesh.getVertex(r, c)
                        val sp = viewport.canvasToScreen(v.x / defSession.previewScale, v.y / defSession.previewScale)
                        if (c + 1 < mesh.cols) {
                            val rightV = mesh.getVertex(r, c + 1)
                            val rightSp = viewport.canvasToScreen(rightV.x / defSession.previewScale, rightV.y / defSession.previewScale)
                            canvas.drawLine(sp.x, sp.y, rightSp.x, rightSp.y, linePaint)
                        }
                        if (r + 1 < mesh.rows) {
                            val downV = mesh.getVertex(r + 1, c)
                            val downSp = viewport.canvasToScreen(downV.x / defSession.previewScale, downV.y / defSession.previewScale)
                            canvas.drawLine(sp.x, sp.y, downSp.x, downSp.y, linePaint)
                        }
                        canvas.drawCircle(sp.x, sp.y, 8f, dotPaint)
                        canvas.drawCircle(sp.x, sp.y, 8f, dotBorderPaint)
                    }
                }
            } else {
                val cursorPaint = Paint().apply {
                    color = Color.argb(160, 255, 100, 100)
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                val cursorScreen = viewport.canvasToScreen(lastDeformTouchX, lastDeformTouchY)
                val cursorRadiusScreen = defSession.brush.radius * viewport.zoom
                canvas.drawCircle(cursorScreen.x, cursorScreen.y, cursorRadiusScreen, cursorPaint)
            }
        }

        if (isStylusHovering) {
            val hoverRadius = (inputPipeline.activeBrushConfig.size * viewport.zoom / 2f).coerceIn(4f, 400f)
            val hoverPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = android.graphics.Color.argb(180, 200, 200, 200)
                isAntiAlias = true
            }
            canvas.drawCircle(hoverX, hoverY, hoverRadius, hoverPaint)
        }

        val durationNs = System.nanoTime() - startTime
        lastRenderDurationMs = durationNs / 1_000_000f

        onMetricsUpdated?.invoke(
            lastRenderDurationMs,
            document.committedStrokes.size + (if (inputPipeline.activeStroke != null) 1 else 0),
            viewport.zoom
        )
    }

    override fun onStrokeStart(stroke: BrushStroke) {
        document.historyManager?.beginInFlight("Drawing stroke ${stroke.id}")
        invalidate()
    }

    override fun onStrokeUpdate(stroke: BrushStroke, newPoints: List<StrokePoint>) {
        if (newPoints.isNotEmpty()) {
            val pad = stroke.baseSize * 1.5f
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (pt in newPoints) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
            val p1 = viewport.canvasToScreen(minX - pad, minY - pad)
            val p2 = viewport.canvasToScreen(maxX + pad, maxY + pad)
            val left = minOf(p1.x, p2.x).toInt() - 2
            val top = minOf(p1.y, p2.y).toInt() - 2
            val right = maxOf(p1.x, p2.x).toInt() + 2
            val bottom = maxOf(p1.y, p2.y).toInt() + 2
            invalidate(left, top, right, bottom)
        } else {
            invalidate()
        }
    }

    override fun onStrokeEnd(stroke: BrushStroke) {
        document.historyManager?.completeInFlight()
        val allStrokes = mutableListOf(stroke)
        if (drawingAidsConfig.symmetry.type != com.artstudio.guides.SymmetryType.NONE) {
            val symStrokes = com.artstudio.guides.DrawingAidsEngine.generateSymmetricStrokes(stroke, drawingAidsConfig.symmetry)
            allStrokes.addAll(symStrokes)
        }
        document.addCommittedStrokes(allStrokes)
        for (s in allStrokes) {
            renderer.bakeFinishedStroke(s, document)
            onStrokeCommitted?.invoke(s)
        }
        invalidate()
    }

    override fun onStrokeCancelled(strokeId: String) {
        document.historyManager?.cancelInFlight()
        invalidate()
    }

    fun undo(): Boolean {
        val result = document.undo()
        if (result) {
            renderer.rebakeAll(document)
            invalidate()
        }
        return result
    }

    fun redo(): Boolean {
        val result = document.redo()
        if (result) {
            renderer.rebakeAll(document)
            invalidate()
        }
        return result
    }

    override fun onViewportPanZoom(
        dx: Float,
        dy: Float,
        scaleFactor: Float,
        focalX: Float,
        focalY: Float
    ) {
        invalidate()
    }

    fun onPause() {
        inputPipeline.pause()
        invalidate()
    }

    fun onResume() {
        inputPipeline.resume()
        invalidate()
    }

    fun addSampleStroke() {
        val sampleStroke = BrushStroke(
            id = "sample_${System.currentTimeMillis()}",
            color = Color.rgb((50..220).random(), (50..220).random(), (50..220).random()),
            baseSize = (10..40).random().toFloat(),
            toolType = ToolType.STYLUS
        )

        // Generate an S-curve sample stroke
        val startX = (300..1500).random().toFloat()
        val startY = (300..1500).random().toFloat()
        val step = 15f
        for (i in 0..40) {
            val angle = i * 0.15f
            val x = startX + i * step
            val y = startY + Math.sin(angle.toDouble()).toFloat() * 120f
            val pressure = 0.3f + 0.6f * (i / 40f)
            sampleStroke.points.add(StrokePoint(x, y, pressure))
        }

        document.addCommittedStroke(sampleStroke)
        invalidate()
    }

    fun loadNativeProject(nativeProject: com.artstudio.document.io.NativeProject) {
        val doc = nativeProject.document
        val newState = DefaultDocumentState(
            width = doc.canvas.width,
            height = doc.canvas.height,
            backgroundColor = doc.canvas.backgroundColor.toInt(),
            artDocument = doc,
            initialPixelBuffers = nativeProject.layerPixelBuffers,
            initialMaskBuffers = nativeProject.layerMaskBuffers
        )
        setDocumentState(newState)
    }

    fun setDocumentState(newState: DocumentState) {
        this.document = newState
        renderer.rebakeAll(newState)
        viewport.documentWidth = newState.width.toFloat()
        viewport.documentHeight = newState.height.toFloat()
        if (width > 0 && height > 0) {
            viewport.fitToScreen(newState.width.toFloat(), newState.height.toFloat(), width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    fun onTrimMemory(level: com.artstudio.core.MemoryTrimLevel) {
        document.onTrimMemory(level)
        renderer.surfaceManager.onTrimMemory(level)
        invalidate()
    }

    fun zoomIn() {
        val focalX = width / 2f
        val focalY = height / 2f
        viewport.scaleZoom(1.25f, focalX, focalY)
        invalidate()
    }

    fun zoomOut() {
        val focalX = width / 2f
        val focalY = height / 2f
        viewport.scaleZoom(0.8f, focalX, focalY)
        invalidate()
    }

    fun resetViewport() {
        viewport.resetView(document.width.toFloat(), document.height.toFloat(), width.toFloat(), height.toFloat())
        invalidate()
    }

    fun fitToScreen() {
        viewport.fitToScreen(document.width.toFloat(), document.height.toFloat(), width.toFloat(), height.toFloat())
        invalidate()
    }

    fun centerCanvas() {
        viewport.centerCanvas(document.width.toFloat(), document.height.toFloat(), width.toFloat(), height.toFloat())
        invalidate()
    }

    fun flipHorizontal() {
        viewport.toggleFlipHorizontal()
        invalidate()
    }

    fun flipVertical() {
        viewport.toggleFlipVertical()
        invalidate()
    }

    fun rotateClockwise(degrees: Float = 90f) {
        val focalX = width / 2f
        val focalY = height / 2f
        viewport.rotateAround(degrees, focalX, focalY)
        invalidate()
    }

    fun snapRotation() {
        viewport.snapRotation(5.0f)
        invalidate()
    }

    fun clearStrokes() {
        document.clearCommittedStrokes()
        invalidate()
    }

    fun clearActiveLayerUndoably(): Boolean {
        val result = document.clearActiveLayerUndoably()
        if (result) {
            renderer.rebakeAll(document)
            invalidate()
        }
        return result
    }

    fun setBrushPreset(type: BrushType) {
        inputPipeline.activeBrushConfig = BrushPresets.getPreset(type)
    }

    fun setBrushConfig(config: BrushConfig) {
        inputPipeline.activeBrushConfig = config
    }

    fun setActiveColor(colorArgb: Int) {
        inputPipeline.activeColor = colorArgb
        inputPipeline.activeBrushConfig = inputPipeline.activeBrushConfig.copy(color = colorArgb)
    }

    fun sampleColorAt(docX: Int, docY: Int, mode: com.artstudio.color.EyedropperSampleMode = com.artstudio.color.EyedropperSampleMode.POINT_1X1): com.artstudio.color.ColorData {
        val activeLayerId = document.authoritativeDocument.activeLayerId
        val compositePixels = (document as? DefaultDocumentState)?.let { ds ->
            ds.getLayerPixels(activeLayerId) ?: ds.getOrRenderLayerPixels(activeLayerId)
        } ?: document.testBitmap?.let { bmp ->
            IntArray(bmp.width * bmp.height).apply {
                bmp.getPixels(this, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            }
        }
        return com.artstudio.color.EyedropperEngine.sampleDocument(
            document = document.authoritativeDocument,
            x = docX,
            y = docY,
            mode = mode,
            compositeBuffer = compositePixels
        )
    }
}
