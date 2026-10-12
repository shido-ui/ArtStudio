package com.artstudio.document.io.psd

import java.io.ByteArrayOutputStream

/**
 * Standard Apple/Adobe PackBits Run-Length Encoding (RLE) encoder and decoder.
 *
 * Used for compressing and decompressing channel scanlines in Photoshop (PSD) files.
 */
object PackBits {

    /**
     * Decodes a PackBits compressed byte array into uncompressed bytes.
     *
     * @param input Compressed byte array
     * @param inOffset Offset to start reading from
     * @param inLength Number of compressed bytes to read
     * @param expectedOutputSize Expected uncompressed byte count
     * @return Decoded uncompressed byte array
     */
    fun decode(input: ByteArray, inOffset: Int, inLength: Int, expectedOutputSize: Int): ByteArray {
        val output = ByteArray(expectedOutputSize)
        var inPos = inOffset
        val inEnd = inOffset + inLength
        var outPos = 0

        while (inPos < inEnd && outPos < expectedOutputSize) {
            val b = input[inPos++].toInt()
            when {
                b >= 0 -> { // Literal run of (b + 1) bytes (0..127 -> 1..128 bytes)
                    val count = b + 1
                    val toCopy = count.coerceAtMost(expectedOutputSize - outPos).coerceAtMost(inEnd - inPos)
                    System.arraycopy(input, inPos, output, outPos, toCopy)
                    inPos += toCopy
                    outPos += toCopy
                }
                b in -127..-1 -> { // Repeat run of (1 - b) bytes (-1..-127 -> 2..128 bytes)
                    val count = 1 - b
                    if (inPos < inEnd) {
                        val byteToRepeat = input[inPos++]
                        val toFill = count.coerceAtMost(expectedOutputSize - outPos)
                        for (i in 0 until toFill) {
                            output[outPos++] = byteToRepeat
                        }
                    }
                }
                b == -128 -> {
                    // No-op / skip pad byte
                }
            }
        }

        return output
    }

    /**
     * Encodes uncompressed bytes using PackBits RLE compression.
     */
    fun encode(input: ByteArray, offset: Int = 0, length: Int = input.size): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = offset
        val end = offset + length

        while (pos < end) {
            var runLength = 1
            while (pos + runLength < end && runLength < 128 && input[pos + runLength] == input[pos]) {
                runLength++
            }

            if (runLength >= 3) {
                // Repeat run: emit (1 - runLength) as signed byte, then the byte value
                val header = (1 - runLength).toByte()
                out.write(header.toInt())
                out.write(input[pos].toInt())
                pos += runLength
            } else {
                // Literal run: find how many non-repeating bytes we have
                var litLength = 1
                while (pos + litLength < end && litLength < 128) {
                    if (pos + litLength + 2 < end &&
                        input[pos + litLength] == input[pos + litLength + 1] &&
                        input[pos + litLength] == input[pos + litLength + 2]
                    ) {
                        break // Found a repeat run of at least 3, terminate literal run
                    }
                    litLength++
                }

                // Literal run: emit (litLength - 1) as byte, then the literal bytes
                val header = (litLength - 1).toByte()
                out.write(header.toInt())
                out.write(input, pos, litLength)
                pos += litLength
            }
        }

        return out.toByteArray()
    }
}
