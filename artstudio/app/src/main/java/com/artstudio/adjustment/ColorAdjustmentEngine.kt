package com.artstudio.adjustment

import kotlin.math.*

/**
 * Authoritative Color & Tonal Adjustment Engine.
 *
 * Implements non-destructive mathematical adjustments:
 * 1. Brightness / Contrast (Sigmoidal contrast scaling + linear brightness shift)
 * 2. Levels (Shadow/highlight clipping + midtone gamma power function via 256-entry LUT)
 * 3. Hue / Saturation / Value (Full 360-degree cylindrical color rotation and saturation scaling)
 * 4. Grayscale (ITU-R BT.709 HDTV perceptual luminance weights + optional duotone tinting)
 * 5. Color Balance (Cyan/Red, Magenta/Green, Yellow/Blue shifts with luminosity preservation)
 *
 * Invariants:
 * - Alpha channel is 100% strictly preserved (A_dest = A_src).
 * - LayerMask modulation respects Prompt 18 mask specifications.
 * - Source pixel buffers are never mutated unless explicit in-place application is requested.
 */
object ColorAdjustmentEngine {

    /**
     * Applies a non-destructive [ColorAdjustment] to [sourcePixels], returning a newly allocated buffer.
     *
     * @param sourcePixels Input ARGB_8888 buffer
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param adjustment Color adjustment configuration
     * @param maskPixels Optional grayscale or ARGB mask buffer (Prompt 18)
     * @param maskInverted Whether mask concealment is inverted
     * @return Newly allocated ARGB_8888 buffer containing adjusted pixels
     */
    fun applyAdjustment(
        sourcePixels: IntArray,
        width: Int,
        height: Int,
        adjustment: ColorAdjustment,
        maskPixels: IntArray? = null,
        maskInverted: Boolean = false
    ): IntArray {
        val dest = sourcePixels.clone()
        applyAdjustmentInPlace(dest, width, height, adjustment, maskPixels, maskInverted)
        return dest
    }

    /**
     * Applies a [ColorAdjustment] in place to [pixels].
     */
    fun applyAdjustmentInPlace(
        pixels: IntArray,
        width: Int,
        height: Int,
        adjustment: ColorAdjustment,
        maskPixels: IntArray? = null,
        maskInverted: Boolean = false
    ) {
        if (!adjustment.enabled || adjustment.opacity <= 0.0f) return

        when (adjustment) {
            is BrightnessContrastAdjustment -> applyBrightnessContrast(pixels, adjustment, maskPixels, maskInverted)
            is LevelsAdjustment -> applyLevels(pixels, adjustment, maskPixels, maskInverted)
            is HueSaturationAdjustment -> applyHueSaturation(pixels, adjustment, maskPixels, maskInverted)
            is GrayscaleAdjustment -> applyGrayscale(pixels, adjustment, maskPixels, maskInverted)
            is ColorBalanceAdjustment -> applyColorBalance(pixels, adjustment, maskPixels, maskInverted)
        }
    }

    // =========================================================================
    // 1. Brightness / Contrast
    // =========================================================================

    private fun applyBrightnessContrast(
        pixels: IntArray,
        adj: BrightnessContrastAdjustment,
        maskPixels: IntArray?,
        maskInverted: Boolean
    ) {
        // Precompute 256-entry lookup table for high-throughput execution
        val lut = IntArray(256)
        val factor = if (adj.contrast >= 100f) {
            255f
        } else {
            (259.0f * (adj.contrast + 255.0f)) / (255.0f * (259.0f - adj.contrast))
        }
        val brightnessShift = adj.brightness * 2.55f

        for (i in 0..255) {
            val adjusted = factor * (i - 128.0f) + 128.0f + brightnessShift
            lut[i] = adjusted.roundToInt().coerceIn(0, 255)
        }

        val opacity = adj.opacity.coerceIn(0f, 1f)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel ushr 24) and 0xFF
            if (a == 0) continue

            val weight = resolvePixelWeight(i, opacity, maskPixels, maskInverted)
            if (weight <= 0.0f) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val adjR = lut[r]
            val adjG = lut[g]
            val adjB = lut[b]

            val outR = if (weight >= 1.0f) adjR else lerp(r, adjR, weight)
            val outG = if (weight >= 1.0f) adjG else lerp(g, adjG, weight)
            val outB = if (weight >= 1.0f) adjB else lerp(b, adjB, weight)

            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    // =========================================================================
    // 2. Levels
    // =========================================================================

