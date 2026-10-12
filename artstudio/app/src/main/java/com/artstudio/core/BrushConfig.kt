package com.artstudio.core

import com.artstudio.document.io.ArtJson

/**
 * The 7 essential core tools supported by the shared stroke raster pipeline.
 */
enum class BrushType(val id: String, val displayName: String) {
    PENCIL("pencil", "Pencil"),
    HARD_PEN("hard_pen", "Hard Pen"),
    SOFT_PEN("soft_pen", "Soft Pen"),
    INK_BRUSH("ink_brush", "Ink Brush"),
    BASIC_PAINTBRUSH("basic_paintbrush", "Basic Paintbrush"),
    AIRBRUSH("airbrush", "Airbrush"),
    ERASER("eraser", "Eraser");

    companion object {
        fun fromId(id: String): BrushType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: HARD_PEN
        }
    }
}

enum class StrokeCapMode {
    ROUND,
    BUTT,
    SQUARE
}

enum class StrokeJoinMode {
    ROUND,
    MITER,
    BEVEL
}

/**
 * Authoritative, serializable brush configuration.
 * Fully decoupled from Android UI widgets and View lifecycle.
 */
data class BrushConfig(
    val type: BrushType = BrushType.HARD_PEN,
    val size: Float = 12.0f,
    val opacity: Float = 1.0f,
    val color: Int = 0xFF000000.toInt(),
    val spacing: Float = 0.15f,
    val hardness: Float = 1.0f,
    val flow: Float = 1.0f,
    val minSizeRatio: Float = 0.2f,
    val maxSizeRatio: Float = 1.2f,
    val seed: Long = 42L,
    val cap: StrokeCapMode = StrokeCapMode.ROUND,
    val join: StrokeJoinMode = StrokeJoinMode.ROUND,
    val antiAlias: Boolean = true,
    val pressureResponse: PressureResponseMode = PressureResponseMode.LINEAR,
    val pressureGamma: Float = 1.0f,
    val startTaperRatio: Float = 0.0f,
    val endTaperRatio: Float = 0.0f,
    val tipShape: BrushTipShape = BrushTipShape.ROUND,
    val tipAngleDegrees: Float = 0.0f,
    val followStrokeAngle: Boolean = false,
    val startCap: StrokeCapMode = StrokeCapMode.ROUND,
    val endCap: StrokeCapMode = StrokeCapMode.ROUND,
    val stabilizationWeight: Float = 0.0f,
    val mediaProperties: PaintMediaProperties = PaintMediaProperties()
) {
    init {
        require(size > 0f) { "Brush size must be positive, got $size" }
        require(opacity in 0.0f..1.0f) { "Brush opacity must be in [0.0, 1.0], got $opacity" }
        require(spacing in 0.01f..1.0f) { "Brush spacing must be in [0.01, 1.0], got $spacing" }
        require(hardness in 0.0f..1.0f) { "Brush hardness must be in [0.0, 1.0], got $hardness" }
        require(flow in 0.01f..1.0f) { "Brush flow must be in [0.01, 1.0], got $flow" }
        require(pressureGamma > 0f) { "Brush pressure gamma must be positive, got $pressureGamma" }
        require(startTaperRatio in 0.0f..0.5f) { "Brush start taper ratio must be in [0.0, 0.5], got $startTaperRatio" }
        require(endTaperRatio in 0.0f..0.5f) { "Brush end taper ratio must be in [0.0, 0.5], got $endTaperRatio" }
        require(stabilizationWeight in 0.0f..0.95f) { "Brush stabilization weight must be in [0.0, 0.95], got $stabilizationWeight" }
    }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "type" to type.id,
            "size" to size.toDouble(),
            "opacity" to opacity.toDouble(),
            "color" to color.toLong(),
            "spacing" to spacing.toDouble(),
            "hardness" to hardness.toDouble(),
            "flow" to flow.toDouble(),
            "minSizeRatio" to minSizeRatio.toDouble(),
            "maxSizeRatio" to maxSizeRatio.toDouble(),
            "seed" to seed,
            "cap" to cap.name,
            "join" to join.name,
            "antiAlias" to antiAlias,
            "pressureResponse" to pressureResponse.id,
            "pressureGamma" to pressureGamma.toDouble(),
            "startTaperRatio" to startTaperRatio.toDouble(),
            "endTaperRatio" to endTaperRatio.toDouble(),
            "tipShape" to tipShape.id,
            "tipAngleDegrees" to tipAngleDegrees.toDouble(),
            "followStrokeAngle" to followStrokeAngle,
            "startCap" to startCap.name,
            "endCap" to endCap.name,
            "stabilizationWeight" to stabilizationWeight.toDouble()
        )
        if (mediaProperties.family != PaintMediaFamily.STANDARD) {
            map["mediaProperties"] = mediaProperties.toMap()
        }
        return map
    }

    fun toJson(indent: Int = 2): String {
        return ArtJson.stringify(toMap(), indent)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): BrushConfig {
            val typeStr = map["type"] as? String ?: "hard_pen"
            val type = BrushType.fromId(typeStr)
            val size = (map["size"] as? Number)?.toFloat() ?: 12f
            val opacity = (map["opacity"] as? Number)?.toFloat() ?: 1.0f
            val color = (map["color"] as? Number)?.toInt() ?: 0xFF000000.toInt()
            val spacing = (map["spacing"] as? Number)?.toFloat() ?: 0.15f
            val hardness = (map["hardness"] as? Number)?.toFloat() ?: 1.0f
            val flow = (map["flow"] as? Number)?.toFloat() ?: 1.0f
            val minSizeRatio = (map["minSizeRatio"] as? Number)?.toFloat() ?: 0.2f
            val maxSizeRatio = (map["maxSizeRatio"] as? Number)?.toFloat() ?: 1.2f
            val seed = (map["seed"] as? Number)?.toLong() ?: 42L
            val cap = try {
                StrokeCapMode.valueOf(map["cap"] as? String ?: "ROUND")
            } catch (_: Throwable) {
                StrokeCapMode.ROUND
            }
            val join = try {
                StrokeJoinMode.valueOf(map["join"] as? String ?: "ROUND")
            } catch (_: Throwable) {
                StrokeJoinMode.ROUND
            }
            val antiAlias = map["antiAlias"] as? Boolean ?: true

            val pressureResponseStr = map["pressureResponse"] as? String ?: "linear"
            val pressureResponse = try {
                PressureResponseMode.fromId(pressureResponseStr)
            } catch (_: Throwable) {
                PressureResponseMode.LINEAR
            }
            val pressureGamma = (map["pressureGamma"] as? Number)?.toFloat() ?: 1.0f
            val startTaperRatio = (map["startTaperRatio"] as? Number)?.toFloat() ?: 0.0f
            val endTaperRatio = (map["endTaperRatio"] as? Number)?.toFloat() ?: 0.0f
            val tipShapeStr = map["tipShape"] as? String ?: "round"
            val tipShape = try {
                BrushTipShape.fromId(tipShapeStr)
            } catch (_: Throwable) {
                BrushTipShape.ROUND
            }
            val tipAngleDegrees = (map["tipAngleDegrees"] as? Number)?.toFloat() ?: 0.0f
            val followStrokeAngle = map["followStrokeAngle"] as? Boolean ?: false
            val startCap = try {
                StrokeCapMode.valueOf(map["startCap"] as? String ?: "ROUND")
            } catch (_: Throwable) {
                StrokeCapMode.ROUND
            }
            val endCap = try {
                StrokeCapMode.valueOf(map["endCap"] as? String ?: "ROUND")
            } catch (_: Throwable) {
                StrokeCapMode.ROUND
            }
            val stabilizationWeight = (map["stabilizationWeight"] as? Number)?.toFloat() ?: 0.0f
            val mediaProperties = (map["mediaProperties"] as? Map<String, Any?>)?.let {
                PaintMediaProperties.fromMap(it)
            } ?: PaintMediaProperties()

            return BrushConfig(
                type = type,
                size = size,
                opacity = opacity.coerceIn(0f, 1f),
                color = color,
                spacing = spacing.coerceIn(0.01f, 1f),
                hardness = hardness.coerceIn(0f, 1f),
                flow = flow.coerceIn(0.01f, 1f),
                minSizeRatio = minSizeRatio,
                maxSizeRatio = maxSizeRatio,
                seed = seed,
                cap = cap,
                join = join,
                antiAlias = antiAlias,
                pressureResponse = pressureResponse,
                pressureGamma = maxOf(0.01f, pressureGamma),
                startTaperRatio = startTaperRatio.coerceIn(0.0f, 0.5f),
                endTaperRatio = endTaperRatio.coerceIn(0.0f, 0.5f),
                tipShape = tipShape,
                tipAngleDegrees = tipAngleDegrees,
                followStrokeAngle = followStrokeAngle,
                startCap = startCap,
                endCap = endCap,
                stabilizationWeight = stabilizationWeight.coerceIn(0.0f, 0.95f),
                mediaProperties = mediaProperties
            )
        }

        fun fromJson(json: String): BrushConfig {
            @Suppress("UNCHECKED_CAST")
            val map = ArtJson.parse(json) as? Map<String, Any?>
                ?: throw IllegalArgumentException("Invalid JSON object for BrushConfig")
            return fromMap(map)
        }
    }
}

