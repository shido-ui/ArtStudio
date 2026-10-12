package com.artstudio.texture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Result of mipmap chain generation for a high-resolution texture.
 */
data class MipmapChain(
    val baseFile: File,
    val mip1kFile: File?,
    val mip512File: File?
)

/**
 * Image downsampler and mipmap chain generator for downloaded textures.
 * Restricts textures larger than 2K down to 2048px maximum dimension,
 * generating 1024px and 512px downsampled copies for bounded memory usage on 4 GB devices.
 */
object ImageDownsampler {

    const val MAX_TEXTURE_DIMENSION = 2048
    const val MIPMAP_1K_DIMENSION = 1024
    const val MIPMAP_512_DIMENSION = 512

    /**
     * Checks if image file exceeds 2K dimensions; if so, resamples down to 2K and generates mipmaps.
     */
    fun processTextureMipmaps(sourceFile: File, outputDir: File): MipmapChain {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return MipmapChain(sourceFile, null, null)
        }

        val baseName = sourceFile.nameWithoutExtension

        // On host JVM where android.graphics.BitmapFactory may return dummy values,
        // we check if Bitmap is decodeable; if not, return base file safely.
        val originalBitmap = try {
            BitmapFactory.decodeFile(sourceFile.absolutePath)
        } catch (_: Throwable) {
            null
        }

        if (originalBitmap == null || originalBitmap.width <= 0 || originalBitmap.height <= 0) {
            return MipmapChain(sourceFile, null, null)
        }

        val origW = originalBitmap.width
        val origH = originalBitmap.height
        val maxDim = max(origW, origH)

        // 1. Base 2K image
        val base2kFile = if (maxDim > MAX_TEXTURE_DIMENSION) {
            val scale = MAX_TEXTURE_DIMENSION.toFloat() / maxDim.toFloat()
            val targetW = (origW * scale).toInt()
            val targetH = (origH * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            val outFile = File(outputDir, "${baseName}_2k.png")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            outFile
        } else {
            sourceFile
        }

        // 2. 1K Mipmap (if original was >= 1024)
        val mip1kFile = if (maxDim >= MIPMAP_1K_DIMENSION) {
            val scale = MIPMAP_1K_DIMENSION.toFloat() / maxDim.toFloat()
            val targetW = (origW * scale).toInt()
            val targetH = (origH * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            val outFile = File(outputDir, "${baseName}_1k.png")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.PNG, 90, fos)
            }
            outFile
        } else null

        // 3. 512px Mipmap
        val mip512File = if (maxDim >= MIPMAP_512_DIMENSION) {
            val scale = MIPMAP_512_DIMENSION.toFloat() / maxDim.toFloat()
            val targetW = (origW * scale).toInt()
            val targetH = (origH * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            val outFile = File(outputDir, "${baseName}_512.png")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.PNG, 85, fos)
            }
            outFile
        } else null

        return MipmapChain(base2kFile, mip1kFile, mip512File)
    }

    /**
     * Pure software bilinear downscaling for raw pixel buffers (width x height 32-bit ARGB ints).
     */
    fun downscalePixelsBilinear(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): IntArray {
        if (srcW == dstW && srcH == dstH) return src.clone()
        val dst = IntArray(dstW * dstH)
        val xRatio = (srcW - 1).toFloat() / max(1f, (dstW - 1).toFloat())
        val yRatio = (srcH - 1).toFloat() / max(1f, (dstH - 1).toFloat())

        for (y in 0 until dstH) {
            val yf = y * yRatio
            val y0 = yf.toInt().coerceIn(0, srcH - 1)
            val y1 = min(y0 + 1, srcH - 1)
            val yDiff = yf - y0
            val y0Offset = y0 * srcW
            val y1Offset = y1 * srcW
            val dstOffset = y * dstW

            for (x in 0 until dstW) {
                val xf = x * xRatio
                val x0 = xf.toInt().coerceIn(0, srcW - 1)
                val x1 = min(x0 + 1, srcW - 1)
                val xDiff = xf - x0

                val c00 = src[y0Offset + x0]
                val c10 = src[y0Offset + x1]
                val c01 = src[y1Offset + x0]
                val c11 = src[y1Offset + x1]

                val a = interpolateChannel(c00 ushr 24, c10 ushr 24, c01 ushr 24, c11 ushr 24, xDiff, yDiff)
                val r = interpolateChannel((c00 ushr 16) and 0xFF, (c10 ushr 16) and 0xFF, (c01 ushr 16) and 0xFF, (c11 ushr 16) and 0xFF, xDiff, yDiff)
                val g = interpolateChannel((c00 ushr 8) and 0xFF, (c10 ushr 8) and 0xFF, (c01 ushr 8) and 0xFF, (c11 ushr 8) and 0xFF, xDiff, yDiff)
                val b = interpolateChannel(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, xDiff, yDiff)

                dst[dstOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return dst
    }

    private inline fun interpolateChannel(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
        val top = c00 * (1f - tx) + c10 * tx
        val bottom = c01 * (1f - tx) + c11 * tx
        return (top * (1f - ty) + bottom * ty).toInt().coerceIn(0, 255)
    }
}
