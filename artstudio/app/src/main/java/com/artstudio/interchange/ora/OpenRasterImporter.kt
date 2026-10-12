package com.artstudio.interchange.ora

import com.artstudio.document.io.ConversionReport
import com.artstudio.document.io.ConversionWarning
import com.artstudio.document.io.CorruptDocumentException
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.importer.RasterImageImporter
import java.io.*
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * High-fidelity OpenRaster (.ora) importer.
 * Preserves layer names, order, visibility, opacity, offsets, and raster pixels
 * without silent flattening, compiling non-destructive conversion audit warnings for unsupported features.
 */
object OpenRasterImporter {

    const val MIMETYPE_ORA = "image/openraster"

    const val MAX_ENTRY_SIZE = 100 * 1024 * 1024L // 100 MB per entry
    const val MAX_TOTAL_UNCOMPRESSED_SIZE = 250 * 1024 * 1024L // 250 MB total archive
    const val MAX_CANVAS_DIMENSION = 16384
    const val MAX_LAYER_COUNT = 500

    /**
     * Imports an OpenRaster file from disk.
     */
    fun importOra(file: File): OraImportResult {
        if (!file.exists()) {
            throw CorruptDocumentException("OpenRaster file does not exist: ${file.absolutePath}")
        }
        if (file.length() == 0L) {
            throw CorruptDocumentException("OpenRaster file is empty (0 bytes): ${file.absolutePath}")
        }

        return FileInputStream(file).use { fis ->
            importOra(fis, defaultTitle = file.nameWithoutExtension)
        }
    }

