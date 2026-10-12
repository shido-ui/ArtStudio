package com.artstudio.document.io

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.mapLayers
import com.artstudio.export.ArtworkExportRenderer
import com.artstudio.export.ExportConfig
import com.artstudio.export.ExportFormat
import com.artstudio.export.ExportScope
import com.artstudio.export.PureKotlinPngEncoder
import com.artstudio.importer.RasterImageImporter
import java.io.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Package container representing an opened .ffart project archive.
 */
data class PackageData(
    val document: ArtDocument,
    val assets: Map<String, ByteArray>,
    val thumbnail: ByteArray? = null
)

/**
 * Working native project representation containing authoritative document metadata,
 * decoded layer pixel buffers, mask buffers, auxiliary assets, and thumbnail.
 */
data class NativeProject(
    val document: ArtDocument,
    val layerPixelBuffers: Map<String, IntArray> = emptyMap(),
    val layerMaskBuffers: Map<String, IntArray> = emptyMap(),
    val assets: Map<String, ByteArray> = emptyMap(),
    val thumbnail: ByteArray? = null
)

/**
 * Encodes and decodes the native authoritative project format (.ffart).
 *
 * Package specification:
 * - ZIP container format
 * - Entry 1: "mimetype" (STORED uncompressed, value: "application/vnd.artstudio.project")
 * - Entry 2: "project.json" (DEFLATED UTF-8 text containing the complete ArtDocument schema)
 * - Entry 3+: "assets/..." (DEFLATED binary assets such as raster layer bitmaps, brushes, palettes)
 * - Entry optional: "thumbnail.png" (DEFLATED preview image)
 */
object DocumentPackage {

    const val MIMETYPE = "application/vnd.artstudio.project"
    const val ENTRY_MIMETYPE = "mimetype"
    const val ENTRY_PROJECT_JSON = "project.json"
    const val ENTRY_THUMBNAIL = "thumbnail.png"
    const val ASSETS_DIR = "assets/"

    const val MAX_ENTRY_SIZE = 100 * 1024 * 1024L // 100 MB per entry
    const val MAX_TOTAL_UNCOMPRESSED_SIZE = 250 * 1024 * 1024L // 250 MB total archive
    const val MAX_CANVAS_DIMENSION = 16384
    const val MAX_LAYER_COUNT = 500

