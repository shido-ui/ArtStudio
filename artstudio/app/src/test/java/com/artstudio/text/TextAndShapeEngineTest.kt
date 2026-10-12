package com.artstudio.text

import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.*
import com.artstudio.history.AddTextLayerCommand
import com.artstudio.history.RasterizeTextLayerCommand
import com.artstudio.history.UpdateTextObjectCommand
import com.artstudio.shape.GeometricShapeType
import com.artstudio.shape.ShapeCreationParams
import com.artstudio.shape.ShapeGeometryEngine
import com.artstudio.vector.StrokeCap
import com.artstudio.vector.StrokeJoin
import com.artstudio.vector.VectorObject
import com.artstudio.vector.VectorStyle
import com.artstudio.document.io.ArtJson
import com.artstudio.vector.svg.SafeSvgExporter
import com.artstudio.vector.svg.SvgExportOptions
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Verification test suite for Text Objects, Typography Resolution,
 * Font Fallback, Shape Geometry, Document Persistence, and SVG Export (Prompt 29).
 */
class TextAndShapeEngineTest {

    // =========================================================================
    // 1. Text Models & Layout Metrics
    // =========================================================================

    @Test
    fun testTextObject_defaultAndCustomProperties() {
        val defaultText = TextObject()
        assertEquals("Sample Text", defaultText.text)
        assertEquals("sans-serif", defaultText.fontFamily)
        assertEquals(36f, defaultText.fontSize, 0.001f)
        assertEquals(TextFontStyle.NORMAL, defaultText.fontStyle)
        assertEquals(TextAlignment.LEFT, defaultText.alignment)

        val customText = TextObject(
            text = "Title Line 1\nSubtitle Line 2",
            x = 150f,
            y = 200f,
            fontFamily = "serif",
            fontSize = 48f,
            fontStyle = TextFontStyle.BOLD_ITALIC,
            alignment = TextAlignment.CENTER,
            textColor = 0xFFFF5722L,
            letterSpacing = 2.0f,
            lineSpacingMultiplier = 1.5f,
            isUnderline = true,
            isStrikeThrough = false
        )

        assertEquals(2, customText.lines().size)
        assertEquals("Title Line 1", customText.lines()[0])
        assertEquals("Subtitle Line 2", customText.lines()[1])
        assertEquals(TextFontStyle.BOLD_ITALIC, customText.fontStyle)
        assertEquals(TextAlignment.CENTER, customText.alignment)
        assertTrue(customText.isUnderline)
        assertFalse(customText.isStrikeThrough)
    }

    @Test
    fun testTextObject_boundsCalculationForAlignments() {
        val textObj = TextObject(
            text = "Hello",
            x = 500f,
            y = 300f,
            fontSize = 40f,
            alignment = TextAlignment.LEFT
        )
        val leftBounds = textObj.computeBounds()
        assertTrue("Left bounds left must be anchor X", leftBounds.left == 500f)
        assertTrue("Left bounds right must be > anchor X", leftBounds.right > 500f)

        val centerObj = textObj.copy(alignment = TextAlignment.CENTER)
        val centerBounds = centerObj.computeBounds()
        assertEquals("Center bounds centerX must be anchor X", 500f, centerBounds.centerX, 1.0f)

        val rightObj = textObj.copy(alignment = TextAlignment.RIGHT)
        val rightBounds = rightObj.computeBounds()
        assertEquals("Right bounds right must be anchor X", 500f, rightBounds.right, 0.001f)
    }

    @Test
    fun testTextObject_affineTransform() {
        val textObj = TextObject(text = "Scalable", x = 100f, y = 100f, fontSize = 20f)
        val transform = com.artstudio.vector.AffineTransform2D.translation(50f, 75f)
        val transformed = textObj.transformed(transform)

        assertEquals(150f, transformed.x, 0.001f)
        assertEquals(175f, transformed.y, 0.001f)
        assertEquals(20f, transformed.fontSize, 0.001f)
    }

    // =========================================================================
    // 2. Font Engine & Fallback Arbitration
    // =========================================================================

    @Test
    fun testFontResolver_knownSystemFontsAvailable() {
        val systemFamilies = listOf("sans-serif", "serif", "monospace", "cursive", "casual")
        for (family in systemFamilies) {
            assertTrue("System family '$family' must be recognized", FontEngine.isFontAvailable(family))
            val res = FontEngine.resolveFont(family)
            assertFalse("Known system font should not trigger fallback", res.fallbackOccurred)
            assertEquals(family, res.resolvedFamily)
            assertNull(res.report)
        }
    }

