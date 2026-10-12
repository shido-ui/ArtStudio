package com.artstudio.brush

import com.artstudio.core.*
import com.artstudio.document.io.ArtJson

/**
 * Exception thrown when a brush preset fails schema, range, or structure validation.
 * Enforces the critical invariant: invalid presets must fail with an actionable error
 * and NEVER silently fall back to an unrelated default or preset.
 */
class InvalidBrushPresetException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Authoritative brush preset model for storage, categorization, and interchange.
 */
data class BrushPreset(
    val id: String,
    val name: String,
    val category: String = "General",
    val isFavorite: Boolean = false,
    val isBuiltIn: Boolean = false,
    val tags: List<String> = emptyList(),
    val config: BrushConfig,
    val schemaVersion: Int = 1,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val modifiedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "format" to FORMAT_IDENTIFIER,
            "schemaVersion" to schemaVersion,
            "id" to id,
            "name" to name,
            "category" to category,
            "isFavorite" to isFavorite,
            "isBuiltIn" to isBuiltIn,
            "tags" to tags,
            "config" to config.toMap(),
            "createdAtEpochMs" to createdAtEpochMs,
            "modifiedAtEpochMs" to modifiedAtEpochMs
        )
    }

    fun toJson(indent: Int = 2): String {
        return ArtJson.stringify(toMap(), indent)
    }

    companion object {
        const val FORMAT_IDENTIFIER = "artstudio-brush-preset"
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * Parses and validates a Map representation of a preset.
         * Throws [InvalidBrushPresetException] on any structural, range, or format violation.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): BrushPreset {
            // 1. Format and schema version check
            val format = map["format"] as? String
            if (format != null && format != FORMAT_IDENTIFIER) {
                throw InvalidBrushPresetException(
                    "Invalid brush format identifier: '$format' (expected '$FORMAT_IDENTIFIER')"
                )
            }

            val schemaVersion = (map["schemaVersion"] as? Number)?.toInt() ?: CURRENT_SCHEMA_VERSION
            if (schemaVersion < 1) {
                throw InvalidBrushPresetException("Unsupported brush preset schema version: $schemaVersion")
            }

            // 2. Identifier and Name validation
            val id = (map["id"] as? String)?.trim()
            if (id.isNullOrBlank()) {
                throw InvalidBrushPresetException("Preset id cannot be blank")
            }

            val name = (map["name"] as? String)?.trim()
            if (name.isNullOrBlank()) {
                throw InvalidBrushPresetException("Preset name cannot be blank for id: '$id'")
            }

            val category = (map["category"] as? String)?.trim() ?: "General"
            val isFavorite = map["isFavorite"] as? Boolean ?: false
            val isBuiltIn = map["isBuiltIn"] as? Boolean ?: false
            val tags = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            // 3. Config validation
            val configRaw = map["config"]
                ?: throw InvalidBrushPresetException("Missing required 'config' object for preset '$name' ($id)")

            val configMap = configRaw as? Map<String, Any?>
                ?: throw InvalidBrushPresetException("'config' must be a JSON object, found ${configRaw::class.simpleName}")

            val config = validateAndParseConfig(configMap, presetName = name, presetId = id)

            val createdAtEpochMs = (map["createdAtEpochMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val modifiedAtEpochMs = (map["modifiedAtEpochMs"] as? Number)?.toLong() ?: System.currentTimeMillis()

            return BrushPreset(
                id = id,
                name = name,
                category = category,
                isFavorite = isFavorite,
                isBuiltIn = isBuiltIn,
                tags = tags,
                config = config,
                schemaVersion = schemaVersion,
                createdAtEpochMs = createdAtEpochMs,
                modifiedAtEpochMs = modifiedAtEpochMs
            )
        }

        /**
         * Strictly validates and parses a BrushConfig map.
         * Explicitly rejects unrecognized enums, negative sizes, or out-of-range bounds.
         */
        private fun validateAndParseConfig(
            map: Map<String, Any?>,
            presetName: String,
            presetId: String
        ): BrushConfig {
            val typeStr = map["type"] as? String
                ?: throw InvalidBrushPresetException("Preset '$presetName' ($presetId) missing 'type' in config")

            val type = BrushType.entries.firstOrNull { it.id.equals(typeStr, ignoreCase = true) }
                ?: throw InvalidBrushPresetException("Unknown brush type '$typeStr' in preset '$presetName' ($presetId)")

            val size = (map["size"] as? Number)?.toFloat()
                ?: throw InvalidBrushPresetException("Missing or invalid 'size' in preset '$presetName' ($presetId)")
            if (size <= 0f) {
                throw InvalidBrushPresetException("Brush size must be > 0, got $size in preset '$presetName' ($presetId)")
            }

            val opacity = (map["opacity"] as? Number)?.toFloat() ?: 1.0f
            if (opacity !in 0.0f..1.0f) {
                throw InvalidBrushPresetException("Brush opacity must be in [0.0, 1.0], got $opacity in preset '$presetName'")
            }

            val spacing = (map["spacing"] as? Number)?.toFloat() ?: 0.15f
            if (spacing !in 0.01f..1.0f) {
                throw InvalidBrushPresetException("Brush spacing must be in [0.01, 1.0], got $spacing in preset '$presetName'")
            }

            val hardness = (map["hardness"] as? Number)?.toFloat() ?: 1.0f
            if (hardness !in 0.0f..1.0f) {
                throw InvalidBrushPresetException("Brush hardness must be in [0.0, 1.0], got $hardness in preset '$presetName'")
            }

            val flow = (map["flow"] as? Number)?.toFloat() ?: 1.0f
            if (flow !in 0.01f..1.0f) {
                throw InvalidBrushPresetException("Brush flow must be in [0.01, 1.0], got $flow in preset '$presetName'")
            }

            val color = (map["color"] as? Number)?.toInt() ?: 0xFF000000.toInt()
            val minSizeRatio = (map["minSizeRatio"] as? Number)?.toFloat() ?: 0.2f
            val maxSizeRatio = (map["maxSizeRatio"] as? Number)?.toFloat() ?: 1.2f
            if (minSizeRatio <= 0f || maxSizeRatio < minSizeRatio) {
                throw InvalidBrushPresetException("Invalid size ratio bounds [$minSizeRatio, $maxSizeRatio] in preset '$presetName'")
            }

            val seed = (map["seed"] as? Number)?.toLong() ?: 42L

            val capStr = map["cap"] as? String ?: "ROUND"
            val cap = try {
                StrokeCapMode.valueOf(capStr.uppercase())
            } catch (_: Throwable) {
                throw InvalidBrushPresetException("Invalid stroke cap '$capStr' in preset '$presetName'")
            }

            val joinStr = map["join"] as? String ?: "ROUND"
            val join = try {
                StrokeJoinMode.valueOf(joinStr.uppercase())
            } catch (_: Throwable) {
                throw InvalidBrushPresetException("Invalid stroke join '$joinStr' in preset '$presetName'")
            }

            val antiAlias = map["antiAlias"] as? Boolean ?: true

            val pressureResponseStr = map["pressureResponse"] as? String ?: "linear"
            val pressureResponse = try {
                PressureResponseMode.fromId(pressureResponseStr)
            } catch (_: Throwable) {
                throw InvalidBrushPresetException("Invalid pressureResponse '$pressureResponseStr' in preset '$presetName'")
            }

            val pressureGamma = (map["pressureGamma"] as? Number)?.toFloat() ?: 1.0f
            if (pressureGamma <= 0f) {
                throw InvalidBrushPresetException("Pressure gamma must be > 0, got $pressureGamma in preset '$presetName'")
            }

            val startTaperRatio = (map["startTaperRatio"] as? Number)?.toFloat() ?: 0.0f
            if (startTaperRatio !in 0.0f..0.5f) {
                throw InvalidBrushPresetException("Start taper ratio must be in [0.0, 0.5], got $startTaperRatio in preset '$presetName'")
            }

            val endTaperRatio = (map["endTaperRatio"] as? Number)?.toFloat() ?: 0.0f
            if (endTaperRatio !in 0.0f..0.5f) {
                throw InvalidBrushPresetException("End taper ratio must be in [0.0, 0.5], got $endTaperRatio in preset '$presetName'")
            }

            val tipShapeStr = map["tipShape"] as? String ?: "round"
            val tipShape = try {
                BrushTipShape.fromId(tipShapeStr)
            } catch (_: Throwable) {
                throw InvalidBrushPresetException("Invalid tipShape '$tipShapeStr' in preset '$presetName'")
            }

            val tipAngleDegrees = (map["tipAngleDegrees"] as? Number)?.toFloat() ?: 0.0f
            val followStrokeAngle = map["followStrokeAngle"] as? Boolean ?: false

            val startCapStr = map["startCap"] as? String ?: "ROUND"
            val startCap = try {
                StrokeCapMode.valueOf(startCapStr.uppercase())
            } catch (_: Throwable) {
                throw InvalidBrushPresetException("Invalid startCap '$startCapStr' in preset '$presetName'")
            }

            val endCapStr = map["endCap"] as? String ?: "ROUND"
            val endCap = try {
                StrokeCapMode.valueOf(endCapStr.uppercase())
            } catch (_: Throwable) {
                throw InvalidBrushPresetException("Invalid endCap '$endCapStr' in preset '$presetName'")
            }

            val stabilizationWeight = (map["stabilizationWeight"] as? Number)?.toFloat() ?: 0.0f
            if (stabilizationWeight !in 0.0f..0.95f) {
                throw InvalidBrushPresetException("Stabilization weight must be in [0.0, 0.95], got $stabilizationWeight in preset '$presetName'")
            }

            val mediaProperties = (map["mediaProperties"] as? Map<String, Any?>)?.let {
                PaintMediaProperties.fromMap(it)
            } ?: PaintMediaProperties()

            return BrushConfig(
                type = type,
                size = size,
                opacity = opacity,
                color = color,
                spacing = spacing,
                hardness = hardness,
                flow = flow,
                minSizeRatio = minSizeRatio,
                maxSizeRatio = maxSizeRatio,
                seed = seed,
                cap = cap,
                join = join,
                antiAlias = antiAlias,
                pressureResponse = pressureResponse,
                pressureGamma = pressureGamma,
                startTaperRatio = startTaperRatio,
                endTaperRatio = endTaperRatio,
                tipShape = tipShape,
                tipAngleDegrees = tipAngleDegrees,
                followStrokeAngle = followStrokeAngle,
                startCap = startCap,
                endCap = endCap,
                stabilizationWeight = stabilizationWeight,
                mediaProperties = mediaProperties
            )
        }

        fun fromJson(json: String): BrushPreset {
            val parsed = try {
                ArtJson.parse(json)
            } catch (e: Throwable) {
                throw InvalidBrushPresetException("Malformed JSON syntax in brush preset: ${e.message}", e)
            }

            @Suppress("UNCHECKED_CAST")
            val map = parsed as? Map<String, Any?>
                ?: throw InvalidBrushPresetException("Brush preset JSON must be an object")

            return fromMap(map)
        }
    }
}
