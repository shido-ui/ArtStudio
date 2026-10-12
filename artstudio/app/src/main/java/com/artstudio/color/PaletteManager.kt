package com.artstudio.color

import com.artstudio.document.io.ArtJson
import com.artstudio.document.model.ColorSwatchData
import com.artstudio.document.model.PaletteDefinition
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Standard factory and curated default palettes shipped with Art Studio.
 */
object StandardPalettes {
    val CLASSIC_STUDIO = PaletteDefinition(
        id = "palette_classic_studio",
        name = "Classic Studio",
        swatches = listOf(
            ColorSwatchData("#000000", "Carbon Black"),
            ColorSwatchData("#FFFFFF", "Titanium White"),
            ColorSwatchData("#D32F2F", "Cadmium Red"),
            ColorSwatchData("#F57C00", "Cadmium Orange"),
            ColorSwatchData("#FBC02D", "Cadmium Yellow"),
            ColorSwatchData("#388E3C", "Viridian Green"),
            ColorSwatchData("#1976D2", "Cobalt Blue"),
            ColorSwatchData("#7B1FA2", "Dioxazine Purple"),
            ColorSwatchData("#5D4037", "Burnt Umber"),
            ColorSwatchData("#757575", "Neutral Gray")
        )
    )

    val CYBER_NEON = PaletteDefinition(
        id = "palette_cyber_neon",
        name = "Cyber Neon",
        swatches = listOf(
            ColorSwatchData("#0B0C10", "Void Black"),
            ColorSwatchData("#1F2833", "Carbon Dark"),
            ColorSwatchData("#C5C6C7", "Steel Silver"),
            ColorSwatchData("#66FCF1", "Electric Cyan"),
            ColorSwatchData("#45A29E", "Deep Aqua"),
            ColorSwatchData("#FF007F", "Laser Magenta"),
            ColorSwatchData("#7928CA", "Cyber Violet"),
            ColorSwatchData("#FFE600", "Acid Yellow")
        )
    )

    val PASTEL_DREAM = PaletteDefinition(
        id = "palette_pastel_dream",
        name = "Pastel Dream",
        swatches = listOf(
            ColorSwatchData("#FFB3BA", "Pastel Pink"),
            ColorSwatchData("#FFDFBA", "Pastel Peach"),
            ColorSwatchData("#FFFFBA", "Pastel Lemon"),
            ColorSwatchData("#BAFFC9", "Pastel Mint"),
            ColorSwatchData("#BAE1FF", "Pastel Sky"),
            ColorSwatchData("#E8BAFF", "Pastel Lavender"),
            ColorSwatchData("#F5E6CC", "Warm Cream"),
            ColorSwatchData("#6E6E6E", "Soft Slate")
        )
    )

    val EARTH_TONES = PaletteDefinition(
        id = "palette_earth_tones",
        name = "Earth & Ochre",
        swatches = listOf(
            ColorSwatchData("#3E2723", "Deep Espresso"),
            ColorSwatchData("#5D4037", "Warm Walnut"),
            ColorSwatchData("#8D6E63", "Raw Umber"),
            ColorSwatchData("#D7CCC8", "Bone Ash"),
            ColorSwatchData("#C67D38", "Yellow Ochre"),
            ColorSwatchData("#935116", "Burnt Sienna"),
            ColorSwatchData("#4E5D42", "Olive Drab"),
            ColorSwatchData("#7D8C6C", "Sage Leaf")
        )
    )

    val MANGA_GRAYSCALE = PaletteDefinition(
        id = "palette_manga_ink",
        name = "Manga & Ink",
        swatches = listOf(
            ColorSwatchData("#000000", "Solid 100% Black"),
            ColorSwatchData("#212121", "Deep Shadow 90%"),
            ColorSwatchData("#424242", "Dense Halftone 75%"),
            ColorSwatchData("#616161", "Mid Shadow 60%"),
            ColorSwatchData("#757575", "Screen 50%"),
            ColorSwatchData("#9E9E9E", "Light Halftone 40%"),
            ColorSwatchData("#BDBDBD", "Light Tone 25%"),
            ColorSwatchData("#E0E0E0", "Subtle Tone 10%"),
            ColorSwatchData("#F5F5F5", "Highlight 5%"),
            ColorSwatchData("#FFFFFF", "Paper White 0%")
        )
    )

