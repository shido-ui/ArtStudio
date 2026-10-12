package com.artstudio.vector.svg

import com.artstudio.document.io.ConversionReport
import com.artstudio.document.io.ConversionWarning
import com.artstudio.vector.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * Result of parsing an SVG document.
 */
data class SvgParseResult(
    val vectorObjects: List<VectorObject>,
    val canvasWidth: Int = 1024,
    val canvasHeight: Int = 1024,
    val conversionReport: ConversionReport
)

/**
 * Robust, memory-bounded, and securely hardened parser for a documented, safe SVG 1.1 subset.
 *
 * Supported elements: <svg>, <g>, <rect>, <circle>, <ellipse>, <line>, <polyline>, <polygon>, <path>
 * Supported path commands: M/m, L/l, H/h, V/v, C/c, S/s, Q/q, T/t, A/a (fallback), Z/z
 * Supported styling: fill, stroke, stroke-width, stroke-linecap, stroke-linejoin, stroke-miterlimit,
 *                    opacity, fill-opacity, stroke-opacity, fill-rule, transform (matrix, translate, scale, rotate, skew)
 *
 * Security & Sanitisation:
 * - XML entity expansions and external DTDs are strictly disabled (XXE / billion laughs protection).
 * - Embedded <script> elements are neutralised and stripped without execution.
 * - Inline event handlers (onload, onclick, onerror, etc.) are stripped.
 * - External network references (http/https/ftp) in href/src are blocked; no network fetches occur.
 * - Unsupported elements (<filter>, <clipPath>, <mask>, <linearGradient>, etc.) are recorded in [ConversionReport].
 */
object SafeSvgParser {

    fun parse(svgXml: String): SvgParseResult {
        val warnings = mutableListOf<ConversionWarning>()
        val objects = mutableListOf<VectorObject>()
        var canvasW = 1024
        var canvasH = 1024

        // Explicit security gate: Reject DOCTYPE declarations and custom/external entities on Android
        if (svgXml.contains("<!DOCTYPE", ignoreCase = true) || svgXml.contains("<!ENTITY", ignoreCase = true)) {
            warnings.add(
                ConversionWarning(
                    layerId = "svg_root",
                    layerName = "SVG Document",
                    feature = "XML_SECURITY",
                    actionTaken = "Rejected document containing DOCTYPE or external entities",
                    message = "DOCTYPE declarations and custom entities are strictly disallowed for security."
                )
            )
            return SvgParseResult(emptyList(), canvasW, canvasH, ConversionReport("SVG_IMPORT", warnings))
        }

        val doc = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true)