    private fun applyLevels(
        pixels: IntArray,
        adj: LevelsAdjustment,
        maskPixels: IntArray?,
        maskInverted: Boolean
    ) {
        val lut = IntArray(256)
        val inRange = (adj.inWhite - adj.inBlack).toFloat()
        val outRange = (adj.outWhite - adj.outBlack).toFloat()
        val invGamma = 1.0 / adj.inGamma.toDouble()

        for (i in 0..255) {
            val norm = ((i - adj.inBlack) / inRange).coerceIn(0.0f, 1.0f)
            val gammaMapped = norm.toDouble().pow(invGamma).toFloat()
            val outVal = adj.outBlack + gammaMapped * outRange
            lut[i] = outVal.roundToInt().coerceIn(0, 255)
        }

        val opacity = adj.opacity.coerceIn(0f, 1f)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel ushr 24) and 0xFF
            if (a == 0) continue

            val weight = resolvePixelWeight(i, opacity, maskPixels, maskInverted)
            if (weight <= 0.0f) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val adjR = lut[r]
            val adjG = lut[g]
            val adjB = lut[b]

            val outR = if (weight >= 1.0f) adjR else lerp(r, adjR, weight)
            val outG = if (weight >= 1.0f) adjG else lerp(g, adjG, weight)
            val outB = if (weight >= 1.0f) adjB else lerp(b, adjB, weight)

            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    // =========================================================================
    // 3. Hue / Saturation
    // =========================================================================

    private fun applyHueSaturation(
        pixels: IntArray,
        adj: HueSaturationAdjustment,
        maskPixels: IntArray?,
        maskInverted: Boolean
    ) {
        val opacity = adj.opacity.coerceIn(0f, 1f)
        val satScale = 1.0f + adj.saturationPercent / 100.0f
        val lightOffset = adj.lightnessPercent / 100.0f
        val hueOffset = adj.hueShiftDeg

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel ushr 24) and 0xFF
            if (a == 0) continue

            val weight = resolvePixelWeight(i, opacity, maskPixels, maskInverted)
            if (weight <= 0.0f) continue