    fun getDefaultPalettes(): List<PaletteDefinition> = listOf(
        CLASSIC_STUDIO,
        CYBER_NEON,
        PASTEL_DREAM,
        EARTH_TONES,
        MANGA_GRAYSCALE
    )
}

/**
 * Manages user palettes across application lifecycle and projects.
 * Persists user palettes to disk so they remain accessible globally.
 */
class PaletteRepository(
    private val storageDir: File? = null
) {
    private val _userPalettes = mutableListOf<PaletteDefinition>()
    val userPalettes: List<PaletteDefinition> get() = _userPalettes.toList()

    private val paletteFile: File?
        get() = storageDir?.resolve("user_palettes.json")

    init {
        loadUserPalettes()
    }

    fun getAllPalettes(): List<PaletteDefinition> {
        return StandardPalettes.getDefaultPalettes() + _userPalettes
    }

    fun findPaletteById(id: String): PaletteDefinition? {
        return getAllPalettes().find { it.id == id }
    }

    fun addPalette(palette: PaletteDefinition) {
        _userPalettes.removeAll { it.id == palette.id }
        _userPalettes.add(palette)
        saveUserPalettes()
    }

    fun createPalette(name: String, swatches: List<ColorSwatchData> = emptyList()): PaletteDefinition {
        val newPalette = PaletteDefinition(
            id = "user_palette_${UUID.randomUUID()}",
            name = name,
            swatches = swatches
        )
        addPalette(newPalette)
        return newPalette
    }

    fun updatePalette(palette: PaletteDefinition) {
        val index = _userPalettes.indexOfFirst { it.id == palette.id }
        if (index >= 0) {
            _userPalettes[index] = palette
            saveUserPalettes()
        }
    }

    fun deletePalette(id: String): Boolean {
        val removed = _userPalettes.removeAll { it.id == id }
        if (removed) {
            saveUserPalettes()
        }
        return removed
    }

    fun addSwatchToPalette(paletteId: String, swatch: ColorSwatchData): PaletteDefinition? {
        val existing = _userPalettes.find { it.id == paletteId } ?: return null
        val updated = existing.copy(swatches = existing.swatches + swatch)
        updatePalette(updated)
        return updated
    }

    fun removeSwatchFromPalette(paletteId: String, swatchIndex: Int): PaletteDefinition? {
        val existing = _userPalettes.find { it.id == paletteId } ?: return null
        if (swatchIndex !in existing.swatches.indices) return existing
        val updatedSwatches = existing.swatches.toMutableList().apply { removeAt(swatchIndex) }
        val updated = existing.copy(swatches = updatedSwatches)
        updatePalette(updated)
        return updated
    }

    // Export palette to JSON string
    fun exportToJson(palette: PaletteDefinition): String {
        return ArtJson.stringify(mapOf(
            "id" to palette.id,
            "name" to palette.name,
            "swatches" to palette.swatches.map { mapOf("hex" to it.hex, "name" to it.name) }
        ))
    }

    // Import palette from JSON string
    fun importFromJson(jsonStr: String): PaletteDefinition {
        val map = ArtJson.parse(jsonStr) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON object")
        val name = map["name"]?.toString() ?: "Imported Palette"
        val id = "user_palette_${UUID.randomUUID()}"
        val rawSwatches = (map["swatches"] as? List<*>) ?: emptyList<Any?>()
        val swatches = rawSwatches.mapNotNull { item ->
            val sMap = item as? Map<*, *> ?: return@mapNotNull null
            val hex = sMap["hex"]?.toString() ?: return@mapNotNull null
            val sName = sMap["name"]?.toString()
            ColorSwatchData(hex, sName)
        }
        val palette = PaletteDefinition(id, name, swatches)
        addPalette(palette)
        return palette
    }

    // Export to GIMP Palette (.gpl) format
    fun exportToGpl(palette: PaletteDefinition): String {
        val sb = StringBuilder()
        sb.appendLine("GIMP Palette")
        sb.appendLine("Name: ${palette.name}")
        sb.appendLine("Columns: 4")
        sb.appendLine("#")
        for (swatch in palette.swatches) {
            try {
                val color = ColorData.fromHex(swatch.hex)
                val name = swatch.name ?: swatch.hex
                sb.appendLine("${color.redInt.toString().padStart(3)} ${color.greenInt.toString().padStart(3)} ${color.blueInt.toString().padStart(3)}\t$name")
            } catch (e: Exception) {
                android.util.Log.d("PaletteManager", "Skipping invalid hex swatch in GPL export: ${e.message}")
            }
        }
        return sb.toString()
    }

    // Import from GIMP Palette (.gpl) format
    fun importFromGpl(gplContent: String, defaultName: String = "Imported GPL"): PaletteDefinition {
        val lines = gplContent.lines()
        var name = defaultName
        val swatches = mutableListOf<ColorSwatchData>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("Name:", ignoreCase = true)) {
                name = trimmed.substring(5).trim()
                continue
            }
            if (trimmed.startsWith("Columns:", ignoreCase = true) || trimmed.startsWith("GIMP Palette", ignoreCase = true)) {
                continue
            }
            // Parse RGB lines: R G B [Name]
            val parts = trimmed.split(Regex("\\s+"), limit = 4)
            if (parts.size >= 3) {
                val r = parts[0].toIntOrNull()
                val g = parts[1].toIntOrNull()
                val b = parts[2].toIntOrNull()
                if (r != null && g != null && b != null) {
                    val sName = if (parts.size >= 4) parts[3].trim() else null
                    val color = ColorData.fromRgbInt(r, g, b)
                    swatches.add(ColorSwatchData(color.hexRgb, sName))
                }
            }
        }

        val palette = PaletteDefinition(
            id = "user_palette_${UUID.randomUUID()}",
            name = name,
            swatches = swatches
        )
        addPalette(palette)
        return palette
    }

    fun saveUserPalettes() {
        val file = paletteFile ?: return
        try {
            file.parentFile?.mkdirs()
            val list = _userPalettes.map { p ->
                mapOf(
                    "id" to p.id,
                    "name" to p.name,
                    "swatches" to p.swatches.map { mapOf("hex" to it.hex, "name" to it.name) }
                )
            }
            file.writeText(ArtJson.stringify(list))
        } catch (e: Exception) {
            android.util.Log.w("PaletteManager", "Failed to save user palettes: ${e.message}")
        }
    }

    private fun loadUserPalettes() {
        val file = paletteFile ?: return
        if (!file.exists()) return
        try {
            val content = file.readText()
            val list = ArtJson.parse(content) as? List<*> ?: return
            _userPalettes.clear()
            for (item in list) {
                val map = item as? Map<*, *> ?: continue
                val id = map["id"]?.toString() ?: continue
                val name = map["name"]?.toString() ?: "Palette"
                val rawSwatches = (map["swatches"] as? List<*>) ?: emptyList<Any?>()
                val swatches = rawSwatches.mapNotNull { s ->
                    val sMap = s as? Map<*, *> ?: return@mapNotNull null
                    val hex = sMap["hex"]?.toString() ?: return@mapNotNull null
                    val sName = sMap["name"]?.toString()
                    ColorSwatchData(hex, sName)
                }
                _userPalettes.add(PaletteDefinition(id, name, swatches))
            }
        } catch (e: Exception) {
            android.util.Log.w("PaletteManager", "Failed to load user palettes: ${e.message}")
        }
    }
}

