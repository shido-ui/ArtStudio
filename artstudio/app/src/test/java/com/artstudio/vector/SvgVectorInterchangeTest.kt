package com.artstudio.vector

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.export.*
import com.artstudio.vector.svg.SafeSvgExporter
import com.artstudio.vector.svg.SafeSvgParser
import com.artstudio.vector.svg.SvgTransformParser
import com.artstudio.vector.svg.SvgVectorImporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SvgVectorInterchangeTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("artstudio_svg_tests").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun resolveFixture(relativePath: String): File {
        val candidates = listOf(
            File("tests/fixtures/artstudio/v1/$relativePath"),
            File("../tests/fixtures/artstudio/v1/$relativePath"),
            File("../../tests/fixtures/artstudio/v1/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: File("tests/fixtures/artstudio/v1/$relativePath")
    }

    // 1. Primitive Shapes & Paths Parsing
    @Test
    fun testSvgImport_pathsAndPrimitiveShapes_parsedWithCorrectGeometry() {
        val svg = """
            <svg viewBox="0 0 800 600" width="800" height="600">
                <rect x="50" y="60" width="200" height="150" rx="10" ry="15" fill="#FF0000" stroke="#00FF00" stroke-width="4"/>
                <circle cx="400" cy="200" r="75" fill="#0000FF"/>
                <ellipse cx="600" cy="250" rx="90" ry="45" fill="#FFFF00"/>
                <line x1="20" y1="30" x2="300" y2="400" stroke="#000000" stroke-width="2"/>
                <polyline points="10,10 50,80 90,20" stroke="#123456" stroke-width="3" fill="none"/>
                <polygon points="100,100 200,150 150,250" fill="#654321"/>
                <path d="M 50 50 L 150 50 C 180 80, 200 120, 150 150 Q 100 200, 50 150 Z" fill="#ABCDEF"/>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(svg)
        assertEquals(800, result.canvasWidth)
        assertEquals(600, result.canvasHeight)
        assertEquals(7, result.vectorObjects.size)

        // Rect
        val rect = result.vectorObjects[0]
        assertEquals(VectorShapeType.RECTANGLE, rect.shapeType)
        assertEquals(50f, rect.rectData!!.x, 0.01f)
        assertEquals(60f, rect.rectData!!.y, 0.01f)
        assertEquals(200f, rect.rectData!!.width, 0.01f)
        assertEquals(150f, rect.rectData!!.height, 0.01f)
        assertEquals(10f, rect.rectData!!.rx, 0.01f)
        assertEquals(15f, rect.rectData!!.ry, 0.01f)
        assertEquals(0xFFFF0000L, rect.style.fillColor)
        assertEquals(0xFF00FF00L, rect.style.strokeColor)

        // Circle
        val circle = result.vectorObjects[1]
        assertEquals(VectorShapeType.ELLIPSE, circle.shapeType)
        assertEquals(400f, circle.ellipseData!!.cx, 0.01f)
        assertEquals(200f, circle.ellipseData!!.cy, 0.01f)
        assertEquals(75f, circle.ellipseData!!.rx, 0.01f)
        assertEquals(75f, circle.ellipseData!!.ry, 0.01f)

        // Ellipse
        val ellipse = result.vectorObjects[2]
        assertEquals(VectorShapeType.ELLIPSE, ellipse.shapeType)
        assertEquals(600f, ellipse.ellipseData!!.cx, 0.01f)
        assertEquals(250f, ellipse.ellipseData!!.cy, 0.01f)
        assertEquals(90f, ellipse.ellipseData!!.rx, 0.01f)
        assertEquals(45f, ellipse.ellipseData!!.ry, 0.01f)

        // Line
        val line = result.vectorObjects[3]
        assertEquals(VectorShapeType.LINE, line.shapeType)
        assertEquals(20f, line.anchors[0].x, 0.01f)
        assertEquals(30f, line.anchors[0].y, 0.01f)
        assertEquals(300f, line.anchors[1].x, 0.01f)
        assertEquals(400f, line.anchors[1].y, 0.01f)

        // Polyline
        val polyline = result.vectorObjects[4]
        assertEquals(VectorShapeType.POLYLINE, polyline.shapeType)
        assertEquals(3, polyline.anchors.size)
        assertFalse(polyline.isClosed)

        // Polygon
        val polygon = result.vectorObjects[5]
        assertEquals(VectorShapeType.POLYGON, polygon.shapeType)
        assertEquals(3, polygon.anchors.size)
        assertTrue(polygon.isClosed)

        // Path
        val path = result.vectorObjects[6]
        val segs = path.toSegments()
        assertTrue(segs.any { it is VectorSegment.MoveTo })
        assertTrue(segs.any { it is VectorSegment.LineTo })
        assertTrue(segs.any { it is VectorSegment.CubicTo })
        assertTrue(segs.any { it is VectorSegment.QuadTo })
        assertTrue(segs.any { it is VectorSegment.Close })
    }

    // 2. Chained Transforms (matrix, translate, scale, rotate, skew)
    @Test
    fun testSvgImport_transformsChained_matrixTranslateScaleRotateSkew() {
        val tStr = "translate(50, 100) scale(2, 2)"
        val transform = SvgTransformParser.parseTransform(tStr)

        val (tx, ty) = transform.transformPoint(10f, 20f)
        // Point (10, 20) scaled by (2, 2) is (20, 40), then translated by (50, 100) is (70, 140)
        assertEquals(70f, tx, 0.01f)
        assertEquals(140f, ty, 0.01f)

        // Test with full SVG element
        val svg = """
            <svg viewBox="0 0 500 500">
                <rect x="10" y="10" width="40" height="30" transform="translate(100, 50)" fill="red"/>
                <line x1="0" y1="0" x2="10" y2="0" transform="rotate(90)" stroke="black"/>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(svg)
        assertEquals(2, result.vectorObjects.size)

        // Translated rect
        val rect = result.vectorObjects[0]
        assertEquals(110f, rect.rectData!!.x, 0.01f)
        assertEquals(60f, rect.rectData!!.y, 0.01f)
        assertEquals(40f, rect.rectData!!.width, 0.01f)
        assertEquals(30f, rect.rectData!!.height, 0.01f)

        // Rotated line (90 deg CW around origin): (0,0) -> (0,0), (10,0) -> (0,10)
        val line = result.vectorObjects[1]
        assertEquals(0f, line.anchors[0].x, 0.01f)
        assertEquals(0f, line.anchors[0].y, 0.01f)
        assertEquals(0f, line.anchors[1].x, 0.01f)
        assertEquals(10f, line.anchors[1].y, 0.01f)
    }

    // 3. Nested Group Transforms and Style Inheritance
    @Test
    fun testSvgImport_nestedGroupTransforms_andStyleInheritance() {
        val svg = """
            <svg viewBox="0 0 600 600">
                <g id="parent_group" transform="translate(100, 50)" stroke="#0000FF" stroke-width="4" opacity="0.8">
                    <g id="child_group" transform="scale(2, 2)">
                        <!-- Child inherits stroke and opacity, overrides fill -->
                        <rect x="10" y="20" width="30" height="40" fill="#00FF00"/>
                        <!-- Sibling inherits group stroke -->
                        <line x1="0" y1="0" x2="50" y2="50"/>
                    </g>
                </g>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(svg)
        assertEquals(2, result.vectorObjects.size)

        val rect = result.vectorObjects[0]
        assertEquals(0xFF00FF00L, rect.style.fillColor)
        assertEquals(0xFF0000FFL, rect.style.strokeColor)
        assertEquals(4f, rect.style.strokeWidth, 0.01f)
        assertEquals(0.8f, rect.style.opacity, 0.01f)

        // Position transformed by translate(100, 50) * scale(2, 2)
        // (10, 20) scaled by 2 is (20, 40) + (100, 50) = (120, 90)
        val segs = rect.toSegments()
        val firstMove = segs.first() as VectorSegment.MoveTo
        assertEquals(120f, firstMove.x, 0.01f)
        assertEquals(90f, firstMove.y, 0.01f)
    }

    // 4. ViewBox Offsets and Scaling
    @Test
    fun testSvgImport_viewBoxOffsetsAndScaling_mapsToCanvasOrigin() {
        val svg = """
            <svg viewBox="100 200 400 300" width="400" height="300">
                <rect x="100" y="200" width="50" height="50" fill="red"/>
                <circle cx="200" cy="300" r="25" fill="blue"/>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(svg)
        assertEquals(400, result.canvasWidth)
        assertEquals(300, result.canvasHeight)
        assertEquals(2, result.vectorObjects.size)

        // Rect at (100, 200) with viewBox (100 200) must be shifted by (-100, -200) to (0, 0)
        val rect = result.vectorObjects[0]
        assertEquals(0f, rect.rectData!!.x, 0.01f)
        assertEquals(0f, rect.rectData!!.y, 0.01f)

        // Circle at (200, 300) must be shifted to (100, 100)
        val circle = result.vectorObjects[1]
        assertEquals(100f, circle.ellipseData!!.cx, 0.01f)
        assertEquals(100f, circle.ellipseData!!.cy, 0.01f)
    }

    // 5. Style Parsing: Colors (hex, rgb, rgba, named) and Opacities
    @Test
    fun testSvgImport_styleParsing_colorsAndOpacities() {
        val svg = """
            <svg viewBox="0 0 500 500">
                <rect x="0" y="0" width="10" height="10" fill="#ABC"/>
                <rect x="0" y="0" width="10" height="10" fill="#11223344"/>
                <rect x="0" y="0" width="10" height="10" fill="rgb(255, 128, 0)"/>
                <rect x="0" y="0" width="10" height="10" fill="rgba(0, 128, 255, 0.5)"/>
                <rect x="0" y="0" width="10" height="10" fill="orange" stroke="purple"/>
                <rect x="0" y="0" width="10" height="10" fill="#FF0000" fill-opacity="0.5" stroke="#00FF00" stroke-opacity="0.25" opacity="0.8"/>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(svg)
        assertEquals(6, result.vectorObjects.size)

        // #ABC -> #AABBCC full opaque
        assertEquals(0xFFAABBCCL, result.vectorObjects[0].style.fillColor)

        // #11223344 -> SVG 8-digit hex #RRGGBBAA: R=0x11, G=0x22, B=0x33, A=0x44
        val col44 = result.vectorObjects[1].style.fillColor!!
        assertEquals(0x44L, (col44 ushr 24) and 0xFF)
        assertEquals(0x11L, (col44 ushr 16) and 0xFF)
        assertEquals(0x22L, (col44 ushr 8) and 0xFF)
        assertEquals(0x33L, col44 and 0xFF)

        // rgb(255, 128, 0)
        assertEquals(0xFFFF8000L, result.vectorObjects[2].style.fillColor)

        // rgba(0, 128, 255, 0.5) -> A ~ 128 (0x80)
        val colRgba = result.vectorObjects[3].style.fillColor!!
        assertEquals(128L, (colRgba ushr 24) and 0xFF)
        assertEquals(0L, (colRgba ushr 16) and 0xFF)
        assertEquals(128L, (colRgba ushr 8) and 0xFF)
        assertEquals(255L, colRgba and 0xFF)

        // Named colors: orange (#FFA500), purple (#800080)
        assertEquals(0xFFFFA500L, result.vectorObjects[4].style.fillColor)
        assertEquals(0xFF800080L, result.vectorObjects[4].style.strokeColor)

        // fill-opacity and stroke-opacity
        val dualOp = result.vectorObjects[5]
        val fillAlpha = ((dualOp.style.fillColor!! ushr 24) and 0xFF)
        val strokeAlpha = ((dualOp.style.strokeColor!! ushr 24) and 0xFF)
        assertEquals(128L, fillAlpha) // 0.5 * 255 ~ 128
        assertEquals(64L, strokeAlpha) // 0.25 * 255 ~ 64
        assertEquals(0.8f, dualOp.style.opacity, 0.01f)
    }

    // 6. Security Sanitisation: Script Tags Neutralised
    @Test
    fun testSvgImport_sanitisation_scriptTagsNeutralisedAndNeverExecuted() {
        val adversarialSvg = """
            <svg viewBox="0 0 500 500">
                <script type="text/javascript">
                    window.location = 'https://malicious.example.com';
                    alert('PWNED');
                </script>
                <rect x="10" y="10" width="100" height="100" fill="red"/>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(adversarialSvg)
        assertEquals(1, result.vectorObjects.size)

        // Verified script was stripped and security warning was recorded
        val warnings = result.conversionReport.warnings
        assertTrue(warnings.any { it.feature == "SECURITY_SANITIZATION" && it.layerName == "script" })
    }

    // 7. Security Sanitisation: Event Handlers Stripped
    @Test
    fun testSvgImport_sanitisation_eventHandlersStripped() {
        val adversarialSvg = """
            <svg viewBox="0 0 500 500">
                <rect x="10" y="10" width="100" height="100" fill="blue" onload="alert(1)" onclick="stealCookies()" onerror="crash()"/>
                <circle cx="50" cy="50" r="20" onmouseover="exploit()"/>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(adversarialSvg)
        assertEquals(2, result.vectorObjects.size)

        val warnings = result.conversionReport.warnings
        val sanitizationWarnings = warnings.filter { it.feature == "SECURITY_SANITIZATION" }
        assertTrue("Must report at least 4 stripped event handlers", sanitizationWarnings.size >= 4)
    }

    // 8. Security Sanitisation: External Resources Never Fetched
    @Test
    fun testSvgImport_sanitisation_externalResourcesNeverFetched() {
        val adversarialSvg = """
            <svg viewBox="0 0 500 500" xmlns:xlink="http://www.w3.org/1999/xlink">
                <image xlink:href="https://evil.site/tracker.png" width="100" height="100"/>
                <a href="http://malicious.org/payload">
                    <rect x="20" y="20" width="50" height="50" fill="green"/>
                </a>
                <path d="M 0 0 L 10 10" href="javascript:alert(1)"/>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(adversarialSvg)
        val warnings = result.conversionReport.warnings

        assertTrue("Must block external http resources", warnings.any { it.feature == "SECURITY_EXTERNAL_RESOURCE" })
        assertTrue("Must neutralize javascript URI", warnings.any { it.actionTaken.contains("Neutralized JavaScript URI") })
    }

    // 9. Malformed and Adversarial XML Handled Gracefully Without Crash
    @Test
    fun testSvgImport_malformedAndAdversarialXml_handledGracefullyWithoutCrash() {
        val malformedFile = resolveFixture("corrupt_truncated/malformed_tags_03.svg")
        if (malformedFile.exists()) {
            val result = SafeSvgParser.parse(malformedFile.readText())
            assertNotNull(result)
            assertTrue("Malformed file must record syntax warning", result.conversionReport.hasLossyConversions)
        }

        // Billion laughs entity expansion attack simulation
        val billionLaughs = """
            <?xml version="1.0"?>
            <!DOCTYPE lolz [
             <!ENTITY lol "lol">
             <!ENTITY lol1 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
             <!ENTITY lol2 "&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;">
            ]>
            <svg viewBox="0 0 100 100">
                <text>&lol2;</text>
                <rect x="0" y="0" width="10" height="10"/>
            </svg>
        """.trimIndent()

        val resultBomb = SafeSvgParser.parse(billionLaughs)
        assertNotNull(resultBomb)
        // Entity expansion is disallow-doctype-decl blocked
        assertTrue(resultBomb.conversionReport.hasLossyConversions)
    }

    // 10. Supported SVG Round-Trip Matches Geometry and Appearance
    @Test
    fun testSvgExport_andImport_roundTripPreservesGeometryAndAppearance() {
        val rectObj = VectorObject.createRectangle(
            x = 20f, y = 30f, width = 120f, height = 80f, rx = 8f, ry = 8f,
            style = VectorStyle(fillColor = 0xFFE74C3CL, strokeColor = 0xFFC0392BL, strokeWidth = 3f, opacity = 0.9f)
        )
        val circleObj = VectorObject.createCircle(
            cx = 250f, cy = 150f, r = 50f,
            style = VectorStyle(fillColor = 0xFF3498DBL, strokeColor = 0xFF2980B9L, strokeWidth = 4f)
        )
        val lineObj = VectorObject.createLine(
            x1 = 10f, y1 = 250f, x2 = 300f, y2 = 250f,
            style = VectorStyle(strokeColor = 0xFF2ECC71L, strokeWidth = 2.5f)
        )

        val origObjects = listOf(rectObj, circleObj, lineObj)

        // Export
        val svgXml = SafeSvgExporter.exportObjects(
            objects = origObjects,
            width = 400,
            height = 300
        )

        // Re-import
        val importedResult = SafeSvgParser.parse(svgXml)
        assertEquals(400, importedResult.canvasWidth)
        assertEquals(300, importedResult.canvasHeight)
        assertEquals(3, importedResult.vectorObjects.size)

        // Verify rect
        val importedRect = importedResult.vectorObjects[0]
        assertEquals(VectorShapeType.RECTANGLE, importedRect.shapeType)
        assertEquals(20f, importedRect.rectData!!.x, 0.05f)
        assertEquals(30f, importedRect.rectData!!.y, 0.05f)
        assertEquals(120f, importedRect.rectData!!.width, 0.05f)
        assertEquals(80f, importedRect.rectData!!.height, 0.05f)
        assertEquals(0xFFE74C3CL, importedRect.style.fillColor)
        assertEquals(0xFFC0392BL, importedRect.style.strokeColor)
        assertEquals(3f, importedRect.style.strokeWidth, 0.05f)
        assertEquals(0.9f, importedRect.style.opacity, 0.05f)

        // Verify circle
        val importedCircle = importedResult.vectorObjects[1]
        assertEquals(VectorShapeType.ELLIPSE, importedCircle.shapeType)
        assertEquals(250f, importedCircle.ellipseData!!.cx, 0.05f)
        assertEquals(150f, importedCircle.ellipseData!!.cy, 0.05f)
        assertEquals(50f, importedCircle.ellipseData!!.rx, 0.05f)

        // Verify line
        val importedLine = importedResult.vectorObjects[2]
        assertEquals(VectorShapeType.LINE, importedLine.shapeType)
        assertEquals(10f, importedLine.anchors[0].x, 0.05f)
        assertEquals(250f, importedLine.anchors[0].y, 0.05f)
        assertEquals(300f, importedLine.anchors[1].x, 0.05f)
        assertEquals(250f, importedLine.anchors[1].y, 0.05f)
    }

    // 11. SvgVectorImporter Preserves Editable Vector Layer and Allows Explicit Rasterization
    @Test
    fun testSvgVectorImporter_preservesEditablePathsAndAllowsExplicitRasterization() {
        val svg = """
            <svg viewBox="0 0 200 200">
                <rect x="20" y="20" width="80" height="80" fill="#FF0000"/>
                <circle cx="150" cy="150" r="30" fill="#00FF00"/>
            </svg>
        """.trimIndent()

        val (doc, report) = SvgVectorImporter.importAsDocument(svg, "Test Project")
        assertEquals(200, doc.canvas.width)
        assertEquals(200, doc.canvas.height)
        assertFalse(report.hasLossyConversions)

        // Must be editable VectorPathLayer
        val layer = doc.allLayersFlat().first()
        assertTrue("Must be VectorPathLayer", layer is LayerNode.VectorPathLayer)
        val vectorLayer = layer as LayerNode.VectorPathLayer
        assertEquals(2, vectorLayer.vectorObjects.size)

        // Explicit rasterization
        val rasterizeCmd = SvgVectorImporter.explicitRasterize(doc, vectorLayer.id, createNewLayer = false)
        val docRasterized = rasterizeCmd.apply(doc)
        val layerAfter = docRasterized.allLayersFlat().first()
        assertTrue("Must now be RasterLayer upon explicit user choice", layerAfter is LayerNode.RasterLayer)
    }

    // 12. ArtworkExportService: SVG Export Atomic and Verified
    @Test
    fun testArtworkExportService_svgExportAtomicAndVerified() = runBlocking {
        val obj = VectorObject.createRectangle(50f, 50f, 200f, 200f, style = VectorStyle(fillColor = 0xFF00FF00L))
        val vLayer = LayerNode.VectorPathLayer(name = "Vector Layer", vectorObjects = listOf(obj))
        val doc = ArtDocument(
            title = "Export SVG Doc",
            canvas = CanvasConfig(width = 300, height = 300),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(vLayer))
        )

        val targetFile = File(tempDir, "exported_vector.svg")
        val config = ExportConfig(format = ExportFormat.SVG)

        val service = ArtworkExportService()
        val result = service.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile
        )

        assertTrue("Export must succeed: $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(targetFile.absolutePath, success.outputFile.absolutePath)
        assertTrue(targetFile.exists())
        assertTrue(targetFile.length() > 0L)

        // Read and parse exported SVG
        val parsed = SafeSvgParser.parse(targetFile.readText())
        assertEquals(300, parsed.canvasWidth)
        assertEquals(300, parsed.canvasHeight)
        assertEquals(1, parsed.vectorObjects.size)
        assertEquals(0xFF00FF00L, parsed.vectorObjects[0].style.fillColor)
    }

    // 13. Fixtures Corpus Loading: Clean Line Art and Dense Samples
    @Test
    fun testSvgImport_fixturesCorpus_sampleLineartAndDenseSamples() {
        val lineartFile = resolveFixture("lineart/sample_lineart_01.svg")
        assertTrue("Fixture lineart must exist", lineartFile.exists())
        val lineartResult = SafeSvgParser.parse(lineartFile.readText())
        assertEquals(1024, lineartResult.canvasWidth)
        assertEquals(1024, lineartResult.canvasHeight)
        assertTrue("Lineart fixture must parse objects", lineartResult.vectorObjects.isNotEmpty())

        val denseFile = resolveFixture("svg_samples/sample_vector_dense_02.svg")
        assertTrue("Fixture dense must exist", denseFile.exists())
        val denseResult = SafeSvgParser.parse(denseFile.readText())
        assertEquals(1024, denseResult.canvasWidth)
        assertEquals(1024, denseResult.canvasHeight)
        assertEquals(11, denseResult.vectorObjects.size)
    }
}
