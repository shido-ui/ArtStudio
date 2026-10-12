package com.artstudio.comic

import com.artstudio.document.io.ArtJson
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.history.*
import com.artstudio.vector.VectorObject
import com.artstudio.vector.VectorShapeType
import com.artstudio.vector.svg.SafeSvgExporter
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/**
 * Verification test suite for Comic & Manga Tools (Prompt 30).
 *
 * Covers:
 * 1. Panel Geometry (rectangles, gutters, horizontal/vertical/slanted slicing, presets)
 * 2. Speech & Thought Bubble Paths (welded tails, thought clouds, shout bursts, text safe bounds)
 * 3. Scalable Procedural Screentones (halftone dots, line hatching, cross-hatching, scale invariance)
 * 4. Native Document Persistence Roundtrip & Reversible Commands
 * 5. SVG Export Subset for Comic Elements
 */
class ComicToolsTest {

    // =========================================================================
    // 1. Comic Panel Geometry & Slicing
    // =========================================================================

    @Test
    fun testPanelGeometry_creationAndBoundaryCalculations() {
        val panel = ComicPanel.createRectangular(
            x = 100f,
            y = 150f,
            width = 400f,
            height = 300f,
            name = "Test Panel",
            strokeWidth = 5f
        )

        assertEquals("Test Panel", panel.name)
        assertTrue("Panel must be rectangular", panel.isRectangular())
        val b = panel.bounds()
        assertEquals(100f, b.left, 0.001f)
        assertEquals(150f, b.top, 0.001f)
        assertEquals(500f, b.right, 0.001f)
        assertEquals(450f, b.bottom, 0.001f)
        assertEquals(400f, panel.width, 0.001f)
        assertEquals(300f, panel.height, 0.001f)
    }

    @Test
    fun testPanelGeometry_horizontalGutterSplit() {
        val original = ComicPanel.createRectangular(
            x = 50f,
            y = 50f,
            width = 500f,
            height = 400f,
            name = "Original"
        )
        val gutter = 20f
        val splitRatio = 0.5f

        val (topPanel, bottomPanel) = ComicGeometryEngine.splitPanelHorizontal(original, splitRatio, gutter)

        // Heights must sum to original.height - gutter
        assertEquals(190f, topPanel.height, 0.001f)
        assertEquals(190f, bottomPanel.height, 0.001f)
        assertEquals(original.height - gutter, topPanel.height + bottomPanel.height, 0.001f)

        // Vertical separation between top and bottom panels must exactly equal gutter
        val gap = bottomPanel.bounds().top - topPanel.bounds().bottom
        assertEquals(gutter, gap, 0.001f)

        // Widths must remain unchanged
        assertEquals(original.width, topPanel.width, 0.001f)
        assertEquals(original.width, bottomPanel.width, 0.001f)
    }

    @Test
    fun testPanelGeometry_verticalGutterSplit() {
        val original = ComicPanel.createRectangular(
            x = 100f,
            y = 100f,
            width = 600f,
            height = 300f,
            name = "Row Panel"
        )
        val gutter = 16f
        val splitRatio = 0.4f // 40% left, 60% right

        val (leftPanel, rightPanel) = ComicGeometryEngine.splitPanelVertical(original, splitRatio, gutter)

        // Widths must sum to original.width - gutter
        assertEquals(original.width - gutter, leftPanel.width + rightPanel.width, 0.001f)

        // Horizontal separation must equal gutter
        val gap = rightPanel.bounds().left - leftPanel.bounds().right
        assertEquals(gutter, gap, 0.001f)

        // Heights must remain unchanged
        assertEquals(original.height, leftPanel.height, 0.001f)
        assertEquals(original.height, rightPanel.height, 0.001f)
    }

