package com.artstudio.brush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.artstudio.core.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates authentic, real-pipeline visual stroke previews for brush presets.
 * Renders via [RasterStrokeEngine] without placeholder graphics or approximations.
 */
object BrushPreviewGenerator {

    private val engine = RasterStrokeEngine()

    /**
     * Builds the preview [BrushStroke] path with modulated coordinates and pressure.
     */
    fun buildPreviewStroke(
        config: BrushConfig,
        width: Int = 240,
        height: Int = 60
    ): BrushStroke {
        val stroke = BrushStroke(
            id = "preview_${config.type.name.lowercase()}",
            config = config
        )

        val numSamples = 28
        val startX = 20f
        val endX = width - 20f
        val spanX = endX - startX
        val midY = height / 2f
        val amplitude = height * 0.22f

        for (i in 0..numSamples) {
            val t = i.toFloat() / numSamples.toFloat()
            val x = startX + t * spanX
            val y = midY + sin(t * 2.0 * PI).toFloat() * amplitude
            // Modulate pressure: start light, swell in middle, taper at end
            val p = 0.25f + 0.65f * sin(t * PI).toFloat()
            stroke.points.add(StrokePoint(x, y, p))
        }
        return stroke
    }

    /**
     * Renders preview stroke directly onto the supplied [Canvas].
     */
    fun renderPreviewToCanvas(
        canvas: Canvas,
        config: BrushConfig,
        width: Int = 240,
        height: Int = 60,
        backgroundColor: Int = Color.TRANSPARENT
    ) {
        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor)
        }

        // For Eraser preview, draw a solid background band so the erasure is visible
        if (config.type == BrushType.ERASER) {
            val bgStroke = BrushStroke(
                id = "eraser_bg",
                config = BrushPresets.hardPen(size = 36f, color = 0xFF546E7A.toInt())
            )
            val yMid = height / 2f
            bgStroke.points.add(StrokePoint(16f, yMid, 1.0f))
            bgStroke.points.add(StrokePoint(width - 16f, yMid, 1.0f))
            engine.renderStroke(canvas, bgStroke)
        }

        val stroke = buildPreviewStroke(config, width, height)
        engine.renderStroke(canvas, stroke)
    }

    /**
     * Renders a real-pipeline preview stroke for the given [BrushConfig].
     * Returns null if bitmap cannot be allocated (e.g. host JVM unit test harness without Android display).
     */
    fun generatePreview(
        config: BrushConfig,
        width: Int = 240,
        height: Int = 60,
        backgroundColor: Int = Color.TRANSPARENT
    ): Bitmap? {
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            null
        } ?: return null

        val canvas = Canvas(bitmap)
        renderPreviewToCanvas(canvas, config, width, height, backgroundColor)
        return bitmap
    }
}
