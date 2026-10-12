package com.artstudio.core

import com.artstudio.document.io.ArtJson

/**
 * Distinct paint media families expanding beyond the 7 core tools.
 */
enum class PaintMediaFamily(val id: String, val displayName: String) {
    STANDARD("standard", "Standard Tool"),
    MARKER("marker", "Art Marker"),
    WATERCOLOUR("watercolour", "Watercolour Wash"),
    OIL_PAINT("oil_paint", "Oil Paint"),
    DRY_BRUSH("dry_brush", "Dry Brush"),
    TEXTURED_STAMP("textured_stamp", "Textured Stamp");

    companion object {
        fun fromId(id: String): PaintMediaFamily {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STANDARD
        }
    }
}

/**
 * Procedural and asset-based stamp shapes supported by Textured Stamp.
 */
enum class StampPatternShape(val id: String, val displayName: String) {
    SPONGE("sponge", "Organic Sponge"),
    SPECKLE("speckle", "Stippled Speckle"),
    CANVAS_GRAIN("canvas_grain", "Woven Canvas"),
    LEAF_ORGANIC("leaf_organic", "Organic Leaf"),
    CUSTOM_ASSET("custom_asset", "Custom User Asset");

    companion object {
        fun fromId(id: String): StampPatternShape {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SPONGE
        }
    }
}

/**
 * Authoritative, serializable parameters for distinct paint media variants.
 * Each media family has distinct, testable parameters and documented limits.
 */