    @Test
    fun testPanelGeometry_slantedActionSplit() {
        val original = ComicPanel.createRectangular(
            x = 50f,
            y = 50f,
            width = 400f,
            height = 400f,
            name = "Action Frame"
        )
        // Slanted cut from (200, 50) to (300, 450)
        val gutter = 16f
        val (leftAction, rightAction) = ComicGeometryEngine.splitPanelSlanted(
            panel = original,
            topCutX = 200f,
            bottomCutX = 300f,
            gutter = gutter
        )

        // Both child panels must have 4 polygonal vertices
        assertEquals(4, leftAction.vertices.size)
        assertEquals(4, rightAction.vertices.size)

        // Slanted split is polygonal (not standard axis-aligned rectangle)
        assertFalse("Slanted cut child is a non-rectangular polygon", leftAction.isRectangular())
        assertFalse("Slanted cut child is a non-rectangular polygon", rightAction.isRectangular())

        // Top edge gap between left and right must be >= gutter
        val topGap = rightAction.vertices[0].x - leftAction.vertices[1].x
        assertTrue("Top gap must be at least gutter ($topGap >= $gutter)", topGap >= gutter - 0.01f)
    }

    @Test
    fun testPanelGeometry_pointInPolygonContainment() {
        val panel = ComicPanel.createRectangular(100f, 100f, 200f, 200f)

        assertTrue("Center point must be inside", panel.containsPoint(200f, 200f))
        assertTrue("Inner point must be inside", panel.containsPoint(110f, 110f))
        assertFalse("Point to left must be outside", panel.containsPoint(50f, 200f))
        assertFalse("Point to right must be outside", panel.containsPoint(350f, 200f))
        assertFalse("Point above must be outside", panel.containsPoint(200f, 50f))
        assertFalse("Point below must be outside", panel.containsPoint(200f, 350f))
    }

    @Test
    fun testPanelGeometry_presets_4komaAndGrid() {
        val fourKoma = ComicGeometryEngine.create4KomaLayout(pageW = 1000f, pageH = 2000f, margin = 50f, gutter = 20f)
        assertEquals(4, fourKoma.size)
        for (p in fourKoma) {
            assertEquals(900f, p.width, 0.001f) // 1000 - 2 * 50
        }

        val layout = ComicPanelLayout(pageWidth = 1000f, pageHeight = 1000f, marginOuter = 50f, gutterHorizontal = 20f, gutterVertical = 20f)
        val grid2x2 = ComicGeometryEngine.createGridPanels(layout, rows = 2, cols = 2)
        assertEquals(4, grid2x2.size)
        for (p in grid2x2) {
            assertEquals(440f, p.width, 0.001f) // (1000 - 100 - 20) / 2 = 440
            assertEquals(440f, p.height, 0.001f)
        }
    }

    // =========================================================================
    // 2. Speech & Thought Bubble Paths & Safe Text Placement
    // =========================================================================

    @Test
    fun testSpeechBubble_ovalPathWeldedTail() {
        val bubble = SpeechBubble(
            bubbleType = SpeechBubbleType.SPEECH_OVAL,
            x = 100f,
            y = 100f,
            width = 200f,
            height = 140f,
            tailType = BubbleTailType.STRAIGHT_POINTER,
            tailTipX = 80f,
            tailTipY = 300f
        )

        val vecObj = ComicGeometryEngine.createBubbleVectorObject(bubble)
        assertEquals(VectorShapeType.BEZIER_PATH, vecObj.shapeType)
        assertTrue("Bubble path must be closed", vecObj.isClosed)

        // Must contain tail tip in its anchors
        val hasTip = vecObj.anchors.any { abs(it.x - 80f) < 0.01f && abs(it.y - 300f) < 0.01f }
        assertTrue("Bubble anchors must include the pointer tail tip", hasTip)
    }