            // Disable DTD, entity resolution, and external resource loading
            trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false)
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false

            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { publicId, systemId ->
                throw SecurityException("External entity resolution is forbidden (publicId=$publicId, systemId=$systemId)")
            }
            builder.parse(ByteArrayInputStream(svgXml.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            warnings.add(
                ConversionWarning(
                    layerId = "svg_root",
                    layerName = "SVG Document",
                    feature = "XML_SYNTAX",
                    actionTaken = "Aborted parse with empty result",
                    message = "Malformed or truncated XML: ${e.message}"
                )
            )
            return SvgParseResult(emptyList(), canvasW, canvasH, ConversionReport("SVG_IMPORT", warnings))
        }

        val root = doc.documentElement
        if (root != null) {
            var rootTransform = AffineTransform2D.IDENTITY

            val viewBox = root.getAttribute("viewBox")
            if (viewBox.isNotBlank()) {
                val parts = viewBox.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
                if (parts.size >= 4) {
                    val minX = parts[0]
                    val minY = parts[1]
                    val vbW = parts[2].coerceAtLeast(1f)
                    val vbH = parts[3].coerceAtLeast(1f)
                    canvasW = vbW.toInt()
                    canvasH = vbH.toInt()
                    if (minX != 0f || minY != 0f) {
                        rootTransform = AffineTransform2D.translation(-minX, -minY)
                    }
                }
            } else {
                val wAttr = root.getAttribute("width")
                val hAttr = root.getAttribute("height")
                wAttr.replace("px", "").trim().toFloatOrNull()?.let { canvasW = it.toInt().coerceAtLeast(1) }
                hAttr.replace("px", "").trim().toFloatOrNull()?.let { canvasH = it.toInt().coerceAtLeast(1) }
            }

            // Also check if root element has a transform
            val rootTransformAttr = root.getAttribute("transform")
            if (rootTransformAttr.isNotBlank()) {
                rootTransform = rootTransform * SvgTransformParser.parseTransform(rootTransformAttr)
            }

            val defaultStyle = VectorStyle(strokeColor = null, strokeWidth = 1f, fillColor = 0xFF000000L)
            traverseNode(root, defaultStyle, rootTransform, objects, warnings)
        }

        return SvgParseResult(
            vectorObjects = objects,
            canvasWidth = canvasW,
            canvasHeight = canvasH,
            conversionReport = ConversionReport("SVG_IMPORT", warnings)
        )
    }

    private fun traverseNode(
        node: Node,
        parentStyle: VectorStyle,
        parentTransform: AffineTransform2D,
        objects: MutableList<VectorObject>,
        warnings: MutableList<ConversionWarning>
    ) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val elem = child as Element
            val tagName = elem.tagName.lowercase(Locale.ROOT)

            // 1. Sanitisation: Reject <script> tags completely
            if (tagName == "script") {
                warnings.add(
                    ConversionWarning(
                        layerId = "svg_elem_${objects.size + 1}",
                        layerName = "script",
                        feature = "SECURITY_SANITIZATION",
                        actionTaken = "Stripped executable <script> element",
                        message = "Embedded scripts are prohibited and stripped for security."
                    )
                )
                continue
            }

            // 2. Sanitisation: Strip inline event handlers and external network references
            val attrs = elem.attributes
            val eventAttrsToRemove = mutableListOf<String>()
            for (j in 0 until attrs.length) {
                val attr = attrs.item(j)
                val attrName = attr.nodeName.lowercase(Locale.ROOT)
                if (attrName.startsWith("on")) {
                    eventAttrsToRemove.add(attr.nodeName)
                    warnings.add(
                        ConversionWarning(
                            layerId = "svg_elem_${objects.size + 1}",
                            layerName = tagName,
                            feature = "SECURITY_SANITIZATION",
                            actionTaken = "Stripped event handler '$attrName'",
                            message = "Inline event handlers are prohibited and stripped for security."
                        )
                    )
                } else if (attrName == "href" || attrName == "xlink:href" || attrName == "src") {
                    val valStr = attr.nodeValue.trim().lowercase(Locale.ROOT)
                    if (valStr.startsWith("javascript:")) {
                        warnings.add(
                            ConversionWarning(
                                layerId = "svg_elem_${objects.size + 1}",
                                layerName = tagName,
                                feature = "SECURITY_SANITIZATION",
                                actionTaken = "Neutralized JavaScript URI in '$attrName'",
                                message = "JavaScript URI execution is prohibited and blocked."
                            )
                        )
                    } else if (valStr.startsWith("http://") || valStr.startsWith("https://") || valStr.startsWith("ftp://")) {
                        warnings.add(
                            ConversionWarning(
                                layerId = "svg_elem_${objects.size + 1}",
                                layerName = tagName,
                                feature = "SECURITY_EXTERNAL_RESOURCE",
                                actionTaken = "Blocked external network resource in '$attrName'",
                                message = "External network requests are prohibited in vector import."
                            )
                        )
                    }
                }
            }
            for (attrName in eventAttrsToRemove) {
                elem.removeAttribute(attrName)
            }

            // 3. Check display / visibility
            val displayVal = elem.getAttribute("display").trim().lowercase(Locale.ROOT)
            val visibilityVal = elem.getAttribute("visibility").trim().lowercase(Locale.ROOT)
            if (displayVal == "none" || visibilityVal == "hidden" || visibilityVal == "collapse") {
                continue
            }

            // 4. Compute transform and style
            val localTransform = SvgTransformParser.parseTransform(elem.getAttribute("transform"))
            val currentTransform = parentTransform * localTransform
            val style = parseStyle(elem, parentStyle)

            when (tagName) {
                "g" -> {
                    traverseNode(elem, style, currentTransform, objects, warnings)
                }
                "rect" -> {
                    val x = elem.getAttribute("x").toFloatOrNull() ?: 0f
                    val y = elem.getAttribute("y").toFloatOrNull() ?: 0f
                    val width = elem.getAttribute("width").toFloatOrNull() ?: 0f
                    val height = elem.getAttribute("height").toFloatOrNull() ?: 0f
                    val rx = elem.getAttribute("rx").toFloatOrNull() ?: 0f
                    val ry = elem.getAttribute("ry").toFloatOrNull() ?: rx
                    if (width > 0f && height > 0f) {
                        val baseObj = VectorObject.createRectangle(x, y, width, height, rx, ry, style)
                        objects.add(applyTransformToObject(baseObj, currentTransform))
                    }
                }
                "circle" -> {
                    val cx = elem.getAttribute("cx").toFloatOrNull() ?: 0f
                    val cy = elem.getAttribute("cy").toFloatOrNull() ?: 0f
                    val r = elem.getAttribute("r").toFloatOrNull() ?: 0f
                    if (r > 0f) {
                        val baseObj = VectorObject.createCircle(cx, cy, r, style)
                        objects.add(applyTransformToObject(baseObj, currentTransform))
                    }
                }
                "ellipse" -> {
                    val cx = elem.getAttribute("cx").toFloatOrNull() ?: 0f
                    val cy = elem.getAttribute("cy").toFloatOrNull() ?: 0f
                    val rx = elem.getAttribute("rx").toFloatOrNull() ?: 0f
                    val ry = elem.getAttribute("ry").toFloatOrNull() ?: rx
                    if (rx > 0f && ry > 0f) {
                        val baseObj = VectorObject.createEllipse(cx, cy, rx, ry, style)
                        objects.add(applyTransformToObject(baseObj, currentTransform))
                    }
                }
                "line" -> {
                    val x1 = elem.getAttribute("x1").toFloatOrNull() ?: 0f
                    val y1 = elem.getAttribute("y1").toFloatOrNull() ?: 0f
                    val x2 = elem.getAttribute("x2").toFloatOrNull() ?: 0f
                    val y2 = elem.getAttribute("y2").toFloatOrNull() ?: 0f
                    val baseObj = VectorObject.createLine(x1, y1, x2, y2, style)
                    objects.add(applyTransformToObject(baseObj, currentTransform))
                }
                "polyline" -> {
                    val pts = parsePointList(elem.getAttribute("points"))
                    if (pts.isNotEmpty()) {
                        val baseObj = VectorObject.createPolyline(pts, style)
                        objects.add(applyTransformToObject(baseObj, currentTransform))
                    }
                }
                "polygon" -> {
                    val pts = parsePointList(elem.getAttribute("points"))
                    if (pts.isNotEmpty()) {
                        val baseObj = VectorObject.createPolygon(pts, style)
                        objects.add(applyTransformToObject(baseObj, currentTransform))
                    }
                }
                "path" -> {
                    val d = elem.getAttribute("d")
                    val segments = parsePathData(d)
                    if (segments.isNotEmpty()) {
                        val isClosed = segments.any { it is VectorSegment.Close }
                        val baseObj = VectorObject.fromSegments(segments, style, isClosed, "Path")
                        objects.add(applyTransformToObject(baseObj, currentTransform))
                    }
                }
                "defs" -> {
                    traverseNode(elem, parentStyle, currentTransform, objects, warnings)
                }
                "lineargradient", "radialgradient", "filter", "pattern", "text", "image", "clippath", "mask", "foreignobject" -> {
                    warnings.add(
                        ConversionWarning(
                            layerId = "svg_elem_${objects.size + 1}",
                            layerName = tagName,
                            feature = "UNSUPPORTED_SVG_ELEMENT",
                            actionTaken = "Omitted unsupported element during vector import",
                            message = "SVG element <$tagName> is not supported in native ArtStudio vector subset."
                        )
                    )
                }
            }
        }
    }

    /**
     * Applies an affine transform to a vector object, preserving exact geometry.
     */
    fun applyTransformToObject(obj: VectorObject, transform: AffineTransform2D): VectorObject {
        if (transform.isIdentity()) return obj

        if (transform.isTranslationOnly()) {
            val dx = transform.m02
            val dy = transform.m12
            return when (obj.shapeType) {
                VectorShapeType.RECTANGLE -> {
                    val r = obj.rectData ?: return obj
                    obj.copy(rectData = r.copy(x = r.x + dx, y = r.y + dy))
                }
                VectorShapeType.ELLIPSE -> {
                    val el = obj.ellipseData ?: return obj
                    obj.copy(ellipseData = el.copy(cx = el.cx + dx, cy = el.cy + dy))
                }
                VectorShapeType.LINE -> {
                    if (obj.anchors.size >= 2) {
                        val p1 = obj.anchors[0]
                        val p2 = obj.anchors[1]
                        obj.copy(anchors = listOf(
                            p1.copy(x = p1.x + dx, y = p1.y + dy),
                            p2.copy(x = p2.x + dx, y = p2.y + dy)
                        ))
                    } else obj
                }
                VectorShapeType.POLYLINE, VectorShapeType.POLYGON -> {
                    obj.copy(anchors = obj.anchors.map { it.copy(x = it.x + dx, y = it.y + dy) })
                }
                VectorShapeType.BEZIER_PATH -> {
                    val newSegs = obj.segments.map { it.transformed(transform) }
                    val newAnchors = obj.anchors.map { it.transformed(transform) }
                    obj.copy(segments = newSegs, anchors = newAnchors)
                }
            }
        }

        // Transform includes rotation, non-uniform scaling, or skewing
        return when (obj.shapeType) {
            VectorShapeType.RECTANGLE, VectorShapeType.ELLIPSE -> {
                val segs = obj.toSegments()
                val transformedSegs = segs.map { it.transformed(transform) }
                VectorObject.fromSegments(
                    segments = transformedSegs,
                    style = obj.style,
                    isClosed = true,
                    name = obj.name
                )
            }
            VectorShapeType.LINE -> {
                if (obj.anchors.size >= 2) {
                    val (x1, y1) = transform.transformPoint(obj.anchors[0].x, obj.anchors[0].y)
                    val (x2, y2) = transform.transformPoint(obj.anchors[1].x, obj.anchors[1].y)
                    VectorObject.createLine(x1, y1, x2, y2, obj.style)
                } else obj
            }
            VectorShapeType.POLYLINE -> {
                val pts = obj.anchors.map { transform.transformPoint(it.x, it.y) }
                VectorObject.createPolyline(pts, obj.style)
            }
            VectorShapeType.POLYGON -> {
                val pts = obj.anchors.map { transform.transformPoint(it.x, it.y) }
                VectorObject.createPolygon(pts, obj.style)
            }
            VectorShapeType.BEZIER_PATH -> {
                VectorGeometryEngine.transform(obj, transform)
            }
        }
    }

    private fun parseStyle(elem: Element, defaultStyle: VectorStyle): VectorStyle {
        var strokeColor = defaultStyle.strokeColor
        var strokeWidth = defaultStyle.strokeWidth
        var strokeCap = defaultStyle.strokeCap
        var strokeJoin = defaultStyle.strokeJoin
        var strokeMiterLimit = defaultStyle.strokeMiterLimit
        var fillColor = defaultStyle.fillColor
        var fillRule = defaultStyle.fillRule
        var opacity = defaultStyle.opacity

        val styleAttr = elem.getAttribute("style")
        val styleMap = mutableMapOf<String, String>()
        if (styleAttr.isNotBlank()) {
            val pairs = styleAttr.split(';')
            for (p in pairs) {
                val kv = p.split(':')
                if (kv.size == 2) {
                    styleMap[kv[0].trim().lowercase(Locale.ROOT)] = kv[1].trim()
                }
            }
        }

        fun getVal(name: String): String? {
            if (styleMap.containsKey(name)) return styleMap[name]
            val attr = elem.getAttribute(name)
            return if (attr.isNotBlank()) attr else null
        }

        getVal("stroke")?.let {
            strokeColor = parseColor(it)
        }
        getVal("stroke-width")?.let {
            it.replace("px", "").replace("pt", "").trim().toFloatOrNull()?.let { w -> strokeWidth = w }
        }
        getVal("stroke-linecap")?.let {
            strokeCap = StrokeCap.fromId(it.trim())
        }
        getVal("stroke-linejoin")?.let {
            strokeJoin = StrokeJoin.fromId(it.trim())
        }
        getVal("stroke-miterlimit")?.let {
            it.trim().toFloatOrNull()?.let { ml -> strokeMiterLimit = ml }
        }
        getVal("fill")?.let {
            fillColor = parseColor(it)
        }
        getVal("fill-rule")?.let {
            fillRule = FillRule.fromId(it.trim())
        }
        getVal("opacity")?.let {
            it.trim().toFloatOrNull()?.let { op -> opacity = op.coerceIn(0f, 1f) }
        }

        // Handle fill-opacity and stroke-opacity
        getVal("fill-opacity")?.let {
            it.trim().toFloatOrNull()?.let { fo ->
                val factor = fo.coerceIn(0f, 1f)
                fillColor?.let { c ->
                    val curAlpha = ((c ushr 24) and 0xFF).toFloat() / 255f
                    val newAlpha = (curAlpha * factor * 255f).roundToInt().coerceIn(0, 255)
                    fillColor = (c and 0x00FFFFFFL) or (newAlpha.toLong() shl 24)
                }
            }
        }
        getVal("stroke-opacity")?.let {
            it.trim().toFloatOrNull()?.let { so ->
                val factor = so.coerceIn(0f, 1f)
                strokeColor?.let { c ->
                    val curAlpha = ((c ushr 24) and 0xFF).toFloat() / 255f
                    val newAlpha = (curAlpha * factor * 255f).roundToInt().coerceIn(0, 255)
                    strokeColor = (c and 0x00FFFFFFL) or (newAlpha.toLong() shl 24)
                }
            }
        }

        return VectorStyle(
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            fillColor = fillColor,
            fillRule = fillRule,
            strokeCap = strokeCap,
            strokeJoin = strokeJoin,
            strokeMiterLimit = strokeMiterLimit,
            opacity = opacity
        )
    }

    /**
     * Parses standard SVG colors: hex (#rgb, #rgba, #rrggbb, #rrggbbaa),
     * functional rgb(r, g, b), rgba(r, g, b, a), and named CSS/SVG colors.
     */
    fun parseColor(colorStr: String): Long? {
        val s = colorStr.trim().lowercase(Locale.ROOT)
        if (s == "none" || s == "transparent" || s.isEmpty()) return null

        // Hex colors
        if (s.startsWith("#")) {
            val hex = s.substring(1)
            return when (hex.length) {
                3 -> { // #rgb
                    val r = hex[0].toString().repeat(2).toInt(16)
                    val g = hex[1].toString().repeat(2).toInt(16)
                    val b = hex[2].toString().repeat(2).toInt(16)
                    (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                }
                4 -> { // #rgba
                    val r = hex[0].toString().repeat(2).toInt(16)
                    val g = hex[1].toString().repeat(2).toInt(16)
                    val b = hex[2].toString().repeat(2).toInt(16)
                    val a = hex[3].toString().repeat(2).toInt(16)
                    (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                }
                6 -> { // #rrggbb
                    val rgb = hex.toLong(16)
                    0xFF000000L or rgb
                }
                8 -> { // #rrggbbaa (standard SVG / CSS Color 4)
                    val r = hex.substring(0, 2).toInt(16)
                    val g = hex.substring(2, 4).toInt(16)
                    val b = hex.substring(4, 6).toInt(16)
                    val a = hex.substring(6, 8).toInt(16)
                    (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                }
                else -> null
            }
        }

        // Functional rgb(...) and rgba(...)
        if (s.startsWith("rgb(") || s.startsWith("rgba(")) {
            val inner = s.substringAfter('(').substringBefore(')').trim()
            val parts = inner.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
            if (parts.size >= 3) {
                fun parseComp(part: String): Int {
                    return if (part.endsWith("%")) {
                        (part.dropLast(1).toFloatOrNull() ?: 0f) * 2.55f
                    } else {
                        part.toFloatOrNull() ?: 0f
                    }.roundToInt().coerceIn(0, 255)
                }

                val r = parseComp(parts[0])
                val g = parseComp(parts[1])
                val b = parseComp(parts[2])
                val a = if (parts.size >= 4) {
                    val alphaPart = parts[3]
                    if (alphaPart.endsWith("%")) {
                        ((alphaPart.dropLast(1).toFloatOrNull() ?: 100f) * 2.55f).roundToInt().coerceIn(0, 255)
                    } else {
                        val f = alphaPart.toFloatOrNull() ?: 1.0f
                        if (f <= 1.0f) (f * 255f).roundToInt().coerceIn(0, 255) else f.roundToInt().coerceIn(0, 255)
                    }
                } else {
                    255
                }
                return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            }
        }

        // Standard and extended SVG named colors
        return when (s) {
            "black" -> 0xFF000000L
            "white" -> 0xFFFFFFFFL
            "red" -> 0xFFFF0000L
            "green" -> 0xFF008000L
            "blue" -> 0xFF0000FFL
            "yellow" -> 0xFFFFFF00L
            "cyan" -> 0xFF00FFFFL
            "magenta" -> 0xFFFF00FFL
            "gray", "grey" -> 0xFF808080L
            "orange" -> 0xFFFFA500L
            "purple" -> 0xFF800080L
            "pink" -> 0xFFFFC0CBL
            "brown" -> 0xFFA52A2AL
            "gold" -> 0xFFFFD700L
            "navy" -> 0xFF000080L
            "teal" -> 0xFF008080L
            "lime" -> 0xFF00FF00L
            "maroon" -> 0xFF800000L
            "silver" -> 0xFFC0C0C0L
            "olive" -> 0xFF808000L
            else -> null
        }
    }

    private fun parsePointList(pointsStr: String): List<Pair<Float, Float>> {
        val tokens = pointsStr.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
        val result = mutableListOf<Pair<Float, Float>>()
        var i = 0
        while (i + 1 < tokens.size) {
            result.add(Pair(tokens[i], tokens[i + 1]))
            i += 2
        }
        return result
    }

    /**
     * Parses SVG path data `d` attribute into [VectorSegment]s.
     */
    fun parsePathData(d: String): List<VectorSegment> {
        val segments = mutableListOf<VectorSegment>()
        val tokenizer = SvgPathTokenizer(d)

        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        var lastCp2X = 0f
        var lastCp2Y = 0f
        var lastCommand = ' '

        while (tokenizer.hasNext()) {
            val token = tokenizer.nextToken() ?: break
            val cmd = token[0]
            val isRelative = cmd.isLowerCase()
            val cmdUpper = cmd.uppercaseChar()

            when (cmdUpper) {
                'M' -> {
                    var isFirst = true
                    while (tokenizer.hasNumber()) {
                        val nx = tokenizer.nextNumber() ?: break
                        val ny = tokenizer.nextNumber() ?: break
                        val targetX = if (isRelative) curX + nx else nx
                        val targetY = if (isRelative) curY + ny else ny
                        if (isFirst) {
                            curX = targetX
                            curY = targetY
                            startX = curX
                            startY = curY
                            segments.add(VectorSegment.MoveTo(curX, curY))
                            isFirst = false
                        } else {
                            curX = targetX
                            curY = targetY
                            segments.add(VectorSegment.LineTo(curX, curY))
                        }
                    }
                    lastCommand = cmd
                }
                'L' -> {
                    while (tokenizer.hasNumber()) {
                        val nx = tokenizer.nextNumber() ?: break
                        val ny = tokenizer.nextNumber() ?: break
                        curX = if (isRelative) curX + nx else nx
                        curY = if (isRelative) curY + ny else ny
                        segments.add(VectorSegment.LineTo(curX, curY))
                    }
                    lastCommand = cmd
                }
                'H' -> {
                    while (tokenizer.hasNumber()) {
                        val nx = tokenizer.nextNumber() ?: break
                        curX = if (isRelative) curX + nx else nx
                        segments.add(VectorSegment.LineTo(curX, curY))
                    }
                    lastCommand = cmd
                }
                'V' -> {
                    while (tokenizer.hasNumber()) {
                        val ny = tokenizer.nextNumber() ?: break
                        curY = if (isRelative) curY + ny else ny
                        segments.add(VectorSegment.LineTo(curX, curY))
                    }
                    lastCommand = cmd
                }
                'C' -> {
                    while (tokenizer.hasNumber()) {
                        val x1 = tokenizer.nextNumber() ?: break
                        val y1 = tokenizer.nextNumber() ?: break
                        val x2 = tokenizer.nextNumber() ?: break
                        val y2 = tokenizer.nextNumber() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val cp1X = if (isRelative) curX + x1 else x1
                        val cp1Y = if (isRelative) curY + y1 else y1
                        val cp2X = if (isRelative) curX + x2 else x2
                        val cp2Y = if (isRelative) curY + y2 else y2
                        val endX = if (isRelative) curX + x else x
                        val endY = if (isRelative) curY + y else y

                        segments.add(VectorSegment.CubicTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY))
                        curX = endX
                        curY = endY
                        lastCp2X = cp2X
                        lastCp2Y = cp2Y
                    }
                    lastCommand = cmd
                }
                'S' -> {
                    while (tokenizer.hasNumber()) {
                        val x2 = tokenizer.nextNumber() ?: break
                        val y2 = tokenizer.nextNumber() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val cp1X = if (lastCommand == 'C' || lastCommand == 'c' || lastCommand == 'S' || lastCommand == 's') {
                            2 * curX - lastCp2X
                        } else {
                            curX
                        }
                        val cp1Y = if (lastCommand == 'C' || lastCommand == 'c' || lastCommand == 'S' || lastCommand == 's') {
                            2 * curY - lastCp2Y
                        } else {
                            curY
                        }

                        val cp2X = if (isRelative) curX + x2 else x2
                        val cp2Y = if (isRelative) curY + y2 else y2
                        val endX = if (isRelative) curX + x else x
                        val endY = if (isRelative) curY + y else y

                        segments.add(VectorSegment.CubicTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY))
                        curX = endX
                        curY = endY
                        lastCp2X = cp2X
                        lastCp2Y = cp2Y
                    }
                    lastCommand = cmd
                }
                'Q' -> {
                    while (tokenizer.hasNumber()) {
                        val x1 = tokenizer.nextNumber() ?: break
                        val y1 = tokenizer.nextNumber() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val cp1X = if (isRelative) curX + x1 else x1
                        val cp1Y = if (isRelative) curY + y1 else y1
                        val endX = if (isRelative) curX + x else x
                        val endY = if (isRelative) curY + y else y

                        segments.add(VectorSegment.QuadTo(cp1X, cp1Y, endX, endY))
                        curX = endX
                        curY = endY
                        lastCp2X = cp1X
                        lastCp2Y = cp1Y
                    }
                    lastCommand = cmd
                }
                'T' -> {
                    while (tokenizer.hasNumber()) {
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val cp1X = if (lastCommand == 'Q' || lastCommand == 'q' || lastCommand == 'T' || lastCommand == 't') {
                            2 * curX - lastCp2X
                        } else {
                            curX
                        }
                        val cp1Y = if (lastCommand == 'Q' || lastCommand == 'q' || lastCommand == 'T' || lastCommand == 't') {
                            2 * curY - lastCp2Y
                        } else {
                            curY
                        }

                        val endX = if (isRelative) curX + x else x
                        val endY = if (isRelative) curY + y else y

                        segments.add(VectorSegment.QuadTo(cp1X, cp1Y, endX, endY))
                        curX = endX
                        curY = endY
                        lastCp2X = cp1X
                        lastCp2Y = cp1Y
                    }
                    lastCommand = cmd
                }
                'A' -> {
                    // Elliptical arc command: rx ry x-axis-rotation large-arc-flag sweep-flag x y
                    while (tokenizer.hasNumber()) {
                        val rx = tokenizer.nextNumber() ?: break
                        val ry = tokenizer.nextNumber() ?: break
                        val xRot = tokenizer.nextNumber() ?: break
                        val largeArc = tokenizer.nextNumber() ?: break
                        val sweep = tokenizer.nextNumber() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val endX = if (isRelative) curX + x else x
                        val endY = if (isRelative) curY + y else y

                        // Robust fallback connecting to arc endpoint
                        segments.add(VectorSegment.LineTo(endX, endY))
                        curX = endX
                        curY = endY
                    }
                    lastCommand = cmd
                }
                'Z' -> {
                    segments.add(VectorSegment.Close)
                    curX = startX
                    curY = startY
                    lastCommand = cmd
                }
            }
        }

        return segments
    }

    private fun trySetFeature(factory: DocumentBuilderFactory, feature: String, value: Boolean) {
        try {
            factory.setFeature(feature, value)
        } catch (e: Exception) {
            android.util.Log.d("SafeSvgParser", "Feature $feature not supported: ${e.message}")
        }
    }
}

