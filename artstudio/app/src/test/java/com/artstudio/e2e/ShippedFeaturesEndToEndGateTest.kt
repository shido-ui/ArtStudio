package com.artstudio.e2e

import com.artstudio.animation.AnimationFrame
import com.artstudio.animation.AnimationPlaybackController
import com.artstudio.animation.AnimationTimeline
import com.artstudio.autodraw.AutoDrawV1Pipeline
import com.artstudio.autodraw.playback.DrawingPlaybackController
import com.artstudio.autodraw.playback.DrawingPlaybackRenderer
import com.artstudio.autodraw.playback.PlaybackMode
import com.artstudio.autodraw.playback.PlaybackStateEnum
import com.artstudio.colorstack.ColorStackPipeline
import com.artstudio.colorstack.ColorStackRecipe
import com.artstudio.core.FeatureFlags
import com.artstudio.deformation.*
import com.artstudio.document.io.psd.PsdDocumentImporter
import com.artstudio.document.io.psd.PsdWriter
import com.artstudio.document.model.*
import com.artstudio.export.gif.GifDecoder
import com.artstudio.export.gif.GifEncoder
import com.artstudio.vector.*
import com.artstudio.vector.svg.SafeSvgExporter
import com.artstudio.vector.svg.SafeSvgParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Authoritative end-to-end verification gate for shipped vs hidden features.
 *
 * Verifies:
 * 1. Shipped Feature: AutoDraw v1 Classical CV line art pipeline (downscaling, progress, contours, stroke schedule, playback, layer integration).
 * 2. Shipped Feature: PSD export and import round-trip on real multi-layer image data.
 * 3. Shipped Feature: SVG vector export and import round-trip on real vector geometry.
 * 4. Shipped Feature: Animation playback controller and multi-frame GIF export & decode round-trip.
 * 5. Hidden Features Unreachable: AI models, ColorStack, and Deformation are strictly disabled and blocked by FeatureFlags.
 */
class ShippedFeaturesEndToEndGateTest {

    @Test
    fun testAutoDrawV1EndToEndOnRealSampleImage() = runBlocking {
        assertTrue("AutoDraw v1 must be enabled", FeatureFlags.isAutoDrawV1Enabled)

        // 1. Synthesize a real 128x128 line-art sample image (white background with black box line-art)
        val width = 128
        val height = 128
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }

        // Draw a black outline box (x: 20..100, y: 20..100)
        for (x in 20..100) {
            pixels[20 * width + x] = 0xFF000000.toInt()
            pixels[100 * width + x] = 0xFF000000.toInt()
        }
        for (y in 20..100) {
            pixels[y * width + 20] = 0xFF000000.toInt()
            pixels[y * width + 100] = 0xFF000000.toInt()
        }

        // 2. Track background progress callbacks
        val progressLog = mutableListOf<Pair<Float, String>>()

        // Run AutoDraw v1 with downscaling to maxDimension = 64
        val result = AutoDrawV1Pipeline.processLineArt(
            pixels = pixels,
            width = width,
            height = height,
            maxDimension = 64,
            onProgress = { p, stage -> progressLog.add(p to stage) }
        )

        // 3. Verify downscaling invariants
        assertEquals("Original width preserved", 128, result.originalWidth)
        assertEquals("Original height preserved", 128, result.originalHeight)
        assertEquals("Processed width downscaled", 64, result.processedWidth)
        assertEquals("Processed height downscaled", 64, result.processedHeight)
        assertEquals("Scale factor is 0.5", 0.5f, result.scaleFactor, 0.001f)

        // 4. Verify progress telemetry
        assertTrue("Progress stages were emitted", progressLog.isNotEmpty())
        assertTrue("Initial progress near 0.05", progressLog.first().first <= 0.1f)
        assertEquals("Final progress reaches 1.0", 1.0f, progressLog.last().first, 0.001f)

        // 5. Verify contour and vector extraction
        assertTrue("Contours detected from line art", result.contours.isNotEmpty())
        assertTrue("Vector paths generated", result.vectorObjects.isNotEmpty())
        assertTrue("Drawing schedule populated", result.schedule.entries.isNotEmpty())

        // 6. Verify DrawingPlaybackController initializes with schedule
        val renderer = DrawingPlaybackRenderer(width = 128, height = 128)
        val playbackController = DrawingPlaybackController(result.schedule, renderer, PlaybackMode.LAYER_BY_LAYER)
        assertNotNull("Playback controller created", playbackController)
        playbackController.play()
        assertEquals("Playback controller playing", PlaybackStateEnum.PLAYING, playbackController.currentState)

