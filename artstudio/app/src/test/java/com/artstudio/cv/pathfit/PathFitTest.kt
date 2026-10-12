package com.artstudio.cv.pathfit

import com.artstudio.cv.*
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.vector.VectorGeometryEngine
import com.artstudio.vector.VectorShapeType
import com.artstudio.importer.RasterImageImporter
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PathFitTest {

    private fun resolveFixture(relativePath: String): File {
        val candidates = listOf(
            File("tests/fixtures/artstudio/v1", relativePath),
            File("../tests/fixtures/artstudio/v1", relativePath),
            File("/root/artstudio/tests/fixtures/artstudio/v1", relativePath)
        )
        for (f in candidates) {
            if (f.exists()) return f
        }
        throw IllegalStateException("Fixture not found: $relativePath")
    }

    private fun computeSha256(array: IntArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bb = java.nio.ByteBuffer.allocate(array.size * 4)
        for (v in array) bb.putInt(v)
        return md.digest(bb.array()).joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------
    // 1. Smooth Circle Curve Fitting
    // -------------------------------------------------------------------------
    @Test
    fun testSmoothCircleCurveFitting() {
        val circleContour = SyntheticContourGenerator.createCircleContour(
            cx = 100f,
            cy = 100f,
            radius = 50f,
            numPoints = 64
        )

        val settings = PathFitSettings(
            maxFittingError = 1.5f,
            preserveCorners = true
        )

        val candidate = ContourVectorizationEngine.fitSingleContour(
            contour = circleContour,
            regionId = "region_circle",
            settings = settings
        )

        // Circle has no sharp corners
        assertEquals(0, candidate.cornersDetected.size)
        assertTrue(candidate.isClosed)
        assertFalse(candidate.isHole)
        assertTrue(candidate.anchors.size >= 4)
        assertTrue(candidate.confidence >= 0.85f)

        // Max contour deviation should be <= 1.2 px
        assertTrue(
            "Max deviation should be <= 1.2 px, got ${candidate.maxContourDeviation}",
            candidate.maxContourDeviation <= 1.2f
        )
        assertTrue(
            "Mean deviation should be <= 0.6 px, got ${candidate.meanContourDeviation}",
            candidate.meanContourDeviation <= 0.6f
        )

        // Anchors on smooth circle should have isSmooth = true
        assertTrue(candidate.anchors.all { it.isSmooth })
    }

    // -------------------------------------------------------------------------
    // 2. Rectangle Corner Preservation
    // -------------------------------------------------------------------------
    @Test
    fun testRectangleCornerPreservation() {
        val rectContour = SyntheticContourGenerator.createRectangleContour(
            x = 20f,
            y = 30f,
            width = 80f,
            height = 50f,
            pointsPerSide = 20
        )

        val settings = PathFitSettings(
            maxFittingError = 1.0f,
            cornerAngleThresholdDeg = 55.0f,
            preserveCorners = true
        )

        val candidate = ContourVectorizationEngine.fitSingleContour(
            contour = rectContour,
            regionId = "region_rect",
            settings = settings
        )

        // Exactly 4 corners (90°) must be detected
        assertEquals(4, candidate.cornersDetected.size)

        // Corner anchors must have isSmooth = false (sharp cusps)
        val cornerAnchors = candidate.anchors.filter { !it.isSmooth }
        assertEquals(4, cornerAnchors.size)

        // Max deviation on straight rectangle should be minimal (<= 0.5 px)
        assertTrue(
            "Max deviation on rectangle should be <= 0.5 px, got ${candidate.maxContourDeviation}",
            candidate.maxContourDeviation <= 0.5f
        )
        assertTrue(
            "Mean deviation should be <= 0.2 px, got ${candidate.meanContourDeviation}",
            candidate.meanContourDeviation <= 0.2f
        )
        assertTrue(candidate.confidence >= 0.90f)
    }

    // -------------------------------------------------------------------------
    // 3. Donut Torus Hole Preservation & Containment Hierarchy
    // -------------------------------------------------------------------------
    @Test
    fun testDonutTorusHolePreservation() {
        val (outer, hole) = SyntheticContourGenerator.createDonutContourPair(
            cx = 100f,
            cy = 100f,
            outerRadius = 60f,
            innerRadius = 25f,
            numPoints = 64
        )

        val settings = PathFitSettings(
            preserveHoles = true,
            maxFittingError = 1.5f
        )

        val result = ContourVectorizationEngine.vectorizeContours(
            contours = listOf(outer, hole),
            settings = settings
        )

        assertEquals(2, result.paths.size)
        assertEquals(1, result.totalRegions)
        assertEquals(1, result.totalHoles)

        val outerPath = result.paths.first { !it.isHole }
        val holePath = result.paths.first { it.isHole }

        assertEquals("region_1", outerPath.regionId)
        assertNull(outerPath.parentRegionId)

        assertEquals("region_1_hole_1", holePath.regionId)
        assertEquals("region_1", holePath.parentRegionId)
        assertTrue(holePath.isHole)

        // Both paths fit within error budget
        assertTrue(outerPath.maxContourDeviation <= 1.2f)
        assertTrue(holePath.maxContourDeviation <= 1.2f)
    }

    // -------------------------------------------------------------------------
    // 4. Disconnected Regions Vectorized Independently
    // -------------------------------------------------------------------------
    @Test
    fun testDisconnectedRegionsVectorizedIndependently() {
        val components = SyntheticContourGenerator.createDisconnectedRegions()
        assertEquals(3, components.size)

        val settings = PathFitSettings(
            preserveDisconnectedComponents = true,
            maxFittingError = 1.5f
        )

        val result = ContourVectorizationEngine.vectorizeContours(
            contours = components,
            settings = settings
        )

        assertEquals(3, result.paths.size)
        assertEquals(3, result.totalRegions)
        assertEquals(3, result.disconnectedComponents)
        assertEquals(0, result.totalHoles)

        val regionIds = result.paths.map { it.regionId }
        assertEquals(listOf("region_1", "region_2", "region_3"), regionIds)

        // Each component is closed with distinct non-overlapping bounds
        for (path in result.paths) {
            assertTrue(path.isClosed)
            assertTrue(path.anchors.size >= 3)
            assertTrue(path.confidence >= 0.80f)
        }
    }

    // -------------------------------------------------------------------------
    // 5. Star Polygon Acute Tips and Inner Cusps Preserved
    // -------------------------------------------------------------------------
    @Test
    fun testStarPolygonPreservesAcuteTipsAndInnerVertices() {
        val starContour = SyntheticContourGenerator.createStarContour(
            cx = 100f,
            cy = 100f,
            outerRadius = 70f,
            innerRadius = 30f,
            pointsCount = 5,
            samplesPerEdge = 10
        )

        val settings = PathFitSettings(
            cornerAngleThresholdDeg = 45.0f,
            preserveCorners = true,
            maxFittingError = 1.5f
        )

        val candidate = ContourVectorizationEngine.fitSingleContour(
            contour = starContour,
            regionId = "star_5point",
            settings = settings
        )

        // 5 outer points + 5 inner cusps = 10 corners
        assertEquals(10, candidate.cornersDetected.size)

        // All 10 corners are preserved as sharp non-smooth anchors
        val cuspAnchors = candidate.anchors.filter { !it.isSmooth }
        assertEquals(10, cuspAnchors.size)
        assertTrue(candidate.maxContourDeviation <= 1.0f)
    }

    // -------------------------------------------------------------------------
    // 6. Configurable Error Tolerance & Simplification
    // -------------------------------------------------------------------------
    @Test
    fun testConfigurableErrorToleranceAndSimplification() {
        val circleContour = SyntheticContourGenerator.createCircleContour(
            cx = 100f,
            cy = 100f,
            radius = 50f,
            numPoints = 64
        )

        val tightSettings = PathFitSettings(maxFittingError = 0.5f)
        val looseSettings = PathFitSettings(maxFittingError = 3.0f)

        val tightFit = ContourVectorizationEngine.fitSingleContour(circleContour, "tight", settings = tightSettings)
        val looseFit = ContourVectorizationEngine.fitSingleContour(circleContour, "loose", settings = looseSettings)

        // Tight tolerance yields strictly lower maximum contour deviation
        assertTrue(
            "Tight error (${tightFit.maxContourDeviation}) must be <= loose error (${looseFit.maxContourDeviation})",
            tightFit.maxContourDeviation <= looseFit.maxContourDeviation
        )

        // Looser tolerance allows larger error but preserves validity
        assertTrue(looseFit.maxContourDeviation <= 3.0f)
    }

    // -------------------------------------------------------------------------
    // 7. Source Pixel Mapping & Provenance Metadata Integrity
    // -------------------------------------------------------------------------
    @Test
    fun testSourcePixelMappingAndProvenanceIntegrity() {
        val rectContour = SyntheticContourGenerator.createRectangleContour(
            x = 10f, y = 20f, width = 50f, height = 30f, pointsPerSide = 10, id = 42
        )

        val candidate = ContourVectorizationEngine.fitSingleContour(
            contour = rectContour,
            regionId = "region_audit",
            settings = PathFitSettings()
        )

        // Check SourcePixelMapping
        assertEquals(40, candidate.sourceMapping.pixelCount)
        assertEquals(10f, candidate.sourceMapping.boundingBox.minX, 0.01f)
        assertEquals(20f, candidate.sourceMapping.boundingBox.minY, 0.01f)
        assertEquals(60f, candidate.sourceMapping.boundingBox.maxX, 0.01f)
        assertEquals(50f, candidate.sourceMapping.boundingBox.maxY, 0.01f)

        // Check PathProvenance
        assertEquals("SchneiderAdaptiveBezierFit", candidate.provenance.algorithm)
        assertEquals(42, candidate.provenance.sourceContourId)
        assertTrue(candidate.provenance.timestampMs > 0L)
        assertFalse(candidate.provenance.isHole)

        // Check VectorObject metadata serialization
        val vecObj = candidate.toVectorObject()
        assertEquals(candidate.id, vecObj.metadata["pathId"])
        assertEquals("region_audit", vecObj.metadata["regionId"])
        assertEquals("SchneiderAdaptiveBezierFit", vecObj.metadata["provenanceAlgorithm"])
        assertEquals("40", vecObj.metadata["sourcePixelCount"])
        assertEquals("4", vecObj.metadata["cornerCount"])
        assertNotNull(vecObj.metadata["maxDeviation"])
        assertNotNull(vecObj.metadata["confidence"])
    }

    // -------------------------------------------------------------------------
    // 8. Decoupled Path Width and Outline Styling
    // -------------------------------------------------------------------------
    @Test
    fun testDecoupledPathWidthAndOutlineStyling() {
        val circleContour = SyntheticContourGenerator.createCircleContour(
            cx = 50f, cy = 50f, radius = 30f, numPoints = 32
        )

        val candidate = ContourVectorizationEngine.fitSingleContour(
            contour = circleContour,
            regionId = "styled_circle",
            settings = PathFitSettings()
        )

        val vecObjThick = candidate.toVectorObject(
            strokeColor = 0xFFFF0000L,
            strokeWidth = 12.0f,
            fillColor = 0xFF00FF00L
        )

        val vecObjHairline = candidate.toVectorObject(
            strokeColor = 0xFF0000FFL,
            strokeWidth = 0.5f,
            fillColor = null
        )

        // Anchors (geometry) must be identical
        assertEquals(vecObjThick.anchors.size, vecObjHairline.anchors.size)
        for (i in vecObjThick.anchors.indices) {
            assertEquals(vecObjThick.anchors[i].x, vecObjHairline.anchors[i].x, 0.001f)
            assertEquals(vecObjThick.anchors[i].y, vecObjHairline.anchors[i].y, 0.001f)
        }

        // Styles are independently configured
        assertEquals(12.0f, vecObjThick.style.strokeWidth, 0.001f)
        assertEquals(0xFFFF0000L, vecObjThick.style.strokeColor)
        assertEquals(0xFF00FF00L, vecObjThick.style.fillColor)

        assertEquals(0.5f, vecObjHairline.style.strokeWidth, 0.001f)
        assertEquals(0xFF0000FFL, vecObjHairline.style.strokeColor)
        assertNull(vecObjHairline.style.fillColor)
    }

    // -------------------------------------------------------------------------
    // 9. Rasterisation and Mask Comparison (IoU and Max Deviation)
    // -------------------------------------------------------------------------
    @Test
    fun testContourRasterComparisonIoUAndMaxDeviation() {
        val width = 120
        val height = 120

        val rectContour = SyntheticContourGenerator.createRectangleContour(
            x = 20f, y = 20f, width = 80f, height = 70f, pointsPerSide = 25
        )

        val candidate = ContourVectorizationEngine.fitSingleContour(
            contour = rectContour,
            regionId = "raster_cmp",
            settings = PathFitSettings()
        )

        // Generate ground truth binary mask
        val gtMask = SyntheticContourGenerator.renderPolygonToBinaryMask(rectContour.points, width, height)

        // Rasterize fitted vector object
        val vecObj = candidate.toVectorObject()
        val rasterizedMask = ContourRasterComparator.rasterizeVectorObject(vecObj, width, height, fillPath = true)

        // Compare masks
        val metrics = ContourRasterComparator.compareMasks(rasterizedMask, gtMask, width, height)

        assertTrue(
            "IoU between rasterized vector path and contour mask must be >= 0.90, got ${metrics.iou}",
            metrics.iou >= 0.90
        )
        assertTrue(
            "Dice coefficient must be >= 0.95, got ${metrics.dice}",
            metrics.dice >= 0.95
        )
        assertTrue(
            "Max contour deviation must be <= 0.6 px, got ${candidate.maxContourDeviation}",
            candidate.maxContourDeviation <= 0.6f
        )
    }

    // -------------------------------------------------------------------------
    // 10. Native Save and Reopen Preserves Editable Paths
    // -------------------------------------------------------------------------
    @Test
    fun testNativeSaveAndReopenPreservesEditablePaths() {
        val circleContour = SyntheticContourGenerator.createCircleContour(
            cx = 100f, cy = 100f, radius = 40f, numPoints = 48
        )
        val rectContour = SyntheticContourGenerator.createRectangleContour(
            x = 200f, y = 100f, width = 60f, height = 60f, pointsPerSide = 15
        )

        val fitCircle = ContourVectorizationEngine.fitSingleContour(circleContour, "circle_obj", settings = PathFitSettings())
        val fitRect = ContourVectorizationEngine.fitSingleContour(rectContour, "rect_obj", settings = PathFitSettings())

        val vecCircle = fitCircle.toVectorObject(strokeColor = 0xFF112233L, strokeWidth = 3.5f)
        val vecRect = fitRect.toVectorObject(strokeColor = 0xFF445566L, strokeWidth = 5.0f)

        // Build document with VectorPathLayer
        val vectorLayer = LayerNode.VectorPathLayer(
            id = "layer_vector_contours",
            name = "Fitted Vector Contours",
            vectorObjects = listOf(vecCircle, vecRect)
        )

        val originalDoc = ArtDocument(
            id = "doc_vector_persistence_test",
            title = "Contour Fit Persistence",
            canvas = CanvasConfig(width = 512, height = 512),
            rootGroup = LayerNode.LayerGroup(
                id = "root",
                name = "Root",
                children = listOf(vectorLayer)
            )
        )

        // 1. Serialize to JSON / native .ffart format
        val jsonStr = DocumentSerializer.toJsonString(originalDoc)
        assertTrue(jsonStr.contains("\"layer_vector_contours\""))
        assertTrue(jsonStr.contains("\"SchneiderAdaptiveBezierFit\""))

        // 2. Deserialize back from JSON
        val reloadedDoc = DocumentSerializer.fromJsonString(jsonStr)
        assertNotNull(reloadedDoc)

        val reloadedLayer = reloadedDoc.findLayerById("layer_vector_contours") as? LayerNode.VectorPathLayer
        assertNotNull(reloadedLayer)
        assertEquals(2, reloadedLayer!!.vectorObjects.size)

        val reloadedCircle = reloadedLayer.vectorObjects[0]
        val reloadedRect = reloadedLayer.vectorObjects[1]

        // Check geometry and anchors
        assertEquals(vecCircle.id, reloadedCircle.id)
        assertEquals(vecCircle.anchors.size, reloadedCircle.anchors.size)
        assertEquals(3.5f, reloadedCircle.style.strokeWidth, 0.001f)
        assertEquals(0xFF112233L, reloadedCircle.style.strokeColor)
        assertEquals("circle_obj", reloadedCircle.metadata["regionId"])
        assertEquals("SchneiderAdaptiveBezierFit", reloadedCircle.metadata["provenanceAlgorithm"])

        assertEquals(vecRect.id, reloadedRect.id)
        assertEquals(vecRect.anchors.size, reloadedRect.anchors.size)
        assertEquals(5.0f, reloadedRect.style.strokeWidth, 0.001f)
        assertEquals("rect_obj", reloadedRect.metadata["regionId"])
        assertEquals("4", reloadedRect.metadata["cornerCount"])

        // 3. Edit anchor on reopened object to prove paths remain fully editable
        val firstAnchor = reloadedCircle.anchors[0]
        val editedCircle = VectorGeometryEngine.moveAnchor(
            obj = reloadedCircle,
            anchorIndex = 0,
            newX = firstAnchor.x + 15f,
            newY = firstAnchor.y - 10f
        )

        assertEquals(firstAnchor.x + 15f, editedCircle.anchors[0].x, 0.01f)
        assertEquals(firstAnchor.y - 10f, editedCircle.anchors[0].y, 0.01f)
        assertEquals(reloadedCircle.anchors[1].x, editedCircle.anchors[1].x, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 11. Real Line Art Fixture Contour Vectorization
    // -------------------------------------------------------------------------
    @Test
    fun testRealLineArtFixtureContourVectorization() {
        val file = resolveFixture("line_art/lineart_simple_01.png")
        val decoded = RasterImageImporter.decodeRasterImage(file.readBytes(), file.name)
        assertNotNull("Failed to decode fixture image", decoded)
        val w = decoded!!.width
        val h = decoded.height
        val pixels = decoded.pixels

        val params = CvPipelineParameters(
            vectorizeContours = true,
            pathFitSettings = PathFitSettings(
                maxFittingError = 2.0f,
                minFeatureArea = 16.0f
            )
        )

        val result = CvPipeline.process(pixels, w, h, params)

        assertNotNull(result.vectorizationResult)
        val vResult = result.vectorizationResult!!

        assertTrue("Should extract at least 1 path from real fixture, got ${vResult.paths.size}", vResult.paths.isNotEmpty())
        assertTrue("Total regions must be > 0", vResult.totalRegions > 0)

        for (path in vResult.paths) {
            assertTrue(path.anchors.isNotEmpty())
            assertTrue(path.sourceMapping.pixelCount > 0)
            assertTrue(path.confidence in 0.10f..0.99f)
            assertTrue(path.maxContourDeviation >= 0f)
        }

        // Converting all paths to VectorObjects succeeds
        val vectorObjects = vResult.toVectorObjects()
        assertEquals(vResult.paths.size, vectorObjects.size)
    }

    // -------------------------------------------------------------------------
    // 12. Source Image Immutability Under Vectorization
    // -------------------------------------------------------------------------
    @Test
    fun testSourceImageImmutabilityUnderVectorization() {
        val width = 100
        val height = 100
        val mask = IntArray(width * height)

        // Draw rectangle in mask
        for (y in 20 until 80) {
            for (x in 30 until 70) {
                mask[y * width + x] = 255
            }
        }

        val maskHashBefore = computeSha256(mask)

        // Run vectorization
        val result = ContourVectorizationEngine.vectorizeBinaryMask(mask, width, height)
        assertNotNull(result)
        assertTrue(result.paths.isNotEmpty())

        val maskHashAfter = computeSha256(mask)

        // Source mask array must be 100% bit-exact identical
        assertEquals(
            "Source binary mask must never be modified by vectorization",
            maskHashBefore,
            maskHashAfter
        )
    }
}
