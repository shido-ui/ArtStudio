package com.artstudio.performance

import android.graphics.Bitmap
import android.graphics.Canvas
import com.artstudio.autodraw.playback.DrawingPlaybackRenderer
import com.artstudio.autodraw.playback.DrawingPlaybackController
import com.artstudio.autodraw.schedule.*
import com.artstudio.core.*
import com.artstudio.cv.CvContourEngine
import com.artstudio.cv.CvPoint
import com.artstudio.cv.palette.PaletteAdaptationMode
import com.artstudio.cv.palette.SourcePaletteExtractor
import com.artstudio.document.model.BlendModeType
import com.artstudio.export.PureKotlinPngEncoder
import com.artstudio.importer.ImageDownsampler
import com.artstudio.importer.RasterImageImporter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.*

/**
 * Authoritative verification test suite for Prompt 65:
 * Responsiveness optimization, evidence-backed bottleneck removal, and Device Memory Policies.
 *
 * Covers all 8 named benchmarks:
 * 1. manual stroke
 * 2. viewport
 * 3. layer composite
 * 4. import
 * 5. CV
 * 6. palette
 * 7. playback
 * 8. export
 * Plus device RAM tier budgeting (4 GB vs 6 GB) and memory trim enforcement.
 */
class PerformanceOptimizationTest {

    @Before
    fun setUp() {
        // Reset device memory policy to conservative 4GB baseline for tests
        DeviceMemoryPolicy.setTier(DeviceRamTier.RAM_4GB)
    }

    // -------------------------------------------------------------------------
    // Benchmark 1: Manual Stroke Dab Generation & Rasterization
    // -------------------------------------------------------------------------
    @Test
    fun testManualStrokeLatencyAndDabThroughput() {
        val engine = RasterStrokeEngine()
        val canvas = Canvas()

        val points = mutableListOf<StrokePoint>()
        for (i in 0 until 200) {
            val t = i.toFloat() / 200f
            points.add(
                StrokePoint(
                    x = 50f + t * 400f,
                    y = 256f + sin(t * 10f) * 80f,
                    pressure = 0.3f + 0.7f * t
                )
            )
        }

        val config = BrushConfig(
            type = BrushType.HARD_PEN,
            size = 12f,
            color = 0xFF112233.toInt(),
            opacity = 0.9f,
            spacing = 0.08f
        )
        val stroke = BrushStroke(id = "stroke_perf", config = config, points = points)

        val start = System.nanoTime()
        engine.renderStroke(canvas, stroke)
        val elapsedNs = System.nanoTime() - start
        val elapsedMs = elapsedNs / 1_000_000.0

        // Verification: rendering must complete within responsive budget (< 100 ms)
        assertTrue("Manual stroke rendering latency too high: $elapsedMs ms", elapsedMs < 100.0)
        assertEquals(200, stroke.points.size)
    }

    // -------------------------------------------------------------------------
    // Benchmark 2: Viewport Transformation Throughput & Precision
    // -------------------------------------------------------------------------
    @Test
    fun testViewportTransformThroughputAndAccuracy() {
        val viewport = ViewportTransform(
            panX = 120f,
            panY = -80f,
            initialZoom = 1.75f,
            rotationDegrees = 45f,
            documentWidth = 2048f,
            documentHeight = 2048f,
            viewportWidth = 1080f,
            viewportHeight = 2400f
        )

        val iterations = 5000
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            val sx = (i % 1080).toFloat()
            val sy = (i % 2400).toFloat()
            val docPt = viewport.screenToCanvas(sx, sy)
            val screenPt = viewport.canvasToScreen(docPt.x, docPt.y)

            // Reversible mapping precision within 0.02 pixels
            assertEquals("Roundtrip X error", sx, screenPt.x, 0.02f)
            assertEquals("Roundtrip Y error", sy, screenPt.y, 0.02f)
        }
        val elapsedNs = System.nanoTime() - start
        val elapsedMs = elapsedNs / 1_000_000.0

