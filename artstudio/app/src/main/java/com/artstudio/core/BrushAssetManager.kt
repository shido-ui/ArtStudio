package com.artstudio.core

import java.io.File
import java.io.InputStream
import kotlin.math.*

/**
 * 8-bit single-channel texture mask used for brush stamps, paper tooth, and media grain.
 */
class BrushTexture(
    val width: Int,
    val height: Int,
    val data: ByteArray
) {
    init {
        require(width > 0 && height > 0) { "Texture dimensions must be positive ($width x $height)" }
        require(data.size == width * height) { "Data size (${data.size}) must match width * height ($width * $height)" }
    }

    /**
     * Bilinear sample normalized coordinate (u, v) in [0, 1].
     * Coordinates are wrapped (tiled) across [0, 1].
     */
    fun sample(u: Float, v: Float): Float {
        val wrappedU = ((u % 1.0f) + 1.0f) % 1.0f
        val wrappedV = ((v % 1.0f) + 1.0f) % 1.0f

        val fx = wrappedU * (width - 1)
        val fy = wrappedV * (height - 1)

        val x0 = fx.toInt().coerceIn(0, width - 1)
        val y0 = fy.toInt().coerceIn(0, height - 1)
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)

        val tx = fx - x0
        val ty = fy - y0

        val v00 = (data[y0 * width + x0].toInt() and 0xFF) / 255.0f
        val v10 = (data[y0 * width + x1].toInt() and 0xFF) / 255.0f
        val v01 = (data[y1 * width + x0].toInt() and 0xFF) / 255.0f
        val v11 = (data[y1 * width + x1].toInt() and 0xFF) / 255.0f

        val top = v00 * (1f - tx) + v10 * tx
        val bottom = v01 * (1f - tx) + v11 * tx
        return top * (1f - ty) + bottom * ty
    }

    /**
     * Point sample coordinate (x, y) in integer pixels.
     */
    fun samplePixel(x: Int, y: Int): Float {
        val px = x.coerceIn(0, width - 1)
        val py = y.coerceIn(0, height - 1)
        return (data[py * width + px].toInt() and 0xFF) / 255.0f
    }
}

/**
 * Compact asset manager providing deterministic procedural media textures,
 * compact bundled assets, and an extensible loader for user-supplied external textures.
 */
object BrushAssetManager {

    private val memoryCache = LinkedHashMap<String, BrushTexture>()
    private const val MAX_CACHE_ENTRIES = 24
    const val MAX_USER_TEXTURE_DIMENSION = 512

    /**
     * Retrieves or generates a texture mask for the specified stamp shape.
     */
    fun getTexture(
        shape: StampPatternShape,
        customAssetPath: String? = null,
        seed: Long = 42L,
        dimension: Int = 128
    ): BrushTexture {
        if (shape == StampPatternShape.CUSTOM_ASSET && !customAssetPath.isNullOrBlank()) {
            val userTex = loadUserTexture(customAssetPath)
            if (userTex != null) return userTex
        }

        val cacheKey = "proc_${shape.id}_${seed}_$dimension"
        synchronized(memoryCache) {
            memoryCache[cacheKey]?.let { return it }
        }

        val texture = when (shape) {
            StampPatternShape.SPONGE -> generateSponge(seed, dimension, dimension)
            StampPatternShape.SPECKLE -> generateSpeckle(seed, dimension, dimension)
            StampPatternShape.CANVAS_GRAIN -> generateCanvasGrain(seed, dimension, dimension)
            StampPatternShape.LEAF_ORGANIC -> generateLeafOrganic(seed, dimension, dimension)
            StampPatternShape.CUSTOM_ASSET -> generateSponge(seed, dimension, dimension)
        }

        putCache(cacheKey, texture)
        return texture
    }

    /**
     * Retrieves paper tooth grain for dry brush or watercolor granulation.
     */
    fun getPaperTooth(seed: Long = 1337L, dimension: Int = 128): BrushTexture {
        val cacheKey = "paper_tooth_${seed}_$dimension"
        synchronized(memoryCache) {
            memoryCache[cacheKey]?.let { return it }
        }
        val texture = generatePaperTooth(seed, dimension, dimension)
        putCache(cacheKey, texture)
        return texture
    }