    /**
     * Imports an OpenRaster archive from an input stream.
     */
    fun importOra(inputStream: InputStream, defaultTitle: String = "Imported Artwork"): OraImportResult {
        val warnings = mutableListOf<ConversionWarning>()
        var foundMimetype = false
        var stackXmlString: String? = null
        var thumbnailBytes: ByteArray? = null
        var mergedImageBytes: ByteArray? = null
        val zipEntries = mutableMapOf<String, ByteArray>()
        var totalUncompressedBytes = 0L

        try {
            BufferedInputStream(inputStream).use { bis ->
                ZipInputStream(bis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name

                        // Guard against Zip Slip path traversal
                        if (name.contains("..") || name.startsWith("/")) {
                            throw CorruptDocumentException("Illegal path traversal in OpenRaster entry: $name")
                        }

                        if (!entry.isDirectory) {
                            val bytes = readEntryBytes(
                                zis = zis,
                                entryName = name,
                                maxEntrySize = MAX_ENTRY_SIZE,
                                totalBytesSoFar = totalUncompressedBytes,
                                maxTotalSize = MAX_TOTAL_UNCOMPRESSED_SIZE
                            )
                            totalUncompressedBytes += bytes.size
                            when (name) {
                                "mimetype" -> {
                                    val mime = String(bytes, Charsets.US_ASCII).trim()
                                    if (mime == MIMETYPE_ORA) {
                                        foundMimetype = true
                                    } else {
                                        warnings.add(
                                            ConversionWarning(
                                                layerId = "archive",
                                                layerName = "mimetype",
                                                feature = "MIMETYPE_MISMATCH",
                                                actionTaken = "Parsed archive despite non-standard mimetype '$mime'",
                                                message = "Expected '$MIMETYPE_ORA'"
                                            )
                                        )
                                    }
                                }
                                "stack.xml" -> {
                                    stackXmlString = String(bytes, Charsets.UTF_8)
                                }
                                "Thumbnails/thumbnail.png" -> {
                                    thumbnailBytes = bytes
                                }
                                "mergedimage.png" -> {
                                    mergedImageBytes = bytes
                                }
                                else -> {
                                    zipEntries[name] = bytes
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: CorruptDocumentException) {
            throw e
        } catch (e: Exception) {
            throw CorruptDocumentException("Failed reading OpenRaster archive: ${e.message}", e)
        }

        if (stackXmlString == null) {
            throw CorruptDocumentException("Malformed OpenRaster archive: missing required 'stack.xml'")
        }

        val oraImage = try {
            OpenRasterStackXml.parseStackXml(stackXmlString!!)
        } catch (e: Exception) {
            throw CorruptDocumentException("Failed to parse OpenRaster stack.xml: ${e.message}", e)
        }

        // Validate canvas dimensions
        if (oraImage.width <= 0 || oraImage.width > MAX_CANVAS_DIMENSION ||
            oraImage.height <= 0 || oraImage.height > MAX_CANVAS_DIMENSION
        ) {
            throw CorruptDocumentException("OpenRaster canvas dimensions ${oraImage.width}x${oraImage.height} exceed allowed limits (1..$MAX_CANVAS_DIMENSION)")
        }

        val layerPixelBuffers = mutableMapOf<String, IntArray>()

        // Convert OraStack into ArtDocument LayerNode hierarchy
        val rootChildren = convertOraNodes(
            nodes = oraImage.rootStack.children,
            zipEntries = zipEntries,
            layerPixelBuffers = layerPixelBuffers,
            warnings = warnings
        )

        val document = ArtDocument(
            title = defaultTitle,
            canvas = CanvasConfig(
                width = oraImage.width,
                height = oraImage.height
            ),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = rootChildren
            ),
            selectedLayerId = rootChildren.lastOrNull()?.id
        )

        // Validate total layer count
        if (document.allLayersFlat().size > MAX_LAYER_COUNT) {
            throw CorruptDocumentException("OpenRaster contains ${document.allLayersFlat().size} layers, exceeding maximum allowed limit of $MAX_LAYER_COUNT")
        }

        val report = ConversionReport(
            targetFormat = "OPENRASTER_IMPORT",
            warnings = warnings
        )

        return OraImportResult(
            document = document,
            layerPixelBuffers = layerPixelBuffers,
            thumbnail = thumbnailBytes,
            mergedImage = mergedImageBytes,
            report = report
        )
    }

    private fun convertOraNodes(
        nodes: List<OraNode>,
        zipEntries: Map<String, ByteArray>,
        layerPixelBuffers: MutableMap<String, IntArray>,
        warnings: MutableList<ConversionWarning>
    ): List<LayerNode> {
        val result = mutableListOf<LayerNode>()

        for (node in nodes) {
            when (node) {
                is OraNode.Layer -> {
                    val layerId = UUID.randomUUID().toString()
                    val (blendMode, isUnsupported) = OpenRasterStackXml.compositeOpToBlendMode(node.compositeOp)
                    if (isUnsupported) {
                        warnings.add(
                            ConversionWarning(
                                layerId = layerId,
                                layerName = node.name,
                                feature = "UNSUPPORTED_BLEND_MODE",
                                actionTaken = "Fell back to Normal blend mode",
                                message = "OpenRaster composite operator '${node.compositeOp}' is not natively supported."
                            )
                        )
                    }

                    // Decode layer raster image
                    val imgBytes = zipEntries[node.src]
                    var layerW = 1024
                    var layerH = 1024

                    if (imgBytes != null && imgBytes.isNotEmpty()) {
                        val decoded = RasterImageImporter.decodeRasterImage(imgBytes, node.name)
                        if (decoded != null) {
                            layerW = decoded.width
                            layerH = decoded.height
                            layerPixelBuffers[layerId] = decoded.pixels
                        } else {
                            warnings.add(
                                ConversionWarning(
                                    layerId = layerId,
                                    layerName = node.name,
                                    feature = "LAYER_DECODE_FAILED",
                                    actionTaken = "Initialized empty layer buffer",
                                    message = "Failed to decode raster entry '${node.src}'."
                                )
                            )
                        }
                    } else {
                        warnings.add(
                            ConversionWarning(
                                layerId = layerId,
                                layerName = node.name,
                                feature = "MISSING_LAYER_ASSET",
                                actionTaken = "Initialized empty layer",
                                message = "Raster asset entry '${node.src}' not found in archive."
                            )
                        )
                    }

                    result.add(
                        LayerNode.RasterLayer(
                            id = layerId,
                            name = node.name,
                            visible = node.visible,
                            opacity = node.opacity,
                            blendMode = blendMode,
                            width = layerW,
                            height = layerH,
                            offsetX = node.x,
                            offsetY = node.y,
                            assetPath = node.src
                        )
                    )
                }

                is OraNode.Stack -> {
                    val groupId = UUID.randomUUID().toString()
                    val (blendMode, isUnsupported) = OpenRasterStackXml.compositeOpToBlendMode(node.compositeOp)
                    if (isUnsupported) {
                        warnings.add(
                            ConversionWarning(
                                layerId = groupId,
                                layerName = node.name,
                                feature = "UNSUPPORTED_GROUP_BLEND_MODE",
                                actionTaken = "Fell back to Normal blend mode",
                                message = "OpenRaster group composite operator '${node.compositeOp}' is unsupported."
                            )
                        )
                    }

                    val childNodes = convertOraNodes(
                        nodes = node.children,
                        zipEntries = zipEntries,
                        layerPixelBuffers = layerPixelBuffers,
                        warnings = warnings
                    )

                    result.add(
                        LayerNode.LayerGroup(
                            id = groupId,
                            name = node.name,
                            visible = node.visible,
                            opacity = node.opacity,
                            blendMode = blendMode,
                            children = childNodes
                        )
                    )
                }
            }
        }

        return result
    }

    private fun readEntryBytes(
        zis: ZipInputStream,
        entryName: String,
        maxEntrySize: Long = MAX_ENTRY_SIZE,
        totalBytesSoFar: Long = 0L,
        maxTotalSize: Long = MAX_TOTAL_UNCOMPRESSED_SIZE
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count: Int
        var entryBytesRead = 0L
        while (zis.read(buffer).also { count = it } != -1) {
            entryBytesRead += count
            if (entryBytesRead > maxEntrySize) {
                throw CorruptDocumentException("OpenRaster entry '$entryName' exceeds maximum allowed size ($maxEntrySize bytes, possible zip bomb)")
            }
            if (totalBytesSoFar + entryBytesRead > maxTotalSize) {
                throw CorruptDocumentException("OpenRaster total uncompressed content exceeds limit ($maxTotalSize bytes, possible zip bomb)")
            }
            baos.write(buffer, 0, count)
        }
        return baos.toByteArray()
    }
}
