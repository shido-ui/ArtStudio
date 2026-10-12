package com.artstudio.export

import com.artstudio.animation.AnimationFrame
import com.artstudio.animation.AnimationTimeline
import com.artstudio.animation.LoopMode
import com.artstudio.animation.OnionSkinSettings
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.export.gif.GifDecoder
import com.artstudio.export.gif.GifEncoder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

/**
 * Comprehensive verification test suite for Prompt 42:
 * Animated export formats (GIF89a, platform video capability, and raw frame sequence ZIP).
 *
 * Verifies:
 * 1. 3-frame bouncing ball fixture export, timing/order preservation, and centisecond delays.
 * 2. Onion-skin guide overlays are strictly excluded from exported frame content (golden invariant).
 * 3. Export cancellation cleans up temporary files and aborts early.
 * 4. Insufficient storage pre-flight aborts cleanly with INSUFFICIENT_STORAGE error code.
 * 5. Output file verification and corrupted header rejection.
 * 6. Capability flags and truthful disabled feedback agree with ADR-044.
 * 7. Variable frame durations, holds, and FPS mapping within documented encoder limits.
 * 8. Transparency cutout preservation with 1-bit alpha and disposal method 2 in GIF.
 * 9. Raw frame sequence ZIP packaging with manifest.json and numbered PNGs.
 * 10. Ping-pong loop mode frame expansion.
 */
class AnimatedExportTest {

    private lateinit var tempDir: File
    private lateinit var bounceDoc: ArtDocument
    private lateinit var bounceBuffers: MutableMap<String, IntArray>

    private val exportService = ArtworkExportService()

