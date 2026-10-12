package com.artstudio.filter

import kotlin.math.*

/**
 * High-performance, memory-bounded image processing filter engine.
 *
 * Implements a verified suite of 7 deterministic image operations:
 * 1. Gaussian Blur (Separable 1D convolution with alpha-weighting)
 * 2. Sharpen (Unsharp Masking with high-pass boost)
 * 3. Outline (Sobel gradient magnitude edge detection)
 * 4. Glow (Highlight extraction + Gaussian spread + Additive composite)
 * 5. Procedural Noise (Deterministic pseudo-random hash addition)
 * 6. Pixelate (Mosaic grid block spatial averaging)
 * 7. Ripple (Sinusoidal displacement mapping with bilinear sampling)
 */
object ImageFilterEngine {

    /**
     * Executes the requested filter on a 32-bit ARGB pixel buffer.
     *
     * @param src Source pixel buffer in ARGB format (length = width * height).
     * @param width Image width in pixels (> 0).
     * @param height Image height in pixels (> 0).
     * @param config Strongly-typed filter configuration.
     * @param token Optional cooperative cancellation token.
     * @return Transformed 32-bit ARGB pixel buffer of identical dimensions.
     */
    fun applyFilter(
        src: IntArray,
        width: Int,
        height: Int,
        config: FilterConfig,
        token: FilterCancellationToken? = null
    ): IntArray {
        require(width > 0) { "Width must be positive, got $width" }
        require(height > 0) { "Height must be positive, got $height" }
        require(src.size >= width * height) { "Buffer size (${src.size}) smaller than required ($width * $height)" }

        return when (config) {
            is FilterConfig.GaussianBlurConfig -> applyGaussianBlur(src, width, height, config, token)
            is FilterConfig.SharpenConfig -> applySharpen(src, width, height, config, token)
            is FilterConfig.OutlineConfig -> applyOutline(src, width, height, config, token)
            is FilterConfig.GlowConfig -> applyGlow(src, width, height, config, token)
            is FilterConfig.NoiseConfig -> applyNoise(src, width, height, config, token)
            is FilterConfig.PixelateConfig -> applyPixelate(src, width, height, config, token)
            is FilterConfig.RippleConfig -> applyRipple(src, width, height, config, token)
        }
    }

    /**
     * Generates a memory-bounded downsampled preview buffer (maximum dimension <= maxDim).
     *
     * @return Triple of (previewPixels, previewWidth, previewHeight).
     */
    fun downscaleForPreview(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        maxDim: Int = 512
    ): Triple<IntArray, Int, Int> {
        val maxSrc = max(srcW, srcH)
        if (maxSrc <= maxDim) {
            return Triple(src.clone(), srcW, srcH)
        }

        val scale = maxDim.toFloat() / maxSrc
        val dstW = max(1, (srcW * scale).roundToInt())
        val dstH = max(1, (srcH * scale).roundToInt())
        val dst = IntArray(dstW * dstH)

        val xRatio = srcW.toFloat() / dstW
        val yRatio = srcH.toFloat() / dstH

        for (y in 0 until dstH) {
            val srcY = (y * yRatio).toInt().coerceIn(0, srcH - 1)
            val srcRowOffset = srcY * srcW
            val dstRowOffset = y * dstW
            for (x in 0 until dstW) {
                val srcX = (x * xRatio).toInt().coerceIn(0, srcW - 1)
                dst[dstRowOffset + x] = src[srcRowOffset + srcX]
            }
        }

        return Triple(dst, dstW, dstH)
    }

    // =========================================================================
    // 1. Gaussian Blur (Separable 1D Convolutions)
    // =========================================================================

