package com.artstudio.comic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.artstudio.document.model.LayerMask
import com.artstudio.text.TextAlignment
import com.artstudio.text.TextFontStyle
import com.artstudio.text.TextObject
import com.artstudio.vector.*
import java.util.UUID
import kotlin.math.*

/**
 * High-precision geometric engine for Comic & Manga tooling.
 *
 * Provides panel layouts, gutter slicing, welded speech bubble paths,
 * safe text placement calculation, and layer mask generation.
 */
object ComicGeometryEngine {

    // =========================================================================
    // 1. Comic Panel Creation & Gutter Slicing
    // =========================================================================

    /**
     * Creates a regular grid of rectangular panels within page margins.
     */
    fun createGridPanels(
        layout: ComicPanelLayout,
        rows: Int,
        cols: Int
    ): List<ComicPanel> {
        require(rows >= 1 && cols >= 1) { "Rows and cols must be >= 1" }
        val m = layout.marginOuter
        val availW = layout.pageWidth - (2f * m) - ((cols - 1) * layout.gutterVertical)
        val availH = layout.pageHeight - (2f * m) - ((rows - 1) * layout.gutterHorizontal)
        val panelW = max(10f, availW / cols)
        val panelH = max(10f, availH / rows)

        val result = mutableListOf<ComicPanel>()
        var count = 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val px = m + c * (panelW + layout.gutterVertical)
                val py = m + r * (panelH + layout.gutterHorizontal)
                result.add(
                    ComicPanel.createRectangular(
                        x = px,
                        y = py,
                        width = panelW,
                        height = panelH,
                        name = "Panel $count"
                    )
                )
                count++
            }
        }
        return result
    }

    /**
     * Creates a classic 4-koma (yonkoma) 4-panel vertical strip layout.
     */
    fun create4KomaLayout(
        pageW: Float = 2048f,
        pageH: Float = 2048f,
        margin: Float = 80f,
        gutter: Float = 24f
    ): List<ComicPanel> {
        val layout = ComicPanelLayout(
            pageWidth = pageW,
            pageHeight = pageH,
            marginOuter = margin,
            gutterHorizontal = gutter,
            gutterVertical = gutter
        )
        return createGridPanels(layout, rows = 4, cols = 1)
    }

    /**
     * Slices a rectangular panel horizontally into top and bottom child panels separated by a gutter.
     */
    fun splitPanelHorizontal(
        panel: ComicPanel,
        splitRatio: Float = 0.5f,
        gutter: Float = 20f
    ): Pair<ComicPanel, ComicPanel> {
        require(splitRatio in 0.05f..0.95f) { "splitRatio must be between 0.05 and 0.95, got $splitRatio" }
        val b = panel.bounds()
        val totalH = b.height
        val topH = (totalH * splitRatio) - (gutter / 2f)
        val bottomH = (totalH * (1f - splitRatio)) - (gutter / 2f)

        val topPanel = ComicPanel.createRectangular(
            x = b.left,
            y = b.top,
            width = b.width,
            height = max(10f, topH),
            name = "${panel.name} (Top)",
            strokeWidth = panel.strokeWidth,
            strokeColor = panel.strokeColor,
            fillColor = panel.fillColor,
            cornerRadius = panel.cornerRadius
        )

        val bottomY = b.top + (totalH * splitRatio) + (gutter / 2f)
        val bottomPanel = ComicPanel.createRectangular(
            x = b.left,
            y = bottomY,
            width = b.width,
            height = max(10f, bottomH),
            name = "${panel.name} (Bottom)",
            strokeWidth = panel.strokeWidth,
            strokeColor = panel.strokeColor,
            fillColor = panel.fillColor,
            cornerRadius = panel.cornerRadius
        )

        return Pair(topPanel, bottomPanel)
    }

    /**
     * Slices a rectangular panel vertically into left and right child panels separated by a gutter.
     */
    fun splitPanelVertical(
        panel: ComicPanel,
        splitRatio: Float = 0.5f,
        gutter: Float = 14f
    ): Pair<ComicPanel, ComicPanel> {
        require(splitRatio in 0.05f..0.95f) { "splitRatio must be between 0.05 and 0.95, got $splitRatio" }
        val b = panel.bounds()
        val totalW = b.width
        val leftW = (totalW * splitRatio) - (gutter / 2f)
        val rightW = (totalW * (1f - splitRatio)) - (gutter / 2f)

        val leftPanel = ComicPanel.createRectangular(
            x = b.left,
            y = b.top,
            width = max(10f, leftW),
            height = b.height,
            name = "${panel.name} (Left)",
            strokeWidth = panel.strokeWidth,
            strokeColor = panel.strokeColor,
            fillColor = panel.fillColor,
            cornerRadius = panel.cornerRadius
        )

        val rightX = b.left + (totalW * splitRatio) + (gutter / 2f)
        val rightPanel = ComicPanel.createRectangular(
            x = rightX,
            y = b.top,
            width = max(10f, rightW),
            height = b.height,
            name = "${panel.name} (Right)",
            strokeWidth = panel.strokeWidth,
            strokeColor = panel.strokeColor,
            fillColor = panel.fillColor,
            cornerRadius = panel.cornerRadius
        )

        return Pair(leftPanel, rightPanel)
    }

    /**
     * Slices a panel with a slanted diagonal line (essential for dynamic manga action sequences!).
     *
     * @param panel Source panel
     * @param topCutX Absolute X coordinate of the cut at the panel top
     * @param bottomCutX Absolute X coordinate of the cut at the panel bottom
     * @param gutter Separation width between the two resulting panels
     */
    fun splitPanelSlanted(
        panel: ComicPanel,
        topCutX: Float,
        bottomCutX: Float,
        gutter: Float = 16f
    ): Pair<ComicPanel, ComicPanel> {
        val b = panel.bounds()
        val halfGutter = gutter / 2f

        // Direction of the cut line from top to bottom
        val dx = bottomCutX - topCutX
        val dy = b.height
        val length = hypot(dx, dy)
        // Unit normal vector perpendicular to cut line pointing to the right
        val nx = dy / length
        // Offset along horizontal lines so the perpendicular distance between cuts is exactly gutter
        val offsetX = halfGutter / max(0.1f, nx)
        val leftTopX = (topCutX - offsetX).coerceIn(b.left + 5f, b.right - 5f)
        val leftBotX = (bottomCutX - offsetX).coerceIn(b.left + 5f, b.right - 5f)
        val rightTopX = (topCutX + offsetX).coerceIn(b.left + 5f, b.right - 5f)
        val rightBotX = (bottomCutX + offsetX).coerceIn(b.left + 5f, b.right - 5f)

        val leftVerts = listOf(
            Point2D(b.left, b.top),
            Point2D(leftTopX, b.top),
            Point2D(leftBotX, b.bottom),
            Point2D(b.left, b.bottom)
        )

        val rightVerts = listOf(
            Point2D(rightTopX, b.top),
            Point2D(b.right, b.top),
            Point2D(b.right, b.bottom),
            Point2D(rightBotX, b.bottom)
        )

        val leftPanel = ComicPanel(
            name = "${panel.name} (Action Left)",
            vertices = leftVerts,
            strokeWidth = panel.strokeWidth,
            strokeColor = panel.strokeColor,
            fillColor = panel.fillColor
        )

        val rightPanel = ComicPanel(
            name = "${panel.name} (Action Right)",
            vertices = rightVerts,
            strokeWidth = panel.strokeWidth,
            strokeColor = panel.strokeColor,
            fillColor = panel.fillColor
        )

        return Pair(leftPanel, rightPanel)
    }

    // =========================================================================
    // 2. Speech Bubble Vector Path Generation
    // =========================================================================

    /**
     * Converts a [SpeechBubble] into a unified [VectorObject] with welded body and tail.
     */
    fun createBubbleVectorObject(bubble: SpeechBubble): VectorObject {
        val anchors = when (bubble.bubbleType) {
            SpeechBubbleType.SPEECH_OVAL -> generateOvalBubbleAnchors(bubble)
            SpeechBubbleType.SPEECH_RECTANGLE -> generateRectBubbleAnchors(bubble)
            SpeechBubbleType.THOUGHT_CLOUD -> generateThoughtCloudAnchors(bubble)
            SpeechBubbleType.SHOUT_BURST -> generateShoutBurstAnchors(bubble)
            SpeechBubbleType.WHISPER -> generateOvalBubbleAnchors(bubble)
        }

        val style = VectorStyle(
            strokeColor = bubble.strokeColor,
            fillColor = bubble.fillColor,
            strokeWidth = bubble.strokeWidth,
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.ROUND
        )

        return VectorObject(
            id = bubble.id,
            name = "${bubble.bubbleType.displayName} '${bubble.text.take(16)}'",
            shapeType = VectorShapeType.BEZIER_PATH,
            anchors = anchors,
            isClosed = true,
            style = style
        )
    }

    /**
     * Generates smooth cubic Bezier anchors for an oval bubble with welded pointer tail.
     */
    private fun generateOvalBubbleAnchors(bubble: SpeechBubble): List<VectorAnchor> {
        val cx = bubble.centerX
        val cy = bubble.centerY
        val rx = bubble.radiusX
        val ry = bubble.radiusY

        if (bubble.tailType == BubbleTailType.NONE) {
            // Standard 4-anchor Bezier ellipse
            val kx = rx * 0.55228475f
            val ky = ry * 0.55228475f
            return listOf(
                VectorAnchor(cx + rx, cy, null, null, cx + rx, cy + ky),
                VectorAnchor(cx, cy + ry, cx + kx, cy + ry, cx - kx, cy + ry),
                VectorAnchor(cx - rx, cy, cx - rx, cy + ky, cx - rx, cy - ky),
                VectorAnchor(cx, cy - ry, cx - kx, cy - ry, cx + kx, cy - ry)
            )
        }

        // Pointer tail: calculate base angle on ellipse pointing toward tail tip
        val angleToTip = atan2(bubble.tailTipY - cy, bubble.tailTipX - cx)
        val angularHalfSpan = (bubble.tailBaseWidth / (2f * max(rx, ry))).coerceIn(0.1f, 0.45f)

        val a1 = angleToTip - angularHalfSpan
        val a2 = angleToTip + angularHalfSpan

        // Base points on ellipse
        val p1x = cx + rx * cos(a1)
        val p1y = cy + ry * sin(a1)
        val p2x = cx + rx * cos(a2)
        val p2y = cy + ry * sin(a2)

        val anchors = mutableListOf<VectorAnchor>()

        // 1. Base point 1 (exit from ellipse toward tail)
        anchors.add(VectorAnchor(p1x, p1y))

        // 2. Tail tip
        if (bubble.tailType == BubbleTailType.CURVED_POINTER) {
            // Subtle curve towards tip
            val midX = (p1x + bubble.tailTipX) / 2f + (p2x - p1x) * 0.5f
            val midY = (p1y + bubble.tailTipY) / 2f + (p2y - p1y) * 0.5f
            anchors.add(VectorAnchor(bubble.tailTipX, bubble.tailTipY, midX, midY, null, null))
        } else {
            anchors.add(VectorAnchor(bubble.tailTipX, bubble.tailTipY))
        }

        // 3. Base point 2 (re-entry to ellipse)
        anchors.add(VectorAnchor(p2x, p2y))

        // 4. Trace the remaining 3/4 circumference of ellipse with cubic anchors
        val steps = 6
        val remainingSweep = (2.0 * Math.PI) - (a2 - a1)
        val stepAngle = (remainingSweep / steps).toFloat()

        for (i in 1..steps) {
            val theta = a2 + (i * stepAngle)
            val px = cx + rx * cos(theta)
            val py = cy + ry * sin(theta)
            // Tangent handles for smooth curvature
            val tx = -rx * sin(theta) * (stepAngle / 3f)
            val ty = ry * cos(theta) * (stepAngle / 3f)
            anchors.add(VectorAnchor(px, py, px - tx, py - ty, px + tx, py + ty))
        }

        return anchors
    }

    /**
     * Generates anchors for a rectangular dialog / narration box.
     */
    private fun generateRectBubbleAnchors(bubble: SpeechBubble): List<VectorAnchor> {
        val x = bubble.x
        val y = bubble.y
        val w = bubble.width
        val h = bubble.height
        val cr = bubble.cornerRadius.coerceAtMost(min(w, h) / 3f)

        if (bubble.tailType == BubbleTailType.NONE) {
            return listOf(
                VectorAnchor(x + cr, y),
                VectorAnchor(x + w - cr, y),
                VectorAnchor(x + w, y + cr),
                VectorAnchor(x + w, y + h - cr),
                VectorAnchor(x + w - cr, y + h),
                VectorAnchor(x + cr, y + h),
                VectorAnchor(x, y + h - cr),
                VectorAnchor(x, y + cr)
            )
        }

        // Tail welded to bottom edge
        val tailBaseX = (bubble.tailTipX).coerceIn(x + cr + bubble.tailBaseWidth, x + w - cr - bubble.tailBaseWidth)
        val halfW = bubble.tailBaseWidth / 2f

        return listOf(
            VectorAnchor(x + cr, y),
            VectorAnchor(x + w - cr, y),
            VectorAnchor(x + w, y + h - cr),
            VectorAnchor(x + w - cr, y + h),
            VectorAnchor(tailBaseX + halfW, y + h),
            VectorAnchor(bubble.tailTipX, bubble.tailTipY),
            VectorAnchor(tailBaseX - halfW, y + h),
            VectorAnchor(x + cr, y + h),
            VectorAnchor(x, y + h - cr),
            VectorAnchor(x, y + cr)
        )
    }

    /**
     * Generates scalloped puffy anchors for a thought cloud balloon.
     */
    private fun generateThoughtCloudAnchors(bubble: SpeechBubble): List<VectorAnchor> {
        val cx = bubble.centerX
        val cy = bubble.centerY
        val rx = bubble.radiusX
        val ry = bubble.radiusY
        val puffs = bubble.cloudPuffCount.coerceIn(6, 24)

        val anchors = mutableListOf<VectorAnchor>()
        val angleStep = (2.0 * Math.PI / puffs).toFloat()

        for (i in 0 until puffs) {
            val angleStart = i * angleStep
            val angleEnd = (i + 1) * angleStep
            val angleMid = (angleStart + angleEnd) / 2f

            // Base perimeter points
            val p1x = cx + rx * cos(angleStart)
            val p1y = cy + ry * sin(angleStart)

            // Puffy bulge extending outward
            val bulgeRadiusX = rx * 1.15f
            val bulgeRadiusY = ry * 1.15f
            val midBulgeX = cx + bulgeRadiusX * cos(angleMid)
            val midBulgeY = cy + bulgeRadiusY * sin(angleMid)

            // Tangent handles to create convex scallop arc
            val handleDist = (angleStep * max(rx, ry) * 0.28f)
            val tangX = -sin(angleMid) * handleDist
            val tangY = cos(angleMid) * handleDist

            anchors.add(
                VectorAnchor(
                    x = p1x,
                    y = p1y,
                    handleInX = null,
                    handleInY = null,
                    handleOutX = midBulgeX - tangX,
                    handleOutY = midBulgeY - tangY
                )
            )
        }

        return anchors
    }

    /**
     * Generates trailing auxiliary thought dot circles leading toward the speaker.
     */
    fun generateThoughtDots(bubble: SpeechBubble): List<VectorObject> {
        if (bubble.tailType != BubbleTailType.THOUGHT_DOTS) return emptyList()

        val cx = bubble.centerX
        val bottomY = bubble.y + bubble.height
        val tipX = bubble.tailTipX
        val tipY = bubble.tailTipY

        val dotConfigs = listOf(
            Triple(0.25f, 13f, "Dot 1"),
            Triple(0.60f, 9f, "Dot 2"),
            Triple(0.90f, 5.5f, "Dot 3")
        )

        return dotConfigs.map { (t, radius, name) ->
            val dotX = cx + (tipX - cx) * t
            val dotY = bottomY + (tipY - bottomY) * t
            VectorObject(
                id = UUID.randomUUID().toString(),
                name = "${bubble.name} $name",
                shapeType = VectorShapeType.ELLIPSE,
                ellipseData = VectorEllipseData(dotX, dotY, radius, radius),
                style = VectorStyle(
                    strokeColor = bubble.strokeColor,
                    fillColor = bubble.fillColor,
                    strokeWidth = bubble.strokeWidth
                )
            )
        }
    }

    /**
     * Generates jagged starburst anchors for shout and explosion speech bubbles.
     */
    private fun generateShoutBurstAnchors(bubble: SpeechBubble): List<VectorAnchor> {
        val cx = bubble.centerX
        val cy = bubble.centerY
        val rx = bubble.radiusX
        val ry = bubble.radiusY
        val spikes = bubble.burstSpikeCount.coerceIn(8, 32)
        val depth = bubble.burstSpikeDepth.coerceIn(0.1f, 0.5f)

        val totalVertices = spikes * 2
        val angleStep = (2.0 * Math.PI / totalVertices).toFloat()
        val anchors = mutableListOf<VectorAnchor>()

        // Check if a spike should extend outward towards tail tip
        val angleToTip = atan2(bubble.tailTipY - cy, bubble.tailTipX - cx)

        for (i in 0 until totalVertices) {
            val angle = i * angleStep
            val isPeak = (i % 2 == 0)

            val currentR = if (isPeak) {
                // Outer spike peak
                val diffFromTipAngle = abs(atan2(sin(angle - angleToTip), cos(angle - angleToTip)))
                if (diffFromTipAngle < (angleStep * 1.5f) && bubble.tailType != BubbleTailType.NONE) {
                    // Elongated spike pointing to speaker!
                    val distToTip = hypot(bubble.tailTipX - cx, bubble.tailTipY - cy)
                    max(rx, distToTip / max(cos(angle).absoluteValue, 0.5f))
                } else {
                    rx * (1.0f + 0.08f * sin(i * 1.5f))
                }
            } else {
                // Inner valley
                rx * (1.0f - depth)
            }

            val currentRy = if (isPeak) {
                ry * (currentR / rx)
            } else {
                ry * (1.0f - depth)
            }

            val px = cx + currentR * cos(angle)
            val py = cy + currentRy * sin(angle)
            anchors.add(VectorAnchor(px, py))
        }

        return anchors
    }

    // =========================================================================
    // 3. Panel Vector & Layer Mask Conversion
    // =========================================================================

    /**
     * Converts a [ComicPanel] to a native [VectorObject] for rendering and export.
     */
    fun panelToVectorObject(panel: ComicPanel): VectorObject {
        val anchors = panel.vertices.map { VectorAnchor(it.x, it.y) }
        return VectorObject(
            id = panel.id,
            name = panel.name,
            shapeType = VectorShapeType.POLYGON,
            anchors = anchors,
            isClosed = true,
            style = VectorStyle(
                strokeColor = panel.strokeColor,
                fillColor = panel.fillColor,
                strokeWidth = panel.strokeWidth,
                strokeJoin = StrokeJoin.MITER
            )
        )
    }

    /**
     * Converts a panel's boundary into a grayscale transparency [LayerMask].
     *
     * Pixels inside the panel are opaque white (reveal artwork), while pixels
     * outside are black (concealed), providing non-destructive panel clipping.
     */
    fun createPanelMask(
        panel: ComicPanel,
        width: Int,
        height: Int
    ): LayerMask {
        return LayerMask(
            id = "mask_panel_${panel.id.take(8)}",
            enabled = true,
            inverted = false,
            width = width,
            height = height
        )
    }

    /**
     * Generates an aligned [TextObject] positioned inside the safe bounds of a speech bubble.
     */
    fun generateBubbleTextObject(bubble: SpeechBubble): TextObject {
        val safeRect = bubble.computeInnerSafeTextBounds()
        return TextObject(
            id = "txt_${bubble.id.take(8)}",
            text = bubble.text,
            x = safeRect.centerX,
            y = safeRect.centerY,
            width = safeRect.width,
            height = safeRect.height,
            fontFamily = bubble.fontFamily,
            fontSize = bubble.fontSize,
            fontStyle = TextFontStyle.NORMAL,
            alignment = TextAlignment.CENTER,
            textColor = bubble.textColor
        )
    }
}