    @Test
    fun testFontResolver_bundledFontsAvailable() {
        val bundledFamilies = listOf("Carrois Gothic SC", "Cutive Mono")
        for (family in bundledFamilies) {
            assertTrue("Bundled family '$family' must be recognized", FontEngine.isFontAvailable(family))
            val res = FontEngine.resolveFont(family)
            assertFalse("Bundled font should not trigger fallback", res.fallbackOccurred)
            assertEquals(family, res.resolvedFamily)
            assertNull(res.report)
        }
    }

    @Test
    fun testFontResolver_unknownFontTriggersClearFallbackReport() {
        val unknownFamily = "FictitiousNonExistentFont_123"
        assertFalse("Unknown font should not be marked available", FontEngine.isFontAvailable(unknownFamily))

        val res = FontEngine.resolveFont(unknownFamily)
        assertTrue("Unknown font must trigger fallback", res.fallbackOccurred)
        assertEquals(FontEngine.DEFAULT_FALLBACK_FAMILY, res.resolvedFamily)
        assertNotNull("Diagnostic fallback report must be present", res.report)

        val report = res.report!!
        assertEquals(unknownFamily, report.requestedFamily)
        assertEquals(FontEngine.DEFAULT_FALLBACK_FAMILY, report.resolvedFamily)
        assertTrue(report.fallbackOccurred)
        assertTrue("Report reason must document substitution", report.reason.contains(unknownFamily))
    }

    // =========================================================================
    // 3. Geometric Shape Engines & Math
    // =========================================================================

    @Test
    fun testShapeGeometry_createRectangleAndRoundedRect() {
        val rectParams = ShapeCreationParams(
            type = GeometricShapeType.RECTANGLE,
            x = 50f, y = 50f, width = 200f, height = 100f,
            isFilled = true, fillColor = 0xFF00FF00L,
            isOutlined = true, strokeColor = 0xFF000000L, strokeWidth = 2f
        )
        val rectObj = ShapeGeometryEngine.createShape(rectParams)
        assertEquals(com.artstudio.vector.VectorShapeType.RECTANGLE, rectObj.shapeType)
        assertNotNull(rectObj.rectData)
        assertEquals(200f, rectObj.rectData!!.width, 0.001f)
        assertEquals(100f, rectObj.rectData!!.height, 0.001f)
        assertEquals(0f, rectObj.rectData!!.rx, 0.001f)

        val roundedParams = rectParams.copy(
            type = GeometricShapeType.ROUNDED_RECTANGLE,
            rx = 16f, ry = 16f
        )
        val roundedObj = ShapeGeometryEngine.createShape(roundedParams)
        assertNotNull(roundedObj.rectData)
        assertEquals(16f, roundedObj.rectData!!.rx, 0.001f)
        assertEquals(16f, roundedObj.rectData!!.ry, 0.001f)
    }

    @Test
    fun testShapeGeometry_createEllipseAndCircle() {
        val ellipseParams = ShapeCreationParams(
            type = GeometricShapeType.ELLIPSE,
            x = 100f, y = 100f, width = 300f, height = 200f
        )
        val ellipseObj = ShapeGeometryEngine.createShape(ellipseParams)
        assertEquals(com.artstudio.vector.VectorShapeType.ELLIPSE, ellipseObj.shapeType)
        assertNotNull(ellipseObj.ellipseData)
        assertEquals(250f, ellipseObj.ellipseData!!.cx, 0.001f)
        assertEquals(200f, ellipseObj.ellipseData!!.cy, 0.001f)
        assertEquals(150f, ellipseObj.ellipseData!!.rx, 0.001f)
        assertEquals(100f, ellipseObj.ellipseData!!.ry, 0.001f)

        val circleParams = ShapeCreationParams(
            type = GeometricShapeType.CIRCLE,
            x = 100f, y = 100f, width = 200f, height = 200f
        )
        val circleObj = ShapeGeometryEngine.createShape(circleParams)
        assertNotNull(circleObj.ellipseData)
        assertEquals(circleObj.ellipseData!!.rx, circleObj.ellipseData!!.ry, 0.001f)
    }

