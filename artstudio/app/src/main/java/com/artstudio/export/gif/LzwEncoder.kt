package com.artstudio.export.gif

import java.io.OutputStream
import kotlin.math.min

/**
 * Pure-Kotlin BitOutputStream that packs arbitrary bit-length codes into bytes
 * in LSB-first order and chunks them into GIF sub-blocks (up to 254 bytes each).
 */
class GifBitOutputStream(private val output: OutputStream) {
    private var currentByte = 0
    private var bitCount = 0
    private val buffer = ByteArray(254)
    private var bufferPos = 0

    fun writeBits(value: Int, numBits: Int) {
        var v = value
        var bitsLeft = numBits
        while (bitsLeft > 0) {
            val space = 8 - bitCount
            val bitsToWrite = min(space, bitsLeft)
            val mask = (1 shl bitsToWrite) - 1
            currentByte = currentByte or ((v and mask) shl bitCount)
            v = v ushr bitsToWrite
            bitsLeft -= bitsToWrite
            bitCount += bitsToWrite
            if (bitCount == 8) {
                flushByte()
            }
        }
    }

    private fun flushByte() {
        buffer[bufferPos++] = currentByte.toByte()
        currentByte = 0
        bitCount = 0
        if (bufferPos == buffer.size) {
            flushBuffer()
        }
    }

    private fun flushBuffer() {
        if (bufferPos > 0) {
            output.write(bufferPos) // Sub-block length byte
            output.write(buffer, 0, bufferPos)
            bufferPos = 0
        }
    }

    fun finish() {
        if (bitCount > 0) {
            flushByte()
        }
        flushBuffer()
        output.write(0) // Empty sub-block terminator
    }
}

/**
 * Pure-Kotlin LZW encoder implementing the GIF89a image data compression algorithm.
 *
 * Uses open-addressing double-hashed table with zero object allocations during encoding loop.
 */
object LzwEncoder {

    private const val HASH_TABLE_SIZE = 9973 // Prime number > 4096 / 0.5
    private const val MAX_BITS = 12
    private const val MAX_CODE = 1 shl MAX_BITS // 4096

    fun encode(
        indices: ByteArray,
        minCodeSize: Int,
        outputStream: OutputStream
    ) {
        require(minCodeSize in 2..8) { "minCodeSize must be in 2..8, got $minCodeSize" }

        // Output minCodeSize byte
        outputStream.write(minCodeSize)

        val bitOut = GifBitOutputStream(outputStream)

        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        val firstFreeCode = clearCode + 2

        var currentCodeSize = minCodeSize + 1
        var nextCode = firstFreeCode

        // Open-addressing hash table
        val keyTable = IntArray(HASH_TABLE_SIZE) { -1 }
        val valTable = IntArray(HASH_TABLE_SIZE)

        fun resetTable() {
            keyTable.fill(-1)
            currentCodeSize = minCodeSize + 1
            nextCode = firstFreeCode
        }

        fun findKey(key: Int): Int {
            var idx = (key and 0x7FFFFFFF) % HASH_TABLE_SIZE
            val step = 1 + ((key and 0x7FFFFFFF) % (HASH_TABLE_SIZE - 2))
            while (keyTable[idx] != -1) {
                if (keyTable[idx] == key) return idx
                idx = (idx + step) % HASH_TABLE_SIZE
            }
            return idx
        }

        // Start with ClearCode
        bitOut.writeBits(clearCode, currentCodeSize)

        if (indices.isEmpty()) {
            bitOut.writeBits(eoiCode, currentCodeSize)
            bitOut.finish()
            return
        }

        var prefix = indices[0].toInt() and 0xFF

        for (i in 1 until indices.size) {
            val c = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or c
            val idx = findKey(key)

            if (keyTable[idx] != -1) {
                prefix = valTable[idx]
            } else {
                // Emit code for prefix
                bitOut.writeBits(prefix, currentCodeSize)

                // Add to table if within capacity
                if (nextCode < MAX_CODE) {
                    keyTable[idx] = key
                    valTable[idx] = nextCode++

                    if (nextCode > (1 shl currentCodeSize) && currentCodeSize < MAX_BITS) {
                        currentCodeSize++
                    }
                } else {
                    // Table full: emit ClearCode and reset
                    bitOut.writeBits(clearCode, currentCodeSize)
                    resetTable()
                }

                prefix = c
            }
        }

        // Emit final prefix code and EOI
        bitOut.writeBits(prefix, currentCodeSize)
        bitOut.writeBits(eoiCode, currentCodeSize)
        bitOut.finish()
    }
}
