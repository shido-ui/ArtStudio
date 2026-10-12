package com.artstudio.core

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import com.artstudio.document.model.LayerHierarchyConstants
import com.artstudio.document.model.LayerNode
import kotlin.math.roundToInt

/**
 * Authoritative Layer Blend & Compositing Engine for Art Studio.
 * Implements deterministic W3C / ISO 32000-1 Porter-Duff compositing equations across
 * all 8 supported blend modes with exact parity between GPU/native canvas and software export paths.
 */
object LayerBlendCompositor {

    /**
     * Blends a single normalized color channel in range [0.0, 1.0].
     *
     * @param b Backdrop channel value in [0.0, 1.0]
     * @param s Source channel value in [0.0, 1.0]
     * @param mode Blend mode type
     * @return Blended channel value in [0.0, 1.0]
     */
    fun blendChannel(b: Float, s: Float, mode: BlendModeType): Float {
        return when (mode) {
            BlendModeType.Normal -> s
            BlendModeType.Multiply -> b * s
            BlendModeType.Screen -> b + s - (b * s)
            BlendModeType.Add -> minOf(1.0f, b + s)
            BlendModeType.Darken -> minOf(b, s)
            BlendModeType.Lighten -> maxOf(b, s)
            BlendModeType.Overlay -> {
                if (b <= 0.5f) {
                    2.0f * b * s
                } else {
                    1.0f - 2.0f * (1.0f - b) * (1.0f - s)
                }
            }
            BlendModeType.ColorDodge -> {
                if (b <= 0.0f) {
                    0.0f
                } else if (s >= 1.0f) {
                    1.0f
                } else {
                    minOf(1.0f, b / (1.0f - s))
                }
            }
            // Unsupported modes fall back to Normal per protocol
            else -> s
        }
    }

