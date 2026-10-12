package com.artstudio.document.io.psd

import com.artstudio.document.io.ConversionReport
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import com.artstudio.document.model.LayerNode
import com.artstudio.export.ArtworkExportRenderer
import com.artstudio.export.ExportConfig
import com.artstudio.export.ExportFormat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Cleanroom, memory-safe pure-Kotlin serializer producing standard 8-bit RGB Photoshop (PSD) files.
 *
 * Implements official Adobe Photoshop File Formats Specification without external dependencies.
 *
 * Capabilities:
 * - Emits valid standard 8BPS header with 8-bit RGB color mode.
 * - Writes ResolutionInfo image resource (300 DPI) for print interoperability.
 * - Writes multi-layer records with bounds, channel IDs (-1 alpha, 0 R, 1 G, 2 B),
 *   opacity, visibility flags, alpha lock flags, clipping mask relations, and blend modes.
 * - Compresses channel scanlines using PackBits RLE.
 * - Writes mandatory global composite image data for Photoshop/Procreate thumbnailers.
 * - Writes atomically via temporary files.
 */
object PsdWriter {

    private val PSD_SIGNATURE = byteArrayOf(0x38, 0x42, 0x50, 0x53) // '8BPS'
    private val BIM_SIGNATURE = byteArrayOf(0x38, 0x42, 0x49, 0x4D) // '8BIM'

    fun exportPsd(
        document: ArtDocument,
        layerPixelBuffers: Map<String, IntArray> = emptyMap(),
        layerMaskBuffers: Map<String, IntArray> = emptyMap(),
        outputFile: File
    ): PsdExportResult {
        val width = document.canvas.width.coerceAtLeast(1)
        val height = document.canvas.height.coerceAtLeast(1)

        // 1. Audit non-destructive feature conversions
        val conversionReport = com.artstudio.document.io.DocumentConversionAuditor.auditExport(document, "PSD")

        // 2. Identify raster layers to export
        val allLayers = document.allLayersFlat()
        val rasterLayers = allLayers.filterIsInstance<LayerNode.RasterLayer>()

        // 3. Prepare parent directory and atomic temporary file
        val parentDir = outputFile.parentFile ?: File(".")
        if (!parentDir.exists()) parentDir.mkdirs()
        val tempFile = File(parentDir, ".psd_tmp_${UUID.randomUUID()}.tmp")

        try {
            FileOutputStream(tempFile).use { fos ->
                val dos = DataOutputStream(fos)

                // --- Section 1: File Header (26 bytes) ---
                dos.write(PSD_SIGNATURE)
                dos.writeShort(1) // version = 1
                dos.write(ByteArray(6)) // reserved 6 bytes
                dos.writeShort(3) // 3 channels in composite (RGB)
                dos.writeInt(height)
                dos.writeInt(width)
                dos.writeShort(8) // 8 bits per channel
                dos.writeShort(3) // color mode 3 = RGB

                // --- Section 2: Color Mode Data Section ---
                dos.writeInt(0) // length 0 for RGB

                // --- Section 3: Image Resources Section ---
                val resInfoBlock = buildResolutionInfoBlock()
                dos.writeInt(resInfoBlock.size)
                dos.write(resInfoBlock)

                // --- Section 4: Layer and Mask Information Section ---
                val layerSectionBytes = buildLayerSection(
                    width = width,
                    height = height,
                    rasterLayers = rasterLayers,
                    layerPixelBuffers = layerPixelBuffers
                )
                dos.writeInt(layerSectionBytes.size)
                dos.write(layerSectionBytes)

                // --- Section 5: Global Composite Image Data Section ---
                // Render full composite artwork
                val renderedComposite = ArtworkExportRenderer.renderArtwork(
                    document = document,
                    config = ExportConfig(format = ExportFormat.PNG),
                    layerPixelBuffers = layerPixelBuffers,
                    layerMaskBuffers = layerMaskBuffers
                )
                writeCompositeImageData(dos, width, height, renderedComposite.pixels)

                dos.flush()
            }

            // Verify temporary output
            if (!tempFile.exists() || tempFile.length() <= 26L) {
                throw IllegalStateException("Failed to write valid PSD file")
            }

            // Atomic rename
            if (outputFile.exists()) outputFile.delete()
            val renamed = tempFile.renameTo(outputFile)
            if (!renamed) {
                tempFile.inputStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
            }

            return PsdExportResult(
                outputFile = outputFile,
                layerCount = rasterLayers.size,
                fileSize = outputFile.length(),
                conversionReport = conversionReport
            )
        } catch (e: Throwable) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }

    private fun buildResolutionInfoBlock(): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.write(BIM_SIGNATURE)
        dos.writeShort(0x03ED) // ResolutionInfo resource ID
        dos.writeShort(0) // Pascal name (empty string: 1 byte len 0 + 1 byte pad)
        dos.writeInt(16) // data size = 16 bytes