/**
 * Deterministic color palette extraction engine from raster pixels.
 * Uses Median-Cut color quantization to extract balanced, representative color palettes.
 */
object PaletteExtractor {

    private data class ColorBox(
        val pixels: List<Int>
    ) {
        val count: Int get() = pixels.size

        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0

        init {
            for (p in pixels) {
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                if (r < minR) minR = r
                if (r > maxR) maxR = r
                if (g < minG) minG = g
                if (g > maxG) maxG = g
                if (b < minB) minB = b
                if (b > maxB) maxB = b
            }
        }

        val rangeR: Int get() = maxR - minR
        val rangeG: Int get() = maxG - minG
        val rangeB: Int get() = maxB - minB
        val maxRange: Int get() = max(rangeR, max(rangeG, rangeB))

        fun averageColor(): ColorData {
            if (pixels.isEmpty()) return ColorData.BLACK
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (p in pixels) {
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            val n = pixels.size.toFloat()
            return ColorData.fromRgbInt(
                (sumR / n).toInt(),
                (sumG / n).toInt(),
                (sumB / n).toInt()
            )
        }

        fun split(): Pair<ColorBox, ColorBox>? {
            if (pixels.size <= 1) return null

            // Find dimension with largest range
            val sorted = when {
                rangeR >= rangeG && rangeR >= rangeB -> {
                    pixels.sortedBy { (it ushr 16) and 0xFF }
                }
                rangeG >= rangeR && rangeG >= rangeB -> {
                    pixels.sortedBy { (it ushr 8) and 0xFF }
                }
                else -> {
                    pixels.sortedBy { it and 0xFF }
                }
            }

            val medianIndex = sorted.size / 2
            val box1 = ColorBox(sorted.subList(0, medianIndex))
            val box2 = ColorBox(sorted.subList(medianIndex, sorted.size))
            return Pair(box1, box2)
        }
    }

    /**
     * Extracts k dominant colors from ARGB pixel buffer.
     * @param pixels 32-bit ARGB packed integer array.
     * @param maxColors Target palette swatch count (default 8, typically 4..16).
     * @param skipTransparent If true, pixels with alpha < 128 are ignored.
     */
    fun extractPalette(
        pixels: IntArray,
        paletteName: String = "Extracted Palette",
        maxColors: Int = 8,
        skipTransparent: Boolean = true
    ): PaletteDefinition {
        require(maxColors in 1..64) { "maxColors must be in [1, 64], got $maxColors" }

        // Filter valid pixels and sub-sample large images for speed
        val sampledPixels = mutableListOf<Int>()
        val step = max(1, pixels.size / 50_000) // Sample up to 50,000 pixels

        for (i in pixels.indices step step) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (!skipTransparent || a >= 128) {
                sampledPixels.add(p)
            }
        }

        if (sampledPixels.isEmpty()) {
            return PaletteDefinition(
                name = paletteName,
                swatches = listOf(ColorSwatchData("#000000", "Fallback Black"))
            )
        }

        val boxes = mutableListOf(ColorBox(sampledPixels))

        while (boxes.size < maxColors) {
            // Find box with the largest color range that has more than 1 pixel
            val splitTarget = boxes
                .filter { it.count > 1 }
                .maxByOrNull { it.maxRange } ?: break

            val pair = splitTarget.split() ?: break
            boxes.remove(splitTarget)
            boxes.add(pair.first)
            boxes.add(pair.second)
        }

        // Sort extracted colors by luminance or hue for pleasing arrangement
        val swatches = boxes
            .map { it.averageColor() }
            .sortedBy { it.lightness }
            .mapIndexed { idx, color ->
                ColorSwatchData(color.hexRgb, "Tone ${idx + 1}")
            }

        return PaletteDefinition(
            id = "extracted_${UUID.randomUUID()}",
            name = paletteName,
            swatches = swatches
        )
    }
}