    @Test
    fun testSpeechBubble_thoughtCloudScallopArcsAndDots() {
        val bubble = SpeechBubble(
            bubbleType = SpeechBubbleType.THOUGHT_CLOUD,
            x = 200f,
            y = 200f,
            width = 240f,
            height = 160f,
            tailType = BubbleTailType.THOUGHT_DOTS,
            tailTipX = 150f,
            tailTipY = 450f,
            cloudPuffCount = 10
        )

        val cloudVec = ComicGeometryEngine.createBubbleVectorObject(bubble)
        assertEquals(10, cloudVec.anchors.size) // 10 scalloped arcs
        assertTrue("Thought cloud anchors must have Bezier handles for convex puffs", cloudVec.anchors.any { it.hasHandleOut })

        // Thought dots generator
        val dots = ComicGeometryEngine.generateThoughtDots(bubble)
        assertEquals("Must generate 3 trailing auxiliary thought dots", 3, dots.size)
        assertTrue("Thought dots must be ellipses", dots.all { it.shapeType == VectorShapeType.ELLIPSE })

        // Dots must decrease in radius as they approach speaker
        val r1 = dots[0].ellipseData!!.rx
        val r2 = dots[1].ellipseData!!.rx
        val r3 = dots[2].ellipseData!!.rx
        assertTrue("Thought dots radii must diminish towards speaker", r1 > r2 && r2 > r3)
    }

    @Test
    fun testSpeechBubble_shoutBurstJaggedSpikes() {
        val bubble = SpeechBubble(
            bubbleType = SpeechBubbleType.SHOUT_BURST,
            x = 100f,
            y = 100f,
            width = 200f,
            height = 200f,
            burstSpikeCount = 12,
            burstSpikeDepth = 0.30f
        )

        val burstVec = ComicGeometryEngine.createBubbleVectorObject(bubble)
        // 12 spikes * 2 (alternating peak and valley) = 24 vertices
        assertEquals(24, burstVec.anchors.size)
        assertTrue(burstVec.isClosed)

        // Distances from center must alternate between outer peaks and inner valleys
        val cx = bubble.centerX
        val cy = bubble.centerY
        val d0 = hypot(burstVec.anchors[0].x - cx, burstVec.anchors[0].y - cy)
        val d1 = hypot(burstVec.anchors[1].x - cx, burstVec.anchors[1].y - cy)
        assertTrue("Outer peak radius must be strictly greater than inner valley", d0 > d1)
    }

    @Test
    fun testSpeechBubble_safeTextBoundsCalculation() {
        val ovalBubble = SpeechBubble(
            bubbleType = SpeechBubbleType.SPEECH_OVAL,
            x = 100f,
            y = 100f,
            width = 200f,
            height = 100f // rx = 100, ry = 50
        )

        val safeRect = ovalBubble.computeInnerSafeTextBounds(padding = 10f)
        // Max inscribed half-width is 100 * 0.7071 - 10 = ~60.71
        assertTrue("Safe rect must be strictly inside bubble bounds",
            safeRect.left >= ovalBubble.x && safeRect.right <= ovalBubble.x + ovalBubble.width &&
            safeRect.top >= ovalBubble.y && safeRect.bottom <= ovalBubble.y + ovalBubble.height
        )

        val textObj = ComicGeometryEngine.generateBubbleTextObject(ovalBubble.copy(text = "Comic Dialogue"))
        assertEquals("Comic Dialogue", textObj.text)
        assertEquals(safeRect.centerX, textObj.x, 0.01f)
        assertEquals(safeRect.centerY, textObj.y, 0.01f)
    }

    // =========================================================================
    // 3. Scalable Procedural Screentones
    // =========================================================================

    @Test
    fun testScreentone_proceduralCoverageAndDensity() {
        val tone25 = ScreentoneConfig(
            pattern = ScreentonePattern.DOT,
            frequency = 20f,
            density = 0.25f, // 25% tone
            angleDeg = 0f
        )

        // Target dot radius = pitch * sqrt(density / pi)
        val expectedRadius = 20f * sqrt(0.25f / Math.PI.toFloat())
        assertEquals(expectedRadius, tone25.dotRadius, 0.001f)

        // Evaluate at cell center (10, 10) -> must have 100% coverage
        val centerCov = ScreentoneEngine.evaluateCoverage(10f, 10f, tone25)
        assertEquals(1.0f, centerCov, 0.001f)

        // Evaluate far from dot center (e.g. at cell corner 0, 0) -> must have 0% coverage
        val cornerCov = ScreentoneEngine.evaluateCoverage(0f, 0f, tone25)
        assertEquals(0.0f, cornerCov, 0.001f)
    }