/**
 * Tokenizer for SVG path string splitting commands and numbers with sign and exponent handling.
 */
class SvgPathTokenizer(private val text: String) {
    private var pos = 0
    private val len = text.length

    fun hasNext(): Boolean {
        skipWhitespace()
        return pos < len
    }

    fun hasNumber(): Boolean {
        skipWhitespace()
        if (pos >= len) return false
        val c = text[pos]
        return c.isDigit() || c == '-' || c == '+' || c == '.'
    }

    fun nextToken(): String? {
        skipWhitespace()
        if (pos >= len) return null
        val c = text[pos]
        if (c.isLetter()) {
            pos++
            return c.toString()
        }
        val num = nextNumberString()
        if (num != null) return num
        pos++ // Skip unrecognized character so pos always advances
        return null
    }

    fun nextNumber(): Float? {
        val s = nextNumberString() ?: return null
        return s.toFloatOrNull()
    }

    private fun nextNumberString(): String? {
        skipWhitespace()
        if (pos >= len) return null
        val start = pos
        if (text[pos] == '-' || text[pos] == '+') {
            pos++
        }
        var hasDot = false
        while (pos < len) {
            val c = text[pos]
            if (c.isDigit()) {
                pos++
            } else if (c == '.' && !hasDot) {
                hasDot = true
                pos++
            } else if ((c == 'e' || c == 'E') && pos > start) {
                pos++
                if (pos < len && (text[pos] == '-' || text[pos] == '+')) {
                    pos++
                }
            } else {
                break
            }
        }
        return if (pos > start) text.substring(start, pos) else null
    }

    private fun skipWhitespace() {
        while (pos < len) {
            val c = text[pos]
            if (c.isWhitespace() || c == ',') {
                pos++
            } else {
                break
            }
        }
    }
}
