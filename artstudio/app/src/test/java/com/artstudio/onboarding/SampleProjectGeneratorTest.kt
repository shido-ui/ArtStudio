package com.artstudio.onboarding

import com.artstudio.adjustment.BrightnessContrastAdjustment
import com.artstudio.autodraw.schedule.*
import com.artstudio.comic.*
import com.artstudio.cv.CvPoint
import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.model.*
import com.artstudio.export.PureKotlinPngEncoder
import com.artstudio.text.TextFontStyle
import com.artstudio.text.TextObject
import com.artstudio.vector.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Generates and validates the 3 official CC0 sample projects for Art Studio onboarding:
 * 1. sample_doodle.ffart (3 layers + adjustment layer + brush strokes)
 * 2. sample_comic_panel.ffart (4 comic panels + speech bubbles + screentones + text)
 * 3. sample_auto_trace.ffart (reference image + CV vector paths + Colour Lab baked layers + drawing schedule)
 *
 * Guarantees each sample is valid, lightweight (< 200 KB), and round-trips through DocumentPackage with zero errors.
 */
class SampleProjectGeneratorTest {

    private fun createSolidPng(width: Int, height: Int, colorArgb: Int): ByteArray {
        val pixels = IntArray(width * height) { colorArgb }
        val baos = ByteArrayOutputStream()
        PureKotlinPngEncoder.encodeRgba(pixels, width, height, baos)
        return baos.toByteArray()
    }