data class PaintMediaProperties(
    val family: PaintMediaFamily = PaintMediaFamily.STANDARD,

    // --- 1. Marker parameters ---
    val chiselAspect: Float = 3.0f,               // Aspect ratio width:height [1.0, 8.0]
    val markerAngleDegrees: Float = 45.0f,        // Fixed orientation angle [-180, 180]
    val inkBleed: Float = 0.15f,                  // Ink absorption bleeding [0.0, 1.0]
    val overlapAccumulation: Float = 0.40f,       // Opacity increase on overlap [0.0, 1.0]

    // --- 2. Watercolour parameters ---
    val wetEdgeIntensity: Float = 1.80f,          // Perimeter ring darkening multiplier [1.0, 3.5]
    val waterDiffusion: Float = 0.65f,            // Wash diffusion gradient [0.1, 1.0]
    val granulation: Float = 0.35f,               // Paper crevice pigment settling [0.0, 1.0]
    val pigmentLoad: Float = 0.50f,               // Fluid pigment volume [0.05, 1.0]

    // --- 3. Oil Paint parameters ---
    val impastoDepth: Float = 0.70f,              // Simulated ridge relief [0.0, 1.0]
    val viscosity: Float = 0.80f,                 // Paste thickness / drag resistance [0.0, 1.0]
    val bristleDensity: Int = 16,                 // Number of tracked filament tracks [4, 48]
    val paintLoading: Float = 0.90f,              // Paint volume loading [0.1, 1.0]

    // --- 4. Dry Brush parameters ---
    val toothThreshold: Float = 0.45f,            // Paper grain skip cutoff [0.0, 0.95]
    val fraySpread: Float = 0.30f,                // Lateral bristle fraying [0.0, 1.0]
    val textureScale: Float = 6.0f,               // Paper tooth frequency [1.0, 30.0]
    val dryPressureSensitivity: Float = 0.75f,    // Sensitivity to skipping at low pressure [0.0, 1.0]

    // --- 5. Textured Stamp & Asset parameters ---
    val stampShape: StampPatternShape = StampPatternShape.SPONGE,
    val stampAssetPath: String? = null,           // Optional external file path (PNG/WebP)
    val textureAssetId: String? = null,           // Optional unique asset ID from TextureRepository
    val rotationJitter: Float = 30.0f,            // Random rotation in degrees [0, 360]
    val scaleJitter: Float = 0.20f,               // Random scale variation [0.0, 0.8]
    val scatter: Float = 0.0f                     // Perpendicular displacement [0.0, 1.0]
) {
    init {
        require(chiselAspect in 1.0f..8.0f) { "chiselAspect must be in [1.0, 8.0], got $chiselAspect" }
        require(markerAngleDegrees in -180.0f..180.0f) { "markerAngleDegrees must be in [-180, 180], got $markerAngleDegrees" }
        require(inkBleed in 0.0f..1.0f) { "inkBleed must be in [0.0, 1.0], got $inkBleed" }
        require(overlapAccumulation in 0.0f..1.0f) { "overlapAccumulation must be in [0.0, 1.0], got $overlapAccumulation" }

        require(wetEdgeIntensity in 1.0f..3.5f) { "wetEdgeIntensity must be in [1.0, 3.5], got $wetEdgeIntensity" }
        require(waterDiffusion in 0.1f..1.0f) { "waterDiffusion must be in [0.1, 1.0], got $waterDiffusion" }
        require(granulation in 0.0f..1.0f) { "granulation must be in [0.0, 1.0], got $granulation" }
        require(pigmentLoad in 0.05f..1.0f) { "pigmentLoad must be in [0.05, 1.0], got $pigmentLoad" }

        require(impastoDepth in 0.0f..1.0f) { "impastoDepth must be in [0.0, 1.0], got $impastoDepth" }
        require(viscosity in 0.0f..1.0f) { "viscosity must be in [0.0, 1.0], got $viscosity" }
        require(bristleDensity in 4..48) { "bristleDensity must be in [4, 48], got $bristleDensity" }
        require(paintLoading in 0.1f..1.0f) { "paintLoading must be in [0.1, 1.0], got $paintLoading" }

        require(toothThreshold in 0.0f..0.95f) { "toothThreshold must be in [0.0, 0.95], got $toothThreshold" }
        require(fraySpread in 0.0f..1.0f) { "fraySpread must be in [0.0, 1.0], got $fraySpread" }
        require(textureScale in 1.0f..30.0f) { "textureScale must be in [1.0, 30.0], got $textureScale" }
        require(dryPressureSensitivity in 0.0f..1.0f) { "dryPressureSensitivity must be in [0.0, 1.0], got $dryPressureSensitivity" }

        require(rotationJitter in 0.0f..360.0f) { "rotationJitter must be in [0, 360], got $rotationJitter" }
        require(scaleJitter in 0.0f..0.8f) { "scaleJitter must be in [0.0, 0.8], got $scaleJitter" }
        require(scatter in 0.0f..1.0f) { "scatter must be in [0.0, 1.0], got $scatter" }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "family" to family.id,
        "chiselAspect" to chiselAspect.toDouble(),
        "markerAngleDegrees" to markerAngleDegrees.toDouble(),
        "inkBleed" to inkBleed.toDouble(),
        "overlapAccumulation" to overlapAccumulation.toDouble(),
        "wetEdgeIntensity" to wetEdgeIntensity.toDouble(),
        "waterDiffusion" to waterDiffusion.toDouble(),
        "granulation" to granulation.toDouble(),
        "pigmentLoad" to pigmentLoad.toDouble(),
        "impastoDepth" to impastoDepth.toDouble(),
        "viscosity" to viscosity.toDouble(),
        "bristleDensity" to bristleDensity,
        "paintLoading" to paintLoading.toDouble(),
        "toothThreshold" to toothThreshold.toDouble(),
        "fraySpread" to fraySpread.toDouble(),
        "textureScale" to textureScale.toDouble(),
        "dryPressureSensitivity" to dryPressureSensitivity.toDouble(),
        "stampShape" to stampShape.id,
        "stampAssetPath" to stampAssetPath,
        "textureAssetId" to textureAssetId,
        "rotationJitter" to rotationJitter.toDouble(),
        "scaleJitter" to scaleJitter.toDouble(),
        "scatter" to scatter.toDouble()
    )

    fun toJson(indent: Int = 2): String = ArtJson.stringify(toMap(), indent)

    companion object {
        fun fromMap(map: Map<String, Any?>): PaintMediaProperties {
            val familyStr = map["family"] as? String ?: "standard"
            val family = PaintMediaFamily.fromId(familyStr)

            val chiselAspect = (map["chiselAspect"] as? Number)?.toFloat() ?: 3.0f
            val markerAngleDegrees = (map["markerAngleDegrees"] as? Number)?.toFloat() ?: 45.0f
            val inkBleed = (map["inkBleed"] as? Number)?.toFloat() ?: 0.15f
            val overlapAccumulation = (map["overlapAccumulation"] as? Number)?.toFloat() ?: 0.40f

            val wetEdgeIntensity = (map["wetEdgeIntensity"] as? Number)?.toFloat() ?: 1.80f
            val waterDiffusion = (map["waterDiffusion"] as? Number)?.toFloat() ?: 0.65f
            val granulation = (map["granulation"] as? Number)?.toFloat() ?: 0.35f
            val pigmentLoad = (map["pigmentLoad"] as? Number)?.toFloat() ?: 0.50f

            val impastoDepth = (map["impastoDepth"] as? Number)?.toFloat() ?: 0.70f
            val viscosity = (map["viscosity"] as? Number)?.toFloat() ?: 0.80f
            val bristleDensity = (map["bristleDensity"] as? Number)?.toInt() ?: 16
            val paintLoading = (map["paintLoading"] as? Number)?.toFloat() ?: 0.90f

            val toothThreshold = (map["toothThreshold"] as? Number)?.toFloat() ?: 0.45f
            val fraySpread = (map["fraySpread"] as? Number)?.toFloat() ?: 0.30f
            val textureScale = (map["textureScale"] as? Number)?.toFloat() ?: 6.0f
            val dryPressureSensitivity = (map["dryPressureSensitivity"] as? Number)?.toFloat() ?: 0.75f

            val stampShapeStr = map["stampShape"] as? String ?: "sponge"
            val stampShape = StampPatternShape.fromId(stampShapeStr)
            val stampAssetPath = map["stampAssetPath"] as? String
            val textureAssetId = map["textureAssetId"] as? String
            val rotationJitter = (map["rotationJitter"] as? Number)?.toFloat() ?: 30.0f
            val scaleJitter = (map["scaleJitter"] as? Number)?.toFloat() ?: 0.20f
            val scatter = (map["scatter"] as? Number)?.toFloat() ?: 0.0f

            return PaintMediaProperties(
                family = family,
                chiselAspect = chiselAspect.coerceIn(1.0f, 8.0f),
                markerAngleDegrees = markerAngleDegrees.coerceIn(-180.0f, 180.0f),
                inkBleed = inkBleed.coerceIn(0.0f, 1.0f),
                overlapAccumulation = overlapAccumulation.coerceIn(0.0f, 1.0f),
                wetEdgeIntensity = wetEdgeIntensity.coerceIn(1.0f, 3.5f),
                waterDiffusion = waterDiffusion.coerceIn(0.1f, 1.0f),
                granulation = granulation.coerceIn(0.0f, 1.0f),
                pigmentLoad = pigmentLoad.coerceIn(0.05f, 1.0f),
                impastoDepth = impastoDepth.coerceIn(0.0f, 1.0f),
                viscosity = viscosity.coerceIn(0.0f, 1.0f),
                bristleDensity = bristleDensity.coerceIn(4, 48),
                paintLoading = paintLoading.coerceIn(0.1f, 1.0f),
                toothThreshold = toothThreshold.coerceIn(0.0f, 0.95f),
                fraySpread = fraySpread.coerceIn(0.0f, 1.0f),
                textureScale = textureScale.coerceIn(1.0f, 30.0f),
                dryPressureSensitivity = dryPressureSensitivity.coerceIn(0.0f, 1.0f),
                stampShape = stampShape,
                stampAssetPath = stampAssetPath,
                textureAssetId = textureAssetId,
                rotationJitter = rotationJitter.coerceIn(0.0f, 360.0f),
                scaleJitter = scaleJitter.coerceIn(0.0f, 0.8f),
                scatter = scatter.coerceIn(0.0f, 1.0f)
            )
        }
    }
}
