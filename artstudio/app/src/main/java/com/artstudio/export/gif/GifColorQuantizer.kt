package com.artstudio.export.gif

import kotlin.math.sqrt

/**
 * Result of color quantization for a GIF frame.
 *
 * @param palette 768-byte RGB palette (256 * 3 bytes)
 * @param indexedPixels Array of size (width * height) containing palette indices 0..255
 * @param transparentIndex Palette index representing 1-bit transparency, or null if frame is opaque
 * @param colorCount Number of unique colors allocated in the palette
 */
data class QuantizedGifFrame(
    val palette: ByteArray,
    val indexedPixels: ByteArray,
    val transparentIndex: Int?,
    val colorCount: Int
) {
    init {
        require(palette.size == 768) { "Palette must be exactly 768 bytes (256 * 3), got ${palette.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuantizedGifFrame) return false
        return transparentIndex == other.transparentIndex &&
                colorCount == other.colorCount &&
                palette.contentEquals(other.palette) &&
                indexedPixels.contentEquals(other.indexedPixels)
    }

    override fun hashCode(): Int {
        var result = transparentIndex?.hashCode() ?: 0
        result = 31 * result + colorCount
        result = 31 * result + palette.contentHashCode()
        result = 31 * result + indexedPixels.contentHashCode()
        return result
    }
}

/**
 * High-performance pure-Kotlin Median-Cut color quantizer for GIF89a.
 *
 * Features:
 * 1. 1-bit alpha detection: if transparent pixels exist, reserves palette index 0 as transparent.
 * 2. Exact direct palette: if unique opaque colors <= capacity, zero color distortion.
 * 3. Fast Median-Cut clustering: splits color bounding boxes along widest channel range.
 * 4. 15-bit RGB quantization lookup cache for O(1) nearest-neighbor indexing without allocations.
 */
object GifColorQuantizer {

    private const val MAX_PALETTE_COLORS = 256
    private const val DEFAULT_ALPHA_THRESHOLD = 128

    fun quantize(
        pixels: IntArray,
        width: Int,
        height: Int,
        alphaThreshold: Int = DEFAULT_ALPHA_THRESHOLD
    ): QuantizedGifFrame {
        val totalPixels = width * height
        require(pixels.size == totalPixels) { "Pixel array size (${pixels.size}) does not match dimensions ${width}x${height}" }

        // 1. Detect transparency and gather opaque colors
        var hasTransparent = false
        val colorCounts = mutableMapOf<Int, Int>()

        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a < alphaThreshold) {
                hasTransparent = true
            } else {
                val rgb = p and 0x00FFFFFF
                colorCounts[rgb] = (colorCounts[rgb] ?: 0) + 1
            }
        }

        val transparentIndex: Int? = if (hasTransparent) 0 else null
        val offset = if (hasTransparent) 1 else 0
        val maxOpaqueColors = MAX_PALETTE_COLORS - offset

        val palette = ByteArray(MAX_PALETTE_COLORS * 3)
        val indexedPixels = ByteArray(totalPixels)

        // If completely empty/transparent
        if (colorCounts.isEmpty()) {
            return QuantizedGifFrame(
                palette = palette,
                indexedPixels = indexedPixels,
                transparentIndex = 0,
                colorCount = 1
            )
        }

        val opaqueColors: List<Int>
        val directMap = mutableMapOf<Int, Int>()

        if (colorCounts.size <= maxOpaqueColors) {
            // Direct palette: 1:1 color matching
            opaqueColors = colorCounts.keys.toList()
            for (i in opaqueColors.indices) {
                val rgb = opaqueColors[i]
                val idx = offset + i
                directMap[rgb] = idx
                palette[idx * 3] = ((rgb ushr 16) and 0xFF).toByte()
                palette[idx * 3 + 1] = ((rgb ushr 8) and 0xFF).toByte()
                palette[idx * 3 + 2] = (rgb and 0xFF).toByte()
            }

            for (i in 0 until totalPixels) {
                val p = pixels[i]
                val a = (p ushr 24) and 0xFF
                if (a < alphaThreshold) {
                    indexedPixels[i] = 0 // transparent index
                } else {
                    val rgb = p and 0x00FFFFFF
                    indexedPixels[i] = (directMap[rgb] ?: offset).toByte()
                }
            }

            return QuantizedGifFrame(
                palette = palette,
                indexedPixels = indexedPixels,
                transparentIndex = transparentIndex,
                colorCount = offset + opaqueColors.size
            )
        }

        // Median-Cut clustering
        val clusterPalette = medianCut(colorCounts, maxOpaqueColors)
        val paletteRgbList = mutableListOf<Triple<Int, Int, Int>>()

        for (i in clusterPalette.indices) {
            val rgb = clusterPalette[i]
            val idx = offset + i
            val r = (rgb ushr 16) and 0xFF
            val g = (rgb ushr 8) and 0xFF
            val b = rgb and 0xFF
            palette[idx * 3] = r.toByte()
            palette[idx * 3 + 1] = g.toByte()
            palette[idx * 3 + 2] = b.toByte()
            paletteRgbList.add(Triple(r, g, b))
        }

        // 15-bit fast quantization cache (32768 entries, 5 bits per channel)
        val cache = ShortArray(32768) { -1 }

        fun findNearestPaletteIndex(rgb: Int): Int {
            val r5 = ((rgb ushr 16) and 0xFF) ushr 3
            val g5 = ((rgb ushr 8) and 0xFF) ushr 3
            val b5 = (rgb and 0xFF) ushr 3
            val cacheKey = (r5 shl 10) or (g5 shl 5) or b5

            val cached = cache[cacheKey].toInt()
            if (cached >= 0) return cached

            val r = (rgb ushr 16) and 0xFF
            val g = (rgb ushr 8) and 0xFF
            val b = rgb and 0xFF

            var bestDist = Int.MAX_VALUE
            var bestIdx = offset

            for (i in paletteRgbList.indices) {
                val (pr, pg, pb) = paletteRgbList[i]
                val dr = r - pr
                val dg = g - pg
                val db = b - pb
                val dist = dr * dr + dg * dg + db * db
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = offset + i
                    if (dist == 0) break
                }
            }

            cache[cacheKey] = bestIdx.toShort()
            return bestIdx
        }

        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a < alphaThreshold) {
                indexedPixels[i] = 0 // transparent index
            } else {
                val rgb = p and 0x00FFFFFF
                val idx = findNearestPaletteIndex(rgb)
                indexedPixels[i] = idx.toByte()
            }
        }

        return QuantizedGifFrame(
            palette = palette,
            indexedPixels = indexedPixels,
            transparentIndex = transparentIndex,
            colorCount = offset + clusterPalette.size
        )
    }

    private data class ColorBox(
        val colors: IntArray,
        val startIndex: Int,
        val endIndex: Int
    ) {
        val count: Int get() = endIndex - startIndex
    }

    private fun medianCut(colorFrequencies: Map<Int, Int>, targetColors: Int): List<Int> {
        val uniqueColors = colorFrequencies.keys.toIntArray()
        if (uniqueColors.size <= targetColors) return uniqueColors.toList()

        val boxes = mutableListOf(ColorBox(uniqueColors, 0, uniqueColors.size))

        while (boxes.size < targetColors) {
            // Find box with greatest range across R, G, B
            var bestBoxIdx = -1
            var maxRange = -1
            var bestChannel = 0 // 0=R, 1=G, 2=B

            for (i in boxes.indices) {
                val box = boxes[i]
                if (box.count <= 1) continue

                var minR = 255; var maxR = 0
                var minG = 255; var maxG = 0
                var minB = 255; var maxB = 0

                for (j in box.startIndex until box.endIndex) {
                    val c = box.colors[j]
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF
                    if (r < minR) minR = r; if (r > maxR) maxR = r
                    if (g < minG) minG = g; if (g > maxG) maxG = g
                    if (b < minB) minB = b; if (b > maxB) maxB = b
                }

                val rangeR = maxR - minR
                val rangeG = maxG - minG
                val rangeB = maxB - minB
                val boxMax = maxOf(rangeR, maxOf(rangeG, rangeB))

                if (boxMax > maxRange) {
                    maxRange = boxMax
                    bestBoxIdx = i
                    bestChannel = when {
                        rangeR >= rangeG && rangeR >= rangeB -> 0
                        rangeG >= rangeB -> 1
                        else -> 2
                    }
                }
            }

            if (bestBoxIdx == -1 || maxRange <= 0) break

            val boxToSplit = boxes.removeAt(bestBoxIdx)
            // Sort colors in box along chosen channel
            val slice = boxToSplit.colors.copyOfRange(boxToSplit.startIndex, boxToSplit.endIndex)
            when (bestChannel) {
                0 -> slice.sortBy { (it ushr 16) and 0xFF }
                1 -> slice.sortBy { (it ushr 8) and 0xFF }
                2 -> slice.sortBy { it and 0xFF }
            }
            slice.copyInto(boxToSplit.colors, boxToSplit.startIndex)

            val mid = boxToSplit.startIndex + (boxToSplit.count / 2)
            boxes.add(ColorBox(boxToSplit.colors, boxToSplit.startIndex, mid))
            boxes.add(ColorBox(boxToSplit.colors, mid, boxToSplit.endIndex))
        }

        // Calculate weighted centroids for each box
        val palette = mutableListOf<Int>()
        for (box in boxes) {
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var totalWeight = 0L

            for (i in box.startIndex until box.endIndex) {
                val c = box.colors[i]
                val weight = colorFrequencies[c]?.toLong() ?: 1L
                sumR += (((c ushr 16) and 0xFF) * weight)
                sumG += (((c ushr 8) and 0xFF) * weight)
                sumB += ((c and 0xFF) * weight)
                totalWeight += weight
            }

            val denom = maxOf(1L, totalWeight)
            val avgR = (sumR / denom).toInt().coerceIn(0, 255)
            val avgG = (sumG / denom).toInt().coerceIn(0, 255)
            val avgB = (sumB / denom).toInt().coerceIn(0, 255)
            palette.add((avgR shl 16) or (avgG shl 8) or avgB)
        }

        return palette
    }

    private fun IntArray.sortBy(selector: (Int) -> Int) {
        val boxed = this.toTypedArray()
        boxed.sortBy(selector)
        for (i in indices) {
            this[i] = boxed[i]
        }
    }
}
