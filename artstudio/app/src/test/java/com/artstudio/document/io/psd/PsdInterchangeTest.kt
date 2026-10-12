package com.artstudio.document.io.psd

import com.artstudio.comic.ComicPanel
import com.artstudio.comic.Point2D
import com.artstudio.document.io.CorruptDocumentException
import com.artstudio.document.model.*
import com.artstudio.export.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

class PsdInterchangeTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("artstudio_psd_tests").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun resolveFixture(relativePath: String): File {
        val candidates = listOf(
            File("tests/fixtures/artstudio/v1/$relativePath"),
            File("../tests/fixtures/artstudio/v1/$relativePath"),
            File("/root/artstudio/tests/fixtures/artstudio/v1/$relativePath")
        )
        val found = candidates.firstOrNull { it.exists() }
        assertNotNull("Fixture '$relativePath' must exist on disk", found)
        return found!!
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var n: Int
            while (fis.read(buf).also { n = it } > 0) {
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // 1. PSD Import: Fixture Simple preserves 2 layers, dimensions, blend modes, and pixel buffers
    @Test
    fun testImportSimplePsdFixture() {
        val fixtureFile = resolveFixture("layered_psd/sample_layered_simple_01.psd")
        val result = PsdDocumentImporter.importPsd(fixtureFile)

        val doc = result.document
        assertEquals("Canvas width must match fixture (64)", 64, doc.canvas.width)
        assertEquals("Canvas height must match fixture (64)", 64, doc.canvas.height)

        val layers = doc.allLayersFlat().filterIsInstance<LayerNode.RasterLayer>()
        assertEquals("Document must contain exactly 2 raster layers", 2, layers.size)

        val bgLayer = layers[0]
        assertEquals("Background", bgLayer.name)
        assertTrue("Background layer must be visible", bgLayer.visible)
        assertEquals(1.0f, bgLayer.opacity, 0.01f)
        assertEquals(BlendModeType.Normal, bgLayer.blendMode)

        val subjectLayer = layers[1]
        assertEquals("Subject", subjectLayer.name)
        assertTrue("Subject layer must be visible", subjectLayer.visible)
        assertEquals(1.0f, subjectLayer.opacity, 0.01f)
        assertEquals(BlendModeType.Normal, subjectLayer.blendMode)

        // Verify pixel buffers
        val bgPixels = result.layerPixelBuffers[bgLayer.id]
        assertNotNull("Background pixel buffer must be decoded", bgPixels)
        assertEquals(64 * 64, bgPixels!!.size)
        // Background center pixel is white: 0xFFFFFFFF
        val bgCenter = bgPixels[32 * 64 + 32]
        assertEquals(0xFFFFFFFF.toInt(), bgCenter)

        val fgPixels = result.layerPixelBuffers[subjectLayer.id]
        assertNotNull("Subject pixel buffer must be decoded", fgPixels)
        assertEquals(64 * 64, fgPixels!!.size)
        // Subject center pixel is blue: (30, 144, 255) -> 0xFF1E90FF
        val fgCenter = fgPixels[32 * 64 + 32]
        val a = (fgCenter ushr 24) and 0xFF
        val r = (fgCenter ushr 16) and 0xFF
        val g = (fgCenter ushr 8) and 0xFF
        val b = fgCenter and 0xFF
        assertEquals(255, a)
        assertEquals(30, r)
        assertEquals(144, g)
        assertEquals(255, b)
    }

    // 2. PSD Import: Fixture Clipping & Hidden layers restores clipping mask & visibility flags
    @Test
    fun testImportClippingAndHiddenPsdFixture() {
        val fixtureFile = resolveFixture("layered_psd/sample_layered_clipping_02.psd")
        val result = PsdDocumentImporter.importPsd(fixtureFile)

        val layers = result.document.allLayersFlat().filterIsInstance<LayerNode.RasterLayer>()
        assertEquals("Document must contain 3 layers", 3, layers.size)

        val baseLayer = layers[0]
        assertEquals("Base Layer", baseLayer.name)
        assertFalse("Base layer is not a clipping mask", baseLayer.isClippingMask)
        assertTrue("Base layer must be visible", baseLayer.visible)

        val clipLayer = layers[1]
        assertEquals("Clipping Detail", clipLayer.name)
        assertTrue("Clipping Detail must have isClippingMask == true", clipLayer.isClippingMask)
        assertEquals("BlendMode must be Multiply", BlendModeType.Multiply, clipLayer.blendMode)
        assertTrue("Clipping Detail must be visible", clipLayer.visible)
        assertEquals(230 / 255f, clipLayer.opacity, 0.02f)

        val hiddenLayer = layers[2]
        assertEquals("Hidden Sketch", hiddenLayer.name)
        assertFalse("Hidden layer must have visible == false", hiddenLayer.visible)
        assertEquals(180 / 255f, hiddenLayer.opacity, 0.02f)
    }

    // 3. Reject corrupted header PSD: throws CorruptDocumentException
    @Test
    fun testRejectCorruptedHeaderPsd() {
        val badFixture = resolveFixture("corrupt_truncated/corrupted_header_01.psd")
        try {
            PsdDocumentImporter.importPsd(badFixture)
            fail("Expected CorruptDocumentException for corrupted PSD header")
        } catch (e: CorruptDocumentException) {
            assertTrue("Exception message must detail invalid signature", e.message?.contains("Invalid PSD signature") == true)
        }
    }

    // 4. Reject truncated PSD: throws CorruptDocumentException
    @Test
    fun testRejectTruncatedChannelsPsd() {
        val truncatedFixture = resolveFixture("corrupt_truncated/truncated_channels_02.psd")
        try {
            PsdDocumentImporter.importPsd(truncatedFixture)
            fail("Expected CorruptDocumentException for truncated PSD file")
        } catch (e: CorruptDocumentException) {
            assertNotNull(e.message)
        }
    }

    // 5. Source File Immutability: Disk bytes are 100% unaltered during import
    @Test
    fun testSourceFileImmutability() {
        val fixtureFile = resolveFixture("layered_psd/sample_layered_simple_01.psd")
        val hashBefore = sha256(fixtureFile)
        val sizeBefore = fixtureFile.length()

        // Perform import
        val result = PsdDocumentImporter.importPsd(fixtureFile)
        assertNotNull(result)

        val hashAfter = sha256(fixtureFile)
        val sizeAfter = fixtureFile.length()

        assertEquals("Source file byte size must not change", sizeBefore, sizeAfter)
        assertEquals("Source file SHA-256 hash must be identical (source file immutable)", hashBefore, hashAfter)
    }

    // 6. Conversion Report: Unsupported blend mode triggers warning and safe fallback
    @Test
    fun testConversionReportOnUnsupportedBlendMode() {
        // Construct a synthetic PSD file in memory with blend mode key "diff" (Difference)
        val fixtureFile = resolveFixture("layered_psd/sample_layered_simple_01.psd")
        val bytes = fixtureFile.readBytes()

        // Locate '8BIMnorm' in the byte stream and replace 'norm' with 'diff'
        var replaced = false
        val needle = "8BIMnorm".toByteArray(Charsets.US_ASCII)
        for (i in 0 until bytes.size - needle.size) {
            var match = true
            for (k in needle.indices) {
                if (bytes[i + k] != needle[k]) { match = false; break }
            }
            if (match) {
                // Replace 'norm' with 'diff'
                bytes[i + 4] = 'd'.code.toByte()
                bytes[i + 5] = 'i'.code.toByte()
                bytes[i + 6] = 'f'.code.toByte()
                bytes[i + 7] = 'f'.code.toByte()
                replaced = true
                break
            }
        }
        assertTrue("Synthetic replacement needle must be found", replaced)

        val result = PsdDocumentImporter.importPsd(bytes.inputStream(), "test_unsupported.psd")
        val report = result.conversionReport
        assertNotNull(report)
        assertTrue("Report must record warnings for unsupported blend mode", report.warnings.isNotEmpty())
        val warning = report.warnings.firstOrNull { it.feature == "UNSUPPORTED_BLEND_MODE" }
        assertNotNull("Warning for UNSUPPORTED_BLEND_MODE must exist", warning)
        assertTrue("Warning message must mention 'diff'", warning!!.message.contains("diff"))

        // Blend mode should safely fall back to Normal
        val subjectLayer = result.document.allLayersFlat().filterIsInstance<LayerNode.RasterLayer>().first { it.name == "Subject" }
        assertEquals("Unsupported blend mode must fall back to Normal", BlendModeType.Normal, subjectLayer.blendMode)
    }

    // 7. PSD Export and Re-Import Round-Trip
    @Test
    fun testPsdExportAndReimportRoundTrip() {
        val width = 128
        val height = 128

        val layer1Id = UUID.randomUUID().toString()
        val layer2Id = UUID.randomUUID().toString()

        val layer1 = LayerNode.RasterLayer(
            id = layer1Id,
            name = "Base Paint",
            width = width,
            height = height,
            blendMode = BlendModeType.Normal,
            opacity = 1.0f
        )

        val layer2 = LayerNode.RasterLayer(
            id = layer2Id,
            name = "Screen Highlight",
            width = 64,
            height = 64,
            transform = LayerTransform(translationX = 32f, translationY = 32f),
            blendMode = BlendModeType.Screen,
            opacity = 0.85f,
            isClippingMask = true,
            isAlphaLocked = true
        )

        val doc = ArtDocument(
            title = "PsdRoundTripTest",
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(layer1, layer2)
            )
        )

        // Generate pixel buffers
        val px1 = IntArray(width * height) { 0xFFFF0000.toInt() } // Solid red
        val px2 = IntArray(64 * 64) { 0xCC00FF00.toInt() }       // Semi-transparent green

        val buffers = mapOf(layer1Id to px1, layer2Id to px2)
        val exportFile = File(tempDir, "roundtrip_test.psd")

        // 1. Export
        val exportResult = PsdWriter.exportPsd(
            document = doc,
            layerPixelBuffers = buffers,
            outputFile = exportFile
        )

        assertTrue("Exported file must exist", exportFile.exists())
        assertTrue("Exported file size must be > 100 bytes", exportFile.length() > 100L)
        assertEquals(2, exportResult.layerCount)

        // 2. Re-import
        val importResult = PsdDocumentImporter.importPsd(exportFile)
        val importedDoc = importResult.document

        assertEquals(width, importedDoc.canvas.width)
        assertEquals(height, importedDoc.canvas.height)

        val importedLayers = importedDoc.allLayersFlat().filterIsInstance<LayerNode.RasterLayer>()
        assertEquals(2, importedLayers.size)

        val impL1 = importedLayers[0]
        assertEquals("Base Paint", impL1.name)
        assertEquals(BlendModeType.Normal, impL1.blendMode)
        assertEquals(1.0f, impL1.opacity, 0.02f)
        assertFalse(impL1.isClippingMask)

        val impL2 = importedLayers[1]
        assertEquals("Screen Highlight", impL2.name)
        assertEquals(BlendModeType.Screen, impL2.blendMode)
        assertEquals(0.85f, impL2.opacity, 0.02f)
        assertTrue(impL2.isClippingMask)
        assertTrue(impL2.isAlphaLocked)
        assertEquals(32f, impL2.transform.translationX, 0.1f)
        assertEquals(32f, impL2.transform.translationY, 0.1f)
        assertEquals(64, impL2.width)
        assertEquals(64, impL2.height)

        // Verify pixel buffer reconstructed
        val impPx1 = importResult.layerPixelBuffers[impL1.id]
        assertNotNull(impPx1)
        assertEquals(width * height, impPx1!!.size)
        assertEquals(0xFFFF0000.toInt(), impPx1[0])

        val impPx2 = importResult.layerPixelBuffers[impL2.id]
        assertNotNull(impPx2)
        assertEquals(64 * 64, impPx2!!.size)
        // Red channel is 0, Green is 255, Blue is 0
        val p = impPx2[0]
        assertEquals(0xCC, (p ushr 24) and 0xFF)
        assertEquals(0x00, (p ushr 16) and 0xFF)
        assertEquals(0xFF, (p ushr 8) and 0xFF)
        assertEquals(0x00, p and 0xFF)
    }