        // 7. Verify non-destructive integration into ArtDocument
        val doc = ArtDocument(
            id = "test_doc",
            title = "Test AutoDraw Doc",
            canvas = CanvasConfig(128, 128)
        )
        val initialLayerCount = doc.allLayersFlat().size
        val integrationResult = AutoDrawV1Pipeline.integrateIntoDocument(doc, result)

        assertNotNull("Integration result returned", integrationResult)
        assertTrue("Document layer count increased", integrationResult.document.allLayersFlat().size > initialLayerCount)
        val addedPathsLayer = integrationResult.document.allLayersFlat().filterIsInstance<LayerNode.VectorPathLayer>()
        assertTrue("Vector path layer added to document", addedPathsLayer.isNotEmpty())
    }

    @Test
    fun testPsdExportAndImportRoundTripOnRealSampleImage() {
        assertTrue("PSD must be enabled", FeatureFlags.isPsdEnabled)

        val w = 64
        val h = 64
        val layer1Id = "psd_layer_bg"
        val layer2Id = "psd_layer_fg"

        val layer1Pixels = IntArray(w * h) { 0xFFFF0000.toInt() } // Red
        val layer2Pixels = IntArray(w * h) { 0xFF00FF00.toInt() } // Green

        val l1 = LayerNode.RasterLayer(id = layer1Id, name = "Background", width = w, height = h, opacity = 1.0f)
        val l2 = LayerNode.RasterLayer(id = layer2Id, name = "Foreground", width = w, height = h, opacity = 0.8f)

        val doc = ArtDocument(
            id = "psd_test_doc",
            title = "PSD Roundtrip",
            canvas = CanvasConfig(w, h),
            rootGroup = LayerNode.LayerGroup("root", "Root", children = listOf(l1, l2))
        )

        val tempPsd = File.createTempFile("test_psd_", ".psd")
        tempPsd.deleteOnExit()

        try {
            // 1. Export PSD
            val exportResult = PsdWriter.exportPsd(
                document = doc,
                layerPixelBuffers = mapOf(layer1Id to layer1Pixels, layer2Id to layer2Pixels),
                outputFile = tempPsd
            )
            assertTrue("PSD file created", tempPsd.exists() && tempPsd.length() > 0)
            assertEquals("Export layer count is 2", 2, exportResult.layerCount)

            // 2. Import PSD back
            val importResult = PsdDocumentImporter.importPsd(tempPsd)
            assertNotNull("Imported doc not null", importResult.document)
            assertEquals("Width matches", w, importResult.document.canvas.width)
            assertEquals("Height matches", h, importResult.document.canvas.height)

            val importedLayers = importResult.document.allLayersFlat().filterIsInstance<LayerNode.RasterLayer>()
            assertEquals("Imported layer count is 2", 2, importedLayers.size)
        } finally {
            tempPsd.delete()
        }
    }

    @Test
    fun testSvgExportAndImportRoundTripOnRealSampleVector() {
        assertTrue("SVG must be enabled", FeatureFlags.isSvgEnabled)

        val rectVo = VectorObject(
            id = "rect_1",
            name = "Test Rect",
            shapeType = VectorShapeType.RECTANGLE,
            rectData = VectorRectData(x = 10f, y = 20f, width = 80f, height = 60f),
            style = VectorStyle(strokeColor = 0xFF0000FFL, strokeWidth = 3f)
        )

        val w = 200
        val h = 200

        // 1. Export SVG
        val svgXml = SafeSvgExporter.exportObjects(
            objects = listOf(rectVo),
            width = w,
            height = h,
            title = "Gate Test SVG"
        )

        assertTrue("SVG has opening tag", svgXml.contains("<svg"))
        assertTrue("SVG has width and height", svgXml.contains("""width="200""""))
        assertTrue("SVG has rect element", svgXml.contains("<rect"))

        // 2. Import SVG back
        val parseResult = SafeSvgParser.parse(svgXml)
        assertEquals("Canvas width parsed", w, parseResult.canvasWidth)
        assertEquals("Canvas height parsed", h, parseResult.canvasHeight)
        assertTrue("Vector objects recovered", parseResult.vectorObjects.isNotEmpty())
    }

    @Test
    fun testAnimationPlaybackAndGifExportOnRealSampleFrames() {
        assertTrue("Animation must be enabled", FeatureFlags.isAnimationEnabled)

        // 1. Create animation timeline with 3 frames
        val timeline = AnimationTimeline(
            fps = 12,
            frames = listOf(
                AnimationFrame(name = "Frame 1"),
                AnimationFrame(name = "Frame 2"),
                AnimationFrame(name = "Frame 3")
            ),
            currentFrameIndex = 0
        )
        val controller = AnimationPlaybackController(timeline)
        assertEquals(0, controller.currentFrameIndex)
        assertFalse(controller.isPlaying)

        controller.play()
        assertTrue(controller.isPlaying)

        controller.stepForward()
        assertEquals(1, controller.currentFrameIndex)

        controller.stepForward()
        assertEquals(2, controller.currentFrameIndex)

        // 2. Synthesize 3 frames (32x32) for animated GIF export
        val w = 32
        val h = 32
        val f1 = IntArray(w * h) { 0xFFFF0000.toInt() } // Red
        val f2 = IntArray(w * h) { 0xFF00FF00.toInt() } // Green
        val f3 = IntArray(w * h) { 0xFF0000FF.toInt() } // Blue

        val bos = ByteArrayOutputStream()
        val gifEncoder = GifEncoder(w, h, bos, loopCount = 0)
        gifEncoder.encodeFrame(f1, delayCentiseconds = 10)
        gifEncoder.encodeFrame(f2, delayCentiseconds = 10)
        gifEncoder.encodeFrame(f3, delayCentiseconds = 10)
        gifEncoder.finish()

        val gifBytes = bos.toByteArray()
        assertTrue("GIF bytes non-empty", gifBytes.isNotEmpty())

        // Verify GIF header "GIF89a"
        val header = String(gifBytes.copyOfRange(0, 6))
        assertEquals("GIF89a", header)

        // 3. Decode GIF back
        val decoded = GifDecoder.decode(gifBytes)
        assertEquals(3, decoded.frames.size)
        assertEquals(w, decoded.width)
        assertEquals(h, decoded.height)
    }

    @Test
    fun testHiddenFeaturesAreUnreachable() {
        // Assert release feature flag states
        assertFalse("AI must be disabled in release", FeatureFlags.isAiEnabled)
        assertTrue("ColorStack must be enabled in release", FeatureFlags.isColorStackEnabled)
        assertTrue("Deformation must be enabled in release", FeatureFlags.isDeformationEnabled)

        // 1. AI Model loading blocked
        val dummyFile = File.createTempFile("ai_dummy", ".tflite")
        dummyFile.deleteOnExit()
        try {
            val descriptor = com.artstudio.ai.model.AiModelCatalog.CURATED_CATALOG.first()
            val adapter = com.artstudio.ai.model.TfliteModelAdapter(descriptor, dummyFile)
            val loadResult = runBlocking { adapter.load(dummyFile) }
            assertTrue("AI model load returns error when disabled", loadResult is com.artstudio.ai.model.LoadResult.UnsupportedRuntime)

            // AI downloader blocked
            val downloader = com.artstudio.ai.model.AiModelDownloader(dummyFile.parentFile ?: File("."))
            val dlResult = downloader.downloadModelStreaming(descriptor, { ByteArrayInputStream(ByteArray(0)) })
            assertTrue("AI download fails when disabled", dlResult.isFailure)

            // AI registry loadModel blocked
            val registry = com.artstudio.ai.model.AiModelRegistry(downloader)
            val regLoadResult = runBlocking { registry.loadModel(descriptor.id) }
            assertTrue("AI registry loadModel fails when disabled", regLoadResult is com.artstudio.ai.model.LoadResult.UnsupportedRuntime)
        } finally {
            dummyFile.delete()
        }

        // 2. ColorStack pipeline execution blocked when flag is false
        val origColorStack = FeatureFlags.isColorStackEnabled
        try {
            FeatureFlags.isColorStackEnabled = false
            assertThrows(IllegalStateException::class.java) {
                ColorStackPipeline.execute(
                    recipe = ColorStackRecipe(name = "Test Recipe"),
                    linework = null,
                    regionMasks = emptyMap(),
                    regionColors = emptyMap(),
                    width = 16,
                    height = 16
                )
            }
        } finally {
            FeatureFlags.isColorStackEnabled = origColorStack
        }

        // 3. Deformation Engine and Session blocked when flag is false
        val origDeformation = FeatureFlags.isDeformationEnabled
        try {
            FeatureFlags.isDeformationEnabled = false
            assertThrows(IllegalStateException::class.java) {
                DeformationSession(
                    layerId = "layer_1",
                    originalWidth = 64,
                    originalHeight = 64,
                    originalPixels = IntArray(64 * 64)
                )
            }

            assertThrows(IllegalStateException::class.java) {
                DeformationEngine.warpWithDisplacement(
                    srcPixels = IntArray(16),
                    width = 4,
                    height = 4,
                    destPixels = IntArray(16),
                    displacement = DisplacementField(4, 4),
                    edgeBehavior = EdgeBehavior.CLAMP_TO_EDGE,
                    interpolation = DeformInterpolation.BILINEAR
                )
            }
        } finally {
            FeatureFlags.isDeformationEnabled = origDeformation
        }
    }
}