            val r = ((pixel ushr 16) and 0xFF) / 255.0f
            val g = ((pixel ushr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            val (adjR, adjG, adjB) = if (adj.colorize) {
                // Colorize mode: compute perceived luminance and apply unified hue
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val targetHue = (hueOffset % 360f + 360f) % 360f
                val targetSat = (adj.saturationPercent / 100.0f).coerceIn(0f, 1f)
                val targetVal = (lum + lightOffset).coerceIn(0f, 1f)
                hsvToRgb(targetHue, targetSat, targetVal)
            } else {
                val (h, s, v) = rgbToHsv(r, g, b)
                val newH = (h + hueOffset % 360f + 360f) % 360f
                val newS = (s * satScale).coerceIn(0.0f, 1.0f)
                val newV = if (lightOffset >= 0f) {
                    (v + lightOffset * (1.0f - v)).coerceIn(0.0f, 1.0f)
                } else {
                    (v + lightOffset * v).coerceIn(0.0f, 1.0f)
                }
                hsvToRgb(newH, newS, newV)
            }

            val rInt = ((pixel ushr 16) and 0xFF)
            val gInt = ((pixel ushr 8) and 0xFF)
            val bInt = (pixel and 0xFF)

            val adjRInt = (adjR * 255.0f).roundToInt().coerceIn(0, 255)
            val adjGInt = (adjG * 255.0f).roundToInt().coerceIn(0, 255)
            val adjBInt = (adjB * 255.0f).roundToInt().coerceIn(0, 255)

            val outR = if (weight >= 1.0f) adjRInt else lerp(rInt, adjRInt, weight)
            val outG = if (weight >= 1.0f) adjGInt else lerp(gInt, adjGInt, weight)
            val outB = if (weight >= 1.0f) adjBInt else lerp(bInt, adjBInt, weight)

            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    // =========================================================================
    // 4. Grayscale
    // =========================================================================

    private fun applyGrayscale(
        pixels: IntArray,
        adj: GrayscaleAdjustment,
        maskPixels: IntArray?,
        maskInverted: Boolean
    ) {
        val opacity = adj.opacity.coerceIn(0f, 1f)
        val hasTint = adj.tintColor != null && adj.tintIntensity > 0.0f
        val tintR = if (hasTint) ((adj.tintColor!! ushr 16) and 0xFF).toInt() else 0
        val tintG = if (hasTint) ((adj.tintColor!! ushr 8) and 0xFF).toInt() else 0
        val tintB = if (hasTint) (adj.tintColor!! and 0xFF).toInt() else 0
        val tintInt = adj.tintIntensity.coerceIn(0f, 1f)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel ushr 24) and 0xFF
            if (a == 0) continue

            val weight = resolvePixelWeight(i, opacity, maskPixels, maskInverted)
            if (weight <= 0.0f) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val lum = (r * adj.redWeight + g * adj.greenWeight + b * adj.blueWeight)
                .roundToInt().coerceIn(0, 255)

            val (adjR, adjG, adjB) = if (hasTint) {
                Triple(
                    lerp(lum, tintR, tintInt),
                    lerp(lum, tintG, tintInt),
                    lerp(lum, tintB, tintInt)
                )
            } else {
                Triple(lum, lum, lum)
            }

            val outR = if (weight >= 1.0f) adjR else lerp(r, adjR, weight)
            val outG = if (weight >= 1.0f) adjG else lerp(g, adjG, weight)
            val outB = if (weight >= 1.0f) adjB else lerp(b, adjB, weight)

            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    // =========================================================================
    // 5. Color Balance
    // =========================================================================

    private fun applyColorBalance(
        pixels: IntArray,
        adj: ColorBalanceAdjustment,
        maskPixels: IntArray?,
        maskInverted: Boolean
    ) {
        val opacity = adj.opacity.coerceIn(0f, 1f)
        val deltaR = adj.cyanRed * 1.28f
        val deltaG = adj.magentaGreen * 1.28f
        val deltaB = adj.yellowBlue * 1.28f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel ushr 24) and 0xFF
            if (a == 0) continue

            val weight = resolvePixelWeight(i, opacity, maskPixels, maskInverted)
            if (weight <= 0.0f) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            var shiftedR = (r + deltaR).coerceIn(0f, 255f)
            var shiftedG = (g + deltaG).coerceIn(0f, 255f)
            var shiftedB = (b + deltaB).coerceIn(0f, 255f)

            if (adj.preserveLuminosity) {
                val origLum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val newLum = 0.2126f * shiftedR + 0.7152f * shiftedG + 0.0722f * shiftedB
                if (newLum > 0.001f) {
                    val scale = origLum / newLum
                    shiftedR = (shiftedR * scale).coerceIn(0f, 255f)
                    shiftedG = (shiftedG * scale).coerceIn(0f, 255f)
                    shiftedB = (shiftedB * scale).coerceIn(0f, 255f)
                }
            }

            val adjR = shiftedR.roundToInt().coerceIn(0, 255)
            val adjG = shiftedG.roundToInt().coerceIn(0, 255)
            val adjB = shiftedB.roundToInt().coerceIn(0, 255)

            val outR = if (weight >= 1.0f) adjR else lerp(r, adjR, weight)
            val outG = if (weight >= 1.0f) adjG else lerp(g, adjG, weight)
            val outB = if (weight >= 1.0f) adjB else lerp(b, adjB, weight)

            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun resolvePixelWeight(
        index: Int,
        layerOpacity: Float,
        maskPixels: IntArray?,
        maskInverted: Boolean
    ): Float {
        if (maskPixels == null || index >= maskPixels.size) return layerOpacity

        val maskColor = maskPixels[index]
        val maskA = ((maskColor ushr 24) and 0xFF) / 255.0f
        val maskR = ((maskColor ushr 16) and 0xFF) / 255.0f
        val maskG = ((maskColor ushr 8) and 0xFF) / 255.0f
        val maskB = (maskColor and 0xFF) / 255.0f

        var lum = (0.299f * maskR + 0.587f * maskG + 0.114f * maskB) * maskA
        if (maskInverted) {
            lum = 1.0f - lum
        }

        return (layerOpacity * lum).coerceIn(0.0f, 1.0f)
    }

    private fun lerp(start: Int, end: Int, fraction: Float): Int {
        return (start + (end - start) * fraction).roundToInt().coerceIn(0, 255)
    }

    fun rgbToHsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        val delta = max - min

        var h = when {
            delta == 0.0f -> 0.0f
            max == r -> 60.0f * (((g - b) / delta) % 6.0f)
            max == g -> 60.0f * (((b - r) / delta) + 2.0f)
            else -> 60.0f * (((r - g) / delta) + 4.0f)
        }
        if (h < 0.0f) h += 360.0f

        val s = if (max <= 0.0f) 0.0f else delta / max
        val v = max

        return Triple(h, s, v)
    }

    fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
        val c = v * s
        val normH = (h % 360.0f + 360.0f) % 360.0f
        val x = c * (1.0f - abs((normH / 60.0f) % 2.0f - 1.0f))
        val m = v - c

        val (r1, g1, b1) = when ((normH / 60.0f).toInt()) {
            0 -> Triple(c, x, 0.0f)
            1 -> Triple(x, c, 0.0f)
            2 -> Triple(0.0f, c, x)
            3 -> Triple(0.0f, x, c)
            4 -> Triple(x, 0.0f, c)
            else -> Triple(c, 0.0f, x)
        }

        return Triple(
            (r1 + m).coerceIn(0.0f, 1.0f),
            (g1 + m).coerceIn(0.0f, 1.0f),
            (b1 + m).coerceIn(0.0f, 1.0f)
        )
    }
}
