package com.artstudio.export.gif

import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Decoded GIF frame with metadata and rendered 32-bit ARGB pixel array.
 */
data class DecodedGifFrame(
    val index: Int,
    val width: Int,
    val height: Int,
    val delayCentiseconds: Int,
    val pixels: IntArray,
    val hasTransparency: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedGifFrame) return false
        return index == other.index &&
                width == other.width &&
                height == other.height &&
                delayCentiseconds == other.delayCentiseconds &&
                hasTransparency == other.hasTransparency &&
                pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + delayCentiseconds
        result = 31 * result + hasTransparency.hashCode()
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

/**
 * Complete decoded GIF representation for validation and playback testing.
 */
data class DecodedGif(
    val width: Int,
    val height: Int,
    val loopCount: Int?, // 0 = infinite, null = unspecified
    val frames: List<DecodedGifFrame>
)

/**
 * Pure-Kotlin GIF87a/GIF89a parser and decoder.
 *
 * Used for automated verification in unit tests, verifying:
 * - Frame count, order, and dimensions
 * - Exact centisecond frame delays
 * - Netscape 2.0 loop counts
 * - Pixel colors and transparency
 */
object GifDecoder {

    fun decode(bytes: ByteArray): DecodedGif {
        return decode(ByteArrayInputStream(bytes))
    }

    fun decode(input: InputStream): DecodedGif {
        // 1. Header (6 bytes: GIF87a or GIF89a)
        val headerBytes = ByteArray(6)
        readFully(input, headerBytes)
        val header = String(headerBytes, Charsets.US_ASCII)
        require(header == "GIF87a" || header == "GIF89a") { "Invalid GIF header: $header" }

        // 2. Logical Screen Descriptor (7 bytes)
        val lsd = ByteArray(7)
        readFully(input, lsd)
        val screenWidth = readShort(lsd, 0)
        val screenHeight = readShort(lsd, 2)
        val packed = lsd[4].toInt() and 0xFF
        val hasGct = (packed and 0x80) != 0
        val gctSize = 1 shl ((packed and 0x07) + 1)

        // 3. Global Color Table
        val globalColorTable = if (hasGct) {
            val gct = ByteArray(gctSize * 3)
            readFully(input, gct)
            gct
        } else {
            null
        }

        var loopCount: Int? = null
        val frames = mutableListOf<DecodedGifFrame>()

        // Graphic Control Extension state
        var currentDelay = 10 // default 10 cs (100 ms)
        var currentTransparentIdx: Int? = null
        var currentDisposalMethod = 0

        var b: Int
        while (input.read().also { b = it } != -1) {
            if (b == 0x3B) {
                // GIF Trailer: end of file
                break
            }

            if (b == 0x21) {
                // Extension block
                val extLabel = input.read()
                when (extLabel) {
                    0xF9 -> {
                        // Graphic Control Extension (GCE)
                        val blockSize = input.read()
                        val gceBytes = ByteArray(blockSize)
                        readFully(input, gceBytes)
                        val gcePacked = gceBytes[0].toInt() and 0xFF
                        currentDisposalMethod = (gcePacked ushr 2) and 0x07
                        val hasTransp = (gcePacked and 0x01) != 0
                        currentDelay = readShort(gceBytes, 1)
                        currentTransparentIdx = if (hasTransp) gceBytes[3].toInt() and 0xFF else null
                        skipSubBlocks(input)
                    }
                    0xFF -> {
                        // Application Extension (e.g. NETSCAPE2.0)
                        val blockSize = input.read()
                        val appBytes = ByteArray(blockSize)
                        readFully(input, appBytes)
                        val appName = String(appBytes, Charsets.US_ASCII)
                        if (appName == "NETSCAPE2.0") {
                            val subBlockSize = input.read()
                            if (subBlockSize == 3) {
                                val subId = input.read()
                                if (subId == 1) {
                                    val loopBytes = ByteArray(2)
                                    readFully(input, loopBytes)
                                    loopCount = readShort(loopBytes, 0)
                                } else {
                                    skipBytes(input, 2)
                                }
                            } else {
                                skipBytes(input, subBlockSize)
                            }
                        }
                        skipSubBlocks(input)
                    }
                    else -> {
                        // Comment or other extension: skip sub-blocks
                        skipSubBlocks(input)
                    }
                }
            } else if (b == 0x2C) {
                // Image Descriptor
                val idBytes = ByteArray(9)
                readFully(input, idBytes)
                val imgLeft = readShort(idBytes, 0)
                val imgTop = readShort(idBytes, 2)
                val imgWidth = readShort(idBytes, 4)
                val imgHeight = readShort(idBytes, 6)
                val idPacked = idBytes[8].toInt() and 0xFF
                val hasLct = (idPacked and 0x80) != 0
                val lctSize = 1 shl ((idPacked and 0x07) + 1)

                val activePalette = if (hasLct) {
                    val lct = ByteArray(lctSize * 3)
                    readFully(input, lct)
                    lct
                } else {
                    globalColorTable ?: ByteArray(768)
                }

                // LZW Image Data
                val minCodeSize = input.read()
                val lzwData = readSubBlocks(input)
                val decodedIndices = decodeLzw(lzwData, minCodeSize, imgWidth * imgHeight)

                // Render frame pixels into 32-bit ARGB
                val framePixels = IntArray(imgWidth * imgHeight)
                var hasAlpha = false
                val paletteColors = activePalette.size / 3

                for (i in decodedIndices.indices) {
                    val idx = decodedIndices[i].toInt() and 0xFF
                    if (currentTransparentIdx != null && idx == currentTransparentIdx) {
                        framePixels[i] = 0x00000000 // Fully transparent
                        hasAlpha = true
                    } else if (idx < paletteColors) {
                        val r = activePalette[idx * 3].toInt() and 0xFF
                        val g = activePalette[idx * 3 + 1].toInt() and 0xFF
                        val bCol = activePalette[idx * 3 + 2].toInt() and 0xFF
                        framePixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bCol
                    } else {
                        framePixels[i] = 0xFF000000.toInt()
                    }
                }

                frames.add(
                    DecodedGifFrame(
                        index = frames.size,
                        width = imgWidth,
                        height = imgHeight,
                        delayCentiseconds = currentDelay,
                        pixels = framePixels,
                        hasTransparency = hasAlpha
                    )
                )

                // Reset per-frame GCE overrides
                currentTransparentIdx = null
                currentDelay = 10
            }
        }

        return DecodedGif(
            width = screenWidth,
            height = screenHeight,
            loopCount = loopCount,
            frames = frames
        )
    }

