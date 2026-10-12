package com.artstudio.document.io.psd

import com.artstudio.document.io.CorruptDocumentException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cleanroom, memory-safe pure-Kotlin parser for Adobe Photoshop (PSD) 8BPS format.
 *
 * Implements the official Adobe Photoshop File Formats Specification without third-party dependencies.
 *
 * Capabilities:
 * - 8-bit RGB color mode.
 * - Multi-layer records: bounds (top, left, bottom, right), channel metadata.
 * - Blend modes: Normal, Multiply, Screen, Overlay, Darken, Lighten, Color Dodge, Add.
 * - Layer properties: opacity, visibility flags, alpha lock flags, clipping mask relations, layer names.
 * - Channel compression: PackBits RLE (compression = 1) and Raw uncompressed (compression = 0).
 * - Layer masks: user supplied mask channels (channel ID = -2).
 * - Composite image: global composite preview fallback.
 */
object PsdParser {

    private val PSD_SIGNATURE = byteArrayOf(0x38, 0x42, 0x50, 0x53) // '8BPS'
    private val BIM_SIGNATURE = byteArrayOf(0x38, 0x42, 0x49, 0x4D) // '8BIM'

    const val MAX_PSD_FILE_SIZE = 100 * 1024 * 1024L // 100 MB max PSD size
    const val MAX_PSD_DIMENSION = 16384 // 16K x 16K
    const val MAX_PSD_LAYER_COUNT = 500

    /**
     * Parses an input stream into a structured [PsdDocument].
     * Reads in bounded streaming chunks to avoid unbounded memory expansion.
     */
    fun parse(inputStream: InputStream): PsdDocument {
        val baos = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count: Int
        var totalRead = 0L
        while (inputStream.read(buffer).also { count = it } != -1) {
            totalRead += count
            if (totalRead > MAX_PSD_FILE_SIZE) {
                throw CorruptDocumentException("PSD file exceeds maximum allowed size of ${MAX_PSD_FILE_SIZE / (1024 * 1024)}MB")
            }
            baos.write(buffer, 0, count)
        }
        return parse(baos.toByteArray())
    }