    @Test
    fun testScreentone_lineAndCrossHatchEvaluation() {
        val lineTone = ScreentoneConfig(
            pattern = ScreentonePattern.LINE,
            frequency = 10f,
            density = 0.30f, // 30% line width = 3 px
            angleDeg = 0f
        )
        assertEquals(3.0f, lineTone.lineWidth, 0.001f)

        // Line center is at y = 5 -> full coverage
        assertEquals(1.0f, ScreentoneEngine.evaluateCoverage(5f, 5f, lineTone), 0.01f)
        // Outside line (y = 0) -> 0 coverage
        assertEquals(0.0f, ScreentoneEngine.evaluateCoverage(5f, 0f, lineTone), 0.01f)

        val crossTone = ScreentoneConfig(
            pattern = ScreentonePattern.CROSS_HATCH,
            frequency = 10f,
            density = 0.50f,
            angleDeg = 0f
        )
        // Center intersection (5, 5) -> full coverage
        assertEquals(1.0f, ScreentoneEngine.evaluateCoverage(5f, 5f, crossTone), 0.01f)
    }

    @Test
    fun testScreentone_scaleInvarianceWithoutMemorySpike() {
        val base = ScreentoneConfig(
            frequency = 16f,
            density = 0.35f,
            angleDeg = 45f
        )

        val scaled2x = base.scaled(2.0f)
        assertEquals(32f, scaled2x.frequency, 0.001f)
        assertEquals(base.density, scaled2x.density, 0.001f)
        assertEquals(base.angleDeg, scaled2x.angleDeg, 0.001f)

        // Coverage density ratio remains identical regardless of scaling
        val baseR = base.dotRadius / base.frequency
        val scaledR = scaled2x.dotRadius / scaled2x.frequency
        assertEquals("Relative dot radius ratio must be scale-invariant", baseR, scaledR, 0.0001f)
    }

    // =========================================================================
    // 4. Native Document Persistence Roundtrip & Reversible Commands
    // =========================================================================

    @Test
    fun testNativeDocumentPersistence_comicElementsRoundTrip() {
        val panel = ComicPanel.createRectangular(50f, 50f, 400f, 300f, name = "Hero Panel", strokeWidth = 5f)
        val bubble = SpeechBubble(
            bubbleType = SpeechBubbleType.SPEECH_OVAL,
            x = 80f,
            y = 80f,
            width = 220f,
            height = 140f,
            text = "Nani?!",
            fontSize = 28f
        )
        val screentone = ScreentoneConfig(
            name = "Manga Shade 30%",
            pattern = ScreentonePattern.DOT,
            frequency = 14f,
            density = 0.30f
        )
        val guides = ComicPageGuides(enabled = true, trimMargin = 50f, safeAreaMargin = 140f)

        val doc = ArtDocument(
            canvas = CanvasConfig(1024, 1024),
            comicPanels = listOf(panel),
            speechBubbles = listOf(bubble),
            screentones = listOf(screentone),
            comicPageGuides = guides
        )

        // Serialize to JSON string
        val json = DocumentSerializer.toJsonString(doc)
        assertTrue("JSON must contain comicPanels", json.contains("comicPanels"))
        assertTrue("JSON must contain speechBubbles", json.contains("speechBubbles"))
        assertTrue("JSON must contain screentones", json.contains("screentones"))
        assertTrue("JSON must contain comicPageGuides", json.contains("comicPageGuides"))

        // Deserialize back
        val restoredDoc = DocumentSerializer.fromJsonString(json)

        // Verify comic elements survived native round-trip
        assertEquals(1, restoredDoc.comicPanels.size)
        val restoredPanel = restoredDoc.comicPanels[0]
        assertEquals("Hero Panel", restoredPanel.name)
        assertEquals(400f, restoredPanel.width, 0.001f)
        assertEquals(5f, restoredPanel.strokeWidth, 0.001f)

        assertEquals(1, restoredDoc.speechBubbles.size)
        val restoredBubble = restoredDoc.speechBubbles[0]
        assertEquals("Nani?!", restoredBubble.text)
        assertEquals(28f, restoredBubble.fontSize, 0.001f)
        assertEquals(SpeechBubbleType.SPEECH_OVAL, restoredBubble.bubbleType)

        assertEquals(1, restoredDoc.screentones.size)
        val restoredTone = restoredDoc.screentones[0]
        assertEquals(ScreentonePattern.DOT, restoredTone.pattern)
        assertEquals(14f, restoredTone.frequency, 0.001f)
        assertEquals(0.30f, restoredTone.density, 0.001f)

        assertNotNull(restoredDoc.comicPageGuides)
        assertEquals(50f, restoredDoc.comicPageGuides!!.trimMargin, 0.001f)
        assertEquals(140f, restoredDoc.comicPageGuides!!.safeAreaMargin, 0.001f)
    }

