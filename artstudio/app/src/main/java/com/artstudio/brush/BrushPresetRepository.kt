package com.artstudio.brush

import com.artstudio.core.*
import com.artstudio.document.io.ArtJson
import java.io.File
import java.util.UUID

/**
 * Repository managing persistent brush presets, categories, favorites, search,
 * lifecycle operations (duplicate, rename, delete), and strictly validated import/export.
 */
class BrushPresetRepository(
    private val storageDir: File? = null
) {
    private val presetsMap = LinkedHashMap<String, BrushPreset>()

    init {
        loadDefaults()
        loadPersisted()
    }

    private fun loadDefaults() {
        val defaultList = listOf(
            BrushPreset(
                id = "preset_classic_pencil",
                name = "Classic Pencil (HB)",
                category = "Sketching",
                isFavorite = true,
                isBuiltIn = true,
                tags = listOf("pencil", "sketch", "graphite"),
                config = BrushPresets.pencil()
            ),
            BrushPreset(
                id = "preset_soft_graphite",
                name = "Soft Graphite (4B)",
                category = "Sketching",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("pencil", "shading"),
                config = BrushPresets.pencil(size = 14f).copy(
                    hardness = 0.45f,
                    pressureResponse = PressureResponseMode.SOFT,
                    startTaperRatio = 0.15f,
                    endTaperRatio = 0.15f
                )
            ),
            BrushPreset(
                id = "preset_studio_pen",
                name = "Studio Hard Pen",
                category = "Inking",
                isFavorite = true,
                isBuiltIn = true,
                tags = listOf("pen", "lineart", "ink"),
                config = BrushPresets.hardPen()
            ),
            BrushPreset(
                id = "preset_smooth_feather_pen",
                name = "Smooth Feather Pen",
                category = "Inking",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("pen", "feather"),
                config = BrushPresets.softPen()
            ),
            BrushPreset(
                id = "preset_asian_calligraphy",
                name = "Asian Calligraphy Brush",
                category = "Inking",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("calligraphy", "ribbon"),
                config = BrushPresets.inkBrush(size = 18f).copy(
                    followStrokeAngle = true,
                    stabilizationWeight = 0.35f
                )
            ),
            BrushPreset(
                id = "preset_bristle_paintbrush",
                name = "Bristle Paintbrush",
                category = "Painting",
                isFavorite = true,
                isBuiltIn = true,
                tags = listOf("paint", "bristles"),
                config = BrushPresets.basicPaintbrush()
            ),
            BrushPreset(
                id = "preset_chisel_marker",
                name = "Chisel Flat Marker",
                category = "Painting",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("marker", "chisel"),
                config = BrushConfig(
                    type = BrushType.HARD_PEN,
                    size = 26f,
                    opacity = 0.85f,
                    color = 0xFF3F51B5.toInt(),
                    tipShape = BrushTipShape.CHISEL,
                    tipAngleDegrees = 30f,
                    spacing = 0.08f
                )
            ),
            BrushPreset(
                id = "preset_soft_airbrush",
                name = "Soft Airbrush",
                category = "Airbrush",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("spray", "soft"),
                config = BrushPresets.airbrush()
            ),
            BrushPreset(
                id = "preset_precision_eraser",
                name = "Hard Block Eraser",
                category = "Erasers",
                isFavorite = true,
                isBuiltIn = true,
                tags = listOf("eraser", "clean"),
                config = BrushPresets.eraser(hardness = 1.0f)
            ),
            BrushPreset(
                id = "preset_kneaded_eraser",
                name = "Soft Kneaded Eraser",
                category = "Erasers",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("eraser", "soft"),
                config = BrushPresets.eraser(hardness = 0.40f).copy(
                    startTaperRatio = 0.12f,
                    endTaperRatio = 0.12f
                )
            ),
            BrushPreset(
                id = "preset_media_marker",
                name = "Art Chisel Marker",
                category = "Paint Media",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("marker", "chisel", "media", "alcohol"),
                config = BrushPresets.marker()
            ),
            BrushPreset(
                id = "preset_media_watercolour",
                name = "Watercolour Wash",
                category = "Paint Media",
                isFavorite = true,
                isBuiltIn = true,
                tags = listOf("watercolour", "wash", "media", "granulation"),
                config = BrushPresets.watercolourWash()
            ),
            BrushPreset(
                id = "preset_media_oil",
                name = "Impasto Oil Paint",
                category = "Paint Media",
                isFavorite = true,
                isBuiltIn = true,
                tags = listOf("oil", "impasto", "media", "viscous"),
                config = BrushPresets.oilPaint()
            ),
            BrushPreset(
                id = "preset_media_dry_brush",
                name = "Textured Dry Brush",
                category = "Paint Media",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("drybrush", "texture", "media", "grain"),
                config = BrushPresets.dryBrush()
            ),
            BrushPreset(
                id = "preset_media_stamp",
                name = "Organic Sponge Stamp",
                category = "Paint Media",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("stamp", "sponge", "media", "texture"),
                config = BrushPresets.texturedStamp()
            ),
            BrushPreset(
                id = "preset_texture_charcoal_grain",
                name = "Charcoal Surface Grain",
                category = "Textures & Stamps",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("grain", "charcoal", "tooth", "texture"),
                config = BrushPresets.grainBrush()
            ),
            BrushPreset(
                id = "preset_texture_canvas_pattern",
                name = "Canvas Weave Pattern",
                category = "Textures & Stamps",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("pattern", "canvas", "textile", "texture"),
                config = BrushPresets.patternBrush()
            ),
            BrushPreset(
                id = "preset_texture_ink_splatter",
                name = "Splattered Ink Stamp",
                category = "Textures & Stamps",
                isFavorite = true,
                isBuiltIn = true,
                tags = listOf("stamp", "splatter", "ink", "texture"),
                config = BrushPresets.stampBrush()
            ),
            BrushPreset(
                id = "preset_texture_dry_rake",
                name = "Dry Bristle Rake",
                category = "Textures & Stamps",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("textured_stroke", "rake", "bristle", "texture"),
                config = BrushPresets.texturedStrokeBrush()
            ),
            BrushPreset(
                id = "preset_fountain_pen",
                name = "Classic Fountain Pen",
                category = "Inking",
                isFavorite = true,
                isBuiltIn = true,
                tags = listOf("ink", "pen", "calligraphy", "fountain"),
                config = BrushConfig(
                    type = BrushType.HARD_PEN,
                    size = 14f,
                    opacity = 1.0f,
                    tipShape = BrushTipShape.ELLIPSE,
                    tipAngleDegrees = 45f,
                    followStrokeAngle = true,
                    stabilizationWeight = 0.25f,
                    spacing = 0.05f,
                    mediaProperties = PaintMediaProperties(
                        family = PaintMediaFamily.MARKER,
                        chiselAspect = 2.5f
                    )
                )
            ),
            BrushPreset(
                id = "preset_bristle_ink",
                name = "Coarse Bristle Ink",
                category = "Inking",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("ink", "bristle", "dry", "texture"),
                config = BrushConfig(
                    type = BrushType.INK_BRUSH,
                    size = 24f,
                    opacity = 0.88f,
                    hardness = 0.75f,
                    spacing = 0.08f,
                    stabilizationWeight = 0.15f,
                    mediaProperties = PaintMediaProperties(
                        family = PaintMediaFamily.TEXTURED_STAMP,
                        stampShape = StampPatternShape.CANVAS_GRAIN,
                        textureScale = 1.2f
                    )
                )
            ),
            BrushPreset(
                id = "preset_spray_soft",
                name = "Soft Airbrush Spray",
                category = "Painting",
                isFavorite = false,
                isBuiltIn = true,
                tags = listOf("airbrush", "spray", "soft", "paint"),
                config = BrushConfig(
                    type = BrushType.AIRBRUSH,
                    size = 48f,
                    opacity = 0.40f,
                    hardness = 0.10f,
                    spacing = 0.05f,
                    mediaProperties = PaintMediaProperties(
                        family = PaintMediaFamily.TEXTURED_STAMP,
                        stampShape = StampPatternShape.SPONGE,
                        textureScale = 2.0f
                    )
                )
            )
        )

        for (p in defaultList) {
            presetsMap[p.id] = p
        }
    }

    private fun loadPersisted() {
        val dir = storageDir ?: return
        val file = File(dir, "brush_presets.json")
        if (!file.exists()) return

        try {
            val json = file.readText()
            @Suppress("UNCHECKED_CAST")
            val list = ArtJson.parse(json) as? List<Map<String, Any?>> ?: return
            for (map in list) {
                try {
                    val preset = BrushPreset.fromMap(map)
                    presetsMap[preset.id] = preset
                } catch (_: Throwable) {
                    // Skip corrupt entry on disk without blowing up repository
                }
            }
        } catch (_: Throwable) {
            // Safe fallback to defaults if persisted index is truncated
        }
    }

    @Synchronized
    private fun persist() {
        val dir = storageDir ?: return
        try {
            dir.mkdirs()
            val tempFile = File(dir, "brush_presets.json.tmp")
            val targetFile = File(dir, "brush_presets.json")

            val customList = presetsMap.values.filter { !it.isBuiltIn }.map { it.toMap() }
            val json = ArtJson.stringify(customList, indent = 2)
            tempFile.writeText(json)
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        } catch (_: Throwable) {
            // Best effort persistence
        }
    }

    @Synchronized
    fun getAllPresets(): List<BrushPreset> {
        return presetsMap.values.toList()
    }

    @Synchronized
    fun getPresetById(id: String): BrushPreset? {
        return presetsMap[id]
    }

    @Synchronized
    fun getCategories(): List<String> {
        val categories = LinkedHashSet<String>()
        categories.add("All")
        categories.add("Favorites")
        for (p in presetsMap.values) {
            categories.add(p.category)
        }
        return categories.toList()
    }

    @Synchronized
    fun getPresetsByCategory(category: String): List<BrushPreset> {
        return when (category.lowercase()) {
            "all" -> getAllPresets()
            "favorites" -> presetsMap.values.filter { it.isFavorite }
            else -> presetsMap.values.filter { it.category.equals(category, ignoreCase = true) }
        }
    }

    @Synchronized
    fun searchPresets(query: String): List<BrushPreset> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return getAllPresets()

        return presetsMap.values.filter { p ->
            p.name.lowercase().contains(q) ||
            p.category.lowercase().contains(q) ||
            p.tags.any { it.lowercase().contains(q) }
        }
    }

    @Synchronized
    fun toggleFavorite(id: String): BrushPreset {
        val current = presetsMap[id]
            ?: throw IllegalArgumentException("Cannot toggle favorite: Preset not found with ID '$id'")
        val updated = current.copy(
            isFavorite = !current.isFavorite,
            modifiedAtEpochMs = System.currentTimeMillis()
        )
        presetsMap[id] = updated
        persist()
        return updated
    }

    @Synchronized
    fun duplicatePreset(id: String): BrushPreset {
        val source = presetsMap[id]
            ?: throw IllegalArgumentException("Cannot duplicate: Preset not found with ID '$id'")

        val newId = "preset_custom_${UUID.randomUUID().toString().take(8)}"
        val newName = "${source.name} (Copy)"
        val cloned = BrushPreset(
            id = newId,
            name = newName,
            category = if (source.isBuiltIn) "Custom" else source.category,
            isFavorite = false,
            isBuiltIn = false,
            tags = source.tags + "custom",
            config = source.config.copy(
                seed = System.currentTimeMillis()
            ),
            schemaVersion = BrushPreset.CURRENT_SCHEMA_VERSION,
            createdAtEpochMs = System.currentTimeMillis(),
            modifiedAtEpochMs = System.currentTimeMillis()
        )
        presetsMap[newId] = cloned
        persist()
        return cloned
    }

    @Synchronized
    fun renamePreset(id: String, newName: String): BrushPreset {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("Preset name cannot be blank")
        }
        val current = presetsMap[id]
            ?: throw IllegalArgumentException("Cannot rename: Preset not found with ID '$id'")
        if (current.isBuiltIn) {
            throw IllegalArgumentException("Cannot rename built-in preset '${current.name}'")
        }

        val updated = current.copy(
            name = cleanName,
            modifiedAtEpochMs = System.currentTimeMillis()
        )
        presetsMap[id] = updated
        persist()
        return updated
    }

    @Synchronized
    fun deletePreset(id: String): Boolean {
        val current = presetsMap[id] ?: return false
        if (current.isBuiltIn) {
            throw IllegalArgumentException("Cannot delete built-in system preset '${current.name}'")
        }
        presetsMap.remove(id)
        persist()
        return true
    }

    @Synchronized
    fun savePreset(preset: BrushPreset): BrushPreset {
        presetsMap[preset.id] = preset
        persist()
        return preset
    }

    /**
     * Imports a brush preset from JSON string.
     * Strictly validates format, schema, bounds, and fields.
     * Returns Result.failure with [InvalidBrushPresetException] on validation failure.
     * GUARANTEE: Never falls back silently to an unrelated preset.
     */
    @Synchronized
    fun importPreset(json: String): Result<BrushPreset> {
        return try {
            val preset = BrushPreset.fromJson(json)
            // Generate distinct UUID if imported ID already collides with built-in
            val safePreset = if (presetsMap[preset.id]?.isBuiltIn == true) {
                preset.copy(id = "imported_${UUID.randomUUID().toString().take(8)}")
            } else {
                preset.copy(isBuiltIn = false)
            }
            presetsMap[safePreset.id] = safePreset
            persist()
            Result.success(safePreset)
        } catch (e: InvalidBrushPresetException) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(InvalidBrushPresetException("Unexpected import failure: ${e.message}", e))
        }
    }

    @Synchronized
    fun exportPreset(id: String): String {
        val preset = presetsMap[id]
            ?: throw IllegalArgumentException("Cannot export: Preset not found with ID '$id'")
        return preset.toJson()
    }

    /**
     * Groups presets into Bundled, Downloaded, and User Imported families for Studio UI.
     */
    @Synchronized
    fun getPresetsByGroup(): Map<String, List<BrushPreset>> {
        val bundled = mutableListOf<BrushPreset>()
        val downloaded = mutableListOf<BrushPreset>()
        val userImported = mutableListOf<BrushPreset>()
        for (p in presetsMap.values) {
            when {
                p.isBuiltIn -> bundled.add(p)
                p.category.contains("Imported") || p.tags.contains("downloaded") || p.tags.contains("krita") || p.tags.contains("mypaint") -> downloaded.add(p)
                else -> userImported.add(p)
            }
        }
        return mapOf(
            "Bundled" to bundled,
            "Downloaded" to downloaded,
            "User Imported" to userImported
        )
    }

    /**
     * Purges presets downloaded as part of an external pack.
     */
    @Synchronized
    fun deleteDownloadedPack(packId: String): Int {
        val toRemove = presetsMap.values.filter {
            !it.isBuiltIn && (it.category.contains(packId, ignoreCase = true) || it.tags.any { tag -> tag.contains(packId, ignoreCase = true) })
        }.map { it.id }
        for (id in toRemove) {
            presetsMap.remove(id)
        }
        if (toRemove.isNotEmpty()) persist()
        return toRemove.size
    }
}
