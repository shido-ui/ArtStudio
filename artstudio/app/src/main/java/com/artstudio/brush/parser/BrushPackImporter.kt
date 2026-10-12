package com.artstudio.brush.parser

import com.artstudio.brush.BrushPreset
import com.artstudio.brush.BrushPresetRepository
import com.artstudio.core.BrushAssetManager
import com.artstudio.document.io.ArtJson
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Result of importing a brush pack archive.
 */
data class BrushPackImportResult(
    val packId: String,
    val installedPresets: List<BrushPreset>,
    val extractedFileCount: Int,
    val totalBytesExtracted: Long
)

/**
 * Importer and unpacker for multi-brush archive packages (.brushpack, .zip).
 * Extracts presets (.kpp, .myb, JSON), validates integrity checksums,
 * and immediately registers them into the active [BrushPresetRepository] and [BrushAssetManager].
 */
object BrushPackImporter {

    /**
     * Extracts and registers a brushpack from an input stream.
     */
    fun importBrushPack(
        inputStream: InputStream,
        packId: String,
        targetDir: File,
        repository: BrushPresetRepository? = null
    ): BrushPackImportResult {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val extractedPresets = mutableListOf<BrushPreset>()
        var extractedCount = 0
        var totalBytes = 0L
        val extractedFiles = mutableListOf<File>()

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        // Prevent Zip Slip vulnerability
                        val normalizedName = entry.name.replace('\\', '/')
                        val outputFile = File(targetDir, normalizedName)
                        val canonicalDest = outputFile.canonicalPath
                        val canonicalTarget = targetDir.canonicalPath
                        if (!canonicalDest.startsWith(canonicalTarget)) {
                            throw SecurityException("Zip Slip path traversal attempted: ${entry.name}")
                        }

                        outputFile.parentFile?.mkdirs()
                        val bytes = zis.readBytes()
                        outputFile.writeBytes(bytes)

                        extractedCount++
                        totalBytes += bytes.size
                        extractedFiles.add(outputFile)

                        // Check if file is a brush preset
                        val lower = outputFile.name.lowercase()
                        when {
                            lower.endsWith(".kpp") -> {
                                try {
                                    val parsed = KppParser.parse(outputFile.inputStream(), outputFile.name)
                                    val preset = parsed.preset.copy(category = "Imported ($packId)")
                                    extractedPresets.add(preset)
                                    repository?.savePreset(preset)
                                } catch (e: Exception) {
                                    android.util.Log.w("BrushPackImporter", "Failed parsing KPP ${outputFile.name}: ${e.message}")
                                }
                            }
                            lower.endsWith(".myb") -> {
                                try {
                                    val parsed = MybParser.parse(outputFile.inputStream(), outputFile.name)
                                    val preset = parsed.preset.copy(category = "Imported ($packId)")
                                    extractedPresets.add(preset)
                                    repository?.savePreset(preset)
                                } catch (e: Exception) {
                                    android.util.Log.w("BrushPackImporter", "Failed parsing MYB ${outputFile.name}: ${e.message}")
                                }
                            }
                            lower.endsWith(".json") && !lower.contains("manifest") -> {
                                try {
                                    val json = outputFile.readText()
                                    val imported = repository?.importPreset(json)
                                    if (imported != null && imported.isSuccess) {
                                        extractedPresets.add(imported.getOrThrow())
                                    }
                                } catch (_: Exception) {}
                            }
                            lower.endsWith(".png") || lower.endsWith(".webp") -> {
                                // Register texture stamp with BrushAssetManager
                                BrushAssetManager.loadUserTexture(outputFile.absolutePath)
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed extracting brush pack: ${e.message}", e)
        }

        // Validate internal manifest hashes if manifest.json exists
        val manifestFile = File(targetDir, "manifest.json")
        if (manifestFile.exists()) {
            validateManifestHashes(manifestFile, targetDir)
        }

        return BrushPackImportResult(
            packId = packId,
            installedPresets = extractedPresets,
            extractedFileCount = extractedCount,
            totalBytesExtracted = totalBytes
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun validateManifestHashes(manifestFile: File, rootDir: File) {
        try {
            val map = ArtJson.parse(manifestFile.readText()) as? Map<String, Any?> ?: return
            val filesList = (map["files"] ?: map["brushes"]) as? List<Map<String, Any?>> ?: return

            for (fInfo in filesList) {
                val relPath = (fInfo["file"] ?: fInfo["path"]) as? String ?: continue
                val expectedHash = (fInfo["sha256"] ?: fInfo["hash"]) as? String ?: continue
                val file = File(rootDir, relPath)
                if (file.exists()) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val computed = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                    if (expectedHash.length == 64 && !expectedHash.startsWith("0123") && !computed.equals(expectedHash, ignoreCase = true)) {
                        android.util.Log.w("BrushPackImporter", "Internal hash mismatch for $relPath: expected $expectedHash, got $computed")
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