    @Test
    fun testComicCommands_undoRedoTransactionReversibility() {
        val initialDoc = ArtDocument(canvas = CanvasConfig(1000, 1000))
        val panel = ComicPanel.createRectangular(100f, 100f, 400f, 300f, name = "Command Panel")

        val addPanelCmd = AddComicPanelCommand(panel = panel, createVectorLayer = true)
        val docWithPanel = addPanelCmd.apply(initialDoc)

        assertEquals(1, docWithPanel.comicPanels.size)
        assertTrue("Vector layer must be created for panel border",
            docWithPanel.rootGroup.children.any { it.name == "Command Panel" }
        )

        // Revert add panel
        val revertedDoc = addPanelCmd.revert(docWithPanel)
        assertEquals(0, revertedDoc.comicPanels.size)
        assertFalse("Vector layer must be removed on revert",
            revertedDoc.rootGroup.children.any { it.name == "Command Panel" }
        )

        // Add speech bubble command test
        val bubble = SpeechBubble(
            x = 150f, y = 150f, width = 200f, height = 120f,
            text = "Reversible Bubble"
        )
        val addBubbleCmd = AddSpeechBubbleCommand(bubble = bubble, createVectorAndTextLayers = true)
        val docWithBubble = addBubbleCmd.apply(docWithPanel)
        assertEquals(1, docWithBubble.speechBubbles.size)

        val revertedBubbleDoc = addBubbleCmd.revert(docWithBubble)
        assertEquals(0, revertedBubbleDoc.speechBubbles.size)
    }

    // =========================================================================
    // 5. SVG Export Subset for Comic Elements
    // =========================================================================

    @Test
    fun testSvgExport_comicPanelsAndBubblesInterchange() {
        val panel = ComicPanel.createRectangular(50f, 50f, 300f, 200f, name = "SVG Panel")
        val bubble = SpeechBubble(
            x = 100f, y = 100f, width = 180f, height = 120f,
            text = "SVG Manga Bubble"
        )
        val screentone = ScreentoneConfig(
            pattern = ScreentonePattern.DOT,
            frequency = 12f,
            density = 0.20f
        )

        val doc = ArtDocument(
            canvas = CanvasConfig(500, 500),
            comicPanels = listOf(panel),
            speechBubbles = listOf(bubble),
            screentones = listOf(screentone)
        )

        val (svgContent, report) = SafeSvgExporter.exportDocument(doc)

        // SVG must contain pattern definitions for screentone
        assertTrue("SVG must contain <defs> for screentone pattern", svgContent.contains("<defs>"))
        assertTrue("SVG must contain <pattern", svgContent.contains("<pattern"))

        // SVG must contain panel polygon/rect and bubble path
        assertTrue("SVG must contain <polygon or <path", svgContent.contains("<polygon") || svgContent.contains("<path"))

        // SVG must contain dialogue text
        assertTrue("SVG must contain text", svgContent.contains("<text"))
        assertTrue("SVG must contain bubble text content", svgContent.contains("SVG Manga Bubble"))
        assertEquals("SVG", report.targetFormat)
    }
}
