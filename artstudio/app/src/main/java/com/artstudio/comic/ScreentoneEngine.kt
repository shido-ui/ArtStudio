package com.artstudio.comic

import android.graphics.Bitmap
import android.graphics.Color
import java.util.Locale
import kotlin.math.*

/**
 * Scalable procedural engine for Manga Screentones and Comic Hatching fills.
 *
 * Keeps pattern representations mathematical and scale-invariant, preventing
 * unbounded bitmap allocations while supporting arbitrary canvas resolutions.
 */
object ScreentoneEngine {

    /**
     * Evaluates the tone coverage fraction in [0.0, 1.0] at a specific document coordinate (x, y).
     *
     * @param x Document X coordinate
     * @param y Document Y coordinate
     * @param config Procedural tone configuration
     * @param scale Optional resolution scaling factor
     */
    fun evaluateCoverage(
        x: Float,
        y: Float,
        config: ScreentoneConfig,
        scale: Float = 1.0f
    ): Float {
        val scaledConfig = if (scale != 1.0f) config.scaled(scale) else config
        val pitch = scaledConfig.frequency
        val angleRad = Math.toRadians(-scaledConfig.angleDeg.toDouble()).toFloat()
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)

        // Rotate document coordinates into aligned pattern space
        val u = x * cosA - y * sinA
        val v = x * sinA + y * cosA

        val softness = max(0.01f, scaledConfig.softness)