    @Test
    fun testShapeGeometry_createRegularPolygonVertices() {
        val cx = 300f
        val cy = 300f
        val radius = 100f
        val sides = 6 // Regular Hexagon
        val style = VectorStyle(fillColor = 0xFFFFEB3BL)

        val hexObj = ShapeGeometryEngine.createRegularPolygon(cx, cy, radius, sides, style)
        assertEquals(com.artstudio.vector.VectorShapeType.POLYGON, hexObj.shapeType)
        assertEquals("Hexagon must have 6 vertices", 6, hexObj.anchors.size)
        assertTrue("Polygon must be closed", hexObj.isClosed)

        // Verify all vertices lie on perimeter with distance = radius
        for (v in hexObj.anchors) {
            val dist = sqrt((v.x - cx) * (v.x - cx) + (v.y - cy) * (v.y - cy))
            assertEquals(radius, dist, 0.01f)
        }
    }

    @Test
    fun testShapeGeometry_createStarShapeVertices() {
        val cx = 400f
        val cy = 400f
        val outerR = 120f
        val innerR = 60f
        val points = 5 // 5-point star

        val starObj = ShapeGeometryEngine.createStar(cx, cy, outerR, innerR, points)
        assertEquals(com.artstudio.vector.VectorShapeType.POLYGON, starObj.shapeType)
        assertEquals("5-point star must have 10 vertices", 10, starObj.anchors.size)

        // Alternating outer and inner vertices
        for (i in starObj.anchors.indices) {
            val v = starObj.anchors[i]
            val dist = sqrt((v.x - cx) * (v.x - cx) + (v.y - cy) * (v.y - cy))
            val expectedR = if (i % 2 == 0) outerR else innerR
            assertEquals(expectedR, dist, 0.01f)
        }
    }

    // =========================================================================
    // 4. Native Document Persistence Roundtrip (.ffart JSON)
    // =========================================================================

    @Test
    fun testDocumentPersistence_textLayerSaveAndReopenSurvives() {
        val textObj = TextObject(
            id = "txt_persistent_01",
            text = "Master Typography",
            x = 240f,
            y = 360f,
            fontFamily = "Carrois Gothic SC",
            fontSize = 54f,
            fontStyle = TextFontStyle.BOLD,
            alignment = TextAlignment.CENTER,
            textColor = 0xFF1E88E5L,
            letterSpacing = 1.5f,
            lineSpacingMultiplier = 1.3f,
            isUnderline = true,
            isStrikeThrough = false
        )
        val textLayer = LayerNode.TextLayer(
            id = "layer_text_01",
            name = "Title Layer",
            opacity = 0.9f,
            blendMode = BlendModeType.Multiply,
            textObject = textObj
        )

        val doc = ArtDocument(
            title = "Text Project",
            canvas = CanvasConfig(1024, 1024),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(textLayer)
            ),
            selectedLayerId = textLayer.id
        )

        // Serialize to native JSON
        val json = DocumentSerializer.toJsonString(doc)
        assertTrue("JSON must contain text type", json.contains("\"type\": \"text\""))
        assertTrue("JSON must contain text content", json.contains("Master Typography"))
        assertTrue("JSON must contain font family", json.contains("Carrois Gothic SC"))

        // Deserialize back
        val reloaded = DocumentSerializer.fromJsonString(json)
        assertEquals(1, reloaded.rootGroup.children.size)

        val reloadedLayer = reloaded.rootGroup.children[0]
        assertTrue("Reloaded layer must be TextLayer", reloadedLayer is LayerNode.TextLayer)

        val reloadedTextLayer = reloadedLayer as LayerNode.TextLayer
        assertEquals("layer_text_01", reloadedTextLayer.id)
        assertEquals("Title Layer", reloadedTextLayer.name)
        assertEquals(0.9f, reloadedTextLayer.opacity, 0.001f)
        assertEquals(BlendModeType.Multiply, reloadedTextLayer.blendMode)