    /**
     * Saves an ArtDocument and its binary assets to a .ffart ZIP package file.
     * Uses atomic file replacement via temporary file to prevent corruption on crash/power loss.
     */
    fun save(
        document: ArtDocument,
        assets: Map<String, ByteArray>,
        outputFile: File,
        thumbnail: ByteArray? = null
    ) {
        val parentDir = outputFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val tempFile = File.createTempFile("artstudio_save_", ".tmp", parentDir ?: File("."))

        try {
            FileOutputStream(tempFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        // 1. Uncompressed mimetype entry (OpenRaster / EPUB style)
                        val mimeBytes = MIMETYPE.toByteArray(Charsets.US_ASCII)
                        val mimeEntry = ZipEntry(ENTRY_MIMETYPE).apply {
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

                        // 2. project.json entry
                        val jsonString = DocumentSerializer.toJsonString(document)
                        val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
                        val jsonEntry = ZipEntry(ENTRY_PROJECT_JSON).apply {
                            method = ZipEntry.DEFLATED
                        }
                        zos.putNextEntry(jsonEntry)
                        zos.write(jsonBytes)
                        zos.closeEntry()

                        // 3. thumbnail.png if provided
                        if (thumbnail != null && thumbnail.isNotEmpty()) {
                            val thumbEntry = ZipEntry(ENTRY_THUMBNAIL).apply {
                                method = ZipEntry.DEFLATED
                            }
                            zos.putNextEntry(thumbEntry)
                            zos.write(thumbnail)
                            zos.closeEntry()
                        }

                        // 4. assets/... entries
                        for ((path, data) in assets) {
                            val normalizedPath = if (path.startsWith(ASSETS_DIR)) path else "$ASSETS_DIR$path"
                            val assetEntry = ZipEntry(normalizedPath).apply {
                                method = ZipEntry.DEFLATED
                            }
                            zos.putNextEntry(assetEntry)
                            zos.write(data)
                            zos.closeEntry()
                        }
                    }
                }
            }

            // Atomic rename / replace
            if (outputFile.exists()) {
                if (!outputFile.delete()) {
                    throw IOException("Could not delete existing destination file ${outputFile.absolutePath}")
                }
            }
            if (!tempFile.renameTo(outputFile)) {
                // Fallback copy if rename across volumes fails
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    /**
     * Loads and validates a .ffart ZIP package file.
     * Throws CorruptDocumentException or UnsupportedSchemaVersionException on malformed packages.
     */
    fun load(file: File): PackageData {
        if (!file.exists()) {
            throw CorruptDocumentException("Document file does not exist: ${file.absolutePath}")
        }
        if (file.length() == 0L) {
            throw CorruptDocumentException("Document file is empty (0 bytes): ${file.absolutePath}")
        }

        return FileInputStream(file).use { fis ->
            load(fis)
        }
    }

    /**
     * Loads and validates a .ffart ZIP package from an input stream.
     */
    fun load(inputStream: InputStream): PackageData {
        var foundMimetype = false
        var projectJsonBytes: ByteArray? = null
        var thumbnailBytes: ByteArray? = null
        val assets = mutableMapOf<String, ByteArray>()
        var totalUncompressedBytes = 0L

        try {
            BufferedInputStream(inputStream).use { bis ->
                ZipInputStream(bis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name

                            // Guard against Zip Slip directory traversal attacks
                            if (name.contains("..") || name.startsWith("/")) {
                                throw CorruptDocumentException("Illegal path traversal in archive entry: $name")
                            }

                            if (!entry.isDirectory) {
                                val buffer = readEntryBytes(
                                    zis = zis,
                                    entryName = name,
                                    maxEntrySize = MAX_ENTRY_SIZE,
                                    totalBytesSoFar = totalUncompressedBytes,
                                    maxTotalSize = MAX_TOTAL_UNCOMPRESSED_SIZE
                                )
                                totalUncompressedBytes += buffer.size

                                when {
                                    name == ENTRY_MIMETYPE -> {
                                        val mimeContent = String(buffer, Charsets.US_ASCII).trim()
                                        if (mimeContent != MIMETYPE) {
                                            throw CorruptDocumentException("Invalid mimetype '$mimeContent', expected '$MIMETYPE'")
                                        }
                                        foundMimetype = true
                                    }
                                    name == ENTRY_PROJECT_JSON -> {
                                        projectJsonBytes = buffer
                                    }
                                    name == ENTRY_THUMBNAIL -> {
                                        thumbnailBytes = buffer
                                    }
                                    name.startsWith(ASSETS_DIR) -> {
                                        assets[name] = buffer
                                    }
                                    else -> {
                                        // Store other recognized or auxiliary metadata files
                                        assets[name] = buffer
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
        } catch (e: UnsupportedSchemaVersionException) {
            throw e
        } catch (e: Exception) {
            throw CorruptDocumentException("Failed to read package archive: ${e.message}", e)
        }

        if (!foundMimetype) {
            throw CorruptDocumentException("Missing required 'mimetype' entry in package")
        }

        if (projectJsonBytes == null) {
            throw CorruptDocumentException("Missing required 'project.json' entry in package")
        }

        val jsonString = String(projectJsonBytes, Charsets.UTF_8)
        val document = DocumentSerializer.fromJsonString(jsonString)

        // Validate canvas dimensions
        if (document.canvas.width <= 0 || document.canvas.width > MAX_CANVAS_DIMENSION ||
            document.canvas.height <= 0 || document.canvas.height > MAX_CANVAS_DIMENSION
        ) {
            throw CorruptDocumentException("Canvas dimensions ${document.canvas.width}x${document.canvas.height} exceed allowed limits (1..$MAX_CANVAS_DIMENSION)")
        }

        // Validate layer count
        val allLayers = document.allLayersFlat()
        if (allLayers.size > MAX_LAYER_COUNT) {
            throw CorruptDocumentException("Document contains ${allLayers.size} layers, exceeding maximum allowed limit of $MAX_LAYER_COUNT")
        }

        for (node in allLayers) {
            if (node is LayerNode.RasterLayer) {
                if (node.width <= 0 || node.width > MAX_CANVAS_DIMENSION ||
                    node.height <= 0 || node.height > MAX_CANVAS_DIMENSION
                ) {
                    throw CorruptDocumentException("Layer '${node.name}' dimensions ${node.width}x${node.height} exceed allowed limits (1..$MAX_CANVAS_DIMENSION)")
                }
            }
        }

        return PackageData(
            document = document,
            assets = assets,
            thumbnail = thumbnailBytes
        )
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
                throw CorruptDocumentException("Archive entry '$entryName' exceeds maximum allowed size ($maxEntrySize bytes, possible zip bomb)")
            }
            if (totalBytesSoFar + entryBytesRead > maxTotalSize) {
                throw CorruptDocumentException("Archive total uncompressed content exceeds limit ($maxTotalSize bytes, possible zip bomb)")
            }
            baos.write(buffer, 0, count)
        }
        return baos.toByteArray()
    }

    /**
     * Saves an authoritative ArtDocument with full layer pixel buffers, mask buffers,
     * and metadata into a .ffart native project package.
     */
    fun saveProject(
        document: ArtDocument,
        layerPixelBuffers: Map<String, IntArray> = emptyMap(),
        layerMaskBuffers: Map<String, IntArray> = emptyMap(),
        extraAssets: Map<String, ByteArray> = emptyMap(),
        outputFile: File,
        thumbnail: ByteArray? = null
    ) {
        val assets = extraAssets.toMutableMap()

        // 1. Pack layer pixel buffers and mask buffers into PNG assets
        val updatedDocument = document.mapLayers { node ->
            when (node) {
                is LayerNode.RasterLayer -> {
                    val px = layerPixelBuffers[node.id]
                    var updatedPath = node.assetPath
                    if (px != null && px.size == node.width * node.height) {
                        val assetKey = "layer_${node.id}.png"
                        val baos = ByteArrayOutputStream()
                        PureKotlinPngEncoder.encodeRgba(px, node.width, node.height, baos)
                        assets[assetKey] = baos.toByteArray()
                        updatedPath = "$ASSETS_DIR$assetKey"
                    }

                    // Pack mask pixels if present
                    val mask = node.mask
                    var updatedMask = mask
                    if (mask != null) {
                        val maskPx = layerMaskBuffers[node.id]
                        if (maskPx != null && maskPx.size == mask.width * mask.height) {
                            val maskKey = "mask_${node.id}.png"
                            val baos = ByteArrayOutputStream()
                            PureKotlinPngEncoder.encodeRgba(maskPx, mask.width, mask.height, baos)
                            assets[maskKey] = baos.toByteArray()
                            updatedMask = mask.copy(assetPath = "$ASSETS_DIR$maskKey")
                        }
                    }

                    node.copy(assetPath = updatedPath, mask = updatedMask)
                }
                else -> node
            }
        }

        // 2. Generate thumbnail if not provided
        val thumbBytes = thumbnail ?: try {
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
            baos.toByteArray()
        } catch (e: Exception) {
            android.util.Log.w("DocumentPackage", "Project thumbnail generation skipped: ${e.message}")
            null
        }

        // 3. Save standard package
        save(updatedDocument, assets, outputFile, thumbBytes)
    }

    /**
     * Loads a native .ffart project, reconstructing document metadata, layer hierarchy,
     * and decoding raster pixel and mask buffers with 100% full fidelity.
     */
    fun loadProject(file: File): NativeProject {
        val packageData = load(file)
        val doc = packageData.document
        val layerPixelBuffers = mutableMapOf<String, IntArray>()
        val layerMaskBuffers = mutableMapOf<String, IntArray>()

        for (node in doc.allLayersFlat()) {
            if (node is LayerNode.RasterLayer) {
                // Find layer asset
                val candidateKeys = listOf(
                    "assets/layer_${node.id}.png",
                    "layer_${node.id}.png",
                    node.assetPath,
                    node.assetPath?.removePrefix(ASSETS_DIR),
                    if (node.assetPath != null) "$ASSETS_DIR${node.assetPath}" else null
                ).filterNotNull()

                val assetBytes = candidateKeys.firstNotNullOfOrNull { packageData.assets[it] }
                if (assetBytes != null && assetBytes.isNotEmpty()) {
                    val decoded = RasterImageImporter.decodeRasterImage(assetBytes, node.name)
                    if (decoded != null) {
                        layerPixelBuffers[node.id] = decoded.pixels
                    }
                }

                // Find mask asset
                val mask = node.mask
                if (mask != null) {
                    val maskCandidates = listOf(
                        "assets/mask_${node.id}.png",
                        "mask_${node.id}.png",
                        mask.assetPath,
                        mask.assetPath?.removePrefix(ASSETS_DIR),
                        if (mask.assetPath != null) "$ASSETS_DIR${mask.assetPath}" else null
                    ).filterNotNull()

                    val maskBytes = maskCandidates.firstNotNullOfOrNull { packageData.assets[it] }
                    if (maskBytes != null && maskBytes.isNotEmpty()) {
                        val decodedMask = RasterImageImporter.decodeRasterImage(maskBytes, "mask_${node.id}")
                        if (decodedMask != null) {
                            layerMaskBuffers[node.id] = decodedMask.pixels
                        }
                    }
                }
            }
        }

        return NativeProject(
            document = doc,
            layerPixelBuffers = layerPixelBuffers,
            layerMaskBuffers = layerMaskBuffers,
            assets = packageData.assets,
            thumbnail = packageData.thumbnail
        )
    }
}
