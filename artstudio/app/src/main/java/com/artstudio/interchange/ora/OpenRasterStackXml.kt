package com.artstudio.interchange.ora

import com.artstudio.document.model.BlendModeType
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Serializer and parser for OpenRaster `stack.xml` manifests.
 */
object OpenRasterStackXml {

    /**
     * Maps ArtStudio internal [BlendModeType] to standard OpenRaster `composite-op` attribute.
     */
    fun blendModeToCompositeOp(blendMode: BlendModeType): String {
        return when (blendMode) {
            BlendModeType.Normal -> "svg:src-over"
            BlendModeType.Multiply -> "svg:multiply"
            BlendModeType.Screen -> "svg:screen"
            BlendModeType.Overlay -> "svg:overlay"
            BlendModeType.Darken -> "svg:darken"
            BlendModeType.Lighten -> "svg:lighten"
            BlendModeType.ColorDodge -> "svg:color-dodge"
            BlendModeType.Add -> "svg:plus"
            else -> "svg:src-over"
        }
    }

    /**
     * Maps an OpenRaster `composite-op` attribute string to ArtStudio [BlendModeType].
     * Returns a pair of the resolved [BlendModeType] and a boolean flag indicating if the operator was unsupported.
     */
    fun compositeOpToBlendMode(compositeOp: String?): Pair<BlendModeType, Boolean> {
        val op = compositeOp?.trim()?.lowercase() ?: return Pair(BlendModeType.Normal, false)
        return when (op) {
            "svg:src-over", "src-over", "" -> Pair(BlendModeType.Normal, false)
            "svg:multiply", "multiply" -> Pair(BlendModeType.Multiply, false)
            "svg:screen", "screen" -> Pair(BlendModeType.Screen, false)
            "svg:overlay", "overlay" -> Pair(BlendModeType.Overlay, false)
            "svg:darken", "darken" -> Pair(BlendModeType.Darken, false)
            "svg:lighten", "lighten" -> Pair(BlendModeType.Lighten, false)
            "svg:color-dodge", "color-dodge" -> Pair(BlendModeType.ColorDodge, false)
            "svg:plus", "plus", "svg:add", "add" -> Pair(BlendModeType.Add, false)
            else -> Pair(BlendModeType.Normal, true) // Unsupported blend mode, fallback to Normal
        }
    }

    /**
     * Parses OpenRaster `stack.xml` from XML string.
     */
    fun parseStackXml(xmlString: String): OraImage {
        // Explicit security gate: Reject DOCTYPE declarations and custom/external entities on Android
        if (xmlString.contains("<!DOCTYPE", ignoreCase = true) || xmlString.contains("<!ENTITY", ignoreCase = true)) {
            throw SecurityException("DOCTYPE declarations and custom entities are strictly disallowed in OpenRaster stack.xml")
        }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            trySetFeature(this, javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
            trySetFeature(this, "http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature(this, "http://xml.org/sax/features/external-general-entities", false)
            trySetFeature(this, "http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature(this, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { publicId, systemId ->
            throw SecurityException("External entity resolution is forbidden in stack.xml (publicId=$publicId, systemId=$systemId)")
        }
        val doc = try {
            builder.parse(ByteArrayInputStream(xmlString.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            throw com.artstudio.document.io.CorruptDocumentException("Malformed XML in stack.xml: ${e.message}", e)
        }
        val root = doc.documentElement ?: throw com.artstudio.document.io.CorruptDocumentException("Missing root element in stack.xml")

        val width = root.getAttribute("width").toIntOrNull()
            ?: root.getAttribute("w").toIntOrNull()
            ?: 1024
        val height = root.getAttribute("height").toIntOrNull()
            ?: root.getAttribute("h").toIntOrNull()
            ?: 1024
        val version = root.getAttribute("version").ifEmpty { "0.0.3" }

        // Find root <stack>
        var rootStackElement: Element? = null
        val childNodes = root.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName.equals("stack", ignoreCase = true)) {
                rootStackElement = node as Element
                break
            }
        }

        val rootStack = if (rootStackElement != null) {
            parseStackElement(rootStackElement)
        } else {
            OraNode.Stack(name = "Root")
        }

        return OraImage(
            version = version,
            width = width,
            height = height,
            rootStack = rootStack
        )
    }

    private fun parseStackElement(element: Element): OraNode.Stack {
        val name = element.getAttribute("name").ifEmpty { "Group" }
        val opacity = element.getAttribute("opacity").toFloatOrNull() ?: 1.0f
        val visibilityStr = element.getAttribute("visibility").trim().lowercase()
        val visible = visibilityStr != "hidden"
        val compositeOp = element.getAttribute("composite-op").ifEmpty { "svg:src-over" }

        val children = mutableListOf<OraNode>()
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) {
                val el = n as Element
                when (el.nodeName.lowercase()) {
                    "layer" -> {
                        val layerName = el.getAttribute("name").ifEmpty { "Layer" }
                        val src = el.getAttribute("src").ifEmpty { "data/layer.png" }
                        val x = el.getAttribute("x").toIntOrNull() ?: 0
                        val y = el.getAttribute("y").toIntOrNull() ?: 0
                        val layerOpacity = el.getAttribute("opacity").toFloatOrNull() ?: 1.0f
                        val layerVisStr = el.getAttribute("visibility").trim().lowercase()
                        val layerVisible = layerVisStr != "hidden"
                        val layerCompOp = el.getAttribute("composite-op").ifEmpty { "svg:src-over" }

                        children.add(
                            OraNode.Layer(
                                name = layerName,
                                src = src,
                                x = x,
                                y = y,
                                opacity = layerOpacity.coerceIn(0f, 1f),
                                visible = layerVisible,
                                compositeOp = layerCompOp
                            )
                        )
                    }
                    "stack" -> {
                        children.add(parseStackElement(el))
                    }
                }
            }
        }

        return OraNode.Stack(
            name = name,
            opacity = opacity.coerceIn(0f, 1f),
            visible = visible,
            compositeOp = compositeOp,
            children = children
        )
    }

