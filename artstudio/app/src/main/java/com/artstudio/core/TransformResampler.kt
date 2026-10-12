package com.artstudio.core

import com.artstudio.document.model.InterpolationMode
import com.artstudio.document.model.LayerTransform
import kotlin.math.*

/**
 * Pure-math 2D affine raster resampler supporting Nearest Neighbor, Bilinear, and Bicubic interpolation.
 * Performs inverse-mapping coordinate sampling to guarantee zero gaps and deterministic fidelity.
 */
object TransformResampler {

    /**
     * Resamples an ARGB_8888 source pixel buffer according to [transform] into a destination buffer.
     *
     * @param srcPixels Source ARGB IntArray of size [srcW * srcH]
     * @param srcW Source buffer width
     * @param srcH Source buffer height
     * @param destPixels Destination ARGB IntArray of size [destW * destH]
     * @param destW Destination buffer width
     * @param destH Destination buffer height
     * @param transform 2D geometric transform to apply
     */
    fun resample(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        destPixels: IntArray,
        destW: Int,
        destH: Int,
        transform: LayerTransform
    ) {
        if (destW <= 0 || destH <= 0 || srcW <= 0 || srcH <= 0) return

        val rad = Math.toRadians(transform.rotationDegrees.toDouble())
        val cosTheta = cos(rad)
        val sinTheta = sin(rad)

        val sx = if (transform.flipHorizontal) -transform.scaleX else transform.scaleX
        val sy = if (transform.flipVertical) -transform.scaleY else transform.scaleY

        // Avoid division by zero
        val safeSx = if (abs(sx) < 1e-6f) 1e-6f else sx
        val safeSy = if (abs(sy) < 1e-6f) 1e-6f else sy

        // Center pivot of destination canvas
        val cx = destW / 2.0
        val cy = destH / 2.0

        for (destY in 0 until destH) {
            val dy = (destY + 0.5) - cy - transform.translationY
            val rowOffset = destY * destW

            for (destX in 0 until destW) {
                val dx = (destX + 0.5) - cx - transform.translationX

                // 1. Inverse Rotation
                val unrotX = dx * cosTheta + dy * sinTheta
                val unrotY = -dx * sinTheta + dy * cosTheta

                // 2. Inverse Scale
                val unscaledX = unrotX / safeSx
                val unscaledY = unrotY / safeSy

                // 3. Map back to source coordinates relative to source center
                val srcX = unscaledX + (srcW / 2.0)
                val srcY = unscaledY + (srcH / 2.0)

                // 4. Sample pixel according to interpolation mode
                val sample = when (transform.interpolation) {
                    InterpolationMode.NEAREST_NEIGHBOR -> sampleNearest(srcPixels, srcW, srcH, srcX, srcY)
                    InterpolationMode.BILINEAR -> sampleBilinear(srcPixels, srcW, srcH, srcX, srcY)
                    InterpolationMode.BICUBIC -> sampleBicubic(srcPixels, srcW, srcH, srcX, srcY)
                }

                destPixels[rowOffset + destX] = sample
            }
        }
    }

    private fun sampleNearest(pixels: IntArray, w: Int, h: Int, x: Double, y: Double): Int {
        val px = floor(x).toInt()
        val py = floor(y).toInt()
        if (px !in 0 until w || py !in 0 until h) return 0
        return pixels[py * w + px]
    }

    private fun sampleBilinear(pixels: IntArray, w: Int, h: Int, x: Double, y: Double): Int {
        val fx = x - 0.5
        val fy = y - 0.5

        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        val wx = (fx - x0).toFloat().coerceIn(0f, 1f)
        val wy = (fy - y0).toFloat().coerceIn(0f, 1f)

        val p00 = getPixelClamped(pixels, w, h, x0, y0)
        val p10 = getPixelClamped(pixels, w, h, x1, y0)
        val p01 = getPixelClamped(pixels, w, h, x0, y1)
        val p11 = getPixelClamped(pixels, w, h, x1, y1)

        val a = bilerp((p00 ushr 24) and 0xFF, (p10 ushr 24) and 0xFF, (p01 ushr 24) and 0xFF, (p11 ushr 24) and 0xFF, wx, wy)
        if (a <= 0) return 0

        val r = bilerp((p00 ushr 16) and 0xFF, (p10 ushr 16) and 0xFF, (p01 ushr 16) and 0xFF, (p11 ushr 16) and 0xFF, wx, wy)
        val g = bilerp((p00 ushr 8) and 0xFF, (p10 ushr 8) and 0xFF, (p01 ushr 8) and 0xFF, (p11 ushr 8) and 0xFF, wx, wy)
        val b = bilerp(p00 and 0xFF, p10 and 0xFF, p01 and 0xFF, p11 and 0xFF, wx, wy)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun sampleBicubic(pixels: IntArray, w: Int, h: Int, x: Double, y: Double): Int {
        // Fall back to bilinear for edge bounds, or perform cubic convolution
        val fx = x - 0.5
        val fy = y - 0.5
        val ix = floor(fx).toInt()
        val iy = floor(fy).toInt()

        if (ix < 1 || ix >= w - 2 || iy < 1 || iy >= h - 2) {
            return sampleBilinear(pixels, w, h, x, y)
        }

        val tx = (fx - ix).toFloat()
        val ty = (fy - iy).toFloat()

        fun cubicWeight(t: Float, a: Float = -0.5f): FloatArray {
            val t2 = t * t
            val t3 = t2 * t
            return floatArrayOf(
                a * (t3 - 2f * t2 + t),
                (a + 2f) * t3 - (a + 3f) * t2 + 1f,
                -(a + 2f) * t3 + (2f * a + 3f) * t2 - a * t,
                -a * t3 + a * t2
            )
        }

        val wx = cubicWeight(tx)
        val wy = cubicWeight(ty)

        var accA = 0f
        var accR = 0f
        var accG = 0f
        var accB = 0f

        for (j in 0..3) {
            val py = (iy - 1 + j).coerceIn(0, h - 1)
            val rowOffset = py * w
            for (i in 0..3) {
                val px = (ix - 1 + i).coerceIn(0, w - 1)
                val c = pixels[rowOffset + px]
                val weight = wx[i] * wy[j]

                accA += ((c ushr 24) and 0xFF) * weight
                accR += ((c ushr 16) and 0xFF) * weight
                accG += ((c ushr 8) and 0xFF) * weight
                accB += (c and 0xFF) * weight
            }
        }

        val a = accA.roundToInt().coerceIn(0, 255)
        if (a <= 0) return 0
        val r = accR.roundToInt().coerceIn(0, 255)
        val g = accG.roundToInt().coerceIn(0, 255)
        val b = accB.roundToInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun getPixelClamped(pixels: IntArray, w: Int, h: Int, x: Int, y: Int): Int {
        if (x !in 0 until w || y !in 0 until h) return 0
        return pixels[y * w + x]
    }

    private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
        val top = c00 * (1f - tx) + c10 * tx
        val bottom = c01 * (1f - tx) + c11 * tx
        return (top * (1f - ty) + bottom * ty).roundToInt().coerceIn(0, 255)
    }
}