    private fun createDoodlePng(width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height) { 0x00000000 } // Transparent
        // Draw simple ink circle & smile
        val cx = width / 2
        val cy = height / 2
        val r = width / 4
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (kotlin.math.abs(dist - r) < 4f) {
                    pixels[y * width + x] = 0xFF1A1A1A.toInt() // Dark ink
                }
                // Smile
                if (dy > 0 && kotlin.math.abs(dist - (r * 0.6f)) < 3f) {
                    pixels[y * width + x] = 0xFF1A1A1A.toInt()
                }
                // Eyes
                if ((x == cx - r / 3 || x == cx + r / 3) && (y == cy - r / 3)) {
                    for (ey in -4..4) {
                        for (ex in -4..4) {
                            val py = (y + ey).coerceIn(0, height - 1)
                            val px = (x + ex).coerceIn(0, width - 1)
                            pixels[py * width + px] = 0xFF1A1A1A.toInt()
                        }
                    }
                }
            }
        }
        val baos = ByteArrayOutputStream()
        PureKotlinPngEncoder.encodeRgba(pixels, width, height, baos)
        return baos.toByteArray()
    }

    private fun resolveSamplesDir(): File {
        val candidates = listOf(
            File("app/src/main/assets/samples"),
            File("src/main/assets/samples"),
            File("../app/src/main/assets/samples")
        )
        val dir = candidates.firstOrNull { it.parentFile?.exists() == true } ?: File("app/src/main/assets/samples")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Test
    fun testGenerateAndVerifySampleDoodle() {
        val samplesDir = resolveSamplesDir()
        val outFile = File(samplesDir, "sample_doodle.ffart")

        val bgPng = createSolidPng(512, 512, 0xFFFAF8F5.toInt()) // Warm paper
        val midPng = createDoodlePng(512, 512) // Inking
        val fgPng = createSolidPng(512, 512, 0x00000000) // Highlights layer

        val assets = mapOf(
            "layer_bg.png" to bgPng,
            "layer_mid.png" to midPng,
            "layer_fg.png" to fgPng
        )

        val layerBg = LayerNode.RasterLayer(
            id = "layer_doodle_bg",
            name = "Background Paper",
            width = 512,
            height = 512,
            assetPath = "assets/layer_bg.png"
        )
        val layerMid = LayerNode.RasterLayer(
            id = "layer_doodle_mid",
            name = "Mid - Linework",
            width = 512,
            height = 512,
            assetPath = "assets/layer_mid.png"
        )
        val layerFg = LayerNode.RasterLayer(
            id = "layer_doodle_fg",
            name = "Foreground - Highlights",
            width = 512,
            height = 512,
            assetPath = "assets/layer_fg.png"
        )
        val adjustment = LayerNode.AdjustmentLayer(
            id = "adj_doodle_tonal",
            name = "Warm Grade Adjustment",
            adjustment = BrightnessContrastAdjustment(
                id = "bc_adj",
                brightness = 5f,
                contrast = 10f
            )
        )

        val doc = ArtDocument(
            id = "sample_project_doodle",
            title = "Quick Sketch Doodle",
            canvas = CanvasConfig(width = 512, height = 512, dpi = 300),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(layerBg, layerMid, layerFg, adjustment)
            )
        )

        DocumentPackage.save(doc, assets, outFile, thumbnail = bgPng)

        assertTrue(outFile.exists())
        assertTrue("Doodle size ${outFile.length()} exceeds 200 KB", outFile.length() < 200 * 1024)

        // Validate package round trip
        val loaded = DocumentPackage.load(outFile)
        assertEquals("sample_project_doodle", loaded.document.id)
        assertEquals(4, loaded.document.allLayersFlat().size)
        assertTrue(loaded.document.allLayersFlat().any { it is LayerNode.AdjustmentLayer })
    }

    @Test
    fun testGenerateAndVerifySampleComicPanel() {
        val samplesDir = resolveSamplesDir()
        val outFile = File(samplesDir, "sample_comic_panel.ffart")

        val bgPng = createSolidPng(512, 512, 0xFFFFFFFF.toInt())
        val inkPng = createDoodlePng(512, 512)

        val assets = mapOf(
            "layer_comic_bg.png" to bgPng,
            "layer_comic_ink.png" to inkPng
        )

        val panels = listOf(
            ComicPanel.createRectangular(24f, 24f, 220f, 220f, name = "Panel 1"),
            ComicPanel.createRectangular(268f, 24f, 220f, 220f, name = "Panel 2"),
            ComicPanel.createRectangular(24f, 268f, 220f, 220f, name = "Panel 3"),
            ComicPanel.createRectangular(268f, 268f, 220f, 220f, name = "Panel 4")
        )

        val speechBubbles = listOf(
            SpeechBubble(
                id = "bubble_1",
                name = "Greeting",
                x = 40f,
                y = 40f,
                width = 180f,
                height = 80f,
                text = "Welcome to Art Studio!"
            ),
            SpeechBubble(
                id = "bubble_2",
                name = "Callout",
                x = 280f,
                y = 280f,
                width = 180f,
                height = 80f,
                text = "Create manga anywhere!"
            )
        )

        val screentones = listOf(
            ScreentoneConfig(
                name = "Ami-tone 25%",
                pattern = ScreentonePattern.DOT,
                frequency = 12f,
                density = 0.25f,
                angleDeg = 45f
            )
        )

        val textLayer = LayerNode.TextLayer(
            id = "layer_text_caption",
            name = "Panel Captions",
            textObject = TextObject(
                id = "caption_obj",
                text = "ACT 1: THE STUDIO",
                x = 30f,
                y = 30f,
                width = 180f,
                height = 24f,
                fontFamily = "sans-serif",
                fontSize = 14f,
                fontStyle = TextFontStyle.BOLD
            )
        )

        val doc = ArtDocument(
            id = "sample_project_comic",
            title = "4-Panel Comic Strip",
            canvas = CanvasConfig(width = 512, height = 512, dpi = 300),
            comicPanels = panels,
            speechBubbles = speechBubbles,
            screentones = screentones,
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "comic_bg",
                        name = "White Page",
                        width = 512,
                        height = 512,
                        assetPath = "assets/layer_comic_bg.png"
                    ),
                    LayerNode.RasterLayer(
                        id = "comic_ink",
                        name = "Panel Inks",
                        width = 512,
                        height = 512,
                        assetPath = "assets/layer_comic_ink.png"
                    ),
                    textLayer
                )
            )
        )

        DocumentPackage.save(doc, assets, outFile, thumbnail = bgPng)

        assertTrue(outFile.exists())
        assertTrue("Comic size ${outFile.length()} exceeds 200 KB", outFile.length() < 200 * 1024)

        val loaded = DocumentPackage.load(outFile)
        assertEquals("sample_project_comic", loaded.document.id)
        assertEquals(4, loaded.document.comicPanels.size)
        assertEquals(2, loaded.document.speechBubbles.size)
        assertEquals(1, loaded.document.screentones.size)
    }

    @Test
    fun testGenerateAndVerifySampleAutoTrace() {
        val samplesDir = resolveSamplesDir()
        val outFile = File(samplesDir, "sample_auto_trace.ffart")

        val refPng = createDoodlePng(512, 512)
        val bakedBasePng = createSolidPng(512, 512, 0xFFE0E0E0.toInt())
        val bakedShadePng = createSolidPng(512, 512, 0x55000000)

        val assets = mapOf(
            "reference_sketch.png" to refPng,
            "layer_baked_base.png" to bakedBasePng,
            "layer_baked_shade.png" to bakedShadePng
        )

        val refImages = listOf(
            ReferenceImageConfig(
                id = "ref_sketch_config",
                assetPath = "assets/reference_sketch.png",
                x = 0f,
                y = 0f,
                width = 512f,
                height = 512f,
                opacity = 0.5f,
                visible = true
            )
        )

        val vectorObj = VectorObject(
            id = "vec_trace_1",
            name = "Traced Contour",
            shapeType = VectorShapeType.BEZIER_PATH,
            segments = listOf(
                VectorSegment.MoveTo(128f, 128f),
                VectorSegment.LineTo(384f, 128f),
                VectorSegment.LineTo(384f, 384f),
                VectorSegment.LineTo(128f, 384f),
                VectorSegment.Close
            ),
            isClosed = true,
            style = VectorStyle(strokeColor = 0xFF000000L, strokeWidth = 3f)
        )

        val vectorLayer = LayerNode.VectorPathLayer(
            id = "layer_vector_traced",
            name = "CV Traced Vector Linework",
            vectorObjects = listOf(vectorObj)
        )

        val bakedGroup = LayerNode.LayerGroup(
            id = "group_baked_colour_lab",
            name = "Colour Lab - Classic Cel",
            children = listOf(
                LayerNode.RasterLayer(
                    id = "layer_baked_base",
                    name = "Cel Base Tone",
                    width = 512,
                    height = 512,
                    assetPath = "assets/layer_baked_base.png"
                ),
                LayerNode.RasterLayer(
                    id = "layer_baked_shade",
                    name = "Cel Shadows",
                    width = 512,
                    height = 512,
                    blendMode = BlendModeType.Multiply,
                    assetPath = "assets/layer_baked_shade.png"
                )
            )
        )

        val scheduleEntries = listOf(
            DrawingScheduleEntry(
                pathId = "vec_trace_1",
                layerId = "layer_vector_traced",
                sequenceIndex = 0,
                direction = StrokeDirection.FORWARD,
                timing = ScheduleTiming(estimatedDurationMs = 1200L, startTimestampMs = 0L),
                startPoint = CvPoint(128f, 128f),
                endPoint = CvPoint(128f, 384f),
                pathLengthPx = 1024f,
                isContour = true
            )
        )

        val schedule = DrawingSchedule(
            id = "schedule_sample_trace",
            entries = scheduleEntries,
            config = DrawingScheduleConfig(),
            totalDurationMs = 1200L,
            totalPenTravelPx = 1024f,
            totalDrawingLengthPx = 1024f,
            scheduleHash = DrawingSchedule.computeScheduleHash(scheduleEntries)
        )

        val doc = ArtDocument(
            id = "sample_project_trace",
            title = "CV AutoDraw Reference & Trace",
            canvas = CanvasConfig(width = 512, height = 512, dpi = 300),
            referenceImages = refImages,
            drawingSchedule = schedule,
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(bakedGroup, vectorLayer)
            )
        )

        DocumentPackage.save(doc, assets, outFile, thumbnail = refPng)

        assertTrue(outFile.exists())
        assertTrue("Trace size ${outFile.length()} exceeds 200 KB", outFile.length() < 200 * 1024)

        val loaded = DocumentPackage.load(outFile)
        assertEquals("sample_project_trace", loaded.document.id)
        assertEquals(1, loaded.document.referenceImages.size)
        assertNotNull(loaded.document.drawingSchedule)
        assertEquals(1, loaded.document.drawingSchedule?.entries?.size)
    }
}
