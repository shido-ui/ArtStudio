package com.artstudio.cv.palette

import com.artstudio.document.io.ArtJson

/**
 * Lossless persistence serializer for [SourcePaletteAnalysis].
 *
 * In accordance with Prompt 57:
 * - Persists source palette and region mappings separately from style output.
 * - Ensures palette mappings survive native save/reopen lifecycles and remain
 *   available to every Colour Lab style.
 */
object SourcePaletteSerializer {

    /**
     * Serializes a [SourcePaletteAnalysis] instance into a formatted JSON string.
     */
    fun toJson(analysis: SourcePaletteAnalysis, indent: Int = 2): String {
        return ArtJson.stringify(analysis.toMap(), indent)
    }

    /**
     * Restores a [SourcePaletteAnalysis] instance from a serialized JSON string.
     */
    fun fromJson(jsonStr: String): SourcePaletteAnalysis {
        @Suppress("UNCHECKED_CAST")
        val root = ArtJson.parse(jsonStr) as? Map<String, Any?>
            ?: throw IllegalArgumentException("Invalid JSON root, expected Map object")

        val id = root["id"]?.toString() ?: "palette_analysis_restored"

        @Suppress("UNCHECKED_CAST")
        val rawSwatches = (root["globalPalette"] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val globalPalette = rawSwatches.map { SourcePaletteSwatch.fromMap(it) }

        @Suppress("UNCHECKED_CAST")
        val rawAssignments = (root["regionAssignments"] as? Map<String, Any?>) ?: emptyMap()
        val regionAssignments = mutableMapOf<String, RegionColorAssignment>()
        for ((k, v) in rawAssignments) {
            val vMap = v as? Map<String, Any?> ?: continue
            regionAssignments[k] = RegionColorAssignment.fromMap(vMap)
        }

        @Suppress("UNCHECKED_CAST")
        val rawAdjacencies = (root["adjacencies"] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val adjacencies = rawAdjacencies.map { RegionAdjacency.fromMap(it) }

        val adaptModeName = root["adaptationMode"]?.toString() ?: PaletteAdaptationMode.PRESERVE_SOURCE.name
        val adaptationMode = runCatching { PaletteAdaptationMode.valueOf(adaptModeName) }
            .getOrDefault(PaletteAdaptationMode.PRESERVE_SOURCE)

        val schemeName = root["artisticScheme"]?.toString() ?: ArtisticScheme.ORIGINAL_TONAL.name
        val artisticScheme = runCatching { ArtisticScheme.valueOf(schemeName) }
            .getOrDefault(ArtisticScheme.ORIGINAL_TONAL)

        val rmse = (root["paletteExtractionErrorRmse"] as? Number)?.toFloat() ?: 0f
        val transparentCount = (root["transparentPixelCount"] as? Number)?.toInt() ?: 0
        val totalAnalyzed = (root["totalAnalyzedPixels"] as? Number)?.toInt() ?: 0
        val timestamp = (root["provenanceTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

        return SourcePaletteAnalysis(
            id = id,
            globalPalette = globalPalette,
            regionAssignments = regionAssignments,
            adjacencies = adjacencies,
            adaptationMode = adaptationMode,
            artisticScheme = artisticScheme,
            paletteExtractionErrorRmse = rmse,
            transparentPixelCount = transparentCount,
            totalAnalyzedPixels = totalAnalyzed,
            provenanceTimestamp = timestamp
        )
    }
}
