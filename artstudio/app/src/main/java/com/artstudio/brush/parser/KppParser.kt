package com.artstudio.brush.parser

import com.artstudio.brush.BrushPreset
import com.artstudio.core.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class KppParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Parsed output from a Krita (.kpp) preset container or XML specification.
 */
data class ParsedKppBrush(
    val preset: BrushPreset,
    val mediaProperties: PaintMediaProperties,
    val thumbnailPngBytes: ByteArray? = null
)

/**
 * Parser for Krita .kpp paint operation preset XML definitions and ZIP preset packages.
 * Enforces strict XML parsing without reflection, mapping Krita attributes into
 * native ArtStudio BrushPreset and PaintMediaProperties representations.
 */
object KppParser {

    /**
     * Parses a .kpp file from an input stream (which may be a ZIP containing paintoppresets/name.kpp
     * or a direct XML stream).
     */
    fun parse(inputStream: InputStream, filename: String = "preset.kpp"): ParsedKppBrush {
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            throw KppParseException("Failed reading KPP stream: ${e.message}", e)
        }
        if (bytes.isEmpty()) {
            throw KppParseException("Empty KPP file: $filename")
        }

        // Check if bytes represent a ZIP container (PK\x03\x04)
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return parseFromZipBytes(bytes, filename)
        }

        // Direct XML
        val xmlString = try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw KppParseException("Failed decoding XML text: ${e.message}", e)
        }
        return parseXml(xmlString, filename.substringBeforeLast('.'))
    }

    private fun parseFromZipBytes(zipBytes: ByteArray, filename: String): ParsedKppBrush {
        var kppXml: String? = null
        var thumbnailBytes: ByteArray? = null
        val defaultName = filename.substringBeforeLast('.')

        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.endsWith(".kpp") || name.startsWith("paintoppresets/")) {
                        kppXml = String(zis.readBytes(), Charsets.UTF_8)
                    } else if (name.endsWith(".png") && (name.contains("preview") || name.contains("mergedimage") || name.contains("thumbnail"))) {
                        thumbnailBytes = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            throw KppParseException("Failed extracting KPP ZIP container: ${e.message}", e)
        }

        if (kppXml == null) {
            throw KppParseException("No .kpp preset XML found inside ZIP archive: $filename")
        }

        val parsed = parseXml(kppXml!!, defaultName)
        return if (thumbnailBytes != null) {
            parsed.copy(thumbnailPngBytes = thumbnailBytes)
        } else {
            parsed
        }
    }

    /**
     * Parses Krita XML text.
     */
    fun parseXml(xml: String, fallbackName: String = "Krita Preset"): ParsedKppBrush {
        if (xml.isBlank()) {
            throw KppParseException("KPP XML content is blank")
        }

        // Explicit security gate: Reject DOCTYPE declarations and custom/external entities
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
            throw KppParseException("DOCTYPE and entity declarations are forbidden in KPP XML")
        }

        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                try {
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                } catch (_: Throwable) {}
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { publicId, systemId ->
                throw SecurityException("External entity resolution forbidden in KPP XML: $publicId, $systemId")
            }
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            val root = doc.documentElement ?: throw KppParseException("Missing root element in KPP XML")

            var brushName = fallbackName
            if (root.hasAttribute("name") && root.getAttribute("name").isNotBlank()) {
                brushName = root.getAttribute("name")
            }

            var sizePx = 20.0f
            var opacity = 1.0f
            var flow = 1.0f
            var spacing = 0.10f
            var tipShape = BrushTipShape.ROUND
            var patternShape = StampPatternShape.SPONGE
            var textureRef: String? = null
            var isBitmap = false
            var pressureSize = true
            var pressureOpacity = false

            val nodeList = root.getElementsByTagName("*")
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val elem = node as Element
                    val tagName = elem.tagName.lowercase()
                    when (tagName) {
                        "name" -> {
                            val text = elem.textContent.trim()
                            if (text.isNotBlank()) brushName = text
                        }
                        "param", "property" -> {
                            val pName = (elem.getAttribute("name") ?: "").lowercase()
                            val pVal = (if (elem.hasAttribute("value")) elem.getAttribute("value") else elem.textContent).trim()

                            when {
                                pName.contains("brush_tip/shape") || pName.contains("brush_tip.shape") -> {
                                    patternShape = when (pVal.lowercase()) {
                                        "canvas", "canvas_grain" -> StampPatternShape.CANVAS_GRAIN
                                        "speckle" -> StampPatternShape.SPECKLE
                                        "leaf", "leaf_organic" -> StampPatternShape.LEAF_ORGANIC
                                        else -> StampPatternShape.SPONGE
                                    }
                                }
                                pName.contains("brush_tip/brush_type") || pName.contains("brush_tip.brush_type") -> {
                                    isBitmap = pVal.contains("image", ignoreCase = true) || pVal.contains("bitmap", ignoreCase = true)
                                }
                                pName.contains("brush_size") || pName.contains("diameter") -> {
                                    sizePx = pVal.toFloatOrNull()?.coerceIn(1.0f, 500.0f) ?: sizePx
                                }
                                pName.contains("opacity") -> {
                                    opacity = pVal.toFloatOrNull()?.let { if (it > 1.0f) it / 100f else it }?.coerceIn(0.01f, 1.0f) ?: opacity
                                }
                                pName.contains("flow") -> {
                                    flow = pVal.toFloatOrNull()?.let { if (it > 1.0f) it / 100f else it }?.coerceIn(0.01f, 1.0f) ?: flow
                                }
                                pName.contains("spacing") -> {
                                    spacing = pVal.toFloatOrNull()?.coerceIn(0.01f, 1.0f) ?: spacing
                                }
                                pName.contains("pressuresize") || pName.contains("pressure_curve_size") -> {
                                    pressureSize = pVal.toBooleanStrictOrNull() ?: true
                                }
                                pName.contains("pressureopacity") || pName.contains("pressure_curve_opacity") -> {
                                    pressureOpacity = pVal.toBooleanStrictOrNull() ?: false
                                }
                                pName.contains("texture") || pName.contains("pattern") -> {
                                    textureRef = pVal
                                }
                            }
                        }
                    }
                }
            }

            val mediaProps = PaintMediaProperties(
                family = PaintMediaFamily.TEXTURED_STAMP,
                stampShape = patternShape,
                textureAssetId = textureRef,
                dryPressureSensitivity = if (pressureSize) 0.75f else 0.25f
            )

            val brushConfig = BrushConfig(
                type = if (isBitmap) BrushType.INK_BRUSH else BrushType.HARD_PEN,
                size = sizePx,
                opacity = opacity * flow,
                hardness = 0.85f,
                spacing = spacing,
                tipShape = tipShape,
                pressureResponse = if (pressureOpacity) PressureResponseMode.SOFT else PressureResponseMode.LINEAR,
                mediaProperties = mediaProps
            )

            val preset = BrushPreset(
                id = "kpp_${UUID.randomUUID().toString().take(8)}",
                name = brushName,
                category = "Imported Krita",
                isFavorite = false,
                isBuiltIn = false,
                tags = listOf("krita", "imported", "kpp"),
                config = brushConfig
            )

            return ParsedKppBrush(preset, mediaProps)
        } catch (e: KppParseException) {
            throw e
        } catch (e: Exception) {
            throw KppParseException("Malformed KPP XML: ${e.message}", e)
        }
    }
}