    fun applyGaussianBlur(
        src: IntArray,
        width: Int,
        height: Int,
        config: FilterConfig.GaussianBlurConfig,
        token: FilterCancellationToken? = null
    ): IntArray {
        var current = src.clone()
        val radius = config.radius.coerceIn(1f, 50f)
        val sigma = max(0.5f, radius / 2.5f)
        val kernelHalf = min(radius.toInt(), 25)
        val kernelSize = kernelHalf * 2 + 1

        // Precompute normalized 1D Gaussian kernel
        val kernel = FloatArray(kernelSize)
        var sum = 0f
        val twoSigmaSq = 2f * sigma * sigma
        for (i in -kernelHalf..kernelHalf) {
            val w = exp(-(i * i).toFloat() / twoSigmaSq)
            kernel[i + kernelHalf] = w
            sum += w
        }
        for (i in 0 until kernelSize) {
            kernel[i] /= sum
        }

        for (iter in 0 until config.iterations) {
            if (token?.isCancelled == true) return current

            // Horizontal Pass
            val horiz = IntArray(width * height)
            for (y in 0 until height) {
                if (token?.isCancelled == true) return current
                val rowOffset = y * width
                for (x in 0 until width) {
                    var aSum = 0f
                    var rSum = 0f
                    var gSum = 0f
                    var bSum = 0f
                    var totalWeight = 0f

                    for (k in -kernelHalf..kernelHalf) {
                        val sampleX = (x + k).coerceIn(0, width - 1)
                        val p = current[rowOffset + sampleX]
                        val kw = kernel[k + kernelHalf]

                        val a = (p ushr 24) and 0xFF
                        val r = (p ushr 16) and 0xFF
                        val g = (p ushr 8) and 0xFF
                        val b = p and 0xFF

                        // Premultiplied weighting
                        val alphaFrac = a / 255f
                        aSum += a * kw
                        rSum += r * alphaFrac * kw
                        gSum += g * alphaFrac * kw
                        bSum += b * alphaFrac * kw
                        totalWeight += kw
                    }

                    val finalA = (aSum / totalWeight).roundToInt().coerceIn(0, 255)
                    val unPremult = if (finalA > 0) 255f / finalA else 0f
                    val finalR = (rSum * unPremult).roundToInt().coerceIn(0, 255)
                    val finalG = (gSum * unPremult).roundToInt().coerceIn(0, 255)
                    val finalB = (bSum * unPremult).roundToInt().coerceIn(0, 255)

                    horiz[rowOffset + x] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                }
            }

            // Vertical Pass
            val vert = IntArray(width * height)
            for (x in 0 until width) {
                if (token?.isCancelled == true) return current
                for (y in 0 until height) {
                    var aSum = 0f
                    var rSum = 0f
                    var gSum = 0f
                    var bSum = 0f
                    var totalWeight = 0f

                    for (k in -kernelHalf..kernelHalf) {
                        val sampleY = (y + k).coerceIn(0, height - 1)
                        val p = horiz[sampleY * width + x]
                        val kw = kernel[k + kernelHalf]

                        val a = (p ushr 24) and 0xFF
                        val r = (p ushr 16) and 0xFF
                        val g = (p ushr 8) and 0xFF
                        val b = p and 0xFF

                        val alphaFrac = a / 255f
                        aSum += a * kw
                        rSum += r * alphaFrac * kw
                        gSum += g * alphaFrac * kw
                        bSum += b * alphaFrac * kw
                        totalWeight += kw
                    }

                    val finalA = (aSum / totalWeight).roundToInt().coerceIn(0, 255)
                    val unPremult = if (finalA > 0) 255f / finalA else 0f
                    val finalR = (rSum * unPremult).roundToInt().coerceIn(0, 255)
                    val finalG = (gSum * unPremult).roundToInt().coerceIn(0, 255)
                    val finalB = (bSum * unPremult).roundToInt().coerceIn(0, 255)

                    vert[y * width + x] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                }
            }

            current = vert
        }

        return current
    }

    // =========================================================================
    // 2. Sharpen (Unsharp Mask)
    // =========================================================================

