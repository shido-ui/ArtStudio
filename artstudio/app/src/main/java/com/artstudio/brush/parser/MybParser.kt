package com.artstudio.brush.parser

import com.artstudio.brush.BrushPreset
import com.artstudio.core.*
import com.artstudio.document.io.ArtJson
import java.io.InputStream
import java.util.UUID
import kotlin.math.exp

class MybParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Parsed output from a MyPaint (.myb) JSON brush preset.
 */
data class ParsedMybBrush(
    val preset: BrushPreset,
    val mediaProperties: PaintMediaProperties
)

/**
 * Parser for MyPaint v1 & v2 JSON brush (.myb) definitions.
 * Maps logarithmic brush radius, dynamic opacity, smudge settings, and dabs-per-second
 * into ArtStudio's [BrushPreset] and [PaintMediaProperties] engine.
 */
object MybParser {

    fun parse(inputStream: InputStream, filename: String = "brush.myb"): ParsedMybBrush {
        val jsonText = try {
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            throw MybParseException("Failed reading MYB stream: ${e.message}", e)
        }
        return parseJson(jsonText, filename.substringBeforeLast('.'))
    }

    @Suppress("UNCHECKED_CAST")
    fun parseJson(jsonText: String, fallbackName: String = "MyPaint Brush"): ParsedMybBrush {
        if (jsonText.isBlank()) {
            throw MybParseException("MYB JSON text is blank")
        }

        val jsonMap = try {
            ArtJson.parse(jsonText) as? Map<String, Any?>
                ?: throw MybParseException("Root JSON element is not an object")
        } catch (e: MybParseException) {
            throw e
        } catch (e: Exception) {
            throw MybParseException("Malformed MYB JSON syntax: ${e.message}", e)
        }

        // Settings can be in "settings" object (v2) or flat at root (v1)
        val settings = (jsonMap["settings"] as? Map<String, Any?>) ?: jsonMap

        fun extractFloat(key: String, default: Float): Float {
            val raw = settings[key] ?: return default
            if (raw is Number) return raw.toFloat()
            if (raw is Map<*, *>) {
                val base = (raw as Map<String, Any?>)["base_value"]
                if (base is Number) return base.toFloat()
            }
            return raw.toString().toFloatOrNull() ?: default
        }

        // 1. Radius: radius_logarithmic -> radius = exp(r_log)
        val rLog = extractFloat("radius_logarithmic", 2.0f)
        val radiusPx = exp(rLog.toDouble()).toFloat().coerceIn(1.0f, 250.0f)
        val sizePx = (radiusPx * 2.0f).coerceIn(2.0f, 500.0f)

        // 2. Opacity
        val opaque = extractFloat("opaque", 0.8f).coerceIn(0.0f, 1.0f)
        val opaqueMultiply = extractFloat("opaque_multiply", 1.0f).coerceIn(0.0f, 2.0f)
        val finalOpacity = (opaque * opaqueMultiply).coerceIn(0.05f, 1.0f)

        // 3. Spacing / Dabs
        val dabsPerSec = extractFloat("dabs_per_second", 15.0f).coerceIn(0.5f, 100.0f)
        val spacing = (1.0f / (dabsPerSec * 0.5f)).coerceIn(0.02f, 1.0f)

        // 4. Smudge
        val smudge = extractFloat("smudge", 0.0f).coerceIn(0.0f, 1.0f)
        val smudgeLength = extractFloat("smudge_length", 0.5f).coerceIn(0.0f, 1.0f)

        // 5. Hardness
        val hardness = extractFloat("hardness", 0.8f).coerceIn(0.0f, 1.0f)

        // 6. Name
        val brushName = (jsonMap["name"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackName

        val brushType = if (smudge > 0.1f) BrushType.INK_BRUSH else BrushType.HARD_PEN

        val config = BrushConfig(
            type = brushType,
            size = sizePx,
            opacity = finalOpacity,
            hardness = hardness,
            spacing = spacing,
            pressureResponse = PressureResponseMode.LINEAR
        )

        val mediaProps = PaintMediaProperties(
            family = if (smudge > 0.1f) PaintMediaFamily.OIL_PAINT else PaintMediaFamily.STANDARD,
            viscosity = (1.0f - smudgeLength).coerceIn(0.1f, 1.0f),
            paintLoading = finalOpacity.coerceIn(0.1f, 1.0f)
        )

        val preset = BrushPreset(
            id = "myb_${UUID.randomUUID().toString().take(8)}",
            name = brushName,
            category = "Imported MyPaint",
            isFavorite = false,
            isBuiltIn = false,
            tags = listOf("mypaint", "imported", "myb"),
            config = config
        )

        return ParsedMybBrush(preset, mediaProps)
    }
}