    private fun readShort(data: ByteArray, offset: Int): Int {
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        return (b1 shl 8) or b0
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count == -1) break
            offset += count
        }
    }

    private fun skipBytes(input: InputStream, count: Int) {
        var remaining = count.toLong()
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun skipSubBlocks(input: InputStream) {
        while (true) {
            val len = input.read()
            if (len <= 0) break
            skipBytes(input, len)
        }
    }

    private fun readSubBlocks(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val len = input.read()
            if (len <= 0) break
            val buf = ByteArray(len)
            readFully(input, buf)
            out.write(buf)
        }
        return out.toByteArray()
    }

    /**
     * Pure-Kotlin LZW decompressor for GIF image data.
     */
    private fun decodeLzw(data: ByteArray, minCodeSize: Int, pixelCount: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        var nextCode = clearCode + 2
        var currentCodeSize = minCodeSize + 1
        val maxCode = 4096

        val prefix = IntArray(maxCode)
        val suffix = ByteArray(maxCode)
        val pixelStack = ByteArray(maxCode + 1)

        fun resetDict() {
            currentCodeSize = minCodeSize + 1
            nextCode = clearCode + 2
            for (i in 0 until clearCode) {
                prefix[i] = -1
                suffix[i] = i.toByte()
            }
        }

        resetDict()

        val output = ByteArray(pixelCount)
        var outPos = 0

        var bitBuffer = 0
        var bitCount = 0
        var dataPos = 0
        var oldCode = -1
        var stackTop = 0

        while (outPos < pixelCount && dataPos < data.size) {
            // Refill bit buffer
            while (bitCount < currentCodeSize && dataPos < data.size) {
                bitBuffer = bitBuffer or ((data[dataPos++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }

            if (bitCount < currentCodeSize) break

            val code = bitBuffer and ((1 shl currentCodeSize) - 1)
            bitBuffer = bitBuffer ushr currentCodeSize
            bitCount -= currentCodeSize

            if (code == clearCode) {
                resetDict()
                oldCode = -1
                continue
            }

            if (code == eoiCode) {
                break
            }

            var inCode = code
            if (oldCode == -1) {
                if (code < maxCode) {
                    output[outPos++] = suffix[code]
                    oldCode = code
                }
                continue
            }

            if (code >= nextCode) {
                pixelStack[stackTop++] = suffix[oldCode]
                inCode = oldCode
            }

            while (inCode >= clearCode && inCode < maxCode) {
                pixelStack[stackTop++] = suffix[inCode]
                inCode = prefix[inCode]
            }

            val firstChar = suffix[inCode]
            pixelStack[stackTop++] = firstChar

            while (stackTop > 0 && outPos < pixelCount) {
                output[outPos++] = pixelStack[--stackTop]
            }

            if (nextCode < maxCode) {
                prefix[nextCode] = oldCode
                suffix[nextCode] = firstChar
                nextCode++
                if (nextCode >= (1 shl currentCodeSize) && currentCodeSize < 12) {
                    currentCodeSize++
                }
            }

            oldCode = code
        }

        return output
    }
}