    /**
     * Parses a byte array into a structured [PsdDocument].
     */
    fun parse(bytes: ByteArray): PsdDocument {
        if (bytes.size < 26) {
            throw CorruptDocumentException("File is too small to be a valid PSD document (${bytes.size} bytes)")
        }
        if (bytes.size > MAX_PSD_FILE_SIZE) {
            throw CorruptDocumentException("PSD file exceeds maximum allowed size of ${MAX_PSD_FILE_SIZE / (1024 * 1024)}MB")
        }

        try {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            // 1. File Header (26 bytes)
            val sig = ByteArray(4)
            buf.get(sig)
        if (!sig.contentEquals(PSD_SIGNATURE)) {
            val actual = sig.toString(Charsets.US_ASCII)
            throw CorruptDocumentException("Invalid PSD signature: expected '8BPS', got '$actual'")
        }

        val version = buf.short.toInt()
        if (version != 1) {
            throw CorruptDocumentException("Unsupported PSD version $version (only standard PSD v1 supported)")
        }

        // 6 bytes reserved
        buf.position(buf.position() + 6)

        val channels = buf.short.toInt()
        val height = buf.int
        val width = buf.int
        val depth = buf.short.toInt()
        val colorMode = buf.short.toInt()

        if (width <= 0 || height <= 0 || width > MAX_PSD_DIMENSION || height > MAX_PSD_DIMENSION) {
            throw CorruptDocumentException("Invalid or unsupported PSD canvas dimensions: ${width}x${height} (max: $MAX_PSD_DIMENSION)")
        }
        if (colorMode != 3) { // 3 = RGB
            // Mode is not RGB: throw informative error
            throw CorruptDocumentException("Unsupported color mode $colorMode (only RGB mode 3 is supported)")
        }

        val header = PsdHeader(
            channels = channels,
            height = height,
            width = width,
            depth = depth,
            colorMode = colorMode
        )

        // 2. Color Mode Data Section
        val colorModeLen = buf.int
        if (colorModeLen < 0 || buf.position() + colorModeLen > buf.limit()) {
            throw CorruptDocumentException("Invalid color mode section length: $colorModeLen")
        }
        if (colorModeLen > 0) {
            buf.position(buf.position() + colorModeLen)
        }

        // 3. Image Resources Section
        val imgResLen = buf.int
        if (imgResLen < 0 || buf.position() + imgResLen > buf.limit()) {
            throw CorruptDocumentException("Invalid image resources section length: $imgResLen")
        }
        if (imgResLen > 0) {
            buf.position(buf.position() + imgResLen)
        }

        // 4. Layer and Mask Information Section
        val layers = mutableListOf<PsdLayer>()
        if (buf.remaining() >= 4) {
            val layerAndMaskSectionLen = buf.int
            val layerSectionStart = buf.position()
            val layerSectionEnd = layerSectionStart + layerAndMaskSectionLen

            if (layerAndMaskSectionLen > 0) {
                if (layerSectionEnd > bytes.size) {
                    throw CorruptDocumentException("Premature end of PSD file: layer section truncated")
                }
                // Layer Info Section length
                val layerInfoLen = buf.int
                if (layerInfoLen > 0) {
                    if (buf.position() + layerInfoLen > layerSectionEnd) {
                        throw CorruptDocumentException("Premature end of PSD file: layer info truncated")
                    }
                    val rawLayerCount = buf.short.toInt()
                    val layerCount = Math.abs(rawLayerCount)
                    if (layerCount > MAX_PSD_LAYER_COUNT) {
                        throw CorruptDocumentException("PSD layer count $layerCount exceeds maximum allowed limit of $MAX_PSD_LAYER_COUNT")
                    }

                    val records = mutableListOf<PsdLayerRecord>()

                    // Parse Layer Records
                    for (i in 0 until layerCount) {
                        val top = buf.int
                        val left = buf.int
                        val bottom = buf.int
                        val right = buf.int
                        val channelCount = buf.short.toInt()

                        val channelInfos = mutableListOf<PsdChannelInfo>()
                        for (c in 0 until channelCount) {
                            val chId = buf.short.toInt()
                            val chLen = buf.int
                            channelInfos.add(PsdChannelInfo(chId, chLen))
                        }

                        val blendSig = ByteArray(4)
                        buf.get(blendSig) // '8BIM'

                        val blendKeyBytes = ByteArray(4)
                        buf.get(blendKeyBytes)
                        val blendKey = blendKeyBytes.toString(Charsets.US_ASCII)

                        val opacity = buf.get().toInt() and 0xFF
                        val clipping = buf.get().toInt() and 0xFF
                        val flags = buf.get().toInt() and 0xFF
                        buf.get() // filler

                        val extraLen = buf.int
                        val extraStart = buf.position()
                        if (extraLen < 0 || extraStart + extraLen > buf.limit()) {
                            throw CorruptDocumentException("Invalid extra layer info length: $extraLen")
                        }

                        // Extra Data: Mask info
                        var maskInfo: PsdMaskInfo? = null
                        val maskDataLen = buf.int
                        if (maskDataLen >= 20) {
                            val mTop = buf.int
                            val mLeft = buf.int
                            val mBottom = buf.int
                            val mRight = buf.int
                            val mDefColor = buf.get().toInt() and 0xFF
                            val mFlags = buf.get().toInt() and 0xFF
                            maskInfo = PsdMaskInfo(mTop, mLeft, mBottom, mRight, mDefColor, mFlags)
                            val remainingMaskBytes = maskDataLen - 20
                            if (remainingMaskBytes > 0 && buf.position() + remainingMaskBytes <= buf.limit()) {
                                buf.position(buf.position() + remainingMaskBytes)
                            }
                        } else if (maskDataLen > 0 && buf.position() + maskDataLen <= buf.limit()) {
                            buf.position(buf.position() + maskDataLen)
                        }

                        // Layer blending ranges
                        val blendRangeLen = buf.int
                        if (blendRangeLen > 0 && buf.position() + blendRangeLen <= buf.limit()) {
                            buf.position(buf.position() + blendRangeLen)
                        }

                        // Layer name (Pascal string, padded to 4 bytes)
                        val nameLen = buf.get().toInt() and 0xFF
                        if (nameLen > buf.remaining()) {
                            throw CorruptDocumentException("Layer name length exceeds remaining stream: $nameLen")
                        }
                        val nameBytes = ByteArray(nameLen)
                        buf.get(nameBytes)
                        val layerName = if (nameLen > 0) nameBytes.toString(Charsets.UTF_8) else "Layer $i"

                        // Pad Pascal string to multiple of 4 bytes
                        val stringPaddedLen = ((1 + nameLen + 3) / 4) * 4
                        val paddingToSkip = stringPaddedLen - (1 + nameLen)
                        if (paddingToSkip > 0 && buf.position() + paddingToSkip <= extraStart + extraLen) {
                            buf.position(buf.position() + paddingToSkip)
                        }

                        // Skip remainder of extra data (e.g. adjustment layer tags, text engine data)
                        val consumedExtra = buf.position() - extraStart
                        val remainingExtra = extraLen - consumedExtra
                        if (remainingExtra > 0 && buf.position() + remainingExtra <= buf.limit()) {
                            buf.position(buf.position() + remainingExtra)
                        }

                        records.add(
                            PsdLayerRecord(
                                top = top,
                                left = left,
                                bottom = bottom,
                                right = right,
                                channels = channelInfos,
                                blendModeKey = blendKey,
                                opacity = opacity,
                                clipping = clipping,
                                flags = flags,
                                name = layerName,
                                maskInfo = maskInfo
                            )
                        )
                    }

                    // Parse Channel Image Data for each layer record
                    for (rec in records) {
                        val layerW = rec.width
                        val layerH = rec.height

                        if (layerW > MAX_PSD_DIMENSION || layerH > MAX_PSD_DIMENSION ||
                            layerW.toLong() * layerH > 16 * 1024 * 1024L
                        ) {
                            throw CorruptDocumentException("PSD layer dimensions ${layerW}x${layerH} exceed safe limits")
                        }

                        val channelByteArrays = mutableMapOf<Int, ByteArray>()

                        for (chInfo in rec.channels) {
                            if (layerW <= 0 || layerH <= 0 || chInfo.length <= 0) {
                                continue
                            }

                            if (buf.remaining() < 2) {
                                throw CorruptDocumentException("Premature end of PSD file: truncated channel data")
                            }
                            val compMethod = buf.short.toInt() // 0 = Raw, 1 = RLE

                            val decompressedBytes = if (compMethod == 1) {
                                // RLE: read scanline byte counts
                                val rowByteCounts = IntArray(layerH)
                                for (r in 0 until layerH) {
                                    if (buf.remaining() < 2) throw CorruptDocumentException("Truncated PSD RLE row byte counts")
                                    rowByteCounts[r] = buf.short.toInt() and 0xFFFF
                                }

                                val fullChannel = ByteArray(layerW * layerH)
                                var outOffset = 0
                                for (r in 0 until layerH) {
                                    val rowLen = rowByteCounts[r]
                                    if (rowLen < 0 || rowLen > buf.remaining()) {
                                        throw CorruptDocumentException("Invalid PSD row length $rowLen in RLE channel")
                                    }
                                    val rowBytes = ByteArray(rowLen)
                                    buf.get(rowBytes)
                                    val decodedRow = PackBits.decode(rowBytes, 0, rowLen, layerW)
                                    System.arraycopy(decodedRow, 0, fullChannel, outOffset, layerW)
                                    outOffset += layerW
                                }
                                fullChannel
                            } else {
                                // Raw uncompressed
                                if (layerW.toLong() * layerH > buf.remaining()) {
                                    throw CorruptDocumentException("Raw layer channel data truncated")
                                }
                                val rawBytes = ByteArray(layerW * layerH)
                                buf.get(rawBytes)
                                rawBytes
                            }

                            channelByteArrays[chInfo.id] = decompressedBytes
                        }

                        // Assemble channels into ARGB IntArray
                        val pixelBuffer = if (layerW > 0 && layerH > 0 && channelByteArrays.isNotEmpty()) {
                            val pixels = IntArray(layerW * layerH)
                            val rBytes = channelByteArrays[0]
                            val gBytes = channelByteArrays[1]
                            val bBytes = channelByteArrays[2]
                            val aBytes = channelByteArrays[-1]

                            for (idx in pixels.indices) {
                                val a = if (aBytes != null) aBytes[idx].toInt() and 0xFF else 255
                                val r = if (rBytes != null) rBytes[idx].toInt() and 0xFF else 0
                                val g = if (gBytes != null) gBytes[idx].toInt() and 0xFF else 0
                                val b = if (bBytes != null) bBytes[idx].toInt() and 0xFF else 0
                                pixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                            }
                            pixels
                        } else null

                        // Assemble user mask buffer if present (channel ID = -2)
                        val maskBuffer = if (rec.maskInfo != null && channelByteArrays.containsKey(-2)) {
                            val maskBytes = channelByteArrays[-2]!!
                            val maskPixels = IntArray(maskBytes.size)
                            for (idx in maskPixels.indices) {
                                val g = maskBytes[idx].toInt() and 0xFF
                                maskPixels[idx] = (g shl 24) or (g shl 16) or (g shl 8) or g
                            }
                            maskPixels
                        } else null

                        layers.add(PsdLayer(record = rec, pixelBuffer = pixelBuffer, maskBuffer = maskBuffer))
                    }
                }

                // Advance to end of layer section
                if (buf.position() < layerSectionEnd) {
                    buf.position(layerSectionEnd)
                }
            }
        }

        // 5. Global Composite Image Data Section (Optional / Fallback)
        var compositePixels: IntArray? = null
        if (buf.remaining() >= 2) {
            val compMethod = buf.short.toInt() // 0 = Raw, 1 = RLE
            val compW = width
            val compH = height

            if (compW.toLong() * compH <= 16 * 1024 * 1024L) {
                val compositeBytes = mutableListOf<ByteArray>()
                val chCount = channels.coerceIn(1, 4)

                if (compMethod == 1) { // RLE
                    val totalRows = chCount * compH
                    val rowCounts = IntArray(totalRows)
                    for (r in 0 until totalRows) {
                        if (buf.remaining() >= 2) {
                            rowCounts[r] = buf.short.toInt() and 0xFFFF
                        }
                    }

                    for (ch in 0 until chCount) {
                        val chData = ByteArray(compW * compH)
                        var outOff = 0
                        for (r in 0 until compH) {
                            val rowIdx = ch * compH + r
                            val rowLen = rowCounts[rowIdx]
                            if (buf.remaining() >= rowLen) {
                                val rowBytes = ByteArray(rowLen)
                                buf.get(rowBytes)
                                val decoded = PackBits.decode(rowBytes, 0, rowLen, compW)
                                System.arraycopy(decoded, 0, chData, outOff, compW)
                            }
                            outOff += compW
                        }
                        compositeBytes.add(chData)
                    }
                } else { // Raw
                    for (ch in 0 until chCount) {
                        val chData = ByteArray(compW * compH)
                        val toRead = (compW * compH).coerceAtMost(buf.remaining())
                        buf.get(chData, 0, toRead)
                        compositeBytes.add(chData)
                    }
                }

                if (compositeBytes.size >= 3) {
                    compositePixels = IntArray(compW * compH)
                    val rB = compositeBytes[0]
                    val gB = compositeBytes[1]
                    val bB = compositeBytes[2]
                    val aB = if (compositeBytes.size >= 4) compositeBytes[3] else null

                    for (idx in compositePixels.indices) {
                        val a = if (aB != null) aB[idx].toInt() and 0xFF else 255
                        val r = rB[idx].toInt() and 0xFF
                        val g = gB[idx].toInt() and 0xFF
                        val b = bB[idx].toInt() and 0xFF
                        compositePixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }

            return PsdDocument(
                header = header,
                layers = layers,
                compositePixels = compositePixels
            )
        } catch (e: CorruptDocumentException) {
            throw e
        } catch (e: java.nio.BufferUnderflowException) {
            throw CorruptDocumentException("Premature end of PSD file: stream truncated", e)
        } catch (e: IndexOutOfBoundsException) {
            throw CorruptDocumentException("Corrupted PSD structure: index out of bounds", e)
        } catch (e: Exception) {
            throw CorruptDocumentException("Corrupted PSD structure: ${e.message}", e)
        }
    }
}