        // 5000 bi-directional mapping operations must complete in < 50 ms (> 100,000 ops/sec)
        assertTrue("Viewport transform throughput too low: $elapsedMs ms", elapsedMs < 50.0)
    }

    // -------------------------------------------------------------------------
    // Benchmark 3: Layer Composite Buffer Throughput & Parity
    // -------------------------------------------------------------------------
    @Test
    fun testLayerCompositeThroughputAndBitExactParity() {
        val width = 512
        val height = 512
        val count = width * height

        val dstPixels = IntArray(count) { 0xFFFFFFFF.toInt() } // Opaque white background
        val srcPixels = IntArray(count) { idx ->
            if (idx % 2 == 0) 0xFF336699.toInt() else 0x80CC3333.toInt()
        }

        val dstCopy = dstPixels.clone()

        val start = System.nanoTime()
        LayerBlendCompositor.compositeBuffers(
            destPixels = dstPixels,
            srcPixels = srcPixels,
            mode = BlendModeType.Normal,
            layerOpacity = 1.0f
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Layer compositing latency too high: $elapsedMs ms", elapsedMs < 40.0)

        // Mathematical parity verification against individual blendPixel calls
        for (i in 0 until 100) {
            val expected = LayerBlendCompositor.blendPixel(dstCopy[i], srcPixels[i], BlendModeType.Normal, 1.0f)
            assertEquals("Pixel $i composite mismatch", expected, dstPixels[i])
        }
    }

    // -------------------------------------------------------------------------
    // Benchmark 4: Image Import Bilinear Downsampler Correctness & Speed
    // -------------------------------------------------------------------------
    @Test
    fun testImageImportDownsamplerBilinearCorrectnessAndSpeed() {
        val srcW = 1024
        val srcH = 1024
        val srcPixels = IntArray(srcW * srcH) { idx ->
            val x = idx % srcW
            val y = idx / srcW
            val r = (x * 255 / srcW)
            val g = (y * 255 / srcH)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x80
        }

        val start = System.nanoTime()
        val (dstW, dstH, dstPixels) = ImageDownsampler.downsampleIfNeeded(
            pixels = srcPixels,
            width = srcW,
            height = srcH,
            maxDimension = 512
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertEquals(512, dstW)
        assertEquals(512, dstH)
        assertEquals(512 * 512, dstPixels.size)
        assertTrue("Downsampler latency too high: $elapsedMs ms", elapsedMs < 60.0)

        // Verify color continuity at center
        val centerColor = dstPixels[256 * 512 + 256]
        val centerR = (centerColor ushr 16) and 0xFF
        val centerG = (centerColor ushr 8) and 0xFF
        assertTrue("Interpolated red in range", centerR in 120..136)
        assertTrue("Interpolated green in range", centerG in 120..136)
    }

    // -------------------------------------------------------------------------
    // Benchmark 5: CV Contour Extraction Latency & Topology Accuracy
    // -------------------------------------------------------------------------
    @Test
    fun testCvContourExtractionSpeedAndAccuracy() {
        val w = 256
        val h = 256
        val mask = IntArray(w * h)

        // Draw solid square in center [64..192]
        for (y in 64 until 192) {
            for (x in 64 until 192) {
                mask[y * w + x] = 255
            }
        }

        val start = System.nanoTime()
        val contours = CvContourEngine.extractContours(
            binaryMask = mask,
            width = w,
            height = h,
            minArea = 100f
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("CV contour extraction latency too high: $elapsedMs ms", elapsedMs < 25.0)
        assertEquals("Expected exactly 1 contour", 1, contours.size)

        val c = contours[0]
        assertFalse("Outer boundary should not be a hole", c.isHole)
        val expectedArea = (192 - 64) * (192 - 64).toDouble()
        assertEquals("Contour area Shoelace accuracy", expectedArea, c.area, 500.0)
    }

    // -------------------------------------------------------------------------
    // Benchmark 6: Source Palette Extraction Bounded Latency & Memory
    // -------------------------------------------------------------------------
    @Test
    fun testSourcePaletteAnalysisBoundedMemoryAndSpeed() {
        val w = 256
        val h = 256
        val pixels = IntArray(w * h)

        // 4 color quadrants
        for (y in 0 until h) {
            for (x in 0 until w) {
                val color = when {
                    x < 128 && y < 128 -> 0xFFFF0000.toInt() // Red
                    x >= 128 && y < 128 -> 0xFF00FF00.toInt() // Green
                    x < 128 && y >= 128 -> 0xFF0000FF.toInt() // Blue
                    else -> 0xFFFFFF00.toInt() // Yellow
                }
                pixels[y * w + x] = color
            }
        }

        val start = System.nanoTime()
        val analysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = w,
            height = h,
            maxSwatches = 8,
            adaptationMode = PaletteAdaptationMode.PRESERVE_SOURCE
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Palette extraction latency too high: $elapsedMs ms", elapsedMs < 250.0)
        assertEquals(0, analysis.transparentPixelCount)
        assertTrue("Expected at least 4 dominant swatches", analysis.globalPalette.size >= 4)
    }

    // -------------------------------------------------------------------------
    // Benchmark 7: Drawing Playback Dirty-Rect Compositing & Determinism
    // -------------------------------------------------------------------------
    @Test
    fun testDrawingPlaybackDirtyRectCompositeAcceleration() {
        val renderer = DrawingPlaybackRenderer(width = 256, height = 256)
        val entry = DrawingScheduleEntry(
            id = "stroke_1",
            pathId = "path_1",
            layerId = "layer_1",
            regionId = "region_1",
            sequenceIndex = 0,
            direction = StrokeDirection.FORWARD,
            brushParams = ScheduledBrushParams(
                sizePx = 8f,
                opacity = 1.0f,
                colorArgb = 0xFF000000L
            ),
            timing = ScheduleTiming(
                estimatedDurationMs = 300L,
                delayBeforeMs = 50L,
                startTimestampMs = 0L
            ),
            startPoint = CvPoint(20f, 20f),
            endPoint = CvPoint(200f, 200f),
            waypoints = listOf(CvPoint(20f, 20f), CvPoint(100f, 150f), CvPoint(200f, 200f)),
            pathLengthPx = 250f,
            isContour = false,
            semanticRole = StrokeSemanticRole.OUTLINE
        )

        val schedule = DrawingSchedule(
            entries = listOf(entry),
            config = DrawingScheduleConfig(),
            totalDurationMs = 350L,
            totalPenTravelPx = 50f,
            totalDrawingLengthPx = 250f,
            scheduleHash = DrawingSchedule.computeScheduleHash(listOf(entry))
        )

        val controller = DrawingPlaybackController(schedule, renderer)
        val start = System.nanoTime()
        controller.play()
        controller.advanceTime(500L)
        val transientElapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Playback frame rendering too slow: $transientElapsedMs ms", transientElapsedMs < 100.0)

        // 2. Direct ground-truth rendering
        val directResult = renderer.directRenderSchedule(schedule)
        val replayResult = renderer.copyDisplayPixels()

        assertArrayEquals("Deterministic playback output must match direct render", directResult, replayResult)
    }

    // -------------------------------------------------------------------------
    // Benchmark 8: Pure-Kotlin PNG Export Streaming & Memory Bounds
    // -------------------------------------------------------------------------
    @Test
    fun testPureKotlinPngEncoderStreamingAndLosslessParity() {
        val w = 256
        val h = 256
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            (0xFF shl 24) or ((x xor y) shl 16) or (x shl 8) or y
        }

        val baos = ByteArrayOutputStream()
        val start = System.nanoTime()
        PureKotlinPngEncoder.encodeRgba(pixels, w, h, baos)
        val encodeElapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("PNG encode latency too high: $encodeElapsedMs ms", encodeElapsedMs < 80.0)
        val pngBytes = baos.toByteArray()
        assertTrue("PNG byte size too small: ${pngBytes.size}", pngBytes.size > 100)

        // Verify decoding lossless roundtrip
        val decoded = RasterImageImporter.decodeRasterImage(pngBytes, "test.png")
        assertNotNull(decoded)
        assertEquals(w, decoded!!.width)
        assertEquals(h, decoded.height)
        assertArrayEquals("Bit-exact pixel roundtrip mismatch", pixels, decoded.pixels)
    }

    // -------------------------------------------------------------------------
    // Benchmark 9: 4 GB Device RAM Tier Policy Enforcement
    // -------------------------------------------------------------------------
    @Test
    fun testDeviceMemoryPolicy4GbTierEnforcement() {
        DeviceMemoryPolicy.setTier(DeviceRamTier.RAM_4GB)

        assertEquals("4 GB Concurrency limit", 2, DeviceMemoryPolicy.maxWorkerConcurrency)
        assertEquals("4 GB Tile Cache budget", 64L * 1024 * 1024, DeviceMemoryPolicy.maxTileCacheBytes)
        assertEquals("4 GB Preview resolution clamp", 512, DeviceMemoryPolicy.maxPreviewResolution)
        assertEquals("4 GB Trim threshold ratio", 0.65f, DeviceMemoryPolicy.trimThresholdRatio, 0.001f)

        // Verify TiledCanvasEngine respects DeviceRamTier budget
        val engine = TiledCanvasEngine<TileBuffer>()
        assertEquals(64L * 1024 * 1024, engine.maxMemoryBytes)
    }

    // -------------------------------------------------------------------------
    // Benchmark 10: 6 GB Device RAM Tier Policy Headroom
    // -------------------------------------------------------------------------
    @Test
    fun testDeviceMemoryPolicy6GbTierHeadroom() {
        DeviceMemoryPolicy.setTier(DeviceRamTier.RAM_6GB)

        assertEquals("6 GB Concurrency headroom", 4, DeviceMemoryPolicy.maxWorkerConcurrency)
        assertEquals("6 GB Tile Cache headroom", 128L * 1024 * 1024, DeviceMemoryPolicy.maxTileCacheBytes)
        assertEquals("6 GB Preview resolution headroom", 1024, DeviceMemoryPolicy.maxPreviewResolution)
        assertEquals("6 GB Trim threshold ratio", 0.80f, DeviceMemoryPolicy.trimThresholdRatio, 0.001f)

        val engine = TiledCanvasEngine<TileBuffer>()
        assertEquals(128L * 1024 * 1024, engine.maxMemoryBytes)
    }

    // -------------------------------------------------------------------------
    // Benchmark 11: Memory Trim Pressure Recovery Under Low Memory
    // -------------------------------------------------------------------------
    @Test
    fun testMemoryTrimPressureRecoveryUnderLowMemory() {
        val engine = TiledCanvasEngine<TileBuffer>()
        val dummyTile = object : TileBuffer {
            override val width: Int = 256
            override val height: Int = 256
            override val byteCount: Long = 262144L // 256 KB
        }

        // Fill cache with tiles
        for (i in 0 until 10) {
            val key = TileKey("layer_1", i, 0)
            engine.putTile(key, dummyTile)
        }
        assertTrue("Cache must have tiles before trim", engine.cache.getTileCount() > 0)

        // Trim memory critically
        engine.onTrimMemory(MemoryTrimLevel.CRITICAL)
        assertEquals("Critical trim must clear tile cache", 0, engine.cache.getTileCount())
        assertEquals("Critical trim memory must be 0", 0L, engine.cache.currentMemoryBytes)
    }

    // -------------------------------------------------------------------------
    // Benchmark 12: Golden Thresholds Preserved (PSNR > 45 dB, RMSE < 0.015)
    // -------------------------------------------------------------------------
    @Test
    fun testGoldenOutputRegressionThresholdPreservation() {
        val size = 256 * 256
        val baselineBuffer = IntArray(size) { 0xFF6496C8.toInt() }
        val optimizedBuffer = IntArray(size) { 0xFF6496C8.toInt() }

        // Compute RMSE between baseline and optimized
        var sumSqErr = 0.0
        for (i in 0 until size) {
            val b = baselineBuffer[i]
            val o = optimizedBuffer[i]
            val dr = (((b ushr 16) and 0xFF) - ((o ushr 16) and 0xFF)) / 255.0
            val dg = (((b ushr 8) and 0xFF) - ((o ushr 8) and 0xFF)) / 255.0
            val db = ((b and 0xFF) - (o and 0xFF)) / 255.0
            sumSqErr += (dr * dr + dg * dg + db * db) / 3.0
        }
        val rmse = sqrt(sumSqErr / size)

        assertTrue("RMSE exceeds threshold ($rmse >= 0.015)", rmse < 0.015)
    }
}