/**
 * Standard factory presets for the 7 core tools and 5 distinct paint media families.
 */
object BrushPresets {
    fun pencil(size: Float = 8f, color: Int = 0xFF333333.toInt(), seed: Long = 1337L) = BrushConfig(
        type = BrushType.PENCIL,
        size = size,
        opacity = 0.85f,
        color = color,
        spacing = 0.20f,
        hardness = 0.60f,
        seed = seed,
        minSizeRatio = 0.4f,
        maxSizeRatio = 1.1f
    )

    fun hardPen(size: Float = 6f, color: Int = 0xFF000000.toInt()) = BrushConfig(
        type = BrushType.HARD_PEN,
        size = size,
        opacity = 1.0f,
        color = color,
        spacing = 0.10f,
        hardness = 1.0f,
        minSizeRatio = 0.2f,
        maxSizeRatio = 1.2f
    )

    fun softPen(size: Float = 14f, color: Int = 0xFF111111.toInt()) = BrushConfig(
        type = BrushType.SOFT_PEN,
        size = size,
        opacity = 0.90f,
        color = color,
        spacing = 0.12f,
        hardness = 0.45f,
        minSizeRatio = 0.3f,
        maxSizeRatio = 1.2f
    )

    fun inkBrush(size: Float = 16f, color: Int = 0xFF0A0A0A.toInt()) = BrushConfig(
        type = BrushType.INK_BRUSH,
        size = size,
        opacity = 1.0f,
        color = color,
        spacing = 0.08f,
        hardness = 0.90f,
        minSizeRatio = 0.1f,
        maxSizeRatio = 1.5f
    )

