package com.artstudio.export

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Pure-Kotlin deterministic lossless PNG encoder.
 * Guarantees zero external library dependencies, 100% Android & JVM portable execution,
 * and exact bit-level round-trip reproduction when decoded.
 */
object PureKotlinPngEncoder {

    private val PNG_MAGIC = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )

    /**
     * Encodes 32-bit ARGB_8888 pixels into a standard lossless PNG stream.
     *
     * @param pixels 32-bit ARGB pixels (packed as: (A shl 24) or (R shl 16) or (G shl 8) or B)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param outputStream Target stream
     */
    fun encodeRgba(
        pixels: IntArray,
        width: Int,
        height: Int,
        outputStream: OutputStream
    ) {
        require(width > 0) { "width must be > 0, got $width" }
        require(height > 0) { "height must be > 0, got $height" }
        require(pixels.size == width * height) { "pixel array size (${pixels.size}) != width * height (${width * height})" }

        // 1. Write PNG Signature
        outputStream.write(PNG_MAGIC)

        // 2. Write IHDR (Image Header)
        val ihdrBytes = ByteArray(13)
        ihdrBytes[0] = (width ushr 24).toByte()
        ihdrBytes[1] = (width ushr 16).toByte()
        ihdrBytes[2] = (width ushr 8).toByte()
        ihdrBytes[3] = width.toByte()

        ihdrBytes[4] = (height ushr 24).toByte()
        ihdrBytes[5] = (height ushr 16).toByte()
        ihdrBytes[6] = (height ushr 8).toByte()
        ihdrBytes[7] = height.toByte()

        ihdrBytes[8] = 8.toByte() // Bit depth: 8
        ihdrBytes[9] = 6.toByte() // Color type: 6 (RGBA)
        ihdrBytes[10] = 0.toByte() // Compression: 0 (deflate)
        ihdrBytes[11] = 0.toByte() // Filter: 0 (standard adaptive)
        ihdrBytes[12] = 0.toByte() // Interlace: 0 (none)

        writeChunk(outputStream, "IHDR", ihdrBytes)

        // 3. Deflate compress scanlines row-by-row into IDAT (memory-bounded streaming)
        // Format per row: [1 byte filter = 0] [R, G, B, A, R, G, B, A, ...]
        val rowSize = 1 + width * 4
        val rowBuffer = ByteArray(rowSize)
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        val idatBaos = ByteArrayOutputStream()
        val chunkBuf = ByteArray(16384)

        for (y in 0 until height) {
            rowBuffer[0] = 0 // Filter: None
            val srcOffset = y * width
            for (x in 0 until width) {
                val argb = pixels[srcOffset + x]
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF

                val pxOffset = 1 + x * 4
                rowBuffer[pxOffset] = r.toByte()
                rowBuffer[pxOffset + 1] = g.toByte()
                rowBuffer[pxOffset + 2] = b.toByte()
                rowBuffer[pxOffset + 3] = a.toByte()
            }

            deflater.setInput(rowBuffer)
            while (!deflater.needsInput()) {
                val count = deflater.deflate(chunkBuf)
                idatBaos.write(chunkBuf, 0, count)
            }
        }

        deflater.finish()
        while (!deflater.finished()) {
            val count = deflater.deflate(chunkBuf)
            idatBaos.write(chunkBuf, 0, count)
        }
        deflater.end()

        writeChunk(outputStream, "IDAT", idatBaos.toByteArray())

        // 5. Write IEND (Image End)
        writeChunk(outputStream, "IEND", ByteArray(0))
        outputStream.flush()
    }

    private fun writeChunk(out: OutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val len = data.size

        val dos = DataOutputStream(out)
        dos.writeInt(len)
        dos.write(typeBytes)
        if (data.isNotEmpty()) {
            dos.write(data)
        }

        val crc = CRC32()
        crc.update(typeBytes)
        if (data.isNotEmpty()) {
            crc.update(data)
        }
        dos.writeInt(crc.value.toInt())
    }
}
