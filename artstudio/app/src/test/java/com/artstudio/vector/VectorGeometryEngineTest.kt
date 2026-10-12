package com.artstudio.vector

import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.*
import com.artstudio.history.AddVectorObjectCommand
import com.artstudio.history.RasterizeVectorLayerCommand
import com.artstudio.vector.svg.SafeSvgExporter
import com.artstudio.vector.svg.SafeSvgParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VectorGeometryEngineTest {

    private fun resolveFixtureDir(): File {
        val direct = File("tests/fixtures/artstudio/v1/svg_samples")
        if (direct.exists()) return direct
        val relative = File("../tests/fixtures/artstudio/v1/svg_samples")
        if (relative.exists()) return relative
        throw IllegalStateException("Cannot find svg_samples directory at ${direct.absolutePath} or ${relative.absolutePath}")
    }

    private fun resolveCorruptFixtureDir(): File {
        val direct = File("tests/fixtures/artstudio/v1/corrupt_truncated")
        if (direct.exists()) return direct
        val relative = File("../tests/fixtures/artstudio/v1/corrupt_truncated")
        if (relative.exists()) return relative
        throw IllegalStateException("Cannot find corrupt_truncated directory")
    }

    private fun createTestDoc(w: Int = 1024, h: Int = 1024): ArtDocument {
        return ArtDocument(canvas = CanvasConfig(width = w, height = h))
    }

    @Test
    fun testCreateBasicShapes_linePolylinePolygonRectEllipse() {
        // Line
        val line = VectorObject.createLine(10f, 20f, 100f, 200f)
        assertEquals(VectorShapeType.LINE, line.shapeType)
        assertEquals(2, line.anchors.size)
        assertFalse(line.isClosed)

        // Polyline
        val polyline = VectorObject.createPolyline(listOf(Pair(0f, 0f), Pair(50f, 25f), Pair(100f, 0f)))
        assertEquals(VectorShapeType.POLYLINE, polyline.shapeType)
        assertEquals(3, polyline.anchors.size)
        assertFalse(polyline.isClosed)

        // Polygon
        val polygon = VectorObject.createPolygon(listOf(Pair(0f, 0f), Pair(100f, 0f), Pair(50f, 100f)))
        assertEquals(VectorShapeType.POLYGON, polygon.shapeType)
        assertEquals(3, polygon.anchors.size)
        assertTrue(polygon.isClosed)

        // Rectangle
        val rect = VectorObject.createRectangle(50f, 60f, 200f, 100f, rx = 10f, ry = 10f)
        assertEquals(VectorShapeType.RECTANGLE, rect.shapeType)
        assertNotNull(rect.rectData)
        assertEquals(50f, rect.rectData!!.x, 0.01f)
        assertEquals(60f, rect.rectData!!.y, 0.01f)
        assertEquals(200f, rect.rectData!!.width, 0.01f)
        assertEquals(100f, rect.rectData!!.height, 0.01f)
        assertEquals(10f, rect.rectData!!.rx, 0.01f)

        // Ellipse
        val ellipse = VectorObject.createEllipse(256f, 256f, 80f, 40f)
        assertEquals(VectorShapeType.ELLIPSE, ellipse.shapeType)
        assertNotNull(ellipse.ellipseData)
        assertEquals(256f, ellipse.ellipseData!!.cx, 0.01f)
        assertEquals(256f, ellipse.ellipseData!!.cy, 0.01f)
        assertEquals(80f, ellipse.ellipseData!!.rx, 0.01f)
        assertEquals(40f, ellipse.ellipseData!!.ry, 0.01f)

        // Verify segments generation for all basic shapes
        assertTrue(line.toSegments().isNotEmpty())
        assertTrue(polyline.toSegments().isNotEmpty())
        assertTrue(polygon.toSegments().isNotEmpty())
        assertTrue(rect.toSegments().isNotEmpty())
        assertTrue(ellipse.toSegments().isNotEmpty())
    }

    @Test
    fun testBezierCurve_cubicAndQuadraticSegments() {
        val a1 = VectorAnchor(100f, 100f, handleOutX = 150f, handleOutY = 50f)
        val a2 = VectorAnchor(300f, 100f, handleInX = 250f, handleInY = 150f)
        val bezier = VectorObject.createBezierPath(listOf(a1, a2))

        val segments = bezier.toSegments()
        assertEquals(2, segments.size)
        assertTrue(segments[0] is VectorSegment.MoveTo)
        assertTrue(segments[1] is VectorSegment.CubicTo)

        val cubic = segments[1] as VectorSegment.CubicTo
        assertEquals(150f, cubic.x1, 0.01f)
        assertEquals(50f, cubic.y1, 0.01f)
        assertEquals(250f, cubic.x2, 0.01f)
        assertEquals(150f, cubic.y2, 0.01f)
        assertEquals(300f, cubic.x, 0.01f)
        assertEquals(100f, cubic.y, 0.01f)

        // Evaluate Bezier curve points at t=0, t=0.5, t=1.0
        val pStart = VectorGeometryEngine.evalCubicBezier(100f, 100f, 150f, 50f, 250f, 150f, 300f, 100f, 0f)
        assertEquals(100f, pStart.first, 0.01f)
        assertEquals(100f, pStart.second, 0.01f)

        val pMid = VectorGeometryEngine.evalCubicBezier(100f, 100f, 150f, 50f, 250f, 150f, 300f, 100f, 0.5f)
        assertEquals(200f, pMid.first, 0.01f)
        assertEquals(100f, pMid.second, 0.01f)

        val pEnd = VectorGeometryEngine.evalCubicBezier(100f, 100f, 150f, 50f, 250f, 150f, 300f, 100f, 1f)
        assertEquals(300f, pEnd.first, 0.01f)
        assertEquals(100f, pEnd.second, 0.01f)

        // Quadratic evaluation
        val qMid = VectorGeometryEngine.evalQuadBezier(0f, 0f, 50f, 100f, 100f, 0f, 0.5f)
        assertEquals(50f, qMid.first, 0.01f)
        assertEquals(50f, qMid.second, 0.01f)
    }

    @Test
    fun testAnchorEditing_moveAnchorPointPreservesRelativeHandles() {
        val a = VectorAnchor(
            x = 100f, y = 100f,
            handleInX = 80f, handleInY = 90f,
            handleOutX = 120f, handleOutY = 110f
        )
        val obj = VectorObject.createBezierPath(listOf(a))

        val moved = VectorGeometryEngine.moveAnchor(obj, 0, 150f, 130f)
        val ma = moved.anchors[0]

        assertEquals(150f, ma.x, 0.01f)
        assertEquals(130f, ma.y, 0.01f)
        // dx = 50, dy = 30
        assertEquals(130f, ma.handleInX!!, 0.01f)
        assertEquals(120f, ma.handleInY!!, 0.01f)
        assertEquals(170f, ma.handleOutX!!, 0.01f)
        assertEquals(140f, ma.handleOutY!!, 0.01f)
    }

    @Test
    fun testAnchorEditing_manipulateControlHandlesInAndOut() {
        val a = VectorAnchor(x = 100f, y = 100f)
        val obj = VectorObject.createBezierPath(listOf(a))

        val withIn = VectorGeometryEngine.moveHandleIn(obj, 0, 70f, 80f)
        assertEquals(70f, withIn.anchors[0].handleInX!!, 0.01f)
        assertEquals(80f, withIn.anchors[0].handleInY!!, 0.01f)

        val withOut = VectorGeometryEngine.moveHandleOut(withIn, 0, 130f, 120f)
        assertEquals(130f, withOut.anchors[0].handleOutX!!, 0.01f)
        assertEquals(120f, withOut.anchors[0].handleOutY!!, 0.01f)
    }

    @Test
    fun testAnchorEditing_smoothModeEnforcesCollinearHandles() {
        val a = VectorAnchor(
            x = 100f, y = 100f,
            handleInX = 80f, handleInY = 100f, // 20 units left
            handleOutX = 120f, handleOutY = 100f, // 20 units right
            isSmooth = true
        )
        val obj = VectorObject.createBezierPath(listOf(a))

        // Move handleIn to (80, 80) -> vector from anchor is (-20, -20)
        val updated = VectorGeometryEngine.moveHandleIn(obj, 0, 80f, 80f)
        val ma = updated.anchors[0]
        assertEquals(80f, ma.handleInX!!, 0.01f)
        assertEquals(80f, ma.handleInY!!, 0.01f)

        val outVx = ma.handleOutX!! - 100f
        val outVy = ma.handleOutY!! - 100f
        val inVx = ma.handleInX!! - 100f
        val inVy = ma.handleInY!! - 100f

        // Dot product of (inV) and (outV) must be negative (opposite directions)
        val dot = inVx * outVx + inVy * outVy
        assertTrue("Collinear opposite vectors must have negative dot product", dot < 0f)

        // Cross product must be approximately 0 (collinear)
        val cross = inVx * outVy - inVy * outVx
        assertEquals(0f, cross, 0.05f)
    }

    @Test
    fun testAnchorEditing_insertAndDeleteAnchor() {
        val a0 = VectorAnchor(0f, 0f)
        val a1 = VectorAnchor(100f, 100f)
        val obj = VectorObject.createBezierPath(listOf(a0, a1))

        val inserted = VectorGeometryEngine.insertAnchor(obj, 1, VectorAnchor(50f, 25f))
        assertEquals(3, inserted.anchors.size)
        assertEquals(50f, inserted.anchors[1].x, 0.01f)
        assertEquals(25f, inserted.anchors[1].y, 0.01f)

        val deleted = VectorGeometryEngine.deleteAnchor(inserted, 1)
        assertEquals(2, deleted.anchors.size)
        assertEquals(100f, deleted.anchors[1].x, 0.01f)
    }

    @Test
    fun testVectorTransform_translateScaleRotateFlip() {
        val rect = VectorObject.createRectangle(10f, 10f, 100f, 80f)

        // Translate
        val translated = VectorGeometryEngine.translate(rect, 20f, 30f)
        assertEquals(30f, translated.rectData!!.x, 0.01f)
        assertEquals(40f, translated.rectData!!.y, 0.01f)

        // Scale around pivot (0, 0)
        val scaled = VectorGeometryEngine.scale(rect, 2f, 2f, 0f, 0f)
        assertEquals(20f, scaled.rectData!!.x, 0.01f)
        assertEquals(20f, scaled.rectData!!.y, 0.01f)
        assertEquals(200f, scaled.rectData!!.width, 0.01f)
        assertEquals(160f, scaled.rectData!!.height, 0.01f)

        // Rotate path anchors
        val line = VectorObject.createLine(100f, 0f, 200f, 0f)
        val rotated = VectorGeometryEngine.rotate(line, 90f, 100f, 0f)
        assertEquals(100f, rotated.anchors[0].x, 0.01f)
        assertEquals(0f, rotated.anchors[0].y, 0.01f)
        assertEquals(100f, rotated.anchors[1].x, 0.01f)
        assertEquals(100f, rotated.anchors[1].y, 0.01f)

        // Flip path horizontally around x=100
        val flipped = VectorGeometryEngine.flip(line, horizontal = true, vertical = false, pivotX = 100f, pivotY = 0f)
        assertEquals(100f, flipped.anchors[0].x, 0.01f)
        assertEquals(0f, flipped.anchors[0].y, 0.01f)
        assertEquals(0f, flipped.anchors[1].x, 0.01f)
    }

    @Test
    fun testVectorStyle_strokeFillOpacityAndRules() {
        val style = VectorStyle(
            strokeColor = 0xFF123456L,
            strokeWidth = 6.5f,
            fillColor = 0xFFABCDEFL,
            fillRule = FillRule.EVEN_ODD,
            strokeCap = StrokeCap.SQUARE,
            strokeJoin = StrokeJoin.BEVEL,
            strokeMiterLimit = 5.0f,
            opacity = 0.85f
        )
        val obj = VectorObject.createRectangle(0f, 0f, 50f, 50f, style = style)

        assertEquals(0xFF123456L, obj.style.strokeColor)
        assertEquals(6.5f, obj.style.strokeWidth, 0.01f)
        assertEquals(0xFFABCDEFL, obj.style.fillColor)
        assertEquals(FillRule.EVEN_ODD, obj.style.fillRule)
        assertEquals(StrokeCap.SQUARE, obj.style.strokeCap)
        assertEquals(StrokeJoin.BEVEL, obj.style.strokeJoin)
        assertEquals(5.0f, obj.style.strokeMiterLimit, 0.01f)
        assertEquals(0.85f, obj.style.opacity, 0.01f)
    }

    @Test
    fun testDocumentPersistence_saveAndReopenVectorObjects() {
        val rectObj = VectorObject.createRectangle(20f, 30f, 200f, 150f, rx = 8f, ry = 8f, style = VectorStyle(strokeColor = 0xFFFF0000L, fillColor = 0x8000FF00L))
        val ellipseObj = VectorObject.createEllipse(400f, 300f, 100f, 50f, style = VectorStyle(strokeColor = 0xFF0000FFL))
        val bezierObj = VectorObject.createBezierPath(
            anchors = listOf(
                VectorAnchor(50f, 500f, handleOutX = 100f, handleOutY = 450f),
                VectorAnchor(250f, 500f, handleInX = 200f, handleInY = 550f, isSmooth = true)
            ),
            isClosed = false,
            style = VectorStyle(strokeWidth = 5f, strokeCap = StrokeCap.ROUND)
        )

        val vectorLayer = LayerNode.VectorPathLayer(
            id = "vector_layer_01",
            name = "Geometric Art",
            vectorObjects = listOf(rectObj, ellipseObj, bezierObj)
        )

        val doc = createTestDoc(1024, 1024).withAddedLayer(vectorLayer)

        val json = DocumentSerializer.toJsonString(doc)
        val reopened = DocumentSerializer.fromJsonString(json)

        val restoredLayer = reopened.findLayerById("vector_layer_01") as? LayerNode.VectorPathLayer
        assertNotNull(restoredLayer)
        assertEquals(3, restoredLayer!!.vectorObjects.size)

        // 1. Rect
        val restoredRect = restoredLayer.vectorObjects[0]
        assertEquals(VectorShapeType.RECTANGLE, restoredRect.shapeType)
        assertEquals(20f, restoredRect.rectData!!.x, 0.01f)
        assertEquals(30f, restoredRect.rectData!!.y, 0.01f)
        assertEquals(200f, restoredRect.rectData!!.width, 0.01f)
        assertEquals(150f, restoredRect.rectData!!.height, 0.01f)
        assertEquals(0xFFFF0000L, restoredRect.style.strokeColor)
        assertEquals(0x8000FF00L, restoredRect.style.fillColor)

        // 2. Ellipse
        val restoredEllipse = restoredLayer.vectorObjects[1]
        assertEquals(VectorShapeType.ELLIPSE, restoredEllipse.shapeType)
        assertEquals(400f, restoredEllipse.ellipseData!!.cx, 0.01f)
        assertEquals(300f, restoredEllipse.ellipseData!!.cy, 0.01f)

        // 3. Bezier
        val restoredBezier = restoredLayer.vectorObjects[2]
        assertEquals(VectorShapeType.BEZIER_PATH, restoredBezier.shapeType)
        assertEquals(2, restoredBezier.anchors.size)
        assertEquals(100f, restoredBezier.anchors[0].handleOutX!!, 0.01f)
        assertEquals(200f, restoredBezier.anchors[1].handleInX!!, 0.01f)
        assertTrue(restoredBezier.anchors[1].isSmooth)
    }

    @Test
    fun testSvgImport_simpleGeometricShapesFixture() {
        val file = File(resolveFixtureDir(), "sample_vector_simple_01.svg")
        assertTrue("Fixture must exist: ${file.absolutePath}", file.exists())

        val result = SafeSvgParser.parse(file.readText())
        assertEquals(512, result.canvasWidth)
        assertEquals(512, result.canvasHeight)
        assertFalse(result.conversionReport.hasLossyConversions)

        // 5 shapes: background rect, red rounded rect, blue circle, green triangle polygon, orange ellipse
        assertEquals(5, result.vectorObjects.size)

        // Background rect
        val bgRect = result.vectorObjects[0]
        assertEquals(VectorShapeType.RECTANGLE, bgRect.shapeType)
        assertEquals(512f, bgRect.rectData!!.width, 0.01f)
        assertEquals(0xFFFAFAFA.toLong(), bgRect.style.fillColor)

        // Red rounded rect
        val redRect = result.vectorObjects[1]
        assertEquals(VectorShapeType.RECTANGLE, redRect.shapeType)
        assertEquals(64f, redRect.rectData!!.x, 0.01f)
        assertEquals(64f, redRect.rectData!!.y, 0.01f)
        assertEquals(160f, redRect.rectData!!.width, 0.01f)
        assertEquals(16f, redRect.rectData!!.rx, 0.01f)
        assertEquals(0xFFE74C3CL, redRect.style.fillColor)
        assertEquals(0xFFC0392BL, redRect.style.strokeColor)
        assertEquals(4f, redRect.style.strokeWidth, 0.01f)

        // Circle
        val circle = result.vectorObjects[2]
        assertEquals(VectorShapeType.ELLIPSE, circle.shapeType)
        assertEquals(360f, circle.ellipseData!!.cx, 0.01f)
        assertEquals(144f, circle.ellipseData!!.cy, 0.01f)
        assertEquals(80f, circle.ellipseData!!.rx, 0.01f)

        // Polygon
        val poly = result.vectorObjects[3]
        assertEquals(VectorShapeType.POLYGON, poly.shapeType)
        assertEquals(3, poly.anchors.size)
        assertTrue(poly.isClosed)

        // Ellipse
        val ellipse = result.vectorObjects[4]
        assertEquals(VectorShapeType.ELLIPSE, ellipse.shapeType)
        assertEquals(360f, ellipse.ellipseData!!.cx, 0.01f)
        assertEquals(376f, ellipse.ellipseData!!.cy, 0.01f)
        assertEquals(96f, ellipse.ellipseData!!.rx, 0.01f)
        assertEquals(64f, ellipse.ellipseData!!.ry, 0.01f)
    }

    @Test
    fun testSvgImport_denseCubicAndQuadBezierFixture() {
        val file = File(resolveFixtureDir(), "sample_vector_dense_02.svg")
        assertTrue("Fixture must exist: ${file.absolutePath}", file.exists())

        val result = SafeSvgParser.parse(file.readText())
        assertEquals(1024, result.canvasWidth)
        assertEquals(1024, result.canvasHeight)
        assertFalse(result.conversionReport.hasLossyConversions)

        // 1 background rect + 5 paths + 5 lines = 11 objects
        assertEquals(11, result.vectorObjects.size)

        // Check first complex path (Cubic C with close Z)
        val mainContour = result.vectorObjects[1]
        val segs = mainContour.toSegments()
        assertTrue("Main contour must contain segments", segs.isNotEmpty())
        assertTrue("First segment must be MoveTo", segs[0] is VectorSegment.MoveTo)
        assertTrue("Must contain cubic curves", segs.any { it is VectorSegment.CubicTo })
        assertTrue("Must close path with Z", segs.any { it is VectorSegment.Close })

        // Check path with Quad Q and smooth T
        val flourishPath = result.vectorObjects[2]
        val flourishSegs = flourishPath.toSegments()
        assertTrue("Flourish path must contain QuadTo", flourishSegs.any { it is VectorSegment.QuadTo })
    }

    @Test
    fun testSvgExport_roundTripStructuralPreservation() {
        val file = File(resolveFixtureDir(), "sample_vector_simple_01.svg")
        val initialResult = SafeSvgParser.parse(file.readText())

        // Export parsed objects back to SVG
        val exportedSvg = SafeSvgExporter.exportObjects(
            objects = initialResult.vectorObjects,
            width = initialResult.canvasWidth,
            height = initialResult.canvasHeight
        )

        // Re-import exported SVG
        val roundTripResult = SafeSvgParser.parse(exportedSvg)
        assertEquals(initialResult.vectorObjects.size, roundTripResult.vectorObjects.size)

        for (i in initialResult.vectorObjects.indices) {
            val orig = initialResult.vectorObjects[i]
            val roundTrip = roundTripResult.vectorObjects[i]
            assertEquals(orig.shapeType, roundTrip.shapeType)
            assertEquals(orig.style.fillColor, roundTrip.style.fillColor)
            assertEquals(orig.style.strokeColor, roundTrip.style.strokeColor)
            assertEquals(orig.style.strokeWidth, roundTrip.style.strokeWidth, 0.01f)
        }
    }

    @Test
    fun testSvgImport_unsupportedElementsLoggedToConversionReport() {
        val svgWithUnsupported = """
            <svg viewBox="0 0 500 500">
                <defs>
                    <linearGradient id="grad1"><stop offset="0%" stop-color="red"/></linearGradient>
                    <filter id="f1"><feGaussianBlur stdDeviation="2"/></filter>
                </defs>
                <text x="20" y="50">Unsupported Text</text>
                <rect x="10" y="10" width="100" height="100" fill="blue"/>
            </svg>
        """.trimIndent()

        val result = SafeSvgParser.parse(svgWithUnsupported)
        assertEquals(1, result.vectorObjects.size)
        assertTrue(result.conversionReport.hasLossyConversions)
        assertEquals(3, result.conversionReport.warningCount)
        assertTrue(result.conversionReport.warnings.any { it.layerName == "lineargradient" })
        assertTrue(result.conversionReport.warnings.any { it.layerName == "filter" })
        assertTrue(result.conversionReport.warnings.any { it.layerName == "text" })
    }

    @Test
    fun testSvgImport_malformedXmlHandledGracefullyWithoutCrash() {
        val malformedFile = File(resolveCorruptFixtureDir(), "malformed_tags_03.svg")
        if (malformedFile.exists()) {
            val result = SafeSvgParser.parse(malformedFile.readText())
            // Must not throw an unrecovered exception
            assertNotNull(result)
            assertTrue(result.conversionReport.hasLossyConversions)
        }
    }

    @Test
    fun testExplicitRasterization_replacesVectorWithRasterAndIsUndoable() {
        val obj1 = VectorObject.createRectangle(50f, 50f, 200f, 150f, style = VectorStyle(fillColor = 0xFFFF0000L))
        val obj2 = VectorObject.createCircle(200f, 200f, 80f, style = VectorStyle(strokeColor = 0xFF00FF00L, strokeWidth = 8f))

        val vectorLayer = LayerNode.VectorPathLayer(
            id = "vector_test_layer",
            name = "Shapes Layer",
            vectorObjects = listOf(obj1, obj2)
        )

        var doc = createTestDoc(512, 512).withAddedLayer(vectorLayer)
        assertEquals(2, doc.allLayersFlat().size)

        // 1. Explicit rasterize
        val cmd = RasterizeVectorLayerCommand.create(doc, "vector_test_layer", createNewLayer = false)
        assertEquals(512, cmd.rasterizedLayer.width)
        assertEquals(512, cmd.rasterizedLayer.height)

        doc = cmd.apply(doc)
        val activeLayerAfter = doc.findLayerById("vector_test_layer")
        assertTrue("Must be converted to RasterLayer", activeLayerAfter is LayerNode.RasterLayer)

        // 2. Undo rasterize
        doc = cmd.revert(doc)
        val activeLayerReverted = doc.findLayerById("vector_test_layer")
        assertTrue("Must be reverted back to VectorPathLayer", activeLayerReverted is LayerNode.VectorPathLayer)
        val vLayer = activeLayerReverted as LayerNode.VectorPathLayer
        assertEquals(2, vLayer.vectorObjects.size)
        assertEquals(VectorShapeType.RECTANGLE, vLayer.vectorObjects[0].shapeType)
        assertEquals(VectorShapeType.ELLIPSE, vLayer.vectorObjects[1].shapeType)
    }

    @Test
    fun testExplicitRasterization_createNewLayerLeavesOriginalVectorIntact() {
        val obj = VectorObject.createRectangle(10f, 10f, 100f, 100f)
        val vectorLayer = LayerNode.VectorPathLayer(id = "vector_orig", name = "Original Vector", vectorObjects = listOf(obj))
        var doc = createTestDoc().withAddedLayer(vectorLayer)

        val cmd = RasterizeVectorLayerCommand.create(doc, "vector_orig", createNewLayer = true)
        doc = cmd.apply(doc)

        // Original vector layer is untouched, new raster layer is added
        val orig = doc.findLayerById("vector_orig")
        assertTrue(orig is LayerNode.VectorPathLayer)
        val newRaster = doc.findLayerById(cmd.rasterizedLayer.id)
        assertTrue(newRaster is LayerNode.RasterLayer)

        // Revert removes the new raster layer
        doc = cmd.revert(doc)
        assertNull(doc.findLayerById(cmd.rasterizedLayer.id))
        assertNotNull(doc.findLayerById("vector_orig"))
    }

    @Test
    fun testVectorEditSession_interactiveToolInteractionsAndCommands() {
        val vectorLayer = LayerNode.VectorPathLayer(id = "vlayer", name = "Vectors")
        val doc = createTestDoc().withAddedLayer(vectorLayer)

        val session = VectorEditSession(activeLayerId = "vlayer", currentTool = VectorToolMode.RECTANGLE)

        // 1. Drag to create rectangle
        session.handlePointerDown(doc, 50f, 50f)
        session.handlePointerMove(250f, 150f)
        val cmd = session.handlePointerUp(doc, 250f, 150f)

        assertNotNull(cmd)
        assertTrue(cmd is AddVectorObjectCommand)
        val addCmd = cmd as AddVectorObjectCommand
        assertEquals(VectorShapeType.RECTANGLE, addCmd.vectorObject.shapeType)
        assertEquals(50f, addCmd.vectorObject.rectData!!.x, 0.01f)
        assertEquals(50f, addCmd.vectorObject.rectData!!.y, 0.01f)
        assertEquals(200f, addCmd.vectorObject.rectData!!.width, 0.01f)
        assertEquals(100f, addCmd.vectorObject.rectData!!.height, 0.01f)

        val docWithRect = addCmd.apply(doc)

        // 2. Select and move
        session.currentTool = VectorToolMode.SELECT
        session.handlePointerDown(docWithRect, 100f, 100f)
        assertEquals(addCmd.vectorObject.id, session.selectedObjectId)

        session.handlePointerMove(120f, 130f)
        val moveCmd = session.handlePointerUp(docWithRect, 120f, 130f)
        assertNotNull(moveCmd)
    }
}