        val rText = reloadedTextLayer.textObject
        assertEquals(textObj.id, rText.id)
        assertEquals("Master Typography", rText.text)
        assertEquals(240f, rText.x, 0.001f)
        assertEquals(360f, rText.y, 0.001f)
        assertEquals("Carrois Gothic SC", rText.fontFamily)
        assertEquals(54f, rText.fontSize, 0.001f)
        assertEquals(TextFontStyle.BOLD, rText.fontStyle)
        assertEquals(TextAlignment.CENTER, rText.alignment)
        assertEquals(0xFF1E88E5L, rText.textColor)
        assertTrue(rText.isUnderline)
        assertFalse(rText.isStrikeThrough)
    }

    @Test
    fun testDocumentPersistence_shapesInVectorLayerSaveAndReopenSurvive() {
        val rect = VectorObject.createRectangle(10f, 20f, 100f, 200f, 15f, 15f, VectorStyle(fillColor = 0xFFE91E63L))
        val hex = ShapeGeometryEngine.createRegularPolygon(300f, 300f, 80f, 6, VectorStyle(strokeColor = 0xFF00BCD4L, strokeWidth = 3f))
        val vectorLayer = LayerNode.VectorPathLayer(
            id = "layer_vec_shapes",
            name = "Geometric Shapes Layer",
            vectorObjects = listOf(rect, hex)
        )

        val doc = ArtDocument(
            title = "Shape Project",
            canvas = CanvasConfig(1024, 1024),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(vectorLayer)
            )
        )

        val json = DocumentSerializer.toJsonString(doc)
        val reloaded = DocumentSerializer.fromJsonString(json)

        val reloadedLayer = reloaded.rootGroup.children[0] as LayerNode.VectorPathLayer
        assertEquals(2, reloadedLayer.vectorObjects.size)

        val rRect = reloadedLayer.vectorObjects[0]
        assertEquals(com.artstudio.vector.VectorShapeType.RECTANGLE, rRect.shapeType)
        assertNotNull(rRect.rectData)
        assertEquals(100f, rRect.rectData!!.width, 0.001f)
        assertEquals(15f, rRect.rectData!!.rx, 0.001f)

        val rHex = reloadedLayer.vectorObjects[1]
        assertEquals(com.artstudio.vector.VectorShapeType.POLYGON, rHex.shapeType)
        assertEquals(6, rHex.anchors.size)
    }

    // =========================================================================
    // 5. SVG Export Fidelity & Font Handling
    // =========================================================================

    @Test
    fun testSvgExport_geometricShapesProduceValidSvgElements() {
        val rect = VectorObject.createRectangle(10f, 20f, 150f, 80f, 8f, 8f, VectorStyle(fillColor = 0xFFFF0000L))
        val circle = VectorObject.createCircle(300f, 300f, 50f, VectorStyle(strokeColor = 0xFF00FF00L, strokeWidth = 2f))
        val poly = ShapeGeometryEngine.createRegularPolygon(500f, 500f, 60f, 5, VectorStyle(fillColor = 0xFF0000FFL))

        val vectorLayer = LayerNode.VectorPathLayer(
            name = "Shapes",
            vectorObjects = listOf(rect, circle, poly)
        )
        val doc = ArtDocument(
            title = "SVG Test Document",
            canvas = CanvasConfig(1024, 1024),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(vectorLayer))
        )

        val (svg, report) = SafeSvgExporter.exportDocument(doc)
        assertNotNull(svg)
        assertTrue("<svg element must open", svg.contains("<svg"))
        assertTrue("Must contain <rect element with rx", svg.contains("<rect x=\"10\" y=\"20\" width=\"150\" height=\"80\" rx=\"8\""))
        assertTrue("Must contain <circle element", svg.contains("<circle cx=\"300\" cy=\"300\" r=\"50\""))
        assertTrue("Must contain <polygon element", svg.contains("<polygon points=\""))
        assertTrue("SVG must close with </svg>", svg.contains("</svg>"))
        assertEquals(0, report.warnings.size)
    }

    @Test
    fun testSvgExport_textObjectProducesValidTextElement() {
        val textObj = TextObject(
            text = "Vectorized Text",
            x = 120f,
            y = 200f,
            fontFamily = "sans-serif",
            fontSize = 32f,
            fontStyle = TextFontStyle.BOLD,
            alignment = TextAlignment.LEFT,
            textColor = 0xFF212121L
        )
        val textLayer = LayerNode.TextLayer(
            name = "Text Layer",
            textObject = textObj
        )
        val doc = ArtDocument(
            title = "Text SVG Export",
            canvas = CanvasConfig(1024, 1024),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(textLayer))
        )

        val (svg, _) = SafeSvgExporter.exportDocument(doc)
        assertTrue("Must contain <text element", svg.contains("<text"))
        assertTrue("Must contain font-family attribute", svg.contains("font-family=\"sans-serif\""))
        assertTrue("Must contain font-size attribute", svg.contains("font-size=\"32\""))
        assertTrue("Must contain font-weight attribute", svg.contains("font-weight=\"bold\""))
        assertTrue("Must contain escaped text", svg.contains("Vectorized Text"))
    }

    @Test
    fun testSvgExport_fontFallbackGeneratesReportWarning() {
        val unknownFont = "ExoticAlienFont99"
        val textObj = TextObject(
            text = "Fallback Alert",
            fontFamily = unknownFont
        )
        val textLayer = LayerNode.TextLayer(
            name = "Text",
            textObject = textObj
        )
        val doc = ArtDocument(
            canvas = CanvasConfig(512, 512),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(textLayer))
        )

        val (_, report) = SafeSvgExporter.exportDocument(doc)
        assertTrue("Report must contain FONT_FALLBACK warning", report.warnings.any { it.feature == "FONT_FALLBACK" })
        val warning = report.warnings.first { it.feature == "FONT_FALLBACK" }
        assertTrue(warning.actionTaken.contains(unknownFont))
    }

    // =========================================================================
    // 6. Explicit & Reversible Text Rasterization
    // =========================================================================

    @Test
    fun testExplicitRasterization_textLayerToRasterLayerIsUndoable() {
        val textObj = TextObject(text = "Undoable Text", fontSize = 40f)
        val textLayer = LayerNode.TextLayer(
            id = "txt_layer_to_raster",
            name = "Original Text",
            textObject = textObj
        )
        val doc = ArtDocument(
            canvas = CanvasConfig(512, 512),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(textLayer)),
            selectedLayerId = textLayer.id
        )

        val cmd = RasterizeTextLayerCommand.create(doc, textLayer.id, context = null)

        // Apply rasterization
        val rasterizedDoc = cmd.apply(doc)
        assertEquals(1, rasterizedDoc.rootGroup.children.size)
        val afterLayer = rasterizedDoc.rootGroup.children[0]
        assertTrue("Layer must be converted to RasterLayer", afterLayer is LayerNode.RasterLayer)
        assertEquals("${textLayer.name} (Rasterized)", afterLayer.name)

        // Revert rasterization
        val revertedDoc = cmd.revert(rasterizedDoc)
        assertEquals(1, revertedDoc.rootGroup.children.size)
        val revertedLayer = revertedDoc.rootGroup.children[0]
        assertTrue("Reverted layer must be restored to TextLayer", revertedLayer is LayerNode.TextLayer)
        val restoredTextLayer = revertedLayer as LayerNode.TextLayer
        assertEquals("Undoable Text", restoredTextLayer.textObject.text)
        assertEquals(40f, restoredTextLayer.textObject.fontSize, 0.001f)
    }

    // =========================================================================
    // 7. Bundled Font Licensing & Manifest Integrity
    // =========================================================================

    private fun resolveAssetFile(relativePath: String): File {
        return listOf(
            File("src/main/assets", relativePath),
            File("app/src/main/assets", relativePath),
            File("/root/artstudio/app/src/main/assets", relativePath)
        ).firstOrNull { it.exists() } ?: File("src/main/assets", relativePath)
    }

    @Test
    fun testBundledFonts_manifestIntegrityAndLicenses() {
        val manifestFile = resolveAssetFile("fonts/manifest.json")
        assertTrue("fonts/manifest.json must exist in assets", manifestFile.exists())

        val content = manifestFile.readText(Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        val json = ArtJson.parse(content) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val fontsArray = json["fonts"] as List<Map<String, Any?>>
        assertTrue("At least 2 bundled fonts must be declared in manifest", fontsArray.size >= 2)

        for (fontObj in fontsArray) {
            val fontId = fontObj["id"]?.toString() ?: ""
            val relativePath = fontObj["asset_path"]?.toString() ?: ""
            val license = fontObj["license"]?.toString() ?: ""
            val declaredHash = fontObj["sha256"]?.toString() ?: ""

            assertEquals("Bundled font must be licensed under OFL-1.1", "OFL-1.1", license)

            val fileOnDisk = resolveAssetFile(relativePath)
            assertTrue("Font file '$relativePath' must exist on disk", fileOnDisk.exists())

            // Verify SHA-256 hash
            val bytes = fileOnDisk.readBytes()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val computedHash = digest.joinToString("") { "%02x".format(it) }

            assertEquals("Hash mismatch for font asset '$fontId'", declaredHash.lowercase(), computedHash.lowercase())
        }

        val oflLicenseFile = resolveAssetFile("fonts/OFL.txt")
        assertTrue("OFL.txt license file must exist in assets/fonts/", oflLicenseFile.exists())
        assertTrue("OFL.txt must contain SIL OPEN FONT LICENSE header", oflLicenseFile.readText().contains("SIL OPEN FONT LICENSE Version 1.1"))
    }
}
