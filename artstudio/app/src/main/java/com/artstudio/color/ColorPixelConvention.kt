package com.artstudio.color

/**
 * Architectural color pixel convention authority for ArtStudio.
 *
 * PIXEL CONVENTIONS:
 * 1. LAYERS: Straight ARGB_8888 (un-premultiplied).
 *    Storing straight alpha in layer buffers preserves RGB pigment values
 *    even when opacity is reduced, erased, or masked.
 *
 * 2. FILTERING & COMPOSITING: Premultiplied ARGB_8888.
 *    Spatial filtering (bilinear scaling, Gaussian blur, convolution) and
 *    Porter-Duff blending MUST operate in premultiplied space to eliminate
 *    the dark edge-halo (dirty black/grey fringing) around transparent boundaries.
 *    Results are converted back to straight ARGB for layer persistence.
 *
 * 3. EXPORT & IMPORT: Straight ARGB_8888 tagged explicitly with sRGB (IEC 61966-2-1).
 */
object ColorPixelConvention {

    const val COLOR_SPACE_NAME = "sRGB IEC61966-2.1"

    /**
     * Converts a single 32-bit ARGB pixel from Straight to Premultiplied alpha.
     */
    fun straightToPremultiplied(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        if (a == 255) return argb
        if (a == 0) return 0

        val r = (((argb ushr 16) and 0xFF) * a + 127) / 255
        val g = (((argb ushr 8) and 0xFF) * a + 127) / 255
        val b = ((argb and 0xFF) * a + 127) / 255

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Converts a single 32-bit ARGB pixel from Premultiplied to Straight alpha.
     */
    fun premultipliedToStraight(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        if (a == 255) return argb
        if (a == 0) return 0

        val r = ((((argb ushr 16) and 0xFF) * 255 + a / 2) / a).coerceIn(0, 255)
        val g = ((((argb ushr 8) and 0xFF) * 255 + a / 2) / a).coerceIn(0, 255)
        val b = (((argb and 0xFF) * 255 + a / 2) / a).coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Converts an in-place pixel buffer from Straight to Premultiplied alpha.
     */
    fun convertBufferToPremultiplied(buffer: IntArray) {
        for (i in buffer.indices) {
            buffer[i] = straightToPremultiplied(buffer[i])
        }
    }

    /**
     * Converts an in-place pixel buffer from Premultiplied to Straight alpha.
     */
    fun convertBufferToStraight(buffer: IntArray) {
        for (i in buffer.indices) {
            buffer[i] = premultipliedToStraight(buffer[i])
        }
    }

    /**
     * Samples a pixel from [buffer] with bilinear filtering using PREMULTIPLIED alpha,
     * mathematically eliminating dark edge-halos on transparent borders.
     * Returns Straight ARGB.
     */
    fun sampleBilinearPremultiplied(
        buffer: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Int {
        val x0 = x.toInt().coerceIn(0, width - 1)
        val y0 = y.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceIn(0, width - 1)
        val y1 = (y0 + 1).coerceIn(0, height - 1)

        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)

        val p00 = straightToPremultiplied(buffer[y0 * width + x0])
        val p10 = straightToPremultiplied(buffer[y0 * width + x1])
        val p01 = straightToPremultiplied(buffer[y1 * width + x0])
        val p11 = straightToPremultiplied(buffer[y1 * width + x1])

        val w00 = (1f - fx) * (1f - fy)
        val w10 = fx * (1f - fy)
        val w01 = (1f - fx) * fy
        val w11 = fx * fy

        val a = ((p00 ushr 24 and 0xFF) * w00 + (p10 ushr 24 and 0xFF) * w10 + (p01 ushr 24 and 0xFF) * w01 + (p11 ushr 24 and 0xFF) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val r = ((p00 ushr 16 and 0xFF) * w00 + (p10 ushr 16 and 0xFF) * w10 + (p01 ushr 16 and 0xFF) * w01 + (p11 ushr 16 and 0xFF) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val g = ((p00 ushr 8 and 0xFF) * w00 + (p10 ushr 8 and 0xFF) * w10 + (p01 ushr 8 and 0xFF) * w01 + (p11 ushr 8 and 0xFF) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val b = ((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 + (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11 + 0.5f).toInt().coerceIn(0, 255)

        val premul = (a shl 24) or (r shl 16) or (g shl 8) or b
        return premultipliedToStraight(premul)
    }

    /**
     * Naive sample with straight alpha without premultiplication.
     * Demonstrates the dark halo defect when interpolating against transparent zero pixels.
     */
    fun sampleBilinearStraightNaive(
        buffer: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Int {
        val x0 = x.toInt().coerceIn(0, width - 1)
        val y0 = y.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceIn(0, width - 1)
        val y1 = (y0 + 1).coerceIn(0, height - 1)

        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)

        val p00 = buffer[y0 * width + x0]
        val p10 = buffer[y0 * width + x1]
        val p01 = buffer[y1 * width + x0]
        val p11 = buffer[y1 * width + x1]

        val w00 = (1f - fx) * (1f - fy)
        val w10 = fx * (1f - fy)
        val w01 = (1f - fx) * fy
        val w11 = fx * fy

        val a = ((p00 ushr 24 and 0xFF) * w00 + (p10 ushr 24 and 0xFF) * w10 + (p01 ushr 24 and 0xFF) * w01 + (p11 ushr 24 and 0xFF) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val r = ((p00 ushr 16 and 0xFF) * w00 + (p10 ushr 16 and 0xFF) * w10 + (p01 ushr 16 and 0xFF) * w01 + (p11 ushr 16 and 0xFF) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val g = ((p00 ushr 8 and 0xFF) * w00 + (p10 ushr 8 and 0xFF) * w10 + (p01 ushr 8 and 0xFF) * w01 + (p11 ushr 8 and 0xFF) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val b = ((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 + (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11 + 0.5f).toInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