    // 8. ArtworkExportService: PSD Export Integration, atomic commit & verification
    @Test
    fun testArtworkExportServicePsdIntegration() = runBlocking {
        val service = ArtworkExportService()
        val doc = ArtDocument(
            title = "ServiceExportTest",
            canvas = CanvasConfig(width = 64, height = 64)
        )
        val layer = doc.allLayersFlat().filterIsInstance<LayerNode.RasterLayer>().first()
        val px = IntArray(64 * 64) { 0xFF0000FF.toInt() } // Blue
        val buffers = mapOf(layer.id to px)

        val targetFile = File(tempDir, "service_export.psd")
        val config = ExportConfig(format = ExportFormat.PSD)

        val result = service.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = buffers
        )

        assertTrue("Result must be Success", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(ExportFormat.PSD, success.format)
        assertTrue("Committed file must exist", targetFile.exists())
        assertEquals(targetFile.length(), success.fileSize)

        // Verify PSD signature
        val header = ByteArray(4)
        FileInputStream(targetFile).use { it.read(header) }
        assertArrayEquals("8BPS".toByteArray(Charsets.US_ASCII), header)

        // Verify that PsdParser can parse it
        val parsed = PsdParser.parse(targetFile.readBytes())
        assertEquals(64, parsed.header.width)
        assertEquals(64, parsed.header.height)
        assertEquals(1, parsed.layers.size)
    }

