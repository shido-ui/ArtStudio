package com.artstudio.interchange.ora

import com.artstudio.document.io.DocumentConversionAuditor
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.export.*
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Authoritative OpenRaster (.ora) exporter.
 * Packages document layers, order, visibility, opacity, blend modes, and raster pixels
 * into standard OpenRaster archive with Thumbnails/thumbnail.png and mergedimage.png.
 */
object OpenRasterExporter {

    /**
     * Exports an [ArtDocument] to OpenRaster archive format at [outputFile].
     *
     * @param document Source document
     * @param layerPixelBuffers Map of layer UUID to IntArray pixel buffer
     * @param outputFile Target file on disk
     * @param thumbnail Optional custom thumbnail PNG bytes
     * @return [OraExportResult] containing written file, layer count, and conversion audit report
     */
    fun exportOra(
        document: ArtDocument,
        layerPixelBuffers: Map<String, IntArray> = emptyMap(),
        outputFile: File,
        thumbnail: ByteArray? = null
    ): OraExportResult {
        val parentDir = outputFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        val tempFile = File.createTempFile("artstudio_ora_export_", ".tmp", parentDir)
        var totalLayerCount = 0

        try {
            FileOutputStream(tempFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        // 1. Uncompressed mimetype entry (OpenRaster standard requirement)
                        val mimeBytes = OpenRasterImporter.MIMETYPE_ORA.toByteArray(Charsets.US_ASCII)
                        val mimeEntry = ZipEntry("mimetype").apply {
                            method = ZipEntry.STORED
                            size = mimeBytes.size.toLong()
                            compressedSize = mimeBytes.size.toLong()
                            val crc = CRC32()
                            crc.update(mimeBytes)
                            this.crc = crc.value
                        }
                        zos.putNextEntry(mimeEntry)
                        zos.write(mimeBytes)
                        zos.closeEntry()

                        // 2. Prepare layer stack and write layer PNG images into data/
                        val oraChildren = mutableListOf<OraNode>()
                        var layerIndex = 0

                        fun processNode(node: LayerNode): OraNode {
                            return when (node) {
                                is LayerNode.LayerGroup -> {
                                    val groupChildren = node.children.map { processNode(it) }
                                    OraNode.Stack(
                                        name = node.name,
                                        opacity = node.opacity,
                                        visible = node.visible,
                                        compositeOp = OpenRasterStackXml.blendModeToCompositeOp(node.blendMode),
                                        children = groupChildren
                                    )
                                }
                                else -> {
                                    totalLayerCount++
                                    val currentIndex = layerIndex++
                                    val srcPath = "data/layer_$currentIndex.png"

                                    // Extract or generate layer pixel buffer
                                    val (layerW, layerH, pixels) = resolveLayerPixels(node, document, layerPixelBuffers)

                                    // Write layer PNG entry
                                    val layerEntry = ZipEntry(srcPath).apply {
                                        method = ZipEntry.DEFLATED
                                    }
                                    zos.putNextEntry(layerEntry)
                                    PureKotlinPngEncoder.encodeRgba(pixels, layerW, layerH, zos)
                                    zos.closeEntry()

                                    val (xOff, yOff) = if (node is LayerNode.RasterLayer) {
                                        Pair(node.offsetX, node.offsetY)
                                    } else {
                                        Pair(0, 0)
                                    }

                                    OraNode.Layer(
                                        name = node.name,
                                        src = srcPath,
                                        x = xOff,
                                        y = yOff,
                                        opacity = node.opacity,
                                        visible = node.visible,
                                        compositeOp = OpenRasterStackXml.blendModeToCompositeOp(node.blendMode)
                                    )
                                }
                            }
                        }

                        for (child in document.rootGroup.children) {
                            oraChildren.add(processNode(child))
                        }

                        val oraImage = OraImage(
                            version = "0.0.3",
                            width = document.canvas.width,
                            height = document.canvas.height,
                            rootStack = OraNode.Stack(
                                name = "Root",
                                children = oraChildren
                            )
                        )

                        // 3. Write stack.xml
                        val stackXml = OpenRasterStackXml.generateStackXml(oraImage)
                        val stackBytes = stackXml.toByteArray(Charsets.UTF_8)
                        val stackEntry = ZipEntry("stack.xml").apply {
                            method = ZipEntry.DEFLATED
                        }
                        zos.putNextEntry(stackEntry)
                        zos.write(stackBytes)
                        zos.closeEntry()

                        // 4. Write Thumbnails/thumbnail.png
                        val thumbBytes = thumbnail ?: generateThumbnail(document, layerPixelBuffers)
                        val thumbEntry = ZipEntry("Thumbnails/thumbnail.png").apply {
                            method = ZipEntry.DEFLATED
                        }
                        zos.putNextEntry(thumbEntry)
                        zos.write(thumbBytes)
                        zos.closeEntry()

                        // 5. Write mergedimage.png (Full composite of visible layers)
                        val mergedBytes = generateMergedImage(document, layerPixelBuffers)
                        val mergedEntry = ZipEntry("mergedimage.png").apply {
                            method = ZipEntry.DEFLATED
                        }
                        zos.putNextEntry(mergedEntry)
                        zos.write(mergedBytes)
                        zos.closeEntry()
                    }
                }
            }

            // Atomic commit
            if (outputFile.exists()) {
                outputFile.delete()
            }
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }

        val report = DocumentConversionAuditor.auditExport(document, "OPENRASTER")

        return OraExportResult(
            outputFile = outputFile,
            layerCount = totalLayerCount,
            fileSize = outputFile.length(),
            report = report
        )
    }

    private fun resolveLayerPixels(
        layer: LayerNode,
        document: ArtDocument,
        layerPixelBuffers: Map<String, IntArray>
    ): Triple<Int, Int, IntArray> {
        val buffer = layerPixelBuffers[layer.id]
        val (w, h) = if (layer is LayerNode.RasterLayer) {
            Pair(layer.width, layer.height)
        } else {
            Pair(document.canvas.width, document.canvas.height)
        }

        val pixels = if (buffer != null && buffer.size == w * h) {
            buffer
        } else {
            IntArray(w * h) // Transparent buffer fallback
        }
        return Triple(w, h, pixels)
    }

    private fun generateThumbnail(
        document: ArtDocument,
        layerPixelBuffers: Map<String, IntArray>
    ): ByteArray {
        val config = ExportConfig(
            format = ExportFormat.PNG,
            scope = ExportScope.FULL_CANVAS,
            outputWidth = 256,
            outputHeight = 256
        )
        val rendered = ArtworkExportRenderer.renderArtwork(
            document = document,
            config = config,
            layerPixelBuffers = layerPixelBuffers
        )
        val baos = ByteArrayOutputStream()
        PureKotlinPngEncoder.encodeRgba(rendered.pixels, rendered.width, rendered.height, baos)
        return baos.toByteArray()
    }

    private fun generateMergedImage(
        document: ArtDocument,
        layerPixelBuffers: Map<String, IntArray>
    ): ByteArray {
        val config = ExportConfig(
            format = ExportFormat.PNG,
            scope = ExportScope.FULL_CANVAS
        )
        val rendered = ArtworkExportRenderer.renderArtwork(
            document = document,
            config = config,
            layerPixelBuffers = layerPixelBuffers
        )
        val baos = ByteArrayOutputStream()
        PureKotlinPngEncoder.encodeRgba(rendered.pixels, rendered.width, rendered.height, baos)
        return baos.toByteArray()
    }
}