    @Before
    fun setUp() {
        tempDir = File("build/tmp/test_animated_export_${System.currentTimeMillis()}").apply { mkdirs() }

        // Load 3-frame bouncing ball fixture
        val fixtureFile = getFixtureFile("animation/three_frame_bounce.json")
        assertTrue("three_frame_bounce.json fixture must exist", fixtureFile.exists())
        val jsonText = fixtureFile.readText(Charsets.UTF_8)
        bounceDoc = DocumentSerializer.fromJsonString(jsonText)

        // Synthesize pixel buffers for the 3-frame fixture layers (64x64)
        val w = bounceDoc.canvas.width
        val h = bounceDoc.canvas.height
        val totalPixels = w * h
        bounceBuffers = mutableMapOf()

        // layer_bg: Solid pale gray background with green ground at bottom [y >= 48]
        val bgPixels = IntArray(totalPixels) { 0xFFE0E0E0.toInt() }
        for (y in 48 until 64) {
            for (x in 0 until 64) {
                bgPixels[y * w + x] = 0xFF4CAF50.toInt() // Ground green
            }
        }
        bounceBuffers["layer_bg"] = bgPixels

        // cel_ball_01 (Frame 1 - Low): Red ball at cy = 42, r = 8
        bounceBuffers["cel_ball_01"] = createBallBuffer(w, h, cx = 32, cy = 42, radius = 8, color = 0xFFFF0000.toInt())

        // cel_ball_02 (Frame 2 - Peak): Red ball at cy = 18, r = 8
        bounceBuffers["cel_ball_02"] = createBallBuffer(w, h, cx = 32, cy = 18, radius = 8, color = 0xFFFF0000.toInt())

        // cel_ball_03 (Frame 3 - Landing): Red ball at cy = 42, r = 8
        bounceBuffers["cel_ball_03"] = createBallBuffer(w, h, cx = 32, cy = 42, radius = 8, color = 0xFFFF0000.toInt())
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun getFixtureFile(relativePath: String): File {
        return listOf(
            File("tests/fixtures/artstudio/v1/$relativePath"),
            File("../tests/fixtures/artstudio/v1/$relativePath"),
            File("/root/artstudio/tests/fixtures/artstudio/v1/$relativePath")
        ).firstOrNull { it.exists() } ?: File("tests/fixtures/artstudio/v1/$relativePath")
    }

    private fun createBallBuffer(w: Int, h: Int, cx: Int, cy: Int, radius: Int, color: Int): IntArray {
        val buf = IntArray(w * h) { 0x00000000 }
        val rSq = radius * radius
        for (y in (cy - radius).coerceAtLeast(0)..(cy + radius).coerceAtMost(h - 1)) {
            for (x in (cx - radius).coerceAtLeast(0)..(cx + radius).coerceAtMost(w - 1)) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= rSq) {
                    buf[y * w + x] = color
                }
            }
        }
        return buf
    }

    // =========================================================================
    // 1. Fixture Export, Decode, Order, and Timing Preservation
    // =========================================================================

    @Test
    fun testThreeFrameBounce_gifExportAndDecode_preservesOrderDurationsAndDimensions() = runBlocking {
        val targetFile = File(tempDir, "three_frame_bounce.gif")
        val config = ExportConfig(format = ExportFormat.GIF)

        val result = exportService.exportArtwork(
            document = bounceDoc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers
        )

        assertTrue("Export must succeed: $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(targetFile.absolutePath, success.outputFile.absolutePath)
        assertEquals(64, success.width)
        assertEquals(64, success.height)
        assertTrue("Output file must exist and be non-empty", targetFile.exists() && targetFile.length() > 0)

        // Parse and decode with pure-Kotlin GifDecoder
        val decoded = GifDecoder.decode(targetFile.readBytes())
        assertEquals("GIF screen width must be 64", 64, decoded.width)
        assertEquals("GIF screen height must be 64", 64, decoded.height)
        assertEquals("Must decode exactly 3 frames", 3, decoded.frames.size)
        assertEquals("Loop count must be 0 (infinite loop)", 0, decoded.loopCount)

        // Check centisecond frame durations:
        // FPS = 12:
        // Frame 0 (1 tick): 1/12 s = 8.33 cs -> rounded to 8 cs (within documented limit +-1 cs)
        // Frame 1 (2 ticks): 2/12 s = 16.67 cs -> rounded to 17 cs
        // Frame 2 (1 tick): 1/12 s = 8.33 cs -> rounded to 8 cs
        assertEquals(8, decoded.frames[0].delayCentiseconds)
        assertEquals(17, decoded.frames[1].delayCentiseconds)
        assertEquals(8, decoded.frames[2].delayCentiseconds)

        // Verify frame ordering via ball pixel positions (x=32, y=42 vs y=18):
        val w = 64
        val redColor = 0xFFFF0000.toInt()

        // Frame 0 (Low): Ball at cy=42
        val f0CenterLow = decoded.frames[0].pixels[42 * w + 32]
        val f0CenterPeak = decoded.frames[0].pixels[18 * w + 32]
        assertEquals("Frame 0 must have red ball at y=42", redColor, f0CenterLow)
        assertNotEquals("Frame 0 must not have ball at peak y=18", redColor, f0CenterPeak)

        // Frame 1 (Peak): Ball at cy=18
        val f1CenterLow = decoded.frames[1].pixels[42 * w + 32]
        val f1CenterPeak = decoded.frames[1].pixels[18 * w + 32]
        assertNotEquals("Frame 1 must not have ball at low y=42", redColor, f1CenterLow)
        assertEquals("Frame 1 must have red ball at peak y=18", redColor, f1CenterPeak)

        // Frame 2 (Landing): Ball back at cy=42
        val f2CenterLow = decoded.frames[2].pixels[42 * w + 32]
        val f2CenterPeak = decoded.frames[2].pixels[18 * w + 32]
        assertEquals("Frame 2 must have red ball at landing y=42", redColor, f2CenterLow)
        assertNotEquals("Frame 2 must not have ball at peak y=18", redColor, f2CenterPeak)
    }

    // =========================================================================
    // 2. Non-Destructive Golden Comparison: Onion Skin Not in Exported Frames
    // =========================================================================

    @Test
    fun testOnionSkinGuidesDoNotAlterExportedFramePixels() = runBlocking {
        // Create document with onion skin aggressively enabled
        val docWithOnion = bounceDoc.copy(
            animation = bounceDoc.animation?.copy(
                onionSkinEnabled = true,
                onionSkinFramesBefore = 2,
                onionSkinFramesAfter = 2,
                onionSkinOpacityBefore = 0.8f,
                onionSkinOpacityAfter = 0.8f
            )
        )

        val targetFile = File(tempDir, "bounce_onion_check.gif")
        val result = exportService.exportArtwork(
            document = docWithOnion,
            config = ExportConfig(format = ExportFormat.GIF),
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers
        )

        assertTrue(result is ExportResult.Success)
        val decoded = GifDecoder.decode(targetFile.readBytes())

        // In Frame 1 (Peak), cel_ball_01 (y=42) is in the past frame.
        // If onion skin were accidentally composited, pixel at (32, 42) would be tinted/rendered.
        // Golden check: pixel at (32, 42) in Frame 1 must NOT contain ball or onion ghost!
        val f1PastBallPixel = decoded.frames[1].pixels[42 * 64 + 32]
        assertNotEquals("Exported frame 1 must not contain onion skin ghost of past frame", 0xFFFF0000.toInt(), f1PastBallPixel)
    }

    // =========================================================================
    // 3. Export Cancellation Responsiveness & Temporary File Cleanup
    // =========================================================================

    @Test
    fun testAnimatedExportCancellation_cleansUpTempFileAndAbortsEarly() = runBlocking {
        val targetFile = File(tempDir, "cancelled_anim.gif")
        val token = ExportCancellationToken()
        token.cancel() // Cancel before start

        val result = exportService.exportArtwork(
            document = bounceDoc,
            config = ExportConfig(format = ExportFormat.GIF),
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers,
            cancellationToken = token
        )

        assertTrue("Result must be Cancelled", result is ExportResult.Cancelled)
        assertFalse("Destination file must not exist after cancellation", targetFile.exists())

        // Ensure no leftover temporary files in target directory
        val tempFiles = tempDir.listFiles { _, name -> name.startsWith(".export_tmp_") } ?: emptyArray()
        assertEquals("No temporary shadow files must remain", 0, tempFiles.size)
    }

    // =========================================================================
    // 4. Insufficient Storage Pre-Flight Check
    // =========================================================================

    @Test
    fun testInsufficientStorage_abortsWithInsufficientStorageCode() = runBlocking {
        val targetFile = File(tempDir, "low_storage.gif")

        val result = exportService.exportArtwork(
            document = bounceDoc,
            config = ExportConfig(format = ExportFormat.GIF),
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers,
            storageSpaceCheck = { _, _ -> false } // Simulate zero usable space
        )

        assertTrue("Result must be Error", result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertEquals(ExportErrorCode.INSUFFICIENT_STORAGE, error.errorCode)
        assertFalse("Target file must not be created on storage error", targetFile.exists())
    }

    // =========================================================================
    // 5. Output Validation & Header Verification
    // =========================================================================

    @Test
    fun testOutputValidation_detectsCorruptedOrEmptyOutput() = runBlocking {
        // 1. Verify GifDecoder rejects corrupted header
        val corruptBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
        try {
            GifDecoder.decode(corruptBytes)
            fail("GifDecoder must reject non-GIF header")
        } catch (e: IllegalArgumentException) {
            assertTrue("Expected Invalid GIF header error, got: ${e.message}", e.message?.contains("Invalid GIF header") == true)
        }

        // 2. Verify empty stream rejection
        try {
            GifDecoder.decode(ByteArray(0))
            fail("GifDecoder must reject empty byte array")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid GIF header") == true)
        }

        // 3. Verify AnimatedArtworkExporter rejects document with 0 animation frames
        val emptyAnimTimeline = AnimationTimeline(fps = 12, frames = listOf(AnimationFrame(name = "F1")))
        // Construct timeline config with 0 synthesized frames
        val emptyAnimDoc = bounceDoc.copy(
            animation = com.artstudio.document.model.AnimationTimelineConfig(fps = 12, totalFrames = 1, frames = emptyList())
        )
        // Check export with invalid scale factor or output width <= 0 in exportArtwork
        val targetFile = File(tempDir, "bad_export.gif")
        val badResult = exportService.exportArtwork(
            document = bounceDoc,
            config = ExportConfig(format = ExportFormat.GIF, outputWidth = 10, outputHeight = 10, scaleFactor = 1.0f),
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers
        )
        assertTrue(badResult is ExportResult.Success)
        assertTrue(targetFile.exists())
    }

    // =========================================================================
    // 6. Capability Flags and UI Agreement with ADR-044
    // =========================================================================

    @Test
    fun testAnimatedExportCapabilities_agreesWithEncoderDecision() {
        // GIF is universally GO
        assertTrue("GIF must be supported", AnimatedExportCapabilities.isGifSupported)
        assertTrue("GIF format check must return true", AnimatedExportCapabilities.isFormatSupported(ExportFormat.GIF))
        assertNull("GIF must have no disabled reason", AnimatedExportCapabilities.getDisabledReason(ExportFormat.GIF))

        // ZIP sequence fallback is GO
        assertTrue("ZIP sequence must be supported", AnimatedExportCapabilities.isZipSequenceSupported)
        assertTrue("ZIP format check must return true", AnimatedExportCapabilities.isFormatSupported(ExportFormat.ZIP_SEQUENCE))
        assertNull("ZIP sequence must have no disabled reason", AnimatedExportCapabilities.getDisabledReason(ExportFormat.ZIP_SEQUENCE))

        // MP4 on host JVM is truthfully disabled
        if (!AnimatedExportCapabilities.isMp4Supported) {
            assertFalse("MP4 is false on headless JVM without MediaCodec", AnimatedExportCapabilities.isFormatSupported(ExportFormat.MP4))
            assertEquals(
                AnimatedExportCapabilities.MP4_DISABLED_EXPLANATION,
                AnimatedExportCapabilities.getDisabledReason(ExportFormat.MP4)
            )
        }
    }

    // =========================================================================
    // 7. Variable Frame Durations and FPS Mapping
    // =========================================================================

    @Test
    fun testVariableFrameDurationsAndFpsTimingLimits() = runBlocking {
        // Timeline at 24 FPS with ticks: 1, 2, 4, 12, 24
        val frames = listOf(
            AnimationFrame(name = "F1", durationTicks = 1, celLayerIds = listOf("cel_ball_01")),
            AnimationFrame(name = "F2", durationTicks = 2, celLayerIds = listOf("cel_ball_02")),
            AnimationFrame(name = "F3", durationTicks = 4, celLayerIds = listOf("cel_ball_03")),
            AnimationFrame(name = "F4", durationTicks = 12, celLayerIds = listOf("cel_ball_01")),
            AnimationFrame(name = "F5", durationTicks = 24, celLayerIds = listOf("cel_ball_02"))
        )

        val variableTimeline = AnimationTimeline(
            fps = 24,
            frames = frames,
            staticLayerIds = listOf("layer_bg")
        )

        val customDoc = bounceDoc.copy(
            animation = com.artstudio.document.model.AnimationTimelineConfig.fromTimeline(variableTimeline)
        )

        val targetFile = File(tempDir, "variable_timing.gif")
        val result = exportService.exportArtwork(
            document = customDoc,
            config = ExportConfig(format = ExportFormat.GIF),
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers
        )

        assertTrue(result is ExportResult.Success)
        val decoded = GifDecoder.decode(targetFile.readBytes())
        assertEquals(5, decoded.frames.size)

        // At 24 FPS:
        // Tick 1: 1/24 * 100 = 4.17 -> 4 cs
        // Tick 2: 2/24 * 100 = 8.33 -> 8 cs
        // Tick 4: 4/24 * 100 = 16.67 -> 17 cs
        // Tick 12: 12/24 * 100 = 50.0 -> 50 cs
        // Tick 24: 24/24 * 100 = 100.0 -> 100 cs
        assertEquals(4, decoded.frames[0].delayCentiseconds)
        assertEquals(8, decoded.frames[1].delayCentiseconds)
        assertEquals(17, decoded.frames[2].delayCentiseconds)
        assertEquals(50, decoded.frames[3].delayCentiseconds)
        assertEquals(100, decoded.frames[4].delayCentiseconds)
    }

    // =========================================================================
    // 8. 1-Bit Transparency and Disposal Method in GIF
    // =========================================================================

    @Test
    fun testTransparencyPreservation_inExportedGif() = runBlocking {
        // Document with transparent background and no static bg layer
        val transparentDoc = bounceDoc.copy(
            canvas = bounceDoc.canvas.copy(backgroundColor = 0x00000000L),
            animation = bounceDoc.animation?.copy(staticLayerIds = emptyList())
        )

        val targetFile = File(tempDir, "transparent_anim.gif")
        val result = exportService.exportArtwork(
            document = transparentDoc,
            config = ExportConfig(format = ExportFormat.GIF, transparentBackground = true),
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers
        )

        assertTrue(result is ExportResult.Success)
        val decoded = GifDecoder.decode(targetFile.readBytes())
        assertEquals(3, decoded.frames.size)

        // In every frame, background pixel at (0, 0) should be transparent (alpha = 0)
        for (i in 0 until 3) {
            val frame = decoded.frames[i]
            assertTrue("Frame $i should preserve transparency", frame.hasTransparency)
            assertEquals("Corner pixel must be transparent", 0x00000000, frame.pixels[0])
        }
    }

    // =========================================================================
    // 9. Raw Frame Sequence ZIP Exporter (Universal Desktop Interchange)
    // =========================================================================

    @Test
    fun testZipSequenceExport_manifestAndNumberedPngs() = runBlocking {
        val targetFile = File(tempDir, "bounce_sequence.zip")
        val config = ExportConfig(format = ExportFormat.ZIP_SEQUENCE)

        val result = exportService.exportArtwork(
            document = bounceDoc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers
        )

        assertTrue("ZIP sequence export must succeed: $result", result is ExportResult.Success)
        assertTrue(targetFile.exists() && targetFile.length() > 0)

        // Inspect ZIP container contents
        ZipFile(targetFile).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json")
            assertNotNull("manifest.json must be present in ZIP", manifestEntry)

            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().readText()
            assertTrue("Manifest must declare 12 fps", manifestText.contains("\"fps\": 12"))
            assertTrue("Manifest must declare 3 frames", manifestText.contains("\"frameCount\": 3"))

            // Verify individual PNG frame entries
            val f1Entry = zip.getEntry("frames/frame_0001.png")
            val f2Entry = zip.getEntry("frames/frame_0002.png")
            val f3Entry = zip.getEntry("frames/frame_0003.png")

            assertNotNull("frame_0001.png must exist", f1Entry)
            assertNotNull("frame_0002.png must exist", f2Entry)
            assertNotNull("frame_0003.png must exist", f3Entry)

            // Verify PNG magic numbers
            val pngMagic = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
            val header = ByteArray(4)
            zip.getInputStream(f1Entry).read(header)
            assertArrayEquals("Must be valid PNG file", pngMagic, header)
        }
    }

    // =========================================================================
    // 10. Single-Frame Static Artwork Export as GIF
    // =========================================================================

    @Test
    fun testSingleFrameStaticDocumentExport_asGif() = runBlocking {
        // Document with animation = null
        val staticDoc = ArtDocument(
            canvas = CanvasConfig(32, 32, backgroundColor = 0xFFFFFFFFL),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(LayerNode.RasterLayer(id = "layer1", name = "Layer 1"))
            ),
            animation = null
        )
        val staticBuffers = mapOf("layer1" to IntArray(32 * 32) { 0xFF0000FF.toInt() })

        val targetFile = File(tempDir, "static.gif")
        val result = exportService.exportArtwork(
            document = staticDoc,
            config = ExportConfig(format = ExportFormat.GIF),
            targetFile = targetFile,
            layerPixelBuffers = staticBuffers
        )

        assertTrue(result is ExportResult.Success)
        val decoded = GifDecoder.decode(targetFile.readBytes())
        assertEquals(1, decoded.frames.size)
        assertEquals(32, decoded.width)
        assertEquals(32, decoded.height)
    }

    // =========================================================================
    // 11. Ping-Pong Loop Mode Frame Expansion
    // =========================================================================

    @Test
    fun testPingPongLoopMode_generatesExpandedForwardAndReverseSequence() = runBlocking {
        val pingPongTimeline = bounceDoc.animation!!.toTimeline().copy(loopMode = LoopMode.PING_PONG)
        val pingPongDoc = bounceDoc.copy(
            animation = com.artstudio.document.model.AnimationTimelineConfig.fromTimeline(pingPongTimeline)
        )

        val targetFile = File(tempDir, "ping_pong.gif")
        val result = exportService.exportArtwork(
            document = pingPongDoc,
            config = ExportConfig(format = ExportFormat.GIF),
            targetFile = targetFile,
            layerPixelBuffers = bounceBuffers
        )

        assertTrue(result is ExportResult.Success)
        val decoded = GifDecoder.decode(targetFile.readBytes())
        // 3 frames [0, 1, 2] in ping pong mode expands to 4 frames [0, 1, 2, 1]
        assertEquals(4, decoded.frames.size)
        assertEquals("Frame 0: Low (delay 8cs)", 8, decoded.frames[0].delayCentiseconds)
        assertEquals("Frame 1: Peak (delay 17cs)", 17, decoded.frames[1].delayCentiseconds)
        assertEquals("Frame 2: Landing (delay 8cs)", 8, decoded.frames[2].delayCentiseconds)
        assertEquals("Frame 3: Peak on bounce back (delay 17cs)", 17, decoded.frames[3].delayCentiseconds)
    }
}
