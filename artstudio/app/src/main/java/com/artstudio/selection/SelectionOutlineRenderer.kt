package com.artstudio.selection

import android.graphics.*

/**
 * Renders the visible selection boundary (marching ants) on screen or canvas.
 * Uses high-contrast dual-stroke rendering (white dash alternating with black dash)
 * to guarantee visibility over both dark and light artwork backgrounds.
 */
class SelectionOutlineRenderer {

    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 2f
    }

    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f
    }

    /**
     * Renders the marching ants selection boundary along [selectionPath].
     *
     * @param canvas Target Android Canvas (in document or screen coordinates).
     * @param selectionPath Vector Path of selection boundary.
     * @param phase Animation phase offset in pixels.
     * @param strokeWidth Stroke width in canvas pixels.
     */
    fun renderMarchingAnts(
        canvas: Canvas,
        selectionPath: Path,
        phase: Float = 0f,
        strokeWidth: Float = 2f
    ) {
        if (selectionPath.isEmpty) return

        blackPaint.strokeWidth = strokeWidth
        whitePaint.strokeWidth = strokeWidth

        val dashLength = 8f
        val blackDash = DashPathEffect(floatArrayOf(dashLength, dashLength), phase)
        val whiteDash = DashPathEffect(floatArrayOf(dashLength, dashLength), phase + dashLength)

        blackPaint.pathEffect = blackDash
        whitePaint.pathEffect = whiteDash

        canvas.drawPath(selectionPath, blackPaint)
        canvas.drawPath(selectionPath, whitePaint)
    }
}
