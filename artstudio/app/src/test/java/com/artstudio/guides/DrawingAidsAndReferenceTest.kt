package com.artstudio.guides

import com.artstudio.color.ColorData
import com.artstudio.color.EyedropperSampleMode
import com.artstudio.core.BrushStroke
import com.artstudio.core.StrokePoint
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class DrawingAidsAndReferenceTest {

    @Test
    fun testLineGuide_projectionAndSnappingPrecision() {
        // Horizontal line at y = 500
        val lineConfig = LineGuideConfig(originX = 0f, originY = 500f, angleDegrees = 0f)
        val aidsConfig = DrawingAidsConfig(
            guideType = GuideType.STRAIGHT_LINE,
            snappingEnabled = true,
            snapDistance = 25f,
            lineGuide = lineConfig
        )

        // Point at (300, 508) is 8px away (< 25px) -> must snap to (300, 500)
        val snapped = DrawingAidsEngine.snapPoint(300f, 508f, aidsConfig)
        assertEquals(300f, snapped.first, 0.001f)
        assertEquals(500f, snapped.second, 0.001f)

        // Verify precision is within 0.5 document pixels
        assertTrue(abs(snapped.second - 500f) <= 0.5f)

        // Slanted line at 45° passing through (100, 100)
        val slantedConfig = LineGuideConfig(originX = 100f, originY = 100f, angleDegrees = 45f)
        val slantedAids = aidsConfig.copy(lineGuide = slantedConfig)

        // Point at (200, 205) -> projection onto y - 100 = x - 100 is (202.5, 202.5)
        val slantedSnapped = DrawingAidsEngine.snapPoint(200f, 205f, slantedAids)
        val proj = DrawingAidsEngine.projectPointToLine(200f, 205f, 100f, 100f, 45f)
        assertEquals(proj.first, slantedSnapped.first, 0.01f)
        assertEquals(proj.second, slantedSnapped.second, 0.01f)
        assertTrue(abs(slantedSnapped.first - proj.first) <= 0.5f)
    }

    @Test
    fun testEllipseGuide_circlePerimeterSnappingPrecision() {
        val circleConfig = EllipseGuideConfig(
            centerX = 500f,
            centerY = 500f,
            radiusX = 200f,
            radiusY = 200f
        )
        val aidsConfig = DrawingAidsConfig(
            guideType = GuideType.CIRCLE_ELLIPSE,
            snappingEnabled = true,
            snapDistance = 30f,
            ellipseGuide = circleConfig
        )

        // Point at (500, 715) is 15px outside circle perimeter (dist from center = 215, radius = 200)
        val snapped = DrawingAidsEngine.snapPoint(500f, 715f, aidsConfig)
        assertEquals(500f, snapped.first, 0.001f)
        assertEquals(700f, snapped.second, 0.001f)
        assertTrue(abs(snapped.second - 700f) <= 0.5f)

        // Point inside circle at (500, 685) is 15px inside circle perimeter
        val snappedInner = DrawingAidsEngine.snapPoint(500f, 685f, aidsConfig)
        assertEquals(500f, snappedInner.first, 0.001f)
        assertEquals(700f, snappedInner.second, 0.001f)
    }

    @Test
    fun testEllipseGuide_generalOrientedEllipseSnappingPrecision() {
        val ellipseConfig = EllipseGuideConfig(
            centerX = 1000f,
            centerY = 1000f,
            radiusX = 400f,
            radiusY = 200f,
            rotationDegrees = 0f
        )
        val aidsConfig = DrawingAidsConfig(
            guideType = GuideType.CIRCLE_ELLIPSE,
            snappingEnabled = true,
            snapDistance = 25f,
            ellipseGuide = ellipseConfig
        )

        // Point near right vertex (1410, 1000) -> vertex is at (1400, 1000)
        val snappedVertex = DrawingAidsEngine.snapPoint(1410f, 1000f, aidsConfig)
        assertEquals(1400f, snappedVertex.first, 0.01f)
        assertEquals(1000f, snappedVertex.second, 0.01f)
        assertTrue(abs(snappedVertex.first - 1400f) <= 0.5f)

        // Point near top vertex (1000, 790) -> vertex is at (1000, 800)
        val snappedTop = DrawingAidsEngine.snapPoint(1000f, 790f, aidsConfig)
        assertEquals(1000f, snappedTop.first, 0.01f)
        assertEquals(800f, snappedTop.second, 0.01f)
        assertTrue(abs(snappedTop.second - 800f) <= 0.5f)
    }

    @Test
    fun testRadialGuide_angularRaysSnapping() {
        // 8 rays = every 45° radiating from (1000, 1000)
        val radialConfig = RadialGuideConfig(centerX = 1000f, centerY = 1000f, rayCount = 8)
        val aidsConfig = DrawingAidsConfig(
            guideType = GuideType.RADIAL,
            snappingEnabled = true,
            snapDistance = 50f,
            radialGuide = radialConfig
        )

        // Point at (1200, 1005) is near horizontal ray (0° / angle = 1.4°)
        val snapped = DrawingAidsEngine.snapPoint(1200f, 1005f, aidsConfig)
        assertEquals(1200f, snapped.first, 0.05f)
        assertEquals(1000f, snapped.second, 0.05f)
        assertTrue(abs(snapped.second - 1000f) <= 0.5f)
    }

    @Test
    fun testPerspectiveGuide_vanishingPointsSnapping() {
        val perspConfig = PerspectiveGuideConfig(
            type = PerspectiveType.TWO_POINT,
            horizonY = 1000f,
            vp1 = VanishingPoint(x = -1000f, y = 1000f),
            vp2 = VanishingPoint(x = 3000f, y = 1000f),
            raySpacingDegrees = 15f,
            snapToVerticals = false
        )
        val aidsConfig = DrawingAidsConfig(
            guideType = GuideType.PERSPECTIVE,
            snappingEnabled = true,
            snapDistance = 30f,
            perspectiveGuide = perspConfig
        )

        // Point slightly off horizon (1040, 1008) -> snaps to horizon at (1040, 1000)
        val snappedHorizon = DrawingAidsEngine.snapPoint(1040f, 1008f, aidsConfig)
        assertEquals(1040f, snappedHorizon.first, 0.01f)
        assertEquals(1000f, snappedHorizon.second, 0.01f)
        assertTrue(abs(snappedHorizon.second - 1000f) <= 0.5f)
    }

    @Test
    fun testSnapping_respectsSnapDistanceThreshold() {
        val lineConfig = LineGuideConfig(originX = 0f, originY = 500f, angleDegrees = 0f)
        val aidsConfig = DrawingAidsConfig(
            guideType = GuideType.STRAIGHT_LINE,
            snappingEnabled = true,
            snapDistance = 15f,
            lineGuide = lineConfig
        )

        // Point at (400, 530) has distance 30px > snapDistance (15px)
        // Must NOT snap; original coordinate must be preserved
        val notSnapped = DrawingAidsEngine.snapPoint(400f, 530f, aidsConfig)
        assertEquals(400f, notSnapped.first, 0.001f)
        assertEquals(530f, notSnapped.second, 0.001f)
    }

    @Test
    fun testVerticalMirrorSymmetry_exactCoordinateReflection() {
        val symmetryConfig = SymmetryConfig(
            type = SymmetryType.MIRROR_VERTICAL,
            centerX = 1024f,
            centerY = 1024f
        )

        val ptX = 824f
        val ptY = 600f
        val symPoints = DrawingAidsEngine.computeSymmetryPoints(ptX, ptY, symmetryConfig)

        assertEquals(2, symPoints.size)
        // Original point
        assertEquals(824f, symPoints[0].first, 0.0001f)
        assertEquals(600f, symPoints[0].second, 0.0001f)

        // Reflected point: x' = 2*1024 - 824 = 1224, y' = 600
        assertEquals(1224f, symPoints[1].first, 0.0001f)
        assertEquals(600f, symPoints[1].second, 0.0001f)

        // Error is < 0.5 document pixels
        assertTrue(abs(symPoints[1].first - 1224f) <= 0.5f)
    }

    @Test
    fun testHorizontalMirrorSymmetry_exactCoordinateReflection() {
        val symmetryConfig = SymmetryConfig(
            type = SymmetryType.MIRROR_HORIZONTAL,
            centerX = 1024f,
            centerY = 1024f
        )

        val ptX = 500f
        val ptY = 824f
        val symPoints = DrawingAidsEngine.computeSymmetryPoints(ptX, ptY, symmetryConfig)

        assertEquals(2, symPoints.size)
        assertEquals(500f, symPoints[0].first, 0.0001f)
        assertEquals(824f, symPoints[0].second, 0.0001f)

        // Reflected point: x' = 500, y' = 2*1024 - 824 = 1224
        assertEquals(500f, symPoints[1].first, 0.0001f)
        assertEquals(1224f, symPoints[1].second, 0.0001f)
        assertTrue(abs(symPoints[1].second - 1224f) <= 0.5f)
    }

    @Test
    fun testDualMirrorSymmetry_fourQuadrants() {
        val symmetryConfig = SymmetryConfig(
            type = SymmetryType.MIRROR_DUAL,
            centerX = 1000f,
            centerY = 1000f
        )

        val symPoints = DrawingAidsEngine.computeSymmetryPoints(800f, 700f, symmetryConfig)
        assertEquals(4, symPoints.size)

        // 1. Original (800, 700)
        assertEquals(Pair(800f, 700f), symPoints[0])
        // 2. Vertical reflection (1200, 700)
        assertEquals(Pair(1200f, 700f), symPoints[1])
        // 3. Horizontal reflection (800, 1300)
        assertEquals(Pair(800f, 1300f), symPoints[2])
        // 4. Dual reflection (1200, 1300)
        assertEquals(Pair(1200f, 1300f), symPoints[3])
    }

    @Test
    fun testRadialSymmetry_rotatesAcrossNSectors() {
        val sectors = 6
        val symmetryConfig = SymmetryConfig(
            type = SymmetryType.RADIAL,
            centerX = 500f,
            centerY = 500f,
            radialSectors = sectors
        )

        // Point at distance 100 along horizontal: (600, 500)
        val symPoints = DrawingAidsEngine.computeSymmetryPoints(600f, 500f, symmetryConfig)
        assertEquals(sectors, symPoints.size)

        // Verify all rotated points maintain identical radial distance of 100px
        for ((sx, sy) in symPoints) {
            val dist = hypot(sx - 500f, sy - 500f)
            assertEquals(100f, dist, 0.01f)
            assertTrue("Radial distance within 0.5px tolerance", abs(dist - 100f) <= 0.5f)
        }

        // Sector 0: 0° -> (600, 500)
        assertEquals(600f, symPoints[0].first, 0.01f)
        assertEquals(500f, symPoints[0].second, 0.01f)

        // Sector 3: 180° -> (400, 500)
        assertEquals(400f, symPoints[3].first, 0.01f)
        assertEquals(500f, symPoints[3].second, 0.01f)
    }

    @Test
    fun testSymmetricStrokeGeneration_preservesPressureAndTiming() {
        val symmetryConfig = SymmetryConfig(
            type = SymmetryType.MIRROR_VERTICAL,
            centerX = 1000f,
            centerY = 1000f
        )

        val mainStroke = BrushStroke(id = "stroke_test_01")
        mainStroke.points.add(StrokePoint(x = 800f, y = 600f, pressure = 0.45f, timestampNano = 1000000L))
        mainStroke.points.add(StrokePoint(x = 850f, y = 650f, pressure = 0.75f, timestampNano = 2000000L))

        val symStrokes = DrawingAidsEngine.generateSymmetricStrokes(mainStroke, symmetryConfig)
        assertEquals(1, symStrokes.size)

        val sibling = symStrokes[0]
        assertEquals("${mainStroke.id}_sym_1", sibling.id)
        assertEquals(2, sibling.points.size)

        // Point 0 reflected: x' = 2*1000 - 800 = 1200
        assertEquals(1200f, sibling.points[0].x, 0.01f)
        assertEquals(600f, sibling.points[0].y, 0.01f)
        assertEquals(0.45f, sibling.points[0].pressure, 0.001f)
        assertEquals(1000000L, sibling.points[0].timestampNano)

        // Point 1 reflected: x' = 2*1000 - 850 = 1150
        assertEquals(1150f, sibling.points[1].x, 0.01f)
        assertEquals(650f, sibling.points[1].y, 0.01f)
        assertEquals(0.75f, sibling.points[1].pressure, 0.001f)
        assertEquals(2000000L, sibling.points[1].timestampNano)
    }

    @Test
    fun testReferenceImage_positioningAndResizing() {
        val manager = ReferenceImageManager()
        val ref = ReferenceImageOverlay(
            id = "ref_01",
            name = "Anatomy Reference",
            x = 50f,
            y = 50f,
            width = 300f,
            height = 400f,
            opacity = 0.8f
        )
        manager.addReference(ref)

        assertEquals(1, manager.references.size)
        assertEquals(ref, manager.activeReference)

        // Move
        assertTrue(manager.updatePosition("ref_01", 150f, 200f))
        assertEquals(150f, manager.activeReference!!.x, 0.01f)
        assertEquals(200f, manager.activeReference!!.y, 0.01f)

        // Resize
        assertTrue(manager.updateSize("ref_01", 600f, 800f))
        assertEquals(600f, manager.activeReference!!.width, 0.01f)
        assertEquals(800f, manager.activeReference!!.height, 0.01f)

        // Reject invalid negative dimensions
        assertFalse(manager.updateSize("ref_01", -10f, 50f))
    }

    @Test
    fun testReferenceImage_opacityAndVisibilityToggles() {
        val manager = ReferenceImageManager()
        val ref = ReferenceImageOverlay(id = "ref_02", opacity = 0.75f, visible = true)
        manager.addReference(ref)

        // Update opacity with clamping
        manager.updateOpacity("ref_02", 0.4f)
        assertEquals(0.4f, manager.activeReference!!.opacity, 0.01f)

        manager.updateOpacity("ref_02", 1.5f)
        assertEquals(1.0f, manager.activeReference!!.opacity, 0.01f)

        // Toggle visibility
        manager.toggleVisibility("ref_02")
        assertFalse(manager.activeReference!!.visible)

        manager.toggleVisibility("ref_02")
        assertTrue(manager.activeReference!!.visible)
    }

    @Test
    fun testReferenceImage_colorSamplingAtCoordinates() {
        // Create 2x2 test bitmap:
        // (0,0)=Red,   (1,0)=Green
        // (0,1)=Blue,  (1,1)=White
        val pixels = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
            0xFF0000FF.toInt(), 0xFFFFFFFF.toInt()
        )
        val overlay = ReferenceImageOverlay(
            id = "ref_sampling",
            x = 100f,
            y = 100f,
            width = 200f,
            height = 200f,
            pixels = pixels,
            bitmapWidth = 2,
            bitmapHeight = 2
        )

        // Sample top-left quadrant (docX=120, docY=120) -> should be Red
        val colorTL = overlay.sampleColorAtDocumentCoord(120f, 120f)
        assertNotNull(colorTL)
        assertEquals("#FF0000", colorTL!!.hexRgb)

        // Sample top-right quadrant (docX=280, docY=120) -> should be Green
        val colorTR = overlay.sampleColorAtDocumentCoord(280f, 120f)
        assertNotNull(colorTR)
        assertEquals("#00FF00", colorTR!!.hexRgb)

        // Sample bottom-left quadrant (docX=120, docY=280) -> should be Blue
        val colorBL = overlay.sampleColorAtDocumentCoord(120f, 280f)
        assertNotNull(colorBL)
        assertEquals("#0000FF", colorBL!!.hexRgb)

        // Sample outside bounds (docX=50, docY=50) -> should be null
        val colorOutside = overlay.sampleColorAtDocumentCoord(50f, 50f)
        assertNull(colorOutside)
    }

    @Test
    fun testReferenceImage_isolationFromArtworkLayersOnExport() {
        val doc = ArtDocument(canvas = CanvasConfig(width = 1024, height = 1024))
        val manager = ReferenceImageManager()
        val ref = ReferenceImageOverlay(id = "ref_isolated", name = "Secret Sketch")
        manager.addReference(ref)

        // Reference exists in manager
        assertNotNull(manager.activeReference)

        // INVARIANT CHECK: Reference is NOT in document layer tree
        assertFalse("Reference must not be merged into root group children",
            doc.rootGroup.children.any { it.name == "Secret Sketch" }
        )
        assertFalse("Reference must not be in allLayersFlat",
            doc.allLayersFlat().any { it.name == "Secret Sketch" }
        )
        assertFalse("isMergedIntoDocument must be false prior to explicit import",
            manager.isMergedIntoDocument(doc, "ref_isolated")
        )
    }

    @Test
    fun testReferenceImage_explicitImportCreatesPaintLayer() {
        var doc = ArtDocument(canvas = CanvasConfig(width = 1024, height = 1024))
        val initialLayerCount = doc.allLayersFlat().size

        val manager = ReferenceImageManager()
        val ref = ReferenceImageOverlay(
            id = "ref_to_import",
            name = "Model Photo",
            x = 50f,
            y = 80f,
            width = 400f,
            height = 300f,
            opacity = 0.9f
        )
        manager.addReference(ref)

        // Explicit user conversion into a paint layer
        val (updatedDoc, importedLayer) = manager.importReferenceToLayer(doc, "ref_to_import")

        assertEquals(initialLayerCount + 1, updatedDoc.allLayersFlat().size)
        assertNotNull(importedLayer)
        assertEquals("Imported: Model Photo", importedLayer.name)
        assertEquals(0.9f, importedLayer.opacity, 0.01f)
        assertEquals(50f, importedLayer.transform.translationX, 0.01f)
        assertEquals(80f, importedLayer.transform.translationY, 0.01f)

        // Now isMergedIntoDocument returns true
        assertTrue(manager.isMergedIntoDocument(updatedDoc, "ref_to_import"))
    }
}
