package com.artstudio.texture

import com.artstudio.core.BrushAssetManager
import com.artstudio.core.BrushTexture
import com.artstudio.document.io.ArtJson
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Authoritative repository managing built-in and user-supplied texture and stamp assets.
 * Enforces:
 * - Immutable protection of built-in system assets (cannot be renamed or deleted).
 * - Full auditability and manifest parity.
 * - Graceful fallback with diagnostic error reporting on missing or corrupt assets.
 * - Lazy preview generation via [TexturePreviewCache].
 */
class TextureRepository(
    val userStorageDir: File,
    val builtInAssetsDir: File? = null,
    private val assetStreamOpener: ((String) -> InputStream?)? = null
) {
    val previewCache = TexturePreviewCache()

    private val catalog = LinkedHashMap<String, TextureMetadata>()
    private val userFavorites = mutableSetOf<String>()
    private var lastRecordedError: TextureLoadError? = null

    init {
        userStorageDir.mkdirs()
    }

    /**
     * Initializes repository by loading built-in manifest and user index.
     */
    fun initialize() {
        catalog.clear()

        // 1. Load built-in manifest
        loadBuiltInAssets()

        // 2. Load user index (including user assets and favorite states)
        loadUserIndex()
    }

    fun getAllAssets(): List<TextureMetadata> = catalog.values.toList()

    fun getAssetsByCategory(category: TextureCategory): List<TextureMetadata> {
        return catalog.values.filter { it.category == category }
    }

    fun getFavorites(): List<TextureMetadata> {
        return catalog.values.filter { it.isFavorite }
    }

    fun searchAssets(
        query: String,
        category: TextureCategory? = null,
        favoritesOnly: Boolean = false
    ): List<TextureMetadata> {
        val q = query.trim().lowercase()
        return catalog.values.filter { meta ->
            val matchesCategory = category == null || meta.category == category
            val matchesFav = !favoritesOnly || meta.isFavorite
            val matchesQuery = q.isEmpty() ||
                    meta.name.lowercase().contains(q) ||
                    meta.provenance.lowercase().contains(q) ||
                    meta.author.lowercase().contains(q)
            matchesCategory && matchesFav && matchesQuery
        }
    }

    fun getAssetById(id: String): TextureMetadata? = catalog[id]

    val lastError: TextureLoadError?
        get() = lastRecordedError

    fun clearLastError() {
        lastRecordedError = null
    }

    // -------------------------------------------------------------------------
    // Asset Management Operations
    // -------------------------------------------------------------------------

    /**
     * Imports a user-supplied texture file.
     */
    fun importAsset(
        name: String,
        category: TextureCategory,
        fileBytes: ByteArray,
        sourceFileName: String
    ): TextureValidationResult {
        val validation = TextureAssetValidator.validateAndDecode(
            bytes = fileBytes,
            name = name.ifBlank { sourceFileName.substringBeforeLast('.') },
            category = category,
            isBuiltIn = false,
            author = "User",
            license = "User Content",
            provenance = "Imported from $sourceFileName"
        )

        if (validation is TextureValidationResult.Success) {
            val targetFile = File(userStorageDir, "${validation.metadata.id}.png")
            targetFile.writeBytes(fileBytes)

            val finalMetadata = validation.metadata.copy(filePath = targetFile.absolutePath)
            catalog[finalMetadata.id] = finalMetadata
            saveUserIndex()
            return TextureValidationResult.Success(finalMetadata, validation.texture)
        }

        return validation
    }

    /**
     * Duplicates an existing asset (built-in or user) as a new user asset.
     */
    fun duplicateAsset(id: String, newName: String? = null): TextureValidationResult {
        val sourceMeta = catalog[id] ?: return TextureValidationResult.Error(
            code = TextureErrorCode.FILE_NOT_FOUND,
            message = "Source asset '$id' not found in catalog."
        )

        val bytes = readAssetBytes(sourceMeta) ?: return TextureValidationResult.Error(
            code = TextureErrorCode.FILE_NOT_FOUND,
            message = "Unable to read source asset bytes for '${sourceMeta.name}'."
        )

        val cloneName = newName ?: "${sourceMeta.name} (Copy)"
        val validation = TextureAssetValidator.validateAndDecode(
            bytes = bytes,
            name = cloneName,
            category = sourceMeta.category,
            isBuiltIn = false,
            author = "User",
            license = sourceMeta.license,
            provenance = "Duplicated from ${sourceMeta.name} (${sourceMeta.id})"
        )

        if (validation is TextureValidationResult.Success) {
            val targetFile = File(userStorageDir, "${validation.metadata.id}.png")
            targetFile.writeBytes(bytes)

            val finalMetadata = validation.metadata.copy(filePath = targetFile.absolutePath)
            catalog[finalMetadata.id] = finalMetadata
            saveUserIndex()
            return TextureValidationResult.Success(finalMetadata, validation.texture)
        }

        return validation
    }

    /**
     * Renames a user-supplied asset. Built-in assets cannot be renamed.
     */
    fun renameAsset(id: String, newName: String): Result<TextureMetadata> {
        require(newName.isNotBlank()) { "Texture name cannot be blank." }
        val meta = catalog[id] ?: return Result.failure(
            TextureValidationException("Asset '$id' does not exist.")
        )

        if (meta.isBuiltIn) {
            return Result.failure(
                CannotModifyBuiltInAssetException("Built-in system asset '${meta.name}' cannot be renamed.")
            )
        }

        val updated = meta.copy(name = newName.trim())
        catalog[id] = updated
        saveUserIndex()
        return Result.success(updated)
    }

    /**
     * Deletes a user-supplied asset. Built-in assets cannot be deleted.
     */
    fun deleteAsset(id: String): Result<Boolean> {
        val meta = catalog[id] ?: return Result.failure(
            TextureValidationException("Asset '$id' does not exist.")
        )

        if (meta.isBuiltIn) {
            return Result.failure(
                CannotDeleteBuiltInAssetException("Built-in system asset '${meta.name}' cannot be deleted.")
            )
        }

        meta.filePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }

        catalog.remove(id)
        userFavorites.remove(id)
        saveUserIndex()
        return Result.success(true)
    }

    /**
     * Toggles favorite status on any asset.
     */
    fun toggleFavorite(id: String): Result<Boolean> {
        val meta = catalog[id] ?: return Result.failure(
            TextureValidationException("Asset '$id' does not exist.")
        )

        val newFav = !meta.isFavorite
        catalog[id] = meta.copy(isFavorite = newFav)
        if (newFav) userFavorites.add(id) else userFavorites.remove(id)
        saveUserIndex()
        return Result.success(newFav)
    }

    // -------------------------------------------------------------------------
    // Texture Loading with Graceful Fallback & Explicit Error Reporting
    // -------------------------------------------------------------------------

    /**
     * Loads a texture by asset ID.
     * Guarantees zero silent failures: if the asset is missing, corrupted, or oversized,
     * it records an explicit [TextureLoadError] and returns [TextureLoadResult.FallbackWithError].
     */
    fun loadTexture(id: String): TextureLoadResult {
        val meta = catalog[id]
        if (meta == null) {
            val error = TextureLoadError(
                assetId = id,
                assetName = "Unknown",
                errorCode = TextureErrorCode.FILE_NOT_FOUND,
                userMessage = "Texture asset '$id' was not found in catalog."
            )
            lastRecordedError = error
            return TextureLoadResult.FallbackWithError(error, getDiagnosticFallback(id))
        }

        val bytes = readAssetBytes(meta)
        if (bytes == null || bytes.isEmpty()) {
            val error = TextureLoadError(
                assetId = id,
                assetName = meta.name,
                errorCode = TextureErrorCode.FILE_NOT_FOUND,
                userMessage = "Texture file for '${meta.name}' is missing from storage."
            )
            lastRecordedError = error
            return TextureLoadResult.FallbackWithError(error, getDiagnosticFallback(id))
        }

        val validation = TextureAssetValidator.validateAndDecode(
            bytes = bytes,
            name = meta.name,
            category = meta.category,
            filePath = meta.filePath,
            assetPath = meta.assetPath,
            isBuiltIn = meta.isBuiltIn
        )

        return when (validation) {
            is TextureValidationResult.Success -> {
                TextureLoadResult.Success(validation.texture)
            }
            is TextureValidationResult.Error -> {
                val error = TextureLoadError(
                    assetId = id,
                    assetName = meta.name,
                    errorCode = validation.code,
                    userMessage = "Failed to load '${meta.name}': ${validation.message}"
                )
                lastRecordedError = error
                TextureLoadResult.FallbackWithError(error, getDiagnosticFallback(id))
            }
        }
    }

    /**
     * Retrieves or generates a lazy-loaded preview thumbnail for UI display.
     */
    fun getPreview(metadata: TextureMetadata): BrushTexture {
        return previewCache.getPreview(metadata) {
            val loadResult = loadTexture(metadata.id)
            when (loadResult) {
                is TextureLoadResult.Success -> loadResult.texture
                is TextureLoadResult.FallbackWithError -> loadResult.fallbackTexture
            }
        }
    }

    fun getBuiltInAssetFootprintBytes(): Long {
        return catalog.values.filter { it.isBuiltIn }.sumOf { it.fileSizeBytes }
    }

    // -------------------------------------------------------------------------
    // Diagnostic Fallback (Zero Invisible Substitutions)
    // -------------------------------------------------------------------------

    private fun getDiagnosticFallback(seedStr: String): BrushTexture {
        val seed = seedStr.hashCode().toLong()
        // Returns a clearly visible procedural texture with diagnostic tooth
        return BrushAssetManager.generatePaperTooth(seed, 64, 64)
    }

    // -------------------------------------------------------------------------
    // Persistence & Manifest Ingestion
    // -------------------------------------------------------------------------

    private fun readAssetBytes(meta: TextureMetadata): ByteArray? {
        // User asset file path
        if (meta.filePath != null) {
            val file = File(meta.filePath)
            if (file.exists() && file.isFile) return file.readBytes()
        }

        // Built-in assets dir
        if (meta.assetPath != null && builtInAssetsDir != null) {
            val subPath = meta.assetPath.removePrefix("brushes/textures/")
            val file = File(builtInAssetsDir, subPath)
            if (file.exists() && file.isFile) return file.readBytes()
        }

        // Android AssetManager stream opener
        if (meta.assetPath != null && assetStreamOpener != null) {
            return try {
                assetStreamOpener.invoke(meta.assetPath)?.use { it.readBytes() }
            } catch (_: Throwable) {
                null
            }
        }

        return null
    }

    private fun loadBuiltInAssets() {
        // Try reading manifest.json
        var manifestText: String? = null

        if (builtInAssetsDir != null) {
            val mf = File(builtInAssetsDir, "manifest.json")
            if (mf.exists()) manifestText = mf.readText()
        }

        if (manifestText == null && assetStreamOpener != null) {
            try {
                assetStreamOpener.invoke("brushes/textures/manifest.json")?.use {
                    manifestText = it.reader().readText()
                }
            } catch (t: Throwable) {
                android.util.Log.w("TextureRepository", "Failed opening built-in texture manifest: ${t.message}")
            }
        }

        if (manifestText != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val manifestMap = ArtJson.parse(manifestText!!) as? Map<String, Any?>
                val assetsList = manifestMap?.get("assets") as? List<Map<String, Any?>>
                if (assetsList != null) {
                    for (entry in assetsList) {
                        val meta = TextureMetadata(
                            id = entry["id"] as? String ?: continue,
                            name = entry["name"] as? String ?: "Built-in",
                            category = TextureCategory.fromId(entry["category"] as? String ?: "grain"),
                            isBuiltIn = true,
                            isFavorite = false,
                            assetPath = entry["asset_path"] as? String,
                            width = (entry["width"] as? Number)?.toInt() ?: 64,
                            height = (entry["height"] as? Number)?.toInt() ?: 64,
                            fileSizeBytes = (entry["size_bytes"] as? Number)?.toLong() ?: 0L,
                            sha256 = entry["sha256"] as? String ?: "",
                            author = entry["author"] as? String ?: "Art Studio Core Team",
                            license = entry["license"] as? String ?: "CC0-1.0",
                            provenance = entry["provenance"] as? String ?: "Synthetic procedural asset",
                            createdAt = 0L
                        )
                        catalog[meta.id] = meta
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.w("TextureRepository", "Failed parsing texture manifest: ${t.message}")
            }
        }

        // Try reading textures/bundled/manifest.json as well
        if (assetStreamOpener != null) {
            try {
                assetStreamOpener.invoke("textures/bundled/manifest.json")?.use { stream ->
                    val text = stream.reader().readText()
                    @Suppress("UNCHECKED_CAST")
                    val map = ArtJson.parse(text) as? Map<String, Any?>
                    val texList = map?.get("textures") as? List<Map<String, Any?>>
                    if (texList != null) {
                        for (entry in texList) {
                            val id = entry["id"] as? String ?: continue
                            val meta = TextureMetadata(
                                id = id,
                                name = entry["name"] as? String ?: id,
                                category = TextureCategory.fromId(entry["category"] as? String ?: "grain"),
                                isBuiltIn = true,
                                isFavorite = false,
                                assetPath = "textures/bundled/${entry["file"] as? String ?: ""}",
                                width = (entry["width"] as? Number)?.toInt() ?: 64,
                                height = (entry["height"] as? Number)?.toInt() ?: 64,
                                fileSizeBytes = (entry["file_size"] as? Number)?.toLong() ?: 0L,
                                sha256 = entry["sha256"] as? String ?: "",
                                author = entry["author"] as? String ?: "Art Studio Team",
                                license = entry["license"] as? String ?: "CC0-1.0",
                                provenance = "Bundled Procedural Texture",
                                createdAt = 0L
                            )
                            catalog[meta.id] = meta
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // Hardcoded authoritative fallback list for 7 canonical assets
        for (meta in BUILT_IN_CANONICAL_ASSETS) {
            catalog[meta.id] = meta
        }
    }

    private fun loadUserIndex() {
        val indexFile = File(userStorageDir, "textures_index.json")
        if (!indexFile.exists()) return

        try {
            @Suppress("UNCHECKED_CAST")
            val map = ArtJson.parse(indexFile.readText()) as? Map<String, Any?> ?: return
            val favs = map["favorites"] as? List<String>
            if (favs != null) {
                userFavorites.addAll(favs)
                for (favId in userFavorites) {
                    catalog[favId]?.let { catalog[favId] = it.copy(isFavorite = true) }
                }
            }

            val userAssets = map["user_assets"] as? List<Map<String, Any?>>
            if (userAssets != null) {
                for (entry in userAssets) {
                    val meta = TextureMetadata.fromMap(entry)
                    val isFav = userFavorites.contains(meta.id)
                    catalog[meta.id] = meta.copy(isFavorite = isFav)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("TextureRepository", "Failed loading user texture index: ${t.message}")
        }
    }

    private fun saveUserIndex() {
        val indexFile = File(userStorageDir, "textures_index.json")
        val userAssets = catalog.values.filter { !it.isBuiltIn }.map { it.toMap() }
        val data = mapOf(
            "favorites" to userFavorites.toList(),
            "user_assets" to userAssets
        )
        indexFile.writeText(ArtJson.stringify(data))
    }

    /**
     * Scans and registers textures extracted from a downloaded pack into the live catalog.
     */
    fun registerDownloadedPack(packId: String, packDir: File): List<TextureMetadata> {
        val registered = mutableListOf<TextureMetadata>()
        if (!packDir.exists()) return registered

        val imageFiles = packDir.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".png", ignoreCase = true) || it.name.endsWith(".jpg", ignoreCase = true) || it.name.endsWith(".webp", ignoreCase = true)) }
            .toList()

        for (file in imageFiles) {
            val baseName = file.nameWithoutExtension
            val id = "dl_${packId}_${baseName}"
            val category = when {
                baseName.contains("canvas", ignoreCase = true) || baseName.contains("linen", ignoreCase = true) -> TextureCategory.PATTERN
                baseName.contains("paper", ignoreCase = true) -> TextureCategory.GRAIN
                baseName.contains("stamp", ignoreCase = true) -> TextureCategory.STAMP
                else -> TextureCategory.GRAIN
            }
            val meta = TextureMetadata(
                id = id,
                name = baseName.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() },
                category = category,
                isBuiltIn = false,
                isFavorite = false,
                filePath = file.absolutePath,
                width = 512,
                height = 512,
                fileSizeBytes = file.length(),
                sha256 = "",
                author = "CC0 Community",
                license = "CC0-1.0",
                provenance = "Downloaded Pack ($packId)",
                createdAt = System.currentTimeMillis()
            )
            catalog[meta.id] = meta
            registered.add(meta)
        }
        return registered
    }

    /**
     * Purges textures registered from a downloaded pack.
     */
    fun removeDownloadedPack(packId: String): Int {
        val toRemove = catalog.values.filter { it.provenance.contains(packId) || it.id.startsWith("dl_${packId}") }.map { it.id }
        for (id in toRemove) {
            catalog.remove(id)
        }
        return toRemove.size
    }

    companion object {
        val BUILT_IN_CANONICAL_ASSETS = listOf(
            TextureMetadata(
                id = "builtin_paper_tooth_64",
                name = "Cold-Press Paper Tooth",
                category = TextureCategory.GRAIN,
                isBuiltIn = true,
                assetPath = "brushes/textures/paper_tooth_64.png",
                width = 64,
                height = 64,
                fileSizeBytes = 4364L,
                sha256 = "73f82b517bd1ed9571e723f7acb194b0415b19f95428bab28c2022f00b822657",
                author = "Art Studio Core Team",
                license = "CC0-1.0",
                provenance = "Synthetically generated multi-octave gradient value noise simulating cold-press watercolor paper tooth."
            ),
            TextureMetadata(
                id = "builtin_charcoal_grain_64",
                name = "Charcoal Tooth Grain",
                category = TextureCategory.GRAIN,
                isBuiltIn = true,
                assetPath = "brushes/textures/charcoal_grain_64.png",
                width = 64,
                height = 64,
                fileSizeBytes = 4136L,
                sha256 = "405bbc565fe3a439f187ce237cc7ce1ae187e6b271c5ac399cf7441a3d44fe4c",
                author = "Art Studio Core Team",
                license = "CC0-1.0",
                provenance = "Synthetically generated high-frequency stochastic surface tooth for dry charcoal and pastel skipping."
            ),
            TextureMetadata(
                id = "builtin_canvas_grain_64",
                name = "Woven Textile Canvas",
                category = TextureCategory.PATTERN,
                isBuiltIn = true,
                assetPath = "brushes/textures/canvas_grain_64.png",
                width = 64,
                height = 64,
                fileSizeBytes = 197L,
                sha256 = "a56ce343f02ba51727cd84cb871d72c205eb174de575381eca1a193c6ef35003",
                author = "Art Studio Core Team",
                license = "CC0-1.0",
                provenance = "Synthetically generated orthogonal warp-and-weft textile weave simulation with subtle thread noise."
            ),
            TextureMetadata(
                id = "builtin_halftone_dot_64",
                name = "Geometric Halftone Dots",
                category = TextureCategory.PATTERN,
                isBuiltIn = true,
                assetPath = "brushes/textures/halftone_dot_64.png",
                width = 64,
                height = 64,
                fileSizeBytes = 195L,
                sha256 = "f8564eaedef214719efa782aa021d1a3253d05462f957f6819de1e55b6d3781e",
                author = "Art Studio Core Team",
                license = "CC0-1.0",
                provenance = "Synthetically calculated Euclidean circular quadratic falloff grid for comic manga screen tones."
            ),
            TextureMetadata(
                id = "builtin_sponge_stamp_64",
                name = "Organic Sea Sponge",
                category = TextureCategory.STAMP,
                isBuiltIn = true,
                assetPath = "brushes/textures/sponge_stamp_64.png",
                width = 64,
                height = 64,
                fileSizeBytes = 4616L,
                sha256 = "6a71118d8dc6f9c4b2813cc3d332d12197397663542dbf3c8794de0a7a1d3cd7",
                author = "Art Studio Core Team",
                license = "CC0-1.0",
                provenance = "Synthetically generated cellular porous structure with radial edge attenuation simulating natural sea sponge."
            ),
            TextureMetadata(
                id = "builtin_ink_blot_64",
                name = "Splattered Ink Blot",
                category = TextureCategory.STAMP,
                isBuiltIn = true,
                assetPath = "brushes/textures/ink_blot_64.png",
                width = 64,
                height = 64,
                fileSizeBytes = 604L,
                sha256 = "e419de5368aa5ef9e73b5f6ab1667302fc6a1e21abe6122b3976849f90bae96e",
                author = "Art Studio Core Team",
                license = "CC0-1.0",
                provenance = "Synthetically computed perturbed harmonic perimeter with radial satellite droplet splatters."
            ),
            TextureMetadata(
                id = "builtin_dry_bristle_64",
                name = "Dry Brush Bristle Rake",
                category = TextureCategory.TEXTURED_STROKE,
                isBuiltIn = true,
                assetPath = "brushes/textures/dry_bristle_64.png",
                width = 64,
                height = 64,
                fileSizeBytes = 4146L,
                sha256 = "760b4aec595173b0f6742fb7e4b761fd0206374560a7262b5b9aded37cf0bdec",
                author = "Art Studio Core Team",
                license = "CC0-1.0",
                provenance = "Synthetically generated multi-filament bristle striation array with longitudinal modulation."
            )
        )
    }
}
