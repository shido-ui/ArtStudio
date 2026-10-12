package com.artstudio.interchange

import com.artstudio.adjustment.BrightnessContrastAdjustment
import com.artstudio.animation.AnimationFrame
import com.artstudio.comic.ComicPanel
import com.artstudio.comic.Point2D
import com.artstudio.comic.SpeechBubble
import com.artstudio.document.io.CorruptDocumentException
import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.model.*
import com.artstudio.export.ArtworkExportService
import com.artstudio.export.ExportConfig
import com.artstudio.export.ExportFormat
import com.artstudio.export.ExportResult
import com.artstudio.export.PureKotlinPngEncoder
import com.artstudio.interchange.ora.OpenRasterExporter
import com.artstudio.interchange.ora.OpenRasterImporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class OpenRasterAndNativeRoundTripTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("artstudio_ora_tests").toFile()
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

    // 1. ORA Import: Fixture Simple preserves dimensions, layer order, blend modes, and raster pixels
    @Test
    fun testOraImport_fixtureSimple_preservesDimensionsLayersOrderAndPixels() {
        val fixtureFile = resolveFixture("layered_ora/sample_layered_simple_01.ora")
        val result = OpenRasterImporter.importOra(fixtureFile)

        assertNotNull(result.document)
        assertEquals(512, result.document.canvas.width)
        assertEquals(512, result.document.canvas.height)

        val layers = result.document.rootGroup.children
        assertEquals("Expected exactly 2 layers in simple ORA fixture", 2, layers.size)

        // Verify bottom-to-top layer order
        val layer0 = layers[0] as LayerNode.RasterLayer
        val layer1 = layers[1] as LayerNode.RasterLayer

        assertEquals("Background", layer0.name)
        assertEquals(1.0f, layer0.opacity, 0.001f)
        assertTrue(layer0.visible)
        assertEquals(BlendModeType.Normal, layer0.blendMode)

        assertEquals("Linework", layer1.name)
        assertEquals(1.0f, layer1.opacity, 0.001f)
        assertTrue(layer1.visible)
        assertEquals(BlendModeType.Multiply, layer1.blendMode)

        // Verify raster pixel buffers decoded
        val pixels0 = result.layerPixelBuffers[layer0.id]
        val pixels1 = result.layerPixelBuffers[layer1.id]
        assertNotNull(pixels0)
        assertNotNull(pixels1)
        assertEquals(512 * 512, pixels0!!.size)
        assertEquals(512 * 512, pixels1!!.size)

        // All features supported: 0 conversion warnings
        assertFalse(result.report.hasLossyConversions)
        assertEquals(0, result.report.warningCount)
    }

    // 2. ORA Import: Fixture Dense preserves three layers, opacities, and blend modes
    @Test
    fun testOraImport_fixtureDense_preservesThreeLayersOrderOpacityAndBlendModes() {
        val fixtureFile = resolveFixture("layered_ora/sample_layered_dense_02.ora")
        val result = OpenRasterImporter.importOra(fixtureFile)

        assertNotNull(result.document)
        assertEquals(1024, result.document.canvas.width)
        assertEquals(1024, result.document.canvas.height)

        val layers = result.document.rootGroup.children
        assertEquals("Expected exactly 3 layers in dense ORA fixture", 3, layers.size)

        val l0 = layers[0] as LayerNode.RasterLayer
        val l1 = layers[1] as LayerNode.RasterLayer
        val l2 = layers[2] as LayerNode.RasterLayer

        assertEquals("Canvas Base", l0.name)
        assertEquals(1.0f, l0.opacity, 0.001f)
        assertEquals(BlendModeType.Normal, l0.blendMode)

        assertEquals("Color Wash", l1.name)
        assertEquals(0.85f, l1.opacity, 0.001f)
        assertEquals(BlendModeType.Normal, l1.blendMode)

        assertEquals("Ink Contours", l2.name)
        assertEquals(1.0f, l2.opacity, 0.001f)
        assertEquals(BlendModeType.Multiply, l2.blendMode)

        for (layer in listOf(l0, l1, l2)) {
            val buf = result.layerPixelBuffers[layer.id]
            assertNotNull(buf)
            assertEquals(1024 * 1024, buf!!.size)
        }
    }

    // 3. ORA Import: Corrupt fixture raises recoverable CorruptDocumentException
    @Test
    fun testOraImport_corruptFixture_raisesRecoverableError() {
        val corruptFile = resolveFixture("corrupt_truncated/corrupted_archive_02.ora")
        try {
            OpenRasterImporter.importOra(corruptFile)
            fail("Corrupt archive must fail with CorruptDocumentException")
        } catch (e: CorruptDocumentException) {
            assertTrue("Exception message indicates archive corruption", e.message!!.isNotEmpty())
        }
    }

    // 4. ORA Import: Unsupported blend mode falls back to Normal without silent flattening
    @Test
    fun testOraImport_unsupportedBlendMode_fallsBackToNormalAndReportsWarningWithoutFlattening() {
        val syntheticOra = File(tempDir, "synthetic_unsupported_blend.ora")
        val width = 64
        val height = 64

        // Generate synthetic ORA with svg:color-burn and svg:difference
        val stackXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <image version="0.0.3" width="$width" height="$height">
              <stack>
                <layer name="Base Art" src="data/layer_0.png" opacity="1.0" visibility="visible" composite-op="svg:color-burn"/>
                <layer name="Effect Art" src="data/layer_1.png" opacity="0.75" visibility="visible" composite-op="svg:difference"/>
              </stack>
            </image>
        """.trimIndent()

        val samplePng = ByteArrayOutputStream().apply {
            PureKotlinPngEncoder.encodeRgba(IntArray(width * height) { 0xFF112233.toInt() }, width, height, this)
        }.toByteArray()

        FileOutputStream(syntheticOra).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val mimeBytes = OpenRasterImporter.MIMETYPE_ORA.toByteArray(Charsets.US_ASCII)
                val mEntry = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    val crc = CRC32()
                    crc.update(mimeBytes)
                    this.crc = crc.value
                }
                zos.putNextEntry(mEntry)
                zos.write(mimeBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("stack.xml"))
                zos.write(stackXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("data/layer_0.png"))
                zos.write(samplePng)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("data/layer_1.png"))
                zos.write(samplePng)
                zos.closeEntry()
            }
        }

        val result = OpenRasterImporter.importOra(syntheticOra)

        // INVARIANT: Document is NOT flattened! Still has 2 separate layers.
        val layers = result.document.rootGroup.children
        assertEquals("Must NOT flatten layered artwork when unsupported properties exist", 2, layers.size)
        assertEquals("Base Art", layers[0].name)
        assertEquals("Effect Art", layers[1].name)

        // Fell back to Normal
        assertEquals(BlendModeType.Normal, layers[0].blendMode)
        assertEquals(BlendModeType.Normal, layers[1].blendMode)

        // Conversion report contains warnings
        assertTrue("Conversion report must flag lossy conversions", result.report.hasLossyConversions)
        assertEquals(2, result.report.warningCount)
        assertTrue(result.report.warnings.any { it.feature == "UNSUPPORTED_BLEND_MODE" })
    }

    // 5. ORA Export: Generates valid archive with stack.xml preserving layer names, order, opacity
    @Test
    fun testOraExport_preservesLayerNamesOrderVisibilityAndOpacityInStackXml() {
        val width = 32
        val height = 32
        val doc = ArtDocument(
            title = "Test Export",
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "l1", name = "Background Sketch", opacity = 1.0f, visible = true, blendMode = BlendModeType.Normal, width = width, height = height),
                    LayerNode.RasterLayer(id = "l2", name = "Colors", opacity = 0.8f, visible = true, blendMode = BlendModeType.Screen, width = width, height = height),
                    LayerNode.RasterLayer(id = "l3", name = "Inks", opacity = 1.0f, visible = false, blendMode = BlendModeType.Multiply, width = width, height = height)
                )
            )
        )

        val outputFile = File(tempDir, "exported_test.ora")
        val exportResult = OpenRasterExporter.exportOra(
            document = doc,
            outputFile = outputFile
        )

        assertTrue(outputFile.exists())
        assertEquals(3, exportResult.layerCount)

        // Unpack stack.xml and verify content
        val zf = ZipFile(outputFile)
        val stackEntry = zf.getEntry("stack.xml")
        assertNotNull("stack.xml must be present", stackEntry)

        val xmlContent = zf.getInputStream(stackEntry).bufferedReader().readText()
        assertTrue(xmlContent.contains("name=\"Background Sketch\""))
        assertTrue(xmlContent.contains("name=\"Colors\""))
        assertTrue(xmlContent.contains("name=\"Inks\""))
        assertTrue(xmlContent.contains("opacity=\"0.8\""))
        assertTrue(xmlContent.contains("visibility=\"hidden\""))
        assertTrue(xmlContent.contains("composite-op=\"svg:screen\""))
        assertTrue(xmlContent.contains("composite-op=\"svg:multiply\""))
        zf.close()
    }

    // 6. ORA Round-Trip: Export and Re-Import retains exact raster pixels (Zero Diff)
    @Test
    fun testOraExport_andReimport_bitExactRasterPixelRoundTrip() {
        val width = 16
        val height = 16
        val testPixels0 = IntArray(width * height) { i -> (0xFF shl 24) or ((i * 15) shl 16) or 0x00FF }
        val testPixels1 = IntArray(width * height) { i -> (0x80 shl 24) or 0x00FF00 } // Semi-transparent green

        val doc = ArtDocument(
            title = "Pixel Roundtrip",
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "base_layer", name = "Base", width = width, height = height),
                    LayerNode.RasterLayer(id = "overlay_layer", name = "Overlay", opacity = 0.9f, blendMode = BlendModeType.Multiply, width = width, height = height)
                )
            )
        )

        val exportFile = File(tempDir, "pixel_roundtrip.ora")
        OpenRasterExporter.exportOra(
            document = doc,
            layerPixelBuffers = mapOf(
                "base_layer" to testPixels0,
                "overlay_layer" to testPixels1
            ),
            outputFile = exportFile
        )

        val reimported = OpenRasterImporter.importOra(exportFile)
        assertEquals(2, reimported.document.rootGroup.children.size)

        val importedL0 = reimported.document.rootGroup.children[0]
        val importedL1 = reimported.document.rootGroup.children[1]

        val rePixels0 = reimported.layerPixelBuffers[importedL0.id]
        val rePixels1 = reimported.layerPixelBuffers[importedL1.id]

        assertNotNull(rePixels0)
        assertNotNull(rePixels1)

        var diff0 = 0
        for (i in testPixels0.indices) {
            if (testPixels0[i] != rePixels0!![i]) diff0++
        }
        assertEquals("Base layer pixel round-trip diff must be zero", 0, diff0)

        var diff1 = 0
        for (i in testPixels1.indices) {
            if (testPixels1[i] != rePixels1!![i]) diff1++
        }
        assertEquals("Overlay layer pixel round-trip diff must be zero", 0, diff1)
    }

    // 7. ORA Export & Import: Nested groups preserved as nested stacks
    @Test
    fun testOraExport_nestedGroups_preservesHierarchyAsNestedStacks() {
        val width = 24
        val height = 24
        val group = LayerNode.LayerGroup(
            name = "Character Group",
            opacity = 0.95f,
            blendMode = BlendModeType.Normal,
            children = listOf(
                LayerNode.RasterLayer(id = "skin", name = "Skin Tone", width = width, height = height),
                LayerNode.RasterLayer(id = "hair", name = "Hair Detail", width = width, height = height)
            )
        )

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "bg", name = "Background", width = width, height = height),
                    group
                )
            )
        )

        val outputFile = File(tempDir, "nested_groups.ora")
        OpenRasterExporter.exportOra(document = doc, outputFile = outputFile)

        val reimported = OpenRasterImporter.importOra(outputFile)
        val rootChildren = reimported.document.rootGroup.children
        assertEquals(2, rootChildren.size)

        assertTrue("Second child must be a LayerGroup", rootChildren[1] is LayerNode.LayerGroup)
        val importedGroup = rootChildren[1] as LayerNode.LayerGroup
        assertEquals("Character Group", importedGroup.name)
        assertEquals(2, importedGroup.children.size)
        assertEquals("Skin Tone", importedGroup.children[0].name)
        assertEquals("Hair Detail", importedGroup.children[1].name)
    }

    // 8. ORA Export: Unsupported features generate explicit conversion warnings
    @Test
    fun testOraExport_unsupportedNativeFeatures_generateDetailedConversionReportWithoutFailing() {
        val width = 32
        val height = 32
        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "masked_layer",
                        name = "Masked",
                        mask = LayerMask(width = width, height = height),
                        isClippingMask = true,
                        isAlphaLocked = true,
                        width = width,
                        height = height
                    ),
                    LayerNode.AdjustmentLayer(
                        id = "adj_layer",
                        name = "Color Grade",
                        adjustment = BrightnessContrastAdjustment(brightness = 20f, contrast = 10f)
                    )
                )
            ),
            animation = AnimationTimelineConfig(fps = 24, totalFrames = 5)
        )

        val outputFile = File(tempDir, "unsupported_audit.ora")
        val exportResult = OpenRasterExporter.exportOra(document = doc, outputFile = outputFile)

        assertTrue(outputFile.exists())
        val report = exportResult.report
        assertTrue("Report must detect non-destructive conversions", report.hasLossyConversions)

        val features = report.warnings.map { it.feature }
        assertTrue("Must warn about LAYER_MASK", features.contains("LAYER_MASK"))
        assertTrue("Must warn about CLIPPING_MASK", features.contains("CLIPPING_MASK"))
        assertTrue("Must warn about ALPHA_LOCK", features.contains("ALPHA_LOCK"))
        assertTrue("Must warn about ADJUSTMENT_LAYER", features.contains("ADJUSTMENT_LAYER"))
        assertTrue("Must warn about ANIMATION_TIMELINE", features.contains("ANIMATION_TIMELINE"))
    }

    // 9. Native Package: saveProject and loadProject preserve full fidelity with pixel buffers
    @Test
    fun testNativePackage_saveProjectAndLoadProject_fullFidelityWithLayerPixelBuffers() {
        val width = 20
        val height = 20
        val pixels0 = IntArray(width * height) { 0xFF00FF00.toInt() } // Opaque green
        val pixels1 = IntArray(width * height) { 0xFF0000FF.toInt() } // Opaque blue
        val maskPixels = IntArray(width * height) { 0xFF808080.toInt() } // 50% mask

        val doc = ArtDocument(
            title = "Native Full Fidelity",
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "layer_base", name = "Base", width = width, height = height),
                    LayerNode.RasterLayer(
                        id = "layer_clip",
                        name = "Clip",
                        isClippingMask = true,
                        mask = LayerMask(width = width, height = height),
                        width = width,
                        height = height
                    )
                )
            )
        )

        val nativeFile = File(tempDir, "project.ffart")
        DocumentPackage.saveProject(
            document = doc,
            layerPixelBuffers = mapOf(
                "layer_base" to pixels0,
                "layer_clip" to pixels1
            ),
            layerMaskBuffers = mapOf(
                "layer_clip" to maskPixels
            ),
            outputFile = nativeFile
        )

        assertTrue(nativeFile.exists())
        val loaded = DocumentPackage.loadProject(nativeFile)

        assertEquals("Native Full Fidelity", loaded.document.title)
        assertEquals(2, loaded.document.rootGroup.children.size)

        val loadedBase = loaded.document.rootGroup.children[0] as LayerNode.RasterLayer
        val loadedClip = loaded.document.rootGroup.children[1] as LayerNode.RasterLayer

        assertTrue(loadedClip.isClippingMask)
        assertNotNull(loadedClip.mask)

        val loadedPx0 = loaded.layerPixelBuffers[loadedBase.id]
        val loadedPx1 = loaded.layerPixelBuffers[loadedClip.id]
        val loadedMaskPx = loaded.layerMaskBuffers[loadedClip.id]

        assertNotNull(loadedPx0)
        assertNotNull(loadedPx1)
        assertNotNull(loadedMaskPx)

        assertArrayEquals(pixels0, loadedPx0)
        assertArrayEquals(pixels1, loadedPx1)
        assertArrayEquals(maskPixels, loadedMaskPx)
    }

    // 10. Native Package: Preserves Animation Timeline and AutoDraw Metadata
    @Test
    fun testNativePackage_roundTrip_preservesAnimationAndAutoDrawMetadata() {
        val anim = AnimationTimelineConfig(
            fps = 12,
            totalFrames = 3,
            onionSkinEnabled = true,
            onionSkinFramesBefore = 2,
            frames = listOf(
                AnimationFrame(id = "f0", name = "Frame 1", durationTicks = 1, celLayerIds = listOf("l1")),
                AnimationFrame(id = "f1", name = "Frame 2", durationTicks = 2, celLayerIds = listOf("l1")),
                AnimationFrame(id = "f2", name = "Frame 3", durationTicks = 1, celLayerIds = listOf("l1"))
            )
        )

        val prov = AutoDrawProvenance(
            sourceImageHash = "a1b2c3d4e5f6",
            algorithm = "morphological_v2",
            edgeThresholdLow = 40f,
            edgeThresholdHigh = 160f,
            generatedLayerIds = listOf("l1"),
            timestampMs = 1700000000000L
        )

        val doc = ArtDocument(
            title = "Anim AutoDraw",
            animation = anim,
            autoDrawProvenance = prov
        )

        val nativeFile = File(tempDir, "anim_autodraw.ffart")
        DocumentPackage.saveProject(document = doc, outputFile = nativeFile)

        val loaded = DocumentPackage.loadProject(nativeFile)
        val loadedAnim = loaded.document.animation
        val loadedProv = loaded.document.autoDrawProvenance

        assertNotNull(loadedAnim)
        assertEquals(12, loadedAnim!!.fps)
        assertEquals(3, loadedAnim.totalFrames)
        assertTrue(loadedAnim.onionSkinEnabled)
        assertEquals(2, loadedAnim.onionSkinFramesBefore)
        assertEquals(3, loadedAnim.frames.size)

        assertNotNull(loadedProv)
        assertEquals("a1b2c3d4e5f6", loadedProv!!.sourceImageHash)
        assertEquals("morphological_v2", loadedProv.algorithm)
        assertEquals(40f, loadedProv.edgeThresholdLow, 0.01f)
        assertEquals(160f, loadedProv.edgeThresholdHigh, 0.01f)
    }

    // 11. Native Package: Preserves Comic Panels, Speech Bubbles, and Screentones
    @Test
    fun testNativePackage_roundTrip_preservesComicPanelsAndSpeechBubbles() {
        val panel = ComicPanel(
            id = "panel_1",
            name = "Panel 1",
            vertices = listOf(
                Point2D(10f, 10f),
                Point2D(500f, 10f),
                Point2D(500f, 500f),
                Point2D(10f, 500f)
            )
        )
        val bubble = SpeechBubble(
            id = "bubble_1",
            text = "Hello Art Studio!",
            x = 50f,
            y = 50f,
            width = 200f,
            height = 100f
        )

        val doc = ArtDocument(
            title = "Comic Project",
            comicPanels = listOf(panel),
            speechBubbles = listOf(bubble)
        )

        val nativeFile = File(tempDir, "comic_project.ffart")
        DocumentPackage.saveProject(document = doc, outputFile = nativeFile)

        val loaded = DocumentPackage.loadProject(nativeFile)
        assertEquals(1, loaded.document.comicPanels.size)
        assertEquals("panel_1", loaded.document.comicPanels[0].id)
        assertEquals(1, loaded.document.speechBubbles.size)
        assertEquals("Hello Art Studio!", loaded.document.speechBubbles[0].text)
    }

    // 12. Native Package: All Sample Project Corpus Fixtures Load Successfully
    @Test
    fun testNativePackage_corpusFixtures_allSampleProjectsLoadSuccessfully() {
        val fixtureDir = resolveFixture("sample_projects")
        val sampleFiles = listOf(
            "sample_animation_timeline.ffart",
            "sample_multilayer_groups_masks.ffart",
            "sample_single_layer.ffart",
            "sample_transparency_and_reference.ffart",
            "sample_vector_paths.ffart",
            "ten_layer_fixture.ffart"
        )

        for (filename in sampleFiles) {
            val file = File(fixtureDir, filename)
            assertTrue("Fixture $filename must exist", file.exists())

            val loaded = DocumentPackage.loadProject(file)
            assertNotNull("Document in $filename must load cleanly", loaded.document)
            assertTrue("Canvas width must be > 0", loaded.document.canvas.width > 0)
            assertTrue("Canvas height must be > 0", loaded.document.canvas.height > 0)
            assertNotNull("Root group must exist", loaded.document.rootGroup)
        }
    }

    // 13. ORA Mimetype: Verified as first entry and uncompressed STORED
    @Test
    fun testOraMimetype_isFirstEntryAndUncompressedStored() {
        val doc = ArtDocument(title = "Mime Test")
        val outputFile = File(tempDir, "mime_test.ora")
        OpenRasterExporter.exportOra(document = doc, outputFile = outputFile)

        ZipFile(outputFile).use { zf ->
            val entries = zf.entries()
            assertTrue(entries.hasMoreElements())
            val firstEntry = entries.nextElement()
            assertEquals("mimetype", firstEntry.name)
            assertEquals("mimetype must be uncompressed STORED", ZipEntry.STORED, firstEntry.method)

            val mimeContent = zf.getInputStream(firstEntry).bufferedReader().readText()
            assertEquals("image/openraster", mimeContent)
        }
    }

    // 14. ORA Auxiliary Images: Thumbnails and mergedimage.png are present
    @Test
    fun testOraThumbnailsAndMergedImage_arePresentAndValidPng() {
        val doc = ArtDocument(title = "Aux Test")
        val outputFile = File(tempDir, "aux_test.ora")
        OpenRasterExporter.exportOra(document = doc, outputFile = outputFile)

        ZipFile(outputFile).use { zf ->
            val thumbEntry = zf.getEntry("Thumbnails/thumbnail.png")
            val mergedEntry = zf.getEntry("mergedimage.png")

            assertNotNull("Thumbnails/thumbnail.png must be present", thumbEntry)
            assertNotNull("mergedimage.png must be present", mergedEntry)

            val thumbBytes = zf.getInputStream(thumbEntry).readBytes()
            val mergedBytes = zf.getInputStream(mergedEntry).readBytes()

            assertTrue(thumbBytes.size > 8)
            assertTrue(mergedBytes.size > 8)

            // Verify PNG magic bytes
            val pngMagic = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
            for (i in 0..3) {
                assertEquals(pngMagic[i], thumbBytes[i])
                assertEquals(pngMagic[i], mergedBytes[i])
            }
        }
    }

    // 15. ArtworkExportService: ORA export produces valid verified archive via background service
    @Test
    fun testArtworkExportService_exportOra_producesValidArchive() = runBlocking {
        val doc = ArtDocument(
            title = "Service ORA",
            canvas = CanvasConfig(width = 32, height = 32)
        )
        val targetFile = File(tempDir, "service_export.ora")
        val service = ArtworkExportService()

        val result = service.exportArtwork(
            document = doc,
            config = ExportConfig(format = ExportFormat.ORA),
            targetFile = targetFile
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(ExportFormat.ORA, success.format)
        assertTrue(targetFile.exists())
        assertTrue(targetFile.length() > 0)

        // Verify importable by OpenRasterImporter
        val imported = OpenRasterImporter.importOra(targetFile)
        assertEquals(32, imported.document.canvas.width)
        assertEquals(32, imported.document.canvas.height)
    }
}
