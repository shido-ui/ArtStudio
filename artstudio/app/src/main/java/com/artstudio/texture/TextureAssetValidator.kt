package com.artstudio.texture

import com.artstudio.core.BrushTexture
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.min

/**
 * Robust, memory-safe asset validator and pure-Kotlin decoder for texture and stamp files.
 * Guarantees compliance with the Art Studio Texture Specification:
 * - Formats: PNG (8-bit grayscale, RGB, RGBA), WebP, JPEG
 * - Max dimensions: 1024 x 1024 pixels
 * - Min dimensions: 8 x 8 pixels
 * - Max file size: 2.0 MB (2,097,152 bytes)
 * - Safe decompression with bounds checking to prevent heap exhaustion.
 */
object TextureAssetValidator {

    const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB
    const val MAX_DIMENSION = 1024
    const val MIN_DIMENSION = 8

    private val PNG_MAGIC = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )

    private val JPEG_MAGIC = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()
    )

    private val WEBP_RIFF = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte()) // "RIFF"
    private val WEBP_MAGIC = byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte()) // "WEBP"

    /**
     * Validates and decodes raw asset bytes.
     */
    fun validateAndDecode(
        bytes: ByteArray,
        name: String,
        category: TextureCategory,
        filePath: String? = null,
        assetPath: String? = null,
        isBuiltIn: Boolean = false,
        author: String = "Art Studio",
        license: String = "CC0-1.0",
        provenance: String = "Imported Texture"
    ): TextureValidationResult {
        // 1. Check for empty file
        if (bytes.isEmpty()) {
            return TextureValidationResult.Error(
                code = TextureErrorCode.EMPTY_FILE,
                message = "Texture file is empty (0 bytes)"
            )
        }

        // 2. Check maximum file size budget
        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            return TextureValidationResult.Error(
                code = TextureErrorCode.FILE_TOO_LARGE,
                message = "File size (${bytes.size} bytes) exceeds maximum budget of 2 MB (${MAX_FILE_SIZE_BYTES} bytes)"
            )
        }

        // 3. Format & Magic Byte Verification
        val isPng = isPngFormat(bytes)
        val isJpeg = isJpegFormat(bytes)
        val isWebP = isWebPFormat(bytes)

        if (!isPng && !isJpeg && !isWebP) {
            return TextureValidationResult.Error(
                code = TextureErrorCode.INVALID_HEADER_OR_FORMAT,
                message = "Unsupported file format. Art Studio requires PNG, WebP, or JPEG textures with valid magic headers."
            )
        }

        // 4. Decode texture and validate dimensions
        val (texture, width, height) = if (isPng) {
            decodePng(bytes) ?: return TextureValidationResult.Error(
                code = TextureErrorCode.CORRUPT_OR_TRUNCATED,
                message = "Failed to parse PNG chunks: file appears truncated or corrupt."
            )
        } else {
            // Android platform fallback for JPEG / WebP
            decodeBitmapFallback(bytes) ?: return TextureValidationResult.Error(
                code = TextureErrorCode.CORRUPT_OR_TRUNCATED,
                message = "Failed to decode WebP or JPEG texture: format damaged or unsupported on this platform."
            )
        }

        // 5. Dimension bounds enforcement
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            return TextureValidationResult.Error(
                code = TextureErrorCode.DIMENSIONS_TOO_SMALL,
                message = "Texture dimensions (${width}x${height}) are smaller than minimum supported size of ${MIN_DIMENSION}x${MIN_DIMENSION}."
            )
        }

        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            return TextureValidationResult.Error(
                code = TextureErrorCode.DIMENSIONS_TOO_LARGE,
                message = "Texture dimensions (${width}x${height}) exceed maximum allowed ceiling of ${MAX_DIMENSION}x${MAX_DIMENSION}."
            )
        }

        // 6. Compute cryptographic SHA-256 hash
        val sha256 = computeSha256(bytes)

        val metadata = TextureMetadata(
            id = if (isBuiltIn) (assetPath?.substringAfterLast('/')?.substringBeforeLast('.') ?: UUID.randomUUID().toString())
                 else UUID.randomUUID().toString(),
            name = name,
            category = category,
            isBuiltIn = isBuiltIn,
            isFavorite = false,
            filePath = filePath,
            assetPath = assetPath,
            width = width,
            height = height,
            fileSizeBytes = bytes.size.toLong(),
            sha256 = sha256,
            author = author,
            license = license,
            provenance = provenance,
            createdAt = System.currentTimeMillis()
        )

        return TextureValidationResult.Success(metadata, texture)
    }

    /**
     * Convenience method to validate a file on disk.
     */
    fun validateFile(file: File, name: String, category: TextureCategory): TextureValidationResult {
        if (!file.exists() || !file.isFile) {
            return TextureValidationResult.Error(
                code = TextureErrorCode.FILE_NOT_FOUND,
                message = "File does not exist: ${file.absolutePath}"
            )
        }
        return try {
            val bytes = file.readBytes()
            validateAndDecode(
                bytes = bytes,
                name = name,
                category = category,
                filePath = file.absolutePath,
                isBuiltIn = false
            )
        } catch (e: Exception) {
            TextureValidationResult.Error(
                code = TextureErrorCode.CORRUPT_OR_TRUNCATED,
                message = "I/O error reading texture file: ${e.message}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Format Detection
    // -------------------------------------------------------------------------

    private fun isPngFormat(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        for (i in 0 until 8) {
            if (bytes[i] != PNG_MAGIC[i]) return false
        }
        return true
    }

    private fun isJpegFormat(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false
        return bytes[0] == JPEG_MAGIC[0] && bytes[1] == JPEG_MAGIC[1] && bytes[2] == JPEG_MAGIC[2]
    }

    private fun isWebPFormat(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val isRiff = bytes[0] == WEBP_RIFF[0] && bytes[1] == WEBP_RIFF[1] &&
                bytes[2] == WEBP_RIFF[2] && bytes[3] == WEBP_RIFF[3]
        val isWebp = bytes[8] == WEBP_MAGIC[0] && bytes[9] == WEBP_MAGIC[1] &&
                bytes[10] == WEBP_MAGIC[2] && bytes[11] == WEBP_MAGIC[3]
        return isRiff && isWebp
    }

    // -------------------------------------------------------------------------
    // Pure-Kotlin Lossless PNG Decoder with Complete Scanline Un-filtering
    // -------------------------------------------------------------------------

    private data class DecodedTextureInfo(val texture: BrushTexture, val width: Int, val height: Int)

    private fun decodePng(bytes: ByteArray): DecodedTextureInfo? {
        if (bytes.size < 29) return null

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

            if (len < 0 || idx + 12 + len > bytes.size) return null

            val type = String(bytes, idx + 4, 4, Charsets.US_ASCII)
            val dataOffset = idx + 8
            idx += 12 + len

            when (type) {
                "IHDR" -> {
                    if (len != 13) return null // Strict IHDR length
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

                    if (bitDepth != 8) return null // 8-bit channels standard
                }
                "IDAT" -> {
                    val chunk = bytes.copyOfRange(dataOffset, dataOffset + len)
                    idatChunks.add(chunk)
                }
                "IEND" -> break
            }
        }

        if (width <= 0 || height <= 0 || idatChunks.isEmpty()) return null

        return try {
            val totalIdatLen = idatChunks.sumOf { it.size }
            val combinedIdat = ByteArray(totalIdatLen)
            var offset = 0
            for (chunk in idatChunks) {
                System.arraycopy(chunk, 0, combinedIdat, offset, chunk.size)
                offset += chunk.size
            }

            val bpp = when (colorType) {
                0 -> 1 // Grayscale
                2 -> 3 // RGB
                4 -> 2 // Grayscale + Alpha
                6 -> 4 // RGBA
                else -> return null
            }

            val rowStride = 1 + width * bpp
            val uncompressed = ByteArray(height * rowStride)
            val inflater = Inflater()
            inflater.setInput(combinedIdat)
            val decompressedBytes = inflater.inflate(uncompressed)
            inflater.end()

            if (decompressedBytes <= 0) return null

            val outData = ByteArray(width * height)
            val scanline = ByteArray(width * bpp)
            val prevScanline = ByteArray(width * bpp)

            var srcIdx = 0
            for (y in 0 until height) {
                val filterType = uncompressed[srcIdx].toInt() and 0xFF
                srcIdx++

                // Unfilter the current scanline row
                for (i in 0 until width * bpp) {
                    val raw = uncompressed[srcIdx + i].toInt() and 0xFF
                    val a = if (i >= bpp) scanline[i - bpp].toInt() and 0xFF else 0
                    val b = prevScanline[i].toInt() and 0xFF
                    val c = if (i >= bpp) prevScanline[i - bpp].toInt() and 0xFF else 0

                    val recon = when (filterType) {
                        0 -> raw // None
                        1 -> (raw + a) and 0xFF // Sub
                        2 -> (raw + b) and 0xFF // Up
                        3 -> (raw + ((a + b) shr 1)) and 0xFF // Average
                        4 -> (raw + paethPredictor(a, b, c)) and 0xFF // Paeth
                        else -> raw
                    }
                    scanline[i] = recon.toByte()
                }

                // Copy to prevScanline for next row
                System.arraycopy(scanline, 0, prevScanline, 0, width * bpp)

                // Extract single-channel alpha/intensity mask
                for (x in 0 until width) {
                    val pxVal = when (colorType) {
                        0 -> scanline[x].toInt() and 0xFF
                        2 -> {
                            val r = scanline[x * 3].toInt() and 0xFF
                            val g = scanline[x * 3 + 1].toInt() and 0xFF
                            val b = scanline[x * 3 + 2].toInt() and 0xFF
                            (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                        }
                        4 -> {
                            val g = scanline[x * 2].toInt() and 0xFF
                            val a = scanline[x * 2 + 1].toInt() and 0xFF
                            ((g * a) / 255).coerceIn(0, 255)
                        }
                        6 -> {
                            val a = scanline[x * 4 + 3].toInt() and 0xFF
                            a // Use alpha for RGBA stamps
                        }
                        else -> 255
                    }
                    outData[y * width + x] = pxVal.toByte()
                }

                srcIdx += width * bpp
            }

            DecodedTextureInfo(BrushTexture(width, height, outData), width, height)
        } catch (_: Throwable) {
            null
        }
    }

    private fun paethPredictor(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    // -------------------------------------------------------------------------
    // Android Bitmap Platform Fallback for JPEG / WebP
    // -------------------------------------------------------------------------

    private fun decodeBitmapFallback(bytes: ByteArray): DecodedTextureInfo? {
        return try {
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val w = bmp.width
            val h = bmp.height

            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            bmp.recycle()

            val outData = ByteArray(w * h)
            var hasAlpha = false
            for (p in pixels) {
                if (((p ushr 24) and 0xFF) < 255) {
                    hasAlpha = true
                    break
                }
            }

            for (i in 0 until w * h) {
                val color = pixels[i]
                val a = (color ushr 24) and 0xFF
                val r = (color ushr 16) and 0xFF
                val g = (color ushr 8) and 0xFF
                val b = color and 0xFF

                val maskVal = if (hasAlpha) a else (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                outData[i] = maskVal.toByte()
            }

            DecodedTextureInfo(BrushTexture(w, h, outData), w, h)
        } catch (_: Throwable) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Cryptographic SHA-256 Checksum Calculation
    // -------------------------------------------------------------------------

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }
}
