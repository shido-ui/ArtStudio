package com.artstudio.cv

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic preprocessing and denoising algorithms for raster illustration analysis.
 *
 * Implements:
 * 1. Perceptual grayscale conversion (Rec. 709).
 * 2. Separable 1D Gaussian blur.
 * 3. Median despeckle filtering (graphite dust and scanner grain reduction).
 * 4. Edge-preserving bilateral filtering.
 */
object CvDenoiseEngine {

    /**
     * Converts an ARGB pixel buffer to an 8-bit grayscale intensity array [0..255].
     * Fully transparent pixels are treated as white background (255) to facilitate line art discovery.
     */
    fun toGrayscale(pixels: IntArray, width: Int, height: Int): IntArray {
        val count = width * height
        val gray = IntArray(count)

        for (i in 0 until count) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            if (a == 0) {
                gray[i] = 255 // Treat transparent background as white paper
                continue
            }

            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF

            // Perceptual Rec. 709 luminance
            val lum = (299 * r + 587 * g + 114 * b) / 1000
            // If semi-transparent, blend with white paper
            gray[i] = if (a < 255) {
                ((lum * a) + (255 * (255 - a))) / 255
            } else {
                lum
            }
        }
        return gray
    }

    /**
     * Separable 1D Gaussian blur operating in O(N * K) time.
     */
    fun gaussianBlur(
        gray: IntArray,
        width: Int,
        height: Int,
        radius: Int = 1,
        sigma: Float = 1.0f
    ): IntArray {
        if (radius <= 0) return gray.clone()

        val kernelSize = 2 * radius + 1
        val kernel = FloatArray(kernelSize)
        val s = max(0.1f, sigma)
        val twoSigmaSq = 2f * s * s
        var sum = 0f

        for (i in -radius..radius) {
            val w = exp(-(i * i).toFloat() / twoSigmaSq)
            kernel[i + radius] = w
            sum += w
        }
        for (i in 0 until kernelSize) {
            kernel[i] /= sum
        }

        // Horizontal pass
        val temp = FloatArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                var acc = 0f
                for (k in -radius..radius) {
                    val kx = (x + k).coerceIn(0, width - 1)
                    acc += gray[rowOffset + kx] * kernel[k + radius]
                }
                temp[rowOffset + x] = acc
            }
        }

        // Vertical pass
        val result = IntArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                var acc = 0f
                for (k in -radius..radius) {
                    val ky = (y + k).coerceIn(0, height - 1)
                    acc += temp[ky * width + x] * kernel[k + radius]
                }
                result[rowOffset + x] = acc.toInt().coerceIn(0, 255)
            }
        }

        return result
    }

    /**
     * Median despeckle filter. Replaces each pixel with the median value of its neighborhood.
     * Eliminates isolated graphite specks and scanner noise while preserving crisp boundary steps.
     */
    fun medianDespeckle(
        gray: IntArray,
        width: Int,
        height: Int,
        radius: Int = 1
    ): IntArray {
        if (radius <= 0) return gray.clone()

        val r = radius.coerceIn(1, 3)
        val windowSize = (2 * r + 1) * (2 * r + 1)
        val window = IntArray(windowSize)
        val result = IntArray(width * height)
        val medianIdx = windowSize / 2

        for (y in 0 until height) {
            val yMin = max(0, y - r)
            val yMax = min(height - 1, y + r)

            for (x in 0 until width) {
                val xMin = max(0, x - r)
                val xMax = min(width - 1, x + r)

                var count = 0
                for (ny in yMin..yMax) {
                    val rowOffset = ny * width
                    for (nx in xMin..xMax) {
                        window[count++] = gray[rowOffset + nx]
                    }
                }

                // Insertion sort on small array
                for (i in 1 until count) {
                    val key = window[i]
                    var j = i - 1
                    while (j >= 0 && window[j] > key) {
                        window[j + 1] = window[j]
                        j--
                    }
                    window[j + 1] = key
                }

                result[y * width + x] = window[count / 2]
            }
        }

        return result
    }

    /**
     * Bilateral filter for edge-preserving surface smoothing.
     */
    fun bilateralFilter(
        gray: IntArray,
        width: Int,
        height: Int,
        radius: Int = 2,
        sigmaSpace: Float = 2.0f,
        sigmaRange: Float = 25.0f
    ): IntArray {
        if (radius <= 0) return gray.clone()

        val result = IntArray(width * height)
        val twoSigmaSpaceSq = 2f * sigmaSpace * sigmaSpace
        val twoSigmaRangeSq = 2f * sigmaRange * sigmaRange

        for (y in 0 until height) {
            val yMin = max(0, y - radius)
            val yMax = min(height - 1, y + radius)

            for (x in 0 until width) {
                val xMin = max(0, x - radius)
                val xMax = min(width - 1, x + radius)
                val centerVal = gray[y * width + x]

                var sumWeights = 0f
                var sumValues = 0f

                for (ny in yMin..yMax) {
                    val dy = ny - y
                    val rowOffset = ny * width
                    for (nx in xMin..xMax) {
                        val dx = nx - x
                        val neighborVal = gray[rowOffset + nx]
                        val diffRange = neighborVal - centerVal

                        val spaceWeight = exp(-(dx * dx + dy * dy).toFloat() / twoSigmaSpaceSq)
                        val rangeWeight = exp(-(diffRange * diffRange).toFloat() / twoSigmaRangeSq)
                        val weight = spaceWeight * rangeWeight

                        sumWeights += weight
                        sumValues += neighborVal * weight
                    }
                }

                result[y * width + x] = if (sumWeights > 0f) {
                    (sumValues / sumWeights).toInt().coerceIn(0, 255)
                } else {
                    centerVal
                }
            }
        }

        return result
    }

    /**
     * Normalizes non-uniform illumination, vignetting, and paper shadows across real photographed/scanned sketches.
     * Computes a local background surface estimate using a fast separable moving average,
     * then normalizes local pixel intensities by the estimated background to flatten paper to uniform white.
     */
    fun normalizeIllumination(
        gray: IntArray,
        width: Int,
        height: Int,
        radius: Int = 32
    ): IntArray {
        val count = width * height
        if (count == 0 || radius <= 0) return gray.clone()

        val r = radius.coerceIn(4, 128)
        val result = IntArray(count)

        // 1. Fast separable moving average in O(W * H) using sliding window
        // Horizontal pass
        val temp = FloatArray(count)
        for (y in 0 until height) {
            val rowOffset = y * width
            var windowSum = 0L
            val initialWindow = min(width, r + 1)
            for (x in 0 until initialWindow) {
                windowSum += gray[rowOffset + x]
            }

            for (x in 0 until width) {
                val left = max(0, x - r)
                val right = min(width - 1, x + r)
                val windowLen = right - left + 1

                if (x > 0) {
                    val addIdx = x + r
                    if (addIdx < width) {
                        windowSum += gray[rowOffset + addIdx]
                    }
                    val removeIdx = x - r - 1
                    if (removeIdx >= 0) {
                        windowSum -= gray[rowOffset + removeIdx]
                    }
                }
                temp[rowOffset + x] = windowSum.toFloat() / windowLen.toFloat()
            }
        }

        // Vertical pass & normalization
        for (x in 0 until width) {
            var windowSum = 0.0
            val initialWindow = min(height, r + 1)
            for (y in 0 until initialWindow) {
                windowSum += temp[y * width + x]
            }

            for (y in 0 until height) {
                if (y > 0) {
                    val addIdx = y + r
                    if (addIdx < height) {
                        windowSum += temp[addIdx * width + x]
                    }
                    val removeIdx = y - r - 1
                    if (removeIdx >= 0) {
                        windowSum -= temp[removeIdx * width + x]
                    }
                }
                val top = max(0, y - r)
                val bottom = min(height - 1, y + r)
                val windowLen = bottom - top + 1
                val localBg = (windowSum / windowLen).toFloat().coerceAtLeast(1.0f)

                val idx = y * width + x
                val pixelVal = gray[idx]

                // Normalization: scaled ratio of pixel to local background
                // Paper areas where pixelVal ≈ localBg scale to ~255 (clean white paper)
                // Dark pencil/ink lines remain sharp and dark
                val normalized = ((pixelVal.toFloat() / localBg) * 255f).toInt().coerceIn(0, 255)
                result[idx] = normalized
            }
        }

        return result
    }
}