        return when (scaledConfig.pattern) {
            ScreentonePattern.DOT -> {
                val r = scaledConfig.dotRadius
                // Cell offset in [0, pitch)
                val uCell = ((u % pitch) + pitch) % pitch
                val vCell = ((v % pitch) + pitch) % pitch
                val du = uCell - (pitch / 2f)
                val dv = vCell - (pitch / 2f)
                val dist = hypot(du, dv)

                if (dist <= r - (softness / 2f)) {
                    1.0f
                } else if (dist >= r + (softness / 2f)) {
                    0.0f
                } else {
                    1.0f - ((dist - (r - softness / 2f)) / softness).coerceIn(0f, 1f)
                }
            }
            ScreentonePattern.LINE -> {
                val w = scaledConfig.lineWidth
                val vCell = ((v % pitch) + pitch) % pitch
                val dist = abs(vCell - (pitch / 2f))
                val halfW = w / 2f

                if (dist <= halfW - (softness / 2f)) {
                    1.0f
                } else if (dist >= halfW + (softness / 2f)) {
                    0.0f
                } else {
                    1.0f - ((dist - (halfW - softness / 2f)) / softness).coerceIn(0f, 1f)
                }
            }
            ScreentonePattern.CROSS_HATCH -> {
                // Combined orthogonal lines
                val w = pitch * (1.0f - sqrt(max(0f, 1.0f - scaledConfig.density)))
                val halfW = w / 2f

                val uCell = ((u % pitch) + pitch) % pitch
                val vCell = ((v % pitch) + pitch) % pitch
                val distU = abs(uCell - (pitch / 2f))
                val distV = abs(vCell - (pitch / 2f))

                val covU = if (distU <= halfW - (softness / 2f)) 1.0f
                else if (distU >= halfW + (softness / 2f)) 0.0f
                else 1.0f - ((distU - (halfW - softness / 2f)) / softness).coerceIn(0f, 1f)

                val covV = if (distV <= halfW - (softness / 2f)) 1.0f
                else if (distV >= halfW + (softness / 2f)) 0.0f
                else 1.0f - ((distV - (halfW - softness / 2f)) / softness).coerceIn(0f, 1f)

                // Screen union: covU + covV - (covU * covV)
                covU + covV - (covU * covV)
            }
            ScreentonePattern.DIAMOND -> {
                val r = pitch * sqrt(scaledConfig.density / 2f)
                val uCell = ((u % pitch) + pitch) % pitch
                val vCell = ((v % pitch) + pitch) % pitch
                val du = abs(uCell - (pitch / 2f))
                val dv = abs(vCell - (pitch / 2f))
                val manhattanDist = du + dv

                if (manhattanDist <= r - (softness / 2f)) {
                    1.0f
                } else if (manhattanDist >= r + (softness / 2f)) {
                    0.0f
                } else {
                    1.0f - ((manhattanDist - (r - softness / 2f)) / softness).coerceIn(0f, 1f)
                }
            }
        }
    }

    /**
     * Blends foreground and background colors according to procedural tone coverage at (x, y).
     */
    fun evaluatePixelColor(
        x: Float,
        y: Float,
        config: ScreentoneConfig,
        scale: Float = 1.0f
    ): Long {
        val cov = evaluateCoverage(x, y, config, scale)
        if (cov <= 0f) return config.backgroundColor
        if (cov >= 1f) return config.foregroundColor

        val fgA = ((config.foregroundColor shr 24) and 0xFF).toFloat()
        val fgR = ((config.foregroundColor shr 16) and 0xFF).toFloat()
        val fgG = ((config.foregroundColor shr 8) and 0xFF).toFloat()
        val fgB = (config.foregroundColor and 0xFF).toFloat()

        val bgA = ((config.backgroundColor shr 24) and 0xFF).toFloat()
        val bgR = ((config.backgroundColor shr 16) and 0xFF).toFloat()
        val bgG = ((config.backgroundColor shr 8) and 0xFF).toFloat()
        val bgB = (config.backgroundColor and 0xFF).toFloat()

        val outA = (bgA + (fgA - bgA) * cov).toInt().coerceIn(0, 255)
        val outR = (bgR + (fgR - bgR) * cov).toInt().coerceIn(0, 255)
        val outG = (bgG + (fgG - bgG) * cov).toInt().coerceIn(0, 255)
        val outB = (bgB + (fgB - bgB) * cov).toInt().coerceIn(0, 255)

        return ((outA.toLong() and 0xFF) shl 24) or
               ((outR.toLong() and 0xFF) shl 16) or
               ((outG.toLong() and 0xFF) shl 8) or
               (outB.toLong() and 0xFF)
    }

    /**
     * Renders procedural tone into an in-memory 32-bit ARGB pixel buffer.
     */
    fun renderToBuffer(
        buffer: IntArray,
        width: Int,
        height: Int,
        config: ScreentoneConfig,
        offsetX: Int = 0,
        offsetY: Int = 0,
        bounds: Rect2D? = null,
        mask: ((Int, Int) -> Boolean)? = null
    ) {
        val minX = bounds?.left?.toInt()?.coerceIn(0, width - 1) ?: 0
        val minY = bounds?.top?.toInt()?.coerceIn(0, height - 1) ?: 0
        val maxX = bounds?.right?.toInt()?.coerceIn(0, width) ?: width
        val maxY = bounds?.bottom?.toInt()?.coerceIn(0, height) ?: height

        for (y in minY until maxY) {
            val docY = (offsetY + y).toFloat()
            val rowStart = y * width
            for (x in minX until maxX) {
                if (mask != null && !mask(x, y)) continue
                val docX = (offsetX + x).toFloat()
                val colorLong = evaluatePixelColor(docX, docY, config)
                buffer[rowStart + x] = colorLong.toInt()
            }
        }
    }

    /**
     * Generates a reusable cached tile bitmap for the screentone pattern.
     */
    fun createTileBitmap(config: ScreentoneConfig, size: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        renderToBuffer(pixels, size, size, config)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Serializes this screentone as an SVG 1.1 `<pattern>` definition for vector export.
     */
    fun generateSvgPatternDef(config: ScreentoneConfig, patternId: String = "tone_pattern"): String {
        val pitch = config.frequency
        val angle = config.angleDeg
        val fgHex = String.format(Locale.US, "#%06X", config.foregroundColor and 0xFFFFFF)
        val fgAlpha = ((config.foregroundColor shr 24) and 0xFF) / 255.0f

        val sb = StringBuilder()
        sb.appendLine("""<pattern id="$patternId" width="$pitch" height="$pitch" patternUnits="userSpaceOnUse" patternTransform="rotate($angle)">""")

        when (config.pattern) {
            ScreentonePattern.DOT -> {
                val r = config.dotRadius
                val halfP = pitch / 2f
                sb.appendLine("""  <circle cx="$halfP" cy="$halfP" r="$r" fill="$fgHex" fill-opacity="$fgAlpha"/>""")
            }
            ScreentonePattern.LINE -> {
                val w = config.lineWidth
                val halfP = pitch / 2f
                sb.appendLine("""  <line x1="0" y1="$halfP" x2="$pitch" y2="$halfP" stroke="$fgHex" stroke-width="$w" stroke-opacity="$fgAlpha"/>""")
            }
            ScreentonePattern.CROSS_HATCH -> {
                val w = pitch * (1.0f - sqrt(max(0f, 1.0f - config.density)))
                val halfP = pitch / 2f
                sb.appendLine("""  <line x1="0" y1="$halfP" x2="$pitch" y2="$halfP" stroke="$fgHex" stroke-width="$w" stroke-opacity="$fgAlpha"/>""")
                sb.appendLine("""  <line x1="$halfP" y1="0" x2="$halfP" y2="$pitch" stroke="$fgHex" stroke-width="$w" stroke-opacity="$fgAlpha"/>""")
            }
            ScreentonePattern.DIAMOND -> {
                val r = pitch * sqrt(config.density / 2f)
                val halfP = pitch / 2f
                val pts = "${halfP},${halfP - r} ${halfP + r},${halfP} ${halfP},${halfP + r} ${halfP - r},${halfP}"
                sb.appendLine("""  <polygon points="$pts" fill="$fgHex" fill-opacity="$fgAlpha"/>""")
            }
        }

        sb.appendLine("</pattern>")
        return sb.toString()
    }
}