    // 9. DocumentConversionAuditor Disclosures on Export
    @Test
    fun testAuditExportWarningsLoggedForUnsupportedFeatures() {
        val width = 64
        val height = 64
        val rasterLayer = LayerNode.RasterLayer(
            name = "Background",
            width = width,
            height = height
        )
        val vectorLayer = LayerNode.VectorPathLayer(
            name = "Ink Stroke"
        )
        val comicPanel = ComicPanel(
            id = "panel_1",
            vertices = listOf(Point2D(0f, 0f), Point2D(50f, 0f), Point2D(50f, 50f), Point2D(0f, 50f))
        )

        val doc = ArtDocument(
            title = "AuditTest",
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(rasterLayer, vectorLayer)
            ),
            comicPanels = listOf(comicPanel)
        )

        val targetFile = File(tempDir, "audit_test.psd")
        val exportResult = PsdWriter.exportPsd(
            document = doc,
            layerPixelBuffers = mapOf(rasterLayer.id to IntArray(width * height)),
            outputFile = targetFile
        )

        val report = exportResult.conversionReport
        assertNotNull(report)
        val warnings = report.warnings
        assertTrue("Must log VECTOR_GEOMETRY_RASTERIZED warning", warnings.any { it.feature == "VECTOR_GEOMETRY_RASTERIZED" })
        assertTrue("Must log COMIC_METADATA_FLATTENED warning", warnings.any { it.feature == "COMIC_METADATA_FLATTENED" })
    }

    // 10. PackBits RLE Round-Trip: exact bit-level reproduction across literals and runs
    @Test
    fun testPackBitsRleRoundTrip() {
        // Test case 1: repeated bytes (run of 50)
        val repeated = ByteArray(50) { 0x7F.toByte() }
        val encRepeated = PackBits.encode(repeated)
        val decRepeated = PackBits.decode(encRepeated, 0, encRepeated.size, 50)
        assertArrayEquals(repeated, decRepeated)

        // Test case 2: literals (distinct sequential bytes)
        val literals = ByteArray(64) { it.toByte() }
        val encLiterals = PackBits.encode(literals)
        val decLiterals = PackBits.decode(encLiterals, 0, encLiterals.size, 64)
        assertArrayEquals(literals, decLiterals)

        // Test case 3: mixed pattern (literals + run of 128 + literals)
        val mixed = ByteArray(200)
        for (i in 0 until 30) mixed[i] = i.toByte()
        for (i in 30 until 158) mixed[i] = 0xAA.toByte() // run of 128
        for (i in 158 until 200) mixed[i] = (200 - i).toByte()

        val encMixed = PackBits.encode(mixed)
        val decMixed = PackBits.decode(encMixed, 0, encMixed.size, 200)
        assertArrayEquals(mixed, decMixed)

        // Test case 4: single byte
        val single = byteArrayOf(42)
        val encSingle = PackBits.encode(single)
        val decSingle = PackBits.decode(encSingle, 0, encSingle.size, 1)
        assertArrayEquals(single, decSingle)
    }

    // 11. Flat PSD Composite Fallback: imports composite image preview when layer section is empty
    @Test
    fun testFlatPsdCompositeImportFallback() {
        val width = 32
        val height = 32
        val red = ByteArray(width * height) { 255.toByte() }
        val green = ByteArray(width * height) { 0.toByte() }
        val blue = ByteArray(width * height) { 0.toByte() }

        // Build a PSD with 0 layers and composite preview
        val header = ByteArray(26)
        val bb = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.BIG_ENDIAN)
        bb.put("8BPS".toByteArray(Charsets.US_ASCII))
        bb.putShort(1) // version
        bb.put(ByteArray(6)) // reserved
        bb.putShort(3) // 3 channels
        bb.putInt(height)
        bb.putInt(width)
        bb.putShort(8) // depth
        bb.putShort(3) // RGB

        val baos = java.io.ByteArrayOutputStream()
        baos.write(header)
        baos.write(ByteArray(4)) // color mode data len = 0
        baos.write(ByteArray(4)) // image resources len = 0
        baos.write(ByteArray(4)) // layer and mask section len = 0

        // Composite image: raw uncompressed (compression = 0)
        val dos = java.io.DataOutputStream(baos)
        dos.writeShort(0) // compression = 0
        dos.write(red)
        dos.write(green)
        dos.write(blue)
        dos.flush()

        val flatBytes = baos.toByteArray()
        val result = PsdDocumentImporter.importPsd(flatBytes.inputStream(), "flat_sample.psd")

        assertEquals(width, result.document.canvas.width)
        assertEquals(height, result.document.canvas.height)

        val layers = result.document.allLayersFlat().filterIsInstance<LayerNode.RasterLayer>()
        assertEquals(1, layers.size)
        assertEquals("Background", layers[0].name)

        val pixels = result.layerPixelBuffers[layers[0].id]
        assertNotNull(pixels)
        assertEquals(0xFFFF0000.toInt(), pixels!![0])

        val report = result.conversionReport
        assertTrue("Report must record FLATTENED_PSD warning", report.warnings.any { it.feature == "FLATTENED_PSD" })
    }
}