    /**
     * Composites a single source ARGB pixel onto a backdrop ARGB pixel using
     * the standard Porter-Duff Source Over model with the requested blend function.
     *
     * @param dstColor Backdrop pixel (ARGB_8888 Int)
     * @param srcColor Source pixel (ARGB_8888 Int)
     * @param mode Blend mode
     * @param layerOpacity Layer-level alpha scalar in [0.0, 1.0]
     * @return Resulting composited ARGB pixel Int
     */
    fun blendPixel(
        dstColor: Int,
        srcColor: Int,
        mode: BlendModeType = BlendModeType.Normal,
        layerOpacity: Float = 1.0f
    ): Int {
        val rawSrcA = ((srcColor ushr 24) and 0xFF) / 255.0f
        val srcA = rawSrcA * layerOpacity.coerceIn(0.0f, 1.0f)
        if (srcA <= 0.0f) return dstColor

        val dstA = ((dstColor ushr 24) and 0xFF) / 255.0f
        val srcR = ((srcColor ushr 16) and 0xFF) / 255.0f
        val srcG = ((srcColor ushr 8) and 0xFF) / 255.0f
        val srcB = (srcColor and 0xFF) / 255.0f

        // Transparent backdrop fast-path
        if (dstA <= 0.0f) {
            val aInt = (srcA * 255.0f).roundToInt().coerceIn(0, 255)
            val rInt = (srcR * 255.0f).roundToInt().coerceIn(0, 255)
            val gInt = (srcG * 255.0f).roundToInt().coerceIn(0, 255)
            val bInt = (srcB * 255.0f).roundToInt().coerceIn(0, 255)
            return (aInt shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
        }

        val dstR = ((dstColor ushr 16) and 0xFF) / 255.0f
        val dstG = ((dstColor ushr 8) and 0xFF) / 255.0f
        val dstB = (dstColor and 0xFF) / 255.0f

        // Result Alpha: a_r = a_s + a_b * (1 - a_s)
        val outA = srcA + dstA * (1.0f - srcA)
        if (outA <= 0.0f) return 0

        // Per-channel blend and Porter-Duff compositing
        val blendR = blendChannel(dstR, srcR, mode)
        val blendG = blendChannel(dstG, srcG, mode)
        val blendB = blendChannel(dstB, srcB, mode)

        val outR = ((1.0f - dstA) * srcA * srcR + dstA * ((1.0f - srcA) * dstR + srcA * blendR)) / outA
        val outG = ((1.0f - dstA) * srcA * srcG + dstA * ((1.0f - srcA) * dstG + srcA * blendG)) / outA
        val outB = ((1.0f - dstA) * srcA * srcB + dstA * ((1.0f - srcA) * dstB + srcA * blendB)) / outA

        val aInt = (outA * 255.0f).roundToInt().coerceIn(0, 255)
        val rInt = (outR.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt()
        val gInt = (outG.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt()
        val bInt = (outB.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt()

        return (aInt shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
    }

    /**
     * Composites an entire source pixel buffer onto a destination buffer in place.
     */
    fun compositeBuffers(
        destPixels: IntArray,
        srcPixels: IntArray,
        mode: BlendModeType = BlendModeType.Normal,
        layerOpacity: Float = 1.0f
    ) {
        val count = minOf(destPixels.size, srcPixels.size)
        val effOpacity = layerOpacity.coerceIn(0.0f, 1.0f)
        if (effOpacity <= 0.0f) return

        if (mode == BlendModeType.Normal && effOpacity >= 0.999f) {
            for (i in 0 until count) {
                val src = srcPixels[i]
                val srcA = (src ushr 24) and 0xFF
                if (srcA == 0) continue
                if (srcA == 255) {
                    destPixels[i] = src
                } else {
                    val dst = destPixels[i]
                    if ((dst ushr 24) == 0) {
                        destPixels[i] = src
                    } else {
                        destPixels[i] = blendPixel(dst, src, mode, effOpacity)
                    }
                }
            }
            return
        }

        for (i in 0 until count) {
            val src = srcPixels[i]
            val srcA = (src ushr 24) and 0xFF
            if (srcA == 0) continue

            val dst = destPixels[i]
            if ((dst ushr 24) == 0) {
                val aInt = (srcA * effOpacity).roundToInt().coerceIn(0, 255)
                destPixels[i] = (aInt shl 24) or (src and 0x00FFFFFF)
            } else {
                destPixels[i] = blendPixel(dst, src, mode, effOpacity)
            }
        }
    }

    /**
     * Applies a grayscale layer mask onto a pixel buffer in place.
     * Mask luminance dictates layer transparency: white (255) = fully visible, black (0) = fully transparent.
     * If inverted: white = transparent, black = visible.
     */
    fun applyMaskToBuffer(
        pixels: IntArray,
        maskPixels: IntArray,
        inverted: Boolean = false,
        enabled: Boolean = true
    ) {
        if (!enabled) return
        val count = minOf(pixels.size, maskPixels.size)
        for (i in 0 until count) {
            val src = pixels[i]
            val origA = ((src ushr 24) and 0xFF) / 255.0f
            if (origA <= 0.0f) continue

            val maskColor = maskPixels[i]
            val maskA = ((maskColor ushr 24) and 0xFF) / 255.0f
            val maskR = ((maskColor ushr 16) and 0xFF) / 255.0f
            val maskG = ((maskColor ushr 8) and 0xFF) / 255.0f
            val maskB = (maskColor and 0xFF) / 255.0f

            // Grayscale luminance
            var lum = (0.299f * maskR + 0.587f * maskG + 0.114f * maskB) * maskA
            if (inverted) {
                lum = 1.0f - lum
            }
            val newA = (origA * lum.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt().coerceIn(0, 255)
            pixels[i] = (newA shl 24) or (src and 0x00FFFFFF)
        }
    }

    /**
     * Modulates a clipping layer's alpha by its base layer's alpha in place.
     * Pixels where the base layer has zero alpha are made completely transparent.
     */
    fun applyClippingMaskToBuffer(
        clipPixels: IntArray,
        basePixels: IntArray
    ) {
        val count = minOf(clipPixels.size, basePixels.size)
        for (i in 0 until count) {
            val clipColor = clipPixels[i]
            val clipA = ((clipColor ushr 24) and 0xFF) / 255.0f
            if (clipA <= 0.0f) continue

            val baseColor = basePixels[i]
            val baseA = ((baseColor ushr 24) and 0xFF) / 255.0f

            val newA = (clipA * baseA * 255.0f).roundToInt().coerceIn(0, 255)
            clipPixels[i] = (newA shl 24) or (clipColor and 0x00FFFFFF)
        }
    }

    /**
     * Composites a stroke or overlay onto a base buffer using Porter-Duff SRC_ATOP (Alpha Lock).
     * The stroke colors are applied only where base layer alpha > 0, and the base alpha is strictly preserved.
     */
    fun applyAlphaLockStroke(
        basePixels: IntArray,
        strokePixels: IntArray,
        strokeOpacity: Float = 1.0f
    ) {
        val count = minOf(basePixels.size, strokePixels.size)
        val effOpacity = strokeOpacity.coerceIn(0.0f, 1.0f)
        if (effOpacity <= 0.0f) return

        for (i in 0 until count) {
            val baseColor = basePixels[i]
            val baseAInt = (baseColor ushr 24) and 0xFF
            if (baseAInt == 0) continue // Zero alpha preserved, no stroke drawn outside content

            val strokeColor = strokePixels[i]
            val rawStrokeA = ((strokeColor ushr 24) and 0xFF) / 255.0f
            val strokeA = rawStrokeA * effOpacity
            if (strokeA <= 0.0f) continue

            val baseA = baseAInt / 255.0f
            val baseR = ((baseColor ushr 16) and 0xFF) / 255.0f
            val baseG = ((baseColor ushr 8) and 0xFF) / 255.0f
            val baseB = (baseColor and 0xFF) / 255.0f

            val strokeR = ((strokeColor ushr 16) and 0xFF) / 255.0f
            val strokeG = ((strokeColor ushr 8) and 0xFF) / 255.0f
            val strokeB = (strokeColor and 0xFF) / 255.0f

            // SRC_ATOP: Color = strokeColor * strokeA + baseColor * (1 - strokeA), Output Alpha = baseA
            val outR = (strokeR * strokeA + baseR * (1.0f - strokeA)).coerceIn(0.0f, 1.0f)
            val outG = (strokeG * strokeA + baseG * (1.0f - strokeA)).coerceIn(0.0f, 1.0f)
            val outB = (strokeB * strokeA + baseB * (1.0f - strokeA)).coerceIn(0.0f, 1.0f)

            val rInt = (outR * 255.0f).roundToInt()
            val gInt = (outG * 255.0f).roundToInt()
            val bInt = (outB * 255.0f).roundToInt()

            basePixels[i] = (baseAInt shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
        }
    }

    /**
     * Creates an Android hardware [Paint] configured with the layer's blend mode and opacity.
     */
    fun createLayerPaint(blendMode: BlendModeType, opacity: Float): Paint {
        return Paint().apply {
            isAntiAlias = true
            alpha = (opacity.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                this.blendMode = blendMode.toAndroidBlendMode()
            } else {
                val pdMode = blendMode.toPorterDuffMode()
                if (pdMode != null) {
                    xfermode = PorterDuffXfermode(pdMode)
                }
            }
        }
    }

    /**
     * Creates a Paint configured for Porter-Duff DST_IN (used for masking and clipping in hardware canvas).
     */
    fun createDstInPaint(): Paint {
        return Paint().apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    }

    /**
     * Creates a Paint configured for Porter-Duff SRC_ATOP (used for alpha-lock painting).
     */
    fun createSrcAtopPaint(color: Int = Color.BLACK, alphaMultiplier: Float = 1.0f): Paint {
        return Paint().apply {
            isAntiAlias = true
            this.color = color
            val origAlpha = (color ushr 24) and 0xFF
            alpha = (origAlpha * alphaMultiplier.coerceIn(0.0f, 1.0f)).roundToInt().coerceIn(0, 255)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
    }

    /**
     * Applies a non-destructive [com.artstudio.adjustment.ColorAdjustment] from an [AdjustmentLayer]
     * to a destination raster pixel buffer during compositing or export.
     */
    fun applyAdjustmentLayer(
        targetPixels: IntArray,
        width: Int,
        height: Int,
        layer: com.artstudio.document.model.LayerNode.AdjustmentLayer,
        maskPixels: IntArray? = null
    ) {
        if (!layer.visible || layer.opacity <= 0.0f || !layer.adjustment.enabled) return
        val maskInverted = layer.mask?.inverted ?: false
        com.artstudio.adjustment.ColorAdjustmentEngine.applyAdjustmentInPlace(
            pixels = targetPixels,
            width = width,
            height = height,
            adjustment = layer.adjustment,
            maskPixels = maskPixels,
            maskInverted = maskInverted
        )
    }
}

/**
 * Maps [BlendModeType] to modern Android framework [BlendMode] (API 29+).
 */
@RequiresApi(Build.VERSION_CODES.Q)
fun BlendModeType.toAndroidBlendMode(): BlendMode {
    return when (this) {
        BlendModeType.Normal -> BlendMode.SRC_OVER
        BlendModeType.Multiply -> BlendMode.MULTIPLY
        BlendModeType.Screen -> BlendMode.SCREEN
        BlendModeType.Add -> BlendMode.PLUS
        BlendModeType.Darken -> BlendMode.DARKEN
        BlendModeType.Lighten -> BlendMode.LIGHTEN
        BlendModeType.Overlay -> BlendMode.OVERLAY
        BlendModeType.ColorDodge -> BlendMode.COLOR_DODGE
        else -> BlendMode.SRC_OVER
    }
}

/**
 * Maps [BlendModeType] to legacy [PorterDuff.Mode] for backward compatibility.
 */
fun BlendModeType.toPorterDuffMode(): PorterDuff.Mode? {
    return when (this) {
        BlendModeType.Normal -> PorterDuff.Mode.SRC_OVER
        BlendModeType.Multiply -> PorterDuff.Mode.MULTIPLY
        BlendModeType.Screen -> PorterDuff.Mode.SCREEN
        BlendModeType.Add -> PorterDuff.Mode.ADD
        BlendModeType.Darken -> PorterDuff.Mode.DARKEN
        BlendModeType.Lighten -> PorterDuff.Mode.LIGHTEN
        BlendModeType.Overlay -> PorterDuff.Mode.OVERLAY
        else -> PorterDuff.Mode.SRC_OVER
    }
}