    fun basicPaintbrush(size: Float = 24f, color: Int = 0xFF2A52BE.toInt(), seed: Long = 2026L) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.80f,
        color = color,
        spacing = 0.15f,
        hardness = 0.70f,
        seed = seed,
        minSizeRatio = 0.5f,
        maxSizeRatio = 1.3f
    )

    fun airbrush(size: Float = 40f, color: Int = 0xFFE04040.toInt(), seed: Long = 777L) = BrushConfig(
        type = BrushType.AIRBRUSH,
        size = size,
        opacity = 0.25f,
        color = color,
        spacing = 0.10f,
        hardness = 0.15f,
        flow = 0.20f,
        seed = seed,
        minSizeRatio = 0.6f,
        maxSizeRatio = 1.2f
    )

    fun eraser(size: Float = 28f, hardness: Float = 0.90f) = BrushConfig(
        type = BrushType.ERASER,
        size = size,
        opacity = 1.0f,
        color = 0x00000000,
        spacing = 0.15f,
        hardness = hardness,
        minSizeRatio = 0.5f,
        maxSizeRatio = 1.2f
    )

    // -------------------------------------------------------------------------
    // Starter Presets for 5 Distinct Paint Media Families (Prompt 24)
    // -------------------------------------------------------------------------

    fun marker(
        size: Float = 24f,
        color: Int = 0xFF1E88E5.toInt(),
        angle: Float = 45f,
        aspect: Float = 3.0f,
        bleed: Float = 0.15f
    ) = BrushConfig(
        type = BrushType.HARD_PEN,
        size = size,
        opacity = 0.70f,
        color = color,
        spacing = 0.08f,
        hardness = 0.95f,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.MARKER,
            chiselAspect = aspect,
            markerAngleDegrees = angle,
            inkBleed = bleed,
            overlapAccumulation = 0.40f
        )
    )

    fun watercolourWash(
        size: Float = 36f,
        color: Int = 0xFF2E7D32.toInt(),
        wetEdge: Float = 1.80f,
        diffusion: Float = 0.65f,
        seed: Long = 42L
    ) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.50f,
        color = color,
        spacing = 0.12f,
        hardness = 0.70f,
        seed = seed,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.WATERCOLOUR,
            wetEdgeIntensity = wetEdge,
            waterDiffusion = diffusion,
            granulation = 0.35f,
            pigmentLoad = 0.50f
        )
    )

    fun oilPaint(
        size: Float = 28f,
        color: Int = 0xFFC2185B.toInt(),
        impasto: Float = 0.70f,
        viscosity: Float = 0.80f,
        bristles: Int = 16,
        seed: Long = 77L
    ) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.92f,
        color = color,
        spacing = 0.10f,
        hardness = 0.85f,
        seed = seed,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.OIL_PAINT,
            impastoDepth = impasto,
            viscosity = viscosity,
            bristleDensity = bristles,
            paintLoading = 0.90f
        )
    )

    fun dryBrush(
        size: Float = 22f,
        color: Int = 0xFF4E342E.toInt(),
        toothThreshold: Float = 0.45f,
        fraySpread: Float = 0.30f,
        seed: Long = 101L
    ) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.85f,
        color = color,
        spacing = 0.14f,
        hardness = 0.80f,
        seed = seed,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.DRY_BRUSH,
            toothThreshold = toothThreshold,
            fraySpread = fraySpread,
            textureScale = 6.0f,
            dryPressureSensitivity = 0.75f
        )
    )

    fun texturedStamp(
        size: Float = 32f,
        color: Int = 0xFF512DA8.toInt(),
        shape: StampPatternShape = StampPatternShape.SPONGE,
        rotationJitter: Float = 30f,
        seed: Long = 2026L
    ) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.80f,
        color = color,
        spacing = 0.28f,
        hardness = 0.75f,
        seed = seed,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.TEXTURED_STAMP,
            stampShape = shape,
            rotationJitter = rotationJitter,
            scaleJitter = 0.20f,
            scatter = 0.0f
        )
    )

    fun grainBrush(
        size: Float = 24f,
        color: Int = 0xFF212121.toInt(),
        grainAssetId: String = "builtin_charcoal_grain_64",
        toothThreshold: Float = 0.40f,
        seed: Long = 3030L
    ) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.85f,
        color = color,
        spacing = 0.12f,
        hardness = 0.75f,
        seed = seed,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.DRY_BRUSH,
            toothThreshold = toothThreshold,
            textureScale = 8.0f,
            dryPressureSensitivity = 0.80f,
            textureAssetId = grainAssetId
        )
    )

    fun patternBrush(
        size: Float = 36f,
        color: Int = 0xFF1976D2.toInt(),
        patternAssetId: String = "builtin_canvas_grain_64",
        seed: Long = 4040L
    ) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.80f,
        color = color,
        spacing = 0.22f,
        hardness = 0.80f,
        seed = seed,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.TEXTURED_STAMP,
            stampShape = StampPatternShape.CANVAS_GRAIN,
            textureAssetId = patternAssetId,
            rotationJitter = 0.0f,
            scaleJitter = 0.0f
        )
    )

    fun stampBrush(
        size: Float = 40f,
        color: Int = 0xFFD32F2F.toInt(),
        stampAssetId: String = "builtin_ink_blot_64",
        seed: Long = 5050L
    ) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.90f,
        color = color,
        spacing = 0.35f,
        hardness = 0.90f,
        seed = seed,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.TEXTURED_STAMP,
            stampShape = StampPatternShape.SPONGE,
            textureAssetId = stampAssetId,
            rotationJitter = 45.0f,
            scaleJitter = 0.25f
        )
    )

    fun texturedStrokeBrush(
        size: Float = 28f,
        color: Int = 0xFF388E3C.toInt(),
        strokeAssetId: String = "builtin_dry_bristle_64",
        seed: Long = 6060L
    ) = BrushConfig(
        type = BrushType.BASIC_PAINTBRUSH,
        size = size,
        opacity = 0.88f,
        color = color,
        spacing = 0.10f,
        hardness = 0.85f,
        seed = seed,
        mediaProperties = PaintMediaProperties(
            family = PaintMediaFamily.DRY_BRUSH,
            fraySpread = 0.35f,
            textureScale = 5.0f,
            textureAssetId = strokeAssetId
        )
    )

    fun getPreset(type: BrushType): BrushConfig {
        return when (type) {
            BrushType.PENCIL -> pencil()
            BrushType.HARD_PEN -> hardPen()
            BrushType.SOFT_PEN -> softPen()
            BrushType.INK_BRUSH -> inkBrush()
            BrushType.BASIC_PAINTBRUSH -> basicPaintbrush()
            BrushType.AIRBRUSH -> airbrush()
            BrushType.ERASER -> eraser()
        }
    }
}
