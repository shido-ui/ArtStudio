package com.artstudio.export.gif

import com.artstudio.animation.LoopMode
import com.artstudio.export.ExportCancellationToken
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Pure-Kotlin streaming multi-frame GIF89a encoder.
 *
 * Implements full GIF89a specification:
 * - GIF89a header and Logical Screen Descriptor
 * - Netscape 2.0 looping application extension
 * - Graphic Control Extension (GCE) per-frame with centisecond delay and 1-bit transparency
 * - Local Color Table (LCT) per frame with 256 colors
 * - LZW compression stream via [LzwEncoder]
 * - Streaming O(1) memory pipeline: frames are quantized and encoded on-the-fly
 */
class GifEncoder(
    private val width: Int,
    private val height: Int,
    private val outputStream: OutputStream,
    private val loopCount: Int = 0 // 0 = infinite loop
) {
    private var headerWritten = false
    private var frameCount = 0

    init {
        require(width in 1..65535) { "Width must be in 1..65535, got $width" }
        require(height in 1..65535) { "Height must be in 1..65535, got $height" }
    }

    /**
     * Initializes the GIF stream by writing header, logical screen descriptor, and Netscape loop block.
     * Uses the first frame's palette as the Global Color Table (GCT).
     */
    private fun writeHeader(firstFramePalette: ByteArray) {
        // 1. Header: 'GIF89a'
        outputStream.write("GIF89a".toByteArray(Charsets.US_ASCII))

        // 2. Logical Screen Descriptor (7 bytes)
        writeShort(width)
        writeShort(height)
        // Packed: GCT flag (1), Color res (7 = 8 bits), Sort (0), GCT size (7 = 256 colors) -> 0xF7
        outputStream.write(0xF7)
        outputStream.write(0x00) // Background color index
        outputStream.write(0x00) // Pixel aspect ratio

        // 3. Global Color Table (768 bytes)
        outputStream.write(firstFramePalette, 0, 768)

        // 4. Netscape 2.0 Looping Application Extension
        if (loopCount >= 0) {
            outputStream.write(0x21) // Extension Introducer
            outputStream.write(0xFF) // Application Extension Label
            outputStream.write(0x0B) // Block Size (11 bytes)
            outputStream.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            outputStream.write(0x03) // Sub-block Size (3 bytes)
            outputStream.write(0x01) // Sub-block ID
            writeShort(loopCount)
            outputStream.write(0x00) // Block Terminator
        }

        headerWritten = true
    }

    /**
     * Quantizes and encodes a single frame into the GIF stream.
     *
     * @param pixels 32-bit ARGB pixel array of size width * height
     * @param delayCentiseconds Frame display duration in centiseconds (1/100th of a second)
     * @param cancellationToken Optional cancellation token to abort encoding early
     */
    fun encodeFrame(
        pixels: IntArray,
        delayCentiseconds: Int,
        cancellationToken: ExportCancellationToken? = null
    ) {
        if (cancellationToken?.isCancelled == true) return

        val safeDelay = delayCentiseconds.coerceIn(1, 65535)
        val quantized = GifColorQuantizer.quantize(pixels, width, height)

        if (!headerWritten) {
            writeHeader(quantized.palette)
        }

        if (cancellationToken?.isCancelled == true) return

        // 1. Graphic Control Extension (GCE)
        outputStream.write(0x21) // Extension Introducer
        outputStream.write(0xF9) // GCE Label
        outputStream.write(0x04) // Block Size (4 bytes)

        val hasTransparent = quantized.transparentIndex != null
        // Disposal Method: 2 = Restore to background (essential for transparency/moving objects), 1 = Do not dispose
        val disposal = if (hasTransparent) 2 else 1
        val transpFlag = if (hasTransparent) 1 else 0
        val packedFields = (disposal shl 2) or transpFlag
        outputStream.write(packedFields)

        writeShort(safeDelay)
        outputStream.write((quantized.transparentIndex ?: 0) and 0xFF)
        outputStream.write(0x00) // Block Terminator

        // 2. Image Descriptor (10 bytes)
        outputStream.write(0x2C) // Image Separator
        writeShort(0)            // Left position
        writeShort(0)            // Top position
        writeShort(width)        // Frame width
        writeShort(height)       // Frame height
        // Packed: Local Color Table flag (1), Interlace (0), Sort (0), LCT size (7 = 256 colors) -> 0x87
        outputStream.write(0x87)

        // 3. Local Color Table (768 bytes)
        outputStream.write(quantized.palette, 0, 768)

        // 4. LZW Compressed Image Data
        LzwEncoder.encode(
            indices = quantized.indexedPixels,
            minCodeSize = 8,
            outputStream = outputStream
        )

        frameCount++
    }

    /**
     * Writes GIF trailer byte (0x3B) and flushes the stream.
     */
    fun finish() {
        if (!headerWritten) {
            // Write empty dummy frame if finish called before any frames
            writeHeader(ByteArray(768))
        }
        outputStream.write(0x3B) // GIF Trailer
        outputStream.flush()
    }

    private fun writeShort(value: Int) {
        outputStream.write(value and 0xFF)
        outputStream.write((value ushr 8) and 0xFF)
    }

    companion object {
        /**
         * Computes GIF delay in centiseconds (1/100th second) from duration ticks and timeline FPS.
         * delayCentiseconds = round((durationTicks / fps) * 100)
         */
        fun calculateDelayCentiseconds(durationTicks: Int, fps: Int): Int {
            require(fps > 0) { "FPS must be > 0, got $fps" }
            require(durationTicks > 0) { "durationTicks must be > 0, got $durationTicks" }
            val delaySeconds = durationTicks.toDouble() / fps.toDouble()
            return (delaySeconds * 100.0).roundToInt().coerceIn(1, 65535)
        }
    }
}