    /**
     * Generates standard OpenRaster `stack.xml` string.
     */
    fun generateStackXml(image: OraImage): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<image version=\"${escapeXml(image.version)}\" width=\"${image.width}\" height=\"${image.height}\">")
        writeStackNode(sb, image.rootStack, indent = 2)
        sb.appendLine("</image>")
        return sb.toString()
    }

    private fun writeStackNode(sb: StringBuilder, stack: OraNode.Stack, indent: Int) {
        val spaces = " ".repeat(indent)
        val nameAttr = if (stack.name.isNotEmpty() && stack.name != "Root") " name=\"${escapeXml(stack.name)}\"" else ""
        val opAttr = if (stack.opacity < 1.0f) " opacity=\"${stack.opacity}\"" else ""
        val visAttr = if (!stack.visible) " visibility=\"hidden\"" else ""
        val compAttr = if (stack.compositeOp != "svg:src-over") " composite-op=\"${escapeXml(stack.compositeOp)}\"" else ""

        sb.appendLine("$spaces<stack$nameAttr$opAttr$visAttr$compAttr>")
        for (child in stack.children) {
            when (child) {
                is OraNode.Layer -> {
                    val childSpaces = " ".repeat(indent + 2)
                    val cName = " name=\"${escapeXml(child.name)}\""
                    val cSrc = " src=\"${escapeXml(child.src)}\""
                    val cX = if (child.x != 0) " x=\"${child.x}\"" else ""
                    val cY = if (child.y != 0) " y=\"${child.y}\"" else ""
                    val cOp = if (child.opacity < 1.0f) " opacity=\"${child.opacity}\"" else " opacity=\"1.0\""
                    val cVis = if (child.visible) " visibility=\"visible\"" else " visibility=\"hidden\""
                    val cComp = if (child.compositeOp.isNotEmpty()) " composite-op=\"${escapeXml(child.compositeOp)}\"" else ""

                    sb.appendLine("$childSpaces<layer$cName$cSrc$cX$cY$cOp$cVis$cComp/>")
                }
                is OraNode.Stack -> {
                    writeStackNode(sb, child, indent + 2)
                }
            }
        }
        sb.appendLine("$spaces</stack>")
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun trySetFeature(factory: DocumentBuilderFactory, feature: String, value: Boolean) {
        try {
            factory.setFeature(feature, value)
        } catch (e: Exception) {
            android.util.Log.d("OpenRasterStackXml", "Feature $feature not supported: ${e.message}")
        }
    }
}