    fun applySharpen(
        src: IntArray,
        width: Int,
        height: Int,
        config: FilterConfig.SharpenConfig,
        token: FilterCancellationToken? = null
    ): IntArray {
        // High-pass detail extraction via Gaussian blur subtraction
        val blurred = applyGaussianBlur(
            src = src,
            width = width,
            height = height,
            config = FilterConfig.GaussianBlurConfig(radius = config.radius, iterations = 1),
            token = token
        )
        if (token?.isCancelled == true) return src.clone()

        val dst = IntArray(width * height)
        val strength = config.strength

        for (i in src.indices) {
            val orig = src[i]
            val blur = blurred[i]

            val a = (orig ushr 24) and 0xFF
            val rOrig = (orig ushr 16) and 0xFF
            val gOrig = (orig ushr 8) and 0xFF
            val bOrig = orig and 0xFF

            val rBlur = (blur ushr 16) and 0xFF
            val gBlur = (blur ushr 8) and 0xFF
            val bBlur = blur and 0xFF

            val rSharp = (rOrig + strength * (rOrig - rBlur)).roundToInt().coerceIn(0, 255)
            val gSharp = (gOrig + strength * (gOrig - gBlur)).roundToInt().coerceIn(0, 255)
            val bSharp = (bOrig + strength * (bOrig - bBlur)).roundToInt().coerceIn(0, 255)

            dst[i] = (a shl 24) or (rSharp shl 16) or (gSharp shl 8) or bSharp
        }

        return dst
    }

    // =========================================================================
    // 3. Outline (Sobel Edge Detector)
    // =========================================================================

    fun applyOutline(
        src: IntArray,
        width: Int,
        height: Int,
        config: FilterConfig.OutlineConfig,
        token: FilterCancellationToken? = null
    ): IntArray {
        val dst = IntArray(width * height)
        val edgeColor = config.edgeColor.toInt()
        val bgColor = config.backgroundColor.toInt()
        val thresh = config.threshold

        // Sobel kernels
        // Gx: [-1 0 1, -2 0 2, -1 0 1]
        // Gy: [-1 -2 -1, 0 0 0, 1 2 1]

        fun luminanceAt(x: Int, y: Int): Float {
            val cx = x.coerceIn(0, width - 1)
            val cy = y.coerceIn(0, height - 1)
            val p = src[cy * width + cx]
            val a = (p ushr 24) and 0xFF
            if (a == 0) return 0f
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            return (0.299f * r + 0.587f * g + 0.114f * b) * (a / 255f)
        }

        for (y in 0 until height) {
            if (token?.isCancelled == true) return src.clone()
            val rowOffset = y * width
            for (x in 0 until width) {
                val l00 = luminanceAt(x - 1, y - 1)
                val l10 = luminanceAt(x, y - 1)
                val l20 = luminanceAt(x + 1, y - 1)

                val l01 = luminanceAt(x - 1, y)
                val l21 = luminanceAt(x + 1, y)

                val l02 = luminanceAt(x - 1, y + 1)
                val l12 = luminanceAt(x, y + 1)
                val l22 = luminanceAt(x + 1, y + 1)

                val gx = (l20 + 2f * l21 + l22) - (l00 + 2f * l01 + l02)
                val gy = (l02 + 2f * l12 + l22) - (l00 + 2f * l10 + l20)
                val mag = hypot(gx, gy)

                val isEdge = mag >= thresh
                val meetsEdge = if (config.invert) !isEdge else isEdge

                dst[rowOffset + x] = if (meetsEdge) edgeColor else bgColor
            }
        }

        return dst
    }

    // =========================================================================
    // 4. Glow / Bloom (Highlight Extraction + Gaussian Blur + Additive Composite)
    // =========================================================================