    /**
     * Extensible loader for user-supplied bitmap assets (PNG/WebP/JPEG).
     * Bounded to [MAX_USER_TEXTURE_DIMENSION] to guarantee physical memory safety.
     */
    fun loadUserTexture(filePath: String): BrushTexture? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() == 0L) return null

        val cacheKey = "file_${file.absolutePath}_${file.lastModified()}"
        synchronized(memoryCache) {
            memoryCache[cacheKey]?.let { return it }
        }

        return try {
            val bytes = file.readBytes()
            val texture = parsePngGrayscale(bytes) ?: parseAndroidBitmapFallback(file)
            if (texture != null) {
                putCache(cacheKey, texture)
            }
            texture
        } catch (_: Throwable) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Deterministic Procedural Generators
    // -------------------------------------------------------------------------

    /**
     * Coarse cold-press paper tooth: multi-octave deterministic value noise.
     */
    fun generatePaperTooth(seed: Long, width: Int, height: Int): BrushTexture {
        val data = ByteArray(width * height)
        val octaves = 3

        for (y in 0 until height) {
            val yOff = y * width
            for (x in 0 until width) {
                var n = 0f
                var amp = 0.60f
                var freq = 4.0f

                for (o in 0 until octaves) {
                    val nx = (x.toFloat() / width.toFloat()) * freq
                    val ny = (y.toFloat() / height.toFloat()) * freq
                    n += pseudoNoise(seed + o * 7919L, nx, ny) * amp
                    amp *= 0.50f
                    freq *= 2.0f
                }

                val intensity = (n * 255.0f).toInt().coerceIn(0, 255)
                data[yOff + x] = intensity.toByte()
            }
        }
        return BrushTexture(width, height, data)
    }

    /**
     * Organic sponge: cellular / Voronoi-like porous structure.
     */
    fun generateSponge(seed: Long, width: Int, height: Int): BrushTexture {
        val data = ByteArray(width * height)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(cx, cy)

        for (y in 0 until height) {
            val dy = y - cy
            val yOff = y * width
            for (x in 0 until width) {
                val dx = x - cx
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val radialFade = (1.0f - (dist / maxR)).coerceIn(0.0f, 1.0f)

                if (radialFade <= 0f) {
                    data[yOff + x] = 0
                    continue
                }

                val n1 = pseudoNoise(seed, x * 0.12f, y * 0.12f)
                val n2 = pseudoNoise(seed + 1013L, x * 0.28f, y * 0.28f)
                val pore = (n1 * 0.7f + n2 * 0.3f)

                val valByte = if (pore > 0.42f) {
                    (radialFade * 255f * pore).toInt().coerceIn(0, 255)
                } else {
                    (radialFade * 60f * pore).toInt().coerceIn(0, 255)
                }
                data[yOff + x] = valByte.toByte()
            }
        }
        return BrushTexture(width, height, data)
    }

    /**
     * Stippled speckle pattern: clustered random spatter drops.
     */
    fun generateSpeckle(seed: Long, width: Int, height: Int): BrushTexture {
        val data = ByteArray(width * height)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(cx, cy)

        var rng = seed.toInt()
        fun nextRand(): Float {
            rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
            return (rng and 0xFFFF) / 65535.0f
        }

        val dotCount = (width * height * 0.08f).toInt()
        for (i in 0 until dotCount) {
            val rNorm = sqrt(nextRand()) * maxR
            val theta = nextRand() * (2f * PI.toFloat())
            val px = (cx + rNorm * cos(theta)).toInt()
            val py = (cy + rNorm * sin(theta)).toInt()

            if (px in 0 until width && py in 0 until height) {
                val dropR = 1 + (nextRand() * 2.5f).toInt()
                val dropAlpha = (180 + nextRand() * 75f).toInt().coerceIn(0, 255)

                for (ny in max(0, py - dropR)..min(height - 1, py + dropR)) {
                    val dy = ny - py
                    for (nx in max(0, px - dropR)..min(width - 1, px + dropR)) {
                        val dx = nx - px
                        if (dx * dx + dy * dy <= dropR * dropR) {
                            val cur = data[ny * width + nx].toInt() and 0xFF
                            data[ny * width + nx] = max(cur, dropAlpha).toByte()
                        }
                    }
                }
            }
        }
        return BrushTexture(width, height, data)
    }

    /**
     * Woven canvas grain: periodic crosshatch textile lines with subtle noise.
     */
    fun generateCanvasGrain(seed: Long, width: Int, height: Int): BrushTexture {
        val data = ByteArray(width * height)
        val threadFreq = 0.25f

        for (y in 0 until height) {
            val yOff = y * width
            val warp = 0.5f + 0.5f * sin(y * threadFreq * 2.0 * PI).toFloat()
            for (x in 0 until width) {
                val weft = 0.5f + 0.5f * cos(x * threadFreq * 2.0 * PI).toFloat()
                val noise = pseudoNoise(seed, x * 0.08f, y * 0.08f) * 0.25f
                val intensity = ((warp * 0.45f + weft * 0.45f + noise) * 255f).toInt().coerceIn(0, 255)
                data[yOff + x] = intensity.toByte()
            }
        }
        return BrushTexture(width, height, data)
    }

    /**
     * Organic botanical leaf silhouette.
     */
    fun generateLeafOrganic(seed: Long, width: Int, height: Int): BrushTexture {
        val data = ByteArray(width * height)
        val cx = width / 2f
        val cy = height / 2f

        for (y in 0 until height) {
            val dy = (y - cy) / cy
            val yOff = y * width
            for (x in 0 until width) {
                val dx = (x - cx) / cx

                // Elliptical droplet shape: sharper at top (y < 0)
                val rBound = if (dy < 0) {
                    (1.0f + dy * 0.7f).coerceAtLeast(0.05f) * 0.45f
                } else {
                    (1.0f - dy * 0.85f).coerceAtLeast(0.05f) * 0.55f
                }

                val inside = abs(dx) <= rBound && abs(dy) <= 0.85f
                if (inside) {
                    val edgeDist = 1.0f - (abs(dx) / rBound)
                    val noise = pseudoNoise(seed, x * 0.15f, y * 0.15f)
                    val intensity = (edgeDist.coerceIn(0f, 1f) * (0.8f + noise * 0.2f) * 255f).toInt().coerceIn(0, 255)
                    data[yOff + x] = intensity.toByte()
                } else {
                    data[yOff + x] = 0
                }
            }
        }
        return BrushTexture(width, height, data)
    }

    // -------------------------------------------------------------------------
    // Noise Mathematics
    // -------------------------------------------------------------------------

    private fun pseudoNoise(seed: Long, x: Float, y: Float): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi

        val sx = xf * xf * (3.0f - 2.0f * xf)
        val sy = yf * yf * (3.0f - 2.0f * yf)

        val v00 = hash(seed, xi, yi)
        val v10 = hash(seed, xi + 1, yi)
        val v01 = hash(seed, xi, yi + 1)
        val v11 = hash(seed, xi + 1, yi + 1)

        val nx0 = v00 * (1f - sx) + v10 * sx
        val nx1 = v01 * (1f - sx) + v11 * sx
        return nx0 * (1f - sy) + nx1 * sy
    }

    private fun hash(seed: Long, x: Int, y: Int): Float {
        var h = seed xor (x.toLong() * 374761393L) xor (y.toLong() * 668265263L)
        h = (h xor (h ushr 13)) * 1274126177L
        return ((h xor (h ushr 16)).toInt() and 0x7FFFFFFF) / 2147483647.0f
    }

    // -------------------------------------------------------------------------
    // Lightweight Pure-Kotlin PNG Parser for User Assets & Testing
    // -------------------------------------------------------------------------

    private fun parsePngGrayscale(bytes: ByteArray): BrushTexture? {
        if (bytes.size < 8) return null
        // Check PNG signature: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
        if (bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte() ||
            bytes[2] != 0x4E.toByte() || bytes[3] != 0x47.toByte()
        ) {
            return null
        }

        var idx = 8
        var width = 0
        var height = 0
        var bitDepth = 8
        var colorType = 0
        val idatChunks = mutableListOf<ByteArray>()

        while (idx + 8 <= bytes.size) {
            val len = ((bytes[idx].toInt() and 0xFF) shl 24) or
                    ((bytes[idx + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[idx + 2].toInt() and 0xFF) shl 8) or
                    (bytes[idx + 3].toInt() and 0xFF)

            val type = String(bytes, idx + 4, 4, Charsets.US_ASCII)
            val dataOffset = idx + 8
            idx += 12 + len

            when (type) {
                "IHDR" -> {
                    if (len >= 13) {
                        width = ((bytes[dataOffset].toInt() and 0xFF) shl 24) or
                                ((bytes[dataOffset + 1].toInt() and 0xFF) shl 16) or
                                ((bytes[dataOffset + 2].toInt() and 0xFF) shl 8) or
                                (bytes[dataOffset + 3].toInt() and 0xFF)

                        height = ((bytes[dataOffset + 4].toInt() and 0xFF) shl 24) or
                                ((bytes[dataOffset + 5].toInt() and 0xFF) shl 16) or
                                ((bytes[dataOffset + 6].toInt() and 0xFF) shl 8) or
                                (bytes[dataOffset + 7].toInt() and 0xFF)

                        bitDepth = bytes[dataOffset + 8].toInt() and 0xFF
                        colorType = bytes[dataOffset + 9].toInt() and 0xFF
                    }
                }
                "IDAT" -> {
                    val chunk = bytes.copyOfRange(dataOffset, min(bytes.size, dataOffset + len))
                    idatChunks.add(chunk)
                }
                "IEND" -> break
            }
        }

        if (width <= 0 || height <= 0 || width > MAX_USER_TEXTURE_DIMENSION || height > MAX_USER_TEXTURE_DIMENSION) {
            return null
        }

        return try {
            val totalIdatLen = idatChunks.sumOf { it.size }
            val combinedIdat = ByteArray(totalIdatLen)
            var offset = 0
            for (chunk in idatChunks) {
                System.arraycopy(chunk, 0, combinedIdat, offset, chunk.size)
                offset += chunk.size
            }

            val inflater = java.util.zip.Inflater()
            inflater.setInput(combinedIdat)

            val channels = when (colorType) {
                0 -> 1 // Grayscale
                2 -> 3 // RGB
                4 -> 2 // Grayscale + Alpha
                6 -> 4 // RGBA
                else -> 1
            }

            val rowStride = 1 + width * channels
            val uncompressed = ByteArray(height * rowStride)
            val decompressedBytes = inflater.inflate(uncompressed)
            inflater.end()

            if (decompressedBytes <= 0) return null

            val outData = ByteArray(width * height)
            var srcIdx = 0
            for (y in 0 until height) {
                val filter = uncompressed[srcIdx].toInt() and 0xFF
                srcIdx++

                for (x in 0 until width) {
                    val pxVal = when (colorType) {
                        0 -> uncompressed[srcIdx + x].toInt() and 0xFF
                        2 -> {
                            val r = uncompressed[srcIdx + x * 3].toInt() and 0xFF
                            val g = uncompressed[srcIdx + x * 3 + 1].toInt() and 0xFF
                            val b = uncompressed[srcIdx + x * 3 + 2].toInt() and 0xFF
                            (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                        }
                        4 -> uncompressed[srcIdx + x * 2 + 1].toInt() and 0xFF // Alpha channel
                        6 -> uncompressed[srcIdx + x * 4 + 3].toInt() and 0xFF // Alpha channel
                        else -> 255
                    }
                    outData[y * width + x] = pxVal.toByte()
                }
                srcIdx += width * channels
            }
            BrushTexture(width, height, outData)
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseAndroidBitmapFallback(file: File): BrushTexture? {
        return try {
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val w = bmp.width
            val h = bmp.height

            if (w <= 0 || h <= 0 || w > MAX_USER_TEXTURE_DIMENSION || h > MAX_USER_TEXTURE_DIMENSION) {
                bmp.recycle()
                return null
            }

            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            bmp.recycle()

            val outData = ByteArray(w * h)
            for (i in pixels.indices) {
                val c = pixels[i]
                val a = (c ushr 24) and 0xFF
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                // If has alpha variation use alpha, else grayscale luminance
                val v = if (a < 255) a else (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                outData[i] = v.toByte()
            }
            BrushTexture(w, h, outData)
        } catch (_: Throwable) {
            null
        }
    }

    private fun putCache(key: String, texture: BrushTexture) {
        synchronized(memoryCache) {
            if (memoryCache.size >= MAX_CACHE_ENTRIES) {
                val oldest = memoryCache.keys.firstOrNull()
                if (oldest != null) memoryCache.remove(oldest)
            }
            memoryCache[key] = texture
        }
    }

    /**
     * Measures total disk space of built-in assets in the app bundle.
     */
    fun getBuiltInAssetSizeBytes(assetsDir: File? = null): Long {
        val dir = assetsDir ?: return 0L
        if (!dir.exists() || !dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