        // ResolutionInfo structure (300 DPI)
        dos.writeInt(300 shl 16) // Horizontal resolution (fixed 16.16)
        dos.writeShort(1) // Unit: 1 = pixels per inch
        dos.writeShort(1) // Width unit: 1 = inches
        dos.writeInt(300 shl 16) // Vertical resolution (fixed 16.16)
        dos.writeShort(1) // Unit: 1 = pixels per inch
        dos.writeShort(1) // Height unit: 1 = inches

        return baos.toByteArray()
    }

    private fun buildLayerSection(
        width: Int,
        height: Int,
        rasterLayers: List<LayerNode.RasterLayer>,
        layerPixelBuffers: Map<String, IntArray>
    ): ByteArray {
        if (rasterLayers.isEmpty()) {
            return ByteArray(0)
        }

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1. Pre-encode channel data for each layer to compute channel lengths
        val layerChannelPayloads = mutableListOf<List<ChannelPayload>>()

        for (layer in rasterLayers) {
            val lWidth = layer.width.coerceAtLeast(1)
            val lHeight = layer.height.coerceAtLeast(1)
            val pixels = layerPixelBuffers[layer.id] ?: IntArray(lWidth * lHeight) { 0 }

            val channels = mutableListOf<ChannelPayload>()
            val chIds = listOf(-1, 0, 1, 2) // Alpha, R, G, B

            for (chId in chIds) {
                val rlePayload = encodeChannelRle(pixels, lWidth, lHeight, chId)
                channels.add(ChannelPayload(chId, rlePayload))
            }

            layerChannelPayloads.add(channels)
        }

        // Layer info section
        val layerInfoBaos = ByteArrayOutputStream()
        val layerInfoDos = DataOutputStream(layerInfoBaos)

        // Negative layer count indicates merged alpha channel is present in composite
        layerInfoDos.writeShort(-rasterLayers.size)

        // Layer Records
        for (i in rasterLayers.indices) {
            val layer = rasterLayers[i]
            val lWidth = layer.width.coerceAtLeast(1)
            val lHeight = layer.height.coerceAtLeast(1)
            val left = layer.transform.translationX.roundToInt()
            val top = layer.transform.translationY.roundToInt()
            val right = left + lWidth
            val bottom = top + lHeight

            val channels = layerChannelPayloads[i]

            layerInfoDos.writeInt(top)
            layerInfoDos.writeInt(left)
            layerInfoDos.writeInt(bottom)
            layerInfoDos.writeInt(right)
            layerInfoDos.writeShort(channels.size)

            for (ch in channels) {
                layerInfoDos.writeShort(ch.id)
                layerInfoDos.writeInt(ch.payload.size) // Channel data byte length
            }

            layerInfoDos.write(BIM_SIGNATURE) // '8BIM'
            layerInfoDos.write(blendModeToPsdKey(layer.blendMode).toByteArray(Charsets.US_ASCII))

            val opacityByte = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            layerInfoDos.writeByte(opacityByte)

            val clippingByte = if (layer.isClippingMask) 1 else 0
            layerInfoDos.writeByte(clippingByte)

            var flags = 0
            if (layer.isAlphaLocked) flags = flags or 0x01
            if (!layer.visible) flags = flags or 0x02
            layerInfoDos.writeByte(flags)
            layerInfoDos.writeByte(0) // filler

            // Extra Data field
            val extraBaos = ByteArrayOutputStream()
            val extraDos = DataOutputStream(extraBaos)

            extraDos.writeInt(0) // Layer mask data length = 0
            extraDos.writeInt(0) // Layer blending ranges length = 0

            // Pascal string layer name (padded to 4 bytes)
            val nameBytes = layer.name.toByteArray(Charsets.UTF_8).take(255).toByteArray()
            extraDos.writeByte(nameBytes.size)
            extraDos.write(nameBytes)
            val totalNameBytes = 1 + nameBytes.size
            val paddedLen = ((totalNameBytes + 3) / 4) * 4
            val padBytes = paddedLen - totalNameBytes
            for (p in 0 until padBytes) {
                extraDos.writeByte(0)
            }

            val extraBytes = extraBaos.toByteArray()
            layerInfoDos.writeInt(extraBytes.size)
            layerInfoDos.write(extraBytes)
        }

        // Channel Image Data for each layer record
        for (channels in layerChannelPayloads) {
            for (ch in channels) {
                layerInfoDos.write(ch.payload)
            }
        }

        val layerInfoBytes = layerInfoBaos.toByteArray()

        // Padded layer info length to even bytes
        val layerInfoPadded = if (layerInfoBytes.size % 2 != 0) {
            layerInfoBytes + byteArrayOf(0)
        } else {
            layerInfoBytes
        }

        dos.writeInt(layerInfoPadded.size)
        dos.write(layerInfoPadded)

        val fullLayerSectionBytes = baos.toByteArray()
        return if (fullLayerSectionBytes.size % 2 != 0) {
            fullLayerSectionBytes + byteArrayOf(0)
        } else {
            fullLayerSectionBytes
        }
    }

    private fun encodeChannelRle(
        pixels: IntArray,
        width: Int,
        height: Int,
        chId: Int
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeShort(1) // Compression = 1 (RLE PackBits)

        // Pre-compress scanlines
        val compressedRows = mutableListOf<ByteArray>()
        for (y in 0 until height) {
            val rowBytes = ByteArray(width)
            val rowOffset = y * width
            for (x in 0 until width) {
                val p = pixels[rowOffset + x]
                rowBytes[x] = when (chId) {
                    -1 -> ((p ushr 24) and 0xFF).toByte() // Alpha
                    0 -> ((p ushr 16) and 0xFF).toByte()  // Red
                    1 -> ((p ushr 8) and 0xFF).toByte()   // Green
                    2 -> (p and 0xFF).toByte()            // Blue
                    else -> 0.toByte()
                }
            }
            compressedRows.add(PackBits.encode(rowBytes))
        }

        // Write row byte counts (2 bytes each)
        for (row in compressedRows) {
            dos.writeShort(row.size)
        }

        // Write compressed scanline bytes
        for (row in compressedRows) {
            dos.write(row)
        }

        return baos.toByteArray()
    }

    private fun writeCompositeImageData(
        dos: DataOutputStream,
        width: Int,
        height: Int,
        pixels: IntArray
    ) {
        dos.writeShort(1) // Compression = 1 (RLE PackBits)

        val channels = listOf(0, 1, 2) // R, G, B
        val compressedChannelRows = mutableListOf<List<ByteArray>>()

        for (ch in channels) {
            val rows = mutableListOf<ByteArray>()
            for (y in 0 until height) {
                val rowBytes = ByteArray(width)
                val rowOffset = y * width
                for (x in 0 until width) {
                    val p = pixels[rowOffset + x]
                    rowBytes[x] = when (ch) {
                        0 -> ((p ushr 16) and 0xFF).toByte()
                        1 -> ((p ushr 8) and 0xFF).toByte()
                        2 -> (p and 0xFF).toByte()
                        else -> 0.toByte()
                    }
                }
                rows.add(PackBits.encode(rowBytes))
            }
            compressedChannelRows.add(rows)
        }

        // Write row byte counts for all channels (3 * height)
        for (rows in compressedChannelRows) {
            for (row in rows) {
                dos.writeShort(row.size)
            }
        }

        // Write compressed rows
        for (rows in compressedChannelRows) {
            for (row in rows) {
                dos.write(row)
            }
        }
    }

    fun blendModeToPsdKey(mode: BlendModeType): String {
        return when (mode) {
            BlendModeType.Normal -> "norm"
            BlendModeType.Multiply -> "mul "
            BlendModeType.Screen -> "scrn"
            BlendModeType.Overlay -> "over"
            BlendModeType.Darken -> "dark"
            BlendModeType.Lighten -> "lite"
            BlendModeType.ColorDodge -> "div "
            BlendModeType.Add -> "plus"
            BlendModeType.ColorBurn -> "idiv"
            BlendModeType.Difference -> "diff"
            BlendModeType.Exclusion -> "smud"
            BlendModeType.Hue -> "hue "
            BlendModeType.Saturation -> "sat "
            BlendModeType.Color -> "colr"
            BlendModeType.Luminosity -> "lum "
            BlendModeType.SoftLight -> "sLit"
            BlendModeType.HardLight -> "hLit"
            else -> "norm"
        }
    }

    fun psdKeyToBlendMode(key: String): BlendModeType {
        return when (key.trim()) {
            "norm" -> BlendModeType.Normal
            "mul" -> BlendModeType.Multiply
            "scrn" -> BlendModeType.Screen
            "over" -> BlendModeType.Overlay
            "dark" -> BlendModeType.Darken
            "lite" -> BlendModeType.Lighten
            "div" -> BlendModeType.ColorDodge
            "plus", "lddg" -> BlendModeType.Add
            "idiv" -> BlendModeType.ColorBurn
            "diff" -> BlendModeType.Difference
            "smud" -> BlendModeType.Exclusion
            "hue" -> BlendModeType.Hue
            "sat" -> BlendModeType.Saturation
            "colr" -> BlendModeType.Color
            "lum" -> BlendModeType.Luminosity
            "sLit" -> BlendModeType.SoftLight
            "hLit" -> BlendModeType.HardLight
            else -> BlendModeType.Normal
        }
    }

    private data class ChannelPayload(
        val id: Int,
        val payload: ByteArray
    )
}