    fun applyGlow(
        src: IntArray,
        width: Int,
        height: Int,
        config: FilterConfig.GlowConfig,
        token: FilterCancellationToken? = null
    ): IntArray {
        // Step 1: Extract bright highlights above luminance threshold
        val brightPass = IntArray(width * height)
        val thresh = config.threshold

        for (i in src.indices) {
            val p = src[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) {
                brightPass[i] = 0
                continue
            }
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF

            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            if (lum >= thresh) {
                val boost = (lum - thresh) / max(0.01f, 1f - thresh)
                val br = (r * boost).roundToInt().coerceIn(0, 255)
                val bg = (g * boost).roundToInt().coerceIn(0, 255)
                val bb = (b * boost).roundToInt().coerceIn(0, 255)
                brightPass[i] = (a shl 24) or (br shl 16) or (bg shl 8) or bb
            } else {
                brightPass[i] = 0
            }
        }

        if (token?.isCancelled == true) return src.clone()

        // Step 2: Blur the extracted highlights
        val blurredHighlights = applyGaussianBlur(
            src = brightPass,
            width = width,
            height = height,
            config = FilterConfig.GaussianBlurConfig(radius = config.radius, iterations = 2),
            token = token
        )

        if (token?.isCancelled == true) return src.clone()

        // Step 3: Additive composite onto original
        val dst = IntArray(width * height)
        val intensity = config.intensity

        for (i in src.indices) {
            val orig = src[i]
            val glow = blurredHighlights[i]

            val a = (orig ushr 24) and 0xFF
            val rOrig = (orig ushr 16) and 0xFF
            val gOrig = (orig ushr 8) and 0xFF
            val bOrig = orig and 0xFF

            val rGlow = (glow ushr 16) and 0xFF
            val gGlow = (glow ushr 8) and 0xFF
            val bGlow = glow and 0xFF

            val rOut = (rOrig + intensity * rGlow).roundToInt().coerceIn(0, 255)
            val gOut = (gOrig + intensity * gGlow).roundToInt().coerceIn(0, 255)
            val bOut = (bOrig + intensity * bGlow).roundToInt().coerceIn(0, 255)

            dst[i] = (a shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
        }

        return dst
    }

    // =========================================================================
    // 5. Procedural Noise (Deterministic Pseudo-Random Addition)
    // =========================================================================

    fun applyNoise(
        src: IntArray,
        width: Int,
        height: Int,
        config: FilterConfig.NoiseConfig,
        token: FilterCancellationToken? = null
    ): IntArray {
        val dst = IntArray(width * height)
        val amount = config.amount
        val seed = config.seed
        val isMono = config.monochrome

        fun hash2D(x: Int, y: Int, s: Int): Float {
            var h = s xor (x * 374761393 + y * 668265263)
            h = (h xor (h ushr 13)) * 1274126177
            val u = (h xor (h ushr 16)) and 0x7FFFFFFF
            return (u / 2147483647f) * 2f - 1f // [-1.0, 1.0]
        }

        for (y in 0 until height) {
            if (token?.isCancelled == true) return src.clone()
            val rowOffset = y * width
            for (x in 0 until width) {
                val p = src[rowOffset + x]
                val a = (p ushr 24) and 0xFF
                if (a == 0) {
                    dst[rowOffset + x] = 0
                    continue
                }

                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF

                if (isMono) {
                    val delta = (hash2D(x, y, seed) * amount * 255f).roundToInt()
                    val rOut = (r + delta).coerceIn(0, 255)
                    val gOut = (g + delta).coerceIn(0, 255)
                    val bOut = (b + delta).coerceIn(0, 255)
                    dst[rowOffset + x] = (a shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                } else {
                    val dR = (hash2D(x, y, seed) * amount * 255f).roundToInt()
                    val dG = (hash2D(x, y, seed + 1013) * amount * 255f).roundToInt()
                    val dB = (hash2D(x, y, seed + 2027) * amount * 255f).roundToInt()
                    val rOut = (r + dR).coerceIn(0, 255)
                    val gOut = (g + dG).coerceIn(0, 255)
                    val bOut = (b + dB).coerceIn(0, 255)
                    dst[rowOffset + x] = (a shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                }
            }
        }

        return dst
    }

    // =========================================================================
    // 6. Pixelate (Mosaic Grid Spatial Averaging)
    // =========================================================================

    fun applyPixelate(
        src: IntArray,
        width: Int,
        height: Int,
        config: FilterConfig.PixelateConfig,
        token: FilterCancellationToken? = null
    ): IntArray {
        val dst = IntArray(width * height)
        val blockSize = config.blockSize.coerceIn(2, 64)

        for (by in 0 until height step blockSize) {
            if (token?.isCancelled == true) return src.clone()
            val endY = min(by + blockSize, height)

            for (bx in 0 until width step blockSize) {
                val endX = min(bx + blockSize, width)

                // Compute block average color
                var aSum = 0L
                var rSum = 0L
                var gSum = 0L
                var bSum = 0L
                var count = 0

                for (y in by until endY) {
                    val row = y * width
                    for (x in bx until endX) {
                        val p = src[row + x]
                        val a = (p ushr 24) and 0xFF
                        val r = (p ushr 16) and 0xFF
                        val g = (p ushr 8) and 0xFF
                        val b = p and 0xFF

                        aSum += a
                        rSum += r
                        gSum += g
                        bSum += b
                        count++
                    }
                }

                if (count > 0) {
                    val avgA = (aSum / count).toInt().coerceIn(0, 255)
                    val avgR = (rSum / count).toInt().coerceIn(0, 255)
                    val avgG = (gSum / count).toInt().coerceIn(0, 255)
                    val avgB = (bSum / count).toInt().coerceIn(0, 255)
                    val blockColor = (avgA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB

                    for (y in by until endY) {
                        val row = y * width
                        for (x in bx until endX) {
                            dst[row + x] = blockColor
                        }
                    }
                }
            }
        }

        return dst
    }

    // =========================================================================
    // 7. Ripple (Sinusoidal Displacement with Bilinear Sampling)
    // =========================================================================

    fun applyRipple(
        src: IntArray,
        width: Int,
        height: Int,
        config: FilterConfig.RippleConfig,
        token: FilterCancellationToken? = null
    ): IntArray {
        val dst = IntArray(width * height)
        val amp = config.amplitude
        val waveLen = config.wavelength
        val phaseRad = Math.toRadians(config.phaseDeg.toDouble()).toFloat()
        val twoPiOverLen = (2.0 * Math.PI / waveLen).toFloat()

        // Bilinear sampler with clamp-to-edge
        fun sampleBilinear(fx: Float, fy: Float): Int {
            val x0 = fx.toInt().coerceIn(0, width - 1)
            val y0 = fy.toInt().coerceIn(0, height - 1)
            val x1 = (x0 + 1).coerceIn(0, width - 1)
            val y1 = (y0 + 1).coerceIn(0, height - 1)

            val wx = (fx - fx.toInt()).coerceIn(0f, 1f)
            val wy = (fy - fy.toInt()).coerceIn(0f, 1f)

            val p00 = src[y0 * width + x0]
            val p10 = src[y0 * width + x1]
            val p01 = src[y1 * width + x0]
            val p11 = src[y1 * width + x1]

            fun blendChannel(c00: Int, c10: Int, c01: Int, c11: Int): Int {
                val top = c00 * (1f - wx) + c10 * wx
                val bot = c01 * (1f - wx) + c11 * wx
                return (top * (1f - wy) + bot * wy).roundToInt().coerceIn(0, 255)
            }

            val a = blendChannel((p00 ushr 24) and 0xFF, (p10 ushr 24) and 0xFF, (p01 ushr 24) and 0xFF, (p11 ushr 24) and 0xFF)
            val r = blendChannel((p00 ushr 16) and 0xFF, (p10 ushr 16) and 0xFF, (p01 ushr 16) and 0xFF, (p11 ushr 16) and 0xFF)
            val g = blendChannel((p00 ushr 8) and 0xFF, (p10 ushr 8) and 0xFF, (p01 ushr 8) and 0xFF, (p11 ushr 8) and 0xFF)
            val b = blendChannel(p00 and 0xFF, p10 and 0xFF, p01 and 0xFF, p11 and 0xFF)

            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        for (y in 0 until height) {
            if (token?.isCancelled == true) return src.clone()
            val rowOffset = y * width
            for (x in 0 until width) {
                val radY = y * twoPiOverLen + phaseRad
                val radX = x * twoPiOverLen + phaseRad

                val dx = amp * sin(radY)
                val dy = amp * cos(radX)

                val sampleX = (x + dx).coerceIn(0f, (width - 1).toFloat())
                val sampleY = (y + dy).coerceIn(0f, (height - 1).toFloat())

                dst[rowOffset + x] = sampleBilinear(sampleX, sampleY)
            }
        }

        return dst
    }
}
